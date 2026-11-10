from __future__ import annotations

import base64
import csv
import hashlib
import io
import json
import logging
import re
import secrets
import socket
import sqlite3
import time
import uuid as uuidlib
import zipfile
from decimal import Decimal, InvalidOperation
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from starlette.middleware.sessions import SessionMiddleware

from .config import AppConfig, DEFAULT_PORT, save_config
from .database import Database
from .forms import form_data, format_cup, integer, money_to_cents
from .security import csrf_token, hash_password, valid_csrf, verify_password
from .sync_protocol import canonical_json, generate_pairing_code
from .sync_server import ShalevSyncServer
from .xlsx import build_xlsx

TEMPLATES = Path(__file__).parent / "templates"
STATIC = Path(__file__).parent / "static"
USERNAME_RE = re.compile(r"^[A-Za-z0-9._-]{3,40}$")
PERMISSIONS = {
    "vender_productos": "Vender productos",
    "vender_servicios": "Vender servicios",
    "inventario": "Editar inventario y servicios",
    "precios": "Cambiar precios",
    "exportar": "Exportar y compartir",
}
PERMISSION_PROTOCOL = {
    "vender_productos": "VENDER_PRODUCTOS", "vender_servicios": "VENDER_SERVICIOS",
    "inventario": "EDITAR_INVENTARIO", "precios": "CAMBIAR_PRECIOS", "exportar": "EXPORTAR",
}
PUBLIC_HOST_RE = re.compile(r"^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\.(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*$")


def csv_text(value: Any) -> str:
    """Neutraliza texto que una hoja de cálculo podría evaluar como fórmula.

    Se aplica solo a campos textuales, no a cantidades ni importes numéricos.
    El apóstrofo es parte del CSV y no modifica los datos guardados.
    """
    text = "" if value is None else str(value)
    if text and (text[0] in "\t\r\n" or text.lstrip().startswith(("=", "+", "-", "@"))):
        return "'" + text
    return text


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def get_lan_addresses() -> list[str]:
    found: set[str] = set()
    try:
        for result in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET, socket.SOCK_STREAM):
            address = result[4][0]
            if not address.startswith("127.") and not address.startswith("169.254."):
                found.add(address)
    except OSError:
        pass
    return sorted(found)


def create_app(config: AppConfig, database: Database | None = None, *, port: int = DEFAULT_PORT) -> FastAPI:
    db = database or Database(config.database_path)
    db.initialize()
    templates = Jinja2Templates(directory=str(TEMPLATES))
    templates.env.filters["cup"] = format_cup
    templates.env.filters["qty"] = lambda value: f"{float(value):.3f}".rstrip("0").rstrip(".")

    @asynccontextmanager
    async def lifespan(live_app):
        sync_server = None
        live_app.state.sync_server = None
        live_app.state.sync_server_error = None
        if live_app.state.config.network_mode in {"lan", "remote"}:
            try:
                sync_server = ShalevSyncServer(db, host="0.0.0.0")
                sync_server.start()
                live_app.state.sync_server = sync_server
            except OSError:
                logging.getLogger("dshalev.sync").exception("No se pudo iniciar el listener SPVI")
                live_app.state.sync_server_error = "No se pudo abrir un puerto para las apps Shalev."
        try:
            yield
        finally:
            if sync_server:
                sync_server.stop()

    app = FastAPI(title="DShalev", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)
    app.state.config = config
    app.state.db = db
    app.state.port = port
    app.state.sync_server = None
    app.state.sync_server_error = None
    app.mount("/static", StaticFiles(directory=str(STATIC)), name="static")
    app.add_middleware(
        SessionMiddleware,
        secret_key=config.secret_key,
        session_cookie="dshalev_session",
        max_age=8 * 60 * 60,
        same_site="strict",
        https_only=config.tls_enabled,
    )

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
        response.headers["Content-Security-Policy"] = "default-src 'self'; style-src 'self'; img-src 'self' data:; script-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"
        response.headers["Cache-Control"] = "no-store"
        if config.tls_enabled:
            response.headers["Strict-Transport-Security"] = "max-age=31536000"
        return response

    def csrf(request: Request) -> str:
        value = request.session.get("csrf")
        if not isinstance(value, str):
            value = csrf_token()
            request.session["csrf"] = value
        return value

    def current_user(request: Request) -> dict[str, Any] | None:
        user_id = request.session.get("user_id")
        if not isinstance(user_id, int):
            return None
        with db.connect() as conn:
            row = conn.execute("SELECT id, username, role FROM users WHERE id=? AND active=1", (user_id,)).fetchone()
        return dict(row) if row else None

    def context(request: Request, *, user: dict[str, Any] | None = None, section: str = "", **values: Any) -> dict[str, Any]:
        if user is None:
            user = current_user(request)
        if values.get("notice") is None:
            flash_notice = request.session.pop("flash_notice", None)
            if flash_notice:
                values["notice"] = flash_notice
                values["notice_kind"] = request.session.pop("flash_kind", "info")
        with db.connect() as conn:
            business = conn.execute("SELECT value FROM app_meta WHERE key='business_name'").fetchone()
        return {
            "request": request, "user": user, "csrf": csrf(request), "section": section,
            "business_name": business["value"] if business else "DShalev", "notice": None,
            **values,
        }

    def redirect(path: str, *, notice: str | None = None) -> RedirectResponse:
        if notice:
            from urllib.parse import quote
            path += ("&" if "?" in path else "?") + "notice=" + quote(notice)
        return RedirectResponse(path, status_code=303)

    def flash_redirect(request: Request, path: str, message: str, *, kind: str = "error") -> RedirectResponse:
        request.session["flash_notice"] = message
        request.session["flash_kind"] = kind
        return RedirectResponse(path, status_code=303)

    def decimal_quantity(raw: Any, *, maximum: Decimal = Decimal("1000000000")) -> float:
        try:
            value = Decimal(str(raw).strip())
        except (InvalidOperation, ValueError, AttributeError) as exc:
            raise ValueError("La existencia debe ser un número no negativo con hasta tres decimales.") from exc
        if not value.is_finite() or value < 0 or value > maximum or value != value.quantize(Decimal("0.001")):
            raise ValueError("La existencia debe ser un número no negativo con hasta tres decimales.")
        return float(value)

    def notice_text(request: Request) -> str | None:
        allowed = {
            "item_added": "Artículo guardado.", "sale_saved": "Venta registrada y existencia actualizada.",
            "shift_opened": "Turno abierto.", "shift_closed": "Turno cerrado; revisa el arqueo en Turnos.", "cash_saved": "Movimiento de caja registrado.", "employee_added": "Plaza secundaria creada.",
            "pairing_created": "QR de vinculación generado; caduca en 10 minutos.",
            "item_saved": "Artículo actualizado.", "item_deleted": "Artículo retirado del inventario.",
            "recipe_saved": "Receta guardada; se descontará del inventario al vender.", "recipe_deleted": "Ingrediente retirado de la receta.",
            "customer_added": "Cliente agregado.", "customer_saved": "Datos del cliente actualizados.", "customer_retired": "Cliente retirado; sus ventas históricas se conservan.",
            "mobile_close_approved": "Se aprobó el cierre; se ordenará en la próxima sincronización.", "mobile_close_rejected": "Se rechazó la solicitud; la app recibirá el aviso al sincronizar.",
            "mobile_count_approved": "Arqueo móvil aprobado y listo para recibir confirmación en la app.",
            "mobile_sale_cancelled": "Venta móvil anulada; se repuso el stock y se enviará el cambio a la app.",
            "mobile_sale_corrected": "Venta móvil corregida; se ajustó el stock y la app recibirá la anulación y el reemplazo.",
            "employee_removed": "Plaza retirada y credenciales revocadas. La app recibirá el aviso al reconectarse.",
            "permissions_saved": "Permisos guardados; se aplicarán en la próxima sincronización.",
            "network_saved": "La configuración se guardó. Reinicia DShalev para aplicar el cambio de red.",
            "logged_out": "Sesión cerrada.",
        }
        return allowed.get(request.query_params.get("notice", ""))

    async def require_csrf(request: Request, data: dict[str, str] | None = None) -> dict[str, str] | None:
        data = data if data is not None else await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return None
        return data

    def require_admin(request: Request) -> dict[str, Any] | None:
        user = current_user(request)
        return user if user and user["role"] == "admin" else None

    @app.get("/healthz")
    async def health() -> JSONResponse:
        return JSONResponse({"ok": True, "app": "DShalev", "version": "0.1.0"})

    @app.get("/", response_class=HTMLResponse)
    async def home(request: Request):
        if not db.has_admin():
            return redirect("/configurar")
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        today = datetime.now().astimezone().date().isoformat()
        with db.connect() as conn:
            shift = conn.execute("SELECT * FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
            item_count = conn.execute("SELECT COUNT(*) FROM items WHERE active=1").fetchone()[0]
            low_count = conn.execute("SELECT COUNT(*) FROM items WHERE active=1 AND stock<=low_stock").fetchone()[0]
            today_row = conn.execute("SELECT COUNT(*) count, COALESCE(SUM(total_cents),0) total FROM sales WHERE date(created_at)=?", (today,)).fetchone()
            native_today = conn.execute("SELECT COUNT(*) count, COALESCE(SUM(total_cents),0) total FROM sync_sales WHERE cancelled_at IS NULL AND date(sale_at_ms/1000,'unixepoch','localtime')=?", (today,)).fetchone()
            today_count = today_row["count"] + native_today["count"]
            today_total = today_row["total"] + native_today["total"]
            sales = conn.execute("""SELECT s.*, COALESCE(group_concat(sl.item_name || ' × ' || sl.quantity, ', '),'Venta') summary
                FROM sales s LEFT JOIN sale_lines sl ON sl.sale_id=s.id
                GROUP BY s.id ORDER BY s.id DESC LIMIT 6""").fetchall()
            expected_cash = 0
            cash_movements = []
            if shift:
                cash_sales = conn.execute("SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE shift_id=? AND payment_method='EFECTIVO'", (shift["id"],)).fetchone()[0]
                cash_totals = conn.execute("SELECT COALESCE(SUM(CASE WHEN movement_type='ENTRADA' THEN amount_cents ELSE -amount_cents END),0) FROM cash_movements WHERE shift_id=?", (shift["id"],)).fetchone()[0]
                expected_cash = shift["opening_cash_cents"] + cash_sales + cash_totals
                cash_movements = conn.execute("SELECT * FROM cash_movements WHERE shift_id=? ORDER BY id DESC LIMIT 10", (shift["id"],)).fetchall()
        mode = config.network_mode
        mode_labels = {"local": "solo este equipo", "lan": "red local", "remote": "remoto con HTTPS"}
        notice = notice_text(request)
        return templates.TemplateResponse(request, "dashboard.html", context(
            request, user=user, section="inicio", notice=notice, notice_kind="success" if notice else None,
            shift=dict(shift) if shift else None, item_count=item_count, low_count=low_count,
            expected_cash=format_cup(expected_cash), cash_movements=[dict(r) for r in cash_movements],
            today_total=format_cup(today_total), today_count=today_count,
            sales=[dict(r) for r in sales], network_label=mode_labels[mode],
        ))

    @app.get("/configurar", response_class=HTMLResponse)
    async def setup_page(request: Request):
        if db.has_admin():
            return redirect("/")
        return templates.TemplateResponse(request, "setup.html", context(request))

    @app.post("/configurar", response_class=HTMLResponse)
    async def setup(request: Request):
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return templates.TemplateResponse(request, "setup.html", context(request, error="La sesión del formulario venció; recarga la página."), status_code=403)
        name = data.get("business_name", "").strip()
        username = data.get("username", "").strip()
        password = data.get("password", "")
        if db.has_admin():
            return redirect("/entrar")
        error = None
        if not name or len(name) > 80:
            error = "Escribe un nombre de negocio de hasta 80 caracteres."
        elif not USERNAME_RE.fullmatch(username):
            error = "El usuario debe tener entre 3 y 40 caracteres: letras, números, punto, guion o guion bajo."
        elif password != data.get("password_confirm"):
            error = "Las contraseñas no coinciden."
        else:
            try:
                encoded = hash_password(password)
            except ValueError as exc:
                error = str(exc)
        if error:
            return templates.TemplateResponse(request, "setup.html", context(request, error=error), status_code=422)
        try:
            with db.transaction() as conn:
                if conn.execute("SELECT 1 FROM users WHERE role='admin' AND active=1").fetchone():
                    return redirect("/entrar")
                conn.execute("INSERT INTO users(username,password_hash,role) VALUES(?,?,'admin')", (username, encoded))
                conn.execute("UPDATE app_meta SET value=? WHERE key='business_name'", (name,))
        except sqlite3.IntegrityError:
            return templates.TemplateResponse(request, "setup.html", context(request, error="No se pudo crear la cuenta. Reinicia la configuración inicial."), status_code=409)
        return redirect("/entrar")

    @app.get("/entrar", response_class=HTMLResponse)
    async def login_page(request: Request):
        if not db.has_admin():
            return redirect("/configurar")
        user = current_user(request)
        if user:
            return redirect("/")
        return templates.TemplateResponse(request, "login.html", context(request, error=request.query_params.get("error"), notice=notice_text(request)))

    @app.post("/entrar", response_class=HTMLResponse)
    async def login(request: Request):
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return redirect("/entrar?error=Sesión%20vencida")
        remote = request.client.host if request.client else "unknown"
        now = int(time.time())
        with db.transaction() as conn:
            conn.execute("CREATE TABLE IF NOT EXISTS auth_throttle (ip TEXT PRIMARY KEY, attempts INTEGER NOT NULL, locked_until INTEGER NOT NULL DEFAULT 0)")
            throttle = conn.execute("SELECT attempts,locked_until FROM auth_throttle WHERE ip=?", (remote,)).fetchone()
            if throttle and throttle["locked_until"] > now:
                return templates.TemplateResponse(request, "login.html", context(request, error="Demasiados intentos. Espera unos minutos y vuelve a intentarlo."), status_code=429)
            row = conn.execute("SELECT id,username,password_hash,role FROM users WHERE username=? AND active=1", (data.get("username", "").strip(),)).fetchone()
            valid = verify_password(data.get("password", ""), row["password_hash"]) if row else verify_password(data.get("password", ""), hash_password("invalid placeholder password"))
            if not valid:
                attempts = (throttle["attempts"] + 1) if throttle else 1
                locked_until = now + 300 if attempts >= 5 else 0
                conn.execute("INSERT INTO auth_throttle(ip,attempts,locked_until) VALUES(?,?,?) ON CONFLICT(ip) DO UPDATE SET attempts=excluded.attempts,locked_until=excluded.locked_until", (remote, attempts, locked_until))
                error = "Usuario o contraseña incorrectos." if locked_until == 0 else "Demasiados intentos. Espera cinco minutos y vuelve a intentarlo."
                return templates.TemplateResponse(request, "login.html", context(request, error=error), status_code=401 if locked_until == 0 else 429)
            conn.execute("DELETE FROM auth_throttle WHERE ip=?", (remote,))
        request.session.clear()
        request.session["user_id"] = row["id"]
        request.session["csrf"] = csrf_token()
        return redirect("/")

    @app.post("/salir")
    async def logout(request: Request):
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        request.session.clear()
        request.session["csrf"] = csrf_token()
        return redirect("/entrar?notice=logged_out")

    @app.get("/inventario", response_class=HTMLResponse)
    async def inventory(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        query = request.query_params.get("q", "").strip()[:80]
        with db.connect() as conn:
            if query:
                items = conn.execute("SELECT * FROM items WHERE active=1 AND name LIKE ? ESCAPE '\\' ORDER BY name LIMIT 200", ("%" + query.replace("%", "\\%").replace("_", "\\_") + "%",)).fetchall()
            else:
                items = conn.execute("SELECT * FROM items WHERE active=1 ORDER BY name LIMIT 200").fetchall()
        return templates.TemplateResponse(request, "inventory.html", context(request, user=user, section="inventario", items=[dict(r) for r in items], query=query, notice=notice_text(request), notice_kind="success"))

    @app.get("/inventario/{item_id}/editar", response_class=HTMLResponse)
    async def edit_item_page(request: Request, item_id: int):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        with db.connect() as conn:
            item = conn.execute("SELECT * FROM items WHERE id=? AND active=1", (item_id,)).fetchone()
        if not item:
            return flash_redirect(request, "/inventario", "El artículo ya no existe.")
        return templates.TemplateResponse(request, "inventory_edit.html", context(request, user=user, section="inventario", item=dict(item)))

    @app.post("/inventario/{item_id}/editar")
    async def edit_item(request: Request, item_id: int):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            name = data.get("name", "").strip()
            category = data.get("category", "Producto")
            unit = data.get("unit", "unidad").strip()
            if not name or len(name) > 80 or not unit or len(unit) > 20 or category not in {"Producto", "Insumo", "Servicio"}:
                raise ValueError("Revisa el nombre, la categoría y la unidad.")
            sale_price, cost = money_to_cents(data.get("sale_price")), money_to_cents(data.get("cost"))
            quantity_parser = decimal_quantity if category == "Insumo" else lambda value: integer(value, maximum=10**9)
            stock = quantity_parser(data.get("stock"))
            low = quantity_parser(data.get("low_stock"))
            critical = quantity_parser(data.get("critical_stock"))
            if category == "Servicio":
                stock = low = critical = 0
            if critical > low:
                raise ValueError("El nivel crítico no puede superar el nivel bajo.")
        except ValueError as exc:
            return flash_redirect(request, f"/inventario/{item_id}/editar", str(exc))
        with db.transaction() as conn:
            updated = conn.execute("""UPDATE items SET name=?,category=?,unit=?,stock=?,sale_price_cents=?,cost_cents=?,low_stock=?,critical_stock=?
                WHERE id=? AND active=1""", (name, category, unit, stock, sale_price, cost, low, critical, item_id)).rowcount
        if updated != 1:
            return flash_redirect(request, "/inventario", "El artículo ya no existe.")
        return redirect("/inventario", notice="item_saved")

    @app.post("/inventario/{item_id}/eliminar")
    async def delete_item(request: Request, item_id: int):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        with db.transaction() as conn:
            updated = conn.execute("UPDATE items SET active=0 WHERE id=? AND active=1", (item_id,)).rowcount
        if updated != 1:
            return flash_redirect(request, "/inventario", "El artículo ya no existe.")
        return redirect("/inventario", notice="item_deleted")

    @app.post("/inventario/nuevo")
    async def add_item(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            name = data.get("name", "").strip()
            category = data.get("category", "Producto")
            unit = data.get("unit", "unidad").strip()
            if not name or len(name) > 80 or not unit or len(unit) > 20 or category not in {"Producto", "Insumo", "Servicio"}:
                raise ValueError("Revisa el nombre, la categoría y la unidad del artículo.")
            sale_price = money_to_cents(data.get("sale_price"))
            cost = money_to_cents(data.get("cost"))
            quantity_parser = decimal_quantity if category == "Insumo" else lambda value: integer(value, maximum=10**9)
            stock = quantity_parser(data.get("stock"))
            low = quantity_parser(data.get("low_stock"))
            critical = quantity_parser(data.get("critical_stock", "1"))
            if category == "Servicio":
                stock = low = critical = 0
            if critical > low:
                raise ValueError("El nivel crítico no puede superar el nivel bajo.")
        except ValueError as exc:
            return flash_redirect(request, "/inventario", str(exc))
        try:
            with db.transaction() as conn:
                conn.execute("INSERT INTO items(name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock) VALUES(?,?,?,?,?,?,?,?)", (name, category, unit, stock, sale_price, cost, low, critical))
        except sqlite3.IntegrityError:
            return flash_redirect(request, "/inventario", "No se pudo guardar el artículo.")
        return redirect("/inventario", notice="item_added")

    @app.get("/ventas", response_class=HTMLResponse)
    async def sales_page(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        query = request.query_params.get("q", "").strip()[:80]
        with db.connect() as conn:
            shift = conn.execute("SELECT * FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
            if query:
                items = conn.execute("SELECT id,name,category,stock,unit,sale_price_cents FROM items WHERE active=1 AND category!='Insumo' AND (category='Servicio' OR stock>0) AND name LIKE ? ORDER BY name LIMIT 60", (f"%{query}%",)).fetchall()
            else:
                items = conn.execute("SELECT id,name,category,stock,unit,sale_price_cents FROM items WHERE active=1 AND category!='Insumo' AND (category='Servicio' OR stock>0) ORDER BY name LIMIT 60").fetchall()
            sales = conn.execute("""SELECT s.*, COALESCE(group_concat(sl.item_name || ' × ' || sl.quantity, ', '),'Venta') summary,c.name customer_name
                FROM sales s LEFT JOIN sale_lines sl ON sl.sale_id=s.id LEFT JOIN customers c ON c.id=s.customer_id
                GROUP BY s.id ORDER BY s.id DESC LIMIT 50""").fetchall()
            customers = conn.execute("SELECT id,name FROM customers WHERE active=1 ORDER BY name LIMIT 500").fetchall()
            native_sales = conn.execute("SELECT * FROM sync_sales ORDER BY sale_at_ms DESC LIMIT 50").fetchall()
        recent = []
        for row in sales:
            sale = dict(row)
            try:
                sale["_sort"] = datetime.fromisoformat(sale["created_at"].replace("Z", "+00:00")).timestamp()
            except (TypeError, ValueError):
                sale["_sort"] = 0
            sale["origin"] = "Escritorio"
            recent.append(sale)
        for row in native_sales:
            native = dict(row)
            payload = json.loads(native["payload_json"] or "{}")
            sale_data = payload.get("venta", {}) if isinstance(payload, dict) else {}
            details = sale_data.get("detalles", []) if isinstance(sale_data, dict) else []
            transaction = sale_data.get("transaccion", {}) if isinstance(sale_data, dict) else {}
            native["customer_name"] = str(transaction.get("clienteNombre", "") or "") if isinstance(transaction, dict) else ""
            native["details"] = [dict(detail) for detail in details if isinstance(detail, dict)]
            movement_rows = payload.get("movimientos", []) if isinstance(payload, dict) else []
            product_ids_for_sale = []
            try:
                product_ids_for_sale = [int(detail["productoId"]) for detail in native["details"]]
                expected_movements: dict[int, int] = {}
                for detail in native["details"]:
                    if detail.get("clase", "") != "PRODUCTO":
                        raise ValueError("tipo")
                    product_id = int(detail["productoId"])
                    expected_movements[product_id] = expected_movements.get(product_id, 0) - int(detail["cantidad"])
                actual_movements: dict[int, int] = {}
                simple_movements = isinstance(movement_rows, list)
                for movement in movement_rows if isinstance(movement_rows, list) else []:
                    if not isinstance(movement, dict) or str(movement.get("entidad", "")).upper() != "PRODUCTO":
                        simple_movements = False
                        break
                    product_id = int(movement["entidadId"])
                    actual_movements[product_id] = actual_movements.get(product_id, 0) + int(movement["delta"])
                with db.connect() as conn:
                    ids = tuple(sorted(set(product_ids_for_sale)))
                    placeholders = ",".join("?" for _ in ids)
                    found_products = conn.execute(f"SELECT COUNT(*) FROM items WHERE active=1 AND category!='Insumo' AND id IN ({placeholders})", ids).fetchone()[0] if ids else 0
                    has_recipes = conn.execute(f"SELECT COUNT(*) FROM recipes WHERE product_id IN ({placeholders})", ids).fetchone()[0] if ids else 0
                native["correctable"] = bool(product_ids_for_sale) and simple_movements and actual_movements == expected_movements and found_products == len(ids) and has_recipes == 0
            except (KeyError, TypeError, ValueError):
                native["correctable"] = False
            summary = ", ".join(
                f"{str(detail.get('nombre', 'Artículo'))[:80]} × {int(detail.get('cantidad', 0))}"
                for detail in details[:50] if isinstance(detail, dict)
            ) or "Venta móvil"
            native["created_at"] = datetime.fromtimestamp(native["sale_at_ms"] / 1000, timezone.utc).astimezone().isoformat(timespec="seconds")
            native["summary"] = summary
            native["_sort"] = native["sale_at_ms"] / 1000
            native["origin"] = f"App secundaria · {native['employee_name'] or 'Empleado'}"
            native["cancelled"] = native.get("cancelled_at") is not None
            recent.append(native)
        recent.sort(key=lambda sale: sale["_sort"], reverse=True)
        for sale in recent:
            sale.pop("_sort", None)
        return templates.TemplateResponse(request, "sales.html", context(request, user=user, section="ventas", shift=dict(shift) if shift else None, items=[dict(r) for r in items], customers=[dict(r) for r in customers], query=query, sales=recent[:50], notice=notice_text(request), notice_kind="success"))

    @app.post("/ventas/nueva")
    async def create_sale(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            selected: list[tuple[int, int]] = []
            for key, raw in data.items():
                if key.startswith("quantity_"):
                    item_id = integer(key.removeprefix("quantity_"), minimum=1, maximum=2**31 - 1)
                    quantity = integer(raw, maximum=100_000)
                    if quantity:
                        selected.append((item_id, quantity))
            # Backward compatibility for old clients/forms with one item.
            if not selected and data.get("item_id"):
                selected.append((integer(data.get("item_id"), minimum=1, maximum=2**31 - 1),
                                 integer(data.get("quantity"), minimum=1, maximum=100_000)))
            if not selected or len(selected) > 60:
                raise ValueError("Selecciona al menos un artículo.")
            customer_id = integer(data.get("customer_id"), minimum=1, maximum=2**31 - 1) if data.get("customer_id") else None
        except ValueError as exc:
            return flash_redirect(request, "/ventas", str(exc))
        payment = data.get("payment_method", "")
        if payment not in {"EFECTIVO", "TRANSFERENCIA"}:
            return flash_redirect(request, "/ventas", "Forma de pago no válida.")
        try:
            with db.transaction() as conn:
                shift = conn.execute("SELECT id FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
                if not shift:
                    raise ValueError("Abre un turno antes de registrar ventas.")
                if customer_id and not conn.execute("SELECT 1 FROM customers WHERE id=? AND active=1", (customer_id,)).fetchone():
                    raise ValueError("El cliente ya no está disponible.")
                prepared = []
                total = 0
                for item_id, quantity in selected:
                    item = conn.execute("SELECT * FROM items WHERE id=? AND active=1", (item_id,)).fetchone()
                    if not item or item["category"] == "Insumo":
                        raise ValueError("Uno de los artículos ya no está disponible para la venta.")
                    if item["category"] != "Servicio" and item["stock"] < quantity:
                        raise ValueError(f"No hay existencia suficiente de {item['name']}.")
                    line_total = item["sale_price_cents"] * quantity
                    total += line_total
                    if total > 10**14:
                        raise ValueError("El total de la venta supera el límite permitido.")
                    prepared.append((item, quantity))
                ingredient_requirements: dict[int, int] = {}
                for item, quantity in prepared:
                    if item["category"] not in {"Producto", "Servicio"}:
                        continue
                    for recipe in conn.execute("SELECT ingredient_id,quantity_milli FROM recipes WHERE product_id=?", (item["id"],)):
                        ingredient_requirements[recipe["ingredient_id"]] = ingredient_requirements.get(recipe["ingredient_id"], 0) + recipe["quantity_milli"] * quantity
                for ingredient_id, required_milli in ingredient_requirements.items():
                    ingredient = conn.execute("SELECT name,stock FROM items WHERE id=? AND category='Insumo' AND active=1", (ingredient_id,)).fetchone()
                    available_milli = int(Decimal(str(ingredient["stock"])) * 1000) if ingredient else 0
                    if not ingredient or available_milli < required_milli:
                        raise ValueError(f"No hay suficiente {ingredient['name'] if ingredient else 'insumo'} para preparar la venta.")
                cursor = conn.execute("INSERT INTO sales(shift_id,total_cents,payment_method,customer_id) VALUES(?,?,?,?)", (shift["id"], total, payment, customer_id))
                for item, quantity in prepared:
                    conn.execute("INSERT INTO sale_lines(sale_id,item_id,item_name,quantity,unit_price_cents) VALUES(?,?,?,?,?)",
                                 (cursor.lastrowid, item["id"], item["name"], quantity, item["sale_price_cents"]))
                    if item["category"] != "Servicio":
                        updated = conn.execute("UPDATE items SET stock=stock-? WHERE id=? AND active=1 AND stock>=?",
                                               (quantity, item["id"], quantity)).rowcount
                        if updated != 1:
                            raise ValueError(f"La existencia de {item['name']} cambió; revisa el inventario.")
                for ingredient_id, required_milli in ingredient_requirements.items():
                    required = required_milli / 1000
                    updated = conn.execute("UPDATE items SET stock=stock-? WHERE id=? AND active=1 AND category='Insumo' AND stock>=?", (required, ingredient_id, required)).rowcount
                    if updated != 1:
                        raise ValueError("La existencia de un insumo cambió; revisa las recetas.")
        except ValueError as exc:
            return flash_redirect(request, "/ventas", str(exc))
        return redirect("/ventas", notice="sale_saved")

    @app.post("/ventas/movil/{sale_uuid}/anular")
    async def cancel_mobile_sale(request: Request, sale_uuid: str):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        reason = data.get("reason", "").strip()
        if not 3 <= len(reason) <= 160:
            return flash_redirect(request, "/ventas", "Escribe un motivo de anulación de 3 a 160 caracteres.")
        try:
            sale_uuid = str(uuidlib.UUID(sale_uuid))
            cancelled_at = int(time.time() * 1000)
            with db.transaction() as conn:
                sale = conn.execute("SELECT * FROM sync_sales WHERE uuid=?", (sale_uuid,)).fetchone()
                if not sale:
                    raise ValueError("La venta móvil no existe.")
                if sale["cancelled_at"] is not None:
                    raise ValueError("La venta ya está anulada.")
                payload = json.loads(sale["payload_json"] or "{}")
                movements = payload.get("movimientos", []) if isinstance(payload, dict) else []
                if not isinstance(movements, list):
                    movements = []
                for movement in movements[:5000]:
                    if not isinstance(movement, dict):
                        continue
                    try:
                        item_id = int(movement["entidadId"])
                        delta = int(movement["delta"])
                        entity = str(movement["entidad"]).upper()
                    except (KeyError, TypeError, ValueError):
                        continue
                    if entity == "INSUMO":
                        restore = -delta / 1000
                        current = conn.execute("SELECT stock FROM items WHERE id=? AND category='Insumo' AND active=1", (item_id,)).fetchone()
                        if current and float(current["stock"]) + restore < -0.000001:
                            raise ValueError("No se puede anular: la reversión dejaría un insumo con existencia negativa.")
                        if current:
                            conn.execute("UPDATE items SET stock=stock+? WHERE id=? AND category='Insumo'", (restore, item_id))
                    elif entity == "PRODUCTO":
                        restore = -delta
                        current = conn.execute("SELECT stock FROM items WHERE id=? AND category!='Insumo' AND active=1", (item_id,)).fetchone()
                        if current and int(current["stock"]) + restore < 0:
                            raise ValueError("No se puede anular: la reversión dejaría un producto con existencia negativa.")
                        if current:
                            conn.execute("UPDATE items SET stock=stock+? WHERE id=? AND category!='Insumo'", (restore, item_id))
                conn.execute("UPDATE sync_sales SET cancelled_at=?,cancellation_reason=?,cancelled_by=? WHERE uuid=? AND cancelled_at IS NULL",
                             (cancelled_at, reason, user["username"], sale_uuid))
                change = {"uuid": sale_uuid, "turnoUuid": sale["shift_uuid"], "anuladaEn": cancelled_at,
                          "motivo": reason, "anuladaPor": user["username"], "nueva": None, "corrigeUuid": None}
                conn.execute("INSERT OR REPLACE INTO sync_sale_changes(uuid,employee_id,payload_json) VALUES(?,?,?)",
                             (sale_uuid, sale["employee_id"], canonical_json(change).decode("utf-8")))
        except (ValueError, TypeError, json.JSONDecodeError) as exc:
            return flash_redirect(request, "/ventas", str(exc))
        return redirect("/ventas", notice="mobile_sale_cancelled")

    @app.post("/ventas/movil/{sale_uuid}/corregir")
    async def correct_mobile_sale(request: Request, sale_uuid: str):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            sale_uuid = str(uuidlib.UUID(sale_uuid))
            payment = data.get("payment_method", "")
            if payment not in {"EFECTIVO", "TRANSFERENCIA"}:
                raise ValueError("Forma de pago no válida.")
            corrected_at = int(time.time() * 1000)
            new_uuid = str(uuidlib.uuid4())
            reason = data.get("reason", "").strip()
            if not 3 <= len(reason) <= 160:
                raise ValueError("Escribe un motivo de corrección de 3 a 160 caracteres.")
            with db.transaction() as conn:
                original = conn.execute("SELECT * FROM sync_sales WHERE uuid=?", (sale_uuid,)).fetchone()
                if not original or original["cancelled_at"] is not None:
                    raise ValueError("La venta no existe o ya fue anulada.")
                wrapper = json.loads(original["payload_json"] or "{}")
                old_sale = wrapper.get("venta", {}) if isinstance(wrapper, dict) else {}
                old_details = old_sale.get("detalles", []) if isinstance(old_sale, dict) else []
                old_movements = wrapper.get("movimientos", []) if isinstance(wrapper, dict) else []
                if not isinstance(old_details, list) or not old_details or not isinstance(old_movements, list):
                    raise ValueError("Esta venta solo permite anulación; no se recibió un detalle corregible.")
                if any(not isinstance(line, dict) or line.get("clase") != "PRODUCTO" for line in old_details) or any(not isinstance(movement, dict) or str(movement.get("entidad", "")).upper() != "PRODUCTO" for movement in old_movements):
                    raise ValueError("Por ahora la corrección de cantidades admite ventas solo de productos simples, sin recetas ni insumos; puedes anularla con motivo.")
                expected_old_deltas: dict[int, int] = {}
                for line in old_details:
                    item_id = int(line["productoId"])
                    quantity = int(line["cantidad"])
                    if quantity <= 0:
                        raise ValueError("La cantidad original de una línea no es válida.")
                    expected_old_deltas[item_id] = expected_old_deltas.get(item_id, 0) - quantity
                actual_old_deltas: dict[int, int] = {}
                for movement in old_movements:
                    item_id = int(movement["entidadId"])
                    delta = int(movement["delta"])
                    actual_old_deltas[item_id] = actual_old_deltas.get(item_id, 0) + delta
                if actual_old_deltas != expected_old_deltas:
                    raise ValueError("Los movimientos de stock no coinciden con el detalle; solo se puede anular esta venta.")
                item_ids = set(expected_old_deltas)
                if conn.execute(f"SELECT COUNT(*) FROM recipes WHERE product_id IN ({','.join('?' for _ in item_ids)})", tuple(item_ids)).fetchone()[0]:
                    raise ValueError("Esta venta usa recetas; anúlala con motivo en lugar de aplicar una corrección parcial.")
                selected: list[tuple[dict[str, Any], int]] = []
                for index, line in enumerate(old_details):
                    quantity = integer(data.get(f"quantity_{index}"), maximum=100_000)
                    if quantity:
                        selected.append((line, quantity))
                if not selected:
                    raise ValueError("La corrección debe conservar al menos una línea con cantidad mayor que cero.")
                if len(selected) > 60:
                    raise ValueError("La venta corregida supera el límite de líneas.")
                old_deltas: dict[int, int] = {}
                for movement in old_movements:
                    item_id, delta = int(movement["entidadId"]), int(movement["delta"])
                    old_deltas[item_id] = old_deltas.get(item_id, 0) + delta
                new_deltas: dict[int, int] = {}
                new_details = []
                total = 0
                for line, quantity in selected:
                    item_id = int(line["productoId"])
                    unit_price = int(line.get("precioUnitarioCent", 0))
                    if unit_price < 0:
                        raise ValueError("El precio de una línea no es válido.")
                    item = conn.execute("SELECT name,active FROM items WHERE id=? AND category='Producto'", (item_id,)).fetchone()
                    if not item:
                        raise ValueError("No se puede corregir porque falta el producto en el catálogo.")
                    changed = dict(line)
                    changed["cantidad"] = quantity
                    changed["nombre"] = str(changed.get("nombre") or item["name"])
                    new_details.append(changed)
                    total += unit_price * quantity
                    new_deltas[item_id] = new_deltas.get(item_id, 0) - quantity
                new_movements = []
                for item_id in set(old_deltas) | set(new_deltas):
                    item = conn.execute("SELECT name,stock FROM items WHERE id=? AND active=1 AND category!='Insumo'", (item_id,)).fetchone()
                    if not item:
                        raise ValueError("No se puede corregir una venta con un producto retirado del catálogo.")
                    change_delta = new_deltas.get(item_id, 0) - old_deltas.get(item_id, 0)
                    new_stock = int(item["stock"]) + change_delta
                    if new_stock < 0:
                        raise ValueError(f"La corrección dejaría sin existencia a {item['name']}.")
                    conn.execute("UPDATE items SET stock=? WHERE id=?", (new_stock, item_id))
                    if item_id in new_deltas:
                        new_movements.append({"id": 0, "fecha": corrected_at, "tipo": "VENTA", "entidad": "PRODUCTO",
                                              "entidadId": item_id, "nombre": item["name"], "delta": new_deltas[item_id],
                                              "existencia": new_stock, "turnoId": old_sale.get("turnoId"), "nota": "Corrección de venta"})
                transaction = old_sale.get("transaccion")
                if payment == "EFECTIVO":
                    transaction = None
                elif transaction is None:
                    raise ValueError("Para corregir a transferencia se necesita la transacción original; conserva el método actual o anula la venta.")
                elif isinstance(transaction, dict):
                    transaction = dict(transaction, importeCent=total)
                corrected_sale = dict(old_sale)
                corrected_sale.update({"fecha": corrected_at, "metodoPago": payment, "detalles": new_details,
                                       "transaccion": transaction, "uuid": new_uuid, "empleadoId": original["employee_id"],
                                       "sincronizado": True, "anuladaEn": None, "motivoAnulacion": None,
                                       "anuladaPor": None, "corrigeVentaId": old_sale.get("id")})
                turn_uuid = original["shift_uuid"]
                corrected_wrapper = {"uuid": new_uuid, "turnoUuid": turn_uuid, "venta": corrected_sale, "movimientos": new_movements}
                conn.execute("UPDATE sync_sales SET cancelled_at=?,cancellation_reason=?,cancelled_by=? WHERE uuid=?",
                             (corrected_at, reason, user["username"], sale_uuid))
                conn.execute("INSERT INTO sync_sales(uuid,employee_id,employee_name,shift_uuid,sale_at_ms,payment_method,total_cents,payload_json) VALUES(?,?,?,?,?,?,?,?)",
                             (new_uuid, original["employee_id"], original["employee_name"], turn_uuid, corrected_at, payment, total,
                              canonical_json(corrected_wrapper).decode("utf-8")))
                cancelled_change = {"uuid": sale_uuid, "turnoUuid": turn_uuid, "anuladaEn": corrected_at,
                                    "motivo": reason, "anuladaPor": user["username"], "nueva": None, "corrigeUuid": None}
                replacement_change = {"uuid": new_uuid, "turnoUuid": turn_uuid, "nueva": corrected_sale, "corrigeUuid": sale_uuid}
                conn.execute("INSERT INTO sync_sale_changes(uuid,employee_id,payload_json) VALUES(?,?,?)",
                             (sale_uuid, original["employee_id"], canonical_json(cancelled_change).decode("utf-8")))
                conn.execute("INSERT INTO sync_sale_changes(uuid,employee_id,payload_json) VALUES(?,?,?)",
                             (new_uuid, original["employee_id"], canonical_json(replacement_change).decode("utf-8")))
        except (ValueError, TypeError, KeyError, json.JSONDecodeError, sqlite3.IntegrityError) as exc:
            return flash_redirect(request, "/ventas", str(exc))
        return redirect("/ventas", notice="mobile_sale_corrected")

    @app.post("/turno/abrir")
    async def open_shift(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            opening_cash = money_to_cents(data.get("opening_cash", "0"))
            with db.transaction() as conn:
                if conn.execute("SELECT 1 FROM shifts WHERE closed_at IS NULL LIMIT 1").fetchone():
                    raise ValueError("Ya hay un turno abierto.")
                conn.execute("INSERT INTO shifts(opening_cash_cents) VALUES(?)", (opening_cash,))
        except ValueError as exc:
            return flash_redirect(request, "/", str(exc))
        return redirect("/", notice="shift_opened")

    @app.post("/turno/cerrar")
    async def close_shift(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            counted_cash = money_to_cents(data.get("counted_cash"))
            with db.transaction() as conn:
                row = conn.execute("SELECT id FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
                if not row:
                    raise ValueError("No hay un turno abierto para cerrar.")
                conn.execute("UPDATE shifts SET closed_at=?,counted_cash_cents=? WHERE id=?", (now_utc(), counted_cash, row["id"]))
        except ValueError as exc:
            return flash_redirect(request, "/", str(exc))
        return redirect("/", notice="shift_closed")

    @app.post("/caja/movimiento")
    async def cash_movement(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        movement_type = data.get("movement_type", "")
        reason = data.get("reason", "").strip()
        if movement_type not in {"ENTRADA", "SALIDA"} or not reason or len(reason) > 160:
            return flash_redirect(request, "/", "Especifica si es entrada o salida y agrega un motivo de hasta 160 caracteres.")
        try:
            amount = money_to_cents(data.get("amount"))
            if amount <= 0:
                raise ValueError("El movimiento debe ser mayor que cero.")
            with db.transaction() as conn:
                shift = conn.execute("SELECT id FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
                if not shift:
                    raise ValueError("Abre un turno para registrar un movimiento de caja.")
                conn.execute("INSERT INTO cash_movements(shift_id,movement_type,amount_cents,reason) VALUES(?,?,?,?)",
                             (shift["id"], movement_type, amount, reason))
        except ValueError as exc:
            return flash_redirect(request, "/", str(exc))
        return redirect("/", notice="cash_saved")

    @app.get("/turnos", response_class=HTMLResponse)
    async def shifts_page(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        with db.connect() as conn:
            shifts = conn.execute("""SELECT sh.*,
                (SELECT COALESCE(SUM(total_cents),0) FROM sales s WHERE s.shift_id=sh.id) sales_total,
                (SELECT COALESCE(SUM(total_cents),0) FROM sales s WHERE s.shift_id=sh.id AND s.payment_method='EFECTIVO') cash_sales,
                (SELECT COALESCE(SUM(CASE WHEN movement_type='ENTRADA' THEN amount_cents ELSE -amount_cents END),0) FROM cash_movements cm WHERE cm.shift_id=sh.id) cash_adjustments
                FROM shifts sh ORDER BY sh.id DESC LIMIT 100""").fetchall()
        result = []
        for row in shifts:
            shift = dict(row)
            shift["expected_cash_cents"] = shift["opening_cash_cents"] + shift["cash_sales"] + shift["cash_adjustments"]
            shift["difference_cents"] = (shift["counted_cash_cents"] - shift["expected_cash_cents"]) if shift["counted_cash_cents"] is not None else None
            result.append(shift)
        return templates.TemplateResponse(request, "shifts.html", context(request, user=user, section="turnos", shifts=result, notice=notice_text(request), notice_kind="success"))

    @app.get("/recetas", response_class=HTMLResponse)
    async def recipes_page(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        with db.connect() as conn:
            products = [dict(row) for row in conn.execute("SELECT id,name,category FROM items WHERE active=1 AND category IN ('Producto','Servicio') ORDER BY name")]
            ingredients = [dict(row) for row in conn.execute("SELECT id,name,unit,stock FROM items WHERE active=1 AND category='Insumo' ORDER BY name")]
            recipes = [dict(row) for row in conn.execute("""SELECT r.product_id,r.ingredient_id,r.quantity_milli,p.name product_name,i.name ingredient_name,i.unit
                FROM recipes r JOIN items p ON p.id=r.product_id JOIN items i ON i.id=r.ingredient_id
                WHERE p.active=1 AND i.active=1 ORDER BY p.name,i.name""")]
        return templates.TemplateResponse(request, "recipes.html", context(request, user=user, section="recetas", products=products, ingredients=ingredients, recipes=recipes, notice=notice_text(request), notice_kind="success"))

    @app.post("/recetas/guardar")
    async def save_recipe(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            product_id = integer(data.get("product_id"), minimum=1, maximum=2**31 - 1)
            ingredient_id = integer(data.get("ingredient_id"), minimum=1, maximum=2**31 - 1)
            quantity = decimal_quantity(data.get("quantity"))
            quantity_milli = int(Decimal(str(quantity)) * 1000)
            if not quantity_milli:
                raise ValueError("La cantidad debe ser al menos 0.001 por producto.")
            with db.transaction() as conn:
                product = conn.execute("SELECT 1 FROM items WHERE id=? AND active=1 AND category IN ('Producto','Servicio')", (product_id,)).fetchone()
                ingredient = conn.execute("SELECT 1 FROM items WHERE id=? AND active=1 AND category='Insumo'", (ingredient_id,)).fetchone()
                if not product or not ingredient:
                    raise ValueError("Selecciona un producto y un insumo activos.")
                conn.execute("INSERT INTO recipes(product_id,ingredient_id,quantity_milli) VALUES(?,?,?) ON CONFLICT(product_id,ingredient_id) DO UPDATE SET quantity_milli=excluded.quantity_milli", (product_id, ingredient_id, quantity_milli))
        except ValueError as exc:
            return flash_redirect(request, "/recetas", str(exc))
        return redirect("/recetas", notice="recipe_saved")

    @app.post("/recetas/{product_id}/{ingredient_id}/eliminar")
    async def delete_recipe(request: Request, product_id: int, ingredient_id: int):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        with db.transaction() as conn:
            conn.execute("DELETE FROM recipes WHERE product_id=? AND ingredient_id=?", (product_id, ingredient_id))
        return redirect("/recetas", notice="recipe_deleted")

    @app.get("/clientes", response_class=HTMLResponse)
    async def customers_page(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        with db.connect() as conn:
            customers = [dict(row) for row in conn.execute("SELECT * FROM customers WHERE active=1 ORDER BY name LIMIT 1000")]
        return templates.TemplateResponse(request, "customers.html", context(request, user=user, section="clientes", customers=customers, notice=notice_text(request), notice_kind="success"))

    @app.post("/clientes/nuevo")
    async def create_customer(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        name, phone, email, notes = (data.get(key, "").strip() for key in ("name", "phone", "email", "notes"))
        if not name or len(name) > 100 or len(phone) > 40 or len(email) > 160 or len(notes) > 500 or (email and ("@" not in email or any(ch.isspace() for ch in email))):
            return flash_redirect(request, "/clientes", "Revisa el nombre, teléfono, correo y nota del cliente.")
        with db.transaction() as conn:
            conn.execute("INSERT INTO customers(name,phone,email,notes) VALUES(?,?,?,?)", (name, phone, email, notes))
        return redirect("/clientes", notice="customer_added")

    @app.get("/clientes/{customer_id}/editar", response_class=HTMLResponse)
    async def edit_customer_page(request: Request, customer_id: int):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        with db.connect() as conn:
            customer = conn.execute("SELECT * FROM customers WHERE id=? AND active=1", (customer_id,)).fetchone()
        if not customer:
            return flash_redirect(request, "/clientes", "El cliente no existe.")
        return templates.TemplateResponse(request, "customer_edit.html", context(request, user=user, section="clientes", customer=dict(customer)))

    @app.post("/clientes/{customer_id}/editar")
    async def edit_customer(request: Request, customer_id: int):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        name, phone, email, notes = (data.get(key, "").strip() for key in ("name", "phone", "email", "notes"))
        if not name or len(name) > 100 or len(phone) > 40 or len(email) > 160 or len(notes) > 500 or (email and ("@" not in email or any(ch.isspace() for ch in email))):
            return flash_redirect(request, f"/clientes/{customer_id}/editar", "Revisa el nombre, teléfono, correo y nota del cliente.")
        with db.transaction() as conn:
            updated = conn.execute("UPDATE customers SET name=?,phone=?,email=?,notes=? WHERE id=? AND active=1", (name, phone, email, notes, customer_id)).rowcount
        if updated != 1:
            return flash_redirect(request, "/clientes", "El cliente no existe.")
        return redirect("/clientes", notice="customer_saved")

    @app.post("/clientes/{customer_id}/retirar")
    async def retire_customer(request: Request, customer_id: int):
        if not current_user(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        with db.transaction() as conn:
            conn.execute("UPDATE customers SET active=0 WHERE id=? AND active=1", (customer_id,))
        return redirect("/clientes", notice="customer_retired")

    @app.get("/clientes.csv")
    async def customers_csv(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        with db.connect() as conn:
            rows = conn.execute("SELECT name,phone,email,notes,created_at FROM customers WHERE active=1 ORDER BY name").fetchall()
        output = io.StringIO(newline="")
        writer = csv.writer(output)
        writer.writerow(["Nombre", "Teléfono", "Correo", "Notas", "Fecha de alta"])
        writer.writerows([[csv_text(value) for value in row] for row in rows])
        return Response("\ufeff" + output.getvalue(), media_type="text/csv; charset=utf-8", headers={"Content-Disposition": 'attachment; filename="clientes.csv"'})

    @app.get("/distribucion", response_class=HTMLResponse)
    async def distribution_page(request: Request):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        with db.connect() as conn:
            photos = [dict(row) for row in conn.execute("SELECT entity,entity_id,filename,byte_length,sha256,created_at FROM catalog_photos ORDER BY created_at DESC")]
            values = {row["key"]: row["value"] for row in conn.execute("SELECT key,value FROM app_meta WHERE key IN ('apk_version_code','apk_sha256','apk_bytes')")}
        return templates.TemplateResponse(request, "distribution.html", context(request, user=user, section="distribucion", photos=photos, apk=values, notice=notice_text(request), notice_kind="success"))

    @app.post("/distribucion/foto")
    async def upload_catalog_photo(request: Request):
        if not require_admin(request):
            return JSONResponse({"detail": "Se requiere una sesión administradora."}, status_code=403)
        if not valid_csrf(request.session.get("csrf"), request.headers.get("x-csrf-token")):
            return JSONResponse({"detail": "Solicitud no válida."}, status_code=403)
        entity = request.headers.get("x-entity", "")
        try:
            entity_id = int(request.headers.get("x-entity-id", "0"))
        except ValueError:
            return JSONResponse({"detail": "ID de catálogo no válido."}, status_code=400)
        if entity not in {"producto", "servicio"} or entity_id <= 0 or request.headers.get("content-type", "").split(";")[0] != "image/jpeg":
            return JSONResponse({"detail": "Se requiere un producto/servicio y una imagen JPEG."}, status_code=400)
        body = bytearray()
        async for chunk in request.stream():
            body.extend(chunk)
            if len(body) > 15 * 1024 * 1024:
                return JSONResponse({"detail": "La foto supera el máximo de 15 MiB."}, status_code=413)
        if len(body) < 4 or not body.startswith(b"\xff\xd8") or not body.rstrip().endswith(b"\xff\xd9"):
            return JSONResponse({"detail": "El archivo no parece una imagen JPEG completa."}, status_code=400)
        with db.connect() as conn:
            if entity == "producto":
                exists = conn.execute("SELECT 1 FROM items WHERE id=? AND active=1 AND category NOT IN ('Insumo','Servicio')", (entity_id,)).fetchone()
            else:
                exists = conn.execute("SELECT 1 FROM sync_services WHERE service_id=? AND deleted=0", (entity_id,)).fetchone()
            old = conn.execute("SELECT filename FROM catalog_photos WHERE entity=? AND entity_id=?", (entity, entity_id)).fetchone()
        if not exists:
            return JSONResponse({"detail": "No se encontró un artículo móvil activo con ese ID."}, status_code=404)
        filename = f"{uuidlib.uuid4()}.jpg"
        photo_dir = config.data_dir / "catalog_photos"
        photo_dir.mkdir(parents=True, exist_ok=True)
        destination = photo_dir / filename
        temporary = destination.with_suffix(".tmp")
        temporary.write_bytes(body)
        temporary.replace(destination)
        digest = hashlib.sha256(body).hexdigest()
        try:
            with db.transaction() as conn:
                conn.execute("INSERT INTO catalog_photos(entity,entity_id,filename,byte_length,sha256) VALUES(?,?,?,?,?) ON CONFLICT(entity,entity_id) DO UPDATE SET filename=excluded.filename,byte_length=excluded.byte_length,sha256=excluded.sha256,created_at=strftime('%Y-%m-%dT%H:%M:%fZ','now')",
                             (entity, entity_id, filename, len(body), digest))
        except Exception:
            destination.unlink(missing_ok=True)
            raise
        if old and old["filename"] != filename:
            (photo_dir / old["filename"]).unlink(missing_ok=True)
        return JSONResponse({"message": "Foto cargada y disponible para la siguiente sincronización.", "filename": filename, "bytes": len(body), "sha256": digest})

    @app.post("/distribucion/apk")
    async def upload_update_apk(request: Request):
        if not require_admin(request):
            return JSONResponse({"detail": "Se requiere una sesión administradora."}, status_code=403)
        if not valid_csrf(request.session.get("csrf"), request.headers.get("x-csrf-token")):
            return JSONResponse({"detail": "Solicitud no válida."}, status_code=403)
        try:
            version_code = int(request.headers.get("x-version-code", "0"))
        except ValueError:
            return JSONResponse({"detail": "Version code no válido."}, status_code=400)
        if version_code <= 0 or request.headers.get("content-type", "").split(";")[0] not in {"application/vnd.android.package-archive", "application/octet-stream"}:
            return JSONResponse({"detail": "Selecciona un APK y especifica su version code."}, status_code=400)
        body = bytearray()
        async for chunk in request.stream():
            body.extend(chunk)
            if len(body) > 300 * 1024 * 1024:
                return JSONResponse({"detail": "El APK supera el máximo de 300 MiB."}, status_code=413)
        try:
            with zipfile.ZipFile(io.BytesIO(body)) as apk_file:
                names = set(apk_file.namelist())
                if "AndroidManifest.xml" not in names:
                    raise ValueError("Falta AndroidManifest.xml.")
        except (zipfile.BadZipFile, ValueError) as exc:
            return JSONResponse({"detail": f"El archivo no parece un APK válido: {exc}"}, status_code=400)
        apk_dir = config.data_dir / "updates"
        apk_dir.mkdir(parents=True, exist_ok=True)
        destination = apk_dir / "shalev.apk"
        temporary = destination.with_suffix(".tmp")
        temporary.write_bytes(body)
        temporary.replace(destination)
        digest = hashlib.sha256(body).hexdigest()
        with db.transaction() as conn:
            for key, value in (("apk_version_code", str(version_code)), ("apk_sha256", digest), ("apk_bytes", str(len(body)))):
                conn.execute("INSERT INTO app_meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (key, value))
        return JSONResponse({"message": f"APK guardado (version code {version_code}); las secundarias recibirán la oferta al sincronizar.", "sha256": digest, "bytes": len(body)})

    @app.get("/respaldo", response_class=HTMLResponse)
    async def backup_page(request: Request):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        return templates.TemplateResponse(request, "backup.html", context(request, user=user, section="respaldo"))

    @app.post("/respaldo/descargar")
    async def backup_download(request: Request):
        if not require_admin(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        if data.get("passphrase", "") != data.get("confirm_passphrase", ""):
            return flash_redirect(request, "/respaldo", "Las contraseñas no coinciden.")
        try:
            backup = db.export_backup(data.get("passphrase", ""))
        except ValueError as exc:
            return flash_redirect(request, "/respaldo", str(exc))
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        return Response(backup, media_type="application/octet-stream", headers={"Content-Disposition": f'attachment; filename="dshalev-{timestamp}.dshbackup"'})

    @app.post("/respaldo/restaurar")
    async def backup_restore(request: Request):
        if not require_admin(request):
            return JSONResponse({"detail": "Se requiere una sesión administradora."}, status_code=403)
        if not valid_csrf(request.session.get("csrf"), request.headers.get("x-csrf-token")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        encoded_passphrase = request.headers.get("x-backup-passphrase-b64", "")
        try:
            if len(encoded_passphrase) > 8192:
                raise ValueError("Contraseña demasiado larga.")
            passphrase = base64.b64decode(encoded_passphrase, validate=True).decode("utf-8")
        except (ValueError, UnicodeDecodeError) as exc:
            return JSONResponse({"detail": "La contraseña del respaldo no tiene un formato válido."}, status_code=400)
        chunks = bytearray()
        async for chunk in request.stream():
            chunks.extend(chunk)
            if len(chunks) > 1024 * 1024 * 1024:
                return JSONResponse({"detail": "El respaldo supera el límite de 1 GiB."}, status_code=413)
        try:
            db.restore_backup(bytes(chunks), passphrase)
        except ValueError as exc:
            return JSONResponse({"detail": str(exc)}, status_code=400)
        request.session.clear()
        request.session["csrf"] = csrf_token()
        return JSONResponse({"message": "Respaldo restaurado. Vuelve a iniciar sesión con los datos incluidos en el respaldo."})

    def report_date(value: str | None, fallback: str) -> str:
        value = value or fallback
        try:
            parsed = datetime.strptime(value, "%Y-%m-%d")
        except ValueError as exc:
            raise ValueError("Usa fechas con formato AAAA-MM-DD.") from exc
        if parsed.strftime("%Y-%m-%d") != value:
            raise ValueError("Usa fechas con formato AAAA-MM-DD.")
        return value

    def report_sales(start: str, end: str) -> list[dict[str, Any]]:
        with db.connect() as conn:
            web_rows = conn.execute("""SELECT s.created_at fecha,'Escritorio' origen,
                COALESCE(GROUP_CONCAT(sl.item_name || ' × ' || sl.quantity, '; '),'Venta') descripcion,
                s.payment_method pago,s.total_cents total_cents,c.name cliente
                FROM sales s LEFT JOIN sale_lines sl ON sl.sale_id=s.id LEFT JOIN customers c ON c.id=s.customer_id
                WHERE date(s.created_at) BETWEEN ? AND ? GROUP BY s.id ORDER BY s.created_at""", (start, end)).fetchall()
            mobile_rows = conn.execute("""SELECT datetime(s.sale_at_ms/1000,'unixepoch','localtime') fecha,
                'Móvil · ' || s.employee_name origen,s.payment_method pago,s.total_cents total_cents,'' cliente,s.payload_json
                FROM sync_sales s WHERE s.cancelled_at IS NULL AND date(s.sale_at_ms/1000,'unixepoch','localtime') BETWEEN ? AND ? ORDER BY s.sale_at_ms""", (start, end)).fetchall()
        rows = [dict(row) for row in web_rows]
        for row in mobile_rows:
            data = dict(row)
            try:
                sale = json.loads(data.pop("payload_json", "{}")).get("venta", {})
                details = sale.get("detalles", []) if isinstance(sale, dict) else []
                transaction = sale.get("transaccion", {}) if isinstance(sale, dict) else {}
                data["cliente"] = str(transaction.get("clienteNombre", "") or "") if isinstance(transaction, dict) else ""
                data["descripcion"] = "; ".join(f"{str(d.get('nombre','Artículo'))[:80]} × {int(d.get('cantidad',0))}" for d in details if isinstance(d, dict)) or "Venta móvil"
            except (json.JSONDecodeError, TypeError, ValueError):
                data["descripcion"] = "Venta móvil"
            rows.append(data)
        return sorted(rows, key=lambda item: str(item.get("fecha", "")))

    @app.get("/reportes", response_class=HTMLResponse)
    async def reports_page(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        today = datetime.now(timezone.utc).date().isoformat()
        try:
            start = report_date(request.query_params.get("desde"), today)
            end = report_date(request.query_params.get("hasta"), today)
            if start > end:
                raise ValueError("La fecha inicial debe ser anterior o igual a la final.")
        except ValueError as exc:
            return HTMLResponse(str(exc), status_code=422)
        sales_rows = report_sales(start, end)
        total = sum(int(row["total_cents"]) for row in sales_rows)
        by_payment: dict[str, int] = {}
        for row in sales_rows:
            by_payment[row["pago"]] = by_payment.get(row["pago"], 0) + int(row["total_cents"])
        return templates.TemplateResponse(request, "reports.html", context(request, user=user, section="reportes", sales=sales_rows, start=start, end=end, sales_total=total, payment_totals=by_payment, notice=notice_text(request), notice_kind="success"))

    @app.get("/reportes/ventas.csv")
    async def sales_csv(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        today = datetime.now(timezone.utc).date().isoformat()
        try:
            start = report_date(request.query_params.get("desde"), today)
            end = report_date(request.query_params.get("hasta"), today)
            if start > end:
                raise ValueError("La fecha inicial debe ser anterior o igual a la final.")
        except ValueError as exc:
            return Response(str(exc), status_code=422, media_type="text/plain")
        output = io.StringIO(newline="")
        writer = csv.writer(output)
        writer.writerow(["Fecha", "Origen", "Cliente", "Descripción", "Pago", "Total (CUP)"])
        for row in report_sales(start, end):
            writer.writerow([csv_text(row.get("fecha", "")), csv_text(row.get("origen", "Móvil")), csv_text(row.get("cliente", "")), csv_text(row.get("descripcion", "Venta")), csv_text(row["pago"]), f"{int(row['total_cents']) / 100:.2f}"])
        return Response("\ufeff" + output.getvalue(), media_type="text/csv; charset=utf-8", headers={"Content-Disposition": f'attachment; filename="ventas-{start}-{end}.csv"'})

    @app.get("/reportes/ventas.xlsx")
    async def sales_xlsx(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        today = datetime.now(timezone.utc).date().isoformat()
        try:
            start = report_date(request.query_params.get("desde"), today)
            end = report_date(request.query_params.get("hasta"), today)
            if start > end:
                raise ValueError("La fecha inicial debe ser anterior o igual a la final.")
        except ValueError as exc:
            return Response(str(exc), status_code=422, media_type="text/plain")
        rows = report_sales(start, end)
        totals: dict[str, int] = {}
        for row in rows:
            totals[row["pago"]] = totals.get(row["pago"], 0) + int(row["total_cents"])
        summary = [["Campo", "Valor"], ["Desde", start], ["Hasta", end], ["Cantidad de ventas", len(rows)],
                   ["Total (CUP)", sum(int(row["total_cents"]) for row in rows) / 100], [], ["Método de pago", "Total (CUP)"]]
        summary.extend([[method, amount / 100] for method, amount in sorted(totals.items())])
        sales = [["Fecha", "Origen", "Cliente", "Descripción", "Pago", "Total (CUP)"]]
        sales.extend([[row.get("fecha", ""), row.get("origen", "Móvil"), row.get("cliente", ""), row.get("descripcion", "Venta"), row["pago"], int(row["total_cents"]) / 100] for row in rows])
        content = build_xlsx([("Resumen", summary), ("Ventas", sales)])
        return Response(content, media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        headers={"Content-Disposition": f'attachment; filename="ventas-{start}-{end}.xlsx"'})

    @app.get("/inventario.xlsx")
    async def inventory_xlsx(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        with db.connect() as conn:
            items = conn.execute("SELECT name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock FROM items WHERE active=1 ORDER BY name").fetchall()
        rows: list[list[Any]] = [["Nombre", "Categoría", "Unidad", "Existencia", "Precio (CUP)", "Costo (CUP)", "Stock bajo", "Stock crítico"]]
        rows.extend([[item["name"], item["category"], item["unit"], item["stock"], item["sale_price_cents"] / 100,
                      item["cost_cents"] / 100, item["low_stock"], item["critical_stock"]] for item in items])
        return Response(build_xlsx([("Inventario", rows)]), media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        headers={"Content-Disposition": 'attachment; filename="inventario.xlsx"'})

    @app.get("/clientes.xlsx")
    async def customers_xlsx(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        with db.connect() as conn:
            rows = conn.execute("SELECT name,phone,email,notes,created_at FROM customers WHERE active=1 ORDER BY name").fetchall()
        table: list[list[Any]] = [["Nombre", "Teléfono", "Correo", "Notas", "Fecha de alta"]]
        table.extend([list(row) for row in rows])
        return Response(build_xlsx([("Clientes", table)]), media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        headers={"Content-Disposition": 'attachment; filename="clientes.xlsx"'})

    @app.get("/inventario.csv")
    async def inventory_csv(request: Request):
        if not current_user(request):
            return redirect("/entrar")
        with db.connect() as conn:
            items = conn.execute("SELECT name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock FROM items WHERE active=1 ORDER BY name").fetchall()
        output = io.StringIO(newline="")
        writer = csv.writer(output)
        writer.writerow(["Nombre", "Categoría", "Unidad", "Existencia", "Precio (CUP)", "Costo (CUP)", "Stock bajo", "Stock crítico"])
        for item in items:
            writer.writerow([csv_text(item["name"]), csv_text(item["category"]), csv_text(item["unit"]), item["stock"], f"{item['sale_price_cents']/100:.2f}", f"{item['cost_cents']/100:.2f}", item["low_stock"], item["critical_stock"]])
        return Response("\ufeff" + output.getvalue(), media_type="text/csv; charset=utf-8", headers={"Content-Disposition": 'attachment; filename="inventario.csv"'})

    @app.get("/equipo", response_class=HTMLResponse)
    async def team_page(request: Request):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        with db.connect() as conn:
            employee_rows = conn.execute("SELECT * FROM employees ORDER BY slot").fetchall()
            limit = int(conn.execute("SELECT value FROM app_meta WHERE key='secondary_limit'").fetchone()[0])
            receipt_rows = conn.execute("""SELECT r.*,COALESCE(e.name,x.employee_name,'Empleado') employee_name
                FROM sync_receipts r LEFT JOIN employees e ON e.id=r.employee_id
                LEFT JOIN retired_employees x ON x.employee_id=r.employee_id
                ORDER BY r.received_at DESC LIMIT 30""").fetchall()
            mobile_shifts = [dict(row) for row in conn.execute("""SELECT sh.*,COALESCE(e.name,r.employee_name,sh.employee_name,'Empleado') employee_name FROM sync_shifts sh
                LEFT JOIN employees e ON e.id=sh.employee_id LEFT JOIN retired_employees r ON r.employee_id=sh.employee_id ORDER BY sh.opened_ms DESC LIMIT 50""")]
            close_requests = [dict(row) for row in conn.execute("""SELECT r.*,e.name employee_name FROM mobile_close_requests r
                JOIN employees e ON e.id=r.employee_id WHERE r.decision IS NULL ORDER BY r.requested_at""")]
            mobile_cash = [dict(row) for row in conn.execute("""SELECT c.*,COALESCE(e.name,r.employee_name,c.employee_name,'Empleado') employee_name FROM sync_cash_movements c
                LEFT JOIN employees e ON e.id=c.employee_id LEFT JOIN retired_employees r ON r.employee_id=c.employee_id ORDER BY c.created_ms DESC LIMIT 50""")]
        for movement in mobile_cash:
            movement["created_at_display"] = datetime.fromtimestamp(movement["created_ms"] / 1000, timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M") if movement["created_ms"] else "—"
        for shift in mobile_shifts:
            try:
                shift["turn_payload"] = json.loads(shift["payload_json"]).get("turno", {})
                shift["count_payload"] = json.loads(shift["count_json"]) if shift["count_json"] else None
            except (json.JSONDecodeError, TypeError):
                shift["turn_payload"], shift["count_payload"] = {}, None
        sync_activities = []
        for row in receipt_rows:
            activity = dict(row)
            try:
                payload = json.loads(activity["payload_json"] or "{}")
            except json.JSONDecodeError:
                payload = {}
            if activity["message_type"] == "turno":
                turn = payload.get("turno", {}) if isinstance(payload, dict) else {}
                activity["kind_label"] = "Turno cerrado" if isinstance(turn, dict) and turn.get("cerradoEn") is not None else "Turno enviado"
                activity["summary"] = str(turn.get("abiertoPor") or turn.get("cerradoPor") or "Resumen de turno")[:80] if isinstance(turn, dict) else "Resumen de turno"
            elif activity["message_type"] == "caja":
                movement = payload.get("mov", {}) if isinstance(payload, dict) else {}
                try:
                    amount = int(movement.get("importeCent", 0) or 0) if isinstance(movement, dict) else 0
                except (TypeError, ValueError):
                    amount = 0
                activity["kind_label"] = "Movimiento de caja"
                activity["summary"] = f"{str(movement.get('tipo', 'Movimiento'))[:20]} · {format_cup(amount)} · {str(movement.get('motivo', ''))[:60]}" if isinstance(movement, dict) else "Movimiento de caja"
            else:
                activity["kind_label"] = "Venta móvil"
                activity["summary"] = "Recibida; detalle en Ventas"
            sync_activities.append(activity)
        employees = []
        for row in employee_rows:
            employee = dict(row)
            enabled = set(json.loads(employee["permissions_json"] or "[]"))
            employee["enabled_permissions"] = {key for key, protocol_name in PERMISSION_PROTOCOL.items() if protocol_name in enabled}
            employees.append(employee)
        return templates.TemplateResponse(request, "employees.html", context(request, user=user, section="equipo", employees=employees, permissions=PERMISSIONS, limit=limit, sync_activities=sync_activities, mobile_shifts=mobile_shifts, close_requests=close_requests, mobile_cash=mobile_cash, now_ms=int(time.time() * 1000), sync_port=app.state.sync_server.sync_port if app.state.sync_server else None, sync_error=app.state.sync_server_error, notice=notice_text(request), notice_kind="success"))

    @app.post("/equipo/{employee_id}/cierre")
    async def decide_mobile_close(request: Request, employee_id: int):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        decision_text = data.get("decision")
        if decision_text not in {"aprobar", "rechazar"}:
            return flash_redirect(request, "/equipo", "Decisión de cierre no válida.")
        decision = 1 if decision_text == "aprobar" else 0
        with db.transaction() as conn:
            employee = conn.execute("SELECT 1 FROM employees WHERE id=? AND active=1", (employee_id,)).fetchone()
            if not employee:
                return flash_redirect(request, "/equipo", "La plaza móvil no está activa.")
            conn.execute("""INSERT INTO mobile_close_requests(employee_id,decision,decided_by,decided_at)
                VALUES(?,?,?,?) ON CONFLICT(employee_id) DO UPDATE SET decision=excluded.decision,
                decided_by=excluded.decided_by,decided_at=excluded.decided_at""",
                (employee_id, decision, user["username"], now_utc()))
        return redirect("/equipo", notice="mobile_close_approved" if decision else "mobile_close_rejected")

    @app.post("/equipo/{employee_id}/turnos/{shift_uuid}/conteo/aprobar")
    async def approve_mobile_count(request: Request, employee_id: int, shift_uuid: str):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            shift_uuid = str(uuidlib.UUID(shift_uuid))
            approved_at = int(time.time() * 1000)
            with db.transaction() as conn:
                row = conn.execute("SELECT closed_ms,count_json FROM sync_shifts WHERE employee_id=? AND uuid=?", (employee_id, shift_uuid)).fetchone()
                if not row or not row["closed_ms"] or not row["count_json"]:
                    raise ValueError("El turno no tiene un cierre con conteo pendiente de aprobar.")
                count = json.loads(row["count_json"])
                if not isinstance(count, dict) or not isinstance(count.get("inventario", []), list):
                    raise ValueError("El conteo recibido no tiene un formato válido.")
                if count.get("aprobadoEn") is not None:
                    raise ValueError("Este conteo ya fue aprobado.")
                cash = count.get("billetes", {})
                if not isinstance(cash, dict):
                    raise ValueError("El conteo de efectivo no es válido.")
                denominations = {1, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 5000, 20000}
                cash_total = 0
                for denomination, quantity in cash.items():
                    denom, count_bills = int(denomination), int(quantity)
                    if denom not in denominations or not 0 <= count_bills <= 10**7:
                        raise ValueError("El conteo de billetes no es válido.")
                    cash_total += denom * count_bills * 100
                other_cash = int(count.get("otroCent", 0))
                if other_cash < 0 or cash_total + other_cash != int(conn.execute("SELECT counted_cash_cents FROM sync_shifts WHERE employee_id=? AND uuid=?", (employee_id, shift_uuid)).fetchone()[0] or 0):
                    raise ValueError("El detalle de efectivo no coincide con el total contado.")
                approved = dict(count, aprobadoPor=user["username"], aprobadoEn=approved_at)
                encoded = canonical_json(approved).decode("utf-8")
                conn.execute("INSERT OR REPLACE INTO mobile_count_approvals(employee_id,shift_uuid,count_json,approved_by,approved_at) VALUES(?,?,?,?,?)",
                             (employee_id, shift_uuid, encoded, user["username"], approved_at))
        except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
            return flash_redirect(request, "/equipo", str(exc))
        return redirect("/equipo", notice="mobile_count_approved")

    @app.post("/equipo/nueva")
    async def add_employee(request: Request):
        if not require_admin(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        name = data.get("name", "").strip()
        permissions = [PERMISSION_PROTOCOL[key] for key in PERMISSIONS if data.get("perm_" + key) == "on"]
        if not name or len(name) > 60:
            return flash_redirect(request, "/equipo", "Escribe un nombre de hasta 60 caracteres.")
        try:
            with db.transaction() as conn:
                limit = int(conn.execute("SELECT value FROM app_meta WHERE key='secondary_limit'").fetchone()[0])
                used = {r[0] for r in conn.execute("SELECT slot FROM employees")}
                available = next((slot for slot in range(1, limit + 1) if slot not in used), None)
                if available is None:
                    raise ValueError("No quedan plazas configuradas; desactiva o elimina una secundaria antes de continuar.")
                conn.execute("INSERT INTO employees(slot,name,permissions_json) VALUES(?,?,?)", (available, name, json.dumps(permissions)))
        except ValueError as exc:
            return flash_redirect(request, "/equipo", str(exc))
        return redirect("/equipo", notice="employee_added")

    @app.post("/equipo/{employee_id}/permisos")
    async def update_employee_permissions(request: Request, employee_id: int):
        if not require_admin(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        selected = [PERMISSION_PROTOCOL[key] for key in PERMISSIONS if data.get("perm_" + key) == "on"]
        with db.transaction() as conn:
            result = conn.execute("UPDATE employees SET permissions_json=? WHERE id=? AND active=1",
                                  (json.dumps(selected), employee_id))
            if result.rowcount != 1:
                return flash_redirect(request, "/equipo", "La plaza no existe o está retirada.")
        return redirect("/equipo", notice="permissions_saved")

    @app.post("/equipo/{employee_id}/vincular")
    async def create_pairing(request: Request, employee_id: int):
        if not require_admin(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        if not app.state.sync_server:
            return flash_redirect(request, "/equipo", "Activa el modo LAN o remoto y reinicia DShalev para generar un QR.")
        token = secrets.token_bytes(16)
        expiry = int(time.time() * 1000) + 10 * 60 * 1000
        with db.transaction() as conn:
            employee = conn.execute("SELECT id FROM employees WHERE id=? AND active=1", (employee_id,)).fetchone()
            if not employee:
                return flash_redirect(request, "/equipo", "La plaza no existe o está desactivada.")
            conn.execute("UPDATE employees SET pairing_token=?,pairing_expires=? WHERE id=?",
                         (base64.b64encode(token).decode("ascii"), expiry, employee_id))
        return redirect("/equipo", notice="pairing_created")

    @app.post("/equipo/{employee_id}/retirar")
    async def remove_employee(request: Request, employee_id: int):
        if not require_admin(request):
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            with db.transaction() as conn:
                employee = conn.execute("SELECT id,name,employee_key FROM employees WHERE id=?", (employee_id,)).fetchone()
                if not employee:
                    return flash_redirect(request, "/equipo", "La plaza ya no existe.")
                if employee["employee_key"]:
                    conn.execute("INSERT OR REPLACE INTO retired_employees(employee_id,employee_name,employee_key) VALUES(?,?,?)",
                                 (employee_id, employee["name"], employee["employee_key"]))
                conn.execute("DELETE FROM employees WHERE id=?", (employee_id,))
        except sqlite3.IntegrityError:
            return flash_redirect(request, "/equipo", "No se pudo retirar la plaza porque todavía tiene registros asociados.")
        return redirect("/equipo", notice="employee_removed")

    @app.get("/equipo/{employee_id}/qr.svg")
    async def pairing_qr(request: Request, employee_id: int):
        if not require_admin(request):
            return JSONResponse({"detail": "Autenticación requerida"}, status_code=401)
        if not app.state.sync_server:
            return JSONResponse({"detail": "El listener SPVI no está activo"}, status_code=503)
        with db.connect() as conn:
            employee = conn.execute("SELECT * FROM employees WHERE id=? AND active=1", (employee_id,)).fetchone()
            if not employee or not employee["pairing_token"] or int(employee["pairing_expires"] or 0) <= int(time.time() * 1000):
                return JSONResponse({"detail": "QR vencido o no generado"}, status_code=404)
            business_id = conn.execute("SELECT value FROM app_meta WHERE key='business_id'").fetchone()[0]
            business_name = conn.execute("SELECT value FROM app_meta WHERE key='business_name'").fetchone()[0]
        live = load_live_config(config)
        hosts = list(get_lan_addresses())
        if live.public_host:
            hosts.insert(0, live.public_host)
        hosts = list(dict.fromkeys(hosts))[:12]
        if not hosts:
            return JSONResponse({"detail": "No hay dirección disponible para el QR"}, status_code=503)
        code = generate_pairing_code(
            business_id=business_id, business_name=business_name, hosts=hosts, port=app.state.sync_server.sync_port,
            employee_id=employee_id, employee_name=employee["name"],
            token=base64.b64decode(employee["pairing_token"], validate=True),
            expires_at_ms=int(employee["pairing_expires"]),
        )
        try:
            import qrcode
            from qrcode.image.svg import SvgPathImage
            qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=5, border=3)
            qr.add_data(code)
            image = qr.make_image(image_factory=SvgPathImage)
            output = io.BytesIO()
            image.save(output)
            return Response(output.getvalue(), media_type="image/svg+xml", headers={"Content-Disposition": "inline; filename=shalev-vinculacion.svg"})
        except ImportError:
            return JSONResponse({"detail": "No está instalado el generador QR"}, status_code=503)

    @app.get("/red", response_class=HTMLResponse)
    async def network_page(request: Request):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        current = load_live_config(config)
        return templates.TemplateResponse(request, "network.html", context(request, user=user, section="red", network_mode=current.network_mode, mode_description={"local": "Solo el navegador de este equipo", "lan": "Otros equipos de la misma red pueden conectarse", "remote": "HTTPS para acceso por VPN o una red con ruta y certificado público"}[current.network_mode], addresses=get_lan_addresses(), scheme="https" if current.tls_enabled else "http", port=app.state.port,
            tls_cert=current.tls_cert, tls_key=current.tls_key, public_host=current.public_host, sync_port=app.state.sync_server.sync_port if app.state.sync_server else None, notice=notice_text(request), notice_kind="success"))

    @app.post("/red")
    async def save_network(request: Request):
        user = require_admin(request)
        if not user:
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        mode = data.get("network_mode", "")
        cert = data.get("tls_cert", "").strip()
        key = data.get("tls_key", "").strip()
        public_host = data.get("public_host", "").strip().lower()
        if mode not in {"local", "lan", "remote"}:
            return flash_redirect(request, "/red", "Modo de acceso no válido.")
        if public_host and not PUBLIC_HOST_RE.fullmatch(public_host):
            return flash_redirect(request, "/red", "Escribe solo un nombre de host o dominio, sin esquema, ruta ni puerto.")
        if mode == "remote" and (not cert or not key or not Path(cert).is_file() or not Path(key).is_file()):
            return flash_redirect(request, "/red", "Para acceso remoto necesitas rutas válidas al certificado TLS y su clave privada. No abras el servidor a Internet sin HTTPS.")
        updated = save_config(config, network_mode=mode, tls_cert=cert, tls_key=key, public_host=public_host)
        app.state.config = updated
        return redirect("/red", notice="network_saved")

    @app.get("/api/v1/status")
    async def api_status(request: Request):
        if not current_user(request):
            return JSONResponse({"detail": "Autenticación requerida"}, status_code=401)
        with db.connect() as conn:
            shift = conn.execute("SELECT id,opened_at FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
            items = conn.execute("SELECT COUNT(*) FROM items WHERE active=1").fetchone()[0]
        return JSONResponse({"app": "DShalev", "version": "0.1.0", "shift_open": bool(shift), "active_items": items})

    @app.get("/api/v1/catalog")
    async def api_catalog(request: Request):
        if not current_user(request):
            return JSONResponse({"detail": "Autenticación requerida"}, status_code=401)
        with db.connect() as conn:
            rows = conn.execute("SELECT id,name,category,unit,stock,sale_price_cents,cost_cents,low_stock FROM items WHERE active=1 ORDER BY name").fetchall()
        return JSONResponse({"items": [dict(row) for row in rows]})

    return app


def load_live_config(config: AppConfig) -> AppConfig:
    from .config import load_config
    return load_config(config.settings_path.parent.parent)
