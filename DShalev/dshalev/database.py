from __future__ import annotations

import ctypes
import hashlib
import os
import secrets
import sqlite3
import uuid as uuidlib
import time
from contextlib import contextmanager
from pathlib import Path
from threading import RLock
from typing import Iterator

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS app_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'admin' CHECK (role IN ('admin','staff')),
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'Producto',
    unit TEXT NOT NULL DEFAULT 'unidad',
    stock REAL NOT NULL DEFAULT 0,
    stock_milli INTEGER NOT NULL DEFAULT 0 CHECK (stock_milli BETWEEN -9000000000000000000 AND 9000000000000000000),
    sale_price_cents INTEGER NOT NULL DEFAULT 0 CHECK (sale_price_cents >= 0),
    cost_cents INTEGER NOT NULL DEFAULT 0 CHECK (cost_cents >= 0),
    low_stock REAL NOT NULL DEFAULT 5,
    low_stock_milli INTEGER NOT NULL DEFAULT 5000 CHECK (low_stock_milli >= 0),
    critical_stock REAL NOT NULL DEFAULT 1,
    critical_stock_milli INTEGER NOT NULL DEFAULT 1000 CHECK (critical_stock_milli >= 0),
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS catalog_photos (
    entity TEXT NOT NULL CHECK (entity IN ('producto','servicio')),
    entity_id INTEGER NOT NULL,
    filename TEXT NOT NULL UNIQUE,
    byte_length INTEGER NOT NULL,
    sha256 TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    PRIMARY KEY(entity,entity_id)
);
CREATE TABLE IF NOT EXISTS inventory_movements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    item_id INTEGER NOT NULL REFERENCES items(id),
    item_name TEXT NOT NULL,
    item_category TEXT NOT NULL,
    item_unit TEXT NOT NULL,
    movement_type TEXT NOT NULL CHECK (movement_type IN ('MIGRACION','ALTA','AJUSTE','VENTA','ANULACION_VENTA','VENTA_MOVIL','ANULACION_MOVIL','CORRECCION_MOVIL','COMANDO_SYNC','SINCRONIZACION')),
    delta_milli INTEGER NOT NULL CHECK (delta_milli BETWEEN -9000000000000000000 AND 9000000000000000000),
    stock_after_milli INTEGER NOT NULL CHECK (stock_after_milli BETWEEN -9000000000000000000 AND 9000000000000000000),
    actor_type TEXT NOT NULL CHECK (actor_type IN ('usuario','empleado','sistema')),
    actor_id INTEGER,
    actor TEXT NOT NULL,
    reason TEXT NOT NULL,
    source TEXT NOT NULL,
    reference TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_item ON inventory_movements(item_id,id);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_created ON inventory_movements(created_at,id);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_reference ON inventory_movements(reference);
CREATE TABLE IF NOT EXISTS sync_services (
    service_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    service_type TEXT NOT NULL DEFAULT 'General',
    price_cents INTEGER NOT NULL DEFAULT 0,
    description TEXT NOT NULL DEFAULT '',
    photo_uri TEXT,
    created_ms INTEGER NOT NULL DEFAULT 0,
    updated_ms INTEGER NOT NULL DEFAULT 0,
    deleted INTEGER NOT NULL DEFAULT 0,
    ingredients_json TEXT NOT NULL DEFAULT '[]',
    local_name TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS presets (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    basis_points INTEGER NOT NULL DEFAULT 0,
    product_ids_json TEXT NOT NULL DEFAULT '[]',
    service_ids_json TEXT NOT NULL DEFAULT '[]',
    payment_method TEXT,
    minimum_cents INTEGER,
    active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS recipes (
    product_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    ingredient_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    quantity_milli INTEGER NOT NULL CHECK (quantity_milli > 0),
    PRIMARY KEY(product_id, ingredient_id),
    CHECK (product_id != ingredient_id)
);
CREATE TABLE IF NOT EXISTS cost_sheets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    margin TEXT NOT NULL DEFAULT '',
    margin_unit TEXT NOT NULL DEFAULT 'PORCENTAJE' CHECK (margin_unit IN ('PORCENTAJE','FACTOR')),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS cost_sheet_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sheet_id INTEGER NOT NULL REFERENCES cost_sheets(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('MATERIAS','TRABAJO','OTROS','INDIRECTOS')),
    quantity TEXT NOT NULL,
    unit TEXT NOT NULL,
    unit_cost TEXT NOT NULL,
    currency TEXT NOT NULL CHECK (currency IN ('CUP','USD'))
);
CREATE INDEX IF NOT EXISTS idx_cost_sheet_lines_sheet ON cost_sheet_lines(sheet_id,id);
CREATE TABLE IF NOT EXISTS cost_sheet_exports (
    sheet_id INTEGER NOT NULL REFERENCES cost_sheets(id) ON DELETE CASCADE,
    category TEXT NOT NULL CHECK (category IN ('Producto','Servicio')),
    item_id INTEGER NOT NULL REFERENCES items(id),
    PRIMARY KEY(sheet_id,category)
);
CREATE TABLE IF NOT EXISTS shifts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    opened_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    closed_at TEXT,
    opening_cash_cents INTEGER NOT NULL DEFAULT 0,
    counted_cash_cents INTEGER
);
CREATE TABLE IF NOT EXISTS cash_movements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shift_id INTEGER NOT NULL REFERENCES shifts(id),
    movement_type TEXT NOT NULL CHECK (movement_type IN ('ENTRADA','SALIDA')),
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    reason TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX IF NOT EXISTS idx_cash_shift ON cash_movements(shift_id,created_at);
CREATE TABLE IF NOT EXISTS employees (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slot INTEGER NOT NULL UNIQUE CHECK (slot BETWEEN 1 AND 10),
    name TEXT NOT NULL,
    permissions_json TEXT NOT NULL DEFAULT '[]',
    device_kind TEXT NOT NULL DEFAULT 'browser' CHECK (device_kind IN ('browser','android','ios')),
    active INTEGER NOT NULL DEFAULT 1,
    linked_at TEXT,
    last_seen TEXT,
    pairing_token TEXT,
    pairing_expires INTEGER,
    employee_key TEXT,
    device_id TEXT,
    phone TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS customers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    phone TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS sales (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shift_id INTEGER NOT NULL REFERENCES shifts(id),
    total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
    payment_method TEXT NOT NULL CHECK (payment_method IN ('EFECTIVO','TRANSFERENCIA')),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    employee_id INTEGER REFERENCES employees(id),
    user_id INTEGER REFERENCES users(id),
    customer_id INTEGER REFERENCES customers(id),
    note TEXT NOT NULL DEFAULT '',
    request_key TEXT,
    request_hash TEXT NOT NULL DEFAULT '',
    inventory_logged INTEGER NOT NULL DEFAULT 0 CHECK (inventory_logged IN (0,1)),
    cancelled_at TEXT,
    cancellation_reason TEXT,
    cancelled_by INTEGER REFERENCES users(id),
    correction_of_id INTEGER REFERENCES sales(id)
);
CREATE TABLE IF NOT EXISTS sale_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    item_id INTEGER NOT NULL REFERENCES items(id),
    item_name TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0)
);
CREATE TABLE IF NOT EXISTS retired_employees (
    employee_id INTEGER PRIMARY KEY,
    employee_name TEXT NOT NULL,
    employee_key TEXT NOT NULL,
    revoked_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS sync_receipts (
    uuid TEXT PRIMARY KEY,
    employee_id INTEGER,
    message_type TEXT NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '{}',
    received_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS sync_shifts (
    employee_id INTEGER NOT NULL,
    employee_name TEXT NOT NULL DEFAULT '',
    uuid TEXT NOT NULL,
    opened_ms INTEGER NOT NULL DEFAULT 0,
    closed_ms INTEGER,
    opening_cash_cents INTEGER,
    counted_cash_cents INTEGER,
    count_json TEXT,
    payload_json TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    PRIMARY KEY(employee_id,uuid)
);
CREATE TABLE IF NOT EXISTS sync_cash_movements (
    uuid TEXT PRIMARY KEY,
    employee_id INTEGER NOT NULL,
    employee_name TEXT NOT NULL DEFAULT '',
    shift_uuid TEXT NOT NULL,
    movement_type TEXT NOT NULL,
    amount_cents INTEGER NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_ms INTEGER NOT NULL DEFAULT 0,
    payload_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS mobile_close_requests (
    employee_id INTEGER PRIMARY KEY REFERENCES employees(id) ON DELETE CASCADE,
    requested_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    decision INTEGER,
    decided_by TEXT,
    decided_at TEXT
);
CREATE TABLE IF NOT EXISTS mobile_count_approvals (
    employee_id INTEGER NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    shift_uuid TEXT NOT NULL,
    count_json TEXT NOT NULL,
    approved_by TEXT NOT NULL,
    approved_at INTEGER NOT NULL,
    PRIMARY KEY(employee_id,shift_uuid)
);
CREATE TABLE IF NOT EXISTS sync_sales (
    uuid TEXT PRIMARY KEY,
    employee_id INTEGER NOT NULL,
    employee_name TEXT NOT NULL DEFAULT '',
    shift_uuid TEXT NOT NULL,
    sale_at_ms INTEGER NOT NULL,
    payment_method TEXT NOT NULL,
    total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
    payload_json TEXT NOT NULL,
    cancelled_at INTEGER,
    cancellation_reason TEXT,
    cancelled_by TEXT,
    received_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS sync_sale_changes (
    uuid TEXT PRIMARY KEY,
    employee_id INTEGER NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE INDEX IF NOT EXISTS idx_sync_sales_date ON sync_sales(sale_at_ms);
CREATE TABLE IF NOT EXISTS sync_commands (
    employee_id INTEGER NOT NULL,
    command_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    PRIMARY KEY(employee_id,command_key)
);
CREATE TABLE IF NOT EXISTS auth_throttle (
    ip TEXT PRIMARY KEY,
    attempts INTEGER NOT NULL,
    locked_until INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sales_created ON sales(created_at);
CREATE INDEX IF NOT EXISTS idx_sales_shift ON sales(shift_id);
CREATE INDEX IF NOT EXISTS idx_items_active ON items(active, name);
"""

DB_MAGIC = b"DSHALEVDB1"
BACKUP_MAGIC = b"DSHALEV-BACKUP-1\n"
BACKUP_PLAIN_MAGIC = b"DSHALEV-BACKUP-DATA-1\n"
BACKUP_KDF_ROUNDS = 300_000
KEY_MAGIC_DPAPI = b"DSPAPI1"
KEY_MAGIC_DEV = b"DSKEY1"
DB_AAD = b"DShalev SQLite snapshot v1"


def _windows_protect(data: bytes, *, decrypt: bool) -> bytes:
    class DataBlob(ctypes.Structure):
        _fields_ = [("cbData", ctypes.c_uint32), ("pbData", ctypes.POINTER(ctypes.c_ubyte))]

    source_buffer = ctypes.create_string_buffer(data)
    source = DataBlob(len(data), ctypes.cast(source_buffer, ctypes.POINTER(ctypes.c_ubyte)))
    destination = DataBlob()
    crypt32 = ctypes.windll.crypt32
    crypt32.CryptProtectData.argtypes = [ctypes.POINTER(DataBlob), ctypes.c_wchar_p, ctypes.POINTER(DataBlob),
                                         ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, ctypes.POINTER(DataBlob)]
    crypt32.CryptProtectData.restype = ctypes.c_int
    crypt32.CryptUnprotectData.argtypes = [ctypes.POINTER(DataBlob), ctypes.c_void_p, ctypes.POINTER(DataBlob),
                                           ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, ctypes.POINTER(DataBlob)]
    crypt32.CryptUnprotectData.restype = ctypes.c_int
    if decrypt:
        ok = crypt32.CryptUnprotectData(ctypes.byref(source), None, None, None, None, 0, ctypes.byref(destination))
    else:
        ok = crypt32.CryptProtectData(ctypes.byref(source), "DShalev database key", None, None, None, 0, ctypes.byref(destination))
    if not ok:
        raise OSError("Windows no pudo proteger o recuperar la clave local de DShalev.")
    try:
        return ctypes.string_at(destination.pbData, destination.cbData)
    finally:
        local_free = ctypes.windll.kernel32.LocalFree
        local_free.argtypes = [ctypes.c_void_p]
        local_free.restype = ctypes.c_void_p
        local_free(destination.pbData)


def _key_store(path: Path, *, create: bool) -> bytes:
    if path.exists():
        stored = path.read_bytes()
        if stored.startswith(KEY_MAGIC_DPAPI) and os.name == "nt":
            key = _windows_protect(stored[len(KEY_MAGIC_DPAPI):], decrypt=True)
        elif stored.startswith(KEY_MAGIC_DEV):
            key = stored[len(KEY_MAGIC_DEV):]
        else:
            raise RuntimeError("La clave de datos no se puede abrir en este equipo o está dañada. Conserva DShalevData completo y el usuario de Windows original.")
        if len(key) != 32:
            raise RuntimeError("La clave de datos de DShalev está dañada.")
        return key
    if not create:
        raise RuntimeError("Falta la clave de cifrado. DShalev no creará una base vacía encima de los datos existentes.")
    key = secrets.token_bytes(32)
    if os.name == "nt":
        stored = KEY_MAGIC_DPAPI + _windows_protect(key, decrypt=False)
    else:
        stored = KEY_MAGIC_DEV + key
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    temp.write_bytes(stored)
    try:
        os.chmod(temp, 0o600)
    except OSError:
        pass
    temp.replace(path)
    return key


class _ClosingConnection(sqlite3.Connection):
    _release_lock = None
    _on_close = None
    _initial_changes = 0

    def close(self) -> None:
        if getattr(self, "_release_lock", None) is None:
            return
        release = self._release_lock
        self._release_lock = None
        try:
            if getattr(self, "_on_close", None) and self.total_changes != self._initial_changes:
                self._on_close()
        finally:
            try:
                super().close()
            finally:
                release()

    def __exit__(self, exc_type, exc, traceback):
        try:
            return super().__exit__(exc_type, exc, traceback)
        finally:
            self.close()


class Database:
    """Encrypted SQLite snapshot. Windows protege la clave con DPAPI del usuario actual."""

    def __init__(self, path: Path):
        self.path = path
        self.key_path = path.with_name("database.key")
        self._lock = RLock()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        key = _key_store(self.key_path, create=not path.exists())
        self._key = key
        self._uri = f"file:dshalev_{secrets.token_hex(10)}?mode=memory&cache=shared"
        self._anchor = sqlite3.connect(self._uri, uri=True, timeout=10, isolation_level=None, check_same_thread=False)
        if path.exists():
            raw = path.read_bytes()
            if not raw.startswith(DB_MAGIC) or len(raw) < len(DB_MAGIC) + 12 + 16:
                raise RuntimeError("La base de datos no tiene un formato DShalev cifrado reconocido; no se modificó.")
            nonce = raw[len(DB_MAGIC):len(DB_MAGIC) + 12]
            try:
                clear = AESGCM(self._key).decrypt(nonce, raw[len(DB_MAGIC) + 12:], DB_AAD)
                source = sqlite3.connect(":memory:", isolation_level=None)
                try:
                    source.deserialize(clear)
                    source.backup(self._anchor)
                finally:
                    source.close()
            except Exception as exc:
                raise RuntimeError("No se pudo descifrar la base de datos. Conserva el archivo original y la clave local.") from exc
        self._anchor.execute("PRAGMA foreign_keys = ON")
        self._anchor.execute("PRAGMA busy_timeout = 10000")

    def _persist(self) -> None:
        clear = self._anchor.serialize()
        nonce = secrets.token_bytes(12)
        sealed = DB_MAGIC + nonce + AESGCM(self._key).encrypt(nonce, clear, DB_AAD)
        temp = self.path.with_suffix(self.path.suffix + ".tmp")
        with temp.open("wb") as output:
            output.write(sealed)
            output.flush()
            os.fsync(output.fileno())
        try:
            os.chmod(temp, 0o600)
        except OSError:
            pass
        temp.replace(self.path)

    @staticmethod
    def _backup_passphrase(passphrase: str) -> bytes:
        if not isinstance(passphrase, str) or not 12 <= len(passphrase) <= 1024:
            raise ValueError("La contraseña del respaldo debe tener entre 12 y 1024 caracteres.")
        return passphrase.encode("utf-8")

    def export_backup(self, passphrase: str) -> bytes:
        password = self._backup_passphrase(passphrase)
        raw_db = self.path.read_bytes()
        assets: dict[str, bytes] = {}
        with self.connect() as conn:
            photos = conn.execute("SELECT filename,byte_length,sha256 FROM catalog_photos").fetchall()
            meta = {row["key"]: row["value"] for row in conn.execute("SELECT key,value FROM app_meta WHERE key IN ('apk_version_code','apk_sha256','apk_bytes')")}
        photo_dir = self.path.parent / "catalog_photos"
        for photo in photos:
            asset_path = photo_dir / photo["filename"]
            data = asset_path.read_bytes() if asset_path.is_file() else b""
            if len(data) != photo["byte_length"] or hashlib.sha256(data).hexdigest() != photo["sha256"]:
                raise ValueError(f"Falta o está dañada la foto {photo['filename']}; no se creó el respaldo.")
            assets[f"catalog_photos/{photo['filename']}"] = data
        if meta.get("apk_version_code"):
            apk_path = self.path.parent / "updates" / "shalev.apk"
            apk_data = apk_path.read_bytes() if apk_path.is_file() else b""
            if len(apk_data) != int(meta.get("apk_bytes", 0)) or hashlib.sha256(apk_data).hexdigest() != meta.get("apk_sha256"):
                raise ValueError("El APK publicado falta o está dañado; no se creó el respaldo.")
            assets["updates/shalev.apk"] = apk_data
        payload = bytearray(BACKUP_PLAIN_MAGIC + self._key + len(raw_db).to_bytes(8, "big") + raw_db)
        payload.extend(len(assets).to_bytes(2, "big"))
        for name, data in sorted(assets.items()):
            name_bytes = name.encode("ascii")
            payload.extend(len(name_bytes).to_bytes(2, "big"))
            payload.extend(name_bytes)
            payload.extend(len(data).to_bytes(8, "big"))
            payload.extend(data)
        salt, nonce = secrets.token_bytes(16), secrets.token_bytes(12)
        encryption_key = hashlib.pbkdf2_hmac("sha256", password, salt, BACKUP_KDF_ROUNDS, dklen=32)
        return BACKUP_MAGIC + salt + nonce + AESGCM(encryption_key).encrypt(nonce, bytes(payload), BACKUP_MAGIC)

    def restore_backup(self, backup: bytes, passphrase: str) -> None:
        password = self._backup_passphrase(passphrase)
        if not isinstance(backup, bytes) or len(backup) > 1024 * 1024 * 1024:
            raise ValueError("El archivo de respaldo excede el límite permitido.")
        header_size = len(BACKUP_MAGIC) + 16 + 12
        if len(backup) < header_size + 16 or not backup.startswith(BACKUP_MAGIC):
            raise ValueError("El archivo no es un respaldo DShalev reconocido.")
        offset = len(BACKUP_MAGIC)
        salt, nonce = backup[offset:offset + 16], backup[offset + 16:offset + 28]
        encryption_key = hashlib.pbkdf2_hmac("sha256", password, salt, BACKUP_KDF_ROUNDS, dklen=32)
        try:
            payload = AESGCM(encryption_key).decrypt(nonce, backup[header_size:], BACKUP_MAGIC)
        except Exception as exc:
            raise ValueError("Contraseña incorrecta o respaldo dañado.") from exc
        prefix_len = len(BACKUP_PLAIN_MAGIC)
        if len(payload) < prefix_len + 40 or not payload.startswith(BACKUP_PLAIN_MAGIC):
            raise ValueError("El contenido del respaldo no es válido.")
        restored_key = payload[prefix_len:prefix_len + 32]
        db_length = int.from_bytes(payload[prefix_len + 32:prefix_len + 40], "big")
        db_start, db_end = prefix_len + 40, prefix_len + 40 + db_length
        raw_db = payload[db_start:db_end]
        if len(restored_key) != 32 or len(raw_db) != db_length or not raw_db.startswith(DB_MAGIC):
            raise ValueError("El respaldo está incompleto o no es válido.")
        assets: dict[str, bytes] = {}
        cursor = db_end
        if cursor < len(payload):
            if len(payload) < cursor + 2:
                raise ValueError("El manifiesto de archivos del respaldo está truncado.")
            asset_count = int.from_bytes(payload[cursor:cursor + 2], "big")
            cursor += 2
            if asset_count > 10_000:
                raise ValueError("El respaldo contiene demasiados archivos.")
            for _ in range(asset_count):
                if len(payload) < cursor + 2:
                    raise ValueError("El manifiesto de archivos del respaldo está truncado.")
                name_length = int.from_bytes(payload[cursor:cursor + 2], "big")
                cursor += 2
                if not 1 <= name_length <= 256 or len(payload) < cursor + name_length + 8:
                    raise ValueError("Una ruta del respaldo no es válida.")
                try:
                    asset_name = payload[cursor:cursor + name_length].decode("ascii")
                except UnicodeDecodeError as exc:
                    raise ValueError("Una ruta del respaldo no es válida.") from exc
                cursor += name_length
                asset_length = int.from_bytes(payload[cursor:cursor + 8], "big")
                cursor += 8
                if asset_length > 300 * 1024 * 1024 or len(payload) < cursor + asset_length:
                    raise ValueError("Un archivo del respaldo está truncado o supera el máximo.")
                if asset_name == "updates/shalev.apk":
                    pass
                elif asset_name.startswith("catalog_photos/") and asset_name.endswith(".jpg"):
                    try:
                        if asset_name != f"catalog_photos/{uuidlib.UUID(asset_name.removeprefix('catalog_photos/').removesuffix('.jpg'))}.jpg":
                            raise ValueError("ruta")
                    except ValueError as exc:
                        raise ValueError("Una ruta de foto del respaldo no es válida.") from exc
                else:
                    raise ValueError("El respaldo contiene una ruta de archivo no permitida.")
                if asset_name in assets:
                    raise ValueError("El respaldo contiene nombres de archivo duplicados.")
                assets[asset_name] = payload[cursor:cursor + asset_length]
                cursor += asset_length
            if cursor != len(payload):
                raise ValueError("El respaldo contiene datos adicionales no reconocidos.")
        nonce_db = raw_db[len(DB_MAGIC):len(DB_MAGIC) + 12]
        source: sqlite3.Connection | None = None
        try:
            clear = AESGCM(restored_key).decrypt(nonce_db, raw_db[len(DB_MAGIC) + 12:], DB_AAD)
            source = sqlite3.connect(":memory:", isolation_level=None)
            source.row_factory = sqlite3.Row
            source.deserialize(clear)
            integrity = source.execute("PRAGMA integrity_check").fetchone()[0]
            tables = {row[0] for row in source.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            if integrity != "ok" or not {"users", "items", "sales"}.issubset(tables):
                raise ValueError("La base del respaldo no pasó la verificación de integridad.")
            if "catalog_photos" in tables:
                for photo in source.execute("SELECT filename,byte_length,sha256 FROM catalog_photos"):
                    asset = assets.get(f"catalog_photos/{photo['filename']}")
                    if asset is None or len(asset) != photo["byte_length"] or hashlib.sha256(asset).hexdigest() != photo["sha256"]:
                        raise ValueError("Falta una foto o su hash no coincide con el catálogo del respaldo.")
                    if len(asset) < 4 or not asset.startswith(b"\xff\xd8") or not asset.endswith(b"\xff\xd9"):
                        raise ValueError("Una foto del respaldo no es JPEG válido.")
            if "app_meta" in tables:
                meta = {row["key"]: row["value"] for row in source.execute("SELECT key,value FROM app_meta WHERE key IN ('apk_version_code','apk_sha256','apk_bytes')")}
                if meta.get("apk_version_code"):
                    apk = assets.get("updates/shalev.apk")
                    if apk is None or len(apk) != int(meta.get("apk_bytes", 0)) or hashlib.sha256(apk).hexdigest() != meta.get("apk_sha256"):
                        raise ValueError("El APK y sus metadatos no coinciden en el respaldo.")
        except ValueError:
            if source is not None:
                source.close()
            raise
        except Exception as exc:
            if source is not None:
                source.close()
            raise ValueError("No se pudo verificar la base cifrada del respaldo.") from exc

        if source is None:
            raise ValueError("No se pudo abrir la base de datos del respaldo.")
        staged_assets: list[tuple[Path, Path, bytes | None]] = []
        for name, data in assets.items():
            target = self.path.parent / name
            target.parent.mkdir(parents=True, exist_ok=True)
            temporary = target.with_name(target.name + ".restore-" + secrets.token_hex(6))
            temporary.write_bytes(data)
            try:
                os.chmod(temporary, 0o600)
            except OSError:
                pass
            staged_assets.append((temporary, target, target.read_bytes() if target.is_file() else None))
        with self._lock:
            old_clear = self._anchor.serialize()
            old_db = self.path.read_bytes() if self.path.exists() else None
            old_key_file = self.key_path.read_bytes() if self.key_path.exists() else None
            old_key = self._key
            try:
                source.backup(self._anchor)
                self._key = restored_key
                stored_key = KEY_MAGIC_DPAPI + _windows_protect(restored_key, decrypt=False) if os.name == "nt" else KEY_MAGIC_DEV + restored_key
                key_tmp = self.key_path.with_suffix(self.key_path.suffix + ".restore-tmp")
                key_tmp.write_bytes(stored_key)
                try:
                    os.chmod(key_tmp, 0o600)
                except OSError:
                    pass
                key_tmp.replace(self.key_path)
                for temporary, target, _old_data in staged_assets:
                    temporary.replace(target)
                self.initialize()
                self._persist()
            except Exception:
                self._anchor.deserialize(old_clear)
                self._key = old_key
                if old_db is not None:
                    self.path.write_bytes(old_db)
                if old_key_file is not None:
                    self.key_path.write_bytes(old_key_file)
                else:
                    self.key_path.unlink(missing_ok=True)
                for temporary, target, old_data in staged_assets:
                    if old_data is None:
                        target.unlink(missing_ok=True)
                    else:
                        rollback_tmp = target.with_name(target.name + ".rollback-" + secrets.token_hex(6))
                        rollback_tmp.write_bytes(old_data)
                        rollback_tmp.replace(target)
                    temporary.unlink(missing_ok=True)
                raise
            finally:
                source.close()
                for temporary, _target, _old_data in staged_assets:
                    temporary.unlink(missing_ok=True)

    def initialize(self) -> None:
        with self.connect() as conn:
            conn.executescript(SCHEMA)
            conn.execute("BEGIN IMMEDIATE")
            try:
                conn.execute("INSERT OR IGNORE INTO app_meta(key,value) VALUES ('schema_version','1')")
                conn.execute("INSERT OR IGNORE INTO app_meta(key,value) VALUES ('business_name','Mi negocio')")
                conn.execute("INSERT OR IGNORE INTO app_meta(key,value) VALUES ('secondary_limit','5')")
                conn.execute("INSERT OR IGNORE INTO app_meta(key,value) VALUES ('business_id',?)", (secrets.token_hex(12),))
                conn.execute("INSERT OR IGNORE INTO app_meta(key,value) VALUES ('trial_started_ms',?)", (str(int(time.time() * 1000)),))
                employee_columns = {row[1] for row in conn.execute("PRAGMA table_info(employees)")}
                for name, definition in {
                    "pairing_token": "TEXT", "pairing_expires": "INTEGER", "employee_key": "TEXT",
                    "device_id": "TEXT", "phone": "TEXT",
                }.items():
                    if name not in employee_columns:
                        conn.execute(f"ALTER TABLE employees ADD COLUMN {name} {definition}")
                receipt_columns = {row[1] for row in conn.execute("PRAGMA table_info(sync_receipts)")}
                if "payload_json" not in receipt_columns:
                    conn.execute("ALTER TABLE sync_receipts ADD COLUMN payload_json TEXT NOT NULL DEFAULT '{}'")
                sync_sale_columns = {row[1] for row in conn.execute("PRAGMA table_info(sync_sales)")}
                for name, definition in {
                    "employee_name": "TEXT NOT NULL DEFAULT ''", "cancelled_at": "INTEGER",
                    "cancellation_reason": "TEXT", "cancelled_by": "TEXT",
                }.items():
                    if name not in sync_sale_columns:
                        conn.execute(f"ALTER TABLE sync_sales ADD COLUMN {name} {definition}")
                sales_columns = {row[1] for row in conn.execute("PRAGMA table_info(sales)")}
                for name, definition in {
                    "customer_id": "INTEGER REFERENCES customers(id)",
                    "user_id": "INTEGER REFERENCES users(id)",
                    "request_key": "TEXT", "request_hash": "TEXT NOT NULL DEFAULT ''",
                    "inventory_logged": "INTEGER NOT NULL DEFAULT 0",
                    "cancelled_at": "TEXT", "cancellation_reason": "TEXT",
                    "cancelled_by": "INTEGER REFERENCES users(id)",
                    "correction_of_id": "INTEGER REFERENCES sales(id)",
                }.items():
                    if name not in sales_columns:
                        conn.execute(f"ALTER TABLE sales ADD COLUMN {name} {definition}")
                command_columns = {row[1] for row in conn.execute("PRAGMA table_info(sync_commands)")}
                if "request_hash" not in command_columns:
                    conn.execute("ALTER TABLE sync_commands ADD COLUMN request_hash TEXT NOT NULL DEFAULT ''")

                item_columns = {row[1] for row in conn.execute("PRAGMA table_info(items)")}
                added_fixed_point = "stock_milli" not in item_columns
                for name, definition in {
                    "stock_milli": "INTEGER NOT NULL DEFAULT 0 CHECK (stock_milli BETWEEN -9000000000000000000 AND 9000000000000000000)",
                    "low_stock_milli": "INTEGER NOT NULL DEFAULT 5000 CHECK (low_stock_milli >= 0)",
                    "critical_stock_milli": "INTEGER NOT NULL DEFAULT 1000 CHECK (critical_stock_milli >= 0)",
                }.items():
                    if name not in item_columns:
                        conn.execute(f"ALTER TABLE items ADD COLUMN {name} {definition}")
                if added_fixed_point:
                    from .inventory import format_milli, migrate_legacy_quantity

                    for item in conn.execute("SELECT id,name,category,unit,stock,low_stock,critical_stock FROM items ORDER BY id").fetchall():
                        try:
                            stock_milli = migrate_legacy_quantity(item["stock"])
                            low_milli = migrate_legacy_quantity(item["low_stock"])
                            critical_milli = migrate_legacy_quantity(item["critical_stock"])
                        except ValueError as exc:
                            raise RuntimeError(f"No se pudo migrar el stock de {item['name']!r}; conserva el respaldo original.") from exc
                        if low_milli < 0 or critical_milli < 0:
                            raise RuntimeError(f"Los niveles de stock de {item['name']!r} son negativos; corrígelos antes de migrar.")
                        conn.execute("UPDATE items SET stock_milli=?,low_stock_milli=?,critical_stock_milli=? WHERE id=?",
                                     (stock_milli, low_milli, critical_milli, item["id"]))
                        conn.execute("""INSERT INTO inventory_movements(
                            item_id,item_name,item_category,item_unit,movement_type,delta_milli,stock_after_milli,
                            actor_type,actor_id,actor,reason,source,reference
                        ) VALUES(?,?,?,?, 'MIGRACION',?,?, 'sistema',NULL,'Sistema',?,?,?)""",
                                     (item["id"], item["name"], item["category"], item["unit"], stock_milli,
                                      stock_milli, f"Saldo heredado de SQLite ({item['stock']!r}) normalizado a {format_milli(stock_milli)}; el libro no contiene movimientos anteriores.",
                                      "migracion", f"migracion-stock-v9:{item['id']}"))

                conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sales_request_key ON sales(request_key) WHERE request_key IS NOT NULL")
                for trigger_sql in (
                    """CREATE TRIGGER IF NOT EXISTS items_legacy_quantity_insert
                        AFTER INSERT ON items BEGIN
                            UPDATE items SET stock_milli=CAST(ROUND(NEW.stock*1000) AS INTEGER),
                                low_stock_milli=CAST(ROUND(NEW.low_stock*1000) AS INTEGER),
                                critical_stock_milli=CAST(ROUND(NEW.critical_stock*1000) AS INTEGER)
                            WHERE id=NEW.id;
                        END""",
                    """CREATE TRIGGER IF NOT EXISTS items_legacy_quantity_update
                        AFTER UPDATE OF stock,low_stock,critical_stock ON items BEGIN
                            UPDATE items SET stock_milli=CAST(ROUND(NEW.stock*1000) AS INTEGER),
                                low_stock_milli=CAST(ROUND(NEW.low_stock*1000) AS INTEGER),
                                critical_stock_milli=CAST(ROUND(NEW.critical_stock*1000) AS INTEGER)
                            WHERE id=NEW.id;
                        END""",
                    """CREATE TRIGGER IF NOT EXISTS items_fixed_quantity_update
                        AFTER UPDATE OF stock_milli,low_stock_milli,critical_stock_milli ON items BEGIN
                            UPDATE items SET stock=NEW.stock_milli/1000.0,
                                low_stock=NEW.low_stock_milli/1000.0,
                                critical_stock=NEW.critical_stock_milli/1000.0
                            WHERE id=NEW.id;
                        END""",
                    """CREATE TRIGGER IF NOT EXISTS inventory_movements_no_update
                        BEFORE UPDATE ON inventory_movements BEGIN
                            SELECT RAISE(ABORT,'inventory movements are append-only');
                        END""",
                    """CREATE TRIGGER IF NOT EXISTS inventory_movements_no_delete
                        BEFORE DELETE ON inventory_movements BEGIN
                            SELECT RAISE(ABORT,'inventory movements are append-only');
                        END""",
                ):
                    conn.execute(trigger_sql)
                conn.execute("UPDATE app_meta SET value='9' WHERE key='schema_version'")
                conn.commit()
            except Exception:
                conn.rollback()
                raise

    def connect(self) -> sqlite3.Connection:
        self._lock.acquire()
        try:
            conn = sqlite3.connect(self._uri, uri=True, timeout=10, isolation_level=None,
                                   check_same_thread=False, factory=_ClosingConnection)
            conn.row_factory = sqlite3.Row
            conn.execute("PRAGMA foreign_keys = ON")
            conn.execute("PRAGMA busy_timeout = 10000")
            conn._release_lock = self._lock.release
            conn._on_close = self._persist
            conn._initial_changes = conn.total_changes
            return conn
        except Exception:
            self._lock.release()
            raise

    @contextmanager
    def transaction(self, *, immediate: bool = True) -> Iterator[sqlite3.Connection]:
        conn = self.connect()
        try:
            conn.execute("BEGIN IMMEDIATE" if immediate else "BEGIN")
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def has_admin(self) -> bool:
        with self.connect() as conn:
            return conn.execute("SELECT 1 FROM users WHERE role='admin' AND active=1 LIMIT 1").fetchone() is not None

    def close(self) -> None:
        with self._lock:
            self._anchor.close()
            self._key = b"\0" * len(self._key)
