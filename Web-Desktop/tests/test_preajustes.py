"""Pruebas de los preajustes de precios de DShalev (paridad con `PreajustePrecios` de Android).

Cubren la aritmética (puntos básicos y redondeo), la lectura de condiciones, la validación del formulario y las
rutas protegidas por administrador.
"""

from __future__ import annotations

import re
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

from fastapi.testclient import TestClient

from dshalev import preajustes
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


def login(client: TestClient) -> None:
    page = client.get("/entrar")
    response = client.post("/entrar", data={
        "csrf": token(page), "username": "dueno", "password": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    assert response.status_code == 200


def add_item(client: TestClient, name: str = "Café molido", price: str = "100") -> None:
    page = client.get("/inventario")
    response = client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": name, "category": "Producto", "unit": "u",
        "sale_price": price, "cost": "50", "stock": "0", "low_stock": "5",
        "critical_stock": "1", "reason": "",
    }, follow_redirects=True)
    assert response.status_code == 200


# ---- Aritmética (el corazón de la paridad) ----

def test_one_basis_point_is_one_hundredth_of_a_percent() -> None:
    # 1 000 puntos = +10 % (la escala de `PreajustePrecios`), no 1 000 %.
    assert preajustes.precio_ajustado(10_000, 1_000) == 11_000
    assert preajustes.precio_ajustado(10_000, -1_000) == 9_000
    assert preajustes.precio_ajustado(10_000, 0) == 10_000
    assert preajustes.precio_ajustado(10_000, 100) == 10_100


def test_rounding_matches_half_up_against_decimal_oracle() -> None:
    def oracle(precio: int, puntos: int) -> int:
        valor = Decimal(precio) * (Decimal(1) + Decimal(puntos) / Decimal(10_000))
        return max(0, int(valor.quantize(Decimal("1"), rounding=ROUND_HALF_UP)))

    for precio in (0, 1, 2, 3, 99, 100, 333, 999, 1_000, 12_345, 3_813_908):
        for puntos in (-10_000, -7_250, -5_000, -3_750, -1, 1, 500, 1_000, 2_500, 10_000):
            assert preajustes.precio_ajustado(precio, puntos) == oracle(precio, puntos), (precio, puntos)


def test_price_never_goes_below_zero_and_discounts_cap_at_free() -> None:
    assert preajustes.precio_ajustado(100, -10_000) == 0
    assert preajustes.precio_ajustado(0, 5_000) == 0


def test_several_presets_on_the_same_item_add_up() -> None:
    # Android: «Varios preajustes aplicables al mismo artículo se suman».
    assert preajustes.importe_con_preajustes(10_000, [500, 500]) == 11_000
    assert preajustes.aplicar_a_catalogo(
        {1: 10_000, 2: 5_000},
        [preajustes.Preajuste(id=1, nombre="A", puntos_basicos=500, producto_ids=[1]),
         preajustes.Preajuste(id=2, nombre="B", puntos_basicos=1_000, producto_ids=[1])],
    ) == {1: 11_500, 2: 5_000}


def test_inactive_preset_does_not_touch_prices() -> None:
    assert preajustes.aplicar_a_catalogo(
        {1: 10_000},
        [preajustes.Preajuste(id=1, nombre="A", puntos_basicos=1_000, producto_ids=[1], activo=False)],
    ) == {1: 10_000}


def test_percentage_parser_rejects_impossible_values() -> None:
    assert preajustes.validar_puntos("10") == 1_000
    assert preajustes.validar_puntos("-12,5") == -1_250
    assert preajustes.validar_puntos(" 0.01 ") == 1
    for malo in ("", "abc", "101", "-100.5", "1.234", "1.2.3"):
        try:
            preajustes.validar_puntos(malo)
        except ValueError:
            continue
        raise AssertionError(f"debió rechazar {malo!r}")


def test_name_validation_trims_and_limits() -> None:
    assert preajustes.validar_nombre("  Mayorista   12  ") == "Mayorista 12"
    for malo in ("", "ab", "x" * 61):
        try:
            preajustes.validar_nombre(malo)
        except ValueError:
            continue
        raise AssertionError(f"debió rechazar {malo!r}")


def test_condition_and_scope_texts_are_human_readable() -> None:
    p = preajustes.Preajuste(id=1, nombre="Mayorista", puntos_basicos=1_000,
                             producto_ids=[1, 2], metodo_pago="efectivo")
    assert p.condicion_texto == "pago en efectivo"
    assert p.alcance_texto == "2 productos"
    assert p.porcentaje == 10
    vacio = preajustes.Preajuste(id=2, nombre="Todo", puntos_basicos=-500)
    assert vacio.condicion_texto == "todas las ventas"
    assert vacio.alcance_texto == "ningún artículo"


# ---- Rutas ----

def test_presets_page_requires_admin_login(tmp_path) -> None:
    client = make_client(tmp_path)
    response = client.get("/herramientas/precios", follow_redirects=False)
    assert response.status_code in (303, 307)
    assert response.headers["location"].startswith("/entrar")


def test_create_preset_from_the_form(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client, "Café molido", "100")

    page = client.get("/herramientas/precios")
    assert page.status_code == 200
    assert "Preajustes de precios" in page.text

    created = client.post("/herramientas/precios/nuevo", data={
        "csrf": token(page), "name": "Mayorista 12", "basis_points": "10",
        "productos": "1", "servicios": "", "payment_method": "", "volume_minimum": "12", "customer_type": "",
    }, follow_redirects=True)
    assert created.status_code == 200
    assert "Preajuste creado" in created.text
    assert "Mayorista 12" in created.text
    assert "12 unidades o más" in created.text

    with client.app.state.db.connect() as conn:
        fila = conn.execute("SELECT name,basis_points,product_ids_json,volume_minimum,customer_type FROM presets").fetchone()
    assert fila["basis_points"] == 1_000
    assert fila["product_ids_json"] == "[1]"
    assert fila["volume_minimum"] == 12
    assert fila["customer_type"] is None


def test_two_conditions_are_rejected(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client)
    page = client.get("/herramientas/precios")
    response = client.post("/herramientas/precios/nuevo", data={
        "csrf": token(page), "name": "Mezcla", "basis_points": "10", "productos": "1",
        "payment_method": "efectivo", "volume_minimum": "5",
    }, follow_redirects=True)
    assert "una sola condición" in response.text
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT COUNT(*) FROM presets").fetchone()[0] == 0


def test_unknown_item_is_rejected(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client)
    page = client.get("/herramientas/precios")
    response = client.post("/herramientas/precios/nuevo", data={
        "csrf": token(page), "name": "Fantasma", "basis_points": "5", "productos": "999",
    }, follow_redirects=True)
    assert "no existe en el inventario" in response.text


def test_preset_without_items_is_rejected(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/herramientas/precios")
    response = client.post("/herramientas/precios/nuevo", data={
        "csrf": token(page), "name": "Vacío", "basis_points": "5",
    }, follow_redirects=True)
    assert "al menos un producto o un servicio" in response.text


def test_toggle_pauses_and_resumes(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client)
    page = client.get("/herramientas/precios")
    client.post("/herramientas/precios/nuevo", data={
        "csrf": token(page), "name": "Temporal", "basis_points": "5", "productos": "1",
    }, follow_redirects=True)
    with client.app.state.db.connect() as conn:
        preset_id = conn.execute("SELECT id FROM presets").fetchone()["id"]

    page = client.get("/herramientas/precios")
    paused = client.post(f"/herramientas/precios/{preset_id}/estado", data={"csrf": token(page)}, follow_redirects=True)
    assert paused.status_code == 200
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT active FROM presets WHERE id=?", (preset_id,)).fetchone()["active"] == 0

    page = client.get("/herramientas/precios")
    resumed = client.post(f"/herramientas/precios/{preset_id}/estado", data={"csrf": token(page)}, follow_redirects=True)
    assert resumed.status_code == 200
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT active FROM presets WHERE id=?", (preset_id,)).fetchone()["active"] == 1


def test_preset_routes_reject_bad_csrf(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client)
    response = client.post("/herramientas/precios/nuevo", data={
        "csrf": "falso", "name": "Sin token", "basis_points": "5", "productos": "1",
    })
    assert response.status_code == 403


def test_schema_migration_adds_condition_columns(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    with client.app.state.db.connect() as conn:
        columnas = {row[1] for row in conn.execute("PRAGMA table_info(presets)")}
        version = conn.execute("SELECT value FROM app_meta WHERE key='schema_version'").fetchone()[0]
    assert {"volume_minimum", "customer_type"} <= columnas
    assert version == "11"
