#!/usr/bin/env python3
"""Genera los 7 diagramas vectoriales SVG del Manual de Usuario de Shalev.

Usa los iconos vectoriales reales de Shalev (tools/manual/iconos.json) y la paleta
oficial del Design System (`Android/designsystem`) con contraste verificado WCAG AA.
"""

from __future__ import annotations

import html
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ICONS_PATH = ROOT / "tools" / "manual" / "iconos.json"
OUT_DIR = ROOT / "docs" / "manual" / "diagramas"

ICONS: dict[str, list[str]] = json.loads(ICONS_PATH.read_text(encoding="utf-8"))

# Paleta oficial de Shalev (modo claro editorial)
C_BG = "#F5F7FA"
C_CARD = "#FFFFFF"
C_SHADOW = "#E2E7F0"
C_BORDER = "#CFD7E3"
C_TEXT = "#1A1C1E"
C_SUB = "#5F6368"

C_PRI = "#2B4FA3"
C_PRI_BG = "#E6EDFA"
C_SEC = "#B4541A"
C_SEC_BG = "#FDEEE4"
C_TER = "#2F6F80"
C_TER_BG = "#E2F2F6"
C_GRN = "#0B7A5E"
C_GRN_BG = "#E1F5EE"
C_RED = "#C62828"
C_RED_BG = "#FCE8E8"
C_PUR = "#7B3FA0"
C_PUR_BG = "#F3E8FA"
C_GLD = "#8A6500"
C_GLD_BG = "#FFF6DB"

FONT = "DejaVu Sans, Arial, sans-serif"


def esc(t: str) -> str:
    return html.escape(t, quote=True)


def svg_wrap(w: int, h: int, title: str, subtitle: str, body: str) -> str:
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
  <rect width="{w}" height="{h}" rx="18" fill="{C_BG}" stroke="{C_BORDER}" stroke-width="1.5"/>
  <rect x="0" y="0" width="{w}" height="62" rx="18" fill="{C_PRI}"/>
  <rect x="0" y="44" width="{w}" height="18" fill="{C_PRI}"/>
  <text x="26" y="29" font-family="{FONT}" font-size="16" font-weight="bold" fill="#FFFFFF">{esc(title)}</text>
  <text x="26" y="49" font-family="{FONT}" font-size="11.5" fill="#DCE6FF">{esc(subtitle)}</text>
{body}
</svg>
"""


def icon_svg(name: str, cx: float, cy: float, r: float = 18, bg: str = C_PRI, fg: str = "#FFFFFF", scale: float = 0.85) -> str:
    paths = ICONS.get(name, [])
    s = scale
    tx = cx - 12 * s
    ty = cy - 12 * s
    p_str = "".join(f'<path d="{d}" fill="{fg}"/>' for d in paths)
    circle = f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{bg}"/>' if r > 0 else ""
    prefix = f"  {circle}\n" if circle else ""
    return f"{prefix}  <g transform=\"translate({tx:.2f}, {ty:.2f}) scale({s:.3f})\">{p_str}</g>"


def card(
    x: float,
    y: float,
    w: float,
    h: float,
    stroke: str = C_BORDER,
    fill: str = C_CARD,
    rx: float = 14,
    sw: float = 1.6,
    shadow: bool = True,
) -> str:
    sh = f'  <rect x="{x + 2:.1f}" y="{y + 3:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx:.1f}" fill="{C_SHADOW}"/>\n' if shadow else ""
    return f'{sh}  <rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx:.1f}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'


def arrow_h(x1: float, y: float, x2: float, color: str = C_PRI, dashed: bool = False) -> str:
    dash = ' stroke-dasharray="5,4"' if dashed else ""
    if x2 > x1:
        lx2 = x2 - 8
        pts = f"{x2:.1f},{y:.1f} {x2 - 10:.1f},{y - 5.5:.1f} {x2 - 10:.1f},{y + 5.5:.1f}"
    else:
        lx2 = x2 + 8
        pts = f"{x2:.1f},{y:.1f} {x2 + 10:.1f},{y - 5.5:.1f} {x2 + 10:.1f},{y + 5.5:.1f}"
    return (
        f'  <line x1="{x1:.1f}" y1="{y:.1f}" x2="{lx2:.1f}" y2="{y:.1f}" stroke="{color}" stroke-width="2.2"{dash}/>\n'
        f'  <polygon points="{pts}" fill="{color}"/>'
    )


def arrow_v(x: float, y1: float, y2: float, color: str = C_PRI, dashed: bool = False) -> str:
    dash = ' stroke-dasharray="5,4"' if dashed else ""
    if y2 > y1:
        ly2 = y2 - 8
        pts = f"{x:.1f},{y2:.1f} {x - 5.5:.1f},{y2 - 10:.1f} {x + 5.5:.1f},{y2 - 10:.1f}"
    else:
        ly2 = y2 + 8
        pts = f"{x:.1f},{y2:.1f} {x - 5.5:.1f},{y2 + 10:.1f} {x + 5.5:.1f},{y2 + 10:.1f}"
    return (
        f'  <line x1="{x:.1f}" y1="{y1:.1f}" x2="{x:.1f}" y2="{ly2:.1f}" stroke="{color}" stroke-width="2.2"{dash}/>\n'
        f'  <polygon points="{pts}" fill="{color}"/>'
    )


def arrow_both_h(x1: float, y: float, x2: float, color: str = C_TER) -> str:
    return (
        f'  <line x1="{x1 + 9:.1f}" y1="{y:.1f}" x2="{x2 - 9:.1f}" y2="{y:.1f}" stroke="{color}" stroke-width="2.2"/>\n'
        f'  <polygon points="{x1:.1f},{y:.1f} {x1 + 10:.1f},{y - 5.5:.1f} {x1 + 10:.1f},{y + 5.5:.1f}" fill="{color}"/>\n'
        f'  <polygon points="{x2:.1f},{y:.1f} {x2 - 10:.1f},{y - 5.5:.1f} {x2 - 10:.1f},{y + 5.5:.1f}" fill="{color}"/>'
    )


def pill(cx: float, cy: float, w: float, h: float, text: str, bg: str, fg: str, fs: float = 11, bold: bool = True) -> str:
    fw = ' font-weight="bold"' if bold else ""
    return (
        f'  <rect x="{cx - w / 2:.1f}" y="{cy - h / 2:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{h / 2:.1f}" fill="{bg}"/>\n'
        f'  <text x="{cx:.1f}" y="{cy + fs * 0.36:.1f}" font-family="{FONT}" font-size="{fs}"{fw} fill="{fg}" text-anchor="middle">{esc(text)}</text>'
    )


# ---------------------------------------------------------------------------
# 1. Mapa de Navegación General y Botones de Icono
# ---------------------------------------------------------------------------
def diag_01_navegacion() -> str:
    w, h = 880, 440
    parts: list[str] = []

    # Sección superior: Las 5 pestañas de la barra inferior
    parts.append(f'  <text x="440" y="90" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_PRI}" text-anchor="middle">BARRA INFERIOR DE 5 SECCIONES · Toca un icono o desliza el dedo a izquierda o derecha</text>')

    tabs = [
        ("Home", "1. Inicio", "Turno, alertas,", "gráficos y Top 3", True),
        ("Inventory2", "2. Inventario", "Artículos, insumos,", "recetas y exportar", False),
        ("ManoRecibe", "3. Servicios", "Catálogo y tarifas", "e insumos usados", False),
        ("History", "4. Registros", "Ventas, clientes,", "caja y turnos", False),
        ("Settings", "5. Ajustes", "Permisos, respaldo", "y configuración", False),
    ]
    tw, th = 154, 128
    gap = 16
    start_x = (w - (5 * tw + 4 * gap)) / 2
    ty = 106
    for idx, (ico, tit, l1, l2, active) in enumerate(tabs):
        x = start_x + idx * (tw + gap)
        stroke = C_PRI if active else C_BORDER
        fill = "#F2F6FF" if active else C_CARD
        sw = 2.4 if active else 1.5
        parts.append(card(x, ty, tw, th, stroke=stroke, fill=fill, sw=sw))
        # Pastilla tipo barra inferior de Shalev (resaltado invertido cuando está activa)
        if active:
            parts.append(f'  <rect x="{x + 45:.1f}" y="{ty + 14:.1f}" width="64" height="34" rx="17" fill="{C_PRI}"/>')
            parts.append(icon_svg(ico, x + tw / 2, ty + 31, r=0, fg="#FFFFFF", scale=0.92))
        else:
            parts.append(f'  <rect x="{x + 45:.1f}" y="{ty + 14:.1f}" width="64" height="34" rx="17" fill="{C_PRI_BG}"/>')
            parts.append(icon_svg(ico, x + tw / 2, ty + 31, r=0, fg=C_PRI, scale=0.92))
        parts.append(f'  <text x="{x + tw / 2:.1f}" y="{ty + 70:.1f}" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_TEXT}" text-anchor="middle">{esc(tit)}</text>')
        parts.append(f'  <text x="{x + tw / 2:.1f}" y="{ty + 92:.1f}" font-family="{FONT}" font-size="11" fill="{C_SUB}" text-anchor="middle">{esc(l1)}</text>')
        parts.append(f'  <text x="{x + tw / 2:.1f}" y="{ty + 109:.1f}" font-family="{FONT}" font-size="11" fill="{C_SUB}" text-anchor="middle">{esc(l2)}</text>')

    # Indicador de gesto deslizar
    parts.append(arrow_both_h(230, 256, 650, color=C_TER))
    parts.append(pill(440, 256, 310, 24, "Deslizar hacia los lados cambia de sección", C_TER_BG, C_TER, fs=11))

    # Sección inferior: Guía visual de botones de solo icono
    parts.append(card(22, 282, 836, 138, stroke=C_BORDER, fill=C_CARD))
    parts.append(f'  <text x="440" y="306" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_TEXT}" text-anchor="middle">GUÍA RÁPIDA DE BOTONES (mantén pulsado cualquier icono para leer su función)</text>')

    btns = [
        ("Add", C_PRI, "#FFFFFF", "Crear / Agregar", "Esquina inferior"),
        ("Check", C_PRI, "#FFFFFF", "Guardar / Confirmar", "Acepta el paso"),
        ("Close", C_TER_BG, C_TER, "Cancelar / Cerrar", "Descarta o vuelve"),
        ("ShoppingCart", C_PRI, "#FFFFFF", "Nueva venta", "Con turno abierto"),
        ("Payments", C_PRI, "#FFFFFF", "Caja (+ / −)", "Entrada o salida"),
        ("FilterList", C_PRI_BG, C_PRI, "Filtrar lista", "Fechas o estados"),
        ("Share", C_PRI_BG, C_PRI, "Exportar", "PDF, Excel o PNG"),
    ]
    bw = 112
    bx0 = 36
    for i, (ico, bg, fg, t1, t2) in enumerate(btns):
        cx = bx0 + i * bw + bw / 2
        parts.append(icon_svg(ico, cx, 342, r=19, bg=bg, fg=fg, scale=0.85))
        parts.append(f'  <text x="{cx:.1f}" y="381" font-family="{FONT}" font-size="10.8" font-weight="bold" fill="{C_TEXT}" text-anchor="middle">{esc(t1)}</text>')
        parts.append(f'  <text x="{cx:.1f}" y="397" font-family="{FONT}" font-size="10" fill="{C_SUB}" text-anchor="middle">{esc(t2)}</text>')

    return svg_wrap(
        w,
        h,
        "Mapa de navegación y botones de Shalev",
        "Ajustes ocupa la quinta sección; la Calculadora de cambio se abre desde Inicio.",
        "\n".join(parts),
    )


# ---------------------------------------------------------------------------
# 2. Ciclo Completo del Turno y Arqueo de Caja
# ---------------------------------------------------------------------------
def diag_02_ciclo_turno() -> str:
    w, h = 880, 455
    parts: list[str] = []

    steps = [
        ("Storefront", C_PRI, C_PRI_BG, "1. Abrir turno", ["En Inicio activa el", "interruptor del turno.", "Escribe el Fondo de", "caja inicial (o 0 CUP)."]),
        ("ShoppingCart", C_TER, C_TER_BG, "2. Operar y vender", ["Registra ventas de", "productos o servicios,", "y entradas o salidas", "de efectivo con motivo."]),
        ("PointOfSale", C_SEC, C_SEC_BG, "3. Contar al cerrar", ["Paso A: Billetes CUP", "o botón «Cuadra».", "Paso B: Inventario que", "cambió por categoría."]),
        ("ReceiptLong", C_GRN, C_GRN_BG, "4. Revisar y guardar", ["Explica el motivo si", "hay diferencias.", "Queda guardado en", "Registros → Turnos."]),
    ]
    sw_box, sh_box = 188, 172
    gap = 26
    x0 = (w - (4 * sw_box + 3 * gap)) / 2
    y0 = 86
    for i, (ico, col, bg, tit, lines) in enumerate(steps):
        x = x0 + i * (sw_box + gap)
        parts.append(card(x, y0, sw_box, sh_box, stroke=col, fill=C_CARD, sw=2.0))
        parts.append(f'  <rect x="{x:.1f}" y="{y0:.1f}" width="{sw_box:.1f}" height="44" rx="14" fill="{bg}"/>')
        parts.append(f'  <rect x="{x:.1f}" y="{y0 + 30:.1f}" width="{sw_box:.1f}" height="14" fill="{bg}"/>')
        parts.append(icon_svg(ico, x + 26, y0 + 22, r=15, bg=col, fg="#FFFFFF", scale=0.72))
        parts.append(f'  <text x="{x + 49:.1f}" y="{y0 + 27:.1f}" font-family="{FONT}" font-size="12.5" font-weight="bold" fill="{C_TEXT}">{esc(tit)}</text>')
        for j, ln in enumerate(lines):
            parts.append(f'  <text x="{x + sw_box / 2:.1f}" y="{y0 + 72 + j * 21:.1f}" font-family="{FONT}" font-size="11.5" fill="{C_TEXT}" text-anchor="middle">{esc(ln)}</text>')
        if i < 3:
            parts.append(arrow_h(x + sw_box + 3, y0 + sh_box / 2, x + sw_box + gap - 3, color=col))

    # Caja de fórmula de arqueo
    parts.append(card(22, 280, 836, 86, stroke=C_PRI, fill="#F3F6FC", sw=1.8))
    parts.append(f'  <text x="440" y="304" font-family="{FONT}" font-size="12.5" font-weight="bold" fill="{C_PRI}" text-anchor="middle">FÓRMULA AUTOMÁTICA DEL ARQUEO DE CAJA</text>')

    f_boxes = [
        ("Fondo inicial", C_CARD, C_TEXT, 118),
        ("+", None, C_PRI, 22),
        ("Ventas en efectivo", C_CARD, C_TEXT, 142),
        ("+", None, C_PRI, 22),
        ("Entradas de caja", C_GRN_BG, C_GRN, 134),
        ("−", None, C_RED, 22),
        ("Salidas de caja", C_RED_BG, C_RED, 126),
        ("=", None, C_PRI, 22),
        ("Efectivo esperado", C_PRI, "#FFFFFF", 146),
    ]
    total_fw = sum(b[3] for b in f_boxes) + (len(f_boxes) - 1) * 4
    fx = (w - total_fw) / 2
    fy = 336
    for label, bg, fg, bw in f_boxes:
        if bg is None:
            parts.append(f'  <text x="{fx + bw / 2:.1f}" y="{fy + 6:.1f}" font-family="{FONT}" font-size="18" font-weight="bold" fill="{fg}" text-anchor="middle">{esc(label)}</text>')
        else:
            border_c = C_PRI if bg == C_PRI else C_BORDER
            parts.append(f'  <rect x="{fx:.1f}" y="{fy - 18:.1f}" width="{bw:.1f}" height="34" rx="10" fill="{bg}" stroke="{border_c}" stroke-width="1.2"/>')
            parts.append(f'  <text x="{fx + bw / 2:.1f}" y="{fy + 4:.1f}" font-family="{FONT}" font-size="11.5" font-weight="bold" fill="{fg}" text-anchor="middle">{esc(label)}</text>')
        fx += bw + 4

    # Nota inferior sobre empleados vs principal
    parts.append(card(22, 382, 836, 52, stroke=C_SEC, fill=C_SEC_BG, sw=1.4, shadow=False))
    parts.append(icon_svg("PhoneAndroid", 48, 408, r=15, bg=C_SEC, fg="#FFFFFF", scale=0.72))
    parts.append(f'  <text x="74" y="403" font-family="{FONT}" font-size="11.5" font-weight="bold" fill="{C_TEXT}">¿Qué cambia en la app de un empleado?</text>')
    parts.append(f'  <text x="74" y="421" font-family="{FONT}" font-size="11" fill="{C_TEXT}">El empleado hace el mismo conteo, pero su confirmación envía una solicitud: solo el encargado aprueba el cierre en Apps vinculadas.</text>')

    return svg_wrap(
        w,
        h,
        "Ciclo de la jornada de trabajo y arqueo de caja",
        "Regla fundamental: sin turno abierto no se puede vender · Conciliación al cierre en 3 pasos",
        "\n".join(parts),
    )


# ---------------------------------------------------------------------------
# 3. Flujo de Venta y Métodos de Cobro (Efectivo vs Transferencia)
# ---------------------------------------------------------------------------
def diag_03_flujo_venta() -> str:
    w, h = 880, 480
    parts: list[str] = []

    # Columna izquierda: inicio de venta y carrito
    parts.append(card(24, 90, 210, 128, stroke=C_PRI, fill=C_CARD, sw=2.0))
    parts.append(icon_svg("ShoppingCart", 54, 120, r=17, bg=C_PRI, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="80" y="117" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_TEXT}">1. Nueva venta</text>')
    parts.append(f'  <text x="80" y="134" font-family="{FONT}" font-size="10.8" fill="{C_SUB}">Turno abierto</text>')
    parts.append(f'  <text x="129" y="164" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}" text-anchor="middle">Elige Productos o</text>')
    parts.append(f'  <text x="129" y="182" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}" text-anchor="middle">Servicios (por separado)</text>')
    parts.append(f'  <text x="129" y="200" font-family="{FONT}" font-size="11" fill="{C_PRI}" font-weight="bold" text-anchor="middle">Ajusta cantidades (− / +)</text>')

    parts.append(arrow_v(129, 222, 266, color=C_PRI))

    parts.append(card(24, 270, 210, 124, stroke=C_TER, fill=C_CARD, sw=2.0))
    parts.append(icon_svg("Percent", 54, 300, r=17, bg=C_TER, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="80" y="297" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_TEXT}">2. Carrito y precio</text>')
    parts.append(f'  <text x="80" y="314" font-family="{FONT}" font-size="10.8" fill="{C_SUB}">Cálculo automático</text>')
    parts.append(f'  <text x="129" y="344" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}" text-anchor="middle">Aplica preajustes activos</text>')
    parts.append(f'  <text x="129" y="362" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}" text-anchor="middle">según método o importe</text>')
    parts.append(f'  <text x="129" y="380" font-family="{FONT}" font-size="11" fill="{C_TER}" font-weight="bold" text-anchor="middle">Elige método de pago</text>')

    # Bifurcación hacia arriba (Efectivo) y hacia abajo (Transferencia)
    parts.append(f'  <line x1="236" y1="332" x2="268" y2="332" stroke="{C_PRI}" stroke-width="2.2"/>')
    parts.append(f'  <line x1="268" y1="154" x2="268" y2="356" stroke="{C_PRI}" stroke-width="2.2"/>')
    parts.append(arrow_h(268, 154, 304, color=C_GRN))
    parts.append(arrow_h(268, 356, 304, color=C_SEC))

    # Rama superior: EFECTIVO
    parts.append(card(308, 92, 326, 124, stroke=C_GRN, fill=C_GRN_BG, sw=2.0))
    parts.append(icon_svg("Payments", 340, 122, r=17, bg=C_GRN, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="366" y="120" font-family="{FONT}" font-size="13.5" font-weight="bold" fill="{C_GRN}">Opción A · Cobro en Efectivo</text>')
    parts.append(f'  <text x="366" y="137" font-family="{FONT}" font-size="11" fill="{C_TEXT}">Rápido en un solo paso</text>')
    parts.append(f'  <text x="328" y="166" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}">• Muestra el Comprobante de pago con el total.</text>')
    parts.append(f'  <text x="328" y="186" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}">• Suma el importe al efectivo esperado en caja.</text>')
    parts.append(f'  <text x="328" y="204" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}">• Toca Confirmar (✓) para cerrar la venta.</text>')

    # Rama inferior: TRANSFERENCIA (3 subpasos)
    parts.append(card(308, 238, 326, 218, stroke=C_SEC, fill=C_SEC_BG, sw=2.0))
    parts.append(icon_svg("QrCodeScanner", 340, 268, r=17, bg=C_SEC, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="366" y="266" font-family="{FONT}" font-size="13.5" font-weight="bold" fill="{C_SEC}">Opción B · Cobro por Transferencia</text>')
    parts.append(f'  <text x="366" y="283" font-family="{FONT}" font-size="11" fill="{C_TEXT}">Con tarjeta y teléfono marcados «En uso»</text>')

    sub_y = [302, 352, 402]
    subs = [
        ("1. QR Transfermóvil:", "el cliente escanea el QR y escribe el importe mostrado debajo."),
        ("2. Datos del cliente:", "nombre, CI y móvil (marca «Cliente fijo» para recordarlo)."),
        ("3. Nº de transacción:", "captura opcional, pegar o compartir el SMS de PAGOxMOVIL."),
    ]
    for sy, (st, sd) in zip(sub_y, subs):
        parts.append(f'  <rect x="322" y="{sy:.1f}" width="298" height="42" rx="9" fill="{C_CARD}" stroke="{C_BORDER}" stroke-width="1.1"/>')
        parts.append(f'  <text x="332" y="{sy + 17:.1f}" font-family="{FONT}" font-size="11" font-weight="bold" fill="{C_TEXT}">{esc(st)}</text>')
        parts.append(f'  <text x="332" y="{sy + 33:.1f}" font-family="{FONT}" font-size="10.2" fill="{C_SUB}">{esc(sd)}</text>')

    # Columna derecha: Confirmación y efectos automáticos
    parts.append(arrow_h(638, 154, 676, color=C_GRN))
    parts.append(arrow_h(638, 346, 676, color=C_SEC))

    parts.append(card(680, 126, 176, 276, stroke=C_PRI, fill=C_CARD, sw=2.2))
    parts.append(icon_svg("Check", 768, 164, r=22, bg=C_PRI, fg="#FFFFFF", scale=0.95))
    parts.append(f'  <text x="768" y="205" font-family="{FONT}" font-size="13.5" font-weight="bold" fill="{C_PRI}" text-anchor="middle">3. Venta registrada</text>')
    effects = [
        "Descuenta artículos",
        "del inventario",
        "— — —",
        "Descuenta insumos",
        "de la receta o",
        "del servicio",
        "— — —",
        "Guarda en Registros",
        "y actualiza gráficos",
    ]
    for idx, ef in enumerate(effects):
        col_t = C_BORDER if ef == "— — —" else C_TEXT
        fw = ' font-weight="bold"' if idx in (0, 3, 7) else ""
        parts.append(f'  <text x="768" y="{232 + idx * 18:.1f}" font-family="{FONT}" font-size="11"{fw} fill="{col_t}" text-anchor="middle">{esc(ef)}</text>')

    return svg_wrap(
        w,
        h,
        "Flujo de venta y métodos de cobro",
        "Comparativa paso a paso entre cobro en efectivo y cobro electrónico con Transfermóvil",
        "\n".join(parts),
    )


# ---------------------------------------------------------------------------
# 4. Ecosistema de Inventario: Artículos, Insumos, Elaborados y Servicios
# ---------------------------------------------------------------------------
def diag_04_inventario() -> str:
    w, h = 880, 465
    parts: list[str] = []

    # Columna izquierda: Artículos normales + Insumos
    parts.append(card(24, 86, 250, 130, stroke=C_PRI, fill=C_CARD, sw=2.0))
    parts.append(icon_svg("Inventory2", 54, 116, r=17, bg=C_PRI, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="80" y="114" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_PRI}">Artículos físicos</text>')
    parts.append(f'  <text x="80" y="131" font-family="{FONT}" font-size="10.8" fill="{C_SUB}">Compra y venta directa</text>')
    parts.append(f'  <text x="38" y="158" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Unidades enteras (u, cajas…)</text>')
    parts.append(f'  <text x="38" y="177" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Costo, precio de venta y foto</text>')
    parts.append(f'  <text x="38" y="196" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Fecha de caducidad opcional</text>')

    parts.append(card(24, 236, 250, 146, stroke=C_TER, fill=C_TER_BG, sw=2.0))
    parts.append(icon_svg("PlaylistAdd", 54, 266, r=17, bg=C_TER, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="80" y="264" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_TER}">Insumos (materias primas)</text>')
    parts.append(f'  <text x="80" y="281" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}">Categoría «Insumos»</text>')
    parts.append(f'  <text x="38" y="308" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Harina, queso, café, champú…</text>')
    parts.append(f'  <text x="38" y="327" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Admiten hasta 3 decimales (kg, L)</text>')
    parts.append(f'  <text x="38" y="346" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Si pones precio de venta, también</text>')
    parts.append(f'  <text x="38" y="363" font-family="{FONT}" font-size="11" fill="{C_TEXT}">  se venden en unidades enteras</text>')

    # Flechas desde Insumos hacia Elaborados y Servicios
    parts.append(f'  <line x1="276" y1="308" x2="312" y2="308" stroke="{C_TER}" stroke-width="2.3"/>')
    parts.append(f'  <line x1="312" y1="168" x2="312" y2="322" stroke="{C_TER}" stroke-width="2.3"/>')
    parts.append(arrow_h(312, 168, 352, color=C_SEC))
    parts.append(arrow_h(312, 322, 352, color=C_PUR))

    # Centro: Elaborados con receta y Servicios
    parts.append(card(356, 86, 264, 152, stroke=C_SEC, fill=C_CARD, sw=2.0))
    parts.append(icon_svg("AutoFixHigh", 386, 116, r=17, bg=C_SEC, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="412" y="114" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_SEC}">Productos Elaborados</text>')
    parts.append(f'  <text x="412" y="131" font-family="{FONT}" font-size="10.8" fill="{C_SUB}">Categoría «Elaborado» + Receta</text>')
    parts.append(f'  <text x="370" y="157" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Ej.: Pizza, sándwich, batido, café</text>')
    parts.append(f'  <text x="370" y="176" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Costo calculado solo por su receta</text>')
    parts.append(f'  <text x="370" y="195" font-family="{FONT}" font-size="11" font-weight="bold" fill="{C_SEC}">• Muestra «Alcanza para N»</text>')
    parts.append(f'  <text x="370" y="214" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Al vender, descuenta sus insumos</text>')

    parts.append(card(356, 254, 264, 128, stroke=C_PUR, fill=C_CARD, sw=2.0))
    parts.append(icon_svg("ManoRecibe", 386, 284, r=17, bg=C_PUR, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="412" y="282" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_PUR}">Servicios del negocio</text>')
    parts.append(f'  <text x="412" y="299" font-family="{FONT}" font-size="10.8" fill="{C_SUB}">Pestaña «Servicios»</text>')
    parts.append(f'  <text x="370" y="325" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Corte, manicure, lavado, envío…</text>')
    parts.append(f'  <text x="370" y="344" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Pueden gastar insumos opcionales</text>')
    parts.append(f'  <text x="370" y="363" font-family="{FONT}" font-size="11" fill="{C_TEXT}">• Se cobran en Nueva venta → Servicios</text>')

    # Columna derecha: Semáforo de alertas de existencias
    parts.append(card(644, 86, 212, 296, stroke=C_BORDER, fill=C_CARD, sw=1.8))
    parts.append(icon_svg("WarningAmber", 674, 116, r=16, bg=C_PRI_BG, fg=C_PRI, scale=0.76))
    parts.append(f'  <text x="698" y="115" font-family="{FONT}" font-size="12.5" font-weight="bold" fill="{C_TEXT}">Avisos automáticos</text>')
    parts.append(f'  <text x="698" y="131" font-family="{FONT}" font-size="10.3" fill="{C_SUB}">En Inicio e Inventario</text>')

    alerts = [
        ("Sin existencia (0)", C_RED, "#FFFFFF", C_RED),
        ("Stock o insumo crítico", C_RED_BG, C_RED, C_RED),
        ("Stock bajo", C_GLD_BG, C_GLD, C_GLD),
        ("Insumo bajo", C_TER_BG, C_TER, C_TER),
        ("Próximo a caducar (7 d)", C_PUR_BG, C_PUR, C_PUR),
    ]
    for idx, (al_txt, bg, fg, bd) in enumerate(alerts):
        ay = 148 + idx * 44
        parts.append(f'  <rect x="658" y="{ay:.1f}" width="184" height="34" rx="9" fill="{bg}" stroke="{bd}" stroke-width="1.2"/>')
        parts.append(f'  <text x="750" y="{ay + 21:.1f}" font-family="{FONT}" font-size="11" font-weight="bold" fill="{fg}" text-anchor="middle">{esc(al_txt)}</text>')

    # Franja inferior resumen
    parts.append(card(24, 398, 832, 46, stroke=C_PRI, fill=C_PRI_BG, sw=1.3, shadow=False))
    parts.append(f'  <text x="440" y="426" font-family="{FONT}" font-size="11.5" font-weight="bold" fill="{C_PRI}" text-anchor="middle"> Al cerrar el turno, los Elaborados no se cuentan por separado: cuentas directamente los Insumos reales que quedan.</text>')

    return svg_wrap(
        w,
        h,
        "Cómo se relacionan los productos, insumos, elaborados y servicios",
        "Control exacto de existencias y descuento automático por receta al vender",
        "\n".join(parts),
    )


# ---------------------------------------------------------------------------
# 5. Arquitectura de Apps Vinculadas (Principal y Secundarias en Red Local)
# ---------------------------------------------------------------------------
def diag_05_vinculacion() -> str:
    w, h = 880, 470
    parts: list[str] = []

    # Izquierda: App Principal (Dueño)
    parts.append(card(24, 90, 266, 286, stroke=C_PRI, fill=C_CARD, sw=2.3))
    parts.append(f'  <rect x="24" y="90" width="266" height="52" rx="14" fill="{C_PRI}"/>')
    parts.append(f'  <rect x="24" y="126" width="266" height="16" fill="{C_PRI}"/>')
    parts.append(icon_svg("Storefront", 54, 116, r=17, bg="#FFFFFF", fg=C_PRI, scale=0.78))
    parts.append(f'  <text x="80" y="113" font-family="{FONT}" font-size="13.5" font-weight="bold" fill="#FFFFFF">App Principal (Dueño)</text>')
    parts.append(f'  <text x="80" y="130" font-family="{FONT}" font-size="10.8" fill="#DCE6FF">1 teléfono por negocio</text>')

    p_items = [
        "• Guarda toda la base de datos",
        "• Tiene la licencia activa",
        "• Crea catálogo, precios y fotos",
        "• Genera el código QR por empleado",
        "• Elige qué permisos tiene cada uno",
        "• Elige a qué tarjeta cobra cada uno",
        "• Ve quién está «Conectada ahora»",
        "• Aprueba o rechaza cierres de turno",
        "• Reparte actualizaciones por Wi-Fi",
    ]
    for i, it in enumerate(p_items):
        parts.append(f'  <text x="38" y="{166 + i * 22:.1f}" font-family="{FONT}" font-size="11.1" fill="{C_TEXT}">{esc(it)}</text>')

    # Centro: Red Local Wi-Fi (sin internet)
    parts.append(card(328, 128, 224, 210, stroke=C_TER, fill=C_TER_BG, sw=2.0))
    parts.append(icon_svg("Wifi", 440, 166, r=24, bg=C_TER, fg="#FFFFFF", scale=1.0))
    parts.append(f'  <text x="440" y="208" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_TER}" text-anchor="middle">Red Wi-Fi Local</text>')
    parts.append(f'  <text x="440" y="226" font-family="{FONT}" font-size="11" font-weight="bold" fill="{C_TEXT}" text-anchor="middle">¡Sin gastar datos ni internet!</text>')
    parts.append(f'  <text x="440" y="250" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}" text-anchor="middle">Wi-Fi del local o Zona Wi-Fi</text>')
    parts.append(f'  <text x="440" y="266" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}" text-anchor="middle">del teléfono principal</text>')
    parts.append(pill(440, 294, 192, 24, "Conexión cifrada de extremo", C_CARD, C_TER, fs=10.3))
    parts.append(pill(440, 322, 192, 24, "Avisos con sonido y progreso", C_CARD, C_PRI, fs=10.3))

    parts.append(arrow_both_h(292, 233, 326, color=C_TER))
    parts.append(arrow_both_h(554, 175, 588, color=C_TER))
    parts.append(arrow_both_h(554, 295, 588, color=C_TER))

    # Derecha: Apps Secundarias (Empleados 1..10)
    parts.append(card(590, 90, 266, 136, stroke=C_SEC, fill=C_CARD, sw=2.0))
    parts.append(icon_svg("PhoneAndroid", 620, 120, r=17, bg=C_SEC, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="646" y="117" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_SEC}">Secundaria · Empleado 1</text>')
    parts.append(f'  <text x="646" y="134" font-family="{FONT}" font-size="10.6" fill="{C_SUB}">Recibe catálogo + fotos</text>')
    parts.append(f'  <text x="604" y="160" font-family="{FONT}" font-size="10.9" fill="{C_TEXT}">• Abre su turno y escribe su fondo</text>')
    parts.append(f'  <text x="604" y="179" font-family="{FONT}" font-size="10.9" fill="{C_TEXT}">• Su QR lleva su propio móvil (SMS)</text>')
    parts.append(f'  <text x="604" y="198" font-family="{FONT}" font-size="10.9" fill="{C_TEXT}">• Sigue vendiendo aunque caiga Wi-Fi</text>')
    parts.append(f'  <text x="604" y="216" font-family="{FONT}" font-size="10.9" font-weight="bold" fill="{C_SEC}">• Toca «Sincronizar ahora» al enviar</text>')

    parts.append(card(590, 240, 266, 136, stroke=C_GRN, fill=C_CARD, sw=2.0))
    parts.append(icon_svg("Devices", 620, 270, r=17, bg=C_GRN, fg="#FFFFFF", scale=0.78))
    parts.append(f'  <text x="646" y="267" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_GRN}">Hasta 10 Secundarias</text>')
    parts.append(f'  <text x="646" y="284" font-family="{FONT}" font-size="10.6" fill="{C_SUB}">5 incluidas en la prueba gratis</text>')
    parts.append(f'  <text x="604" y="310" font-family="{FONT}" font-size="10.9" fill="{C_TEXT}">• Sincroniza sola al abrir su turno</text>')
    parts.append(f'  <text x="604" y="329" font-family="{FONT}" font-size="10.9" fill="{C_TEXT}">• Sincroniza sola al pedir el cierre</text>')
    parts.append(f'  <text x="604" y="348" font-family="{FONT}" font-size="10.9" fill="{C_TEXT}">• Avisa si el dueño cambió catálogo</text>')
    parts.append(f'  <text x="604" y="366" font-family="{FONT}" font-size="10.9" fill="{C_TEXT}">• Permisos ajustables al instante</text>')

    # Pie informativo
    parts.append(card(24, 394, 832, 54, stroke=C_PRI, fill=C_PRI_BG, sw=1.3, shadow=False))
    parts.append(f'  <text x="440" y="416" font-family="{FONT}" font-size="11.3" font-weight="bold" fill="{C_PRI}" text-anchor="middle">Cobro inteligente por transferencia en empleados:</text>')
    parts.append(f'  <text x="440" y="435" font-family="{FONT}" font-size="11" fill="{C_TEXT}" text-anchor="middle">El dinero entra directo a la tarjeta elegida por el dueño, pero el SMS de confirmación le llega al teléfono del empleado.</text>')

    return svg_wrap(
        w,
        h,
        "Trabajo en equipo: App Principal y Apps Secundarias",
        "Sincronización segura por la red local del negocio sin depender de internet",
        "\n".join(parts),
    )


# ---------------------------------------------------------------------------
# 6. Respaldo .shalev y Cambio de Teléfono
# ---------------------------------------------------------------------------
def diag_06_respaldo() -> str:
    w, h = 880, 445
    parts: list[str] = []

    cols = [
        (
            "PhoneAndroid",
            C_PRI,
            C_PRI_BG,
            "1. Teléfono actual",
            "Antes de cambiar o guardar",
            [
                "Ve a Ajustes → Respaldo.",
                "Deja activado «Proteger",
                "con contraseña» y anótala.",
                "Guarda en el teléfono o",
                "envíalo a otra app segura.",
            ],
        ),
        (
            "Lock",
            C_TER,
            C_TER_BG,
            "2. Archivo .shalev",
            "Copia completa cifrada",
            [
                "Incluye productos, recetas,",
                "insumos, turnos, ventas,",
                "clientes fijos y el ID de",
                "tu licencia para migrarla.",
                "(No incluye fotos ni clave).",
            ],
        ),
        (
            "UploadFile",
            C_GRN,
            C_GRN_BG,
            "3. Teléfono nuevo",
            "Restaurar tus datos",
            [
                "Instala Shalev como Principal.",
                "Ve a Respaldo → Importar,",
                "elige el archivo .shalev",
                "y escribe tu contraseña.",
                "¡Todo tu negocio vuelve!",
            ],
        ),
        (
            "Key",
            C_SEC,
            C_SEC_BG,
            "4. Pasar tu licencia",
            "Recuperación gratuita",
            [
                "En Ajustes → Licencia",
                "verás ya puesto el ID de",
                "tu licencia anterior.",
                "Envía la solicitud: el viejo",
                "se revoca y el nuevo activa.",
            ],
        ),
    ]

    bw, bh = 188, 224
    gap = 26
    x0 = (w - (4 * bw + 3 * gap)) / 2
    y0 = 88
    for i, (ico, col, bg, tit, sub, lines) in enumerate(cols):
        x = x0 + i * (bw + gap)
        parts.append(card(x, y0, bw, bh, stroke=col, fill=C_CARD, sw=2.0))
        parts.append(f'  <rect x="{x:.1f}" y="{y0:.1f}" width="{bw:.1f}" height="52" rx="14" fill="{bg}"/>')
        parts.append(f'  <rect x="{x:.1f}" y="{y0 + 36:.1f}" width="{bw:.1f}" height="16" fill="{bg}"/>')
        parts.append(icon_svg(ico, x + 26, y0 + 26, r=15, bg=col, fg="#FFFFFF", scale=0.72))
        parts.append(f'  <text x="{x + 48:.1f}" y="{y0 + 23:.1f}" font-family="{FONT}" font-size="12.2" font-weight="bold" fill="{C_TEXT}">{esc(tit)}</text>')
        parts.append(f'  <text x="{x + 48:.1f}" y="{y0 + 39:.1f}" font-family="{FONT}" font-size="10.1" fill="{C_SUB}">{esc(sub)}</text>')
        for j, ln in enumerate(lines):
            parts.append(f'  <text x="{x + bw / 2:.1f}" y="{y0 + 80 + j * 24:.1f}" font-family="{FONT}" font-size="11.1" fill="{C_TEXT}" text-anchor="middle">{esc(ln)}</text>')
        if i < 3:
            parts.append(arrow_h(x + bw + 3, y0 + bh / 2, x + bw + gap - 3, color=col))

    # Advertencias clave abajo
    parts.append(card(24, 334, 404, 88, stroke=C_RED, fill=C_RED_BG, sw=1.5, shadow=False))
    parts.append(icon_svg("WarningAmber", 52, 362, r=15, bg=C_RED, fg="#FFFFFF", scale=0.72))
    parts.append(f'  <text x="76" y="358" font-family="{FONT}" font-size="11.8" font-weight="bold" fill="{C_RED}">¡Guarda bien tu contraseña!</text>')
    parts.append(f'  <text x="76" y="378" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}">Nadie puede recuperarla si la olvidas. Además,</text>')
    parts.append(f'  <text x="76" y="395" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}">importar un respaldo reemplaza todos los datos</text>')
    parts.append(f'  <text x="76" y="411" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}">actuales del teléfono donde lo abras.</text>')

    parts.append(card(452, 334, 404, 88, stroke=C_PRI, fill=C_PRI_BG, sw=1.5, shadow=False))
    parts.append(icon_svg("Backup", 480, 362, r=15, bg=C_PRI, fg="#FFFFFF", scale=0.72))
    parts.append(f'  <text x="504" y="358" font-family="{FONT}" font-size="11.8" font-weight="bold" fill="{C_PRI}">Recordatorio y respaldo al actualizar</text>')
    parts.append(f'  <text x="504" y="378" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}">Shalev te avisa en Inicio si llevas 30 días sin</text>')
    parts.append(f'  <text x="504" y="395" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}">exportar copia, y guarda un respaldo interno</text>')
    parts.append(f'  <text x="504" y="411" font-family="{FONT}" font-size="10.8" fill="{C_TEXT}">automático justo antes de instalar mejoras.</text>')

    return svg_wrap(
        w,
        h,
        "Respaldo protegido (.shalev) y mudanza a un teléfono nuevo",
        "Cómo resguardar tu negocio y recuperar gratis tu licencia si cambias de equipo",
        "\n".join(parts),
    )


# ---------------------------------------------------------------------------
# 7. Solicitud y Activación de Licencia en 3 Pasos
# ---------------------------------------------------------------------------
def diag_07_licencia() -> str:
    w, h = 880, 440
    parts: list[str] = []

    # Banner superior de prueba gratuita
    parts.append(card(24, 82, 832, 46, stroke=C_PRI, fill=C_PRI_BG, sw=1.4, shadow=False))
    parts.append(icon_svg("HourglassTop", 52, 105, r=15, bg=C_PRI, fg="#FFFFFF", scale=0.72))
    parts.append(f'  <text x="76" y="110" font-family="{FONT}" font-size="11.8" font-weight="bold" fill="{C_PRI}">Empiezas con 7 días de prueba gratis (incluye hasta 5 apps de empleados). Puedes activar tu licencia en cualquier momento.</text>')

    steps = [
        (
            "Person",
            C_PRI,
            C_PRI_BG,
            "Paso 1 · Tus datos",
            [
                "Revisa tu nombre, apellidos,",
                "carné de identidad y móvil",
                "(se toman de tu Perfil).",
                "Toca Siguiente (→).",
            ],
        ),
        (
            "Send",
            C_GRN,
            C_GRN_BG,
            "Paso 2 · Solicitar",
            [
                "Elige Mensual, Semestral,",
                "Anual o Perpetua + cuántas",
                "apps de empleados quieres.",
                "Envía por WhatsApp o SMS.",
            ],
        ),
        (
            "Verified",
            C_SEC,
            C_SEC_BG,
            "Paso 3 · Activar",
            [
                "Copia el mensaje SPVI2:",
                "que te envía el desarrollador.",
                "Toca Pegar y Confirmar (✓).",
                "¡Licencia activa sin internet!",
            ],
        ),
    ]

    bw, bh = 252, 174
    gap = 38
    x0 = (w - (3 * bw + 2 * gap)) / 2
    y0 = 148
    for i, (ico, col, bg, tit, lines) in enumerate(steps):
        x = x0 + i * (bw + gap)
        parts.append(card(x, y0, bw, bh, stroke=col, fill=C_CARD, sw=2.1))
        parts.append(f'  <rect x="{x:.1f}" y="{y0:.1f}" width="{bw:.1f}" height="46" rx="14" fill="{bg}"/>')
        parts.append(f'  <rect x="{x:.1f}" y="{y0 + 30:.1f}" width="{bw:.1f}" height="16" fill="{bg}"/>')
        parts.append(icon_svg(ico, x + 28, y0 + 23, r=15, bg=col, fg="#FFFFFF", scale=0.74))
        parts.append(f'  <text x="{x + 52:.1f}" y="{y0 + 28:.1f}" font-family="{FONT}" font-size="13" font-weight="bold" fill="{C_TEXT}">{esc(tit)}</text>')
        for j, ln in enumerate(lines):
            parts.append(f'  <text x="{x + bw / 2:.1f}" y="{y0 + 76 + j * 22:.1f}" font-family="{FONT}" font-size="11.3" fill="{C_TEXT}" text-anchor="middle">{esc(ln)}</text>')
        if i < 2:
            parts.append(arrow_h(x + bw + 4, y0 + bh / 2, x + bw + gap - 4, color=col))

    # Tabla visual de tarifas abajo
    parts.append(card(24, 340, 832, 80, stroke=C_BORDER, fill=C_CARD, sw=1.5))
    plans = [
        ("Mensual", "6,000 CUP", "+1,000 / empl.", C_PRI_BG, C_PRI),
        ("Semestral", "30,000 CUP", "+5,000 / empl.", C_TER_BG, C_TER),
        ("Anual", "50,000 CUP", "+9,000 / empl.", C_GRN_BG, C_GRN),
        ("Perpetua", "90,000 CUP", "+17,000 / empl.", C_SEC_BG, C_SEC),
    ]
    pw = 190
    pgap = 14
    px0 = (w - (4 * pw + 3 * pgap)) / 2
    for idx, (pname, base_p, emp_p, pbg, pcol) in enumerate(plans):
        px = px0 + idx * (pw + pgap)
        parts.append(f'  <rect x="{px:.1f}" y="351" width="{pw:.1f}" height="58" rx="10" fill="{pbg}" stroke="{pcol}" stroke-width="1.2"/>')
        parts.append(f'  <text x="{px + pw / 2:.1f}" y="369" font-family="{FONT}" font-size="11.5" font-weight="bold" fill="{pcol}" text-anchor="middle">{esc(pname)}: {esc(base_p)}</text>')
        parts.append(f'  <text x="{px + pw / 2:.1f}" y="388" font-family="{FONT}" font-size="10.5" fill="{C_TEXT}" text-anchor="middle">Secundaria: {esc(emp_p)}</text>')
        if idx == 2:
            parts.append(f'  <text x="{px + pw / 2:.1f}" y="403" font-family="{FONT}" font-size="9.5" fill="{C_SUB}" text-anchor="middle">Renovar antes suma los días</text>')
        elif idx == 3:
            parts.append(f'  <text x="{px + pw / 2:.1f}" y="403" font-family="{FONT}" font-size="9.5" fill="{C_SUB}" text-anchor="middle">Un solo pago, sin vencimiento</text>')
        else:
            parts.append(f'  <text x="{px + pw / 2:.1f}" y="403" font-family="{FONT}" font-size="9.5" fill="{C_SUB}" text-anchor="middle">Renovar antes no pierde días</text>')

    return svg_wrap(
        w,
        h,
        "Cómo solicitar, activar y renovar tu licencia",
        "Proceso guiado en 3 pasos por WhatsApp o SMS · Tarifas oficiales en CUP",
        "\n".join(parts),
    )


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    diagrams = {
        "01_navegacion_general.svg": diag_01_navegacion(),
        "02_ciclo_jornada_turno.svg": diag_02_ciclo_turno(),
        "03_flujo_venta_cobro.svg": diag_03_flujo_venta(),
        "04_ecosistema_inventario.svg": diag_04_inventario(),
        "05_arquitectura_apps_vinculadas.svg": diag_05_vinculacion(),
        "06_respaldo_cambio_telefono.svg": diag_06_respaldo(),
        "07_ciclo_licencia.svg": diag_07_licencia(),
    }
    for name, content in diagrams.items():
        p = OUT_DIR / name
        p.write_text(content, encoding="utf-8")
        print(f"Generado: {p.relative_to(ROOT)} ({len(content)} bytes)")


if __name__ == "__main__":
    main()
