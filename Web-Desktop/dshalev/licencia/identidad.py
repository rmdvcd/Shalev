"""Identidad criptográfica persistente de la instalación de DShalev.

Una sola identidad P-256 por instalación (``DShalevData/licencia/identidad.bin``),
compartida por los locales independientes. La licencia de canje se vincula a esa
identidad, a la app y al equipo; **no** se crea una identidad por local.

Reglas que esta implementación garantiza:

* La clave privada nunca se escribe en claro en Windows: DPAPI del usuario.
* Si el archivo existe y no se puede abrir, se falla con error; **no** se genera
  otra identidad en silencio.
* Se exporta solo la clave pública SPKI DER. La huella es
  ``Base64url(SHA-256(SPKI))`` sin relleno.
* Los mensajes de error no contienen la clave ni datos privados.
"""

from __future__ import annotations

import base64
import hashlib
import os
import secrets
import time
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from . import proteccion

CABECERA = b"DSHALEV-ID-1\n"
VERSION = 1
NOMBRE_ARCHIVO = "identidad.bin"
DESCRIPCION = "DShalev license identity (P-256)"
_CURVA = "secp256r1"
_INTENTOS_LECTURA = 40
_ESPERA_LECTURA = 0.025


class IdentidadError(RuntimeError):
    """La identidad del equipo no se puede usar y no se debe regenerar."""


def ruta_identidad(raiz_instalacion: Path) -> Path:
    """Ruta de la identidad de la instalación (no la de un local concreto)."""
    return Path(raiz_instalacion) / "DShalevData" / "licencia" / NOMBRE_ARCHIVO


def huella_de_spki(spki_der: bytes) -> str:
    return base64.urlsafe_b64encode(hashlib.sha256(spki_der).digest()).decode("ascii").rstrip("=")


class Identidad:
    """Par P-256 cargado desde disco. La clave privada no se expone."""

    __slots__ = ("ruta", "_clave", "spki_der", "huella")

    def __init__(self, ruta: Path, clave: ec.EllipticCurvePrivateKey) -> None:
        self.ruta = Path(ruta)
        self._clave = clave
        self.spki_der = clave.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        self.huella = huella_de_spki(self.spki_der)

    def __repr__(self) -> str:
        return f"Identidad(ruta={str(self.ruta)!r}, huella={self.huella!r})"

    @property
    def spki_b64url(self) -> str:
        return base64.urlsafe_b64encode(self.spki_der).decode("ascii").rstrip("=")

    @property
    def publica(self) -> ec.EllipticCurvePublicKey:
        return self._clave.public_key()

    def firmar(self, datos: bytes) -> bytes:
        """ECDSA P-256/SHA-256 con codificación DER, como exige el contrato GL."""
        if not isinstance(datos, (bytes, bytearray)):
            raise TypeError("Solo se firman bytes.")
        return self._clave.sign(bytes(datos), ec.ECDSA(hashes.SHA256()))


def _cargar_clave(contenido: bytes, ruta: Path) -> Identidad:
    prefijo = CABECERA
    if not contenido.startswith(prefijo):
        raise IdentidadError(
            "La identidad de este equipo está dañada o no es de DShalev. No se generó otra identidad."
        )
    resto = contenido[len(prefijo):]
    if len(resto) < 2 or resto[0] != VERSION:
        raise IdentidadError(
            "La identidad de este equipo tiene una versión no reconocida. Actualiza DShalev; no se generó otra identidad."
        )
    try:
        clave = serialization.load_der_private_key(resto[1:], password=None)
    except (ValueError, TypeError):
        raise IdentidadError(
            "No se pudo leer la clave privada de la identidad. Conserve DShalevData y el usuario de Windows original; no se generó otra identidad."
        ) from None
    if not isinstance(clave, ec.EllipticCurvePrivateKey) or clave.curve.name != _CURVA:
        raise IdentidadError("La identidad de este equipo no es un par P-256 válido. No se generó otra identidad.")
    return Identidad(ruta, clave)


def cargar_identidad(ruta: Path) -> Identidad | None:
    """Carga la identidad existente. Devuelve ``None`` solo si el archivo no existe."""
    ruta = Path(ruta)
    if not ruta.exists():
        return None
    try:
        contenido = proteccion.leer_protegido(ruta, descripcion=DESCRIPCION)
    except proteccion.ProteccionError as exc:
        raise IdentidadError(
            f"No se pudo abrir la identidad P-256 de este equipo y no se creó otra. {exc}"
        ) from None
    return _cargar_clave(contenido, ruta)


def _escribir_temporal(ruta: Path, datos: bytes) -> Path:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    temporal = ruta.with_name(f".{ruta.name}.{secrets.token_hex(8)}.tmp")
    descriptor = os.open(temporal, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as salida:
            salida.write(datos)
            salida.flush()
            os.fsync(salida.fileno())
    except OSError:
        try:
            temporal.unlink()
        except OSError:
            pass
        raise
    return temporal


def _crear(ruta: Path) -> Identidad:
    clave = ec.generate_private_key(ec.SECP256R1())
    privada = clave.private_bytes(
        serialization.Encoding.DER,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    contenido = CABECERA + bytes([VERSION]) + privada
    try:
        sello = proteccion.sellar(contenido, descripcion=DESCRIPCION, ruta=ruta)
    except proteccion.ProteccionError as exc:
        raise IdentidadError(f"No se pudo proteger la nueva identidad y no se guardó. {exc}") from None
    try:
        temporal = _escribir_temporal(ruta, sello)
    except OSError:
        raise IdentidadError("No se pudo preparar el archivo de identidad de la instalación.") from None
    try:
        try:
            # Publica el archivo ya completo: nunca se observa una identidad a medias.
            os.link(temporal, ruta)
            proteccion.sincronizar_directorio(ruta.parent)
        except FileExistsError:
            return _cargar_con_espera(ruta)
        except OSError:
            # Sistemas de archivos sin enlaces duros (por ejemplo, FAT32 en un USB).
            return _publicar_con_exclusiva(ruta, temporal, clave)
        return Identidad(ruta, clave)
    finally:
        try:
            temporal.unlink()
        except OSError:
            pass


def _publicar_con_exclusiva(ruta: Path, temporal: Path, clave: ec.EllipticCurvePrivateKey) -> Identidad:
    try:
        descriptor = os.open(ruta, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        return _cargar_con_espera(ruta)
    except OSError:
        raise IdentidadError("No se pudo crear el archivo de identidad de la instalación.") from None
    try:
        datos = temporal.read_bytes()
        with os.fdopen(descriptor, "wb") as salida:
            salida.write(datos)
            salida.flush()
            os.fsync(salida.fileno())
        try:
            os.chmod(ruta, 0o600)
        except OSError:
            pass
        proteccion.sincronizar_directorio(ruta.parent)
    except OSError:
        try:
            ruta.unlink()
        except OSError:
            pass
        raise IdentidadError("No se pudo guardar la identidad de la instalación; no se dejó un archivo a medias.") from None
    return Identidad(ruta, clave)


def _cargar_con_espera(ruta: Path) -> Identidad:
    """Otra ejecución pudo crear el archivo en paralelo y estar escribiéndolo."""
    ultimo: Exception | None = None
    for _ in range(_INTENTOS_LECTURA):
        try:
            identidad = cargar_identidad(ruta)
        except IdentidadError as exc:
            ultimo = exc
            identidad = None
        if identidad is not None:
            return identidad
        time.sleep(_ESPERA_LECTURA)
    if ultimo is not None:
        raise ultimo
    raise IdentidadError("La identidad de la instalación desapareció mientras se creaba.")


def cargar_o_crear_identidad(ruta: Path) -> Identidad:
    """Devuelve la identidad de la instalación, creándola solo si no existe.

    Nunca reemplaza un archivo existente, aunque esté dañado: en ese caso lanza
    ``IdentidadError`` para no perder el vínculo con una licencia ya emitida.
    """
    ruta = Path(ruta)
    if ruta.exists():
        # Otra ejecución pudo publicarlo y estar escribiéndolo (FAT32 sin enlaces).
        return _cargar_con_espera(ruta)
    return _crear(ruta)


def importar_publica(spki_der: bytes) -> ec.EllipticCurvePublicKey:
    """Utilidad para pruebas y para fijar claves en la configuración de compilación."""
    clave = serialization.load_der_public_key(spki_der)
    if not isinstance(clave, ec.EllipticCurvePublicKey) or clave.curve.name != _CURVA:
        raise ValueError("La clave pública no es P-256.")
    return clave
