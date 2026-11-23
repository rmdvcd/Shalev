from __future__ import annotations

import base64
import os
import re
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

# Raíz del repo para importar tools
_RAIZ_REPO = Path(__file__).resolve().parent.parent.parent
if str(_RAIZ_REPO) not in sys.path:
    sys.path.insert(0, str(_RAIZ_REPO))

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database
from tools.licencia import probar_gl


CSRF_RE = re.compile(r'name="csrf"\s+value="([^"]+)"')


@pytest.fixture(autouse=True)
def _permitir_identidad_dev(monkeypatch: pytest.MonkeyPatch, tmp_path: Path):
    monkeypatch.setenv("DSHALEV_ALLOW_DEV_IDENTITY", "1")
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path / "xdg"))
    # En Windows el ancla de licencia vive en LOCALAPPDATA: aislarla para no usar la del usuario real.
    monkeypatch.setenv("LOCALAPPDATA", str(tmp_path / "localappdata"))


def _make_client(tmp_path: Path) -> TestClient:
    data = tmp_path / "DShalevData"
    data.mkdir()
    config = AppConfig(
        data_dir=data,
        settings_path=data / "portable-settings.json",
        database_path=data / "test.sqlite3",
        secret_key="test-session-secret-not-for-production-000000000000",
    )
    return TestClient(create_app(config, Database(config.database_path)))


def _token(response) -> str:
    match = CSRF_RE.search(response.text)
    assert match, response.text[:500]
    return match.group(1)


def _login_admin(client: TestClient):
    page = client.get("/configurar")
    res = client.post(
        "/configurar",
        data={
            "csrf": _token(page),
            "business_name": "Negocio Central",
            "username": "dueno",
            "password": "ContraseñaSegura2026!",
            "password_confirm": "ContraseñaSegura2026!",
        },
        follow_redirects=True,
    )
    assert res.status_code == 200

    page_login = client.get("/entrar")
    res_login = client.post(
        "/entrar",
        data={
            "csrf": _token(page_login),
            "username": "dueno",
            "password": "ContraseñaSegura2026!",
        },
        follow_redirects=True,
    )
    assert res_login.status_code == 200


def _get_csrf(client: TestClient, path: str = "/licencia") -> str:
    res = client.get(path)
    return _token(res)


def test_rutas_licencia_requieren_autenticacion(tmp_path: Path):
    client = _make_client(tmp_path)
    for ruta in ("/licencia", "/licencia/solicitar", "/licencia/activar"):
        res = client.get(ruta, follow_redirects=False)
        assert res.status_code == 303
        assert res.headers["location"] == "/entrar"


def test_solicitar_licencia_valida_ci_y_telefono(tmp_path: Path):
    client = _make_client(tmp_path)
    _login_admin(client)

    # 1. Ver página de solicitud
    page = client.get("/licencia/solicitar")
    assert page.status_code == 200
    assert "Solicitar licencia" in page.text
    assert "Carné de identidad cubano" in page.text

    csrf = _get_csrf(client, "/licencia/solicitar")

    # 2. CI inválido (menos de 11 dígitos)
    res_bad_ci = client.post(
        "/licencia/solicitar",
        data={
            "csrf": csrf,
            "nombre": "Ana",
            "apellidos": "Valdés",
            "ci": "12345",
            "telefono": "+5351234567",
            "tipo": "MENSUAL",
            "secundarias": "0",
        },
        follow_redirects=True,
    )
    assert "El carné de identidad debe contener exactamente 11 dígitos numéricos." in res_bad_ci.text

    # 3. Teléfono inválido (no empieza por 5 ni 6)
    csrf = _get_csrf(client, "/licencia/solicitar")
    res_bad_tel = client.post(
        "/licencia/solicitar",
        data={
            "csrf": csrf,
            "nombre": "Ana",
            "apellidos": "Valdés",
            "ci": "91040922502",
            "telefono": "+5371234567",
            "tipo": "MENSUAL",
            "secundarias": "0",
        },
        follow_redirects=True,
    )
    assert "El teléfono móvil cubano debe ser de 8 dígitos y comenzar con 5 o 6." in res_bad_tel.text


def test_solicitar_licencia_genera_spvir1_y_mensaje_gsm7(tmp_path: Path):
    client = _make_client(tmp_path)
    _login_admin(client)

    csrf = _get_csrf(client, "/licencia/solicitar")
    res = client.post(
        "/licencia/solicitar",
        data={
            "csrf": csrf,
            "nombre": "Carlos",
            "apellidos": "Rodríguez",
            "ci": "91040922502",
            "telefono": "+5351815604",
            "tipo": "SEMESTRAL",
            "secundarias": "2",
            "via": "WHATSAPP",
        },
        follow_redirects=True,
    )

    assert res.status_code == 200
    assert "Mensaje listo para enviar" in res.text
    assert "SPVIR1:" in res.text
    # Semestral base: 30,000 + 2 * 5,000 = 40,000 CUP
    assert "40,000.00 CUP" in res.text
    assert "api.whatsapp.com" in res.text
    # C1 GSM-7 en el mensaje mostrado
    assert "Rodriguez" in res.text
    assert "enviala" in res.text


def test_activar_licencia_c3_errores_accionables(tmp_path: Path):
    client = _make_client(tmp_path)
    _login_admin(client)

    csrf = _get_csrf(client, "/licencia/activar")

    # Texto vacío
    res_vacio = client.post(
        "/licencia/activar",
        data={"csrf": csrf, "texto_licencia": "   "},
        follow_redirects=True,
    )
    assert "Debes pegar el mensaje o código de la licencia." in res_vacio.text

    # Texto sin SPVI2
    csrf = _get_csrf(client, "/licencia/activar")
    res_sin_spvi2 = client.post(
        "/licencia/activar",
        data={"csrf": csrf, "texto_licencia": "Hola, este es un texto cualquiera."},
        follow_redirects=True,
    )
    assert "No se encontró una licencia en el texto pegado" in res_sin_spvi2.text

    # Texto incompleto (C3)
    csrf = _get_csrf(client, "/licencia/activar")
    res_incompleto = client.post(
        "/licencia/activar",
        data={"csrf": csrf, "texto_licencia": "SPVI2:" + "A" * 75},
        follow_redirects=True,
    )
    assert "Llegaron 75 de 210 caracteres; copia todas las partes del mensaje" in res_incompleto.text


def test_flujo_completo_activar_licencia_y_comprobar_banner(tmp_path: Path):
    client = _make_client(tmp_path)
    _login_admin(client)

    # 1. Obtener deviceId y devicePub de la instalación cargando la identidad creada
    from dshalev.licencia.identidad import cargar_o_crear_identidad, ruta_identidad
    from dshalev.licencia.solicitud import derivar_device_id, derivar_device_pub
    from dshalev.licencia.corta import GL_SIGN_SPKIS_DEFECTO

    id_inst = cargar_o_crear_identidad(ruta_identidad(tmp_path))
    dev_id = derivar_device_id(id_inst)
    dev_pub_b64 = derivar_device_pub(id_inst)
    dev_pub_bytes = probar_gl.unb64url(dev_pub_b64)

    # 2. Generar licencia GL que vence en 3 días (para verificar banner C5 a <= 7 días)
    ahora = datetime.now(timezone.utc).replace(microsecond=0)
    vence = ahora + timedelta(days=3)
    lic_id = str(uuid.uuid4())
    huella_dev = probar_gl.huella(dev_id, dev_pub_bytes)
    cuerpo = probar_gl.cuerpo(lic_id, huella_dev, "MENSUAL", "ACTIVA", 2, ahora, vence)

    # Usar la clave de prueba del vector dorado que está incluida en GL_SIGN_SPKIS_DEFECTO
    gl_sign_priv = ec.derive_private_key(probar_gl.VECTOR_FIRMA_D, ec.SECP256R1())
    spvi2_msg = probar_gl.emitir(cuerpo, dev_pub_bytes, gl_sign_priv)

    # 3. Enviar al formulario de activación
    csrf = _get_csrf(client, "/licencia/activar")
    res_act = client.post(
        "/licencia/activar",
        data={"csrf": csrf, "texto_licencia": f"Tu licencia:\n\n{spvi2_msg}\n\nGracias por su compra"},
        follow_redirects=True,
    )
    assert res_act.status_code == 200
    print("NOTICE IN RES_ACT:", re.findall(r'<div class="notice [^>]+>(.*?)</div>', res_act.text, re.DOTALL))
    assert "Licencia activada con éxito." in res_act.text
    assert "MENSUAL" in res_act.text
    assert "Plazas secundarias autorizadas:</strong> 2" in res_act.text
    # Comprobar banner C5
    assert "Recordatorio: tu licencia vence en" in res_act.text

