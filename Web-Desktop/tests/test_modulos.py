"""Pruebas de los módulos del negocio en DShalev.

Las tres reglas vienen de `Modulo` de Android (`Android/domain/.../model/Modulo.kt`) y de las variables
`shalev.module.*` de iOS: al menos uno activo, VENTAS arrastra INVENTARIO y desmarcar INVENTARIO desmarca VENTAS.
Además, lo oculto **no** se borra: es lo que las pruebas de rutas comprueban al final.
"""

from __future__ import annotations

import re
from pathlib import Path

from fastapi.testclient import TestClient

from dshalev import modulos
from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database

CSRF_RE = re.compile(r'name="csrf" value="([^"]+)"')
RAIL = re.compile(r'<nav class="rail"[^>]*>(.*?)</nav>', re.DOTALL)


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
    client.post("/configurar", data={
        "csrf": token(page), "business_name": "Cafetería Central", "username": "dueno",
        "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
    }, follow_redirects=True)


def login(client: TestClient) -> None:
    page = client.get("/entrar")
    response = client.post("/entrar", data={
        "csrf": token(page), "username": "dueno", "password": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    assert response.status_code == 200


def rail_links(response) -> str:
    match = RAIL.search(response.text)
    assert match, "no se encontró el rail de navegación"
    return match.group(1)


# ---- Reglas (dominio) ----

def test_empty_selection_means_every_module() -> None:
    assert modulos.normalizar(set()) == modulos.TODOS
    assert modulos.de_texto(None) == modulos.TODOS
    assert modulos.de_texto("") == modulos.TODOS


def test_sales_pulls_inventory_in() -> None:
    assert modulos.alternar({"SERVICIOS"}, "VENTAS") == {"VENTAS", "INVENTARIO", "SERVICIOS"}
    assert modulos.normalizar({"VENTAS"}) == {"VENTAS", "INVENTARIO"}


def test_unchecking_inventory_unchecks_sales() -> None:
    assert modulos.alternar({"VENTAS", "INVENTARIO", "SERVICIOS"}, "INVENTARIO") == {"SERVICIOS"}
    assert modulos.alternar({"INVENTARIO"}, "INVENTARIO") == set()


def test_only_services_hides_the_other_sections() -> None:
    visible = modulos.secciones_visibles({"SERVICIOS"})
    assert visible == {"inventario": False, "servicios": True, "punto_de_venta": False}
    visible = modulos.secciones_visibles({"INVENTARIO"})
    # Inventario solo: la pestaña de inventario se ve, pero no hay punto de venta ni servicios.
    assert visible == {"inventario": True, "servicios": False, "punto_de_venta": False}


def test_unknown_keys_are_ignored_and_storage_is_stable() -> None:
    assert modulos.de_texto("VENTAS,BASURA") == {"VENTAS", "INVENTARIO"}
    assert modulos.de_texto("basura") == modulos.TODOS
    assert modulos.a_texto({"SERVICIOS", "INVENTARIO"}) == "INVENTARIO,SERVICIOS"


# ---- Rutas ----

def test_modules_page_requires_admin(tmp_path) -> None:
    client = make_client(tmp_path)
    response = client.get("/modulos", follow_redirects=False)
    assert response.status_code in (303, 307)
    assert response.headers["location"].startswith("/entrar")


def test_by_default_every_section_is_visible(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    rail = rail_links(client.get("/"))
    assert 'href="/ventas"' in rail
    assert 'href="/inventario"' in rail
    assert 'href="/servicios"' in rail


def test_choosing_only_services_hides_sales_and_inventory_from_the_rail(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/modulos")
    saved = client.post("/modulos", data={"csrf": token(page), "modulo_SERVICIOS": "1"}, follow_redirects=True)
    assert saved.status_code == 200
    assert "Módulos actualizados" in saved.text

    rail = rail_links(client.get("/"))
    assert 'href="/servicios"' in rail
    assert 'href="/ventas"' not in rail
    assert 'href="/inventario"' not in rail
    # El resto de la barra (herramientas del negocio) sigue ahí.
    assert 'href="/reportes"' in rail and 'href="/clientes"' in rail


def test_marking_sales_marks_inventory_without_the_client_sending_it(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/modulos")
    client.post("/modulos", data={"csrf": token(page), "modulo_VENTAS": "1"}, follow_redirects=True)
    with client.app.state.db.connect() as conn:
        guardado = conn.execute("SELECT value FROM app_meta WHERE key='modulos'").fetchone()["value"]
    assert guardado == "VENTAS,INVENTARIO"
    rail = rail_links(client.get("/"))
    assert 'href="/ventas"' in rail and 'href="/inventario"' in rail


def test_cannot_leave_every_module_unchecked(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/modulos")
    response = client.post("/modulos", data={"csrf": token(page)}, follow_redirects=True)
    assert "al menos un módulo activo" in response.text
    # Y el rail sigue completo: no se guardó nada.
    rail = rail_links(client.get("/"))
    assert 'href="/ventas"' in rail and 'href="/servicios"' in rail


def test_hiding_a_module_does_not_delete_its_data(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    inventory = client.get("/inventario")
    client.post("/inventario/nuevo", data={
        "csrf": token(inventory), "name": "Café molido", "category": "Producto", "unit": "u",
        "sale_price": "100", "cost": "50", "stock": "0", "low_stock": "5",
        "critical_stock": "1", "reason": "",
    }, follow_redirects=True)

    page = client.get("/modulos")
    client.post("/modulos", data={"csrf": token(page), "modulo_SERVICIOS": "1"}, follow_redirects=True)
    assert 'href="/inventario"' not in rail_links(client.get("/"))

    # Volver a marcarlo devuelve los datos: siguen en la base.
    page = client.get("/modulos")
    client.post("/modulos", data={"csrf": token(page), "modulo_SERVICIOS": "1", "modulo_INVENTARIO": "1"},
                follow_redirects=True)
    inventory = client.get("/inventario")
    assert "Café molido" in inventory.text


def test_modules_save_rejects_bad_csrf(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    response = client.post("/modulos", data={"csrf": "falso", "modulo_SERVICIOS": "1"})
    assert response.status_code == 403
