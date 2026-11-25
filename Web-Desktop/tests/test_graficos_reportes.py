"""Pruebas de los gráficos de Reportes.

Android tiene tres gráficos (barras, dona y evolución) con descripción textual. El escritorio ya tenía el de Inicio;
aquí se cubren los dos de Reportes: ventas por día y reparto por forma de pago. Se dibujan con `progress` y cada barra
lleva su `aria-label` con día, importe y porcentaje, así que no hace falta SVG ni canvas para leerlos.
"""

from __future__ import annotations

import re
from pathlib import Path

from fastapi.testclient import TestClient

from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database

CSRF_RE = re.compile(r'name="csrf" value="([^"]+)"')
BAR_RE = re.compile(r'<progress max="(?P<max>[^"]+)" value="(?P<value>[^"]+)" aria-label="(?P<label>[^"]+)"')


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


def setup_shop(tmp_path: Path) -> TestClient:
    client = make_client(tmp_path)
    page = client.get("/configurar")
    client.post("/configurar", data={
        "csrf": token(page), "business_name": "Cafetería Central", "username": "dueno",
        "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    page = client.get("/entrar")
    client.post("/entrar", data={
        "csrf": token(page), "username": "dueno", "password": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    turnos = client.get("/turnos")
    client.post("/turno/abrir", data={"csrf": token(turnos), "opening_cash": "0"}, follow_redirects=True)
    inventario = client.get("/inventario")
    creado = client.post("/inventario/nuevo", data={
        "csrf": token(inventario), "name": "Café", "category": "Producto", "unit": "taza",
        "sale_price": "120", "cost": "40", "stock": "50", "low_stock": "5", "critical_stock": "2",
        "reason": "Existencia inicial para pruebas",
    }, follow_redirects=True)
    assert creado.status_code == 200 and "Café" in creado.text
    return client


def sell(client: TestClient, quantity: str, payment: str) -> None:
    page = client.get("/ventas")
    with client.app.state.db.connect() as conn:
        item_id = str(conn.execute("SELECT id FROM items WHERE name='Café'").fetchone()[0])
    response = client.post("/ventas/nueva", data={
        "csrf": token(page), "sale_key": re.search(r'name="sale_key" value="([^"]+)"', page.text).group(1),
        f"quantity_{item_id}": quantity, "payment_method": payment,
    }, follow_redirects=True)
    assert response.status_code == 200


def bars(html: str, section: str) -> list[dict[str, str]]:
    start = html.index(f'id="{section}"')
    block = html[start : html.index("</article>", start)]
    return [match.groupdict() for match in BAR_RE.finditer(block)]


def test_reports_without_sales_render_empty_charts(tmp_path) -> None:
    client = setup_shop(tmp_path)
    # Período sin ninguna venta: antes esto rompía con `max(1, *[])`.
    response = client.get("/reportes", params={"desde": "2020-01-01", "hasta": "2020-01-31"})
    assert response.status_code == 200
    assert "Sin ventas en el período" in response.text
    assert bars(response.text, "reportes-diario") == []
    assert bars(response.text, "reportes-pagos") == []


def test_daily_chart_has_one_bar_per_day_with_the_amount_in_text(tmp_path) -> None:
    client = setup_shop(tmp_path)
    sell(client, "2", "EFECTIVO")  # 240 CUP
    sell(client, "1", "TRANSFERENCIA")  # 120 CUP
    response = client.get("/reportes")
    assert response.status_code == 200
    daily = bars(response.text, "reportes-diario")
    assert len(daily) == 1, daily  # todas las ventas son de hoy
    # Los importes van en centavos y el formato es el del resto de la app: "360 CUP".
    assert daily[0]["value"] == "36000"
    assert "360 CUP" in daily[0]["label"]
    assert daily[0]["max"] == "36000"


def test_payment_chart_splits_the_total_and_shares_sum_to_100(tmp_path) -> None:
    client = setup_shop(tmp_path)
    sell(client, "3", "EFECTIVO")  # 360 de 480
    sell(client, "1", "TRANSFERENCIA")  # 120 de 480
    response = client.get("/reportes")
    pagos = bars(response.text, "reportes-pagos")
    assert [bar["label"].split(":")[0] for bar in pagos] == ["Efectivo", "Transferencia"]
    assert [bar["value"] for bar in pagos] == ["75", "25"]
    assert sum(int(bar["value"]) for bar in pagos) == 100
    assert all(bar["max"] == "100" for bar in pagos)


def test_charts_ignore_cancelled_sales_and_period_limits(tmp_path) -> None:
    client = setup_shop(tmp_path)
    sell(client, "2", "EFECTIVO")  # 240 CUP
    with client.app.state.db.connect() as conn:
        sale_id = conn.execute("SELECT id FROM sales").fetchone()[0]
    ventas = client.get("/ventas")
    cancelada = client.post(f"/ventas/{sale_id}/anular",
                            data={"csrf": token(ventas), "reason": "Cliente devolvió el pedido"},
                            follow_redirects=True)
    assert cancelada.status_code == 200
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT cancelled_at FROM sales WHERE id=?", (sale_id,)).fetchone()[0] is not None
    response = client.get("/reportes")
    # Una venta anulada no deja barra: el gráfico no puede contar dinero que se devolvió.
    assert bars(response.text, "reportes-diario") == []
    assert bars(response.text, "reportes-pagos") == []
    assert "Sin ventas en el período" in response.text


def test_chart_is_reachable_by_the_reports_tour(tmp_path) -> None:
    client = setup_shop(tmp_path)
    response = client.get("/recorrido/reportes")
    assert response.status_code == 200
    assert "?recorrido=reportes" in response.text
