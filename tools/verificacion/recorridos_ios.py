#!/usr/bin/env python3
"""Comprueba que cada paso de los recorridos guiados de iOS tiene su anclaje en el código.

Un paso cuyo `objetivo` nadie marca con `.recorridoObjetivo("…")` **se salta siempre**: el globo no aparece y el
recorrido se queda corto o vacío, sin ningún error de compilación que lo delate. Es exactamente lo que ocurría al
declarar 13 recorridos y solo ocho anclajes.

Las pruebas del dominio iOS (`iOS/Tests/IShalevDomainTests`) **no pueden comprobarlo**: ese target solo compila
`Sources/Domain`, mientras que los anclajes viven en `Sources/Features`. Por eso la comprobación es este script, que
corre sin Xcode y se puede llamar desde el CI.

Uso:
    python3 tools/verificacion/recorridos_ios.py          # informa y sale con 1 si falta algún anclaje
    python3 tools/verificacion/recorridos_ios.py --lista  # solo lista los anclajes encontrados
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
RECORRIDOS = RAIZ / "iOS" / "Sources" / "Domain" / "Recorridos.swift"
FUENTES = RAIZ / "iOS" / "Sources"


def objetivos_declarados() -> list[tuple[str, str]]:
    """(recorrido, objetivo) de cada paso, en el orden en que aparecen en `Recorridos.swift`."""
    texto = RECORRIDOS.read_text(encoding="utf-8")
    encontrados: list[tuple[str, str]] = []
    # Cada caso del enum abre con `case .clave:` y los pasos van como PasoRecorrido("objetivo", "Título", "Frase").
    for bloque in re.split(r"\n        case \.", texto):
        match = re.match(r"([a-zA-Z]+):", bloque)
        if not match:
            continue
        recorrido = match.group(1)
        for objetivo in re.findall(r'PasoRecorrido\("([a-z_]+)"', bloque):
            encontrados.append((recorrido, objetivo))
    return encontrados


def anclajes_en_codigo() -> dict[str, list[str]]:
    """Objetivo → ficheros donde alguien lo marca."""
    anclajes: dict[str, list[str]] = {}
    for archivo in sorted(FUENTES.rglob("*.swift")):
        texto = archivo.read_text(encoding="utf-8")
        for objetivo in re.findall(r'\.recorridoObjetivo\("([a-z_]+)"\)', texto):
            anclajes.setdefault(objetivo, []).append(archivo.relative_to(RAIZ).as_posix())
    return anclajes


def main(argv: list[str]) -> int:
    declarados = objetivos_declarados()
    anclajes = anclajes_en_codigo()
    if "--lista" in argv:
        for objetivo, ficheros in sorted(anclajes.items()):
            print(f"{objetivo}: {', '.join(ficheros)}")
        return 0

    if not declarados:
        print("No se pudo leer ningún paso de Recorridos.swift: revisa el formato del fichero.", file=sys.stderr)
        return 1

    huerfanos = [(r, o) for r, o in declarados if o not in anclajes]
    print(f"Pasos declarados: {len(declarados)} en {len({r for r, _ in declarados})} recorridos")
    print(f"Objetivos anclados en el código: {len(anclajes)}")

    # Aviso (no error): un anclaje sin paso no rompe nada, pero suele ser un resto de un paso borrado.
    sobrantes = sorted(set(anclajes) - {o for _, o in declarados})
    if sobrantes:
        print(f"Aviso: {len(sobrantes)} anclaje(s) sin paso que los use: {', '.join(sobrantes)}")

    if huerfanos:
        print("\nPasos sin anclaje (se saltarían siempre):", file=sys.stderr)
        for recorrido, objetivo in huerfanos:
            print(f"  {recorrido}: {objetivo}", file=sys.stderr)
        return 1

    print("Todos los pasos tienen anclaje.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
