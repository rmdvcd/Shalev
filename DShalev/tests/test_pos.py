from test_inventory_pos import client_for, setup_and_login, token


def test_catalogo_pos_autenticado_filtra_y_limita(tmp_path):
    client = client_for(tmp_path)
    assert client.get('/api/v1/pos/catalogo').status_code == 401
    setup_and_login(client)
    with client.app.state.db.transaction() as conn:
        for index in range(35):
            conn.execute("INSERT INTO items(name,category,stock,sale_price_cents,cost_cents) VALUES(?, 'Producto', 3, 1250, 500)", (f'Artículo {index:02}',))
        conn.execute("INSERT INTO items(name,category,stock,sale_price_cents) VALUES('Oferta 100%', 'Producto', 1, 100)")
        conn.execute("INSERT INTO items(name,category,stock,sale_price_cents) VALUES('Servicio', 'Servicio', 0, 300)")
        conn.execute("INSERT INTO items(name,category,stock) VALUES('Sin existencia', 'Producto', 0)")
        conn.execute("INSERT INTO items(name,category,stock) VALUES('Ingrediente', 'Insumo', 5)")
        conn.execute("INSERT INTO items(name,category,stock,active) VALUES('Retirado', 'Producto', 5, 0)")
    response = client.get('/api/v1/pos/catalogo')
    assert response.status_code == 200 and response.headers['cache-control'] == 'no-store'
    assert len(response.json()['items']) == 30 and response.json()['has_more']
    assert 'cost_cents' not in response.json()['items'][0]
    assert [item['name'] for item in client.get('/api/v1/pos/catalogo', params={'q': '%'}).json()['items']] == ['Oferta 100%']
    assert [item['name'] for item in client.get('/api/v1/pos/catalogo', params={'q': 'Servicio'}).json()['items']] == ['Servicio']
    for query in ('Sin existencia', 'Ingrediente', 'Retirado', '_'):
        assert client.get('/api/v1/pos/catalogo', params={'q': query}).json()['items'] == []


def test_punto_de_venta_mantiene_validaciones_servidor(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,stock,sale_price_cents) VALUES('Café', 'Producto', 2, 1250)")
    home = client.get('/')
    client.post('/turno/abrir', data={'csrf': token(home), 'opening_cash': '0'})
    page = client.get('/ventas')
    assert 'id="pos-app"' in page.text and 'id="pos-fallback"' in page.text
    assert '/static/pos.js' in page.text
    rejected = client.post('/ventas/nueva', data={'csrf': token(page), 'quantity_1': '3', 'payment_method': 'EFECTIVO'}, follow_redirects=True)
    assert 'No hay existencia suficiente' in rejected.text
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM sales').fetchone()[0] == 0
        assert conn.execute('SELECT stock FROM items WHERE id=1').fetchone()[0] == 2
    # El precio enviado por el cliente no se usa; se calcula con el catálogo vigente.
    client.post('/ventas/nueva', data={'csrf': token(page), 'quantity_1': '2', 'payment_method': 'EFECTIVO', 'price': '0'})
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT total_cents FROM sales').fetchone()[0] == 2500
        assert conn.execute('SELECT stock FROM items WHERE id=1').fetchone()[0] == 0


def test_precio_cambiado_impide_cobro_hasta_revisar(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,stock,sale_price_cents) VALUES('Café', 'Producto', 3, 1300)")
    client.post('/turno/abrir', data={'csrf': token(client.get('/')), 'opening_cash': '0'})
    csrf = token(client.get('/ventas'))
    form = {'csrf': csrf, 'quantity_1': '1', 'payment_method': 'EFECTIVO', 'pos_prices': '{"1":1250}'}
    response = client.post('/ventas/nueva', data=form, follow_redirects=True)
    assert 'Un precio cambió' in response.text
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM sales').fetchone()[0] == 0
        assert conn.execute('SELECT stock FROM items').fetchone()[0] == 3
    assert client.get('/api/v1/pos/catalogo?ids=1').json()['items'][0]['sale_price_cents'] == 1300
    assert client.get('/api/v1/pos/catalogo?ids=1,mal').status_code == 422
    form['pos_prices'] = '{"1":1300}'
    client.post('/ventas/nueva', data=form)
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT total_cents FROM sales').fetchone()[0] == 1300
