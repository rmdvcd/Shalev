"""Directorio de confianza v1 (Anexo A) para MIPVentaX / DShalev.

Permite verificar y consultar claves públicas de firma, cifrado ECDH y canje
distribuidas de forma dinámica y autenticadas por claves raíz P-256 fijadas.
Sustituye el anclaje manual estático y evita tener que recompilar las apps
en cada rotación de claves de GL.
"""

from __future__ import annotations

import base64
import hashlib
import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import (
    decode_dss_signature,
    encode_dss_signature,
)

DOMINIO_DIRECTORIO = b"SPVI-DIR1|"
MAXIMO_DIRECTORIO_BYTES = 64 * 1024  # 64 KiB
VERSION_DIRECTORIO = 1

# Marcadores para las raíces en compilación (se configuran con las raíces reales de producción).
# Si permanecen como marcadores vacíos, las funciones de producción fallan cerrado.
RAIZ_A_SPKI = ""
RAIZ_B_SPKI = ""

USOS_VALIDOS = {"firma", "ecdh", "canje"}
ESTADOS_VALIDOS = {"vigente", "retirada", "comprometida"}


class DirectorioError(ValueError):
    """El directorio de confianza no es válido, está dañado o no supera la verificación."""


@dataclass(frozen=True)
class ClaveDirectorio:
    kid: str
    uso: str
    spki_b64: str
    spki_der: bytes
    huella: str
    desde: int
    hasta: int | None
    estado: str

    @property
    def activa(self) -> bool:
        return self.estado != "comprometida"

    def cubre_fecha(self, instante_segundos: int) -> bool:
        if self.estado == "comprometida":
            return False
        if instante_segundos < self.desde:
            return False
        if self.hasta is not None and instante_segundos > self.hasta:
            return False
        return True


@dataclass(frozen=True)
class DirectorioConfianza:
    v: int
    seq: int
    emitida_en: int
    claves: Sequence[ClaveDirectorio]

    def sign_keys_for(self, emitida_en: int | datetime) -> list[bytes]:
        """Devuelve las claves SPKI DER con uso 'firma' que cubren la fecha indicada y no están comprometidas."""
        segundos = int(emitida_en.timestamp()) if isinstance(emitida_en, datetime) else int(emitida_en)
        return [
            c.spki_der
            for c in self.claves
            if c.uso == "firma" and c.cubre_fecha(segundos)
        ]

    def ecdh_vigente(self) -> bytes | None:
        """Devuelve la clave SPKI DER vigente para cifrar solicitudes hacia GL."""
        for c in self.claves:
            if c.uso == "ecdh" and c.estado == "vigente":
                return c.spki_der
        return None

    def redeem_key(self, kid: str) -> bytes | None:
        """Devuelve la clave SPKI DER de canje para el kid dado si no está comprometida."""
        for c in self.claves:
            if c.uso == "canje" and c.kid == kid and c.activa:
                return c.spki_der
        return None

    def claves_canje(self) -> dict[str, bytes]:
        """Devuelve un mapa kid -> spki_der con todas las claves de canje no comprometidas."""
        return {
            c.kid: c.spki_der
            for c in self.claves
            if c.uso == "canje" and c.activa
        }

    def es_clave_comprometida(self, spki_der: bytes) -> bool:
        for c in self.claves:
            if c.spki_der == spki_der and c.estado == "comprometida":
                return True
        return False


def normalizar_huella(huella: str) -> str:
    """Normaliza 'sha256:0289:...' o hex plano a 64 hex minúsculas."""
    return huella.strip().lower().removePrefix("sha256:").replace(":", "")


def calcular_huella(spki_der: bytes) -> str:
    """Calcula la huella en formato agrupado sha256:xxxx:..."""
    h = hashlib.sha256(spki_der).hexdigest()
    return "sha256:" + ":".join(h[i : i + 4] for i in range(0, len(h), 4))


def _cargar_clave_raiz_der(spki_der: bytes) -> ec.EllipticCurvePublicKey:
    try:
        k = serialization.load_der_public_key(spki_der)
    except Exception as exc:
        raise DirectorioError("La clave raíz no es una clave pública DER válida.") from exc
    if not isinstance(k, ec.EllipticCurvePublicKey) or k.curve.name != "secp256r1":
        raise DirectorioError("La clave raíz debe ser P-256 (secp256r1).")
    return k


def raices_compilacion() -> list[bytes]:
    """Carga las claves raíz fijadas en el build. Falla cerrado si son marcadores."""
    raices: list[bytes] = []
    for nombre, b64 in (("RAIZ_A", RAIZ_A_SPKI), ("RAIZ_B", RAIZ_B_SPKI)):
        if b64 and b64.strip():
            try:
                der = base64.b64decode(b64.strip(), validate=True)
                _cargar_clave_raiz_der(der)
                raices.append(der)
            except Exception as exc:
                raise DirectorioError(f"La clave raíz {nombre} del build es inválida.") from exc
    return raices


def verificar_y_parsear_directorio(
    contenido_texto_o_bytes: str | bytes,
    raices_spki: Sequence[bytes] | None = None,
) -> DirectorioConfianza:
    """Verifica y parsea un sobre de directorio según el Anexo A.

    Lanza DirectorioError si no es válido, excede 64 KiB o la firma de la raíz falla.
    """
    raw_bytes = (
        contenido_texto_o_bytes.encode("utf-8")
        if isinstance(contenido_texto_o_bytes, str)
        else bytes(contenido_texto_o_bytes)
    )
    if len(raw_bytes) > MAXIMO_DIRECTORIO_BYTES:
        raise DirectorioError(f"El directorio excede el tamaño máximo permitido ({len(raw_bytes)} > {MAXIMO_DIRECTORIO_BYTES}).")

    try:
        sobre = json.loads(raw_bytes.decode("utf-8"))
    except Exception as exc:
        raise DirectorioError("El archivo de directorio no es un JSON válido.") from exc

    if not isinstance(sobre, dict):
        raise DirectorioError("El sobre de directorio no es un objeto JSON.")

    for campo_requerido in ("datos", "firma"):
        if campo_requerido not in sobre or not isinstance(sobre[campo_requerido], str):
            raise DirectorioError(f"Falta el campo requerido '{campo_requerido}' en el sobre.")

    datos_str = sobre["datos"]
    firma_b64url = sobre["firma"].strip()

    # Raíces autorizadas
    raices = list(raices_spki) if raices_spki is not None else raices_compilacion()
    if not raices:
        raise DirectorioError("No hay claves raíz configuradas para validar el directorio.")

    # Decodificar firma cruda r || s (64 bytes)
    try:
        raw_sig = base64.urlsafe_b64decode(firma_b64url + "==" * (-len(firma_b64url) % 4))
    except Exception as exc:
        raise DirectorioError("La firma del directorio no es Base64url válida.") from exc

    if len(raw_sig) != 64:
        raise DirectorioError(f"La firma debe tener exactamente 64 bytes (r||s), recibidos: {len(raw_sig)}.")

    r = int.from_bytes(raw_sig[:32], "big")
    s = int.from_bytes(raw_sig[32:], "big")
    der_sig = encode_dss_signature(r, s)

    mensaje_firmado = DOMINIO_DIRECTORIO + datos_str.encode("utf-8")

    firma_valida = False
    for raiz_der in raices:
        try:
            pub = _cargar_clave_raiz_der(raiz_der)
            pub.verify(der_sig, mensaje_firmado, ec.ECDSA(hashes.SHA256()))
            firma_valida = True
            break
        except (InvalidSignature, DirectorioError):
            continue

    if not firma_valida:
        raise DirectorioError("Firma del directorio no válida contra ninguna clave raíz.")

    # Parsea datos internos
    try:
        datos = json.loads(datos_str)
    except Exception as exc:
        raise DirectorioError("El campo 'datos' no es un JSON válido.") from exc

    if not isinstance(datos, dict):
        raise DirectorioError("El campo 'datos' debe ser un objeto JSON.")

    if datos.get("v") != VERSION_DIRECTORIO:
        raise DirectorioError(f"Versión de directorio no soportada: {datos.get('v')}.")

    seq = datos.get("seq")
    if not isinstance(seq, int) or seq < 0:
        raise DirectorioError("El campo 'seq' debe ser un entero no negativo.")

    emitida_en = datos.get("emitidaEn")
    if not isinstance(emitida_en, int) or emitida_en <= 0:
        raise DirectorioError("El campo 'emitidaEn' debe ser un timestamp positivo.")

    claves_json = datos.get("claves")
    if not isinstance(claves_json, list):
        raise DirectorioError("El campo 'claves' debe ser una lista.")

    claves_procesadas: list[ClaveDirectorio] = []
    ecdh_vigentes_count = 0

    for idx, c in enumerate(claves_json):
        if not isinstance(c, dict):
            raise DirectorioError(f"La clave en índice {idx} no es un objeto.")

        kid = c.get("kid")
        uso = c.get("uso")
        spki_b64 = c.get("spki")
        huella = c.get("huella")
        desde = c.get("desde")
        hasta = c.get("hasta")
        estado = c.get("estado")

        if not isinstance(kid, str) or not kid.strip():
            raise DirectorioError(f"Clave {idx}: 'kid' inválido.")
        if uso not in USOS_VALIDOS:
            raise DirectorioError(f"Clave {idx}: 'uso' no soportado: {uso!r}.")
        if estado not in ESTADOS_VALIDOS:
            raise DirectorioError(f"Clave {idx}: 'estado' no soportado: {estado!r}.")
        if not isinstance(spki_b64, str):
            raise DirectorioError(f"Clave {idx}: 'spki' debe ser string Base64.")
        if not isinstance(huella, str):
            raise DirectorioError(f"Clave {idx}: 'huella' debe ser string.")
        if not isinstance(desde, int):
            raise DirectorioError(f"Clave {idx}: 'desde' debe ser entero.")
        if hasta is not None and not isinstance(hasta, int):
            raise DirectorioError(f"Clave {idx}: 'hasta' debe ser entero o null.")

        try:
            spki_der = base64.b64decode(spki_b64.strip(), validate=True)
        except Exception as exc:
            raise DirectorioError(f"Clave {idx} ({kid}): SPKI no es Base64 válido.") from exc

        try:
            pub_key = serialization.load_der_public_key(spki_der)
        except Exception as exc:
            raise DirectorioError(f"Clave {idx} ({kid}): SPKI no es una clave pública DER válida.") from exc

        if not isinstance(pub_key, ec.EllipticCurvePublicKey) or pub_key.curve.name != "secp256r1":
            raise DirectorioError(f"Clave {idx} ({kid}): La clave no es P-256 (secp256r1).")

        fp_calculada = hashlib.sha256(spki_der).hexdigest()
        if normalizar_huella(huella) != fp_calculada:
            raise DirectorioError(f"Clave {idx} ({kid}): La huella no coincide con el SPKI.")

        if uso == "ecdh" and estado == "vigente":
            ecdh_vigentes_count += 1
            if ecdh_vigentes_count > 1:
                raise DirectorioError("El directorio no puede tener más de una clave ECDH en estado 'vigente'.")

        claves_procesadas.append(
            ClaveDirectorio(
                kid=kid,
                uso=uso,
                spki_b64=spki_b64,
                spki_der=spki_der,
                huella=huella,
                desde=desde,
                hasta=hasta,
                estado=estado,
            )
        )

    return DirectorioConfianza(
        v=VERSION_DIRECTORIO,
        seq=seq,
        emitida_en=emitida_en,
        claves=claves_procesadas,
    )


def firmar_directorio(
    datos_obj: Mapping[str, Any],
    raiz_privada: ec.EllipticCurvePrivateKey,
    identificador_raiz: str = "A",
) -> str:
    """Herramienta para generar y firmar un sobre de directorio."""
    datos_str = json.dumps(datos_obj, separators=(",", ":"), ensure_ascii=False)
    mensaje = DOMINIO_DIRECTORIO + datos_str.encode("utf-8")
    der_sig = raiz_privada.sign(mensaje, ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(der_sig)
    raw_sig = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    sig_b64url = base64.urlsafe_b64encode(raw_sig).decode().rstrip("=")
    sobre = {
        "datos": datos_str,
        "firma": sig_b64url,
        "raiz": identificador_raiz,
    }
    return json.dumps(sobre, separators=(",", ":"), ensure_ascii=False)
