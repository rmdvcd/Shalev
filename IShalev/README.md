# IShalev — Shalev nativo para iOS

Subproyecto independiente en **SwiftUI**, pensado como contraparte iOS de la aplicación Android del repositorio. Conserva la marca y el flujo de un punto de venta offline, pero usa controles nativos de Apple; no es una conversión de Flutter ni un contenedor web.

> **Estado y límites:** IShalev ya incorpora onboarding, módulos del negocio, flujo de solicitud/activación y validación local de licencias GL, bloqueo con autenticación del dispositivo, perfiles y permisos locales por empleado, fotos, pegado/análisis manual de SMS, corrección/anulación de ventas, exportación PDF/CSV, ayuda, soporte y consulta de publicaciones iOS. Estos flujos aún necesitan compilación y pruebas funcionales en macOS/Appetize. No hay todavía sincronización iOS↔Android ni perfiles compartidos entre teléfonos. iOS no puede leer automáticamente SMS ni auto-instalar una actualización descargada; la distribución debe usar TestFlight, App Store o un canal autorizado. La exportación «Excel» es CSV compatible con Excel, no `.xlsx`. El respaldo iOS conserva su formato cifrado propio `.shalev-ios`, incompatible con el `.shalev` de Android.

## Contenido

- `IShalev.xcodeproj` — proyecto Xcode compartido y esquema `IShalev`.
- `Sources/App` — navegación nativa en cinco pestañas.
- `Sources/Domain` — modelos y reglas del negocio, precios enteros en centavos, persistencia AES-GCM, copia de seguridad con contraseña.
- `Sources/Design` — paleta adaptativa de Shalev, controles reutilizables y QR Transfermóvil.
- `Sources/Features` — Inicio, Inventario, Venta, Servicios, Registros y Ajustes.
- `Resources` — icono, logo, tipografía Orbitron y licencia OFL.
- `.github/workflows/ios-build.yml` — compilación de simulador y artefacto `.app` empaquetado para Appetize.

## Funciones disponibles

- **Inicio:** estado de turno, períodos de consulta, alertas de stock/caducidad, accesos a cobro y precios, acordeones de ventas, ganancia, inventario, métodos de pago y Top 3.
- **Caja:** apertura con fondo, entradas y salidas con motivo, cálculo de efectivo esperado y cierre con conteo.
- **Inventario:** alta, edición y baja lógica de productos, insumos y elaborados; búsqueda, filtros, existencias, precios, umbrales de alerta, caducidad, recetas de insumos y fotos opcionales seleccionadas desde la fototeca del sistema.
- **Servicios:** catálogo con tipos libres y consumo opcional de insumos.
- **Venta:** carrito, cantidades, cobro en efectivo o transferencia, número de transacción y cliente fijo. No permite vender con el turno cerrado. Permite pegar mensajes de Transfermóvil para extraer número e importe; el empleado confirma manualmente que recibió el pago. No lee SMS ni notificaciones. Los elaborados y servicios con receta descuentan insumos en la misma operación.
- **QR de Transfermóvil:** utiliza el formato `TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta>,<móvil>,`. El importe se muestra debajo y **no** se inventa dentro del QR; la app no verifica el pago con el banco.
- **Registros:** ventas, servicios, transferencias, movimientos, clientes fijos y turnos. Las ventas del turno abierto se pueden modificar o anular con motivo; la corrección conserva el historial y repone/reaplica existencias de forma atómica.
- **Empleados y permisos:** perfiles locales seleccionables, con permisos para ventas, inventario/servicios, registros, caja, cambios de precios, exportación y correcciones. Al volver de un perfil de empleado al administrador se requiere Face ID o el código del dispositivo. No hay PIN individual ni sincronización con Android.
- **Módulos:** Ventas, Inventario y Servicios se pueden activar/desactivar en onboarding y Ajustes; Ventas requiere y activa Inventario.
- **Precios:** descuentos/recargos con método de pago e importe mínimo opcionales.
- **Exportación:** PDF y CSV con BOM UTF-8 y separador `;`; el CSV abre en Excel, pero no es un libro `.xlsx`.
- **Soporte y actualizaciones:** ficha de contacto con WhatsApp/SMS; consulta manual de publicaciones GitHub `ios-v*`. La comprobación no descarga ni instala una versión.
- **Privacidad:** funcionamiento local, sin cuenta, servidor, analítica ni anuncios. La base JSON se cifra con AES-GCM y una clave aleatoria guardada en Keychain; el archivo tiene protección de datos de iOS.
- **Respaldo iOS:** archivo `.shalev-ios` cifrado con AES-GCM y clave derivada con PBKDF2-HMAC-SHA256. La contraseña no se almacena. Si se pierde, el respaldo no se puede descifrar.

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
```

El proyecto de Android que lo acompaña sigue en la raíz del repositorio. Los 17 archivos Swift pasan revisión sintáctica con Tree-sitter y el proyecto Xcode pasa parseo estructural; esto **no** sustituye el type-check ni la compilación. El entorno de desarrollo es Linux y carece de Xcode/`xcodebuild`; la compilación real debe confirmarse en GitHub Actions con macOS.

## Compilar y probar online sin coste

### 1. GitHub Actions — opción recomendada para compilar

El workflow de la raíz `.github/workflows/ios-build.yml` compila para **iOS Simulator** en un runner macOS y guarda `IShalev-iOS-Simulator.zip` como artefacto durante siete días. En repositorios públicos, los runners macOS estándar de GitHub Actions son gratuitos; en repositorios privados se consumen los minutos incluidos en el plan. [Política de uso de Actions](https://docs.github.com/actions/reference/usage-limits-billing-and-administration) · [Runners disponibles](https://docs.github.com/actions/using-jobs/choosing-the-runner-for-a-job).

### 2. Codemagic — alternativa con cuota gratis

Para una cuenta personal/hobby, el plan gratuito incluye **500 minutos mensuales de macOS M2**, que se renuevan al inicio del mes. Se puede conectar el repositorio y configurar la compilación con `xcodebuild`; el plan individual no comparte esos minutos con equipos. [Precios de Codemagic](https://docs.codemagic.io/billing/pricing/) · [Ejemplo de compilación iOS Simulator](https://docs.codemagic.io/yaml-code-signing/ios-simulator-builds/).

### 3. Appetize — ejecutar el simulador en el navegador

Después de compilar, se puede subir el `.app` de simulador (o el ZIP del artefacto de Actions) a Appetize para probarlo desde un navegador. El plan gratuito indicado por Appetize ofrece **30 minutos al mes**, hasta dos dispositivos activos y sesiones de hasta tres minutos. Appetize sirve para **emular/mostrar**, no para compilar el código, y requiere una compilación de simulador; no acepta un `.ipa` de distribución como sustituto. [Planes de Appetize](https://appetize.io/pricing) · [Formato iOS admitido](https://docs.appetize.io/platform/app-management/uploading-apps/ios).

### 4. Xcode Cloud — si ya se paga Apple Developer Program

Apple incluye 25 horas de cómputo al mes de Xcode Cloud **dentro de la membresía paga** de Apple Developer Program. Compila y ejecuta pruebas en la nube; no es una opción gratuita para una cuenta Apple gratuita sin esa membresía. [Requisitos y cuota de Xcode Cloud](https://developer.apple.com/xcode-cloud/get-started/).

**Flujo práctico gratis:** GitHub Actions (compilar y descargar ZIP) → Appetize (interactuar con el simulador en el navegador). Para un repositorio privado, Codemagic da una cuota mensual separada. La distribución en un iPhone físico o por TestFlight tiene requisitos de firma y cuenta de Apple que son distintos de compilar para simulador.

## Seguridad y límites conocidos

- La clave local AES-GCM vive en Keychain con `ThisDeviceOnly`; desinstalar, perder el llavero o cambiar de dispositivo no convierte el archivo local en un respaldo portable. Usa **Respaldo** y conserva la contraseña para migrar.
- La copia `.shalev-ios` es incompatible con el formato Android `.shalev`.
- En esta fase no se migran clientes, licencias, empleados ni datos Android.
- Onboarding, solicitud/activación y validación local GL, bloqueo del dispositivo y permisos locales ya tienen interfaz y lógica portadas. La activación, revocación, trial y recuperación **no** se han validado end-to-end contra GL; las seis anclas SPKI públicas se verificaron contra Android y no se deben regenerar ni sustituir.
- Las cuentas/permisos por empleado son perfiles locales de selección manual en este iPhone, sin PIN propio; la vuelta al administrador exige autenticación del dispositivo. No se sincronizan con las apps secundarias ni con Android.
- La sincronización principal/secundaria de Shalev Android y su red local no está presente.
- El comprobador de actualizaciones consulta solo publicaciones GitHub `ios-v*`; no descarga ni instala. iOS distribuye nuevas versiones por TestFlight, App Store o gestión autorizada. Appetize recibe una build de simulador desde Actions.
- El SMS se pega manualmente y se analiza; iOS no permite capturar SMS/notificaciones de otra app. La exportación «Excel» es CSV UTF-8, no `.xlsx`.
- El proyecto no contiene claves de firma ni certificados de distribución.
- El proyecto no contiene claves de firma ni certificados de distribución.
