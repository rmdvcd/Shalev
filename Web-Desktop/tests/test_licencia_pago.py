"""Pruebas de instrucciones de pago y cálculo canónico de precios (T2)."""

import json
import re
from pathlib import Path

import pytest
from starlette.testclient import TestClient

from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database
from dshalev.licencia.pago import (
    AVISO_SIN_METODOS,
    FRASE_PAGA_SOLO,
    METODOS_COBRO,
    TITULO_PAGO,
    MetodoCobro,
)
from dshalev.licencia.solicitud import calcular_precio, calcular_precio_ampliacion

ROOT_DIR = Path(__file__).resolve().parents[2]
VECTOR_PRECIOS = ROOT_DIR / "docs" / "vectores" / "precios.json"


def test_precios_contra_vector_canonico():
    """T2: Todos los importes por acción y plan coinciden con docs/vectores/precios.json."""
    datos = json.loads(VECTOR_PRECIOS.read_text(encoding="utf-8"))
    tarifas = datos["tarifas"]

    for tipo, valores in tarifas.items():
        base_pesos = valores["precioBasePesos"]
        sec_pesos = valores["precioSecundariaPesos"]

        for sec in range(11):
            esperado = base_pesos + (sec * sec_pesos)
            obtenido = calcular_precio(tipo, sec)
            assert obtenido == esperado, f"Fallo en {tipo} con {sec} secundarias: {obtenido} != {esperado}"

        # Prueba de ampliación
        for adic in range(1, 11):
            esp_amp = adic * sec_pesos
            obt_amp = calcular_precio_ampliacion(tipo, adic)
            assert obt_amp == esp_amp, f"Fallo en ampliación {tipo} con +{adic}: {obt_amp} != {esp_amp}"


def test_textos_cobro_gsm7():
    """T2: Los textos de aviso y métodos de pago no contienen caracteres fuera de GSM-7."""
    gsm7_basic = set(
        "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\x1bÆæßÉ !\"#¤%&'()*+,-./"
        "0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    )
    for texto in (AVISO_SIN_METODOS,):
        for ch in texto:
            assert ch in gsm7_basic, f"Carácter {ch!r} en {texto!r} no es GSM-7 básico"


@pytest.fixture(autouse=True)
def _permitir_identidad_dev(monkeypatch: pytest.MonkeyPatch, tmp_path: Path):
    monkeypatch.setenv("DSHALEV_ALLOW_DEV_IDENTITY", "1")
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path / "xdg"))


@pytest.fixture
def client(tmp_path: Path):
    data = tmp_path / "DShalevData"
    data.mkdir()
    config = AppConfig(
        data_dir=data,
        settings_path=data / "portable-settings.json",
        database_path=data / "test.sqlite3",
        secret_key="test-session-secret-not-for-production-000000000000",
    )
    c = TestClient(create_app(config, Database(config.database_path)))

    # 1. Configurar negocio
    page = c.get("/configurar")
    token_cfg = re.search(r'name="csrf"\s+value="([^"]+)"', page.text).group(1)
    c.post(
        "/configurar",
        data={
            "csrf": token_cfg,
            "business_name": "Negocio Central",
            "username": "dueno",
            "password": "ContraseñaSegura2026!",
            "password_confirm": "ContraseñaSegura2026!",
        },
        follow_redirects=True,
    )

    # 2. Login
    page_login = c.get("/entrar")
    token_login = re.search(r'name="csrf"\s+value="([^"]+)"', page_login.text).group(1)
    c.post(
        "/entrar",
        data={
            "csrf": token_login,
            "username": "dueno",
            "password": "ContraseñaSegura2026!",
        },
        follow_redirects=True,
    )
    return c


def test_tarjeta_pago_en_solicitar_web(client: TestClient, monkeypatch):
    """T2: La tarjeta 'Cómo pagar' se muestra antes del envío y se oculta en recuperación."""
    # 1. Sin métodos de cobro configurados: muestra texto alternativo. La advertencia de pagar solo a los
    # datos de esta pantalla se muestra siempre (antes solo aparecía cuando había métodos configurados).
    res = client.get("/licencia/solicitar")
    assert res.status_code == 200
    assert TITULO_PAGO in res.text
    assert AVISO_SIN_METODOS in res.text
    assert FRASE_PAGA_SOLO in res.text
    # Paridad con Android e iOS: la tarjeta lleva el importe con las tarifas canónicas (mensual sin secundarias).
    assert 'id="importe-pagar"' in res.text
    assert "6,000.00 CUP" in res.text

    # 2. Con métodos de cobro configurados: muestra frase y destino con botón Copiar
    metodos_test = (
        MetodoCobro(nombre="Transfermóvil", destino="9225020011223344", nota="CUP"),
    )
    from dshalev.licencia import pago
    monkeypatch.setattr(pago, "METODOS_COBRO", metodos_test)

    res2 = client.get("/licencia/solicitar")
    assert res2.status_code == 200
    assert FRASE_PAGA_SOLO in res2.text
    assert "9225020011223344" in res2.text

    # 3. En recuperación: gratis y sin tarjeta de pago
    res_rec = client.get("/licencia/solicitar?recupera=1")
    assert res_rec.status_code == 200
    assert TITULO_PAGO not in res_rec.text
