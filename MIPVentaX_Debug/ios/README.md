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

## Uso en Xcode / Simulador iOS

Puedes cargar este archivo de datos en el simulador o importarlo mediante la pantalla de **Respaldo** en modo debug para poblar la aplicación instantáneamente.
