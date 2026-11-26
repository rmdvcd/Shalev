"""Exportación de la lista de precios y las tarjetas a **PDF** y **PNG** sin dependencias externas.

Android exporta estas dos salidas como archivo (`FormatoSalida.PDF`, `IMAGEN` y `TARJETAS`); DShalev solo ofrecía el
HTML para imprimir desde el navegador. Aquí se generan los ficheros de verdad, con la misma regla de `catalogo_cliente`:
**nunca** costo ni existencias.

Cómo se hace sin dependencias
-----------------------------
* **PNG**: el escritor de `png.py` de este paquete (zlib + texto dibujado con el mapa de glifos de 5×7). No hay
  fuentes ni bibliotecas de imágenes en el ejecutable portable, así que el texto se pinta píxel a píxel: es legible,
  nítido y sin dependencias, y la tarjeta que se comparte por mensajería se ve igual en cualquier teléfono.
* **PDF**: se escribe a mano un PDF 1.4 con `/Helvetica` (una de las catorce fuentes que todo lector trae, sin
  incrustar nada). El texto lleva acentos en WinAnsi, y los importes en CUP se dibujan con su símbolo escrito
  («CUP»), no con un glifo que Helvetica no tenga.

Ambos formatos paginan igual que la plantilla HTML: la lista de precios va en tablas de tres columnas y las tarjetas
de seis en seis.
"""

from __future__ import annotations

from datetime import datetime
from typing import Iterable, Sequence
import zlib

from .catalogo_cliente import FilaPrecio, TarjetaArticulo, TARJETAS_POR_PAGINA
from .forms import format_cup
from .png import Lienzo, decodificar_png_rgb

#: Márgenes y medidas de la hoja A4 en puntos (1 pt = 1/72 de pulgada).
A4_ANCHO = 595.28
A4_ALTO = 841.89
MARGEN = 42.0
AZUL = (0.169, 0.310, 0.639)  # #2B4FA3, el mismo acento del escritorio y del teléfono.
TINTA = (0.102, 0.110, 0.118)
GRIS = (0.373, 0.388, 0.408)

#: Medidas de la tarjeta PNG: 1080×1080 es el cuadrado que usan WhatsApp y las redes.
TARJETA_PX = 1080
TARJETA_MARGEN = 64


# --------------------------------------------------------------------------- PDF

def _escapar_pdf(texto: str) -> str:
    """Texto seguro para el contenido de un PDF con fuente WinAnsi."""
    reemplazos = {"\\": r"\\", "(": r"\(", ")": r"\)", "\r": "", "\n": " "}
    limpio = "".join(reemplazos.get(caracter, caracter) for caracter in texto)
    # Fuera del rango Latin-1 no hay glifo en WinAnsi: se sustituye por '?' en vez de romper el fichero.
    return "".join(caracter if ord(caracter) < 256 else "?" for caracter in limpio)


class _Pdf:
    """Constructor mínimo de PDF: páginas con texto y líneas, sin fuentes incrustadas."""

    def __init__(self) -> None:
        self.paginas: list[list[str]] = []
        self.actual: list[str] = []
        self.imagenes: list[tuple[str, int, int, bytes]] = []

    def imagen_png(self, datos: bytes, x: float, y: float, ancho: float, alto: float) -> None:
        ancho_px, alto_px, rgb = decodificar_png_rgb(datos)
        nombre = f"Im{len(self.imagenes) + 1}"
        if self.imagenes and self.imagenes[0][3] == rgb:
            nombre = self.imagenes[0][0]
        else:
            self.imagenes.append((nombre, ancho_px, alto_px, rgb))
        self.actual.append(f"q {ancho:.2f} 0 0 {alto:.2f} {x:.2f} {y:.2f} cm /{nombre} Do Q")

    def nueva_pagina(self) -> None:
        if self.actual:
            self.paginas.append(self.actual)
        self.actual = []

    def texto(self, x: float, y: float, contenido: str, *, tamano: float = 11,
              color: tuple[float, float, float] = TINTA, negrita: bool = False) -> None:
        fuente = "F2" if negrita else "F1"
        self.actual.append(
            f"BT /{fuente} {tamano:.2f} Tf {color[0]:.3f} {color[1]:.3f} {color[2]:.3f} rg "
            f"1 0 0 1 {x:.2f} {y:.2f} Tm ({_escapar_pdf(contenido)}) Tj ET"
        )

    def linea(self, x1: float, y1: float, x2: float, y2: float, *,
              grosor: float = 0.6, color: tuple[float, float, float] = GRIS) -> None:
        self.actual.append(
            f"{grosor:.2f} w {color[0]:.3f} {color[1]:.3f} {color[2]:.3f} RG "
            f"{x1:.2f} {y1:.2f} m {x2:.2f} {y2:.2f} l S"
        )

    def rectangulo(self, x: float, y: float, ancho: float, alto: float,
                   color: tuple[float, float, float]) -> None:
        self.actual.append(
            f"{color[0]:.3f} {color[1]:.3f} {color[2]:.3f} rg "
            f"{x:.2f} {y:.2f} {ancho:.2f} {alto:.2f} re f"
        )

    def construir(self) -> bytes:
        if self.actual:
            self.paginas.append(self.actual)
            self.actual = []
        objetos: list[bytes] = []
        # 1 catálogo, 2 árbol de páginas, 3 y 4 fuentes, luego una página y su contenido por hoja.
        total = len(self.paginas)
        ids_paginas = [5 + 2 * i for i in range(total)]
        primer_id_imagen = 5 + 2 * total
        ids_imagenes = {nombre: primer_id_imagen + i for i, (nombre, _, _, _) in enumerate(self.imagenes)}
        objetos.append(b"<< /Type /Catalog /Pages 2 0 R >>")
        kids = " ".join(f"{pid} 0 R" for pid in ids_paginas)
        objetos.append(f"<< /Type /Pages /Kids [{kids}] /Count {total} >>".encode("ascii"))
        objetos.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        objetos.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        for indice, contenido in enumerate(self.paginas):
            flujo = "\n".join(contenido).encode("latin-1", errors="replace")
            xobjects = " ".join(f"/{nombre} {objeto_id} 0 R" for nombre, objeto_id in ids_imagenes.items())
            extra = f" /XObject << {xobjects} >>" if xobjects else ""
            objetos.append(
                f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {A4_ANCHO:.2f} {A4_ALTO:.2f}] "
                f"/Resources << /Font << /F1 3 0 R /F2 4 0 R >>{extra} >> "
                f"/Contents {ids_paginas[indice] + 1} 0 R >>".encode("ascii")
            )
            objetos.append(b"<< /Length " + str(len(flujo)).encode("ascii") + b" >>\nstream\n" + flujo + b"\nendstream")
        for nombre, ancho, alto, rgb in self.imagenes:
            flujo_imagen = zlib.compress(rgb, 9)
            objetos.append(
                f"<< /Type /XObject /Subtype /Image /Name /{nombre} /Width {ancho} /Height {alto} "
                f"/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length {len(flujo_imagen)} >>\nstream\n".encode("ascii")
                + flujo_imagen + b"\nendstream"
            )

        salida = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
        posiciones: list[int] = []
        for numero, cuerpo in enumerate(objetos, start=1):
            posiciones.append(len(salida))
            salida += f"{numero} 0 obj\n".encode("ascii") + cuerpo + b"\nendobj\n"
        inicio_xref = len(salida)
        salida += f"xref\n0 {len(objetos) + 1}\n".encode("ascii")
        salida += b"0000000000 65535 f \n"
        for posicion in posiciones:
            salida += f"{posicion:010d} 00000 n \n".encode("ascii")
        salida += (
            f"trailer\n<< /Size {len(objetos) + 1} /Root 1 0 R >>\nstartxref\n{inicio_xref}\n%%EOF\n"
        ).encode("ascii")
        return bytes(salida)


def _cabecera(pdf: _Pdf, negocio: str, titulo: str, subtitulo: str, logo_png: bytes | None = None) -> float:
    """Cabecera de cada salida. Devuelve la coordenada Y donde puede empezar el contenido."""
    pdf.rectangulo(0, A4_ALTO - 96, A4_ANCHO, 96, AZUL)
    if logo_png:
        pdf.imagen_png(logo_png, A4_ANCHO - MARGEN - 42, A4_ALTO - 72, 36, 36)
    pdf.texto(MARGEN, A4_ALTO - 44, negocio[:70], tamano=17, color=(1, 1, 1), negrita=True)
    pdf.texto(MARGEN, A4_ALTO - 70, titulo, tamano=12, color=(0.85, 0.90, 1.0))
    pdf.texto(A4_ANCHO - MARGEN - 150, A4_ALTO - 70, subtitulo, tamano=8.5, color=(0.85, 0.90, 1.0))
    return A4_ALTO - 130


def _pie(pdf: _Pdf, pagina: int, total: int, negocio: str) -> None:
    pdf.linea(MARGEN, 56, A4_ANCHO - MARGEN, 56, grosor=0.5)
    pdf.texto(MARGEN, 40, f"{negocio[:48]} · lista para clientes, sin costo ni existencias", tamano=7.5, color=GRIS)
    pdf.texto(A4_ANCHO - MARGEN - 60, 40, f"Página {pagina} de {total}", tamano=7.5, color=GRIS)


def lista_precios_pdf(filas: Sequence[FilaPrecio], *, negocio: str,
                      generado: datetime | None = None, logo_png: bytes | None = None) -> bytes:
    """PDF de la lista de precios: Categoría / Producto / Precio, agrupada por categoría.

    Se corta de página cuando no cabe otra fila; la cabecera de la tabla se repite en cada hoja.
    """
    momento = (generado or datetime.now()).strftime("%d/%m/%Y %H:%M")
    pdf = _Pdf()
    y = _cabecera(pdf, negocio, "Lista de precios", f"Generada el {momento}", logo_png)
    _cabecera_tabla(pdf, y)
    y -= 20
    categoria_anterior: str | None = None
    for fila in filas:
        if y < 80:
            pdf.nueva_pagina()
            y = _cabecera(pdf, negocio, "Lista de precios", f"Generada el {momento}", logo_png)
            _cabecera_tabla(pdf, y)
            y -= 20
            categoria_anterior = None
        if fila.categoria != categoria_anterior:
            if categoria_anterior is not None:
                y -= 4
            pdf.texto(MARGEN, y, (fila.categoria or "Sin categoría")[:34], tamano=9.5, color=AZUL, negrita=True)
            y -= 15
            categoria_anterior = fila.categoria
        pdf.texto(MARGEN + 6, y, fila.nombre[:58], tamano=10)
        precio = format_cup(fila.precio_cents)
        pdf.texto(A4_ANCHO - MARGEN - pdf_ancho_texto(precio, 10), y, precio, tamano=10, negrita=True)
        pdf.linea(MARGEN, y - 5, A4_ANCHO - MARGEN, y - 5, grosor=0.25, color=(0.85, 0.87, 0.89))
        y -= 17
    if not filas:
        pdf.texto(MARGEN, y, "No hay artículos activos para la lista.", tamano=11, color=GRIS)
    total = len(pdf.paginas) + (1 if pdf.actual else 0)
    # El pie se añade ya con el total conocido: se reconstruyen las páginas una sola vez.
    for indice, contenido in enumerate(pdf.paginas, start=1):
        _pie_en_contenido(contenido, indice, total, negocio)
    if pdf.actual:
        _pie_en_contenido(pdf.actual, total, total, negocio)
    return pdf.construir()


def _cabecera_tabla(pdf: _Pdf, y: float) -> None:
    pdf.texto(MARGEN + 6, y - 4, "Producto", tamano=8, color=GRIS, negrita=True)
    pdf.texto(A4_ANCHO - MARGEN - 46, y - 4, "Precio", tamano=8, color=GRIS, negrita=True)
    pdf.linea(MARGEN, y - 9, A4_ANCHO - MARGEN, y - 9, grosor=0.8)


def _pie_en_contenido(contenido: list[str], pagina: int, total: int, negocio: str) -> None:
    """Añade el pie a una página ya construida (el total solo se conoce al final)."""
    contenido.append(
        f"0.60 w 0.373 0.388 0.408 RG {MARGEN:.2f} 56 m {A4_ANCHO - MARGEN:.2f} 56 l S"
    )
    contenido.append(
        "BT /F1 7.50 Tf 0.373 0.388 0.408 rg "
        f"1 0 0 1 {MARGEN:.2f} 40 Tm ({_escapar_pdf((negocio[:48] + ' · lista para clientes, sin costo ni existencias'))}) Tj ET"
    )
    etiqueta = f"Página {pagina} de {total}"
    contenido.append(
        "BT /F1 7.50 Tf 0.373 0.388 0.408 rg "
        f"1 0 0 1 {A4_ANCHO - MARGEN - pdf_ancho_texto(etiqueta, 7.5):.2f} 40 Tm ({etiqueta}) Tj ET"
    )


def pdf_ancho_texto(texto: str, tamano: float) -> float:
    """Ancho aproximado del texto en Helvetica (suficiente para alinear el importe a la derecha)."""
    # Anchos medios medidos sobre Helvetica: 0,50 em para minúsculas y 0,58 para mayúsculas y dígitos.
    total = sum(0.58 if caracter.isupper() or caracter.isdigit() else 0.50 for caracter in texto)
    return total * tamano


def tarjetas_pdf(tarjetas: Sequence[TarjetaArticulo], *, negocio: str, comentario: str = "",
                 generado: datetime | None = None, logo_png: bytes | None = None) -> bytes:
    """PDF de tarjetas promocionales: dos columnas y hasta seis por hoja (como `DisenoTarjetas`)."""
    momento = (generado or datetime.now()).strftime("%d/%m/%Y %H:%M")
    pdf = _Pdf()
    paginas = [tarjetas[i:i + TARJETAS_POR_PAGINA] for i in range(0, len(tarjetas), TARJETAS_POR_PAGINA)]
    if not paginas:
        paginas = [[]]
    for grupo_indice, grupo in enumerate(paginas):
        if grupo_indice:
            pdf.nueva_pagina()
        y = _cabecera(pdf, negocio, "Tarjetas promocionales", f"Generadas el {momento}", logo_png)
        if comentario:
            pdf.texto(MARGEN, y, comentario[:110], tamano=10.5, color=TINTA)
            y -= 20
        ancho = (A4_ANCHO - MARGEN * 2 - 18) / 2
        alto = 132
        for indice, tarjeta in enumerate(grupo):
            columna, fila = indice % 2, indice // 2
            x = MARGEN + columna * (ancho + 18)
            arriba = y - fila * (alto + 14)
            pdf.rectangulo(x, arriba - alto, ancho, alto, (0.953, 0.961, 0.976))
            pdf.texto(x + 12, arriba - 24, tarjeta.categoria[:24].upper(), tamano=7.5, color=AZUL, negrita=True)
            pdf.texto(x + 12, arriba - 58, tarjeta.nombre[:30], tamano=13, negrita=True)
            pdf.texto(x + 12, arriba - 92, format_cup(tarjeta.precio_cents), tamano=15, color=AZUL, negrita=True)
            if tarjeta.unidad:
                pdf.texto(x + 12, arriba - 112, f"Precio por {tarjeta.unidad[:22]}", tamano=8, color=GRIS)
        if not grupo:
            pdf.texto(MARGEN, y, "No hay artículos para las tarjetas.", tamano=11, color=GRIS)
    total = len(pdf.paginas) + (1 if pdf.actual else 0)
    for indice, contenido in enumerate(pdf.paginas, start=1):
        _pie_en_contenido(contenido, indice, total, negocio)
    if pdf.actual:
        _pie_en_contenido(pdf.actual, total, total, negocio)
    return pdf.construir()


# --------------------------------------------------------------------------- PNG (tarjetas)

def tarjeta_png(tarjeta: TarjetaArticulo, *, negocio: str, comentario: str = "", logo_png: bytes | None = None) -> bytes:
    """PNG cuadrado (1080×1080) de una tarjeta: negocio, nombre, precio y unidad.

    Es la salida «Imagen/Tarjetas» de Android llevada al escritorio: se comparte por mensajería y no lleva costo ni
    existencias, igual que el PDF y el HTML.
    """
    lienzo = Lienzo(TARJETA_PX, TARJETA_PX, fondo=(255, 255, 255))
    azul = tuple(int(canal * 255) for canal in AZUL)
    # Banda superior de marca y banda inferior con el recordatorio de moneda.
    lienzo.rectangulo(0, 0, TARJETA_PX, 210, azul)
    lienzo.rectangulo(0, TARJETA_PX - 120, TARJETA_PX, 120, (243, 245, 250))
    if logo_png:
        logo_width, logo_height, logo_rgb = decodificar_png_rgb(logo_png)
        lienzo.imagen_rgb(870, 30, 150, 150, logo_width, logo_height, logo_rgb)
    # El nombre del negocio se ajusta al ancho en vez de recortarse: dos líneas como máximo.
    for posicion, linea in enumerate(_partir(negocio.upper(), 22)[:2]):
        lienzo.texto(TARJETA_MARGEN, 56 + posicion * 56, linea, escala=4, color=(255, 255, 255))
    lienzo.texto(TARJETA_MARGEN, 168, (tarjeta.categoria or "PRODUCTO").upper()[:30], escala=2,
                 color=(214, 226, 250))

    # Bloque central: comentario, nombre (hasta 2 líneas) y precio, repartido para que no quede hueco muerto.
    y = 300
    if comentario:
        lienzo.texto(TARJETA_MARGEN, y, comentario.upper()[:34], escala=2, color=(120, 126, 134))
        y += 80
    for linea in _partir(tarjeta.nombre.upper(), 18)[:2]:
        lienzo.texto(TARJETA_MARGEN, y, linea, escala=5, color=(26, 28, 30))
        y += 70
    y += 40
    lienzo.texto(TARJETA_MARGEN, y, format_cup(tarjeta.precio_cents), escala=7, color=azul)
    if tarjeta.unidad:
        lienzo.texto(TARJETA_MARGEN, y + 110, f"PRECIO POR {tarjeta.unidad}".upper()[:40], escala=2,
                     color=(95, 99, 104))
    lienzo.texto(TARJETA_MARGEN, TARJETA_PX - 84, "PRECIOS EN CUP", escala=2, color=(95, 99, 104))
    return lienzo.png()


def tarjetas_png(tarjetas: Iterable[TarjetaArticulo], *, negocio: str, comentario: str = "",
                 logo_png: bytes | None = None) -> list[tuple[str, bytes]]:
    """Una imagen por tarjeta, con un nombre de fichero estable y sin caracteres problemáticos."""
    salida: list[tuple[str, bytes]] = []
    for posicion, tarjeta in enumerate(tarjetas, start=1):
        nombre = "".join(c if c.isalnum() or c in " -_" else "-" for c in tarjeta.nombre).strip() or "articulo"
        salida.append((f"{posicion:02d}-{nombre[:40].replace(' ', '_')}.png", tarjeta_png(
            tarjeta, negocio=negocio, comentario=comentario, logo_png=logo_png)))
    return salida


def _partir(texto: str, ancho: int) -> list[str]:
    """Corta el texto en líneas sin partir palabras cuando se pueda."""
    palabras = texto.split()
    lineas: list[str] = []
    actual = ""
    for palabra in palabras:
        candidato = f"{actual} {palabra}".strip()
        if len(candidato) <= ancho:
            actual = candidato
        else:
            if actual:
                lineas.append(actual)
            actual = palabra[:ancho]
    if actual:
        lineas.append(actual)
    return lineas or [""]
