# Shalev — reglas para agentes (OpenCode)

App Android de punto de venta e inventario para pequeños negocios en Cuba. La app principal se mantiene en Android; `IShalev/` es una contraparte nueva, nativa en SwiftUI, con proyecto Xcode separado y alcance de paridad descrito en `IShalev/README.md`. Los antiguos proyectos Flutter/iOS y web se retiraron el 2026-10-15. **Versión Android declarada 1.0.1 (`versionCode 54`)**; la Release `v1.0` (`versionCode 53`) se publicó el 2026-10-22; el número de trabajo anterior era 0.32.0/52 (nunca publicado con ese número, y la 0.30.0/51 tampoco). El contrato de licencias GL vigente está resumido en `LICENSE_CLIENT.md` §10–§11. Paquete Android `cu.shalev.app`. Las reglas específicas de Kotlin/Gradle de este archivo se aplican al código Android; el proyecto iOS se compila con Xcode. Todo el texto de las interfaces, los comentarios y la documentación están en **español**; responde en español.

**Empieza por `Contexto.md`** (resumen del proyecto), `Pendiente.md` (lo que falta) y `Pruebas.md` (qué probar y en qué orden). La referencia visual es `Shalev_0.26.0_capturas_y_exportaciones.pdf` (raíz; no se sube a git). Guía para compilar con OpenCode Desktop (herramienta de desarrollo; no es una app Shalev): `docs/OPENCODE_DESKTOP.md`. Lee los demás documentos solo cuando la tarea los necesite:
- arquitectura, compilación y novedades: `README.md`;
- release firmado y pruebas en el teléfono: `RELEASE.md`;
- archivos que escribe la app (respaldo, PDF, Excel, PNG): `FORMATOS.md`;
- seguridad y permisos: `SECURITY.md`;
- componentes, colores y tipografía: `DESIGN_SYSTEM.md`, `UI_UX_IX.md`;
- capturas Roborazzi: `CAPTURAS.md`;
- licencias (cliente GL): `LICENSE_CLIENT.md`, `docs/GL_ESPECIFICACION.md`;
- registro de decisiones de cada versión: `docs/HISTORIAL_DESARROLLO.md`.

## Pila

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.11.1 (wrapper) · JDK 17 · compileSdk/targetSdk 35 · minSdk 26 · Compose BOM 2024.12.01 (Material 3) · Hilt 2.52 (KSP) · Room 2.6.1 + SQLCipher 4.6.1 · OkHttp + kotlinx.serialization · fastexcel · ML Kit (QR de vinculación) · Robolectric 4.14.1 + Roborazzi 1.38.0. Versiones en `gradle/libs.versions.toml`.

## Módulos (Clean Architecture)

| Módulo | Contenido | Depende de |
|---|---|---|
| `:core` | Utilidades puras (dinero `Cup`, fechas, `Resultado`) | — |
| `:licencia` | Cliente de licencias GL (formatos `SPVIR1:` / `SPVI2:`, envelope v1, revocaciones, clave maestra de depuración y sub-tokens de las secundarias) | core |
| `:domain` | Modelos, repositorios (interfaces), casos de uso, `TablasExport` | core, licencia |
| `:data` | Room, SQLCipher, respaldo, sincronización LAN, exportadores (PdfWriter, XlsxWriter, `DisenoPdf`) | domain |
| `:designsystem` | Tema, `Shalev*` componentes, `ShalevIcons`, `ShalevSpacing` | — |
| `:app` | Pantallas Compose, ViewModels Hilt, navegación | todos |

Reutiliza repositorios y casos de uso existentes; no metas lógica de negocio en las pantallas.

## Comandos

```bash
./gradlew shalevTests                    # todos los tests JVM (sin dispositivo ni red)
./gradlew :app:assembleDebug           # APK debug
./gradlew shalevCheck                    # antes de entregar: tests + lintDebug + assembleDebug + shalevPermisos
./gradlew :app:recordRoborazziDebug    # capturas reales (claro y oscuro)
./gradlew shalevInstrumentedTests        # emulador o teléfono API 26+
./gradlew shalevRelease                  # AAB + APK firmados (requiere keystore.properties, ver RELEASE.md)
```

Un solo test: `./gradlew :domain:test --tests "cu.shalev.domain.Version025Test"`. Sin Android SDK queda `bash tools/verificacion/verificar.sh` (descarga kotlinc y compila sin Gradle; ~15 min).

## Reglas que no se negocian (decididas por el dueño del proyecto)

1. **Permisos:** solo `CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` y, solo para el registro de la prueba (0.26.0), `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 32) y `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion` 28) y, solo para el acceso con clave opcional (0.27.0), `USE_BIOMETRIC` y `USE_FINGERPRINT` (los añade `androidx.biometric`). `shalevPermisos` falla con cualquier otro. Nada de WAKE_LOCK, arranque, batería ni otro uso del almacenamiento.
2. **Sin** Firebase, analítica, telemetría, anuncios ni servidores propios. INTERNET solo para: sincronización en la red local, consultas de códigos de barras, y GitHub (actualizaciones y lista de revocadas).
3. **No se vende sin turno abierto.** La cámara solo mientras se usa. SMS solo pegando o compartiendo (nunca leer SMS ni el portapapeles en segundo plano).
4. **UI:** Material 3 claro/oscuro, contraste WCAG AA (`ContrastTest`), sin estilos sueltos: usa `Shalev*` del `:designsystem`. `ShalevSpacing` tiene xs=8, md=16, lg=24 (**no existe `sm` ni `xl`**). Botones de solo icono con descripción. Diálogos centrados. Guardar = ✓. Gráficos sin líneas de rejilla. Asistentes de 3–4 pasos.
5. **Datos sensibles:** nada en logs ni en Recientes (`FLAG_SECURE` en Licencia, Perfil, QR y datos del cliente). El CI sale enmascarado (`••••••••345`) en los textos compartidos.
6. **Licencia:** no copies el almacenamiento de GL ni expongas detalles internos. El teléfono activa **solo** la licencia corta `SPVI2:` y, **solo en builds de depuración**, la clave maestra `GLMK-PERM-MAXS-9999` (mitigación 1: en release siempre vale `false`). Una licencia **larga** (envelope v1) no se activa desde la 0.23.0: si quedó instalada en un teléfono se sigue re-verificando. La cuota de secundarias la firma la principal (sub-token, mitigación 7): la secundaria comprueba el permiso y, sin él (principal anterior), acepta la licencia como antes. Detalle en `LICENSE_CLIENT.md` §10–§11.
7. **Documentación sin funciones inventadas.** Si cambias comportamiento, actualiza README/MANUAL_USUARIO/FORMATOS y añade una entrada en `docs/HISTORIAL_DESARROLLO.md`.
8. Tests sin red. Todo lo que añadas debe compilar y pasar `shalevTests`.

## Trampas conocidas

- Hilt: los constructores `@Inject` **no** admiten parámetros por defecto. Si cambias el constructor de un ViewModel, actualiza `TurnoViewModelsTest`, `PerfilTest` y `EntornoIntegracion.kt`.
- Kotlin: una entrada de `enum` no puede usar una `const` de su propio companion; `CompositionLocal.current` no se puede leer dentro del DSL de `LazyColumn`; `shalevAnimateItem()` solo dentro de `LazyItemScope`.
- KSP: si falla Room/Hilt de forma rara, borra `build/generated/ksp` del módulo y repite.
- Categoría de elaborados: `Categorias.ELABORADO = "Elaborado"` (singular).
- Excel: el formato de importe es `#,##0.00 \C\U\P` (con comillas fastexcel genera un `styles.xml` inválido).
- Base de datos v12 (`MIGRACION_11_12` añade `empleado.device` para el sub-token de las secundarias; esquema `data/schemas/cu.shalev.data.db.ShalevDatabase/12.json`); respaldo DTO v4 (importa v3); archivo `.shalev` v4 (lee v3). Un cambio de esquema exige migración + esquema exportado + test. **El `12.json` lo escribe Room al compilar**: si falta, `tools/verificacion/` y el CI lo avisan; no se inventa a mano.
- Firma de los sub-tokens: clave P-256 propia de la principal (`FirmanteLicenciaAndroid`, Keystore `PURPOSE_SIGN`, alias `spvi_licencia_firma_v1`), distinta de la clave ECDH del equipo y que **nunca** se borra (tampoco al desvincular).
- La firma de release debe ser **siempre la misma** (el `deviceId` de la licencia depende de ella).

## Estado de verificación

El estado medido se mantiene en [docs/VERIFICACION.md](docs/VERIFICACION.md). El CI de `main` volvió a verde el 2026-10-18 (run `39176254439`, commit `6959eb1`): permisos del manifiesto, API mínima 26, lint, tests JVM y compilación de instrumentados, release R8 y APK debug. El fallo anterior (run `38802995010`, `84afc87`) era el SDK instrumentado de Robolectric; `app/build.gradle.kts` lo prepara con Gradle y los tests JVM de `:app` corren en modo offline. `shalevTests` cuenta **883** casos JVM en verde —19 `:core` + 114 `:licencia` + 229 `:domain` + 93 `:data` + 34 `:designsystem` + 394 `:app`; los 137 `@Test` de `app/src/test/.../capturas/` **no** entran: `shalevTests` los excluye y los graba `recordRoborazziDebug`— y `shalevCheck` pasa en local (hay JDK 17 y Android SDK instalados en este equipo; `./gradlew` funciona).

El CI no ejecuta de forma ordinaria las capturas Roborazzi ni los tests instrumentados en un teléfono/emulador. Tampoco prueba la app instalada, la sincronización entre dos teléfonos ni la firma de release. Antes de publicar, sigue `Pruebas.md`, `PRUEBAS_DISPOSITIVO.md` y `RELEASE.md`.

**2026-10-21 (0.32.0).** Los instrumentados **sí** se ejecutaron en el teléfono 4bc9f8f3 (`shalevInstrumentedTests`): 61 de `:data` y 61 de `:app`. En `:data` fallaron dos pruebas del lote que nunca se habían ejecutado en un dispositivo (el CI solo las compila): una contaba mal los bytes de una foto de prueba (15 por 16) y otra usaba la semántica antigua del conteo (contar todo) en vez de la nueva (conteo **parcial**: solo lo que cambió durante el turno). Se corrigieron las dos pruebas; la app no cambió. `shalevTests` sigue con **883** casos JVM en verde, `:app:verifyRoborazziDebug` con **274** capturas en verde y `shalevCheck` en verde. **Pendiente para publicar:** el keystore de la app (`spvi-release.jks`, alias `shalev`) **no está en este equipo** —solo está el de GL (`~/.android/gl-release.jks`, alias `gl`), que es el del emisor de licencias y **no** sirve para firmar la app. Sin esa clave no hay APK firmado: usa la misma de siempre o ninguna, porque cambiar la firma anula las licencias ya emitidas y obliga a desinstalar.
