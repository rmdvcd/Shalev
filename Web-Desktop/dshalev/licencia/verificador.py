"""Verificación criptográfica de licencias, estados y sub-tokens de GL.

Nada de lo que hay aquí se fía del contenido: primero se valida la estructura
del sobre, luego el ``kid`` contra el mapa fijado en la configuración de
compilación, después la firma ECDSA P-256/SHA-256 en DER y, solo entonces, los
campos y su relación con la identidad local.

Las claves públicas del servicio se reciben como SPKI DER en un mapa
``kid -> bytes``; nunca se leen de formularios, respuestas ni respaldos.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Mapping

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import load_der_public_key

from . import contrato
from .contrato import ContratoError

# Campos que identifican una licencia y no pueden cambiar al consultar estado.
INMUTABLES = (
    "licenseId",
    "appId",
    "type",
    "issuedAt",
    "expiresAt",
    "deviceThumbprint",
    "redemptionId",
    "devicePublicKey",
)


class LicenciaInvalida(RuntimeError):
    """La licencia o el estado recibidos no superan la verificación."""

    def __init__(self, motivo: str, detalle: str = "") -> None:
        self.motivo = motivo
        self.detalle = detalle
        super().__init__(f"{motivo}: {detalle}" if detalle else motivo)


@dataclass(frozen=True)
class LicenciaVerificada:
    """Sobre ya verificado, su contenido y el estado calculado con el reloj."""

    sobre: dict[str, Any]
    contenido: dict[str, Any]
    estado: str

    @property
    def utilizable(self) -> bool:
        return self.estado in contrato.ESTADOS_UTILIZABLES

    @property
    def es_perpetua(self) -> bool:
        return self.contenido["type"] == "PERPETUA"


@dataclass(frozen=True)
class EstadoVerificado:
    """Documento de estado verificado y el estado resultante."""

    sobre: dict[str, Any]
    contenido: dict[str, Any]
    estado: str
    comprobado_en: datetime


def cargar_clave_publica(spki_der: bytes) -> ec.EllipticCurvePublicKey:
    """Carga un SPKI y exige que sea P-256 (secp256r1)."""
    try:
        llave = load_der_public_key(bytes(spki_der))
    except Exception as exc:  # noqa: BLE001 - la causa no importa al llamador
        raise LicenciaInvalida("clave", "SPKI ilegible.") from exc
    if not isinstance(llave, ec.EllipticCurvePublicKey):
        raise LicenciaInvalida("clave", "La clave no es EC.")
    if not isinstance(llave.curve, ec.SECP256R1):
        raise LicenciaInvalida("clave", "La clave no es P-256.")
    return llave


def verificar_firma(spki_der: bytes, mensaje: bytes, firma_der: bytes) -> None:
    """Verifica una firma ECDSA P-256/SHA-256 en DER; lanza si no cuadra."""
    if not firma_der or len(firma_der) > contrato.MAXIMO_FIRMA:
        raise LicenciaInvalida("firma", "Tamaño de firma inválido.")
    llave = cargar_clave_publica(spki_der)
    try:
        llave.verify(firma_der, mensaje, ec.ECDSA(hashes.SHA256()))
    except InvalidSignature as exc:
        raise LicenciaInvalida("firma", "La firma no corresponde.") from exc


def _validar_sobre(sobre: Any, *, alg: str, claves: Mapping[str, bytes], nombre: str) -> dict[str, Any]:
    if not isinstance(sobre, Mapping):
        raise LicenciaInvalida("estructura", f"{nombre} no es un objeto.")
    if set(sobre) != {"v", "alg", "kid", "license" if nombre == "licencia" else "status", "sig"}:
        raise LicenciaInvalida("estructura", f"{nombre} tiene campos inesperados.")
    if sobre["v"] != contrato.VERSION_SERVICIO or isinstance(sobre["v"], bool):
        raise LicenciaInvalida("version", "Versión de sobre no soportada.")
    if sobre["alg"] != alg:
        raise LicenciaInvalida("alg", "Algoritmo de sobre no soportado.")
    kid = sobre["kid"]
    if not isinstance(kid, str) or kid not in claves:
        raise LicenciaInvalida("kid", "kid no autorizado.")
    return dict(sobre)


def _firma_valida(sobre: Mapping[str, Any], claves: Mapping[str, bytes], mensaje: bytes) -> None:
    try:
        firma = contrato.decodificar_base64(
            sobre.get("sig"), nombre="sig", maximo=contrato.MAXIMO_FIRMA
        )
    except ContratoError as exc:
        raise LicenciaInvalida("firma", str(exc)) from exc
    verificar_firma(claves[sobre["kid"]], mensaje, firma)


def verificar_licencia(
    sobre: Any,
    *,
    claves: Mapping[str, bytes],
    app_id: str,
    huella_local: str,
    ahora: datetime | None = None,
) -> LicenciaVerificada:
    """Verifica un sobre ``GL-REDEEM-LICENSE1`` completo.

    Comprueba versión, algoritmo, ``kid`` fijado, firma DER, estructura, tipos,
    límites, ``appId``, fechas, estado y que ``devicePublicKey`` corresponda a
    ``deviceThumbprint`` y a la identidad local. No guarda nada.
    """
    documento = _validar_sobre(sobre, alg=contrato.ALG_LICENCIA, claves=claves, nombre="licencia")
    try:
        contenido = contrato.validar_contenido_licencia(documento["license"])
    except ContratoError as exc:
        raise LicenciaInvalida("estructura", str(exc)) from exc
    _firma_valida(documento, claves, contrato.bytes_firma_licencia(contenido))

    if contenido["appId"] != app_id:
        raise LicenciaInvalida("app", "La licencia es para otra aplicación.")
    if not contrato.es_huella(huella_local):
        raise LicenciaInvalida("dispositivo", "Huella local inválida.")
    if contenido["deviceThumbprint"] != huella_local:
        raise LicenciaInvalida("dispositivo", "La licencia es para otro equipo.")
    if contenido.get("devicePublicKey") is None:
        raise LicenciaInvalida("dispositivo", "La licencia no incluye la clave del dispositivo.")

    instante = ahora or datetime.now(timezone.utc)
    estado = contrato.estado_efectivo(contenido, ahora=instante)
    return LicenciaVerificada(sobre=documento, contenido=contenido, estado=estado)


def verificar_estado(
    sobre: Any,
    *,
    claves: Mapping[str, bytes],
    app_id: str,
    huella_local: str,
    licencia_actual: Mapping[str, Any],
    ahora: datetime | None = None,
) -> EstadoVerificado:
    """Verifica un sobre ``GL-REDEEM-STATUS1`` y su relación con la licencia.

    Rechaza documentos de otra licencia, de otra app, de otro equipo o que
    contradigan campos inmutables de la licencia guardada. Un ``checkedAt``
    más de cinco minutos en el futuro se considera inválido.
    """
    documento = _validar_sobre(sobre, alg=contrato.ALG_ESTADO, claves=claves, nombre="estado")
    try:
        contenido = contrato.validar_documento_estado(documento["status"])
    except ContratoError as exc:
        raise LicenciaInvalida("estructura", str(exc)) from exc
    _firma_valida(documento, claves, contrato.bytes_firma_estado(contenido))

    if contenido["appId"] != app_id:
        raise LicenciaInvalida("app", "El estado es para otra aplicación.")
    if contenido["deviceThumbprint"] != huella_local:
        raise LicenciaInvalida("dispositivo", "El estado es para otro equipo.")
    if contenido.get("devicePublicKey") is None:
        raise LicenciaInvalida("dispositivo", "El estado no incluye la clave del dispositivo.")
    for campo in INMUTABLES:
        if contenido.get(campo) != licencia_actual.get(campo):
            raise LicenciaInvalida("licencia", f"El estado contradice {campo} de la licencia.")

    instante = ahora or datetime.now(timezone.utc)
    comprobado = contrato.analizar_instante(contenido["checkedAt"])
    if comprobado > instante + _segundos(contrato.TOLERANCIA_RELOJ_SERVIDOR):
        raise LicenciaInvalida("reloj", "El estado está fechado en el futuro.")
    estado = contrato.estado_efectivo(contenido, ahora=instante)
    return EstadoVerificado(
        sobre=documento, contenido=contenido, estado=estado, comprobado_en=comprobado
    )


def verificar_subtoken(
    token: Any,
    *,
    licencia_primaria: Mapping[str, Any],
    huella_secundaria: str,
    ahora: datetime | None = None,
    permitir_vencido: bool = False,
) -> contrato.SubToken:
    """Verifica un sub-token con la clave pública de la instalación primaria.

    No se usa todavía en ninguna ruta: las secundarias no están habilitadas
    comercialmente. Existe para que la propuesta esté probada contra el vector.
    """
    try:
        subtoken = contrato.analizar_subtoken(token)
    except ContratoError as exc:
        raise LicenciaInvalida("subtoken", str(exc)) from exc

    if licencia_primaria.get("licenseId") != subtoken.license_id:
        raise LicenciaInvalida("subtoken", "El sub-token pertenece a otra licencia.")
    if licencia_primaria.get("secundarias") != subtoken.allowed:
        raise LicenciaInvalida("subtoken", "El cupo no coincide con la licencia primaria.")
    esperado = licencia_primaria.get("expiresAt") or "PERPETUA"
    if subtoken.expires_at != esperado:
        raise LicenciaInvalida("subtoken", "El vencimiento no coincide con la licencia primaria.")
    if subtoken.device_thumbprint != huella_secundaria:
        raise LicenciaInvalida("subtoken", "El sub-token es para otro equipo.")
    if licencia_primaria.get("status") in ("REVOCADA", "VENCIDA"):
        raise LicenciaInvalida("subtoken", "La licencia primaria no está activa.")
    if subtoken.expires_at != "PERPETUA" and not permitir_vencido:
        instante = ahora or datetime.now(timezone.utc)
        if contrato.analizar_instante(subtoken.expires_at) <= instante:
            raise LicenciaInvalida("subtoken", "El sub-token está vencido.")
    clave = licencia_primaria.get("devicePublicKey")
    if not isinstance(clave, str):
        raise LicenciaInvalida("subtoken", "Falta la clave de la instalación primaria.")
    try:
        spki = contrato.decodificar_base64(clave, nombre="devicePublicKey")
    except ContratoError as exc:
        raise LicenciaInvalida("subtoken", str(exc)) from exc
    if contrato.huella_spki(spki) != licencia_primaria.get("deviceThumbprint"):
        raise LicenciaInvalida("subtoken", "La clave primaria no corresponde a su huella.")
    try:
        firma = contrato.decodificar_base64(subtoken.sig, nombre="sig", maximo=contrato.MAXIMO_FIRMA)
    except ContratoError as exc:
        raise LicenciaInvalida("subtoken", str(exc)) from exc
    verificar_firma(spki, subtoken.bytes_firma(), firma)
    return subtoken


def verificar_documento_guardado(
    documento: str,
    *,
    claves: Mapping[str, bytes],
    app_id: str,
    huella_local: str,
    ahora: datetime | None = None,
) -> LicenciaVerificada:
    """Parsea y verifica el sobre guardado en disco (texto JSON)."""
    try:
        sobre = json.loads(documento)
    except (TypeError, ValueError) as exc:
        raise LicenciaInvalida("estructura", "La licencia guardada no es JSON.") from exc
    return verificar_licencia(
        sobre, claves=claves, app_id=app_id, huella_local=huella_local, ahora=ahora
    )


def _segundos(cantidad: int) -> timedelta:
    return timedelta(seconds=cantidad)
