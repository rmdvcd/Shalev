"""Cliente del servicio GL de canje y estado (``POST /v1/redeem`` y ``/v1/status``).

Reglas que este módulo hace cumplir:

* el endpoint HTTPS y las claves ``kid`` vienen de la configuración del build,
  nunca de un formulario ni de un respaldo;
* se verifica el certificado y el nombre del servidor, no se desactiva TLS y no
  se siguen redirecciones;
* se firma el transcript canónico con la identidad persistente del equipo;
* la respuesta se verifica completa antes de guardar nada;
* un fallo de red no borra ni habilita la licencia anterior;
* el código en claro no se guarda ni se registra. Para reintentar tras perder
  la respuesta se conserva en memoria el ``requestId`` y una huella del código
  normalizado; si el proceso termina, el usuario debe volver a escribirlo.
"""

from __future__ import annotations

import base64
import hashlib
import json
import secrets
import socket
import ssl
import threading
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Callable, Mapping, Protocol

from . import contrato
from .almacen import AlmacenError, AlmacenLicencia, LicenciaLocal
from .config_licencia import ConfiguracionLicencia
from .contrato import ContratoError
from .identidad import Identidad
from .verificador import (
    EstadoVerificado,
    LicenciaInvalida,
    LicenciaVerificada,
    verificar_documento_guardado,
    verificar_estado,
    verificar_licencia,
)

TIEMPO_ESPERA = 15.0

MENSAJES = {
    "ACTIVACION_DESHABILITADA": "La activación comercial está deshabilitada en este build.",
    "INVALID_CODE": "El código no tiene el formato esperado. Revísalo y vuelve a escribirlo.",
    "NETWORK": "No se pudo contactar con el servicio de licencias. Se conserva la licencia anterior.",
    "INVALID_RESPONSE": "El servicio respondió algo que no cumple el contrato. No se guardó nada.",
    "INVALID_PROOF": "El servicio rechazó la prueba de este equipo. No se guardó nada.",
    "CODE_UNAVAILABLE": "El código no existe, ya no está disponible o no es para esta aplicación.",
    "CODE_ALREADY_REDEEMED": "El código ya se canjeó en otro equipo. No se guardó nada.",
    "CODE_EXPIRED": "El código caducó antes de canjearse.",
    "IDEMPOTENCY_CONFLICT": "El reintento no coincide con la solicitud original. No se guardó nada.",
    "LICENSE_NOT_FOUND": "El servicio no reconoce esta licencia para este equipo.",
    "RATE_LIMITED": "Demasiados intentos seguidos. Espera unos minutos.",
    "SERVER_ERROR": "El servicio de licencias falló. Inténtalo más tarde; la licencia anterior sigue intacta.",
    "INVALID_REQUEST": "El servicio rechazó la solicitud. Revisa la hora del equipo: debe estar a menos de cinco minutos de la del servicio.",
    "PAYLOAD_TOO_LARGE": "La solicitud o la respuesta superan el tamaño admitido.",
    "INVALID_LICENSE_SIGNATURE": "La firma de la licencia no es válida. No se guardó nada.",
    "LICENSE_WRONG_APP": "La licencia es para otra aplicación. No se guardó nada.",
    "LICENSE_WRONG_DEVICE": "La licencia es para otro equipo. Usa la transferencia manual.",
    "UNKNOWN_KID": "La licencia usa una clave que este build no autoriza. Actualiza DShalev.",
    "CLOCK_ROLLBACK": "El reloj del equipo retrocedió más de lo permitido. Corrige la hora antes de continuar.",
    "LICENSE_EXPIRED": "La licencia está vencida.",
    "LICENSE_REVOKED": "La licencia fue revocada por el servicio.",
    "SIN_LICENCIA": "No hay una licencia guardada que consultar.",
    "LICENCIA_LOCAL_INVALIDA": "La licencia guardada no supera la verificación. Vuelve a activar o recupera el respaldo.",
    "ALMACEN": "No se pudo guardar la licencia en este equipo. No se activó nada; revisa los permisos de la carpeta de datos.",
}


class ErrorGL(RuntimeError):
    """Error del canje o de la consulta de estado, con código estable."""

    def __init__(
        self,
        codigo: str,
        *,
        estado_http: int | None = None,
        detalle: str = "",
        request_id: str | None = None,
    ) -> None:
        self.codigo = codigo
        self.estado_http = estado_http
        self.detalle = detalle
        self.request_id = request_id
        super().__init__(self.mensaje)

    @property
    def mensaje(self) -> str:
        base = MENSAJES.get(self.codigo, "No se pudo completar la operación de licencia.")
        if self.detalle:
            return f"{base} ({self.detalle})"
        return base


@dataclass(frozen=True)
class RespuestaHTTP:
    estado: int
    cuerpo: bytes


class Transporte(Protocol):
    def enviar(
        self,
        url: str,
        cuerpo: bytes,
        *,
        tiempo_espera: float,
        contexto_ssl: ssl.SSLContext,
    ) -> RespuestaHTTP:  # pragma: no cover - protocolo
        ...


class _SinRedirecciones(urllib.request.HTTPRedirectHandler):
    """Cualquier 3xx se convierte en error: no se siguen redirecciones."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D401, ANN001
        return None


class TransporteUrllib:
    """Transporte por defecto: HTTPS con verificación de certificado y host."""

    def enviar(
        self,
        url: str,
        cuerpo: bytes,
        *,
        tiempo_espera: float,
        contexto_ssl: ssl.SSLContext,
    ) -> RespuestaHTTP:
        peticion = urllib.request.Request(
            url,
            data=cuerpo,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json",
                "User-Agent": "DShalev-licencia/1",
                "Cache-Control": "no-store",
            },
        )
        abridor = urllib.request.build_opener(
            _SinRedirecciones(), urllib.request.HTTPSHandler(context=contexto_ssl)
        )
        try:
            with abridor.open(peticion, timeout=tiempo_espera) as respuesta:
                return RespuestaHTTP(respuesta.status, respuesta.read(contrato.MAXIMO_CUERPO_RESPUESTA + 1))
        except urllib.error.HTTPError as exc:
            try:
                return RespuestaHTTP(exc.code, exc.read(contrato.MAXIMO_CUERPO_RESPUESTA + 1))
            finally:
                exc.close()
        except (urllib.error.URLError, socket.timeout, TimeoutError, ssl.SSLError, OSError) as exc:
            raise ErrorGL("NETWORK", detalle=type(exc).__name__) from None


@dataclass(frozen=True)
class ResultadoCanje:
    licencia: LicenciaVerificada
    request_id: str
    restaurada: bool
    guardada_en: datetime


@dataclass(frozen=True)
class ResultadoEstado:
    estado: str
    comprobado_en: datetime | None
    actualizado: bool
    request_id: str
    licencia: LicenciaLocal


def _ahora_utc() -> datetime:
    return datetime.now(timezone.utc)


def _contexto_ssl_verificado() -> ssl.SSLContext:
    contexto = ssl.create_default_context()
    contexto.check_hostname = True
    contexto.verify_mode = ssl.CERT_REQUIRED
    contexto.minimum_version = ssl.TLSVersion.TLSv1_2
    return contexto


def _hash_codigo(normalizado: str) -> str:
    """Huella en memoria del código; el código en claro no se retiene."""
    return hashlib.sha256(normalizado.encode("utf-8")).hexdigest()


class ClienteGL:
    """Canje y consulta de estado contra el servicio GL configurado."""

    def __init__(
        self,
        *,
        configuracion: ConfiguracionLicencia,
        identidad: Identidad,
        almacen: AlmacenLicencia,
        ahora: Callable[[], datetime] = _ahora_utc,
        tiempo_espera: float = TIEMPO_ESPERA,
        contexto_ssl: ssl.SSLContext | None = None,
        transporte: Transporte | None = None,
    ) -> None:
        if identidad.huella != almacen.huella:
            raise ValueError("La identidad y el almacén pertenecen a huellas distintas.")
        self.configuracion = configuracion
        self.identidad = identidad
        self.almacen = almacen
        self._ahora = ahora
        self.tiempo_espera = float(tiempo_espera)
        self.contexto_ssl = contexto_ssl or _contexto_ssl_verificado()
        self.transporte: Transporte = transporte or TransporteUrllib()
        self._cerrojo = threading.RLock()
        # requestId y nonce del último intento que pudo llegar al servicio.
        # Nunca se guarda el código en claro ni se escribe esto en disco.
        self._pendiente: tuple[str, str, str, str] | None = None

    # ── utilidades ───────────────────────────────────────────────────────────
    def _exigir_habilitada(self) -> None:
        if not self.configuracion.comercial_habilitada:
            raise ErrorGL(
                "ACTIVACION_DESHABILITADA",
                detalle=self.configuracion.motivo_deshabilitada(),
            )

    def _comprobar_reloj(self) -> None:
        resultado = self.almacen.observar(self._ahora())
        if resultado.reloj_retrocedido:
            raise ErrorGL("CLOCK_ROLLBACK")

    def _identificadores(self) -> tuple[str, str, str]:
        """Devuelve (requestId, requestedAt, nonce) del intento actual."""
        request_id = str(uuid.uuid4())
        solicitado = contrato.formatear_instante(self._ahora())
        nonce = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
        return request_id, solicitado, nonce

    def _cuerpo(self, campos: dict[str, Any]) -> bytes:
        try:
            cuerpo = json.dumps(campos, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        except (TypeError, ValueError):
            raise ErrorGL("INVALID_RESPONSE", detalle="serialización") from None
        if len(cuerpo) > contrato.MAXIMO_CUERPO_PETICION:
            raise ErrorGL("PAYLOAD_TOO_LARGE")
        return cuerpo

    def _enviar(self, url: str, cuerpo: bytes, request_id: str) -> dict[str, Any]:
        try:
            respuesta = self.transporte.enviar(
                url,
                cuerpo,
                tiempo_espera=self.tiempo_espera,
                contexto_ssl=self.contexto_ssl,
            )
        except ErrorGL:
            raise
        if len(respuesta.cuerpo) > contrato.MAXIMO_CUERPO_RESPUESTA:
            raise ErrorGL("INVALID_RESPONSE", detalle="respuesta demasiado grande", request_id=request_id)
        if 300 <= respuesta.estado < 400:
            raise ErrorGL("INVALID_RESPONSE", detalle="redirección rechazada", request_id=request_id)
        if respuesta.estado >= 400:
            raise self._error_servicio(respuesta, request_id)
        try:
            datos = json.loads(respuesta.cuerpo.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            raise ErrorGL("INVALID_RESPONSE", detalle="JSON ilegible", request_id=request_id) from None
        if not isinstance(datos, dict) or datos.get("v") != contrato.VERSION_SERVICIO:
            raise ErrorGL("INVALID_RESPONSE", detalle="sobre inesperado", request_id=request_id)
        return datos

    def _error_servicio(self, respuesta: RespuestaHTTP, request_id: str) -> ErrorGL:
        codigo = ""
        detalle = ""
        try:
            datos = json.loads(respuesta.cuerpo.decode("utf-8"))
            error = datos.get("error") if isinstance(datos, dict) else None
            if isinstance(error, dict):
                codigo = str(error.get("code") or "")
                detalle = str(error.get("message") or "")[:200]
        except (ValueError, UnicodeDecodeError):
            pass
        conocidos = {
            "INVALID_REQUEST",
            "PAYLOAD_TOO_LARGE",
            "INVALID_PROOF",
            "CODE_UNAVAILABLE",
            "CODE_ALREADY_REDEEMED",
            "CODE_EXPIRED",
            "LICENSE_NOT_FOUND",
            "RATE_LIMITED",
            "IDEMPOTENCY_CONFLICT",
            "SERVER_ERROR",
        }
        if codigo not in conocidos:
            if respuesta.estado == 413:
                codigo = "PAYLOAD_TOO_LARGE"
            elif respuesta.estado == 429:
                codigo = "RATE_LIMITED"
            elif respuesta.estado >= 500:
                codigo = "SERVER_ERROR"
            else:
                codigo = "INVALID_RESPONSE"
        return ErrorGL(codigo, estado_http=respuesta.estado, detalle=detalle, request_id=request_id)

    @staticmethod
    def _traducir_verificacion(exc: LicenciaInvalida, *, request_id: str) -> ErrorGL:
        mapa = {
            "firma": "INVALID_LICENSE_SIGNATURE",
            "app": "LICENSE_WRONG_APP",
            "dispositivo": "LICENSE_WRONG_DEVICE",
            "kid": "UNKNOWN_KID",
        }
        return ErrorGL(mapa.get(exc.motivo, "INVALID_RESPONSE"), detalle=exc.detalle, request_id=request_id)

    def _sobre_a_texto(self, sobre: Mapping[str, Any]) -> str:
        return json.dumps(dict(sobre), ensure_ascii=False, separators=(",", ":"), sort_keys=True)

    # ── canje ────────────────────────────────────────────────────────────────
    def canjear(self, codigo: str, *, request_id: str | None = None) -> ResultadoCanje:
        """Canjea un código normalizándolo y guarda solo una licencia verificada.

        Se serializa por proceso: dos canjes simultáneos compartirían el
        ``requestId`` pendiente y el servicio GL no serializa renovaciones por
        instalación. Sigue siendo un riesgo si hay dos procesos abiertos.
        """
        with self._cerrojo:
            return self._canjear(codigo, request_id=request_id)

    def _canjear(self, codigo: str, *, request_id: str | None = None) -> ResultadoCanje:
        self._exigir_habilitada()
        self._comprobar_reloj()
        try:
            normalizado = contrato.normalizar_codigo(codigo)
        except ContratoError as exc:
            raise ErrorGL("INVALID_CODE", detalle=str(exc)) from None

        huella_codigo = _hash_codigo(normalizado)
        reutilizar = request_id
        if reutilizar is None and self._pendiente is not None and self._pendiente[0] == huella_codigo:
            # Mismo código tras perder la respuesta: misma solicitud, mismo
            # requestId, instante y nonce, para que el servicio sea idempotente.
            _, reutilizar, solicitado, nonce = self._pendiente
        else:
            reutilizar, solicitado, nonce = self._identificadores()
        try:
            request_id_actual = contrato.validar_request_id(reutilizar)
        except ContratoError as exc:
            raise ErrorGL("INVALID_RESPONSE", detalle=str(exc)) from None

        dispositivo = contrato.base64_estandar(self.identidad.spki_der)
        cuerpo = {
            "v": contrato.VERSION_SERVICIO,
            "appId": self.configuracion.app_id,
            "code": normalizado,
            "deviceKey": dispositivo,
            "requestId": request_id_actual,
            "requestedAt": solicitado,
            "nonce": nonce,
        }
        prueba = self.identidad.firmar(contrato.bytes_prueba_canje(cuerpo))
        cuerpo["proof"] = contrato.base64_estandar(prueba)
        # Se recuerda antes de enviar: si la respuesta se pierde, el reintento
        # reutiliza exactamente la misma solicitud. Solo vive en memoria.
        self._pendiente = (huella_codigo, request_id_actual, solicitado, nonce)
        try:
            datos = self._enviar(
                self.configuracion.endpoint_redeem, self._cuerpo(cuerpo), request_id_actual
            )
        except ErrorGL as exc:
            if exc.codigo not in ("NETWORK", "INVALID_RESPONSE", "SERVER_ERROR", "RATE_LIMITED"):
                self._pendiente = None
            raise

        if datos.get("ok") is not True or "license" not in datos:
            raise ErrorGL("INVALID_RESPONSE", detalle="respuesta sin licencia", request_id=request_id_actual)
        try:
            verificada = verificar_licencia(
                datos["license"],
                claves=self.configuracion.kids_spki,
                app_id=self.configuracion.app_id,
                huella_local=self.identidad.huella,
                ahora=self._ahora(),
            )
        except LicenciaInvalida as exc:
            raise self._traducir_verificacion(exc, request_id=request_id_actual) from None

        if verificada.estado == "REVOCADA":
            raise ErrorGL("LICENSE_REVOKED", request_id=request_id_actual)
        if not verificada.utilizable:
            raise ErrorGL("LICENSE_EXPIRED", request_id=request_id_actual)

        try:
            guardada = self.almacen.guardar_licencia(
                self._sobre_a_texto(verificada.sobre),
                ahora=self._ahora(),
                estado=verificada.estado,
            )
        except AlmacenError as exc:
            raise ErrorGL("ALMACEN", detalle=str(exc), request_id=request_id_actual) from None
        self._pendiente = None
        return ResultadoCanje(
            licencia=verificada,
            request_id=request_id_actual,
            restaurada=bool(datos.get("restored")),
            guardada_en=guardada.guardada_en,
        )

    # ── estado ───────────────────────────────────────────────────────────────
    def _licencia_guardada(self) -> tuple[LicenciaLocal, LicenciaVerificada]:
        resultado = self.almacen.observar(self._ahora())
        if resultado.reloj_retrocedido:
            raise ErrorGL("CLOCK_ROLLBACK")
        if resultado.licencia is None:
            raise ErrorGL("SIN_LICENCIA")
        try:
            verificada = verificar_documento_guardado(
                resultado.licencia.documento,
                claves=self.configuracion.kids_spki,
                app_id=self.configuracion.app_id,
                huella_local=self.identidad.huella,
                ahora=self._ahora(),
            )
        except LicenciaInvalida as exc:
            raise self._traducir_verificacion(exc, request_id="") from None
        return resultado.licencia, verificada

    def consultar_estado(self, *, request_id: str | None = None) -> ResultadoEstado:
        """Consulta autenticada de estado; persiste una revocación verificada."""
        with self._cerrojo:
            return self._consultar_estado(request_id=request_id)

    def _consultar_estado(self, *, request_id: str | None = None) -> ResultadoEstado:
        self._exigir_habilitada()
        guardada, verificada = self._licencia_guardada()
        solicitado = contrato.formatear_instante(self._ahora())
        request_id_actual = contrato.validar_request_id(request_id or str(uuid.uuid4()))
        nonce = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
        cuerpo = {
            "v": contrato.VERSION_SERVICIO,
            "appId": self.configuracion.app_id,
            "licenseId": verificada.contenido["licenseId"],
            "deviceKey": contrato.base64_estandar(self.identidad.spki_der),
            "requestId": request_id_actual,
            "requestedAt": solicitado,
            "nonce": nonce,
        }
        prueba = self.identidad.firmar(contrato.bytes_prueba_estado(cuerpo))
        cuerpo["proof"] = contrato.base64_estandar(prueba)
        datos = self._enviar(
            self.configuracion.endpoint_estado, self._cuerpo(cuerpo), request_id_actual
        )
        if datos.get("ok") is not True or "status" not in datos:
            raise ErrorGL("INVALID_RESPONSE", detalle="respuesta sin estado", request_id=request_id_actual)
        try:
            estado: EstadoVerificado = verificar_estado(
                datos["status"],
                claves=self.configuracion.kids_spki,
                app_id=self.configuracion.app_id,
                huella_local=self.identidad.huella,
                licencia_actual=verificada.contenido,
                ahora=self._ahora(),
            )
        except LicenciaInvalida as exc:
            raise self._traducir_verificacion(exc, request_id=request_id_actual) from None

        try:
            actualizado = self.almacen.registrar_estado(
                estado.estado, comprobado_en=estado.comprobado_en, ahora=self._ahora()
            )
        except AlmacenError as exc:
            raise ErrorGL("LICENCIA_LOCAL_INVALIDA", detalle=str(exc), request_id=request_id_actual) from None

        actual = self.almacen.observar(self._ahora()).licencia or guardada
        estado_final = "REVOCADA" if actual.revocada else estado.estado
        return ResultadoEstado(
            estado=estado_final,
            comprobado_en=actual.estado_comprobado_en,
            actualizado=actualizado,
            request_id=request_id_actual,
            licencia=actual,
        )
