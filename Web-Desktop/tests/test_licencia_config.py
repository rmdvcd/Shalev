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


def test_el_build_aprobado_tiene_endpoint_y_claves_de_produccion():
    actual = configuracion()
    assert actual.endpoint_redeem == "https://gl-redeem.rmdvcd.workers.dev/v1/redeem"
    assert actual.endpoint_estado == "https://gl-redeem.rmdvcd.workers.dev/v1/status"
    assert actual.app_id == "DSHALEV" and "gl-redeem-v1" in actual.kids_spki
    assert actual.comercial_habilitada is True
    assert actual.motivo_deshabilitada() == ""

    sin_conf = definir(app_id="", endpoint_redeem="", endpoint_estado="", kids_firma={})
    assert sin_conf.comercial_habilitada is False
    motivo = sin_conf.motivo_deshabilitada()
    assert "deshabilitada" in motivo
    assert "producción" in motivo
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
    assert actual.endpoint_redeem == config_licencia.ENDPOINT_REDEEM
    assert actual.endpoint_estado == config_licencia.ENDPOINT_ESTADO
    assert actual.app_id == config_licencia.APP_ID
    assert "falso" not in actual.kids_spki
    assert actual.endpoint_redeem != "https://atacante.ejemplo.test/v1/redeem"


def test_el_codigo_no_contiene_claves_privadas_ni_endpoints_de_prueba():
    fuente = Path(config_licencia.__file__).read_text(encoding="utf-8")
    assert "PRIVATE KEY" not in fuente
    assert "BEGIN EC" not in fuente
    assert "localhost" not in fuente
    assert "127.0.0.1" not in fuente
    # Valores de compilación aprobados de producción
    assert config_licencia.APP_ID == "DSHALEV"
    assert config_licencia.ENDPOINT_REDEEM == "https://gl-redeem.rmdvcd.workers.dev/v1/redeem"
    assert config_licencia.ENDPOINT_ESTADO == "https://gl-redeem.rmdvcd.workers.dev/v1/status"
    assert "gl-redeem-v1" in config_licencia.KIDS_FIRMA
    assert config_licencia.POLITICA_IDENTIDAD == "instalacion"


def test_configuracion_desde_directorio_con_canje():
    from dshalev.licencia.directorio import ClaveDirectorio, DirectorioConfianza
    from dshalev.licencia.config_licencia import configuracion_desde_directorio

    clave = ec.generate_private_key(ec.SECP256R1())
    spki = clave.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    c = ClaveDirectorio(
        kid="gl-sign-v1",
        uso="firma",
        spki_b64=base64.b64encode(spki).decode(),
        spki_der=spki,
        huella="sha256:0000:...",
        desde=1700000000,
        hasta=None,
        estado="vigente",
    )
    directorio = DirectorioConfianza(
        v=1,
        seq=1,
        emitida_en=1776470400,
        claves=[c],
        endpoint_base="https://worker-licencias.shalev.cu",
        app_ids={"MIPVENTAX-DESKTOP": "MIPVENTAX-DESKTOP"},
    )

    conf = configuracion_desde_directorio(directorio)
    assert conf.comercial_habilitada is True
    assert conf.app_id == "MIPVENTAX-DESKTOP"
    assert conf.endpoint_redeem == "https://worker-licencias.shalev.cu/v1/redeem"
    assert conf.endpoint_estado == "https://worker-licencias.shalev.cu/v1/status"
    assert conf.kid_autorizado("gl-sign-v1") == spki


def test_configuracion_desde_directorio_prioriza_claves_canje_y_dshalev():
    from dshalev.licencia.directorio import ClaveDirectorio, DirectorioConfianza
    from dshalev.licencia.config_licencia import configuracion_desde_directorio, BASE_PUBLICA_AUTORIZADA

    clave_canje = ec.generate_private_key(ec.SECP256R1())
    spki_canje = clave_canje.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    clave_firma = ec.generate_private_key(ec.SECP256R1())
    spki_firma = clave_firma.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )

    c_canje = ClaveDirectorio(
        kid="gl-redeem-v1",
        uso="canje",
        spki_b64=base64.b64encode(spki_canje).decode(),
        spki_der=spki_canje,
        huella="sha256:1111:...",
        desde=1700000000,
        hasta=None,
        estado="vigente",
    )
    c_firma = ClaveDirectorio(
        kid="gl-sign-v2",
        uso="firma",
        spki_b64=base64.b64encode(spki_firma).decode(),
        spki_der=spki_firma,
        huella="sha256:2222:...",
        desde=1700000000,
        hasta=None,
        estado="vigente",
    )
    directorio = DirectorioConfianza(
        v=1,
        seq=1,
        emitida_en=1776470400,
        claves=[c_canje, c_firma],
        endpoint_base="",
        app_ids={"DSHALEV": "DSHALEV"},
    )

    conf = configuracion_desde_directorio(directorio)
    assert conf.comercial_habilitada is True
    assert conf.app_id == "DSHALEV"
    assert conf.endpoint_redeem == f"{BASE_PUBLICA_AUTORIZADA}/v1/redeem"
    assert conf.endpoint_estado == f"{BASE_PUBLICA_AUTORIZADA}/v1/status"
    assert conf.kid_autorizado("gl-redeem-v1") == spki_canje
    assert conf.kid_autorizado("gl-sign-v2") is None


def test_configuracion_desde_directorio_sin_endpoint_falla_cerrado():
    from dshalev.licencia.directorio import ClaveDirectorio, DirectorioConfianza
    from dshalev.licencia.config_licencia import configuracion_desde_directorio

    directorio_sin_endpoint = DirectorioConfianza(
        v=1,
        seq=1,
        emitida_en=1776470400,
        claves=[],
        endpoint_base="",
    )
    conf = configuracion_desde_directorio(directorio_sin_endpoint)
    assert conf.comercial_habilitada is False
    assert "Falta" in conf.motivo_deshabilitada()

