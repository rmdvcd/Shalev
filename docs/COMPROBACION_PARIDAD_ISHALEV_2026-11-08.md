# Comprobación de paridad funcional: IShalev frente a Shalev Android

**Fecha:** 2026-11-08
**Base analizada:** `cd4e32b` (HEAD de `main` al iniciar la revisión)
**Rama de trabajo:** `arena/8f193797-shalev`
**Tipo de revisión:** estática (fuentes, tests y ejecuciones de CI visibles); no se ejecutaron apps en dispositivos.

## Dictamen

**IShalev no está todavía en un estado equivalente y verificado respecto a Shalev Android.** Es una contraparte SwiftUI sustancial, con la mayor parte del ciclo de caja, venta, inventario, servicios, registros, respaldo/licencia y un protocolo LAN implementado. Sin embargo, hay diferencias funcionales concretas, divergencias deliberadas de plataforma y —más importante— el código iOS revisado tiene una inconsistencia en `MainSection` que impide compilar tal como está: se usa `.tools` en varios `switch`, pero el enum no declara ese caso.

La propia `IShalev/README.md` declara que la sesión real iOS↔Android aún no se ha probado y que no se declara paridad validada. La conclusión de esta comprobación coincide con esa advertencia: **paridad funcional parcial, no paridad de producto certificada**.

## Resumen de resultados

| Área | Estado | Observación |
|---|---|---|
| Venta, turno y caja | **Cercana** | IShalev implementa venta con turno, métodos efectivo/transferencia, arqueo, entradas/salidas y corrección; pruebas de dominio cubren reglas compartidas. Falta validación iOS↔Android en dos dispositivos. |
| Inventario, recetas y servicios | **Cercana, con una diferencia de alcance** | Están presentes productos/insumos/elaborados y servicios con consumo; Android tiene selección de **local** que filtra catálogo e historial; IShalev no tiene un modelo/campo de local. |
| Registros, clientes y ventas corregidas | **Cercana** | Las pestañas principales de registro y la anulación/corrección existen en ambas; existen tests iOS de reglas de dominio. |
| Navegación y secciones | **No equivalente; iOS además presenta error de compilación** | Android tiene cinco destinos principales, incluido Herramientas; Ajustes se abre desde Inicio. IShalev declara cinco secciones pero el enum incluye Ajustes y no Herramientas, aunque el código intenta presentar `ToolsView` mediante `.tools`. |
| Onboarding | **Parcialmente equivalente** | IShalev configura módulos y datos, pero no reproduce el recorrido Android de tipo principal/secundaria, acceso opcional y cantidad de empleados. La vinculación está disponible en Ajustes. |
| Sync principal/secundaria | **Implementación presente, sin equivalencia extremo a extremo verificada** | Existe protocolo LAN v1, intercambio y flujo de roles; la documentación confirma que no se ha probado una sesión física iOS↔Android. |
| Respaldo e intercambio Android | **Compatibilidad implementada con diferencias** | IShalev lee/escribe formatos Android y mapea datos, pero hay extensiones y datos locales sin equivalencia directa. |
| OCR / importación documental | **Divergencia de producto** | IShalev conserva el flujo OCR/importación; Android lo retiró el 2026-10-31. Es una función extra iOS, no paridad. |
| Verificación de build | **No satisfactoria en el estado visible** | La última CI iOS consultada falla al compilar el simulador; las pruebas del dominio pasan en esa ejecución. La CI Android actual también tiene fallo en JVM tests, aunque compila APK debug/release. No se pudo leer el log de fallo por error de descarga de Actions. |

## Hallazgos que impiden afirmar equivalencia

### P0 — El destino Herramientas iOS no está coherente con su enum de navegación

En `IShalev/Sources/App/AppRoot.swift`, `MainSection` declara `home`, `inventory`, `services`, `records` y `settings`; sin embargo, el mismo archivo usa `case .tools` en el símbolo de sección y en los `switch` de navegación/visibilidad, y tiene un branch que muestra `ToolsView`. El enum no declara `.tools` (revisado en líneas 43–59 y los `switch` posteriores). Swift no permite referenciar un caso inexistente; esto concuerda con que el último workflow iOS disponible termina fallando en **“Compilar sin firma para el simulador”**.

También hay una diferencia de arquitectura de navegación respecto a Android:

- Android: Inicio, Inventario, Servicios, Registros y Herramientas en los destinos superiores; Ajustes se abre desde Inicio.
- IShalev: enum de cinco secciones con Ajustes, sin Herramientas declarada; Settings/Home tiene rutas de acceso cruzadas, pero Tools queda sin destino válido.

**Criterio para cerrar:** definir una navegación iOS compilable que contenga Herramientas, preserve acceso a Ajustes y documente si se busca paridad estricta de los cinco destinos o una adaptación nativa; luego pasar `xcodebuild`/CI y probar todos los destinos.

### P1 — No aparece el modelo Android de locales en IShalev

Android tiene `AlmacenLocalEntity`, repositorio y selector en Inicio/Ajustes. DAOs filtran productos, insumos, servicios, movimientos, ventas y turnos por el local seleccionado; las escrituras registran el local. La base actual se versiona en `ShalevDatabase` y la migración 15→16 introduce esa dimensión.

En IShalev, `AppData` contiene settings, productos, servicios, ventas, turnos, movimientos, clientes y empleados, pero no una lista de locales ni un `localID` en los registros del modelo. No encontré selector ni filtro equivalente. Por tanto, mover el mismo respaldo desde Android a iOS puede importar registros pero **no conserva la separación/selección por local** como concepto operativo.

**Criterio para cerrar:** elegir una de dos opciones de producto y dejarla explícita:

1. portar locales, reglas de selección, filtros, asociación del historial y compatibilidad de respaldo/sync; o
2. limitar oficialmente la portabilidad a un único local, mostrando advertencia/previsualización y definiendo qué datos del resto de locales se importan.

No se debe afirmar paridad de inventario/historial mult local mientras esta diferencia no se resuelva.

### P1 — La integración LAN sigue sin prueba de interoperabilidad real

IShalev implementa Bonjour/TCP, protocolo v1, cifrado de sesión y comandos, y hay pruebas con vectores independientes y datos serializados. Pero `IShalev/README.md` dice expresamente que aún no se ha ejecutado una sesión iOS↔Android ni una prueba con dos dispositivos físicos. La compatibilidad de formato/criptografía en tests unitarios no cubre permisos del SO, descubrimiento Bonjour, modo de red, estados de fondo, reintentos, fotos ni flujos de aprobación en un teléfono.

**Criterio para cerrar:** prueba documentada de al menos iPhone ↔ Android en rol principal/secundaria en ambas direcciones, en Wi‑Fi y hotspot, incluyendo venta offline, reenvío duplicado, cambio de catálogo, fotos, cierre/conteo aprobado, expulsión de empleado y revocación/licencia. No mover PIN/perfiles locales si esa sigue siendo la decisión de seguridad.

### P1 — Onboarding no reproduce todos los pasos de Android

El onboarding iOS tiene cuatro pasos: bienvenida, módulos, datos y resumen (`OnboardingView.swift`). Android incluye pasos propios para principal/secundaria, acceso con clave, objetivo, número de empleados, datos, alertas y prueba; una secundaria puede dirigirse a vinculación. En IShalev, el cambio de rol/vinculación se encuentra en Ajustes → Sincronización, y no aparece en los cuatro pasos iniciales.

Es una adaptación viable, pero no es el mismo flujo: puede hacer que una persona que instala el teléfono del empleado llegue primero a un espacio principal vacío y descubra tarde dónde vincularse.

**Criterio para cerrar:** probar instalación nueva como empleado sin asistencia. Si se conserva configuración posterior, añadir en onboarding una elección clara de principal/secundaria o una llamada a la acción inmediata a “Vincular con la app principal”, sin obligar a definir cantidad de empleados si el modelo iOS no la necesita.

### P2 — Diferencias entre las plataformas que deben permanecer explícitas

- **OCR:** iOS conserva `OcrImportView` y `OcrDocumentParser`; Android retiró importación documental/OCR el 2026-10-31. El usuario recibe capacidades distintas según dispositivo.
- **Perfiles de empleado:** iOS admite varios perfiles locales con PIN local y bloqueo; Android se basa en instalación secundaria por empleado y permisos concedidos por la principal. IShalev documenta que los PIN/perfiles locales no se sincronizan. Son modelos distintos, no equivalentes funcionales.
- **SMS y actualizaciones:** IShalev solo analiza texto pegado/compartido y no instala actualizaciones; Android tiene captura opcional mediante acceso especial a notificaciones y puede distribuir el APK Android por LAN. Son restricciones de plataforma razonables; explicar la diferencia, no prometer paridad literal.
- **Herramientas / fichas de costo:** Android describe la ficha de costo como temporal en memoria; `ToolsView` de iOS contiene fichas guardadas en `AppStorage` y edición/exportación a producto/servicio. IShalev tiene mayor persistencia en este módulo, no equivalencia exacta.
- **Fotos en backup:** iOS puede incluir `fotoData` como extensión en archivos de intercambio Android; el importador Android ignora campos extra y no ofrece el mismo tratamiento. La transferencia de fotos también es específica por plataforma.

Estas divergencias no tienen por qué bloquear el uso de IShalev, pero deben figurar en una matriz oficial de compatibilidad por función y formato.

## Funciones con paridad sustancial observada

La lectura de `IShalev/README.md`, `Domain.swift`, `AndroidBackup.swift`, `SyncManager.swift` y los módulos Android muestra implementación compartida o equivalente en estas capacidades:

- inicio con resumen de negocio, período, gráficos/rankings, alertas y acceso a venta/caja;
- catálogo de productos, insumos, elaborados, servicios y recetas con consumo de insumos;
- venta en efectivo o transferencia/QR y registro de cliente/referencia;
- prohibición de venta sin turno, caja con fondo y conteo, corrección/anulación con historial;
- filtros de movimientos/ventas, clientes fijos y exportación;
- ajustes de precios, licencia GL, respaldo cifrado y lectura/escritura de respaldo Android;
- permisos separados entre principal y secundaria, con cambios de catálogo controlados por la principal;
- accesibilidad iOS trabajada (Dynamic Type, objetivos táctiles, VoiceOver, Reduce Motion/Transparency), con reglas equivalentes nativas a la auditoría Android.

Esta lista acredita portado significativo del dominio; **no** sustituye la prueba de paridad de cada borde ni comprueba el comportamiento interactivo.

## Estado de verificación observado

- **iOS:** el workflow más reciente disponible consultado es [`41930040832`](https://github.com/rmdvcd/Shalev/actions/runs/41930040832), 2026-11-04. `swift test` termina en verde, pero falla la compilación iOS Simulator. No hay en el historial de Actions inspeccionado una corrida posterior para el commit actual. En este entorno no está instalado `swift`, así que no pude reproducirlo localmente.
- **Android:** el workflow [`42489340612`](https://github.com/rmdvcd/Shalev/actions/runs/42489340612), para el HEAD `cd4e32b`, compila release/debug, lint, API mínima y permisos; falla el job de tests JVM/compilación androidTest. Los logs no pudieron descargarse en esta revisión (EOF del endpoint de Actions), así que no atribuyo causa.
- **Dispositivos reales:** no se ejecutó ninguna prueba en esta revisión.

El hecho de que Android también tenga el job JVM fallido no convierte automáticamente IShalev en equivalente: el código iOS conserva diferencias funcionales y su propia compilación no está verde. Antes de una declaración pública de paridad, hace falta volver a verde ambos workflows y completar la matriz de integración física.

## Siguiente orden recomendado (pendientes del estado actual)

1. Ejecutar build/typecheck y suite Swift en un runner macOS; corregir los errores reales que aparezcan. El enum `.tools` y el modelo de locales ya se trabajaron en esta rama, pero no se han compilado aquí.
2. Ejecutar los tests Android del protocolo, incluida la compatibilidad del campo `Instantanea.local`; confirmar también el fallo JVM señalado en el run de base. Este contenedor no tiene Java.
3. Hacer prueba de humo en Appetize y pruebas de autenticación, onboarding, VoiceOver/Dynamic Type y locales en iPhone/simulador.
4. Completar la sesión iOS↔Android en dos dispositivos, incluyendo Wi-Fi/hotspot, cambio de local, venta offline, duplicados, fotos, cierres/conteo y revocación/licencia.
5. La matriz actualizada ya está publicada más abajo. El OCR extra, fichas persistentes, perfiles locales y tratamiento de fotos se dejan como adaptaciones explícitas; cambiar/eliminar esas funciones requiere decisión de producto y no se hace a ciegas.

## Seguimiento de implementación en la rama Arena (2026-11-08)

Esta sección registra trabajo posterior a la comprobación estática de `main`; no cambia el dictamen de la base analizada ni implica un build de iOS exitoso.

- **Navegación:** se agregó `Herramientas` a `MainSection`, se muestran los cinco destinos Android (Inicio, Inventario, Servicios, Registros y Herramientas) y Ajustes queda fuera de la barra, accesible desde Inicio con retorno explícito a Inicio. La lógica de navegación se revisó en fuente; no se ha ejecutado la app.
- **Locales:** se agregó el esquema 2 con migración de los registros anteriores a un local inicial, selección bloqueada mientras exista turno abierto, administración (agregar/renombrar/eliminar sin borrar historial), y asociación/filtros para catálogo, ventas, turnos, caja e inventario.
- **Backups Android:** el JSON de respaldo v4 ahora exporta/importa `almacenesLocales` y el campo `local` de artículos, insumos, servicios, ventas, turnos y movimientos; las entradas previas sin local se migran al seleccionado. Hay pruebas de round-trip añadidas, aún no ejecutadas en este entorno.
- **Sync:** la instantánea, lotes pendientes y fotos de catálogo se acotan al local activo; al recibir instantánea se reemplaza solo el catálogo del local destino y se conservan los demás. En la continuación más reciente, la instantánea añade el nombre del local de origen y iOS prefiere un local existente con el mismo nombre; si el campo falta (clientes anteriores) o no coincide, conserva la asignación al local iOS seleccionado. Esta compatibilidad está cubierta por un test añadido pero aún no ejecutado.
- **Onboarding:** se añadió una elección explícita Principal/Secundaria antes de módulos y datos. Elegir Secundaria lleva directamente a Apps vinculadas para escanear el QR, con la confirmación destructiva existente; también queda un acceso rápido desde bienvenida. En los avances posteriores descritos abajo se incorporan acceso opcional con autenticación del dispositivo, empleados previstos, niveles de avisos y una pantalla informativa de prueba/licencia. La prueba real y la concesión de licencia siguen gobernadas por GL.
- **Aún pendiente:** ejecutar build y suite Swift/Kotlin, validar los tests nuevos y volver a correr CI; prueba interactiva y sesión física iOS↔Android. La divergencia de OCR y las demás adaptaciones se registran en la matriz que sigue. No se declara paridad validada.
- **Limitación de esta ejecución:** `swift test` no pudo arrancar porque el contenedor no tiene `swift`; `xcodebuild` tampoco está disponible. La revisión sintáctica con Tree-sitter detecta como error conocido de su gramática un bloque preexistente de `SettingsView.swift` (el mismo patrón aparece en el archivo base); no sustituye la compilación de Xcode.

### Continuación de paridad — preferencias de aviso y acceso opcional (2026-11-08)

Cambios de esta continuación en la rama `arena/8f193797-shalev`:

- Onboarding: el recorrido ahora recoge Principal/Secundaria, módulos, número previsto de apps secundarias (limitado por licencia/prueba), datos, niveles de inventario, información de prueba/licencia y resumen. Si se elige Secundaria, cerrar la hoja de vinculación sin vincular no marca el asistente como terminado; se completa al confirmar la vinculación. La cantidad se guarda como preferencia y precarga la solicitud de licencia cuando aún no hay un cupo instalado. En el paso de avisos se puede activar el bloqueo de IShalev con autenticación del dispositivo; se exige que iOS permita autenticación y se confirma identidad antes de cambiar el ajuste. Desde Ajustes → Completar configuración se conservan los pasos de datos, avisos y resumen.
- Preferencias de inventario: `AppSettings` guarda niveles predeterminados opcionales para productos e insumos, con valores recomendados 5/1. El usuario puede cambiarlos desde Ajustes → Avisos de inventario; se aplican al crear artículos nuevos en el formulario de inventario y al crear insumos desde Herramientas. No modifican artículos existentes.
- Intercambio: los niveles y `empleadosPrevistos` se exportan/importan bajo el objeto Android `preferencias`, y viajan también en instantáneas LAN. Los respaldos antiguos sin ese objeto siguen usando los valores recomendados; los datos recibidos se validan antes de guardarse.
- Sync multi-local: se amplió `Instantanea` con el nombre del local de catálogo, con valor por defecto para compatibilidad de protocolo. IShalev mapea por nombre si ese local ya existe; si el campo es ausente o no coincide, conserva el destino activo. Los ajustes de precios de la instantánea también se limitan a artículos del local y, al aplicar, se conservan las selecciones de otros locales. Se añadieron checks iOS y Android para compatibilidad y aislamiento, aún sin ejecutar.
- Tests añadidos: round-trip y fallback legado de preferencias de backup (incluido número previsto de empleados), sincronización de preferencias y validación/persistencia en dominio. Están escritos pero no ejecutados.
- Periodo de prueba: se añadió un paso informativo con días restantes, consecuencias al expirar y precios de referencia, además de enlace textual a Ajustes → Licencia. No concede, reinicia ni extiende una prueba; eso sigue sujeto al motor GL. Los perfiles iOS son locales y no equivalen al número de apps secundarias Android.
- Estado de validación: `git diff --check` pasa. No se dispone de `swift` ni `xcodebuild` en el contenedor; no hay build/typecheck/tests iOS confirmados y tampoco se ha probado la autenticación en un dispositivo real. El dictamen general sigue siendo **no equivalente/no verificado**.

## Matriz de paridad actualizada de la rama

Esta matriz describe lo que existe en el código de esta rama tras los cambios registrados arriba. **“Implementado” no significa compilado ni validado en un dispositivo.**

| Área | Estado de implementación | Adaptación o pendiente verificable |
|---|---|---|
| Navegación | Implementada según cinco destinos Android; Ajustes queda accesible desde Inicio. | Sin build ni prueba de interfaz en simulador/dispositivo. |
| Locales | Modelo, migración, selección, filtros y alcance de ajustes de precio por local en la instantánea. | La instantánea LAN transmite el nombre; iOS empareja por nombre existente o cae al seleccionado. Al aplicar se conservan las asociaciones de otros locales. Tests escritos, no ejecutados. |
| Onboarding | Tipo de app, módulos, empleados previstos limitados por licencia/prueba, datos, avisos, autenticación opcional del dispositivo y pantalla de prueba/licencia. | La autenticación usa Face ID/Touch ID/código de iOS; el paso de prueba es informativo y no concede licencia. Falta probar el recorrido real. |
| Inventario | Niveles predeterminados editables y aplicados a artículos nuevos. | Los umbrales de artículos existentes se conservan; alertas/selecciones no se han contrastado en ejecución contra Android. |
| Respaldo | Lectura/escritura Android v3/v4, locales y preferencias, con fallback para campos ausentes. | Tests de round-trip añadidos pero no ejecutados; resta validar archivos reales en ambos sentidos. |
| Sync LAN | Scoping por local, preferencias y cambios de catálogo implementados en código. | No verificado iPhone↔Android, Wi-Fi/hotspot, reintentos, fotos ni estados de fondo. |
| Licencia/trial GL | Flujo propio disponible y el onboarding informa estado/prueba. | No se emitió ni extendió licencia; activación, expiración y revocación no están validadas end-to-end. |
| OCR/importación documental | Función extra que conserva iOS. | Android retiró OCR; es una diferencia deliberada que requiere decisión de producto si se exige igualdad estricta. |
| SMS y distribución | Análisis manual de texto y distribución iOS por canales Apple. | La captura automática de SMS y la instalación de APK por LAN no aplican a iOS; no se intenta imitarlas. |
| Perfiles de empleado | PIN y permisos locales en iOS; secundarias vinculadas en Android. | Modelos distintos por plataforma; PIN/perfiles no se sincronizan. |
| Fichas de costo/fotos | iOS conserva fichas guardadas y extensiones de fotos de respaldo. | Android trata fichas como temporales y puede ignorar extensiones de foto; iOS ofrece comportamiento adicional. |
| Compilación y pruebas | Tree-sitter revisó sintaxis de los archivos cambiados y `git diff --check` pasa. | No hay `swift`, Xcode ni simulador en este contenedor; build, typecheck, suite Swift y pruebas físicas quedan bloqueadas hasta usar macOS/dispositivos. |

### Pendientes que no se pueden cerrar desde este contenedor

- Ejecutar `swift test`, build de simulador y revisión de errores de concurrencia/SwiftUI con Xcode en macOS. Tree-sitter solo confirma sintaxis aproximada.
- Probar los flujos de onboarding, permisos de autenticación, licencia GL, selección de local y UI con VoiceOver/Dynamic Type en iPhone o simulador.
- Probar sync extremo a extremo y formatos de backup intercambiados con una compilación Android instalada; ahora se transmite el nombre del local, pero falta comprobar el emparejamiento y la compatibilidad con versiones anteriores en ejecución real.
- Decidir producto sobre OCR, fichas persistentes y perfiles locales frente al modelo Android. Se documentaron como extras/adaptaciones en vez de eliminar comportamiento útil sin autorización.
