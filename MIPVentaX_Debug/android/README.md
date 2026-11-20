# MIPVentaX Android — Entorno Debug y Seed de Datos

Esta carpeta contiene la aplicación y base de datos de depuración para **MIPVentaX Android** con un modelo de negocio diferente al seed original de víveres/bodega:

- **Negocio:** Gourmet Habana Bistro & Café
- **Locales:**
  1. `Bistro Principal (Plaza)` (Sede central)
  2. `Sucursal Malecón (Vedado)`
  3. `Kiosco Gourmet (Miramar)`
- **Catálogo:** 20 productos (cafetería, bebidas, coctelería, alimentos, dulcería), 12 insumos para recetas, 4 servicios para eventos.
- **Historial:** 2 años completos (730 días) con 730 turnos de caja, más de 11,000 ventas registradas con detalle, fondo de caja, arqueos y movimientos de efectivo.
- **Usuarios de prueba:**
  - Administrador: Carlos Martínez (PIN `112233`)
  - Cajera: Laura Valdés (PIN `123456`)
  - Dependiente: Dayana Gómez (PIN `246810`)
  - Encargado: Marcos Fonseca (PIN `654321`)

## Archivos incluidos

- `mipventax_debug.db`: Archivo SQLite compatible al 100% con Room v21 y `ShalevDatabase`. Para usarlo en un emulador o dispositivo Android, puedes copiarlo directamente con ADB:
  ```bash
  adb push mipventax_debug.db /data/data/cu.shalev.app/databases/shalev.db
  ```
- `generar_db_android.py`: Generador reproducible que lee el esquema JSON oficial de Room (`21.json`) y produce la base de datos completa.
- `SembradorDebugDistinto.kt`: Especificación en Kotlin para inicialización programática.
