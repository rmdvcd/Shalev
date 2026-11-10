"""Utilidades compartidas por las pruebas de licencia GL.

Las claves que se generan aquí son **de prueba**: viven en memoria y nunca se
fijan en la configuración de un build.
"""

from __future__ import annotations

import base64
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from dshalev.licencia import contrato
from dshalev.licencia.almacen import AlmacenLicencia, AnclaInstalacion
from dshalev.licencia.identidad import Identidad

KID = "gl-redeem-v1"
APP_ID = "DSHALEV"
AHORA = datetime(2026, 11, 11, 12, 0, 0, tzinfo=timezone.utc)
LICENCIA_ID = "22222222-2222-4222-8222-222222222222"
REDENCION_ID = "33333333-3333-4333-8333-333333333333"


def clave_p256() -> ec.EllipticCurvePrivateKey:
    return ec.generate_private_key(ec.SECP256R1())


def spki_der(clave: ec.EllipticCurvePrivateKey) -> bytes:
    return clave.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )


def spki_b64(clave: ec.EllipticCurvePrivateKey) -> str:
    return base64.b64encode(spki_der(clave)).decode("ascii")


def b64url(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).decode("ascii").rstrip("=")


def cargar_vector() -> dict:
    ruta = Path(__file__).parent / "fixtures" / "dshalev-protocol-v1.json"
    return json.loads(ruta.read_text(encoding="utf-8"))


def crear_identidad(
    tmp_path: Path,
    nombre: str = "identidad.bin",
    clave: ec.EllipticCurvePrivateKey | None = None,
) -> Identidad:
    return Identidad(tmp_path / nombre, clave if clave is not None else clave_p256())


def crear_almacen(
    tmp_path: Path,
    identidad: Identidad,
    *,
    ahora=AHORA,
    nombre: str = "licencia.bin",
) -> AlmacenLicencia:
    reloj = ahora if callable(ahora) else (lambda: ahora)

    ancla = AnclaInstalacion(tmp_path / "ancla.bin", huella=identidad.huella, ahora=reloj)
    return AlmacenLicencia(
        tmp_path / nombre, huella=identidad.huella, app_id=APP_ID, ancla=ancla, ahora=reloj
    )


def contenido_licencia(
    identidad: Identidad,
    *,
    license_id: str = LICENCIA_ID,
    tipo: str = "MENSUAL",
    estado: str = "ACTIVA",
    emitida: datetime = AHORA,
    vence: datetime | None = None,
    secundarias: int = 0,
    con_clave: bool = True,
) -> dict:
    if tipo == "PERPETUA":
        vencimiento = None
    else:
        vencimiento = vence or (emitida + timedelta(days=30))
    contenido = {
        "v": 1,
        "licenseId": license_id,
        "appId": APP_ID,
        "type": tipo,
        "status": estado,
        "issuedAt": contrato.formatear_instante(emitida),
        "expiresAt": None if vencimiento is None else contrato.formatear_instante(vencimiento),
        "deviceThumbprint": identidad.huella,
        "redemptionId": REDENCION_ID,
        "transferredFrom": None,
        "secundarias": secundarias,
    }
    if con_clave:
        contenido["devicePublicKey"] = contrato.base64_estandar(identidad.spki_der)
    return contenido


def firmar_servicio(contenido: dict, clave_servicio: ec.EllipticCurvePrivateKey) -> str:
    return contrato.base64_estandar(
        clave_servicio.sign(contrato.bytes_firma_licencia(contenido), ec.ECDSA(hashes.SHA256()))
    )


def sobre_licencia(contenido: dict, clave_servicio: ec.EllipticCurvePrivateKey) -> dict:
    return {
        "v": 1,
        "alg": contrato.ALG_LICENCIA,
        "kid": KID,
        "license": contenido,
        "sig": firmar_servicio(contenido, clave_servicio),
    }


def sobre_estado(
    contenido: dict,
    *,
    estado: str,
    comprobado_en: datetime,
    clave_servicio: ec.EllipticCurvePrivateKey,
) -> dict:
    documento = dict(contenido)
    documento["status"] = estado
    documento["checkedAt"] = contrato.formatear_instante(comprobado_en)
    firma = contrato.base64_estandar(
        clave_servicio.sign(contrato.bytes_firma_estado(documento), ec.ECDSA(hashes.SHA256()))
    )
    return {
        "v": 1,
        "alg": contrato.ALG_ESTADO,
        "kid": KID,
        "status": documento,
        "sig": firma,
    }


def crear_subtoken(
    licencia_primaria: dict,
    *,
    huella_secundaria: str,
    slot: int,
    clave_primaria: ec.EllipticCurvePrivateKey,
) -> dict:
    token = contrato.SubToken(
        v=1,
        alg=contrato.ALG_SUBTOKEN,
        license_id=licencia_primaria["licenseId"],
        device_thumbprint=huella_secundaria,
        slot=slot,
        allowed=licencia_primaria["secundarias"],
        expires_at=licencia_primaria.get("expiresAt") or "PERPETUA",
        sig="",
    )
    firma = clave_primaria.sign(token.bytes_firma(), ec.ECDSA(hashes.SHA256()))
    return contrato.SubToken(
        v=token.v,
        alg=token.alg,
        license_id=token.license_id,
        device_thumbprint=token.device_thumbprint,
        slot=token.slot,
        allowed=token.allowed,
        expires_at=token.expires_at,
        sig=contrato.base64_estandar(firma),
    ).a_dict()
