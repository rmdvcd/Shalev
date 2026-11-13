"""Protección en reposo de los archivos de licencia de DShalev.

Hay dos modos deliberadamente separados:

``DPAPI``
    Solo en Windows. ``CryptProtectData`` con ámbito de usuario, sin interfaz y
    con una descripción propia del archivo. Un archivo copiado a otro usuario de
    Windows o a otro equipo no se puede abrir. DPAPI **no** es atestación de
    hardware ni demuestra que el equipo sea el original.

``PRUEBA``
    Solo fuera de Windows y solo con ``DSHALEV_ALLOW_DEV_IDENTITY=1``. Cifra con
    AES-256-GCM y una clave local junto al archivo. Sirve para pruebas en
    Linux/macOS; copiar la carpeta copia también la clave, así que no protege
    contra un respaldo ajeno. Se rechaza siempre en Windows, de forma que jamás
    pueda sustituir a DPAPI en la distribución portable.

Los mensajes de error nunca incluyen material de clave ni el contenido sellado.
"""

from __future__ import annotations

import ctypes
import os
import secrets
import time
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

VARIABLE_PRUEBA = "DSHALEV_ALLOW_DEV_IDENTITY"
MODO_DPAPI = "DPAPI"
MODO_PRUEBA = "PRUEBA"

CABECERA_DPAPI = b"DSLDPAPI1\n"
CABECERA_PRUEBA = b"DSLPRUEBA1\n"
CABECERA_CLAVE_PRUEBA = b"DSDEVKEY1"
LONGITUD_CLAVE = 32
LONGITUD_NONCE = 12

_FLAG_DPAPI_SIN_UI = 0x1
_INTENTOS_CLAVE = 40
_ESPERA_CLAVE = 0.025


class ProteccionError(RuntimeError):
    """No se pudo proteger o recuperar un archivo local."""


def es_windows() -> bool:
    return os.name == "nt"


def modo_de_escritura() -> str:
    return MODO_DPAPI if es_windows() else MODO_PRUEBA


def exigir_prueba_permitida() -> None:
    if es_windows():
        raise ProteccionError(
            "La protección de prueba no se puede usar en Windows: este equipo exige DPAPI."
        )
    if os.environ.get(VARIABLE_PRUEBA) != "1":
        raise ProteccionError(
            "La identidad de prueba requiere la variable DSHALEV_ALLOW_DEV_IDENTITY=1 y nunca se usa en Windows."
        )


def _dpapi(datos: bytes, *, descripcion: str, descifrar: bool) -> bytes:
    if not es_windows():
        raise ProteccionError("DPAPI solo está disponible en Windows.")

    class DataBlob(ctypes.Structure):
        _fields_ = [("cbData", ctypes.c_uint32), ("pbData", ctypes.POINTER(ctypes.c_ubyte))]

    origen_buffer = ctypes.create_string_buffer(datos)
    origen = DataBlob(len(datos), ctypes.cast(origen_buffer, ctypes.POINTER(ctypes.c_ubyte)))
    destino = DataBlob()
    crypt32 = ctypes.windll.crypt32
    crypt32.CryptProtectData.argtypes = [
        ctypes.POINTER(DataBlob), ctypes.c_wchar_p, ctypes.POINTER(DataBlob),
        ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, ctypes.POINTER(DataBlob),
    ]
    crypt32.CryptProtectData.restype = ctypes.c_int
    crypt32.CryptUnprotectData.argtypes = [
        ctypes.POINTER(DataBlob), ctypes.c_void_p, ctypes.POINTER(DataBlob),
        ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, ctypes.POINTER(DataBlob),
    ]
    crypt32.CryptUnprotectData.restype = ctypes.c_int
    if descifrar:
        ok = crypt32.CryptUnprotectData(
            ctypes.byref(origen), None, None, None, None, _FLAG_DPAPI_SIN_UI, ctypes.byref(destino)
        )
    else:
        ok = crypt32.CryptProtectData(
            ctypes.byref(origen), descripcion, None, None, None, _FLAG_DPAPI_SIN_UI, ctypes.byref(destino)
        )
    if not ok:
        accion = "recuperar" if descifrar else "proteger"
        raise ProteccionError(
            f"Windows no pudo {accion} el archivo de licencia con DPAPI de este usuario. "
            "Conserva DShalevData y usa el usuario y equipo originales."
        )
    try:
        return ctypes.string_at(destino.pbData, destino.cbData)
    finally:
        local_free = ctypes.windll.kernel32.LocalFree
        local_free.argtypes = [ctypes.c_void_p]
        local_free.restype = ctypes.c_void_p
        local_free(destino.pbData)


def _ruta_clave_prueba(ruta: Path) -> Path:
    return ruta.with_name(ruta.name + ".devkey")


def _leer_clave_prueba(ruta: Path, *, crear: bool) -> bytes:
    clave_ruta = _ruta_clave_prueba(ruta)
    if clave_ruta.exists():
        # Otra ejecución pudo crear la clave con O_EXCL y estar escribiéndola.
        for intento in range(_INTENTOS_CLAVE):
            guardada = clave_ruta.read_bytes()
            if len(guardada) == len(CABECERA_CLAVE_PRUEBA) + LONGITUD_CLAVE and guardada.startswith(CABECERA_CLAVE_PRUEBA):
                return guardada[len(CABECERA_CLAVE_PRUEBA):]
            if intento + 1 < _INTENTOS_CLAVE:
                time.sleep(_ESPERA_CLAVE)
        raise ProteccionError("La clave local de prueba está dañada o incompleta; no se regeneró.")
    if not crear:
        raise ProteccionError(
            "Falta la clave local de prueba de este archivo; no se recreó. "
            "Restaura la carpeta completa o vuelve a activar."
        )
    clave = secrets.token_bytes(LONGITUD_CLAVE)
    clave_ruta.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(clave_ruta, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        return _leer_clave_prueba(ruta, crear=False)
    try:
        with os.fdopen(descriptor, "wb") as salida:
            salida.write(CABECERA_CLAVE_PRUEBA + clave)
            salida.flush()
            os.fsync(salida.fileno())
    except OSError:
        try:
            clave_ruta.unlink()
        except OSError:
            pass
        raise ProteccionError("No se pudo guardar la clave local de prueba.") from None
    sincronizar_directorio(clave_ruta.parent)
    return clave


def sincronizar_directorio(directorio: Path) -> None:
    if not hasattr(os, "O_DIRECTORY"):
        return
    try:
        descriptor = os.open(directorio, os.O_RDONLY | os.O_DIRECTORY)
    except OSError:
        return
    try:
        os.fsync(descriptor)
    except OSError:
        pass
    finally:
        os.close(descriptor)


def sellar(datos: bytes, *, descripcion: str, ruta: Path, modo: str | None = None) -> bytes:
    """Devuelve el contenido protegido sin escribirlo (para crear con O_EXCL)."""
    modo = modo or modo_de_escritura()
    if not isinstance(datos, (bytes, bytearray)):
        raise ProteccionError("Solo se pueden proteger datos binarios.")
    if modo == MODO_DPAPI:
        if not es_windows():
            raise ProteccionError("No se puede escribir un archivo DPAPI fuera de Windows.")
        return CABECERA_DPAPI + _dpapi(bytes(datos), descripcion=descripcion, descifrar=False)
    if modo == MODO_PRUEBA:
        exigir_prueba_permitida()
        clave = _leer_clave_prueba(ruta, crear=True)
        nonce = secrets.token_bytes(LONGITUD_NONCE)
        sello = AESGCM(clave).encrypt(nonce, bytes(datos), descripcion.encode("utf-8"))
        return CABECERA_PRUEBA + nonce + sello
    raise ProteccionError(f"Modo de protección desconocido: {modo!r}.")


def _abrir(contenido: bytes, *, descripcion: str, ruta: Path) -> bytes:
    if contenido.startswith(CABECERA_DPAPI):
        if not es_windows():
            raise ProteccionError(
                "Este archivo se protegió con DPAPI en Windows y solo se puede abrir con el usuario y equipo originales."
            )
        return _dpapi(contenido[len(CABECERA_DPAPI):], descripcion=descripcion, descifrar=True)
    if contenido.startswith(CABECERA_PRUEBA):
        if es_windows():
            raise ProteccionError(
                "Este archivo usa la protección de prueba y no se acepta en Windows."
            )
        exigir_prueba_permitida()
        resto = contenido[len(CABECERA_PRUEBA):]
        if len(resto) <= LONGITUD_NONCE:
            raise ProteccionError("El archivo protegido está truncado.")
        clave = _leer_clave_prueba(ruta, crear=False)
        try:
            return AESGCM(clave).decrypt(resto[:LONGITUD_NONCE], resto[LONGITUD_NONCE:], descripcion.encode("utf-8"))
        except Exception as exc:
            raise ProteccionError("El archivo protegido no se pudo autenticar o está dañado.") from exc
    raise ProteccionError("El archivo no tiene un formato DShalev de protección reconocido.")


def escribir_atomico(ruta: Path, datos: bytes, *, modo: int = 0o600) -> None:
    """Escribe con archivo temporal, fsync y reemplazo; nunca deja un archivo a medias."""
    ruta.parent.mkdir(parents=True, exist_ok=True)
    temporal = ruta.with_name(f".{ruta.name}.{secrets.token_hex(8)}.tmp")
    try:
        descriptor = os.open(temporal, os.O_CREAT | os.O_EXCL | os.O_WRONLY, modo)
        with os.fdopen(descriptor, "wb") as salida:
            salida.write(datos)
            salida.flush()
            os.fsync(salida.fileno())
        os.replace(temporal, ruta)
        sincronizar_directorio(ruta.parent)
    except OSError:
        try:
            temporal.unlink()
        except OSError:
            pass
        raise ProteccionError("No se pudo escribir el archivo de licencia de forma segura.") from None


def escribir_protegido(ruta: Path, datos: bytes, *, descripcion: str, modo: str | None = None) -> None:
    escribir_atomico(ruta, sellar(datos, descripcion=descripcion, ruta=ruta, modo=modo))


def leer_protegido(ruta: Path, *, descripcion: str) -> bytes:
    try:
        contenido = ruta.read_bytes()
    except FileNotFoundError:
        raise
    except OSError:
        raise ProteccionError("No se pudo leer el archivo de licencia.") from None
    return _abrir(contenido, descripcion=descripcion, ruta=ruta)
