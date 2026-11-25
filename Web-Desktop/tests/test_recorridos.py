"""Pruebas de los recorridos guiados de DShalev.

La mecánica viene de Android (`Recorridos.kt` y `OnboardingPreferences.kt`): los pasos cuyo objetivo no está se saltan,
el estado se guarda con la clave `<clave>_tour_shown` y un recorrido ya visto no vuelve a salir solo. Los textos
respetan el límite de Android (título ≤ 28, frase ≤ 120).
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from dshalev import recorridos
from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database

CSRF_RE = re.compile(r'name="csrf" value="([^"]+)"')


def make_client(tmp_path: Path) -> TestClient:
    data = tmp_path / "DShalevData"
    data.mkdir()
    config = AppConfig(
        data_dir=data,
        settings_path=data / "portable-settings.json",
        database_path=data / "test.sqlite3",
        secret_key="test-session-secret-not-for-production-000000000000",
    )
    return TestClient(create_app(config, Database(config.database_path)))


def token(response) -> str:
    match = CSRF_RE.search(response.text)
    assert match, response.text[:500]
    return match.group(1)


def create_admin(client: TestClient) -> None:
    page = client.get("/configurar")
    client.post("/configurar", data={
        "csrf": token(page), "business_name": "Cafetería Central", "username": "dueno",
        "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
    }, follow_redirects=True)


def login(client: TestClient) -> None:
    page = client.get("/entrar")
    response = client.post("/entrar", data={
        "csrf": token(page), "username": "dueno", "password": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    assert response.status_code == 200


# ---- Definición ----

def test_every_tour_respects_android_text_limits() -> None:
    assert len(recorridos.RECORRIDOS) == 6
    for recorrido in recorridos.RECORRIDOS:
        assert recorrido.pasos, recorrido.clave
        for paso in recorrido.pasos:
            assert len(paso.titulo) <= recorridos.TITULO_MAX, paso
            assert len(paso.frase) <= recorridos.FRASE_MAX, paso
            # Android: la frase empieza por la acción.
            assert paso.frase[0].isupper(), paso


def test_un_texto_demasiado_largo_falla_al_construir() -> None:
    with pytest.raises(ValueError):
        recorridos.Paso("x", "t" * 29, "Frase corta.")
    with pytest.raises(ValueError):
        recorridos.Paso("x", "Título", "F" + "x" * 120)


def test_state_key_matches_android_convention() -> None:
    assert recorridos.clave_estado("inventario") == "inventario_tour_shown"
    with pytest.raises(ValueError):
        recorridos.clave_estado("no-existe")


def test_steps_without_target_are_skipped() -> None:
    disponibles = {"inventario-lista", "inventario-agregar"}
    pasos = recorridos.pasos_disponibles("inventario", disponibles)
    assert [p.objetivo for p in pasos] == ["inventario-lista", "inventario-agregar"]
    assert recorridos.pasos_disponibles("no-existe", disponibles) == []


# ---- Rutas ----

def test_tour_appears_the_first_time_and_not_the_second(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)

    primera = client.get("/inventario")
    assert 'id="recorrido-datos"' in primera.text
    assert "inventario_tour_shown" in primera.text
    assert "/static/recorrido.js" in primera.text

    visto = client.post("/ayuda/recorrido-visto",
                        data={"csrf": token(primera), "clave": "inventario_tour_shown"})
    assert visto.status_code == 200 and visto.json() == {"ok": True}

    segunda = client.get("/inventario")
    assert 'id="recorrido-datos"' not in segunda.text
    assert "/static/recorrido.js" not in segunda.text


def test_tour_can_be_replayed_on_purpose(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    primera = client.get("/inventario")
    client.post("/ayuda/recorrido-visto", data={"csrf": token(primera), "clave": "inventario_tour_shown"})
    assert 'id="recorrido-datos"' not in client.get("/inventario").text
    # Repetir a mano con ?recorrido=inventario lo vuelve a mostrar sin borrar el estado.
    repetido = client.get("/inventario", params={"recorrido": "inventario"})
    assert 'id="recorrido-datos"' in repetido.text


def test_tour_targets_exist_in_the_page(tmp_path) -> None:
    """Cada paso del recorrido tiene su objetivo en el HTML: si no, el recorrido se quedaría sin pasos."""
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    paginas = {"inicio": "/", "inventario": "/inventario", "servicios": "/servicios",
               "reportes": "/reportes", "herramientas": "/herramientas"}
    for clave, ruta in paginas.items():
        html = client.get(ruta).text
        for paso in recorridos.POR_CLAVE[clave].pasos:
            assert f'id="{paso.objetivo}"' in html, (clave, paso.objetivo)


def test_sales_tour_needs_an_open_shift(tmp_path) -> None:
    """Sin turno no se puede vender, así que el recorrido del punto de venta no se ofrece (Android también lo omite)."""
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    # La página de repetición explica los pasos; el recorrido se pinta en Ventas, que exige turno abierto.
    ficha = client.get("/recorrido/ventas")
    assert "?recorrido=ventas" in ficha.text
    sin_turno = client.get("/ventas", params={"recorrido": "ventas"}, follow_redirects=False)
    assert sin_turno.status_code in (303, 307)
    assert sin_turno.headers["location"].startswith("/turnos")
    assert "Abre un turno" in client.get(sin_turno.headers["location"]).text

    turnos = client.get("/turnos")
    client.post("/turno/abrir", data={"csrf": token(turnos), "opening_cash": "0"}, follow_redirects=True)
    ventas = client.get("/ventas")
    assert 'id="pos-app"' in ventas.text
    for paso in recorridos.POR_CLAVE["ventas"].pasos:
        assert f'id="{paso.objetivo}"' in ventas.text, paso.objetivo


def test_mark_seen_rejects_unknown_key_and_bad_csrf(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/inventario")
    malo = client.post("/ayuda/recorrido-visto", data={"csrf": token(page), "clave": "otra_cosa_tour_shown"})
    assert malo.status_code == 400
    sin_token = client.post("/ayuda/recorrido-visto", data={"csrf": "falso", "clave": "inventario_tour_shown"})
    assert sin_token.status_code == 403


def test_mark_seen_requires_login(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)  # sin login
    response = client.post("/ayuda/recorrido-visto", data={"csrf": "x", "clave": "inventario_tour_shown"})
    assert response.status_code == 401


def test_replay_page_lists_the_steps_and_links_back(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    response = client.get("/recorrido/herramientas")
    assert response.status_code == 200
    assert "Recorrido: Herramientas" in response.text
    assert "ajusta precios" in response.text.lower()
    assert "?recorrido=herramientas" in response.text
    # Un recorrido inventado vuelve a Ayuda con aviso.
    inventado = client.get("/recorrido/no-existe", follow_redirects=True)
    assert "no existe" in inventado.text


def test_help_page_links_every_tour(tmp_path) -> None:
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    ayuda = client.get("/ayuda")
    for clave in recorridos.POR_CLAVE:
        assert f'href="/recorrido/{clave}"' in ayuda.text
