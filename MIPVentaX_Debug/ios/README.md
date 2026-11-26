# MIPVentaX iOS — Entorno Debug y Seed de Datos

Esta carpeta contiene el archivo de datos de prueba para **MIPVentaX iOS** con el modelo gastronómico de 2 años de historial pre-cargado.

## Archivos incluidos

- `mipventax_debug_seed.json`: Documento JSON serializado conforme a `AppData` (versión de esquema 2) de `IShalev` / `MIPVentaX iOS`, con:
  - 3 locales configurados
  - 20 productos de cafetería, bebidas, coctelería y alimentos
  - 12 insumos de cocina y bar
  - 3 servicios especiales
  - 4 empleados y 4 clientes fijos
  - 730 días de turnos y más de 9,400 ventas detalladas
- `sembrar_ios_debug.py`: Generador en Python reproducible para construir o regenerar los datos.

## Forma del JSON (importante al regenerarlo)

El archivo sigue **exactamente** los modelos de `iOS/Sources/Domain/Domain.swift`, porque `AppData` se decodifica con
`Codable` y una clave con otro nombre se pierde en silencio (o rompe la carga entera):

- `settings` es un `AppSettings`: los datos del negocio van dentro de **`settings.profile`**, no sueltos.
- `locations` son `BusinessLocation` y **no llevan `id`**: la identidad del local es su nombre.
- Cada línea de venta es una `SaleLine`: `sourceID`, `kind` (`product`/`service`), `name`, `category`, `quantity`,
  `basePriceCents`, `unitPriceCents`, `unitCostCents`.
- Los enums se serializan por su valor: `paymentMethod` es `Efectivo`/`Transferencia` y el tipo de movimiento de caja
  es `Entrada`/`Salida`.
- Los servicios son `ServiceItem` (`type` y `description`, sin costo; el consumo va en `uses`).
- En `settings.priceAdjustments` hay dos preajustes de ejemplo, uno de ellos con `productIDs` de los cócteles.

Lo comprueba `Web-Desktop/tests/test_semillas_debug.py`; si el modelo cambia, esa prueba avisa antes de que el
simulador cargue un archivo viejo.

## Uso en Xcode / Simulador iOS

Puedes cargar este archivo de datos en el simulador o importarlo mediante la pantalla de **Respaldo** en modo debug para poblar la aplicación instantáneamente.
