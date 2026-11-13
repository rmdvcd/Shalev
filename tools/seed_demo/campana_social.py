"""Material social con capturas reales, sin recrear ni inventar pantallas.

Instalación: python -m pip install -r tools/seed_demo/requirements_promocion.txt
Uso: python tools/seed_demo/campana_social.py [--video]
No requiere Android. El reel es un montaje animado, no una grabación de uso.
"""
from __future__ import annotations

import argparse
from functools import lru_cache
from pathlib import Path
import re
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAIZ = Path(__file__).resolve().parents[2]
CAPTURAS = RAIZ / 'promocion/capturas'
SALIDA = RAIZ / 'promocion/campana_social'
CREMA = '#F5F6F0'
TINTA = '#122C40'
AZUL = '#2E50A3'
CIAN = '#61D0E6'
GRIS = '#526775'
BLANCO = '#FFFFFF'
CONTACTO = (RAIZ / 'Android/core/src/main/kotlin/cu/shalev/core/contact/DeveloperContact.kt').read_text()
TELEFONO = re.search(r'TELEFONO_LOCAL = "(\d{8})"', CONTACTO).group(1)
WHATSAPP = f'+53 {TELEFONO[:4]} {TELEFONO[4:]}'
DURACIONES = [4, 4, 4, 4, 4, 4, 6]


@lru_cache(maxsize=64)
def fuente(tam: int, negrita: bool = False):
    nombre = 'DejaVuSans-Bold.ttf' if negrita else 'DejaVuSans.ttf'
    candidatos = [Path('/usr/share/fonts/truetype/dejavu') / nombre,
                  Path('C:/Windows/Fonts/' + ('arialbd.ttf' if negrita else 'arial.ttf')),
                  Path('/Library/Fonts/Arial' + (' Bold.ttf' if negrita else '.ttf'))]
    for ruta in candidatos:
        if ruta.exists():
            return ImageFont.truetype(str(ruta), tam)
    raise RuntimeError('Instala DejaVu Sans o Arial para mantener la legibilidad.')


def texto(im, xy, contenido, tam=28, color=TINTA, bold=False, maxw=None):
    d = ImageDraw.Draw(im)
    f = fuente(tam, bold)
    for i, linea in enumerate(contenido.split('\n')):
        if maxw is not None and d.textlength(linea, font=f) > maxw:
            raise ValueError(f'Texto fuera de su columna: {linea}')
        d.text((xy[0], xy[1] + i * int(tam * 1.22)), linea, font=f, fill=color, stroke_width=0)


def centro(im, y, contenido, tam=28, color=TINTA, bold=False):
    d = ImageDraw.Draw(im)
    w = d.textlength(contenido, font=fuente(tam, bold))
    assert w <= im.width - 60, contenido
    texto(im, ((im.width-w)/2, y), contenido, tam, color, bold)


def caja(im, rect, color, radio=24, borde=None):
    ImageDraw.Draw(im).rounded_rectangle(rect, radius=radio, fill=color, outline=borde, width=2)


def fondo(alto=1920, oscuro=False):
    im = Image.new('RGB', (1080, alto), TINTA if oscuro else CREMA)
    d = ImageDraw.Draw(im)
    # Geometría editorial: acentos fuera del área de lectura, sin trama sobre la app.
    d.ellipse((770, -200, 1290, 320), fill='#1C4257' if oscuro else '#E1ECED')
    d.ellipse((-260, alto-300, 200, alto+180), fill='#1C4257' if oscuro else '#E7EBF7')
    return im


@lru_cache(maxsize=24)
def pantalla(nombre):
    im = Image.open(CAPTURAS / f'shot_{nombre}.png').convert('RGB')
    # Solo se quitan las barras del sistema Android. El contenido de la app no se altera.
    return im.crop((0, 96, im.width, im.height - 104))


def foto(im, nombre, x, y, w, recorte=None):
    src = pantalla(nombre).copy()
    if recorte:
        # Coordenadas proporcionales sobre el área de la app; solo para detalles ampliados.
        a, b, c, e = recorte
        src = src.crop((int(a*src.width), int(b*src.height), int(c*src.width), int(e*src.height)))
    h = round(src.height*w/src.width)
    src = src.resize((w,h), Image.Resampling.LANCZOS)
    margen = 24
    sombra = Image.new('RGBA', (w+2*margen, h+2*margen), (0,0,0,0))
    ImageDraw.Draw(sombra).rounded_rectangle((margen, margen+6, w+margen, h+margen+6), 28, fill=(8,29,45,55))
    sombra = sombra.filter(ImageFilter.GaussianBlur(12))
    im.paste(sombra, (x-margen,y-margen), sombra)
    mascara = Image.new('L', (w,h), 0)
    ImageDraw.Draw(mascara).rounded_rectangle((0,0,w-1,h-1), 22, fill=255)
    im.paste(src, (x,y), mascara)
    ImageDraw.Draw(im).rounded_rectangle((x,y,x+w-1,y+h-1), 22, outline='#CFD9DE', width=2)
    return h


def pildora(im, xy, mensaje, oscuro=False, tam=24):
    w = int(ImageDraw.Draw(im).textlength(mensaje, font=fuente(tam,True))) + 42
    caja(im, (xy[0],xy[1],xy[0]+w,xy[1]+50), CIAN if oscuro else '#E1E8FA', 25)
    texto(im, (xy[0]+21,xy[1]+9), mensaje, tam, TINTA if oscuro else AZUL, True)


def marca(im, oscuro=False, indice=None):
    texto(im, (64,57), 'SHALEV', 40, BLANCO if oscuro else TINTA, True)
    texto(im, (65,110), 'PUNTO DE VENTA + INVENTARIO', 19, CIAN if oscuro else GRIS, True)
    if indice:
        texto(im, (911,65), indice, 23, CIAN if oscuro else AZUL, True)


def pie(im, y, oscuro=False, compacto=False):
    color = BLANCO if oscuro else TINTA
    centro(im, y, 'Conoce Shalev por WhatsApp', 26 if compacto else 30, color, True)
    centro(im, y+44, WHATSAPP, 32 if compacto else 36, CIAN if oscuro else AZUL, True)
    centro(im, y+98, 'Android 8.0+  ·  Capturas con datos de demostración', 18, '#B8D0DA' if oscuro else GRIS)


def guardar(im, nombre):
    im.save(SALIDA / nombre, optimize=True)


def collages():
    # Tres pantallas claras, dos detalles oscuros de análisis y registros.
    im = fondo(1350)
    marca(im)
    texto(im, (64,168), 'Tu negocio en orden.', 66, TINTA, True, 960)
    texto(im, (64,251), 'Incluso sin internet.', 57, AZUL, True, 960)
    pildora(im, (64,334), 'Vende · Controla · Analiza')
    for nombre,x,etiqueta in [('inicio_claro',64,'TU RESUMEN'),('inventario_claro',400,'INVENTARIO'),('servicios_claro',736,'SERVICIOS')]:
        texto(im,(x,412),etiqueta,20,AZUL,True,280)
        foto(im,nombre,x,452,280)
    texto(im,(64,1042),'GRÁFICOS Y REGISTROS EN MODO OSCURO',19,GRIS,True)
    foto(im,'inicio',64,1080,450,(.02,.61,.98,.77))
    foto(im,'registros',552,1080,464,(.02,.16,.98,.30))
    # Detalles ampliados, sin estirar la captura; CTA separada del contenido.
    caja(im,(0,1234,1080,1350),TINTA,0)
    texto(im,(64,1250),'Efectivo + transferencias',27,BLANCO,True)
    texto(im,(64,1293),'WhatsApp  '+WHATSAPP,27,CIAN,True)
    texto(im,(835,1298),'Android 8.0+',18,BLANCO)
    guardar(im,'collage_feed_1080x1350.png')
    im.save(SALIDA/'collage_feed_whatsapp.jpg',quality=92,optimize=True)

    im = fondo()
    marca(im)
    texto(im,(64,173),'Todo tu negocio.',70,TINTA,True,960)
    texto(im,(64,261),'En tu Android.',70,AZUL,True,960)
    pildora(im,(64,367),'Funciona sin internet')
    texto(im,(64,449),'Productos, servicios, caja y empleados.',29,GRIS,False,960)
    for nombre,x,etiqueta in [('inicio_claro',64,'01 / RESUMEN'),('inventario_claro',400,'02 / PRODUCTOS'),('servicios_claro',736,'03 / SERVICIOS')]:
        texto(im,(x,527),etiqueta,19,AZUL,True)
        foto(im,nombre,x,568,280)
    texto(im,(64,1193),'Decide con tus datos.',43,TINTA,True)
    foto(im,'inicio',64,1269,256,(0,.43,1,1))
    foto(im,'registros',352,1269,256,(0,.04,1,.61))
    texto(im,(654,1271),'Ventas y ganancia',25,TINTA,True,365)
    texto(im,(654,1312),'Gráficos y registros\npara revisar tu negocio.',23,GRIS,False,365)
    texto(im,(654,1405),'PDF + Excel',27,AZUL,True,365)
    texto(im,(654,1446),'Reportes y respaldos\nsin depender de internet.',23,GRIS,False,365)
    texto(im,(654,1540),'Tu equipo conectado',25,TINTA,True,365)
    texto(im,(654,1581),'Apps de empleados por\nWi-Fi local, según licencia.',22,GRIS,False,365)
    caja(im,(48,1680,1032,1876),TINTA,30)
    centro(im,1701,'Menos desorden. Más control.',33,BLANCO,True)
    centro(im,1754,'WhatsApp  '+WHATSAPP,34,CIAN,True)
    centro(im,1811,'Android 8.0+ · Productos e importes de demostración',18,'#B8D0DA')
    guardar(im,'super_collage_1080x1920.png')
    im.save(SALIDA/'super_collage_whatsapp.jpg',quality=92,optimize=True)


def escena(numero, titulo, subtitulo, claro, nombre, etiqueta, segunda=None):
    im = fondo(oscuro=not claro)
    color = TINTA if claro else BLANCO
    marca(im,not claro,f'{numero:02d} / 07')
    texto(im,(64,188),titulo,64,color,True,940)
    texto(im,(64,362),subtitulo,29,GRIS if claro else '#C0D5DE',False,940)
    pildora(im,(64,461),etiqueta,not claro,23)
    if segunda:
        foto(im,nombre,102,573,420)
        foto(im,segunda,558,657,420)
    else:
        foto(im,nombre,289,562,502)
    pie(im,1650,not claro)
    return im


def escenas():
    carpeta = SALIDA/'escenas_reel'
    carpeta.mkdir(exist_ok=True)
    datos = [
        ('Tu negocio.\nSin internet.','Vende y lleva el control desde tu Android.',True,'inicio_claro','SHALEV · Para pequeños negocios',None),
        ('Cada producto,\nbajo control.','Fotos, existencias, insumos y alertas.',True,'inventario_claro','INVENTARIO · Busca y organiza',None),
        ('Tus servicios\ntambién cuentan.','Catálogo con fotos, categorías y precios.',True,'servicios_claro','SERVICIOS · Todo en un solo lugar',None),
        ('Vende y cobra.\nSin complicarte.','Efectivo y transferencias, en la misma app.',False,'carrito','VENTAS · Control por turnos','pago'),
        ('Tus números.\nMás claros.','Consulta ventas, ganancia y registros.',False,'inicio','REPORTES · Exporta a PDF y Excel','registros'),
        ('Conecta\na tu equipo.','Vincula los teléfonos de tus empleados.',False,'vinculadas','WI-FI LOCAL · Cupo según licencia',None),
    ]
    for i,datos_escena in enumerate(datos,1):
        escena(i,*datos_escena).save(carpeta/f'{i:02d}.png',optimize=True)
    im = fondo()
    marca(im,False,'07 / 07')
    texto(im,(64,190),'Menos desorden.\nMás control.',70,TINTA,True,950)
    texto(im,(64,380),'Punto de venta + inventario + servicios',29,GRIS)
    for nombre,x,y in [('inicio_claro',64,586),('inventario_claro',400,550),('servicios_claro',736,586)]:
        foto(im,nombre,x,y,280)
    pildora(im,(64,1234),'Funciona sin internet')
    texto(im,(64,1325),'Datos cifrados en tu teléfono.\nSin anuncios.',35,TINTA,True,950)
    caja(im,(64,1515,1016,1778),TINTA,32)
    centro(im,1547,'Conoce Shalev por WhatsApp',35,BLANCO,True)
    centro(im,1611,WHATSAPP,49,CIAN,True)
    centro(im,1693,'Android 8.0+ · Consulta las opciones de licencia',21,BLANCO)
    centro(im,1814,'Capturas reales · Datos de demostración',20,GRIS)
    im.save(carpeta/'07.png',optimize=True)


def video():
    import imageio_ffmpeg
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    # Fundidos, escala suave, 24 fps constantes y audio AAC silencioso para compatibilidad.
    # La salida final conserva 30 s; no se simulan pulsaciones ni una sesión de uso real.
    with tempfile.TemporaryDirectory(prefix='shalev_reel_') as tmp:
        clips=[]
        for i,duracion in enumerate(DURACIONES,1):
            clip=Path(tmp)/f'{i}.mp4'
            frames=duracion*24
            filtro=(f"scale=1124:1998,zoompan=z='1.01+0.025*on/{frames}':"
                    f"x='iw/2-iw/zoom/2':y='ih/2-ih/zoom/2':d={frames}:s=1080x1920:fps=24,"
                    f"fade=t=in:st=0:d=0.22,fade=t=out:st={duracion-.22}:d=0.22,format=yuv420p")
            subprocess.run([ffmpeg,'-y','-loglevel','error','-i',str(SALIDA/'escenas_reel'/f'{i:02d}.png'),
                            '-vf',filtro,'-t',str(duracion),'-an','-c:v','libx264','-preset','fast',
                            '-crf','21','-threads','2',str(clip)],check=True)
            clips.append(clip)
            print(f'Escena {i}/7 renderizada',flush=True)
        lista=Path(tmp)/'clips.txt'
        lista.write_text(''.join(f"file '{p.as_posix()}'\n" for p in clips))
        subprocess.run([ffmpeg,'-y','-loglevel','error','-f','concat','-safe','0','-i',str(lista),
                        '-f','lavfi','-i','anullsrc=r=48000:cl=stereo','-map','0:v:0','-map','1:a:0',
                        '-c:v','copy','-c:a','aac','-b:a','96k','-t','30','-movflags','+faststart',
                        str(SALIDA/'reel_shalev_1080x1920.mp4')],check=True)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--video',action='store_true')
    args=parser.parse_args()
    SALIDA.mkdir(parents=True,exist_ok=True)
    collages()
    escenas()
    if args.video:
        video()
    print(f'Material guardado en {SALIDA}')
