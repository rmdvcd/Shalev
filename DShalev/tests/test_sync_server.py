from __future__ import annotations

import base64
import socket
import time
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database
from dshalev.sync_protocol import (
    EncryptedChannel,
    pairing_mac_primary,
    pairing_mac_secondary,
    derive_employee_key,
    session_keys,
    reject_mac,
    read_json_frame,
    send_json_frame,
)


def _csrf(response) -> str:
    import re
    return re.search(r'name="csrf" value="([^"]+)"', response.text).group(1)


def test_lan_pairing_qr_and_encrypted_spvi_sync(tmp_path: Path, monkeypatch):
    monkeypatch.setattr("dshalev.app.get_lan_addresses", lambda: ["192.168.1.9"])
    data = tmp_path / "DShalevData"
    data.mkdir()
    config = AppConfig(
        data_dir=data,
        settings_path=data / "portable-settings.json",
        database_path=data / "test.sqlite3",
        secret_key="test-session-secret-not-for-production-000000000000",
        network_mode="lan",
    )
    db = Database(config.database_path)
    with TestClient(create_app(config, db)) as client:
        setup = client.get("/configurar")
        response = client.post("/configurar", data={
            "csrf": _csrf(setup), "business_name": "Café Central", "username": "dueno",
            "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
        }, follow_redirects=True)
        assert response.status_code == 200
        login = client.get("/entrar")
        response = client.post("/entrar", data={
            "csrf": _csrf(login), "username": "dueno", "password": "ContraseñaSegura2026!",
        }, follow_redirects=True)
        assert response.status_code == 200

        with db.transaction() as conn:
            conn.execute("INSERT INTO items(id,name,category,unit,stock,sale_price_cents,cost_cents,low_stock,critical_stock) VALUES(3,'Café','Producto','paquete',10,500,200,3,1)")
        page = client.get("/equipo")
        response = client.post("/equipo/nueva", data={
            "csrf": _csrf(page), "name": "Ana", "perm_vender_productos": "on",
        }, follow_redirects=True)
        assert "Ana" in response.text
        page = client.get("/equipo")
        response = client.post("/equipo/1/vincular", data={"csrf": _csrf(page)}, follow_redirects=True)
        assert "caduca en 10 minutos" in response.text
        qr = client.get("/equipo/1/qr.svg")
        assert qr.status_code == 200 and "<svg" in qr.text
        sync_server = client.app.state.sync_server
        assert sync_server and 47811 <= sync_server.sync_port <= 47820

        with db.connect() as conn:
            token = base64.b64decode(conn.execute("SELECT pairing_token FROM employees WHERE id=1").fetchone()[0])
            business_id = conn.execute("SELECT value FROM app_meta WHERE key='business_id'").fetchone()[0]
        client_private = ec.generate_private_key(ec.SECP256R1())
        client_public = client_private.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        pairing_socket = socket.create_connection(("127.0.0.1", sync_server.sync_port), timeout=3)
        pairing_stream = pairing_socket.makefile("rwb")
        send_json_frame(pairing_stream, {
            "t": "vincular", "v": 1, "negocio": business_id, "empleado": 1,
            "pub": base64.b64encode(client_public).decode(),
            "mac": base64.b64encode(pairing_mac_secondary(token, client_public)).decode(),
            "device": "SPVI:test-device-0001",
        })
        linked = read_json_frame(pairing_stream)
        assert linked["t"] == "vincular_ok" and linked["nombreNegocio"] == "Café Central"
        server_public = base64.b64decode(linked["pub"])
        assert base64.b64decode(linked["mac"]) == pairing_mac_primary(token, server_public, client_public)
        employee_key, _ = derive_employee_key(client_private, server_public, token)
        pairing_stream.close()
        pairing_socket.close()

        secondary_nonce = b"secondary-nonce!"
        sync_socket = socket.create_connection(("127.0.0.1", sync_server.sync_port), timeout=3)
        stream = sync_socket.makefile("rwb")
        send_json_frame(stream, {
            "t": "hola", "v": 1, "negocio": business_id, "empleado": 1,
            "nonce": base64.b64encode(secondary_nonce).decode(), "device": "SPVI:test-device-0001",
        })
        greeting = read_json_frame(stream)
        assert greeting["t"] == "hola_ok" and greeting["comandosUnicos"] is True
        primary_nonce = base64.b64decode(greeting["nonce"])
        c2s, s2c = session_keys(employee_key, secondary_nonce, primary_nonce)
        secondary = EncryptedChannel(c2s, s2c, send_label=1, receive_label=2)
        shift_uuid = "223e4567-e89b-12d3-a456-426614174001"
        sale_uuid = "123e4567-e89b-12d3-a456-426614174000"
        batch = {
            "turnos": [{"uuid": shift_uuid, "turno": {"cerradoEn": None}}],
            "ventas": [{"uuid": sale_uuid, "turnoUuid": shift_uuid,
                        "venta": {"fecha": int(time.time() * 1000), "metodoPago": "EFECTIVO",
                                  "detalles": [{"nombre": "Café", "cantidad": 2, "precioUnitarioCent": 500}]},
                        "movimientos": [{"tipo": "VENTA", "entidad": "PRODUCTO", "entidadId": 3, "delta": -2}]}],
            "caja": [],
        }
        secondary.send(stream, {
            "t": "sync", "id": 41, "lote": batch, "telefono": None,
            "solicitaCierre": False, "cambiosAplicados": [], "versionCode": None,
        })
        synced = secondary.receive(stream)
        assert synced["t"] == "sync_ok" and synced["id"] == 41
        assert synced["recibidos"]["turnos"] == [{"uuid": shift_uuid, "cerrado": False}]
        assert synced["recibidos"]["ventas"] == [sale_uuid]
        assert synced["instantanea"]["nombreNegocio"] == "Café Central"
        assert synced["instantanea"]["empleado"]["permisos"] == ["VENDER_PRODUCTOS"]
        with db.connect() as conn:
            assert conn.execute("SELECT stock FROM items WHERE id=3").fetchone()[0] == 8
            assert conn.execute("SELECT total_cents FROM sync_sales WHERE uuid=?", (sale_uuid,)).fetchone()[0] == 1000
        secondary.send(stream, {
            "t": "sync", "id": 42, "lote": batch, "hash": synced["hash"],
            "telefono": None, "solicitaCierre": False, "cambiosAplicados": [], "versionCode": None,
        })
        repeated = secondary.receive(stream)
        assert repeated["id"] == 42 and repeated["instantanea"] is None
        activity = client.get("/ventas")
        assert "Café × 2" in activity.text and "App secundaria · Ana" in activity.text
        team_activity = client.get("/equipo")
        assert "Actividad móvil reciente" in team_activity.text and "Venta móvil" in team_activity.text
        with db.connect() as conn:
            assert conn.execute("SELECT stock FROM items WHERE id=3").fetchone()[0] == 8
            assert conn.execute("SELECT COUNT(*) FROM sync_sales WHERE uuid=?", (sale_uuid,)).fetchone()[0] == 1
        secondary.send(stream, {"t": "cmd", "id": 43, "accion": {"t": "producto_stock", "id": 3, "delta": -1}})
        denied = secondary.receive(stream)
        assert denied["error"] == "permiso"
        team = client.get("/equipo")
        changed = client.post("/equipo/1/permisos", data={
            "csrf": _csrf(team), "perm_inventario": "on", "perm_precios": "on",
        }, follow_redirects=True)
        assert "Permisos guardados" in changed.text
        secondary.send(stream, {"t": "cmd", "id": 44, "clave": "test-command-replay-001", "accion": {"t": "producto_stock", "id": 3, "delta": -1}})
        applied = secondary.receive(stream)
        assert applied["valor"] == 7 and "error" not in applied
        secondary.send(stream, {"t": "cmd", "id": 45, "clave": "test-command-replay-001", "accion": {"t": "producto_stock", "id": 3, "delta": -1}})
        replayed = secondary.receive(stream)
        assert replayed == {"id": 45, "t": "cmd_ok", "valor": 7}
        with db.connect() as conn:
            assert conn.execute("SELECT stock FROM items WHERE id=3").fetchone()[0] == 7
            assert conn.execute("SELECT COUNT(*) FROM sync_commands").fetchone()[0] == 1
        secondary.send(stream, {"t": "cmd", "id": 46, "clave": "test-command-replay-001", "accion": {"t": "producto_stock", "id": 3, "delta": -4}})
        key_conflict = secondary.receive(stream)
        assert key_conflict["error"] == "otro"
        with db.connect() as conn:
            assert conn.execute("SELECT stock FROM items WHERE id=3").fetchone()[0] == 7
        secondary.send(stream, {"t": "cmd", "id": 47, "clave": "price-batch-failure-001", "accion": {"t": "producto_precios", "precios": {"3": 600, "999": 700}}})
        price_failure = secondary.receive(stream)
        assert price_failure["error"] == "no_encontrado"
        with db.connect() as conn:
            assert conn.execute("SELECT sale_price_cents FROM items WHERE id=3").fetchone()[0] == 500
        secondary.send(stream, {"t": "cmd", "id": 48, "clave": "price-batch-ok-0001", "accion": {"t": "producto_precios", "precios": {"3": 600}}})
        price_success = secondary.receive(stream)
        assert "error" not in price_success
        secondary.send(stream, {"t": "ping", "id": 49})
        assert secondary.receive(stream) == {"id": 49, "t": "pong"}
        team = client.get("/equipo")
        removed = client.post("/equipo/1/retirar", data={"csrf": _csrf(team)}, follow_redirects=True)
        assert "credenciales revocadas" in removed.text
        secondary.send(stream, {"t": "ping", "id": 50})
        assert secondary.receive(stream) == {"t": "quitada"}
        stream.close()
        sync_socket.close()

        retired_socket = socket.create_connection(("127.0.0.1", sync_server.sync_port), timeout=3)
        retired_stream = retired_socket.makefile("rwb")
        send_json_frame(retired_stream, {
            "t": "hola", "v": 1, "negocio": business_id, "empleado": 1,
            "nonce": base64.b64encode(secondary_nonce).decode(),
        })
        revoked = read_json_frame(retired_stream)
        assert revoked["t"] == "rechazo" and revoked["motivo"] == "quitada"
        assert base64.b64decode(revoked["mac"]) == reject_mac(employee_key, secondary_nonce, "quitada")
        retired_stream.close()
        retired_socket.close()

        with db.connect() as conn:
            assert conn.execute("SELECT COUNT(*) FROM employees WHERE id=1").fetchone()[0] == 0
            assert conn.execute("SELECT employee_name FROM sync_sales WHERE uuid=?", (sale_uuid,)).fetchone()[0] == "Ana"
