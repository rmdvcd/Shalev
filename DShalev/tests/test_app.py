from __future__ import annotations

import re
from pathlib import Path

from fastapi.testclient import TestClient

from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database


CSRF_RE = re.compile(r'name="csrf" value="([^"]+)"')


def make_client(tmp_path: Path) -> TestClient:
    data = tmp_path / "DShalevData"
    data.mkdir()
    config = AppConfig(
        data_dir=data,
        settings_path=data / "portable-settings.json",
        database_path=data / "test.sqlite3",
        secret_key="test-session-secret-not-for-production-000000000000",
    )
    return TestClient(create_app(config, Database(config.database_path)))


def token(response) -> str:
    match = CSRF_RE.search(response.text)
    assert match, response.text[:500]
    return match.group(1)


def create_admin(client: TestClient) -> None:
    page = client.get("/configurar")
    response = client.post("/configurar", data={
        "csrf": token(page), "business_name": "Cafetería Central", "username": "dueno",
        "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    assert response.status_code == 200
    assert "Bienvenido" in response.text


def login(client: TestClient) -> None:
    page = client.get("/entrar")
    response = client.post("/entrar", data={
        "csrf": token(page), "username": "dueno", "password": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    assert response.status_code == 200
    assert "RESUMEN DEL NEGOCIO" in response.text


def test_first_run_requires_admin_and_password_is_only_hashed(tmp_path):
    client = make_client(tmp_path)
    page = client.get("/")
    assert page.url.path == "/configurar"
    setup = client.get("/configurar")
    response = client.post("/configurar", data={
        "csrf": token(setup), "business_name": "Tienda", "username": "dueno",
        "password": "corta", "password_confirm": "corta",
    })
    assert response.status_code == 422
    assert "al menos 12" in response.text
    create_admin(client)
    with client.app.state.db.connect() as conn:
        row = conn.execute("SELECT password_hash FROM users WHERE username='dueno'").fetchone()
    assert row and row["password_hash"].startswith("pbkdf2_sha256$")
    assert "ContraseñaSegura2026!" not in row["password_hash"]


def test_login_requires_csrf_and_authentication(tmp_path):
    client = make_client(tmp_path)
    create_admin(client)
    denied = client.get("/api/v1/catalog")
    assert denied.status_code == 401
    bad = client.post("/entrar", data={"username": "dueno", "password": "ContraseñaSegura2026!"}, follow_redirects=False)
    assert bad.status_code == 303
    login(client)
    assert client.get("/api/v1/status").json()["app"] == "DShalev"
    assert client.get("/equipo").status_code == 200
    assert client.get("/red").status_code == 200
    assert client.get("/static/app.css").status_code == 200


def test_inventory_shift_and_sale_are_transactional(tmp_path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/inventario")
    bad_csrf = client.post("/inventario/nuevo", data={"name": "Café"})
    assert bad_csrf.status_code == 403
    added = client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": "Café", "category": "Producto", "unit": "paquete",
        "sale_price": "125.50", "cost": "60", "stock": "3", "low_stock": "2", "critical_stock": "1",
    }, follow_redirects=True)
    assert added.status_code == 200
    assert "Café" in added.text

    dashboard = client.get("/")
    opened = client.post("/turno/abrir", data={"csrf": token(dashboard), "opening_cash": "500"}, follow_redirects=True)
    assert opened.status_code == 200 and "Abierto" in opened.text

    sales = client.get("/ventas")
    sold = client.post("/ventas/nueva", data={
        "csrf": token(sales), "item_id": "1", "quantity": "2", "payment_method": "EFECTIVO",
    }, follow_redirects=True)
    assert sold.status_code == 200 and "Venta registrada" in sold.text
    with client.app.state.db.connect() as conn:
        item = conn.execute("SELECT stock FROM items WHERE id=1").fetchone()
        sale = conn.execute("SELECT total_cents FROM sales").fetchone()
    assert item["stock"] == 1
    assert sale["total_cents"] == 25_100

    oversell = client.post("/ventas/nueva", data={
        "csrf": token(client.get("/ventas")), "item_id": "1", "quantity": "2", "payment_method": "EFECTIVO",
    }, follow_redirects=True)
    assert oversell.status_code == 200 and "No hay existencia suficiente" in oversell.text
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT COUNT(*) FROM sales").fetchone()[0] == 1
        assert conn.execute("SELECT stock FROM items WHERE id=1").fetchone()[0] == 1


def test_team_slots_and_remote_mode_require_tls_paths(tmp_path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/equipo")
    created = client.post("/equipo/nueva", data={
        "csrf": token(page), "name": "Ana", "perm_vender_productos": "on",
    }, follow_redirects=True)
    assert created.status_code == 200 and "Ana" in created.text

    network = client.get("/red")
    response = client.post("/red", data={
        "csrf": token(network), "network_mode": "remote", "tls_cert": str(tmp_path / "missing.pem"),
        "tls_key": str(tmp_path / "missing.key"),
    }, follow_redirects=True)
    assert response.status_code == 200
    assert "No abras el servidor a Internet sin HTTPS" in response.text
    assert "Solo este equipo" in response.text


def test_health_is_minimal_and_app_sets_security_headers(tmp_path):
    client = make_client(tmp_path)
    response = client.get("/healthz")
    assert response.json() == {"ok": True, "app": "DShalev", "version": "0.1.0"}
    assert response.headers["x-frame-options"] == "DENY"
    assert "default-src 'self'" in response.headers["content-security-policy"]
