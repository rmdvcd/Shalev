"""Pruebas de `dshalev.graficos`: la geometría debe ser la de `Charts.kt` de Android, no una parecida.

Lo que se fija aquí es lo que hace que el dibujo se vea igual: normalizar las series contra el mismo máximo, la curva
suave por el punto medio, el relleno al 18 %, las etiquetas repartidas del eje X y los arcos de la dona con su hueco.
"""

from __future__ import annotations

from dshalev.graficos import (
    OPACIDAD_RELLENO,
    Punto,
    Serie,
    arcos,
    area_svg,
    dona_svg,
    indices_etiquetas,
    normalizar_juntas,
)


def test_normaliza_las_series_contra_el_mismo_maximo() -> None:
    """Como `ChartMath.normalizarJuntas`: la venta y su costo se comparan en la misma escala."""
    venta, costo = normalizar_juntas([[100, 200, 300], [40, 80, 120]])
    assert venta == [100 / 300, 200 / 300, 1.0]
    assert costo == [40 / 300, 80 / 300, 120 / 300]
    # Un máximo compartido: la serie de costo nunca llega al 1 aunque su propio máximo sea el mayor de ella.
    assert max(costo) < 1.0


def test_los_negativos_cuentan_como_cero_y_sin_positivos_todo_es_cero() -> None:
    assert normalizar_juntas([[-5, 10]]) == [[0.0, 1.0]]
    assert normalizar_juntas([[0, 0], [0, 0]]) == [[0.0, 0.0], [0.0, 0.0]]


def test_las_etiquetas_del_eje_x_se_reparten_como_en_android() -> None:
    """`ChartMath.indicesEtiquetas(n, max = 4)`: primero y último siempre, sin repetir."""
    assert indices_etiquetas(0) == []
    assert indices_etiquetas(3) == [0, 1, 2]
    assert indices_etiquetas(7) == [0, 2, 4, 6]
    assert indices_etiquetas(6) == [0, 2, 3, 5]
    for n in range(1, 30):
        indices = indices_etiquetas(n)
        assert indices[0] == 0 and indices[-1] == n - 1, n
        assert len(indices) <= 4 and indices == sorted(set(indices)), n


def test_el_area_es_una_curva_por_serie_con_relleno_al_18_por_ciento() -> None:
    svg = area_svg(
        [Punto("01", 1), Punto("02", 2), Punto("03", 3)],
        [(Serie("Venta", "total", 1), [100, 200, 300]), (Serie("Costo", "costo", 3), [40, 80, 120])],
        descripcion="Venta y costo",
    )
    assert svg.count(f'fill-opacity="{OPACIDAD_RELLENO}"') == 2
    assert svg.count("<path") == 4, "dos rellenos y dos trazos"
    # Curva suave por el punto medio (Android: `cubicTo(mx, a.y, mx, b.y, b.x, b.y)`). El área cierra con rectas a
    # la base, que es lo correcto; la curva que forma el borde son los tramos `C`.
    # Dos tramos de curva por serie (tres puntos), y el relleno repite esa curva antes de bajar a la base: 8 en total.
    assert svg.count(" C ") == 8
    curva = svg.split('fill="none"', 1)[0].split('d="', 1)[1].split('"', 1)[0]
    assert " L " in curva and " Z" in curva, "el relleno baja a la base y cierra"
    # Eje X sin eje Y ni rejilla: una sola línea en la base.
    assert svg.count("<line") == 1
    # Las etiquetas del eje van en HTML: dentro del SVG estirado, la letra se deformaría.
    assert "<text" not in svg.split("grafico-leyenda")[0]
    assert 'class="grafico-eje"' in svg and "<span>01</span>" in svg
    assert "grafico-leyenda" in svg and "Venta" in svg and "Costo" in svg


def test_un_solo_punto_se_centra() -> None:
    """Android: con un único dato el punto va al centro del ancho disponible."""
    svg = area_svg([Punto("01", 5)], [(Serie("Venta", "total", 1), [5])], descripcion="Un día")
    assert "M 160 " in svg or "M 160.0 " in svg


def test_el_area_no_depende_de_javascript_y_se_puede_leer() -> None:
    svg = area_svg([Punto("01", 1)], [(Serie("Venta", "total", 1), [5])], descripcion="Ventas del día")
    assert "<script" not in svg
    assert 'role="img"' in svg and 'aria-label="Ventas del día"' in svg


def test_los_arcos_empiezan_arriba_y_dejan_hueco_entre_porciones() -> None:
    """`ChartMath.arcos`: 0° = las 12 en punto, sentido horario, 2° de separación cuando hay más de una."""
    mitad, mitad2 = arcos([50, 50])
    assert round(mitad[0], 1) == -89.0, "arranca arriba, con medio hueco de separación"
    assert round(mitad[1], 1) == 178.0
    assert round(mitad2[0], 1) == 91.0
    # Una sola porción no lleva hueco: da la vuelta entera.
    solo = arcos([100])
    assert solo == [(-90.0, 360.0)]
    assert arcos([0, 0]) == []


def test_la_dona_lleva_leyenda_con_nombre_valor_y_porcentaje() -> None:
    """El color nunca es el único canal: nombre, valor y porcentaje van escritos."""
    svg = dona_svg(
        [("Producto", 60, "60 CUP"), ("Insumo", 40, "40 CUP")],
        descripcion="Existencias por categoría",
        centro="100 CUP",
    )
    assert "grafico-dona" in svg
    assert "<circle" in svg, "pista de fondo"
    assert svg.count("<path") == 2, "una porción por categoría"
    assert "60 %" in svg and "40 %" in svg
    assert "Producto" in svg and "60 CUP" in svg
    assert "100 CUP" in svg, "texto central"
    assert 'role="img"' in svg and "aria-label" in svg


def test_la_dona_escapa_el_texto_de_las_categorias() -> None:
    svg = dona_svg([("<script>x</script>", 1, "1 CUP")], descripcion="Prueba")
    assert "<script>" not in svg
    assert "&lt;script&gt;" in svg
