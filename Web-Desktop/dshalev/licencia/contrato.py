"""Contrato de cable del servicio GL de canje (``services/redeem``).

Este módulo reproduce, del lado Python, lo que el servicio GL define en
``src/protocol.js`` y ``src/canonical.js``:

* los *transcripts* que firma el dispositivo son la concatenación de campos
  UTF-8, cada uno prefijado con su longitud en **uint32 big-endian**; no hay
  JSON, delimitadores ni separadores;
* el código de canje se normaliza quitando espacios y guiones y pasando a
  mayúsculas; el alfabeto excluye ``I`` y ``O`` para evitar confusiones;
* el sobre de licencia y el documento de estado se firman con el mismo
  esquema aunque con dominios distintos;
* el sub-token de secundarias es la excepción: es texto con separadores ``|``.

Las funciones de este módulo no firman ni verifican por sí solas: construyen
los bytes exactos y validan la estructura. La criptografía vive en
``verificador.py`` y en el cliente.
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import re
import struct
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Mapping, Sequence

# --- Versiones, algoritmos y dominios -------------------------------------

VERSION_SERVICIO = 1
VERSION_LICENCIA = 1
VERSION_ESTADO = 1
VERSION_SUBTOKEN = 1

DOMINIO_SOLICITUD_CANJE = "GL-REDEEM-REQUEST1"
DOMINIO_SOLICITUD_ESTADO = "GL-REDEEM-STATUS-REQUEST1"
DOMINIO_LICENCIA = "GL-REDEEM-LICENSE1"
DOMINIO_ESTADO = "GL-REDEEM-STATUS1"
DOMINIO_SUBTOKEN = "GL-REDEEM-SUB1"

ALG_LICENCIA = "GL-REDEEM-LICENSE-P256-SHA256-v1"
ALG_ESTADO = "GL-REDEEM-STATUS-P256-SHA256-v1"
ALG_SUBTOKEN = "GL-REDEEM-SUB-P256-SHA256-v1"

# --- Límites del servicio -------------------------------------------------

MAXIMO_CUERPO_PETICION = 16 * 1024
MAXIMO_CUERPO_RESPUESTA = 64 * 1024
MAXIMO_CODIGO_ENTRADA = 64
LONGITUD_CODIGO = 16
MAXIMO_SECUNDARIAS = 10
MAXIMO_CLAVE_DISPOSITIVO = 400
MAXIMO_FIRMA = 128
TOLERANCIA_RELOJ_SERVIDOR = 5 * 60  # segundos; el servicio rechaza fuera de ±5 min
TOLERANCIA_RELOJ_CLIENTE = 2 * 60 * 60  # marca de agua local

TIPOS_DIAS: dict[str, int | None] = {
    "MENSUAL": 30,
    "SEMESTRAL": 180,
    "ANUAL": 365,
    "PERPETUA": None,
}
ESTADOS_LICENCIA = ("ACTIVA", "VENCIDA", "REVOCADA", "PERPETUA")
ESTADOS_UTILIZABLES = ("ACTIVA", "PERPETUA")

ALFABETO_CODIGO = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
_CODIGO_RE = re.compile(r"^[0-9A-HJ-NP-Z]{16}$")
_HUELLA_RE = re.compile(r"^[A-Za-z0-9_-]{43}$")
_APP_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$")
_REQUEST_ID_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
_NONCE_RE = re.compile(r"^[A-Za-z0-9+/=_-]{16,128}$")
_LICENSE_ID_RE = re.compile(r"^[A-Za-z0-9._:-]{8,64}$")
_SPKI_RE = re.compile(r"^[A-Za-z0-9+/]+={0,2}$")
_REDEMPTION_ID_RE = re.compile(r"^[A-Za-z0-9._:-]{8,64}$")


class ContratoError(ValueError):
    """Un valor no cumple el contrato de GL."""


# --- Codificación ---------------------------------------------------------


def canonico(partes: Sequence[str]) -> bytes:
    """Serializa ``partes`` con prefijo de longitud uint32 big-endian.

    Equivale a ``canonical()`` de ``src/canonical.js``. Los ``None`` se
    convierten en cadena vacía para que el llamador no tenga que decidirlo,
    pero el contrato exige que cada posición opcional se traduzca a ``""``
    explícitamente; aquí solo se aceptan cadenas.
    """
    salida = bytearray()
    for parte in partes:
        if not isinstance(parte, str):
            raise ContratoError("Los campos canónicos deben ser texto.")
        datos = parte.encode("utf-8")
        salida += struct.pack(">I", len(datos))
        salida += datos
    return bytes(salida)


def texto(datos: bytes) -> str:
    """Decodifica un transcript canónico en su representación de depuración."""
    posicion = 0
    trozos: list[str] = []
    while posicion < len(datos):
        if posicion + 4 > len(datos):
            raise ContratoError("Transcript truncado.")
        (longitud,) = struct.unpack(">I", datos[posicion : posicion + 4])
        posicion += 4
        if posicion + longitud > len(datos):
            raise ContratoError("Transcript truncado.")
        trozos.append(datos[posicion : posicion + longitud].decode("utf-8"))
        posicion += longitud
    return "\x00".join(trozos)


def base64_estandar(datos: bytes) -> str:
    return base64.b64encode(datos).decode("ascii")


def base64url_sin_relleno(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).decode("ascii").rstrip("=")


def decodificar_base64(valor: Any, *, nombre: str, maximo: int | None = None) -> bytes:
    """Decodifica Base64 *estándar* de forma estricta (sin urlsafe)."""
    if not isinstance(valor, str) or not valor:
        raise ContratoError(f"{nombre} debe ser texto Base64 no vacío.")
    try:
        datos = base64.b64decode(valor, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ContratoError(f"{nombre} no es Base64 válido.") from exc
    if maximo is not None and len(datos) > maximo:
        raise ContratoError(f"{nombre} supera el tamaño máximo.")
    return datos


# --- Código de canje ------------------------------------------------------


def normalizar_codigo(entrada: Any) -> str:
    """Normaliza como el servicio: sin espacios ni guiones, en mayúsculas.

    No se habilita nada por la forma del código: solo se acepta el alfabeto de
    34 símbolos y 16 caracteres. La validación de que exista la hace GL.
    """
    if not isinstance(entrada, str):
        raise ContratoError("El código debe ser texto.")
    if len(entrada) > MAXIMO_CODIGO_ENTRADA:
        raise ContratoError("El código es demasiado largo.")
    limpio = re.sub(r"[\s-]", "", entrada).upper()
    if not _CODIGO_RE.fullmatch(limpio):
        raise ContratoError("El código no tiene el formato esperado.")
    return limpio


def formatear_codigo(normalizado: str) -> str:
    """Devuelve ``XXXX-XXXX-XXXX-XXXX`` para mostrar; nunca para firmar."""
    if not _CODIGO_RE.fullmatch(normalizado):
        raise ContratoError("Código normalizado inválido.")
    return "-".join(normalizado[i : i + 4] for i in range(0, LONGITUD_CODIGO, 4))


# --- Fechas y huellas -----------------------------------------------------


def formatear_instante(momento: datetime) -> str:
    """Formato del contrato: ISO 8601 UTC con milisegundos y sufijo ``Z``."""
    if not isinstance(momento, datetime) or momento.tzinfo is None:
        raise ContratoError("Se esperaba una fecha con zona horaria.")
    return (
        momento.astimezone(timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def analizar_instante(valor: Any) -> datetime:
    """Acepta solo instantes ISO en UTC terminados en ``Z`` y parseables."""
    if not isinstance(valor, str) or not (20 <= len(valor) <= 35) or not valor.endswith("Z"):
        raise ContratoError("Instante ISO inválido.")
    try:
        momento = datetime.fromisoformat(valor[:-1] + "+00:00")
    except ValueError as exc:
        raise ContratoError("Instante ISO inválido.") from exc
    if momento.tzinfo is None:
        raise ContratoError("Instante sin zona horaria.")
    return momento.astimezone(timezone.utc)


def huella_spki(spki_der: bytes) -> str:
    """Huella de instalación: SHA-256 del SPKI en Base64url sin relleno."""
    if not isinstance(spki_der, (bytes, bytearray)) or not spki_der:
        raise ContratoError("SPKI vacío.")
    return base64url_sin_relleno(hashlib.sha256(bytes(spki_der)).digest())


def es_huella(valor: Any) -> bool:
    return isinstance(valor, str) and bool(_HUELLA_RE.fullmatch(valor))


# --- Validación de textos del contrato ------------------------------------


def _texto(valor: Any, nombre: str, maximo: int, *, vacio: bool = False) -> str:
    if not isinstance(valor, str):
        raise ContratoError(f"{nombre} debe ser texto.")
    if (not valor and not vacio) or len(valor) > maximo:
        raise ContratoError(f"{nombre} tiene una longitud inválida.")
    return valor


def validar_app_id(valor: Any) -> str:
    if not isinstance(valor, str) or not _APP_ID_RE.fullmatch(valor):
        raise ContratoError("appId inválido.")
    return valor


def validar_request_id(valor: Any) -> str:
    if not isinstance(valor, str) or not _REQUEST_ID_RE.fullmatch(valor):
        raise ContratoError("requestId inválido.")
    return valor


def validar_nonce(valor: Any) -> str:
    if not isinstance(valor, str) or not _NONCE_RE.fullmatch(valor):
        raise ContratoError("nonce inválido.")
    return valor


def validar_license_id(valor: Any) -> str:
    if not isinstance(valor, str) or not _LICENSE_ID_RE.fullmatch(valor):
        raise ContratoError("licenseId inválido.")
    return valor


def validar_redemption_id(valor: Any) -> str:
    if not isinstance(valor, str) or not _REDEMPTION_ID_RE.fullmatch(valor):
        raise ContratoError("redemptionId inválido.")
    return valor


def validar_clave_dispositivo(valor: Any) -> str:
    """``deviceKey`` es SPKI P-256 en Base64 estándar, no urlsafe."""
    texto_clave = _texto(valor, "deviceKey", MAXIMO_CLAVE_DISPOSITIVO)
    if not _SPKI_RE.fullmatch(texto_clave):
        raise ContratoError("deviceKey no es Base64 estándar.")
    datos = decodificar_base64(texto_clave, nombre="deviceKey", maximo=200)
    if not 60 <= len(datos) <= 200:
        raise ContratoError("deviceKey no tiene tamaño de SPKI P-256.")
    return texto_clave


# --- Transcripts ----------------------------------------------------------


def bytes_prueba_canje(solicitud: Mapping[str, Any]) -> bytes:
    """Transcript firmado por el dispositivo para ``POST /v1/redeem``."""
    return canonico(
        [
            DOMINIO_SOLICITUD_CANJE,
            str(solicitud["v"]),
            _texto(solicitud["appId"], "appId", 64),
            _texto(solicitud["code"], "code", 64),
            _texto(solicitud["deviceKey"], "deviceKey", MAXIMO_CLAVE_DISPOSITIVO),
            _texto(solicitud["requestId"], "requestId", 64),
            _texto(solicitud["requestedAt"], "requestedAt", 40),
            _texto(solicitud["nonce"], "nonce", 128),
        ]
    )


def bytes_prueba_estado(solicitud: Mapping[str, Any]) -> bytes:
    """Transcript firmado por el dispositivo para ``POST /v1/status``."""
    return canonico(
        [
            DOMINIO_SOLICITUD_ESTADO,
            str(solicitud["v"]),
            _texto(solicitud["appId"], "appId", 64),
            _texto(solicitud["licenseId"], "licenseId", 64),
            _texto(solicitud["deviceKey"], "deviceKey", MAXIMO_CLAVE_DISPOSITIVO),
            _texto(solicitud["requestId"], "requestId", 64),
            _texto(solicitud["requestedAt"], "requestedAt", 40),
            _texto(solicitud["nonce"], "nonce", 128),
        ]
    )


def bytes_firma_licencia(payload: Mapping[str, Any]) -> bytes:
    """Bytes que firma el servicio GL sobre el contenido de la licencia.

    Orden exacto de ``licenseSigningBytes``. ``expiresAt`` y
    ``transferredFrom`` nulos se firman como cadena vacía; ``secundarias`` se
    firma en decimal; ``devicePublicKey`` ausente se firma como cadena vacía.
    """
    return canonico(
        [
            DOMINIO_LICENCIA,
            str(payload["v"]),
            _texto(payload["licenseId"], "licenseId", 64),
            _texto(payload["appId"], "appId", 64),
            _texto(payload["type"], "type", 16),
            _texto(payload["status"], "status", 16),
            _texto(payload["issuedAt"], "issuedAt", 40),
            "" if payload.get("expiresAt") is None else _texto(payload["expiresAt"], "expiresAt", 40),
            _texto(payload["deviceThumbprint"], "deviceThumbprint", 43),
            _texto(payload["redemptionId"], "redemptionId", 64),
            ""
            if payload.get("transferredFrom") is None
            else _texto(payload["transferredFrom"], "transferredFrom", 64),
            str(payload["secundarias"]),
            "" if payload.get("devicePublicKey") is None else str(payload["devicePublicKey"]),
        ]
    )


def bytes_firma_estado(documento: Mapping[str, Any]) -> bytes:
    """Bytes que firma el servicio GL sobre un documento de estado."""
    return canonico(
        [
            DOMINIO_ESTADO,
            str(documento["v"]),
            _texto(documento["licenseId"], "licenseId", 64),
            _texto(documento["appId"], "appId", 64),
            _texto(documento["type"], "type", 16),
            _texto(documento["status"], "status", 16),
            _texto(documento["issuedAt"], "issuedAt", 40),
            ""
            if documento.get("expiresAt") is None
            else _texto(documento["expiresAt"], "expiresAt", 40),
            _texto(documento["deviceThumbprint"], "deviceThumbprint", 43),
            _texto(documento["redemptionId"], "redemptionId", 64),
            ""
            if documento.get("transferredFrom") is None
            else _texto(documento["transferredFrom"], "transferredFrom", 64),
            str(documento["secundarias"]),
            "" if documento.get("devicePublicKey") is None else str(documento["devicePublicKey"]),
            _texto(documento["checkedAt"], "checkedAt", 40),
        ]
    )


# --- Sub-token de secundarias ---------------------------------------------


_ISO_ESTRICTO_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$")


@dataclass(frozen=True)
class SubToken:
    """Sub-token firmado por la instalación primaria.

    Se implementa y se prueba contra el vector, pero **no se activa
    comercialmente**: ``MAX_SECUNDARIAS`` sigue en 0 en el servicio.
    """

    v: int
    alg: str
    license_id: str
    device_thumbprint: str
    slot: int
    allowed: int
    expires_at: str
    sig: str

    def texto_canonico(self) -> str:
        return "|".join(
            [
                DOMINIO_SUBTOKEN,
                self.license_id,
                self.device_thumbprint,
                str(self.slot),
                str(self.allowed),
                self.expires_at,
            ]
        )

    def bytes_firma(self) -> bytes:
        return self.texto_canonico().encode("utf-8")

    def a_dict(self) -> dict[str, Any]:
        return {
            "v": self.v,
            "alg": self.alg,
            "licenseId": self.license_id,
            "deviceThumbprint": self.device_thumbprint,
            "slot": self.slot,
            "allowed": self.allowed,
            "expiresAt": self.expires_at,
            "sig": self.sig,
        }


def analizar_subtoken(datos: Any) -> SubToken:
    """Valida estructura, tipos y límites de un sub-token ``GL-REDEEM-SUB1``."""
    if not isinstance(datos, Mapping):
        raise ContratoError("El sub-token no es un objeto.")
    campos = {"v", "alg", "licenseId", "deviceThumbprint", "slot", "allowed", "expiresAt", "sig"}
    if set(datos) != campos:
        raise ContratoError("Campos de sub-token desconocidos o ausentes.")
    if datos["v"] != VERSION_SUBTOKEN or isinstance(datos["v"], bool):
        raise ContratoError("Versión de sub-token no soportada.")
    if datos["alg"] != ALG_SUBTOKEN:
        raise ContratoError("Algoritmo de sub-token desconocido.")
    license_id = validar_license_id(datos["licenseId"])
    huella = datos["deviceThumbprint"]
    if not es_huella(huella):
        raise ContratoError("Huella secundaria inválida.")
    slot = datos["slot"]
    allowed = datos["allowed"]
    for nombre, valor in (("slot", slot), ("allowed", allowed)):
        if isinstance(valor, bool) or not isinstance(valor, int):
            raise ContratoError(f"{nombre} debe ser entero.")
        if not 1 <= valor <= MAXIMO_SECUNDARIAS:
            raise ContratoError(f"{nombre} fuera de rango.")
    if slot > allowed:
        raise ContratoError("slot fuera del cupo permitido.")
    expires = datos["expiresAt"]
    if not isinstance(expires, str):
        raise ContratoError("expiresAt inválido.")
    if expires != "PERPETUA":
        if not _ISO_ESTRICTO_RE.fullmatch(expires):
            raise ContratoError("expiresAt inválido.")
        analizar_instante(expires)
    firma = datos["sig"]
    if not isinstance(firma, str) or not firma:
        raise ContratoError("Falta la firma del sub-token.")
    return SubToken(
        v=VERSION_SUBTOKEN,
        alg=ALG_SUBTOKEN,
        license_id=license_id,
        device_thumbprint=huella,
        slot=slot,
        allowed=allowed,
        expires_at=expires,
        sig=firma,
    )


def bytes_firma_subtoken(token: SubToken) -> bytes:
    return token.bytes_firma()


# --- Contenido de la licencia y del estado --------------------------------


def validar_contenido_licencia(payload: Any) -> dict[str, Any]:
    """Valida estructura, tipos y límites del objeto ``license``.

    Devuelve una copia normalizada; no verifica firmas ni decide derechos.
    """
    if not isinstance(payload, Mapping):
        raise ContratoError("La licencia no es un objeto.")
    campos = set(payload)
    obligatorios = {
        "v",
        "licenseId",
        "appId",
        "type",
        "status",
        "issuedAt",
        "deviceThumbprint",
        "redemptionId",
        "secundarias",
    }
    permitidos = obligatorios | {"expiresAt", "transferredFrom", "devicePublicKey"}
    if not obligatorios <= campos or not campos <= permitidos:
        raise ContratoError("Campos de licencia desconocidos o ausentes.")

    if payload["v"] != VERSION_LICENCIA or isinstance(payload["v"], bool):
        raise ContratoError("Versión de licencia no soportada.")
    validar_license_id(payload["licenseId"])
    validar_app_id(payload["appId"])
    tipo = payload["type"]
    if tipo not in TIPOS_DIAS:
        raise ContratoError("Tipo de licencia desconocido.")
    estado = payload["status"]
    if estado not in ESTADOS_LICENCIA:
        raise ContratoError("Estado de licencia desconocido.")

    issued = analizar_instante(payload["issuedAt"])
    expires_raw = payload.get("expiresAt")
    expires = None if expires_raw is None else analizar_instante(expires_raw)
    if tipo == "PERPETUA":
        if expires is not None:
            raise ContratoError("Una licencia perpetua no puede tener vencimiento.")
        if estado not in ("PERPETUA", "REVOCADA"):
            raise ContratoError("Estado incoherente para una licencia perpetua.")
    else:
        if expires is None:
            raise ContratoError("Falta el vencimiento de una licencia temporal.")
        if expires <= issued:
            raise ContratoError("El vencimiento no es posterior a la emisión.")
        if estado == "PERPETUA":
            raise ContratoError("Estado perpetuo incoherente con el tipo.")

    huella = payload["deviceThumbprint"]
    if not es_huella(huella):
        raise ContratoError("deviceThumbprint inválido.")
    validar_redemption_id(payload["redemptionId"])
    transferida = payload.get("transferredFrom")
    if transferida is not None:
        validar_redemption_id(transferida)
    secundarias = payload["secundarias"]
    if isinstance(secundarias, bool) or not isinstance(secundarias, int):
        raise ContratoError("secundarias debe ser un entero.")
    if not 0 <= secundarias <= MAXIMO_SECUNDARIAS:
        raise ContratoError("secundarias fuera de rango.")
    clave = payload.get("devicePublicKey")
    if clave is not None:
        validar_clave_dispositivo(clave)
        if huella_spki(decodificar_base64(clave, nombre="devicePublicKey")) != huella:
            raise ContratoError("La huella no corresponde a devicePublicKey.")
    return dict(payload)


def validar_documento_estado(documento: Any) -> dict[str, Any]:
    """Valida estructura de un documento de estado (incluye ``checkedAt``)."""
    if not isinstance(documento, Mapping):
        raise ContratoError("El estado no es un objeto.")
    if "checkedAt" not in documento:
        raise ContratoError("Falta checkedAt.")
    checked = documento["checkedAt"]
    analizar_instante(checked)  # valida sin alterar el texto firmado
    contenido = {clave: valor for clave, valor in documento.items() if clave != "checkedAt"}
    validado = validar_contenido_licencia(contenido)
    validado["checkedAt"] = checked
    return validado


def estado_efectivo(
    payload: Mapping[str, Any],
    *,
    ahora: datetime,
    referencia: datetime | None = None,
) -> str:
    """Calcula el estado utilizable de una licencia ya verificada.

    ``referencia`` permite aplicar la marca de agua del almacén: el reloj que
    se usa nunca es anterior al último observado. No sustituye a un reloj
    confiable antimanipulación.
    """
    estado = payload["status"]
    if estado == "REVOCADA":
        return "REVOCADA"
    instante = referencia if referencia is not None and referencia > ahora else ahora
    if payload["type"] == "PERPETUA":
        return "PERPETUA"
    vence = analizar_instante(payload["expiresAt"])
    return "VENCIDA" if instante >= vence else "ACTIVA"
