from __future__ import annotations

import re
import zipfile
from io import BytesIO
from xml.etree import ElementTree as ET

from dshalev.xlsx import build_xlsx
from test_inventory_pos import add_item, client_for, setup_and_login

NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def sheet_names(content: bytes) -> list[str]:
    with zipfile.ZipFile(BytesIO(content)) as archive:
        root = ET.fromstring(archive.read("xl/workbook.xml"))
        return [sheet.attrib["name"] for sheet in root.findall("x:sheets/x:sheet", NS)]


def test_xlsx_writer_creates_valid_sheets_and_keeps_text_as_text():
    content = build_xlsx([("Hoja/ventas", [["Nombre", "Importe"], ["=HYPERLINK(\"x\")", 12.5]]),
                          ("Hoja/ventas", [["Texto", "=2+2"]])])
    with zipfile.ZipFile(BytesIO(content)) as archive:
        assert archive.testzip() is None
        assert "[Content_Types].xml" in archive.namelist()
        sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
        string_cell = sheet.find(".//x:c[@r='A2']", NS)
        assert string_cell is not None and string_cell.attrib.get("t") == "inlineStr"
        assert string_cell.findtext("x:is/x:t", namespaces=NS) == '=HYPERLINK("x")'
        assert sheet.find(".//x:f", NS) is None
    assert sheet_names(content) == ["Hoja ventas", "Hoja ventas (2)"]


def test_sales_inventory_customers_xlsx_and_csv_bom(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="=Peligro", category="Producto", stock=4, price="12.50")
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO customers(name,phone,email,notes) VALUES('Ana','=SUM(1,1)','ana@example.test','Cliente')")

    inventory = client.get("/inventario.xlsx")
    customers = client.get("/clientes.xlsx")
    assert inventory.status_code == customers.status_code == 200
    assert inventory.headers["content-type"].startswith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    assert sheet_names(inventory.content) == ["Inventario"]
    assert sheet_names(customers.content) == ["Clientes"]
    with zipfile.ZipFile(BytesIO(inventory.content)) as archive:
        sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
        # Catalog values are inline strings; an attacker-controlled leading '=' is never a formula.
        cell = sheet.find(".//x:c[@r='A2']", NS)
        assert cell is not None and cell.attrib.get("t") == "inlineStr"
    with zipfile.ZipFile(BytesIO(customers.content)) as archive:
        sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
        cell = sheet.find(".//x:c[@r='B2']", NS)
        assert cell is not None and cell.attrib.get("t") == "inlineStr"
        assert sheet.find(".//x:f", NS) is None

    csv = client.get("/inventario.csv")
    assert csv.status_code == 200 and csv.content.startswith(b"\xef\xbb\xbf")

    home = client.get("/")
    client.post("/turno/abrir", data={"csrf": re.search(r'name="csrf" value="([^"]+)', home.text).group(1), "opening_cash": "0"}, follow_redirects=True)
    sales_page = client.get("/ventas")
    csrf = re.search(r'name="csrf" value="([^"]+)', sales_page.text).group(1)
    client.post("/ventas/nueva", data={"csrf": csrf, "quantity_1": "1", "payment_method": "EFECTIVO"}, follow_redirects=True)
    report = client.get("/reportes/ventas.xlsx")
    assert report.status_code == 200
    assert sheet_names(report.content) == ["Resumen", "Ventas"]
    with zipfile.ZipFile(BytesIO(report.content)) as archive:
        summary = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
        assert summary.findtext(".//x:c[@r='B5']/x:v", namespaces=NS) == "12.5"
        rows = ET.fromstring(archive.read("xl/worksheets/sheet2.xml"))
        values = [node.text for node in rows.findall(".//x:is/x:t", NS)]
        assert any("Peligro" in value for value in values)
        assert rows.find(".//x:f", NS) is None


def test_csv_protege_texto_en_las_tres_exportaciones(tmp_path):
    import csv
    from io import StringIO
    from dshalev.app import csv_text

    for value in ("=1+1", "+cmd", "-cmd", "@formula", "  =1+1", "\ttexto", "\r\ntexto"):
        assert csv_text(value) == "'" + value
    for value in ("", "  normal", "nombre, con coma", "línea\nnueva"):
        assert csv_text(value) == value

    client = client_for(tmp_path)
    setup_and_login(client)
    add_item(client, name="=Peligro", category="Producto", stock=4, price="12.50")
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO customers(name,phone,email,notes) VALUES(?,?,?,?)",
                     ("  @cliente", "+5351234567", "a@example.test", "normal, con coma"))
    home = client.get("/")
    csrf = re.search(r'name="csrf" value="([^"]+)', home.text).group(1)
    client.post("/turno/abrir", data={"csrf": csrf, "opening_cash": "0"})
    csrf = re.search(r'name="csrf" value="([^"]+)', client.get("/ventas").text).group(1)
    client.post("/ventas/nueva", data={"csrf": csrf, "quantity_1": "1", "payment_method": "EFECTIVO", "customer_id": "1"})

    def filas(url):
        response = client.get(url)
        assert response.status_code == 200
        return list(csv.reader(StringIO(response.content.decode("utf-8-sig"))))

    inventory = filas("/inventario.csv")
    assert inventory[1][0] == "'=Peligro"
    assert inventory[1][3:5] == ["3", "12.50"]
    customers = filas("/clientes.csv")
    assert customers[1][:4] == ["'  @cliente", "'+5351234567", "a@example.test", "normal, con coma"]
    sales = filas("/reportes/ventas.csv")
    assert sales[1][2] == "'  @cliente"
    assert sales[1][3].startswith("'=Peligro")
    assert sales[1][-1] == "12.50"
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT name FROM items WHERE id=1").fetchone()[0] == "=Peligro"
