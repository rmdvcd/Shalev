from __future__ import annotations

import base64
import socket
import time
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient

from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database
from dshalev.sync_server import ShalevSyncServer, snapshot
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
            "turnos": [{"uuid": shift_uuid, "turno": {
                "abiertoEn": int(time.time() * 1000) - 3_600_000, "cerradoEn": int(time.time() * 1000),
                "fondoCent": 10_000, "contadoCent": 150_000,
                "conteoCierre": {"billetes": {"500": 3}, "otroCent": 0, "inventario": [], "motivo": "Cierre de turno", "aprobadoPor": None, "aprobadoEn": None},
            }}],
            "ventas": [{"uuid": sale_uuid, "turnoUuid": shift_uuid,
                        "venta": {"fecha": int(time.time() * 1000), "metodoPago": "EFECTIVO",
                                  "detalles": [{"id": 0, "productoId": 3, "nombre": "Café", "categoria": "Producto", "cantidad": 2,
                                              "precioBaseCent": 500, "precioUnitarioCent": 500, "costoUnitarioCent": 200, "clase": "PRODUCTO"}]},
                        "movimientos": [{"tipo": "VENTA", "entidad": "PRODUCTO", "entidadId": 3, "delta": -2}]}],
            "caja": [{"uuid": "323e4567-e89b-12d3-a456-426614174002", "turnoUuid": shift_uuid,
                       "mov": {"tipo": "SALIDA", "importeCent": 250, "motivo": "Compra menor", "fecha": int(time.time() * 1000)}}],
        }
        secondary.send(stream, {
            "t": "sync", "id": 41, "lote": batch, "telefono": None,
            "solicitaCierre": True, "cambiosAplicados": [], "versionCode": None,
        })
        synced = secondary.receive(stream)
        assert synced["t"] == "sync_ok" and synced["id"] == 41
        assert synced["recibidos"]["turnos"] == [{"uuid": shift_uuid, "cerrado": True}]
        assert synced["recibidos"]["ventas"] == [sale_uuid]
        assert synced["instantanea"]["nombreNegocio"] == "Café Central"
        assert synced["instantanea"]["empleado"]["permisos"] == ["VENDER_PRODUCTOS"]
        assert not synced["cerrarTurno"] and not synced["cierreRechazado"]
        with db.connect() as conn:
            assert conn.execute("SELECT stock FROM items WHERE id=3").fetchone()[0] == 8
            assert conn.execute("SELECT total_cents FROM sync_sales WHERE uuid=?", (sale_uuid,)).fetchone()[0] == 1000
            shift_row = conn.execute("SELECT closed_ms,counted_cash_cents FROM sync_shifts WHERE uuid=?", (shift_uuid,)).fetchone()
            assert shift_row["closed_ms"] and shift_row["counted_cash_cents"] == 150000
            assert conn.execute("SELECT amount_cents,reason FROM sync_cash_movements WHERE uuid=?", ("323e4567-e89b-12d3-a456-426614174002",)).fetchone()["amount_cents"] == 250
        team = client.get("/equipo")
        assert "Solicitudes de cierre móvil" in team.text and "Aprobar conteo" in team.text
        approved_close = client.post("/equipo/1/cierre", data={"csrf": _csrf(team), "decision": "aprobar"}, follow_redirects=True)
        assert "Se aprobó el cierre" in approved_close.text
        team = client.get("/equipo")
        approved_count = client.post(f"/equipo/1/turnos/{shift_uuid}/conteo/aprobar", data={"csrf": _csrf(team)}, follow_redirects=True)
        assert "Arqueo móvil aprobado" in approved_count.text
        secondary.send(stream, {
            "t": "sync", "id": 42, "lote": batch, "hash": synced["hash"],
            "telefono": None, "solicitaCierre": True, "cambiosAplicados": [], "versionCode": None,
        })
        approved = secondary.receive(stream)
        assert approved["cerrarTurno"] is True and approved["conteoTurnoUuid"] == shift_uuid
        assert approved["conteoAprobado"]["aprobadoPor"] == "dueno"
        batch_approved = {**batch, "turnos": [{**batch["turnos"][0], "turno": {**batch["turnos"][0]["turno"], "conteoCierre": approved["conteoAprobado"]}}]}
        secondary.send(stream, {
            "t": "sync", "id": 52, "lote": batch_approved, "hash": approved["hash"],
            "telefono": None, "solicitaCierre": False, "cambiosAplicados": [], "versionCode": None,
        })
        acknowledged = secondary.receive(stream)
        assert acknowledged["conteoAprobado"] is None
        with db.connect() as conn:
            assert conn.execute("SELECT COUNT(*) FROM mobile_count_approvals WHERE employee_id=1").fetchone()[0] == 0
        secondary.send(stream, {
            "t": "sync", "id": 53, "lote": batch, "hash": synced["hash"],
            "telefono": None, "solicitaCierre": False, "cambiosAplicados": [], "versionCode": None,
        })
        repeated = secondary.receive(stream)
        assert repeated["id"] == 53 and repeated["instantanea"] is None
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
        sales_page = client.get("/ventas")
        corrected = client.post(f"/ventas/movil/{sale_uuid}/corregir", data={
            "csrf": _csrf(sales_page), "quantity_0": "1", "payment_method": "EFECTIVO", "reason": "Cantidad incorrecta",
        }, follow_redirects=True)
        assert "Venta móvil corregida" in corrected.text and "Corregida" not in corrected.text
        with db.connect() as conn:
            assert conn.execute("SELECT stock FROM items WHERE id=3").fetchone()[0] == 8
            initial_movement = conn.execute("SELECT delta_milli,actor FROM inventory_movements WHERE reference=?", (f"mobile-sale:{sale_uuid}",)).fetchone()
            correction_movement = conn.execute("SELECT delta_milli,reason FROM inventory_movements WHERE reference=?", (f"mobile-sale-correction:{sale_uuid}",)).fetchone()
            assert initial_movement["delta_milli"] == -2000 and initial_movement["actor"] == "Ana"
            assert correction_movement["delta_milli"] == 1000 and "Cantidad incorrecta" in correction_movement["reason"]
            corrected_uuid = conn.execute("SELECT uuid FROM sync_sales WHERE uuid!=? ORDER BY sale_at_ms DESC LIMIT 1", (sale_uuid,)).fetchone()[0]
            assert conn.execute("SELECT total_cents FROM sync_sales WHERE uuid=?", (corrected_uuid,)).fetchone()[0] == 500
            assert conn.execute("SELECT cancelled_at FROM sync_sales WHERE uuid=?", (sale_uuid,)).fetchone()[0] is not None
        secondary.send(stream, {
            "t": "sync", "id": 54, "lote": {}, "telefono": None,
            "solicitaCierre": False, "cambiosAplicados": [], "versionCode": None,
        })
        correction = secondary.receive(stream)
        assert [change["uuid"] for change in correction["cambiosVentas"]] == [sale_uuid, corrected_uuid]
        assert correction["cambiosVentas"][0]["motivo"] == "Cantidad incorrecta"
        assert correction["cambiosVentas"][1]["corrigeUuid"] == sale_uuid
        secondary.send(stream, {
            "t": "sync", "id": 55, "lote": {}, "telefono": None,
            "solicitaCierre": False, "cambiosAplicados": [sale_uuid, corrected_uuid], "versionCode": None,
        })
        acknowledged_change = secondary.receive(stream)
        assert acknowledged_change["cambiosVentas"] == []
        sales_page = client.get("/ventas")
        cancelled = client.post(f"/ventas/movil/{corrected_uuid}/anular", data={
            "csrf": _csrf(sales_page), "reason": "Error de caja",
        }, follow_redirects=True)
        assert "Venta móvil anulada" in cancelled.text and "Anulada" in cancelled.text
        with db.connect() as conn:
            assert conn.execute("SELECT stock FROM items WHERE id=3").fetchone()[0] == 9
            reversed_movement = conn.execute("SELECT delta_milli,reason FROM inventory_movements WHERE reference=?", (f"mobile-sale-cancel:{corrected_uuid}",)).fetchone()
            assert reversed_movement["delta_milli"] == 1000 and "Error de caja" in reversed_movement["reason"]
        secondary.send(stream, {
            "t": "sync", "id": 56, "lote": {}, "telefono": None,
            "solicitaCierre": False, "cambiosAplicados": [], "versionCode": None,
        })
        cancelled_change = secondary.receive(stream)
        assert cancelled_change["cambiosVentas"][0]["uuid"] == corrected_uuid
        secondary.send(stream, {
            "t": "sync", "id": 57, "lote": {}, "telefono": None,
            "solicitaCierre": False, "cambiosAplicados": [corrected_uuid], "versionCode": None,
        })
        assert secondary.receive(stream)["cambiosVentas"] == []
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


def test_sync_recipe_commands_and_milli_ingredient_stock(tmp_path: Path):
    db = Database(tmp_path / "spvi.sqlite3")
    db.initialize()
    with db.transaction() as conn:
        ShalevSyncServer._apply_command(conn, {"t": "insumo_guardar", "insumo": {
            "id": 12, "nombre": "Leche", "unidad": "litro", "cantidadMil": 1275,
            "nivelBajoMil": 500, "nivelCriticoMil": 125, "precioCent": 250,
        }})
        ShalevSyncServer._apply_command(conn, {"t": "producto_guardar", "producto": {
            "id": 13, "nombre": "Café con leche", "categoria": "Producto", "cantidad": 10,
            "precioVentaCent": 500, "precioCostoCent": 100,
        }, "receta": {"productoId": 13, "lineas": [{"insumoId": 12, "cantidadMil": 125}]}})
        ShalevSyncServer._apply_command(conn, {"t": "servicio_guardar", "servicio": {
            "id": 21, "nombre": "Corte", "tipo": "Belleza", "importeCent": 1500, "descripcion": "Corte básico",
            "creadoEn": 1000, "actualizadoEn": 2000, "insumos": [{"insumoId": 12, "cantidadMil": 50}],
        }})
        ShalevSyncServer._apply_command(conn, {"t": "preajuste_guardar", "preajuste": {
            "id": 31, "nombre": "Transferencia +10", "puntosBasicos": 1000, "productoIds": [13],
            "servicioIds": [21], "metodoPago": "TRANSFERENCIA", "importeMinimoCent": 500, "activo": True,
        }})
        conn.execute("INSERT INTO employees(slot,name,permissions_json,active) VALUES(1,'Ana','[]',1)")
    current = snapshot(db, 1)
    assert current["recetas"] == [{"productoId": 13, "lineas": [{"insumoId": 12, "cantidadMil": 125}]}]
    assert current["servicios"][0]["id"] == 21 and current["servicios"][0]["insumos"] == [{"insumoId": 12, "cantidadMil": 50}]
    assert current["preajustes"][0]["servicioIds"] == [21] and current["preajustes"][0]["puntosBasicos"] == 1000
    with db.transaction() as conn:
        remaining_milli = ShalevSyncServer._apply_command(conn, {"t": "insumo_stock", "id": 12, "deltaMil": -125})
    assert remaining_milli == 1150
    with db.connect() as conn:
        assert conn.execute("SELECT stock FROM items WHERE id=12").fetchone()[0] == 1.15

def test_sync_rechaza_articulos_cortos_y_audita_retiros_remotos(tmp_path: Path):
    db = Database(tmp_path / "retiros.sqlite3")
    db.initialize()
    with db.transaction() as conn:
        with pytest.raises(ValueError, match="producto"):
            ShalevSyncServer._apply_command(conn, {"t": "producto_guardar", "producto": {
                "id": 1, "nombre": "AB", "cantidad": 0, "precioVentaCent": 0, "precioCostoCent": 0,
            }})
        with pytest.raises(ValueError, match="insumo"):
            ShalevSyncServer._apply_command(conn, {"t": "insumo_guardar", "insumo": {
                "id": 2, "nombre": "XY", "unidad": "kg", "cantidadMil": 0, "precioCent": 0,
            }})
        assert conn.execute("SELECT COUNT(*) FROM items").fetchone()[0] == 0
        conn.execute("INSERT INTO items(id,name,category,stock) VALUES(1,'Café','Producto',3)")
        conn.execute("INSERT INTO items(id,name,category,stock) VALUES(2,'Harina','Insumo',2500)")
        ShalevSyncServer._apply_command(conn, {"t": "producto_eliminar", "id": 1},
                                        actor="Ana", actor_id=7, reference="sync-command:7:producto")
        ShalevSyncServer._apply_command(conn, {"t": "insumo_eliminar", "id": 2},
                                        actor="Ana", actor_id=7, reference="sync-command:7:insumo")
    with db.connect() as conn:
        rows = conn.execute("SELECT item_id,item_name,stock_milli,actor,actor_type,actor_id,reason,source,reference FROM item_retirements ORDER BY item_id").fetchall()
        assert [row["item_id"] for row in rows] == [1, 2]
        assert [row["item_name"] for row in rows] == ["Café", "Harina"]
        assert all(row["actor"] == "Ana" and row["actor_type"] == "empleado" and row["actor_id"] == 7 for row in rows)
        assert all("Retirado desde Shalev" in row["reason"] and row["source"] == "sincronizacion" for row in rows)
        assert [row["reference"] for row in rows] == ["sync-command:7:producto", "sync-command:7:insumo"]
        assert conn.execute("SELECT COUNT(*) FROM items WHERE active=0").fetchone()[0] == 2
    db.close()


def test_sync_command_receipts_remain_durable_beyond_old_512_limit(tmp_path: Path):
    db = Database(tmp_path / "commands.sqlite3")
    db.initialize()
    with db.transaction() as conn:
        conn.execute("INSERT INTO employees(slot,name,permissions_json,active) VALUES(1,'Ana','[\"EDITAR_INVENTARIO\"]',1)")
        conn.execute("INSERT INTO items(id,name,category,stock) VALUES(1,'Café','Producto',0)")
    sync = ShalevSyncServer.__new__(ShalevSyncServer)
    sync.database = db
    for index in range(520):
        result = sync.handle_command(1, {
            "id": index + 1, "clave": f"retained-command-{index:04}",
            "accion": {"t": "producto_stock", "id": 1, "delta": 1},
        })
        assert result["valor"] == index + 1 and "error" not in result
    db.close()
    db = Database(tmp_path / "commands.sqlite3")
    db.initialize()
    sync.database = db
    replay = sync.handle_command(1, {
        "id": 900, "clave": "retained-command-0000",
        "accion": {"t": "producto_stock", "id": 1, "delta": 1},
    })
    assert replay["valor"] == 1 and replay["id"] == 900
    with db.connect() as conn:
        assert conn.execute("SELECT stock_milli FROM items WHERE id=1").fetchone()[0] == 520000
        assert conn.execute("SELECT COUNT(*) FROM sync_commands WHERE employee_id=1").fetchone()[0] == 520
        assert conn.execute("SELECT COUNT(*) FROM inventory_movements WHERE movement_type='COMANDO_SYNC'").fetchone()[0] == 520
        assert conn.execute("SELECT COUNT(*) FROM inventory_movements WHERE actor_id=1").fetchone()[0] == 520
