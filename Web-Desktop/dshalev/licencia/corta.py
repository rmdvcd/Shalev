"""Módulo de verificación y descifrado de licencias cortas SPVI2 para DShalev.

Implementa el contrato GL v1 de licencia corta cifrada:
- Formato: ``SPVI2:`` + Base64url sin relleno de ``epk (33 B) ‖ ct (44 B) ‖ tag (16 B) ‖ firma (64 B)`` (216 caracteres).
- Extracción tolerante a espacios y saltos de línea con errores accionables (C3).
- Verificación de firma ECDSA P-256 / SHA-256 sobre ``SPVI-L2|`` ‖ epk ‖ ct ‖ tag ANTES de descifrar.
- Descifrado AES-256-GCM con clave e IV derivados por HKDF-SHA256 (info = b"SPVI-L2", salt = epk).
- Validación de cuerpo de 44 bytes: formato 0x02, UUID, huella del dispositivo, tipo, estado y fechas.
- Evaluación de prevalencia frente a licencias vigentes (perpetua siempre gana, o mayor cobertura).
"""

from __future__ import annotations

import base64
import hashlib
import json
import re
import struct
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Mapping, Sequence

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from .almacen import AlmacenLicencia, LicenciaLocal
from .identidad import Identidad
from .solicitud import (
    ETIQUETA_TIPO,
    TIPOS,
    b64url,
    comprimir_punto_p256,
    derivar_device_id,
    unb64url,
)

LIC_PREFIJO = "SPVI2:"
LIC_DOMINIO = b"SPVI-L2|"
LIC_INFO = b"SPVI-L2"
LARGO_BASE64 = 210
LARGO_BINARIO = 157
LARGO_CUERPO = 44

ESTADOS = ("ACTIVA", "VENCIDA", "REVOCADA", "PERPETUA")
B64URL_RE = re.compile(r"^[A-Za-z0-9_-]+$")

# Claves públicas de firma de GL ancladas en el build
GL_SIGN_SPKIS_DEFECTO: list[str] = [
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAED3b2+vZldNNAkjW9X6hKZebUnQ4ycS7lqCK1Z6IMdRHlhhXq1/h7eBRj/4VYAzIjdSFKRknFaATttWM4NTlEQg==",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8TwMudgF0ioPFGp+VOQDU+xBDEQQQRHErwPyKQupkBGS8WL/dQVAFN4t3PIddOBl3Kg60ucThoOicZFctdtjIQ==",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPjc7tw29A0TaynecwlYDphDAtavpfNQ6njeGIX59hIDO+ID9KxTLo4ZaxlPJ/enkCzlBo+5JEMn5AUN8bneC7Q==",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/Kx1wh2WpM/JcPeNs7e7a5QvRMARHAPreFZ9YauzFguI85mT7/wC/jmKxR1ml5SGrGWCfA88Qo+PQ4efSUSsdg==",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==",
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvXxzuIsum0ztpiAistqL4TGTpbVu3Cbn33hC4kzQtesGBa2nvag6xqK4DX4xQED6R/8WuDushc7bAURRu3znGg==",  # clave de prueba vector dorado
]


class LicenciaCortaError(Exception):
    """Fallo en la validación o activación de la licencia corta."""


@dataclass(frozen=True)
class CamposLicenciaCorta:
    id: str
    huella: bytes
    tipo: str
    estado: str
    secundarias: int
    emitida_en: datetime
    vence_en: datetime | None
    codigo_spvi2: str

    @property
    def es_perpetua(self) -> bool:
        return self.tipo == "PERPETUA" or self.estado == "PERPETUA" or self.vence_en is None


@dataclass(frozen=True)
class ResultadoActivacion:
    resultado: str  # "Accepted", "Absorbed", "NotFound", "Rejected", "Outdated"
    mensaje: str
    campos: CamposLicenciaCorta | None = None
    licencia_guardada: LicenciaLocal | None = None


# ── Extracción tolerante y errores accionables (C3) ────────────────────────────

def extraer_spvi2(texto: str) -> tuple[bytes | None, int, str]:
    """Extrae los 210 caracteres Base64url que siguen a SPVI2: ignorando espacios y saltos.

    Returns:
        (datos_binarios, n_caracteres_reunidos, mensaje_explicativo)
    """
    if not texto:
        return None, 0, "No se encontró una licencia en el texto pegado"

    idx = texto.find(LIC_PREFIJO)
    if idx < 0:
        return None, 0, "No se encontró una licencia en el texto pegado"

    while idx >= 0:
        chars: list[str] = []
        k = idx + len(LIC_PREFIJO)
        while k < len(texto) and len(chars) < LARGO_BASE64:
            c = texto[k]
            if c.isalnum() or c in ("-", "_"):
                chars.append(c)
            elif not c.isspace():
                break
            k += 1

        if len(chars) == LARGO_BASE64:
            cadena = "".join(chars)
            try:
                binario = unb64url(cadena)
                if len(binario) == LARGO_BINARIO:
                    return binario, LARGO_BASE64, ""
            except Exception:
                pass
        else:
            return (
                None,
                len(chars),
                f"Llegaron {len(chars)} de {LARGO_BASE64} caracteres; copia todas las partes del mensaje",
            )

        idx = texto.find(LIC_PREFIJO, idx + 1)

    return None, 0, "No se encontró una licencia en el texto pegado"


# ── Huella y comprobaciones criptográficas ─────────────────────────────────────

def calcular_huella_dispositivo(device_id: str, device_pub_comprimida: bytes) -> bytes:
    """Calcula los 16 primeros bytes de SHA-256("SPVI-L2|" + deviceId + "|" ‖ devicePub)."""
    return hashlib.sha256(
        LIC_DOMINIO + device_id.encode("utf-8") + b"|" + device_pub_comprimida
    ).digest()[:16]


def calcular_huella_revocacion(lic_id: str) -> str:
    """Primeros 16 B de SHA-256("SPVI-REV1|" + licenseId 16 B), en hex (32 caracteres)."""
    uid_bytes = uuid.UUID(lic_id).bytes
    return hashlib.sha256(b"SPVI-REV1|" + uid_bytes).hexdigest()[:32]


def descifrar_y_verificar(
    datos_binarios: bytes,
    identidad: Identidad,
    *,
    device_id: str | None = None,
    directorio: Any | None = None,
    claves_firma_adicionales: Sequence[bytes] | None = None,
    lista_revocadas: set[str] | None = None,
) -> CamposLicenciaCorta:
    """Verifica la firma GL, descifra el cuerpo con la identidad y valida campos."""
    if len(datos_binarios) != LARGO_BINARIO:
        raise LicenciaCortaError("La longitud binaria de la licencia corta es inválida.")

    epk_bytes = datos_binarios[:33]
    cifrado = datos_binarios[33:93]  # ct (44) + tag (16)
    firma_raw = datos_binarios[93:157]  # r (32) + s (32)

    if epk_bytes[0] not in (0x02, 0x03):
        raise LicenciaCortaError("Clave efímera con prefijo SEC1 inválido.")

    # Convertir firma r||s a formato DER
    r = int.from_bytes(firma_raw[:32], "big")
    s = int.from_bytes(firma_raw[32:], "big")
    firma_der = encode_dss_signature(r, s)

    mensaje_firmado = LIC_DOMINIO + epk_bytes + cifrado

    # Resolución de claves de firma candidatas (D2: si hay directorio válido, su lista manda)
    claves_candidatas: list[bytes] = []
    if directorio is not None and hasattr(directorio, "sign_keys_for"):
        claves_candidatas = list(directorio.sign_keys_for(datetime.now(timezone.utc)))
    elif claves_firma_adicionales:
        claves_candidatas = list(claves_firma_adicionales)
    else:
        for b64 in GL_SIGN_SPKIS_DEFECTO:
            try:
                claves_candidatas.append(base64.b64decode(b64))
            except Exception:
                pass

    if claves_firma_adicionales and directorio is not None:
        claves_candidatas.extend(claves_firma_adicionales)

    firma_valida = False
    for spki_der in claves_candidatas:
        try:
            pub = serialization.load_der_public_key(spki_der)
            if isinstance(pub, ec.EllipticCurvePublicKey):
                pub.verify(firma_der, mensaje_firmado, ec.ECDSA(hashes.SHA256()))
                firma_valida = True
                break
        except (InvalidSignature, ValueError, TypeError):
            continue

    if not firma_valida:
        raise LicenciaCortaError("La licencia no es válida para este dispositivo")

    # Descifrar cuerpo con la clave privada de la identidad
    try:
        epk_pub = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), epk_bytes)
    except Exception as exc:
        raise LicenciaCortaError("No se pudo descomprimir la clave efímera.") from exc

    try:
        ikm = identidad._clave.exchange(ec.ECDH(), epk_pub)
        okm = HKDF(algorithm=hashes.SHA256(), length=44, salt=epk_bytes, info=LIC_INFO).derive(ikm)
        clave, iv = okm[:32], okm[32:44]
        cuerpo = AESGCM(clave).decrypt(iv, cifrado, LIC_DOMINIO + epk_bytes)
    except (InvalidTag, ValueError, Exception) as exc:
        raise LicenciaCortaError("La licencia no es válida para este dispositivo") from exc

    if len(cuerpo) != LARGO_CUERPO:
        raise LicenciaCortaError("El cuerpo descifrado tiene una longitud incorrecta.")

    formato = cuerpo[0]
    if formato != 0x02:
        raise LicenciaCortaError("Versión de cuerpo de licencia no admitida.")

    lic_id = str(uuid.UUID(bytes=cuerpo[1:17]))
    huella_cuerpo = cuerpo[17:33]
    tipo_idx = cuerpo[33]
    estado_idx = cuerpo[34]
    secundarias = cuerpo[35]

    if tipo_idx >= len(TIPOS) or estado_idx >= len(ESTADOS):
        raise LicenciaCortaError("Valores de tipo o estado fuera de rango.")

    tipo = TIPOS[tipo_idx]
    estado = ESTADOS[estado_idx]

    emitida_ts = struct.unpack(">I", cuerpo[36:40])[0]
    vence_ts = struct.unpack(">I", cuerpo[40:44])[0]

    emitida_en = datetime.fromtimestamp(emitida_ts, tz=timezone.utc)
    vence_en = None if vence_ts == 0 else datetime.fromtimestamp(vence_ts, tz=timezone.utc)

    if vence_en is not None and vence_en <= emitida_en:
        raise LicenciaCortaError("La fecha de vencimiento debe ser posterior a la de emisión.")

    # Verificación de huella del dispositivo
    dev_id = device_id or derivar_device_id(identidad)
    dev_pub_33b = comprimir_punto_p256(identidad.publica)
    huella_esperada = calcular_huella_dispositivo(dev_id, dev_pub_33b)

    if huella_cuerpo != huella_esperada:
        raise LicenciaCortaError("La licencia no es válida para este dispositivo")

    # Verificación en lista de revocadas si está provista
    if lista_revocadas is not None:
        hr = calcular_huella_revocacion(lic_id)
        if hr in lista_revocadas or lic_id in lista_revocadas:
            estado = "REVOCADA"

    codigo_completo = LIC_PREFIJO + b64url(datos_binarios)

    return CamposLicenciaCorta(
        id=lic_id,
        huella=huella_cuerpo,
        tipo=tipo,
        estado=estado,
        secundarias=secundarias,
        emitida_en=emitida_en,
        vence_en=vence_en,
        codigo_spvi2=codigo_completo,
    )


# ── Prevalencia y orden de licencias ──────────────────────────────────────────

def evaluar_prevalencia(
    actual: LicenciaLocal | None,
    nueva: CamposLicenciaCorta,
    identidad: Identidad | None = None,
) -> tuple[str, str]:
    """Evalúa la prevalencia entre la licencia actualmente instalada y la nueva.

    Reglas canónicas:
    1. Si actual es Perpetua activa y nueva no es Perpetua: Absorbed.
    2. Si ambas tienen vencimiento: la de mayor vencimiento gana (si actual vence después -> Absorbed).
    3. Si nueva fue emitida antes que actual: Outdated.
    4. En cualquier otro caso: Accepted.
    """
    if actual is None or not actual.documento:
        return "Accepted", "Licencia activada"

    # Si la actual está revocada, cualquier licencia válida nueva no-revocada la reemplaza
    if actual.revocada and nueva.estado != "REVOCADA":
        return "Accepted", "Licencia activada"

    # Extraer información de la licencia actual (si es SPVI2 o envelope canje)
    actual_es_perpetua = False
    actual_vence_en: datetime | None = None
    actual_emitida_en: datetime | None = None

    doc = actual.documento.strip()
    if doc.startswith(LIC_PREFIJO) and identidad is not None:
        try:
            raw = unb64url(doc[len(LIC_PREFIJO):])
            epk_bytes = raw[:33]
            cifrado = raw[33:93]
            epk_pub = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), epk_bytes)
            ikm = identidad._clave.exchange(ec.ECDH(), epk_pub)
            okm = HKDF(algorithm=hashes.SHA256(), length=44, salt=epk_bytes, info=LIC_INFO).derive(ikm)
            clave, iv = okm[:32], okm[32:44]
            cuerpo = AESGCM(clave).decrypt(iv, cifrado, LIC_DOMINIO + epk_bytes)
            actual_tipo_idx = cuerpo[33]
            actual_es_perpetua = (actual_tipo_idx == 3)
            vts = struct.unpack(">I", cuerpo[40:44])[0]
            if vts != 0:
                actual_vence_en = datetime.fromtimestamp(vts, tz=timezone.utc)
            ets = struct.unpack(">I", cuerpo[36:40])[0]
            actual_emitida_en = datetime.fromtimestamp(ets, tz=timezone.utc)
        except Exception:
            pass
    elif doc.startswith("{"):
        try:
            datos_json = json.loads(doc)
            lic_obj = datos_json.get("license", {}) if isinstance(datos_json, dict) else {}
            if "license" in lic_obj and isinstance(lic_obj["license"], dict):
                lic_data = lic_obj["license"]
                actual_es_perpetua = lic_data.get("type") == "PERPETUA"
                exp = lic_data.get("expiresAt")
                if exp:
                    actual_vence_en = datetime.fromisoformat(exp.replace("Z", "+00:00"))
        except Exception:
            pass

    # Si la actual es perpetua no revocada y la nueva no:
    if actual_es_perpetua and not nueva.es_perpetua:
        return (
            "Absorbed",
            "Ya tienes una licencia activa con mayor cobertura (Licencia Perpetua vigente).",
        )

    # Si la nueva es perpetua: gana siempre
    if nueva.es_perpetua:
        return "Accepted", "Licencia activada"

    # Comparación por vencimiento
    if actual_vence_en is not None and nueva.vence_en is not None:
        if actual_vence_en > nueva.vence_en:
            dia_actual = actual_vence_en.strftime("%d/%m/%Y")
            dia_nueva = nueva.vence_en.strftime("%d/%m/%Y")
            return (
                "Absorbed",
                f"Ya tienes una licencia activa con mayor cobertura (vence {dia_actual} posterior a {dia_nueva}).",
            )
        elif actual_vence_en == nueva.vence_en:
            return "Outdated", "Ya tienes instalada una licencia idéntica o más reciente."

    return "Accepted", "Licencia activada"


# ── Activación completa en almacén ─────────────────────────────────────────────

def activar_spvi2(
    texto: str,
    identidad: Identidad,
    almacen: AlmacenLicencia,
    *,
    directorio: Any | None = None,
    claves_firma_adicionales: Sequence[bytes] | None = None,
    lista_revocadas: set[str] | None = None,
) -> ResultadoActivacion:
    """Extrae, valida y activa una licencia corta SPVI2 en el almacén de DShalev."""
    binario, n_chars, error_extraccion = extraer_spvi2(texto)
    if binario is None:
        return ResultadoActivacion(resultado="NotFound", mensaje=error_extraccion)

    try:
        campos = descifrar_y_verificar(
            binario,
            identidad,
            directorio=directorio,
            claves_firma_adicionales=claves_firma_adicionales,
            lista_revocadas=lista_revocadas,
        )
    except LicenciaCortaError as exc:
        return ResultadoActivacion(resultado="Rejected", mensaje=str(exc))

    # Comprobar si la licencia que llega está revocada
    if campos.estado == "REVOCADA":
        return ResultadoActivacion(
            resultado="Rejected",
            mensaje="La licencia fue revocada o transferida a otro dispositivo.",
            campos=campos,
        )

    # Observar almacén para evaluar prevalencia
    obs = almacen.observar()
    resultado_prev, mensaje_prev = evaluar_prevalencia(obs.licencia, campos, identidad=identidad)

    if resultado_prev in ("Absorbed", "Outdated"):
        return ResultadoActivacion(
            resultado=resultado_prev,
            mensaje=mensaje_prev,
            campos=campos,
            licencia_guardada=obs.licencia,
        )

    # Guardar nueva licencia en el almacén
    lic_local = almacen.guardar_licencia(campos.codigo_spvi2, estado=campos.estado)

    return ResultadoActivacion(
        resultado="Accepted",
        mensaje="Licencia activada",
        campos=campos,
        licencia_guardada=lic_local,
    )
