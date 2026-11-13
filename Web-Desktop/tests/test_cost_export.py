from test_inventory_pos import client_for, setup_and_login, token


def fixture(client):
    csrf = token(client.get('/herramientas/fichas'))
    client.post('/herramientas/fichas/nueva', data={'csrf': csrf, 'name': 'Pizza'})
    client.post('/herramientas/fichas/1/ajustes', data={'csrf': csrf, 'name': 'Pizza', 'margin': '25', 'margin_unit': 'PORCENTAJE'})
    for description, qty, unit, cost, currency in [('Harina','2','kg','10','CUP'),('Queso','1.5','kg','2','USD')]:
        client.post('/herramientas/fichas/1/partidas', data={'csrf': csrf, 'description': description, 'category': 'MATERIAS', 'quantity': qty, 'unit': unit, 'unit_cost': cost, 'currency': currency})
    return csrf


def test_exportacion_producto_atomica_y_venta_con_receta(tmp_path):
    client = client_for(tmp_path); setup_and_login(client); csrf = fixture(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,unit,stock,cost_cents) VALUES('harina','Insumo','kg',10,1000)")
    fail = client.post('/herramientas/fichas/1/exportar-catalogo', data={'csrf': csrf, 'category': 'Producto', 'rate': ''}, follow_redirects=True)
    assert 'tasa mayor que cero' in fail.text
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM items').fetchone()[0] == 1
        assert conn.execute('SELECT COUNT(*) FROM recipes').fetchone()[0] == 0
    response = client.post('/herramientas/fichas/1/exportar-catalogo', data={'csrf': csrf, 'category': 'Producto', 'rate': '100'}, follow_redirects=True)
    assert 'exportada como producto con receta' in response.text
    with client.app.state.db.connect() as conn:
        p = conn.execute("SELECT * FROM items WHERE name='Pizza'").fetchone()
        assert (p['category'], p['stock'], p['cost_cents'], p['sale_price_cents']) == ('Producto', 0, 32000, 40000)
        recipe = conn.execute('SELECT ingredient_id,quantity_milli FROM recipes WHERE product_id=? ORDER BY ingredient_id', (p['id'],)).fetchall()
        assert [(r['ingredient_id'], r['quantity_milli']) for r in recipe] == [(1, 2000), (2, 1500)]
        assert conn.execute("SELECT stock,cost_cents FROM items WHERE name='Queso'").fetchone()[:] == (0, 20000)
    duplicate = client.post('/herramientas/fichas/1/exportar-catalogo', data={'csrf': csrf, 'category': 'Producto', 'rate': '100'}, follow_redirects=True)
    assert 'ya se exportó' in duplicate.text
    with client.app.state.db.transaction() as conn:
        conn.execute("UPDATE items SET stock=5 WHERE name IN ('Pizza','Queso')")
    client.post('/turno/abrir', data={'csrf': csrf, 'opening_cash': '0'})
    client.post('/ventas/nueva', data={'csrf': csrf, f'quantity_{p["id"]}': '1', 'payment_method': 'EFECTIVO'})
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM sales').fetchone()[0] == 1
        assert conn.execute("SELECT stock FROM items WHERE name='harina'").fetchone()[0] == 8
        assert conn.execute("SELECT stock FROM items WHERE name='Queso'").fetchone()[0] == 3.5
        assert conn.execute("SELECT stock FROM items WHERE name='Pizza'").fetchone()[0] == 4


def test_exportar_servicio_y_conservar_otras_partidas(tmp_path):
    client = client_for(tmp_path); setup_and_login(client); csrf = fixture(client)
    result = client.post('/herramientas/fichas/1/exportar-catalogo', data={'csrf': csrf, 'category': 'Servicio', 'rate': '100'}, follow_redirects=True)
    assert 'exportada como servicio' in result.text
    with client.app.state.db.connect() as conn:
        service = conn.execute("SELECT * FROM items WHERE name='Pizza' AND category='Servicio'").fetchone()
        assert service['sale_price_cents'] == 40000
        assert conn.execute('SELECT COUNT(*) FROM recipes WHERE product_id=?', (service['id'],)).fetchone()[0] == 2
        assert conn.execute('SELECT COUNT(*) FROM cost_sheet_lines').fetchone()[0] == 2
    assert client.post('/herramientas/fichas/1/exportar-catalogo', data={'csrf': 'bad', 'category': 'Servicio'}).status_code == 403


def test_exportacion_rechaza_nombres_cortos_de_articulo_e_insumo(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    csrf = fixture(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("UPDATE cost_sheets SET name='AB' WHERE id=1")
    short_item = client.post("/herramientas/fichas/1/exportar-catalogo", data={
        "csrf": csrf, "category": "Producto", "rate": "100",
    }, follow_redirects=True)
    assert "El nombre del artículo exportado debe tener entre 3 y 80 caracteres" in short_item.text
    with client.app.state.db.transaction() as conn:
        conn.execute("UPDATE cost_sheets SET name='Pizza' WHERE id=1")
        conn.execute("UPDATE cost_sheet_lines SET description='AB' WHERE id=1")
    short_ingredient = client.post("/herramientas/fichas/1/exportar-catalogo", data={
        "csrf": csrf, "category": "Producto", "rate": "100",
    }, follow_redirects=True)
    assert "El nombre de cada insumo creado debe tener entre 3 y 80 caracteres" in short_ingredient.text
    with client.app.state.db.connect() as conn:
        assert conn.execute("SELECT COUNT(*) FROM items").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM cost_sheet_exports").fetchone()[0] == 0


def test_unidad_incompatible_y_cantidad_fuera_de_rango_revierten(tmp_path):
    client = client_for(tmp_path); setup_and_login(client); csrf = fixture(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,unit,stock) VALUES('Harina','Insumo','litro',10)")
    result = client.post('/herramientas/fichas/1/exportar-catalogo', data={'csrf': csrf, 'category': 'Producto', 'rate': '100'}, follow_redirects=True)
    assert 'unidad de una materia prima no coincide' in result.text
    with client.app.state.db.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM items').fetchone()[0] == 1
        assert conn.execute('SELECT COUNT(*) FROM cost_sheet_exports').fetchone()[0] == 0
