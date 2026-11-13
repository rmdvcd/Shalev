"""Prepara los recursos de la ficha de Google Play a partir de las capturas reales.

Salidas en `Android/play_store/`:
  - icono_512.png            (512x512, 32 bits)
  - grafico_1024x500.png     (gráfico destacado)
  - capturas/01..08.png      (1080x1920, 9:16)
  - ficha.md                 (textos de la ficha)
"""
import os
import shutil

from PIL import Image, ImageDraw, ImageFilter, ImageFont

AQUI = os.path.dirname(__file__)
RAIZ = os.path.dirname(os.path.dirname(AQUI))
SHOTS = os.path.join(RAIZ, "app_usb_check")
RES = os.path.join(RAIZ, "Android", "app", "src", "main", "res", "mipmap-xxxhdpi")
SALIDA = os.path.join(RAIZ, "Android", "play_store")
CAPTURAS = os.path.join(SALIDA, "capturas")

AZUL = (96, 206, 244)
TEXTO_SUAVE = (176, 196, 220)
ARRIBA, ABAJO = (9, 15, 27), (18, 31, 50)
FUENTES = [r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"]

# Capturas para la ficha, en el orden en que se verán
ORDEN = [
    ("shot_inicio.png", "01_inicio"),
    ("shot_carrito.png", "02_venta"),
    ("shot_inventario.png", "03_inventario"),
    ("shot_servicios.png", "04_servicios"),
    ("shot_registros.png", "05_registros"),
    ("shot_vinculadas.png", "06_apps_vinculadas"),
    ("shot_pago.png", "07_comprobante"),
    ("shot_inventario_claro.png", "08_inventario_claro"),
]


def fuente(tam):
    for ruta in FUENTES:
        if os.path.exists(ruta):
            return ImageFont.truetype(ruta, tam)
    return ImageFont.load_default()


def icono():
    """Icono 512x512: la ilustración del lanzador sobre fondo blanco."""
    delante = Image.open(os.path.join(RES, "ic_launcher_foreground.png")).convert("RGBA")
    caja = delante.getbbox()
    delante = delante.crop(caja)
    lado = 430
    alto = max(1, int(delante.height * lado / delante.width))
    delante = delante.resize((lado, alto), Image.LANCZOS)
    hoja = Image.new("RGB", (512, 512), (255, 255, 255))
    hoja.paste(delante, ((512 - lado) // 2, (512 - alto) // 2), delante)
    return hoja


def recorte_9_16(ruta):
    im = Image.open(ruta).convert("RGB")
    alto = 1920
    im = im.crop((0, 96, im.width, im.height - (im.height - 96 - alto)))
    return im.resize((1080, alto), Image.LANCZOS)


def fondo(ancho, alto):
    img = Image.new("RGB", (ancho, alto), ARRIBA)
    d = ImageDraw.Draw(img)
    for y in range(alto):
        t = y / (alto - 1)
        d.line([(0, y), (ancho, y)], fill=tuple(int(ARRIBA[i] + (ABAJO[i] - ARRIBA[i]) * t) for i in range(3)))
    capa = Image.new("RGBA", (ancho, alto), (0, 0, 0, 0))
    dl = ImageDraw.Draw(capa)
    for x in range(0, ancho + 50, 50):
        dl.line([(x, 0), (x, alto)], fill=(255, 255, 255, 12), width=1)
    for y in range(0, alto + 50, 50):
        dl.line([(0, y), (ancho, y)], fill=(255, 255, 255, 12), width=1)
    return Image.alpha_composite(img.convert("RGBA"), capa)


def movil(ruta, alto):
    im = Image.open(ruta).convert("RGB")
    im = im.crop((0, 96, im.width, im.height - 96))
    ancho = int(alto * im.width / im.height)
    im = im.resize((ancho, alto), Image.LANCZOS)
    radio = max(10, ancho // 14)
    mascara = Image.new("L", im.size, 0)
    ImageDraw.Draw(mascara).rounded_rectangle((0, 0, ancho - 1, alto - 1), radio, fill=255)
    sombra = Image.new("RGBA", (ancho + 40, alto + 40), (0, 0, 0, 0))
    ImageDraw.Draw(sombra).rounded_rectangle((20, 24, ancho + 20, alto + 24), radio, fill=(0, 0, 0, 170))
    sombra = sombra.filter(ImageFilter.GaussianBlur(12))
    foto = Image.new("RGBA", (ancho, alto), (0, 0, 0, 0))
    foto.paste(im, (0, 0), mascara)
    return sombra, foto


def grafico():
    hoja = fondo(1024, 500)
    d = ImageDraw.Draw(hoja)
    d.text((64, 96), "SHALEV", font=fuente(76), fill=(255, 255, 255))
    d.text((66, 190), "Punto de venta e inventario", font=fuente(30), fill=AZUL)
    f = fuente(22)
    texto = "Funciona sin internet"
    caja = d.textbbox((0, 0), texto, font=f)
    ancho = (caja[2] - caja[0]) + 44
    d.rounded_rectangle((66, 248, 66 + ancho, 292), 22, fill=(23, 38, 58), outline=(34, 118, 176), width=2)
    d.text((88, 255), texto, font=f, fill=AZUL)
    d.text((66, 330), "Ventas · Inventario · Servicios", font=fuente(24), fill=TEXTO_SUAVE)

    for ruta, x, alto in ((os.path.join(SHOTS, "shot_carrito.png"), 640, 420),
                          (os.path.join(SHOTS, "shot_inicio.png"), 820, 420)):
        sombra, foto = movil(ruta, alto)
        capa = Image.new("RGBA", (foto.width + 40, foto.height + 40), (0, 0, 0, 0))
        capa.alpha_composite(sombra, (0, 0))
        capa.alpha_composite(foto, (20, 20))
        hoja.alpha_composite(capa, (x - 40, 52))
    return hoja.convert("RGB")


FICHA = """# Ficha de Google Play — Shalev

## Nombre (máx. 30)
Shalev: ventas e inventario

## Descripción corta (máx. 80)
Punto de venta e inventario para tu negocio. Funciona sin internet.

## Descripción completa (máx. 4000)

Shalev es tu punto de venta y tu inventario en el mismo lugar: cobra, controla lo que entra y lo
que sale, y revisa cómo va el negocio desde el teléfono. **Funciona sin internet**: todos los datos
se guardan en el teléfono y no hacen falta cuentas, servidores ni conexión.

**Ventas**
- Cobra por efectivo o por transferencia, con el comprobante del cliente.
- Carrito con cantidades, ajustes de precio y cálculo automático del total.
- Turnos de caja: apertura con fondo, entradas y salidas de efectivo, cierre con arqueo.
- Anula o corrige una venta sin perder el registro; queda el motivo y quién lo hizo.

**Inventario y servicios**
- Productos con foto, categoría, precio de costo y de venta, caducidad y niveles de stock.
- Insumos por unidad de medida (g, ml, kg, L) y recetas para los elaborados: al vender un café se
  descuentan sus insumos y se sabe cuántos más se pueden preparar.
- Servicios con foto, tipo e importe, también con los insumos que consumen.
- Avisos de stock bajo, crítico y próximo a caducar.

**Reportes**
- Gráficos de ventas y ganancia por turno, día, semana, mes o año.
- Lo más vendido, lo que se mueve lento, rentabilidad por artículo y top de servicios.
- Exporta el inventario, los servicios y los registros a PDF y Excel para compartir.

**Para tu equipo**
- Apps secundarias para los empleados (hasta 10), cada una con su turno y sus permisos.
- Se vinculan con un código QR por la red local: no hace falta internet ni cuenta.
- Cada transferencia lleva el vendedor que la hizo.

**Tus datos, en tu teléfono**
- La base de datos va cifrada y la app no pide permisos que no necesite.
- Respaldo completo cifrado con contraseña que puedes guardar y restaurar.
- Sin publicidad, sin analítica y sin rastreo.

**Licencia**
La app se prueba gratis 7 días. Para seguir usándola se activa una licencia que se solicita desde
la propia app (mensual, semestral, anual o perpetua). La licencia se emite de forma manual para
cada teléfono.

## Novedades (versión 1.0)
- Activación de licencias corregida: la solicitud cifrada ya la acepta el emisor.
- Primera versión pública.
"""


def main():
    os.makedirs(CAPTURAS, exist_ok=True)
    icono().save(os.path.join(SALIDA, "icono_512.png"))
    grafico().save(os.path.join(SALIDA, "grafico_1024x500.png"))
    for origen, destino in ORDEN:
        ruta = os.path.join(SHOTS, origen)
        if os.path.exists(ruta):
            recorte_9_16(ruta).save(os.path.join(CAPTURAS, f"{destino}.png"))
    with open(os.path.join(SALIDA, "ficha.md"), "w", encoding="utf-8") as f:
        f.write(FICHA)
    shutil.copy2(os.path.join(RES, "ic_launcher_foreground.png"), os.path.join(SALIDA, "icono_fuente_foreground.png"))
    print("recursos en", SALIDA)


if __name__ == "__main__":
    main()
