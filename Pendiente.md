# Pendiente de Shalev

## Alcance vigente

El producto mantenido en este repositorio es la app Android nativa (`:app`, `:designsystem`, `:data`, `:domain`, `:licencia`, `:core`). La app Flutter/iOS y la aplicación web de escritorio se retiraron el 2026-10-15; sus pruebas y pipelines ya no forman parte del proceso. La sincronización local entre teléfonos Android, las licencias GL y el formato `.shalev` siguen activos.

La versión del código es **0.30.0 (`versionCode 51`)**. Los cambios Android en curso aún no son un release: el de editar importe, cantidad, método y nº en Registros se rotula **0.31.0** (commiteado como `54c85f9`).

## Verificación pendiente

- Cierre con billetes/conciliación (rama `arena/5c955caa-shalev`, ya en `main`): `13.json` está versionado desde `54c85f9`; en este entorno compila y sus tests JVM pasaron dentro de `shalevTests` (891 en verde, 2026-10-17). Faltan por confirmar los instrumentados de migración 12→13 y probar en dos teléfonos actualizados la aprobación/rechazo, el conteo desfasado, la desconexión y el reenvío.

- El último CI observado antes de esta limpieza, run [38802995010](https://github.com/rmdvcd/Shalev/actions/runs/38802995010) sobre `main` (`84afc87`), falló en dos pruebas de `ProcesoActualizarTest` al no poder descargar el SDK de Robolectric desde el proceso de tests. Lint, permisos y API mínima sí pasaron. `app/build.gradle.kts` prepara ese SDK con Gradle y los tests JVM de `:app` corren en modo offline; el CI volvió a verde el 2026-10-18: run [39176254439](https://github.com/rmdvcd/Shalev/actions/runs/39176254439) sobre `6959eb1` (permisos, API mínima, lint, tests JVM y compilación de instrumentados, release R8 y APK debug). Los instrumentados en emulador no corren de forma ordinaria.
- `./gradlew shalevCheck` ejecutado el 2026-10-17 con JDK 17 y Android SDK 35: en verde (tests JVM, lint, compilación y permisos). `./gradlew shalevTests`: 891 tests JVM en verde. La corrida de CI de la rama ya se hizo (ver arriba) y `shalevCheck` volvió a pasar el 2026-10-18; tras retirar el OCR y aplicar la auditoría de UI el 2026-10-19, `shalevTests` mide **905** tests JVM.
- Capturas Roborazzi ejecutadas el 2026-10-17: 525 capturas, **1 fallo de comparación** en `01f_inicio_dialogo_cerrar_turno.png`, que es del cambio de cierre con conteo ya en `main` (no regenerada aquí); ninguna captura de Registros cambia. Falta decidir si esa captura se regenera con el cambio de cierre. El 2026-10-18 `verifyRoborazziDebug` quedó en **527 capturas en verde**.
- Publicar los cambios en curso (editar Registros, retirada del OCR y auditoría de UI, 0.31.0): correr CI, subir y regenerar capturas tras decidir lo anterior.

## Auditoría UI · UX · IX (2026-10-28)

Informe: [docs/AUDITORIA_UI_UX_IX_2026-10-28.md](docs/AUDITORIA_UI_UX_IX_2026-10-28.md). Los bloqueantes y altos de IShalev ya están corregidos en esta rama (Dynamic Type en las 483 tipografías, área táctil de 44 pt en 19 controles, Reduce Motion, Reduce Transparency, vibración y etiquetas de VoiceOver).

**`main` estaba en rojo y esta rama lo arregla.** El cambio de textos de 1.0.1 (2026-10-27) se hizo sin poder ejecutar los tests y dejó **dos** pruebas rotas, las dos arregladas aquí:

1. Un paso de ayuda de 136 caracteres en `app/ayuda/AyudaContenido.kt:173`, cuando `AyudaSoporteTest` exige ≤ 110. Se reescribió a 110 conservando la etiqueta real del botón y las tres redes (wifi, zona wifi y datos). El CI ya no reporta ese fallo. **Regrabar `app/capturas/claro/07g_ayuda.png` y `app/capturas/oscuro/07g_ayuda.png`**, que ya estaban pendientes por esos mismos textos de 1.0.1.
2. `ProcesoActualizarTest.sinConexionNoSeCierraSolo()` devolvía un valor (su última expresión es `withTimeout { first { … } }`), así que el método no era `void` y JUnit 4 rechazaba la clase entera: `initializationError`, y sus cuatro tests nunca corrían. Se añadió `runBlocking<Unit>`. **Ojo:** al correr de verdad por primera vez desde 1.0.1 pueden aparecer fallos reales de comportamiento que estaban ocultos tras el error de inicialización.

`main` fallaba en las dos desde el commit base `d125513` (run de CI `40437981165`, PR #63).

Queda:

- **Compilar IShalev.** Este entorno no tiene Xcode/Swift: los cambios no se compilaron aquí. Correr `swift test --package-path IShalev` y `xcodebuild -project IShalev/IShalev.xcodeproj -scheme IShalev -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO build` (lo hace el CI en macOS), y revisar la app con VoiceOver, en Modo Oscuro y con letra grande.
- **IX-013 (Alta, IShalev):** portar el reflow de Android (`letraGrande()`) para las categorías de accesibilidad AX2–AX5 y quitar el tope de 1,6× en `ShalevDynamicType.escalaMaxima`.
- **AND-001 (Alta, Shalev):** casilla de selección a 48 dp (`size(ShalevSize.touchTarget)` conservando el dibujo de 16 dp) y **regrabar** las capturas de Inventario, Servicios, Registros, Clientes y Pago electrónico. Pendiente de decisión del dueño desde 0.32.0.
- **IX-012 (Media, IShalev):** `@FocusState` + `.submitLabel(.next/.done)` + `.textContentType` en los formularios, para el teclado que avanza y el autocompletado que Android sí tiene.
- **AND-002 (Media, Shalev):** decidir si los permisos de la primera apertura siguen en lote o pasan a petición just-in-time.
- **UX-014 / AND-003 (Media, las dos):** decidir si se añade `pull to refresh` en las pantallas con sincronización LAN (en el resto no hay nada que refrescar de la red).

## Validación en dispositivos antes de publicar

- Instrumentados de Room: `:data:RepositoriosRoomTest` 9/9 y `:data:TurnoRepositoryInstrumentedTest` 8/8 en verde el 2026-10-17 en el teléfono 4bc9f8f3 (Android 15, API 35). Faltan los instrumentados de UI de `:app`.
- Comprobar en dos teléfonos Android la vinculación principal/secundaria, permisos, sincronización y desconexión/reconexión por la red local.
- Probar activación y revalidación con GL real, incluida una licencia envelope v1 heredada si está disponible.
- Crear e importar un respaldo `.shalev` con y sin contraseña; probar también lectura de un respaldo compatible anterior.
- Firmar el APK/AAB con el keystore de release, verificar firma y ejecutar la prueba de humo descrita en [RELEASE.md](RELEASE.md).

## Al cerrar

Actualizar [docs/VERIFICACION.md](docs/VERIFICACION.md) con el SHA, el resultado de CI, las pruebas de dispositivo realmente ejecutadas y cualquier limitación conocida. No declarar verde una comprobación que no se haya ejecutado.
