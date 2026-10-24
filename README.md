# Shalev — Sistema de Punto de Venta e Inventario

App Android nativa para pequeños negocios: vender en turnos, controlar inventario e insumos, cobrar por transferencia, ver estadísticas y exportar registros. Funciona **sin conexión** y guarda todos los datos **cifrados en el teléfono**. El uso se controla con licencias offline emitidas por la app GL del desarrollador (contrato v1).

**Contraparte iOS:** [IShalev](IShalev/README.md) es un proyecto SwiftUI nativo e independiente dentro de este repositorio. Incluye onboarding, licencia GL, perfiles locales de empleados, venta, caja, inventario, servicios, corrección de ventas, exportación y soporte; la sincronización iOS↔Android y la distribución firmada aún no están resueltas. El estado de compilación y pruebas está en [docs/VERIFICACION.md](docs/VERIFICACION.md).

| | |
|---|---|
| Versión | **1.0** (`versionCode 53`) |
| Paquete | `cu.shalev.app` (debug: `cu.shalev.app.debug`) |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| Base de datos | Room **v13** en desarrollo (migración 12→13; exportación del esquema pendiente de compilación), cifrada con SQLCipher (respaldo `RespaldoDto` v4; archivo `.shalev` v4) |
| Contrato de licencias | GL v1 (`ECIES-P256-AES256GCM-v1`) |
| Permisos | Solo `CAMERA`, `INTERNET` y `POST_NOTIFICATIONS` (aviso «Turno abierto», Android 13+), más `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE` (servicio de la app principal con secundarias, 0.19.2), `REQUEST_INSTALL_PACKAGES` / `REQUEST_DELETE_PACKAGES` (actualizaciones y licencia transferida, 0.25.0) y `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (hasta Android 12) / `WRITE_EXTERNAL_STORAGE` (hasta Android 9) para el registro de la prueba (0.26.0), y `USE_BIOMETRIC` / `USE_FINGERPRINT` (acceso con clave opcional, 0.27.0; permisos normales que añade `androidx.biometric`). La captura SMS opcional requiere habilitar manualmente el Acceso especial a notificaciones; no solicita `READ_SMS`. |

**Documentación**

| Documento | Contenido |
|---|---|
| [IShalev/README.md](IShalev/README.md) | Proyecto iOS nativo SwiftUI, alcance, compilación y pruebas online |
| [SECURITY.md](SECURITY.md) | Gestión de claves, cifrado, modelo de amenazas y buenas prácticas |
| [LICENSE_CLIENT.md](LICENSE_CLIENT.md) | Cómo aplica Shalev el contrato de licencias GL v1 |
| [FORMATOS.md](FORMATOS.md) | Formato del respaldo `.shalev` y de las exportaciones (PDF, Excel, imágenes; registro de la prueba) |
| [UI_UX_IX.md](UI_UX_IX.md) | Design system, decisiones de interfaz, antes/después y pendientes |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Referencia de tokens y componentes del módulo `:designsystem` |
| [MANUAL_USUARIO.md](MANUAL_USUARIO.md) | Manual breve para el usuario final, sin tecnicismos |
| [RELEASE.md](RELEASE.md) | Keystore, firma, `bundleRelease`/`assembleRelease`, verificación del APK y prueba de humo |
| [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md) | Pruebas en teléfono por USB: adb, tests instrumentados, pruebas manuales guiadas, checklist y solución de problemas |
| [Contexto.md](Contexto.md) · [Pruebas.md](Pruebas.md) · [Pendiente.md](Pendiente.md) | Resumen del proyecto, orden de pruebas y lo que falta (para OpenCode) |
| [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md) | Qué se hizo en cada fase (Prompts 1–19) y las suposiciones de cada momento |

Los documentos de trabajo que estaban fuera del proyecto (`DECISIONES_LICENCIA_Shalev.md`, `PRUEBA_COMPATIBILIDAD_GL.md`, `ARQUITECTURA_Shalev.md`, planes y auditorías de los Prompts 17 y 18, entregas `ENTREGA_*`) se retiraron en la 0.18.0. Lo vigente está en `LICENSE_CLIENT.md` (contrato GL y decisiones de licencia), `SECURITY.md`, `docs/HISTORIAL_DESARROLLO.md` y el propio código.

## Alcance actual y cambios recientes — 24 de octubre de 2026

La aplicación principal de este repositorio sigue siendo **Android nativa**. Los antiguos proyectos Flutter/iOS y web de escritorio se retiraron; `IShalev/` es una contraparte iOS nueva, nativa en SwiftUI y con proyecto Xcode separado, no una restauración del código Flutter eliminado. La sincronización por red local entre teléfonos Android sigue siendo parte de la app Android.

La rama contiene mejoras recientes en Inicio, Inventario, Registros, Actualizar y Licencia. El detalle para usuarios está en [MANUAL_USUARIO.md](MANUAL_USUARIO.md), los cambios técnicos en [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md) y el estado medido de las pruebas en [docs/VERIFICACION.md](docs/VERIFICACION.md).

La versión publicada es la **1.0 (`versionCode 53`)**: la primera versión pública de Shalev (Release `v1.0` de GitHub, 22/10/2026). El número interno de trabajo era `0.32.0` (`versionCode 52`); el detalle de lo que trae está en la sección «Novedades de la 0.32.0» y en [docs/HISTORIAL_DESARROLLO.md](docs/HISTORIAL_DESARROLLO.md).

## Descripción

### Qué hace

| Área | Funciones |
|---|---|
| **Inicio** | Fijos arriba (no se desplazan): turno, selector de período y banner de licencia. Debajo, con desplazamiento: alertas de inventario (stock bajo/crítico, insumo bajo/crítico, próximo a caducar), accesos a Pago electrónico (teléfono y tarjeta/cuenta resaltados) y Precios, y acordeones: 4 gráficos (Ventas, Inventario, Métodos de pago, Ganancia neta) y Top 3 (más vendido, lento movimiento, rentabilidad). **Nueva venta** y movimiento de caja son botones flotantes (con el turno abierto). Los acordeones empiezan cerrados y se cierran de nuevo al cambiar de ventana |
| **Venta** | Solo con turno abierto. Selección desde el Inventario, carrito con cantidades, **Efectivo** (comprobante) o **Transferencia** (QR de Transfermóvil con tarjeta y móvil; el total aparece debajo porque el QR oficial no lo admite). El nº de transacción se puede capturar opcionalmente desde una notificación autorizada o pegar/compartir desde el SMS de PAGOxMOVIL. Un Elaborado se vende mientras alcancen sus insumos, que se descuentan en la misma transacción |
| **Inventario** | Lista con buscador, filtros, selección múltiple, ficha, alta manual y exportación (PDF, Excel, Imagen, Tarjetas) |
| **QR de vinculación** | CameraX + ML Kit (modelo empaquetado). Solo lee el QR para vincular la principal con la secundaria |
| **Servicios** | (0.18.0, sustituye a Elaboración.) Nombre, Tipo, Importe y, opcionales, foto y descripción. Pueden gastar insumos. Se venden aparte de los productos, tienen su pestaña en Registros y sus indicadores en Inicio. Los **insumos** son ahora la categoría «Insumos» del Inventario (precio de venta opcional, en unidades enteras). Los Elaborados muestran «Alcanza para N» |
| **Registros** | Ventas, Transferencias, Movimientos, **Clientes** (clientes fijos, 0.27.0; corregir sus datos desde la ficha, 0.31.0) y Turnos, con buscador y filtros de fecha e importe. Exportar a PDF/Excel (0.26.0: ya no se comparte como texto). Desde la 0.31.0 se corrige lo registrado de un turno **abierto**: cantidades, método de pago y nº de transacción en ventas y servicios; la venta de una transferencia desde su ficha; y la cantidad de un ajuste desde la ficha del movimiento |
| **Ajustes** | Completar configuración, Perfil, Licencia, Pago electrónico, Precios (preajustes), Avisos de inventario, Permisos, Respaldo, Actualizar, Ayuda, Soporte |

### Qué no hace

- No tiene servidor, cuentas de usuario, sincronización en la nube, Firebase, analítica, telemetría ni anuncios.
- No pide `READ_SMS` ni consulta el buzón. El SMS se puede pegar o compartir; también hay captura opcional de notificaciones nuevas mientras una venta por transferencia espera el pago (requiere habilitar manualmente el Acceso especial a notificaciones y solo usa el número e importe reconocidos, no guarda el texto completo).
- No usa el almacenamiento compartido para tus datos: guardar y abrir archivos pasa por el selector del sistema. La única excepción (0.26.0) es el registro cifrado de la prueba en Imágenes/Shalev y Download/Documents.
- No tiene usuarios ni contraseñas propias. Desde la 0.27.0 hay un **acceso con clave opcional** que usa la huella o el PIN/patrón **del teléfono** (Shalev no guarda ninguna clave).
- No modifica el launcher, la barra de estado, la de navegación ni el notch (edge-to-edge estándar).

## Requisitos

- **Android Studio** Ladybug (2024.2) o superior, con **JDK 17** (el JBR incluido sirve).
- **Android SDK 35**.
- **Gradle 8.11.1**: lo descarga el wrapper (`gradlew`).
- Teléfono o emulador con **Android 8.0 (API 26)** o superior. La cámara es opcional (`android.hardware.camera.any`, `required=false`).

Versiones principales (`gradle/libs.versions.toml`): Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, Hilt 2.52, Room 2.6.1, SQLCipher 4.6.1, DataStore 1.1.1, Navigation 2.8.5, CameraX 1.4.1, ML Kit barcode 17.3.0 (empaquetado, sin descargas), OkHttp 4.12.0, Coil 3.0.4, fastexcel 0.18.4, ZXing core 3.5.3, kotlinx.serialization 1.7.3, coroutines 1.9.0.

## Configuración

1. **Claves públicas de GL.** `LicenseTrust.kt` fija la ECDH `sha256:fb3a:f7fc:…:078d` y la firma `sha256:113f:a5f6:…:4b1c` del emisor vigente (GL 1.2.0, pestaña Claves del teléfono `4bc9f8f3`, 22/10/2026). También acepta las firmas de instalaciones anteriores (`sha256:58d4:3aac:…:8c4e` del bundle del 05/10/2026, `sha256:e693:6c20:…:0a6e` y `sha256:d9c4:d821:…:998e` del 15/10/2026, `sha256:8a91:…:6888` del 30/09/2026) para licencias ya emitidas. Como GL genera claves locales por instalación, verifica siempre las huellas en la pestaña Claves del GL emisor: firma nueva → añadir a `SIGN_KEYS`; ECDH distinta → fijar esa clave y publicar un build nuevo. `LicenseTrustTest` valida las huellas.
2. **Firma de release.** El repositorio no contiene ningún keystore ni contraseña. `app/build.gradle.kts` los lee de `keystore.properties`, que git ignora, o de las variables `Shalev_KEYSTORE`, `Shalev_KEYSTORE_PASSWORD`, `Shalev_KEY_ALIAS` y `Shalev_KEY_PASSWORD`. Los pasos completos están en [RELEASE.md](RELEASE.md). **Firma siempre con la misma clave**, porque `ANDROID_ID` (y con él el `deviceId` de la licencia) depende de la firma del APK.
3. **Contacto del desarrollador.** Teléfono `+5351815604` en una sola constante (`core/.../contact/DeveloperContact.kt`), que usan Licencia y Soporte.
4. **Esquema Room.** La versión actual es **v12** (`data/schemas/cu.shalev.data.db.ShalevDatabase/12.json`). Cada cambio requiere migración, esquema exportado y `EsquemaTest`.
5. **Baseline de lint.** Se entrega vacío. La primera vez ejecuta `./gradlew :app:updateLintBaseline` y versiona `app/lint-baseline.xml`; a partir de ahí, cualquier error nuevo de lint rompe `shalevCheck`.
6. **Actualizaciones:** el build apunta por defecto a las Releases públicas de [`rmdvcd/Shalev`](https://github.com/rmdvcd/Shalev/releases) (`shalevGithubRepo` en `gradle.properties`). Para compilar un fork, se puede sobrescribir con `-PshalevGithubRepo=usuario/repositorio`; vacío desactiva las consultas.
7. **Nada más que configurar:** no hay claves de API, `google-services.json` ni variables de entorno.

## Compilación

```bash
./gradlew assembleDebug           # APK debug (cu.shalev.app.debug)
./gradlew assembleRelease         # APK release: R8 activo (ofusca y elimina android.util.Log); firmado si hay keystore
./gradlew bundleRelease           # AAB release (solo para Google Play)
./gradlew shalevRelease             # shalevCheck + AAB + APK de release (ver RELEASE.md)
./gradlew shalevTests               # todos los tests JVM (sin dispositivo y sin red)
./gradlew shalevInstrumentedTests   # tests instrumentados (emulador o teléfono API 26+)
./gradlew shalevCheck               # antes de entregar: shalevTests + lintDebug + assembleDebug + shalevPermisos
```

`shalevPermisos` lee el manifiesto **fusionado** de debug y de release, y falla si aparece cualquier permiso fuera de la lista autorizada (`CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `CHANGE_NETWORK_STATE`, desde la 0.25.0 `REQUEST_INSTALL_PACKAGES` y `REQUEST_DELETE_PACKAGES`, desde la 0.26.0 `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` y `WRITE_EXTERNAL_STORAGE`, estos dos últimos con `maxSdkVersion`, y desde la 0.27.0 `USE_BIOMETRIC` y `USE_FINGERPRINT`). `tools/verificacion/verificar.sh` hace la misma comprobación sobre el manifiesto de `:app`. La única excepción es el permiso propio de nivel *signature* `cu.shalev.app….DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, que declara androidx.core: no lo concede el usuario. La CI de GitHub Actions (`.github/workflows/ci.yml`) se ejecuta en cada push a `main` o a una rama `arena/**` y en cada pull request; sus trabajos son los mismos comandos de arriba: `shalevTests` + compilación de los instrumentados, `:app:lintDebug`, `:app:assembleRelease` (con `missing_rules.txt` vacío), `shalevPermisos` y `tools/verificacion/api_minima.py`.

### Arquitectura

```
:app ──────────► :designsystem   (Compose, tokens, componentes)
  │  └────────► :data ─────────┐   (incluye Keystore: data/security)
  └──────────► :domain ◄───────┘
                 └──► :licencia
                         └──► :core
```

| Módulo | Tipo | Contenido |
|---|---|---|
| `:core` | JVM | `AppResult`/`AppError`, `Cup` (centavos) y `Money.format` («1,450.00 CUP»), `Percent`, `Cantidad` (milésimas), `Clock`, validadores, contacto del desarrollador |
| `:licencia` | JVM | Contrato GL v1, criptografía (ECDH, HKDF, AES-GCM, ECDSA), `LicenseManager`, estados y banner |
| `:domain` | JVM | Modelos, interfaces de repositorio, servicios puros (stock, planificador de venta, recetas, estadísticas, tablas de exportación) y casos de uso |
| `:data` | Android | Room + SQLCipher, DAOs, repositorios, DTO de respaldo, `BackupCipher`, exportadores PDF/XLSX, DataStore cifrado, consulta/descarga de actualizaciones (OkHttp), sincronización LAN, fotos y, en `security/`, `KeystoreAead`, `AndroidDeviceKey` y `DatabasePassphrase` (antes módulo `:security`, fusionado en P17) |
| `:designsystem` | Android | Tema, tokens, iconos, componentes y gráficos (ver [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)) |
| `:app` | Android | Una Activity, NavHost y las pantallas como paquetes (MVVM + UDF: `StateFlow` de estado y `Channel` de eventos). Los ViewModels usan casos de uso cuando hay lógica, y la interfaz de repositorio de `:domain` cuando solo se lee o se borra (P17) |

- **DI:** Hilt. **Navegación:** Navigation Compose 2.8 con rutas `@Serializable`.
- **Arranque:** splash del sistema → `RootViewModel` → *Bloqueo* (si la licencia no permite el uso) → *Onboarding* (primera vez) → pantalla principal. Bloqueo y Onboarding están fuera del NavHost: ni Atrás ni un enlace pueden saltárselas.
- **Barra inferior:** Inicio, Inventario, Servicios, Registros, Ajustes (solo iconos, resaltado invertido). Se pasa de una sección a otra deslizando el dedo hacia los lados.

### Pruebas

| Qué | Comando |
|---|---|
| Todos los JVM (core, licencia, domain, data, designsystem, app) | `./gradlew shalevTests` |
| Un módulo | `./gradlew :domain:test` · `./gradlew :app:testDebugUnitTest` |
| Todos los instrumentados | `./gradlew shalevInstrumentedTests` |
| Solo los flujos de integración | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=cu.shalev.app.integracion` |
| Capturas de pantalla claro/oscuro (Robolectric + Roborazzi, sin emulador) | `./gradlew :app:recordRoborazziDebug` → `app/capturas/` · ver [CAPTURAS.md](CAPTURAS.md) |
| **Sin Gradle ni Android SDK** (Linux): compila todo, Room y Hilt por KSP, migración, tests JVM y los tests de Room de `:data` con SQLite por JDBC | `bash tools/verificacion/verificar.sh` (descarga ~1,5 GB la primera vez en `~/.cache/spvi-tc`) |

- Los tests JVM **no pueden salir a internet** (proxy inexistente salvo el bucle local, que usa `MockWebServer`) y se ejecutan con `user.timezone=UTC`.
- Los instrumentados usan Room **en memoria** y fakes en los bordes (cámara, selector de archivos, bases públicas). Los fakes de `:app` viven en `app/src/sharedTest` y los comparten los tests JVM y los instrumentados.
- Informes: `<módulo>/build/reports/tests/` y `<módulo>/build/reports/androidTests/connected/`.
- **Verificación actual: [docs/VERIFICACION.md](docs/VERIFICACION.md)** (única fuente de verdad). El último CI observado, del 2026-10-15, falló en tests JVM al no poder descargar el SDK instrumentado de Robolectric; los demás trabajos pasaron. La corrección de esta rama aún requiere CI. No hay JDK/Android SDK local para ejecutar Gradle.

## Contrato GL aplicado

Shalev es **cliente** del contrato v1 de GL, sin cambiarlo. Detalle completo en [LICENSE_CLIENT.md](LICENSE_CLIENT.md).

| Punto | Aplicación en Shalev |
|---|---|
| Solicitud | `RequestPayload` (`v = 2`): nombre, apellidos, CI, vía, teléfono, `deviceId`, tipo, fecha, nonce y clave pública del teléfono; añade `secundarias` y `renueva` cuando corresponden. Se cifra como `SPVIR1:` hacia la clave ECDH de GL; no lleva `appName` ni firma efímera |
| `deviceId` | `"SPVI:" + ANDROID_ID` |
| `devicePub` | Clave P-256 persistente del teléfono. **Nunca se envía `null`** |
| Envío | Solo texto: WhatsApp (`wa.me/5351815604`) o SMS (`smsto:+5351815604`) con los datos legibles + la solicitud cifrada `SPVIR1:`. Respuesta de GL: licencia corta cifrada `SPVI2:` (216 caracteres) con las características arriba ([docs/LICENCIA_0.23.md](docs/LICENCIA_0.23.md)) |
| Activación | Se pega el mensaje de GL: firma ECDSA de GL, descifrado con la clave del teléfono, `deviceId`, `devicePub`, `estado` y `venceEn` |
| Envelope legado v1 | Android conserva la verificación criptográfica de una licencia larga ya instalada; no activa una nueva desde 0.23.0. El formato v1 acepta únicamente sus 8 campos |
| Claves de GL | Firma y ECDH: solo fijadas en el build |
| Prueba | 7 días |
| Tipos y precios | Mensual 6,000 · Semestral 30,000 · Anual 50,000 · Perpetua 90,000 CUP |
| Reloj | Si retrocede más de 2 h respecto a la última hora vista → bloqueo hasta corregirlo |
| Clave maestra | `GLMK-PERM-MAXS-9999`: perpetuidad y 10 secundarias **solo** en builds de depuración |
| Apps secundarias | La principal firma el permiso de cada ranura y dispositivo (sub-token); la secundaria lo comprueba antes de dar la licencia por buena |

## Novedades de la 0.32.0

- **Sincronización manual, con barra de avance y sonidos:** ya no hay sincronización automática: el empleado toca «Sincronizar ahora» (y se sincroniza al abrir turno y al pedir el cierre). La app secundaria **mantiene la conexión abierta** con la principal, así el dueño ve si está conectada. Mientras sincroniza hay una **barra de avance horizontal** en la notificación y, en la principal, una barra por cada app vinculada. Tres avisos con **sonido propio**: la app de un empleado se conectó, se desconectó y terminó de sincronizar (la app del empleado oye «sincronización terminada»). Los audios están en `app/src/main/res/raw` y se pueden sustituir.
- **El fondo de caja lo escribe quien abre el turno**, también en la app de un empleado: el encargado ya no lo asigna ni lo pide (se invierte la regla de 0.26.0).
- **Las fotos de productos y servicios llegan a la app del empleado**: viajan por la red local en bloques (como el APK), se guardan en su teléfono con el mismo nombre y se comprueban con su SHA-256.
- **El conteo de inventario es parcial y va por categoría:** solo se cuenta **lo que cambió durante el turno**, separado en pestañas por categoría. En la principal, la diferencia se aplica sobre el valor actual (no se rechaza el conteo si el artículo se movió después).
- **Se retiran «Design system» y «Generar datos de prueba»** del menú de Ajustes (y el generador del APK de release).
- **Cierre de turno:** conteo opcional por denominaciones CUP (1, 3, 5, 10, 20, 50, 100, 200, 500, 1000, **5000 y 20000**), monedas/otro efectivo y comparación con el esperado. Después se cuentan productos físicos e insumos (hasta tres decimales), se revisan diferencias y se exige motivo si hay ajustes. Los elaborados se controlan mediante sus insumos. El conteo queda en el turno, el respaldo y sus PDF/Excel. Cada secundaria envía su declaración; solo la principal autoriza y ajusta. Si las existencias cambiaron, se debe repetir el conteo, no sobrescribir stock nuevo. Para este flujo deben actualizarse tanto la principal como las secundarias. **Pendiente de compilación y prueba en dos dispositivos**; ver `docs/VERIFICACION.md`.

- **Pago electrónico:** los logos de BPA, BANDEC y BANMET se muestran con esquinas redondeadas y el mismo tamaño (96 × 48 dp). El logo se elige por los 4 primeros dígitos de la tarjeta; es una estimación (ver [docs/LOGOS_BANCOS.md](docs/LOGOS_BANCOS.md): `9225`/`9235` los comparten varios bancos y `9226` tiene fuentes contradictorias).
- **Inicio:** el turno con su interruptor de dos estados, debajo el selector de período y, debajo, el banner de licencia: los tres a todo el ancho y **fijos** (el resto de la pantalla se desplaza por debajo). Inicio se abre siempre por arriba: no guarda por dónde ibas. En la tarjeta de Pago electrónico, el teléfono y la tarjeta/cuenta van en seminegrita. Los gráficos y listas que siguen al selector de período son acordeones: empiezan cerrados y se vuelven a cerrar al cambiar de ventana.
- **Licencia:** una tarjeta «Precios de la licencia» lista el precio de cada tipo y lo que suma cada app secundaria.
- **Textos y tamaños:** las pestañas de Registros (de Ventas a Turnos) van en una sola fila horizontal desplazable, por decisión del dueño. Los títulos de las tarjetas y el valor de los selectores ya no se cortan con «…»: pasan a otra línea. Textos sin detalles técnicos: «protegidos» en lugar de «cifrados» en Bienvenida, Perfil y Respaldo, y «no es una actualización válida de Shalev» en lugar de «no es un APK válido».
- **Actualizaciones:** antes de abrir el instalador Shalev guarda un respaldo completo (sin contraseña, en la carpeta privada de la app; no se exporta ni cuenta para el recordatorio mensual). Si no se puede guardar, no se actualiza. Al abrir la versión nueva se restaura y se borra; si la instalación se cancela, se borra sin restaurar. Las ventas hechas entre el respaldo y la instalación no entran en él. El texto de la actualización ya no menciona el repositorio ni su alojamiento.
- **Registros → Clientes:** «Editar» y «Quitar de clientes fijos» quedan centrados en el pie de la ficha, siempre a la vista. «Editar» abre una ventana para corregir nombre, carné y teléfono, con las mismas comprobaciones de la venta; no se admite un carné que ya sea de otro cliente y sus compras anteriores quedan con los datos corregidos (carné incluido).

## Novedades de la 0.27.1 (correcciones, [historial](docs/HISTORIAL_DESARROLLO.md))

- La app ya no se cierra en Android 8–13 al abrir Inicio, ni en Android 8–11 al convertir importes y cantidades: usaba APIs de Android 14 y 12.
- Con letra muy grande ya no se parten palabras ni importes: alertas y accesos de Inicio, arqueo de caja, subtítulos de Ajustes y Exportar.
- Textos de Ajustes al día: Respaldo y Permisos del teléfono.

## Novedades de la 0.27.0 ([docs/PROMPT_0.27.0.md](docs/PROMPT_0.27.0.md))

| Función | Resumen |
|---|---|
| **Títulos centrados** | Barra superior, diálogos y hojas con el título centrado arriba y sin logo. En Inicio, «Shalev» con la fuente de marca |
| **Textos completos** | Los textos pasan a varias líneas en lugar de cortarse (también con letra al 200 % en 360 dp); capturas con letra grande de Inicio, ficha, Licencia y arqueo |
| **Top 3 con medallas** | Oro, plata y bronce (`ShalevMedalla`), con contraste AA en claro y oscuro |
| **Animaciones** | 150–250 ms y muelle sin rebote (`ShalevMotion`); las ventanas emergentes suben desde abajo al centro y bajan al cerrar. `profileinstaller` se añadió en esa versión y se retiró después al no existir un baseline profile generado (auditoría 2026-10-14) |
| **Deslizar** | Solo entre las 5 pantallas principales, nunca en subpantallas, con un diálogo abierto, en selección o escribiendo |
| **Campos** | El texto de ejemplo (placeholder) va centrado |
| **PDF y Excel** | Con su icono en todas las hojas de exportar |
| **Textos llanos** | Sin versiones, nombres técnicos ni explicaciones de la licencia o del registro de la prueba |
| **Principal ≠ secundaria** | Una app principal nunca puede pasar a secundaria; para cambiar el tipo hay que exportar el respaldo y borrar los datos de Shalev |
| **Respaldo sin contraseña (estado de 0.27.0)** | En esa versión, «Proteger con contraseña» era opcional y venía apagado; el archivo `.shalev` v4 indica si la lleva. Desde S-01 (10/2026), viene activado por defecto; ver [SECURITY.md](SECURITY.md). |
| **Acceso con clave** | Opcional (recorrido inicial y Ajustes): huella o PIN/patrón del teléfono al abrir la app y tras 10 minutos o más en segundo plano. No va en el respaldo |
| **Soporte** | Datos del desarrollador con icono |
| **Foto primero** | Nuevo producto y Nuevo servicio empiezan por la foto, con botones Cámara y Galería (selector de fotos del sistema, sin permisos) |
| **Alertas de Inicio** | Centradas y repartidas por filas (4 → 2 + 2, 5 → 3 + 2, 6 → 3 + 3) |
| **Gráfico de inventario** | Los insumos cuentan en su propia medida (sin «u») |
| **Icono** | Logo con fondo transparente |
| **Clientes fijos** | En una venta por transferencia, «Cliente fijo» guarda nombre, carné y teléfono (por carné). Al escribir el nombre se sugieren hasta 3 clientes y tocar uno rellena sus datos. Registros → Clientes muestra sus compras y permite quitarlo. Viajan en el respaldo y desde las secundarias al sincronizar |
| **Claves de GL** | Par nuevo de GL del 05/10/2026 fijado en `LicenseTrust` (se conserva la firma anterior) |

Base de datos v10 (`MIGRACION_9_10`): tabla `cliente_fijo` y `transaccion.clienteFijo`.

## Novedades de la 0.26.0 ([docs/PLAN_0.26.md](docs/PLAN_0.26.md))

| Función | Resumen |
|---|---|
| **Fondo de los empleados** | El encargado asigna en Apps vinculadas el fondo de caja de cada turno de una secundaria («Fondo del próximo turno» → Asignar fondo; puede ser 0). Sin él la secundaria no abre turno: toca «Pedir fondo» y lo recibe al sincronizar, sin poder cambiarlo. Sirve para un solo turno. Inicio de la principal avisa «Luis pide abrir turno» |
| **Apps de empleados antiguas** | Una secundaria 0.25.x sigue abriendo turno con su propio fondo; la principal muestra «Actualiza la app de X» |
| **Actualizaciones obligatorias** | Si hay una versión nueva, «Más tarde» la aplaza (hasta 30 días, con la fecha límite visible). Vencido el plazo, la app se bloquea con «Actualiza Shalev para seguir» y solo deja Actualizar, Exportar respaldo y cerrar el turno abierto. La búsqueda semanal ya no se desactiva (Ajustes → Actualizar) |
| **Respaldo solo cifrado** | Respaldo guarda solo el archivo `.shalev` con contraseña; se quitó el «Documento PDF» |
| **Sin exportaciones de texto** | Registros, Inventario, Servicios y las fichas exportan PDF y Excel (e Imagen/Tarjetas en Inventario); solo la licencia se envía como texto |
| **«+» en la esquina** | El botón «+» va abajo a la derecha en Inventario, Servicios, Precios y Apps vinculadas |
| **La prueba no se reinicia al reinstalar** | La fecha de inicio de la prueba se guarda cifrada (AES-GCM, clave derivada de ANDROID_ID) en una imagen pequeña en Imágenes/Shalev y en copias en Download y Documents, que quedan al desinstalar. Recién instalada, Shalev pide **una vez** el acceso a fotos para encontrar la de una instalación anterior; gana la fecha más antigua. Una copia ilegible se ignora (no bloquea). La fecha atrasada se detecta además con el reloj monótono del teléfono ([docs/PLAN_ANTIREINSTALACION.md](docs/PLAN_ANTIREINSTALACION.md)) |
| **Seminegrita** | Importes, precios, cantidades, fechas y totales en seminegrita en la app y en el PDF; en Excel en negrita, con columnas al ancho de su contenido y texto largo en varias líneas |

Base de datos v9 (`MIGRACION_8_9`): empleado con `fondoAsignadoCent`, `fondoAsignadoEn`, `aperturaSolicitadaEn` y `versionCode`. Protocolo v1 ampliado con campos opcionales (compatible con 0.25.x).

## Novedades de la 0.25.1

| Función | Resumen |
|---|---|
| **Compartir el turno** | Registros → Turnos → detalle → Compartir: PDF o Excel del turno completo (resumen, arqueo, caja, ventas con estado, movimientos); enviar a otra app o guardar en el teléfono |
| **Modificar venta completa** | Pantalla completa: cantidades, quitar, **añadir artículos** (precio actual) y **cambiar el método de pago** (transferencia con nº obligatorio) |
| **Recuperar con licencia vencida** | El campo «ID de la licencia anterior» aparece también si la licencia instalada venció |
| **Conteo con «Ahora no»** | En la secundaria, el conteo pedido por el encargado se puede posponer; el aviso ofrece «Contar» y nada se cierra sin conteo |
| **Exportaciones corregidas** | Excel válido (formato CUP escapado); PDF en horizontal con más de 5 columnas, anchos por contenido e importes a la derecha; «Servicios» sin «Shalev ·» duplicado; Inventario → Exportar marca «Uso interno (incluye costos)» / «Para clientes (sin costos)»; nombres de hoja con fecha legible |

Con esto desaparecen las desviaciones de la 0.25.0 sobre Modificar, el conteo no descartable, la recuperación solo sin licencia y la falta de PDF/Excel del turno.

## Novedades de la 0.25.0

| Función | Resumen |
|---|---|
| **Arqueo de caja** | Fondo obligatorio al abrir (puede ser 0), entradas/salidas de efectivo con motivo, efectivo contado al cerrar (botón «Cuadra»), esperado y diferencia en la pestaña **Caja** del turno. En la principal y en las secundarias (su conteo viaja a la principal antes de aprobar el cierre) |
| **Anular / modificar ventas** | Solo la principal, solo ventas de turnos abiertos, con motivo. La venta queda ANULADA (fuera de totales y caja; existencias devueltas). Modificar = anular + venta corregida enlazada («Corrige #N») con los precios de entonces |
| **Recordatorio de respaldo** | Aviso en Inicio de la principal tras 30 días sin exportar; abre Respaldo |
| **Actualizaciones** | Consulta semanal a la última Release pública de GitHub (`-PshalevGithubRepo`), descarga con SHA-256 e instalación con `PackageInstaller`; la principal reparte el APK a las secundarias por la red local |
| **Recuperar licencia** | El respaldo v4 trae el ID de la licencia; la solicitud `SPVIR1` lleva `recupera`; GL revoca la anterior y publica `revocadas.json` firmado; el teléfono antiguo borra sus datos, se bloquea («Licencia transferida») y pide desinstalarse. Compartir el mensaje `SPVI2:` a Shalev la activa sola |

Desviaciones documentadas: instalación con `PackageInstaller` (sin FileProvider ni configuración de red propia; OkHttp solo HTTPS); Modificar no cambia el método de pago ni añade artículos (para eso, anular y vender de nuevo); el diálogo de conteo de un cierre pedido por la principal no se puede descartar; la tarjeta de actualización solo aparece en Inicio y Ajustes y nunca interrumpe una venta; la descarga no sigue en segundo plano; el campo de recuperación solo aparece sin licencia instalada; el arqueo se ve en la pestaña Caja (no hay PDF/Excel del turno) y el Excel de ventas añade la columna Estado; una venta de servicios anulada llega a la secundaria en su siguiente sincronización. Protocolo de sincronización: sigue en v1 con campos opcionales (las secundarias 0.24 siguen funcionando).

Para GL: [docs/GL_PROMPT_0.25.md](docs/GL_PROMPT_0.25.md) (recuperación automática y lista de revocadas; pruebas con `tools/licencia/probar_gl.py revocadas …` y `vector_0.25.txt`; dependencias Python en `tools/requirements.txt`).

## Apps Principal y Secundaria (0.19.0)

Una app **principal** (dueño: base de datos general y licencia) y hasta **5 secundarias** (empleados), vinculadas con un QR cifrado y sincronizadas por la **red local** (wifi del local o zona wifi del teléfono principal) **a mano** (el empleado toca «Sincronizar ahora»; además se sincroniza al abrir turno y al pedir el cierre). La app secundaria **mantiene la conexión abierta** mientras esté en la red, con una barra de avance por app en «Apps vinculadas» y avisos con sonido cuando una app se conecta, se desconecta o termina de sincronizar. Las **fotos** de productos y servicios viajan también a la secundaria. Ajustes → Apps vinculadas. Detalle técnico y de seguridad: [docs/VINCULACION.md](docs/VINCULACION.md).

## Esquema de base de datos

Archivo `shalev.db`, Room **versión 10** (v4: servicios; v5: Principal/Secundaria; historial abajo, ver [docs/VINCULACION.md](docs/VINCULACION.md)), cifrado con SQLCipher. Definición en `data/src/main/kotlin/cu/shalev/data/db/entity/Entities.kt`.

**Convenciones:** importes en centavos (`…Cent`, `Long`); cantidades de insumo en milésimas (`…Mil`); instantes en epoch ms UTC; fechas de calendario en `epochDay`; enums como `TEXT` con su nombre; sin `TypeConverters`.

| Tabla | Clave | Columnas principales | Relaciones e índices |
|---|---|---|---|
| `producto` | `id` auto | `categoria`, `nombre`, `descripcion`, `fotoUri`, `fechaCaducidad`, `precioCostoCent`, `precioVentaCent`, `cantidad`, `nivelBajo`, `nivelCritico`, `codigo`, `creadoEn`, `actualizadoEn`, `eliminado` | Índices: `categoria`, `codigo`, (`eliminado`, `creadoEn`), `fechaCaducidad`. Borrado lógico (`eliminado`) |
| `insumo` | `id` auto | `nombre`, `unidad`, `precioCent`, `cantidadMil`, `nivelBajoMil`, `nivelCriticoMil`, `creadoEn`, `actualizadoEn` | Índices: `creadoEn`, `nombre` |
| `receta_linea` | (`productoId`, `insumoId`) | `cantidadMil` | FK producto (CASCADE), FK insumo (**RESTRICT**: un insumo en uso no se borra) |
| `turno` | `id` auto | `abiertoEn`, `cerradoEn`, `numVentas`, `unidades`, `totalCent`, `efectivoCent`, `transferenciaCent`, `costoCent`, `numMovimientos`, `abiertoPor`, `cerradoPor`, `ventasEfectivo`, `ventasTransferencia`, `movimientosProducto`, `movimientosInsumo` | Resumen nulo mientras está abierto |
| `venta` | `id` auto | `turnoId`, `fecha`, `metodoPago`, `totalCent`, `costoCent`, `unidades` | FK turno (RESTRICT). Índices: `fecha`, `turnoId`, `totalCent` |
| `detalle_venta` | `id` auto | `ventaId`, `productoId`, `nombre`, `categoria`, `cantidad`, `precioBaseCent`, `precioUnitarioCent`, `costoUnitarioCent` | FK venta (CASCADE). Nombre y precios copiados al vender |
| `transaccion` | `id` auto | `ventaId`, `fecha`, `importeCent`, `numero`, `clienteNombre`, `clienteCi`, `clienteTelefono`, `tarjetaCobro`, `telefonoCobro`, `clienteFijo` (v10) | FK venta (CASCADE), `ventaId` único. Índices: `fecha`, `numero`, `importeCent` |
| `movimiento` | `id` auto | `fecha`, `tipo`, `entidad` (PRODUCTO/INSUMO), `entidadId`, `nombre`, `delta`, `existencia`, `turnoId`, `ventaId`, `nota` | Índices: `fecha`, (`entidad`, `entidadId`), `turnoId` |
| `perfil` | `id` = 1 | `nombre`, `apellidos`, `ci`, `pagoTarjetaId`, `pagoTelefonoId` | Fila única |
| `tarjeta` | `id` auto | `numero`, `alias` | `numero` único |
| `telefono` | `id` auto | `numero`, `alias` | `numero` único |
| `preajuste` | `id` auto | `nombre`, `puntosBasicos`, `metodoPago`, `importeMinimoCent`, `activo` | — |
| `preajuste_producto` | (`preajusteId`, `productoId`) | — | FK a ambos (CASCADE) |
| `cliente_fijo` (v10) | `id` auto | `nombreApellidos`, `ci`, `telefono`, `creadoEn`, `actualizadoEn` | `ci` único. Las compras se calculan de `transaccion` por carné |

- **Versión 3 consolidada (P17):** sin `tasa_cambio` ni `caducidad_codigo`. Como no había clientes, las bases de desarrollo v1 y v2 se recrean vacías (`fallbackToDestructiveMigrationFrom(1, 2)`) y no hay clases de migración. Cualquier versión futura necesita una `Migration(3, 4)`… explícita: una migración que falte rompe en desarrollo en lugar de borrar datos de usuarios.
- **v5 (0.19.0):** `turno` y `venta` con `uuid` único, `empleadoId` y `sincronizado`; tabla `empleado` (apps secundarias de la principal). `MIGRACION_4_5`.
- **v10 (0.27.0):** tabla `cliente_fijo` (carné único) y `transaccion.clienteFijo` (`MIGRACION_9_10`, esquema `10.json`). Solo añade.
- **v6–v9:** v6/v7 (0.20–0.21) empleados y permisos; **v9 (0.26.0)**: `empleado.fondoAsignadoCent`/`fondoAsignadoEn`/`aperturaSolicitadaEn`/`versionCode` (`MIGRACION_8_9`, esquema `9.json`); **v8 (0.25.0)**: tabla `movimiento_caja`, `turno.fondoCent`/`contadoCent` y totales de caja, `venta.anuladaEn`/`motivoAnulacion`/`anuladaPor`/`corrigeVentaId`. `MIGRACION_7_8`, esquema `8.json`.
- **Stock nunca negativo:** las actualizaciones usan `WHERE cantidad + :delta >= 0`.
- **Atomicidad:** venta, producción, cierre de turno e importación de respaldo van cada uno en una sola transacción (`db.tx { }`).
- **Fuera de la base de datos:** preferencias (`pref.v1`), progreso del asistente (`setup.v1`) y licencia (`lic.*`) van en DataStore con cada valor cifrado; las fotos, en `filesDir/fotos`.

## Manual de uso

El manual para el usuario final, sin tecnicismos, está en [MANUAL_USUARIO.md](MANUAL_USUARIO.md). La app incluye una versión breve en **Ajustes → Ayuda**. Resumen del flujo diario:

1. **Primera vez:** el asistente (Bienvenida → Tus datos → Avisos de inventario → Periodo de prueba). Todo se puede omitir y completar después en **Ajustes → Completar configuración**.
2. **Cargar productos:** Inventario → botón **+** → formulario (con el filtro *Insumos* puesto, **+** abre directamente su formulario). Las recetas se escriben en el formulario del Elaborado.
3. **Configurar el cobro por transferencia:** Pago electrónico (tarjeta de Inicio o Ajustes): tarjeta y teléfono.
4. **Vender:** en Inicio, abrir el turno (interruptor de la tarjeta de turno) → **Nueva venta** → elegir productos → Efectivo o Transferencia → Confirmar.
5. **Cerrar el turno** al terminar: queda su resumen en Registros → Turnos.
6. **Revisar y exportar:** Registros (PDF o Excel) e Inventario (PDF, Excel, imágenes y tarjetas).
7. **Copia de seguridad:** Ajustes → Respaldo, con contraseña. El respaldo siempre es completo.
8. **Licencia:** Ajustes → Licencia → elegir tipo → *Enviar por WhatsApp* o *SMS* → pegar la respuesta → *Activar*.

## Desviaciones de Shalev.txt (decididas en P17)

| Shalev.txt | Pedía | Estado en 0.14.0 | Motivo |
|---|---|---|---|
| línea 6 | Transfermóvil **y Enzona** para confirmar el pago y el QR | **Solo Transfermóvil.** Se quitaron el botón «Abrir Enzona» y su `<queries>` | Enzona no publica el formato de su QR: el botón solo abría la app |
| línea 11 | Exportar en PDF el perfil, los preajustes **y las tasas de cambio** | **Sin tasas** (tabla, DTO y sección del PDF eliminadas) | Nunca tuvieron pantalla de edición y ninguna operación las usaba |
| línea 87 | Escáner: buscar la fecha de caducidad en Room por código y guardarla | **Sin escáner ni campo código.** La fecha va en cada producto | Se eliminó el subsistema de código de barras; decisión del propietario |
| línea 132 | Respaldo de «Configuración» o «Todo» | **Solo «Todo»** | Un único tipo evita importar a medias y simplifica el formato (v3) |

## Advertencias

- **La contraseña del respaldo no se puede recuperar.** Sin ella el archivo `.shalev` no se abre.
- **Importar reemplaza TODOS los datos:** inventario, insumos, turnos, ventas y configuración. Los respaldos de versiones anteriores a la 0.13.0 (formato v1/v2) ya no se abren.
- **Las fotos de los productos no van en el respaldo.** El respaldo guarda la ruta de la foto, pero no la imagen; en otro teléfono los productos aparecen sin foto.
- **La licencia no va en el respaldo.** Cada teléfono necesita la suya (`deviceId` distinto). Desde la 0.25.0 el respaldo lleva su ID para **recuperarla** gratis en otro teléfono (MANUAL §9).
- **Borrar los datos de la app o reinstalarla** destruye la base de datos (su clave vive en el Keystore del teléfono) y la licencia instalada. Haz un respaldo antes. El `deviceId` suele mantenerse si el APK está firmado con la misma clave, así que el desarrollador puede volver a emitir la licencia.
- **El PDF de configuración y las exportaciones de Registros no llevan contraseña.** Las de Transferencias incluyen nombre, carné y teléfono de clientes.
- **Licencia offline:** la única revocación es la lista de GitHub (0.25.0), que el teléfono antiguo solo aplica si se conecta a internet; un APK modificado puede saltarse la comprobación. **Reinstalar** Shalev reinicia los 7 días de prueba (aceptado y documentado). Ver [SECURITY.md](SECURITY.md#modelo-de-amenazas).
- **QR de Transfermóvil sin importe:** el formato oficial no lo admite; el cliente escribe el total que Shalev muestra bajo el QR.
- **SMS de licencia:** la solicitud ocupa unos 780 caracteres (varios SMS) y la licencia, unos 340 (3 SMS) o 216 si GL manda solo el código. WhatsApp es el canal recomendado.
- **Valores fijos:** el tema sigue al sistema, el aviso de caducidad salta 7 días antes y la red espera 5 s. Son constantes, no preferencias (P17).
- **Pruebas en este entorno:** la suite se compiló y los tests JVM pasaron fuera de Gradle. Gradle, lint, R8, Room KSP y los tests instrumentados no se han ejecutado nunca. Confirma con `./gradlew shalevCheck shalevInstrumentedTests` y la prueba de humo de [RELEASE.md](RELEASE.md) antes de distribuir.
- **Debug y release no comparten licencia:** cada uno tiene su propio paquete (`cu.shalev.app.debug` frente a `cu.shalev.app`) y su propia firma, así que su `deviceId` es distinto. Lo mismo pasa entre el APK distribuido a mano y uno publicado en Google Play con *Play App Signing*.

## Licencia del código

El repositorio no incluye un archivo de licencia del código fuente; los derechos corresponden al desarrollador (ver Ajustes → Soporte). El documento [LICENSE_CLIENT.md](LICENSE_CLIENT.md) describe el sistema de **licencias de uso** de la app, no la licencia del código fuente.

**Recursos de terceros incluidos en el APK:** iconos con trazados de Material Symbols (Apache 2.0) y, desde la 0.15.0, 9 ilustraciones de [unDraw](https://undraw.co) (licencia unDraw: uso libre, también comercial, sin atribución obligatoria) tomadas del paquete npm `undraw-svg` 2.0.0 (MIT) y convertidas a vectores Compose (`designsystem/.../ilustracion/ShalevIlustraciones.kt`).
