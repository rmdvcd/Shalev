"""Pruebas de la ordenación ciclable de listas en DShalev.

Misma regla que Android (`ModoOrden` + `InventarioFiltro`) e iOS (`ModoOrden.aplicar`): cuatro modos que ciclan en el
mismo orden, orden **estable** y `casefold` para el alfabético. En el escritorio el modo vive en la URL
(`?orden=...`), así que se comprueban también el ciclo, los enlaces y que el botón anuncie el modo en texto.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from dshalev import orden
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
    assert match, response.text[:400]
    return match.group(1)


def setup_shop(tmp_path: Path) -> TestClient:
    client = make_client(tmp_path)
    page = client.get("/configurar")
    client.post("/configurar", data={
        "csrf": token(page), "business_name": "Cafetería Central", "username": "dueno",
        "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    page = client.get("/entrar")
    client.post("/entrar", data={
        "csrf": token(page), "username": "dueno", "password": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    return client


def add_item(client: TestClient, name: str, price: str, category: str = "Producto") -> None:
    page = client.get("/inventario")
    client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": name, "category": category, "unit": "unidad",
        "sale_price": price, "cost": "1", "stock": "10", "low_stock": "2", "critical_stock": "1",
        "reason": "Existencia inicial para pruebas",
    }, follow_redirects=True)


def nombres_de(html: str) -> list[str]:
    """Nombres de la tabla de inventario en el orden en que se pintan."""
    cuerpo = html.split('<table class="inventory-table">', 1)[1].split("</table>", 1)[0]
    return re.findall(r"<td data-label=\"Artículo\"><strong>([^<]+)</strong>", cuerpo)


# ---- El módulo ----

def test_el_ciclo_es_el_mismo_que_android_e_ios() -> None:
    assert orden.MODOS == ("recientes", "alfabetico", "mayor", "menor")
    assert orden.modo_de("recientes").siguiente == "alfabetico"
    assert orden.modo_de("alfabetico").siguiente == "mayor"
    assert orden.modo_de("mayor").siguiente == "menor"
    # Cierra el círculo, como `ModoOrden.siguiente()` en Android.
    assert orden.modo_de("menor").siguiente == "recientes"


def test_un_modo_desconocido_vuelve_a_recientes() -> None:
    assert orden.modo_de(None).clave == "recientes"
    assert orden.modo_de("").clave == "recientes"
    assert orden.modo_de("precio-aleatorio").clave == "recientes"
    # Y nunca rompe la URL: el valor basura no se propaga al siguiente enlace.
    assert orden.enlace("/inventario", {"orden": ""}) == "/inventario"


def test_el_alfabetico_usa_casefold_y_es_estable() -> None:
    filas = [
        {"name": "bebidas", "precio": 300},
        {"name": "Almuerzo", "precio": 100},
        {"name": "Bebidas", "precio": 200},
    ]
    alfabetico = orden.modo_de("alfabetico").aplicar(filas, nombre=lambda f: f["name"], importe=lambda f: f["precio"])
    # `casefold` deja "bebidas" y "Bebidas" empatados: el orden de entrada decide, como `sortedWith` en Android.
    assert [fila["name"] for fila in alfabetico] == ["Almuerzo", "bebidas", "Bebidas"]


def test_importes_ordenan_sin_mutar_la_entrada() -> None:
    filas = [{"name": "A", "precio": 250}, {"name": "B", "precio": 900}, {"name": "C", "precio": 250}]
    copia = [dict(fila) for fila in filas]
    mayor = orden.modo_de("mayor").aplicar(filas, nombre=lambda f: f["name"], importe=lambda f: f["precio"])
    menor = orden.modo_de("menor").aplicar(filas, nombre=lambda f: f["name"], importe=lambda f: f["precio"])
    assert [fila["name"] for fila in mayor] == ["B", "A", "C"]  # empate 250: A antes que C (entrada)
    assert [fila["name"] for fila in menor] == ["A", "C", "B"]
    assert filas == copia


def test_recientes_conserva_el_orden_de_la_consulta() -> None:
    filas = [{"name": "Zeta", "precio": 1}, {"name": "alfa", "precio": 2}]
    assert orden.modo_de("recientes").aplicar(filas, nombre=lambda f: f["name"], importe=lambda f: f["precio"]) == filas


def test_los_enlaces_no_arrastran_parametros_vacios() -> None:
    assert orden.enlace("/inventario", {"q": "café", "orden": "mayor"}) == "/inventario?q=caf%C3%A9&orden=mayor"
    assert orden.enlace("/servicios", {"q": "", "orden": "mayor"}) == "/servicios?orden=mayor"


# ---- La página ----

def test_el_boton_cicla_por_los_cuatro_modos_en_inventario(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Café", "120")
    pagina = client.get("/inventario")
    assert "Orden: Recientes" in pagina.text
    assert 'href="/inventario?orden=alfabetico"' in pagina.text
    # El nombre accesible anuncia el modo siguiente, no solo el actual.
    assert "Ordenar por A–Z" in pagina.text
    for clave, etiqueta, siguiente in (
        ("alfabetico", "A–Z", "mayor"),
        ("mayor", "Mayor importe", "menor"),
        ("menor", "Menor importe", "recientes"),
        ("recientes", "Recientes", "alfabetico"),
    ):
        respuesta = client.get("/inventario", params={"orden": clave})
        assert f"Orden: {etiqueta}" in respuesta.text, clave
        assert f"orden={siguiente}" in respuesta.text, clave


def test_la_lista_cambia_de_orden_y_la_busqueda_lo_conserva(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Zumo", "900")
    add_item(client, "Almuerzo", "1500")
    add_item(client, "batido", "300")

    por_nombre = nombres_de(client.get("/inventario", params={"orden": "alfabetico"}).text)
    assert por_nombre == ["Almuerzo", "batido", "Zumo"]

    por_mayor = nombres_de(client.get("/inventario", params={"orden": "mayor"}).text)
    assert por_mayor == ["Almuerzo", "Zumo", "batido"]

    por_menor = nombres_de(client.get("/inventario", params={"orden": "menor"}).text)
    assert por_menor == ["batido", "Zumo", "Almuerzo"]

    # Recientes = último creado primero (id descendente), igual que `creadoEn` en Android.
    assert nombres_de(client.get("/inventario").text) == ["batido", "Almuerzo", "Zumo"]

    # El buscador reenvía el modo para no perderlo al filtrar.
    buscado = client.get("/inventario", params={"q": "a", "orden": "mayor"})
    assert 'name="orden" value="mayor"' in buscado.text
    assert nombres_de(buscado.text) == ["Almuerzo", "batido"]


def test_servicios_tiene_el_mismo_boton_y_orden(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Corte", "700", category="Servicio")
    add_item(client, "Barba", "300", category="Servicio")
    pagina = client.get("/servicios", params={"orden": "mayor"})
    assert "Orden: Mayor importe" in pagina.text
    assert 'href="/servicios?orden=menor"' in pagina.text
    assert 'name="orden" value="mayor"' in pagina.text
    cuerpo = pagina.text.split('<table>', 1)[1]
    assert cuerpo.index("Corte") < cuerpo.index("Barba")


def test_un_modo_inventado_se_sirve_como_recientes(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Café", "120")
    respuesta = client.get("/inventario", params={"orden": "../../etc/passwd"})
    assert respuesta.status_code == 200
    assert "Orden: Recientes" in respuesta.text
    assert "../../etc/passwd" not in respuesta.text


@pytest.mark.parametrize("ruta", ["/inventario", "/servicios"])
def test_el_boton_cumple_el_objetivo_tactil(tmp_path, ruta) -> None:
    """El botón de orden es un control de la lista: 44 px como el resto (guía de diseño de DShalev)."""
    client = setup_shop(tmp_path)
    html = client.get(ruta).text
    assert 'class="quiet-button orden-boton"' in html
    assert 'id="' + ("inventario-ordenar" if ruta == "/inventario" else "servicios-ordenar") + '"' in html
