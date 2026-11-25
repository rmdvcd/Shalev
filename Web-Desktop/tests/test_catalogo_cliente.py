"""Pruebas de la lista de precios y las tarjetas promocionales de DShalev.

Regla heredada de Android (`TablasExport.listaPrecios`, `DisenoTarjetas`): estas dos salidas son para clientes y **no
pueden** contener costo ni existencias. Las pruebas lo comprueban en el dominio y en el HTML servido.
"""

from __future__ import annotations

import re
from pathlib import Path

from fastapi.testclient import TestClient

from dshalev import catalogo_cliente
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


def add_item(client: TestClient, name: str, price: str, cost: str, category: str = "Producto") -> None:
    page = client.get("/inventario")
    response = client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": name, "category": category, "unit": "u",
        "sale_price": price, "cost": cost, "stock": "7", "low_stock": "5",
        "critical_stock": "1", "reason": "Existencia de prueba",
    }, follow_redirects=True)
    assert response.status_code == 200


# ---- Dominio ----

def test_price_list_follows_android_order_and_columns() -> None:
    items = [
        {"name": "Refresco", "category": "Bebidas", "sale_price_cents": 15_000},
        {"name": "Arroz", "category": "Granos", "sale_price_cents": 30_000},
        {"name": "Agua", "category": "bebidas", "sale_price_cents": 8_000},
    ]
    filas = catalogo_cliente.lista_precios(items)
    # Vector de Android (ExportacionTest.): «bebidas» y «Bebidas» son la MISMA categoría al ordenar, y dentro de
    # ella manda el nombre. Por eso sale Agua antes que Refresco, y la categoría conserva su grafía original.
    assert [(f.categoria, f.nombre, f.precio_cents) for f in filas] == [
        ("bebidas", "Agua", 8_000),
        ("Bebidas", "Refresco", 15_000),
        ("Granos", "Arroz", 30_000),
    ]


def test_price_list_never_carries_cost_or_stock() -> None:
    items = [{"name": "Café", "category": "Producto", "sale_price_cents": 1_250,
              "cost_cents": 900, "stock_milli": 7_000, "low_stock_milli": 5_000}]
    filas = catalogo_cliente.lista_precios(items)
    assert filas[0].precio_cents == 1_250
    campos = {campo for fila in filas for campo in fila.__dict__}
    assert campos == {"categoria", "nombre", "precio_cents"}
    texto = repr(filas)
    assert "900" not in texto and "stock" not in texto.lower()


def test_price_list_ignores_blank_names_and_negative_prices() -> None:
    filas = catalogo_cliente.lista_precios([
        {"name": "  ", "category": "Producto", "sale_price_cents": 100},
        {"name": "Raro", "category": "Producto", "sale_price_cents": -500},
    ])
    assert [f.nombre for f in filas] == ["Raro"]
    assert filas[0].precio_cents == 0


def test_cards_group_six_per_page_like_android() -> None:
    items = [{"name": f"Artículo {i}", "category": "Producto", "sale_price_cents": i * 100, "unit": "u"}
             for i in range(1, 14)]
    paginas = catalogo_cliente.tarjetas(items)
    assert [len(p) for p in paginas] == [6, 6, 1]
    # Se respeta el orden recibido (el de la pantalla de inventario), no se reordena.
    assert paginas[0][0].nombre == "Artículo 1"
    assert paginas[2][0].nombre == "Artículo 13"


def test_card_comment_is_trimmed_to_one_hundred_sixty_characters() -> None:
    assert catalogo_cliente.comentario_limpio("  Oferta\n\nde   la semana ") == "Oferta de la semana"
    largo = "x" * 300
    assert len(catalogo_cliente.comentario_limpio(largo)) == catalogo_cliente.COMENTARIO_MAX


def test_selection_keeps_catalog_order_and_drops_unknown_names() -> None:
    disponibles = ["A", "B", "C"]
    assert catalogo_cliente.texto_seleccion("C|A|Z", disponibles) == ["A", "C"]
    assert catalogo_cliente.texto_seleccion("A|A", disponibles) == ["A"]
    assert catalogo_cliente.texto_seleccion("", disponibles) == []


# ---- Rutas ----

def test_client_outputs_require_login(tmp_path) -> None:
    client = make_client(tmp_path)
    for ruta in ("/inventario/lista-precios", "/inventario/tarjetas", "/inventario/tarjetas.pdf"):
        response = client.get(ruta, follow_redirects=False)
        assert response.status_code in (303, 307), ruta
        assert response.headers["location"].startswith("/entrar")


def test_price_list_page_shows_names_and_prices_but_not_costs(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client, "Café molido", price="100", cost="777")
    add_item(client, "Pan de casa", price="45.50", cost="888")

    response = client.get("/inventario/lista-precios")
    assert response.status_code == 200
    assert "Lista de precios" in response.text
    assert "Café molido" in response.text and "Pan de casa" in response.text
    assert "100 CUP" in response.text and "45.50 CUP" in response.text
    # El costo no aparece por ningún lado: se comparte con clientes.
    assert "777" not in response.text and "888" not in response.text


def test_inventory_page_links_to_both_client_outputs(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    response = client.get("/inventario")
    assert 'href="/inventario/lista-precios"' in response.text
    assert 'href="/inventario/tarjetas"' in response.text


def test_cards_print_groups_and_escapes_content(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client, "Café <molido>", price="100", cost="777")

    formulario = client.get("/inventario/tarjetas")
    assert formulario.status_code == 200
    assert "Tarjetas promocionales" in formulario.text

    salida = client.get("/inventario/tarjetas.pdf", params={"comentario": "Oferta <b>semanal</b>"})
    assert salida.status_code == 200
    assert "Café &lt;molido&gt;" in salida.text, "el nombre debe salir escapado"
    assert "<molido>" not in salida.text
    assert "Oferta &lt;b&gt;semanal&lt;/b&gt;" in salida.text
    assert "777" not in salida.text


def test_cards_print_only_selected_items(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    add_item(client, "Café molido", price="100", cost="10")
    add_item(client, "Pan de casa", price="45.50", cost="10")

    salida = client.get("/inventario/tarjetas.pdf", params={"articulos": "Pan de casa"})
    assert salida.status_code == 200
    assert "Pan de casa" in salida.text
    assert "Café molido" not in salida.text

    # Un nombre que no existe no se cuela ni deja la página vacía de más.
    inventado = client.get("/inventario/tarjetas.pdf", params={"articulos": "No existe"})
    assert inventado.status_code == 200
    assert "No hay artículos para las tarjetas" in inventado.text
