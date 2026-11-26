"""Pruebas de la exportación a PDF y PNG de la lista de precios y las tarjetas.

Android exporta estas salidas como archivo y DShalev solo tenía el HTML para imprimir. Aquí se comprueba que los
ficheros generados son válidos de verdad (cabecera PDF correcta y `startxref`, PNG con IHDR/IEND y CRC, ZIP con las
imágenes), que respetan el orden y los importes de `catalogo_cliente` y, sobre todo, que **nunca** aparecen el costo
ni las existencias.
"""

from __future__ import annotations

import io
import re
import struct
import zipfile
import zlib
from datetime import datetime
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from dshalev import exportacion
from dshalev.app import create_app
from dshalev.catalogo_cliente import FilaPrecio, TarjetaArticulo, lista_precios
from dshalev.config import AppConfig
from dshalev.database import Database
from dshalev.png import Lienzo

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
        "csrf": token(page), "business_name": "Gourmet Habana", "username": "dueno",
        "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    page = client.get("/entrar")
    client.post("/entrar", data={
        "csrf": token(page), "username": "dueno", "password": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    return client


def add_item(client: TestClient, name: str, price: str, cost: str = "40",
             stock: str = "50", category: str = "Producto") -> None:
    page = client.get("/inventario")
    client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": name, "category": category, "unit": "unidad",
        "sale_price": price, "cost": cost, "stock": stock, "low_stock": "2", "critical_stock": "1",
        "reason": "Existencia inicial para pruebas",
    }, follow_redirects=True)


# ---- PDF: estructura real ----

def pdf_texto(datos: bytes) -> str:
    """Texto sin comprimir del PDF (el constructor no comprime los flujos: se puede leer tal cual)."""
    assert datos.startswith(b"%PDF-1.4")
    assert datos.rstrip().endswith(b"%%EOF")
    assert b"startxref" in datos
    return datos.decode("latin-1", errors="replace")


def test_el_pdf_declara_xref_y_paginas_coherentes() -> None:
    filas = [FilaPrecio("Bebidas", "Café", 12000)]
    datos = exportacion.lista_precios_pdf(filas, negocio="Gourmet Habana")
    texto = pdf_texto(datos)
    paginas = int(re.search(r"/Count (\d+)", texto).group(1))
    assert paginas == len(re.findall(r"/Type /Page\b", texto))
    # Cada página apunta a su flujo de contenido y el xref tiene una entrada por objeto más la libre.
    objetos = int(re.search(r"xref\n0 (\d+)", texto).group(1))
    assert objetos > paginas * 2


def test_el_pdf_escapa_parentesis_y_barras() -> None:
    """Un nombre con paréntesis o barra no debe romper el flujo de contenido."""
    filas = [FilaPrecio("Bebidas", r"Café (grande) \ especial", 12000)]
    datos = exportacion.lista_precios_pdf(filas, negocio=r"Bar (El \\ Rincón)")
    texto = pdf_texto(datos)
    assert r"Café \(grande\) \\ especial" in texto
    assert r"Bar \(El \\\\ Rincón\)" in texto


def test_el_pdf_sustituye_caracteres_sin_glifo() -> None:
    """Fuera de Latin-1 no hay glifo en WinAnsi: se sustituye por '?' en vez de escribir bytes inválidos."""
    filas = [FilaPrecio("Bebidas", "Café ☕ especial", 12000)]
    datos = exportacion.lista_precios_pdf(filas, negocio="Gourmet")
    assert b"Caf\xe9 ? especial" in datos


def test_el_pdf_pagina_y_repite_la_cabecera_de_tabla() -> None:
    filas = [FilaPrecio("Bebidas", f"Artículo {n:03d}", 1000 + n) for n in range(120)]
    datos = exportacion.lista_precios_pdf(filas, negocio="Gourmet Habana")
    texto = pdf_texto(datos)
    assert texto.count("(Producto) Tj") >= 2, "la cabecera de la tabla debe repetirse al cortar de página"
    assert "Página 1 de" in texto


def test_la_lista_vacia_no_rompe_el_pdf() -> None:
    datos = exportacion.lista_precios_pdf([], negocio="Gourmet Habana")
    texto = pdf_texto(datos)
    # El flujo se escribe en Latin-1 directo (no con escapes octales): «í» es un byte 0xED.
    assert "No hay artículos activos para la lista." in texto


# ---- PNG: estructura real ----

def _chunks_png(datos: bytes) -> list[tuple[bytes, bytes]]:
    assert datos[:8] == b"\x89PNG\r\n\x1a\n"
    posicion, salida = 8, []
    while posicion < len(datos):
        (longitud,) = struct.unpack(">I", datos[posicion:posicion + 4])
        tipo = datos[posicion + 4:posicion + 8]
        contenido = datos[posicion + 8:posicion + 8 + longitud]
        (crc,) = struct.unpack(">I", datos[posicion + 8 + longitud:posicion + 12 + longitud])
        assert zlib.crc32(tipo + contenido) & 0xFFFFFFFF == crc, f"CRC inválido en {tipo!r}"
        salida.append((tipo, contenido))
        posicion += 12 + longitud
    return salida


def test_el_lienzo_escribe_un_png_valido_con_su_tamano() -> None:
    lienzo = Lienzo(40, 20, fondo=(10, 20, 30))
    chunks = _chunks_png(lienzo.png())
    assert [tipo for tipo, _ in chunks] == [b"IHDR", b"IDAT", b"IEND"]
    ancho, alto, profundidad, color = struct.unpack(">IIBB", chunks[0][1][:10])
    assert (ancho, alto, profundidad, color) == (40, 20, 8, 2)


def test_el_texto_del_lienzo_pinta_pixeles_y_no_se_sale() -> None:
    lienzo = Lienzo(50, 20, fondo=(255, 255, 255))
    ocupado = lienzo.texto(0, 0, "AB", escala=1, color=(0, 0, 0))
    assert ocupado > 0
    assert any(lienzo.pixeles[i:i + 3] == b"\x00\x00\x00" for i in range(0, len(lienzo.pixeles), 3))
    # Escribir fuera del lienzo no lanza: la tarjeta dibuja texto largo con nombres reales.
    lienzo.texto(45, 18, "MUY LARGO", escala=2)


def test_la_tarjeta_png_es_cuadrada_y_legible_como_imagen() -> None:
    tarjeta = TarjetaArticulo("Mojito clásico", "Bebidas", 25000, "vaso")
    datos = exportacion.tarjeta_png(tarjeta, negocio="Gourmet Habana Bistró & Café", comentario="Promoción")
    chunks = _chunks_png(datos)
    ancho, alto = struct.unpack(">II", chunks[0][1][:8])
    assert (ancho, alto) == (exportacion.TARJETA_PX, exportacion.TARJETA_PX)
    # La banda superior es del azul de la marca: hay píxeles exactamente de ese color.
    crudo = zlib.decompress(chunks[1][1])
    azul = bytes(int(canal * 255) for canal in exportacion.AZUL)
    assert azul in crudo


def test_el_nombre_del_fichero_no_lleva_caracteres_problematicos() -> None:
    tarjetas = [TarjetaArticulo("Café/leche: “grande”", "Bebidas", 12000, "taza")]
    for nombre, datos in exportacion.tarjetas_png(tarjetas, negocio="Gourmet"):
        assert nombre.endswith(".png")
        assert not set(nombre) & set('/\\:*?"<>|')
        assert datos.startswith(b"\x89PNG")


# ---- Las tres salidas juntas: nada de costo ni existencias ----

def test_las_salidas_no_filtran_costo_ni_existencias(tmp_path) -> None:
    client = setup_shop(tmp_path)
    # Costo 9.99 y 777 existencias: ningún número debe aparecer en lo que se entrega al cliente.
    add_item(client, "Café", "120", cost="9.99", stock="777")
    for ruta, tipo in (
        ("/inventario/lista-precios.pdf", "application/pdf"),
        ("/inventario/tarjetas.pdf", "application/pdf"),
        ("/inventario/tarjetas.png", "application/zip"),
    ):
        respuesta = client.get(ruta)
        assert respuesta.status_code == 200, ruta
        assert respuesta.headers["content-type"].startswith(tipo), ruta
        assert "attachment" in respuesta.headers["content-disposition"], ruta
        assert b"777" not in respuesta.content, f"{ruta} filtra existencias"
        assert b"9.99" not in respuesta.content and b"999" not in respuesta.content, f"{ruta} filtra costo"


def test_el_zip_lleva_una_imagen_por_articulo_mas_el_leeme(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Café", "120")
    add_item(client, "Té verde", "80")
    respuesta = client.get("/inventario/tarjetas.png")
    with zipfile.ZipFile(io.BytesIO(respuesta.content)) as paquete:
        nombres = paquete.namelist()
        assert "LEEME.txt" in nombres
        imagenes = [n for n in nombres if n.endswith(".png")]
        assert len(imagenes) == 2
        assert paquete.read(imagenes[0])[:8] == b"\x89PNG\r\n\x1a\n"
        assert b"costo" in paquete.read("LEEME.txt").lower()


def test_la_seleccion_de_tarjetas_se_respeta_en_pdf_y_zip(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Café", "120")
    add_item(client, "Té verde", "80")
    solo_cafe = client.get("/inventario/tarjetas.pdf", params={"articulos": "Café"})
    assert solo_cafe.status_code == 200
    texto = pdf_texto(solo_cafe.content)
    assert "Café" in texto
    assert "Té verde" not in texto, "solo se pidió el café"
    with zipfile.ZipFile(io.BytesIO(client.get("/inventario/tarjetas.png", params={"articulos": "Café"}).content)) as paquete:
        assert len([n for n in paquete.namelist() if n.endswith(".png")]) == 1


def test_el_pdf_respeta_el_orden_de_la_lista_de_precios(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Zumo", "300")
    add_item(client, "batido", "100")
    articulos = [
        {"name": "Zumo", "category": "Bebidas", "sale_price_cents": 30000},
        {"name": "batido", "category": "Bebidas", "sale_price_cents": 10000},
        {"name": "Andar", "category": "Servicios", "sale_price_cents": 5000},
    ]
    filas = lista_precios(articulos)
    assert [f.nombre for f in filas] == ["batido", "Zumo", "Andar"]
    datos = exportacion.lista_precios_pdf(filas, negocio="Gourmet", generado=datetime(2026, 11, 25, 2, 30))
    texto = pdf_texto(datos)
    assert texto.index("batido") < texto.index("Zumo") < texto.index("Andar")
    assert "25/11/2026 02:30" in texto


def test_el_pdf_y_el_zip_exigen_sesion(tmp_path) -> None:
    client = make_client(tmp_path)
    for ruta in ("/inventario/lista-precios.pdf", "/inventario/tarjetas.pdf", "/inventario/tarjetas.png"):
        respuesta = client.get(ruta, follow_redirects=False)
        assert respuesta.status_code in (303, 307), ruta
        assert respuesta.headers["location"] == "/entrar"


def test_los_botones_nuevos_estan_en_las_paginas(tmp_path) -> None:
    client = setup_shop(tmp_path)
    add_item(client, "Café", "120")
    lista = client.get("/inventario/lista-precios").text
    assert 'href="/inventario/lista-precios.pdf"' in lista
    tarjetas = client.get("/inventario/tarjetas").text
    assert 'href="/inventario/tarjetas.pdf"' in tarjetas
    assert 'href="/inventario/tarjetas.png"' in tarjetas
    assert 'action="/inventario/tarjetas/imprimir"' in tarjetas


@pytest.mark.parametrize("nombre", ["Gourmet Habana", "Bar (El Rincón) & Co."])
def test_el_pie_dice_que_no_lleva_costo_ni_existencias(nombre) -> None:
    datos = exportacion.lista_precios_pdf([FilaPrecio("Bebidas", "Café", 12000)], negocio=nombre)
    texto = pdf_texto(datos)
    assert "sin costo ni existencias" in texto
