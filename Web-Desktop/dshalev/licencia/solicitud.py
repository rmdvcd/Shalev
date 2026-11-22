"""Módulo de generación de solicitudes de licencia SPVIR1 para DShalev.

Implementa el contrato GL v1 / v2 de solicitud cifrada:
- Formato compacto: ``SPVIR1:`` + Base64url sin relleno de ``epk (33 B) ‖ ct ‖ tag (16 B)``.
- Clave efímera P-256 única por solicitud.
- Derivación HKDF-SHA256 con salt = epk e info = b"SPVI-R1", 44 bytes (clave 32 B + IV 12 B).
- AES-256-GCM con AAD = b"SPVI-R1|" + epk.
- Payload JSON v=2 con orden canónico, compactado sin espacios.
- Texto legible conforme a C1 (100% GSM-7, sin tildes ni caracteres que fuercen UCS-2).
"""

from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Mapping

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from .identidad import Identidad

# ── Constantes y tarifas ───────────────────────────────────────────────────────

SOL_PREFIJO = "SPVIR1:"
SOL_INFO = b"SPVI-R1"
SOL_DOMINIO = b"SPVI-R1|"
LARGO_EPK = 33
LARGO_TAG = 16

AVISO_FRESCURA = "Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."

ENVIAR_TX_EN_SOLICITUD: bool = False

TIPOS = ("MENSUAL", "SEMESTRAL", "ANUAL", "PERPETUA")
VIAS = ("WHATSAPP", "SMS")

PRECIOS_BASE: Mapping[str, int] = {
    "MENSUAL": 6_000,
    "SEMESTRAL": 30_000,
    "ANUAL": 50_000,
    "PERPETUA": 90_000,
}

PRECIOS_SECUNDARIA: Mapping[str, int] = {
    "MENSUAL": 1_000,
    "SEMESTRAL": 5_000,
    "ANUAL": 9_000,
    "PERPETUA": 17_000,
}

ETIQUETA_TIPO: Mapping[str, str] = {
    "MENSUAL": "Mensual",
    "SEMESTRAL": "Semestral",
    "ANUAL": "Anual",
    "PERPETUA": "Perpetua",
}

GL_ECDH_SPKI_DEFECTO = (
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELQaSJKdqp6b/uqGc+0juRctWGdb9mHQZj2CZRHsrKNsux9UhBbTHirLhF7q6KupNGc9PwwiqNAVcpAT7MR2EvA=="
)

TEL_SOPORTE_DEFECTO = "+5351815604"


class SolicitudError(ValueError):
    """Datos inválidos para generar la solicitud."""


# ── Utilidades de codificación y negocio ───────────────────────────────────────

def b64url(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).decode("ascii").rstrip("=")


def unb64url(texto: str) -> bytes:
    pad = "=" * (-len(texto) % 4)
    return base64.urlsafe_b64decode(texto + pad)


def comprimir_punto_p256(public_key: ec.EllipticCurvePublicKey) -> bytes:
    return public_key.public_bytes(
        serialization.Encoding.X962, serialization.PublicFormat.CompressedPoint
    )


def derivar_device_id(identidad: Identidad) -> str:
    """Deriva un deviceId determinista para la instalación de DShalev."""
    hex16 = hashlib.sha256(identidad.spki_der).hexdigest()[:16]
    return f"SPVI:{hex16}"


def derivar_device_pub(identidad: Identidad) -> str:
    """Devuelve la clave pública comprimida P-256 en Base64url sin relleno."""
    return b64url(comprimir_punto_p256(identidad.publica))


def precio_total(tipo: str, secundarias: int = 0) -> int:
    if tipo not in PRECIOS_BASE:
        raise SolicitudError(f"Tipo de licencia desconocido: {tipo}")
    n = max(0, min(10, int(secundarias)))
    return PRECIOS_BASE[tipo] + n * PRECIOS_SECUNDARIA[tipo]


def precio_ampliacion(tipo: str, adicionales: int) -> int:
    if tipo not in PRECIOS_SECUNDARIA:
        raise SolicitudError(f"Tipo de licencia desconocido: {tipo}")
    n = max(1, int(adicionales))
    return n * PRECIOS_SECUNDARIA[tipo]


calcular_precio = precio_total
calcular_precio_ampliacion = precio_ampliacion


def formatear_cup(pesos: int | float) -> str:
    return f"{pesos:,.2f} CUP"


def normalizar_gsm7(texto: str) -> str:
    """Normaliza texto eliminando acentos y caracteres fuera del conjunto básico GSM-7 (C1)."""
    # Reemplazo de caracteres con tilde y eñes
    reemplazos = {
        "á": "a", "é": "e", "í": "i", "ó": "o", "ú": "u", "ü": "u", "ñ": "n",
        "Á": "A", "É": "E", "Í": "I", "Ó": "O", "Ú": "U", "Ü": "U", "Ñ": "N",
    }
    limpio = "".join(reemplazos.get(c, c) for c in texto)
    # Descomposición y eliminación de marcas diacríticas residuales
    nfkd = unicodedata.normalize("NFKD", limpio)
    sin_diacriticos = "".join(c for c in nfkd if not unicodedata.combining(c))
    # Caracteres no permitidos en GSM-7 o que generan saltos UCS-2 / escapes
    prohibidos = str.maketrans("", "", "|{}[]^~\\€")
    resultado = sin_diacriticos.translate(prohibidos)
    # Conservar solo caracteres ASCII legibles y espacios
    return "".join(c for c in resultado if 0x20 <= ord(c) <= 0x7E or c in "\r\n\t")


# ── Validación de entradas ─────────────────────────────────────────────────────

_RE_CI = re.compile(r"^\d{11}$")
_RE_UUID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
_RE_TX = re.compile(r"^[A-Za-z0-9-]{4,30}$")


def validar_transferencia(tx: str | None) -> str | None:
    if not tx:
        return None
    limpio = tx.strip()
    if not limpio:
        return None
    if not _RE_TX.fullmatch(limpio):
        raise SolicitudError("El número de transferencia debe tener entre 4 y 30 caracteres alfanuméricos o guiones.")
    return limpio


def validar_nombre(nombre: str) -> str:
    n = (nombre or "").strip()
    if len(n) < 2 or len(n) > 80:
        raise SolicitudError("El nombre debe tener entre 2 y 80 caracteres.")
    if not (n[0].isalpha() and n[-1].isalpha()):
        raise SolicitudError("El nombre debe empezar y terminar con una letra.")
    if not all(c.isalpha() or c in (" ", "'", "-") for c in n):
        raise SolicitudError("El nombre solo puede contener letras, espacios, apóstrofo o guion.")
    return n


def validar_carne_cubano(ci: str, hoy: datetime | None = None) -> str:
    c = (ci or "").strip()
    if not _RE_CI.fullmatch(c):
        raise SolicitudError("El carné de identidad debe contener exactamente 11 dígitos numéricos.")
    siglo_char = c[6]
    siglos = {
        "9": 1800,
        "0": 1900, "1": 1900, "2": 1900, "3": 1900, "4": 1900, "5": 1900,
        "6": 2000, "7": 2000, "8": 2000,
    }
    if siglo_char not in siglos:
        raise SolicitudError("Dígito de siglo no válido en el carné de identidad.")
    anio = siglos[siglo_char] + int(c[0:2])
    mes = int(c[2:4])
    dia = int(c[4:6])
    try:
        from datetime import date
        fecha_nac = date(anio, mes, dia)
    except ValueError:
        raise SolicitudError("La fecha de nacimiento en el carné de identidad no es válida.") from None
    fecha_hoy = (hoy.date() if hoy else datetime.now(timezone.utc).date())
    if fecha_nac > fecha_hoy:
        raise SolicitudError("La fecha de nacimiento en el carné de identidad no puede ser futura.")
    return c


def normalizar_telefono_cubano(telefono: str) -> str:
    raw = (telefono or "").strip()
    digitos = re.sub(r"\D", "", raw)
    if raw.startswith("+"):
        e164 = f"+{digitos}"
    elif raw.startswith("00") and len(digitos) > 2:
        e164 = f"+{digitos[2:]}"
    elif len(digitos) == 8:
        e164 = f"+53{digitos}"
    elif len(digitos) == 10 and digitos.startswith("53"):
        e164 = f"+{digitos}"
    else:
        e164 = f"+{digitos}"

    if len(e164) != 11 or not e164.startswith("+53") or e164[3] not in ("5", "6"):
        raise SolicitudError("El teléfono móvil cubano debe ser de 8 dígitos y comenzar con 5 o 6.")
    return e164


def normalizar_id_licencia(id_str: str) -> str:
    s = (id_str or "").strip().lower()
    if not _RE_UUID.fullmatch(s):
        raise SolicitudError(f"El identificador de licencia no es un UUID válido: {id_str}")
    return s


# ── Cifrado y formato de solicitud ─────────────────────────────────────────────

def sellar_solicitud(
    plano_bytes: bytes,
    gl_ecdh_pub: ec.EllipticCurvePublicKey,
    efimera: ec.EllipticCurvePrivateKey | None = None,
) -> str:
    """Cifra el payload JSON hacia la clave pública ECDH de GL."""
    eph = efimera or ec.generate_private_key(ec.SECP256R1())
    epk = comprimir_punto_p256(eph.public_key())
    ikm = eph.exchange(ec.ECDH(), gl_ecdh_pub)
    okm = HKDF(algorithm=hashes.SHA256(), length=44, salt=epk, info=SOL_INFO).derive(ikm)
    clave, iv = okm[:32], okm[32:44]
    ct_tag = AESGCM(clave).encrypt(iv, plano_bytes, SOL_DOMINIO + epk)
    return SOL_PREFIJO + b64url(epk + ct_tag)


def generar_solicitud(
    identidad: Identidad,
    *,
    nombre: str,
    apellidos: str,
    ci: str,
    telefono: str,
    tipo: str = "MENSUAL",
    secundarias: int = 0,
    via: str = "WHATSAPP",
    renueva: str | None = None,
    recupera: str | None = None,
    amplia: str | None = None,
    secundarias_previas: int = 0,
    tx: str | None = None,
    gl_ecdh_pub: ec.EllipticCurvePublicKey | None = None,
    efimera: ec.EllipticCurvePrivateKey | None = None,
    nonce: str | None = None,
    ahora: datetime | None = None,
) -> tuple[str, str, dict[str, Any]]:
    """Genera la solicitud cifrada SPVIR1 y el mensaje de texto 100% GSM-7.

    Returns:
        (mensaje_texto, codigo_spvir1, payload_dict)
    """
    nom = validar_nombre(nombre)
    ape = validar_nombre(apellidos)
    carnet = validar_carne_cubano(ci, ahora)
    tel = normalizar_telefono_cubano(telefono)
    tx_valido = validar_transferencia(tx)

    t = (tipo or "").strip().upper()
    if t not in TIPOS:
        raise SolicitudError(f"Tipo de licencia no válido: {tipo}")

    v = (via or "WHATSAPP").strip().upper()
    if v not in VIAS:
        raise SolicitudError(f"Vía de contacto no válida: {via}")

    sec = max(0, min(10, int(secundarias)))

    dev_id = derivar_device_id(identidad)
    dev_pub = derivar_device_pub(identidad)

    momento = (ahora or datetime.now(timezone.utc)).astimezone(timezone.utc).replace(microsecond=0)
    solicitada_en = momento.isoformat().replace("+00:00", "Z")

    n_nonce = nonce or b64url(os.urandom(16))

    id_renueva = normalizar_id_licencia(renueva) if renueva else None
    id_recupera = normalizar_id_licencia(recupera) if recupera else None
    id_amplia = normalizar_id_licencia(amplia) if amplia else None

    # Exclusiones mutuas
    activos = [x for x in (id_renueva, id_recupera, id_amplia) if x is not None]
    if len(activos) > 1:
        raise SolicitudError("renueva, recupera y amplia son mutuamente excluyentes.")

    # Construir payload JSON v=2
    payload: dict[str, Any] = {
        "v": 2,
        "nombre": nom,
        "apellidos": ape,
        "ci": carnet,
        "via": v,
        "telefono": tel,
        "deviceId": dev_id,
        "tipo": t,
        "solicitadaEn": solicitada_en,
        "nonce": n_nonce,
        "devicePub": dev_pub,
        "secundarias": sec,
    }
    if id_renueva:
        payload["renueva"] = id_renueva
    if id_recupera:
        payload["recupera"] = id_recupera
    if id_amplia:
        payload["amplia"] = id_amplia
    if ENVIAR_TX_EN_SOLICITUD and tx_valido:
        payload["tx"] = tx_valido

    plano_bytes = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")

    # Clave pública de GL
    if gl_ecdh_pub is None:
        gl_ecdh_pub = serialization.load_der_public_key(base64.b64decode(GL_ECDH_SPKI_DEFECTO))

    codigo = sellar_solicitud(plano_bytes, gl_ecdh_pub, efimera=efimera)

    # Construcción de mensaje 100% GSM-7
    nom_gsm = normalizar_gsm7(nom)
    ape_gsm = normalizar_gsm7(ape)
    lineas: list[str] = []

    if id_recupera:
        lineas.append("Recuperar licencia Shalev")
        lineas.append(f"Nombre: {nom_gsm} {ape_gsm}")
        lineas.append(f"Carne de identidad: {carnet}")
        lineas.append(f"Telefono: {tel}")
        lineas.append(f"Licencia anterior: {id_recupera}")
        if tx_valido:
            lineas.append(f"Transferencia: {tx_valido}")
        lineas.append(AVISO_FRESCURA)
        lineas.append("")
        lineas.append(codigo)
    elif id_amplia:
        prev = max(0, min(sec - 1, int(secundarias_previas)))
        adic = max(1, sec - prev)
        lineas.append("Ampliar secundarias Shalev")
        lineas.append(f"Nombre: {nom_gsm} {ape_gsm}")
        lineas.append(f"Carne de identidad: {carnet}")
        lineas.append(f"Telefono: {tel}")
        lineas.append(f"Tipo: {ETIQUETA_TIPO[t]}")
        lineas.append(f"Apps secundarias actuales: {prev}")
        lineas.append(f"Secundarias adicionales: {adic}")
        lineas.append(f"Apps secundarias: {sec}")
        lineas.append(f"Precio: {formatear_cup(precio_ampliacion(t, adic))}")
        lineas.append(f"Amplia: {id_amplia}")
        if tx_valido:
            lineas.append(f"Transferencia: {tx_valido}")
        lineas.append(AVISO_FRESCURA)
        lineas.append("")
        lineas.append(codigo)
    else:
        lineas.append("Solicitud de licencia Shalev")
        lineas.append(f"Nombre: {nom_gsm} {ape_gsm}")
        lineas.append(f"Carne de identidad: {carnet}")
        lineas.append(f"Telefono: {tel}")
        lineas.append(f"Tipo: {ETIQUETA_TIPO[t]}")
        lineas.append(f"Apps secundarias: {sec}")
        lineas.append(f"Precio: {formatear_cup(precio_total(t, sec))}")
        if id_renueva:
            lineas.append(f"Renueva: {id_renueva}")
        if tx_valido:
            lineas.append(f"Transferencia: {tx_valido}")
        lineas.append(AVISO_FRESCURA)
        lineas.append("")
        lineas.append(codigo)

    mensaje = "\n".join(lineas)
    return mensaje, codigo, payload
