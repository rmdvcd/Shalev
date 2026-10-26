# Shalev — Verificación real

> **Fuente única de verdad** del estado de verificación: solo se considera medido lo anotado aquí. Última actualización Android: **2026-10-21**. La app principal sigue siendo Android. El antiguo Flutter/iOS y la web de escritorio se retiraron; la nueva contraparte SwiftUI vive en `IShalev/` y tiene una sección de verificación independiente al final de este documento.

## Auditoría de interfaz: teclado, icono de «Continuar» y ventanas (2026-10-21, 0.32.0)

Auditoría de la interfaz completa con las reglas de `ui-ux-pro-max` (`:designsystem` + 20 pantallas + 274 capturas). Se aplicaron tres correcciones: `imePadding()` en Venta y Modificar venta (el teclado tapaba la barra de acción y los campos), icono de flecha en el botón «Continuar» del carrito (el de «Efectivo» a 24 dp se leía como una cámara) y `safeDrawingPadding()` en las ventanas emergentes (respetan barras del sistema, recorte y teclado; antes solo apartaban el teclado). Se corrige además un comentario desactualizado en `Charts.kt`. **La casilla de 24 dp se deja pendiente a decisión del dueño** (queda anotado en `DESIGN_SYSTEM.md`).

- `./gradlew shalevTests` (2026-10-21): **883 casos JVM en verde**, los mismos del lote (19 `:core` + 114 `:licencia` + 229 `:domain` + 93 `:data` + 34 `:designsystem` + 394 `:app`); no se añadió ni se retiró ninguna prueba.
- `./gradlew :app:verifyRoborazziDebug` (2026-10-21): **274 capturas en verde**. Se regrabaron **10 PNG** (5 capturas de Venta × claro/oscuro) por el icono de «Continuar». **Ninguna** captura cambia por las ventanas emergentes: en Robolectric los insets del sistema son 0, así que el cambio solo se ve en un teléfono real.
- `./gradlew shalevCheck` (2026-10-21): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`).
- **Lo que no se probó:** nada en un teléfono (la instalación por USB sigue bloqueada por el HyperOS del 4bc9f8f3), así que **el efecto del teclado y el de las barras del sistema en horizontal no se han visto en pantalla**; tampoco TalkBack real ni capturas en horizontal (hoy las 274 son de 360×800 dp en vertical).

## Limpieza de sobreingeniería aplicada (2026-10-21, 0.32.0)

Se aplicaron las correcciones de la auditoría del repositorio completo. Salen tres extensiones muertas de `AppResult`, la propiedad `formatos` de `ExportadorDocumentos`, el icono `Reiniciar` y el espacio `xl`; se deja de depender de `androidx.exifinterface` (se usa el `ExifInterface` del sistema); el mensaje de «no hay app para compartir» pasa a `Compartir.SIN_APLICACION`; y las barras de las listas (`BotonFiltro`, `BarraSeleccion`, `BarraContinuarVenta`) y la hoja de insumos de la receta viven en `app/common/ListasComunes.kt`. No cambia ningún comportamiento ni el esquema.

- `./gradlew shalevTests` (2026-10-21): **883 casos JVM en verde**, los mismos del lote (880 + los 3 de `AlmacenFotosTest`).
- `./gradlew :app:verifyRoborazziDebug` (2026-10-21): **274 capturas en verde**; las piezas compartidas dibujan igual y no se regeneró ninguna por este lote. Se regeneraron **72 PNG** (36 capturas × claro/oscuro) de Ajustes e Inicio que **seguían con el rótulo «SPVI»** de antes del renombrado: no era ruido de renderizado, era una dorada vieja. Con eso el lote 0.32.0 regenera **154 PNG** (77 capturas × 2).
- `./gradlew shalevCheck` (2026-10-21): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`).

## Sincronización manual, barra de avance, sonidos, fotos y conteo por categoría (2026-10-21, 0.32.0)

Ocho pedidos del dueño en un lote: barra de avance, el fondo lo escribe quien abre el turno, conteo parcial por categoría, retirada de «Design system» y «Generar datos de prueba», sonidos propios, **fuera los datos móviles**, sin sincronización automática y **las fotos hacia la app secundaria**.

- `./gradlew shalevTests` (2026-10-21): **883 casos JVM en verde** (19 `:core` + 114 `:licencia` + 229 `:domain` + **93** `:data` + 34 `:designsystem` + **394** `:app`). Son 25 menos que los 905 del 2026-10-19: se retiraron las 22 pruebas del seed y del catálogo, las de `FondoSyncTest`/`FondoAsignadoTest` y las de «asignar fondo», y se añadieron `Sync32Test` (4), `FondoEnTurnoTest` (3) y 2 casos de avance en `FondoAperturaTest`.
- `./gradlew shalevCheck` (2026-10-21): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`). La primera pasada falló al empaquetar por una **ruta vieja** de `domain/build` (la carpeta se movió de `SPVI/` a `Shalev/`): se borró `domain/build` y `core|licencia|designsystem/build` y repetida quedó en verde.
- **Sin cambio de esquema de base de datos:** las columnas `empleado.fondoAsignadoCent/fondoAsignadoEn/aperturaSolicitadaEn` se conservan (quedan sin uso) y el conteo añade un campo **opcional** `categoria` en la línea del DTO (los conteos anteriores se leen igual). No hay migración ni `14.json`.
- Protocolo: sigue en **v1** y todos los campos son opcionales (`ignoreUnknownKeys = true`): una principal o secundaria anteriores se siguen entendiendo. Se retiran los campos del fondo y se añaden `Instantanea.fotos`, `PedirFoto` y `BloqueFoto`.
- Teléfono 4bc9f8f3 (Android 15, API 35), APK debug `app-arm64-v8a-debug.apk` instalado: la app **arranca sin fallos** (nada en `logcat -b crash`), Ajustes ya **no** muestra «Design system» ni «Generar datos de prueba», y «Apps vinculadas» abre con la lista de secundarias («Secundarias (2 de 5)», red local activa) sin las filas de fondo. Los **cuatro canales de notificación** quedan creados con su sonido: `sync_conectada` → `android.resource://…/2131689472`, `sync_desconectada` → `…973`, `sync_terminada` → `…974` y `sync_progreso` **sin sonido** (comprobado con `dumpsys notification`).
- Capturas Roborazzi: se regeneraron las **82** (41 en claro y 41 en oscuro) que cambian de verdad por la casilla más pequeña y el icono de «móvil»: Inventario (02*), Registros (04*), Venta (05f, 05h, 05i), Pago electrónico (06c, 06e–06h), Precios (06l), Ajustes (07a, 07a2), Ayuda (07g), Apps vinculadas (07p–07s), Servicios (09a, 09c, 09d) y el recorrido inicial (10e). **Las demás se dejaron intactas**: en este entorno el renderizado difiere un pelo de la máquina que las grabó (del 0,001 % al 0,02 % en pantallas que no se tocaron) y subirlas metería ruido en el diff. Al verificar el lote siguiente se vio que **36 capturas de Ajustes e Inicio sí eran viejas**: conservaban el rótulo «SPVI» de antes del renombrado; se regeneraron (72 PNG). `verifyRoborazziDebug` queda en verde con las doradas nuevas (274 capturas).
- **Las fotos de la app secundaria (medido en el teléfono).** Los archivos llegaban, pero los artículos no los mostraban: faltaba el enlace. La causa era que la firma ECDSA del sub-token usa un nonce aleatorio → el sub-token cambia en cada ronda → cambia el hash de la instantánea → la principal la reenvía siempre → al aplicarse el catálogo se borraba la ruta de la foto (es un archivo de este teléfono) y, como el archivo ya estaba, la descarga no volvía a enlazarlo. Se arregla conservando la ruta propia cuando el archivo está en el álbum y enlazando **todas** las fotos del catálogo tras la descarga. **Comprobado en HEQKPFXSGMYPNNAE (OnePlus, Android 11, la app de un empleado): tras sincronizar, el inventario muestra las fotos** de los productos (antes salían sin foto) y `run-as … ls files/fotos` devuelve los **38** archivos. La foto viaja por su **nombre** (el UUID .jpg), no por su ruta: la del principal no significa nada en la secundaria.
- Lo que **no** se probó: la vinculación real entre dos teléfonos con la barra de avance, los sonidos al conectar/desconectar y el conteo parcial. Aquí solo hay un dispositivo: falta la prueba de extremo a extremo (ver `Pruebas.md` y `PRUEBAS_DISPOSITIVO.md`).

## Auditoría de UI aplicada (2026-10-19, 0.31.0)

Se aplicaron los hallazgos de `ui-ux-pro-max` que no contradicen el sistema de diseño: chips de Precios en `FlowRow`, subtítulo de acordeón oculto al abrir, título de tarjeta como encabezado, chevron en banners clicables, `selected` solo cuando es verdadero, contador de filtros en la descripción del botón, icono de saltar en onboarding e icono de salida en «Salir sin guardar».

- `./gradlew shalevTests` (2026-10-19): **905 casos JVM en verde** (19 `:core` + 114 `:licencia` + 250 `:domain` + 91 `:data` + 34 `:designsystem` + **397** `:app`). Los 7 nuevos son `AuditoriaUiTest`; 5 se vieron en rojo antes de implementar, el de `selected = true` pasó como control y el de la descripción del filtro se añadió con el helper.
- `./gradlew shalevCheck` (2026-10-19): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`), con `-Xmx4g`.
- `./gradlew :app:verifyRoborazziDebug` (2026-10-19): **verde**; 137 tests de captura en claro/oscuro (274 PNG) y 534 tests de `:app` en total. Se regeneraron **38 PNG** de este lote: 11 capturas de Inicio, 2 de Precios, 4 de onboarding y los diálogos de salida de Perfil e Inventario, × claro/oscuro. Los 30 de Inventario ya estaban regenerados por la retirada del OCR.
- Lo que **no** se probó: reinstalar el APK en el teléfono para este lote, TalkBack real, landscape y pantallas pequeñas. La accesibilidad se comprobó por semántica de Compose y por las capturas de contenido; no sustituye una pasada con lector de pantalla.

## OCR de inventario retirado (2026-10-19, 0.31.0)

Por decisión del dueño se eliminó el escáner OCR de inventario: no quedan código, ruta, textos ni dependencia de reconocimiento de texto.

- Se borraron `EscanerInventarioLogic.kt`, `EscanerInventarioViewModel.kt`, `EscanerInventarioScreen.kt`, sus dos pruebas JVM, la ruta `Route.EscanerInventario`, su `composable` y el icono **Escanear** de la barra de Inventario; también `TextosInventario.ESCANEAR` y los 42 textos `scan_*`.
- Salió `com.google.mlkit:text-recognition` 16.0.1 del catálogo (`mlkitText`/`mlkit-text`) y de `:app`. Se conservan `barcode-scanning` para el QR de vinculación, la cámara de fotos y el permiso `CAMERA`; `shalevPermisos` sigue en verde.
- Se retiraron `docs/ESCANEO_OCR.md` y `docs/OPENCODE_ESCANEO_OCR.md`; las menciones de las entradas anteriores de este archivo y de `HISTORIAL_DESARROLLO.md` son históricas. PP-Layout/ONNX nunca se implementó.
- `./gradlew shalevTests` (2026-10-19): **898 casos JVM en verde** (19 `:core` + 114 `:licencia` + 250 `:domain` + 91 `:data` + 34 `:designsystem` + 390 `:app`). Son 24 menos que los 922 anteriores: 25 tests del escáner retirados menos 1 prueba de limpieza añadida antes.
- `./gradlew shalevCheck` (2026-10-19): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`). La primera pasada falló al empaquetar por `Java heap space` con `-Xmx3072m`; repetida con `-Xmx4g`, verde.
- `./gradlew :app:recordRoborazziDebug --tests ...InventarioCapturas` y `./gradlew :app:verifyRoborazziDebug` (2026-10-19): **527 capturas en verde**. Cambiaron las **30 doradas** de Inventario (15 × claro/oscuro) al quitar el icono; ningún otro PNG cambia.
- Teléfono 4bc9f8f3 (Android 15, API 35), APK debug instalado: la barra de Inventario muestra solo **Filtrar** y **Exportar**, sin **Escanear**; la lista sigue operativa. No se probó una ruta antigua guardada en el back stack.

## Fotos del seed «Bodega cubana»: 38 imágenes dentro del APK debug (2026-10-19, 0.31.0)

«Generar datos de prueba» deja de sembrar sin foto: los 30 productos (los 2 Elaborados no admiten foto) y los 8 servicios llevan una foto real, copiada del propio APK, sin red en tiempo de ejecución.

- Origen y licencias: **Wikimedia Commons**, solo dominio público / CC0 / CC BY / CC BY-SA (ninguna NC ni ND). Se bajaron 4 candidatas por artículo y se eligieron **mirando** hojas de contactos; el primer paso automático falló en ~25 de 38 (un lince como «desodorante», una planta como «galletas», autopistas de Salt Lake City como «sal»). Detalle con autoría y enlace de cada una en `docs/CREDITOS_FOTOS_SEED.md`.
- Las 38 imágenes viven en `app/src/debug/assets/seed/fotos/<clave>.jpg` (solo depuración; el release no las lleva) a ≤512 px JPEG: **1,5 MB** en total. `app/src/debug/assets/seed/fotos/CREDITOS.md` viaja con ellas.
- `./gradlew shalevTests` (2026-10-19): **922 casos JVM en verde**, contados en los XML de los seis tasks (19 `:core` + 114 `:licencia` + **250** `:domain` + 91 `:data` + 34 `:designsystem` + 414 `:app`). Los 2 nuevos de `:domain` son de este lote y se vieron en **rojo** antes del arreglo, y se comprobó con una mutación temporal que fallan si la foto no se asigna.
- `./gradlew shalevCheck` (2026-10-19): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`).
- Assets fusionados por variante (`mergeDebugAssets` / `mergeReleaseAssets`): **38** `.jpg` en debug y **0** en release (allí solo quedan los modelos de ML Kit). El código devuelve `Err` y siembra sin foto si el asset no existe.
- CI (2026-10-19): [run `39198204810`](https://github.com/rmdvcd/Shalev/actions/runs/39198204810) sobre `a1743cb`: **verde** (permisos del manifiesto, API mínima 26, lint, tests JVM y compilación de instrumentados, release R8 y APK debug).
- Teléfono 4bc9f8f3 (Android 15, API 35), APK debug `app-arm64-v8a-debug.apk` instalado: Ajustes › «Generar datos de prueba» › «Seed listo.»; **Inventario** (43 filas) y **Servicios** (8 filas) muestran las fotos elegidas, los Elaborados «Dulce de coco» y «Pan con lechón» salen sin foto, y `run-as cu.shalev.app.debug ls files/fotos` devuelve **38 archivos**.
- Lo que **no** se probó: la vista de la foto en la ficha del producto (misma imagen ya verificada en la lista) y que un release sin assets siembre igual —el código devuelve `Err` y siembra sin foto, cubierto por el test `unaFotoQueFaltaNoAbortaElSeed`—. Tampoco entra en el respaldo ni en la sincronización LAN: las fotos siguen siendo locales, como antes.
- **Actualización (2026-10-21):** la función «Generar datos de prueba» y sus 38 imágenes se retiraron de la app: `app/src/debug/assets/seed/`, `docs/CREDITOS_FOTOS_SEED.md` y `FotoRepository.importarIncluida` ya no existen. La medición de arriba queda como registro histórico de aquel lote.

## Escáner OCR completo y geometría ajustada en el teléfono (2026-10-18, `6959eb1`, CI verde)

Fases del escáner (lógica pura, ViewModel, pantalla y documentación) y las reglas de geometría corregidas con lo medido en el teléfono.

- `./gradlew shalevTests` (2026-10-18): **920 casos JVM en verde**, contados en los XML de los seis tasks (19 `:core` + 114 `:licencia` + 248 `:domain` + 91 `:data` + 34 `:designsystem` + 414 `:app`). Los 414 de `:app` son los 385 anteriores + los 19 tests del escáner del merge #44 (existían, pero el merge no compilaba) + 5 nuevos de esta revisión. **Cifra corregida el 2026-10-19**: aquí se anotó «1057 / 551», que contaba los 137 `@Test` de `app/src/test/.../capturas/` — `shalevTests` **no** los ejecuta, los graba `recordRoborazziDebug` (`app/build.gradle.kts` excluye `**/capturas/**` salvo en tareas Roborazzi).
- `./gradlew shalevCheck` (2026-10-18): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`).
- `./gradlew verifyRoborazziDebug` (2026-10-18): **527 capturas en verde**, ninguna cambia.
- CI (2026-10-18): [run `39176254439`](https://github.com/rmdvcd/Shalev/actions/runs/39176254439) sobre `6959eb1`: **verde** (permisos del manifiesto, API mínima 26, lint, tests JVM y compilación de instrumentados, release R8 y APK debug). Es la primera corrida verde desde el fallo de `84afc87`; los tests instrumentados en emulador no corren de forma ordinaria.
- Prueba de extremo a extremo en el teléfono 4bc9f8f3 (Android 15, API 35), APK debug, con los tres PNG de tabla generados para la prueba:
  - **Productos**: 43 → **47 productos**; las 4 filas entraron con nombre, categoría, cantidad y ambos precios. El encabezado se detectó solo (5 columnas → Nombre / Categoría / Cantidad / Precio de costo / Precio de venta) y la revisión mostró los valores tal cual, con la categoría del texto («Alimentos», «Aseo», «Bebidas») en vez de «Otros».
  - **Insumos**: → **50 registros**; 3 insumos con unidad `kg` y cantidad decimal (`12.5`, `8.25`, `3.75`) contra `UnidadMedida`.
  - **Sin tabla (párrafo)**: aviso «No se pudo confirmar la estructura de tabla. Revisa cuidadosamente las filas detectadas.»; al pulsar Confirmar, «No se pudo leer “Cantidad” en la fila 1» y **nada se guarda** (el teléfono sigue con 50 productos).
  - **Regeneración de datos de ejemplo**: hecha en este teléfono (Ajustes › Generar datos de prueba › «Seed listo.»); los nombres del catálogo ya no llevan la unidad pegada, como pedía el pendiente anterior.
- Tres defectos reales encontrados **con el teléfono** y corregidos con prueba JVM en rojo antes de recompilar:
  - el **título** de la lista («Inventario de productos») ocupaba la primera fila y estorbaba el encabezado → ahora el encabezado se busca en las tres primeras filas y lo de encima (título, fecha) no es un registro;
  - un **nombre largo** que desborda su columna unía su columna con la vecina (una banda ancha se comía las dos) → las columnas se agrupan por el **borde izquierdo** de la celda y cada celda va a la columna de inicio más cercano;
  - el corte de una línea con dos columnas pegadas partía celdas normales: «Jabon de tocador» (26 px de hueco, 20 px de alto) se partía en dos y creaba columnas fantasma → ahora solo corta si el hueco **duplica el alto** del texto. El diagnóstico se hizo con `logcat` temporal, ya retirado.
- Modelo PP-Layout/ONNX: sigue **bloqueado** (sin artefacto con licencia y contrato verificables) y `onnxruntime-android` está fuera del catálogo y de `:app`; los pasos para reincorporarlo estaban en `docs/ESCANEO_OCR.md` (retirado el 2026-10-19 con el escáner).
- Pendiente de decisión del dueño: reescribir o no los mensajes de los commits `54c85f9 "j"` y `5dc2689 "6"` (ya publicados en `origin/main`).

## Filtro por alerta de Inicio y compilación del escaneo OCR (2026-10-18, `e31ff9a` y `7d71f72`, CI verde)

- `./gradlew shalevTests` (2026-10-18): **896 casos JVM en verde**, contados en los XML de los seis tasks (19 `:core` + 114 `:licencia` + 248 `:domain` + 91 `:data` + 34 `:designsystem` + 390 `:app`). La cifra «1033» que se anotó aquí contaba los tests de capturas de `:app`, que **no** corren en `shalevTests` (corregido el 2026-10-19).
- `./gradlew shalevCheck` (2026-10-18): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`).
- `./gradlew verifyRoborazziDebug` (2026-10-18): **15 capturas de Inventario estaban viejas** porque el merge del escaneo OCR (que no compilaba) añadió el icono **Escanear** a la barra sin regenerarlas; se regeneraron las **30 doradas** afectadas (15 × claro/oscuro) y la suite completa (**527**) queda en verde. Revisada una captura, el único cambio es el icono nuevo en la barra.
- Prueba en el teléfono 4bc9f8f3 (Android 15, API 35), APK debug con estos cambios:
  - **Alerta de Inicio → Inventario**: pulsar «Sin existencia: 1» abre la **pestaña** con el filtro puesto — «1 de 43 productos», chip «Sin existencia / Quitar filtro», el producto «Sal fina» (0 kg, Insumos) y el contador del icono de filtro en la barra. La barra inferior vuelve a **Inicio**; volver a Inventario conserva el filtro; ATRÁS desde Inventario vuelve a Inicio (ya no sale de la app); una segunda alerta («Insumo bajo») deja «3 de 43 productos» con «Leche en polvo».
  - **Escaneo OCR (merge #44)**: el botón de la barra de Inventario abre el asistente («¿Qué tipo de registros vas a importar?», Productos/Insumos y los campos a extraer) y ATRÁS vuelve a la lista; `logcat -b crash` vacío.
  - **Diagnóstico**: los logs temporales mostraron que la entrada de Inventario tenía la alerta en su `SavedStateHandle` y el ViewModel se construía con `null` (Hilt le inyecta otro handle, creado de los argumentos de la ruta). El arreglo cambia el canal (señal de proceso `AlertaPendiente`); los logs se retiraron.

## Cinco correcciones de la revisión del dueño (2026-10-18, `5dc2689`)

Alerta de Inicio → Inventario filtrado, botón «Ver la venta» centrado en la ficha de una transferencia, nombres del catálogo sin unidad pegada, auditoría de precios congelados y conteo de billetes visible con «Omitir» al cerrar turno.

- `./gradlew shalevTests` (2026-10-18): **896 tests JVM en verde**, sin red ni dispositivo (891 antes de este lote + 5 nuevos). Esta cifra es la correcta: la «corrección» a **1033** que se anotó aquí el mismo día contaba los tests de capturas de `:app`, que `shalevTests` no ejecuta (los graba `recordRoborazziDebug`); deshecha el 2026-10-19.
- `./gradlew shalevCheck` (2026-10-18): **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`).
- `./gradlew verifyRoborazziDebug` (2026-10-18): **527 capturas en verde**. Cambiaban dos doradas y se regeneraron por decisión del dueño: `04p_registros_ficha_transferencia` (el botón ya sale centrado) y `01f_inicio_dialogo_cerrar_turno` (billetes a la vista y «Omitir» en la esquina), en claro y oscuro; ningún otro PNG cambia.
- `:data:connectedDebugAndroidTest` en el teléfono 4bc9f8f3 (Android 15, API 35), 2026-10-18: **60/60 en verde**. Incluye el caso nuevo `RepositoriosRoomTest.cambiarElPrecioNoAlteraElImporteDeLoVendido`, comprobado **en rojo** (esperado 30.00 CUP, obtenido 99.00 CUP al reescribir el detalle a propósito) y arregla los tres tests de migración de `EsquemaTest`, que fallaban con «duplicate column name: clienteFijo» por un recorte incompleto del fixture (fallo del test, no de las migraciones).
- Prueba de extremo a extremo en el teléfono 4bc9f8f3 (2026-10-18), APK debug con estos cambios:
  - **Alerta de Inicio → Inventario**: con este APK **no filtraba** (el filtro viajaba por el `SavedStateHandle` de la entrada, que el ViewModel no lee); se arregló después y la prueba de arriba es la buena.
  - **Ficha de transferencia**: con el turno cerrado, «Ver la venta» centrado (x 515–568 de 1080; antes 156–209). Con el turno abierto y una transferencia del turno, la fila queda [Modificar venta][Anular venta][Ver la venta] (x 374–709, centro 541).
  - **Cierre de turno**: los campos por denominación se ven al abrir el diálogo; «Omitir el conteo de billetes» está en la esquina inferior derecha; 23 × 100 suma 2,300.00 CUP y muestra «Cuadra»; «Omitir el conteo de inventario» también en la esquina del paso de inventario y la revisión avisa «Conteo de inventario omitido: se cierra sin ajustar existencias.»; el turno **cerró** de verdad con ese conteo (sin error de inventario cambiado).
  - **Datos del teléfono**: la prueba abrió un turno (fondo 2,300.00), registró una venta por transferencia de prueba («Prueba Verificacion», 190.00 CUP, VERIF00001) y lo cerró con el conteo omitido; el teléfono queda como estaba, con el turno cerrado.
- **Pendiente antes de publicar**: decidir si se reescriben los mensajes de los commits `54c85f9 "j"` y `5dc2689 "6"` (ya publicados en `origin/main`). La regeneración de los datos de ejemplo del teléfono (nombres del catálogo sin unidad pegada) se hizo el 2026-10-18: ver la sección de arriba. Estado del lote: **commiteado en `e31ff9a`** (con `7d71f72`, que arregló la compilación del escaneo) y **CI verde** en el run de `6959eb1`.

## Cambio en curso: editar importe, cantidad, método y nº en Registros (2026-10-18, `main`, `54c85f9`)

- `./gradlew shalevTests` (2026-10-17): **891 tests JVM en verde**, sin red ni dispositivo.
- `./gradlew shalevCheck`: **OK** (tests JVM + `lintDebug` + `assembleDebug` + `shalevPermisos`).
- `./gradlew verifyRoborazziDebug`: 525 capturas; **1 fallo de comparación**, `01f_inicio_dialogo_cerrar_turno.png`, que corresponde al cambio de cierre con conteo ya en `main` y no se regenera aquí. Ninguna captura de Registros cambia (las doradas montan `AccionesRegistros()` sin acciones).
- `:data:TurnoRepositoryInstrumentedTest` en el teléfono 4bc9f8f3 (Android 15, API 35), 2026-10-17: **8/8 en verde**, incluidos los 2 casos nuevos de corrección de un ajuste (stock, movimiento y existencias posteriores). Los rojos se comprobaron antes de implementar.
- Prueba de extremo a extremo en el teléfono 4bc9f8f3 (2026-10-18), APK debug con estos cambios:
  - **Movimientos (turno abierto)**: un ajuste de +2 creado al editar la cantidad de un producto en Inventario se corrigió a +5 con motivo; el movimiento quedó en +5, «Existencia después» 123 y nota «Edición · Corregido: …», y el producto pasó a 123 u en Inventario.
  - **Movimientos (turno cerrado)**: ese mismo movimiento, ya con el turno cerrado, **no ofrece** «Editar ajuste» en la ficha.
  - **Transferencias (turno cerrado)**: la ficha solo ofrece «Ver la venta»; no aparecen las acciones de la venta.
  - **Transferencias (turno abierto)**: la ficha ofrece «Modificar venta», «Anular venta» y «Ver la venta»; desde «Modificar venta» se cambiaron la cantidad (190.00 → 380.00 CUP) y el nº de transacción (TRANS12345 → TRANS99999) con motivo obligatorio, y Registros muestra la transferencia nueva con el importe y el nº corregidos. El diálogo «Anular venta» avisa de que era una transferencia (se canceló, no se anuló).
- Versión rotulada **0.31.0**; el cambio quedó commiteado como `54c85f9`, que además versiona `data/schemas/cu.shalev.data.db.ShalevDatabase/13.json` (pieza que faltaba del cierre con conteo). La corrida de CI de la rama sigue pendiente.

## Cambio en curso: cierre con conteo (2026-10-17, `arena/5c955caa-shalev`)

- `git diff --check`: pasó.
- Análisis sintáctico Tree-sitter de los 42 archivos Kotlin modificados/nuevos: sin errores de sintaxis; **no comprueba tipos, Room ni Hilt y no sustituye compilación**.
- Prueba Python/SQLite de `SQL_12_13` sobre `12.json`: pasó; conserva caja y añade `conteoCierreJson` nullable.
- `./gradlew shalevTests`: **no pudo iniciar**, no hay Java ni JAVA_HOME.
- `python3 tools/verificacion/api_minima.py`: **no pudo ejecutarse**, falta ANDROID_HOME.
- Nuevos tests JVM/instrumentados y de sincronización: añadidos, **no ejecutados**.
- **Bloqueante antes de publicar:** ejecutar `shalevCheck`, las migraciones instrumentadas, capturas y cierre/aprobación en dos teléfonos actualizados. En este entorno el código del cierre sí compiló y sus tests JVM corrieron dentro de `shalevTests` (891 en verde, 2026-10-17) tras el merge `a293bad`; `13.json` está versionado desde `54c85f9`. Los resultados históricos de abajo no validan este cambio.

## 1. CI actual

GitHub Actions ejecuta el workflow [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) en pushes a `main` y ramas `arena/**`, pull requests y ejecuciones manuales.

| Trabajo | Comando principal | Qué comprueba |
|---|---|---|
| Tests JVM y compilación de instrumentados | `./gradlew shalevTests :data:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` | Tests unitarios JVM y compilación de tests instrumentados |
| Lint | `./gradlew :app:lintDebug` | Lint Android con el baseline versionado |
| Release sin firma y APK debug | `./gradlew :app:assembleRelease :app:assembleDebug` | Compilación, R8 y generación de APKs |
| Permisos | `./gradlew shalevPermisos` | Permisos de los manifiestos fusionados debug/release |
| API mínima 26 | `python3 tools/verificacion/api_minima.py` | Llamadas a APIs Android posteriores a API 26 |

El job de emulador instrumentado se ejecuta en la programación semanal y en disparos manuales (`workflow_dispatch`), no en cada push; no se considera validado por defecto. Los tests unitarios no deben salir a internet. En `:app`, Robolectric recibe el SDK instrumentado local resuelto por Gradle y se ejecuta en modo offline.

## 2. Última corrida observada

| Fecha | Commit | Corrida | Resultado |
|---|---|---|---|
| 2026-10-15 | `84afc87` | [38802995010](https://github.com/rmdvcd/Shalev/actions/runs/38802995010) | **Falló el job de tests JVM**; lint, permisos, API mínima y release/debug pasaron. El job de emulador quedó omitido. |

Detalle de esa corrida, anterior a la corrección en esta rama:

| Comprobación | Resultado medido |
|---|---|
| Tests JVM | Dos tests de `ProcesoActualizarTest` no pudieron obtener `org.robolectric:android-all-instrumented:15-robolectric-12650502-i7`; el proceso de tests no tenía acceso a red para resolverlo. No fue un fallo de aserción funcional. |
| Lint | **0 errores, 110 avisos** |
| Permisos | Pasaron |
| API mínima | **3.553 clases revisadas, 0 llamadas no permitidas** |
| Release/debug | Compilaron; R8 no reportó clases ausentes y se publicaron los APKs sin firma como artefactos de CI |
| Instrumentados en emulador | Omitidos en esa corrida |

En esta rama, `app/build.gradle.kts` prepara el SDK instrumentado de Robolectric mediante Gradle y lo pasa en modo offline a **todos los tests JVM de `:app`**, no solo a las capturas Roborazzi. Esta corrección está **pendiente de CI**; localmente `shalevTests` pasa en verde (2026-10-17) con ese SDK preparado por Gradle.

## 3. Verificaciones pendientes

| Pendiente | Estado |
|---|---|
| Confirmar la corrección de Robolectric | Esperar una corrida de CI de esta rama; localmente `shalevTests` ya pasa (2026-10-17) con el SDK preparado por Gradle |
| Capturas Roborazzi | Ejecutadas el 2026-10-17: 525 capturas, 1 fallo de comparación en `01f_inicio_dialogo_cerrar_turno.png` (del cierre con conteo, no regenerada); ninguna captura de Registros cambia |
| Tests instrumentados de Room/UI | `:data:RepositoriosRoomTest`: 9/9 en verde el 2026-10-17 en el teléfono 4bc9f8f3 (Android 15, API 35). `:data:TurnoRepositoryInstrumentedTest`: 8/8 en verde el 2026-10-17, con los 2 casos nuevos de corrección de un ajuste. Los instrumentados de UI de `:app` siguen sin ejecutarse en esta revisión |
| Prueba de extremo a extremo en teléfono | Hecha para editar importe/cantidad/método/nº el 2026-10-18 en el teléfono 4bc9f8f3 (ver arriba); pendiente para el cierre con conteo |
| Sincronización principal/secundaria con dos teléfonos Android | Pendiente |
| Release firmado/AAB | No generado ni probado aquí; requiere el keystore fuera del repositorio |
| `tools/verificacion/verificar.sh` | No ejecutado en esta revisión; es una herramienta de verificación independiente que puede descargar compiladores/dependencias |

En esta rama (2026-10-17) sí hay JDK y Android SDK locales: `./gradlew shalevTests` (869 tests JVM) y `./gradlew shalevCheck` (tests + lint + `assembleDebug` + permisos) terminan en verde, y `:data:RepositoriosRoomTest` corre en el teléfono 4bc9f8f3. Lo pendiente para declarar la rama verificada sigue siendo la corrida de CI (job de tests JVM) y la prueba de la interfaz en el teléfono.

## 4. Reproducir

Con JDK 17 y Android SDK 35 configurados:

```bash
./gradlew shalevTests                     # todos los tests JVM
./gradlew shalevCheck                     # tests JVM + lint + assembleDebug + permisos
./gradlew :app:recordRoborazziDebug     # capturas claro/oscuro
./gradlew shalevInstrumentedTests         # requiere teléfono/emulador API 26+
./gradlew shalevRelease                   # APK/AAB; firma si se configura el keystore
python3 tools/verificacion/api_minima.py # después de compilar bytecode debug
```

Añadir a esta tabla cada nueva corrida con fecha, SHA, resultado por job y cualquier límite que no haya sido comprobado. Mantener el historial anterior en `docs/HISTORIAL_DESARROLLO.md`; sus métricas pertenecen a las versiones y commits que menciona, no al estado actual.

## Contraparte nativa iOS `IShalev` — avance (2026-10-24)

- Proyecto Xcode/SwiftUI separado en `IShalev/` (iOS 17+). Incluye onboarding y selección global de módulos; flujo GL con anclas públicas cotejadas literalmente contra Android; bloqueo con autenticación del dispositivo; perfiles de empleados con permisos locales por módulo/acción; fotografías desde la fototeca; pegado/análisis manual de SMS de Transfermóvil; corrección/anulación de ventas del turno abierto; PDF/CSV; respaldo propio cifrado `.shalev-ios`; guía, soporte y consulta de versiones `ios-v*`.
- `.github/workflows/ios-build.yml` compila una app de simulador en `macos-15`, la empaqueta para Appetize y publica el ZIP de Actions durante siete días. Si están configurados `APPETIZE_API_KEY` (y opcionalmente `APPETIZE_PUBLIC_KEY`), el job intenta subir/actualizar la app de Appetize.
- **CI iOS (2026-10-24):** [run `40112292578`](https://github.com/rmdvcd/Shalev/actions/runs/40112292578), commit `94e5287`: **verde**. `swift test --package-path IShalev` ejecutó las nuevas pruebas de reglas/derivación del PIN, bloqueo por intentos y compatibilidad de perfiles antiguos; después `xcodebuild` compiló la app para iOS Simulator y publicó el ZIP (retención de 7 días).
- **CI Android (2026-10-24):** [run `40112292573`](https://github.com/rmdvcd/Shalev/actions/runs/40112292573), mismo commit: **verde** (tests JVM y compilación de instrumentados, lint, permisos, API mínima y APKs). El job de pruebas instrumentadas en emulador quedó omitido según la configuración ordinaria.
- **Appetize verificado (2026-10-26):** `APPETIZE_API_KEY` ya está disponible para el workflow. Los runs `40155048824` (`f82be0a`) y `40155895866` (`c2a6cef`) subieron la app y anotaron la URL en el resumen del job. La build de `c2a6cef` está en [appetize.io/app/kczay4chapvuf53xy6w62fyy7a](https://appetize.io/app/kczay4chapvuf53xy6w62fyy7a) y carga como `IShalev` v1.0.0 sobre iPhone 14 Pro con iOS 17.2; la versión incrustable responde en `appetize.io/embed/kczay4chapvuf53xy6w62fyy7a`. El simulador queda a la espera de **Tap to Start**: el arranque de la sesión y la interacción ocurren en el navegador, no en el runner de CI. Sin `APPETIZE_PUBLIC_KEY`, cada run crea una app nueva (las builds `oxa2…` y `kczay…` son de runs distintos); configura ese secreto con la clave pública para actualizar una sola app. El ZIP sigue en los artefactos del run para subirlo a mano.
- **Alineación visual iOS/Android (2026-10-26):** se corrigieron Inicio, Venta y Ajustes de IShalev para que sigan la estructura de la app Android (bloque fijo con turno/período/banner, dos botones flotantes, venta a pantalla completa por pasos y lista única de Ajustes). Runs verdes de `Compilación iOS de IShalev`: `40252507437`/`40252507416` (Inicio, `a1b67b5`), `40252841474`/`40252841481` (Venta, `8dac2a3`) y `40253094544`/`40253094427` (Ajustes, `88093c8`). No se hizo sesión interactiva; el repaso visual queda pendiente en Appetize o en un iPhone.
- **Compilación local:** NO DISPONIBLE; el entorno es Linux y no tiene `swift` ni `xcodebuild`. La compilación aprobada es la del job macOS indicado arriba.
- **Revisión estática (2026-10-24):** 17 fuentes Swift parseadas con Tree-sitter Swift; `.pbxproj`, workflow YAML y esquema de referencias se validaron estructuralmente. Esto no sustituye `xcodebuild`.
- **Perfiles de empleado (implementado y con pruebas unitarias en verde; falta validar la interfaz):** cada perfil exige PIN de 6–12 dígitos; se guarda un verificador PBKDF2-HMAC-SHA256 con sal aleatoria (310 000 iteraciones), nunca el PIN en claro. Cinco fallos bloquean 15 minutos; el perfil vuelve a bloquearse al pasar la app a segundo plano y al reiniciarla. El retorno al administrador conserva autenticación del dispositivo. Se añadieron campos opcionales para que los datos/respaldo anteriores sigan decodificando; perfiles antiguos sin PIN requieren que el administrador lo configure antes de activarlos.
- **Pendiente mayor:** sincronización de datos entre iOS y Android/otros teléfonos y prueba end-to-end interactiva en Appetize o en un dispositivo físico. Los perfiles y permisos siguen siendo locales y no se sincronizan. El comprobador de actualizaciones no instala (iOS exige TestFlight/App Store/MDM). El SMS se pega manualmente; Excel se exporta como CSV, no `.xlsx`. GL, exportación, respaldo y flujos de venta aún necesitan validación funcional.
- El respaldo iOS mantiene intencionalmente su formato y cifrado propios; no se cambió el contrato GL ni sus claves.
