"""Configuración de licencias fijada en la compilación (no editable por el usuario).

El endpoint HTTPS, el ``appId`` y el mapa ``kid`` → clave pública SPKI se fijan
en este módulo y viajan dentro del ejecutable. No se leen de formularios, de
``portable-settings.json`` ni de un respaldo restaurado: un respaldo de otro
equipo no puede redirigir el canje ni sustituir una clave de confianza.

Mientras no existan un endpoint y unas claves públicas de **producción**
aprobados, ``comercial_habilitada()`` devuelve ``False`` y la interfaz debe
mostrar el motivo. Los vectores y claves de prueba del contrato GL no se usan
aquí nunca.
"""

from __future__ import annotations

import base64
import re
from dataclasses import dataclass
from datetime import timedelta
from types import MappingProxyType
from typing import Any, Mapping
from urllib.parse import urlsplit

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

# ── Valores de compilación ─────────────────────────────────────────────────────
# Build aprobado con endpoint y claves de producción GL v1.
# Cambiar cualquiera de estos valores exige un build nuevo,
# revisión y pruebas; nunca se reciben del usuario.
APP_ID = "DSHALEV"
ENDPOINT_REDEEM = "https://gl-redeem.rmdvcd.workers.dev/v1/redeem"
ENDPOINT_ESTADO = "https://gl-redeem.rmdvcd.workers.dev/v1/status"
KIDS_FIRMA: Mapping[str, str] = MappingProxyType({"gl-redeem-v1": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE553eHDlFY2mooBnPsNoaRe9gUd4iPEST98yH8OKQOCWCqphj1AwSAIMsfpTKr85QgyweZqMlH8aG248aJnHZpg=="})
BASE_PUBLICA_AUTORIZADA = "https://gl-redeem.rmdvcd.workers.dev"

# ── Política de instalación ────────────────────────────────────────────────────
# Una identidad P-256 y una licencia por instalación; los locales de `--local`
# comparten identidad y licencia y no se licencian por separado. Cambiar esta
# política exige decisión del dueño y soporte del contrato GL (appId/vínculo de local).
POLITICA_IDENTIDAD = "instalacion"
TOLERANCIA_RELOJ = timedelta(hours=2)
MAXIMO_DOCUMENTO_LICENCIA = 64 * 1024

MOTIVO_SIN_CONFIGURACION = (
    "Este build no tiene un endpoint HTTPS ni claves públicas de producción aprobadas para la "
    "licencia de canje GL. La activación comercial está deshabilitada: el panel /licencia no "
    "activa ni valida licencias y la prueba local de siete días de las secundarias no es una "
    "licencia comercial."
)

_KID_RE = re.compile(r"^[A-Za-z0-9._:-]{1,64}$")
_APP_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$")


class ConfiguracionLicenciaError(ValueError):
    """La configuración de licencias del build no es segura o es incoherente."""


@dataclass(frozen=True)
class ConfiguracionLicencia:
    app_id: str
    endpoint_redeem: str
    endpoint_estado: str
    kids_spki: Mapping[str, bytes]

    @property
    def comercial_habilitada(self) -> bool:
        return bool(self.app_id and self.endpoint_redeem and self.endpoint_estado and self.kids_spki)

    def kid_autorizado(self, kid: str) -> bytes | None:
        """SPKI DER fijado para ``kid``, o ``None`` si no está autorizado."""
        return self.kids_spki.get(kid)

    def motivo_deshabilitada(self) -> str:
        if self.comercial_habilitada:
            return ""
        faltan = []
        if not self.app_id:
            faltan.append("appId")
        if not self.endpoint_redeem:
            faltan.append("endpoint de canje")
        if not self.endpoint_estado:
            faltan.append("endpoint de estado")
        if not self.kids_spki:
            faltan.append("claves públicas (kid)")
        detalle = ", ".join(faltan) if faltan else "configuración incompleta"
        return f"{MOTIVO_SIN_CONFIGURACION} Falta: {detalle}."


def _validar_endpoint(valor: str, nombre: str) -> str:
    if not isinstance(valor, str) or not valor:
        return ""
    if not valor.isascii() or valor != valor.strip() or any(not 0x21 <= ord(caracter) <= 0x7E for caracter in valor):
        raise ConfiguracionLicenciaError(f"El {nombre} contiene caracteres no permitidos.")
    try:
        partes = urlsplit(valor)
    except ValueError:
        raise ConfiguracionLicenciaError(f"El {nombre} no es una URL válida.") from None
    if partes.scheme != "https":
        raise ConfiguracionLicenciaError(f"El {nombre} debe usar HTTPS.")
    if not partes.hostname:
        raise ConfiguracionLicenciaError(f"El {nombre} no tiene nombre de servidor.")
    if partes.username or partes.password or "@" in partes.netloc:
        raise ConfiguracionLicenciaError(f"El {nombre} no puede llevar credenciales en la URL.")
    if partes.query or partes.fragment:
        raise ConfiguracionLicenciaError(f"El {nombre} no admite parámetros ni fragmentos.")
    try:
        puerto = partes.port
    except ValueError:
        raise ConfiguracionLicenciaError(f"El {nombre} tiene un puerto inválido.") from None
    if puerto is not None and not 1 <= puerto <= 65535:
        raise ConfiguracionLicenciaError(f"El {nombre} tiene un puerto inválido.")
    return valor


def _validar_kids(kids: Mapping[str, str]) -> Mapping[str, bytes]:
    resultado: dict[str, bytes] = {}
    for kid, spki_b64 in dict(kids).items():
        if not isinstance(kid, str) or not _KID_RE.fullmatch(kid):
            raise ConfiguracionLicenciaError(f"Identificador de clave (kid) inválido: {kid!r}.")
        try:
            spki = base64.b64decode(str(spki_b64), validate=True)
        except (ValueError, TypeError):
            raise ConfiguracionLicenciaError(f"La clave pública de {kid} no es Base64 válido.") from None
        try:
            publica = serialization.load_der_public_key(spki)
        except ValueError:
            raise ConfiguracionLicenciaError(f"La clave pública de {kid} no es una SPKI DER válida.") from None
        if not isinstance(publica, ec.EllipticCurvePublicKey) or publica.curve.name != "secp256r1":
            raise ConfiguracionLicenciaError(f"La clave pública de {kid} no es P-256.")
        resultado[kid] = spki
    return MappingProxyType(resultado)


def definir(
    *,
    app_id: str | None = None,
    endpoint_redeem: str | None = None,
    endpoint_estado: str | None = None,
    kids_firma: Mapping[str, str] | None = None,
    directorio: Any | None = None,
) -> ConfiguracionLicencia:
    """Construye y valida la configuración.

    Si no se proporcionan valores explícitos ni de compilación, intenta resolver
    las claves y endpoints del directorio de confianza efectivo (Fase 5 / S6).
    """
    if app_id is not None:
        app = app_id
    elif directorio is not None:
        app = (
            getattr(directorio, "app_ids", {}).get("DSHALEV")
            or getattr(directorio, "app_ids", {}).get("MIPVENTAX-DESKTOP")
            or getattr(directorio, "app_id", None)
            or APP_ID
        )
    else:
        app = APP_ID

    if app and not _APP_ID_RE.fullmatch(app):
        raise ConfiguracionLicenciaError("El appId debe ser ASCII alfanumérico, punto, guion o guion bajo.")

    if endpoint_redeem is not None:
        ep_redeem = endpoint_redeem
    elif directorio is not None:
        if getattr(directorio, "endpoint_base", None):
            base = getattr(directorio, "endpoint_base").rstrip("/")
            ep_redeem = f"{base}/v1/redeem"
        elif BASE_PUBLICA_AUTORIZADA and getattr(directorio, "claves", None):
            base = BASE_PUBLICA_AUTORIZADA.rstrip("/")
            ep_redeem = f"{base}/v1/redeem"
        else:
            ep_redeem = ""
    else:
        ep_redeem = ENDPOINT_REDEEM

    if endpoint_estado is not None:
        ep_estado = endpoint_estado
    elif directorio is not None:
        if getattr(directorio, "endpoint_base", None):
            base = getattr(directorio, "endpoint_base").rstrip("/")
            ep_estado = f"{base}/v1/status"
        elif BASE_PUBLICA_AUTORIZADA and getattr(directorio, "claves", None):
            base = BASE_PUBLICA_AUTORIZADA.rstrip("/")
            ep_estado = f"{base}/v1/status"
        else:
            ep_estado = ""
    else:
        ep_estado = ENDPOINT_ESTADO

    mapa_kids: dict[str, bytes] = {}
    if kids_firma is not None:
        mapa_kids = dict(_validar_kids(kids_firma))
    elif directorio is not None:
        claves_canje = getattr(directorio, "claves_canje", None)
        if callable(claves_canje) and claves_canje():
            mapa_kids = dict(claves_canje())
        else:
            claves_dir = getattr(directorio, "claves", [])
            canje = {
                getattr(c, "kid", ""): getattr(c, "spki_der", None)
                for c in claves_dir
                if getattr(c, "uso", "") == "canje"
                and getattr(c, "activa", True)
                and getattr(c, "kid", "")
                and getattr(c, "spki_der", None)
                and _KID_RE.fullmatch(getattr(c, "kid", ""))
            }
            if canje:
                mapa_kids = canje
            else:
                for c in claves_dir:
                    if getattr(c, "activa", True):
                        kid = getattr(c, "kid", "")
                        spki = getattr(c, "spki_der", None)
                        if kid and spki and _KID_RE.fullmatch(kid):
                            mapa_kids[kid] = spki
    elif endpoint_redeem is None and endpoint_estado is None and app_id is None and KIDS_FIRMA:
        mapa_kids = dict(_validar_kids(KIDS_FIRMA))

    return ConfiguracionLicencia(
        app_id=app,
        endpoint_redeem=_validar_endpoint(ep_redeem, "endpoint de canje"),
        endpoint_estado=_validar_endpoint(ep_estado, "endpoint de estado"),
        kids_spki=MappingProxyType(mapa_kids),
    )


def configuracion_desde_directorio(directorio: Any) -> ConfiguracionLicencia:
    """Construye una configuración dinámica anclada a las claves y endpoints del directorio (S6)."""
    return definir(directorio=directorio)


_CONFIGURACION: ConfiguracionLicencia | None = None
_DIRECTORIO_EFECTIVO: Any | None = None


def registrar_directorio_efectivo(directorio: Any | None) -> ConfiguracionLicencia:
    """Fija el directorio de confianza cargado en el arranque y deriva de él la configuración (S6).

    Es la contraparte de `TrustDirectoryManager.loadEffective()` en Android y iOS: sin este paso,
    `configuracion()` nunca vería las claves de `uso == "canje"` ni la base autorizada, y el panel
    /licencia quedaría cerrado aunque el directorio fuese válido.
    """
    global _CONFIGURACION, _DIRECTORIO_EFECTIVO
    _DIRECTORIO_EFECTIVO = directorio
    _CONFIGURACION = definir(directorio=directorio) if directorio is not None else definir()
    return _CONFIGURACION


def directorio_efectivo() -> Any | None:
    """Directorio de confianza cargado en el arranque (None = no hay ninguno verificable)."""
    return _DIRECTORIO_EFECTIVO


def configuracion() -> ConfiguracionLicencia:
    """Configuración del build, validada una sola vez."""
    global _CONFIGURACION
    if _CONFIGURACION is None:
        _CONFIGURACION = definir()
    return _CONFIGURACION


def motivo_activacion_deshabilitada() -> str:
    return configuracion().motivo_deshabilitada()
