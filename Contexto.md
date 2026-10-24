# Contexto de Shalev para la IA de OpenCode

Este documento resume la app principal Android nativa de este repositorio. También existe el subproyecto iOS nativo `IShalev/`, cuya arquitectura, alcance y compilación se describen en `IShalev/README.md`; las reglas Kotlin/Gradle de este contexto aplican a Android. Las reglas obligatorias están en `AGENTS.md`; lo pendiente de Android está en `Pendiente.md`, y el orden de pruebas, en `Pruebas.md`. El estado medido de las verificaciones está en `docs/VERIFICACION.md`. Los documentos de versiones anteriores se conservan como referencia histórica.

> **Idioma:** responde siempre en español. Todo el texto de la interfaz, los comentarios y la documentación también están en español.

---

## 1. Qué es Shalev

Shalev (Sistema de Punto de Venta e Inventario) es una app Android nativa para pequeños negocios de Cuba.

| | |
|---|---|
| Versión | **1.0** (`versionCode 53`) |
| Paquete | `cu.shalev.app` (debug: `cu.shalev.app.debug`) |
| SDK | minSdk 26 · targetSdk 35 · compileSdk 35 |
| Base de datos | Room **v12** cifrada con SQLCipher |
| Respaldo | `.shalev` v4 cifrado, contraseña opcional (lee también v3); `RespaldoDto` v4 (importa también v3) |
| Protocolo de la red local | `VERSION_PROTOCOLO = 1` (los campos nuevos son opcionales) |
| Licencias | Offline, emitidas por la app GL del desarrollador (contrato GL v1) |

- **Funciona sin conexión.** Todos los datos van cifrados en el teléfono.
- **Sin servidor propio, sin cuentas, sin nube.** Sin Firebase, analítica, telemetría ni anuncios.
- **Prueba gratis de 7 días.** Después hace falta una licencia que vende el desarrollador.

### Funciones

| Área | Qué hace |
|---|---|
| **Inicio** | Estado de licencia y turno; apertura/cierre, **Nueva venta** y movimiento de caja cuando corresponde; alertas de inventario y accesos a Pago electrónico/Precios. La principal muestra además gráficos y resúmenes del negocio, avisos de vinculación/actualización y recordatorio de respaldo |
| **Venta** | Solo con turno abierto. **Efectivo**, o **Transferencia** con QR de Transfermóvil (el total se muestra debajo) y nº de transacción; el SMS de PAGOxMOVIL se puede pegar/compartir o capturar opcionalmente desde una notificación con permiso manual. **Cliente fijo** (0.27.0): se guarda por carné y se sugiere al escribir el nombre (hasta 3). Un Elaborado descuenta sus insumos y muestra «Alcanza para N» |
| **Caja (arqueo)** | Fondo obligatorio al abrir, entradas y salidas con motivo, esperado = fondo + efectivo + entradas − salidas. Al cerrar se cuenta, con «Cuadra» en un toque |
| **Inventario** | Productos, Elaborados e Insumos. Precio opcional en los insumos, en unidades enteras. Buscador, filtros y exportación a PDF, Excel, imágenes y tarjetas |
| **Servicios** | Tipo libre, importe, foto y descripción. Pueden gastar insumos y tienen su pestaña en Registros |
| **Registros** | Ventas, Servicios, Transferencias, Movimientos, **Clientes** (0.27.0) y Turnos, ordenados por fecha, con filtros. **Anular o modificar** una venta: solo la principal y solo con el turno abierto |
| **Ajustes** | Perfil (nombre, apellidos y carné), Licencia, **Acceso con clave** (0.27.0, opcional: huella o PIN del teléfono al abrir y tras ≥ 10 min fuera), Pago electrónico (teléfonos y tarjetas), Precios, Avisos, Permisos, Respaldo, Actualizar, Ayuda y Soporte |
| **Apps vinculadas** | Una app **principal** (dueño) y hasta 10 **secundarias** (empleados) en la misma wifi, sin internet ni servidor. Cada empleado tiene sus permisos. La secundaria no crea secundarias y **una principal nunca pasa a secundaria** (0.27.0): el tipo se elige en el recorrido inicial; para cambiarlo hay que exportar el respaldo y borrar los datos de Shalev. Una secundaria activa el acceso con clave solo desde Ajustes |

### Exportaciones (desde la 0.26.0, sin texto)

- **PDF y Excel** de Registros, Inventario, Servicios y Turno.
- **Imagen de precios** y **Tarjetas** de productos.
- **Formato:** importes, cantidades y fechas van en **seminegrita** en el PDF y en **negrita** en Excel. Los códigos de barras y teléfonos (8 o más cifras) no se destacan.
- **PDF:** en horizontal cuando la tabla tiene más de 5 columnas.
- **Excel:** cada columna mide lo que su contenido (4–80 unidades) y los importes llevan el formato `#,##0.00 \C\U\P`.
- **Única salida como mensaje de texto:** la solicitud de licencia (y el contacto de Soporte).

Formato exacto de cada archivo: `FORMATOS.md`.

---

## 2. Arquitectura

**Pila:** Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.11.1 (wrapper) · JDK 17 · Compose BOM 2024.12.01 (Material 3) · Hilt 2.52 (KSP) · Room 2.6.1 + SQLCipher 4.6.1 · DataStore · OkHttp 4.12 + kotlinx.serialization · fastexcel · CameraX + ML Kit (QR de vinculación) · ZXing · Coil · Robolectric 4.14.1 + Roborazzi 1.38.0.

| Módulo | Contenido | Depende de |
|---|---|---|
| `:core` | Utilidades puras: dinero `Cup` en centavos, fechas, resultados | — |
| `:licencia` | Cliente de licencias GL (`LicenseManager`, formatos `SPVIR1:`/`SPVI2:`, revocaciones) y `prueba/RegistroPrueba.kt` | core |
| `:domain` | Modelos, interfaces de repositorio, casos de uso, `TablasExport` | core, licencia |
| `:data` | Room, SQLCipher, DataStore cifrado, respaldo, sincronización LAN (`ServidorSync`, `ClienteSync`), exportadores (`PdfWriter`, `DisenoPdf`, `EscritoresJvm`/XlsxWriter), implementaciones de repositorios | domain |
| `:designsystem` | Tema, componentes `Shalev*`, `ShalevIcons`, `ShalevSpacing`, `ShalevTextos` | — |
| `:app` | Pantallas Compose, ViewModels Hilt, navegación type-safe y `root/` (puertas de licencia, bloqueo y onboarding) | todos |

**Reglas de diseño:**
- Clean Architecture: nada de lógica de negocio en las pantallas. Reutiliza los repositorios y casos de uso que ya existen.
- UI solo con componentes `Shalev*` y tokens, sin estilos sueltos ni `fontWeight` a mano.
- `ShalevSpacing`: xs=8, md=16, lg=24, xl=32. **No existe `sm`**.

---

## 3. Piezas clave y dónde están

### Licencia (`:licencia`)

- **`LicenseManager.evaluate()`** decide el estado: `Trial(n)`, `TrialExpired`, `Active`, `Perpetual`, `Expired`, `Revoked` o `ClockTampered`.
  - Si el reloj retrocede más de 2 h respecto a la última fecha vista (`lastSeen`), el estado es `ClockTampered`.
- **Solicitud:** cifrada, en formato `SPVIR1:` (ECIES, HKDF «SPVI-R1»). Se envía por WhatsApp (`wa.me/5351815604`) o por SMS: un texto de introducción, una línea en blanco y la parte cifrada.
- **Licencia:** el formato corto `SPVI2:` (210 caracteres, firmado). Es el único activable. En la app, «Activar» = **Pegar + Activar**. **Sin QR.**
- **Recuperación:** en un teléfono nuevo, con el «ID de la licencia anterior». El teléfono viejo queda en la lista de revocadas y se desinstala solo.
  - La lista de revocadas es `revocadas.json`, firmada, en la Release `revocaciones` de GitHub.
- **Precios (CUP):**
  - base 6000 / 30000 / 50000 / 90000 (mensual, semestral, anual, perpetua);
  - más 1000 / 5000 / 9000 / 17000 por cada secundaria, de 0 a 10;
  - la prueba cubre 5 secundarias.
- **Claves de GL:** `LicenseTrust` fija la ECDH `sha256:fb3a:f7fc:…:078d` y la firma `sha256:113f:a5f6:…:4b1c` del emisor vigente (GL 1.2.0 del teléfono `4bc9f8f3`, leídas el 22/10/2026) y conserva las cuatro firmas históricas. Una instalación GL con claves locales diferentes requiere verificar sus huellas; detalle en `LICENSE_CLIENT.md` §5.
- **Contrato:** `LICENSE_CLIENT.md`, `docs/GL_ESPECIFICACION.md` y `docs/GL_PROMPT_0.25.md`. **No copies el almacenamiento de GL ni expongas detalles internos.**

### Prueba que no se reinicia al reinstalar (0.26.0, P74)

- **Dónde se guarda:** la fecha de inicio de la prueba va cifrada (AES-GCM, clave PBKDF2 a partir de ANDROID_ID) en 4 copias:
  - `filesDir`;
  - `Pictures/Shalev/sys_<huella>.png`: la vía principal en Android 10+, se lee tras reinstalar con el permiso de fotos;
  - `.sys_<huella>.bin` en Download y en Documents: tras reinstalar solo se leen en Android 8–9.
- **Qué fecha vale:** gana el inicio más antiguo y la última fecha vista (`lastSeen`) más reciente. Una copia ilegible se ignora (**nunca bloquea**).
- **Reloj:** `DetectorRelojAndroid` usa `elapsedRealtime` dentro del mismo arranque, además de la comprobación de `lastSeen`.
- **Permisos de la primera apertura:** una ventana con el checklist (cámara, aviso de turno y fotos, cada uno con su para qué); «Continuar» abre el diálogo del sistema y «Ahora no» no insiste. Si se niegan, cada función los vuelve a pedir donde hacen falta. Sin el permiso de fotos, la prueba cuenta desde esta instalación.
- **Código:**
  - `licencia/.../prueba/RegistroPrueba.kt`;
  - `data/.../licencia/prueba/RegistroPruebaAndroid.kt`;
  - `domain/.../model/EstadoPrueba.kt`;
  - `app/.../licencia/prueba/TrialViewModel.kt`.
- **Diseño y decisiones del dueño:** `docs/PLAN_ANTIREINSTALACION.md`.

### Apps vinculadas (red local)

- **Vinculación:** con un QR. La principal ofrece un servicio en primer plano (`connectedDevice`) que va en la misma notificación del turno abierto. La notificación no muestra importes y no se restaura tras reiniciar el teléfono.
- **Licencia:** la de la principal cubre a las secundarias. Si no está activa, la secundaria muestra `BloqueoSecundaria`.
- **Cierre de turno:** el empleado solo **solicita** el cierre y el dueño lo aprueba. Nunca se cierra solo ni durante una venta.
- **Sincronización (0.32.0):** **manual**: el empleado toca «Sincronizar ahora»; además se sincroniza al abrir turno y al pedir el cierre. La secundaria **mantiene la conexión abierta** con la principal (ping de 30 s), así el dueño ve si está conectada: hay una **barra de avance** en la notificación y una por app en «Apps vinculadas», y avisos con **sonido propio** al conectar, desconectar y terminar de sincronizar. Si el dueño cambia el catálogo, la app del empleado avisa («Sincroniza para recibirlo»).
- **Fotos:** las fotos de productos y servicios **viajan** a la secundaria (lista en la instantánea + bytes por bloques de 256 KB, con SHA-256).
- **Fondo de caja (0.32.0):** lo escribe **quien abre el turno**, también en una secundaria. La principal ya no lo asigna ni lo pide (se invirtió la regla de 0.26.0 P73 §4).
- **Conteo de inventario (0.32.0):** **parcial** (solo lo que cambió en el turno) y **por categorías** (pestañas). En la principal, la diferencia se aplica sobre el valor actual.
- **Sin conexión:** la secundaria sigue vendiendo, pero necesita sincronizar para abrir un turno nuevo.
- **Actualizaciones:** la principal reparte el APK a las secundarias por la red local.
- **Detalle:** `docs/VINCULACION.md`.

### Actualizaciones (GitHub)

- **Consulta:** una vez por semana, al abrir la app, la principal mira la última Release pública de `GITHUB_REPO` (`-PshalevGithubRepo=usuario/repo`; vacío = no consulta nada).
- **Descarga:** se comprueba el SHA-256 y se instala con `REQUEST_INSTALL_PACKAGES`.
- **Obligatorias (0.26.0):** toda versión nueva es obligatoria 30 días después de que el teléfono la detecte.
  - Al vencer, `BloqueoActualizacion` bloquea toda la app. Solo deja Actualizar, Exportar respaldo y Cerrar turno.
  - Solo bloquea si de verdad existe una actualización.
- **Código:** `app/.../actualizacion/GestorActualizacion.kt`, `InstaladorApk.kt`, `domain/.../model/EstadoApp.kt`.

### Respaldo y cambio de teléfono

- **Respaldo:** solo el archivo `.shalev`, siempre cifrado; la contraseña es opcional pero viene activada por defecto desde la corrección S-01 (10/2026). Sin ella, cualquiera que obtenga el archivo puede abrirlo con Shalev. Lleva los clientes fijos (sin PDF). Un recordatorio mensual, solo en la principal.
- **Cambiar de teléfono:** exporta el respaldo en el viejo, impórtalo en el nuevo y pide la **recuperación** de la licencia (Ajustes → Licencia, con el «ID de la licencia anterior»). El teléfono viejo queda en la lista de revocadas y se desinstala solo.

---

## 4. Reglas del dueño que no se negocian (resumen; la lista completa está en `AGENTS.md`)

1. **Permisos.** Solo estos; `shalevPermisos` falla con cualquier otro:
   - `CAMERA`, `INTERNET` y `POST_NOTIFICATIONS`;
   - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE`;
   - `REQUEST_INSTALL_PACKAGES` y `REQUEST_DELETE_PACKAGES`;
   - `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (maxSdk 32) y `WRITE_EXTERNAL_STORAGE` (maxSdk 28), solo para el registro de la prueba;
   - `USE_BIOMETRIC` y `USE_FINGERPRINT` (0.27.0), solo para el acceso con clave.
2. **Internet** solo para la red local y GitHub Releases (`rmdvcd/Shalev`, actualizaciones y revocaciones); no se requieren claves de API.
3. **Cámara y SMS.** No se vende sin turno abierto. La cámara solo mientras se usa. No se pide `READ_SMS` ni se consulta el buzón; la captura opcional usa Acceso a notificaciones, que el usuario habilita manualmente, y solo procesa un SMS nuevo reconocible mientras una transferencia espera el pago. El portapapeles solo se lee al tocar «Pegar SMS»; compartir el mensaje sigue disponible.
4. **UI:**
   - Material 3 claro y oscuro, con contraste WCAG AA (`ContrastTest`).
   - `FLAG_SECURE` en las pantallas sensibles.
   - Botones de solo icono con descripción y tooltip; Guardar = ✓; diálogos centrados; pestañas como viñetas.
   - El botón «+» va **abajo a la derecha**.
   - Tipografía: bodyMedium 14 y bodySmall 13. Sin «Deshacer».
5. **Datos sensibles:** nada en los logs ni en la vista de Recientes.
6. **Documentación sin funciones inventadas.** Si cambias el comportamiento, actualiza README, MANUAL_USUARIO y FORMATOS, y añade una entrada en `docs/HISTORIAL_DESARROLLO.md`.
7. **Tests:** sin red. Todo lo que añadas debe compilar y pasar `./gradlew shalevTests`.
8. **Firma:** siempre el mismo keystore. El `deviceId` de la licencia depende de la firma.
9. **Cambios mínimos.** No refactorices lo que funciona. Ante una ambigüedad, elige la opción más segura y simple, y documéntala.

---

## 5. Estado de la verificación

La fuente de verdad es [docs/VERIFICACION.md](docs/VERIFICACION.md). El último CI observado sobre `main` (run **38802995010**, commit `84afc87`) detectó que dos tests de `ProcesoActualizarTest` no pudieron obtener el SDK instrumentado de Robolectric por la red bloqueada del proceso de tests. Lint, permisos del manifiesto y API mínima pasaron; no se debe declarar ese CI como verde.

En esta rama se ha corregido la configuración para que todos los tests de `:app` usen el SDK de Robolectric resuelto por Gradle en modo offline. La corrección aún requiere una corrida de CI. En este entorno no hay JDK/Android SDK, por lo que no se puede ejecutar Gradle localmente.

Siguen pendientes la comprobación en un teléfono real, las capturas Roborazzi y la firma de release; consulta `Pendiente.md`.

---

## 6. Comandos

```bash
./gradlew shalevTests                 # todos los tests JVM (sin dispositivo ni red)
./gradlew :app:assembleDebug        # APK debug → app/build/outputs/apk/debug/
./gradlew shalevCheck                 # shalevTests + lintDebug + assembleDebug + shalevPermisos
./gradlew :app:installDebug         # instalar en el teléfono conectado
./gradlew shalevInstrumentedTests     # tests de Room en el teléfono o emulador
./gradlew :app:recordRoborazziDebug # capturas reales claro/oscuro
./gradlew shalevRelease               # AAB + APK firmados (requiere keystore.properties)
./gradlew :domain:test --tests "cu.shalev.domain.EstadoPruebaTest"   # un solo test
```


---

## 7. Trampas conocidas (te ahorrarán tiempo)

**Hilt**
- Los constructores `@Inject` no admiten parámetros por defecto.
- Si cambias el constructor de un ViewModel, actualiza `TurnoViewModelsTest`, `PerfilTest` y `app/src/sharedTest/.../EntornoIntegracion.kt`.

**Kotlin**
- Una entrada de `enum` no puede usar una `const` de su propio companion.
- No se puede leer `CompositionLocal.current` dentro del DSL de `LazyColumn`.
- `shalevAnimateItem()` solo funciona en `LazyItemScope`.
- No hay smart cast de propiedades de otro módulo: copia el valor a un `val` local.
- No uses `fun interface` con métodos `suspend`: usa una interface normal.

**KSP**
- Si Room o Hilt fallan de forma rara, borra `build/generated/ksp` y repite.

**Datos**
- La categoría de los elaborados es `Categorias.ELABORADO = "Elaborado"`, en singular.
- Un cambio de esquema de Room exige migración, esquema exportado y test.

**fastexcel**
- Todo el estilo de una celda va en un solo setter.
- El formato CUP va escapado (`\C\U\P`): con comillas, el `styles.xml` queda inválido.

**R8**
- Los `@Serializable` del contrato GL, de las rutas y de `licencia.prueba.RegistroPrueba` tienen reglas en `app/proguard-rules.pro`.
- Si algo falla solo en release, revisa `missing_rules.txt`.

**Fakes de test**
- Los fakes viven en `domain/src/test/.../Fakes.kt`, `app/src/sharedTest/.../FakesApp.kt` y `licencia/src/test/.../FakeGl.kt`.
- Una interface de repositorio nueva necesita su fake.

---

## 8. Mapa de documentos (léelos solo si la tarea lo pide)

| Documento | Para qué |
|---|---|
| `AGENTS.md` | Reglas para agentes (OpenCode lo lee siempre) |
| `Pruebas.md` / `Pendiente.md` | Qué probar y qué falta |
| `README.md` | Arquitectura y novedades por versión |
| `RELEASE.md` | Keystore, firma, prueba de humo y publicación en GitHub |
| `PRUEBAS_DISPOSITIVO.md` | Guía larga por USB: adb y pruebas manuales por flujo |
| `FORMATOS.md` | Respaldo, PDF, Excel, PNG y registro de la prueba |
| `SECURITY.md` / `LICENSE_CLIENT.md` | Cifrado, claves y licencias |
| `DESIGN_SYSTEM.md` / `UI_UX_IX.md` | Componentes, colores y tipografía |
| `MANUAL_USUARIO.md` | Manual del usuario final |
| `docs/VINCULACION.md` | Protocolo principal ⇄ secundaria |
| `docs/PROMPT_0.27.0.md` | Especificación histórica de la versión 0.27.0 (T1–T14) |
| `docs/PLAN_0.26.md` / `docs/PLAN_ANTIREINSTALACION.md` | Decisiones de la 0.26.0 |
| `docs/HISTORIAL_DESARROLLO.md` | Registro de decisiones de cada versión |
| `docs/OPENCODE_DESKTOP.md` | Guía paso a paso con OpenCode Desktop |

## Alcance del repositorio

La aplicación Android nativa sigue siendo el producto principal. Se retiraron los antiguos proyectos `desktop/` y `flutter_app/` con sus dependencias, pruebas y workflows; la contraparte actual `IShalev/` es un proyecto SwiftUI nuevo y separado, no una restauración del Flutter histórico. La sincronización Android principal/secundaria, el formato `.shalev` y el cliente de licencias GL siguen activos en Android. Las menciones a las plataformas retiradas en `docs/HISTORIAL_DESARROLLO.md` y las auditorías fechadas son contexto histórico, no instrucciones para modificar el código Android.
