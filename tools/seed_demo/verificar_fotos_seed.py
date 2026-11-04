#!/usr/bin/env python3
"""Verifica y empaqueta las fotos del seed de depuración (ver FOTOS_SEED.md).

Comprueba que:
1. El mapeo de FOTOS_SEED.md (tablas productos 1-36 y servicios 1-20) coincide con el
   catálogo real de `data/src/debug/kotlin/cu/shalev/data/seed/Sembrador.kt`
   (el índice del catálogo es el número del archivo: prodNN.jpg / servNN.jpg).
2. Existen todas las imágenes necesarias en `seed_fotos/` y son JPEG válidos.
3. Empaqueta el lote listo para subir al teléfono en `fotos_seed/`
   (prod01..prod36 + serv01..serv20; los elaborados 37-40 no llevan foto por diseño).

Uso:
    python3 tools/seed_demo/verificar_fotos_seed.py

Después, con el teléfono conectado:
    for f in tools/seed_demo/fotos_seed/*.jpg; do
      n=$(basename "$f")
      adb push "$f" /data/local/tmp/"$n"
      adb shell run-as cu.shalev.app.debug cp /data/local/tmp/"$n" files/fotos/"$n"
    done
    adb shell am start -n cu.shalev.app.debug/cu.shalev.app.seed.SeedActivity
"""
import os
import re
import shutil
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(os.path.dirname(AQUI))
SEMBRADOR = os.path.join(RAIZ, "data/src/debug/kotlin/cu/shalev/data/seed/Sembrador.kt")
DOC = os.path.join(RAIZ, "FOTOS_SEED.md")
FOTOS = os.path.join(AQUI, "seed_fotos")
LOTE = os.path.join(AQUI, "fotos_seed")

N_PRODUCTOS = 36   # sin los 4 elaborados (sin foto por diseño)
N_SERVICIOS = 20


def catalogo(ruta, patron):
    src = open(ruta, encoding="utf-8").read()
    return re.findall(patron, src)


def tabla_doc(seccion):
    doc = open(DOC, encoding="utf-8").read()
    parte = doc.split(seccion)[1]
    return re.findall(r"\| \d+ \| ([^|]+?) \| [^|]+ \| `[^`]+` \|", parte)


def jpeg_valido(ruta):
    try:
        from PIL import Image
        with Image.open(ruta) as im:
            im.verify()
        with Image.open(ruta) as im:
            return im.format == "JPEG"
    except Exception:
        return False


def main():
    errores = []

    prods = catalogo(SEMBRADOR, r'Prod\("([^"]+)"')
    servs = catalogo(SEMBRADOR, r'Servicio\("([^"]+)"')
    doc_prods = tabla_doc("## Mapeo productos")
    doc_servs = tabla_doc("## Mapeo servicios")

    if len(prods) < N_PRODUCTOS or len(servs) < N_SERVICIOS:
        errores.append(f"el catálogo del Sembrador tiene {len(prods)} productos / {len(servs)} servicios")
    for i, (a, b) in enumerate(zip(prods[:N_PRODUCTOS], doc_prods), 1):
        if a != b:
            errores.append(f"producto {i}: Sembrador «{a}» ≠ FOTOS_SEED.md «{b}»")
    for i, (a, b) in enumerate(zip(servs[:N_SERVICIOS], doc_servs), 1):
        if a != b:
            errores.append(f"servicio {i}: Sembrador «{a}» ≠ FOTOS_SEED.md «{b}»")

    necesarios = [f"prod{i:02d}.jpg" for i in range(1, N_PRODUCTOS + 1)] + \
                 [f"serv{i:02d}.jpg" for i in range(1, N_SERVICIOS + 1)]
    faltan = [n for n in necesarios if not os.path.isfile(os.path.join(FOTOS, n))]
    if faltan:
        errores.append("faltan imágenes: " + ", ".join(faltan))
    invalidas = [n for n in necesarios if os.path.isfile(os.path.join(FOTOS, n)) and not jpeg_valido(os.path.join(FOTOS, n))]
    if invalidas:
        errores.append("imágenes no válidas (no son JPEG legible): " + ", ".join(invalidas))

    if errores:
        print("FALLO:")
        for e in errores:
            print(" -", e)
        return 1

    # Empaqueta el lote listo para adb push (elaborados sin foto por diseño).
    os.makedirs(LOTE, exist_ok=True)
    for n in necesarios:
        shutil.copy2(os.path.join(FOTOS, n), os.path.join(LOTE, n))
    print(f"OK: mapeo alineado ({N_PRODUCTOS} productos + {N_SERVICIOS} servicios) y {len(necesarios)} JPEG válidos.")
    print(f"Lote listo en: {LOTE}")
    print("Sube las fotos al teléfono con el bucle adb del docstring y relanza SeedActivity.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
