"""Genera el vector de prueba docs/vectores/directorio-v1.json y sus claves raíz de prueba.

Estructura conforme al Anexo A de IMPLEMENTACION_SHALEV.md.
"""

from __future__ import annotations

import base64
import json
from pathlib import Path
import sys

# Agregar Web-Desktop al path para importar el módulo de directorio
ROOT_DIR = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT_DIR / "Web-Desktop"))

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
from dshalev.licencia.directorio import firmar_directorio, verificar_y_parsear_directorio, calcular_huella

# Claves raíz de prueba deterministas
# Escalares fijos para reproducibilidad exacta en tests de Android, iOS y DShalev
SCALAR_RAIZ_A = 0x4141414141414141414141414141414141414141414141414141414141414141
SCALAR_RAIZ_B = 0x4242424242424242424242424242424242424242424242424242424242424242

PRIV_A = ec.derive_private_key(SCALAR_RAIZ_A, ec.SECP256R1())
PRIV_B = ec.derive_private_key(SCALAR_RAIZ_B, ec.SECP256R1())

SPKI_A_DER = PRIV_A.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
SPKI_B_DER = PRIV_B.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

SPKI_A_B64 = base64.b64encode(SPKI_A_DER).decode("ascii")
SPKI_B_B64 = base64.b64encode(SPKI_B_DER).decode("ascii")

# Claves de prueba correspondientes a PR #148 (vigentes de GL)
ECDH_VIGENTE_SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELQaSJKdqp6b/uqGc+0juRctWGdb9mHQZj2CZRHsrKNsux9UhBbTHirLhF7q6KupNGc9PwwiqNAVcpAT7MR2EvA=="
ECDH_VIGENTE_HUELLA = calcular_huella(base64.b64decode(ECDH_VIGENTE_SPKI))

SIGN_VIGENTE_SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAED3b2+vZldNNAkjW9X6hKZebUnQ4ycS7lqCK1Z6IMdRHlhhXq1/h7eBRj/4VYAzIjdSFKRknFaATttWM4NTlEQg=="
SIGN_VIGENTE_HUELLA = calcular_huella(base64.b64decode(SIGN_VIGENTE_SPKI))

DATOS_V1 = {
    "v": 1,
    "seq": 1,
    "emitidaEn": 1776470400,  # 2026-04-18T00:00:00Z
    "claves": [
        {
            "kid": "gl-ecdh-vigente",
            "uso": "ecdh",
            "spki": ECDH_VIGENTE_SPKI,
            "huella": ECDH_VIGENTE_HUELLA,
            "desde": 1700000000,
            "hasta": None,
            "estado": "vigente",
        },
        {
            "kid": "gl-sign-vigente",
            "uso": "firma",
            "spki": SIGN_VIGENTE_SPKI,
            "huella": SIGN_VIGENTE_HUELLA,
            "desde": 1700000000,
            "hasta": None,
            "estado": "vigente",
        },
    ],
}


def generar() -> None:
    vectores_dir = ROOT_DIR / "docs" / "vectores"
    vectores_dir.mkdir(parents=True, exist_ok=True)

    sobre_json = firmar_directorio(DATOS_V1, PRIV_A, identificador_raiz="A")

    # Formatear bonito para archivo
    sobre_dict = json.loads(sobre_json)
    archivo_v1 = vectores_dir / "directorio-v1.json"
    with open(archivo_v1, "w", encoding="utf-8") as f:
        json.dump(sobre_dict, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # Validar que el archivo recién creado es válido
    dir_valido = verificar_y_parsear_directorio(archivo_v1.read_text(encoding="utf-8"), raices_spki=[SPKI_A_DER])
    assert dir_valido.seq == 1
    assert len(dir_valido.claves) == 2

    # Generar README.md explicativo
    readme_content = f"""# Vectores de Prueba — Directorio de Confianza v1 (Anexo A)

Este directorio contiene vectores de prueba y especificación de claves para el Directorio de Confianza v1 (`directorio.json`).

## Claves Raíz de Prueba (Test Roots)

Claves fijas deterministas utilizadas para la suite de pruebas unitarias en Android, iOS y DShalev:

### Raíz A (Test)
- **Escalar privado:** `0x41414141...` (32 bytes `0x41`)
- **SPKI DER (Base64):** `{SPKI_A_B64}`
- **Huella SHA-256:** `{calcular_huella(SPKI_A_DER)}`

### Raíz B (Test)
- **Escalar privado:** `0x42424242...` (32 bytes `0x42`)
- **SPKI DER (Base64):** `{SPKI_B_B64}`
- **Huella SHA-256:** `{calcular_huella(SPKI_B_DER)}`

## Archivos de Vectores

- `directorio-v1.json`: Directorio válido con `v=1`, `seq=1`, firmado por la Raíz A de prueba.
  Contiene la clave ECDH vigente de PR #148 y la clave de firma vigente de PR #148.

## Nota de Verificación Cruzada
Generado conforme al Anexo A de `IMPLEMENTACION_SHALEV.md`. La verificación cruzada con el CLI oficial de GL queda registrada como pendiente de ejecución cuando el CLI remoto esté accesible.
"""
    (vectores_dir / "README.md").write_text(readme_content, encoding="utf-8")
    print(f"Vector generado en {archivo_v1}")
    print(f"README generado en {vectores_dir / 'README.md'}")


if __name__ == "__main__":
    generar()
