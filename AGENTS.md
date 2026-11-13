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

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.11.1 (wrapper) · JDK 17 · compileSdk/targetSdk 35 · minSdk 26 · Compose BOM 2025.06.01 (ui/ATF 1.8.3 alineados) (Material 3) · Hilt 2.52 (KSP) · Room 2.6.1 + SQLCipher 4.6.1 · OkHttp + kotlinx.serialization · fastexcel · ML Kit (QR de vinculación) · Robolectric 4.14.1 + Roborazzi 1.38.0. Versiones en `gradle/libs.versions.toml`.

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
2. **Sin** Firebase, analítica, telemetría, anuncios ni servidores propios. INTERNET solo para: sincronización en la red local, consultas de códigos de barras, y GitHub (actualizaciones y lista de revocadas). **Excepción autorizada por el dueño (2026-11-05):** intermediario remoto Cloudflare opcional para sincronizar entre redes distintas, sin almacenar la base de datos del negocio; endpoint fijado en compilación, ver `relay/README.md`.
3. **No se vende sin turno abierto.** La cámara solo mientras se usa. SMS solo pegando o compartiendo (nunca leer SMS ni el portapapeles en segundo plano).
4. **UI:** Material 3 claro/oscuro, contraste WCAG AA (`ContrastTest`), sin estilos sueltos: usa `Shalev*` del `:designsystem`. `ShalevSpacing` tiene xs=8, md=16, lg=24 (**no existe `sm` ni `xl`**). Las acciones usan iconos visibles y descripción accesible; en diálogos/ventanas conservan texto solo Confirmar/Eliminar/Omitir/Cancelar. Diálogos centrados. Guardar = ✓. Gráficos sin líneas de rejilla. Asistentes de 3–4 pasos.
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
- Base de datos de trabajo **v18** (`MIGRACION_17_18`: locales estables, fichas, costo adicional, gestión/lotes/revisiones y metadatos locales de migración); respaldo **DTO v5** (importa v3–v5); contenedor `.shalev` **v4** (lee v3/v4). Un cambio de esquema exige migración + esquema exportado + test. **El JSON lo genera Room/KSP al compilar**; no se inventa a mano. Las ventas de insumos continúan enteras por decisión del dueño; recetas/recepción admiten milésimas.
- Firma de los sub-tokens: clave P-256 propia de la principal (`FirmanteLicenciaAndroid`, Keystore `PURPOSE_SIGN`, alias `spvi_licencia_firma_v1`), distinta de la clave ECDH del equipo y que **nunca** se borra (tampoco al desvincular).
- La firma de release debe ser **siempre la misma** (el `deviceId` de la licencia depende de ella).

## Estado de verificación

El estado medido y la separación entre corridas históricas se mantienen en [docs/VERIFICACION.md](docs/VERIFICACION.md). La auditoría Android del 2026-11-13 usa CI real: el checkout Arena no tiene JDK/SDK Android funcional y no debe atribuirse a este entorno la instalación física ni los 883 tests históricos de otro equipo. El cierre está en curso hasta que pase la corrida final de JVM, emulador, lint, API mínima, permisos y R8/debug.

Los instrumentados se ejecutan en emulador API 35 por programación semanal, `workflow_dispatch` o un commit publicado con `[instrumentados]`. Compilar `androidTest` por sí solo no valida Room/Compose. Las capturas Roborazzi, dos teléfonos reales, revisión visual/TalkBack y actualización de producción firmada siguen siendo comprobaciones independientes; antes de publicar, seguir `Pruebas.md`, `PRUEBAS_DISPOSITIVO.md` y `RELEASE.md`.

**Firma vigente:** la documentada y contrastada con v1.0 en `RELEASE.md`: `gl-release.jks`, alias `gl`, SHA-256 `13:9D:3D:A9:3B:D3:AB:B8:81:A2:C5:55:03:DA:E0:B0:59:AB:4C:36:A6:8A:D1:63:94:A8:B5:26:FF:D4:17:38`. Las notas antiguas que exigían otra clave (`spvi-release.jks`/`shalev-release.jks`) estaban equivocadas; no generar ni cambiar la clave ni pedir sus credenciales por chat. CI no firma ni publica un APK de producción.
