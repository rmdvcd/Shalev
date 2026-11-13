# Resultados de pruebas — PR #87 y #88 (rama `arena/**`)

Documento generado para que **arena.ai** revise la verificación pendiente de los dos últimos
pull requests de `rmdvcd/Shalev`.

- **Fecha:** 2026-11-03
- **Commit probado:** `1f0afd7` (`main`, merge del PR #88; incluye el PR #87)
- **Versión:** 1.0.1 (`versionCode 54`), paquete debug `cu.shalev.app.debug`
- **Dispositivo:** teléfono real `4bc9f8f3`, modelo `23021RAAEG`, Android 15 (API 35), MIUI `V816`
- **Entorno de compilación:** Windows, JDK 17.0.20.1, Gradle 8.11.1 (wrapper), Android SDK 35

## PR verificados

| PR | Rama | Contenido |
|---|---|---|
| #87 | `arena/4281cd6a-shalev` | Herramientas en la barra inferior y ficha de costo temporal en CUP/MLC |
| #88 | `arena/61e4047b-shalev` | Calculadora de cambio CUP/USD |

Los dos PR declaraban pendiente «compilar Android y ejecutar las pruebas UI». Esa verificación
es la que se completa aquí.

## 1. Compilación

```bash
./gradlew :app:assembleDebug
```

**Resultado: BUILD SUCCESSFUL en 5 m 59 s.** Sin errores; solo los avisos que ya existían
(`CajaUi.kt:394`, opt-in de `FlowPreview` en tres ViewModels).

APK generado: `Android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
(además de `universal`, `armeabi-v7a` y `x86_64`).

## 2. Instalación y arranque

```bash
adb install -r Android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell am start -n cu.shalev.app.debug/cu.shalev.app.MainActivity
```

**Resultado:** instalación correcta, `MainActivity` en primer plano y proceso activo.
La barra inferior muestra las cinco secciones: Inicio, Inventario, Servicios, Registros y
Herramientas.

> Las pruebas instrumentadas lanzadas con Gradle desinstalan la app al terminar
> (`uninstall_after_test`). Tras la ejecución se reinstaló y se volvió a abrir; el teléfono
> queda con la app instalada y en ejecución, pero **sin los datos previos** (la desinstalación
> los borra).

## 3. Pruebas JVM (`shalevTests`)

```bash
./gradlew shalevTests
```

**Resultado: BUILD SUCCESSFUL en 8 m 52 s, 0 fallos y 0 errores.**

En esta ejecución Gradle volvió a ejecutar `:app`, `:data`, `:designsystem` y `:domain`;
`:core` y `:licencia` quedaron al día y se reutilizaron sus informes.

| Módulo | Casos | Fallos | Errores | Omitidos | Reejecutado ahora |
|---|---:|---:|---:|---:|---|
| `:app` | 412 | 0 | 0 | 0 | sí |
| `:core` | 19 | 0 | 0 | 0 | no (al día) |
| `:data` | 96 | 0 | 0 | 0 | sí |
| `:designsystem` | 34 | 0 | 0 | 0 | sí |
| `:domain` | 239 | 0 | 0 | 0 | sí |
| `:licencia` | 113 | 0 | 0 | 0 | no (al día) |
| **Total** | **913** | **0** | **0** | **0** | 781 reejecutados |

No se cuentan los `@Test` de `Android/app/src/test/.../capturas/` (Roborazzi), que `shalevTests`
excluye por diseño.

### Pruebas nuevas o modificadas por los PR

| Prueba | Casos | Resultado |
|---|---:|---|
| `cu.shalev.domain.fichacosto.FichaCostoTest` (nueva) | 4 | 4/4 en verde |
| `cu.shalev.domain.fichacosto.CalculoCambioTest` (nueva) | 4 | 4/4 en verde |
| `cu.shalev.app.navigation.DeslizarTest` (modificada) | 6 | 6/6 en verde |
| `cu.shalev.app.soporte.AyudaSoporteTest` (modificada) | 3 | 3/3 en verde |

Las ocho pruebas de dominio nuevas coinciden con las que el PR #88 decía haber ejecutado de
forma aislada; aquí se ejecutaron además dentro de la suite Gradle completa.

## 4. Pruebas instrumentadas en el teléfono

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=cu.shalev.app.herramientas.CalculadoraCambioUiTest,cu.shalev.app.herramientas.FichaCostoUiTest \
  -Pandroid.testInstrumentationRunnerArguments.timeout_msec=60000
```

**Resultado: BUILD SUCCESSFUL en 30 s — 4 de 4 pruebas en verde, 0 fallos.**

| Prueba | Resultado |
|---|---|
| `CalculadoraCambioUiTest.calculaYUsaTasa` | ok |
| `CalculadoraCambioUiTest.tasaCeroMuestraError` | ok |
| `CalculadoraCambioUiTest.convierteEnAmbosSentidos` | ok |
| `FichaCostoUiTest.agregarPartidaYAdvertirAlSalir` | ok |

Informe JUnit: `Android/app/build/outputs/androidTest-results/connected/debug/TEST-23021RAAEG - 15-_app-.xml`
(`tests="4" failures="0" errors="0"`).

Además se ejecutaron las mismas cuatro pruebas directamente con `am instrument`
(`OK (4 tests)` en 14,3 s) como comprobación independiente del informe de Gradle.

## 5. Incidencias del entorno (no de la app)

1. **Primer intento de instrumentados interrumpido por el límite de 10 minutos del terminal.**
   El proceso de UTP (`com.google.testing.platform.launcher.Launcher`) quedó vivo y bloqueaba
   `Android/app/build/outputs/androidTest-results`; Gradle fallaba después con
   `Failed to create MD5 hash for file content`. Se terminó el proceso huérfano, se borró esa
   salida de build y la ejecución posterior terminó correctamente.
2. **MIUI bloqueó el arranque de la actividad de prueba** desde la instrumentación
   (`MIUILOG- Permission Denied Activity`, `result code=102`), mientras que el mismo intento
   desde `adb shell am start` funcionaba (`result code=0`). Tras descartar el proceso residual,
   la instrumentación volvió a lanzar la actividad y las cuatro pruebas pasaron.
3. **Primera reinstalación rechazada** con `INSTALL_FAILED_USER_RESTRICTED`; el reintento
   inmediato instaló sin intervención manual.

Ninguna de estas incidencias requirió cambios en el código de la app.

## 6. Alcance no cubierto

- El resto de pruebas instrumentadas de `:app` (57 casos) y de `:data` (61 casos), que no
  forman parte de lo pendiente en estos PR.
- Las capturas Roborazzi (`recordRoborazziDebug` / `verifyRoborazziDebug`).
- `shalevCheck` completo, lint, compilación de release y firma.
- Las pruebas manuales de `Pruebas.md` y `PRUEBAS_DISPOSITIVO.md` (sincronización entre dos
  teléfonos, instalación de actualización, etc.).

## 7. Reproducción

```bash
# 1. Compilar
./gradlew :app:assembleDebug

# 2. Instalar y abrir
adb install -r Android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell am start -n cu.shalev.app.debug/cu.shalev.app.MainActivity

# 3. Suite JVM
./gradlew shalevTests

# 4. Pruebas de interfaz de Herramientas en el teléfono
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=cu.shalev.app.herramientas.CalculadoraCambioUiTest,cu.shalev.app.herramientas.FichaCostoUiTest

# 5. Dejar la app instalada otra vez (UTP la desinstala al terminar)
adb install -r Android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell am start -n cu.shalev.app.debug/cu.shalev.app.MainActivity
```
