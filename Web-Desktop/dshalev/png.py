"""PNG sin dependencias: lienzo de color verdadero con texto dibujado a partir de un mapa de glifos 5×7.

El ejecutable portable de DShalev no lleva Pillow ni fuentes del sistema, y las tarjetas promocionales tienen que salir
como imagen igual que en Android (`FormatoSalida.IMAGEN`). Con `zlib` —que viene con Python— basta para escribir un
PNG válido; el texto se pinta con una tipografía de mapa de bits escalable.

Límites conscientes: la tipografía es de trazo (todo en mayúsculas legibles) y no sustituye a una fuente real. Se
eligió así porque una tarjeta con texto nítido y sin dependencias es preferible a una tarjeta que no se puede generar
en la instalación del cliente.
"""

from __future__ import annotations

import struct
import zlib

#: Glifos 5×7 en filas de 5 bits (16 = 10000). Solo mayúsculas, dígitos y signos usados en las tarjetas.
GLIFOS: dict[str, tuple[int, ...]] = {
    "A": (14, 17, 17, 31, 17, 17, 17), "B": (30, 17, 17, 30, 17, 17, 30),
    "C": (14, 17, 16, 16, 16, 17, 14), "D": (28, 18, 17, 17, 17, 18, 28),
    "E": (31, 16, 16, 30, 16, 16, 31), "F": (31, 16, 16, 30, 16, 16, 16),
    "G": (14, 17, 16, 23, 17, 17, 15), "H": (17, 17, 17, 31, 17, 17, 17),
    "I": (14, 4, 4, 4, 4, 4, 14), "J": (7, 2, 2, 2, 2, 18, 12),
    "K": (17, 18, 20, 24, 20, 18, 17), "L": (16, 16, 16, 16, 16, 16, 31),
    "M": (17, 27, 21, 21, 17, 17, 17), "N": (17, 25, 21, 19, 17, 17, 17),
    "O": (14, 17, 17, 17, 17, 17, 14), "P": (30, 17, 17, 30, 16, 16, 16),
    "Q": (14, 17, 17, 17, 21, 18, 13), "R": (30, 17, 17, 30, 20, 18, 17),
    "S": (15, 16, 16, 14, 1, 1, 30), "T": (31, 4, 4, 4, 4, 4, 4),
    "U": (17, 17, 17, 17, 17, 17, 14), "V": (17, 17, 17, 17, 17, 10, 4),
    "W": (17, 17, 17, 21, 21, 27, 17), "X": (17, 17, 10, 4, 10, 17, 17),
    "Y": (17, 17, 10, 4, 4, 4, 4), "Z": (31, 1, 2, 4, 8, 16, 31),
    "0": (14, 17, 19, 21, 25, 17, 14), "1": (4, 12, 4, 4, 4, 4, 14),
    "2": (14, 17, 1, 2, 4, 8, 31), "3": (31, 2, 4, 2, 1, 17, 14),
    "4": (2, 6, 10, 18, 31, 2, 2), "5": (31, 16, 30, 1, 1, 17, 14),
    "6": (6, 8, 16, 30, 17, 17, 14), "7": (31, 1, 2, 4, 8, 8, 8),
    "8": (14, 17, 17, 14, 17, 17, 14), "9": (14, 17, 17, 15, 1, 2, 12),
    " ": (0, 0, 0, 0, 0, 0, 0), ".": (0, 0, 0, 0, 0, 12, 12),
    ",": (0, 0, 0, 0, 12, 12, 8), "-": (0, 0, 0, 31, 0, 0, 0),
    "/": (1, 1, 2, 4, 8, 16, 16), "(": (2, 4, 8, 8, 8, 4, 2),
    ")": (8, 4, 2, 2, 2, 4, 8), ":": (0, 12, 12, 0, 12, 12, 0),
    "%": (25, 26, 2, 4, 8, 11, 19), "!": (4, 4, 4, 4, 4, 0, 4),
    "&": (12, 18, 20, 8, 21, 18, 13), "'": (4, 4, 8, 0, 0, 0, 0),
    "+": (0, 4, 4, 31, 4, 4, 0), "Á": (4, 10, 14, 17, 31, 17, 17),
    "É": (4, 10, 31, 16, 30, 16, 31), "Í": (4, 10, 14, 4, 4, 4, 14),
    "Ó": (4, 10, 14, 17, 17, 17, 14), "Ú": (4, 10, 17, 17, 17, 17, 14),
    "Ñ": (10, 21, 3, 5, 9, 25, 17), "Ü": (10, 0, 17, 17, 17, 17, 14),
}
#: Caracteres sin glifo propio: se dibujan como el carácter base (así «á» se ve como «Á»).
SIN_GLIFO = {"Á": "A", "É": "E", "Í": "I", "Ó": "O", "Ú": "U", "À": "A", "È": "E", "Ì": "I",
             "Ò": "O", "Ù": "U", "Â": "A", "Ê": "E", "Ô": "O", "Ç": "C", "Ã": "A", "Õ": "O"}

ANCHO_GLIFO = 5
ALTO_GLIFO = 7
SEPARACION = 1


class Lienzo:
    """Imagen RGB que se construye por píxeles y se exporta a PNG."""

    def __init__(self, ancho: int, alto: int, fondo: tuple[int, int, int] = (255, 255, 255)) -> None:
        if ancho <= 0 or alto <= 0:
            raise ValueError("El lienzo necesita medidas positivas")
        self.ancho = ancho
        self.alto = alto
        self.pixeles = bytearray(bytes(fondo) * (ancho * alto))

    # -- primitivas ---------------------------------------------------------------------------------

    def punto(self, x: int, y: int, color: tuple[int, int, int]) -> None:
        if 0 <= x < self.ancho and 0 <= y < self.alto:
            posicion = (y * self.ancho + x) * 3
            self.pixeles[posicion:posicion + 3] = bytes(color)

    def rectangulo(self, x: int, y: int, ancho: int, alto: int, color: tuple[int, int, int]) -> None:
        x0, y0 = max(0, x), max(0, y)
        x1, y1 = min(self.ancho, x + ancho), min(self.alto, y + alto)
        if x1 <= x0 or y1 <= y0:
            return
        fila = bytes(color) * (x1 - x0)
        for yy in range(y0, y1):
            posicion = (yy * self.ancho + x0) * 3
            self.pixeles[posicion:posicion + len(fila)] = fila

    def texto(self, x: int, y: int, contenido: str, *, escala: int = 2,
              color: tuple[int, int, int] = (26, 28, 30)) -> int:
        """Dibuja el texto desde `(x, y)` hacia la derecha. Devuelve el ancho ocupado en píxeles."""
        cursor = x
        for caracter in contenido.upper():
            if caracter == " ":
                cursor += (ANCHO_GLIFO + SEPARACION) * escala
                continue
            glifo = GLIFOS.get(caracter) or GLIFOS.get(SIN_GLIFO.get(caracter, ""), GLIFOS[" "])
            for fila, bits in enumerate(glifo):
                for columna in range(ANCHO_GLIFO):
                    if bits & (1 << (ANCHO_GLIFO - 1 - columna)):
                        # Se escala el píxel a un bloque de `escala` × `escala`.
                        for dy in range(escala):
                            for dx in range(escala):
                                self.punto(cursor + columna * escala + dx, y + fila * escala + dy, color)
            cursor += (ANCHO_GLIFO + SEPARACION) * escala
        return cursor - x

    def ancho_texto(self, contenido: str, escala: int = 2) -> int:
        return len(contenido) * (ANCHO_GLIFO + SEPARACION) * escala

    # -- exportación --------------------------------------------------------------------------------

    def png(self) -> bytes:
        """PNG de color verdadero (8 bits, sin canal alfa) con compresión por defecto de zlib."""
        crudo = bytearray()
        for fila in range(self.alto):
            crudo.append(0)  # Tipo de filtro «None» para cada scanline.
            inicio = fila * self.ancho * 3
            crudo += self.pixeles[inicio:inicio + self.ancho * 3]
        def bloque(tipo: bytes, datos: bytes) -> bytes:
            return (struct.pack(">I", len(datos)) + tipo + datos
                    + struct.pack(">I", zlib.crc32(tipo + datos) & 0xFFFFFFFF))
        cabecera = struct.pack(">IIBBBBB", self.ancho, self.alto, 8, 2, 0, 0, 0)
        return (b"\x89PNG\r\n\x1a\n" + bloque(b"IHDR", cabecera)
                + bloque(b"IDAT", zlib.compress(bytes(crudo), 9)) + bloque(b"IEND", b""))
