# Capturas de pantalla (claro y oscuro)

Capturas **reales** de la interfaz de Shalev generadas en la JVM con **Robolectric 4.14.1 + Roborazzi 1.38.0**: sin emulador, sin teléfono y sin red. Cada caso pinta un `*Content` sin estado (los mismos que usan los tests instrumentados) con datos de ejemplo y guarda dos imágenes con el mismo nombre, una en tema claro y otra en oscuro.

| | |
|---|---|
| Código | `Android/app/src/test/kotlin/cu/shalev/app/capturas/` (7 clases + `Capturas.kt`) |
| Casos | **137 métodos `@Test`**, con 137 PNG por tema (**274 PNG** en total) |
| Salida | `Android/app/capturas/claro/ID.png` y `Android/app/capturas/oscuro/ID.png` |
| Pantalla de referencia | 360 × 800 dp a xhdpi (720 × 1600 px). Formularios y pantallas largas: 360 × 1600 dp |
| Tema | `ShalevTheme(darkTheme = false/true)`; se cambia sin recrear la actividad |

## 1. Generarlas

Desde la raíz del proyecto:

```bash
./gradlew :app:recordRoborazziDebug
```

- Android Studio: panel **Gradle → Shalev → app → Tasks → roborazzi → recordRoborazziDebug**.
- Las imágenes quedan en `Android/app/capturas/{claro,oscuro}/`. Informe HTML: `Android/app/build/reports/roborazzi/index.html`.
- Solo una clase: `./gradlew :app:recordRoborazziDebug --tests "cu.shalev.app.capturas.VentaCapturas"`.
- Solo un caso: `--tests "cu.shalev.app.capturas.VentaCapturas.qr"`.

La **primera** ejecución descarga con Gradle las dependencias de test (Robolectric, Roborazzi y el Android 15 preinstrumentado de Robolectric, ~150 MB). Después todo funciona sin conexión.

### Comparar contra las guardadas

Con las imágenes ya grabadas, para detectar cambios visuales:

```bash
./gradlew :app:compareRoborazziDebug   # genera *_compare.png con las diferencias marcadas
./gradlew :app:verifyRoborazziDebug    # falla si alguna pantalla cambió
```

## 2. Cómo está montado

| Pieza | Dónde | Por qué |
|---|---|---|
| Plugin `io.github.takahirom.roborazzi` 1.38.0 | `Android/build.gradle.kts` (apply false) y `Android/app/build.gradle.kts` | Añade las tareas `record/compare/verifyRoborazziDebug`. No toca el APK. |
| `testImplementation`: robolectric, roborazzi, roborazzi-compose, ui-test-junit4, androidx.test.ext.junit | `Android/app/build.gradle.kts` | Solo tests: **no entran en el APK** (restricción P18 «sin dependencias nuevas sin justificar»: son de test y la justificación es esta tarea). |
| `unitTests.isIncludeAndroidResources = true` | `Android/app/build.gradle.kts` | Proporciona a Robolectric los recursos Android que necesitan las capturas y los tests JVM de `:app`; no entra en el APK. |
| Configuración `robolectricSdk` + tarea `prepararRobolectricSdk` | `Android/app/build.gradle.kts` | Gradle resuelve `android-all-instrumented:15-robolectric-12650502-i7` (SDK 35), lo copia a `Android/app/build/robolectric-sdk` y configura ese directorio con `robolectric.offline=true` para todos los tests JVM de `:app`. La descarga ocurre durante la resolución de Gradle, no desde el proceso de tests; se mantiene la regla de **tests sin red**. |
| Exclusión `**/capturas/**` | `Android/app/build.gradle.kts` | En `test`, `testDebugUnitTest`, `shalevTests` y `shalevCheck` las capturas **no se ejecutan** (son lentas y no validan lógica). Solo corren cuando el nombre de la tarea pedida contiene «Roborazzi». |
| `@Config(application = Application::class)` | cada clase | No arranca `ShalevApplication` (Hilt, SQLCipher, Keystore): las pantallas se pintan sin datos reales ni claves. |
| `captureScreenRoboImage` | `Capturas.kt` | Captura toda la pantalla, así los diálogos y hojas (que viven en su propia ventana) también salen. |

## 3. Qué se captura

Cada caso asigna un ID estable a su captura. Los 137 IDs de las pruebas coinciden uno a uno con los nombres de los PNG versionados en cada tema. El inventario y los recuentos de esta sección describen el código fuente actual; no incluyen maquetas antiguas.

| Clase | Casos | Contenido |
|---|---|---|
| `InicioCapturas` | 14 | Inicio con datos (normal y largo), acordeones cerrados, vacío, carga y error, alertas, diálogos de venta/cierre, snackbar, existencias negativas y cierre solicitado por la principal; incluye letra grande. |
| `InventarioCapturas` | 23 | Lista y estados de carga/vacío/error, filtros, selección, eliminación, ficha normal/elaborado, hojas de filtro/compartir/exportar y modo venta; formulario de producto nuevo/edición/errores, receta, insumos y salida sin guardar. No incluye el escáner OCR retirado de Android. |
| `ServiciosCapturas` | 5 | Lista, vacío, modo venta, ficha y formulario de servicio. |
| `RegistrosCapturas` | 25 | Ventas, transferencias, movimientos y turnos; estados de carga/vacío/error, fichas, filtros por vendedor, exportación y detalle de turno abierto/cerrado/sin actividad/error. |
| `VentaCapturas` | 24 | Venta sin turno, carritos de productos/servicios y transferencia, stock insuficiente, comprobante, QR, datos de cliente y salida; además, listas y formularios de Pago electrónico y Precios. |
| `AjustesCapturas` | 42 | Ajustes, Perfil, Licencia, Respaldo, Ayuda, Soporte y asistente inicial; incluye estados de prueba, importación/exportación, errores, arqueo y letra grande. |
| `VinculacionCapturas` | 4 | Principal con empleados, ficha de cobro/cierre, cierre solicitado y secundaria conectada. |

- `01j_inicio_acordeones_cerrados` (`InicioCapturas`, 0.30.1): Inicio tal como se ve al entrar, con los gráficos y listas cerrados. El resto de casos de Inicio los pasan abiertos (`acordeonesAbiertos = true`) para mostrar su contenido.

### Letra grande (0.27.0, T2)

Cuatro casos con `LetraGrande` (`Capturas.kt`: `fontScale = 2`, el máximo de Android 14+) para comprobar que nada se corta con «…» ni parte letras:

| ID | Clase | Pantalla |
|---|---|---|
| `01z_inicio_letra_200` | `InicioCapturas` | Inicio con datos (captura larga) |
| `02z_inventario_ficha_letra_200` | `InventarioCapturas` | Ficha de producto |
| `07z_licencia_letra_200` | `AjustesCapturas` | Licencia (prueba) |
| `07z2_caja_arqueo_letra_200` | `AjustesCapturas` | Caja / arqueo al cerrar |

Se usa un `CompositionLocalProvider(LocalDensity)` en vez de `@Config(fontScale)` porque no está claro que Robolectric aplique este último a Compose.

**Histórico de P79 (0.27.1):** aquella versión tenía 154 PNG por tema; no es el inventario actual (137 por tema). La revisión a letra al 200 % motivó estos ajustes, que se conservan en el diseño: alertas de Inicio adaptables, tarjetas Pago/Precios apilables, importes del arqueo que bajan de línea y subtítulos de Ajustes/Exportar de hasta tres líneas.

Límites del arnés:
- **Ventanas `Dialog`:** `LetraGrande` no llega a ellas, porque el `Dialog` crea su propia ventana con la densidad del sistema. Por eso `02z_inventario_ficha_letra_200` sale al 100 %. Los diálogos con letra grande solo se pueden comprobar en un teléfono (Ajustes → Pantalla → Tamaño de fuente al máximo).
- **Barra superior de la actividad de prueba:** la actividad de prueba hereda `Theme.Shalev.Splash`, que en Robolectric muestra una barra. `capturar()` la oculta para que no tape la parte de arriba de las capturas. En la app no aparece, porque `installSplashScreen()` pasa a `Theme.Shalev`.

## 4. Límites conocidos

- **Cámara y selectores del sistema**: no se representan en esta suite; las capturas montan los componentes de contenido y no abren actividades externas.
- **Barra de navegación inferior**: la pinta `MainScaffold`, no los `*Content`; en las capturas reales de Inicio, Inventario, Servicios, Registros y Ajustes no aparece.
- Los estados que dependen de interacción con teclado, calendario o un menú del sistema no están cubiertos por estas capturas; requieren una comprobación manual.
- **Fuentes**: Robolectric usa las fuentes de su Android 15; puede haber diferencias mínimas de interletra respecto a un teléfono.
- Los PNG versionados son referencias existentes, no capturas recién generadas para esta limpieza. En la verificación del 2026-11-13 no se ejecutó `recordRoborazziDebug` ni se compararon los baselines; ver [docs/VERIFICACION.md](docs/VERIFICACION.md).

## PDF histórico de maquetas y exportaciones (0.26.0)

La entrega `Shalev_0.26.0_capturas_y_exportaciones.pdf` se describió históricamente como un PDF de 127 páginas: portada e índice, pantallas 1–108 y exportaciones E1–E16. El archivo está excluido de git y **no está presente en este checkout**; ese material es de la versión 0.26.0, no una referencia vigente de la interfaz. Incluye maquetas de funciones retiradas de Android, entre ellas el escáner OCR.

Se retiró el generador aislado de aquellas maquetas/exportaciones: sus fuentes estaban dedicadas a esa entrega antigua, sin consumidores vigentes, y el ensamblaje necesitaba además un PDF previo 0.25.1 que no está en el repositorio. Los vectores de iconos que sí utiliza el generador actual de diagramas del manual se conservan en `tools/manual/iconos.json`.
