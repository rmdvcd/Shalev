"""Gráficos de área y de dona del escritorio, con la misma forma que Android e iOS.

Android los dibuja con `Canvas` en `designsystem/component/Charts.kt`. Aquí se generan como SVG en el servidor, por
tres razones:

* el ejecutable portable no incorpora ninguna librería de dibujo (no se añade `Pillow` ni `reportlab` al `.exe`);
* el SVG se puede comprobar con una prueba de texto, cosa que un canvas no;
* no depende de JavaScript, así que el gráfico se ve aunque el navegador tenga los scripts bloqueados.

Se reproduce la geometría de Android, no una parecida:

* **Área** (`ShalevAreaChart`): varias series normalizadas contra el **mismo** máximo, curva suave entre puntos
  (punto medio en Bezier), relleno del área con el color de la serie al **18 %** y trazo encima; debajo, un eje X
  sin eje Y ni rejilla, con pocas etiquetas repartidas (primero y último siempre).
* **Dona** (`ShalevDonutChart`): arcos desde las 12 en punto y en sentido horario, con **2°** de separación entre
  porciones cuando hay más de una, pista de fondo (`surface-2`), y leyenda con nombre y porcentaje escritos para que
  el color nunca sea el único portador de la información.

Los valores negativos cuentan como 0, igual que `ChartMath.normalizar`.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Sequence

#: Unidades del viewBox. El SVG se escala con CSS, así que estas cifras no son píxeles de pantalla.
ANCHO = 320.0
ALTO = 96.0
GROSOR_TRAZO = 2.5
#: Android: `ShalevSize.chartHeight` para el área y `ShalevSize.donut` para el diámetro de la dona.
LADO_DONA = 128.0
GROSOR_DONA = 18.0
#: Android: `ChartMath.arcos(fracciones, separacion = 2f)`.
SEPARACION_ARCOS = 2.0
#: Android: `drawPath(area, c.copy(alpha = 0.18f))`.
OPACIDAD_RELLENO = 0.18
#: Máximo de etiquetas del eje X (Android: `ChartMath.indicesEtiquetas(n, max = 4)`).
MAX_ETIQUETAS = 4


@dataclass(frozen=True)
class Punto:
    """Un dato del eje X: su etiqueta y su valor (en la unidad que sea; solo se compara)."""

    etiqueta: str
    valor: float


@dataclass(frozen=True)
class Serie:
    """Una serie del área. `tono` es el índice de la paleta de gráficos (1..5), no un color."""

    nombre: str
    punto: str
    tono: int = 1


def normalizar_juntas(series: Sequence[Sequence[float]]) -> list[list[float]]:
    """Normaliza varias series contra el MISMO máximo (Android: `ChartMath.normalizarJuntas`).

    Los negativos cuentan como 0 y, si no hay ningún positivo, todas quedan en 0 (línea plana en la base).
    """
    valores = [v for s in series for v in s]
    maximo = max(valores, default=0.0)
    if maximo <= 0:
        return [[0.0 for _ in s] for s in series]
    return [[max(0.0, v) / maximo for v in s] for s in series]


def indices_etiquetas(n: int, maximo: int = MAX_ETIQUETAS) -> list[int]:
    """Índices que llevan etiqueta en el eje X: primero y último siempre, y como mucho `maximo` repartidos.

    Es la misma cuenta que `ChartMath.indicesEtiquetas`: `round(i * (n-1) / (max-1))` sin repetir.
    """
    if n <= 0:
        return []
    if n <= maximo:
        return list(range(n))
    vistos: list[int] = []
    for i in range(maximo):
        indice = round(i * (n - 1) / (maximo - 1))
        if indice not in vistos:
            vistos.append(indice)
    return vistos


def _numero(valor: float) -> str:
    """Número corto y estable para el SVG (sin notación científica ni ceros de relleno)."""
    texto = f"{valor:.2f}".rstrip("0").rstrip(".")
    return texto or "0"


def _puntos(serie: Sequence[float], ancho: float, alto: float) -> list[tuple[float, float]]:
    """Posición de cada dato. Con un solo punto se centra, igual que Android."""
    if not serie:
        return []
    if len(serie) == 1:
        return [(ancho / 2, alto - serie[0] * alto)]
    paso = ancho / (len(serie) - 1)
    return [(i * paso, alto - valor * alto) for i, valor in enumerate(serie)]


def _curva(puntos: Sequence[tuple[float, float]]) -> str:
    """Curva suave entre puntos con el punto medio como control (Android: `cubicTo(mx, a.y, mx, b.y, b.x, b.y)`)."""
    if not puntos:
        return ""
    partes = [f"M {_numero(puntos[0][0])} {_numero(puntos[0][1])}"]
    for anterior, siguiente in zip(puntos, puntos[1:]):
        medio = (anterior[0] + siguiente[0]) / 2
        partes.append(
            "C {mx} {ay} {mx} {by} {bx} {by}".format(
                mx=_numero(medio),
                ay=_numero(anterior[1]),
                by=_numero(siguiente[1]),
                bx=_numero(siguiente[0]),
            )
        )
    return " ".join(partes)


def area_svg(
    ejes: Sequence[Punto],
    series: Sequence[tuple[Serie, Sequence[float]]],
    *,
    descripcion: str,
    ancho: float = ANCHO,
    alto: float = ALTO,
) -> str:
    """Área de una o varias series con eje X, sin eje Y ni rejilla (la misma forma que Android).

    `series` son pares (serie, valores): los valores van en el mismo orden que `ejes`. Cada serie toma su color de la
    paleta por `Serie.tono`. Devuelve el SVG completo como texto.
    """
    etiquetas = [e.etiqueta for e in ejes]
    normalizadas = normalizar_juntas([list(valores) for _, valores in series])
    base = alto

    trazos: list[str] = []
    for (serie, _), valores_norm in zip(series, normalizadas):
        puntos = _puntos(valores_norm, ancho, alto)
        if not puntos:
            continue
        linea = _curva(puntos)
        area = (
            f"{linea} L {_numero(puntos[-1][0])} {_numero(base)} "
            f"L {_numero(puntos[0][0])} {_numero(base)} Z"
        )
        tono = f"var(--chart-{serie.tono})"
        trazos.append(
            f'<path d="{area}" fill="{tono}" fill-opacity="{OPACIDAD_RELLENO}"/>'
            f'<path d="{linea}" fill="none" stroke="{tono}" stroke-width="{GROSOR_TRAZO}" '
            f'stroke-linecap="round"/>'
        )

    # Eje X: una línea fina en la base. Sin eje Y ni rejilla, como Android (`drawEjeX`).
    ejes_svg = (
        f'<line x1="0" y1="{_numero(base)}" x2="{_numero(ancho)}" y2="{_numero(base)}" '
        f'stroke="var(--border)" stroke-width="1"/>'
    )

    # Las etiquetas del eje X van en HTML, no dentro del SVG: el trazado se estira para llenar el ancho
    # (`preserveAspectRatio="none"`), y con el texto dentro la letra saldría deformada. En HTML conservan su tamaño y
    # los índices que reparte `indices_etiquetas` caen siempre en fracciones iguales (0, 1/3, 2/3, 1), así que
    # `space-between` los alinea exactamente con su dato sin cálculos de píxeles.
    indices = indices_etiquetas(len(etiquetas))
    etiquetas_html = "".join(
        f'<span>{_escapar(etiquetas[indice])}</span>' for indice in indices
    )

    leyenda = "".join(
        f'<span class="grafico-leyenda-item"><span class="grafico-punto" style="background:var(--chart-{serie.tono})">'
        f'</span>{_escapar(serie.nombre)}</span>'
        for serie, _ in series
    )

    return (
        f'<div class="grafico grafico-area">'
        f'<svg viewBox="0 0 {_numero(ancho)} {_numero(alto)}" role="img" '
        f'aria-label="{_escapar(descripcion)}" preserveAspectRatio="none" focusable="false">'
        f"{ejes_svg}{''.join(trazos)}"
        f"</svg>"
        f'<div class="grafico-eje" aria-hidden="true">{etiquetas_html}</div>'
        f'<p class="grafico-leyenda">{leyenda}</p>'
        f"</div>"
    )


def arcos(fracciones: Sequence[float], separacion: float = SEPARACION_ARCOS) -> list[tuple[float, float]]:
    """Arcos (inicio, barrido) en grados: 0° = las 12 en punto, sentido horario, con hueco entre porciones.

    Es `ChartMath.arcos` de Android: el hueco se descuenta de cada barrido y nunca queda negativo.
    """
    total = sum(f for f in fracciones if f > 0)
    if total <= 0:
        return []
    positivas = [f for f in fracciones if f > 0]
    hueco = separacion if len(positivas) > 1 else 0.0
    inicio = -90.0
    resultado: list[tuple[float, float]] = []
    for fraccion in fracciones:
        barrido = max(0.0, fraccion) / total * 360.0
        resultado.append((inicio + hueco / 2, max(0.0, barrido - hueco)))
        inicio += barrido
    return resultado


def dona_svg(
    porciones: Sequence[tuple[str, float, str]],
    *,
    descripcion: str,
    centro: str | None = None,
    lado: float = LADO_DONA,
    grosor: float = GROSOR_DONA,
) -> str:
    """Dona con leyenda escrita (nombre y porcentaje), como `ShalevDonutChart` de Android.

    `porciones` son tuplas (nombre, fracción, valor ya formateado). La fracción puede venir en cualquier escala: se
    normaliza contra la suma. Si no hay datos positivos, se dibuja solo la pista.
    """
    radio = (lado - grosor) / 2
    centro_x = centro_y = lado / 2
    trazos = [
        f'<circle cx="{_numero(centro_x)}" cy="{_numero(centro_y)}" r="{_numero(radio)}" fill="none" '
        f'stroke="var(--surface-2)" stroke-width="{_numero(grosor)}"/>'
    ]

    for indice, ((_, _, _), (inicio, barrido)) in enumerate(zip(porciones, arcos([p[1] for p in porciones]))):
        if barrido <= 0:
            continue
        tono = indice % 5 + 1
        trazos.append(
            f'<path d="{_arco(centro_x, centro_y, radio, inicio, barrido)}" fill="none" '
            f'stroke="var(--chart-{tono})" stroke-width="{_numero(grosor)}" stroke-linecap="butt"/>'
        )

    texto_centro = (
        f'<text x="{_numero(centro_x)}" y="{_numero(centro_y)}" text-anchor="middle" '
        f'dominant-baseline="central" class="grafico-centro">{_escapar(centro)}</text>'
        if centro
        else ""
    )

    total = sum(max(0.0, p[1]) for p in porciones) or 1.0
    leyenda = "".join(
        f'<li><span class="grafico-punto" style="background:var(--chart-{i % 5 + 1})"></span>'
        f'<span class="grafico-leyenda-nombre">{_escapar(nombre)}</span>'
        f'<span class="grafico-leyenda-valor">{_escapar(valor)}</span>'
        f'<span class="grafico-leyenda-pct">{round(max(0.0, fraccion) / total * 100)} %</span></li>'
        for i, (nombre, fraccion, valor) in enumerate(porciones)
    )

    return (
        f'<div class="grafico grafico-dona">'
        f'<svg viewBox="0 0 {_numero(lado)} {_numero(lado)}" role="img" '
        f'aria-label="{_escapar(descripcion)}" focusable="false">'
        f"{''.join(trazos)}{texto_centro}"
        f"</svg>"
        f'<ul class="grafico-leyenda-lista">{leyenda}</ul>'
        f"</div>"
    )


def _arco(cx: float, cy: float, radio: float, inicio: float, barrido: float) -> str:
    """Arco de circunferencia como `path`. Un barrido de 360° se dibuja casi entero: si se cierra, desaparece."""
    barrido = min(barrido, 359.999)
    x1 = cx + radio * math.cos(math.radians(inicio))
    y1 = cy + radio * math.sin(math.radians(inicio))
    fin = inicio + barrido
    x2 = cx + radio * math.cos(math.radians(fin))
    y2 = cy + radio * math.sin(math.radians(fin))
    largo = 1 if barrido > 180 else 0
    return (
        f"M {_numero(x1)} {_numero(y1)} A {_numero(radio)} {_numero(radio)} 0 {largo} 1 "
        f"{_numero(x2)} {_numero(y2)}"
    )


def _escapar(texto: str) -> str:
    return (
        str(texto)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )
