from __future__ import annotations

import ctypes
import os
import secrets
import sqlite3
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
    stock INTEGER NOT NULL DEFAULT 0,
    sale_price_cents INTEGER NOT NULL DEFAULT 0 CHECK (sale_price_cents >= 0),
    cost_cents INTEGER NOT NULL DEFAULT 0 CHECK (cost_cents >= 0),
    low_stock INTEGER NOT NULL DEFAULT 5,
    critical_stock INTEGER NOT NULL DEFAULT 1,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);
CREATE TABLE IF NOT EXISTS shifts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    opened_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    closed_at TEXT,
    opening_cash_cents INTEGER NOT NULL DEFAULT 0,
    counted_cash_cents INTEGER
);
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
CREATE TABLE IF NOT EXISTS sales (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shift_id INTEGER NOT NULL REFERENCES shifts(id),
    total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
    payment_method TEXT NOT NULL CHECK (payment_method IN ('EFECTIVO','TRANSFERENCIA')),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    employee_id INTEGER REFERENCES employees(id),
    note TEXT NOT NULL DEFAULT ''
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
CREATE TABLE IF NOT EXISTS sync_sales (
    uuid TEXT PRIMARY KEY,
    employee_id INTEGER NOT NULL,
    employee_name TEXT NOT NULL DEFAULT '',
    shift_uuid TEXT NOT NULL,
    sale_at_ms INTEGER NOT NULL,
    payment_method TEXT NOT NULL,
    total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
    payload_json TEXT NOT NULL,
    received_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
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

    def initialize(self) -> None:
        with self.connect() as conn:
            conn.executescript(SCHEMA)
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
            if "employee_name" not in sync_sale_columns:
                conn.execute("ALTER TABLE sync_sales ADD COLUMN employee_name TEXT NOT NULL DEFAULT ''")
            command_columns = {row[1] for row in conn.execute("PRAGMA table_info(sync_commands)")}
            if "request_hash" not in command_columns:
                conn.execute("ALTER TABLE sync_commands ADD COLUMN request_hash TEXT NOT NULL DEFAULT ''")
            conn.execute("UPDATE app_meta SET value='4' WHERE key='schema_version'")

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
