from datetime import datetime, timezone, timedelta
from pathlib import Path

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
    # Los tres gráficos del panel son ahora SVG (área de ventas, área de venta y costo, dona de existencias), como
    # Android; ya no hay barras `<progress>` en Inicio.
    assert page.count('<progress ') == 0
    assert page.count('class="grafico ') == 3
    assert 'aria-label="Ventas de los últimos 7 días' in page
    assert '13.50 CUP' in page and '113.50 CUP' not in page


def test_navegacion_y_licencia_sin_falsa_activacion(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    page = client.get('/').text
    assert 'href="/servicios"' in page and 'href="/ajustes"' in page
    assert 'href="/herramientas"' not in page and 'href="/licencia"' not in page
    ajustes = client.get('/ajustes')
    assert 'href="/herramientas"' in ajustes.text and 'href="/licencia"' in ajustes.text
    assert 'href="/equipo"' in ajustes.text and 'href="/respaldo"' in ajustes.text
    assert 'Fotos / APK' not in page
    assert 'id="theme-select"' in page
    assert 'href="/distribucion"' in client.get('/equipo').text
    lic_page = client.get('/licencia').text
    assert 'Huella de la instalación' in lic_page


def test_icono_de_marca_y_favicon_coinciden_con_launcher_android(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    page = client.get('/')
    assert '<link rel="icon" type="image/png" href="/static/favicon.png">' in page.text
    assert '<img class="brand-mark" src="/static/favicon.png" alt="">' in page.text
    icon = client.get('/static/favicon.png')
    assert icon.status_code == 200
    assert icon.headers['content-type'].startswith('image/png')
    assert icon.content == (Path(__file__).parents[1] / 'dshalev' / 'static' / 'favicon.png').read_bytes()


def test_perfil_del_negocio_guarda_datos_y_valida_ci_y_telefonos(tmp_path):
    client = client_for(tmp_path)
    setup_and_login(client)
    page = client.get('/perfil')
    assert page.status_code == 200
    assert 'href="/perfil"' in client.get('/ajustes').text
    response = client.post('/perfil', data={
        'csrf': token(page), 'nombre': 'María', 'apellidos': 'Pérez González',
        'ci': '85010112345', 'telefonos': '52345678\n61234567', 'nombre_negocio': 'Mi tienda', 'nombre_local': 'Local Centro',
    }, follow_redirects=True)
    assert response.status_code == 200 and 'Perfil guardado correctamente' in response.text
    with client.app.state.db.connect() as conn:
        from dshalev.perfil import cargar
        saved = cargar(conn)
    assert saved['nombre'] == 'María' and saved['apellidos'] == 'Pérez González'
    assert saved['ci'] == '85010112345' and saved['telefonos'] == ['+5352345678', '+5361234567']
    assert saved['nombre_negocio'] == 'Mi tienda' and saved['nombre_local'] == 'Local Centro'
    bad = client.get('/perfil')
    rejected = client.post('/perfil', data={
        'csrf': token(bad), 'nombre': 'María', 'apellidos': 'Pérez',
        'ci': '85133212345', 'telefonos': '12345678', 'nombre_negocio': 'Mi tienda',
    })
    assert rejected.status_code == 422 and 'fecha de nacimiento válida' in rejected.text
    assert client.get('/').text.find('Mi tienda') >= 0


def test_inicio_rankings_agregan_servicios_clientes_empleados_y_locales(tmp_path):
    import json
    client = client_for(tmp_path)
    setup_and_login(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO app_meta(key,value) VALUES('local_name','Sucursal Norte')")
        conn.execute("INSERT INTO items(name,category,stock,sale_price_cents,cost_cents) VALUES('Corte','Servicio',0,1000,200)")
        conn.execute("INSERT INTO shifts(opening_cash_cents) VALUES(0)")
        conn.execute("INSERT INTO customers(name) VALUES('Cliente web')")
        conn.execute("INSERT INTO employees(slot,name) VALUES(1,'Vendedor web')")
        conn.execute("INSERT INTO sales(shift_id,total_cents,payment_method,employee_id,customer_id) VALUES(1,1000,'TRANSFERENCIA',1,1)")
        conn.execute("INSERT INTO sale_lines(sale_id,item_id,item_name,quantity,unit_price_cents) VALUES(1,1,'Corte',1,1000)")
        now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
        payload = {'venta': {'local': 'Sucursal Norte', 'detalles': [{'clase': 'SERVICIO', 'nombre': 'Corte móvil', 'cantidad': 2, 'precioUnitarioCent': 500}], 'transaccion': {'clienteNombre': 'Cliente móvil', 'clienteCi': '85010112345', 'importeCent': 1000}}}
        conn.execute("INSERT INTO sync_sales(uuid,employee_id,employee_name,shift_uuid,sale_at_ms,payment_method,total_cents,payload_json) VALUES('rank-test',7,'Ana Móvil','shift-mobile',?,'TRANSFERENCIA',1000,?)", (now_ms,json.dumps(payload)))
    page = client.get('/').text
    assert 'Corte' in page and 'Corte móvil' in page
    assert 'Cliente web' in page and 'Cliente móvil' in page
    assert 'Vendedor web' in page and 'Ana Móvil' in page
    assert page.count('<li><strong>Sucursal Norte</strong>') == 1


def test_logo_de_perfil_se_guarda_y_aparece_en_salidas_publicas(tmp_path):
    import base64
    from dshalev.png import Lienzo, decodificar_png_rgb
    client = client_for(tmp_path)
    setup_and_login(client)
    with client.app.state.db.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,stock,sale_price_cents,cost_cents) VALUES('Producto logo','Producto',4,100,20)")
    page = client.get('/perfil')
    logo = Lienzo(8, 8, (40, 90, 160)).png()
    upload = client.post('/perfil/logo', json={'csrf': token(page), 'image': 'data:image/png;base64,' + base64.b64encode(logo).decode()})
    assert upload.status_code == 200 and upload.json()['ok']
    response = client.get('/perfil')
    assert 'data:image/png;base64,' in response.text and 'perfil-logo-preview' in response.text
    assert client.get('/inventario/lista-precios').text.count('data:image/png;base64,') == 1
    printed = client.get('/inventario/tarjetas/imprimir')
    assert 'data:image/png;base64,' in printed.text
    pdf = client.get('/inventario/lista-precios.pdf')
    assert pdf.status_code == 200 and b'/Subtype /Image' in pdf.content and b'/Im1 Do' in pdf.content
    import io, zipfile
    package = zipfile.ZipFile(io.BytesIO(client.get('/inventario/tarjetas.png').content))
    card_png = package.read(next(name for name in package.namelist() if name.endswith('.png')))
    width, height, pixels = decodificar_png_rgb(card_png)
    assert (width, height) == (1080, 1080) and pixels[(50 * width + 900) * 3:(50 * width + 900) * 3 + 3] == bytes((40, 90, 160))
    with client.app.state.db.connect() as conn:
        from dshalev.perfil import cargar_logo
        loaded = cargar_logo(conn)
    assert loaded == logo and decodificar_png_rgb(loaded)[0:2] == (8, 8)
    deleted = client.post('/perfil/logo/eliminar', json={'csrf': token(response)})
    assert deleted.status_code == 200 and deleted.json()['ok']
