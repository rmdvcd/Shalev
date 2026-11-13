#!/usr/bin/env sh
# Acceso de compatibilidad desde la raíz del repositorio.
# La configuración y el wrapper Gradle del proyecto Android viven en Android/.
set -eu

REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
exec "$REPO_ROOT/Android/gradlew" -p "$REPO_ROOT/Android" "$@"
