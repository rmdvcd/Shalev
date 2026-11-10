from __future__ import annotations

import base64
import hashlib
import hmac
import json
import logging
import re
import socketserver
import sqlite3
import time
import uuid as uuidlib
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from .database import Database
from .inventory import MAX_QUANTITY_MILLI, record_movement
from .sync_protocol import (
    EncryptedChannel,
    MAX_GREETING,
    VERSION,
    canonical_json,
    derive_employee_key,
    pairing_mac_primary,
    pairing_mac_secondary,
    read_json_frame,
    reject_mac,
    send_json_frame,
)

LOG = logging.getLogger("dshalev.sync")
DEVICE_ID_RE = re.compile(r"^[A-Za-z0-9:_-]{8,128}$")
TRIAL_MS = 7 * 24 * 60 * 60 * 1000


class RemoteCommandError(Exception):
    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _epoch_ms(text: str | None) -> int:
    if not text:
        return 0
    try:
        value = datetime.fromisoformat(text.replace("Z", "+00:00"))
        return int(value.timestamp() * 1000)
    except ValueError:
        return 0


def _read_meta(conn, key: str, default: str = "") -> str:
    row = conn.execute("SELECT value FROM app_meta WHERE key=?", (key,)).fetchone()
    return row["value"] if row else default


def _trial_license(conn, now_ms: int) -> dict[str, Any]:
    started = int(_read_meta(conn, "trial_started_ms", str(now_ms)))
    allowed = int(_read_meta(conn, "secondary_limit", "5"))
    expires = started + TRIAL_MS
    if now_ms < expires:
        return {"clase": "PRUEBA", "venceEn": expires, "permitidas": allowed}
    return {"clase": "BLOQUEADA"}


def _product_snapshot(row) -> dict[str, Any]:
    return {
        "id": row["id"], "categoria": row["category"], "nombre": row["name"], "precioCostoCent": row["cost_cents"],
        "precioVentaCent": row["sale_price_cents"], "cantidad": int(row["stock_milli"]) // 1000,
        "nivelBajo": int(row["low_stock_milli"]) // 1000, "nivelCritico": int(row["critical_stock_milli"]) // 1000,
        "creadoEn": _epoch_ms(row["created_at"]),
        "actualizadoEn": _epoch_ms(row["created_at"]), "eliminado": False, "local": "Local",
    }


def snapshot(db: Database, employee_id: int) -> dict[str, Any]:
    now_ms = int(time.time() * 1000)
    with db.connect() as conn:
        employee = conn.execute("SELECT * FROM employees WHERE id=? AND active=1", (employee_id,)).fetchone()
        if not employee:
            raise ValueError("empleado no disponible")
        name = _read_meta(conn, "business_name", "Mi negocio")
        limit = int(_read_meta(conn, "secondary_limit", "5"))
        products = conn.execute("SELECT * FROM items WHERE active=1 AND category NOT IN ('Insumo','Servicio') ORDER BY id").fetchall()
        ingredients = conn.execute("SELECT * FROM items WHERE active=1 AND category='Insumo' ORDER BY id").fetchall()
        service_rows = conn.execute("SELECT * FROM sync_services WHERE deleted=0 ORDER BY service_id").fetchall()
        preset_rows = conn.execute("SELECT * FROM presets WHERE active=1 ORDER BY id").fetchall()
        photo_rows = conn.execute("SELECT entity,entity_id,filename,byte_length FROM catalog_photos ORDER BY entity,entity_id").fetchall()
        recipe_rows = conn.execute("SELECT product_id,ingredient_id,quantity_milli FROM recipes ORDER BY product_id,ingredient_id").fetchall()
        recipes_by_product: dict[int, list[dict[str, int]]] = {}
        for recipe in recipe_rows:
            recipes_by_product.setdefault(recipe["product_id"], []).append({"insumoId": recipe["ingredient_id"], "cantidadMil": recipe["quantity_milli"]})
        product_ids = {row["id"] for row in products}
        recipes = [{"productoId": product_id, "lineas": lines} for product_id, lines in recipes_by_product.items() if product_id in product_ids]
        service_ids = {row["service_id"] for row in service_rows}
        photo_entities = {"producto": product_ids, "servicio": service_ids}
        photos = [{"entidad": row["entity"], "id": row["entity_id"], "nombre": row["filename"], "bytes": row["byte_length"]}
                  for row in photo_rows if row["entity_id"] in photo_entities.get(row["entity"], set())]
        services = []
        for row in service_rows:
            try:
                components = json.loads(row["ingredients_json"] or "[]")
                if not isinstance(components, list):
                    components = []
            except json.JSONDecodeError:
                components = []
            services.append({
                "id": row["service_id"], "nombre": row["name"], "tipo": row["service_type"],
                "importeCent": row["price_cents"], "descripcion": row["description"] or None,
                "fotoUri": row["photo_uri"], "creadoEn": row["created_ms"],
                "actualizadoEn": row["updated_ms"], "eliminado": False,
                "insumos": components, "local": row["local_name"] or "Local",
            })
        presets = []
        for row in preset_rows:
            try:
                product_ids = json.loads(row["product_ids_json"] or "[]")
                service_ids = json.loads(row["service_ids_json"] or "[]")
                if not isinstance(product_ids, list) or not isinstance(service_ids, list):
                    raise ValueError("preset")
            except (json.JSONDecodeError, ValueError):
                product_ids, service_ids = [], []
            presets.append({"id": row["id"], "nombre": row["name"], "puntosBasicos": row["basis_points"],
                           "productoIds": product_ids, "servicioIds": service_ids, "metodoPago": row["payment_method"],
                           "importeMinimoCent": row["minimum_cents"], "activo": True})
        business_preferences = {
            "productoBajo": 5, "productoCritico": 1, "insumoBajoMil": 5000,
            "insumoCriticoMil": 1000, "modulos": None, "empleadosPrevistos": limit,
        }
        employee_permissions = json.loads(employee["permissions_json"] or "[]")
        trial = _trial_license(conn, now_ms)
        return {
            "nombreNegocio": name,
            "empleado": {"nombre": employee["name"], "permisos": sorted(employee_permissions)},
            "licencia": trial,
            "productos": [_product_snapshot(row) for row in products],
            "recetas": recipes,
            "insumos": [
                {"id": row["id"], "nombre": row["name"], "unidad": row["unit"], "precioCent": row["cost_cents"],
                 "cantidadMil": int(row["stock_milli"]), "nivelBajoMil": int(row["low_stock_milli"]),
                 "nivelCriticoMil": int(row["critical_stock_milli"]), "creadoEn": _epoch_ms(row["created_at"]),
                 "actualizadoEn": _epoch_ms(row["created_at"]), "precioVentaCent": row["sale_price_cents"], "local": "Local"}
                for row in ingredients
            ],
            "servicios": services, "preajustes": presets, "perfil": None, "preferencias": business_preferences,
            "fotos": photos, "local": "Local",
        }


def _catalog_hash(snapshot_data: dict[str, Any]) -> str:
    return hashlib.sha256(canonical_json(snapshot_data)).hexdigest()


class _SyncHandler(socketserver.StreamRequestHandler):
    server: "ShalevSyncServer"

    def handle(self) -> None:
        self.connection.settimeout(90)
        try:
            greeting = read_json_frame(self.rfile, limit=MAX_GREETING)
            if greeting.get("t") == "vincular":
                self._pair(greeting)
            elif greeting.get("t") == "hola":
                self._session(greeting)
        except (OSError, EOFError, ValueError, KeyError, TypeError):
            pass
        except Exception as exc:
            LOG.warning("Sync session closed (%s)", type(exc).__name__)

    def _reject(self, reason: str, mac: str | None = None) -> None:
        body: dict[str, Any] = {"t": "rechazo", "motivo": reason}
        if mac is not None:
            body["mac"] = mac
        send_json_frame(self.wfile, body, limit=MAX_GREETING)

    def _pair(self, message: dict[str, Any]) -> None:
        if message.get("v") != VERSION:
            return self._reject("version")
        try:
            business_id = str(message["negocio"])
            employee_id = int(message["empleado"])
            public_secondary = base64.b64decode(message["pub"], validate=True)
            mac = base64.b64decode(message["mac"], validate=True)
        except (KeyError, ValueError, TypeError):
            return self._reject("codigo_invalido")
        with self.server.database.connect() as conn:
            actual_business = _read_meta(conn, "business_id")
            employee = conn.execute("SELECT * FROM employees WHERE id=? AND active=1", (employee_id,)).fetchone()
            if business_id != actual_business:
                return self._reject("otro_negocio")
            if not employee or not employee["pairing_token"]:
                return self._reject("codigo_invalido")
            token = base64.b64decode(employee["pairing_token"], validate=True)
            if int(employee["pairing_expires"] or 0) <= int(time.time() * 1000):
                return self._reject("codigo_vencido")
            if not hmac.compare_digest(pairing_mac_secondary(token, public_secondary), mac):
                return self._reject("codigo_invalido")
            private = self.server.new_ecdh_key()
            public_primary = private.public_key().public_bytes(
                encoding=self.server.encoding_der, format=self.server.public_format_spki,
            )
            try:
                employee_key, _shared = derive_employee_key(private, public_secondary, token)
            except Exception:
                return self._reject("codigo_invalido")
            conn.execute("UPDATE employees SET employee_key=?,pairing_token=NULL,pairing_expires=NULL,linked_at=?,device_id=? WHERE id=?",
                         (base64.b64encode(employee_key).decode("ascii"), datetime.now(timezone.utc).isoformat(),
                          message.get("device") if isinstance(message.get("device"), str) and DEVICE_ID_RE.fullmatch(message["device"]) else None,
                          employee_id))
            response = {
                "t": "vincular_ok", "pub": base64.b64encode(public_primary).decode("ascii"),
                "mac": base64.b64encode(pairing_mac_primary(token, public_primary, public_secondary)).decode("ascii"),
                "nombreNegocio": _read_meta(conn, "business_name", "Mi negocio"),
            }
        send_json_frame(self.wfile, response, limit=MAX_GREETING)

    def _session(self, hello: dict[str, Any]) -> None:
        if hello.get("v") != VERSION:
            return self._reject("version")
        try:
            business_id = str(hello["negocio"])
            employee_id = int(hello["empleado"])
            nonce_secondary = base64.b64decode(hello["nonce"], validate=True)
        except (KeyError, ValueError, TypeError):
            return self._reject("desconocida")
        if len(nonce_secondary) != 16:
            return self._reject("desconocida")
        with self.server.database.connect() as conn:
            if business_id != _read_meta(conn, "business_id"):
                return self._reject("otro_negocio")
            employee = conn.execute("SELECT * FROM employees WHERE id=?", (employee_id,)).fetchone()
            if not employee:
                revoked = conn.execute("SELECT employee_key FROM retired_employees WHERE employee_id=?", (employee_id,)).fetchone()
                if revoked:
                    key = base64.b64decode(revoked["employee_key"], validate=True)
                    return self._reject("quitada", base64.b64encode(reject_mac(key, nonce_secondary, "quitada")).decode("ascii"))
                return self._reject("desconocida")
            if not employee["employee_key"]:
                return self._reject("desconocida")
            key = base64.b64decode(employee["employee_key"], validate=True)
            if not employee["active"]:
                return self._reject("quitada", base64.b64encode(reject_mac(key, nonce_secondary, "quitada")).decode("ascii"))
        nonce_primary = self.server.random_bytes(16)
        send_json_frame(self.wfile, {"t": "hola_ok", "nonce": base64.b64encode(nonce_primary).decode("ascii"), "comandosUnicos": True}, limit=MAX_GREETING)
        channel = EncryptedChannel.primary(key, nonce_secondary, nonce_primary)
        message = channel.receive(self.rfile)
        with self.server.database.transaction() as conn:
            device = hello.get("device")
            if isinstance(device, str) and DEVICE_ID_RE.fullmatch(device):
                conn.execute("UPDATE employees SET device_id=?,last_seen=? WHERE id=?", (device, datetime.now(timezone.utc).isoformat(), employee_id))
            else:
                conn.execute("UPDATE employees SET last_seen=? WHERE id=?", (datetime.now(timezone.utc).isoformat(), employee_id))
        while True:
            if not self.server.employee_is_active(employee_id):
                channel.send(self.wfile, {"t": "quitada"})
                return
            kind = message.get("t")
            if kind == "sync":
                response = self.server.handle_sync(employee_id, message)
                channel.send(self.wfile, response)
            elif kind == "ping":
                channel.send(self.wfile, {"t": "pong", "id": int(message.get("id", 0))})
            elif kind == "cmd":
                channel.send(self.wfile, self.server.handle_command(employee_id, message))
            elif kind == "foto_pedir":
                channel.send(self.wfile, self.server.photo_block(message))
            elif kind == "apk_pedir":
                channel.send(self.wfile, self.server.apk_block(message))
            else:
                # La versión 1 tolera mensajes opcionales desconocidos.
                pass
            message = channel.receive(self.rfile)


class ShalevSyncServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    request_queue_size = 16

    encoding_der = serialization.Encoding.DER
    public_format_spki = serialization.PublicFormat.SubjectPublicKeyInfo

    def __init__(self, database: Database, *, host: str, first_port: int = 47811, port_count: int = 10):
        self.database = database
        import threading
        self._connection_limit = 16
        self._semaphore = threading.BoundedSemaphore(self._connection_limit)
        bound_error: OSError | None = None
        for port in range(first_port, min(first_port + port_count, 65536)):
            try:
                super().__init__((host, port), _SyncHandler, bind_and_activate=True)
                self.sync_port = port
                break
            except OSError as exc:
                bound_error = exc
                failed_socket = getattr(self, "socket", None)
                if failed_socket is not None:
                    failed_socket.close()
        else:
            raise OSError("No se pudo reservar un puerto de sincronización Shalev.") from bound_error
        self._thread = None

    def process_request(self, request, client_address):
        if not self._semaphore.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self._semaphore.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self._semaphore.release()

    @staticmethod
    def new_ecdh_key():
        return ec.generate_private_key(ec.SECP256R1())

    @staticmethod
    def random_bytes(count: int) -> bytes:
        import secrets
        return secrets.token_bytes(count)

    def start(self) -> None:
        import threading
        self._thread = threading.Thread(target=self.serve_forever, name="DShalev-SPVI", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self.shutdown()
        self.server_close()
        if self._thread:
            self._thread.join(timeout=2)

    @staticmethod
    def _block_data(path: Path, offset: int, total: int, *, sha256: str | None = None) -> str:
        with path.open("rb") as stream:
            stream.seek(offset)
            return base64.b64encode(stream.read(256 * 1024)).decode("ascii")

    def photo_block(self, message: dict[str, Any]) -> dict[str, Any]:
        request_id, offset = 0, -1
        try:
            request_id = int(message.get("id", 0))
            offset = int(message.get("desde", -1))
            name = str(message.get("nombre", ""))
            parsed = uuidlib.UUID(name.removesuffix(".jpg"))
            if name != f"{parsed}.jpg":
                raise ValueError("nombre")
        except (ValueError, TypeError, AttributeError):
            return {"t": "foto_bloque", "id": request_id, "desde": 0, "datos": "", "total": 0, "error": "no_encontrado"}
        with self.database.connect() as conn:
            photo = conn.execute("SELECT byte_length,sha256 FROM catalog_photos WHERE filename=?", (name,)).fetchone()
        path = self.database.path.parent / "catalog_photos" / name
        if not photo or not path.is_file() or path.stat().st_size != photo["byte_length"] or not 0 <= offset < photo["byte_length"]:
            return {"t": "foto_bloque", "id": request_id, "desde": max(offset, 0), "datos": "", "total": int(photo["byte_length"]) if photo else 0, "error": "no_encontrado"}
        return {"t": "foto_bloque", "id": request_id, "desde": offset,
                "datos": self._block_data(path, offset, photo["byte_length"]), "total": photo["byte_length"],
                "sha256": photo["sha256"], "error": None}

    def _apk_metadata(self) -> dict[str, Any] | None:
        with self.database.connect() as conn:
            values = {row["key"]: row["value"] for row in conn.execute("SELECT key,value FROM app_meta WHERE key IN ('apk_version_code','apk_sha256','apk_bytes')")}
        try:
            code, size = int(values.get("apk_version_code", "0")), int(values.get("apk_bytes", "0"))
            sha = values.get("apk_sha256", "")
        except (TypeError, ValueError):
            return None
        path = self.database.path.parent / "updates" / "shalev.apk"
        if code <= 0 or size <= 0 or not re.fullmatch(r"[0-9a-f]{64}", sha) or not path.is_file() or path.stat().st_size != size:
            return None
        return {"nombre": "shalev.apk", "code": code, "sha256": sha, "bytes": size, "path": path}

    def apk_block(self, message: dict[str, Any]) -> dict[str, Any]:
        try:
            request_id = int(message.get("id", 0))
            offset = int(message.get("desde", -1))
            requested_sha = str(message.get("sha256", ""))
        except (ValueError, TypeError):
            request_id, offset, requested_sha = 0, -1, ""
        metadata = self._apk_metadata()
        if not metadata or requested_sha != metadata["sha256"] or not 0 <= offset < metadata["bytes"]:
            return {"t": "apk_bloque", "id": request_id, "desde": max(offset, 0), "datos": "", "total": metadata["bytes"] if metadata else 0, "error": "no_encontrado"}
        return {"t": "apk_bloque", "id": request_id, "desde": offset,
                "datos": self._block_data(metadata["path"], offset, metadata["bytes"]),
                "total": metadata["bytes"], "error": None}

    def employee_is_active(self, employee_id: int) -> bool:
        with self.database.connect() as conn:
            row = conn.execute("SELECT active FROM employees WHERE id=?", (employee_id,)).fetchone()
        return bool(row and row["active"])

    @staticmethod
    def _store_sale(conn, employee_id: int, wrapper: dict[str, Any], uuid: str) -> None:
        sale = wrapper.get("venta") if isinstance(wrapper.get("venta"), dict) else {}
        details = sale.get("detalles", []) if isinstance(sale.get("detalles"), list) else []
        total = 0
        for detail in details[:5000]:
            if not isinstance(detail, dict):
                continue
            try:
                quantity = int(detail.get("cantidad", 0))
                price = int(detail.get("precioUnitarioCent", 0))
                if quantity > 0 and price >= 0:
                    total += quantity * price
            except (TypeError, ValueError):
                continue
            if total > 2**63 - 1:
                raise ValueError("venta demasiado grande")
        try:
            sale_at_ms = int(sale.get("fecha", int(time.time() * 1000)))
        except (TypeError, ValueError):
            sale_at_ms = int(time.time() * 1000)
        method = str(sale.get("metodoPago", "OTRO"))[:32]
        shift_uuid = str(wrapper.get("turnoUuid", ""))[:64]
        employee = conn.execute("SELECT name FROM employees WHERE id=?", (employee_id,)).fetchone()
        employee_name = employee["name"] if employee else "Empleado"
        inserted = conn.execute("INSERT OR IGNORE INTO sync_sales(uuid,employee_id,employee_name,shift_uuid,sale_at_ms,payment_method,total_cents,payload_json) VALUES(?,?,?,?,?,?,?,?)",
                     (uuid, employee_id, employee_name, shift_uuid, sale_at_ms, method, total, canonical_json(wrapper).decode("utf-8"))).rowcount
        if inserted != 1:
            return
        for movement in wrapper.get("movimientos", [])[:5000] if isinstance(wrapper.get("movimientos"), list) else []:
            if not isinstance(movement, dict):
                continue
            try:
                item_id = int(movement["entidadId"])
                delta = int(movement["delta"])
                entity = str(movement["entidad"]).upper()
            except (KeyError, TypeError, ValueError):
                continue
            if not 1 <= item_id <= 2**63 - 1 or abs(delta) > 10**12:
                continue
            if entity == "INSUMO":
                delta_milli = delta
                category_clause = "category='Insumo'"
            elif entity == "PRODUCTO":
                delta_milli = delta * 1000
                category_clause = "category!='Insumo'"
            else:
                continue
            if abs(delta_milli) > MAX_QUANTITY_MILLI:
                continue
            updated = conn.execute(f"UPDATE items SET stock_milli=stock_milli+? WHERE id=? AND {category_clause} AND active=1",
                                   (delta_milli, item_id)).rowcount
            if updated == 1:
                record_movement(conn, item_id, delta_milli, movement_type="VENTA_MOVIL",
                                actor=employee_name, actor_type="empleado", actor_id=employee_id,
                                reason=f"Venta móvil {uuid}", source="sincronizacion", reference=f"mobile-sale:{uuid}")

    @staticmethod
    def _store_shift(conn, employee_id: int, uuid: str, wrapper: dict[str, Any]) -> None:
        turn = wrapper.get("turno", {})
        if not isinstance(turn, dict):
            return
        try:
            opened_ms = max(0, int(turn.get("abiertoEn", 0) or 0))
            closed_ms = int(turn["cerradoEn"]) if turn.get("cerradoEn") is not None else None
            opening_cash = int(turn["fondoCent"]) if turn.get("fondoCent") is not None else None
            counted_cash = int(turn["contadoCent"]) if turn.get("contadoCent") is not None else None
        except (TypeError, ValueError):
            return
        count = turn.get("conteoCierre")
        count_json = canonical_json(count).decode("utf-8") if isinstance(count, dict) else None
        employee = conn.execute("SELECT name FROM employees WHERE id=?", (employee_id,)).fetchone()
        employee_name = employee["name"] if employee else "Empleado"
        conn.execute("""INSERT INTO sync_shifts(employee_id,employee_name,uuid,opened_ms,closed_ms,opening_cash_cents,counted_cash_cents,count_json,payload_json)
            VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(employee_id,uuid) DO UPDATE SET
            employee_name=excluded.employee_name,opened_ms=excluded.opened_ms,closed_ms=COALESCE(excluded.closed_ms,sync_shifts.closed_ms),
            opening_cash_cents=COALESCE(excluded.opening_cash_cents,sync_shifts.opening_cash_cents),
            counted_cash_cents=COALESCE(excluded.counted_cash_cents,sync_shifts.counted_cash_cents),
            count_json=COALESCE(excluded.count_json,sync_shifts.count_json),payload_json=excluded.payload_json,
            updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')""",
            (employee_id, employee_name, uuid, opened_ms, closed_ms, opening_cash, counted_cash, count_json, canonical_json(wrapper).decode("utf-8")))

    @staticmethod
    def _store_cash_movement(conn, employee_id: int, uuid: str, shift_uuid: str, wrapper: dict[str, Any]) -> None:
        movement = wrapper.get("mov", {})
        if not isinstance(movement, dict):
            return
        kind = str(movement.get("tipo", ""))[:32]
        try:
            amount = int(movement.get("importeCent", 0))
            created_ms = max(0, int(movement.get("fecha", 0) or 0))
        except (TypeError, ValueError):
            return
        if amount < 0:
            return
        employee = conn.execute("SELECT name FROM employees WHERE id=?", (employee_id,)).fetchone()
        employee_name = employee["name"] if employee else "Empleado"
        conn.execute("INSERT OR IGNORE INTO sync_cash_movements(uuid,employee_id,employee_name,shift_uuid,movement_type,amount_cents,reason,created_ms,payload_json) VALUES(?,?,?,?,?,?,?,?,?)",
                     (uuid, employee_id, employee_name, shift_uuid, kind, amount, str(movement.get("motivo", ""))[:200], created_ms, canonical_json(wrapper).decode("utf-8")))

    def handle_sync(self, employee_id: int, message: dict[str, Any]) -> dict[str, Any]:
        batch = message.get("lote") if isinstance(message.get("lote"), dict) else {}
        received: dict[str, list[Any]] = {"turnos": [], "ventas": [], "caja": []}
        close_turn = False
        close_rejected = False
        approved_count = None
        approved_shift_uuid = None
        pending_sale_changes: list[dict[str, Any]] = []
        with self.database.transaction() as conn:
            for list_name, receipt_type in (("turnos", "turno"), ("ventas", "venta"), ("caja", "caja")):
                rows = batch.get(list_name, [])
                if not isinstance(rows, list):
                    continue
                for row in rows[:5000]:
                    if not isinstance(row, dict):
                        continue
                    uuid = row.get("uuid")
                    if not isinstance(uuid, str) or len(uuid) > 64:
                        continue
                    try:
                        uuid = str(uuidlib.UUID(uuid))
                        shift_uuid = None
                        if list_name in {"ventas", "caja"}:
                            shift_uuid = str(uuidlib.UUID(str(row.get("turnoUuid", ""))))
                    except (ValueError, AttributeError):
                        continue
                    if list_name == "turnos" and not isinstance(row.get("turno"), dict):
                        continue
                    if list_name == "ventas" and not isinstance(row.get("venta"), dict):
                        continue
                    if list_name == "caja" and not isinstance(row.get("mov"), dict):
                        continue
                    if shift_uuid and not conn.execute("SELECT 1 FROM sync_receipts WHERE uuid=? AND employee_id=? AND message_type='turno'", (shift_uuid, employee_id)).fetchone():
                        continue
                    inserted = conn.execute("INSERT OR IGNORE INTO sync_receipts(uuid,employee_id,message_type,payload_json) VALUES(?,?,?,?)",
                                            (uuid, employee_id, receipt_type, canonical_json(row).decode("utf-8"))).rowcount == 1
                    if not inserted:
                        old = conn.execute("SELECT employee_id,message_type FROM sync_receipts WHERE uuid=?", (uuid,)).fetchone()
                        if not old or old["employee_id"] != employee_id or old["message_type"] != receipt_type:
                            continue
                    if list_name == "turnos":
                        turno = row["turno"]
                        self._store_shift(conn, employee_id, uuid, row)
                        count = turno.get("conteoCierre")
                        if isinstance(count, dict) and count.get("aprobadoEn") is not None:
                            approved = conn.execute("SELECT count_json FROM mobile_count_approvals WHERE employee_id=? AND shift_uuid=?", (employee_id, uuid)).fetchone()
                            try:
                                expected_count = json.loads(approved["count_json"]) if approved else None
                            except json.JSONDecodeError:
                                expected_count = None
                            if expected_count == count:
                                conn.execute("DELETE FROM mobile_count_approvals WHERE employee_id=? AND shift_uuid=?", (employee_id, uuid))
                        received[list_name].append({"uuid": uuid, "cerrado": turno.get("cerradoEn") is not None})
                    else:
                        received[list_name].append(uuid)
                    if list_name == "caja" and inserted and shift_uuid:
                        self._store_cash_movement(conn, employee_id, uuid, shift_uuid, row)
                    if list_name == "ventas" and inserted:
                        self._store_sale(conn, employee_id, row, uuid)
            if message.get("telefono") and isinstance(message["telefono"], str):
                conn.execute("UPDATE employees SET phone=? WHERE id=?", (message["telefono"][:32], employee_id))
            if message.get("solicitaCierre") is True:
                conn.execute("INSERT OR IGNORE INTO mobile_close_requests(employee_id) VALUES(?)", (employee_id,))
            close_request = conn.execute("SELECT decision FROM mobile_close_requests WHERE employee_id=?", (employee_id,)).fetchone()
            if close_request and close_request["decision"] is not None:
                close_turn = close_request["decision"] == 1
                close_rejected = close_request["decision"] == 0
                conn.execute("DELETE FROM mobile_close_requests WHERE employee_id=?", (employee_id,))
            count_approval = conn.execute("SELECT shift_uuid,count_json FROM mobile_count_approvals WHERE employee_id=? ORDER BY approved_at DESC LIMIT 1", (employee_id,)).fetchone()
            if count_approval:
                try:
                    approved_count = json.loads(count_approval["count_json"])
                    approved_shift_uuid = count_approval["shift_uuid"]
                except json.JSONDecodeError:
                    approved_count = None
            acknowledgements = message.get("cambiosAplicados", [])
            if isinstance(acknowledgements, list):
                for acknowledged_uuid in acknowledgements[:5000]:
                    if not isinstance(acknowledged_uuid, str):
                        continue
                    try:
                        acknowledged_uuid = str(uuidlib.UUID(acknowledged_uuid))
                    except ValueError:
                        continue
                    conn.execute("DELETE FROM sync_sale_changes WHERE uuid=? AND employee_id=?", (acknowledged_uuid, employee_id))
            for change in conn.execute("SELECT payload_json FROM sync_sale_changes WHERE employee_id=? ORDER BY created_at LIMIT 500", (employee_id,)):
                try:
                    decoded_change = json.loads(change["payload_json"])
                    if isinstance(decoded_change, dict):
                        pending_sale_changes.append(decoded_change)
                except json.JSONDecodeError:
                    continue
        catalog = snapshot(self.database, employee_id)
        digest = _catalog_hash(catalog)
        same = message.get("hash") == digest
        apk = self._apk_metadata()
        try:
            installed_version = int(message.get("versionCode", 0) or 0)
        except (TypeError, ValueError):
            installed_version = 0
        apk_offer = {key: value for key, value in apk.items() if key != "path"} if apk and apk["code"] > installed_version else None
        return {
            "t": "sync_ok", "id": int(message.get("id", 0)), "recibidos": received, "hash": digest,
            "instantanea": None if same else catalog, "cerrarTurno": close_turn, "cierreRechazado": close_rejected,
            "cambiosVentas": pending_sale_changes, "conteoAprobado": approved_count, "conteoTurnoUuid": approved_shift_uuid,
            "apk": apk_offer,
        }

    def handle_command(self, employee_id: int, message: dict[str, Any]) -> dict[str, Any]:
        action = message.get("accion") if isinstance(message.get("accion"), dict) else {}
        kind = action.get("t")
        try:
            command_id = int(message.get("id", 0))
        except (TypeError, ValueError):
            command_id = 0
        command_key = message.get("clave")
        if not isinstance(command_key, str) or not command_key or len(command_key) > 64:
            command_key = None
        response: dict[str, Any] = {"t": "cmd_ok", "id": command_id}
        request_hash = hashlib.sha256(canonical_json(action)).hexdigest()
        with self.database.transaction() as conn:
            employee = conn.execute("SELECT name,permissions_json FROM employees WHERE id=? AND active=1", (employee_id,)).fetchone()
            if not employee:
                response["error"] = "permiso"
                return response
            if command_key:
                cached = conn.execute("SELECT request_hash,result_json FROM sync_commands WHERE employee_id=? AND command_key=?",
                                      (employee_id, command_key)).fetchone()
                if cached:
                    if cached["request_hash"] != request_hash:
                        response["error"] = "otro"
                        return response
                    try:
                        result = json.loads(cached["result_json"])
                        if isinstance(result, dict):
                            response.update(result)
                            return response
                    except json.JSONDecodeError:
                        response["error"] = "otro"
                        return response
            permissions = set(json.loads(employee["permissions_json"] or "[]"))
            if kind in {"producto_guardar", "producto_eliminar", "producto_stock", "insumo_guardar", "insumo_eliminar", "insumo_stock", "servicio_guardar", "servicio_eliminar"}:
                required = "EDITAR_INVENTARIO"
            elif kind in {"producto_precios", "preajuste_guardar", "preajuste_eliminar"}:
                required = "CAMBIAR_PRECIOS"
            else:
                required = None
            try:
                if required is None:
                    raise RemoteCommandError("no_encontrado")
                if required not in permissions:
                    raise RemoteCommandError("permiso")
                value = self._apply_command(
                    conn, action, actor=employee["name"], actor_id=employee_id,
                    reference=f"sync-command:{employee_id}:{command_key or command_id}",
                )
                if value is not None:
                    response["valor"] = value
            except RemoteCommandError as exc:
                response["error"] = exc.code
            except (ValueError, KeyError, TypeError, OverflowError, sqlite3.IntegrityError):
                response["error"] = "validacion:datos:FORMATO"
            if command_key:
                result = {key: value for key, value in response.items() if key not in {"t", "id"}}
                conn.execute("INSERT INTO sync_commands(employee_id,command_key,request_hash,result_json) VALUES(?,?,?,?)",
                             (employee_id, command_key, request_hash, canonical_json(result).decode("utf-8")))
        return response

    @staticmethod
    def _apply_command(conn, action: dict[str, Any], *, actor: str = "Sincronización", actor_id: int | None = None, reference: str = "") -> int | None:
        kind = action.get("t")
        if kind == "producto_guardar":
            item = action.get("producto")
            if not isinstance(item, dict):
                raise ValueError("producto")
            item_id = int(item["id"])
            name = str(item["nombre"]).strip()
            stock = int(item.get("cantidad", 0))
            stock_milli = stock * 1000
            sale_price = int(item.get("precioVentaCent", 0))
            cost = int(item.get("precioCostoCent", 0))
            low = int(item.get("nivelBajo", 5) or 0)
            low_milli = low * 1000
            critical = int(item.get("nivelCritico", 1) or 0)
            critical_milli = critical * 1000
            raw_recipe = action.get("receta")
            recipe_rows: list[tuple[int, int]] = []
            if raw_recipe is not None:
                if isinstance(raw_recipe, dict):
                    recipe_product_id = int(raw_recipe.get("productoId", item_id))
                    if recipe_product_id != item_id:
                        raise ValueError("receta")
                    raw_recipe = raw_recipe.get("lineas", raw_recipe.get("ingredientes", []))
                if not isinstance(raw_recipe, list) or len(raw_recipe) > 5000:
                    raise ValueError("receta")
                seen_ingredients: set[int] = set()
                for component in raw_recipe:
                    if not isinstance(component, dict):
                        raise ValueError("receta")
                    ingredient_id = int(component.get("insumoId", component.get("ingredienteId", component.get("id", 0))))
                    quantity_milli = int(component.get("cantidadMil", component.get("cantidad_mil", 0)))
                    ingredient = conn.execute("SELECT 1 FROM items WHERE id=? AND category='Insumo' AND active=1", (ingredient_id,)).fetchone()
                    if ingredient_id <= 0 or ingredient_id in seen_ingredients or not 1 <= quantity_milli <= 10**12 or not ingredient:
                        raise ValueError("receta")
                    seen_ingredients.add(ingredient_id)
                    recipe_rows.append((ingredient_id, quantity_milli))
            if not 1 <= item_id <= 2**63 - 1 or not name or len(name) > 80 or not 0 <= stock <= 10**12:
                raise ValueError("producto")
            if not 0 <= sale_price <= 2**63 - 1 or not 0 <= cost <= 2**63 - 1 or not 0 <= critical <= low <= 10**12:
                raise ValueError("precio o niveles")
            previous = conn.execute("SELECT stock_milli FROM items WHERE id=?", (item_id,)).fetchone()
            conn.execute("""INSERT INTO items(
                    id,name,category,unit,stock,stock_milli,sale_price_cents,cost_cents,
                    low_stock,low_stock_milli,critical_stock,critical_stock_milli,active
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,1)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,category=excluded.category,
                    stock=excluded.stock,stock_milli=excluded.stock_milli,sale_price_cents=excluded.sale_price_cents,
                    cost_cents=excluded.cost_cents,low_stock=excluded.low_stock,low_stock_milli=excluded.low_stock_milli,
                    critical_stock=excluded.critical_stock,critical_stock_milli=excluded.critical_stock_milli,active=1""",
                (item_id, name, str(item.get("categoria", "Producto"))[:40], "unidad", stock_milli / 1000,
                 stock_milli, sale_price, cost, low_milli / 1000, low_milli, critical_milli / 1000, critical_milli))
            old_stock_milli = int(previous["stock_milli"]) if previous else 0
            delta_milli = stock_milli - old_stock_milli
            if delta_milli:
                record_movement(conn, item_id, delta_milli, movement_type="COMANDO_SYNC", actor=actor,
                                actor_type="empleado", actor_id=actor_id,
                                reason=str(action.get("motivo") or "Actualización remota de existencia"),
                                source="sincronizacion", reference=reference or None)
            if raw_recipe is not None:
                conn.execute("DELETE FROM recipes WHERE product_id=?", (item_id,))
                conn.executemany("INSERT INTO recipes(product_id,ingredient_id,quantity_milli) VALUES(?,?,?)",
                                 [(item_id, ingredient_id, quantity_milli) for ingredient_id, quantity_milli in recipe_rows])
            return item_id
        if kind == "producto_eliminar":
            item_id = int(action["id"])
            updated = conn.execute("UPDATE items SET active=0 WHERE id=? AND category!='Insumo' AND active=1", (item_id,)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            return None
        if kind == "producto_stock":
            item_id, delta = int(action["id"]), int(action["delta"])
            if abs(delta) > 10**12:
                raise ValueError("movimiento de producto")
            current = conn.execute("SELECT stock_milli FROM items WHERE id=? AND category!='Insumo' AND active=1", (item_id,)).fetchone()
            if not current:
                raise RemoteCommandError("no_encontrado")
            delta_milli = delta * 1000
            if abs(int(current["stock_milli"]) + delta_milli) > 9_000_000_000_000_000_000:
                raise ValueError("existencia fuera de límite")
            conn.execute("UPDATE items SET stock_milli=stock_milli+? WHERE id=?", (delta_milli, item_id))
            if delta_milli:
                record_movement(conn, item_id, delta_milli, movement_type="COMANDO_SYNC", actor=actor,
                                actor_type="empleado", actor_id=actor_id,
                                reason=str(action.get("motivo") or "Ajuste remoto de existencia"),
                                source="sincronizacion", reference=reference or None)
            return (int(current["stock_milli"]) + delta_milli) // 1000
        if kind == "producto_precios":
            prices = action.get("precios")
            if not isinstance(prices, dict) or len(prices) > 5000:
                raise ValueError("precios")
            normalized = []
            for item_id, price in prices.items():
                item_id, price = int(item_id), int(price)
                if item_id <= 0 or not 0 <= price <= 2**63 - 1:
                    raise ValueError("precio")
                exists = conn.execute("SELECT 1 FROM items WHERE id=? AND category!='Insumo' AND active=1", (item_id,)).fetchone()
                if not exists:
                    raise RemoteCommandError("no_encontrado")
                normalized.append((item_id, price))
            for item_id, price in normalized:
                conn.execute("UPDATE items SET sale_price_cents=? WHERE id=?", (price, item_id))
            return None
        if kind == "servicio_guardar":
            service = action.get("servicio")
            if not isinstance(service, dict):
                raise ValueError("servicio")
            service_id = int(service["id"])
            name = str(service.get("nombre", "")).strip()
            service_type = str(service.get("tipo", "General")).strip()
            price = int(service.get("importeCent", 0))
            description = str(service.get("descripcion") or "").strip()
            photo_uri = service.get("fotoUri")
            if photo_uri is not None:
                photo_uri = str(photo_uri)
            components = service.get("insumos", [])
            if not isinstance(components, list) or len(components) > 5000:
                raise ValueError("insumos del servicio")
            normalized_components = []
            seen = set()
            for component in components:
                if not isinstance(component, dict):
                    raise ValueError("insumos del servicio")
                ingredient_id = int(component.get("insumoId", 0))
                quantity_milli = int(component.get("cantidadMil", 0))
                ingredient = conn.execute("SELECT 1 FROM items WHERE id=? AND category='Insumo' AND active=1", (ingredient_id,)).fetchone()
                if ingredient_id in seen or not ingredient or not 1 <= quantity_milli <= 10**12:
                    raise ValueError("insumos del servicio")
                seen.add(ingredient_id)
                normalized_components.append({"insumoId": ingredient_id, "cantidadMil": quantity_milli})
            created_ms = int(service.get("creadoEn", 0) or 0)
            updated_ms = int(service.get("actualizadoEn", created_ms) or created_ms)
            local_name = str(service.get("local", "") or "")[:80]
            deleted = 1 if bool(service.get("eliminado", False)) else 0
            if not 1 <= service_id <= 2**63 - 1 or not name or len(name) > 100 or not service_type or len(service_type) > 40 or not 0 <= price <= 2**63 - 1 or len(description) > 1000 or (photo_uri is not None and len(photo_uri) > 2048) or created_ms < 0 or updated_ms < 0:
                raise ValueError("servicio")
            conn.execute("""INSERT INTO sync_services(service_id,name,service_type,price_cents,description,photo_uri,created_ms,updated_ms,deleted,ingredients_json,local_name)
                VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(service_id) DO UPDATE SET name=excluded.name,service_type=excluded.service_type,
                price_cents=excluded.price_cents,description=excluded.description,photo_uri=excluded.photo_uri,created_ms=excluded.created_ms,
                updated_ms=excluded.updated_ms,deleted=excluded.deleted,ingredients_json=excluded.ingredients_json,local_name=excluded.local_name""",
                (service_id, name, service_type, price, description, photo_uri, created_ms, updated_ms, deleted, json.dumps(normalized_components, ensure_ascii=False), local_name))
            return service_id
        if kind == "servicio_eliminar":
            updated = conn.execute("UPDATE sync_services SET deleted=1 WHERE service_id=? AND deleted=0", (int(action["id"]),)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            return None
        if kind == "preajuste_guardar":
            preset = action.get("preajuste")
            if not isinstance(preset, dict):
                raise ValueError("preajuste")
            preset_id = int(preset["id"])
            name = str(preset.get("nombre", "")).strip()
            points = int(preset.get("puntosBasicos", 0))
            product_ids, service_ids = preset.get("productoIds", []), preset.get("servicioIds", [])
            payment = preset.get("metodoPago")
            minimum = preset.get("importeMinimoCent")
            if not isinstance(product_ids, list) or not isinstance(service_ids, list) or len(product_ids) > 5000 or len(service_ids) > 5000:
                raise ValueError("preajuste")
            product_ids = sorted({int(value) for value in product_ids})
            service_ids = sorted({int(value) for value in service_ids})
            if not 1 <= preset_id <= 2**63 - 1 or not name or len(name) > 80 or points == 0 or not -9000 <= points <= 10000:
                raise ValueError("preajuste")
            if payment not in {None, "EFECTIVO", "TRANSFERENCIA"} or (minimum is not None and not 0 <= int(minimum) <= 10**12):
                raise ValueError("preajuste")
            conn.execute("""INSERT INTO presets(id,name,basis_points,product_ids_json,service_ids_json,payment_method,minimum_cents,active)
                VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,basis_points=excluded.basis_points,
                product_ids_json=excluded.product_ids_json,service_ids_json=excluded.service_ids_json,payment_method=excluded.payment_method,
                minimum_cents=excluded.minimum_cents,active=excluded.active""",
                (preset_id, name, points, json.dumps(product_ids), json.dumps(service_ids), payment, int(minimum) if minimum is not None else None, int(bool(preset.get("activo", True)))))
            return preset_id
        if kind == "preajuste_eliminar":
            updated = conn.execute("UPDATE presets SET active=0 WHERE id=? AND active=1", (int(action["id"]),)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            return None
        if kind == "insumo_guardar":
            item = action.get("insumo")
            if not isinstance(item, dict):
                raise ValueError("insumo")
            item_id = int(item["id"])
            name, unit = str(item["nombre"]).strip(), str(item.get("unidad", "unidad")).strip()
            quantity_milli = int(item.get("cantidadMil", 0))
            cost = int(item.get("precioCent", 0))
            sale = int(item.get("precioVentaCent") or 0)
            low_milli = int(item.get("nivelBajoMil", 5000) or 0)
            critical_milli = int(item.get("nivelCriticoMil", 1000) or 0)
            if not 1 <= item_id <= 2**63 - 1 or not name or len(name) > 80 or not unit or len(unit) > 20:
                raise ValueError("insumo")
            if not 0 <= quantity_milli <= 10**12 or not 0 <= cost <= 2**63 - 1 or not 0 <= sale <= 2**63 - 1 or not 0 <= critical_milli <= low_milli <= 10**12:
                raise ValueError("insumo")
            previous = conn.execute("SELECT stock_milli FROM items WHERE id=?", (item_id,)).fetchone()
            conn.execute("""INSERT INTO items(
                    id,name,category,unit,stock,stock_milli,sale_price_cents,cost_cents,
                    low_stock,low_stock_milli,critical_stock,critical_stock_milli,active
                ) VALUES(?,?,'Insumo',?,?,?,?,?,?,?,?,?,1)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,category='Insumo',unit=excluded.unit,
                    stock=excluded.stock,stock_milli=excluded.stock_milli,sale_price_cents=excluded.sale_price_cents,
                    cost_cents=excluded.cost_cents,low_stock=excluded.low_stock,low_stock_milli=excluded.low_stock_milli,
                    critical_stock=excluded.critical_stock,critical_stock_milli=excluded.critical_stock_milli,active=1""",
                (item_id, name, unit, quantity_milli / 1000, quantity_milli, sale, cost,
                 low_milli / 1000, low_milli, critical_milli / 1000, critical_milli))
            old_stock_milli = int(previous["stock_milli"]) if previous else 0
            delta_milli = quantity_milli - old_stock_milli
            if delta_milli:
                record_movement(conn, item_id, delta_milli, movement_type="COMANDO_SYNC", actor=actor,
                                actor_type="empleado", actor_id=actor_id,
                                reason=str(item.get("motivo") or "Actualización remota de existencia"),
                                source="sincronizacion", reference=reference or None)
            return item_id
        if kind == "insumo_eliminar":
            updated = conn.execute("UPDATE items SET active=0 WHERE id=? AND category='Insumo' AND active=1", (int(action["id"]),)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            return None
        if kind == "insumo_stock":
            delta_milli = int(action["deltaMil"])
            if abs(delta_milli) > 10**12:
                raise ValueError("movimiento de insumo")
            item_id = int(action["id"])
            current = conn.execute("SELECT stock_milli FROM items WHERE id=? AND category='Insumo' AND active=1", (item_id,)).fetchone()
            if not current:
                raise RemoteCommandError("no_encontrado")
            new_stock_milli = int(current["stock_milli"]) + delta_milli
            if abs(new_stock_milli) > 9_000_000_000_000_000_000:
                raise ValueError("existencia fuera de límite")
            conn.execute("UPDATE items SET stock_milli=stock_milli+? WHERE id=?", (delta_milli, item_id))
            if delta_milli:
                record_movement(conn, item_id, delta_milli, movement_type="COMANDO_SYNC", actor=actor,
                                actor_type="empleado", actor_id=actor_id,
                                reason=str(action.get("motivo") or "Ajuste remoto de insumo"),
                                source="sincronizacion", reference=reference or None)
            return new_stock_milli
        raise RemoteCommandError("no_encontrado")
