# Prompt para generar las fotos del seed con OpenCode

Copia el bloque de abajo tal cual en OpenCode (CLI o Desktop) **en tu PC**, con el repo
`Shalev` actualizado (`git pull`; ya incluye `FOTOS_SEED.md` y `tools/seed_demo/`).

```text
Genera y prepara las fotos del seed de depuración de Shalev siguiendo FOTOS_SEED.md:

1. Lee primero FOTOS_SEED.md, el catálogo real en
   data/src/debug/kotlin/cu/shalev/data/seed/Sembrador.kt y tools/seed_demo/
   (descargar_fotos.py, fotos_wikipedia.py, generar_fotos.py, CREDITOS.md).

2. Verifica el mapeo y el estado actual:
   python3 tools/seed_demo/verificar_fotos_seed.py
   Debe terminar en OK (36 productos prod01-36 + 20 servicios serv01-20; los 4
   elaborados, índices 37-40, NO llevan foto por diseño: no toques prod37-40).

3. Consigue las 56 imágenes en tools/seed_demo/seed_fotos/ con el nombre exacto del
   mapeo (prodNN.jpg / servNN.jpg):
   - Prioridad: fotos REALES de Wikimedia Commons con tools/seed_demo/descargar_fotos.py
     (prefiere dominio público/CC0; si no, CC BY/CC BY-SA con crédito). Anota cada obra,
     licencia y enlace en tools/seed_demo/CREDITOS.md (y creditos.json si aplica).
   - Si una descarga falla o la imagen no sirve, genera la tarjeta ilustrada de reserva
     con tools/seed_demo/generar_fotos.py y deja constancia en CREDITOS.md.
   - Formato final: JPEG, recorte cuadrado al centro, 512x512, calidad ~88 (la app luego
     las re-codifica a ≤1024 px sin EXIF; vale cualquier foto razonable).

4. Vuelve a ejecutar python3 tools/seed_demo/verificar_fotos_seed.py: debe dar OK y dejar
   el lote listo en tools/seed_demo/fotos_seed/ (56 archivos). NO subas al repo ese lote
   (está en .gitignore); sí versiona seed_fotos/ y los créditos.

5. Con el teléfono conectado por USB y la app debug instalada:
   for f in tools/seed_demo/fotos_seed/*.jpg; do
     n=$(basename "$f")
     adb push "$f" /data/local/tmp/"$n"
     adb shell run-as cu.shalev.app.debug cp /data/local/tmp/"$n" files/fotos/"$n"
   done
   adb shell am start -n cu.shalev.app.debug/cu.shalev.app.seed.SeedActivity

6. Comprueba en el teléfono:
   adb shell run-as cu.shalev.app.debug ls files/fotos | wc -l   # debe dar 56
   Abre Inventario y Servicios: los artículos deben mostrar su foto; los 4 elaborados
   (Café con leche, Capuchino, Refresco natural de mango, Sándwich de jamón) sin foto.

7. No toques nada fuera de tools/seed_demo/ (ni el Sembrador, ni FOTOS_SEED.md salvo que
   el mapeo cambie de verdad, y entonces actualiza ambos a la vez).

Al final dime: cuántas fotos reales vs. generadas quedaron, si CREDITOS.md está completo,
la salida de verificar_fotos_seed.py y el resultado de la comprobación en el teléfono.

## Cambios recientes (Rediseño "Local")

- La función **"Almacén/Local"** se ha renombrado a **"Local"**.
- El combobox único se ubica ahora en **Inicio** (en lugar de campos separados en Inventario/Servicios).
- Cada local corresponde a un **único Inventario y Servicios** (compartido).
- El nuevo local se introduce mediante el botón **(+)** (última opción del combobox) y se confirma por teclado.
- Los documentos seed, el código `Sembrador.kt` y los repositorios han sido actualizados para reflejar este modelo único.
```
