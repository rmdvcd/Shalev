#!/usr/bin/env bash
# T0.1 del PLAN_CORRECCIONES.md — resumen legible de un fallo del CI.
#
# Uso:  bash tools/verificacion/resumen_fallos.sh /tmp/salida.log
#
# Por qué existe: los registros del CI se sirven desde un almacenamiento (Azure Blob) que algunos entornos no
# pueden leer; las anotaciones del check-run, en cambio, siempre están en la API de GitHub. Este script extrae lo
# esencial del fallo, escribe el detalle en $GITHUB_STEP_SUMMARY (pestaña Actions) y publica las líneas clave como
# ::error:: (anotaciones visibles por la API). Nunca sale con error: informa, no añade fallos ni los enmascara.
set -uo pipefail

LOG="${1:-/tmp/salida.log}"
MAX="${MAX_LINEAS:-160}"
SALIDA="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

# Líneas que suelen explicar un fallo de Gradle, Kotlin, Robolectric o del propio contenedor.
PATRONES='^e: |^error: |: error: |(Error|Warning): |Unresolved reference|Permission denied|Execution failed for task|^FAILURE: |^BUILD FAILED|^> Task .* FAILED| FAILED$|Exception|Caused by:|Could not (resolve|download|find|determine|create)|No such file|Configuration cache|^[0-9]+ errors?, [0-9]+ warnings?'

# --- 1. Lo esencial del log ----------------------------------------------------------------------------------
{
  printf '\n## Resumen del fallo\n'
  if [ ! -s "$LOG" ]; then
    printf '\nEl log `%s` no existe o está vacío (¿el fallo ocurrió antes de ejecutar el comando?).\n' "$LOG"
  else
    # Gradle imprime el título con asterisco («* What went wrong:») y el script lo buscaba sin él: por eso los
    # fallos sin `e:`/`FAILED` visibles (p. ej. lint) llegaban a la API sin su causa.
    if grep -qE '^\*? *What went wrong' "$LOG"; then
      printf '\n### Qué fue mal (Gradle)\n\n```\n'
      grep -A 30 -m1 -E '^\*? *What went wrong' "$LOG" || true
      printf '```\n'
    fi
    if grep -qE "$PATRONES" "$LOG"; then
      printf '\n### Líneas que explican el fallo\n\n```\n'
      grep -E "$PATRONES" "$LOG" | head -60 || true
      printf '```\n'
    fi
  fi
} >> "$SALIDA"

# --- 2. Detalle de los tests fallidos y de lint, desde los XML de resultados ---------------------------------
python3 - >> "$SALIDA" <<'PY' || true
import glob
import sys
import xml.etree.ElementTree as ET

def anotar(mensaje):
    # stderr no se redirige: el runner lo convierte en anotaciones legibles por la API de check-runs.
    print(f"::error::{mensaje.replace('%', '%25')}", file=sys.stderr)

def primeras_lineas(texto, n=30):
    lineas = (texto or "").splitlines()
    return "\n".join(lineas[:n]) + ("\n[...]" if len(lineas) > n else "")

fallos = []
for xml in glob.glob("**/build/test-results/**/*.xml", recursive=True):
    try:
        raiz = ET.parse(xml).getroot()
    except Exception:
        continue
    for caso in raiz.iter("testcase"):
        problemas = caso.findall("failure") + caso.findall("error")
        if problemas:
            fallos.append((caso.get("classname", "?"), caso.get("name", "?"), primeras_lineas(problemas[0].text)))

if fallos:
    print(f"\n### Tests fallidos (XML de resultados): {len(fallos)}\n")
    for clase, nombre, detalle in fallos[:15]:
        print(f"- `{clase}.{nombre}`\n\n```\n{detalle}\n```\n")
    for clase, nombre, detalle in fallos[:6]:
        primera = (detalle or "").splitlines()[0] if detalle else ""
        anotar(f"test fallido: {clase}.{nombre} — {primera}")

errores_lint = []
for xml in sorted(glob.glob("**/build/reports/lint-results-*.xml", recursive=True)):
    try:
        raiz = ET.parse(xml).getroot()
    except Exception:
        continue
    for issue in raiz.iter("issue"):
        if issue.get("severity") in ("Error", "Fatal"):
            lugar = issue.find("location")
            errores_lint.append(
                f"{issue.get('id')}: {issue.get('message')} "
                f"({lugar.get('file') if lugar is not None else '?'}:{lugar.get('line') if lugar is not None else '?'})"
            )

if errores_lint:
    print(f"\n### Errores de lint: {len(errores_lint)}\n")
    for error in errores_lint[:40]:
        print(f"- {error}")
    for error in errores_lint[:6]:
        anotar(f"lint: {error}")

if not fallos and not errores_lint:
    print("\n(Sin XML de tests ni de lint: el fallo fue antes de generarlos.)\n")
PY

# --- 3. Últimas líneas del log, para el contexto que no captan los extractos ---------------------------------
if [ -s "$LOG" ]; then
  {
    printf '\n### Últimas %s líneas del log\n\n```\n' "$MAX"
    tail -n "$MAX" "$LOG"
    printf '```\n'
  } >> "$SALIDA"
fi

# --- 4. Anotaciones (API de check-runs): máximo 10, priorizando lo que explica el fallo ----------------------
anotar() {
  local linea
  while IFS= read -r linea; do
    printf '::error::%s\n' "${linea//%/%25}"
  done
}
{
  grep -A 10 -m1 -E '^\*? *What went wrong' "$LOG" 2>/dev/null | head -10 || true
  grep -E '^e: |^error: |: error: |Unresolved reference|Permission denied|Execution failed for task' "$LOG" 2>/dev/null | head -8 || true
  grep -E '^> Task .* FAILED| FAILED$' "$LOG" 2>/dev/null | head -4 || true
  tail -n 2 "$LOG" 2>/dev/null || true
} | anotar | head -10

exit 0
