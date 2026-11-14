#!/usr/bin/env python3
"""Regla de botones de Shalev (1.1).

Todo botón muestra **texto** (en ventanas emergentes) o **icono + texto**, y el texto visible es **una sola palabra**.
Solo icono únicamente para las excepciones: cantidad −/+, adelante/atrás, filtro y exportar/compartir.

Recorre el código Kotlin de `app` y `designsystem` y, en cada llamada a los botones del sistema de diseño, calcula el
texto visible:

- ShalevPrimaryButton / ShalevSecondaryButton / ShalevTextButton: `etiqueta`, o si falta `text`.
- ShalevIconAction / ShalevFab: `etiqueta`, o si falta `contentDescription`.
- ShalevDialog con `onConfirm`: `confirmEtiqueta`, o si falta `confirmDescription` (por defecto «Confirmar»).
- ShalevStatusBanner con `actionIcon`: `actionEtiqueta`, o si falta `actionDescription`.

Cada literal de ese texto debe ser una sola palabra; las constantes (`TextosX.Y`) se resuelven. Las llamadas solo con
icono (`mostrarTexto = false`, `soloIcono = true`) deben usar uno de los iconos de las excepciones.

Uso: python3 tools/verificacion/botones.py [--detalle]   (sale con 1 si hay incumplimientos)
"""
from __future__ import annotations

import os
import re
import sys
from dataclasses import dataclass

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FUENTES = [
    os.path.join(RAIZ, "Android", "app", "src", "main", "kotlin"),
    os.path.join(RAIZ, "Android", "designsystem", "src", "main", "kotlin"),
]

# Iconos permitidos en botones solo con icono (excepciones de la regla).
ICONOS_EXCEPCION = {
    "Disminuir", "Aumentar",  # cantidad −/+ (el «slider» de cantidades y pasos)
    "Atras", "Siguiente", "Adelante", "Anterior",  # adelante / atrás
    "Filtrar",  # filtro
    "Exportar", "Compartir",  # exportar / compartir
}

# nombre → (índice posicional del texto, nombre del parámetro del texto, parámetro de la palabra visible)
BOTONES = {
    "ShalevPrimaryButton": (0, "text", "etiqueta"),
    "ShalevSecondaryButton": (0, "text", "etiqueta"),
    "ShalevTextButton": (0, "text", "etiqueta"),
    "ShalevIconAction": (1, "contentDescription", "etiqueta"),
    "ShalevFab": (1, "contentDescription", "etiqueta"),
}
ICONO = {
    "ShalevPrimaryButton": (2, "icon"),
    "ShalevSecondaryButton": (2, "icon"),
    "ShalevTextButton": (2, "icon"),
    "ShalevIconAction": (0, "icon"),
    "ShalevFab": (0, "icon"),
}


@dataclass
class Hallazgo:
    archivo: str
    linea: int
    funcion: str
    texto: str
    motivo: str

    def __str__(self) -> str:
        return f"{self.archivo}:{self.linea}: {self.funcion}: {self.motivo} «{self.texto}»"


def archivos_kotlin(carpetas: list[str]) -> list[str]:
    salida = []
    for carpeta in carpetas:
        for dp, _, fs in os.walk(carpeta):
            salida += [os.path.join(dp, f) for f in fs if f.endswith(".kt")]
    return sorted(salida)


_CONST = re.compile(r'const val (\w+)\s*=\s*"((?:[^"\\]|\\.)*)"')
_OBJ = re.compile(r"^\s*(?:internal |private )?(?:data )?object\s+(\w+)")


def constantes(textos: dict[str, str]) -> dict[str, set[str]]:
    """Mapa nombre → valores (`NOMBRE` y `Objeto.NOMBRE`)."""
    tabla: dict[str, set[str]] = {}
    for contenido in textos.values():
        objeto = None
        for linea in contenido.split("\n"):
            m = _OBJ.match(linea)
            if m:
                objeto = m.group(1)
            c = _CONST.search(linea)
            if c:
                tabla.setdefault(c.group(1), set()).add(c.group(2))
                if objeto:
                    tabla.setdefault(f"{objeto}.{c.group(1)}", set()).add(c.group(2))
    return tabla


def _cierre(txt: str, i: int) -> int:
    """Índice del paréntesis que cierra el que abre en `i` (respeta cadenas y plantillas)."""
    nivel = 0
    j = i
    en_cadena = False
    while j < len(txt):
        c = txt[j]
        if en_cadena:
            if c == "\\":
                j += 2
                continue
            if c == '"':
                en_cadena = False
            elif c == "$" and j + 1 < len(txt) and txt[j + 1] == "{":
                # plantilla: salta hasta su llave de cierre
                k = j + 2
                prof = 1
                while k < len(txt) and prof:
                    prof += {"{": 1, "}": -1}.get(txt[k], 0)
                    k += 1
                j = k
                continue
        else:
            if c == '"':
                en_cadena = True
            elif c == "/" and txt[j:j + 2] == "//":
                j = txt.find("\n", j)
                if j < 0:
                    return len(txt)
                continue
            elif c in "([{":
                nivel += 1
            elif c in ")]}":
                nivel -= 1
                if nivel == 0:
                    return j
        j += 1
    return len(txt)


def argumentos(cuerpo: str) -> list[str]:
    salida, nivel, actual, en_cadena, k = [], 0, "", False, 0
    while k < len(cuerpo):
        c = cuerpo[k]
        if en_cadena:
            actual += c
            if c == "\\" and k + 1 < len(cuerpo):
                actual += cuerpo[k + 1]
                k += 2
                continue
            if c == '"':
                en_cadena = False
        elif c == "/" and cuerpo[k:k + 2] == "//":
            fin = cuerpo.find("\n", k)
            k = len(cuerpo) if fin < 0 else fin
            continue
        elif c == '"':
            en_cadena = True
            actual += c
        elif c in "([{":
            nivel += 1
            actual += c
        elif c in ")]}":
            nivel -= 1
            actual += c
        elif c == "," and nivel == 0:
            salida.append(actual.strip())
            actual = ""
        else:
            actual += c
        k += 1
    if actual.strip():
        salida.append(actual.strip())
    return salida


_NOMBRADO = re.compile(r"^(\w+)\s*=(?!=)\s*(.*)$", re.S)
_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
_REFERENCIA = re.compile(r"\b((?:[A-Z]\w*\.)?[A-Z][A-Z0-9_]+)\b")


def separar(args: list[str]) -> tuple[list[str], dict[str, str]]:
    posicionales, nombrados = [], {}
    for a in args:
        m = _NOMBRADO.match(a)
        if m and not a.startswith('"'):
            nombrados[m.group(1)] = m.group(2).strip()
        else:
            posicionales.append(a)
    return posicionales, nombrados


def palabras(expr: str, tabla: dict[str, set[str]]) -> tuple[list[str], bool]:
    """Textos que puede mostrar `expr` y si se pudo resolver alguno."""
    literales = [m.group(1) for m in _LITERAL.finditer(expr)]
    sin_literales = _LITERAL.sub("", expr)
    valores = list(literales)
    for ref in _REFERENCIA.findall(sin_literales):
        partes = ref.split(".")
        for clave in (ref, partes[-1]):
            if clave in tabla:
                valores += sorted(tabla[clave])
                break
    return valores, bool(valores)


def es_una_palabra(texto: str) -> bool:
    t = texto.strip()
    return bool(t) and not re.search(r"\s", t) and "${" not in t and "$" not in t.replace("\\$", "")


def revisar(textos: dict[str, str], detalle: bool = False) -> tuple[list[Hallazgo], list[Hallazgo]]:
    """Devuelve (incumplimientos, sin resolver)."""
    tabla = constantes(textos)
    errores: list[Hallazgo] = []
    dudas: list[Hallazgo] = []
    patron = re.compile(r"\b(ShalevPrimaryButton|ShalevSecondaryButton|ShalevTextButton|ShalevIconAction|ShalevFab|ShalevDialog|ShalevStatusBanner)\(")
    for ruta, txt in textos.items():
        rel = os.path.relpath(ruta, RAIZ)
        for m in patron.finditer(txt):
            nombre = m.group(1)
            if txt[max(0, m.start() - 4):m.start()] == "fun ":
                continue
            # Comentarios KDoc / de línea
            ini_linea = txt.rfind("\n", 0, m.start()) + 1
            prefijo = txt[ini_linea:m.start()].strip()
            if prefijo.startswith("*") or prefijo.startswith("//"):
                continue
            fin = _cierre(txt, m.end() - 1)
            pos, nom = separar(argumentos(txt[m.end():fin]))
            linea = txt.count("\n", 0, m.start()) + 1

            if nombre == "ShalevDialog":
                if nom.get("onConfirm", "") in ("", "null"):
                    continue
                expr = nom.get("confirmEtiqueta") or nom.get("confirmDescription") or '"Confirmar"'
            elif nombre == "ShalevStatusBanner":
                if nom.get("actionIcon", "null") == "null" or nom.get("onAction", "null") == "null":
                    continue
                expr = nom.get("actionEtiqueta") or nom.get("actionDescription") or nom.get("text") or (pos[0] if pos else "")
            else:
                i_txt, p_txt, p_etq = BOTONES[nombre]
                solo_icono = nom.get("mostrarTexto") == "false" or nom.get("soloIcono") == "true"
                if solo_icono:
                    i_ico, p_ico = ICONO[nombre]
                    icono = nom.get(p_ico) or (pos[i_ico] if len(pos) > i_ico else "")
                    nombre_icono = icono.split(".")[-1].strip()
                    if nombre_icono not in ICONOS_EXCEPCION:
                        errores.append(Hallazgo(rel, linea, nombre, icono, "solo icono fuera de las excepciones"))
                    continue
                expr = nom.get(p_etq) or nom.get(p_txt) or (pos[i_txt] if len(pos) > i_txt else "")

            valores, resuelto = palabras(expr, tabla)
            if not resuelto:
                dudas.append(Hallazgo(rel, linea, nombre, expr.strip()[:80], "sin resolver"))
                continue
            for v in valores:
                if not es_una_palabra(v):
                    errores.append(Hallazgo(rel, linea, nombre, v, "más de una palabra"))
    return errores, dudas


def leer(carpetas: list[str]) -> dict[str, str]:
    textos = {}
    for ruta in archivos_kotlin(carpetas):
        with open(ruta, encoding="utf-8") as f:
            textos[ruta] = f.read()
    return textos


def main() -> int:
    detalle = "--detalle" in sys.argv
    errores, dudas = revisar(leer(FUENTES), detalle)
    for e in errores:
        print(e)
    if detalle:
        for d in dudas:
            print(f"(revisar a mano) {d}")
    print(f"Regla de botones: {len(errores)} incumplimientos, {len(dudas)} textos calculados en ejecución.")
    return 1 if errores else 0


if __name__ == "__main__":
    sys.exit(main())
