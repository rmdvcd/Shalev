from __future__ import annotations

import base64
import json
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

# Asegurar que la raíz del repositorio esté en sys.path para tools.licencia
_RAIZ_REPO = Path(__file__).resolve().parent.parent.parent
if str(_RAIZ_REPO) not in sys.path:
    sys.path.insert(0, str(_RAIZ_REPO))

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from dshalev.licencia import (
    AVISO_FRESCURA,
    AlmacenLicencia,
    AnclaInstalacion,
    CamposLicenciaCorta,
    Identidad,
    LicenciaCortaError,
    activar_spvi2,
    calcular_precio,
    calcular_precio_ampliacion,
    cargar_o_crear_identidad,
    derivar_device_id,
    derivar_device_pub,
    descifrar_y_verificar,
    extraer_spvi2,
    generar_solicitud,
    normalizar_gsm7,
    ruta_identidad,
)
from tools.licencia import probar_gl


@pytest.fixture(autouse=True)
def _permitir_identidad_dev(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DSHALEV_ALLOW_DEV_IDENTITY", "1")


def _identidad_dorada(tmp_path: Path) -> Identidad:
    """Dispositivo de prueba del vector dorado."""
    clave = serialization.load_der_private_key(
        base64.b64decode(probar_gl.DEV_PRIV_PKCS8), password=None
    )
    return Identidad(tmp_path / "identidad.bin", clave)


def test_vector_dorado_spvi2(tmp_path: Path):
    """Verifica el vector dorado oficial reproducido byte a byte."""
    identidad = _identidad_dorada(tmp_path)
    device_id = probar_gl.DEVICE_ID

    codigo_dorado = (
        "SPVI2:A8Ntuq3AH5-S0FVM2Itwox5ElM8JLkdH1v90_SJsF6KzCtzdFYKcGWYWoXp1RC0191zsFibEJjMpJmCWwJWjkLnY"
        "7KDTfPDFw4lrwTr5VFo_db5f84NP9QfStCUS-vaDoat7CYxydd5YtJCVrp6PY-4BtppRmWUQ6ToKHFlq0l1Fw5MjaKaBU4rE"
        "kjJC1f_wpJoBjH6u850WQv87oA"
    )

    binario, n_chars, err = extraer_spvi2(codigo_dorado)
    assert binario is not None
    assert n_chars == 210
    assert err == ""

    # Clave de firma de prueba del vector
    sign_key_prueba = serialization.load_der_public_key(
        base64.b64decode(probar_gl.GL_SIGN_SPKIS[-1])
    ).public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

    campos = descifrar_y_verificar(
        binario,
        identidad,
        device_id=device_id,
        claves_firma_adicionales=[sign_key_prueba],
    )

    assert campos.id == "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    assert campos.tipo == "MENSUAL"
    assert campos.estado == "ACTIVA"
    assert campos.secundarias == 2
    assert campos.emitida_en == datetime(2026, 10, 3, 20, 0, tzinfo=timezone.utc)
    assert campos.vence_en == datetime(2026, 11, 2, 20, 0, tzinfo=timezone.utc)


def test_solicitud_100_por_ciento_gsm7_y_conserva_nombres_en_payload(tmp_path: Path):
    """C1: el texto legible no contiene caracteres fuera de GSM-7 aunque los nombres tengan tildes."""
    identidad = _identidad_dorada(tmp_path)
    nombre = "María René"
    apellidos = "González Ñúñez"
    ci = "91040922502"
    tel = "+5351815604"

    mensaje, codigo, payload = generar_solicitud(
        identidad,
        nombre=nombre,
        apellidos=apellidos,
        ci=ci,
        telefono=tel,
        tipo="MENSUAL",
        secundarias=2,
    )

    assert codigo.startswith("SPVIR1:")
    # El payload JSON conserva los nombres exactos con tildes y eñes
    assert payload["nombre"] == nombre
    assert payload["apellidos"] == apellidos
    assert payload["ci"] == ci
    assert payload["deviceId"] == derivar_device_id(identidad)
    assert payload["devicePub"] == derivar_device_pub(identidad)

    # El texto legible normaliza a GSM-7
    assert "Maria Rene" in mensaje
    assert "Gonzalez Nunez" in mensaje
    assert AVISO_FRESCURA in mensaje
    assert "enviala" in mensaje
    assert "envíala" not in mensaje
    assert "Carne de identidad:" in mensaje
    assert "Carné" not in mensaje
    assert "Telefono:" in mensaje
    assert "Teléfono" not in mensaje

    # Verificación estricta de caracteres GSM-7 en la cabecera legible
    cabecera = mensaje.split("SPVIR1:")[0]
    # Caracteres no permitidos en GSM-7
    for c in cabecera:
        assert ord(c) < 128, f"Carácter no-ASCII en cabecera: {c!r}"
        assert c not in "|{}[]^~\\€", f"Carácter de escape/prohibido en cabecera: {c!r}"


def test_c3_errores_accionables_extraccion_spvi2():
    """C3: Distingue 'no hay licencia' de 'mensaje incompleto' con número de caracteres."""
    # Sin SPVI2:
    binario, n, msg = extraer_spvi2("Hola mundo sin codigo")
    assert binario is None
    assert n == 0
    assert "No se encontró una licencia en el texto pegado" in msg

    # SPVI2: incompleto (por ejemplo 50 caracteres)
    incompleto = "Licencia\n\nSPVI2:" + "A" * 50
    binario2, n2, msg2 = extraer_spvi2(incompleto)
    assert binario2 is None
    assert n2 == 50
    assert "Llegaron 50 de 210 caracteres; copia todas las partes del mensaje" in msg2


def test_roundtrip_solicitud_emision_y_activacion(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    """Flujo completo ida y vuelta con un GL simulado."""
    monkeypatch.setenv("DSHALEV_ALLOW_DEV_IDENTITY", "1")
    ruta_id = ruta_identidad(tmp_path)
    identidad = cargar_o_crear_identidad(ruta_id)
    ancla = AnclaInstalacion(tmp_path / "ancla.bin", huella=identidad.huella)
    almacen = AlmacenLicencia(
        tmp_path / "DShalevData" / "licencia" / "licencia.bin",
        huella=identidad.huella,
        ancla=ancla,
        app_id="DSHALEV",
    )

    # Claves del emisor GL simulado
    gl_ecdh_priv = ec.generate_private_key(ec.SECP256R1())
    gl_sign_priv = ec.generate_private_key(ec.SECP256R1())
    gl_sign_spki = gl_sign_priv.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )

    mensaje_sol, codigo_sol, payload = generar_solicitud(
        identidad,
        nombre="Carlos",
        apellidos="Pérez",
        ci="95051212345",
        telefono="52345678",
        tipo="ANUAL",
        secundarias=3,
        gl_ecdh_pub=gl_ecdh_priv.public_key(),
    )

    # GL abre la solicitud
    sol_datos = probar_gl.abrir_solicitud(codigo_sol, gl_ecdh_priv)
    req = sol_datos if isinstance(sol_datos, dict) else json.loads(sol_datos)
    assert req["tipo"] == "ANUAL"
    assert req["secundarias"] == 3
    assert req["deviceId"] == derivar_device_id(identidad)

    # GL emite licencia corta
    ahora = datetime.now(timezone.utc).replace(microsecond=0)
    vence = ahora + timedelta(days=365)
    lic_id = str(uuid.uuid4())
    dev_pub_comprimida = probar_gl.unb64url(req["devicePub"])
    huella_dev = probar_gl.huella(req["deviceId"], dev_pub_comprimida)
    cuerpo44 = probar_gl.cuerpo(lic_id, huella_dev, "ANUAL", "ACTIVA", 3, ahora, vence)
    spvi2_msg = probar_gl.emitir(cuerpo44, dev_pub_comprimida, gl_sign_priv)

    # Activar en DShalev
    res = activar_spvi2(
        spvi2_msg,
        identidad,
        almacen,
        claves_firma_adicionales=[gl_sign_spki],
    )

    assert res.resultado == "Accepted"
    assert res.mensaje == "Licencia activada"
    assert res.campos is not None
    assert res.campos.id == lic_id
    assert res.campos.tipo == "ANUAL"
    assert res.campos.secundarias == 3

    # Almacén conserva la licencia instalada
    obs = almacen.observar()
    assert obs.licencia is not None
    assert obs.licencia.documento.startswith("SPVI2:")
    assert obs.licencia.ultimo_estado == "ACTIVA"


def test_licencia_de_otro_dispositivo_se_rechaza(tmp_path: Path):
    """Licencia emitida para otro deviceId o con otra clave privada es rechazada."""
    identidad1 = _identidad_dorada(tmp_path / "inst1")
    # Cambiar identidad 2 generando clave nueva
    identidad2 = Identidad(tmp_path / "inst2" / "identidad.bin", ec.generate_private_key(ec.SECP256R1()))
    ancla2 = AnclaInstalacion(tmp_path / "inst2" / "ancla.bin", huella=identidad2.huella)
    almacen2 = AlmacenLicencia(
        tmp_path / "inst2" / "licencia.bin",
        huella=identidad2.huella,
        ancla=ancla2,
        app_id="DSHALEV",
    )

    gl_sign_priv = ec.generate_private_key(ec.SECP256R1())
    gl_sign_spki = gl_sign_priv.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )

    ahora = datetime.now(timezone.utc).replace(microsecond=0)
    vence = ahora + timedelta(days=30)
    lic_id = str(uuid.uuid4())

    # Emitir para identidad 1
    dev_pub1 = probar_gl.comprimir(identidad1.publica)
    huella1 = probar_gl.huella(derivar_device_id(identidad1), dev_pub1)
    cuerpo = probar_gl.cuerpo(lic_id, huella1, "MENSUAL", "ACTIVA", 0, ahora, vence)
    spvi2 = probar_gl.emitir(cuerpo, dev_pub1, gl_sign_priv)

    # Intentar activar en identidad 2
    res = activar_spvi2(
        spvi2,
        identidad2,
        almacen2,
        claves_firma_adicionales=[gl_sign_spki],
    )
    assert res.resultado == "Rejected"
    assert "no es válida para este dispositivo" in res.mensaje


def test_prevalencia_licencia_activa_absorbe_renovacion_menor(tmp_path: Path):
    """Prevalencia: una licencia anual vigente absorbe una mensual menor."""
    identidad = _identidad_dorada(tmp_path)
    ancla = AnclaInstalacion(tmp_path / "ancla.bin", huella=identidad.huella)
    almacen = AlmacenLicencia(
        tmp_path / "licencia.bin",
        huella=identidad.huella,
        ancla=ancla,
        app_id="DSHALEV",
    )
    gl_sign_priv = ec.generate_private_key(ec.SECP256R1())
    gl_sign_spki = gl_sign_priv.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )

    ahora = datetime.now(timezone.utc).replace(microsecond=0)
    dev_pub = probar_gl.comprimir(identidad.publica)
    dev_id = derivar_device_id(identidad)
    huella_dev = probar_gl.huella(dev_id, dev_pub)

    # 1. Instalar ANUAL que vence en 365 días
    vence_anual = ahora + timedelta(days=365)
    c1 = probar_gl.cuerpo(str(uuid.uuid4()), huella_dev, "ANUAL", "ACTIVA", 0, ahora, vence_anual)
    msg1 = probar_gl.emitir(c1, dev_pub, gl_sign_priv)
    res1 = activar_spvi2(msg1, identidad, almacen, claves_firma_adicionales=[gl_sign_spki])
    assert res1.resultado == "Accepted"

    # 2. Intentar activar MENSUAL que vence en 30 días
    vence_mensual = ahora + timedelta(days=30)
    c2 = probar_gl.cuerpo(str(uuid.uuid4()), huella_dev, "MENSUAL", "ACTIVA", 0, ahora, vence_mensual)
    msg2 = probar_gl.emitir(c2, dev_pub, gl_sign_priv)
    res2 = activar_spvi2(msg2, identidad, almacen, claves_firma_adicionales=[gl_sign_spki])
    assert res2.resultado == "Absorbed"
    assert "mayor cobertura" in res2.mensaje


def test_solicitud_recuperacion_y_ampliacion(tmp_path: Path):
    """Verifica mensajes específicos para recuperación y ampliación."""
    identidad = _identidad_dorada(tmp_path)
    id_anterior = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    # Recuperación
    msg_rec, cod_rec, pay_rec = generar_solicitud(
        identidad,
        nombre="Elena",
        apellidos="Díaz",
        ci="98010112345",
        telefono="53123456",
        tipo="MENSUAL",
        recupera=id_anterior,
    )
    assert "Recuperar licencia Shalev" in msg_rec
    assert f"Licencia anterior: {id_anterior}" in msg_rec
    assert pay_rec["recupera"] == id_anterior
    assert "Precio" not in msg_rec

    # Ampliación
    msg_amp, cod_amp, pay_amp = generar_solicitud(
        identidad,
        nombre="Elena",
        apellidos="Díaz",
        ci="98010112345",
        telefono="53123456",
        tipo="SEMESTRAL",
        secundarias=4,
        secundarias_previas=2,
        amplia=id_anterior,
    )
    assert "Ampliar secundarias Shalev" in msg_amp
    assert "Apps secundarias actuales: 2" in msg_amp
    assert "Secundarias adicionales: 2" in msg_amp
    assert "Apps secundarias: 4" in msg_amp
    assert f"Amplia: {id_anterior}" in msg_amp
    assert pay_amp["amplia"] == id_anterior
    # Semestral secundaria: 5,000 CUP * 2 = 10,000 CUP
    assert "10,000.00 CUP" in msg_amp


def test_d2_directorio_con_clave_comprometida_o_fuera_de_ventana_no_cae_a_fijas(tmp_path: Path):
    """D2: Si hay directorio válido, su respuesta (incluso vacía) manda; no revive claves fijas."""
    identidad = _identidad_dorada(tmp_path)
    gl_sign_priv = ec.derive_private_key(probar_gl.VECTOR_FIRMA_D, ec.SECP256R1())
    dev_pub = probar_gl.comprimir(identidad.publica)
    dev_id = derivar_device_id(identidad)
    huella_dev = probar_gl.huella(dev_id, dev_pub)

    ahora = datetime(2026, 11, 1, 12, 0, tzinfo=timezone.utc)
    vence = ahora + timedelta(days=30)
    cuerpo = probar_gl.cuerpo(str(uuid.uuid4()), huella_dev, "MENSUAL", "ACTIVA", 0, ahora, vence)
    spvi2_msg = probar_gl.emitir(cuerpo, dev_pub, gl_sign_priv)
    binario, _, _ = extraer_spvi2(spvi2_msg)

    # 1. Crear mock de directorio que marca la clave como comprometida
    class DirectorioMockComprometido:
        def sign_keys_for(self, emitida_en):
            return []  # Lista vacía porque la clave fue revocada / comprometida

    with pytest.raises(LicenciaCortaError, match="no es válida para este dispositivo"):
        descifrar_y_verificar(
            binario,
            identidad,
            device_id=dev_id,
            directorio=DirectorioMockComprometido(),
        )


