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
from typing import Any

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from .database import Database
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
        "precioVentaCent": row["sale_price_cents"], "cantidad": row["stock"], "nivelBajo": row["low_stock"],
        "nivelCritico": row["critical_stock"], "creadoEn": _epoch_ms(row["created_at"]),
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
        products = conn.execute("SELECT * FROM items WHERE active=1 AND category!='Insumo' ORDER BY id").fetchall()
        ingredients = conn.execute("SELECT * FROM items WHERE active=1 AND category='Insumo' ORDER BY id").fetchall()
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
            "recetas": [],
            "insumos": [
                {"id": row["id"], "nombre": row["name"], "unidad": row["unit"], "precioCent": row["cost_cents"],
                 "cantidadMil": row["stock"] * 1000, "nivelBajoMil": row["low_stock"] * 1000,
                 "nivelCriticoMil": row["critical_stock"] * 1000, "creadoEn": _epoch_ms(row["created_at"]),
                 "actualizadoEn": _epoch_ms(row["created_at"]), "precioVentaCent": row["sale_price_cents"], "local": "Local"}
                for row in ingredients
            ],
            "servicios": [], "preajustes": [], "perfil": None, "preferencias": business_preferences,
            "fotos": [], "local": "Local",
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
                channel.send(self.wfile, {"t": "foto_bloque", "id": int(message.get("id", 0)), "desde": int(message.get("desde", 0)), "datos": "", "total": 0, "error": "no_encontrado"})
            elif kind == "apk_pedir":
                channel.send(self.wfile, {"t": "apk_bloque", "id": int(message.get("id", 0)), "desde": int(message.get("desde", 0)), "datos": "", "total": 0, "error": "no_encontrado"})
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
        conn.execute("INSERT OR IGNORE INTO sync_sales(uuid,employee_id,employee_name,shift_uuid,sale_at_ms,payment_method,total_cents,payload_json) VALUES(?,?,?,?,?,?,?,?)",
                     (uuid, employee_id, employee_name, shift_uuid, sale_at_ms, method, total, canonical_json(wrapper).decode("utf-8")))
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
                if delta % 1000:
                    continue
                conn.execute("UPDATE items SET stock=stock+? WHERE id=? AND category='Insumo' AND active=1", (delta // 1000, item_id))
            elif entity == "PRODUCTO":
                conn.execute("UPDATE items SET stock=stock+? WHERE id=? AND category!='Insumo' AND active=1", (delta, item_id))

    def handle_sync(self, employee_id: int, message: dict[str, Any]) -> dict[str, Any]:
        batch = message.get("lote") if isinstance(message.get("lote"), dict) else {}
        received: dict[str, list[Any]] = {"turnos": [], "ventas": [], "caja": []}
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
                        received[list_name].append({"uuid": uuid, "cerrado": turno.get("cerradoEn") is not None})
                    else:
                        received[list_name].append(uuid)
                    if list_name == "ventas" and inserted:
                        self._store_sale(conn, employee_id, row, uuid)
            if message.get("telefono") and isinstance(message["telefono"], str):
                conn.execute("UPDATE employees SET phone=? WHERE id=?", (message["telefono"][:32], employee_id))
        catalog = snapshot(self.database, employee_id)
        digest = _catalog_hash(catalog)
        same = message.get("hash") == digest
        return {
            "t": "sync_ok", "id": int(message.get("id", 0)), "recibidos": received, "hash": digest,
            "instantanea": None if same else catalog, "cerrarTurno": False, "cierreRechazado": False,
            "cambiosVentas": [], "conteoAprobado": None, "conteoTurnoUuid": None,
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
            employee = conn.execute("SELECT permissions_json FROM employees WHERE id=? AND active=1", (employee_id,)).fetchone()
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
            if kind in {"producto_guardar", "producto_eliminar", "producto_stock", "insumo_guardar", "insumo_eliminar", "insumo_stock"}:
                required = "EDITAR_INVENTARIO"
            elif kind == "producto_precios":
                required = "CAMBIAR_PRECIOS"
            else:
                required = None
            try:
                if required is None:
                    raise RemoteCommandError("no_encontrado")
                if required not in permissions:
                    raise RemoteCommandError("permiso")
                value = self._apply_command(conn, action)
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
                conn.execute("""DELETE FROM sync_commands WHERE employee_id=? AND command_key NOT IN
                    (SELECT command_key FROM sync_commands WHERE employee_id=? ORDER BY rowid DESC LIMIT 512)""",
                             (employee_id, employee_id))
        return response

    @staticmethod
    def _apply_command(conn, action: dict[str, Any]) -> int | None:
        kind = action.get("t")
        if kind == "producto_guardar":
            item = action.get("producto")
            if not isinstance(item, dict):
                raise ValueError("producto")
            item_id = int(item["id"])
            name = str(item["nombre"]).strip()
            stock = int(item.get("cantidad", 0))
            sale_price = int(item.get("precioVentaCent", 0))
            cost = int(item.get("precioCostoCent", 0))
            low = int(item.get("nivelBajo", 5) or 0)
            critical = int(item.get("nivelCritico", 1) or 0)
            if action.get("receta") is not None:
                raise RemoteCommandError("validacion:receta:FORMATO")
            if not 1 <= item_id <= 2**63 - 1 or not name or len(name) > 80 or not 0 <= stock <= 10**12:
                raise ValueError("producto")
            if not 0 <= sale_price <= 2**63 - 1 or not 0 <= cost <= 2**63 - 1 or not 0 <= critical <= low <= 10**12:
                raise ValueError("precio o niveles")
            conn.execute("""INSERT INTO items(id,name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock,active)
                VALUES(?,?,?,?,?,?,?,?,?,1) ON CONFLICT(id) DO UPDATE SET name=excluded.name,category=excluded.category,
                stock=excluded.stock,sale_price_cents=excluded.sale_price_cents,cost_cents=excluded.cost_cents,
                low_stock=excluded.low_stock,critical_stock=excluded.critical_stock,active=1""",
                (item_id, name, str(item.get("categoria", "Producto"))[:40], "unidad", stock,
                 sale_price, cost, low, critical))
            return item_id
        if kind == "producto_eliminar":
            item_id = int(action["id"])
            updated = conn.execute("UPDATE items SET active=0 WHERE id=? AND category!='Insumo' AND active=1", (item_id,)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            return None
        if kind == "producto_stock":
            item_id, delta = int(action["id"]), int(action["delta"])
            updated = conn.execute("UPDATE items SET stock=stock+? WHERE id=? AND category!='Insumo' AND active=1", (delta, item_id)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            return int(conn.execute("SELECT stock FROM items WHERE id=?", (item_id,)).fetchone()[0])
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
            if quantity_milli % 1000 or low_milli % 1000 or critical_milli % 1000:
                raise ValueError("precisión de insumo")
            if not 0 <= cost <= 2**63 - 1 or not 0 <= sale <= 2**63 - 1 or not 0 <= critical_milli <= low_milli <= 10**12:
                raise ValueError("insumo")
            conn.execute("""INSERT INTO items(id,name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock,active)
                VALUES(?,?,'Insumo',?,?,?,?,?,?,1) ON CONFLICT(id) DO UPDATE SET name=excluded.name,category='Insumo',
                unit=excluded.unit,stock=excluded.stock,sale_price_cents=excluded.sale_price_cents,cost_cents=excluded.cost_cents,
                low_stock=excluded.low_stock,critical_stock=excluded.critical_stock,active=1""",
                (item_id, name, unit, quantity_milli // 1000, sale, cost, low_milli // 1000, critical_milli // 1000))
            return item_id
        if kind == "insumo_eliminar":
            updated = conn.execute("UPDATE items SET active=0 WHERE id=? AND category='Insumo' AND active=1", (int(action["id"]),)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            return None
        if kind == "insumo_stock":
            delta = int(action["deltaMil"])
            if delta % 1000:
                raise ValueError("precisión de insumo")
            item_id = int(action["id"])
            updated = conn.execute("UPDATE items SET stock=stock+? WHERE id=? AND category='Insumo' AND active=1", (delta // 1000, item_id)).rowcount
            if updated != 1:
                raise RemoteCommandError("no_encontrado")
            row = conn.execute("SELECT stock FROM items WHERE id=?", (item_id,)).fetchone()
            return int(row[0]) * 1000
        raise RemoteCommandError("no_encontrado")
