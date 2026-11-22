#!/usr/bin/env python3
"""Comprobación de build D3: verificación de raíces de confianza en compilaciones release.

Garantiza que ningún release de Android, iOS o DShalev se produzca con raíces
vacías (``RAIZ_A_SPKI`` / ``RAIZ_B_SPKI``) o con claves de prueba de desarrollo,
lo que dejaría el canje de licencias cerrado sin aviso al usuario.

Uso:
    python3 tools/verificacion/verificar_raices_release.py [--release]
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

RUTA_ANDROID = ROOT_DIR / "Android" / "licencia" / "src" / "main" / "kotlin" / "cu" / "shalev" / "licencia" / "TrustDirectory.kt"
RUTA_IOS = ROOT_DIR / "iOS" / "Sources" / "Domain" / "TrustDirectory.swift"
RUTA_DSHALEV = ROOT_DIR / "Web-Desktop" / "dshalev" / "licencia" / "directorio.py"

RUTA_REDEEM_ANDROID = ROOT_DIR / "Android" / "licencia" / "src" / "main" / "kotlin" / "cu" / "shalev" / "licencia" / "redeem" / "RedeemContract.kt"
RUTA_REDEEM_IOS = ROOT_DIR / "iOS" / "Sources" / "Domain" / "Redeem" / "RedeemContract.swift"
RUTA_CONFIG_DSHALEV = ROOT_DIR / "Web-Desktop" / "dshalev" / "licencia" / "config_licencia.py"

CANJE_APP_IDS_PERMITIDOS = {"DSHALEV", "SHALEV-ANDROID", "SHALEV-IOS"}

MARCADORES_TEST = {
    "",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkE=",
}


def extraer_raices_android() -> tuple[str, str]:
    if not RUTA_ANDROID.exists():
        raise FileNotFoundError(f"No se encontró {RUTA_ANDROID}")
    texto = RUTA_ANDROID.read_text(encoding="utf-8")
    ma = re.search(r'const\s+val\s+RAIZ_A_SPKI\s*:\s*String\s*=\s*"([^"]*)"', texto)
    mb = re.search(r'const\s+val\s+RAIZ_B_SPKI\s*:\s*String\s*=\s*"([^"]*)"', texto)
    if not ma or not mb:
        raise ValueError("No se encontraron constantes RAIZ_A_SPKI / RAIZ_B_SPKI en Android")
    return ma.group(1).strip(), mb.group(1).strip()


def extraer_raices_ios() -> tuple[str, str]:
    if not RUTA_IOS.exists():
        raise FileNotFoundError(f"No se encontró {RUTA_IOS}")
    texto = RUTA_IOS.read_text(encoding="utf-8")
    ma = re.search(r'static\s+let\s+rootASPKI\s*:\s*String\s*=\s*"([^"]*)"', texto)
    mb = re.search(r'static\s+let\s+rootBSPKI\s*:\s*String\s*=\s*"([^"]*)"', texto)
    if not ma or not mb:
        raise ValueError("No se encontraron constantes rootASPKI / rootBSPKI en iOS")
    return ma.group(1).strip(), mb.group(1).strip()


def extraer_raices_dshalev() -> tuple[str, str]:
    if not RUTA_DSHALEV.exists():
        raise FileNotFoundError(f"No se encontró {RUTA_DSHALEV}")
    texto = RUTA_DSHALEV.read_text(encoding="utf-8")
    ma = re.search(r'RAIZ_A_SPKI\s*=\s*"([^"]*)"', texto)
    mb = re.search(r'RAIZ_B_SPKI\s*=\s*"([^"]*)"', texto)
    if not ma or not mb:
        raise ValueError("No se encontraron constantes RAIZ_A_SPKI / RAIZ_B_SPKI en DShalev")
    return ma.group(1).strip(), mb.group(1).strip()


def extraer_app_id_android() -> str:
    if not RUTA_REDEEM_ANDROID.exists():
        return ""
    texto = RUTA_REDEEM_ANDROID.read_text(encoding="utf-8")
    m = re.search(r'DEFAULT_APP_ID\s*=\s*"([^"]*)"', texto)
    return m.group(1).strip() if m else ""


def extraer_app_id_ios() -> str:
    if not RUTA_REDEEM_IOS.exists():
        return ""
    texto = RUTA_REDEEM_IOS.read_text(encoding="utf-8")
    m = re.search(r'defaultAppID\s*=\s*"([^"]*)"', texto)
    return m.group(1).strip() if m else ""


def extraer_app_id_dshalev() -> str:
    if not RUTA_CONFIG_DSHALEV.exists():
        return ""
    texto = RUTA_CONFIG_DSHALEV.read_text(encoding="utf-8")
    m = re.search(r'APP_ID\s*=\s*"([^"]*)"', texto)
    return m.group(1).strip() if m else ""


def verificar_raices(es_release: bool = False) -> list[str]:
    """Comprueba el estado de las raíces y CANJE_APP_ID en las 3 plataformas.

    En modo release (es_release=True), devuelve una lista de errores si alguna raíz
    está vacía o es un marcador de prueba, o si CANJE_APP_ID no está en el conjunto permitido.
    """
    errores = []

    android_a, android_b = extraer_raices_android()
    ios_a, ios_b = extraer_raices_ios()
    dshalev_a, dshalev_b = extraer_raices_dshalev()

    plataformas = [
        ("Android", android_a, android_b),
        ("iOS", ios_a, ios_b),
        ("DShalev", dshalev_a, dshalev_b),
    ]

    for nombre, ra, rb in plataformas:
        if es_release:
            if not ra or ra in MARCADORES_TEST:
                errores.append(f"[{nombre}] RAIZ_A_SPKI no puede estar vacía ni ser de prueba en release.")
            if not rb or rb in MARCADORES_TEST:
                errores.append(f"[{nombre}] RAIZ_B_SPKI no puede estar vacía ni ser de prueba en release.")

    app_ids = [
        ("Android", extraer_app_id_android()),
        ("iOS", extraer_app_id_ios()),
        ("DShalev", extraer_app_id_dshalev()),
    ]

    for nombre, app_id in app_ids:
        if app_id not in CANJE_APP_IDS_PERMITIDOS:
            errores.append(
                f"[{nombre}] CANJE_APP_ID '{app_id}' no está en el conjunto permitido {CANJE_APP_IDS_PERMITIDOS}."
            )

    return errores


def main() -> int:
    es_release = (
        "--release" in sys.argv
        or os.environ.get("BUILD_RELEASE") == "1"
        or os.environ.get("PRODUCCION") == "SI"
    )

    print(f"Verificando raíces de confianza SPVI (modo release: {es_release})...")
    errores = verificar_raices(es_release=es_release)

    if errores:
        print("\nFALLO D3: Comprobación de raíces de confianza fallida para release:")
        for err in errores:
            print(f"  - {err}")
        print("\nPara builds de release de producción se deben suministrar las claves raíces definitivas.")
        return 1

    print("Comprobación de raíces de confianza superada (marcadores válidos para desarrollo/test).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
