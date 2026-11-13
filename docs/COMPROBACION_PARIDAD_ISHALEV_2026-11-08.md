# Comprobación de paridad funcional: IShalev frente a Shalev Android

**Fecha:** 2026-11-08
**Base analizada:** `cd4e32b` (HEAD de `main` al iniciar la revisión)
**Rama de trabajo:** `arena/8f193797-shalev`
**Tipo de revisión:** estática (fuentes, tests y ejecuciones de CI visibles); no se ejecutaron apps en dispositivos.

## Dictamen

**La implementación abordable de esta revisión está hecha y ambos pipelines relevantes quedaron en verde**, incluida la suite Swift y los builds iOS/Android. Se corrigieron los errores de compilación, se completaron el onboarding y el alcance de locales, se reparó la inclusión del thumbnail bancario en el proyecto Xcode y se aisló la aplicación/confirmación de cambios de ventas por local.

Esto no certifica paridad total ni reemplaza pruebas físicas. Siguen siendo diferencias deliberadas de plataforma (OCR, perfiles y distribución) y falta una sesión real iPhone↔Android; por ello el dictamen responsable es **paridad funcional amplia, compilada y probada por CI, pero no verificada extremo a extremo en dispositivos**.

## Resumen de resultados

| Área | Estado | Observación |
|---|---|---|
| Venta, turno y caja | **Cercana** | IShalev implementa venta con turno, métodos efectivo/transferencia, arqueo, entradas/salidas y corrección; pruebas de dominio cubren reglas compartidas. Falta validación iOS↔Android en dos dispositivos. |
| Inventario, recetas, servicios y locales | **Implementada y compilada** | IShalev tiene locales, filtros/asociaciones, backup y alcance de instantánea/cambios por local; faltan pruebas físicas iOS↔Android. |
| Registros, clientes y ventas corregidas | **Cercana** | Las pestañas principales de registro y la anulación/corrección existen en ambas; existen tests iOS de reglas de dominio. |
| Navegación y secciones | **Implementada y compilada** | Cinco destinos, incluido Herramientas; Ajustes accesible desde Inicio. Falta prueba interactiva de navegación en simulador/dispositivo. |
| Onboarding | **Implementado y compilado** | Incluye principal/secundaria, vínculo inmediato, módulos, empleados previstos, avisos, acceso opcional biométrico y resumen. Falta prueba de uso en dispositivo. |
| Sync principal/secundaria | **Implementación presente, sin equivalencia extremo a extremo verificada** | Existe protocolo LAN v1, intercambio y flujo de roles; la documentación confirma que no se ha probado una sesión física iOS↔Android. |
| Respaldo e intercambio Android | **Compatibilidad implementada con diferencias** | IShalev lee/escribe formatos Android y mapea datos, pero hay extensiones y datos locales sin equivalencia directa. |
| OCR / importación documental | **Divergencia de producto** | IShalev conserva el flujo OCR/importación; Android lo retiró el 2026-10-31. Es una función extra iOS, no paridad. |
| Verificación de build | **Verde en CI** | iOS: tests, simulador universal y build de dispositivo/IPA pasan; subida Appetize confirmada. Android: JVM, compilación androidTest, lint, APK debug/release, API mínima y permisos pasan. La ejecución de instrumentados en emulador se omite por diseño. |

## Hallazgos que impiden afirmar equivalencia

### Hallazgo inicial P0 — navegación iOS (resuelto)

La revisión inicial encontró referencias a `.tools` ausentes de `MainSection`. Se corrigió la navegación en esta rama: `Herramientas` forma parte de los cinco destinos y Ajustes queda accesible desde Inicio. La suite/build Xcode ahora pasan. Queda la prueba interactiva de todas las rutas en un dispositivo; no hay un error de compilación conocido pendiente en esta área.

### Hallazgo inicial P1 — modelo de locales (resuelto en código; falta prueba física)

Esta diferencia de la auditoría inicial se cerró en IShalev con locales, selector, asociación de inventario/ventas/turnos/movimientos, migración y lectura/escritura de backup. La instantánea identifica el local de origen y el receptor empareja por nombre o usa el local seleccionado cuando trabaja con clientes antiguos/no coincidentes. La ruta de cambios de ventas filtra por local y solo confirma/acuse cambios de los turnos y ventas del local activo; hay test Swift dedicado y pasó en CI.

El código y los tests cierran el hueco implementable. Aún no se ha probado el significado operacional de la asignación de locales en una sesión iOS↔Android con nombres distintos, así que no se certifica aislamiento extremo a extremo en red física.

### P1 — La integración LAN sigue sin prueba de interoperabilidad real

IShalev implementa Bonjour/TCP, protocolo v1, cifrado de sesión y comandos, y hay pruebas con vectores independientes y datos serializados. Pero `iOS/README.md` dice expresamente que aún no se ha ejecutado una sesión iOS↔Android ni una prueba con dos dispositivos físicos. La compatibilidad de formato/criptografía en tests unitarios no cubre permisos del SO, descubrimiento Bonjour, modo de red, estados de fondo, reintentos, fotos ni flujos de aprobación en un teléfono.

**Criterio para cerrar:** prueba documentada de al menos iPhone ↔ Android en rol principal/secundaria en ambas direcciones, en Wi‑Fi y hotspot, incluyendo venta offline, reenvío duplicado, cambio de catálogo, fotos, cierre/conteo aprobado, expulsión de empleado y revocación/licencia. No mover PIN/perfiles locales si esa sigue siendo la decisión de seguridad.

### Hallazgo inicial P1 — onboarding (resuelto en código; falta prueba de uso)

El onboarding ahora pregunta Principal/Secundaria y ofrece llevar a una secundaria directamente a Apps vinculadas; recoge módulos, empleados previstos (limitados por el cupo), datos, avisos y acceso opcional con autenticación del dispositivo. La pantalla de prueba/licencia es informativa y no emite ni extiende licencias. Compila y los tests de dominio pasan; falta comprobar que el recorrido es comprensible para una instalación nueva en un dispositivo real.

### P2 — Diferencias entre las plataformas que deben permanecer explícitas

- **OCR:** iOS conserva `OcrImportView` y `OcrDocumentParser`; Android retiró importación documental/OCR el 2026-10-31. El usuario recibe capacidades distintas según dispositivo.
- **Perfiles de empleado:** iOS admite varios perfiles locales con PIN local y bloqueo; Android se basa en instalación secundaria por empleado y permisos concedidos por la principal. IShalev documenta que los PIN/perfiles locales no se sincronizan. Son modelos distintos, no equivalentes funcionales.
- **SMS y actualizaciones:** IShalev solo analiza texto pegado/compartido y no instala actualizaciones; Android tiene captura opcional mediante acceso especial a notificaciones y puede distribuir el APK Android por LAN. Son restricciones de plataforma razonables; explicar la diferencia, no prometer paridad literal.
- **Herramientas / fichas de costo:** Android describe la ficha de costo como temporal en memoria; `ToolsView` de iOS contiene fichas guardadas en `AppStorage` y edición/exportación a producto/servicio. IShalev tiene mayor persistencia en este módulo, no equivalencia exacta.
- **Fotos en backup:** iOS puede incluir `fotoData` como extensión en archivos de intercambio Android; el importador Android ignora campos extra y no ofrece el mismo tratamiento. La transferencia de fotos también es específica por plataforma.

Estas divergencias no tienen por qué bloquear el uso de IShalev, pero deben figurar en una matriz oficial de compatibilidad por función y formato.

## Funciones con paridad sustancial observada

La lectura de `iOS/README.md`, `Domain.swift`, `AndroidBackup.swift`, `SyncManager.swift` y los módulos Android muestra implementación compartida o equivalente en estas capacidades:

- inicio con resumen de negocio, período, gráficos/rankings, alertas y acceso a venta/caja;
- catálogo de productos, insumos, elaborados, servicios y recetas con consumo de insumos;
- venta en efectivo o transferencia/QR y registro de cliente/referencia;
- prohibición de venta sin turno, caja con fondo y conteo, corrección/anulación con historial;
- filtros de movimientos/ventas, clientes fijos y exportación;
- ajustes de precios, licencia GL, respaldo cifrado y lectura/escritura de respaldo Android;
- permisos separados entre principal y secundaria, con cambios de catálogo controlados por la principal;
- accesibilidad iOS trabajada (Dynamic Type, objetivos táctiles, VoiceOver, Reduce Motion/Transparency), con reglas equivalentes nativas a la auditoría Android.

Esta lista acredita portado significativo del dominio; **no** sustituye la prueba de paridad de cada borde ni comprueba el comportamiento interactivo.

## Estado de verificación observado (2026-11-08)

- **iOS y Appetize:** workflow [`42501197745`](https://github.com/rmdvcd/Shalev/actions/runs/42501197745), commit `1074d9d`: `swift test`, build universal de simulador (`arm64` + `x86_64`) y build de dispositivo/IPA sin firma pasan. El ZIP del simulador se subió a Appetize y Actions informó [`https://appetize.io/app/oxx7ftvhfq45n7ngnkynsiu7ce`](https://appetize.io/app/oxx7ftvhfq45n7ngnkynsiu7ce). Artefactos de simulador e IPA publicados. El commit posterior solo cambia pruebas Kotlin.
- **Android:** workflow [`42502302819`](https://github.com/rmdvcd/Shalev/actions/runs/42502302819), commit `f097604`: todos los trabajos requeridos pasan: JVM tests, compilación de androidTest, lint, debug/release R8, API mínima y permisos. Los tests instrumentados se omiten en push, según la configuración del workflow.
- **Dispositivos reales:** aún no se ejecutó una sesión iPhone↔Android, prueba con hotspot ni validación de biometría/VoiceOver en hardware. La compilación y Appetize no sustituyen esas pruebas.
- **Limitación local:** el contenedor no tiene Swift/Xcode; los builds de iOS se ejecutaron correctamente en el runner macOS de GitHub Actions.

## Siguiente orden recomendado (pendientes no cubiertos por CI)

1. Abrir el build de Appetize y recorrer onboarding, navegación, ajustes, locales y venta de prueba; confirmar tamaños/accessibility y permisos en un iPhone/simulador.
2. Ejecutar una sesión iPhone↔Android en principal/secundaria, con Wi‑Fi y hotspot; validar catálogos, cambio de local, venta offline/duplicados, fotos, cierre/conteo y revocación de licencia.
3. Mantener explícitas las diferencias de producto/plataforma de la matriz. Cambiar OCR, fichas persistentes, perfiles o distribución requiere decisión de producto; no son defectos técnicos que deban borrarse automáticamente.

## Seguimiento de implementación en la rama Arena (2026-11-08)

Esta sección registra trabajo posterior a la comprobación estática de `main`; los resultados de CI y Appetize del cierre aparecen en “Estado de verificación observado”.

- **Navegación:** se agregó `Herramientas` a `MainSection`, se muestran los cinco destinos Android (Inicio, Inventario, Servicios, Registros y Herramientas) y Ajustes queda fuera de la barra, accesible desde Inicio con retorno explícito a Inicio. La navegación y las pantallas compilaron en el build iOS de Actions; falta el recorrido visual/usable en Appetize o dispositivo.
- **Locales:** se agregó el esquema 2 con migración de los registros anteriores a un local inicial, selección bloqueada mientras exista turno abierto, administración (agregar/renombrar/eliminar sin borrar historial), y asociación/filtros para catálogo, ventas, turnos, caja e inventario.
- **Backups Android:** el JSON de respaldo v4 ahora exporta/importa `almacenesLocales` y el campo `local` de artículos, insumos, servicios, ventas, turnos y movimientos; las entradas previas sin local se migran al seleccionado. Las pruebas de round-trip y fallback pasaron en el workflow Android.
- **Sync:** la instantánea, lotes pendientes y fotos de catálogo se acotan al local activo; al recibir instantánea se reemplaza solo el catálogo del local destino y se conservan los demás. En la continuación más reciente, la instantánea añade el nombre del local de origen y iOS prefiere un local existente con el mismo nombre; si el campo falta (clientes anteriores) o no coincide, conserva la asignación al local iOS seleccionado. Se añadieron tests de compatibilidad/fallback y alcance de ventas por local; la suite Swift pasó en CI.
- **Onboarding:** se añadió una elección explícita Principal/Secundaria antes de módulos y datos. Elegir Secundaria lleva directamente a Apps vinculadas para escanear el QR, con la confirmación destructiva existente; también queda un acceso rápido desde bienvenida. En los avances posteriores descritos abajo se incorporan acceso opcional con autenticación del dispositivo, empleados previstos, niveles de avisos y una pantalla informativa de prueba/licencia. La prueba real y la concesión de licencia siguen gobernadas por GL.
- **Estado final:** los tests Swift y Kotlin y los builds iOS/Android pasan en CI; las rutas de cambios de ventas también se limitan y prueban por local. Continúan pendientes la prueba interactiva en Appetize/dispositivo y la interoperabilidad física iOS↔Android. La divergencia de OCR y demás adaptaciones se registran en la matriz.
- **Validación posterior:** aunque Swift/Xcode no están instalados en este contenedor, Actions ejecutó con éxito `swift test`, build de simulador universal y build de IPA. Appetize recibió el ZIP. Tree-sitter no se usa como sustituto del typecheck de macOS.

### Continuación de paridad — preferencias de aviso y acceso opcional (2026-11-08)

Cambios de esta continuación en la rama `arena/8f193797-shalev`:

- Onboarding: el recorrido ahora recoge Principal/Secundaria, módulos, número previsto de apps secundarias (limitado por licencia/prueba), datos, niveles de inventario, información de prueba/licencia y resumen. Si se elige Secundaria, cerrar la hoja de vinculación sin vincular no marca el asistente como terminado; se completa al confirmar la vinculación. La cantidad se guarda como preferencia y precarga la solicitud de licencia cuando aún no hay un cupo instalado. En el paso de avisos se puede activar el bloqueo de IShalev con autenticación del dispositivo; se exige que iOS permita autenticación y se confirma identidad antes de cambiar el ajuste. Desde Ajustes → Completar configuración se conservan los pasos de datos, avisos y resumen.
- Preferencias de inventario: `AppSettings` guarda niveles predeterminados opcionales para productos e insumos, con valores recomendados 5/1. El usuario puede cambiarlos desde Ajustes → Avisos de inventario; se aplican al crear artículos nuevos en el formulario de inventario y al crear insumos desde Herramientas. No modifican artículos existentes.
- Intercambio: los niveles y `empleadosPrevistos` se exportan/importan bajo el objeto Android `preferencias`, y viajan también en instantáneas LAN. Los respaldos antiguos sin ese objeto siguen usando los valores recomendados; los datos recibidos se validan antes de guardarse.
- Sync multi-local: se amplió `Instantanea` con el nombre del local de catálogo, con valor por defecto para compatibilidad de protocolo. IShalev mapea por nombre si ese local ya existe; si el campo es ausente o no coincide, conserva el destino activo. Los ajustes de precios de la instantánea también se limitan a artículos del local y, al aplicar, se conservan las selecciones de otros locales. Los checks iOS y Android de compatibilidad y aislamiento pasaron en los workflows de Actions.
- Tests añadidos: round-trip/fallback de preferencias, validación/persistencia y alcance de ventas por local. Pasaron en la suite Swift de Actions.
- Periodo de prueba: se añadió un paso informativo con días restantes, consecuencias al expirar y precios de referencia, además de enlace textual a Ajustes → Licencia. No concede, reinicia ni extiende una prueba; eso sigue sujeto al motor GL. Los perfiles iOS son locales y no equivalen al número de apps secundarias Android.
- Estado de validación: `git diff --check` pasa; `swift test`, simulador universal y IPA pasan en Actions y la subida Appetize fue confirmada. No se ha probado la autenticación ni el flujo completo en un dispositivo físico. El dictamen de producto sigue siendo **paridad amplia, no certificada extremo a extremo**.

## Matriz de paridad actualizada de la rama

Esta matriz describe lo que existe en el código de esta rama tras los cambios registrados arriba. **Build y suites de CI pasan; “validado” no significa probado en dispositivo real.**

| Área | Estado de implementación | Adaptación o pendiente verificable |
|---|---|---|
| Navegación | Implementada según cinco destinos Android; Ajustes queda accesible desde Inicio. | Build iOS pasa; falta prueba de interfaz en Appetize/simulador/dispositivo. |
| Locales | Modelo, migración, selección, filtros y alcance de ajustes de precio por local en la instantánea. | La instantánea LAN transmite el nombre; iOS empareja por nombre existente o cae al seleccionado. Al aplicar se conservan las asociaciones de otros locales; cambios entrantes validan local de turno/venta. Tests pasan en Swift CI. |
| Onboarding | Tipo de app, módulos, empleados previstos limitados por licencia/prueba, datos, avisos, autenticación opcional del dispositivo y pantalla de prueba/licencia. | La autenticación usa Face ID/Touch ID/código de iOS; el paso de prueba es informativo y no concede licencia. Falta probar el recorrido real. |
| Inventario | Niveles predeterminados editables y aplicados a artículos nuevos. | Los umbrales de artículos existentes se conservan; alertas/selecciones no se han contrastado en ejecución contra Android. |
| Respaldo | Lectura/escritura Android v3/v4, locales y preferencias, con fallback para campos ausentes. | Tests de round-trip/fallback pasan en CI; resta intercambiar archivos reales entre instalaciones. |
| Sync LAN | Scoping por local, preferencias y cambios de catálogo implementados en código. | No verificado iPhone↔Android, Wi-Fi/hotspot, reintentos, fotos ni estados de fondo. |
| Licencia/trial GL | Flujo propio disponible y el onboarding informa estado/prueba. | No se emitió ni extendió licencia; activación, expiración y revocación no están validadas end-to-end. |
| OCR/importación documental | Función extra que conserva iOS. | Android retiró OCR; es una diferencia deliberada que requiere decisión de producto si se exige igualdad estricta. |
| SMS y distribución | Análisis manual de texto y distribución iOS por canales Apple. | La captura automática de SMS y la instalación de APK por LAN no aplican a iOS; no se intenta imitarlas. |
| Perfiles de empleado | PIN y permisos locales en iOS; secundarias vinculadas en Android. | Modelos distintos por plataforma; PIN/perfiles no se sincronizan. |
| Fichas de costo/fotos | iOS conserva fichas guardadas y extensiones de fotos de respaldo. | Android trata fichas como temporales y puede ignorar extensiones de foto; iOS ofrece comportamiento adicional. |
| Compilación y pruebas | Suite Swift, simulador universal, IPA; JVM, androidTest compilation, lint, APK/R8, API mínima y permisos pasan en Actions. Appetize recibió el simulador. | Test de emulador Android está configurado para schedule/manual; quedan humo UI y sesión física iOS↔Android. |

### Pendientes que requieren simulador/dispositivos o decisión de producto

- Hacer un smoke test visual en Appetize y probar onboarding, autenticación, licencia GL, locales, VoiceOver y Dynamic Type en iPhone/simulador.
- Probar sync real iOS↔Android, formatos de backup, nombres de locales distintos, Wi‑Fi/hotspot, reintentos, fotos, venta duplicada y cierre/conteo.
- Decidir si OCR, fichas persistentes y perfiles locales deben continuar como diferencias explícitas o cambiar por paridad estricta. No requieren corrección técnica automática y se dejaron intactos.
- El workflow Android compila `androidTest`, pero no ejecuta emulador en cada push; la ejecución instrumentada sigue pendiente de un disparo schedule/manual.
