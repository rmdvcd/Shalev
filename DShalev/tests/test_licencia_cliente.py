"""Cliente de canje/estado: contrato, idempotencia, red, reloj y revocación."""

from __future__ import annotations

import base64
import json
import ssl
import threading
from datetime import datetime, timedelta, timezone

import pytest
from apoyo_licencia import (
    AHORA,
    APP_ID,
    KID,
    clave_p256,
    contenido_licencia,
    crear_almacen,
    crear_identidad,
    sobre_estado,
    sobre_licencia,
    spki_der,
)
from cryptography.hazmat.primitives import hashes

from dshalev.licencia import contrato, proteccion, verificador
from dshalev.licencia.almacen import AlmacenError
from dshalev.licencia.cliente_gl import ClienteGL, ErrorGL, RespuestaHTTP, TransporteUrllib
from dshalev.licencia.config_licencia import configuracion, definir


@pytest.fixture(autouse=True)
def entorno_de_prueba(monkeypatch):
    if proteccion.es_windows():
        pytest.skip("Las pruebas de identidad no Windows no aplican en Windows.")
    monkeypatch.setenv(proteccion.VARIABLE_PRUEBA, "1")


class Reloj:
    def __init__(self, momento: datetime = AHORA) -> None:
        self.momento = momento

    def __call__(self) -> datetime:
        return self.momento


class TransporteFalso:
    def __init__(self, *respuestas) -> None:
        self.respuestas = list(respuestas)
        self.peticiones: list[tuple[str, dict]] = []

    def enviar(self, url, cuerpo, *, tiempo_espera, contexto_ssl):
        self.peticiones.append((url, json.loads(cuerpo)))
        if not self.respuestas:
            raise AssertionError("Falta una respuesta preparada.")
        respuesta = self.respuestas.pop(0)
        if isinstance(respuesta, BaseException):
            raise respuesta
        return respuesta


def _config(servicio, *, redeem=None, estado=None):
    return definir(
        app_id=APP_ID,
        endpoint_redeem=redeem or "https://licencias.ejemplo.test/v1/redeem",
        endpoint_estado=estado or "https://licencias.ejemplo.test/v1/status",
        kids_firma={KID: base64.b64encode(spki_der(servicio)).decode()},
    )


def _ok_licencia(sobre) -> RespuestaHTTP:
    return RespuestaHTTP(200, json.dumps({"v": 1, "ok": True, "license": sobre, "restored": False}).encode())


def _ok_estado(sobre) -> RespuestaHTTP:
    return RespuestaHTTP(200, json.dumps({"v": 1, "ok": True, "status": sobre}).encode())


@pytest.fixture()
def escenario(tmp_path):
    clave_primaria = clave_p256()
    identidad = crear_identidad(tmp_path, clave=clave_primaria)
    servicio = clave_p256()
    reloj = Reloj()
    almacen = crear_almacen(tmp_path, identidad, ahora=reloj)
    contenido = contenido_licencia(identidad)
    return {
        "clave": clave_primaria,
        "identidad": identidad,
        "servicio": servicio,
        "almacen": almacen,
        "contenido": contenido,
        "reloj": reloj,
        "sobre": sobre_licencia(contenido, servicio),
        "claves": {KID: spki_der(servicio)},
    }


def _cliente(escenario, transporte, *, reloj=None):
    return ClienteGL(
        configuracion=_config(escenario["servicio"]),
        identidad=escenario["identidad"],
        almacen=escenario["almacen"],
        ahora=reloj or escenario["reloj"],
        transporte=transporte,
    )


# ── Canje ───────────────────────────────────────────────────────────────────


def test_canje_guarda_solo_tras_verificar(escenario):
    transporte = TransporteFalso(_ok_licencia(escenario["sobre"]))
    resultado = _cliente(escenario, transporte).canjear("abcd efgh jklm npqr")
    assert resultado.licencia.estado == "ACTIVA"
    assert not resultado.restaurada

    url, peticion = transporte.peticiones[0]
    assert url == "https://licencias.ejemplo.test/v1/redeem"
    assert peticion["v"] == 1
    assert peticion["appId"] == APP_ID
    assert peticion["code"] == "ABCDEFGHJKLMNPQR"
    assert peticion["deviceKey"] == contrato.base64_estandar(escenario["identidad"].spki_der)
    assert peticion["requestedAt"].endswith("Z")
    assert len(base64.b64decode(peticion["nonce"])) == 16
    verificador.verificar_firma(
        escenario["identidad"].spki_der,
        contrato.bytes_prueba_canje(peticion),
        base64.b64decode(peticion["proof"]),
    )

    guardada = escenario["almacen"].observar().licencia
    assert guardada is not None
    assert json.loads(guardada.documento)["license"]["licenseId"] == escenario["contenido"]["licenseId"]
    assert guardada.ultimo_estado == "ACTIVA"


def test_canje_codigo_invalido_no_contacta(escenario):
    transporte = TransporteFalso()
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, transporte).canjear("1234")
    assert excinfo.value.codigo == "INVALID_CODE"
    assert not transporte.peticiones


def test_respuesta_perdida_reintenta_la_misma_solicitud(escenario):
    transporte = TransporteFalso(ErrorGL("NETWORK"), _ok_licencia(escenario["sobre"]))
    cliente = _cliente(escenario, transporte)
    with pytest.raises(ErrorGL) as excinfo:
        cliente.canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "NETWORK"
    assert escenario["almacen"].observar().licencia is None

    resultado = cliente.canjear("ABCD-EFGH-JKLM-NPQR")
    assert resultado.licencia.utilizable
    primera = transporte.peticiones[0][1]
    segunda = transporte.peticiones[1][1]
    assert primera["requestId"] == segunda["requestId"]
    assert primera["nonce"] == segunda["nonce"]
    assert primera["requestedAt"] == segunda["requestedAt"]
    assert escenario["almacen"].observar().licencia is not None


def test_error_del_servicio_no_guarda_ni_conserva_pendiente(escenario):
    error = RespuestaHTTP(
        404,
        json.dumps({"v": 1, "ok": False, "error": {"code": "CODE_UNAVAILABLE"}}).encode(),
    )
    transporte = TransporteFalso(error, _ok_licencia(escenario["sobre"]))
    cliente = _cliente(escenario, transporte)
    with pytest.raises(ErrorGL) as excinfo:
        cliente.canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "CODE_UNAVAILABLE"
    assert escenario["almacen"].observar().licencia is None

    cliente.canjear("ABCD-EFGH-JKLM-NPQR")
    assert transporte.peticiones[0][1]["requestId"] != transporte.peticiones[1][1]["requestId"]


@pytest.mark.parametrize(
    ("mutacion", "codigo"),
    [
        ("firma", "INVALID_LICENSE_SIGNATURE"),
        ("app", "LICENSE_WRONG_APP"),
        ("dispositivo", "LICENSE_WRONG_DEVICE"),
        ("kid", "UNKNOWN_KID"),
    ],
    ids=["firma", "app", "dispositivo", "kid"],
)
def test_respuesta_invalida_no_guarda(escenario, tmp_path, mutacion, codigo):
    sobre = json.loads(json.dumps(escenario["sobre"]))
    if mutacion == "firma":
        firma = bytearray(base64.b64decode(sobre["sig"]))
        firma[-1] ^= 0x01
        sobre["sig"] = base64.b64encode(bytes(firma)).decode()
    elif mutacion == "app":
        contenido = dict(sobre["license"])
        contenido["appId"] = "OTRA"
        sobre = sobre_licencia(contenido, escenario["servicio"])
    elif mutacion == "dispositivo":
        ajena = crear_identidad(tmp_path, "ajena.bin")
        sobre = sobre_licencia(contenido_licencia(ajena), escenario["servicio"])
    elif mutacion == "kid":
        sobre["kid"] = "desconocido"
    transporte = TransporteFalso(_ok_licencia(sobre))
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, transporte).canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == codigo
    assert escenario["almacen"].observar().licencia is None


def test_licencia_vencida_no_se_guarda(escenario):
    contenido = contenido_licencia(
        escenario["identidad"],
        emitida=AHORA - timedelta(days=40),
        vence=AHORA - timedelta(days=1),
    )
    transporte = TransporteFalso(_ok_licencia(sobre_licencia(contenido, escenario["servicio"])))
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, transporte).canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "LICENSE_EXPIRED"
    assert escenario["almacen"].observar().licencia is None


def test_licencia_revocada_no_se_guarda(escenario):
    contenido = contenido_licencia(escenario["identidad"], estado="REVOCADA")
    transporte = TransporteFalso(_ok_licencia(sobre_licencia(contenido, escenario["servicio"])))
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, transporte).canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "LICENSE_REVOKED"
    assert escenario["almacen"].observar().licencia is None


def test_redireccion_y_cuerpo_gigante_se_rechazan(escenario):
    transporte = TransporteFalso(RespuestaHTTP(302, b""))
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, transporte).canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "INVALID_RESPONSE"

    transporte = TransporteFalso(RespuestaHTTP(200, b"x" * (contrato.MAXIMO_CUERPO_RESPUESTA + 1)))
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, transporte).canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "INVALID_RESPONSE"


# ── Estado ──────────────────────────────────────────────────────────────────


def _guardar(escenario):
    escenario["almacen"].guardar_licencia(
        json.dumps(escenario["sobre"], separators=(",", ":")), ahora=AHORA, estado="ACTIVA"
    )


def test_estado_verifica_transcript_y_actualiza(escenario):
    _guardar(escenario)
    estado_sobre = sobre_estado(
        escenario["contenido"], estado="ACTIVA", comprobado_en=AHORA + timedelta(hours=1), clave_servicio=escenario["servicio"]
    )
    transporte = TransporteFalso(_ok_estado(estado_sobre))
    escenario["reloj"].momento = AHORA + timedelta(hours=2)
    resultado = _cliente(escenario, transporte).consultar_estado()
    assert resultado.actualizado
    assert resultado.estado == "ACTIVA"
    peticion = transporte.peticiones[0][1]
    assert peticion["licenseId"] == escenario["contenido"]["licenseId"]
    verificador.verificar_firma(
        escenario["identidad"].spki_der,
        contrato.bytes_prueba_estado(peticion),
        base64.b64decode(peticion["proof"]),
    )


def test_revocacion_persiste_sin_red_y_ante_respuesta_antigua(escenario):
    _guardar(escenario)
    revocada = sobre_estado(
        escenario["contenido"],
        estado="REVOCADA",
        comprobado_en=AHORA + timedelta(hours=1),
        clave_servicio=escenario["servicio"],
    )
    transporte = TransporteFalso(_ok_estado(revocada))
    escenario["reloj"].momento = AHORA + timedelta(hours=2)
    resultado = _cliente(escenario, transporte).consultar_estado()
    assert resultado.estado == "REVOCADA"
    assert escenario["almacen"].observar().licencia.revocada

    # Sin red no se borra nada.
    escenario["reloj"].momento = AHORA + timedelta(hours=3)
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, TransporteFalso(ErrorGL("NETWORK"))).consultar_estado()
    assert excinfo.value.codigo == "NETWORK"
    assert escenario["almacen"].observar().licencia.revocada

    # Un documento de estado ACTIVA más antiguo no reactiva la licencia.
    activa_antigua = sobre_estado(
        escenario["contenido"],
        estado="ACTIVA",
        comprobado_en=AHORA + timedelta(minutes=30),
        clave_servicio=escenario["servicio"],
    )
    escenario["reloj"].momento = AHORA + timedelta(hours=4)
    resultado = _cliente(escenario, TransporteFalso(_ok_estado(activa_antigua))).consultar_estado()
    assert not resultado.actualizado
    assert resultado.estado == "REVOCADA"
    assert escenario["almacen"].observar().licencia.revocada


def test_fallo_de_red_con_licencia_previa_la_conserva(escenario):
    _guardar(escenario)
    antes = escenario["almacen"].observar().licencia
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, TransporteFalso(ErrorGL("NETWORK"))).consultar_estado()
    assert excinfo.value.codigo == "NETWORK"
    despues = escenario["almacen"].observar().licencia
    assert despues is not None
    assert despues.documento == antes.documento
    assert not despues.revocada


def test_la_revocacion_del_resguardo_se_recupera_si_falla_el_espejo(escenario, monkeypatch):
    _guardar(escenario)
    original = escenario["almacen"]._escribir_licencia
    fallos = {"n": 0}

    def falla(licencia):
        if fallos["n"] == 0:
            fallos["n"] += 1
            raise AlmacenError("fallo simulado al escribir el espejo")
        return original(licencia)

    with monkeypatch.context() as parche:
        parche.setattr(escenario["almacen"], "_escribir_licencia", falla)
        with pytest.raises(AlmacenError):
            escenario["almacen"].registrar_estado(
                "REVOCADA", comprobado_en=AHORA + timedelta(hours=1)
            )
    assert escenario["almacen"].observar().licencia.revocada


def test_estado_sin_licencia(escenario):
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, TransporteFalso()).consultar_estado()
    assert excinfo.value.codigo == "SIN_LICENCIA"


def test_reloj_retrocedido_bloquea(escenario):
    escenario["almacen"].observar(AHORA)
    reloj_atras = Reloj(AHORA - timedelta(hours=3))
    with pytest.raises(ErrorGL) as excinfo:
        _cliente(escenario, TransporteFalso(), reloj=reloj_atras).canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "CLOCK_ROLLBACK"


def test_concurrencia_local_no_corrompe_el_estado(escenario):
    _guardar(escenario)
    errores: list[BaseException] = []

    def revocar() -> None:
        try:
            escenario["almacen"].registrar_estado(
                "REVOCADA", comprobado_en=AHORA + timedelta(hours=1)
            )
        except BaseException as exc:  # noqa: BLE001
            errores.append(exc)

    def activar() -> None:
        try:
            escenario["almacen"].registrar_estado(
                "ACTIVA", comprobado_en=AHORA + timedelta(hours=2)
            )
        except BaseException as exc:  # noqa: BLE001
            errores.append(exc)

    hilos = [threading.Thread(target=revocar), threading.Thread(target=activar)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()
    assert not errores
    licencia = escenario["almacen"].observar().licencia
    assert licencia.revocada and licencia.ultimo_estado == "REVOCADA"


# ── Configuración ───────────────────────────────────────────────────────────


def test_activacion_deshabilitada_sin_configuracion(tmp_path):
    identidad = crear_identidad(tmp_path)
    almacen = crear_almacen(tmp_path, identidad)
    cliente = ClienteGL(
        configuracion=configuracion(), identidad=identidad, almacen=almacen, transporte=TransporteFalso()
    )
    with pytest.raises(ErrorGL) as excinfo:
        cliente.canjear("ABCD-EFGH-JKLM-NPQR")
    assert excinfo.value.codigo == "ACTIVACION_DESHABILITADA"


@pytest.mark.parametrize(
    "campo",
    ["endpoint_redeem", "endpoint_estado"],
    ids=["canje", "estado"],
)
def test_endpoint_sin_https_se_rechaza(campo):
    with pytest.raises(Exception):
        definir(app_id=APP_ID, **{campo: "http://licencias.ejemplo.test/v1"})


# ── HTTPS real con certificado de prueba ────────────────────────────────────


def _certificado_prueba(tmp_path):
    from cryptography import x509
    from cryptography.hazmat.primitives import serialization
    from cryptography.x509.oid import NameOID

    clave = clave_p256()
    ahora = datetime.now(timezone.utc)
    nombre = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")])
    certificado = (
        x509.CertificateBuilder()
        .subject_name(nombre)
        .issuer_name(nombre)
        .public_key(clave.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(ahora - timedelta(days=1))
        .not_valid_after(ahora + timedelta(days=2))
        .add_extension(
            x509.SubjectAlternativeName([x509.DNSName("localhost")]), critical=False
        )
        .sign(clave, hashes.SHA256())
    )
    ruta_cert = tmp_path / "servidor.pem"
    ruta_clave = tmp_path / "servidor.key"
    ruta_cert.write_bytes(certificado.public_bytes(serialization.Encoding.PEM))
    ruta_clave.write_bytes(
        clave.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    return ruta_cert, ruta_clave


def test_https_real_verifica_certificado_host_y_redirecciones(escenario, tmp_path):
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    sobre = escenario["sobre"]
    respuestas = [200, 302]

    class Manejador(BaseHTTPRequestHandler):
        def do_POST(self):  # noqa: N802
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            estado = respuestas.pop(0)
            if estado == 302:
                self.send_response(302)
                self.send_header("Location", "https://otro.example/robo")
                self.end_headers()
                return
            cuerpo = json.dumps(
                {"v": 1, "ok": True, "license": sobre, "restored": False}
            ).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(cuerpo)))
            self.end_headers()
            self.wfile.write(cuerpo)

        def log_message(self, *args):  # noqa: D401, ANN002
            return

    ruta_cert, ruta_clave = _certificado_prueba(tmp_path)
    servidor = ThreadingHTTPServer(("127.0.0.1", 0), Manejador)
    contexto_servidor = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    contexto_servidor.load_cert_chain(ruta_cert, ruta_clave)
    servidor.socket = contexto_servidor.wrap_socket(servidor.socket, server_side=True)
    hilo = threading.Thread(target=servidor.serve_forever, daemon=True)
    hilo.start()
    try:
        puerto = servidor.server_address[1]
        contexto_cliente = ssl.create_default_context(cafile=str(ruta_cert))
        base = f"https://localhost:{puerto}"
        transporte = TransporteUrllib()
        cliente = ClienteGL(
            configuracion=_config(
                escenario["servicio"], redeem=f"{base}/v1/redeem", estado=f"{base}/v1/status"
            ),
            identidad=escenario["identidad"],
            almacen=escenario["almacen"],
            ahora=escenario["reloj"],
            contexto_ssl=contexto_cliente,
            transporte=transporte,
        )
        resultado = cliente.canjear("ABCD-EFGH-JKLM-NPQR")
        assert resultado.licencia.utilizable

        with pytest.raises(ErrorGL) as excinfo:
            cliente.canjear("ABCD-EFGH-JKLM-NPQR")
        assert excinfo.value.codigo == "INVALID_RESPONSE"

        # El nombre del servidor se verifica: 127.0.0.1 no está en el certificado.
        cliente_ip = ClienteGL(
            configuracion=_config(
                escenario["servicio"],
                redeem=f"https://127.0.0.1:{puerto}/v1/redeem",
                estado=f"https://127.0.0.1:{puerto}/v1/status",
            ),
            identidad=escenario["identidad"],
            almacen=escenario["almacen"],
            ahora=escenario["reloj"],
            contexto_ssl=contexto_cliente,
            transporte=transporte,
        )
        with pytest.raises(ErrorGL) as excinfo:
            cliente_ip.canjear("ABCD-EFGH-JKLM-NPQR")
        assert excinfo.value.codigo == "NETWORK"
    finally:
        servidor.shutdown()
        servidor.server_close()
        hilo.join(timeout=5)
