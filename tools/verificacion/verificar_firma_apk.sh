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
# Por defecto revisa app/build/outputs/apk/release/app-release.apk.
#
# Sale con 0 si la firma es la vigente (v2/v3 verificadas y SHA-256 idéntico) y con 1 en cualquier otro caso.
# =====================================================================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/release/app-release.apk}"

# Clave vigente desde la 0.32.0 (RELEASE.md, paso 1): alias "shalev", RSA 4096 PKCS12, válida hasta 2054-03-08.
# NO la cambies: las licencias GL emitidas dependen de ella.
ESPERADA="4F:3B:5F:24:15:E7:03:16:27:8A:7C:0D:93:A3:45:2A:88:79:02:68:8E:C7:E0:B8:00:9D:1F:D8:69:AD:E8:37"

[ -f "$APK" ] || { echo "FALLO: no existe el APK: $APK"; echo "Compila primero: ./gradlew :app:assembleRelease (con keystore.properties o las variables Shalev_* ya configuradas)"; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "FALLO: falta ANDROID_HOME (o ANDROID_SDK_ROOT)"; exit 1; }
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)apksigner"
[ -x "$APKSIGNER" ] || { echo "FALLO: no se encontró apksigner en $SDK/build-tools/*/"; exit 1; }

echo "== Verificando firma de: $APK"
SALIDA="$("$APKSIGNER" verify --verbose --print-certs "$APK")"
echo "$SALIDA"

echo "$SALIDA" | grep -q "Verified using v2 scheme: true" || { echo "FALLO: el APK no está firmado con esquema v2"; exit 1; }
OBTENIDA="$(echo "$SALIDA" | grep -oP "Signer #1 certificate SHA-256 digest: \K[0-9A-Fa-f:]+" | tr 'a-f' 'A-F' | head -1)"
[ -n "$OBTENIDA" ] || { echo "FALLO: no se pudo leer la huella SHA-256 del certificado"; exit 1; }

if [ "$OBTENIDA" != "$ESPERADA" ]; then
  echo "FALLO: la huella del APK ($OBTENIDA) NO es la clave vigente de Shalev."
  echo "       Esperada (GL-compatible): $ESPERADA"
  echo "       No cambies de keystore ni de alias: firma con el shalev-release.jks documentado en RELEASE.md."
  exit 1
fi
echo "OK: firmado con la clave vigente (alias shalev, compatible con las licencias GL)."
echo "    SHA-256: $OBTENIDA"
