# 5 archivos para probar a mano el escáner de inventario

Estos archivos están hechos para el asistente **Inventario → «Escanear o importar inventario»**
(`app/src/main/kotlin/cu/shalev/app/ocr/`). Cubren las cinco fuentes reales que acepta:
foto (manuscrito y galería), PDF, Excel `.xlsx`, CSV y Word `.docx`.

> **Antes de empezar:** lo que importes se guarda de verdad en el inventario.
> Exporta un respaldo en **Ajustes → Respaldo** o usa una instalación de prueba
> (`cu.shalev.app.debug`). Al terminar puedes borrar los artículos importados desde
> Inventario → selección múltiple → borrar.

El icono del escáner aparece en la barra superior de Inventario solo si el perfil activo
tiene permiso para **editar inventario**. Rama actual: versión 1.0.1 (`versionCode 54`).

---

## 1. Cómo llevar los archivos al teléfono

Con el teléfono conectado por USB:

```bash
adb push pruebas/scanner-inventario/01-inventario-manuscrito.jpg /sdcard/Download/
adb push pruebas/scanner-inventario/02-inventario-impreso-2-paginas.pdf /sdcard/Download/
adb push pruebas/scanner-inventario/03-inventario-excel.xlsx /sdcard/Download/
adb push pruebas/scanner-inventario/04-inventario.csv /sdcard/Download/
adb push pruebas/scanner-inventario/05-inventario-word.docx /sdcard/Download/
```

También sirve enviarlos por correo, Drive o Bluetooth y descargarlos en el teléfono.
En el asistente cada archivo se abre con el botón que corresponda:

| Botón del asistente | Archivos que abre |
|---|---|
| **Cámara (manuscrito o impreso)** | foto nueva en el momento |
| **Foto de la galería** | `01-inventario-manuscrito.jpg` |
| **Abrir PDF** | `02-inventario-impreso-2-paginas.pdf` |
| **Abrir Excel (.xlsx / .csv)** | `03-inventario-excel.xlsx`, `04-inventario.csv` |
| **Abrir Word (.docx)** | `05-inventario-word.docx` |

---

## 2. Resumen

| # | Archivo | Formato | Qué prueba | Resultado esperado |
|---|---|---|---|---|
| 1 | `01-inventario-manuscrito.jpg` | Foto de apuntes a mano (1650 × 1844) | OCR de manuscrito, asignación por toque/arrastre y cola de filas | 11 filas útiles: 8 productos + 3 insumos |
| 2 | `02-inventario-impreso-2-paginas.pdf` | PDF de 2 páginas | Renderizado del PDF, cambio de página y encabezado de tabla | Página 1: 8 productos · Página 2: 6 insumos |
| 3 | `03-inventario-excel.xlsx` | Excel con 2 hojas | Lector `.xlsx`, pestañas por hoja, mapeo por encabezado y fila inválida | Hoja «Inventario»: 10 importados y 1 omitido · Hoja «Insumos»: 5 |
| 4 | `04-inventario.csv` | CSV de Excel (BOM, `;`, comas decimales) | Lector CSV, campos entrecomillados, importes `1 250,00` | 8 importados: 5 productos y 3 insumos |
| 5 | `05-inventario-word.docx` | Word con 2 tablas | Lector `.docx`, dos tablas, fila vacía y fila sin precio | Tabla 1: 7 importados y 1 omitido · Tabla 2: 4 insumos |

Pasos comunes: **Inventario → icono del escáner → elegir origen → revisar el mapeo de columnas
→ desmarcar lo que sobre → «Importar filas seleccionadas»**. Al final la app vuelve al
inventario y muestra «N artículos importados al inventario» (o «N importados · M omitidos»).

---

## 3. `01-inventario-manuscrito.jpg` — el manuscrito

**Qué contiene:** una hoja de libreta fotografiada con 8 productos y 3 insumos escritos a mano,
más un título, la fecha, la palabra «INSUMOS» y **dos notas en rojo** que *no* son artículos
(«revisar precios del queso», «falta leche en polvo»).

| Producto | Cant | Unidad | P. venta | Costo |
|---|---|---|---|---|
| Café Serrano | 30 | u | 850 | 600 |
| Refresco Cola | 24 | u | 250 | 180 |
| Pan de molde | 12 | u | 320 | 250 |
| Queso Gouda | 6 | u | 950 | 700 |
| Aceite girasol | 10 | u | 1200 | 900 |
| Jabón de baño | 15 | u | 150 | 95 |
| Leche polvo | 8 | u | 1650 | 1300 |
| Yogurt natural | 20 | u | 180 | 120 |
| Azúcar | 2,5 | kg | — | 350 |
| Harina | 5 | kg | — | 480 |
| Café en grano | 1 | kg | — | 2400 |

**Cómo probarlo**

1. **Foto de la galería** → elige el JPG (o imprime la imagen y usa **Cámara**).
2. Aparece la foto con los recuadros de texto detectados y el chip **Ver como tabla**.
3. **Opción rápida (recomendada):** toca **Ver como tabla**.
   - La app propone el mapeo por posición, no por encabezado (la primera fila leída es el título),
     así que corrige los cinco chips: `Col A → Nombre`, `Col B → Cant.`, `Col C → Unidad`,
     `Col D → P. Venta`, `Col E → Costo`.
   - Desmarca las filas que no son artículos: título, fila de encabezados, «INSUMOS» y las dos
     notas en rojo.
4. **Opción lenta:** en el lienzo, elige un campo (por ejemplo *Nombre*), toca el recuadro de
   «Café Serrano», cambia a *Cantidad* y toca «30», etc., y pulsa **Añadir a la cola** por fila.
   Sirve para comprobar que el toque y el rectángulo arrastrado asignan bien el texto.
5. Pulsa **Importar filas seleccionadas**.

**Qué mirar:** que el reconocimiento transcriba bien tildes («Café», «Azúcar», «Jabón»), que la
columna `Unidad` con `kg` haga entrar esas tres filas como **Insumos** (con costo, sin precio de
venta) y que las filas con `u` entren como **productos**. El guion `—` de la columna de precio en
los insumos debe ignorarse sin error.

> La letra es sintética (tipografía manuscrita), no un escaneo de una libreta real: sirve para
> medir si el OCR aguanta trazos irregulares, pero tu propia letra puede dar resultados distintos.

---

## 4. `02-inventario-impreso-2-paginas.pdf` — impreso en dos páginas

**Página 1 — Ferretería Los Pinos (8 productos):** Martillo de acero, Cinta métrica 5 m,
Tornillos 2 pulg, Pintura blanca 4 L, Brocha 3 pulg, Llave de agua 1/2, Cemento gris y Alambre
dulce, con categoría, existencia, unidad `u`, precio de venta y precio de costo
(`1 250,00` con espacio de miles y coma decimal).

**Página 2 — Insumos de taller (6 insumos):** Thinner acrílico, Aceite de linaza, Yeso fino,
Sellador madera, Cemento blanco y Resina epóxica, en `L` y `kg`, con `—` en el precio de venta.

**Cómo probarlo**

1. **Abrir PDF** → elige el archivo. Se abre en la página 1 y, debajo del título, aparece
   **«Página 1 de 2»** con las flechas de página.
2. El encabezado de la tabla se detecta solo: los chips deben quedar como
   `Nombre · Categoría · Cant. · Unidad · P. Venta · Costo` y **«Omitir primera fila
   (encabezado)»** marcado.
3. Desmarca las filas de texto suelto (título, subtítulo «Inventario físico — 28 de octubre…» y
   la nota del pie); no son artículos.
4. **Importar filas seleccionadas** → «8 artículos importados al inventario».
5. Vuelve a abrir el escáner con el mismo PDF, pulsa **Página siguiente** («Página 2 de 2») e
   importa: «6 artículos importados al inventario». Las seis filas deben entrar como **insumos**
   (unidad `L`/`kg`), con cantidad decimal (`3,5`, `6,25`, `1,5`) y sin precio de venta.

**Qué mirar:** las flechas de página solo se habilitan cuando corresponde (en la 1 no se puede
retroceder, en la 2 no se puede avanzar); los importes con espacio de miles y coma decimal
(`2 100,00` → `2100.00`) y que el pie de página no se convierta en un artículo.

---

## 5. `03-inventario-excel.xlsx` — Excel de dos hojas

**Hoja «Inventario»** (7 columnas: Nombre, Categoría, Existencia, Unidad, Precio venta,
Precio costo, Descripción y 11 filas):

- 10 filas válidas, entre ellas dos con existencia 0 (Lata de atún, Pizza familiar), precios con
  centavos (Detergente `275,50`) y una descripción de más de 40 caracteres (Chocolate).
- 1 fila que **debe omitirse**: «Sin precio», sin precio de venta.
- «Pizza familiar» viene como categoría **Elaborado**: al importar se guarda como **General**,
  porque un elaborado necesita receta.

**Hoja «Insumos»** (Nombre, Unidad, Cantidad, Costo, Precio venta): Azúcar 2,5 kg, Harina 5 kg,
Aceite vegetal 8,75 L, Café en grano 1 kg y Sal 3 kg.

**Cómo probarlo**

1. **Abrir Excel (.xlsx / .csv)** → elige el `.xlsx`. Abajo aparecen dos chips con las hojas:
   **Inventario** e **Insumos**.
2. En «Inventario» el mapeo debe salir solo (7 columnas) y «Omitir primera fila» marcado.
   Importa: la app avisa **«10 importados · 1 omitidos»**.
3. Cambia a la hoja **Insumos** (se reinicia el mapeo y la selección) e importa los 5 insumos.

**Qué mirar:** que la hoja se cambie bien, que la fila sin precio se omita con el mensaje
«Revisa nombre y precio de venta (> 0 CUP) en «Sin precio»», que la existencia 0 se acepte, que
la descripción larga se recorte a 40 caracteres en la ficha, y que los 5 insumos queden con su
unidad y su cantidad decimal.

---

## 6. `04-inventario.csv` — CSV tal como lo guarda Excel

Lleva **BOM** de UTF-8, separador **punto y coma** y comas decimales, y algunos campos van entre
comillas porque contienen `;` («Frasco 250 ml; tapa roja», «Botella; 1 L», «Sobre individual;
surtido»). Encabezados abreviados a propósito: `Artículo;Existencia;Unidad;PVP;Costo;Categoría;Notas`.

**Cómo probarlo:** **Abrir Excel (.xlsx / .csv)** → elige el CSV. El mapeo automático debe quedar
`Nombre · Cant. · Unidad · P. Venta · Costo · Categoría · Descr.` e importar **8 filas**: 5
productos (Miel de abeja, Café molido, Té verde, Chocolate en polvo, Galletas surtidas) y 3
insumos (Aceite de oliva 3 L, Harina de trigo 10 kg, Vinagre 0 L).

**Qué mirar:** que `1 250,00` se lea como `1250.00`; que las comillas no rompan la fila; que
«Vinagre» con existencia 0 se acepte; y que las tildes («Artículo», «Café») se vean bien pese al
BOM del principio.

> Si el selector de archivos no te muestra el `.csv` (algunos gestores lo declaran como
> `application/vnd.ms-excel`), cópialo con extensión `.txt`: el botón **Abrir Excel (.xlsx / .csv)**
> también abre texto plano y el contenido es el mismo.

---

## 7. `05-inventario-word.docx` — Word con dos tablas

**Tabla 1 — Productos** (Producto, Categoría, Cantidad, Precio venta, Precio costo): Pan de agua,
Pan dulce, Croissant, Pastel de chocolate, una **fila en blanco**, Café molido, Refresco,
Galletas y **Flan** (sin precio de venta).

**Tabla 2 — Insumos** (Nombre, Unidad, Cantidad, Costo): Harina 25 kg, Azúcar 12,5 kg,
Levadura 500 g y Mantequilla 6 kg.

**Cómo probarlo:** **Abrir Word (.docx)** → elige el documento. Aparecen los chips **Tabla 1** y
**Tabla 2**. En la tabla 1 importa **7 filas** («7 importados · 1 omitidos»: el Flan se omite por
no tener precio) y en la tabla 2 los **4 insumos**.

**Qué mirar:** que la fila vacía desaparezca sola de la lista de filas detectadas y que el título
y la nota final del documento (párrafos, no tablas) no aparezcan como artículos.

---

## 8. Lista de verificación

- [ ] El icono **Escanear o importar inventario** aparece en la barra de Inventario y vuelve con ATRÁS.
- [ ] La cámara pide permiso una sola vez y la foto recién tomada se reconoce.
- [ ] El manuscrito se puede leer recuadro por recuadro y también en **Ver como tabla**.
- [ ] El PDF muestra «Página 1 de 2» / «Página 2 de 2» y las flechas se deshabilitan en los extremos.
- [ ] El `.xlsx` muestra las dos hojas y el mapeo automático de 7 columnas.
- [ ] El `.csv` con BOM y `;` se lee completo, con comillas y comas decimales.
- [ ] El `.docx` muestra «Tabla 1» y «Tabla 2», y descarta la fila vacía.
- [ ] Las filas sin precio se omiten con un mensaje claro y el resto se importa igual.
- [ ] Las cantidades decimales (`2,5 kg`, `8,75 L`) se guardan como insumos con decimales.
- [ ] Al terminar, las filas importadas aparecen en Inventario con categoría, precio y existencia correctos.

---

## 9. Regenerar o ajustar los archivos

Los cinco archivos se generan con `generar_archivos.py` (no hay que editar binarios a mano):

```bash
python3 -m pip install pillow reportlab openpyxl python-docx
python3 generar_archivos.py
```

Las tipografías manuscritas (Patrick Hand y Architects Daughter, licencia OFL) se buscan en
`fuentes/`, `/tmp/fuentes` o `/usr/share/fonts`; si no están, el script las descarga del
repositorio `google/fonts`. Los datos (nombres, precios, existencias) son inventados y están en
listas al principio de cada función del script, por si quieres cambiar el contenido de la prueba.

Las expectativas de esta guía se comprobaron contra la lógica real del lector
(`LectorDocumentosLocal`, `MapeadorOcr` y las validaciones de `ImportarOcrViewModel`): CSV 8 válidas,
`.xlsx` hoja 1 → 10 válidas y 1 omitida, hoja 2 → 5, `.docx` tabla 1 → 7 válidas y 1 omitida,
tabla 2 → 4. En el JPG y en el PDF el resultado depende del OCR del teléfono.
