from __future__ import annotations

import base64
import json
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, ed25519

from dshalev.config import load_config
from dshalev.licencia import config_licencia
from dshalev.licencia.config_licencia import (
    ConfiguracionLicenciaError,
    configuracion,
    definir,
    motivo_activacion_deshabilitada,
)


def spki_b64(clave) -> str:
    return base64.b64encode(clave.public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )).decode()


def test_el_build_no_tiene_endpoint_ni_claves_de_produccion():
    actual = configuracion()
    assert actual.endpoint_redeem == "" and actual.endpoint_estado == ""
    assert actual.app_id == "" and dict(actual.kids_spki) == {}
    assert actual.comercial_habilitada is False
    motivo = motivo_activacion_deshabilitada()
    assert "deshabilitada" in motivo
    assert "producción" in motivo
    # Nunca se declara habilitada con material de prueba.
    assert "Falta: appId, endpoint de canje, endpoint de estado, claves públicas (kid)." in motivo


def test_un_build_completo_y_valido_habilita_la_activacion_comercial():
    clave = ec.generate_private_key(ec.SECP256R1())
    config = definir(
        app_id="dshalev",
        endpoint_redeem="https://licencias.ejemplo.test/v1/redeem",
        endpoint_estado="https://licencias.ejemplo.test/v1/status",
        kids_firma={"gl-sign-v2": spki_b64(clave.public_key())},
    )
    assert config.comercial_habilitada is True
    assert config.motivo_deshabilitada() == ""
    assert config.kid_autorizado("gl-sign-v2") == clave.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    assert config.kid_autorizado("otro-kid") is None


def test_la_activacion_exige_todas_las_piezas():
    clave = ec.generate_private_key(ec.SECP256R1())
    kid = {"gl-sign-v2": spki_b64(clave.public_key())}
    sin_endpoint = definir(app_id="dshalev", endpoint_redeem="", endpoint_estado="https://l.test/v1/status", kids_firma=kid)
    sin_kid = definir(app_id="dshalev", endpoint_redeem="https://l.test/v1/redeem", endpoint_estado="https://l.test/v1/status")
    assert sin_endpoint.comercial_habilitada is False
    assert sin_kid.comercial_habilitada is False
    assert "endpoint de canje" in sin_endpoint.motivo_deshabilitada()
    assert "claves públicas" in sin_kid.motivo_deshabilitada()


@pytest.mark.parametrize("endpoint", [
    "http://licencias.ejemplo.test/v1/redeem",
    "https://usuario:clave@licencias.ejemplo.test/v1/redeem",
    "https://licencias.ejemplo.test/v1/redeem?destino=otro",
    "https://licencias.ejemplo.test/v1/redeem#fragmento",
    "https://licencias.ejemplo.test:99999/v1/redeem",
    "licencias.ejemplo.test/v1/redeem",
    "https://licencias ejemplo.test/v1/redeem",
])
def test_endpoint_inseguro_se_rechaza(endpoint: str):
    with pytest.raises(ConfiguracionLicenciaError):
        definir(endpoint_redeem=endpoint)


@pytest.mark.parametrize("kid", ["", "con espacio", "a" * 65, "ñandú", "../clave"])
def test_kid_invalido_se_rechaza(kid: str):
    clave = ec.generate_private_key(ec.SECP256R1())
    with pytest.raises(ConfiguracionLicenciaError):
        definir(kids_firma={kid: spki_b64(clave.public_key())})


def test_clave_que_no_es_p256_se_rechaza():
    ed = ed25519.Ed25519PrivateKey.generate()
    spki = base64.b64encode(ed.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )).decode()
    with pytest.raises(ConfiguracionLicenciaError, match="P-256"):
        definir(kids_firma={"gl-sign-v2": spki})


@pytest.mark.parametrize("valor", ["no-es-base64!!", base64.b64encode(b"\x30\x00").decode()])
def test_spki_invalida_se_rechaza(valor: str):
    with pytest.raises(ConfiguracionLicenciaError):
        definir(kids_firma={"gl-sign-v2": valor})


def test_app_id_invalido_se_rechaza():
    # El contrato admite mayúsculas y minúsculas; lo que no admite son espacios,
    # guiones iniciales ni una sola letra.
    with pytest.raises(ConfiguracionLicenciaError):
        definir(app_id="con espacio")
    with pytest.raises(ConfiguracionLicenciaError):
        definir(app_id="-empieza")
    with pytest.raises(ConfiguracionLicenciaError):
        definir(app_id="a")


def test_la_configuracion_no_se_puede_cambiar_desde_ajustes_ni_desde_un_respaldo(tmp_path: Path):
    raiz = tmp_path / "DShalev"
    datos = raiz / "DShalevData"
    datos.mkdir(parents=True)
    (datos / "portable-settings.json").write_text(json.dumps({
        "secret_key": "clave-de-sesion-de-prueba-0000000000000000",
        "network_mode": "local",
        "licencia_endpoint": "https://atacante.ejemplo.test/v1/redeem",
        "licencia_kids": {"falso": spki_b64(ec.generate_private_key(ec.SECP256R1()).public_key())},
        "licencia_app_id": "otra-app",
    }), encoding="utf-8")
    cargada = load_config(raiz)
    assert cargada.database_path.parent == datos
    actual = configuracion()
    assert actual.endpoint_redeem == "" and actual.endpoint_estado == ""
    assert actual.app_id == "" and dict(actual.kids_spki) == {}


def test_el_codigo_no_contiene_claves_privadas_ni_endpoints_de_prueba():
    fuente = Path(config_licencia.__file__).read_text(encoding="utf-8")
    assert "PRIVATE KEY" not in fuente
    assert "BEGIN EC" not in fuente
    assert "localhost" not in fuente
    assert "127.0.0.1" not in fuente
    # Los valores de compilación siguen vacíos: sin endpoint aprobado, no hay activación.
    assert config_licencia.APP_ID == ""
    assert config_licencia.ENDPOINT_REDEEM == ""
    assert config_licencia.ENDPOINT_ESTADO == ""
    assert dict(config_licencia.KIDS_FIRMA) == {}
    assert config_licencia.POLITICA_IDENTIDAD == "instalacion"
