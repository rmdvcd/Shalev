# MIPVentaX — Suite de Depuración con Datos Semilla (Seed)

Esta carpeta contiene las **3 aplicaciones de MIPVentaX** (Android, Web-Desktop e iOS) configuradas para ejecución en **modo debug** con el mismo nivel de completitud de seed de la app Android, pero con un modelo de negocio y valores completamente distintos:

---

## 🍽️ Modelo de Negocio de Prueba: "Gourmet Habana Bistro & Café"

A diferencia del conjunto de datos original (orientado a bodega y víveres secos), este seed representa un negocio gastronómico y de coctelería real con alta rotación:

- **3 Locales independientes:**
  1. `Bistro Principal (Plaza)` (Sede central con salón climatizado)
  2. `Sucursal Malecón (Vedado)` (Punto costero de coctelería y tapas)
  3. `Kiosco Gourmet (Miramar)` (Punto express de café y bocadillos)
- **Catálogo de Artículos:**
  - 20 productos comerciales (Espresso, Cappuccino, Sándwiches Cubanos, Pizzas, Mojitos, Daiquirís, Cervezas Cristal/Bucanero, etc.)
  - 12 insumos para recetas y trazabilidad (Café arábica, leche fresca, queso gouda, jamón serrano, ron añejo, hierbabuena, etc.)
  - Elaborados con recetas y descuento atómico de insumos
  - 4 servicios para eventos y catering
- **Historial Completo:**
  - **2 años de operación (730 días continuos)**
  - 730 turnos de caja con aperturas, fondos, entradas/salidas y arqueos de cierre
  - Más de 11,000 ventas registradas con detalle por línea, repartidas entre efectivo y transferencias
- **Usuarios y Empleados:**
  - Administrador: Carlos Martínez (PIN: `112233`)
  - Cajera Principal: Laura Valdés (PIN: `123456`)
  - Barista / Dependiente: Dayana Gómez (PIN: `246810`)
  - Encargado: Marcos Fonseca (PIN: `654321`)
- **Clientes Fijos Registrados:**
  - Pedro Morales (CI: `85041212345`)
  - Beatriz Vega (CI: `91072334567`)
  - Javier Fernández (CI: `78110556789`)
  - Marleny Peña (CI: `95021878901`)

---

## 📂 Contenido por Plataforma

### 1. `android/`
- `mipventax_debug.db`: Base de datos SQLite generada bajo el esquema Room v21 (`ShalevDatabase`). Lista para transferir con `adb push` al emulador o dispositivo Android.
- `generar_db_android.py`: Script generador de la base SQLite.
- `SembradorDebugDistinto.kt`: Código fuente en Kotlin para siembra programática.

### 2. `web_desktop/`
- `DShalevData/dshalev.db`: Base de datos pre-sembrada para PC.
- `iniciar_debug.sh` / `iniciar_debug.bat`: Lanzadores directos para iniciar el servidor local en `http://127.0.0.1:47812` (Usuario: `admin`, Clave: `Administrador123*`).
- `sembrar_web_debug.py`: Script generador de la base SQLite de escritorio.

### 3. `ios/`
- `mipventax_debug_seed.json`: Documento JSON estructurado según `AppData` (v2) de SwiftUI para importar o inicializar el simulador/dispositivo.
- `sembrar_ios_debug.py`: Script generador del seed JSON.
