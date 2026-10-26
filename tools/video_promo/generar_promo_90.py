#!/usr/bin/env python3
"""Video promocional de Shalev de ~90 segundos, Full HD y 60 fps.

Uso:
  .venv/bin/python tools/video_promo/generar_promo_90.py --preview
  .venv/bin/python tools/video_promo/generar_promo_90.py --render
  .venv/bin/python tools/video_promo/generar_promo_90.py --render --desde 12 --hasta 20

Entradas
  · Narración: .cache/shalev_promo90/voz/01.mp3 … 10.mp3 (voz del asistente de Arena).
  · Capturas reales: app/capturas/claro y promocion/capturas.
Salidas (promocion/video_promo_90/)
  · Shalev_promo_90s_1080p60.mp4
  · Shalev_promo_90s.srt
  · Shalev_promo_90s_guion.md

El video se dibuja fotograma a fotograma con Pillow (sin plantillas ni material gráfico
ajeno), se codifica con el ffmpeg de imageio-ffmpeg y la música de fondo se sintetiza con
numpy: acordes suaves, arpegio y percusión mínima, atenuados automáticamente bajo la voz.
"""
from __future__ import annotations

import argparse
import math
import os
from pathlib import Path
import subprocess
import sys
import wave
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / ".cache" / "shalev_promo90"
VOZ = WORK / "voz"
PREVIEW = WORK / "preview"
OUT = ROOT / "promocion" / "video_promo_90"
FONTS = ROOT / ".cache" / "fonts"

W, H = 1920, 1080
FPS = 60
SLOT = (1330, 25, 1870, 975)          # ventana del teléfono
PANEL_W = 1300                        # ancho de la capa animada de la izquierda
CAP_Y = 984                           # franja de subtítulos
LEAD = 0.28                           # silencio antes de la narración de cada escena
TAIL = 0.32                           # respiro después de la narración
XFADE = 0.30                          # fundido entre capturas de una escena
CACHE_SCALE = 1.9                     # resolución interna de las capturas del teléfono

# Paleta oficial de Shalev (DESIGN_SYSTEM.md).
NAVY = "#10254B"
DARK = "#0F1318"
BLUE = "#2B4FA3"
CYAN = "#66D1EF"
ORANGE = "#EE7F3B"
ORANGE_DARK = "#B4541A"
TEAL = "#2F6F80"
INK = "#1A1C1E"
MUTED = "#5F6368"
PALE_BLUE = "#E6EDFA"
PALE_CYAN = "#E2F2F6"
PALE_ORANGE = "#FCE9DE"
WHITE = "#FFFFFF"
GREEN = "#24734A"

A = "app/capturas/claro"
P = "promocion/capturas"

TELEGRAM_URL = "https://t.me/spviapp/1"
TELEGRAM_HANDLE = "t.me/spviapp"


# ---------------------------------------------------------------- utilidades

def hx(value: str):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def rgba(value: str, alpha: float):
    r, g, b = hx(value)
    return (r, g, b, int(max(0, min(1, alpha)) * 255))


def ease(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def lerp(a, b, t):
    return a + (b - a) * t


def anim(t: float, t0: float, dur: float = 0.45) -> float:
    """Progreso suavizado de una entrada escalonada."""
    if t <= t0:
        return 0.0
    return ease(min(1.0, (t - t0) / dur))


def out_cubic(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return 1 - (1 - x) ** 3


_FONT_CANDIDATES = {
    "regular": ["Roboto-Regular.ttf", "DejaVuSans.ttf"],
    "medium": ["Roboto-Medium.ttf", "DejaVuSans.ttf"],
    "bold": ["Roboto-Bold.ttf", "DejaVuSans-Bold.ttf"],
    "black": ["Roboto-Black.ttf", "DejaVuSans-Bold.ttf"],
}


@lru_cache(maxsize=256)
def font(size: int, weight: str = "regular", brand: bool = False):
    if brand:
        return ImageFont.truetype(str(ROOT / "designsystem/src/main/res/font/orbitron.ttf"), size=max(8, size))
    for name in _FONT_CANDIDATES[weight]:
        for base in (FONTS, Path("/usr/share/fonts/truetype/dejavu")):
            path = base / name
            if path.exists():
                return ImageFont.truetype(str(path), size=max(8, size))
    raise FileNotFoundError("No se encontró ninguna tipografía utilizable")


def text_w(d: ImageDraw.ImageDraw, txt: str, f) -> float:
    return d.textlength(txt, font=f)


def tracked(d: ImageDraw.ImageDraw, xy, txt: str, f, fill, spacing: float = 2.0, anchor_v: str = "la"):
    """Texto con espaciado entre letras (rótulos y marcas)."""
    x, y = xy
    if anchor_v.endswith("m"):
        total = sum(text_w(d, c, f) for c in txt) + spacing * max(0, len(txt) - 1)
        x -= total / 2
    for ch in txt:
        d.text((x, y), ch, font=f, fill=fill)
        x += text_w(d, ch, f) + spacing
    return x


def wrap(d: ImageDraw.ImageDraw, txt: str, f, max_w: float):
    words, lines, line = txt.split(), [], ""
    for word in words:
        trial = f"{line} {word}".strip()
        if text_w(d, trial, f) <= max_w or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def paste_clip(dst: Image.Image, src: Image.Image, xy):
    """Pega recortando lo que sobresale del destino."""
    x, y = int(round(xy[0])), int(round(xy[1]))
    sx0, sy0 = max(0, -x), max(0, -y)
    dx0, dy0 = max(0, x), max(0, y)
    w = min(src.width - sx0, dst.width - dx0)
    h = min(src.height - sy0, dst.height - dy0)
    if w <= 0 or h <= 0:
        return
    part = src.crop((sx0, sy0, sx0 + w, sy0 + h))
    dst.paste(part, (dx0, dy0), part)


# ---------------------------------------------------------------- fondos

@lru_cache(maxsize=4)
def background(kind: str = "light") -> Image.Image:
    if kind == "dark":
        c1, c2 = np.array(hx("#07111F"), dtype=np.float32), np.array(hx("#152B4C"), dtype=np.float32)
    else:
        c1, c2 = np.array(hx("#F7FAFE"), dtype=np.float32), np.array(hx("#E4EDF9"), dtype=np.float32)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    mix = np.clip(0.55 * xx / W + 0.45 * yy / H, 0, 1)[..., None]
    arr = (c1 * (1 - mix) + c2 * mix).astype(np.uint8)
    im = Image.fromarray(arr, "RGB")
    d = ImageDraw.Draw(im, "RGBA")
    if kind == "dark":
        d.ellipse((1150, -420, 2150, 520), fill=rgba(BLUE, 0.30))
        d.ellipse((-380, 620, 420, 1420), fill=rgba(TEAL, 0.16))
        for x in range(60, W, 96):
            d.line((x, 0, x, H), fill=(255, 255, 255, 4), width=1)
        for y in range(60, H, 96):
            d.line((0, y, W, y), fill=(255, 255, 255, 4), width=1)
    else:
        d.ellipse((1420, -380, 2160, 360), fill=rgba(CYAN, 0.10))
        d.ellipse((-420, 700, 420, 1500), fill=rgba(ORANGE, 0.06))
    return im


@lru_cache(maxsize=8)
def glow(color: str, size: int = 620) -> Image.Image:
    """Mancha radial suave para dar movimiento al fondo."""
    im = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(im)
    steps = 26
    for i in range(steps, 0, -1):
        r = size / 2 * i / steps
        d.ellipse((size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r), fill=int(255 * (1 - i / steps) ** 1.7))
    im = im.filter(ImageFilter.GaussianBlur(size / 26))
    r, g, b = hx(color)
    layer = Image.new("RGBA", (size, size), (r, g, b, 0))
    layer.putalpha(im.point(lambda v: int(v * 0.55)))
    return layer


# ---------------------------------------------------------------- teléfono

def fit_box(src_w: float, src_h: float, max_w: float, max_h: float):
    scale = min(max_w / src_w, max_h / src_h)
    return src_w * scale, src_h * scale


def make_card(img: str, crop, base_w: float, base_h: float) -> Image.Image:
    src = Image.open(ROOT / img).convert("RGB")
    if crop:
        src = src.crop(tuple(crop))
    tw, th = int(round(base_w * CACHE_SCALE)), int(round(base_h * CACHE_SCALE))
    src = src.resize((tw, th), Image.Resampling.LANCZOS)
    if tw > src.width * 0.9:
        src = src.filter(ImageFilter.UnsharpMask(radius=2.2, percent=55, threshold=2))
    radius = int(round(13 * CACHE_SCALE))
    mask = Image.new("L", (tw, th), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, tw - 1, th - 1), radius=radius, fill=255)
    card = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    card.paste(src, (0, 0), mask)
    d = ImageDraw.Draw(card)
    d.rounded_rectangle((1, 1, tw - 2, th - 2), radius=radius, outline=rgba("#0B1A31", 0.95),
                        width=max(2, int(round(3 * CACHE_SCALE))))
    return card


_SLOT_W = SLOT[2] - SLOT[0]
_SLOT_H = SLOT[3] - SLOT[1]


@lru_cache(maxsize=6)
def card_for(img: str, crop, base_w_r: int, base_h_r: int) -> Image.Image:
    return make_card(img, crop, base_w_r, base_h_r)


def geometry(shot):
    src = Image.open(ROOT / shot["img"])
    if shot.get("crop"):
        c = shot["crop"]
        sw, sh = c[2] - c[0], c[3] - c[1]
    else:
        sw, sh = src.size
    base_w, base_h = fit_box(sw, sh, _SLOT_W - 8, _SLOT_H - 6)
    return round(base_w), round(base_h)


def to_screen(px: float, py: float, cam, base_w: float, base_h: float):
    z, fx, fy = cam
    fxs = SLOT[0] + _SLOT_W / 2 + (fx - 0.5) * base_w
    fys = SLOT[1] + _SLOT_H / 2 + (fy - 0.5) * base_h
    return (fxs + (px - fx) * base_w * z, fys + (py - fy) * base_h * z)


def phone_surface(shot, cam) -> Image.Image:
    z, fx, fy = cam
    bw, bh = geometry(shot)
    card = card_for(shot["img"], shot.get("crop"), bw, bh)
    tw, th = max(2, int(round(bw * z))), max(2, int(round(bh * z)))
    img = card.resize((tw, th), Image.Resampling.LANCZOS) if (tw, th) != card.size else card
    fxs = SLOT[0] + _SLOT_W / 2 + (fx - 0.5) * bw
    fys = SLOT[1] + _SLOT_H / 2 + (fy - 0.5) * bh
    left = fxs - fx * tw
    top = fys - fy * th
    surf = Image.new("RGBA", (_SLOT_W, _SLOT_H), (0, 0, 0, 0))
    paste_clip(surf, img, (left - SLOT[0], top - SLOT[1]))
    return surf


def slot_point(shot, cam, px: float, py: float):
    bw, bh = geometry(shot)
    return to_screen(px, py, cam, bw, bh)


# ---------------------------------------------------------------- escenas

def scene(n, slug, kind, kicker, title, audio, captions, shots, *, narration="", bullets=(), chips=(), steps=None,
          plans=False, features=None):
    return dict(n=n, slug=slug, kind=kind, kicker=kicker, title=title, audio=audio, captions=captions,
                shots=shots, narration=narration, bullets=list(bullets), chips=list(chips), steps=steps,
                plans=plans, features=features)


SCENES = [
    scene(
        1, "intro", "intro", "PUNTO DE VENTA · INVENTARIO · SERVICIOS", "",
        "01.mp3",
        [(0.00, 4.40, "Shalev: punto de venta, inventario y servicios"),
         (4.90, 6.90, "Cien por ciento sin internet")],
        [dict(t=0.0, img=f"{P}/shot_inicio_claro.png", zoom=1.04, focus=(0.5, 0.30)),
         dict(t=4.55, img=f"{P}/shot_inicio_claro.png", box=(0.13, 0.245, 0.87, 0.365))],
        narration="Shalev: punto de venta, inventario y servicios para tu negocio. Cien por ciento sin internet.",
        bullets=[("Todo en el teléfono", "Ventas, caja, inventario y servicios"),
                 ("Sin internet, sin cuotas ocultas", "Tus datos nunca salen del equipo"),
                 ("Listo en minutos", "7 días de prueba con todo incluido")],
        chips=["ANDROID 8+", "SIN ANUNCIOS", "SIN REGISTRO EN LA NUBE"],
    ),
    scene(
        2, "inicio", "panel", "01 · INICIO", "Todo el negocio, en una pantalla",
        "02.mp3",
        [(0.00, 2.82, "En Inicio, alertas de stock y caducidad"),
         (3.24, 4.37, "Las ventas del turno y la ganancia neta"),
         (4.81, 6.95, "Y tus rankings")],
        [dict(t=0.0, img=f"{P}/shot_inicio_claro.png", zoom=1.05, focus=(0.5, 0.30)),
         dict(t=2.60, img=f"{P}/shot_inicio_claro.png", box=(0.16, 0.255, 0.84, 0.375),
              tap=(0.5, 0.315)),
         dict(t=4.60, img=f"{P}/shot_inicio_claro.png", box=(0.12, 0.575, 0.88, 0.80))],
        narration="En Inicio ves alertas de stock y caducidad, las ventas del turno, la ganancia neta y tus rankings.",
        bullets=[("Alertas de inventario", "Stock crítico, bajo y próximos a caducar"),
                 ("Cifras del turno", "Ventas, ganancia neta y métodos de pago"),
                 ("Rankings", "Productos, clientes y vendedores")],
        chips=["TURNO Y CAJA", "GRÁFICOS", "PERÍODOS: DÍA · MES · AÑO"],
    ),
    scene(
        3, "venta", "panel", "02 · VENDER Y COBRAR", "De carrito a cobro verificado",
        "03.mp3",
        [(0.00, 1.16, "Vende en segundos: arma el carrito"),
         (1.61, 3.01, "Cobra en efectivo o por transferencia"),
         (3.01, 7.01, "con el QR de Transfermóvil"),
         (7.44, 10.19, "y verifica el pago con el número de transacción")],
        [dict(t=0.0, img=f"{P}/shot_carrito.png", zoom=1.08, focus=(0.5, 0.32)),
         dict(t=3.10, img=f"{A}/05a2_venta_carrito_transferencia.png",
              box=(0.33, 0.055, 0.98, 0.42), tap=(0.80, 0.375)),
         dict(t=6.90, img=f"{A}/05f_venta_qr.png", box=(0.14, 0.175, 0.86, 0.445),
              tap=(0.5, 0.30)),
         dict(t=8.60, img=f"{A}/05h_venta_datos_cliente.png", box=(0.13, 0.505, 0.87, 0.605),
              tap=(0.5, 0.555))],
        narration="Vende en segundos: arma el carrito, cobra en efectivo o por transferencia con el QR de "
                  "Transfermóvil y verifica el pago con el número de transacción.",
        bullets=[("Carrito en segundos", "Cantidades y total al instante"),
                 ("Efectivo o Transfermóvil", "El cliente escanea el QR y escribe el importe"),
                 ("Cobro verificado", "Número de transacción y aviso si el importe no coincide")],
        chips=["TOTAL AUTOMÁTICO", "SMS Y NOTIFICACIONES", "TRAZABILIDAD"],
    ),
    scene(
        4, "inventario", "panel", "03 · INVENTARIO INTELIGENTE", "Existencias, insumos y recetas",
        "04.mp3",
        [(0.00, 3.35, "Existencias, insumos y recetas"),
         (3.80, 5.29, "Calcula el costo y para cuántas unidades alcanza"),
         (5.69, 8.42, "Descuenta cada ingrediente al vender")],
        [dict(t=0.0, img=f"{P}/shot_inventario_claro.png", zoom=1.06, focus=(0.5, 0.35)),
         dict(t=3.60, img=f"{A}/02k2_inventario_ficha_elaborado.png", box=(0.12, 0.545, 0.88, 0.735)),
         dict(t=5.60, img=f"{A}/08m_producto_elaborado_receta.png", crop=(0, 880, 720, 1880),
              box=(0.12, 0.155, 0.88, 0.455)),
         dict(t=8.20, img=f"{P}/shot_inventario_claro.png", box=(0.12, 0.315, 0.88, 0.485))],
        narration="Controla existencias, insumos y recetas: Shalev calcula el costo, te dice para cuántas "
                  "unidades alcanza y descuenta cada ingrediente al vender.",
        bullets=[("Foto, costo, precio y caducidad", "Cada artículo con su estado de stock"),
                 ("Recetas con costo automático", "«Alcanza para N» según el insumo más escaso"),
                 ("Descuento exacto al vender", "Nunca existencias negativas")],
        chips=["INSUMOS EN KG Y LITROS", "ALERTAS DE FALTANTES", "FILTROS Y BÚSQUEDA"],
    ),
    scene(
        5, "servicios", "panel", "04 · SERVICIOS Y PRECIOS", "Cobra tu trabajo sin cuentas a mano",
        "05.mp3",
        [(0.00, 3.05, "Cobra tus servicios"),
         (3.10, 6.68, "Recargos y descuentos automáticos por método de pago")],
        [dict(t=0.0, img=f"{P}/shot_servicios_claro.png", zoom=1.08, focus=(0.5, 0.32)),
         dict(t=3.05, img=f"{A}/06i_precios_lista.png", box=(0.13, 0.565, 0.87, 0.705),
              tap=(0.72, 0.605))],
        narration="Cobra tus servicios y deja que Shalev aplique recargos o descuentos automáticos según el "
                  "método de pago.",
        bullets=[("Servicios con importe e insumos", "Conoces la ganancia real de cada trabajo"),
                 ("Reglas de precio", "Recargo por transferencia, descuento por mayor"),
                 ("Nunca por debajo del costo", "Activa o pausa cada regla")],
        chips=["BARBERÍA", "TALLERES", "CAFETERÍAS"],
    ),
    scene(
        6, "registros", "panel", "05 · REGISTROS Y EXPORTACIÓN", "Revisa, corrige y comparte",
        "06.mp3",
        [(0.00, 2.60, "Revisa y corrige tus ventas"),
         (2.65, 6.55, "Exporta en PDF o Excel, listo para compartir")],
        [dict(t=0.0, img=f"{P}/shot_registros.png", zoom=1.06, focus=(0.5, 0.35)),
         dict(t=2.55, img=f"{A}/04o_registros_ficha_venta.png", box=(0.13, 0.31, 0.87, 0.62)),
         dict(t=4.30, img=f"{A}/04m_registros_hoja_exportar.png", box=(0.16, 0.33, 0.84, 0.56),
              tap=(0.36, 0.465))],
        narration="Revisa, corrige y exporta ventas, turnos y ganancias en PDF o Excel, listos para compartir.",
        bullets=[("Seis vistas", "Ventas, servicios, transferencias, movimientos, clientes y turnos"),
                 ("Corregir o anular", "Con motivo y relación a la operación original"),
                 ("PDF y Excel", "Exporta la selección o lo que ves en pantalla")],
        chips=["TURNOS SELLADOS", "FILTROS POR FECHA", "LISTO PARA COMPARTIR"],
    ),
    scene(
        7, "equipo", "panel", "06 · TRABAJO EN EQUIPO", "Hasta 10 teléfonos en red local",
        "07.mp3",
        [(0.00, 1.27, "Trabaja en equipo"),
         (1.77, 6.78, "Hasta 10 empleados por la Wi-Fi del local, sin gastar datos")],
        [dict(t=0.0, img=f"{P}/shot_vinculadas.png", zoom=1.06, focus=(0.5, 0.35)),
         dict(t=1.75, img=f"{A}/07p_vinculacion_principal_turnos.png", box=(0.14, 0.215, 0.86, 0.40),
              tap=(0.5, 0.30))],
        narration="Trabaja en equipo: conecta hasta diez teléfonos de empleados por la Wi-Fi del local, sin "
                  "gastar datos.",
        bullets=[("Vinculación por QR", "Misma Wi-Fi, cero datos móviles"),
                 ("Permisos por empleado", "Vender, inventario, precios y tarjeta de cobro"),
                 ("Sigue vendiendo sin Wi-Fi", "Sincroniza y envía el cierre al encargado")],
        chips=["1 PRINCIPAL + 10 SECUNDARIAS", "SIN INTERNET MÓVIL", "CIERRES APROBADOS"],
    ),
    scene(
        8, "privacidad", "panel", "07 · PRIVACIDAD Y RESPALDO", "Tus datos, solo en tu teléfono",
        "08.mp3",
        [(0.00, 1.84, "Tus datos viven en tu teléfono"),
         (2.13, 3.68, "Protegidos con huella o PIN"),
         (3.96, 5.92, "Respaldos con contraseña")],
        [dict(t=0.0, img=f"{A}/07a2_ajustes_configuracion_completa.png",
              box=(0.15, 0.635, 0.85, 0.765)),
         dict(t=2.05, img=f"{A}/09a_respaldo_paso1.png", box=(0.13, 0.26, 0.87, 0.545)),
         dict(t=3.90, img=f"{A}/09c_respaldo_paso2.png", box=(0.14, 0.285, 0.86, 0.50),
              tap=(0.5, 0.40))],
        narration="Tus datos viven en tu teléfono, protegidos con huella o PIN y respaldos con contraseña.",
        bullets=[("Sin nube, sin anuncios", "Nada de rastreo ni cuentas externas"),
                 ("Acceso con huella o PIN", "El carrito se conserva al desbloquear"),
                 ("Respaldo .shalev cifrado", "Cambia de teléfono sin perder nada")],
        chips=["DATOS EN EL EQUIPO", "AES-256", "CAMBIO DE TELÉFONO"],
    ),
    scene(
        9, "licencia", "steps", "08 · ACTIVAR LA LICENCIA", "Tres pasos y estás trabajando",
        "09.mp3",
        [(0.00, 2.88, "Activa tu licencia en tres pasos"),
         (3.33, 4.54, "1. Revisa tus datos"),
         (5.00, 5.95, "2. Elige el plan y envía la solicitud"),
         (6.35, 8.19, "3. Pega el código y confirma"),
         (8.19, 12.80, "Cuando llegue el código, lo pegas y confirmas")],
        [dict(t=0.0, img=f"{A}/07i_licencia_prueba_paso1.png", box=(0.14, 0.23, 0.86, 0.53)),
         dict(t=3.30, img=f"{A}/07k_licencia_paso2_solicitar.png", crop=(0, 620, 720, 2220),
              box=(0.13, 0.28, 0.87, 0.60), tap=(0.5, 0.36)),
         dict(t=4.95, img=f"{A}/07l_licencia_paso3_activar.png", box=(0.13, 0.40, 0.87, 0.66),
              tap=(0.5, 0.60)),
         dict(t=8.15, img=f"{A}/07n_licencia_activa.png", box=(0.14, 0.06, 0.86, 0.19)),
         dict(t=10.30, img=f"{A}/07n_licencia_activa.png", box=(0.13, 0.30, 0.87, 0.52))],
        narration="Activar la licencia toma tres pasos: revisa tus datos, elige el plan y envía la solicitud. "
                  "Cuando llegue el código, lo pegas, confirmas, y listo.",
        steps=[("Revisa tus datos", "Nombre, carné y teléfono de tu perfil"),
               ("Elige el plan y envía", "Solicitud por WhatsApp con un toque"),
               ("Pega el código y confirma", "La licencia queda activa al instante")],
        chips=["7 DÍAS DE PRUEBA", "RENOVAR SUMA DÍAS", "SIN PERDER DATOS"],
    ),
    scene(
        10, "planes", "plans", "09 · PRECIOS", "Planes claros, sin sorpresas",
        "10.mp3",
        [(0.00, 4.95, "Mensual, semestral, anual y perpetua"),
         (5.23, 8.61, "Desde 6 000 CUP, con 7 días de prueba gratis")],
        [],
        narration="Planes mensual, semestral, anual y perpetua, desde seis mil CUP, con siete días de prueba "
                  "gratis y todas las funciones.",
        plans=True,
    ),
    scene(
        11, "cierre", "outro", "", "", None,
        [(0.00, 2.60, "Únete al grupo de Telegram: t.me/spviapp"),
         (2.60, 4.40, "Shalev: vende mejor, controla más")],
        [],
        narration="",
    ),
]

FEATURE_SCENES = [s for s in SCENES if s["kind"] in ("panel", "steps")]


# ---------------------------------------------------------------- línea de tiempo

def decode_voices() -> None:
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    for scene_ in SCENES:
        if not scene_["audio"]:
            continue
        mp3 = VOZ / scene_["audio"]
        wav = mp3.with_suffix(".wav")
        if not wav.exists() or wav.stat().st_mtime < mp3.stat().st_mtime:
            subprocess.run([ff, "-y", "-loglevel", "error", "-i", str(mp3), "-ac", "1", "-ar", "44100", str(wav)],
                           check=True)
    # Respaldos de la voz original, por si hace falta volver a mezclar sin conexión.
    for mp3 in sorted(VOZ.glob("*.mp3")):
        pass


def wav_duration(path: Path) -> float:
    with wave.open(str(path), "rb") as wf:
        return wf.getnframes() / wf.getframerate()


def build_timeline() -> float:
    t = 0.0
    for scene_ in SCENES:
        if scene_["audio"]:
            dur = wav_duration(VOZ / Path(scene_["audio"]).with_suffix(".wav"))
        else:
            dur = 4.40
        scene_["narr_dur"] = dur
        scene_["narration_start"] = t + LEAD
        scene_["start"] = t
        scene_["duration"] = LEAD + dur + TAIL
        scene_["end"] = scene_["start"] + scene_["duration"]
        t = scene_["end"]
    return t


TOTAL = 0.0


def locate(t: float):
    for i, scene_ in enumerate(SCENES):
        if scene_["start"] <= t < scene_["end"]:
            return i, t - scene_["start"]
    return len(SCENES) - 1, SCENES[-1]["duration"] - 0.001


def shot_camera(shot):
    """Zoom y foco: si la captura define `box`, se encuadra esa zona con margen."""
    zb = shot.get("box")
    if zb:
        bw, bh = zb[2] - zb[0], zb[3] - zb[1]
        z = min(1.0 / (bw + 0.10), 1.0 / (bh + 0.075)) * shot.get("fill", 1.0)
        z = max(1.0, min(1.9, z))
        return (z, (zb[0] + zb[2]) / 2, (zb[1] + zb[3]) / 2)
    return (shot["zoom"], shot["focus"][0], shot["focus"][1])


def camera_at(scene_, t: float):
    """Interpola zoom y foco entre capturas; añade un leve acercamiento continuo."""
    shots = scene_["shots"]
    if not shots:
        return (1.0, 0.5, 0.5)
    times = [s["t"] for s in shots]
    idx = 0
    for i, ts in enumerate(times):
        if t + 1e-6 >= ts:
            idx = i
    cur = shots[idx]
    z0, fx0, fy0 = shot_camera(cur)
    if idx + 1 < len(shots):
        z1, fx1, fy1 = shot_camera(shots[idx + 1])
        span = max(0.4, times[idx + 1] - times[idx])
        k = ease(min(1.0, max(0.0, (t - times[idx]) / span)))
        return (lerp(z0, z1, k), lerp(fx0, fx1, k), lerp(fy0, fy1, k))
    end = max(times[idx] + 0.6, scene_["narr_dur"] - times[idx] + 0.4)
    k = ease(min(1.0, max(0.0, (t - times[idx]) / end)))
    return (z0 * (1 + 0.045 * k), fx0, fy0)


def shot_index_at(scene_, t: float) -> int:
    idx = 0
    for i, s in enumerate(scene_["shots"]):
        if t + 1e-6 >= s["t"]:
            idx = i
    return idx


# ---------------------------------------------------------------- piezas gráficas

KPI = {
    "inicio": ("TURNO ABIERTO", "Desde las 04:30"),
    "venta": ("TOTAL A COBRAR", "1 100.00 CUP"),
    "inventario": ("PIZZA NAPOLITANA", "Alcanza para 12"),
    "servicios": ("REGLA ACTIVA", "Transferencia +5 %"),
    "registros": ("EXPORTAR", "PDF · Excel"),
    "equipo": ("RED LOCAL", "192.168.43.1"),
    "privacidad": ("RESPALDO", ".shalev cifrado"),
    "licencia": ("ESTADO", "Licencia activa"),
}


def rule(d, x, y, w=170, color=ORANGE, h=7):
    d.rounded_rectangle((x, y, x + w, y + h), radius=h / 2, fill=color)


def header(d, scene_, local, dark=False):
    a = anim(local, 0.0, 0.45)
    if not scene_["kicker"]:
        return 0
    kicker_fill = rgba(CYAN, 0.95) if dark else rgba(BLUE, 0.95 * a)
    tracked(d, (104 - 18 * (1 - a), 58), scene_["kicker"], font(21, "bold"), kicker_fill, 2.6)
    title_fill = rgba(WHITE, a) if dark else rgba(INK, a)
    f = font(50, "bold")
    y = 94
    for line in wrap(d, scene_["title"], f, 1080):
        d.text((104 - 22 * (1 - a), y), line, font=f, fill=title_fill)
        y += 58
    rule(d, 104, y + 8, 180 * a if a else 1, rgba(ORANGE, a))
    return y + 8


def feature_counter(d, scene_, local):
    idx = FEATURE_SCENES.index(scene_) + 1
    total = len(FEATURE_SCENES)
    a = anim(local, 0.1, 0.5)
    txt = f"{idx:02d} / {total:02d}"
    f = font(20, "bold")
    w = text_w(d, txt, f) + 34
    d.rounded_rectangle((1236 - w, 56, 1236, 98), radius=21, fill=rgba(PALE_BLUE, 0.95 * a))
    d.text((1236 - w / 2, 77), txt, font=f, fill=rgba(BLUE, a), anchor="mm")


def bullets(d, items, local, y0=310, x=104, width=1080, t0=0.18, dark=False):
    y = y0
    for i, item in enumerate(items):
        t0i = t0 + 0.14 * i
        a = anim(local, t0i, 0.5)
        if a <= 0:
            y += 104
            continue
        s = -16 * (1 - a)
        cy = y + 24 + s
        d.ellipse((x, cy - 20, x + 40, cy + 20), fill=rgba(CYAN if dark else PALE_CYAN, a))
        # marca de verificación
        d.line((x + 11, cy, x + 18, cy + 8, x + 30, cy - 9), fill=rgba(TEAL, a), width=5, joint="curve")
        title = item[0]
        detail = item[1] if len(item) > 1 else ""
        d.text((x + 60, y + s), title, font=font(30, "bold"), fill=rgba(WHITE if dark else INK, a))
        if detail:
            for j, line in enumerate(wrap(d, detail, font(25), width - 70)):
                d.text((x + 60, y + 40 + j * 31 + s), line, font=font(25),
                       fill=rgba("#C9D8F2" if dark else MUTED, a))
        y += 104
    return y


def chips(d, labels, local, y=806, x=104, t0=0.5):
    cx = x
    for i, label in enumerate(labels):
        a = anim(local, t0 + 0.1 * i, 0.4)
        if a <= 0:
            continue
        f = font(19, "bold")
        w = text_w(d, label, f) + 44
        box = (cx, y + 6 * (1 - a), cx + w, y + 48 + 6 * (1 - a))
        d.rounded_rectangle(box, radius=21, fill=rgba(WHITE, 0.85 * a))
        d.rounded_rectangle(box, radius=21, outline=rgba(BLUE, 0.25 * a), width=2)
        d.text(((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label, font=f, fill=rgba(BLUE, a), anchor="mm")
        cx += w + 14


def kpi_card(d, local, slug, y=880, x=104, t0=0.75):
    if slug not in KPI:
        return
    a = anim(local, t0, 0.55)
    if a <= 0:
        return
    label, value = KPI[slug]
    w, h = 470, 74
    box = (x, y + 14 * (1 - a), x + w, y + h + 14 * (1 - a))
    d.rounded_rectangle(box, radius=18, fill=rgba(NAVY, a))
    d.text((box[0] + 22, box[1] + 20), label, font=font(17, "bold"), fill=rgba(CYAN, a))
    d.text((box[0] + 22, box[1] + 40), value, font=font(24, "bold"), fill=rgba(WHITE, a))


def logo_tile(size: int) -> Image.Image:
    src = Image.open(ROOT / "play_store/icono_512.png").convert("RGB").resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tile.paste(src, (0, 0), mask)
    return tile


# ---------------------------------------------------------------- escenas especiales

def draw_intro(frame, scene_, local, tn):
    ov = Image.new("RGBA", (PANEL_W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    a = anim(local, 0.05, 0.6)
    size = 126
    tile = logo_tile(size)
    if a > 0:
        x, y = 104, int(54 + 18 * (1 - a))
        d.rounded_rectangle((x - 8, y - 8, x + size + 8, y + size + 8), radius=int(size * 0.24),
                            fill=rgba(WHITE, 0.12 * a))
        ov.alpha_composite(tile, (x, y))
    a2 = anim(local, 0.22, 0.6)
    tracked(d, (256, 82 + 18 * (1 - a2)), "SHALEV", font(64, brand=True), rgba(WHITE, a2), 3.0)
    tracked(d, (260, 150), "PUNTO DE VENTA · INVENTARIO · SERVICIOS", font(19, "bold"), rgba(CYAN, a2), 2.2)

    a3 = anim(local, 0.36, 0.6)
    f = font(64, "black")
    y = 216 + 22 * (1 - a3)
    for i, line in enumerate(["Tu negocio,", "bajo control."]):
        d.text((104, y + i * 72), line, font=f, fill=rgba(WHITE, a3))
    rule(d, 104, y + 152, 190 * a3 if a3 else 1, rgba(ORANGE, a3))

    a4 = anim(local, 0.5, 0.55)
    for i, line in enumerate(wrap(d, "Vende, controla la caja y conoce tu ganancia real. Todo desde tu "
                                     "teléfono, incluso sin internet.", font(29), 1000)):
        d.text((104, 412 + i * 38), line, font=font(29), fill=rgba("#D7E3F7", a4))

    bullets(d, scene_["bullets"], local, y0=548, width=1040, t0=0.62, dark=True)
    chips(d, scene_["chips"], local, y=872, t0=0.95)
    frame.alpha_composite(ov, (0, 0))


def draw_panel(frame, scene_, local):
    ov = Image.new("RGBA", (PANEL_W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    header(d, scene_, local)
    feature_counter(d, scene_, local)
    bullets(d, scene_["bullets"], local, y0=312)
    chips(d, scene_["chips"], local, y=792, t0=0.42)
    kpi_card(d, local, scene_["slug"], y=872, t0=0.7)
    frame.alpha_composite(ov, (0, 0))


def draw_steps(frame, scene_, local, tn):
    """Los tres pasos de la licencia se encienden al ritmo de la narración."""
    ov = Image.new("RGBA", (PANEL_W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    header(d, scene_, local)
    feature_counter(d, scene_, local)

    marks = [LEAD + 3.30, LEAD + 4.95, LEAD + 8.15]   # instantes en que se pide cada paso
    done_at = LEAD + 8.15
    active = 0
    for i, m in enumerate(marks):
        if tn >= m:
            active = i
    finished = tn >= done_at
    if finished:
        active = len(marks) - 1

    y = 306
    for i, (title, detail) in enumerate(scene_["steps"]):
        a = anim(local, 0.18 + 0.13 * i, 0.5)
        if a <= 0:
            continue
        done = finished or i < active
        on = (i == active) and not finished
        box = (104, y + 18 * (1 - a), 1160, y + 136 + 18 * (1 - a))
        d.rounded_rectangle((box[0] + 4, box[1] + 10, box[2] + 4, box[3] + 10), radius=26,
                            fill=rgba("#C9D6EA", 0.30 * a))
        d.rounded_rectangle(box, radius=24, fill=rgba(WHITE, (0.99 if on else 0.72) * a))
        if on:
            d.rounded_rectangle(box, radius=24, outline=rgba(BLUE, 0.9 * a), width=3)
        cy = (box[1] + box[3]) / 2
        badge = ORANGE_DARK if on else (TEAL if done else "#AEB8C6")
        d.ellipse((132, cy - 31, 194, cy + 31), fill=rgba(badge, a))
        if done:
            d.line((151, cy, 163, cy + 13, 180, cy - 13), fill=rgba(WHITE, a), width=6, joint="curve")
        else:
            d.text((163, cy - 1), str(i + 1), font=font(29, "bold"), fill=rgba(WHITE, a), anchor="mm")
        d.text((216, cy - 42), title, font=font(32, "bold"), fill=rgba(INK if (on or done) else MUTED, a))
        d.text((216, cy + 2), detail, font=font(24), fill=rgba(MUTED, a))
        # Barra de progreso del paso activo.
        if on:
            start = marks[i]
            end = marks[i + 1] if i + 1 < len(marks) else done_at
            prog = min(1.0, max(0.0, (tn - start) / max(0.4, end - start)))
            d.rounded_rectangle((132, box[3] - 9, 132 + (1028) * prog, box[3] - 5), radius=2,
                                fill=rgba(ORANGE, 0.9 * a))
        y += 156

    a_ok = anim(local, done_at - LEAD, 0.5)
    if a_ok > 0:
        box = (104, 786, 1160, 862)
        d.rounded_rectangle(box, radius=20, fill=rgba(PALE_CYAN, 0.95 * a_ok))
        d.text((130, 800), "LICENCIA ACTIVA", font=font(19, "bold"), fill=rgba(TEAL, a_ok))
        d.text((130, 822), "Tus datos, turnos e historial siguen intactos", font=font(23),
               fill=rgba(INK, a_ok))
    chips(d, scene_["chips"], local, y=880, t0=0.6)
    frame.alpha_composite(ov, (0, 0))


PLANES = [
    ("MENSUAL", "6 000", "+1 000 CUP por app secundaria", "30 días"),
    ("SEMESTRAL", "30 000", "+5 000 CUP por app secundaria", "6 meses"),
    ("ANUAL", "50 000", "+9 000 CUP por app secundaria", "12 meses"),
    ("PERPETUA", "90 000", "+17 000 CUP por app secundaria", "Sin vencimiento"),
]


def draw_plans(frame, scene_, local):
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    header(d, scene_, local)
    a0 = anim(local, 0.15, 0.5)
    for i, line in enumerate(wrap(d, "Todas las funciones incluidas. Precios en CUP.", font(27), 1050)):
        d.text((104, 210 + i * 34), line, font=font(27), fill=rgba(MUTED, a0))

    for i, (name, price, extra, period) in enumerate(PLANES):
        a = anim(local, 0.3 + 0.16 * i, 0.55)
        if a <= 0:
            continue
        col, row = i % 2, i // 2
        x = 104 + col * 600
        y = 288 + row * 250 + 18 * (1 - a)
        w, h = 570, 214
        highlight = name == "ANUAL"
        d.rounded_rectangle((x + 6, y + 12, x + w + 6, y + h + 12), radius=26, fill=rgba("#C9D6EA", 0.35 * a))
        d.rounded_rectangle((x, y, x + w, y + h), radius=24,
                            fill=rgba(NAVY if highlight else WHITE, a))
        d.rounded_rectangle((x, y, x + w, y + h), radius=24,
                            outline=rgba(ORANGE if highlight else "#D5DEEB", a), width=3 if highlight else 2)
        d.text((x + 26, y + 22), name, font=font(21, "bold"), fill=rgba(CYAN if highlight else BLUE, a))
        d.text((x + 24, y + 48), price, font=font(62, "black"), fill=rgba(WHITE if highlight else INK, a))
        pw = text_w(d, price, font(62, "black"))
        d.text((x + 30 + pw, y + 82), "CUP", font=font(22, "bold"), fill=rgba(CYAN if highlight else MUTED, a))
        d.text((x + 26, y + 128), period, font=font(24, "bold"), fill=rgba(WHITE if highlight else TEAL, a))
        d.text((x + 26, y + 162), extra, font=font(21), fill=rgba("#C9D8F2" if highlight else MUTED, a))

    # Tarjeta lateral con todo lo que incluye cualquier plan.
    a_inc = anim(local, 0.55, 0.6)
    if a_inc > 0:
        cx0, cy0 = 1372, 74
        cw, ch = 470, 812
        y_off = 20 * (1 - a_inc)
        d.rounded_rectangle((cx0 + 6, cy0 + 12 + y_off, cx0 + cw + 6, cy0 + ch + 12 + y_off), radius=30,
                            fill=rgba("#C9D6EA", 0.30 * a_inc))
        d.rounded_rectangle((cx0, cy0 + y_off, cx0 + cw, cy0 + ch + y_off), radius=28, fill=rgba(WHITE, 0.97 * a_inc))
        d.rounded_rectangle((cx0, cy0 + y_off, cx0 + cw, cy0 + ch + y_off), radius=28,
                            outline=rgba("#D5DEEB", a_inc), width=2)
        d.text((cx0 + 34, cy0 + 40 + y_off), "INCLUIDO EN TODOS LOS PLANES", font=font(19, "bold"),
               fill=rgba(BLUE, a_inc))
        included = ["Ventas y caja con turnos", "Inventario, insumos y recetas",
                    "Servicios con insumos", "Precios automáticos",
                    "Registros, PDF y Excel", "Apps vinculadas (hasta 10)",
                    "Respaldo cifrado .shalev", "Actualizaciones verificadas"]
        for i, item in enumerate(included):
            ai = anim(local, 0.7 + 0.09 * i, 0.45)
            if ai <= 0:
                continue
            y = cy0 + 92 + y_off + i * 62
            d.ellipse((cx0 + 34, y, cx0 + 68, y + 34), fill=rgba(PALE_CYAN, ai))
            d.line((cx0 + 43, y + 18, cx0 + 50, y + 26, cx0 + 61, y + 10), fill=rgba(TEAL, ai), width=5,
                   joint="curve")
            d.text((cx0 + 84, y + 4), item, font=font(24), fill=rgba(INK, ai))
        a_t = anim(local, 1.5, 0.6)
        if a_t > 0:
            d.rounded_rectangle((cx0 + 34, cy0 + 632, cx0 + cw - 34, cy0 + 756), radius=20,
                                fill=rgba(NAVY, a_t))
            d.text((cx0 + 58, cy0 + 652), "PRUEBA GRATIS", font=font(17, "bold"), fill=rgba(CYAN, a_t))
            d.text((cx0 + 58, cy0 + 678), "7 días", font=font(40, "black"), fill=rgba(WHITE, a_t))
            d.text((cx0 + 58, cy0 + 726), "Sin tarjeta y sin compromiso", font=font(19),
                   fill=rgba("#C9D8F2", a_t))

    a1 = anim(local, 1.0, 0.6)
    box = (104, 800, 1160, 892)
    d.rounded_rectangle(box, radius=22, fill=rgba(PALE_ORANGE, a1))
    d.rounded_rectangle(box, radius=22, outline=rgba(ORANGE, 0.9 * a1), width=3)
    d.text((130, 822), "7 DÍAS DE PRUEBA GRATIS · TODAS LAS FUNCIONES", font=font(22, "bold"),
           fill=rgba(ORANGE_DARK, a1))
    d.text((130, 852), "Activación en 3 pasos desde Ajustes · Licencia", font=font(24), fill=rgba(INK, a1))

    frame.alpha_composite(ov, (0, 0))


def draw_outro(frame, scene_, local, tn):
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    a = anim(local, 0.05, 0.6)
    size = 108
    tile = logo_tile(size)
    if a > 0:
        ov.alpha_composite(tile, (150, int(96 + 16 * (1 - a))))
    tracked(d, (286, 112 + 16 * (1 - a)), "SHALEV", font(54, brand=True), rgba(WHITE, a), 2.6)
    tracked(d, (288, 168), "PUNTO DE VENTA · INVENTARIO · SERVICIOS", font(17, "bold"), rgba(CYAN, a), 2.0)

    a2 = anim(local, 0.25, 0.6)
    f = font(62, "black")
    d.text((150, 250 + 20 * (1 - a2)), "Únete al grupo", font=f, fill=rgba(WHITE, a2))
    d.text((150, 322 + 20 * (1 - a2)), "de Telegram", font=f, fill=rgba(CYAN, a2))
    rule(d, 150, 412, 210 * a2 if a2 else 1, rgba(ORANGE, a2))

    a3 = anim(local, 0.45, 0.55)
    items = [("Novedades y versiones nuevas", CYAN),
             ("Ayuda directa de otros usuarios", CYAN),
             ("Avisos de mantenimiento y soporte", CYAN)]
    for i, (txt, col) in enumerate(items):
        y = 452 + i * 56
        d.ellipse((152, y + 4, 186, y + 38), fill=rgba(col, 0.22 * a3))
        d.line((161, y + 21, 169, y + 30, 182, y + 11), fill=rgba(col, a3), width=5, joint="curve")
        d.text((204, y + 4), txt, font=font(28), fill=rgba("#D9E5F8", a3))

    # Tarjeta del código QR.
    a4 = anim(local, 0.6, 0.6)
    if a4 > 0:
        cx, cy = 1010, 470
        d.rounded_rectangle((cx, cy, cx + 400, cy + 400 + 96), radius=34, fill=rgba(WHITE, 0.98 * a4))
        qr = Image.open(WORK / "telegram_qr.png").convert("RGB").resize((300, 300), Image.Resampling.NEAREST)
        ov.paste(qr, (cx + 50, cy + 40))
        d.text((cx + 200, cy + 372), TELEGRAM_HANDLE, font=font(40, "bold"), fill=rgba(NAVY, a4), anchor="ma")
        d.text((cx + 200, cy + 424), "Escanea con la cámara del teléfono", font=font(21),
               fill=rgba(MUTED, a4), anchor="ma")

    a5 = anim(local, 1.1, 0.6)
    if a5 > 0:
        f2 = font(30, "bold")
        label = TELEGRAM_URL.replace("https://", "")
        w = text_w(d, label, f2) + 90
        box = (700, 906, 700 + w, 976)
        d.rounded_rectangle(box, radius=35, fill=rgba("#229ED9", a5))
        # Avión de papel (icono de Telegram) dibujado a mano.
        bx, by = box[0] + 34, 941
        d.polygon([(bx - 12, by + 2), (bx + 14, by - 12), (bx + 2, by + 13)], fill=rgba(WHITE, a5))
        d.polygon([(bx - 12, by + 2), (bx + 2, by + 13), (bx - 2, by + 2)], fill=rgba("#CFE9F8", a5))
        d.text((box[0] + 76, 941), label, font=f2, fill=rgba(WHITE, a5), anchor="lm")
    a6 = anim(local, 1.35, 0.6)
    d.text((150, 700), "Vende mejor. Controla más.", font=font(44, "bold"), fill=rgba(WHITE, a6))
    d.text((152, 760), "Shalev · Android 8.0 o superior · 7 días gratis", font=font(26),
           fill=rgba("#B9C9E6", a6))
    frame.alpha_composite(ov, (0, 0))


# ---------------------------------------------------------------- teléfono en pantalla

def draw_phone(frame, scene_, tn):
    shots = scene_["shots"]
    if not shots:
        return
    i = shot_index_at(scene_, tn)
    cam = camera_at(scene_, tn)
    surf = phone_surface(shots[i], cam)
    if i > 0:
        dt = tn - shots[i]["t"]
        if 0 <= dt < XFADE:
            k = ease(dt / XFADE)
            prev = phone_surface(shots[i - 1], cam)
            surf = Image.blend(prev, surf, k)
    frame.paste(surf, (SLOT[0], SLOT[1]), surf)


def draw_focus(frame, scene_, tn):
    shots = scene_["shots"]
    if not shots:
        return
    i = shot_index_at(scene_, tn)
    shot = shots[i]
    cam = camera_at(scene_, tn)
    dt = tn - shot["t"]
    if dt < 0:
        return
    ov = Image.new("RGBA", (_SLOT_W, _SLOT_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)

    box = shot.get("box")
    if box:
        a = anim(dt, 0.10, 0.40)
        if a > 0:
            pulse = 0.5 + 0.5 * math.sin(tn * math.pi * 1.6)
            p0 = slot_point(shot, cam, box[0], box[1])
            p1 = slot_point(shot, cam, box[2], box[3])
            rect = (p0[0] - SLOT[0], p0[1] - SLOT[1], p1[0] - SLOT[0], p1[1] - SLOT[1])
            d.rounded_rectangle(rect, radius=22, outline=(0, 0, 0, int(90 * a)), width=9)
            d.rounded_rectangle(rect, radius=22, outline=rgba(ORANGE, a), width=6)
            inner = (rect[0] + 5, rect[1] + 5, rect[2] - 5, rect[3] - 5)
            d.rounded_rectangle(inner, radius=18, outline=rgba("#FFD9B8", (0.25 + 0.35 * pulse) * a), width=2)

    tap = shot.get("tap")
    if tap:
        cx, cy = slot_point(shot, cam, tap[0], tap[1])
        cx, cy = cx - SLOT[0], cy - SLOT[1]
        # Punto que llega hasta el control y pulso de toque.
        travel = min(1.0, max(0.0, (dt - 0.05) / 0.42))
        if travel > 0:
            px = lerp(cx + 150, cx, out_cubic(travel))
            py = lerp(cy + 190, cy, out_cubic(travel))
            rad = 13 * (1 - 0.35 * travel)
            d.ellipse((px - rad - 5, py - rad - 5, px + rad + 5, py + rad + 5), fill=rgba(WHITE, 0.55))
            d.ellipse((px - rad, py - rad, px + rad, py + rad), fill=rgba(ORANGE, 0.95))
        for k in range(2):
            rt = dt - 0.42 - k * 0.22
            if rt <= 0:
                continue
            p = min(1.0, rt / 0.62)
            r = 18 + 62 * out_cubic(p)
            d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=rgba(ORANGE, 0.85 * (1 - p)), width=6)
        if dt >= 0.42:
            p = min(1.0, (dt - 0.42) / 0.5)
            r = 20 * (1 - p)
            if r > 1:
                d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=rgba("#FFF1E2", 0.9 * (1 - p)))
    frame.paste(ov, (SLOT[0], SLOT[1]), ov)


# ---------------------------------------------------------------- subtítulos

def caption_at(t: float):
    for scene_ in SCENES:
        for (a, b, text) in scene_["captions"]:
            t0 = scene_["narration_start"] + a
            t1 = scene_["narration_start"] + b
            if t0 - 0.001 <= t <= t1:
                fade = min(1.0, (t - t0) / 0.16, (t1 - t) / 0.16 + 0.0)
                return text, max(0.0, min(1.0, fade))
    return None, 0.0


def draw_caption(frame, t):
    text, alpha = caption_at(t)
    ov = Image.new("RGBA", (W, H - CAP_Y), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    d.rectangle((0, 0, W, H - CAP_Y), fill=rgba("#0A1A30", 0.97))
    d.rectangle((0, 0, W, 3), fill=rgba(BLUE, 0.55))
    d.rectangle((0, 0, int(W * min(1.0, t / max(TOTAL, 1e-6))), 3), fill=rgba(ORANGE, 1.0))
    tracked(d, (74, (H - CAP_Y) / 2 - 9), "SHALEV", font(17, brand=True), rgba(CYAN, 0.75), 2.2)
    if text:
        f = font(31, "bold")
        while text_w(d, text, f) > 1560 and f.size > 22:
            f = font(f.size - 1, "bold")
        d.text((W / 2 + 60, (H - CAP_Y) / 2), text, font=f, fill=rgba(WHITE, alpha), anchor="mm")
    frame.alpha_composite(ov, (0, CAP_Y))


# ---------------------------------------------------------------- fotograma

XF = 0.26


def compose(t: float, total: float) -> Image.Image:
    """Dibuja la escena (sin subtítulos) en RGBA."""
    idx, local = locate(t)
    scene_ = SCENES[idx]
    tn = local - LEAD
    kind = "dark" if scene_["kind"] in ("intro", "outro") else "light"
    frame = background(kind).convert("RGBA")

    # Fondo con vida: dos manchas que se desplazan despacio.
    ph = t / max(total, 1e-6)
    g1 = glow(CYAN if kind == "dark" else BLUE)
    g2 = glow(TEAL if kind == "dark" else ORANGE)
    frame.alpha_composite(g1, (int(1050 + 120 * math.sin(ph * 5.2)), int(-260 + 90 * math.sin(ph * 3.1))))
    frame.alpha_composite(g2, (int(-250 + 90 * math.cos(ph * 4.1)), int(620 + 70 * math.cos(ph * 2.7))))

    if scene_["kind"] == "intro":
        draw_intro(frame, scene_, local, tn)
    elif scene_["kind"] == "panel":
        draw_panel(frame, scene_, local)
    elif scene_["kind"] == "steps":
        draw_steps(frame, scene_, local, tn)
    elif scene_["kind"] == "plans":
        draw_plans(frame, scene_, local)
    elif scene_["kind"] == "outro":
        draw_outro(frame, scene_, local, tn)

    draw_phone(frame, scene_, tn)
    draw_focus(frame, scene_, tn)
    return frame


def render_frame(t: float, total: float) -> Image.Image:
    frame = compose(t, total)
    idx, local = locate(t)
    if idx > 0 and local < XF:
        prev = compose(SCENES[idx - 1]["end"] - 0.001, total)
        frame = Image.blend(prev, frame, ease(local / XF))

    # Entradas y salidas globales.
    if t < 0.55:
        frame = Image.blend(background("dark").convert("RGBA"), frame, ease(t / 0.55))
    if t > total - 0.9:
        end_frame = Image.new("RGBA", (W, H), hx("#081426") + (255,))
        frame = Image.blend(frame, end_frame, ease((t - (total - 0.9)) / 0.9))
    draw_caption(frame, t)
    return frame.convert("RGB")


# ---------------------------------------------------------------- subtítulos SRT

def srt_time(t: float) -> str:
    ms = int(round(t * 1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_srt(total: float) -> Path:
    lines, n = [], 1
    for scene_ in SCENES:
        for (a, b, text) in scene_["captions"]:
            start = scene_["narration_start"] + a
            end = min(scene_["narration_start"] + b, scene_["end"] - 0.05)
            lines += [str(n), f"{srt_time(start)} --> {srt_time(end)}", text, ""]
            n += 1
    path = OUT / "Shalev_promo_90s.srt"
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


def scene_title(scene_) -> str:
    return scene_["title"] or ("Presentación" if scene_["kind"] == "intro" else "Cierre y Telegram")


def write_guide(total: float) -> Path:
    lines = [
        "# Shalev · Video promocional de 90 segundos",
        "",
        f"**Duración:** {int(total // 60)}:{total % 60:04.1f} min · **{total:.1f} s**  ",
        f"**Formato:** 1920 × 1080 · **{FPS} fps** · H.264 High + AAC  ",
        "**Idioma:** español · narración y subtítulos integrados (también en `.srt`)  ",
        "",
        "Promoción de las funciones clave de Shalev construida con las capturas reales de la app "
        "(`app/capturas/claro` y `promocion/capturas`). El zoom, los pulsos naranja y el punto de toque "
        "señalan el control que se está usando; la música de fondo es original y baja de volumen sola "
        "mientras hay voz.",
        "",
        "## Escenas",
        "",
        "| # | Escena | Inicio | Duración | Capturas |",
        "|---|---|---:|---:|---|",
    ]
    for scene_ in SCENES:
        shots = ", ".join(Path(s["img"]).name for s in scene_["shots"]) or "—"
        lines.append(f'| {scene_["n"]:02d} | {scene_title(scene_)} | '
                     f'{int(scene_["start"] // 60)}:{scene_["start"] % 60:04.1f} | '
                     f'{scene_["duration"]:.1f} s | {shots} |')
    lines += ["", "## Narración", ""]
    for scene_ in SCENES:
        if scene_["narration"]:
            lines += [f'**{scene_["n"]:02d}. {scene_title(scene_)}** — {scene_["narration"]}', ""]
    lines += [
        "**11. Cierre** — «Únete al grupo de Telegram: t.me/spviapp» · «Shalev: vende mejor, controla más».",
        "",
        "## Precios mostrados (CUP)",
        "",
        "| Plan | App principal | Por cada app secundaria (0–10) |",
        "|---|---:|---:|",
        "| Mensual | 6 000 | +1 000 |",
        "| Semestral | 30 000 | +5 000 |",
        "| Anual | 50 000 | +9 000 |",
        "| Perpetua | 90 000 | +17 000 |",
        "",
        "Cifras tomadas de `MANUAL_USUARIO.md` §14.1 y de `LICENSE_CLIENT.md`. La activación se explica "
        "tal como aparece en la app: Ajustes → Licencia → tres pasos (revisar datos, solicitar, activar).",
        "",
        "## Grupo de Telegram",
        "",
        f"- Enlace: <{TELEGRAM_URL}> (`{TELEGRAM_HANDLE}`)",
        "- El cierre muestra el código QR del grupo y el enlace en pantalla.",
        "",
        "## Archivos",
        "",
        "- `Shalev_promo_90s_1080p60.mp4`: video final (narración, música, animaciones y subtítulos).",
        "- `Shalev_promo_90s.srt`: subtítulos para reeditar o publicar aparte.",
        "- `Shalev_promo_90s_guion.md`: este documento.",
        "",
        "## Cómo se regenera",
        "",
        "```bash",
        ".venv/bin/python tools/video_promo/generar_promo_90.py --preview   # fotogramas de control",
        ".venv/bin/python tools/video_promo/generar_promo_90.py --render    # MP4 completo",
        "```",
        "",
        "La voz se lee de `.cache/shalev_promo90/voz/01…10.mp3` (no versionada). Para una voz nueva, "
        "reemplaza esos archivos y vuelve a renderizar.",
    ]
    path = OUT / "Shalev_promo_90s_guion.md"
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


# ---------------------------------------------------------------- audio

SR = 44100


def play(buf: np.ndarray, start: float, data: np.ndarray, gain: float = 1.0) -> None:
    i = int(round(start * SR))
    if i >= len(buf) or i + len(data) <= 0:
        return
    if i < 0:
        data = data[-i:]
        i = 0
    n = min(len(data), len(buf) - i)
    if n > 0:
        buf[i:i + n] += data[:n] * gain


def env_ad(n: int, attack: float, release: float) -> np.ndarray:
    e = np.ones(n, dtype=np.float32)
    a = min(n, int(attack * SR))
    r = min(n, int(release * SR))
    if a > 0:
        e[:a] = (np.linspace(0, 1, a, dtype=np.float32) ** 0.7)
    if r > 0:
        e[-r:] *= (np.linspace(1, 0, r, dtype=np.float32) ** 1.3)
    return e


def note(freq: float, dur: float, *, harmonics=(1.0, 0.32, 0.11), attack=0.02, release=0.3,
         detune: float = 0.0) -> np.ndarray:
    n = max(2, int(dur * SR))
    t = np.arange(n, dtype=np.float32) / SR
    f = freq * (1 + detune)
    out = np.zeros(n, dtype=np.float32)
    for k, amp in enumerate(harmonics, start=1):
        out += (amp * np.sin(2 * np.pi * f * k * t + k * 0.4)).astype(np.float32)
    return out * env_ad(n, attack, release)


def pluck(freq: float, dur: float = 0.55) -> np.ndarray:
    n = max(2, int(dur * SR))
    t = np.arange(n, dtype=np.float32) / SR
    raw = (np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2 * t)
           + 0.12 * np.sin(2 * np.pi * freq * 3 * t)).astype(np.float32)
    return raw * np.exp(-4.6 * t).astype(np.float32) * 0.8


def midi(m: float) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def synth_music(total: float) -> np.ndarray:
    n = int(total * SR)
    out = np.zeros((n, 2), dtype=np.float32)
    bpm = 82.0
    beat = 60.0 / bpm
    bar = 4 * beat
    prog = [([60, 64, 67, 72], 36), ([57, 60, 64, 69], 33), ([53, 57, 60, 65], 29), ([55, 59, 62, 67], 31)]
    bars = int(math.ceil(total / bar)) + 1
    for b in range(bars):
        start = b * bar
        chords, root = prog[b % 4]
        # Colchón de acordes, con apertura lenta.
        for j, m in enumerate(chords):
            dur = bar * 1.02
            left = note(midi(m), dur, attack=0.55, release=1.1, detune=-0.0012)
            right = note(midi(m), dur, attack=0.55, release=1.1, detune=0.0013)
            play(out[:, 0], start, left, 0.11)
            play(out[:, 1], start, right, 0.11)
        # Bajo suave en los pulsos 1 y 3.
        for k in (0, 2):
            play(out[:, 0], start + k * beat, note(midi(root), beat * 1.6, harmonics=(1.0, 0.18),
                                                  attack=0.03, release=0.5), 0.20)
            play(out[:, 1], start + k * beat, note(midi(root), beat * 1.6, harmonics=(1.0, 0.18),
                                                  attack=0.03, release=0.5), 0.20)
        # Arpegio discreto de corcheas.
        for k in range(8):
            m = chords[k % len(chords)] + 12
            pan = 0.25 if k % 2 else -0.25
            s = pluck(midi(m))
            play(out[:, 0], start + k * beat / 2, s, 0.055 * (1 - pan))
            play(out[:, 1], start + k * beat / 2, s, 0.055 * (1 + pan))
        # Percusión mínima: bombo suave y sonajero.
        rng = np.random.default_rng(b) 
        for k in (0, 2):
            ln = int(0.22 * SR)
            t = np.arange(ln, dtype=np.float32) / SR
            kick = (np.sin(2 * np.pi * (58 - 22 * t) * t) * np.exp(-14 * t)).astype(np.float32)
            play(out[:, 0], start + k * beat, kick, 0.16)
            play(out[:, 1], start + k * beat, kick, 0.16)
        for k in range(4):
            ln = int(0.06 * SR)
            t = np.arange(ln, dtype=np.float32) / SR
            sh = (rng.standard_normal(ln).astype(np.float32) * np.exp(-60 * t))
            sh -= np.linspace(sh[0], sh[-1], ln, dtype=np.float32)
            play(out[:, 0], start + (k + 0.5) * beat, sh, 0.030)
            play(out[:, 1], start + (k + 0.5) * beat, sh, 0.026)
    return out


def tap_click() -> np.ndarray:
    n = int(0.09 * SR)
    t = np.arange(n, dtype=np.float32) / SR
    return ((np.sin(2 * np.pi * (1250 - 500 * t) * t) * 0.7
             + np.sin(2 * np.pi * 2100 * t) * 0.25) * np.exp(-55 * t)).astype(np.float32)


def whoosh() -> np.ndarray:
    n = int(0.55 * SR)
    rng = np.random.default_rng(7)
    raw = rng.standard_normal(n).astype(np.float32)
    kernel = np.hanning(140).astype(np.float32)
    kernel /= kernel.sum()
    sm = np.convolve(raw, kernel, mode="same").astype(np.float32)
    t = np.arange(n, dtype=np.float32) / n
    env = np.sin(np.pi * t) ** 2.2
    return sm * env * 1.6


def ding() -> np.ndarray:
    n = int(0.9 * SR)
    t = np.arange(n, dtype=np.float32) / SR
    return ((np.sin(2 * np.pi * 1046.5 * t) + 0.5 * np.sin(2 * np.pi * 1568 * t)
             + 0.2 * np.sin(2 * np.pi * 2093 * t)) * np.exp(-3.4 * t)).astype(np.float32)


def build_audio(total: float) -> Path:
    n = int(total * SR)
    narr = np.zeros(n, dtype=np.float32)
    for scene_ in SCENES:
        if not scene_["audio"]:
            continue
        with wave.open(str(VOZ / Path(scene_["audio"]).with_suffix(".wav")), "rb") as wf:
            data = np.frombuffer(wf.readframes(wf.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
        play(narr, scene_["narration_start"], data)
    narr *= 0.80
    speech_rms = float(np.sqrt(np.mean(narr[narr != 0] ** 2))) if np.any(narr) else 0.05

    music = synth_music(total)
    music_rms = float(np.sqrt(np.mean(music ** 2)))
    # La música se mantiene unos 15 dB por debajo de la voz y baja aún más mientras se habla.
    music *= (speech_rms * 0.26) / max(music_rms, 1e-6)

    env = np.abs(narr)
    win = int(0.18 * SR)
    env = np.convolve(env, np.ones(win, dtype=np.float32) / win, mode="same")
    top = float(np.percentile(env[env > 0], 96)) if np.any(env > 0) else 1.0
    env = np.clip(env / max(top, 1e-9), 0, 1)
    duck_raw = 1.0 - 0.72 * env
    w2 = int(0.28 * SR)
    duck = np.convolve(duck_raw, np.ones(w2, dtype=np.float32) / w2, mode="same")
    duck = np.clip(duck, 0.0, 1.0)

    mix = music * duck[:, None]
    mix[:, 0] += narr
    mix[:, 1] += narr

    # Toques sincronizados con los pulsos de la pantalla.
    click, air = tap_click(), whoosh()
    for scene_ in SCENES:
        for shot in scene_["shots"]:
            if shot.get("tap"):
                play(mix[:, 0], scene_["narration_start"] + shot["t"] + 0.42, click, 0.28)
                play(mix[:, 1], scene_["narration_start"] + shot["t"] + 0.42, click, 0.28)
    for scene_ in SCENES[1:]:
        play(mix[:, 0], scene_["start"] - 0.18, air, 0.10)
        play(mix[:, 1], scene_["start"] - 0.18, air, 0.10)
    # Aviso sonoro al mostrar los precios y al cierre.
    for scene_ in SCENES:
        if scene_["slug"] in ("planes", "cierre"):
            d = ding()
            play(mix[:, 0], scene_["start"] + (0.35 if scene_["slug"] == "planes" else 0.15), d, 0.10)
            play(mix[:, 1], scene_["start"] + (0.35 if scene_["slug"] == "planes" else 0.15), d, 0.10)

    # Fundidos y normalización final.
    fi, fo = int(0.5 * SR), int(1.2 * SR)
    mix[:fi] *= np.linspace(0, 1, fi, dtype=np.float32)[:, None]
    mix[-fo:] *= np.linspace(1, 0, fo, dtype=np.float32)[:, None]
    peak = float(np.max(np.abs(mix)))
    if peak > 0.01:
        mix *= 0.93 / peak
    rms = float(np.sqrt(np.mean(mix ** 2)))
    path = WORK / "mezcla_promo.wav"
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(SR)
        wf.writeframes((np.clip(mix, -1, 1) * 32767).astype("<i2").tobytes())
    print(f"Audio: {path.name} | pico={min(peak, 0.95):.3f} RMS={rms:.3f} "
          f"voz RMS={speech_rms:.3f} música={speech_rms * 0.20:.3f}")
    return path


# ---------------------------------------------------------------- render

def render_video(total: float, audio: Path, desde: float = 0.0, hasta: float | None = None) -> Path:
    hasta = total if hasta is None else min(hasta, total)
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    partial = desde > 0.0 or hasta < total
    name = "Shalev_promo_90s_1080p60.mp4"
    output = OUT / (("_parcial_" + name) if partial else name)
    frames = int(math.ceil((hasta - desde) * FPS))
    cmd = [
        ff, "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-ss", f"{desde:.3f}", "-i", str(audio),
        "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
        "-profile:v", "high", "-level", "4.2", "-g", str(FPS * 2),
        "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
        "-t", f"{hasta - desde:.3f}", "-movflags", "+faststart", str(output),
    ]
    print(f"Render: {output.name} | {frames} fotogramas | {hasta - desde:.2f} s | {FPS} fps")
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    try:
        for i in range(frames):
            t = min(desde + i / FPS, total - 0.001)
            frame = render_frame(t, total)
            proc.stdin.write(frame.tobytes())
            if i % (FPS * 10) == 0:
                print(f"  {i}/{frames} · {t:.1f}/{total:.1f} s", flush=True)
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
    return output


def make_preview(total: float) -> None:
    PREVIEW.mkdir(parents=True, exist_ok=True)
    picks = []
    for scene_ in SCENES:
        if scene_["shots"]:
            for k, shot in enumerate(scene_["shots"]):
                picks.append((scene_["narration_start"] + shot["t"] + 1.0, f'{scene_["n"]:02d}_{k + 1}'))
        else:
            picks.append((scene_["start"] + min(1.6, scene_["duration"] / 2), f'{scene_["n"]:02d}_1'))
    thumbs = []
    for t, tag in picks:
        if t >= total:
            continue
        im = render_frame(min(t, total - 0.05), total)
        im.save(PREVIEW / f"frame_{tag}.png", optimize=True)
        thumb = im.resize((480, 270), Image.Resampling.LANCZOS)
        d = ImageDraw.Draw(thumb)
        d.rounded_rectangle((10, 10, 120, 40), radius=10, fill=rgba("#08131F", 0.85))
        d.text((18, 16), f"{tag} · {t:.0f}s", font=font(16, "bold"), fill=rgba(WHITE, 1.0))
        thumbs.append(thumb)
    cols = 4
    rows = math.ceil(len(thumbs) / cols)
    sheet = Image.new("RGB", (cols * 480, rows * 270), hx("#08131F"))
    for i, thumb in enumerate(thumbs):
        sheet.paste(thumb, ((i % cols) * 480, (i // cols) * 270))
    sheet.save(PREVIEW / "contacto.png", optimize=True)
    print(f"Vistas: {len(thumbs)} · hoja de contacto en {PREVIEW.relative_to(ROOT)}/contacto.png")


def main() -> None:
    global TOTAL
    ap = argparse.ArgumentParser(description="Video promocional de Shalev (~90 s, 1080p60)")
    ap.add_argument("--preview", action="store_true", help="fotogramas de control y hoja de contacto")
    ap.add_argument("--render", action="store_true", help="renderiza el MP4")
    ap.add_argument("--desde", type=float, default=0.0, help="segundo inicial (pruebas)")
    ap.add_argument("--hasta", type=float, default=None, help="segundo final (pruebas)")
    args = ap.parse_args()
    if not args.preview and not args.render:
        args.preview = True
    OUT.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    decode_voices()
    TOTAL = build_timeline()
    print(f"Duración total: {TOTAL:.2f} s ({int(TOTAL // 60)}:{TOTAL % 60:04.1f})")
    for scene_ in SCENES:
        print(f'  {scene_["n"]:02d} {scene_["slug"]:<11} {scene_["duration"]:5.2f} s '
              f'(narración {scene_["narr_dur"]:5.2f} s)')
    write_srt(TOTAL)
    write_guide(TOTAL)
    if args.preview:
        make_preview(TOTAL)
    if args.render:
        audio = build_audio(TOTAL)
        render_video(TOTAL, audio, args.desde, args.hasta)


if __name__ == "__main__":
    main()
