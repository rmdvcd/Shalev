# IShalev — Shalev nativo para iOS

Subproyecto independiente en **SwiftUI**, pensado como contraparte iOS de la aplicación Android del repositorio. Conserva la marca y el flujo de un punto de venta offline, pero usa controles nativos de Apple; no es una conversión de Flutter ni un contenedor web.

> **Estado y límites:** IShalev incorpora un asistente inicial de cuatro pasos (bienvenida, módulos, datos y resumen), módulos del negocio, licencias GL, bloqueo con autenticación del dispositivo, perfiles y permisos locales por empleado, fotos, análisis manual de SMS, corrección/anulación de ventas, exportación XLSX/CSV/PDF/PNG y respaldos iOS/Android. Las pruebas de dominio y la compilación para simulador se verifican en macOS mediante GitHub Actions, y el workflow publica la build en Appetize. Las pruebas funcionales interactivas y la activación extremo a extremo contra GL siguen siendo necesarias. La sincronización principal/secundaria por red local está integrada con el protocolo de Shalev Android, pero todavía falta validar una sesión real iOS↔Android. Los perfiles locales y sus PIN no se comparten. iOS no puede leer automáticamente SMS ni auto-instalar actualizaciones; la distribución debe usar TestFlight, App Store o un canal autorizado.

## Contenido

- `IShalev.xcodeproj` — proyecto Xcode compartido y esquema `IShalev`.
- `Sources/App` — navegación nativa en cinco pestañas.
- `Sources/Domain` — modelos y reglas del negocio, precios enteros en centavos, persistencia AES-GCM, codecs de respaldo iOS/Android y protocolo/transporte/repositorio de sincronización LAN.
- `Sources/Design` — paleta adaptativa de Shalev, controles reutilizables, QR Transfermóvil y las piezas de accesibilidad e interacción: `ShalevDynamicType` y `Font.shalev` (tipografía que escala), `shalevToque()` (área táctil mínima), `ShalevMotion` (Reduce Motion), `ShalevBarBackground` (Reduce Transparency) y `ShalevHaptics` (vibración breve).
- `Sources/Features` — Inicio, Inventario, Venta, Servicios, Registros y Ajustes.
- `Resources` — icono, logo, tipografía Orbitron y licencia OFL.
- `.github/workflows/ios-build.yml` — compilación de simulador y artefacto `.app` empaquetado para Appetize.

## Funciones disponibles

- **Inicio:** bloque fijo con el turno (interruptor, «Desde las HH:MM» y efectivo esperado), el período de consulta —Turno por defecto— y el banner de licencia; alertas de stock/caducidad; accesos a cobro (teléfono y cuenta enmascarada) y precios; acordeones de ventas, ganancia, inventario, métodos de pago y **tres Top 3** (más vendido, lento movimiento y rentabilidad). **Nueva venta** y **Movimiento de caja** son botones flotantes en las esquinas y solo aparecen con el turno abierto.
- **Caja:** apertura con fondo, entradas y salidas con motivo, cálculo de efectivo esperado y cierre con conteo.
- **Inventario:** alta, edición y baja lógica de productos, insumos y elaborados; búsqueda, filtro por tipo y por alerta en la barra superior, existencias, precios, umbrales de alerta, caducidad, recetas de insumos y fotos opcionales. Selección múltiple con exportación o eliminación en bloque, ficha del artículo con Editar/Compartir/Eliminar y exportación en XLSX, CSV o PDF.
- **Servicios:** catálogo con tipos libres, filtro por tipo en la barra superior y consumo opcional de insumos.
- **Venta:** pantalla completa por pasos, como Android: carrito (menos, cantidad, más y papelera), método de pago en dos botones, comprobante para efectivo y QR con número de transacción y cliente fijo para transferencia. No permite vender con el turno cerrado. Permite pegar mensajes de Transfermóvil para extraer número e importe; el empleado confirma manualmente que recibió el pago. No lee SMS ni notificaciones. Los elaborados y servicios con receta descuentan insumos en la misma operación.
- **QR de Transfermóvil:** utiliza el formato `TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta>,<móvil>,`. El importe se muestra debajo y **no** se inventa dentro del QR; la app no verifica el pago con el banco.
- **Registros:** ventas, servicios, transferencias, movimientos, clientes fijos y turnos, con búsqueda y filtro por fecha (hoy, 7 días, mes o rango) e importe mínimo y máximo. Las ventas del turno abierto se pueden modificar o anular con motivo; la corrección conserva el historial y repone/reaplica existencias de forma atómica.
- **Ajustes:** una sola lista con el orden de Android (completar configuración con los pasos pendientes, perfil, licencia, apps vinculadas, pago electrónico, precios, permisos, acceso con clave, respaldo, actualizar, ayuda y soporte).
- **Empleados y permisos:** perfiles locales seleccionables, con permisos para ventas, inventario/servicios, registros, caja, cambios de precios, exportación y correcciones. Cada empleado tiene un PIN propio de 6 a 12 dígitos; se almacena un verificador PBKDF2-HMAC-SHA256 con sal aleatoria, nunca el PIN en claro. Cinco intentos fallidos bloquean el perfil durante 15 minutos y se solicita el PIN de nuevo al volver a la app. Volver al administrador exige Face ID o el código del dispositivo. Los perfiles todavía no se sincronizan con Android.
- **Módulos:** Ventas, Inventario y Servicios se pueden activar/desactivar en onboarding y Ajustes; Ventas requiere y activa Inventario.
- **Precios:** descuentos/recargos con método de pago e importe mínimo opcionales.
- **Exportación:** libro Excel `.xlsx` con hojas y filas tabulares, CSV con BOM UTF-8 y separador `;`, PDF y listas/tarjetas PNG sin costos ni existencias para compartir con clientes.
- **Soporte y actualizaciones:** ficha de contacto con WhatsApp/SMS; consulta manual de publicaciones GitHub `ios-v*`. La comprobación no descarga ni instala una versión.
- **Sincronización LAN:** Ajustes → Sincronización permite actuar como principal o vincularse como secundaria mediante el QR `SPVI-VINC1:` de Android. Usa Bonjour/TCP, tramas v1, ECDH P-256 autenticado con el código temporal, HKDF y AES-256-GCM con claves por sentido. La clave de empleado y la configuración sensible se guardan en Keychain, fuera de los respaldos. La secundaria sincroniza turnos, ventas, inventario y caja diferidos, correcciones, aprobaciones de conteo y solicitudes de cierre; catálogo y preajustes se modifican mediante comandos en la principal. Las fotos del catálogo se transfieren por bloques y solo se cambian desde la principal. Una principal iOS firma el sub-token de licencia de cada secundaria cuando su licencia tiene cuota; una secundaria iOS verifica firma, dispositivo, ranura, cuota y vencimiento, y admite el formato anterior sin sub-token.
- **Privacidad:** funcionamiento local, sin cuenta, servidor en Internet, analítica ni anuncios. La sincronización se limita a la red local y requiere vinculación. La base JSON se cifra con AES-GCM y una clave aleatoria guardada en Keychain; el archivo tiene protección de datos de iOS.
- **Respaldo iOS:** archivo `.shalev-ios` cifrado con AES-GCM y clave derivada con PBKDF2-HMAC-SHA256. Conserva el formato completo propio de IShalev; la contraseña no se almacena.
- **Intercambio con Android:** importa respaldos `SPVIBAK` v3/v4, incluidos los archivos v4 sin contraseña del usuario, y exporta `.shalev` v4 con contraseña. Convierte explícitamente unidades Android a milésimas iOS, mantiene los importes en centavos y migra catálogo, recetas, servicios, ventas, turnos, caja, clientes, preajustes y empleados. Los PIN y secretos de licencia no viajan. Las fotos iOS se incluyen como campos adicionales que Android ignora. Si una cantidad de producto no puede representarse en unidades enteras Android, la exportación se detiene en vez de truncarla.

## Accesibilidad e interacción

Aplicado tras la auditoría del 2026-10-28 contra la guía UI/UX/IX (informe completo en [`docs/AUDITORIA_UI_UX_IX_2026-10-28.md`](../docs/AUDITORIA_UI_UX_IX_2026-10-28.md)):

- **Dynamic Type:** las 483 tipografías de la app pasan por `Font.shalev(_:weight:design:)` (y `Font.shalevMarca(_:)` para Orbitron), que multiplica el tamaño del design system por el factor de la categoría activa. La categoría la instala la raíz leyendo `@Environment(\.dynamicTypeSize)`; en **Large** (la del sistema por defecto) el factor es 1, así la interfaz no cambia. Tope actual 1,6× («Accesibilidad 1») hasta portar el reflow de filas y barras que Android resuelve con `letraGrande()`.
- **Área táctil mínima de 44 pt:** `View.shalevToque()` aplica `frame(minWidth:minHeight:)` + `contentShape(Rectangle())` **fuera** del dibujo, así crece la zona del dedo y no el icono. Cubre los botones de icono, los chevrons del asistente, los chips de filtro, las insignias «Quitar filtro», la casilla de selección de Inventario y los botones de editar y borrar.
- **Reducir movimiento:** `ShalevMotion.cambio(_:)` devuelve `nil` cuando el ajuste está activo, así el cambio de pestaña, el filtro, el FAB expandible y los acordeones de Ayuda se aplican de inmediato. El `scaleEffect` del botón primario también se anula; queda la opacidad como respuesta al toque.
- **Reducir transparencia:** las tres barras flotantes (pestañas, pie del asistente y pie de la venta) usan `ShalevBarBackground`, que cambia el material translúcido por una superficie opaca con el ajuste activo.
- **Vibración:** `ShalevHaptics.exito()` al registrar una venta y al leer o pegar un código de vinculación; `.error()` cuando la operación falla. Los tres nombres (`exito`, `error`, `seleccion`) son los mismos que en `ShalevHaptics` de Android y no piden permisos nuevos.
- **VoiceOver:** etiquetas en todos los indicadores de progreso («Cargando», «Vinculando…», «Sincronizando…»), valor accesible en el asistente («Paso 2 de 3: título») y en la barra de pestañas («Pestaña 2 de 5»), y la barra de progreso del asistente marcada como decorativa para no duplicar la lectura.

**Desviación aceptada:** la barra inferior mantiene **solo iconos** (cinco destinos), igual que Android, aunque la guía de iOS pide etiquetas de texto. Se compensa con el nombre y la posición por VoiceOver, iconos SF Symbols inequívocos y una selección que se ve por relleno e inversión, no solo por color.

## Requisitos y compilación local

- Mac con Xcode 15 o posterior.
- iOS 17 o posterior (deployment target 17.0).
- No requiere CocoaPods ni paquetes de terceros.

Abre `IShalev.xcodeproj`, selecciona el esquema **IShalev** y un simulador. También se puede compilar desde Terminal:

```bash
xcodebuild \
  -project IShalev.xcodeproj \
  -scheme IShalev \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  CODE_SIGNING_ALLOWED=NO \
  build

swift test --package-path IShalev # pruebas unitarias del dominio iOS
```

El proyecto de Android que lo acompaña sigue en la raíz del repositorio. Las pruebas unitarias verifican reglas de venta/cierre, clientes, preajustes, PIN y licencias, además de vectores independientes `SPVIBAK` v3/v4, archivos sin contraseña, escalas de cantidades y estabilidad de IDs al convertir respaldos Android. La compilación y las pruebas automatizadas se ejecutan en GitHub Actions sobre macOS; el entorno de desarrollo local es Linux y carece de Xcode/`swift`.

## Compilar y probar online sin coste

### Verificación registrada

- El run [`40155895866`](https://github.com/rmdvcd/Shalev/actions/runs/40155895866), del commit `c2a6cef` (merge de la sincronización LAN), terminó con las pruebas de dominio, la compilación para simulador, el empaquetado y la subida a Appetize en verde.
- La build de la alineación visual con Android (commit `88093c8`) está en [appetize.io/app/7btghjp2eqikniydplb3y76b5y](https://appetize.io/app/7btghjp2eqikniydplb3y76b5y).
- El run anterior publicó la build [`IShalev 1.0.0` en Appetize](https://appetize.io/app/kczay4chapvuf53xy6w62fyy7a), comprobada el 26/10/2026: la app carga en el simulador como `IShalev` v1.0.0 sobre iPhone 14 Pro con iOS 17.2, y la versión incrustable responde en `appetize.io/embed/kczay4chapvuf53xy6w62fyy7a`. La sesión interactiva no arranca sola: se inicia con **Tap to Start** en el navegador.
- Cada ejecución del workflow publica una **app nueva** de Appetize mientras no se configure el secreto `APPETIZE_PUBLIC_KEY`; la URL se anota en el resumen de cada run («Appetize build: …»). Configura ese secreto con la clave pública de una app existente para actualizarla en lugar de crear otra.
- Los flujos internos (onboarding, activación GL, vinculación LAN y permisos del simulador) siguen pendientes de una sesión interactiva.
- Estas comprobaciones no sustituyen pruebas en un iPhone físico, activación real con GL ni una sesión de sincronización con Android.

### 1. GitHub Actions — opción recomendada para compilar

El workflow de la raíz `.github/workflows/ios-build.yml` compila para **iOS Simulator** en un runner macOS y guarda `IShalev-iOS-Simulator.zip` como artefacto durante siete días. En repositorios públicos, los runners macOS estándar de GitHub Actions son gratuitos; en repositorios privados se consumen los minutos incluidos en el plan. [Política de uso de Actions](https://docs.github.com/actions/reference/usage-limits-billing-and-administration) · [Runners disponibles](https://docs.github.com/actions/using-jobs/choosing-the-runner-for-a-job).

### 2. Codemagic — alternativa con cuota gratis

Para una cuenta personal/hobby, el plan gratuito incluye **500 minutos mensuales de macOS M2**, que se renuevan al inicio del mes. Se puede conectar el repositorio y configurar la compilación con `xcodebuild`; el plan individual no comparte esos minutos con equipos. [Precios de Codemagic](https://docs.codemagic.io/billing/pricing/) · [Ejemplo de compilación iOS Simulator](https://docs.codemagic.io/yaml-code-signing/ios-simulator-builds/).

### 3. Appetize — ejecutar el simulador en el navegador

Después de compilar, se puede subir el `.app` de simulador (o el ZIP del artefacto de Actions) a Appetize para probarlo desde un navegador. El plan gratuito indicado por Appetize ofrece **30 minutos al mes**, hasta dos dispositivos activos y sesiones de hasta tres minutos. Appetize sirve para **emular/mostrar**, no para compilar el código, y requiere una compilación de simulador; no acepta un `.ipa` de distribución como sustituto. [Planes de Appetize](https://appetize.io/pricing) · [Formato iOS admitido](https://docs.appetize.io/platform/app-management/uploading-apps/ios).

Para ejecutarla, abre `https://appetize.io/app/<publicKey>` o incrusta `https://appetize.io/embed/<publicKey>?device=iphone14pro&osVersion=17.2`; la sesión empieza al pulsar **Tap to Start** y los parámetros `device`/`osVersion` fijan el simulador (iOS 17.2 cubre el objetivo mínimo 17.0).

### 4. Xcode Cloud — si ya se paga Apple Developer Program

Apple incluye 25 horas de cómputo al mes de Xcode Cloud **dentro de la membresía paga** de Apple Developer Program. Compila y ejecuta pruebas en la nube; no es una opción gratuita para una cuenta Apple gratuita sin esa membresía. [Requisitos y cuota de Xcode Cloud](https://developer.apple.com/xcode-cloud/get-started/).

**Flujo práctico gratis:** GitHub Actions (compilar y descargar ZIP) → Appetize (interactuar con el simulador en el navegador). Para un repositorio privado, Codemagic da una cuota mensual separada. La distribución en un iPhone físico o por TestFlight tiene requisitos de firma y cuenta de Apple que son distintos de compilar para simulador.

## Seguridad y límites conocidos

- La clave local AES-GCM vive en Keychain con `ThisDeviceOnly`; desinstalar, perder el llavero o cambiar de dispositivo no convierte el archivo local en un respaldo portable. Usa **Respaldo** y conserva la contraseña para migrar.
- Las copias `.shalev-ios` y `.shalev` son formatos distintos, pero Ajustes → Respaldo detecta ambos al importar y permite elegir el formato al exportar. La licencia y los PIN/verificadores locales no se incluyen en la migración Android.
- Onboarding, solicitud/activación y validación local GL, bloqueo del dispositivo y permisos locales ya tienen interfaz y lógica portadas. La activación, revocación, trial y recuperación **no** se han validado end-to-end contra GL; las seis anclas SPKI públicas se verificaron contra Android y no se deben regenerar ni sustituir.
- Los PIN por empleado se guardan como verificador PBKDF2-HMAC-SHA256 con sal aleatoria dentro de la base cifrada local; nunca se almacena el PIN en claro. Cada perfil se vuelve a bloquear al pasar la app a segundo plano y al iniciar otra sesión. Cambiar al administrador exige autenticación del dispositivo. Los perfiles/permisos siguen siendo locales y no se sincronizan con las apps secundarias ni con Android.
- La sincronización LAN implementa el protocolo v1 y los flujos de trabajo integrados, pero aún no se ha ejecutado una sesión iOS↔Android ni una prueba en dos dispositivos físicos. No se declara paridad validada. No sincroniza PIN/perfiles locales ni instala actualizaciones Android; las peticiones de APK responden como no disponibles.
- El comprobador de actualizaciones consulta solo publicaciones GitHub `ios-v*`; no descarga ni instala. iOS distribuye nuevas versiones por TestFlight, App Store o gestión autorizada. Appetize recibe una build de simulador desde Actions.
- El SMS se pega manualmente y se analiza; iOS no permite capturar SMS/notificaciones de otra app. La exportación de tablas ofrece XLSX y CSV.
- El proyecto no contiene claves de firma ni certificados de distribución.
