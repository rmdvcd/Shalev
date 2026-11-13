#!/usr/bin/env python3
"""Verifica y resume XML REALES de connectedAndroidTest, separado de compilar androidTest.

Falla si no existen resultados, si no se ejecutó ningún caso o si hubo errores/fallos.
No confunde un fallo al arrancar el emulador con una ejecución satisfactoria.
"""
from collections import defaultdict
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def anotar(nivel, mensaje):
    seguro = mensaje.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::{nivel}::{seguro}")


def main():
    modulos = defaultdict(lambda: [0, 0, 0])
    fallos = []
    incompleto = False
    for archivo in sorted(Path(".").glob("**/build/outputs/androidTest-results/connected/**/*.xml")):
        try:
            raiz = ET.parse(archivo).getroot()
        except (ET.ParseError, OSError):
            anotar("error", f"Instrumentados: XML ilegible en {archivo}")
            incompleto = True
            continue
        modulo = str(archivo).split("/build/")[0]
        for caso in raiz.iter("testcase"):
            modulos[modulo][0] += 1
            problemas = caso.findall("failure") + caso.findall("error")
            if problemas:
                modulos[modulo][1] += 1
                texto = (problemas[0].text or problemas[0].get("message", "")).strip()
                fallos.append((modulo, f"{caso.get('classname')}.{caso.get('name')}", texto))
            if caso.find("skipped") is not None:
                modulos[modulo][2] += 1
    total = sum(v[0] for v in modulos.values())
    if not total:
        anotar("error", "Instrumentados: sin resultados ejecutados. Compilar androidTest NO valida Room ni el emulador.")
        return 1
    for modulo, (casos, errores, omitidos) in sorted(modulos.items()):
        anotar("notice", f"Instrumentados {modulo}: {casos} casos, {errores} con fallo, {omitidos} omitidos")
    for requerido in ("app", "data"):
        casos, _, omitidos = modulos[requerido]
        if casos - omitidos <= 0:
            anotar("error", f"Instrumentados {requerido}: no se ejecutó ningún caso; no se valida solo con el otro módulo.")
            incompleto = True
    # GitHub solo guarda las primeras 10 anotaciones error por paso. Agrupar fallos idénticos evita
    # que 100 casos con la misma dependencia rota tapen los fallos de SQLite/negocio del otro módulo.
    grupos = {}
    for modulo, caso, texto in sorted(fallos, key=lambda f: (f[0] != "data", f[0], f[1])):
        clave = (modulo, texto.splitlines()[0] if texto else "")
        grupos.setdefault(clave, []).append((caso, texto))
    for (modulo, _), casos in list(grupos.items())[:10]:
        caso, texto = casos[0]
        anotar("error", f"{modulo}: {caso} ({len(casos)} con el mismo tipo de fallo): {texto[:2400]}")
    if len(grupos) > 10:
        anotar("notice", f"Quedan {len(grupos) - 10} tipos de fallo en los XML del artefacto informes-instrumentados.")
    if sum(v[0] - v[2] for v in modulos.values()) == 0:
        anotar("error", "Instrumentados: todos los casos fueron omitidos.")
        return 1
    return 1 if fallos or incompleto else 0


if __name__ == "__main__":
    sys.exit(main())
