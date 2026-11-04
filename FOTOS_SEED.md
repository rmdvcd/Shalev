# Añadir fotos al seed de depuración

## Cómo funciona

En `data/src/debug/.../seed/Sembrador.kt`:
- `foto("prod", i)` → busca `files/fotos/prod01.jpg` … `prod36.jpg` (índice `i` del catálogo de productos, 0-based → archivo con `+1`).
- `foto("serv", i)` → busca `files/fotos/serv01.jpg` … `serv20.jpg` (índice `i` del catálogo de servicios).
- Si el archivo no existe, el producto/servicio se siembra **sin foto** y el seed no falla.
- Los 4 elaborados (Café con leche, Capuchino, Refresco natural de mango, Sándwich de jamón) **no llevan foto por diseño**.

Las fotos deben ir en el directorio privado de la app debug: `/data/data/cu.shalev.app.debug/files/fotos/` (solo accesible con `run-as`).

## Mapeo productos (1-36, sin elaborados)

| # | Producto | Categoría | Archivo destino | Qué buscar |
|---|----------|-----------|-----------------|------------|
| 1 | Café molido Serrano 250 g | Alimentos | `prod01.jpg` | café molido paquete 250 g |
| 2 | Arroz grano largo 1 kg | Granos y cereales | `prod02.jpg` | arroz grano largo paquete 1 kg |
| 3 | Frijol negro 1 kg | Granos y cereales | `prod03.jpg` | frijoles negros paquete |
| 4 | Azúcar refino 1 kg | Alimentos | `prod04.jpg` | azúcar refinado paquete 1 kg |
| 5 | Aceite de girasol 1 L | Aceites y grasas | `prod05.jpg` | aceite vegetal botella 1 litro |
| 6 | Harina de trigo 1 kg | Granos y cereales | `prod06.jpg` | harina de trigo paquete 1 kg |
| 7 | Leche en polvo 400 g | Lácteos | `prod07.jpg` | leche en polvo lata |
| 8 | Leche condensada 397 g | Lácteos | `prod08.jpg` | leche condensada lata |
| 9 | Yogur natural 200 ml | Lácteos | `prod09.jpg` | yogur natural vaso |
| 10 | Queso Gouda 250 g | Lácteos | `prod10.jpg` | queso gouda |
| 11 | Jamón cocido 200 g | Cárnicos y embutidos | `prod11.jpg` | jamón cocido lonchas |
| 12 | Salchicha ahumada 500 g | Cárnicos y embutidos | `prod12.jpg` | salchicha ahumada |
| 13 | Atún en aceite 170 g | Conservas | `prod13.jpg` | atún en aceite lata |
| 14 | Sardinas en tomate 155 g | Conservas | `prod14.jpg` | sardinas en tomate lata |
| 15 | Pasta larga 500 g | Alimentos | `prod15.jpg` | pasta larga paquete 500 g |
| 16 | Salsa de tomate 250 g | Conservas | `prod16.jpg` | salsa de tomate frasco |
| 17 | Mayonesa 250 g | Condimentos y especias | `prod17.jpg` | mayonesa frasco |
| 18 | Mostaza 200 g | Condimentos y especias | `prod18.jpg` | mostaza frasco |
| 19 | Refresco cola 1.5 L | Bebidas | `prod19.jpg` | refresco cola botella 1.5 L |
| 20 | Agua mineral 1.5 L | Bebidas | `prod20.jpg` | agua mineral botella 1.5 L |
| 21 | Jugo de mango 1 L | Bebidas | `prod21.jpg` | jugo de mango botella 1 L |
| 22 | Cerveza rubia 330 ml | Bebidas alcohólicas | `prod22.jpg` | cerveza rubia botella 330 ml |
| 23 | Ron añejo 750 ml | Bebidas alcohólicas | `prod23.jpg` | ron añejo botella 750 ml |
| 24 | Vino tinto seco 750 ml | Bebidas alcohólicas | `prod24.jpg` | vino tinto botella 750 ml |
| 25 | Pan de molde 400 g | Panadería y dulcería | `prod25.jpg` | pan de molde paquete |
| 26 | Galletas María 200 g | Panadería y dulcería | `prod26.jpg` | galletas María paquete |
| 27 | Chocolate en barra 100 g | Confituras y snacks | `prod27.jpg` | chocolate en barra |
| 28 | Papas fritas 120 g | Confituras y snacks | `prod28.jpg` | papas fritas bolsa |
| 29 | Maní salado 150 g | Confituras y snacks | `prod29.jpg` | maní salado paquete |
| 30 | Plátano 1 kg | Frutas y vegetales | `prod30.jpg` | plátanos |
| 31 | Tomate 1 kg | Frutas y vegetales | `prod31.jpg` | tomates |
| 32 | Cebolla 1 kg | Frutas y vegetales | `prod32.jpg` | cebollas |
| 33 | Detergente 500 g | Limpieza del hogar | `prod33.jpg` | detergente en polvo paquete |
| 34 | Jabón de baño 120 g | Aseo personal | `prod34.jpg` | jabón de baño |
| 35 | Pasta dental 90 g | Aseo personal | `prod35.jpg` | pasta dental tubo |
| 36 | Papel higiénico 4 rollos | Aseo personal | `prod36.jpg` | papel higiénico paquete 4 rollos |

Elaborados (sin foto por diseño): Café con leche, Capuchino, Refresco natural de mango, Sándwich de jamón.

## Mapeo servicios (1-20)

| # | Servicio | Categoría | Archivo destino | Qué buscar |
|---|----------|-----------|-----------------|------------|
| 1 | Recarga de saldo móvil | Telefonía | `serv01.jpg` | recarga teléfono móvil |
| 2 | Impresión en blanco y negro | Impresión | `serv02.jpg` | impresora documento blanco y negro |
| 3 | Impresión a color | Impresión | `serv03.jpg` | impresora documento a color |
| 4 | Fotocopia a una cara | Copias | `serv04.jpg` | fotocopiadora |
| 5 | Fotocopia a doble cara | Copias | `serv05.jpg` | fotocopiadora |
| 6 | Plastificado de carné | Encuadernación | `serv06.jpg` | plastificado carné |
| 7 | Encuadernación en espiral | Encuadernación | `serv07.jpg` | encuadernación espiral |
| 8 | Revelado de fotos 4x6 | Fotografía | `serv08.jpg` | revelado fotos 10x15 |
| 9 | Escaneo de documento | Digitalización | `serv09.jpg` | escáner documento |
| 10 | Corte de cabello | Barbería | `serv10.jpg` | corte de cabello barbería |
| 11 | Afelpado de barba | Barbería | `serv11.jpg` | recorte de barba |
| 12 | Manicure | Belleza | `serv12.jpg` | manicure uñas |
| 13 | Tinte de cabello | Belleza | `serv13.jpg` | tinte de cabello |
| 14 | Reparación de uña | Belleza | `serv14.jpg` | esmaltado de uñas |
| 15 | Lavado de ropa por libra | Lavandería | `serv15.jpg` | lavandería ropa |
| 16 | Planchado de prenda | Lavandería | `serv16.jpg` | planchado ropa |
| 17 | Entrega a domicilio | Domicilio | `serv17.jpg` | entrega a domicilio moto |
| 18 | Carga de batería de móvil | Telefonía | `serv18.jpg` | cargador móvil batería |
| 19 | Instalación de aplicación | Telefonía | `serv19.jpg` | instalación app móvil |
| 20 | Funda de regalo | Regalos | `serv20.jpg` | funda regalo |

## Cómo añadir las fotos al teléfono

Las fotos van al directorio privado de la app (requiere `run-as`):

```bash
# Ejemplo para una foto (ajusta la ruta local y el número)
adb push prod01.jpg /data/local/tmp/
adb shell run-as cu.shalev.app.debug cp /data/local/tmp/prod01.jpg files/fotos/prod01.jpg
```

O un lote desde una carpeta local `fotos_seed/`:

```bash
for f in fotos_seed/prod*.jpg fotos_seed/serv*.jpg; do
  n=$(basename "$f")
  adb push "$f" /data/local/tmp/"$n"
  adb shell run-as cu.shalev.app.debug cp /data/local/tmp/"$n" files/fotos/"$n"
done
```

Después, vuelve a lanzar el seed para que las fotos se asocien:

```bash
adb shell am start -n cu.shalev.app.debug/cu.shalev.app.seed.SeedActivity
```

## Notas

- Prioriza **Wikimedia Commons** (licencias claras, reutilizables).
- Las imágenes se recortan/re-codifican por el camino de fotos del usuario (JPEG ≤1024 px, sin EXIF), así que sirve cualquier foto razonable.
- Si falta alguna, el seed continúa sin error: ese producto/servicio simplemente queda sin foto.
