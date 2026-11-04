#!/usr/bin/env bash
# =====================================================================================================================
# Shalev — publicar la actualización en GitHub Releases (se ejecuta en el PC del desarrollador).
#
# Flujo completo y con guardas:
#   1. Lee versionName de app/build.gradle.kts → etiqueta v<versionName> (p. ej. v1.0.1).
#   2. Falla si ya existe una Release con esa etiqueta (no se republica nada).
#   3. Compila ./gradlew :app:assembleRelease (firma con el keystore existente: keystore.properties
#      o variables Shalev_*; NUNCA genera claves nuevas).
#   4. Verifica la firma con verificar_firma_apk.sh (clave vigente: gl-release.jks, alias gl,
#      SHA-256 13:9D:…:17:38, la de GL.apk y de la Release v1.0). Si falla, NO publica.
#   5. Sube SOLO el APK universal como Shalev-<version>.apk + Shalev-<version>.apk.sha256
#      (formato sha256sum), que es lo que el actualizador de la app consume (releases/latest).
#   6. Crea la Release pública (no borrador ni pre-release) con las notas indicadas.
#
# Uso:
#   bash tools/verificacion/publicar_release.sh [notas.md]
#   bash tools/verificacion/publicar_release.sh            # notas automáticas mínimas
#
# Requiere: gh autenticado (gh auth status), gradle wrapper, keystore configurado.
# =====================================================================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE="$ROOT/gradlew"
BUILD="$ROOT/app/build.gradle.kts"

VERSION="$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$BUILD" | head -1)"
[ -n "$VERSION" ] || { echo "FALLO: no se leyó versionName de $BUILD"; exit 1; }
TAG="v$VERSION"
REPO="${Shalev_GITHUB_REPO:-rmdvcd/Shalev}"
echo "== Versión de la app: $VERSION (versionName) → etiqueta $TAG en $REPO"

# 1. No republicar una release existente.
if gh api "repos/$REPO/releases/tags/$TAG" >/dev/null 2>&1; then
  echo "FALLO: ya existe la Release $TAG en $REPO. Sube versionName/versionCode en app/build.gradle.kts antes de publicar."
  exit 1
fi

# 2. Compilar release firmado (usa keystore.properties o Shalev_*; no toca claves).
echo "== Compilando :app:assembleRelease (puede tardar varios minutos)…"
( cd "$ROOT" && "$GRADLE" :app:assembleRelease )

# 3. Verificar la firma del APK (clave vigente de GL.apk / v1.0).
APK_UNIVERSAL="$ROOT/app/build/outputs/apk/release/app-universal-release.apk"
[ -f "$APK_UNIVERSAL" ] || { echo "FALLO: no está el APK universal: $APK_UNIVERSAL"; exit 1; }
echo "== Verificando firma del APK…"
bash "$ROOT/tools/verificacion/verificar_firma_apk.sh" "$APK_UNIVERSAL"

# 4. Preparar los recursos con los nombres que consume el actualizador.
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
APK="$STAGE/Shalev-$VERSION.apk"
cp "$APK_UNIVERSAL" "$APK"
( cd "$STAGE" && sha256sum "Shalev-$VERSION.apk" > "Shalev-$VERSION.apk.sha256" )
echo "== Recursos preparados:"
ls -la "$STAGE"
echo "   SHA-256 del APK: $(cut -d' ' -f1 "$STAGE/Shalev-$VERSION.apk.sha256")"

# 5. Notas de la release.
NOTAS="${1:-}"
if [ -z "$NOTAS" ]; then
  cat > "$STAGE/notas.md" <<EOF
# Shalev v$VERSION

Actualización de la app. Instálala por encima de la versión anterior (los datos se conservan: misma firma).

- Firma del APK: SHA-256 del certificado 13:9D:3D:A9:3B:D3:AB:B8:81:A2:C5:55:03:DA:E0:B0:59:AB:4C:36:A6:8A:D1:63:94:A8:B5:26:FF:D4:17:38 (la misma de siempre).
- SHA-256 del APK: \`$(cut -d' ' -f1 "$STAGE/Shalev-$VERSION.apk.sha256")\` (también en Shalev-$VERSION.apk.sha256).

Completa aquí las novedades de esta versión antes de ejecutar, o pasa un archivo de notas como argumento.
EOF
  NOTAS="$STAGE/notas.md"
  echo "== AVISO: se usarán notas automáticas. Para publicar con novedades reales:"
  echo "   bash tools/verificacion/publicar_release.sh ruta/a/notas.md"
  read -r -p "¿Publicar igualmente con estas notas? [s/N] " resp
  [ "${resp:-N}" = "s" ] || { echo "Cancelado. Nada publicado."; exit 1; }
fi

# 6. Crear la Release pública con el APK universal y su .sha256.
echo "== Creando Release $TAG en $REPO…"
gh release create "$TAG" \
  --repo "$REPO" \
  --title "Shalev $TAG" \
  --notes-file "$NOTAS" \
  "$STAGE/Shalev-$VERSION.apk" \
  "$STAGE/Shalev-$VERSION.apk.sha256"

echo "== Listo: $(gh api "repos/$REPO/releases/tags/$TAG" --jq .html_url)"
echo "   El actualizador de la app la verá en su próxima consulta (releases/latest) o con Ajustes → Buscar ahora."
