from __future__ import annotations

import base64
import csv
import io
import re
import zipfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from dshalev.app import create_app
from dshalev.config import AppConfig
from dshalev.database import Database
from dshalev.sync_server import ShalevSyncServer, snapshot

CSRF_RE = re.compile(r'name="csrf" value="([^"]+)"')


def token(response) -> str:
    match = CSRF_RE.search(response.text)
    assert match, response.text[:500]
    return match.group(1)


def client_for(tmp_path: Path) -> TestClient:
    data = tmp_path / "DShalevData"
    data.mkdir()
    config = AppConfig(data, data / "settings.json", data / "test.db", "test-session-secret-not-for-production-000000000000")
    return TestClient(create_app(config, Database(config.database_path)))


def setup_and_login(client: TestClient) -> None:
    setup = client.get("/configurar")
    client.post("/configurar", data={
        "csrf": token(setup), "business_name": "Tienda", "username": "dueno",
        "password": "ContraseñaSegura2026!", "password_confirm": "ContraseñaSegura2026!",
    }, follow_redirects=True)
    login = client.get("/entrar")
    client.post("/entrar", data={"csrf": token(login), "username": "dueno", "password": "ContraseñaSegura2026!"}, follow_redirects=True)


def add_item(client: TestClient, *, name: str, category: str, stock: int, price: str) -> None:
    page = client.get("/inventario")
    response = client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": name, "category": category, "unit": "unidad",
        "sale_price": price, "cost": "1", "stock": str(stock), "low_stock": "2", "critical_stock": "1",
        "reason": "Existencia inicial para pruebas" if stock else "",
    }, follow_redirects=True)
    assert response.status_code == 200 and name in response.text


def test_photo_and_apk_uploads_are_advertised_and_served_in_blocks(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Café", category="Producto", stock=3, price="2")
    page = client.get("/distribucion")
    csrf = token(page)
    jpeg = b"\xff\xd8\xff\xd9"
    photo = client.post("/distribucion/foto", content=jpeg, headers={
        "Content-Type": "image/jpeg", "X-CSRF-Token": csrf, "X-Entity": "producto", "X-Entity-Id": "1",
    })
    assert photo.status_code == 200 and photo.json()["bytes"] == len(jpeg)
    apk_buffer = io.BytesIO()
    with zipfile.ZipFile(apk_buffer, "w") as archive:
        archive.writestr("AndroidManifest.xml", b"binary manifest fixture")
        archive.writestr("classes.dex", b"dex test")
    apk_bytes = apk_buffer.getvalue()
    apk = client.post("/distribucion/apk", content=apk_bytes, headers={
        "Content-Type": "application/vnd.android.package-archive", "X-CSRF-Token": csrf, "X-Version-Code": "105",
    })
    assert apk.status_code == 200 and apk.json()["bytes"] == len(apk_bytes)
    db = client.app.state.db
    with db.transaction() as conn:
        conn.execute("INSERT INTO employees(slot,name,permissions_json,active) VALUES(1,'Ana','[]',1)")
    backup_bytes = db.export_backup("Respaldo multimedia seguro 2026!")
    restored_db = Database(tmp_path / "restored" / "business.sqlite3")
    restored_db.initialize()
    restored_db.restore_backup(backup_bytes, "Respaldo multimedia seguro 2026!")
    with restored_db.connect() as conn:
        restored_photo = conn.execute("SELECT filename FROM catalog_photos WHERE entity_id=1").fetchone()[0]
        assert conn.execute("SELECT value FROM app_meta WHERE key='apk_version_code'").fetchone()[0] == "105"
    assert (restored_db.path.parent / "catalog_photos" / restored_photo).read_bytes() == jpeg
    assert (restored_db.path.parent / "updates" / "shalev.apk").read_bytes() == apk_bytes
    sync = ShalevSyncServer.__new__(ShalevSyncServer)
    sync.database = db
    image_metadata = snapshot(db, 1)["fotos"][0]
    block = sync.photo_block({"id": 8, "nombre": image_metadata["nombre"], "desde": 0})
    assert block["t"] == "foto_bloque" and base64.b64decode(block["datos"]) == jpeg
    offer = sync.handle_sync(1, {"id": 9, "lote": {}, "versionCode": 104})
    assert offer["apk"]["code"] == 105 and offer["apk"]["sha256"] == apk.json()["sha256"]
    apk_block = sync.apk_block({"id": 10, "sha256": offer["apk"]["sha256"], "desde": 0})
    assert apk_block["t"] == "apk_bloque" and base64.b64decode(apk_block["datos"]) == apk_bytes
    assert sync.apk_block({"id": 11, "sha256": "0" * 64, "desde": 0})["error"] == "no_encontrado"


def test_admin_can_download_and_restore_password_protected_backup(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("UPDATE app_meta SET value='Negocio en respaldo' WHERE key='business_name'")
    page = client.get("/respaldo")
    assert 'autocomplete="new-password"' in page.text
    assert page.text.count('autocomplete="off"') == 1
    backup_response = client.post("/respaldo/descargar", data={
        "csrf": token(page), "passphrase": "Contraseña de respaldo 2026!", "confirm_passphrase": "Contraseña de respaldo 2026!",
    })
    assert backup_response.status_code == 200 and backup_response.headers["content-type"] == "application/octet-stream"
    assert backup_response.content.startswith(b"DSHALEV-BACKUP-1")
    with client.app.state.db.transaction() as conn:
        conn.execute("UPDATE app_meta SET value='Negocio posterior' WHERE key='business_name'")
    restored = client.post("/respaldo/restaurar", content=backup_response.content, headers={
        "X-CSRF-Token": token(page), "X-Backup-Passphrase-B64": base64.b64encode("Contraseña de respaldo 2026!".encode()).decode(), "Content-Type": "application/octet-stream",
    })
    assert restored.status_code == 200 and "Vuelve a iniciar sesión" in restored.json()["message"]
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT value FROM app_meta WHERE key='business_name'").fetchone()[0] == "Negocio en respaldo"


def test_inventory_edit_and_retire_keep_existing_sales_history(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Café", category="Producto", stock=5, price="10")
    home = client.get("/")
    client.post("/turno/abrir", data={"csrf": token(home), "opening_cash": "0"}, follow_redirects=True)
    sales_page = client.get("/ventas")
    sale = client.post("/ventas/nueva", data={
        "csrf": token(sales_page), "quantity_1": "1", "payment_method": "EFECTIVO",
    }, follow_redirects=True)
    assert "Café × 1" in sale.text

    edit = client.get("/inventario/1/editar")
    saved = client.post("/inventario/1/editar", data={
        "csrf": token(edit), "name": "Café molido", "category": "Producto", "unit": "paquete",
        "sale_price": "12.50", "cost": "6", "stock": "4", "low_stock": "2", "critical_stock": "1",
    }, follow_redirects=True)
    assert "Artículo actualizado" in saved.text and "Café molido" in saved.text

    page = client.get("/inventario")
    assert 'minlength="3"' in page.text and "Motivo del retiro" in page.text
    without_reason = client.post("/inventario/1/eliminar", data={"csrf": token(page)}, follow_redirects=True)
    assert "motivo del retiro" in without_reason.text.lower()
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT active FROM items WHERE id=1").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM item_retirements").fetchone()[0] == 0

    page = client.get("/inventario")
    removed = client.post("/inventario/1/eliminar", data={
        "csrf": token(page), "reason": "Artículo fuera de temporada",
    }, follow_redirects=True)
    assert "Artículo retirado" in removed.text and "motivo quedó en el historial" in removed.text
    history = client.get("/inventario/movimientos")
    export = client.get("/inventario/movimientos.csv")
    assert history.status_code == 200 and "Artículo fuera de temporada" in history.text and "Retiro" in history.text
    assert export.status_code == 200 and "RETIRO" in export.text and "Artículo fuera de temporada" in export.text
    with client.app.state.db.connect() as conn:
        row = conn.execute("SELECT active,name,stock_milli FROM items WHERE id=1").fetchone()
        retirement = conn.execute("SELECT * FROM item_retirements WHERE item_id=1").fetchone()
        assert conn.execute("SELECT COUNT(*) FROM sales").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM sale_lines").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM inventory_movements WHERE movement_type='VENTA'").fetchone()[0] == 1
        import sqlite3
        with pytest.raises(sqlite3.IntegrityError):
            conn.execute("UPDATE item_retirements SET reason='modificado' WHERE id=?", (retirement["id"],))
        with pytest.raises(sqlite3.IntegrityError):
            conn.execute("DELETE FROM item_retirements WHERE id=?", (retirement["id"],))
    assert row["active"] == 0 and row["name"] == "Café molido" and row["stock_milli"] == 4000
    assert retirement["actor"] == "dueno" and retirement["reason"] == "Artículo fuera de temporada"
    assert retirement["item_name"] == "Café molido" and retirement["stock_milli"] == 4000

    phrase = "Respaldo de retiros seguro 2026!"
    backup = client.app.state.db.export_backup(phrase)
    restored = Database(tmp_path / "restored-retirements" / "business.sqlite3")
    restored.initialize()
    restored.restore_backup(backup, phrase)
    with restored.connect() as conn:
        saved = conn.execute("SELECT item_name,reason FROM item_retirements WHERE item_id=1").fetchone()
        assert tuple(saved) == ("Café molido", "Artículo fuera de temporada")
    restored.close()


def test_inventory_name_requires_three_characters_in_forms_and_server(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    page = client.get("/inventario")
    assert 'name="name" required minlength="3" maxlength="80"' in page.text
    data = {
        "csrf": token(page), "name": "AB", "category": "Producto", "unit": "unidad",
        "sale_price": "0", "cost": "0", "stock": "0", "low_stock": "2", "critical_stock": "1",
    }
    rejected = client.post("/inventario/nuevo", data=data, follow_redirects=True)
    assert "El nombre del artículo debe tener entre 3 y 80 caracteres." in rejected.text
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT COUNT(*) FROM items").fetchone()[0] == 0

    add_item(client, name="Café", category="Producto", stock=0, price="10")
    edit = client.get("/inventario/1/editar")
    assert 'name="name" required minlength="3" maxlength="80"' in edit.text
    bad_edit = client.post("/inventario/1/editar", data={
        "csrf": token(edit), "name": "AB", "category": "Producto", "unit": "unidad",
        "sale_price": "10", "cost": "1", "stock": "0", "low_stock": "2", "critical_stock": "1",
    }, follow_redirects=True)
    assert "El nombre del artículo debe tener entre 3 y 80 caracteres." in bad_edit.text
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT name FROM items WHERE id=1").fetchone()[0] == "Café"
    services = client.get("/servicios")
    assert 'name="name" minlength="3" maxlength="80" required' in services.text


def test_web_sale_supports_multiple_lines_and_services_without_stock(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Café", category="Producto", stock=5, price="10")
    add_item(client, name="Té negro", category="Producto", stock=4, price="20")
    add_item(client, name="Corte", category="Servicio", stock=0, price="7.50")

    home = client.get("/")
    opened = client.post("/turno/abrir", data={"csrf": token(home), "opening_cash": "100"}, follow_redirects=True)
    assert "Abierto" in opened.text
    page = client.get("/ventas")
    assert "Carrito de venta" in page.text and "Corte" in page.text
    sale = client.post("/ventas/nueva", data={
        "csrf": token(page), "quantity_1": "2", "quantity_2": "1", "quantity_3": "1",
        "payment_method": "EFECTIVO",
    }, follow_redirects=True)
    assert sale.status_code == 200 and "Café × 2" in sale.text and "Té negro × 1" in sale.text and "Corte × 1" in sale.text
    with client.app.state.db.connect() as conn:
        rows = conn.execute("SELECT id,stock FROM items ORDER BY id").fetchall()
        total = conn.execute("SELECT total_cents FROM sales").fetchone()[0]
        lines = conn.execute("SELECT COUNT(*) FROM sale_lines").fetchone()[0]
    assert [row["stock"] for row in rows] == [3, 3, 0]
    assert total == 4750 and lines == 3


def test_shift_cash_movements_and_counted_close_are_reported(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Café", category="Producto", stock=5, price="10")
    home = client.get("/")
    client.post("/turno/abrir", data={"csrf": token(home), "opening_cash": "100"}, follow_redirects=True)
    dashboard = client.get("/")
    entry = client.post("/caja/movimiento", data={
        "csrf": token(dashboard), "movement_type": "ENTRADA", "amount": "20", "reason": "Cambio inicial",
    }, follow_redirects=True)
    assert "Movimiento de caja registrado" in entry.text
    dashboard = client.get("/")
    exit_ = client.post("/caja/movimiento", data={
        "csrf": token(dashboard), "movement_type": "SALIDA", "amount": "5", "reason": "Compra menor",
    }, follow_redirects=True)
    assert "Movimiento de caja registrado" in exit_.text
    sales = client.get("/ventas")
    sold = client.post("/ventas/nueva", data={
        "csrf": token(sales), "quantity_1": "2", "payment_method": "EFECTIVO",
    }, follow_redirects=True)
    assert "Café × 2" in sold.text
    dashboard = client.get("/")
    closed = client.post("/turno/cerrar", data={"csrf": token(dashboard), "counted_cash": "130"}, follow_redirects=True)
    assert "Turno cerrado" in closed.text
    history = client.get("/turnos")
    assert "135 CUP" in history.text and "130 CUP" in history.text and "-5 CUP" in history.text
    with client.app.state.db.connect() as conn:
        shift = conn.execute("SELECT closed_at,counted_cash_cents FROM shifts ORDER BY id DESC LIMIT 1").fetchone()
        assert shift["closed_at"] and shift["counted_cash_cents"] == 13000
        assert conn.execute("SELECT COUNT(*) FROM cash_movements").fetchone()[0] == 2


def test_ingredient_stock_supports_three_decimal_places(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    page = client.get("/inventario")
    saved = client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": "Leche", "category": "Insumo", "unit": "litro",
        "sale_price": "0", "cost": "2.50", "stock": "1.275", "low_stock": "0.500", "critical_stock": "0.125",
        "reason": "Recepción inicial de leche",
    }, follow_redirects=True)
    assert saved.status_code == 200 and "1.275" in saved.text
    with client.app.state.db.connect() as conn:
        row = conn.execute("SELECT stock,low_stock,critical_stock FROM items WHERE name='Leche'").fetchone()
    assert row["stock"] == 1.275 and row["low_stock"] == 0.5 and row["critical_stock"] == 0.125
    assert "Leche" not in client.get("/ventas").text
    edit = client.get("/inventario/1/editar")
    invalid = client.post("/inventario/1/editar", data={
        "csrf": token(edit), "name": "Leche", "category": "Insumo", "unit": "litro",
        "sale_price": "0", "cost": "2.50", "stock": "1.0001", "low_stock": "0.5", "critical_stock": "0.1",
    }, follow_redirects=True)
    assert "hasta tres decimales" in invalid.text
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT stock FROM items WHERE id=1").fetchone()[0] == 1.275


def test_recipe_consumes_fractional_ingredients_and_rejects_insufficient_stock(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Pan", category="Producto", stock=20, price="4")
    page = client.get("/inventario")
    client.post("/inventario/nuevo", data={
        "csrf": token(page), "name": "Harina", "category": "Insumo", "unit": "kg",
        "sale_price": "0", "cost": "1", "stock": "1.275", "low_stock": "0.5", "critical_stock": "0.1",
        "reason": "Recepción inicial de harina",
    }, follow_redirects=True)
    recipes = client.get("/recetas")
    saved = client.post("/recetas/guardar", data={
        "csrf": token(recipes), "product_id": "1", "ingredient_id": "2", "quantity": "0.125",
    }, follow_redirects=True)
    assert "Receta guardada" in saved.text
    home = client.get("/")
    client.post("/turno/abrir", data={"csrf": token(home), "opening_cash": "0"}, follow_redirects=True)
    page = client.get("/ventas")
    sold = client.post("/ventas/nueva", data={"csrf": token(page), "quantity_1": "3", "payment_method": "EFECTIVO"}, follow_redirects=True)
    assert "Pan × 3" in sold.text
    with client.app.state.db.connect() as conn:
        stock = conn.execute("SELECT stock FROM items WHERE id=2").fetchone()[0]
        product_stock = conn.execute("SELECT stock FROM items WHERE id=1").fetchone()[0]
    assert abs(stock - 0.9) < 1e-9 and product_stock == 17
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT stock_milli FROM items WHERE id=2").fetchone()[0] == 900
        sale_movements = conn.execute("SELECT COUNT(*) FROM inventory_movements WHERE reference='web-sale:1'").fetchone()[0]
    assert sale_movements == 2
    page = client.get("/ventas")
    rejected = client.post("/ventas/nueva", data={"csrf": token(page), "quantity_1": "8", "payment_method": "EFECTIVO"}, follow_redirects=True)
    assert "No hay suficiente Harina" in rejected.text
    with client.app.state.db.connect() as conn:
        assert abs(conn.execute("SELECT stock FROM items WHERE id=2").fetchone()[0] - 0.9) < 1e-9
        assert conn.execute("SELECT COUNT(*) FROM sales").fetchone()[0] == 1


def test_sales_report_and_inventory_csv_exports(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Café", category="Producto", stock=5, price="10")
    customer_page = client.get("/clientes")
    created_customer = client.post("/clientes/nuevo", data={
        "csrf": token(customer_page), "name": "María Pérez", "phone": "555-0100", "email": "maria@example.test", "notes": "Preferencia café molido",
    }, follow_redirects=True)
    assert "María Pérez" in created_customer.text
    home = client.get("/")
    client.post("/turno/abrir", data={"csrf": token(home), "opening_cash": "0"}, follow_redirects=True)
    sales = client.get("/ventas")
    client.post("/ventas/nueva", data={
        "csrf": token(sales), "quantity_1": "1", "payment_method": "EFECTIVO", "customer_id": "1",
    }, follow_redirects=True)
    with client.app.state.db.connect() as conn:
        sale_date = conn.execute("SELECT substr(created_at,1,10) FROM sales LIMIT 1").fetchone()[0]
    report = client.get(f"/reportes?desde={sale_date}&hasta={sale_date}")
    assert report.status_code == 200 and "Café × 1" in report.text and "10 CUP" in report.text and "María Pérez" in report.text
    export = client.get(f"/reportes/ventas.csv?desde={sale_date}&hasta={sale_date}")
    assert export.status_code == 200 and "text/csv" in export.headers["content-type"]
    assert "Café × 1" in export.text and "10.00" in export.text and "María Pérez" in export.text
    customer_export = client.get("/clientes.csv")
    assert customer_export.status_code == 200 and "maria@example.test" in customer_export.text
    inventory = client.get("/inventario.csv")
    assert inventory.status_code == 200 and "Café" in inventory.text and "Inventario" not in inventory.text


def test_adjustments_require_reason_and_inventory_ledger_is_append_only(tmp_path: Path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Café", category="Producto", stock=5, price="10")
    with client.app.state.db.connect() as conn:
        initial = conn.execute("SELECT * FROM inventory_movements ORDER BY id").fetchall()
    assert len(initial) == 1 and initial[0]["movement_type"] == "ALTA"
    assert initial[0]["actor"] == "dueno" and initial[0]["reason"] == "Existencia inicial para pruebas"

    edit = client.get("/inventario/1/editar")
    fields = {
        "csrf": token(edit), "name": "Café", "category": "Producto", "unit": "unidad",
        "sale_price": "10", "cost": "1", "stock": "4", "low_stock": "2", "critical_stock": "1",
    }
    missing_reason = client.post("/inventario/1/editar", data=fields, follow_redirects=True)
    assert "motivo del ajuste" in missing_reason.text.lower()
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT stock_milli FROM items WHERE id=1").fetchone()[0] == 5000
        assert conn.execute("SELECT COUNT(*) FROM inventory_movements").fetchone()[0] == 1

    fields["reason"] = "Conteo físico de cierre"
    adjusted = client.post("/inventario/1/editar", data=fields, follow_redirects=True)
    assert "Artículo actualizado" in adjusted.text
    with client.app.state.db.connect() as conn:
        row = conn.execute("SELECT stock_milli FROM items WHERE id=1").fetchone()
        movement = conn.execute("SELECT * FROM inventory_movements ORDER BY id DESC LIMIT 1").fetchone()
    assert row["stock_milli"] == 4000
    assert movement["movement_type"] == "AJUSTE" and movement["delta_milli"] == -1000
    assert movement["stock_after_milli"] == 4000 and movement["reason"] == "Conteo físico de cierre"

    history = client.get("/inventario/movimientos")
    assert history.status_code == 200 and "Conteo físico de cierre" in history.text
    export = client.get("/inventario/movimientos.csv")
    assert export.status_code == 200 and "Existencia posterior" in export.text and "dueno" in export.text
    assert b"\r\n" in export.content and b"\\r\\n" not in export.content
    csv_rows = list(csv.reader(io.StringIO(export.content.decode("utf-8-sig"))))
    assert csv_rows[0] == ["Fecha UTC", "Artículo", "Categoría", "Unidad", "Tipo", "Cambio", "Existencia anterior", "Existencia posterior", "Actor", "Motivo", "Origen", "Referencia"]
    assert csv_rows[1][5:8] == ["5", "0", "5"] and csv_rows[2][5:8] == ["-1", "5", "4"]
    with client.app.state.db.connect() as conn:
        import sqlite3
        with pytest.raises(sqlite3.IntegrityError):
            conn.execute("UPDATE inventory_movements SET reason='editado' WHERE id=1")
        with pytest.raises(sqlite3.IntegrityError):
            conn.execute("DELETE FROM inventory_movements WHERE id=1")


def test_web_sale_idempotence_actor_and_auditable_cancellation(tmp_path: Path):
    import re

    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="Café", category="Producto", stock=5, price="10")
    client.post("/turno/abrir", data={"csrf": token(client.get("/")), "opening_cash": "0"}, follow_redirects=True)
    page = client.get("/ventas")
    sale_key_match = re.search(r'name="sale_key" value="([^"]+)"', page.text)
    assert sale_key_match
    sale_key = sale_key_match.group(1)
    form = {"csrf": token(page), "sale_key": sale_key, "quantity_1": "2", "payment_method": "EFECTIVO"}
    first = client.post("/ventas/nueva", data=form, follow_redirects=True)
    assert first.status_code == 200 and "Venta registrada" in first.text
    replay = client.post("/ventas/nueva", data=form, follow_redirects=True)
    assert replay.status_code == 200 and "Venta registrada" in replay.text
    with client.app.state.db.connect() as conn:
        sale = conn.execute("SELECT * FROM sales").fetchone()
        item = conn.execute("SELECT stock_milli FROM items WHERE id=1").fetchone()
        count = conn.execute("SELECT COUNT(*) FROM sales").fetchone()[0]
    assert count == 1 and item["stock_milli"] == 3000
    assert sale["user_id"] == 1 and sale["request_key"] == sale_key and sale["inventory_logged"] == 1

    # Una nueva instancia de la aplicación debe recordar la clave y su resultado.
    old_config = client.app.state.config
    client.app.state.db.close()
    restarted_db = Database(old_config.database_path)
    restarted = TestClient(create_app(old_config, restarted_db))
    setup_and_login(restarted)
    restarted_page = restarted.get("/ventas")
    restarted_form = dict(form, csrf=token(restarted_page))
    replay_after_restart = restarted.post("/ventas/nueva", data=restarted_form, follow_redirects=True)
    assert replay_after_restart.status_code == 200 and "Venta registrada" in replay_after_restart.text
    with restarted_db.connect() as conn:
        assert conn.execute("SELECT COUNT(*) FROM sales").fetchone()[0] == 1
        assert conn.execute("SELECT stock_milli FROM items WHERE id=1").fetchone()[0] == 3000

    changed_intent = dict(restarted_form, quantity_1="1")
    rejected = restarted.post("/ventas/nueva", data=changed_intent, follow_redirects=True)
    assert "ya registró otra operación" in rejected.text
    with restarted_db.connect() as conn:
        assert conn.execute("SELECT COUNT(*) FROM sales").fetchone()[0] == 1
        assert conn.execute("SELECT stock_milli FROM items WHERE id=1").fetchone()[0] == 3000

    page = restarted.get("/ventas")
    cancelled = restarted.post("/ventas/1/anular", data={
        "csrf": token(page), "reason": "Error de cantidad; anulo para volver a registrar",
    }, follow_redirects=True)
    assert cancelled.status_code == 200 and "Venta de escritorio anulada" in cancelled.text
    with restarted_db.connect() as conn:
        sale = conn.execute("SELECT cancelled_at,cancellation_reason,cancelled_by FROM sales WHERE id=1").fetchone()
        item = conn.execute("SELECT stock_milli FROM items WHERE id=1").fetchone()
        movements = conn.execute("SELECT movement_type,delta_milli,actor,reason FROM inventory_movements WHERE reference='web-sale-cancel:1'").fetchall()
        active_total = conn.execute("SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE cancelled_at IS NULL").fetchone()[0]
    assert sale["cancelled_at"] and sale["cancellation_reason"].startswith("Error de cantidad") and sale["cancelled_by"] == 1
    assert item["stock_milli"] == 5000
    assert len(movements) == 1 and movements[0]["movement_type"] == "ANULACION_VENTA"
    assert movements[0]["delta_milli"] == 2000 and movements[0]["actor"] == "dueno"
    assert "Error de cantidad" in movements[0]["reason"] and active_total == 0
