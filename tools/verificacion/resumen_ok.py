#!/usr/bin/env python3
"""T0.1/T0.5 del PLAN_CORRECCIONES.md — publica el resultado de una comprobación como aviso legible.

Uso (desde la raíz del repositorio):
    python3 tools/verificacion/resumen_ok.py tests     # cuenta los XML de resultados de tests
    python3 tools/verificacion/resumen_ok.py lint      # cuenta errores y avisos del XML de lint
    python3 tools/verificacion/resumen_ok.py apk       # tamaño y sha256 de los APK generados
    python3 tools/verificacion/resumen_ok.py api-min [log]   # última línea de api_minima.py

Escribe `::notice::` (anotaciones de nivel aviso, legibles por la API del check-run) y lo mismo por pantalla.
Nunca sale con error: si no encuentra datos, lo dice en un aviso.
"""
import glob
import hashlib
import os
import sys
import xml.etree.ElementTree as ET

def aviso(mensaje: str) -> None:
    print(f"::notice::{mensaje}")
    print(mensaje)

def resumen_tests() -> None:
    ejecutados = fallidos = errores = omitidos = 0
    modulos = {}
    for xml in glob.glob("**/build/test-results/**/*.xml", recursive=True):
        try:
            raiz = ET.parse(xml).getroot()
        except Exception:
            continue
        e = int(raiz.get("tests", 0)); f = int(raiz.get("failures", 0))
        er = int(raiz.get("errors", 0)); o = int(raiz.get("skipped", 0))
        ejecutados += e; fallidos += f; errores += er; omitidos += o
        modulo = xml.split("/build/")[0] or "."
        antes = modulos.get(modulo, (0, 0, 0, 0))
        modulos[modulo] = (antes[0] + e, antes[1] + f, antes[2] + er, antes[3] + o)
    if ejecutados == 0:
        aviso("tests JVM: no se encontró ningún XML de resultados (¿se ejecutó spviTests?)")
        return
    aviso(f"tests JVM: {ejecutados} ejecutados, {fallidos} fallidos, {errores} errores, {omitidos} omitidos")
    for modulo in sorted(modulos):
        e, f, er, o = modulos[modulo]
        print(f"  {modulo}: {e} ejecutados, {f + er} con fallo, {o} omitidos")

def resumen_lint() -> None:
    por_severidad = {}
    for xml in sorted(glob.glob("**/build/reports/lint-results-*.xml", recursive=True)):
        try:
            raiz = ET.parse(xml).getroot()
        except Exception:
            continue
        for issue in raiz.iter("issue"):
            severidad = issue.get("severity", "?")
            por_severidad[severidad] = por_severidad.get(severidad, 0) + 1
    if not por_severidad:
        aviso("lint: no se encontró el XML de resultados")
        return
    errores = por_severidad.get("Error", 0) + por_severidad.get("Fatal", 0)
    avisos = por_severidad.get("Warning", 0) + por_severidad.get("Information", 0)
    aviso(f"lint: {errores} errores, {avisos} avisos (baseline vacío)")
    print(f"  detalle por severidad: {por_severidad}")

def resumen_apk() -> None:
    apks = sorted(glob.glob("Android/app/build/outputs/apk/*/*.apk"))
    if not apks:
        aviso("apk: no se encontró ningún APK")
        return
    for ruta in apks:
        tamano_mb = os.path.getsize(ruta) / (1024 * 1024)
        sha = hashlib.sha256(open(ruta, "rb").read()).hexdigest()
        aviso(f"{os.path.basename(ruta)}: {tamano_mb:.1f} MB, sha256 {sha[:16]}…")

def resumen_api_minima(log: str) -> None:
    linea = ""
    try:
        with open(log, encoding="utf-8", errors="replace") as f:
            for renglon in f:
                if "API mínima" in renglon:
                    linea = renglon.strip()
    except OSError:
        pass
    aviso(linea or "api-minima: no se encontró la línea de resultado en el log")

def main() -> int:
    modo = sys.argv[1] if len(sys.argv) > 1 else ""
    try:
        if modo == "tests":
            resumen_tests()
        elif modo == "lint":
            resumen_lint()
        elif modo == "apk":
            resumen_apk()
        elif modo == "api-min":
            resumen_api_minima(sys.argv[2] if len(sys.argv) > 2 else "/tmp/salida.log")
        else:
            print(f"Uso: {sys.argv[0]} tests|lint|apk|api-min [log]", file=sys.stderr)
            return 2
    except Exception as exc:  # nunca debe romper el CI por no poder resumir
        aviso(f"resumen_ok.py no pudo resumir ({modo}): {exc!r}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
