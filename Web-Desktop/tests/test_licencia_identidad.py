from __future__ import annotations

import base64
import hashlib
import threading
from pathlib import Path

import pytest
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

from dshalev.licencia import proteccion
from dshalev.licencia.identidad import (
    IdentidadError,
    cargar_identidad,
    cargar_o_crear_identidad,
    huella_de_spki,
    importar_publica,
    ruta_identidad,
)


@pytest.fixture(autouse=True)
def entorno_de_prueba(monkeypatch):
    """Fuera de Windows la identidad de prueba exige una variable explícita."""
    if not proteccion.es_windows():
        monkeypatch.setenv(proteccion.VARIABLE_PRUEBA, "1")
    yield


def crear(ruta: Path):
    return cargar_o_crear_identidad(ruta)


def test_identidad_p256_persistente_y_huella_de_spki(tmp_path: Path):
    ruta = tmp_path / "instalacion" / "identidad.bin"
    primera = crear(ruta)
    segunda = cargar_o_crear_identidad(ruta)

    assert primera.huella == segunda.huella
    assert primera.spki_der == segunda.spki_der
    assert len(primera.spki_der) > 0
    assert primera.huella == huella_de_spki(primera.spki_der)
    assert primera.huella == base64.urlsafe_b64encode(hashlib.sha256(primera.spki_der).digest()).decode().rstrip("=")
    assert len(primera.huella) == 43 and "=" not in primera.huella and "+" not in primera.huella and "/" not in primera.huella

    publica = importar_publica(primera.spki_der)
    assert publica.curve.name == "secp256r1"
    assert isinstance(publica, ec.EllipticCurvePublicKey)

    # El archivo sellado no contiene la clave privada en claro ni PEM.
    contenido = ruta.read_bytes()
    assert contenido.startswith((proteccion.CABECERA_DPAPI, proteccion.CABECERA_PRUEBA))
    assert b"PRIVATE KEY" not in contenido
    assert b"-----BEGIN" not in contenido
    if not proteccion.es_windows():
        assert ruta.stat().st_mode & 0o077 == 0


def test_la_firma_es_ecdsa_der_sha256_y_se_verifica(tmp_path: Path):
    identidad = crear(tmp_path / "identidad.bin")
    mensaje = b"GL-REDEEM-TRANSCRIPT-1\x00\x00\x00\x0bprueba\x00\x00\x00\x04"
    firma = identidad.firmar(mensaje)

    r, s = decode_dss_signature(firma)
    assert 0 < r < 2**256 and 0 < s < 2**256
    importar_publica(identidad.spki_der).verify(firma, mensaje, ec.ECDSA(hashes.SHA256()))

    with pytest.raises(InvalidSignature):
        importar_publica(identidad.spki_der).verify(firma, mensaje + b"x", ec.ECDSA(hashes.SHA256()))


def test_no_se_regenera_la_identidad_si_el_archivo_esta_danado(tmp_path: Path):
    ruta = tmp_path / "identidad.bin"
    original = crear(ruta)
    ruta.write_bytes(b"contenido que no es una identidad DShalev")
    danado = ruta.read_bytes()

    with pytest.raises(IdentidadError, match="no se creó otra|No se pudo abrir"):
        cargar_identidad(ruta)
    with pytest.raises(IdentidadError):
        cargar_o_crear_identidad(ruta)
    assert ruta.read_bytes() == danado, "el archivo dañado no debe reemplazarse en silencio"
    assert original.huella


def test_no_se_reemplaza_un_archivo_existente(tmp_path: Path):
    ruta = tmp_path / "identidad.bin"
    crear(ruta)
    antes = ruta.read_bytes()
    identidad = cargar_o_crear_identidad(ruta)
    assert ruta.read_bytes() == antes
    assert cargar_identidad(ruta).huella == identidad.huella


def test_creaciones_concurrentes_comparten_una_sola_identidad(tmp_path: Path):
    ruta = tmp_path / "identidad.bin"
    resultados: list[str] = []
    errores: list[BaseException] = []
    barrera = threading.Barrier(8)

    def trabajo() -> None:
        try:
            barrera.wait(timeout=5)
            resultados.append(crear(ruta).huella)
        except BaseException as exc:  # noqa: BLE001 - se reenvía al hilo principal
            errores.append(exc)

    hilos = [threading.Thread(target=trabajo) for _ in range(8)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join(timeout=20)

    assert not errores, errores
    assert len(set(resultados)) == 1
    assert cargar_identidad(ruta).huella == resultados[0]


@pytest.mark.skipif(proteccion.es_windows(), reason="la variante de prueba no se usa en Windows")
def test_sin_permiso_explicito_no_se_crea_identidad_de_prueba(tmp_path: Path, monkeypatch):
    monkeypatch.delenv(proteccion.VARIABLE_PRUEBA, raising=False)
    ruta = tmp_path / "identidad.bin"
    with pytest.raises(IdentidadError, match="DSHALEV_ALLOW_DEV_IDENTITY"):
        crear(ruta)
    assert not ruta.exists()


def test_identidad_de_prueba_no_se_acepta_en_windows(tmp_path: Path, monkeypatch):
    ruta = tmp_path / "identidad.bin"
    monkeypatch.setattr(proteccion, "es_windows", lambda: True)
    monkeypatch.setattr(proteccion, "modo_de_escritura", lambda: proteccion.MODO_PRUEBA)
    with pytest.raises(IdentidadError, match="Windows"):
        crear(ruta)
    assert not ruta.exists()


@pytest.mark.skipif(proteccion.es_windows(), reason="DPAPI solo existe en Windows")
def test_un_archivo_dpapi_no_se_abre_fuera_de_windows(tmp_path: Path):
    ruta = tmp_path / "identidad.bin"
    ruta.write_bytes(proteccion.CABECERA_DPAPI + b"\x00" * 32)
    with pytest.raises(IdentidadError, match="DPAPI"):
        cargar_identidad(ruta)
    assert ruta.read_bytes() == proteccion.CABECERA_DPAPI + b"\x00" * 32


def test_ruta_de_identidad_es_por_instalacion_y_no_por_local(tmp_path: Path):
    ruta = ruta_identidad(tmp_path)
    assert ruta == tmp_path / "DShalevData" / "licencia" / "identidad.bin"
    assert "locales" not in ruta.parts


def test_la_clave_privada_no_se_expone_en_el_repr(tmp_path: Path):
    identidad = crear(tmp_path / "identidad.bin")
    texto = repr(identidad)
    assert texto == f"Identidad(ruta={str(identidad.ruta)!r}, huella={identidad.huella!r})"
    assert not hasattr(identidad, "clave_privada")
    assert not hasattr(identidad, "privada")


def test_la_identidad_de_otro_archivo_es_otra_huella(tmp_path: Path):
    una = crear(tmp_path / "a" / "identidad.bin")
    otra = crear(tmp_path / "b" / "identidad.bin")
    assert una.huella != otra.huella
    assert una.spki_b64url != otra.spki_b64url
    assert base64.urlsafe_b64decode(una.spki_b64url + "=" * (-len(una.spki_b64url) % 4)) == una.spki_der
