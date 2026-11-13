#!/usr/bin/env bash
# =====================================================================================================================
# Shalev — verificación de la firma del APK de release CONTRA LA CLAVE VIGENTE (sin cambiar claves).
#
# El deviceId de la licencia GL es "SPVI:" + ANDROID_ID, y el ANDROID_ID depende de la clave de firma.
# Si el APK sale firmado con otra clave, todas las licencias emitidas dejan de valer y Android exige
# desinstalar la app. Este script comprueba que el APK lleva EXACTAMENTE la clave documentada en RELEASE.md.
#
# Uso (en el PC del desarrollador, con Android SDK instalado; no necesita Gradle):
#   bash tools/verificacion/verificar_firma_apk.sh [ruta/al.apk]
# Por defecto revisa Android/app/build/outputs/apk/release/app-universal-release.apk.
#
# Sale con 0 si la firma es la vigente (v2/v3 verificadas y SHA-256 idéntico) y con 1 en cualquier otro caso.
# =====================================================================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${1:-$ROOT/Android/app/build/outputs/apk/release/app-universal-release.apk}"

# Clave vigente: la que firmó la Release v1.0 publicada (Shalev-1.0.apk) y la MISMA con la que se firma
# GL.apk (gl-release.jks, alias "gl", CN=GL) — prioridad según las claves de GL en sus releases.
# Ver docs/HISTORIAL_DESARROLLO.md (2026-10-22) y RELEASE.md. NO la cambies: las licencias GL emitidas
# dependen de ella (deviceId = "SPVI:" + ANDROID_ID, y el ANDROID_ID depende de la clave de firma).
# OJO: shalev-release.jks (4F:3B:…:E8:37) nunca se publicó; usarla anularía las licencias y forzaría
# desinstalar. Este script rechaza cualquier otra huella.
ESPERADA="13:9D:3D:A9:3B:D3:AB:B8:81:A2:C5:55:03:DA:E0:B0:59:AB:4C:36:A6:8A:D1:63:94:A8:B5:26:FF:D4:17:38"

[ -f "$APK" ] || { echo "FALLO: no existe el APK: $APK"; echo "Compila primero: ./gradlew :app:assembleRelease (con Android/keystore.properties o las variables Shalev_* ya configuradas)"; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "FALLO: falta ANDROID_HOME (o ANDROID_SDK_ROOT)"; exit 1; }
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)apksigner"
[ -x "$APKSIGNER" ] || { echo "FALLO: no se encontró apksigner en $SDK/build-tools/*/"; exit 1; }

echo "== Verificando firma de: $APK"
SALIDA="$("$APKSIGNER" verify --verbose --print-certs "$APK")"
echo "$SALIDA"

# apksigner actual incluye «(APK Signature Scheme v2)» y emite el SHA sin dos puntos.
# También se admite la salida corta de versiones anteriores; exigir v2 conserva compatibilidad API 26/27.
echo "$SALIDA" | grep -Eq '^Verified using v2 scheme( \([^)]*\))?: true[[:space:]]*$' || { echo "FALLO: el APK no está firmado con esquema v2"; exit 1; }
FIRMANTES="$(echo "$SALIDA" | grep -cE '^Signer #[0-9]+ certificate SHA-256 digest:' || true)"
[ "$FIRMANTES" = 1 ] || { echo "FALLO: se esperaba exactamente un firmante de Shalev"; exit 1; }
OBTENIDA="$(echo "$SALIDA" | sed -nE 's/^Signer #1 certificate SHA-256 digest: ([0-9A-Fa-f:]+)[[:space:]]*$/\1/p' | tr -d ':' | tr 'a-f' 'A-F')"
[[ "$OBTENIDA" =~ ^[0-9A-F]{64}$ ]] || { echo "FALLO: no se pudo leer la huella SHA-256 del certificado"; exit 1; }
ESPERADA_HEX="${ESPERADA//:/}"

if [ "$OBTENIDA" != "$ESPERADA_HEX" ]; then
  echo "FALLO: la huella del APK ($OBTENIDA) NO es la clave vigente de Shalev."
  echo "       Esperada (la de GL.apk / Release v1.0 publicada): $ESPERADA"
  echo "       No cambies de keystore ni de alias: firma con gl-release.jks (alias gl), el documentado en RELEASE.md."
  echo "       NUNCA firmes con shalev-release.jks (4F:3B:…:E8:37): nunca se publicó con ella y anularía las licencias GL."
  exit 1
fi
echo "OK: firmado con la clave vigente (alias gl, la de GL.apk y de la Release v1.0; compatible con las licencias GL)."
echo "    SHA-256: $OBTENIDA"
