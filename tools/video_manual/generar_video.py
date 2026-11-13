#!/usr/bin/env python3
"""Genera el video-tutorial Full HD de Shalev a partir del manual y sus capturas.

Uso:
  .venv/bin/python tools/video_manual/generar_video.py --preview
  .venv/bin/python tools/video_manual/generar_video.py --render

Las voces se leen de .cache/shalev_video/voz/01.wav ... 10.wav.
El video, los subtítulos y el guion se entregan en promocion/video_manual/.
"""
from __future__ import annotations

import argparse
import math
import os
from pathlib import Path
import subprocess
import sys
import textwrap
import wave
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFont
import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / ".cache" / "shalev_video"
VOICE = WORK / "voz"
PREVIEW = WORK / "preview"
OUT = ROOT / "promocion" / "video_manual"
W, H = 1920, 1080
FPS = 24
VOICE_DELAY = 0.12
SCENE_PAD = 0.35
TRANSITION = 0.38
CAPTION_Y = 956

# Paleta oficial de Shalev.
NAVY = "#10254B"
DARK = "#081526"
BLUE = "#2B4FA3"
CYAN = "#66D1EF"
ORANGE = "#EE7F3B"
ORANGE_DARK = "#B4541A"
TEAL = "#2F6F80"
INK = "#1A1C1E"
MUTED = "#5F6368"
LIGHT = "#F8F9FA"
PALE_BLUE = "#E6EDFA"
PALE_CYAN = "#E2F2F6"
PALE_ORANGE = "#FCE9DE"
WHITE = "#FFFFFF"
GREEN = "#24734A"
RED = "#C62828"
AMBER = "#8A6500"

FONT_REG_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_BRAND_PATH = str(ROOT / "Android/designsystem/src/main/res/font/orbitron.ttf")

A = "Android/app/capturas/claro"
P = "promocion/capturas"


def phone(path: str, x: int = 1400, y: int = 35, h: int = 900, crop=None, label=None):
    return {"path": path, "x": x, "y": y, "h": h, "crop": crop, "label": label}


def phase(at: float, layout: str, main_phone=None, phones=None):
    return {"at": at, "layout": layout, "phone": main_phone, "phones": phones or []}


SCENES = [
    {
        "n": 1,
        "slug": "apertura",
        "kicker": "SHALEV · VIDEO MANUAL",
        "title": "Tu negocio, bajo control",
        "audio": "01.wav",
        "narration": "Shalev es el punto de venta e inventario para pequeños negocios que funciona cien por ciento sin internet. Vende, controla caja, conoce ganancias y protege tus datos desde Android. Empieza con siete días de prueba gratuita.",
        "captions": [
            "Shalev es el punto de venta e inventario para pequeños negocios",
            "que funciona cien por ciento sin internet.",
            "Vende, controla caja, conoce ganancias y protege tus datos desde Android.",
            "Empieza con siete días de prueba gratuita.",
        ],
        "phases": [phase(0, "intro", phone(f"{P}/shot_inicio_claro.png", 1405, 70, 840))],
        "taps": [],
    },
    {
        "n": 2,
        "slug": "inicio_turno",
        "kicker": "01 · INICIO Y TURNO",
        "title": "El pulso del negocio, en una pantalla",
        "audio": "02.wav",
        "narration": "En Inicio tienes alertas de stock y caducidad, gráficos de ventas, ganancia neta, métodos de pago y rankings. Abre el turno, indica el fondo de caja y confirma. Registra entradas y salidas; al cerrar, arquea efectivo e inventario para cuadrar la jornada.",
        "captions": [
            "En Inicio tienes alertas de stock y caducidad,",
            "gráficos de ventas, ganancia neta, métodos de pago y rankings.",
            "Abre el turno, indica el fondo de caja y confirma.",
            "Registra entradas y salidas;",
            "al cerrar, arquea efectivo e inventario para cuadrar la jornada.",
        ],
        "phases": [
            phase(0, "home_overview", phone(f"{A}/01j_inicio_acordeones_cerrados.png")),
            phase(5.0, "shift", phone(f"{P}/shot_inicio_claro.png")),
            phase(6.6, "shift_open", phone(f"{A}/01j_inicio_acordeones_cerrados.png")),
            phase(11.3, "cash_close", phone(f"{A}/04s_turno_detalle_cerrado.png")),
        ],
        "taps": [
            {"phase": 1, "at": 5.85, "xy": (0.875, 0.134), "box": (0.77, 0.105, 0.96, 0.165)},
            {"phase": 2, "at": 7.75, "xy": (0.14, 0.936), "box": (0.03, 0.895, 0.23, 0.98)},
            {"phase": 2, "at": 9.15, "xy": (0.86, 0.936), "box": (0.76, 0.895, 0.96, 0.98)},
        ],
        "highlights": [
            {"phase": 0, "start": 0.5, "end": 4.7, "box": (0.03, 0.35, 0.97, 0.54), "label": "Alertas visibles"},
            {"phase": 3, "start": 12.0, "end": 16.8, "box": (0.04, 0.50, 0.96, 0.79), "label": "Resumen del turno"},
        ],
    },
    {
        "n": 3,
        "slug": "inventario_recetas",
        "kicker": "02 · INVENTARIO INTELIGENTE",
        "title": "Existencias y recetas, sin cuentas manuales",
        "audio": "03.wav",
        "narration": "En Inventario agrega artículos con foto, categoría, costo, precio y cantidad. Busca, filtra y evita faltantes con sus avisos. Crea insumos y elaborados con receta: Shalev calcula el costo, indica para cuántas unidades alcanza y descuenta cada ingrediente automáticamente al vender.",
        "captions": [
            "En Inventario agrega artículos con foto, categoría, costo, precio y cantidad.",
            "Busca, filtra y evita faltantes con sus avisos.",
            "Crea insumos y elaborados con receta:",
            "Shalev calcula el costo, indica para cuántas unidades alcanza",
            "y descuenta cada ingrediente automáticamente al vender.",
        ],
        "phases": [
            phase(0, "inventory", phone(f"{P}/shot_inventario_claro.png")),
            phase(7.0, "recipe", phone(f"{A}/08m_producto_elaborado_receta.png", 1370, 35, 900, (0, 600, 720, 1900), "Vista ampliada de la receta")),
        ],
        "taps": [
            {"phase": 0, "at": 1.6, "xy": (0.90, 0.846), "box": (0.82, 0.80, 0.97, 0.89)},
            {"phase": 0, "at": 5.15, "xy": (0.84, 0.043), "box": (0.78, 0.02, 0.91, 0.075)},
            {"phase": 1, "at": 9.3, "xy": (0.50, 0.615), "box": (0.39, 0.56, 0.61, 0.67)},
        ],
        "highlights": [
            {"phase": 1, "start": 11.0, "end": 14.5, "box": (0.08, 0.66, 0.92, 0.77), "label": "Costo calculado"},
        ],
    },
    {
        "n": 4,
        "slug": "servicios_precios",
        "kicker": "03 · SERVICIOS Y PRECIOS",
        "title": "Cobra tu trabajo y automatiza tus precios",
        "audio": "04.wav",
        "narration": "En Servicios registra cortes, reparaciones o cualquier trabajo, con importe e insumos opcionales para conocer la ganancia real. Las reglas de Precios aplican descuentos o recargos según el pago o el importe mínimo, sin cambiar los productos uno por uno.",
        "captions": [
            "En Servicios registra cortes, reparaciones o cualquier trabajo,",
            "con importe e insumos opcionales para conocer la ganancia real.",
            "Las reglas de Precios aplican descuentos o recargos",
            "según el pago o el importe mínimo, sin cambiar los productos uno por uno.",
        ],
        "phases": [
            phase(0, "services", phone(f"{P}/shot_servicios_claro.png")),
            phase(8.0, "pricing", phone(f"{A}/06i_precios_lista.png")),
        ],
        "taps": [
            {"phase": 0, "at": 2.0, "xy": (0.90, 0.846), "box": (0.82, 0.80, 0.97, 0.89)},
            {"phase": 1, "at": 9.5, "xy": (0.875, 0.94), "box": (0.79, 0.89, 0.96, 0.985)},
            {"phase": 1, "at": 12.1, "xy": (0.875, 0.325), "box": (0.79, 0.29, 0.96, 0.36)},
        ],
        "highlights": [],
    },
    {
        "n": 5,
        "slug": "venta_cobro",
        "kicker": "04 · VENDER Y COBRAR",
        "title": "De carrito a cobro verificado en pocos toques",
        "audio": "05.wav",
        "narration": "Para vender, toca el carrito, elige productos o servicios y ajusta cantidades. Selecciona Efectivo o Transferencia y confirma. Con Transfermóvil, el cliente escanea el código QR y escribe el importe visible. Registra sus datos y el número de transacción pegando o compartiendo el SMS; también hay captura opcional de notificaciones.",
        "captions": [
            "Para vender, toca el carrito, elige productos o servicios y ajusta cantidades.",
            "Selecciona Efectivo o Transferencia y confirma.",
            "Con Transfermóvil, el cliente escanea el código QR y escribe el importe visible.",
            "Registra sus datos y el número de transacción",
            "pegando o compartiendo el SMS;",
            "también hay captura opcional de notificaciones.",
        ],
        "phases": [
            phase(0, "sale_cart", phone(f"{A}/05a_venta_carrito.png")),
            phase(4.8, "sale_cart_selected", phone(f"{A}/05a2_venta_carrito_transferencia.png")),
            phase(7.2, "sale_qr", phone(f"{A}/05f_venta_qr.png")),
            phase(13.3, "sale_data", phone(f"{A}/05h_venta_datos_cliente.png")),
        ],
        "taps": [
            {"phase": 0, "at": 1.45, "xy": (0.63, 0.335), "box": (0.55, 0.29, 0.72, 0.38)},
            {"phase": 0, "at": 4.35, "xy": (0.64, 0.76), "box": (0.45, 0.72, 0.85, 0.80)},
            {"phase": 1, "at": 6.25, "xy": (0.50, 0.945), "box": (0.40, 0.90, 0.60, 0.99)},
            {"phase": 2, "at": 12.35, "xy": (0.50, 0.945), "box": (0.40, 0.90, 0.60, 0.99)},
            {"phase": 3, "at": 15.25, "xy": (0.50, 0.65), "box": (0.41, 0.60, 0.59, 0.70)},
        ],
        "highlights": [
            {"phase": 2, "start": 8.0, "end": 10.8, "box": (0.16, 0.10, 0.84, 0.41), "label": "El cliente escanea"},
            {"phase": 2, "start": 10.0, "end": 12.9, "box": (0.20, 0.43, 0.84, 0.52), "label": "Importe visible"},
            {"phase": 3, "start": 17.4, "end": 20.7, "box": (0.04, 0.74, 0.96, 0.84), "label": "Aviso de diferencia"},
        ],
    },
    {
        "n": 6,
        "slug": "registros_exportacion",
        "kicker": "05 · REGISTROS Y EXPORTACIÓN",
        "title": "Audita, corrige y comparte resultados",
        "audio": "06.wav",
        "narration": "Registros reúne ventas, servicios, transferencias, movimientos, clientes y turnos. Filtra, audita y, con el turno abierto, modifica o anula dejando trazabilidad. Exporta PDF o Excel y crea listas de precios, imágenes y tarjetas promocionales para compartir, sin mostrar costos ni existencias.",
        "captions": [
            "Registros reúne ventas, servicios, transferencias, movimientos, clientes y turnos.",
            "Filtra, audita y, con el turno abierto,",
            "modifica o anula dejando trazabilidad.",
            "Exporta PDF o Excel y crea listas de precios, imágenes y tarjetas promocionales",
            "para compartir, sin mostrar costos ni existencias.",
        ],
        "phases": [
            phase(0, "records", phone(f"{A}/04a_registros_ventas.png")),
            phase(6.0, "corrections", phone(f"{A}/04o_registros_ficha_venta.png")),
            phase(12.0, "exports", phone(f"{A}/02j_inventario_hoja_exportar.png")),
        ],
        "taps": [
            {"phase": 0, "at": 2.0, "xy": (0.78, 0.04), "box": (0.71, 0.015, 0.85, 0.075)},
            {"phase": 0, "at": 5.35, "xy": (0.50, 0.30), "box": (0.07, 0.26, 0.94, 0.34)},
            {"phase": 2, "at": 16.4, "xy": (0.75, 0.64), "box": (0.67, 0.59, 0.82, 0.69)},
        ],
        "highlights": [],
    },
    {
        "n": 7,
        "slug": "equipo",
        "kicker": "06 · APPS VINCULADAS",
        "title": "Un equipo conectado por Wi-Fi local",
        "audio": "07.wav",
        "narration": "Vincula hasta diez teléfonos de empleados por código QR y wifi local, sin gastar datos. El dueño asigna permisos y tarjeta de cobro. Cada empleado puede seguir vendiendo si se interrumpe la conexión, sincroniza sus operaciones y envía el cierre para revisión y aprobación.",
        "captions": [
            "Vincula hasta diez teléfonos de empleados por código QR y wifi local, sin gastar datos.",
            "El dueño asigna permisos y tarjeta de cobro.",
            "Cada empleado puede seguir vendiendo si se interrumpe la conexión,",
            "sincroniza sus operaciones y envía el cierre para revisión y aprobación.",
        ],
        "phases": [
            phase(0, "team", phone(f"{A}/07p_vinculacion_principal_turnos.png")),
            phase(7.5, "team_sync", phone(f"{A}/07s_vinculacion_secundaria.png")),
        ],
        "taps": [
            {"phase": 0, "at": 2.0, "xy": (0.875, 0.94), "box": (0.79, 0.89, 0.96, 0.985)},
            {"phase": 1, "at": 10.1, "xy": (0.50, 0.315), "box": (0.40, 0.27, 0.60, 0.36)},
        ],
        "highlights": [],
    },
    {
        "n": 8,
        "slug": "seguridad_respaldo",
        "kicker": "07 · PRIVACIDAD Y RESPALDO",
        "title": "Tus datos, protegidos y siempre recuperables",
        "audio": "08.wav",
        "narration": "Los datos quedan en tu teléfono, sin anuncios ni rastreo. Activa acceso con huella, rostro o PIN. En Ajustes, Respaldo, exporta un archivo punto shalev con contraseña para restaurar al cambiar de equipo. Importar reemplaza los datos actuales; las fotos no se incluyen en la copia.",
        "captions": [
            "Los datos quedan en tu teléfono, sin anuncios ni rastreo.",
            "Activa acceso con huella, rostro o PIN.",
            "En Ajustes, Respaldo, exporta un archivo punto shalev con contraseña",
            "para restaurar al cambiar de equipo.",
            "Importar reemplaza los datos actuales; las fotos no se incluyen en la copia.",
        ],
        "phases": [
            phase(0, "security", phone(f"{A}/09a_respaldo_paso1.png", 1430, 35, 900, (0, 0, 720, 1750), "Respaldo · vista ampliada")),
            phase(8.0, "backup_flow", phone(f"{A}/09a_respaldo_paso1.png", 1430, 35, 900, (0, 0, 720, 1750), "Respaldo · vista ampliada")),
        ],
        "taps": [
            {"phase": 0, "at": 4.0, "xy": (0.50, 0.50), "box": (0.08, 0.45, 0.92, 0.55)},
            {"phase": 0, "at": 6.85, "xy": (0.85, 0.337), "box": (0.76, 0.29, 0.94, 0.38)},
            {"phase": 1, "at": 14.4, "xy": (0.50, 0.858), "box": (0.40, 0.81, 0.60, 0.91)},
        ],
        "highlights": [],
    },
    {
        "n": 9,
        "slug": "beneficios",
        "kicker": "08 · BENEFICIOS PARA TU NEGOCIO",
        "title": "Menos errores. Más tiempo para crecer.",
        "audio": "09.wav",
        "narration": "El resultado: menos errores, inventario al día, caja cuadrada, cobros verificables y decisiones con cifras reales. Disfruta temas claro y oscuro, letra adaptable, ayuda integrada y soporte directo, con licencia y actualizaciones gestionadas desde Ajustes.",
        "captions": [
            "El resultado: menos errores, inventario al día, caja cuadrada,",
            "cobros verificables y decisiones con cifras reales.",
            "Disfruta temas claro y oscuro, letra adaptable, ayuda integrada y soporte directo,",
            "con licencia y actualizaciones gestionadas desde Ajustes.",
        ],
        "phases": [phase(0, "benefits", phones=[
            phone(f"{A}/01a2_inicio_con_datos_pantalla.png", 1270, 165, 760, label="Tema claro"),
            phone("Android/app/capturas/oscuro/01a2_inicio_con_datos_pantalla.png", 1570, 165, 760, label="Tema oscuro"),
        ])],
        "taps": [],
        "highlights": [],
    },
    {
        "n": 10,
        "slug": "cierre",
        "kicker": "SHALEV",
        "title": "Vende mejor. Controla más.",
        "audio": "10.wav",
        "narration": "Shalev: vende mejor, controla más y haz crecer tu negocio. Descárgalo para Android y empieza hoy tu prueba gratuita de siete días.",
        "captions": [
            "Shalev: vende mejor, controla más y haz crecer tu negocio.",
            "Descárgalo para Android y empieza hoy tu prueba gratuita de siete días.",
        ],
        "phases": [phase(0, "outro")],
        "taps": [],
        "highlights": [],
    },
]


@lru_cache(maxsize=128)
def font(size: int, bold: bool = False, brand: bool = False):
    path = FONT_BRAND_PATH if brand else (FONT_BOLD_PATH if bold else FONT_REG_PATH)
    return ImageFont.truetype(path, size=size)


def ease(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def lerp(a, b, t):
    return a + (b - a) * t


def hexrgb(value: str):
    value = value.lstrip("#")
    return tuple(int(value[i:i+2], 16) for i in (0, 2, 4))


@lru_cache(maxsize=4)
def background(kind: str = "light"):
    if kind == "dark":
        c1, c2 = np.array(hexrgb(DARK)), np.array(hexrgb("#142B4B"))
    else:
        c1, c2 = np.array(hexrgb("#F8FAFD")), np.array(hexrgb("#E8EFF9"))
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    mix = np.clip(0.60 * xx / W + 0.40 * yy / H, 0, 1)[..., None]
    arr = (c1 * (1 - mix) + c2 * mix).astype(np.uint8)
    im = Image.fromarray(arr, "RGB")
    d = ImageDraw.Draw(im, "RGBA")
    if kind == "dark":
        d.ellipse((1230, -330, 2060, 500), fill=(*hexrgb(BLUE), 65))
        d.ellipse((-260, 650, 450, 1360), fill=(*hexrgb(TEAL), 32))
        for x in range(40, W, 80):
            d.line((x, 0, x, H), fill=(255, 255, 255, 5), width=1)
        for y in range(40, H, 80):
            d.line((0, y, W, y), fill=(255, 255, 255, 5), width=1)
    else:
        d.ellipse((1430, -360, 2130, 340), fill=(*hexrgb(CYAN), 22))
        d.ellipse((-300, 720, 330, 1350), fill=(*hexrgb(ORANGE), 13))
        d.rounded_rectangle((34, 40, 44, 210), radius=5, fill=BLUE)
    return im


def text_size(draw, txt, f):
    b = draw.textbbox((0, 0), txt, font=f)
    return b[2] - b[0], b[3] - b[1]


def wrap_lines(draw, txt: str, f, max_width: int):
    lines = []
    for para in txt.split("\n"):
        if not para:
            lines.append("")
            continue
        words = para.split()
        line = words[0]
        for word in words[1:]:
            trial = line + " " + word
            if text_size(draw, trial, f)[0] <= max_width:
                line = trial
            else:
                lines.append(line)
                line = word
        lines.append(line)
    return lines


def paragraph(draw, xy, txt, size=28, fill=INK, bold=False, max_width=1000, line_gap=10, anchor="la"):
    x, y = xy
    f = font(size, bold)
    lines = wrap_lines(draw, txt, f, max_width)
    line_h = size + line_gap
    for i, line in enumerate(lines):
        draw.text((x, y + i * line_h), line, font=f, fill=fill, anchor=anchor)
    return y + len(lines) * line_h


def pill(draw, box, txt, fill=PALE_BLUE, color=BLUE, size=23, outline=None, bold=True):
    draw.rounded_rectangle(box, radius=(box[3]-box[1])//2, fill=fill, outline=outline, width=2 if outline else 1)
    draw.text(((box[0]+box[2])/2, (box[1]+box[3])/2-1), txt, font=font(size, bold), fill=color, anchor="mm")


def card(draw, box, fill=WHITE, outline="#D8E0EC", radius=24, shadow=True):
    if shadow:
        draw.rounded_rectangle((box[0]+5, box[1]+8, box[2]+5, box[3]+8), radius=radius, fill="#DCE4EF")
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=2)


def section_header(draw, scene):
    draw.text((94, 50), scene["kicker"], font=font(22, True), fill=BLUE)
    draw.text((94, 88), scene["title"], font=font(48, True), fill=INK)
    draw.rounded_rectangle((94, 157, 260, 164), radius=3, fill=ORANGE)
    pill(draw, (1170, 60, 1300, 108), f'{scene["n"]:02d} / 10', fill=PALE_BLUE, color=BLUE, size=20)


def bullet_list(draw, x, y, items, width=1080, size=29, gap=20, color=INK, check=True):
    for item in items:
        if check:
            draw.ellipse((x, y+4, x+32, y+36), fill=PALE_CYAN)
            draw.text((x+16, y+19), "✓", font=font(22, True), fill=TEAL, anchor="mm")
            tx = x + 50
        else:
            tx = x
        y = paragraph(draw, (tx, y), item, size=size, fill=color, max_width=width-(tx-x), line_gap=8) + gap
    return y


def step(draw, x, y, number, title, detail, active=True, width=1090):
    color = BLUE if active else MUTED
    card(draw, (x, y, x+width, y+112), fill=WHITE if active else "#F2F4F7", radius=22)
    draw.ellipse((x+22, y+23, x+88, y+89), fill=color)
    draw.text((x+55, y+55), str(number), font=font(29, True), fill=WHITE, anchor="mm")
    draw.text((x+112, y+22), title, font=font(30, True), fill=INK)
    draw.text((x+112, y+65), detail, font=font(23), fill=MUTED)


def arrow(draw, start, end, color=BLUE, width=5, head=16):
    draw.line((*start, *end), fill=color, width=width)
    ang = math.atan2(end[1]-start[1], end[0]-start[0])
    p1 = (end[0]-head*math.cos(ang-0.55), end[1]-head*math.sin(ang-0.55))
    p2 = (end[0]-head*math.cos(ang+0.55), end[1]-head*math.sin(ang+0.55))
    draw.polygon([end, p1, p2], fill=color)


@lru_cache(maxsize=64)
def phone_asset(path, crop, height):
    src = Image.open(ROOT / path).convert("RGB")
    if crop:
        src = src.crop(crop)
    width = round(src.width * height / src.height)
    src = src.resize((width, height), Image.Resampling.LANCZOS)
    mask = Image.new("L", (width, height), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle((0, 0, width-1, height-1), radius=42, fill=255)
    return src, mask


def phone_geom(spec):
    crop = tuple(spec["crop"]) if spec.get("crop") else None
    asset, _ = phone_asset(spec["path"], crop, spec["h"])
    return spec["x"], spec["y"], asset.width, asset.height


def paste_phone(frame, spec):
    crop = tuple(spec["crop"]) if spec.get("crop") else None
    asset, mask = phone_asset(spec["path"], crop, spec["h"])
    x, y, w, h = phone_geom(spec)
    d = ImageDraw.Draw(frame, "RGBA")
    d.rounded_rectangle((x-8+10, y-8+14, x+w+8+10, y+h+8+14), radius=52, fill=(16, 37, 75, 45))
    d.rounded_rectangle((x-7, y-7, x+w+7, y+h+7), radius=49, fill=NAVY)
    frame.paste(asset, (x, y), mask)
    d.rounded_rectangle((x-2, y-2, x+w+2, y+h+2), radius=44, outline=(255, 255, 255, 100), width=2)
    if spec.get("label"):
        pill(d, (x+20, y+18, x+w-20, y+62), spec["label"], fill=(16, 37, 75, 225), color=WHITE, size=18)


def mini_title(draw, txt, y=205, color=BLUE):
    draw.text((98, y), txt, font=font(36, True), fill=color)


def draw_intro(frame, scene, ph):
    d = ImageDraw.Draw(frame, "RGBA")
    # Logo oficial en una tarjeta limpia.
    logo = Image.open(ROOT / "Android/play_store/icono_512.png").convert("RGB").resize((250, 250), Image.Resampling.LANCZOS)
    mask = Image.new("L", (250, 250), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 249, 249), radius=54, fill=255)
    d.rounded_rectangle((137, 177, 413, 453), radius=62, fill=(255, 255, 255, 28))
    frame.paste(logo, (150, 190), mask)
    d.text((150, 490), "SHALEV", font=font(94, brand=True), fill=WHITE)
    d.text((154, 610), "PUNTO DE VENTA · INVENTARIO · SERVICIOS", font=font(25, True), fill=CYAN)
    d.text((150, 680), "Vende. Controla. Crece.", font=font(51, True), fill=WHITE)
    d.text((150, 755), "Tu negocio funciona, incluso sin internet.", font=font(29), fill="#DCE6FA")
    pill(d, (150, 830, 490, 886), "100 % SIN INTERNET", fill=CYAN, color=DARK, size=23)
    pill(d, (510, 830, 790, 886), "ANDROID 8.0+", fill="#203A5E", color=WHITE, size=23, outline="#4B668B")
    pill(d, (810, 830, 1080, 886), "7 DÍAS GRATIS", fill=ORANGE_DARK, color=WHITE, size=23)
    for i, (label, y) in enumerate([("VENTAS", 260), ("INVENTARIO", 450), ("SERVICIOS", 640)]):
        card(d, (1120, y, 1325, y+112), fill="#142C4D", outline="#36557D", radius=24, shadow=False)
        d.text((1230, y+56), label, font=font(22, True), fill=WHITE, anchor="mm")
        d.line((1325, y+56, 1380, y+56), fill=(*hexrgb(CYAN), 130), width=3)
    paste_phone(frame, ph["phone"])


def draw_home_overview(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Todo lo importante, a primera vista")
    items = [
        ("ALERTAS", "Stock bajo, crítico y caducidad", PALE_ORANGE, ORANGE_DARK),
        ("GRÁFICOS", "Ventas y ganancia neta", PALE_BLUE, BLUE),
        ("PAGOS", "Efectivo frente a transferencia", PALE_CYAN, TEAL),
        ("TOP 3", "Productos, clientes y vendedores", "#F3E8FA", "#7B3FA0"),
    ]
    for i, (title, detail, fill, color) in enumerate(items):
        x = 98 + (i % 2) * 580
        y = 280 + (i // 2) * 205
        card(d, (x, y, x+548, y+170), fill=fill, outline=fill)
        d.text((x+26, y+25), title, font=font(24, True), fill=color)
        paragraph(d, (x+26, y+70), detail, size=28, fill=INK, max_width=495, line_gap=8)
    card(d, (98, 710, 1228, 858), fill=WHITE)
    d.text((128, 738), "ELIGE EL PERÍODO", font=font(22, True), fill=BLUE)
    d.text((128, 785), "Turno  ·  Hoy  ·  7 días  ·  Mes  ·  Año", font=font(30, True), fill=INK)
    paste_phone(frame, ph["phone"])


def draw_shift(frame, ph, opened=False):
    d = ImageDraw.Draw(frame)
    mini_title(d, "La jornada empieza con caja clara")
    if not opened:
        step(d, 98, 280, 1, "Abre el turno", "Toca el interruptor en Inicio")
        step(d, 98, 414, 2, "Indica el fondo", "Escribe el efectivo inicial en CUP")
        step(d, 98, 548, 3, "Confirma  ✓", "Se habilitan las ventas")
    else:
        card(d, (98, 280, 1190, 405), fill=PALE_CYAN, outline=PALE_CYAN)
        d.text((130, 303), "✓  TURNO ABIERTO", font=font(32, True), fill=TEAL)
        d.text((130, 352), "Catálogo y precios listos para trabajar", font=font(26), fill=INK)
        step(d, 98, 445, 1, "Nueva venta", "Carrito flotante, abajo a la izquierda")
        step(d, 98, 579, 2, "Movimiento de caja", "Registra entradas y salidas con su motivo")
    pill(d, (98, 755, 1190, 820), "REGLA DE ORO: NO SE VENDE SIN UN TURNO ABIERTO", fill=PALE_ORANGE, color=ORANGE_DARK, size=25)
    paste_phone(frame, ph["phone"])


def draw_cash_close(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Cierra con arqueo y conciliación")
    card(d, (98, 270, 1225, 440), fill=NAVY, outline=NAVY)
    d.text((130, 298), "EFECTIVO ESPERADO", font=font(23, True), fill=CYAN)
    paragraph(d, (130, 346), "Fondo + Ventas + Entradas − Salidas", size=34, fill=WHITE, bold=True, max_width=1050)
    d.text((98, 500), "CIERRE EN 3 PASOS", font=font(24, True), fill=BLUE)
    labels = [("1", "Efectivo", "Cuenta billetes y monedas"), ("2", "Inventario", "Solo artículos con movimiento"), ("3", "Revisión", "Diferencias y motivo")]
    for i, (num, title, detail) in enumerate(labels):
        x = 98 + i*382
        card(d, (x, 555, x+350, 760), fill=WHITE)
        d.ellipse((x+22, 577, x+76, 631), fill=BLUE)
        d.text((x+49, 603), num, font=font(24, True), fill=WHITE, anchor="mm")
        d.text((x+94, 580), title, font=font(28, True), fill=INK)
        paragraph(d, (x+24, 654), detail, size=23, fill=MUTED, max_width=300, line_gap=6)
    pill(d, (98, 810, 1190, 868), "EL TURNO CERRADO CONSERVA SU INFORME EN REGISTROS", fill=PALE_BLUE, color=BLUE, size=23)
    paste_phone(frame, ph["phone"])


def draw_inventory(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Artículos completos y fáciles de localizar")
    bullet_list(d, 104, 292, [
        "Foto, categoría y descripción",
        "Costo, precio de venta y cantidad inicial",
        "Caducidad y avisos de existencias",
        "Búsqueda, filtros y selección múltiple",
    ], width=1110, size=30, gap=23)
    card(d, (98, 690, 1210, 855), fill=PALE_ORANGE, outline=PALE_ORANGE)
    d.text((130, 720), "PREVENIR ES MEJOR QUE IMPROVISAR", font=font(23, True), fill=ORANGE_DARK)
    paragraph(d, (130, 767), "Filtra por estado, tipo o categoría y repón antes de quedarte sin stock.", size=27, fill=INK, max_width=1030)
    paste_phone(frame, ph["phone"])


def draw_recipe(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Una receta descuenta todos sus insumos")
    ingredients = [("HARINA", "0.25 kg"), ("PURÉ", "0.10 kg"), ("QUESO", "0.08 kg")]
    for i, (name, qty) in enumerate(ingredients):
        y = 290 + i*132
        card(d, (98, y, 510, y+102), fill=WHITE)
        d.text((126, y+20), name, font=font(22, True), fill=TEAL)
        d.text((480, y+51), qty, font=font(28, True), fill=INK, anchor="rm")
        arrow(d, (520, y+51), (655, 480), color=TEAL, width=4)
    card(d, (675, 335, 1095, 625), fill=PALE_ORANGE, outline=ORANGE, radius=32)
    d.text((885, 390), "1 PIZZA", font=font(38, True), fill=ORANGE_DARK, anchor="mm")
    d.text((885, 452), "Costo: 275.00 CUP", font=font(28, True), fill=INK, anchor="mm")
    d.text((885, 510), "Precio: 400.00 CUP", font=font(28), fill=INK, anchor="mm")
    pill(d, (730, 552, 1040, 603), "ALCANZA PARA N", fill=WHITE, color=ORANGE_DARK, size=22)
    card(d, (98, 720, 1190, 866), fill=NAVY, outline=NAVY)
    d.text((130, 747), "AL CONFIRMAR LA VENTA", font=font(23, True), fill=CYAN)
    d.text((130, 798), "Shalev descuenta cada ingrediente automáticamente.", font=font(28, True), fill=WHITE)
    paste_phone(frame, ph["phone"])


def draw_services(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "También vendes tu tiempo y tu experiencia")
    labels = ["Barbería", "Belleza", "Lavandería", "Reparaciones", "Domicilio"]
    x, y = 98, 290
    for i, label in enumerate(labels):
        w = 110 + len(label)*15
        if x+w > 1210:
            x, y = 98, y+76
        pill(d, (x, y, x+w, y+56), label, fill=PALE_BLUE if i%2==0 else PALE_CYAN, color=BLUE if i%2==0 else TEAL, size=22)
        x += w+18
    card(d, (98, 500, 1190, 680), fill=WHITE)
    d.text((130, 532), "GANANCIA REAL", font=font(24, True), fill=BLUE)
    paragraph(d, (130, 585), "Importe del servicio − costo de sus insumos", size=34, fill=INK, bold=True, max_width=1010)
    pill(d, (98, 745, 1190, 825), "FOTO · TIPO · IMPORTE · INSUMOS OPCIONALES", fill=PALE_CYAN, color=TEAL, size=25)
    paste_phone(frame, ph["phone"])


def draw_pricing(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Reglas que se aplican al cobrar")
    card(d, (98, 280, 1190, 435), fill=PALE_BLUE, outline=PALE_BLUE)
    d.text((130, 310), "TRANSFERENCIA", font=font(24, True), fill=BLUE)
    d.text((1130, 340), "+5 %", font=font(52, True), fill=BLUE, anchor="rm")
    d.text((130, 366), "Recargo según método de pago", font=font(27), fill=INK)
    card(d, (98, 460, 1190, 615), fill=PALE_ORANGE, outline=PALE_ORANGE)
    d.text((130, 490), "POR MAYOR", font=font(24, True), fill=ORANGE_DARK)
    d.text((1130, 520), "−10 %", font=font(52, True), fill=ORANGE_DARK, anchor="rm")
    d.text((130, 546), "Descuento con importe mínimo opcional", font=font(27), fill=INK)
    bullet_list(d, 104, 680, ["Activa o pausa cada regla", "Vista previa antes de guardar", "Nunca rebaja por debajo del costo"], width=1100, size=27, gap=13)
    paste_phone(frame, ph["phone"])


def sale_steps(draw, active):
    labels = ["CARRITO", "COBRO", "TRANSACCIÓN"]
    for i, label in enumerate(labels, 1):
        x = 98 + (i-1)*378
        fill = BLUE if i == active else "#E7EBF2"
        color = WHITE if i == active else MUTED
        pill(draw, (x, 205, x+350, 263), f"{i} · {label}", fill=fill, color=color, size=22)


def draw_sale_cart(frame, ph, selected=False):
    d = ImageDraw.Draw(frame)
    sale_steps(d, 1)
    mini_title(d, "Productos o servicios, total al instante", y=305)
    bullet_list(d, 104, 390, ["Toca los artículos para añadirlos", "Ajusta unidades con − y +", "Revisa el total antes de cobrar"], width=1110, size=30, gap=24)
    card(d, (98, 650, 630, 815), fill=PALE_CYAN, outline=PALE_CYAN)
    d.text((130, 680), "EFECTIVO", font=font(27, True), fill=TEAL)
    paragraph(d, (130, 730), "Suma el cobro a la caja.", size=25, fill=INK, max_width=450)
    card(d, (660, 650, 1190, 815), fill=BLUE if selected else PALE_BLUE, outline=BLUE if selected else PALE_BLUE)
    d.text((692, 680), "TRANSFERENCIA", font=font(27, True), fill=WHITE if selected else BLUE)
    paragraph(d, (692, 730), "QR, datos y validación.", size=25, fill=WHITE if selected else INK, max_width=450)
    paste_phone(frame, ph["phone"])


def draw_sale_qr(frame, ph):
    d = ImageDraw.Draw(frame)
    sale_steps(d, 2)
    mini_title(d, "Transfermóvil, sin escribir la tarjeta", y=305)
    step(d, 98, 380, 1, "El cliente escanea", "El QR lleva tarjeta y móvil", width=1092)
    step(d, 98, 512, 2, "Escribe el importe", "Lo ve grande debajo del QR", width=1092)
    card(d, (98, 675, 1190, 810), fill=NAVY, outline=NAVY)
    d.text((130, 697), "IMPORTE A TRANSFERIR", font=font(22, True), fill=CYAN)
    d.text((130, 743), "1,100.00 CUP", font=font(46, True), fill=WHITE)
    pill(d, (720, 716, 1150, 780), "EL QR NO LO INCLUYE", fill=ORANGE_DARK, color=WHITE, size=20)
    paragraph(d, (100, 850), "Antes: marca un teléfono y una tarjeta «En uso» en Pago electrónico.", size=23, fill=MUTED, max_width=1120)
    paste_phone(frame, ph["phone"])


def draw_sale_data(frame, ph):
    d = ImageDraw.Draw(frame)
    sale_steps(d, 3)
    mini_title(d, "Verifica el pago y conserva la evidencia", y=305)
    bullet_list(d, 104, 375, ["Datos del cliente y número de transacción", "Cliente fijo para autocompletar próximas ventas"], width=1110, size=28, gap=18)
    for i, label in enumerate(["PEGAR", "COMPARTIR SMS", "NOTIFICACIONES\nOPCIONALES"]):
        x = 98 + i*376
        card(d, (x, 570, x+348, 690), fill=PALE_BLUE if i<2 else PALE_CYAN, outline=PALE_BLUE if i<2 else PALE_CYAN)
        paragraph(d, (x+174, 607), label, size=21, fill=BLUE if i<2 else TEAL, bold=True, max_width=320, anchor="ma", line_gap=4)
    card(d, (98, 740, 1190, 865), fill=PALE_ORANGE, outline=ORANGE)
    d.text((130, 766), "CONTROL ADICIONAL", font=font(22, True), fill=ORANGE_DARK)
    d.text((130, 811), "Avisa si el importe del SMS no coincide.", font=font(28, True), fill=INK)
    paste_phone(frame, ph["phone"])


def draw_records(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Seis vistas para encontrar cada operación")
    labels = [("01", "Ventas"), ("02", "Servicios"), ("03", "Transferencias"), ("04", "Movimientos"), ("05", "Clientes"), ("06", "Turnos")]
    for i, (num, label) in enumerate(labels):
        x = 98 + (i%2)*565
        y = 280 + (i//2)*150
        card(d, (x, y, x+535, y+120), fill=WHITE)
        d.text((x+28, y+28), num, font=font(23, True), fill=BLUE)
        d.text((x+100, y+27), label, font=font(29, True), fill=INK)
        d.text((x+100, y+72), "Filtra y abre la ficha", font=font(22), fill=MUTED)
    pill(d, (98, 770, 1190, 842), "FECHAS · IMPORTES · VENDEDOR · BÚSQUEDA", fill=PALE_BLUE, color=BLUE, size=24)
    paste_phone(frame, ph["phone"])


def draw_corrections(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Corrige sin perder el rastro")
    card(d, (98, 285, 625, 535), fill=PALE_BLUE, outline=PALE_BLUE)
    d.text((130, 320), "MODIFICAR", font=font(30, True), fill=BLUE)
    paragraph(d, (130, 385), "Cambia cantidades, artículos o método de pago con un motivo.", size=26, fill=INK, max_width=450)
    card(d, (660, 285, 1190, 535), fill=PALE_ORANGE, outline=PALE_ORANGE)
    d.text((692, 320), "ANULAR", font=font(30, True), fill=ORANGE_DARK)
    paragraph(d, (692, 385), "Deja de sumar en caja y devuelve las existencias automáticamente.", size=26, fill=INK, max_width=450)
    bullet_list(d, 104, 620, ["Solo con el turno abierto y en la app principal", "El motivo y la relación con la operación original quedan guardados", "Los turnos cerrados permanecen sellados"], width=1110, size=27, gap=19)
    paste_phone(frame, ph["phone"])


def draw_exports(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Un documento para cada destinatario")
    card(d, (98, 280, 1190, 460), fill=NAVY, outline=NAVY)
    d.text((130, 310), "USO INTERNO", font=font(23, True), fill=CYAN)
    d.text((130, 358), "PDF  ·  EXCEL", font=font(43, True), fill=WHITE)
    d.text((130, 414), "Costos, ganancias, registros y turnos", font=font(25), fill="#DCE6FA")
    card(d, (98, 490, 1190, 670), fill=PALE_CYAN, outline=PALE_CYAN)
    d.text((130, 520), "PARA CLIENTES", font=font(23, True), fill=TEAL)
    d.text((130, 568), "IMAGEN  ·  TARJETAS PNG", font=font(36, True), fill=INK)
    d.text((130, 620), "Sin costos ni existencias; listos para compartir", font=font(25), fill=INK)
    card(d, (98, 720, 1190, 870), fill=WHITE)
    d.text((130, 746), "EXPORTA SOLO LO QUE NECESITAS", font=font(22, True), fill=BLUE)
    paragraph(d, (130, 790), "Marca casillas para exportar la selección; sin selección, se usa lo visible según el filtro.", size=25, fill=INK, max_width=1020)
    paste_phone(frame, ph["phone"])


def network_diagram(draw, sync=False):
    # Diagrama grande y legible de la arquitectura local.
    nodes = [(185, 365, "PRINCIPAL", BLUE), (720, 280, "EMPLEADO 1", TEAL), (720, 480, "EMPLEADO 2", TEAL), (720, 660, "+ HASTA 10", MUTED)]
    if not sync:
        draw.rounded_rectangle((420, 380, 585, 550), radius=30, fill=PALE_CYAN, outline=TEAL, width=3)
        draw.text((502, 438), "QR", font=font(44, True), fill=TEAL, anchor="mm")
        draw.text((502, 502), "Wi-Fi", font=font(25, True), fill=INK, anchor="mm")
    else:
        draw.rounded_rectangle((410, 390, 610, 540), radius=30, fill=PALE_ORANGE, outline=ORANGE, width=3)
        draw.text((510, 445), "SYNC", font=font(35, True), fill=ORANGE_DARK, anchor="mm")
        draw.text((510, 495), "manual", font=font(23), fill=INK, anchor="mm")
    for x, y, label, color in nodes:
        draw.rounded_rectangle((x, y, x+270, y+130), radius=28, fill=WHITE, outline=color, width=4)
        draw.rounded_rectangle((x+22, y+25, x+66, y+103), radius=10, outline=color, width=4)
        draw.text((x+88, y+33), label, font=font(23, True), fill=color)
        draw.text((x+88, y+77), "Shalev", font=font(24), fill=INK)
        if x == 185:
            arrow(draw, (455, 430), (700, 345), color=BLUE, width=4)
            arrow(draw, (455, 465), (700, 545), color=BLUE, width=4)
        else:
            draw.line((455, 465, 690, 725), fill="#AAB6C8", width=3)


def draw_team(frame, ph, sync=False):
    d = ImageDraw.Draw(frame)
    network_diagram(d, sync)
    if not sync:
        pill(d, (98, 205, 1190, 260), "MISMA WI-FI LOCAL · SIN DATOS MÓVILES", fill=PALE_CYAN, color=TEAL, size=23)
        card(d, (98, 800, 1190, 885), fill=WHITE)
        d.text((130, 824), "El dueño asigna permisos y tarjeta de cobro para cada empleado.", font=font(25, True), fill=INK)
    else:
        pill(d, (98, 205, 1190, 260), "SI SE VA LA WI-FI, EL EMPLEADO SIGUE VENDIENDO", fill=PALE_ORANGE, color=ORANGE_DARK, size=22)
        card(d, (98, 800, 1190, 885), fill=NAVY, outline=NAVY)
        d.text((130, 824), "Sincronizar  →  Revisar cierre  →  Aprobar o rechazar", font=font(26, True), fill=WHITE)
    paste_phone(frame, ph["phone"])


def shield(draw, center, scale=1.0, color=BLUE):
    cx, cy = center
    pts = [(cx, cy-85*scale), (cx+78*scale, cy-58*scale), (cx+65*scale, cy+35*scale), (cx, cy+95*scale), (cx-65*scale, cy+35*scale), (cx-78*scale, cy-58*scale)]
    draw.polygon(pts, fill=color)
    draw.line((cx-34*scale, cy+2*scale, cx-5*scale, cy+32*scale, cx+42*scale, cy-30*scale), fill=WHITE, width=max(3, int(10*scale)), joint="curve")


def draw_security(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Privacidad desde el propio teléfono")
    shield(d, (240, 410), 1.15)
    card(d, (390, 295, 1190, 525), fill=WHITE)
    bullet_list(d, 425, 330, ["Sin cuentas en la nube", "Sin anuncios ni rastreo", "Datos protegidos en tu equipo"], width=720, size=28, gap=15)
    card(d, (98, 610, 1190, 790), fill=PALE_BLUE, outline=PALE_BLUE)
    d.text((130, 642), "ACCESO CON CLAVE OPCIONAL", font=font(24, True), fill=BLUE)
    d.text((130, 700), "Huella  ·  Rostro  ·  PIN o patrón", font=font(34, True), fill=INK)
    pill(d, (98, 825, 1190, 880), "EL CARRITO SE CONSERVA AL BLOQUEAR Y DESBLOQUEAR", fill=PALE_CYAN, color=TEAL, size=21)
    paste_phone(frame, ph["phone"])


def draw_backup_flow(frame, ph):
    d = ImageDraw.Draw(frame)
    mini_title(d, "Un respaldo para cambiar de teléfono")
    stages = [(98, "1", "EXPORTAR", "Archivo .shalev\ncon contraseña"), (490, "2", "GUARDAR / ENVIAR", "Descargas o una\napp segura"), (882, "3", "IMPORTAR", "Restaura en el\nnuevo equipo")]
    for x, num, title, detail in stages:
        card(d, (x, 285, x+330, 545), fill=WHITE)
        d.ellipse((x+22, 307, x+76, 361), fill=BLUE)
        d.text((x+49, 333), num, font=font(23, True), fill=WHITE, anchor="mm")
        d.text((x+28, 390), title, font=font(24, True), fill=BLUE)
        paragraph(d, (x+28, 440), detail, size=25, fill=INK, max_width=275, line_gap=8)
    arrow(d, (435, 415), (475, 415), color=ORANGE_DARK, width=5)
    arrow(d, (827, 415), (867, 415), color=ORANGE_DARK, width=5)
    card(d, (98, 615, 1190, 730), fill=PALE_ORANGE, outline=ORANGE)
    d.text((130, 640), "ATENCIÓN", font=font(22, True), fill=ORANGE_DARK)
    d.text((130, 683), "Importar reemplaza TODOS los datos actuales.", font=font(29, True), fill=INK)
    bullet_list(d, 104, 775, ["Las fotos no se incluyen en la copia", "Guarda la contraseña en un lugar seguro"], width=1110, size=25, gap=10, check=False)
    paste_phone(frame, ph["phone"])


def draw_benefits(frame, ph):
    d = ImageDraw.Draw(frame)
    benefits = [("01", "Menos errores", "Totales, vuelto y cobros claros"), ("02", "Inventario al día", "Recetas y existencias automáticas"), ("03", "Caja cuadrada", "Turnos con arqueo y conciliación"), ("04", "Cobros verificables", "Transferencias asociadas a cada venta"), ("05", "Mejores decisiones", "Ganancias y rankings con cifras reales")]
    for i, (num, title, detail) in enumerate(benefits):
        y = 205 + i*123
        d.ellipse((100, y+10, 164, y+74), fill=BLUE if i%2==0 else TEAL)
        d.text((132, y+41), "✓", font=font(30, True), fill=WHITE, anchor="mm")
        d.text((190, y+3), title, font=font(31, True), fill=INK)
        d.text((190, y+50), detail, font=font(24), fill=MUTED)
    pill(d, (98, 835, 1180, 890), "CLARO / OSCURO · LETRA 200 % · AYUDA · SOPORTE · LICENCIA · ACTUALIZAR", fill=NAVY, color=WHITE, size=20)
    for spec in ph["phones"]:
        paste_phone(frame, spec)


def draw_outro(frame, scene, ph):
    d = ImageDraw.Draw(frame, "RGBA")
    logo = Image.open(ROOT / "Android/play_store/icono_512.png").convert("RGB").resize((285, 285), Image.Resampling.LANCZOS)
    mask = Image.new("L", (285, 285), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 284, 284), radius=64, fill=255)
    d.rounded_rectangle((803, 95, 1145, 437), radius=76, fill=(255, 255, 255, 25))
    frame.paste(logo, (818, 110), mask)
    d.text((960, 490), "SHALEV", font=font(104, brand=True), fill=WHITE, anchor="mm")
    d.text((960, 610), "Vende mejor. Controla más.", font=font(54, True), fill=WHITE, anchor="mm")
    d.text((960, 685), "Haz crecer tu negocio.", font=font(38), fill=CYAN, anchor="mm")
    pill(d, (490, 770, 920, 838), "ANDROID 8.0 O SUPERIOR", fill="#203A5E", color=WHITE, size=24, outline="#4B668B")
    pill(d, (950, 770, 1430, 838), "7 DÍAS DE PRUEBA GRATUITA", fill=ORANGE_DARK, color=WHITE, size=24)
    d.text((960, 892), "TU NEGOCIO, BAJO CONTROL", font=font(22, True), fill="#B8C9E6", anchor="mm")


def build_phase_static(scene_idx: int, phase_idx: int):
    scene = SCENES[scene_idx]
    ph = scene["phases"][phase_idx]
    layout = ph["layout"]
    kind = "dark" if layout in ("intro", "outro") else "light"
    frame = background(kind).copy()
    if layout not in ("intro", "outro"):
        section_header(ImageDraw.Draw(frame), scene)
    if layout == "intro": draw_intro(frame, scene, ph)
    elif layout == "home_overview": draw_home_overview(frame, ph)
    elif layout == "shift": draw_shift(frame, ph, False)
    elif layout == "shift_open": draw_shift(frame, ph, True)
    elif layout == "cash_close": draw_cash_close(frame, ph)
    elif layout == "inventory": draw_inventory(frame, ph)
    elif layout == "recipe": draw_recipe(frame, ph)
    elif layout == "services": draw_services(frame, ph)
    elif layout == "pricing": draw_pricing(frame, ph)
    elif layout == "sale_cart": draw_sale_cart(frame, ph, False)
    elif layout == "sale_cart_selected": draw_sale_cart(frame, ph, True)
    elif layout == "sale_qr": draw_sale_qr(frame, ph)
    elif layout == "sale_data": draw_sale_data(frame, ph)
    elif layout == "records": draw_records(frame, ph)
    elif layout == "corrections": draw_corrections(frame, ph)
    elif layout == "exports": draw_exports(frame, ph)
    elif layout == "team": draw_team(frame, ph, False)
    elif layout == "team_sync": draw_team(frame, ph, True)
    elif layout == "security": draw_security(frame, ph)
    elif layout == "backup_flow": draw_backup_flow(frame, ph)
    elif layout == "benefits": draw_benefits(frame, ph)
    elif layout == "outro": draw_outro(frame, scene, ph)
    else: raise ValueError(layout)
    return frame


_PHASE_CACHE = {}


def cached_phase(scene_idx, phase_idx):
    key = (scene_idx, phase_idx)
    if key not in _PHASE_CACHE:
        _PHASE_CACHE[key] = build_phase_static(scene_idx, phase_idx)
    return _PHASE_CACHE[key]


def current_phase(scene, local_t):
    idx = 0
    for i, ph in enumerate(scene["phases"]):
        if local_t + 1e-6 >= ph["at"]:
            idx = i
    return idx


def draw_highlight_and_taps(frame, scene, scene_idx, local_t):
    pidx = current_phase(scene, local_t)
    ph = scene["phases"][pidx]
    if not ph.get("phone"):
        return
    x, y, w, h = phone_geom(ph["phone"])
    d = ImageDraw.Draw(frame, "RGBA")
    for hl in scene.get("highlights", []):
        if hl["phase"] != pidx or not (hl["start"] <= local_t <= hl["end"]):
            continue
        b = hl["box"]
        box = (x+b[0]*w, y+b[1]*h, x+b[2]*w, y+b[3]*h)
        pulse = 0.5 + 0.5*math.sin(local_t*math.pi*2.2)
        d.rounded_rectangle(box, radius=20, outline=(*hexrgb(ORANGE), int(170+70*pulse)), width=6)
        label = hl.get("label")
        if label:
            ly = max(80, box[1]-52)
            pill(d, (box[0], ly, min(box[0]+310, W-30), ly+42), label, fill=(180, 84, 26, 235), color=WHITE, size=18)
    for tap in scene.get("taps", []):
        if tap["phase"] != pidx:
            continue
        dt = local_t - tap["at"]
        if not (0 <= dt <= 1.25):
            continue
        cx, cy = x+tap["xy"][0]*w, y+tap["xy"][1]*h
        if "box" in tap:
            b = tap["box"]
            box = (x+b[0]*w, y+b[1]*h, x+b[2]*w, y+b[3]*h)
            d.rounded_rectangle(box, radius=18, outline=(*hexrgb(ORANGE), 230), width=6)
        p = ease(dt/1.05)
        radius = 24 + 62*p
        alpha = int(240*(1-p))
        d.ellipse((cx-radius, cy-radius, cx+radius, cy+radius), outline=(*hexrgb(ORANGE), alpha), width=8)
        d.ellipse((cx-19, cy-19, cx+19, cy+19), fill=(255, 255, 255, 225), outline=(*hexrgb(ORANGE_DARK), 255), width=5)
        # Etiqueta visible sin tapar el objetivo.
        lx = min(max(cx-58, 20), W-160)
        ly = cy-100 if cy > 160 else cy+55
        pill(d, (lx, ly, lx+116, ly+42), "TOCA", fill=(*hexrgb(ORANGE_DARK), 245), color=WHITE, size=19)


def draw_special_animation(frame, scene_idx, local_t):
    d = ImageDraw.Draw(frame, "RGBA")
    layout = SCENES[scene_idx]["phases"][current_phase(SCENES[scene_idx], local_t)]["layout"]
    if layout == "intro":
        # Anillos suaves alrededor del logo y actividad detrás del teléfono.
        for i in range(3):
            p = (local_t*0.32 + i/3) % 1
            r = 150 + 90*p
            d.ellipse((275-r, 315-r, 275+r, 315+r), outline=(*hexrgb(CYAN), int(50*(1-p))), width=4)
        for i in range(4):
            yy = 230 + ((local_t*34 + i*180) % 640)
            d.ellipse((1350, yy, 1362, yy+12), fill=(*hexrgb(ORANGE), 90))
    elif layout == "recipe":
        # Partículas que recorren las flechas de insumos hacia el elaborado.
        for i in range(3):
            y0 = 341 + i*132
            p = (local_t*0.48 + i*0.23) % 1
            cx = lerp(525, 655, p)
            cy = lerp(y0, 480, p)
            d.ellipse((cx-8, cy-8, cx+8, cy+8), fill=(*hexrgb(ORANGE), 210))
        p = (local_t*0.65) % 1
        d.arc((720, 350, 1050, 610), start=-90, end=-90+int(360*p), fill=(*hexrgb(ORANGE_DARK), 120), width=5)
    elif layout in ("team", "team_sync"):
        # Paquetes de sincronización entre principal y secundarias.
        for i, (start, end) in enumerate([((460, 430), (700, 345)), ((460, 465), (700, 545))]):
            p = (local_t*0.42+i*0.45) % 1
            cx, cy = lerp(start[0], end[0], p), lerp(start[1], end[1], p)
            d.ellipse((cx-10, cy-10, cx+10, cy+10), fill=(*hexrgb(CYAN), 230), outline=(*hexrgb(BLUE), 240), width=3)
    elif layout == "backup_flow":
        for i, x in enumerate([450, 842]):
            p = (local_t*0.7+i*0.4) % 1
            d.ellipse((x-8+35*p, 407, x+8+35*p, 423), fill=(*hexrgb(ORANGE), 220))
    elif layout == "benefits":
        # Pulso de confirmación que recorre las cinco ventajas.
        i = int(local_t/2.0) % 5
        y = 205 + i*123
        p = (local_t % 2.0)/2.0
        r = 36 + 18*p
        d.ellipse((132-r, y+42-r, 132+r, y+42+r), outline=(*hexrgb(CYAN), int(150*(1-p))), width=5)
    elif layout == "outro":
        for i in range(3):
            p = (local_t*0.28+i/3) % 1
            r = 200+150*p
            d.ellipse((960-r, 255-r, 960+r, 255+r), outline=(*hexrgb(CYAN), int(55*(1-p))), width=5)


def render_scene(scene_idx, local_t):
    scene = SCENES[scene_idx]
    pidx = current_phase(scene, local_t)
    frame = cached_phase(scene_idx, pidx).copy()
    # Fundido entre estados de una misma escena.
    if pidx > 0:
        dt = local_t - scene["phases"][pidx]["at"]
        if 0 <= dt < TRANSITION:
            prev = cached_phase(scene_idx, pidx-1)
            frame = Image.blend(prev, frame, ease(dt/TRANSITION))
    draw_special_animation(frame, scene_idx, local_t)
    draw_highlight_and_taps(frame, scene, scene_idx, local_t)
    return frame


def audio_duration(path: Path):
    with wave.open(str(path), "rb") as wf:
        return wf.getnframes()/wf.getframerate(), wf.getframerate(), wf.getnchannels(), wf.getsampwidth()


def prepare_timeline():
    start = 0.0
    for scene in SCENES:
        path = VOICE / scene["audio"]
        if not path.exists():
            raise FileNotFoundError(f"Falta la narración: {path}")
        duration, sr, channels, width = audio_duration(path)
        if (sr, channels, width) != (44100, 1, 2):
            raise ValueError(f"Formato inesperado en {path}: {sr} Hz, {channels} canales, {width} bytes")
        scene["audio_duration"] = duration
        scene["start"] = start
        scene["duration"] = duration + SCENE_PAD
        start += scene["duration"]
        for caption in scene.get("captions", []):
            pass
    return start


def caption_events(scene):
    caps = scene["captions"]
    weights = [max(12, len(c)) for c in caps]
    total = sum(weights)
    t = scene["start"] + VOICE_DELAY
    events = []
    for cap, weight in zip(caps, weights):
        dur = scene["audio_duration"] * weight/total
        events.append((t, t+dur, cap))
        t += dur
    return events


def srt_time(seconds):
    ms = int(round(seconds*1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_srt(total):
    lines = []
    n = 1
    for scene in SCENES:
        for start, end, cap in caption_events(scene):
            lines += [str(n), f"{srt_time(start)} --> {srt_time(end)}", cap, ""]
            n += 1
    (OUT / "Shalev_manual_subtitulos.srt").write_text("\n".join(lines), encoding="utf-8")


def caption_for_time(t):
    for scene in SCENES:
        if scene["start"] <= t < scene["start"]+scene["duration"]:
            for start, end, cap in caption_events(scene):
                if start <= t < end:
                    return cap, scene
            return "", scene
    return "", SCENES[-1]


@lru_cache(maxsize=128)
def caption_strip(text):
    im = Image.new("RGB", (W, H-CAPTION_Y), NAVY)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, W, 4), fill=CYAN)
    d.text((70, 27), "SHALEV", font=font(20, brand=True), fill=CYAN)
    if text:
        f = font(32, True)
        lines = wrap_lines(d, text, f, 1510)
        if len(lines) > 2:
            f = font(29, True)
            lines = wrap_lines(d, text, f, 1510)
        total_h = len(lines)*(f.size+7)
        y = (H-CAPTION_Y-total_h)/2 - 2
        for line in lines:
            d.text((1030, y), line, font=f, fill=WHITE, anchor="ma")
            y += f.size+7
    return im


def render_frame(t, total, with_caption=True):
    scene_idx = len(SCENES)-1
    local = SCENES[-1]["duration"]-0.001
    for i, scene in enumerate(SCENES):
        if scene["start"] <= t < scene["start"]+scene["duration"]:
            scene_idx, local = i, t-scene["start"]
            break
    frame = render_scene(scene_idx, local)
    if scene_idx > 0 and local < TRANSITION:
        prev_scene = SCENES[scene_idx-1]
        prev = render_scene(scene_idx-1, prev_scene["duration"]-0.001)
        frame = Image.blend(prev, frame, ease(local/TRANSITION))
    if scene_idx == 0 and t < 0.8:
        frame = Image.blend(background("dark"), frame, ease(t/0.8))
    if t > total-0.8:
        frame = Image.blend(frame, background("dark"), ease((t-(total-0.8))/0.8))
    # Progreso discreto del recorrido.
    d = ImageDraw.Draw(frame, "RGBA")
    d.rectangle((0, 0, int(W*t/total), 5), fill=(*hexrgb(ORANGE), 210))
    if with_caption:
        cap, _ = caption_for_time(t)
        frame.paste(caption_strip(cap), (0, CAPTION_Y))
    return frame


def generate_audio(total):
    """Mezcla narración con una base ambiental original y los toques de interfaz."""
    sr = 44100
    n = int(math.ceil(total*sr))
    mix = np.zeros((n, 2), dtype=np.float32)
    # Música ambiental original: acordes suaves, sin material protegido del repositorio.
    chords = [
        (261.63, 329.63, 392.00),  # C
        (220.00, 261.63, 329.63),  # Am
        (174.61, 220.00, 261.63),  # F
        (196.00, 246.94, 293.66),  # G
    ]
    for begin in range(0, n, sr):
        end = min(n, begin+sr)
        t = (np.arange(begin, end, dtype=np.float32)/sr).astype(np.float32)
        segment = np.zeros((len(t), 2), dtype=np.float32)
        # Un acorde cada cuatro segundos; envolvente nula en los bordes.
        for ci, chord in enumerate(chords):
            mask = ((t//4).astype(np.int32) % 4 == ci)
            if not np.any(mask):
                continue
            local = np.mod(t, 4.0)
            env = np.sin(np.pi*local/4.0).astype(np.float32)**0.65
            for j, freq in enumerate(chord):
                tone_l = np.sin(2*np.pi*freq*t + j*0.23).astype(np.float32)
                tone_r = np.sin(2*np.pi*(freq*1.0007)*t + j*0.31).astype(np.float32)
                segment[:, 0] += mask*env*tone_l*0.0060
                segment[:, 1] += mask*env*tone_r*0.0060
            # Nota alta y breve cada dos segundos.
            beat = np.mod(t, 2.0)
            pluck_env = np.exp(-4.8*beat).astype(np.float32)
            f = chord[2]*2
            segment[:, 0] += mask*pluck_env*np.sin(2*np.pi*f*t).astype(np.float32)*0.0040
            segment[:, 1] += mask*pluck_env*np.sin(2*np.pi*f*t+0.18).astype(np.float32)*0.0040
        mix[begin:end] += segment
    # Narración en el centro.
    for scene in SCENES:
        with wave.open(str(VOICE/scene["audio"]), "rb") as wf:
            samples = np.frombuffer(wf.readframes(wf.getnframes()), dtype="<i2").astype(np.float32)/32768.0
        offset = int(round((scene["start"]+VOICE_DELAY)*sr))
        length = min(len(samples), n-offset)
        mix[offset:offset+length, 0] += samples[:length]*0.92
        mix[offset:offset+length, 1] += samples[:length]*0.92
    # Toques cortos, sincronizados con las pulsaciones visibles.
    for scene in SCENES:
        for k, tap in enumerate(scene.get("taps", [])):
            start = scene["start"]+tap["at"]
            offset = int(start*sr)
            length = min(int(0.12*sr), n-offset)
            if length <= 0:
                continue
            tt = np.arange(length, dtype=np.float32)/sr
            env = np.exp(-42*tt)
            click = (np.sin(2*np.pi*(1150-250*tt)*tt)*0.65 + np.sin(2*np.pi*1900*tt)*0.20)*env*0.055
            mix[offset:offset+length, 0] += click*(0.85 if k%2 else 1.0)
            mix[offset:offset+length, 1] += click*(1.0 if k%2 else 0.85)
    # Fundidos global y salida limpia.
    fade_in = min(int(0.7*sr), n)
    mix[:fade_in] *= np.linspace(0, 1, fade_in, dtype=np.float32)[:, None]
    fade_out = min(int(1.0*sr), n)
    mix[-fade_out:] *= np.linspace(1, 0, fade_out, dtype=np.float32)[:, None]
    peak = float(np.max(np.abs(mix)))
    if peak > 0.97:
        mix *= 0.97/peak
    path = WORK / "mezcla_final.wav"
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes((np.clip(mix, -1, 1)*32767).astype("<i2").tobytes())
    rms = float(np.sqrt(np.mean(mix**2)))
    print(f"Audio: {path} | pico={min(peak,0.97):.3f} RMS={rms:.3f}")
    return path


def write_guide(total):
    lines = [
        "# Shalev · Video manual en 3 minutos",
        "",
        f"**Duración:** {int(total//60)}:{total%60:04.1f} min  ",
        "**Formato:** 1920 × 1080 · 24 fps · H.264 + AAC  ",
        "**Idioma:** español · narración masculina · subtítulos integrados y archivo SRT",
        "",
        "Video basado en `MANUAL_USUARIO.md` y en las capturas reales del manual. Los pulsos naranja con la etiqueta **TOCA** señalan las selecciones del usuario. La música ambiental y los efectos de toque se sintetizan específicamente para este video.",
        "",
        "## Guion y storyboard",
        "",
    ]
    for scene in SCENES:
        lines += [
            f'### {scene["n"]:02d}. {scene["title"]} — {scene["duration"]:.2f} s',
            "",
            f'**Narración:** {scene["narration"]}',
            "",
            "**Pantalla:** " + "; ".join(ph["layout"].replace("_", " ") for ph in scene["phases"]) + ".",
            "",
        ]
    lines += [
        "## Archivos",
        "",
        "- `Shalev_video_manual_1080p.mp4`: video final con voz, música, animaciones y subtítulos integrados.",
        "- `Shalev_manual_subtitulos.srt`: subtítulos independientes para accesibilidad o reedición.",
        "- `Shalev_guion_storyboard.md`: este guion con tiempos y contenido de las escenas.",
        "",
        "La captura de Datos de la transferencia muestra deliberadamente una diferencia de importe: se utiliza para explicar la validación del SMS; no se representa como un cobro confirmado.",
    ]
    (OUT / "Shalev_guion_storyboard.md").write_text("\n".join(lines), encoding="utf-8")


def make_preview(total):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    thumbs = []
    for i, scene in enumerate(SCENES):
        for j, ph in enumerate(scene["phases"]):
            next_at = scene["phases"][j+1]["at"] if j+1 < len(scene["phases"]) else scene["duration"]
            local = ph["at"] + min(1.0, max(0.2, (next_at-ph["at"])*0.25))
            t = scene["start"]+local
            im = render_frame(t, total)
            path = PREVIEW / f"escena_{i+1:02d}_estado_{j+1:02d}.png"
            im.save(path, optimize=True)
            thumb = im.resize((480, 270), Image.Resampling.LANCZOS)
            td = ImageDraw.Draw(thumb)
            td.rounded_rectangle((12, 12, 250, 48), radius=12, fill=(8, 21, 38, 220))
            td.text((24, 19), f"{i+1:02d}.{j+1} · {ph['layout']}", font=font(16, True), fill=WHITE)
            thumbs.append(thumb)
            print("Vista:", path.relative_to(ROOT))
    cols = 4
    rows = math.ceil(len(thumbs)/cols)
    sheet = Image.new("RGB", (cols*480, rows*270), NAVY)
    for i, thumb in enumerate(thumbs):
        sheet.paste(thumb, ((i%cols)*480, (i//cols)*270))
    path = PREVIEW / "storyboard_contacto.png"
    sheet.save(path, optimize=True)
    print("Storyboard:", path.relative_to(ROOT))


def render_video(total, audio):
    output = OUT / "Shalev_video_manual_1080p.mp4"
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    frames = int(math.ceil(total*FPS))
    cmd = [
        ffmpeg, "-y", "-loglevel", "warning",
        "-f", "rawvideo", "-vcodec", "rawvideo", "-pix_fmt", "rgb24",
        "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-i", str(audio),
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "18",
        "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.1",
        "-threads", str(min(8, os.cpu_count() or 4)),
        "-c:a", "aac", "-b:a", "192k", "-ar", "44100",
        "-movflags", "+faststart", "-t", f"{total:.3f}", str(output),
    ]
    print("Render:", output.relative_to(ROOT), f"| {frames} frames | {total:.2f} s")
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    try:
        for i in range(frames):
            t = min(i/FPS, total-0.001)
            frame = render_frame(t, total)
            proc.stdin.write(frame.tobytes())
            if i % (FPS*10) == 0:
                print(f"  {i}/{frames} frames · {t:.0f}/{total:.0f} s", flush=True)
        proc.stdin.close()
        code = proc.wait()
        if code:
            raise RuntimeError(f"ffmpeg terminó con código {code}")
    except Exception:
        if proc.stdin and not proc.stdin.closed:
            proc.stdin.close()
        proc.kill()
        raise
    print("Video listo:", output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", action="store_true", help="Genera fotogramas de control y hoja de contacto")
    parser.add_argument("--render", action="store_true", help="Renderiza el MP4 completo")
    args = parser.parse_args()
    if not args.preview and not args.render:
        args.preview = True
    OUT.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    total = prepare_timeline()
    if total > 180:
        raise RuntimeError(f"El video excede 3 minutos: {total:.2f} s")
    print(f"Duración total: {total:.3f} s ({int(total//60)}:{total%60:04.1f})")
    write_srt(total)
    write_guide(total)
    if args.preview:
        make_preview(total)
    if args.render:
        audio = generate_audio(total)
        render_video(total, audio)


if __name__ == "__main__":
    main()
