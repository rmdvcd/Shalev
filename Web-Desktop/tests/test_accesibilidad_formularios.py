"""Cobertura de las mejoras de accesibilidad/usabilidad del plan UI/UX/IX (2026-11-11):

- DS-2: enlace «Saltar al contenido», `<main id="contenido">` y pie con landmark.
- DS-3: forms.js global contra el doble envío (cargado en todas las páginas con sesión).
- DS-5: errores de validación marcados junto al campo (aria-invalid + mensaje con icono).
- DS-8: página de ayuda consistente en la misma posición del menú de todas las páginas.
"""
from __future__ import annotations

from pathlib import Path

from tests.test_app import CSRF_RE, create_admin, login, make_client


def token(response) -> str:
    return CSRF_RE.search(response.text).group(1)


def alta_articulo(client, **cambios):
    datos = {
        "name": "Café molido", "category": "Producto", "unit": "unidad",
        "sale_price": "10", "cost": "10", "stock": "0", "low_stock": "5",
        "critical_stock": "1", "reason": "",
    }
    datos.update(cambios)
    datos["csrf"] = token(client.get("/inventario"))
    return client.post("/inventario/nuevo", data=datos, follow_redirects=True)


def test_estructura_accesible_base(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    pagina = client.get("/inventario")
    assert pagina.status_code == 200
    assert 'class="skip-link" href="#contenido"' in pagina.text
    assert '<main class="main-shell" id="contenido">' in pagina.text
    assert "<footer" in pagina.text
    assert "/static/forms.js" in pagina.text  # anti doble envío en formularios clásicos
    assert "ayuda" in pagina.text  # enlace de ayuda en el menú


def test_ayuda_consistente(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    pagina = client.get("/ayuda")
    assert pagina.status_code == 200
    assert "Atajos y navegación" in pagina.text
    assert "F2" in pagina.text
    # el enlace de ayuda queda activo en su propia página
    assert 'section == \'ayuda\'' not in pagina.text and "aria-current=\"page\"" in pagina.text


def test_error_inline_en_alta_de_articulo(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    respuesta = alta_articulo(client, sale_price="abc")
    assert respuesta.status_code == 200
    assert 'name="sale_price" type="number" min="0" step="0.01" inputmode="decimal" value="0" required aria-invalid="true"' in respuesta.text
    assert '<p class="campo-error"' in respuesta.text
    assert "Escribe un importe positivo con hasta dos decimales." in respuesta.text


def test_error_inline_no_mancha_campos_buenos(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    respuesta = alta_articulo(client, sale_price="abc")
    # falla sale_price: el resto del formulario no queda marcado
    assert respuesta.text.count('aria-invalid="true"') == 1


def test_error_inline_en_nombre_de_articulo(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    respuesta = alta_articulo(client, name="ab")
    assert 'aria-invalid="true"' in respuesta.text.split('name="name"')[1][:200]
    assert "El nombre del artículo debe tener entre 3 y 80 caracteres." in respuesta.text


def test_error_inline_en_cliente(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/clientes")
    respuesta = client.post("/clientes/nuevo", data={
        "csrf": token(page), "name": "Ana", "phone": "55123456", "email": "correo-sin-arroba", "notes": "",
    }, follow_redirects=True)
    assert respuesta.status_code == 200
    assert 'aria-invalid="true"' in respuesta.text.split('name="email"')[1][:120]
    assert "Escribe un correo válido, con @ y sin espacios" in respuesta.text


def test_error_inline_en_plaza(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    page = client.get("/equipo")
    respuesta = client.post("/equipo/nueva", data={"csrf": token(page), "name": "x" * 61}, follow_redirects=True)
    assert respuesta.status_code == 200
    assert 'aria-invalid="true"' in respuesta.text
    assert "Escribe un nombre de hasta 60 caracteres." in respuesta.text


def test_operacion_valida_no_deja_marcas(tmp_path: Path):
    client = make_client(tmp_path)
    create_admin(client)
    login(client)
    respuesta = alta_articulo(client)
    assert "Artículo guardado." in respuesta.text
    assert "campo-error" not in respuesta.text
    assert 'aria-invalid' not in respuesta.text
