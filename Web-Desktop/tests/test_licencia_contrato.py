"""Interoperabilidad con el contrato GL y validación estricta de documentos."""

from __future__ import annotations

import base64
from datetime import datetime, timedelta, timezone

import pytest
from apoyo_licencia import (
    AHORA,
    APP_ID,
    KID,
    cargar_vector,
    clave_p256,
    contenido_licencia,
    crear_identidad,
    crear_subtoken,
    firmar_servicio,
    sobre_estado,
    sobre_licencia,
    spki_der,
)
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec

from dshalev.licencia import contrato, verificador
from dshalev.licencia.contrato import ContratoError
from dshalev.licencia.verificador import LicenciaInvalida


@pytest.fixture(scope="module")
def vector() -> dict:
    return cargar_vector()


def _claves_prueba(vector: dict) -> dict[str, bytes]:
    return {KID: base64.b64decode(vector["servicePublicKey"])}


# ── Bytes exactos del vector oficial ────────────────────────────────────────


@pytest.mark.parametrize(
    ("clave", "constructor", "documento"),
    [
        ("request", contrato.bytes_prueba_canje, lambda v: v["request"]),
        ("statusRequest", contrato.bytes_prueba_estado, lambda v: v["statusRequest"]),
        ("license", contrato.bytes_firma_licencia, lambda v: v["license"]["license"]),
        ("status", contrato.bytes_firma_estado, lambda v: v["status"]["status"]),
        (
            "subToken",
            lambda _d: contrato.analizar_subtoken(_d).bytes_firma(),
            lambda v: v["subToken"],
        ),
    ],
    ids=["canje", "estado-peticion", "licencia", "estado", "subtoken"],
)
def test_bytes_canonicos_del_vector(vector, clave, constructor, documento):
    esperado = vector["bytesBase64"][clave]
    assert base64.b64encode(constructor(documento(vector))).decode() == esperado


def test_pruebas_del_dispositivo_del_vector(vector):
    llave = base64.b64decode(vector["request"]["deviceKey"])
    verificador.verificar_firma(
        llave,
        contrato.bytes_prueba_canje(vector["request"]),
        base64.b64decode(vector["request"]["proof"]),
    )
    verificador.verificar_firma(
        llave,
        contrato.bytes_prueba_estado(vector["statusRequest"]),
        base64.b64decode(vector["statusRequest"]["proof"]),
    )


def test_licencia_y_estado_del_vector(vector):
    claves = _claves_prueba(vector)
    licencia = verificador.verificar_licencia(
        vector["license"],
        claves=claves,
        app_id=APP_ID,
        huella_local=vector["license"]["license"]["deviceThumbprint"],
        ahora=AHORA,
    )
    assert licencia.estado == "ACTIVA"
    assert licencia.contenido["secundarias"] == 2
    estado = verificador.verificar_estado(
        vector["status"],
        claves=claves,
        app_id=APP_ID,
        huella_local=licencia.contenido["deviceThumbprint"],
        licencia_actual=licencia.contenido,
        ahora=AHORA,
    )
    assert estado.estado == "ACTIVA"
    assert estado.comprobado_en == datetime(2026, 11, 11, 12, 0, tzinfo=timezone.utc)


def test_huellas_spki_del_vector(vector):
    assert contrato.huella_spki(base64.b64decode(vector["request"]["deviceKey"])) == (
        vector["license"]["license"]["deviceThumbprint"]
    )
    assert contrato.huella_spki(base64.b64decode(vector["secondaryPublicKey"])) == (
        vector["subToken"]["deviceThumbprint"]
    )
    assert base64.b64decode(vector["servicePublicKey"]).startswith(b"\x30\x59")


def test_subtoken_del_vector(vector):
    claves = _claves_prueba(vector)
    licencia = verificador.verificar_licencia(
        vector["license"],
        claves=claves,
        app_id=APP_ID,
        huella_local=vector["license"]["license"]["deviceThumbprint"],
        ahora=AHORA,
    )
    huella = contrato.huella_spki(base64.b64decode(vector["secondaryPublicKey"]))
    token = verificador.verificar_subtoken(
        vector["subToken"],
        licencia_primaria=licencia.contenido,
        huella_secundaria=huella,
        ahora=datetime(2026, 11, 12, tzinfo=timezone.utc),
    )
    assert (token.slot, token.allowed) == (1, 2)
    mutado = dict(vector["subToken"])
    mutado["slot"] = 2
    with pytest.raises(LicenciaInvalida):
        verificador.verificar_subtoken(
            mutado, licencia_primaria=licencia.contenido, huella_secundaria=huella
        )


# ── Normalización del código ────────────────────────────────────────────────


@pytest.mark.parametrize(
    ("entrada", "esperado"),
    [
        ("ABCD-EFGH-JKLM-NPQR", "ABCDEFGHJKLMNPQR"),
        ("abcd efgh jklm npqr", "ABCDEFGHJKLMNPQR"),
        ("  ABCD\tEFGH\nJKLM NPQR  ", "ABCDEFGHJKLMNPQR"),
        ("ABCDEFGHJKLMNPQR", "ABCDEFGHJKLMNPQR"),
    ],
    ids=["guiones", "minusculas", "espacios", "limpio"],
)
def test_normalizar_codigo(entrada, esperado):
    assert contrato.normalizar_codigo(entrada) == esperado
    assert contrato.formatear_codigo(esperado) == "ABCD-EFGH-JKLM-NPQR"


@pytest.mark.parametrize(
    "entrada",
    [
        "ABCD-EFGH-JKLM-NPQI",  # I no pertenece al alfabeto
        "ABCD-EFGH-JKLM-NPQO",  # O tampoco
        "ABCD-EFGH-JKLM-NPQ",  # 15 símbolos
        "ABCD-EFGH-JKLM-NPQRX",  # 17 símbolos
        "ABCD_EFGH_JKLM_NPQR",
        "X" * 65,
        "",
        None,
        1234,
    ],
    ids=["i", "o", "corto", "largo", "guion-bajo", "excedido", "vacio", "nulo", "numero"],
)
def test_codigos_malformados(entrada):
    with pytest.raises(ContratoError):
        contrato.normalizar_codigo(entrada)


# ── Verificación de licencias ───────────────────────────────────────────────


def _verificar(sobre, identidad, claves, *, ahora=AHORA):
    return verificador.verificar_licencia(
        sobre, claves=claves, app_id=APP_ID, huella_local=identidad.huella, ahora=ahora
    )


def test_licencia_valida_propia(tmp_path):
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    contenido = contenido_licencia(identidad)
    sobre = sobre_licencia(contenido, servicio)
    verificada = _verificar(sobre, identidad, {KID: spki_der(servicio)})
    assert verificada.estado == "ACTIVA"
    assert verificada.utilizable


@pytest.mark.parametrize(
    ("delta", "esperado"),
    [(-1, "ACTIVA"), (0, "VENCIDA"), (1, "VENCIDA")],
    ids=["un-dia-antes", "al-vencer", "un-dia-despues"],
)
def test_caducidad_se_calcula_con_el_reloj(tmp_path, delta, esperado):
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    contenido = contenido_licencia(identidad)
    sobre = sobre_licencia(contenido, servicio)
    ahora = AHORA + timedelta(days=30 + delta)
    verificada = _verificar(sobre, identidad, {KID: spki_der(servicio)}, ahora=ahora)
    assert verificada.estado == esperado


def test_perpetua_no_vence(tmp_path):
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    contenido = contenido_licencia(identidad, tipo="PERPETUA", estado="PERPETUA")
    sobre = sobre_licencia(contenido, servicio)
    verificada = _verificar(
        sobre, identidad, {KID: spki_der(servicio)}, ahora=AHORA + timedelta(days=3650)
    )
    assert verificada.estado == "PERPETUA"
    assert verificada.es_perpetua


def test_revocada_prevalece(tmp_path):
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    contenido = contenido_licencia(identidad, estado="REVOCADA")
    sobre = sobre_licencia(contenido, servicio)
    verificada = _verificar(sobre, identidad, {KID: spki_der(servicio)})
    assert verificada.estado == "REVOCADA"
    assert not verificada.utilizable


@pytest.mark.parametrize(
    "mutacion",
    [
        "app",
        "dispositivo",
        "sin-clave",
        "kid",
        "alg",
        "version",
        "firma",
        "campo-extra",
    ],
    ids=["app", "dispositivo", "sin-clave", "kid", "alg", "version", "firma", "extra"],
)
def test_sobre_de_licencia_mutado(tmp_path, mutacion):
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    sobre = sobre_licencia(contenido_licencia(identidad), servicio)
    claves = {KID: spki_der(servicio)}
    if mutacion == "app":
        sobre["license"]["appId"] = "OTRA"
    elif mutacion == "dispositivo":
        sobre["license"]["deviceThumbprint"] = "A" * 43
    elif mutacion == "sin-clave":
        sobre["license"].pop("devicePublicKey")
    elif mutacion == "kid":
        sobre["kid"] = "otro-kid"
    elif mutacion == "alg":
        sobre["alg"] = contrato.ALG_ESTADO
    elif mutacion == "version":
        sobre["v"] = 2
    elif mutacion == "firma":
        firma = bytearray(base64.b64decode(sobre["sig"]))
        firma[-1] ^= 0x01
        sobre["sig"] = base64.b64encode(bytes(firma)).decode()
    elif mutacion == "campo-extra":
        sobre["extra"] = True
    with pytest.raises(LicenciaInvalida):
        _verificar(sobre, identidad, claves)


def test_licencia_de_otro_equipo(tmp_path):
    identidad = crear_identidad(tmp_path)
    otro = crear_identidad(tmp_path, "otro.bin")
    servicio = clave_p256()
    sobre = sobre_licencia(contenido_licencia(otro), servicio)
    with pytest.raises(LicenciaInvalida) as excinfo:
        _verificar(sobre, identidad, {KID: spki_der(servicio)})
    assert excinfo.value.motivo == "dispositivo"


@pytest.mark.parametrize(
    ("campo", "valor"),
    [
        ("type", "BIENAL"),
        ("status", "SUSPENDIDA"),
        ("expiresAt", None),
        ("expiresAt", "2026-11-01T12:00:00.000Z"),
        ("issuedAt", "ayer"),
        ("secundarias", 11),
        ("secundarias", -1),
        ("secundarias", True),
        ("secundarias", 2.5),
        ("deviceThumbprint", "corto"),
        ("redemptionId", ""),
        ("licenseId", "x"),
        ("transferredFrom", 7),
    ],
    ids=[
        "tipo",
        "estado",
        "sin-vencimiento",
        "vence-antes",
        "emitida-invalida",
        "cupo-alto",
        "cupo-negativo",
        "cupo-booleano",
        "cupo-decimal",
        "huella",
        "canje",
        "licencia",
        "transferida",
    ],
)
def test_estructura_de_licencia_invalida(tmp_path, campo, valor):
    identidad = crear_identidad(tmp_path)
    contenido = contenido_licencia(identidad)
    contenido[campo] = valor
    with pytest.raises(ContratoError):
        contrato.validar_contenido_licencia(contenido)


def test_perpetua_con_vencimiento_es_incoherente(tmp_path):
    identidad = crear_identidad(tmp_path)
    contenido = contenido_licencia(identidad, tipo="PERPETUA", estado="PERPETUA")
    contenido["expiresAt"] = contenido_licencia(identidad)["expiresAt"]
    with pytest.raises(ContratoError):
        contrato.validar_contenido_licencia(contenido)


def test_clave_del_dispositivo_no_corresponde_a_la_huella(tmp_path):
    identidad = crear_identidad(tmp_path)
    contenido = contenido_licencia(identidad)
    contenido["devicePublicKey"] = contrato.base64_estandar(spki_der(clave_p256()))
    with pytest.raises(ContratoError):
        contrato.validar_contenido_licencia(contenido)


# ── Verificación del estado ─────────────────────────────────────────────────


def _estado(identidad, servicio, *, estado="ACTIVA", comprobado=AHORA):
    contenido = contenido_licencia(identidad)
    return contenido, sobre_estado(
        contenido, estado=estado, comprobado_en=comprobado, clave_servicio=servicio
    )


@pytest.mark.parametrize(
    "mutacion",
    ["licencia", "dispositivo", "app", "futuro", "firma", "version"],
    ids=["licencia", "dispositivo", "app", "futuro", "firma", "version"],
)
def test_estado_mutado(tmp_path, mutacion):
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    contenido, sobre = _estado(identidad, servicio)
    if mutacion == "licencia":
        sobre["status"]["licenseId"] = "44444444-4444-4444-8444-444444444444"
    elif mutacion == "dispositivo":
        sobre["status"]["deviceThumbprint"] = "B" * 43
    elif mutacion == "app":
        sobre["status"]["appId"] = "OTRA"
    elif mutacion == "futuro":
        sobre = sobre_estado(
            contenido,
            estado="ACTIVA",
            comprobado_en=AHORA + timedelta(minutes=6),
            clave_servicio=servicio,
        )
    elif mutacion == "firma":
        firma = bytearray(base64.b64decode(sobre["sig"]))
        firma[0] ^= 0x01
        sobre["sig"] = base64.b64encode(bytes(firma)).decode()
    elif mutacion == "version":
        sobre["v"] = 3
    with pytest.raises(LicenciaInvalida):
        verificador.verificar_estado(
            sobre,
            claves={KID: spki_der(servicio)},
            app_id=APP_ID,
            huella_local=identidad.huella,
            licencia_actual=contenido,
            ahora=AHORA,
        )


def test_estado_revocado(tmp_path):
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    contenido, sobre = _estado(identidad, servicio, estado="REVOCADA")
    estado = verificador.verificar_estado(
        sobre,
        claves={KID: spki_der(servicio)},
        app_id=APP_ID,
        huella_local=identidad.huella,
        licencia_actual=contenido,
        ahora=AHORA,
    )
    assert estado.estado == "REVOCADA"


def test_estado_antiguo_se_acepta_criptograficamente(tmp_path):
    """La firma es válida; descartar respuestas antiguas es política del almacén."""
    identidad = crear_identidad(tmp_path)
    servicio = clave_p256()
    contenido, sobre = _estado(
        identidad, servicio, comprobado=AHORA - timedelta(days=1)
    )
    estado = verificador.verificar_estado(
        sobre,
        claves={KID: spki_der(servicio)},
        app_id=APP_ID,
        huella_local=identidad.huella,
        licencia_actual=contenido,
        ahora=AHORA,
    )
    assert estado.comprobado_en == AHORA - timedelta(days=1)


# ── Sub-token (propuesta, no activada comercialmente) ───────────────────────


def test_subtoken_propio_y_mutaciones(tmp_path):
    clave_primaria = clave_p256()
    identidad = crear_identidad(tmp_path, clave=clave_primaria)
    secundaria = crear_identidad(tmp_path, "secundaria.bin")
    servicio = clave_p256()
    contenido = contenido_licencia(identidad, secundarias=2)
    sobre = sobre_licencia(contenido, servicio)
    primaria = _verificar(sobre, identidad, {KID: spki_der(servicio)}).contenido
    token = crear_subtoken(
        primaria, huella_secundaria=secundaria.huella, slot=1, clave_primaria=clave_primaria
    )
    verificado = verificador.verificar_subtoken(
        token, licencia_primaria=primaria, huella_secundaria=secundaria.huella, ahora=AHORA
    )
    assert verificado.slot == 1

    mutaciones = [
        ("slot", 2),
        ("allowed", 3),
        ("deviceThumbprint", "C" * 43),
        ("licenseId", "55555555-5555-4555-8555-555555555555"),
        ("expiresAt", "2027-01-01T00:00:00.000Z"),
        ("sig", base64.b64encode(b"firma").decode()),
        ("alg", "otro"),
        ("v", 2),
    ]
    for campo, valor in mutaciones:
        mutado = dict(token)
        mutado[campo] = valor
        with pytest.raises(LicenciaInvalida):
            verificador.verificar_subtoken(
                mutado, licencia_primaria=primaria, huella_secundaria=secundaria.huella
            )


def test_subtoken_no_amplia_cupo_ni_plazo(tmp_path):
    clave_primaria = clave_p256()
    identidad = crear_identidad(tmp_path, clave=clave_primaria)
    secundaria = crear_identidad(tmp_path, "secundaria.bin")
    clave_servicio = clave_p256()
    contenido = contenido_licencia(identidad, secundarias=2)
    primaria = _verificar(
        sobre_licencia(contenido, clave_servicio), identidad, {KID: spki_der(clave_servicio)}
    ).contenido
    token = crear_subtoken(
        primaria, huella_secundaria=secundaria.huella, slot=1, clave_primaria=clave_primaria
    )
    with pytest.raises(LicenciaInvalida):
        verificador.verificar_subtoken(
            token,
            licencia_primaria={**primaria, "secundarias": 3},
            huella_secundaria=secundaria.huella,
        )
    with pytest.raises(LicenciaInvalida):
        verificador.verificar_subtoken(
            token,
            licencia_primaria={**primaria, "expiresAt": "2027-01-01T00:00:00.000Z"},
            huella_secundaria=secundaria.huella,
        )
    with pytest.raises(LicenciaInvalida):
        verificador.verificar_subtoken(
            token,
            licencia_primaria={**primaria, "status": "REVOCADA"},
            huella_secundaria=secundaria.huella,
        )


def test_subtoken_vencido(tmp_path):
    clave_primaria = clave_p256()
    identidad = crear_identidad(tmp_path, clave=clave_primaria)
    secundaria = crear_identidad(tmp_path, "secundaria.bin")
    servicio = clave_p256()
    contenido = contenido_licencia(identidad, secundarias=1)
    primaria = _verificar(
        sobre_licencia(contenido, servicio), identidad, {KID: spki_der(servicio)}
    ).contenido
    token = crear_subtoken(
        primaria, huella_secundaria=secundaria.huella, slot=1, clave_primaria=clave_primaria
    )
    with pytest.raises(LicenciaInvalida):
        verificador.verificar_subtoken(
            token,
            licencia_primaria=primaria,
            huella_secundaria=secundaria.huella,
            ahora=AHORA + timedelta(days=31),
        )
    assert verificador.verificar_subtoken(
        token,
        licencia_primaria=primaria,
        huella_secundaria=secundaria.huella,
        ahora=AHORA + timedelta(days=31),
        permitir_vencido=True,
    )
