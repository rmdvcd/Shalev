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
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature
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

# Clave pública de canje de PRUEBA: determinista (escalar privado 0x43 repetido 32 veces), no es la clave
# de producción `gl-redeem-v1` de DShalev. Sirve para que las tres plataformas ejerciten el camino de canje
# con el directorio de prueba; en producción la publica GL y aquí solo viaja la mitad pública.
REDEEM_VECTOR_SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFSz1EqAD2T4rf34udOXYGlaf3NhxxhTeSe50DldUmCWJ59u6uKUtgukQ90gCDZrox9Hv4UJe4SDxNC8FyG1k/w=="
REDEEM_VECTOR_HUELLA = calcular_huella(base64.b64decode(REDEEM_VECTOR_SPKI))

# Firmas HISTÓRICAS fijadas en `LicenseTrust` (Android) y `GLTrust` (iOS). Al cargarse el directorio, este pasa a
# ser autoritativo (D2): si solo publicara la firma vigente, las licencias ya emitidas dejarían de verificar. Se
# publican con `hasta` = retirada, de modo que un licencia emitida dentro de su intervalo sigue validando y ninguna
# licencia nueva puede firmarse con ellas.
FIRMAS_HISTORICAS = [
    ("gl-sign-1022", "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8TwMudgF0ioPFGp+VOQDU+xBDEQQQRHErwPyKQupkBGS8WL/dQVAFN4t3PIddOBl3Kg60ucThoOicZFctdtjIQ==", 1758470400, 1761177600),
    ("gl-sign-1005", "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==", 1700000000, 1761177600),
    ("gl-sign-1015b", "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPjc7tw29A0TaynecwlYDphDAtavpfNQ6njeGIX59hIDO+ID9KxTLo4ZaxlPJ/enkCzlBo+5JEMn5AUN8bneC7Q==", 1700000000, 1761177600),
    ("gl-sign-1015a", "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/Kx1wh2WpM/JcPeNs7e7a5QvRMARHAPreFZ9YauzFguI85mT7/wC/jmKxR1ml5SGrGWCfA88Qo+PQ4efSUSsdg==", 1700000000, 1761177600),
    ("gl-sign-0930", "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==", 1700000000, 1761177600),
]

# Base de canje de PRUEBA y appIds por producto (los mismos identificadores que usan APK, IPA y DShalev).
ENDPOINT_BASE_VECTOR = "https://gl-redeem.example.test"
APP_IDS_VECTOR = {
    "SHALEV-ANDROID": "SHALEV-ANDROID",
    "SHALEV-IOS": "SHALEV-IOS",
    "DSHALEV": "DSHALEV",
}

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
        *[
            {
                "kid": kid,
                "uso": "firma",
                "spki": spki,
                "huella": calcular_huella(base64.b64decode(spki)),
                "desde": desde,
                "hasta": hasta,
                "estado": "retirada",
            }
            for kid, spki, desde, hasta in FIRMAS_HISTORICAS
        ],
        {
            "kid": "gl-redeem-vector",
            "uso": "canje",
            "spki": REDEEM_VECTOR_SPKI,
            "huella": REDEEM_VECTOR_HUELLA,
            "desde": 1700000000,
            "hasta": None,
            "estado": "vigente",
        },
    ],
    "canje": {
        "endpoint_base": ENDPOINT_BASE_VECTOR,
        "app_ids": APP_IDS_VECTOR,
    },
}


# Firma ECDSA fijada: `ec.ECDSA` usa nonce aleatorio, de modo que sin este valor cada ejecución produciría
# una firma distinta y los tres `directorio-bootstrap.json` dejarían de coincidir byte a byte con el vector.
# Se reutiliza la firma ya emitida mientras los `datos` no cambien; si cambian, se regenera y se pega aquí.
FIRMA_V1 = "uLjRzbnw3a7FYwiLFyTs7_TZs62H5ySahh2yuv-_AzmG3MwQ8oUFQHk8CuzWPN4y88bFNSo92Px2LiBarcWCIg"


def _sobre_estable(datos_obj: dict) -> dict:
    """Devuelve el sobre firmado con `FIRMA_V1`, regenerando y avisando si los datos ya no coinciden."""
    from dshalev.licencia.directorio import DOMINIO_DIRECTORIO

    datos_str = json.dumps(datos_obj, separators=(",", ":"), ensure_ascii=False)
    mensaje = DOMINIO_DIRECTORIO + datos_str.encode("utf-8")
    firma = FIRMA_V1
    try:
        # El sobre guarda la firma en 64 bytes crudos (r||s); `verify` de `cryptography` la exige en DER.
        cruda = base64.urlsafe_b64decode(firma + "=" * (-len(firma) % 4))
        der = encode_dss_signature(int.from_bytes(cruda[:32], "big"), int.from_bytes(cruda[32:], "big"))
        PRIV_A.public_key().verify(der, mensaje, ec.ECDSA(hashes.SHA256()))
    except Exception:
        nuevo = json.loads(firmar_directorio(datos_obj, PRIV_A, identificador_raiz="A"))
        print("Los datos cambiaron: nueva firma para FIRMA_V1 =", nuevo["firma"])
        firma = nuevo["firma"]
    return {"datos": datos_str, "firma": firma, "raiz": "A"}


def generar() -> None:
    vectores_dir = ROOT_DIR / "docs" / "vectores"
    vectores_dir.mkdir(parents=True, exist_ok=True)

    # Formatear bonito para archivo
    sobre_dict = _sobre_estable(DATOS_V1)
    archivo_v1 = vectores_dir / "directorio-v1.json"
    with open(archivo_v1, "w", encoding="utf-8") as f:
        json.dump(sobre_dict, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # Validar que el archivo recién creado es válido
    dir_valido = verificar_y_parsear_directorio(archivo_v1.read_text(encoding="utf-8"), raices_spki=[SPKI_A_DER])
    assert dir_valido.seq == 1
    assert len(dir_valido.claves) == 8
    assert dir_valido.endpoint_base == ENDPOINT_BASE_VECTOR
    assert dir_valido.app_ids["DSHALEV"] == "DSHALEV"

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
  Contiene la clave ECDH vigente de PR #148, la clave de firma vigente de PR #148 y una clave de canje
  de prueba (`gl-redeem-vector`, escalar determinista `0x43` repetido 32 veces).
  El bloque `canje` publica `endpoint_base = https://gl-redeem.example.test` (dominio reservado de pruebas,
  no es el servicio real) y `app_ids` para `SHALEV-ANDROID`, `SHALEV-IOS` y `DSHALEV`.

### Firmas históricas

El directorio publica también las cinco firmas que `LicenseTrust` (Android) y `GLTrust` (iOS) tienen fijadas de
rotaciones anteriores (`gl-sign-1022`, `gl-sign-1005`, `gl-sign-1015a`, `gl-sign-1015b`, `gl-sign-0930`), con
`estado: retirada` y `hasta` = fecha de retirada. Es necesario porque, en cuanto existe un directorio efectivo,
este es autoritativo (defecto D2): si solo figurara la firma vigente, las licencias ya emitidas dejarían de
verificar al arrancar. Con `hasta` acotado siguen validando las licencias antiguas y ninguna nueva se puede
firmar con ellas.

### Por qué el vector lleva canje

Sin una clave `uso: "canje"` y una `endpoint_base` HTTPS, APK, IPA y DShalev no pueden ejercitar el canje:
la regla de apertura es la misma en las tres plataformas y falla cerrada. El vector permite probar el camino
completo sin publicar credenciales reales; en producción el directorio lo firma el servicio con sus propias claves.

## Nota de Verificación Cruzada
Generado conforme al Anexo A de `IMPLEMENTACION_SHALEV.md`. La verificación cruzada con el CLI oficial de GL queda registrada como pendiente de ejecución cuando el CLI remoto esté accesible.
"""
    (vectores_dir / "README.md").write_text(readme_content, encoding="utf-8")
    print(f"Vector generado en {archivo_v1}")
    print(f"README generado en {vectores_dir / 'README.md'}")


if __name__ == "__main__":
    generar()
