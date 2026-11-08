from __future__ import annotations

import base64
import io
import json
import logging
import re
import secrets
import socket
import sqlite3
import time
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
from .sync_protocol import generate_pairing_code
from .sync_server import ShalevSyncServer

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

    def notice_text(request: Request) -> str | None:
        allowed = {
            "item_added": "Artículo guardado.", "sale_saved": "Venta registrada y existencia actualizada.",
            "shift_opened": "Turno abierto.", "shift_closed": "Turno cerrado.", "employee_added": "Plaza secundaria creada.",
            "pairing_created": "QR de vinculación generado; caduca en 10 minutos.",
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
            native_today = conn.execute("SELECT COUNT(*) count, COALESCE(SUM(total_cents),0) total FROM sync_sales WHERE date(sale_at_ms/1000,'unixepoch','localtime')=?", (today,)).fetchone()
            today_count = today_row["count"] + native_today["count"]
            today_total = today_row["total"] + native_today["total"]
            sales = conn.execute("""SELECT s.*, COALESCE(group_concat(sl.item_name || ' × ' || sl.quantity, ', '),'Venta') summary
                FROM sales s LEFT JOIN sale_lines sl ON sl.sale_id=s.id
                GROUP BY s.id ORDER BY s.id DESC LIMIT 6""").fetchall()
        mode = config.network_mode
        mode_labels = {"local": "solo este equipo", "lan": "red local", "remote": "remoto con HTTPS"}
        notice = notice_text(request)
        return templates.TemplateResponse(request, "dashboard.html", context(
            request, user=user, section="inicio", notice=notice, notice_kind="success" if notice else None,
            shift=dict(shift) if shift else None, item_count=item_count, low_count=low_count,
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
            if not name or len(name) > 80 or not unit or len(unit) > 20 or category not in {"Producto", "Insumo"}:
                raise ValueError("Revisa el nombre, la categoría y la unidad del artículo.")
            sale_price = money_to_cents(data.get("sale_price"))
            cost = money_to_cents(data.get("cost"))
            stock = integer(data.get("stock"), maximum=10**9)
            low = integer(data.get("low_stock"), maximum=10**9)
            critical = integer(data.get("critical_stock", "1"), maximum=10**9)
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
        with db.connect() as conn:
            shift = conn.execute("SELECT * FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
            items = conn.execute("SELECT id,name,stock,unit,sale_price_cents FROM items WHERE active=1 AND stock>0 ORDER BY name").fetchall()
            sales = conn.execute("""SELECT s.*, COALESCE(group_concat(sl.item_name || ' × ' || sl.quantity, ', '),'Venta') summary
                FROM sales s LEFT JOIN sale_lines sl ON sl.sale_id=s.id GROUP BY s.id ORDER BY s.id DESC LIMIT 50""").fetchall()
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
            summary = ", ".join(
                f"{str(detail.get('nombre', 'Artículo'))[:80]} × {int(detail.get('cantidad', 0))}"
                for detail in details[:50] if isinstance(detail, dict)
            ) or "Venta móvil"
            native["created_at"] = datetime.fromtimestamp(native["sale_at_ms"] / 1000, timezone.utc).astimezone().isoformat(timespec="seconds")
            native["summary"] = summary
            native["_sort"] = native["sale_at_ms"] / 1000
            native["origin"] = f"App secundaria · {native['employee_name'] or 'Empleado'}"
            recent.append(native)
        recent.sort(key=lambda sale: sale["_sort"], reverse=True)
        for sale in recent:
            sale.pop("_sort", None)
        return templates.TemplateResponse(request, "sales.html", context(request, user=user, section="ventas", shift=dict(shift) if shift else None, items=[dict(r) for r in items], sales=recent[:50], notice=notice_text(request), notice_kind="success"))

    @app.post("/ventas/nueva")
    async def create_sale(request: Request):
        user = current_user(request)
        if not user:
            return redirect("/entrar")
        data = await form_data(request)
        if not valid_csrf(request.session.get("csrf"), data.get("csrf")):
            return JSONResponse({"detail": "Solicitud no válida"}, status_code=403)
        try:
            item_id = integer(data.get("item_id"), minimum=1, maximum=2**31-1)
            quantity = integer(data.get("quantity"), minimum=1, maximum=100_000)
        except ValueError as exc:
            return flash_redirect(request, "/ventas", str(exc))
        payment = data.get("payment_method", "")
        if payment not in {"EFECTIVO", "TRANSFERENCIA"}:
            return flash_redirect(request, "/ventas", "Forma de pago no válida.")
        try:
            with db.transaction() as conn:
                shift = conn.execute("SELECT id FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
                item = conn.execute("SELECT * FROM items WHERE id=? AND active=1", (item_id,)).fetchone()
                if not shift:
                    raise ValueError("Abre un turno antes de registrar ventas.")
                if not item:
                    raise ValueError("El artículo ya no está disponible.")
                if item["stock"] < quantity:
                    raise ValueError("No hay existencia suficiente para esa cantidad.")
                total = item["sale_price_cents"] * quantity
                cursor = conn.execute("INSERT INTO sales(shift_id,total_cents,payment_method) VALUES(?,?,?)", (shift["id"], total, payment))
                conn.execute("INSERT INTO sale_lines(sale_id,item_id,item_name,quantity,unit_price_cents) VALUES(?,?,?,?,?)", (cursor.lastrowid, item_id, item["name"], quantity, item["sale_price_cents"]))
                updated = conn.execute("UPDATE items SET stock=stock-? WHERE id=? AND stock>=?", (quantity, item_id, quantity)).rowcount
                if updated != 1:
                    raise ValueError("La existencia cambió; revisa el inventario y vuelve a intentar.")
        except ValueError as exc:
            return flash_redirect(request, "/ventas", str(exc))
        return redirect("/ventas", notice="sale_saved")

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
        with db.transaction() as conn:
            row = conn.execute("SELECT id FROM shifts WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1").fetchone()
            if row:
                conn.execute("UPDATE shifts SET closed_at=? WHERE id=?", (now_utc(), row["id"]))
        return redirect("/", notice="shift_closed")

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
        return templates.TemplateResponse(request, "employees.html", context(request, user=user, section="equipo", employees=employees, permissions=PERMISSIONS, limit=limit, sync_activities=sync_activities, now_ms=int(time.time() * 1000), sync_port=app.state.sync_server.sync_port if app.state.sync_server else None, sync_error=app.state.sync_server_error, notice=notice_text(request), notice_kind="success"))

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
