from decimal import Decimal

import pytest

from dshalev.cost_sheets import calculate, parse_number
from dshalev.database import Database
from test_inventory_pos import client_for, setup_and_login, token


def test_redondeo_partidas_margen_moneda_y_validacion():
    lines = [
        {'quantity': '1', 'unit_cost': '0.005', 'category': 'MATERIAS', 'currency': 'CUP'},
        {'quantity': '1', 'unit_cost': '0.005', 'category': 'MATERIAS', 'currency': 'CUP'},
        {'quantity': '2.5', 'unit_cost': '1.20', 'category': 'TRABAJO', 'currency': 'USD'},
    ]
    result = calculate(lines, Decimal('50'), 'PORCENTAJE')
    assert result['totals'] == {'CUP': Decimal('0.02'), 'USD': Decimal('3.00')}
    assert result['prices'] == {'CUP': Decimal('0.03'), 'USD': Decimal('4.50')}
    assert calculate(lines, Decimal('2'), 'FACTOR')['prices']['USD'] == Decimal('6.00')
    assert calculate(lines, None, 'FACTOR')['prices'] is None
    assert parse_number('1,2500') == Decimal('1.2500')
    for text in ('-1', '1e5', 'nan', '1.12345', '1234567890123'):
        with pytest.raises(ValueError):
            parse_number(text)


def test_fichas_operaciones_aislamiento_y_respaldo(tmp_path):
    client = client_for(tmp_path)
    assert client.get('/herramientas/fichas', follow_redirects=False).status_code == 303
    setup_and_login(client)
    page = client.get('/herramientas/fichas')
    csrf = token(page)
    assert client.post('/herramientas/fichas/nueva', data={'name': 'Sin CSRF'}).status_code == 403
    created = client.post('/herramientas/fichas/nueva', data={'csrf': csrf, 'name': 'Pizza'}, follow_redirects=False)
    assert created.headers['location'] == '/herramientas/fichas/1'
    data = {'csrf': csrf, 'description': 'Harina', 'category': 'MATERIAS', 'quantity': '2.5', 'unit': 'kg', 'unit_cost': '12.00', 'currency': 'CUP'}
    assert client.post('/herramientas/fichas/1/partidas', data=data).status_code == 200
    client.post('/herramientas/fichas/1/ajustes', data={'csrf': csrf, 'name': 'Pizza familiar', 'margin': '25', 'margin_unit': 'PORCENTAJE'})
    page = client.get('/herramientas/fichas/1')
    assert 'Pizza familiar' in page.text and '30.00 CUP' in page.text and '37.50' in page.text
    bad = {**data, 'quantity': '-2'}
    client.post('/herramientas/fichas/1/partidas', data=bad)
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM cost_sheet_lines').fetchone()[0] == 1
        assert conn.execute('SELECT COUNT(*) FROM items').fetchone()[0] == 0

    backup = client.app.state.db.export_backup('Contraseña segura de respaldo 2026!')
    restored = Database(tmp_path / 'restored' / 'test.db')
    restored.initialize()
    restored.restore_backup(backup, 'Contraseña segura de respaldo 2026!')
    with restored.connect() as conn:
        assert conn.execute('SELECT name FROM cost_sheets').fetchone()[0] == 'Pizza familiar'
        assert conn.execute('SELECT description FROM cost_sheet_lines').fetchone()[0] == 'Harina'
    restored.close()

    client.post('/herramientas/fichas/nueva', data={'csrf': csrf, 'name': 'Otra'})
    client.post('/herramientas/fichas/2/partidas/1/eliminar', data={'csrf': csrf})
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM cost_sheet_lines').fetchone()[0] == 1
    client.post('/herramientas/fichas/1/eliminar', data={'csrf': csrf})
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM cost_sheets').fetchone()[0] == 1
        assert conn.execute('SELECT COUNT(*) FROM cost_sheet_lines').fetchone()[0] == 0


def test_migracion_conserva_los_datos_de_base_anterior(tmp_path):
    path = tmp_path / 'legacy' / 'test.db'
    old = Database(path)
    old.initialize()
    with old.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,stock) VALUES('Datos previos','Producto',7)")
        conn.execute('DROP TABLE cost_sheet_exports')
        conn.execute('DROP TABLE cost_sheet_lines')
        conn.execute('DROP TABLE cost_sheets')
        conn.execute("UPDATE app_meta SET value='6' WHERE key='schema_version'")
    old.close()
    upgraded = Database(path)
    upgraded.initialize()
    with upgraded.connect() as conn:
        assert conn.execute('SELECT stock FROM items WHERE name=?', ('Datos previos',)).fetchone()[0] == 7
        assert conn.execute('SELECT COUNT(*) FROM cost_sheets').fetchone()[0] == 0
        assert conn.execute("SELECT value FROM app_meta WHERE key='schema_version'").fetchone()[0] == '10'
    upgraded.close()


def test_editar_y_exportar_ficha_en_excel_sin_formulas(tmp_path):
    import zipfile
    from io import BytesIO
    from xml.etree import ElementTree as ET

    client = client_for(tmp_path)
    setup_and_login(client)
    csrf = token(client.get('/herramientas/fichas'))
    client.post('/herramientas/fichas/nueva', data={'csrf': csrf, 'name': '=Pizza'})
    data = {'csrf': csrf, 'description': '=Harina', 'category': 'MATERIAS', 'quantity': '2.5', 'unit': 'kg', 'unit_cost': '12', 'currency': 'CUP'}
    client.post('/herramientas/fichas/1/partidas', data=data)
    client.post('/herramientas/fichas/1/partidas/1/editar', data={**data, 'quantity': '3', 'currency': 'USD'})
    assert '36.00 USD' in client.get('/herramientas/fichas/1').text
    client.post('/herramientas/fichas/1/partidas/1/editar', data={**data, 'quantity': '-3'})
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT quantity,currency FROM cost_sheet_lines').fetchone()[:] == ('3', 'USD')
    assert client.post('/herramientas/fichas/1/partidas/1/editar', data=data | {'csrf': 'invalid'}).status_code == 403
    response = client.get('/herramientas/fichas/1/exportar.xlsx')
    assert response.status_code == 200 and response.headers['cache-control'] == 'no-store'
    with zipfile.ZipFile(BytesIO(response.content)) as archive:
        sheet = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
        ns = {'x': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
        assert not sheet.findall('.//x:f', ns)
        texts = [element.text for element in sheet.findall('.//x:is/x:t', ns)]
        assert '=Pizza' in texts and '=Harina' in texts and '36.00' in texts
    assert client.get('/herramientas/fichas/404/exportar.xlsx').status_code == 404
