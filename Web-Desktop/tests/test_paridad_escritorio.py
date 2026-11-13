from datetime import datetime, timezone, timedelta

from test_inventory_pos import client_for, setup_and_login, token


def test_servicios_crea_filtra_y_no_mezcla_productos(tmp_path):
    client = client_for(tmp_path)
    assert client.get('/servicios', follow_redirects=False).status_code == 303
    setup_and_login(client)
    page = client.get('/servicios')
    response = client.post('/inventario/nuevo', data={'csrf': token(page), 'origen': 'servicios', 'category': 'Servicio', 'name': 'Corte', 'unit': 'servicio', 'stock': '0', 'low_stock': '0', 'critical_stock': '0', 'sale_price': '12.50', 'cost': '2'}, follow_redirects=True)
    assert response.url.path == '/servicios' and 'Corte' in response.text
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,stock) VALUES ('Corte de pan','Producto',3)")
    page = client.get('/servicios?q=Corte')
    assert 'Corte de pan' not in page.text
    assert 'Corte' in page.text
    assert client.get('/api/v1/pos/catalogo?q=Corte').json()['items'][0]['category'] == 'Servicio'


def test_herramientas_conversor_y_tasa_validadas(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    page = client.get('/herramientas')
    csrf = token(page)
    assert client.post('/herramientas', data={'modo': 'tasa'}).status_code == 403
    def calc(**kwargs):
        return client.post('/herramientas', data={'csrf': csrf, 'calcular': '1', **kwargs}).text
    assert '750.00 CUP' in calc(modo='convertir', origen='USD', importe='2.5', tasa='300')
    assert '2.50 USD' in calc(modo='convertir', origen='CUP', importe='750', tasa='300')
    assert '300 CUP por USD' in calc(modo='tasa', cup='750', usd='2.5')
    assert 'mayores que cero' in calc(modo='convertir', origen='USD', importe='1', tasa='0')
    assert 'válidos' in calc(modo='tasa', cup='1e9', usd='1')
    assert 'Fichas de costo' in page.text and 'href="/herramientas/fichas"' in page.text


def test_estadisticas_reales_de_escritorio_y_limites(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,stock) VALUES('Pan', 'Producto', 10)")
        conn.execute("INSERT INTO items(name,category,stock) VALUES('Café', 'Producto', 10)")
        conn.execute("INSERT INTO items(name,category,stock) VALUES('Corte', 'Servicio', 0)")
        conn.execute("INSERT INTO shifts(opening_cash_cents) VALUES(0)")
        conn.execute("INSERT INTO sales(shift_id,total_cents,payment_method) VALUES(1,1100,'EFECTIVO')")
        conn.execute("INSERT INTO sale_lines(sale_id,item_id,item_name,quantity,unit_price_cents) VALUES(1,1,'Pan',3,200)")
        conn.execute("INSERT INTO sale_lines(sale_id,item_id,item_name,quantity,unit_price_cents) VALUES(1,2,'Café',1,500)")
        old = (datetime.now(timezone.utc) - timedelta(days=20)).isoformat()
        conn.execute("INSERT INTO sales(shift_id,total_cents,payment_method,created_at) VALUES(1,20000,'EFECTIVO',?)", (old,))
        conn.execute("INSERT INTO sale_lines(sale_id,item_id,item_name,quantity,unit_price_cents) VALUES(2,2,'Café',40,500)")
        now = int(datetime.now(timezone.utc).timestamp() * 1000)
        conn.execute("INSERT INTO sync_sales(uuid,employee_id,shift_uuid,sale_at_ms,payment_method,total_cents,payload_json) VALUES('movil',1,'turno',?,'EFECTIVO',250,'{}')", (now,))
        conn.execute("INSERT INTO sync_sales(uuid,employee_id,shift_uuid,sale_at_ms,payment_method,total_cents,payload_json,cancelled_at) VALUES('anulada',1,'turno',?,'EFECTIVO',10000,'{}',?)", (now, now))
    page = client.get('/').text
    assert 'Ventas por día' in page and 'Top 3 artículos' in page
    assert '3 unidades · 6 CUP' in page and '1 unidades · 5 CUP' in page
    assert '40 unidades' not in page
    assert 'No incluye ventas móviles' in page
    assert page.count('<progress ') == 7
    assert '13.50 CUP' in page and '113.50 CUP' not in page


def test_navegacion_y_licencia_sin_falsa_activacion(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    page = client.get('/').text
    assert 'href="/servicios"' in page and 'href="/herramientas"' in page and 'href="/licencia"' in page
    assert 'Fotos / APK' not in page
    assert 'id="theme-select"' in page
    assert 'href="/distribucion"' in client.get('/equipo').text
    assert 'no están implementadas' in client.get('/licencia').text
