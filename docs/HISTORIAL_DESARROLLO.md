# Shalev - Historial de desarrollo

## 2026-10-22 - Sembrador de datos de demostración (solo depuración) y material de promoción

Se añadió un **sembrador de datos de demostración** para poblar la app instalada en un teléfono sin tocar el APK de release: `data/src/debug/.../seed/Sembrador.kt` (lógica) y `app/src/debug/.../seed/SeedActivity.kt` (pantalla que lo dispara). Se ejecuta con `adb shell am start -n cu.shalev.app.debug/cu.shalev.app.seed.SeedActivity` y está declarado en el manifiesto **de debug**, así que el release sigue sin llevarlo (D-05 de la auditoría de la 0.32.0). El sembrador vacía los datos operativos y de configuración (y la tabla `empleado`, que no viaja en los respaldos) antes de cargar, de modo que se puede repetir.

Carga **18 meses** de operación (abr-2025 → oct-2026): **886 turnos**, **2 382 ventas** por efectivo y transferencia con sus líneas, transacciones bancarias, movimientos de inventario y de caja; **54 artículos** de inventario —36 productos y 4 elaborados con receta, más **14 insumos**—, **20 servicios** (algunos consumen insumos), 6 clientes fijos, 2 preajustes de precio y **8 apps secundarias** «vinculadas» con sus permisos, teléfono y `device`.

Las existencias se calculan a partir del consumo planificado, así que ningún artículo queda en negativo y los elaborados conservan «Alcanza para N». Los insumos usan el nombre del enum de `UnidadMedida` (`GRAMO`, `MILILITRO`, `UNIDAD`): guardar el símbolo hacía que las recetas se mostraran en «u».

Las 60 fotos de artículos y servicios son imágenes reales de Wikipedia/Wikimedia Commons (`tools/seed_demo/seed_fotos`, créditos en `tools/seed_demo/CREDITOS.md`), descargadas con `tools/seed_demo/fotos_wikipedia.py` y empujadas a `files/fotos` por `run-as`; el sembrador solo escribe la URI si el archivo existe.

Con la app sembrada se tomaron capturas reales del teléfono `4bc9f8f3` (claro y oscuro) y se montaron dos collages en **`promocion/`** (`collage_feed_1080x1350.png` y `collage_historia_1080x1920.png`) con `tools/seed_demo/collage.py`. El detalle está en `promocion/README.md`.

También se abrió turno en el teléfono y se registró una venta de cinco artículos para capturar la pantalla **Nueva venta** (carrito con cantidades y total) y el **Comprobante de pago**; la lista de más vendidos y los totales del Inicio se actualizaron con esa venta.

Con las capturas definitivas se preparó el paquete de **Google Play** en `play_store/`: icono 512×512, gráfico destacado 1024×500, ocho capturas 1080×1920 (9:16, recortadas de las de 2400) y `ficha.md` con nombre, descripción corta y completa y novedades. Lo genera `tools/seed_demo/play_store.py`. `play_store/README.md` anota lo que falta para publicar: el AAB firmado con la misma clave (`app/build/outputs/bundle/release/app-release.aab`), la política de privacidad y los formularios de la consola.

## 2026-10-22 - Primera versión pública: Release v1.0 en GitHub

Se publicó la **Release `v1.0`** del repositorio `rmdvcd/Shalev` con el APK universal y su `.sha256`: la primera versión pública de la app. Se firmó con el **mismo keystore** de los builds anteriores (certificado `SHA-256 139d3da9…1738`), sin generar ni cambiar ninguna clave: cambiar la firma anula las licencias emitidas (el `deviceId` es `SPVI:` + `ANDROID_ID`, y el `ANDROID_ID` depende de la firma) y obliga a desinstalar.

**(1) El número de versión sube a 1.0.** La app se declaraba `0.32.0` (`versionCode 52`), pero el actualizador compara la etiqueta de la Release (`tag_name` sin la «v») con la versión instalada (`Actualizaciones.esMasNueva`) y `ActualizacionObligatoria` **bloquea la app 30 días después** de ver una versión más nueva y no instalada. Con una Release `v1.0` y un APK que siguiera diciendo `0.32.0`, quien instalara la v1.0 seguiría viendo «1.0» como pendiente y acabaría con la app bloqueada sin actualización que lo resolviera. Por eso `versionName` pasa a `1.0` y `versionCode` a `53`: el APK publicado es de verdad la 1.0.

**(2) Los recursos de la Release importan.** El actualizador toma el **primer** recurso que empieza por «Shalev» y acaba en `.apk`, y exige huella: usa el `digest` SHA-256 que da la API o, si falta, el recurso `<apk>.sha256`. Por eso la Release lleva **un solo** APK, `Shalev-1.0.apk` (universal: arm64-v8a, armeabi-v7a y x86_64; 47,7 MB), más `Shalev-1.0.apk.sha256`. Publicar también los APK por ABI con el mismo prefijo volvería ambigua la elección.

**(3) Firma de release.** `keystore.properties` (local, fuera de git) apunta a `~/.android/gl-release.jks`, alias `gl`, el mismo con el que se firmaron los APK de release anteriores del equipo; `apksigner` confirma el mismo certificado. No se generó ni se sustituyó ninguna clave.

**Pruebas.** `shalevRelease` en verde: `shalevCheck` (tests JVM, lint con baseline, APK debug y control de permisos) + AAB + APK firmado. El daemon de Gradle que estaba en marcha traía 1 GiB de heap y R8 lo agotó; se reinició con 4 GiB (`-Dorg.gradle.jvmargs=-Xmx4096m`) sin tocar `gradle.properties`.

## 2026-10-22 - Activación de licencias arreglada: claves de GL vigentes fijadas en el build

**Síntoma.** Ninguna licencia se podía activar. En el emisor, al pegar la solicitud de Shalev, GL contestaba **«solicitud dañada o no es para GL»**: la clave con la que Shalev cifraba (`LicenseTrust.ECDH_KEY`) no era la de la instalación GL del teléfono, así que el emisor no podía descifrarla. Sin solicitud aceptada no hay licencia que activar.

**Causa.** Las claves de GL se generan en el **Android Keystore de cada instalación** y no son del producto. La ECDH fijada en el build era la del bundle `gl-public-keys.txt` (snapshot 05/10/2026, `sha256:2a3f:…:666d`), pero el teléfono `4bc9f8f3` tiene hoy **GL 1.2.0 (`dev.gl.license`), instalado el 20/10/2026**, con un par nuevo. El cliente ya había seguido dos rotaciones de *firma* (15/10 y 30/09) pero no la de *cifrado*, que es la que rompe la solicitud.

**Arreglo.** Se leyó la pestaña **Claves** del GL instalado (árbol UI con `uiautomator`; la ventana sale en negro por `FLAG_SECURE`) y se recalcularon las huellas SHA-256 desde el Base64 antes de fijarlas:

| | Antes (fijada) | Ahora (GL 1.2.0, 22/10/2026) |
|---|---|---|
| ECDH | `sha256:2a3f:…:666d` | `sha256:fb3a:f7fc:…:078d` |
| Firma | `sha256:58d4:…:8c4e` (primera) | `sha256:113f:a5f6:…:4b1c` (primera) |

`LicenseTrust.ECDH_KEY` se **sustituye** (solo sirve para cifrar solicitudes nuevas; las pendientes con la clave vieja hay que regenerarlas) y la firma nueva se **añade al principio** de `SIGN_KEYS`, conservando `58d4…`, `e693…`, `d9c4…` y `8a91…` para las licencias ya emitidas. `LicenseTrustTest` comprueba las cinco huellas en orden y que la ECDH coincide con la fijada.

**Aviso para el próximo build.** La ECDH solo es válida mientras no se borren los datos de GL ni se reinstale (`pm clear`). Si eso pasa, el par cambia y hay que repetir este trabajo: leer la pestaña Claves, recalcular huellas, sustituir `ECDH_KEY`, añadir la firma y publicar build.

**Pruebas.** `:licencia:test` en verde (`LicenseTrustTest`, `GoldenVectorTest` con la licencia larga real del 03/10, que sigue verificando con la firma histórica). Se comprobó el ciclo completo en el teléfono: solicitud nueva → GL la descifra → emite `SPVI2:` → Shalev la activa.

## 2026-10-21 - Auditoría de interfaz (ui-ux-pro-max): teclado, icono de continuar y ventanas (0.32.0)

Se auditó la interfaz completa (`:designsystem` + 20 pantallas + las 274 capturas) con la base de reglas de `ui-ux-pro-max`. El informe con la evidencia (`archivo:línea` y mediciones) queda en [`docs/AUDITORIA_UI_2026-10-21.md`](AUDITORIA_UI_2026-10-21.md). La app sale bien parada: sin colores ni tamaños sueltos, una sola familia de iconos, estados de carga/vacío/error en todas las listas, contraste verificado por test en claro y oscuro, diálogos centrados y gráficos sin rejilla. Se aplicaron los hallazgos que eran defectos reales.

**(1) El teclado tapaba la barra de acción de la venta.** Con `enableEdgeToEdge` (`MainActivity.kt`) la ventana no se redimensiona al abrir el teclado: hay que aplicar el inset del IME a mano, como ya hacían los formularios. En **Venta** (paso «Datos del cliente»: nombre, carné, teléfono y número de transacción) y en **Modificar venta** (datos del cliente y motivo) faltaba: la barra «Cancelar / Confirmar» quedaba bajo el teclado. Ahora ambos `Scaffold` llevan `imePadding()`, y el contenido se encoge para que los campos sigan a la vista.

**(2) «Continuar» usaba el icono del método de pago.** El botón principal del carrito de Venta era `ShalevPrimaryButton("Continuar", icon = Efectivo|Transferencia)`: el icono de «Efectivo» (`MaterialSolido.Payments`) a 24 dp **se confundía con una cámara** en la barra inferior. Ahora el icono dice la acción (flecha, `ShalevIcons.Siguiente`, como en el resto de «Continuar» de la app) y el método de pago elegido se sigue viendo en los chips de arriba. Solo cambian 5 capturas de Venta (× claro/oscuro).

**(3) Las ventanas emergentes no respetaban las barras del sistema.** `ShalevVentana` (base de `ShalevDialog` y `ShalevBottomSheet`) abría su propia ventana con `decorFitsSystemWindows = false` y solo apartaba el teclado: en **horizontal** la hoja podía quedar bajo la barra de estado o bajo el notch (la Activity sí compensa el recorte, `MainActivity.kt:73-76`, pero el diálogo es otra ventana). Ahora usa `safeDrawingPadding()` (barras del sistema, recorte y teclado). El velo oscuro sigue pintando toda la pantalla.

**(4) Comentario desactualizado.** `Charts.kt` decía «animación de entrada de 300 ms» cuando la entrada es un muelle sin rebote (`ShalevMotion.muelle()`) y el tope del proyecto es 250 ms.

**Lo que se deja pendiente, y por qué.** La **casilla de selección de 24 dp** (`ShalevSize.casilla`) es el único elemento con un área táctil por debajo de 48 dp: es la entrada a la selección múltiple y está junto a una fila que abre la ficha, así que fallar un dedo abre el producto en vez de marcarlo. El cambio (área táctil de 48 dp conservando el dibujo de 16 dp) **queda a decisión del dueño** y anotado en `DESIGN_SYSTEM.md`. El resto de la auditoría (contraste, estados, spacing, iconos, movimiento, diálogos centrados, gráficos sin rejilla) se comprobó y estaba correcto.

**Pruebas.** `shalevTests` **883 en verde** (sin cambios: 19 `:core` + 114 `:licencia` + 229 `:domain` + 93 `:data` + 34 `:designsystem` + 394 `:app`). `:app:verifyRoborazziDebug`: **274 capturas en verde**; se regrabaron **10 PNG** (5 capturas de Venta × claro/oscuro) por el icono de «Continuar» y **ninguna** por las ventanas (en Robolectric los insets son 0, así que el cambio no se ve en las capturas: solo se nota en un teléfono real). `shalevCheck` en verde.

**Lo que no se probó.** No se probó en un teléfono (la instalación por USB sigue bloqueada por el HyperOS del 4bc9f8f3): el efecto del teclado y el de las barras del sistema en horizontal **no** se han visto en pantalla. El único cambio de comportamiento de la lista es el icono de «Continuar»; lo demás es layout.

## 2026-10-21 - Auditoría de sobreingeniería aplicada (0.32.0)

Se revisó el repositorio completo buscando lo que sobra (auditoría ponytail) y se aplicaron las correcciones que no cambian el comportamiento. Lo descartado también se anota, para no volver a proponerlo.

**(1) Código muerto.** Salen `AppResult.isOk`, `errorOrNull()` y `AppError.err()` (nadie los usaba), la propiedad `formatos` de `ExportadorDocumentos` (los dos formatos son los mismos desde que se retiró el TXT), el icono `ShalevIcons.Reiniciar` y el espacio `ShalevSpacing.xl`, que no tenía ningún uso.

**(2) Una dependencia menos.** `androidx.exifinterface` sale: la rotación de la foto se lee con el `ExifInterface` del sistema (`android.media`, API 24; la app pide 26).

**(3) El mensaje de «no hay app para compartir» se escribe una vez** (`Compartir.SIN_APLICACION`): estaba copiado en `TextosInventario`, `TextosServicios` y `TextosRegistros`.

**(4) Piezas compartidas de las listas y de la receta.** `app/common/ListasComunes.kt` reúne `BotonFiltro` (con la insignia de filtros activos), `BarraSeleccion` (quitar, marcar todo, exportar y eliminar; comprueba `exportar` y `editarInventario`), `BarraContinuarVenta` y `HojaAgregarInsumo`. Sustituyen a las copias privadas de Inventario, Servicios, Registros, Producto y Servicio.

**Lo que se descartó, y por qué.** El buscador no se comparte: su segunda fila (contador y chip) es distinta en Registros (importe en negrita) y en Inventario/Servicios (el contador solo aparece al cargar), y un hueco genérico obligaría a cada pantalla a calcular si está vacío, con riesgo de mover el alto. La hoja de exportar tampoco: las filas de Inventario (destinatario y ficha) y las de Servicios/Registros no tienen los mismos campos, y el modelo de fila costaba lo mismo que las tres copias. Los tres formularios y las filas fijadas se quedan como están: comparten el 49 % del código y unificarlos pedía parámetros para todo.

**Pruebas.** `shalevTests` **883 en verde** (las mismas que el lote: no se retiró ni añadió ninguna; la cuenta del lote era 880 porque no incluía los 3 de `AlmacenFotosTest`). `:app:verifyRoborazziDebug`: **274 capturas en verde sin regenerar nada por este lote** (las piezas extraídas dibujan exactamente igual). Al verificarlas se vio que **72 PNG (36 capturas × claro/oscuro) de Ajustes e Inicio seguían con el rótulo «SPVI»** de antes del renombrado a Shalev: no era ruido de renderizado, era una dorada vieja; se regeneraron. `shalevCheck` en verde.

## 2026-10-21 - Sincronización a mano, con barra de avance y sonidos; fotos en la secundaria; conteo por categoría (0.32.0)

Ocho pedidos del dueño en un lote. Se conservan las seis decisiones que se tomaron con él: **A)** los datos móviles se descartan; **B)** «cambió» = lo que se movió **en este teléfono** durante el turno; **C)** la principal aplica la diferencia del conteo sobre su valor actual (no lo rechaza); **D)** el conteo sigue en el diálogo de cierre, agrupado por pestañas de categoría; **E)** tres canales de notificación con sonido propio; **F)** la principal oye conectar/desconectar/sincronizado y la secundaria solo «sincronización terminada».

**(1) La sincronización automática desaparece.** Se borra el bucle automático del cliente (`ClienteSync.iniciarAutomatico`, latido de 30 s con espera creciente 5 s → 60 s), los nueve disparos por evento (venta, turno, caja, conteo, fondo, cierre, aviso de la principal) y el conmutador **Automática/Manual**: ya solo hay «Sincronizar ahora». Siguen siendo automáticas dos cosas, porque no son «sincronización automática» sino reglas del negocio: **abrir turno** sincroniza antes (no se empieza un turno con el catálogo viejo) y **pedir el cierre** envía la solicitud. La sesión con la principal **ya no se cierra** al terminar cada ronda: se queda abierta mientras el teléfono esté en la red (y **se reconecta sola** si se cae, 5 s → 60 s), así el dueño ve de verdad si la app de un empleado está conectada (y por eso los sonidos de conectar/desconectar tienen sentido). Cuando la principal avisa de que cambió el catálogo, la secundaria lo dice en «Apps vinculadas» («Sincroniza para recibirlo») en vez de sincronizar sola.

**(2) El fondo lo escribe quien abre el turno.** Se invierte la decisión de 0.26.0 (P73 §4): en una app secundaria el fondo lo escribe el empleado, igual que en la principal. Se borran el diálogo «Asignar fondo» del dueño, el botón «Pedir fondo» del empleado, el aviso «Luis pide abrir turno: asígnale el fondo» de Inicio, el «Actualiza la app de X» de las 0.25.x y todo el protocolo del fondo (`Sincronizar.pideFondo`, `Sincronizar.fondoUsado`, `SincronizarOk.asignaFondos`, `SincronizarOk.fondo`, `FondoAsignado`, `DatosSecundaria.principalAsignaFondo/fondoCent/fondoToken/fondoUsado/pideFondo`, `PuertaTurno.fondoObligado/fondoUsado`). Las columnas `empleado.fondoAsignadoCent/fondoAsignadoEn/aperturaSolicitadaEn` **se quedan** en el esquema (no hay migración): quedan sin usar.

**(3) Barra de avance en las notificaciones y en cada app vinculada.** El protocolo ya sabía el tamaño de cada trama (4 bytes de longitud), así que el avance se mide en bytes sin tocar el formato: `Tramas.leer`/`escribir` informan del avance y `CanalCifrado` lo propaga. En la principal, cada app conectada tiene su barra en «Apps vinculadas» y su avance en la notificación (canal «Sincronización», sin sonido); en la secundaria, la suya. La notificación de la barra se quita al terminar.

**(4) Sonidos propios.** Tres archivos nuevos en `app/src/main/res/raw` (`conectada.wav`, `desconectada.wav`, `sincronizada.wav`, 13–17 KB, generados por `tools/sonidos/generar-sonidos.ps1` — se pueden sustituir por otros conservando el nombre). El sonido queda congelado en el canal, así que hay **tres canales** nuevos («App conectada», «App desconectada», «Sincronización terminada»). El aviso de turno (canal «Turno») sigue **mudo** como se decidió en P29.

**(5) Las fotos de productos y servicios viajan a la secundaria.** Hasta ahora no viajaban: se anulaban en tres sitios (`AlmacenSync`, `RepositoriosSegunTipo`, `EjecutorComandos`). Ahora la instantánea lleva la **lista** de fotos (artículo, nombre del archivo, tamaño) y los **bytes van aparte, por bloques de 256 KB**, con el mismo mecanismo del APK (`PedirFoto`/`BloqueFoto`): la secundaria pide solo las que no tiene, las guarda en su álbum con el mismo nombre y comprueba el SHA-256. La instantánea se reenvía entera cuando el catálogo cambia, pero los bytes de una foto solo viajan una vez.

**(5 bis) El arreglo de las fotos (medido en el teléfono).** Los archivos llegaban, pero los artículos no los mostraban: faltaba el **enlace**. La causa era el sub-token: la firma ECDSA usa un nonce aleatorio (`SHA256withECDSA`), así que el sub-token de la licencia cambia en **cada** ronda → cambia el hash de la instantánea → la principal la reenvía **siempre** → al aplicarse el catálogo, la ruta de la foto se borraba (es un archivo de este teléfono) y, como el archivo ya estaba descargado, la descarga no volvía a enlazarlo. Se arregla en dos sitios: `reemplazarCatalogo` **conserva** la ruta propia cuando el archivo está en el álbum, y `descargarFotos` **enlaza todas** las fotos del catálogo (las descargadas ahora y las que ya estaban), no solo las nuevas. Además, la foto viaja **por su nombre** (el UUID .jpg), no por su ruta: la del principal no significa nada en la secundaria.

**Ponytail:** el hash de la instantánea sigue cambiando en cada ronda por el sub-token, así que el catálogo entero viaja en cada sincronización (unos decenas de KB por la red local: se nota poco). Arreglarlo de verdad es un cambio de protocolo (que la licencia viaje aparte, en su propio campo, y que el hash cubra solo el catálogo) y rompe la refrescación de la licencia en las apps anteriores. Se deja así hasta que haya un salto de versión en el que se puedan actualizar las dos apps a la vez.

**(6) El conteo de inventario es parcial y va por categoría.** Antes se contaban **todos** los productos y **todos** los insumos (una lista plana en el paso 2 del cierre). Ahora se cuenta **solo lo que cambió durante el turno** (los artículos con movimientos de ese turno) y la pantalla lo separa por pestañas de categoría («Alimentos», «Bebidas», «Insumos»…). La categoría viaja en el conteo (`LineaConteo.categoria`, campo opcional del DTO) y encabeza la tabla «Conciliación de inventario» del PDF/Excel. En la principal, la diferencia se aplica **sobre el valor actual** (si la principal movió el artículo después de la foto del empleado, no se rechaza el conteo: se aplica el delta sobre lo que hay ahora).

**(7) Casillas más pequeñas y el icono de «móvil».** Las casillas de verificación de toda la app pasan a un componente propio, `ShalevCheckbox` (`:designsystem`): la de Material 3 mide 28 dp de lado y 18 dp de cuadro; la de Shalev, 24 dp y 16 dp. La fila entera sigue siendo lo que se toca, así que el área táctil no cambia. Y el icono de «Apps vinculadas» (en Ajustes, en la Ayuda y **en cada app vinculada** de la lista) es ahora un **móvil** (`ShalevIcons.Movil`, que ya existía para el recorrido inicial).

**(8) Se borran «Design system» y «Generar datos de prueba».** 15 ficheros, 22 pruebas JVM, 39 fotos de la bodega de prueba y la ruta `Route.Catalogo`. El **módulo `:designsystem` no se toca** (es la base de toda la interfaz): solo sale su catálogo de ejemplo (`preview/ShalevCatalog.kt`) y la pantalla que lo enseñaba. El generador de prueba vivía en `src/main` y por tanto **viajaba en el APK de release** (hallazgo D-05 de la auditoría): ahora no viaja.

**Pruebas.** `shalevTests` en verde; `shalevCheck` en verde (con `-Xmx4g`: con 3 GB falla el empaquetado por falta de memoria). Pruebas nuevas: `Sync32Test` (convivencia con una app anterior: los datos guardados con `modo`/`pideFondo` se leen igual, una principal 0.31 sin fotos se entiende, la instantánea avisa de las fotos y el bloque lleva su hash), `FondoEnTurnoTest` (el fondo es el que escribe quien abre, también 0), `AlmacenFotosTest` (el nombre de la foto se valida y el archivo nunca sale de la carpeta) y dos casos de `FondoAperturaTest` (el texto del avance y el de la conexión). Se retiraron `FondoSyncTest` y `FondoAsignadoTest` (prueban lo que ya no existe) y los casos de «asignar fondo» de `VinculacionViewModelTest`. **883 casos JVM en verde** (19 `:core` + 114 `:licencia` + 229 `:domain` + 93 `:data` + 34 `:designsystem` + 394 `:app`). En el teléfono 4bc9f8f3 (Android 15): el APK debug arranca sin fallos, Ajustes ya no muestra las dos pantallas retiradas, «Apps vinculadas» abre sin las filas de fondo y los **cuatro canales** quedan con su sonido (`sync_conectada`, `sync_desconectada`, `sync_terminada` con audio; `sync_progreso` mudo). En el teléfono HEQKPFXSGMYPNNAE (OnePlus, Android 11, la secundaria): tras sincronizar, **el inventario muestra las fotos** de los productos (antes salían sin foto) — comprobado con captura de pantalla.

## 2026-10-20 - Renombrado a Shalev y ajustes de filtros (0.31.0)

**(1) Nombre.** La app pasa de SPVI a **Shalev**: etiqueta del icono, textos de pantalla, `rootProject.name`, paquete `cu.shalev.*` (módulos `:app`, `:data`, `:domain`, `:licencia`, `:core`, `:designsystem`), clases `Shalev*`, tareas Gradle (`shalevTests`, `shalevCheck`, `shalevPermisos`, `shalevRelease`, `shalevInstrumentedTests`), esquema Room `cu.shalev.data.db.ShalevDatabase`, base de datos `shalev.db`, respaldo `.shalev` (formato `spvi-respaldo`), nombres de archivo exportados (`Shalev_*`), carpeta `Pictures/Shalev`, servicio NSD `Shalev-*`, User-Agent y toda la documentación. **No cambia** el contrato con GL ni el de los ficheros ya escritos: se conservan `SPVI2:`, `SPVI1:`, `SPVIR1:`, el prefijo `deviceId = "SPVI:"`, `SPVI-VINC1:`, `SPVI-REV|`, la magia `SPVIBAK`, las etiquetas HKDF/AAD del canal cifrado, el tipo `spVi` del registro de prueba y los alias del Keystore (`spvi_aead_v1`, `spvi_device_ecdh_v1`, `spvi_licencia_firma_v1`). Cambiar cualquiera de ellos rompería licencias ya emitidas, la vinculación entre apps o las claves guardadas en el teléfono. Como el paquete cambia de nombre, la app se instala **como una app nueva**: no hereda datos, prueba ni vinculaciones de la anterior.

**(2) Filtro de Registros  Movimientos.** La ventana de filtro ya no muestra la sección ni el aviso de importe: solo el período (y las fechas si es personalizado). Se retiran `TextosRegistros.SIN_IMPORTE_EN_MOVIMIENTOS` y el `Seccion("Importe")` que se dibujaba siempre.

**(3) Filtro de Inventario.** El combo **Estado** queda en **Todos, Stock bajo, Stock crítico, Próximos a caducar y Sin existencia** (`ALERTAS_FILTRO`). Los avisos de insumos y el nombre repetido siguen existiendo en Inicio y en la ficha, pero ya no se ofrecen como filtro de la lista.

**(4) Manual y Ayuda.** `MANUAL_USUARIO.md` recoge los dos filtros y corrige las secciones reales de la Ayuda. `AyudaContenido` ańade el tema **Buscar y filtrar el inventario** y aclara que en Movimientos solo se filtra por fecha.

**Pruebas.** `shalevTests` en verde, incluido el caso nuevo `filtroDeEstadoSoloOfreceLasCincoOpciones`. `shalevCheck` y `:app:assembleDebug` en verde tras limpiar las cachés incrementales (el paquete anterior dejaba clases `cu.spvi.*` en `build/`). APK debug instalado y abierto en el teléfono 4bc9f8f3.

## 2026-10-19 - Auditoría de UI: chips de Precios y remates de accesibilidad (0.31.0)

Se auditó la interfaz con `ui-ux-pro-max` y se contrastaron sus propuestas con `DESIGN_SYSTEM.md` y `UI_UX_IX.md`. Se aplican solo los hallazgos que no contradicen las reglas del proyecto: la barra inferior de cinco ítems sigue siendo de solo iconos (con tooltip y TalkBack) y los botones de acción siguen la regla P24/P25.

**(1) Precios.** Los tres chips de método de pago (Cualquier pago, Efectivo, Transferencia) pasan a `FlowRow` con separación y alineación centrada. Antes, a 360 dp, Transferencia se comprimía a 34 px y el texto se cortaba; ahora baja a una segunda fila y conserva su ancho mínimo. La prueba `elChipDeTransferenciaNoSeAplastaEnElFormularioDePrecios` mide el ancho y falló en rojo antes del cambio.

**(2) Inicio y componentes.** El subtítulo de `ShalevAccordionCard` se oculta cuando la tarjeta está abierta: el total de «Ventas» ya no aparece dos veces. `ShalevCard` marca su título como encabezado para TalkBack. `ShalevStatusBanner` muestra un chevron cuando es clicable y no tiene una acción propia. `ShalevIconAction` y `ShalevListItem` solo anuncian `selected` cuando es verdadero, no como «no seleccionado» en cada fila.

**(3) Filtros, onboarding y salida.** En Inventario, Servicios y Registros el contador del filtro va en la descripción del botón («Filtrar, 2 filtros activos») y el badge queda decorativo, para no leer un número suelto. «Ahora no» del onboarding usa el icono de saltar en vez del reloj, que se confundía con «más tarde». La confirmación de «Salir sin guardar» usa el icono de salida (`ShalevIcons.Salir`), no la papelera, tanto en `GuardiaCambios` como en `ShalevBottomSheet`.

**Pruebas.** `AuditoriaUiTest` añade 7 casos: encabezado de tarjeta, subtítulo de acordeón, `selected` verdadero/falso en botón y fila, descripción de filtros con plural y ancho del chip a 360 dp. Antes de implementar, los 5 casos de los arreglos fallaron; el de `selected = true` pasó como control y el séptimo (descripción del filtro) se añadió con el helper. `shalevTests`: **905 casos JVM en verde** (19 `:core` + 114 `:licencia` + 250 `:domain` + 91 `:data` + 34 `:designsystem` + **397** `:app`). `shalevCheck`: **OK**. `:app:verifyRoborazziDebug`: **verde** tras regenerar **38 PNG** de este lote (11 capturas de Inicio, 2 de Precios, 4 de onboarding y los diálogos de salida de Perfil e Inventario, × claro/oscuro); los 30 de Inventario ya venían de la retirada del OCR. No se reinstaló el APK ni se probó con TalkBack o en landscape; la accesibilidad se comprobó por semántica de Compose y por las capturas.

## 2026-10-19 - Se retira el escáner OCR de inventario (0.31.0)

Por decisión del dueño, el escáner OCR de inventario se elimina del proyecto. Se borran `EscanerInventarioLogic.kt`, `EscanerInventarioViewModel.kt`, `EscanerInventarioScreen.kt` y sus dos pruebas JVM (`EscanerInventarioLogicTest`, `EscanerInventarioViewModelTest`), la ruta `Route.EscanerInventario`, su `composable` en `MainScaffold`, el icono **Escanear** de la barra de Inventario, `TextosInventario.ESCANEAR` y los 42 textos `scan_*`. También sale `com.google.mlkit:text-recognition` (versión `mlkitText` y entrada `mlkit-text` del catálogo): el APK deja de llevar el modelo OCR y sus bibliotecas. Se conservan el lector QR de vinculación (`barcode-scanning`), la cámara para fotos de productos y servicios, y el permiso `CAMERA`.

Se retiran `docs/ESCANEO_OCR.md` y `docs/OPENCODE_ESCANEO_OCR.md`; las entradas anteriores de este historial que los enlazan son históricas. PP-Layout/ONNX nunca llegó a implementarse y sale del alcance.

**Pruebas.** `shalevTests`: **898 casos JVM en verde** (19 `:core` + 114 `:licencia` + 250 `:domain` + 91 `:data` + 34 `:designsystem` + 390 `:app`); son **24 menos** que los 922 anteriores: los 25 tests del escáner retirados menos 1 prueba de limpieza que se había añadido antes de retirarlo. `shalevCheck`: **OK** (la primera pasada falló al empaquetar por `Java heap space` con `-Xmx3072m`; con `-Xmx4g` terminó en verde). `verifyRoborazziDebug`: **527 capturas en verde** tras regenerar las **30 doradas** de Inventario (15 × claro/oscuro) que llevaban el icono; ningún otro PNG cambia. En el teléfono 4bc9f8f3 (Android 15, API 35), APK debug instalado: la barra de Inventario muestra solo **Filtrar** y **Exportar**, sin **Escanear**, y la lista sigue operativa.

## 2026-10-19 - El seed de prueba con fotos de verdad, sin red y con licencia redistribuible (0.31.0)

«Generar datos de prueba» creaba la bodega con los nombres del catálogo, pero todos los artículos salían **sin foto**. Ahora el seed copia al almacén de fotos del teléfono las imágenes de los 30 productos (los 2 Elaborados no admiten foto por diseño) y los 8 servicios, sin usar la red en tiempo de ejecución: las imágenes **viajan dentro del APK de depuración**.

**(1) De dónde salen y con qué licencia.** Se buscaron en **Wikimedia Commons**, con el filtro puesto en licencias que permiten redistribuir: dominio público, CC0, CC BY y CC BY-SA; **nada** con cláusula NC (no comercial) o ND (sin obras derivadas). De cada artículo se bajaron 4 candidatas a una hoja de contactos (§ por categoría de Commons) y **se eligieron mirándolas una por una**, no por el título: la primera pasada automática dio un lince como «desodorante», una planta como «galletas» o una autopista de Salt Lake City como «sal». Las claves son las del catálogo (`CatalogoBodega`), así que la ausencia de archivo significa «sin foto», sin ningún dato más.

**(2) Dónde viven y cómo entran.** `app/src/debug/assets/seed/fotos/<clave>.jpg` — **solo depuración**, por el `sourceSets` del `build.gradle.kts` de `:app`; el APK/AAB de release no las lleva (y sigue sin poder sembrar: la pantalla está detrás de `BuildConfig.DEBUG`). Se recortan a ≤512 px y se re-codifican a JPEG (1,5 MB los 38 archivos). Al sembrar, `FotoRepository.importarIncluida(clave)` las copia **por el mismo conducto que las fotos del usuario** —`guardar` re-codifica a JPEG ≤1024 px y descarta EXIF—, así que en el teléfono son fotos normales del almacén de siempre, y `limpiarHuerfanas` las recoge cuando el seed vuelve a borrar todo.

**(3) La foto nunca tumba el seed.** El orquestador pide la imagen de cada producto no elaborado y de cada servicio y, si esa compilación no la trae (release, APK de otra rama) o la copia falla, el artículo se siembra **sin foto**: la foto es cosmética y el seed sigue siendo «todo o nada» para los datos. Los **insumos** quedan fuera: no tienen campo de foto en el modelo (ni en el formulario ni en la base de datos), y añadírselo habría exigido esquema v14 y migración para una decoración de la demo.

**(4) Créditos.** `docs/CREDITOS_FOTOS_SEED.md` lista las 38 con título original, autor, licencia y enlace a Commons (copia dentro de los propios assets, `CREDITOS.md`). Las CC BY-SA se comparten bajo la misma licencia por ser versiones reducidas; las CC BY solo piden atribución; las CC0 y de dominio público se acreditan igual, por trazabilidad.

**Pruebas.** Primero en rojo, en `OrquestadorSeedTest`: `fotosIncluidasEnProductosYServicios` (cada producto no elaborado y cada servicio llevan la foto de **su** clave, el elaborado no la pide, el insumo tampoco) y `unaFotoQueFaltaNoAbortaElSeed` (con el almacén de fotos fallando, el seed termina `Ok` y todo queda sin foto). Se comprobó que muerden de verdad con una **mutación temporal** (foto siempre `Err`) que puso las dos en rojo, y se revirtió. `shalevTests`: **922 casos JVM en verde**; `shalevCheck`: **OK**; `mergeReleaseAssets` no lleva ninguna de las 38 (solo debug). En el teléfono 4bc9f8f3 (Android 15, API 35), APK debug: Ajustes › Generar datos de prueba › «Seed listo.», Inventario y Servicios muestran las fotos elegidas, los dos Elaborados salen sin foto y `files/fotos` queda con **38 archivos** (comprobado con `run-as`). Commit `a1743cb`, **CI verde** (run `39198204810`).

**Corrección de una cifra de las entradas anteriores.** `shalevTests` **no** corre los tests de capturas: el `Test` de `:app` excluye `**/capturas/**` salvo cuando la tarea invocada es de Roborazzi (`app/build.gradle.kts`, línea 219), así que los 137 `@Test` de `app/src/test/.../capturas/` (que `recordRoborazziDebug` graba como PNG) **no cuentan** en `shalevTests`: `:app` corre 414, no 551, y el total honesto es **922** (19 `:core` + 114 `:licencia` + 250 `:domain` + 91 `:data` + 34 `:designsystem` + 414 `:app`). Se corrigió el texto en `docs/VERIFICACION.md` y en `AGENTS.md`.

## 2026-10-18 - El escáner OCR de inventario, terminado y ajustado con el teléfono (0.31.0)

El asistente de escaneo del merge #44 llegó sin compilar y sin pruebas; en este lote se terminó (lógica, ViewModel y pantalla), se probó en el teléfono y se corrigieron con esa medición los tres defectos de geometría que impedían importar una lista impresa normal. Todo sin red: ML Kit Text Recognition v2 latino empaquetado, sin descargas dinámicas ni telemetría.

**(1) La lógica, fuera de Android.** `EscanerInventarioLogic.kt` reconstruye la tabla por coordenadas (sin tabuladores ni barras), mapea columnas a campos, arma las filas en revisión y revisa el lote con los validadores del dominio. `EscanerInventarioViewModel.kt` se queda con lo que depende de Android (imagen + ML Kit + estado) y no usa textos: los errores son tipos (`ErrorEscaneo`) que la pantalla traduce. La pantalla es un asistente de 3 pasos (tipo y campos + foto → columnas → revisión) con `Shalev*`; los obligatorios de producto son nombre, categoría, cantidad, costo y venta, y los de insumo nombre, cantidad, costo y unidad, y no se pueden desmarcar (comprobado en el teléfono por el estado `checkable`/`enabled` de la interfaz).

**(2) Nada se guarda sin revisar y confirmar.** Se validan **todas** las filas antes de insertar ninguna; si alguna falla no entra nada y se señala la fila y el campo. El guardado usa los casos de uso existentes fila por fila y **quita cada fila al guardarse**, así un reintento tras un fallo parcial no duplica y se informa cuántos entraron y cuál quedó pendiente. La cantidad del producto es en unidades enteras (como el formulario) y la del insumo en milésimas; una categoría vacía se muestra a la vista como «Otros» (`Categorias.OTROS`), editable, y «Insumos»/«Elaborado» se rechazan con su motivo porque se crean en sus pantallas. La unidad se busca contra `UnidadMedida` y, si no se reconoce, **no se inventa**: la fila se señala.

**(3) Lo que dijo el teléfono (tres defectos, no adivinados).** Con la tabla de productos de 5 columnas:
- **El título estorbaba.** «Inventario de productos» ocupaba la primera fila y el encabezado no se reconocía; ahora se busca la fila de encabezado en las **tres primeras** y todo lo de encima (título, fecha) no entra como registro.
- **Un nombre largo se comía la columna vecina.** Las columnas se agrupaban por solape de cajas y «Arroz grano largo» (que desborda su columna) unía su columna con la de categoría; ahora se agrupan por **borde izquierdo** de la celda —todas las celdas de una columna empiezan en la misma x— y cada celda va a la columna de inicio más cercano.
- **El corte por palabras partía celdas.** Para separar dos columnas que ML Kit devuelve en una línea, se cortaba donde el hueco pasaba del alto del texto, y con eso «Jabon de tocador» (26 px de hueco, 20 px de alto) se partía en dos y aparecían columnas fantasma; ahora el corte exige un hueco que **duplique el alto**. El diagnóstico se hizo con `logcat` temporal en el APK debug, ya retirado: ML Kit devuelve una línea por celda y el corte fino era nuestro.

**(4) PP-Layout/ONNX: retirado y bloqueado.** La detección de disposición con modelo ONNX **no está implementada**: no hay artefacto con licencia de redistribución, procedencia y contrato de salida verificables, así que `onnxruntime-android` salió de `gradle/libs.versions.toml` y de `:app` (declarar el runtime solo engordaba el APK) y los pasos exactos para reincorporarlo quedan en `docs/ESCANEO_OCR.md`. El fallback de ML Kit se conserva: sin estructura confirmada, **una línea = un registro** con aviso visible.

**Pruebas.** `shalevTests`: **920 casos JVM en verde** (19 `:core` + 114 `:licencia` + 248 `:domain` + 91 `:data` + 34 `:designsystem` + 414 `:app`; cifra corregida el 2026-10-19: los 137 tests de `app/src/test/.../capturas/` **no** corren en `shalevTests`); los 414 de `:app` incluyen los 19 tests del escáner del merge #44 —que existían pero no corrían porque el merge no compilaba— y **5 nuevos** de `EscanerInventarioLogicTest` (19 en total): corte de columnas pegadas, hueco de palabra que no corta, nombre que desborda su columna y encabezado bajo un título, todos en rojo antes del arreglo. `shalevCheck`: **OK**. `verifyRoborazziDebug`: **527 capturas en verde**, ninguna cambia. En el teléfono 4bc9f8f3 (Android 15, API 35), APK debug, con tres PNG generados para la prueba: **productos 43 → 47** (4 filas correctas, encabezado mapeado solo), **insumos → 50** (3 filas con unidad `kg` y cantidades `12.5`/`8.25`/`3.75`) y el **párrafo sin tabla**: aviso de estructura no confirmada, «No se pudo leer “Cantidad” en la fila 1» y **nada guardado** (sigue con 50). Se regeneraron también los datos de ejemplo del teléfono (Ajustes › Generar datos de prueba), que cierra el pendiente de los nombres del catálogo sin unidad pegada.

## 2026-10-18 - El filtro por alerta de Inicio y la compilación del escaneo OCR (0.31.0)

**(1) La alerta de Inicio abría el Inventario sin filtrar.** El canal del lote anterior —escribir el nombre de la alerta en el `SavedStateHandle` de la entrada de navegación— **no llega al ViewModel**: con Hilt el `SavedStateHandle` que recibe el ViewModel es **otro objeto** (se crea a partir de los argumentos de la ruta), así que la lista abría sin filtro aunque el test JVM pasara: el test le daba el handle al ViewModel directamente, y en la app real la escritura caía en un handle que nadie lee. Medido en el teléfono con logs temporales, antes del arreglo: la entrada de Inventario tenía `alerta=SIN_EXISTENCIA` en su handle y el ViewModel se construía con `alerta=null` (handles distintos). Ahora la alerta viaja por `AlertaPendiente`, una señal de proceso de un solo uso (como `TourPendiente`): `abrirAlertaInventario` la deja anotada justo antes de abrir la pestaña y el ViewModel la **consume** al construirse como `alerta` del filtro inicial (con `texto = ""`), de modo que no se repite al volver. Se retiraron `InventarioViewModel.KEY_ALERTA`, la observación del handle y el campo `alerta` de `Route.Inventario` (ya no lo escribe nadie).

**(2) El merge del escaneo OCR (#44) llegó sin compilar.** Dos errores que impedían construir el APK: `InventarioContent` llamaba a `onNavigate` sin tener ese parámetro (se añade con valor por defecto al final: las capturas y los tests que montan el `Content` no cambian) y `EscanerInventarioViewModel` declaraba a la vez `var error` y `fun setError(...)`, que chocan en la JVM (la función pasa a `mostrarError`). Con eso el escaneo se puede probar en el teléfono: el botón de la barra de Inventario abre el asistente («¿Qué tipo de registros vas a importar?», Productos/Insumos y los campos a extraer) y ATRÁS vuelve a la lista, sin crash. El PR tampoco regeneró las capturas de Inventario, que ahora llevan ese icono **Escanear** en la barra: 15 capturas × claro/oscuro = **30 PNG** regenerados, y ningún otro cambia.

**Pruebas.** `shalevTests`: **896 casos JVM en verde** (19 `:core` + 114 `:licencia` + 248 `:domain` + 91 `:data` + 34 `:designsystem` + 390 `:app`; la cifra «1033» que llegó a escribirse aquí contaba los tests de capturas de `:app`, que `shalevTests` no ejecuta —los graba `recordRoborazziDebug`—, corregido el 2026-10-19). `shalevCheck`: **OK**. `verifyRoborazziDebug`: **527 en verde** tras regenerar las 30 doradas de Inventario. En el teléfono 4bc9f8f3 (Android 15), con el arreglo: la alerta «Sin existencia» abre el Inventario con «1 de 43 productos» y «Sal fina»; la barra inferior vuelve a Inicio; volver a Inventario conserva el filtro; ATRÁS desde Inventario vuelve a Inicio; la alerta «Insumo bajo» deja «3 de 43 productos» con «Leche en polvo». El test nuevo `InventarioViewModelTest.laAlertaPendienteSeAplicaUnaVezYSeConsume` cubre el canal; el fallo real (handle de Hilt ≠ handle de la entrada) se vio en el teléfono con logs temporales, ya retirados.

## 2026-10-18 - Móvil: cinco correcciones de la revisión del dueño (0.31.0)

Cinco encargos sobre la app instalada; la versión declarada sigue siendo 0.30.0 (`versionCode 51`) y estos cambios se rotulan 0.31.0.

**(1) Inicio → alerta → Inventario filtrado.** Pulsar una tarjeta de alerta empujaba `Route.Inventario(alerta = …)`, que **es el destino de una pestaña**: la barra inferior (`navigateTopLevel` = `popUpTo(start){saveState=true}` + `restoreState=true`) reproducía después esa entrada guardada, así que cualquier vuelta a una sección aterrizaba en el Inventario filtrado y BACK salía de la app —entrada fantasma, sin excepción en el log—. Ahora se abre la **pestaña** (`navigateTopLevel(Route.Inventario())`) y el filtro entra por `AlertaPendiente` (ver la entrada del arreglo, más abajo): la entrega por el `SavedStateHandle` de la entrada no funcionaba. Se retiró `InicioLogic.destinoAlerta`.

**(2) Ficha de una transferencia.** «Ver la venta» quedaba pegado a la izquierda (medido en el teléfono antes del arreglo: x 156–209 de 1080) porque los `Shalev*Button` envuelven el botón en un `TooltipBox` y el `Modifier.align(…)` del llamador se aplicaba a un nodo interno, no al botón. Entra en la misma fila centrada (`ShalevButtonRow`) que Modificar/Anular —después de «Anular venta»— desde `AccionesFichaVenta(..., verVenta = …)`; sin turno abierto queda solo y centrado. No se tocó el sistema de diseño para esto.

**(3) Nombres del catálogo.** El catálogo de datos de ejemplo repetía la unidad en el nombre («Pan (lb)», «Hilo (bobina)»): todos los productos y ese insumo quedan sin sufijo, porque la unidad tiene su columna. Los dos servicios conservan «(lb)»/«(pieza)»: no tienen campo de unidad. Es solo dato semilla —los nombres ya copiados a `detalle_venta`/`movimiento` son históricos y no se reescriben—, así que en un teléfono ya sembrado se ven al regenerar los datos de ejemplo.

**(4) Precios e importes (auditoría, sin bug).** Cambiar un precio **no** altera las ventas pasadas: el importe se congela en `detalle_venta` al registrar (`PlanificadorVenta`), `ProductoRepositoryImpl.actualizarPrecios` solo escribe `producto` y ninguna consulta del histórico se une con el catálogo. Lo único intencional es que, al *modificar* una venta, las líneas nuevas usan el precio actual. Se añadieron tests (dominio y Room) para que siga siendo así.

**(5) Conteo de billetes al cerrar turno.** Estaba implementado (commit `78ca9ae`) pero no se veía: los campos por denominación colgaban de `if (porBilletes)` (por defecto `false`) y el botón que los activaba es de solo icono, así que parecía que no existía. Ahora las denominaciones se ven **de entrada**, «Omitir el conteo de billetes» (esquina inferior derecha, nuevo parámetro `esquina` de `ShalevDialog`) pasa al total suelto —y desde ahí se vuelve a contar— y `CajaForm.algoContado` impide confirmar sin contar al menos un billete o moneda: declarar 0 sin contar descuadraría la caja, así que el 0 explícito se hace con el total. En el inventario, «Omitir el conteo de inventario» guarda el conteo con **contado = esperado** y motivo «Conteo de inventario omitido»: `comprobarConteo` exige que las claves del conteo coincidan con la instantánea actual (una lista vacía se rechaza) y el motivo deja la traza en `ResumenConteo`, en el PDF/Excel del turno y en el respaldo. «Atrás» y editar un campo reinician la omisión para no ignorar lo escrito.

**Pruebas.** `shalevTests`: **896 tests JVM en verde**; `shalevCheck`: **OK**; `verifyRoborazziDebug`: **527 capturas en verde** tras regenerar las dos doradas que cambian (`04p_registros_ficha_transferencia` por el botón centrado y `01f_inicio_dialogo_cerrar_turno` por los billetes y «Omitir», en claro y oscuro). Nuevas: `CajaConteoUiTest` (Robolectric: los billetes se ven de entrada, ✓ deshabilitado sin contar, «Omitir» pasa al total suelto, el 0 escrito a mano sí vale y 23 × 100 suma 2,300.00 «Cuadra»), caso de alerta en `InventarioViewModelTest` (el `SavedStateHandle` aplica el filtro y lo consume), `CatalogoBodegaTest.nombresSinUnidadPegada`, el precio congelado en `VentaFlujoTest` (producto y costo de un elaborado) y `RepositoriosRoomTest.cambiarElPrecioNoAlteraElImporteDeLoVendido`, comprobado **en rojo** reescribiendo a propósito el detalle desde `actualizarPrecios` (esperado 30.00 CUP, obtenido 99.00 CUP) y en verde después. En el teléfono 4bc9f8f3 (Android 15): «Ver la venta» centrado (x 515–568) y, con el turno abierto, la fila [Modificar][Anular][Ver la venta] (x 374–709, centro 541); cierre de turno completo con el conteo por billetes (23 × 100 = 2,300.00, «Cuadra») y los dos «Omitir» en la esquina, cerrando de verdad sin error de inventario cambiado. `:data:connectedDebugAndroidTest`: **60/60 en verde**.

**Hallazgo aparte (no era del encargo):** los tres tests de migración de `EsquemaTest` (4→5, 5→6 y 6→7) fallaban con «duplicate column name: clienteFijo»: construyen la BD vieja recortando el `12.json` y no quitaban `transaccion.clienteFijo`, que la v10 añade, así que `MIGRACION_9_10` la volvía a añadir. Es un fallo del fixture, no de las migraciones —un teléfono real que venga de la v9 sí la necesita—; ahora el recorte incluye esa columna (y la tabla `cliente_fijo`) en los esquemas anteriores a la v10.

## 2026-10-17 - Conteo de billetes y conciliación física al cierre (en desarrollo)

Decisiones del dueño: billetes CUP, incluidos **5000 y 20000**; ajuste del inventario con confirmación y motivo; cada empleado cuenta y la principal revisa y autoriza. El cierre tiene tres pasos (efectivo, inventario y revisión). Se conserva la entrada de total y «Cuadra». Los insumos mantienen milésimas y los elaborados virtuales se excluyen. El conteo se conserva en el turno, su ficha Caja y sus PDF/Excel, respaldo y sincronización.

Room pasa a v13 con `conteoCierreJson` nullable y migración aditiva 12→13. La declaración no cambia stock. En el cierre propio, comprobación de existencias, ajustes, movimientos AJUSTE y resumen/cierre van en una transacción. Para empleados, la principal aplica en la transacción de aprobación y la secundaria cierra sin repetir ajustes. Se comparan las existencias antes de aplicar, se protege la idempotencia y se liga la aprobación al UUID y al contenido del conteo. Los reenvíos no borran autorizaciones de la principal. Un conteo sin aprobación impide el cierre remoto; los turnos anteriores sin conteo conservan compatibilidad. Se requiere actualizar ambas apps para usar la conciliación nueva.

Pruebas añadidas: cálculo/validación/exportación en dominio, ida/vuelta Room-DTO-JSON, migración 12→13 instrumentada y JDBC, cierre con ajustes y rollback ante stock cambiado, y sincronización con dos bases de datos (declaración, aprobación y reenvío sin duplicar). **No ejecutadas** por falta de JDK/SDK. El análisis sintáctico Tree-sitter de los Kotlin cambiados no detectó errores (no es compilación). Se ejecutaron `git diff --check` y una prueba Python/SQLite del SQL aditivo contra el esquema v12 (conserva fondo/contado, nueva columna nullable). `./gradlew shalevTests` no pudo iniciar por falta de Java; API mínima no pudo ejecutarse por falta de ANDROID_HOME. **Pendientes: generar `13.json` con Room/KSP, compilar, ejecutar toda la suite, capturas y dos teléfonos. No es un release verificado.**

## 2026-10-17 - Móvil: corregir lo registrado en las cuatro pestañas de Registros (0.31.0)

Encargo del dueño: desde Registros, con el turno **sin cerrar**, poder arreglar «importe, cantidad, método de pago y nº de transacción según corresponda» en Ventas, Servicios, Transferencias y Movimientos. Ventas y Servicios ya lo tenían desde la 0.25.0 (ficha → *Modificar venta* / *Anular venta*, solo principal y turno abierto). Faltaban las otras dos.

**Transferencias.** El importe y el nº de una transferencia **son** de la venta que la pagó (`transaccion.importe = total de la venta`), así que corregirlos por separado los dejaría descuadrados de la caja y de la ganancia: su ficha ofrece las mismas acciones de la venta reutilizando `AccionesFichaVenta`, además del botón *Ver la venta* que ya tenía. `FichaRegistro.DeTransferencia` lleva ahora la venta (`venta: Venta? = null`, se carga al abrir la ficha en `RegistrosViewModel.cargarVentaDeTransferencia`).

**Movimientos.** En el inventario solo se teclea una cantidad a mano en un **ajuste** (al editar la cantidad de un producto o insumo); una salida por venta, un alta o una baja son consecuencia de otra acción y se corrigen desde ella. La ficha de un movimiento gana *Editar ajuste* cuando el movimiento es de tipo `AJUSTE` **y** pertenece al turno abierto (el turno pasa a ser la regla de «sin cerrar», como en las ventas; a diferencia de ellas, aquí la corrección es la única salida: no se anula). Se abre un diálogo con la **cantidad** (con signo, en unidades enteras o hasta tres decimales en la unidad del insumo) y un **motivo** obligatorio de 3 a 60 caracteres. Al guardar (`CorregirMovimiento` → `TurnoRepository.corregirMovimiento`, en una transacción): se aplica la **diferencia** al stock de la entidad (no admite dejarlo negativo: `StockInsuficiente` y no se escribe nada), el movimiento queda con la cantidad nueva, su existencia se desplaza lo mismo y `desplazarExistencia` corrige la «existencia después» de los movimientos **posteriores de esa entidad** para que el libro siga cuadrando; el motivo queda en la `nota` («Compra · Corregido: …»), porque sin rastro una corrección de existencias es una reescritura silenciosa del historial. Se apoya en lógica pura (`domain/service/CorreccionMovimiento`: qué se puede corregir, cómo se lee la cantidad y qué queda anotado) y añade `FiltroEntrada.CANTIDAD` (dígitos con signo y un separador) al design system, con `Cantidad.editable()` en `:core` compartido con el formulario de insumos.

Descartado: editar el importe de una venta por separado (sale de cantidad × precio; un descuento sería otra función), corregir la transferencia solo en su fila (descuadra la venta), y «corregir con el contrario» en movimientos (registraría dos filas para arreglar una).

**Pruebas:** `CorreccionMovimientoTest` (6 casos de la lógica pura), `FiltroEntradaTest` (el filtro con signo) y `MovimientoAccionesViewModelTest` (5 casos: solo se ofrece en ajustes del turno abierto de la principal, abrir trae la cantidad actual, guardar pide la corrección con la cantidad y el motivo normalizados, una cantidad ilegible no guarda, y un turno ya cerrado avisa y deja reintentar). En `:data`, dos instrumentados nuevos en `TurnoRepositoryInstrumentedTest` —el ajuste corregido cuadra stock y existencias posteriores; solo se corrigen ajustes del turno abierto, el insumo va en milésimas y nunca queda stock negativo— comprobados **en rojo** rompiendo a propósito el desplazamiento del libro (esperado 57, obtenido 12) y la aplicación al stock (producto 12 vs 57 e insumo 5500 vs 6250), y en verde (8 tests) en el teléfono 4bc9f8f3.

## 2026-10-17 - Móvil: la ficha del cliente fijo se puede corregir (0.31.0)

En Registros → Clientes, la ficha no permitía arreglar un nombre mal escrito, un carné equivocado o un teléfono viejo: la única salida era quitar el cliente y volver a crearlo (perdiendo, además, el vínculo con sus compras). La ficha gana el botón «Editar» y los dos botones (Editar y Quitar de clientes fijos) pasan a un pie fijo centrado (`ShalevBottomSheet` con `footer` y `ShalevButtonRow`, que ya centra y separa); antes colgaban del contenido y quedaban a la izquierda. La ventana de corrección reutiliza el formulario de la venta (`FormCliente`/`CampoCliente`): `VentaScreen` extrae `CampoClienteField` para no duplicar la configuración de los campos y `FormCliente.erroresCliente()` valida sin el nº de transacción, que aquí no se toca. Se edita nombre, carné y teléfono; se normaliza como en la venta (nombre sin espacios de más, teléfono E.164). Un carné que ya es de otro cliente no se admite: la regla vive en el repositorio (`ClienteFijoRepository.actualizar` → `false`, además del índice único de Room) y la VM muestra «Ese carné ya es de otro cliente». Al guardar, sus compras anteriores quedan con los datos corregidos (`UPDATE transaccion SET clienteCi, clienteNombre, clienteTelefono` en la misma transacción) y la ficha sigue abierta con el cliente ya corregido: antes el carné era el vínculo, así que corregirlo le borraba el historial y sus transferencias seguían con los datos viejos. De paso, `FLAG_SECURE` de los diálogos pasa al `SecureDialogWindow()` compartido (`common/SecureWindow.kt`): la ventana del diálogo no hereda la de la Activity, así que la ficha, la corrección y `ModificarVenta` lo aplican por su cuenta. Se eliminó un import muerto en `AjustesPantallasUiTest` que impedía compilar todo el androidTest de `:app`.

**Pruebas:** nueva `ClientesFijosViewModelTest` (5 casos: la corrección guarda, carné inválido muestra el error del campo, carné de otro cliente no se acepta, al cambiar el carné la ficha sigue abierta con el nuevo, cancelar no cambia nada), comprobada en rojo rompiendo el guardado a propósito; el test instrumentado de `RepositoriosRoomTest` corrige un cliente con una compra por transferencia y exige que la transferencia quede con el carné, el nombre y el teléfono nuevos y que la ficha conserve la compra, en verde en el teléfono 4bc9f8f3 (9 tests) y en rojo sin la corrección de las compras. `shalevTests` y `shalevCheck` en verde local (869 tests JVM) y `:app`/`:data` androidTest compilan. Pendiente CI y la prueba de la interfaz en el teléfono.

## 2026-10-16 - Móvil: Inicio abre siempre por arriba (0.31.0 P1)

Inicio aparecía a media lista al abrir la app: sin el título «Alertas de inventario» y con las tarjetas de alerta cortadas por arriba (medido en el teléfono 4bc9f8f3: la fila de alertas 53 px por encima del borde de la lista y el título fuera; dos franjas en vez de las tarjetas). Eran **dos** causas: el `LazyColumn` no tenía estado propio, así que usaba su `rememberLazyListState()` interno —que es `rememberSaveable`— y Android restauraba la posición al reabrir la app; y, además, los datos llegan después del primer dibujo: al insertarse las alertas **por encima** del primer elemento visible, Compose ancla ese elemento para no moverlo y la lista se desplazaba sola (traza temporal: primer fotograma en índice 0 y, 0,7 s después, al llegar las alertas, índice 1 + 139 px). Por decisión del dueño, Inicio empieza siempre por arriba: la lista recibe un `LazyListState` creado con `remember` (no se guarda) y, en la primera carga, vuelve al principio; después la posición es cosa del usuario, así que una alerta nueva mientras lee no le mueve la pantalla.

**Pruebas:** `InicioPeriodoTest` gana dos casos con la lista como referencia: `inicioAbreArribaAunqueSeGuardeLaPosicion` (`StateRestorationTester`: desplaza hasta el Top 3, emula el guardado y la restauración del estado y exige que vuelva a 0; en rojo antes del arreglo, 125) y `alLlegarLasAlertasLaListaSigueArriba` (primer dibujo sin datos y después alertas + resumen: la lista sigue en 0; en rojo antes, 1000). `shalevTests` y `verifyRoborazziDebug` en verde —las doradas de Inicio no cambian, porque en el arnés los datos están desde el primer dibujo— y `:app:installDebug` en el teléfono 4bc9f8f3, donde ahora se ve el título y las tarjetas completas al abrir.

## 2026-10-16 - Móvil: el selector de período vuelve a verse en Inicio, apilado y fijo con el banner (0.31.0 P1)

El selector de período estaba implementado desde la 0.31.0 (P1) pero no se veía ni en el teléfono ni en las capturas: la tarjeta del turno ocupaba toda la fila y el combo quedaba con ancho 0 (medido en el teléfono 4bc9f8f3 antes del arreglo: turno 70→1010 y selector 992→1080 sin texto). Causa: el `Modifier.weight(1f)` del `Row` de `FilaTurnoPeriodo` no llegaba a sus hijos directos — `TurnoContenedor` recibía `modifier` y lo ignoraba (usaba `Modifier.testTag(TURNO)`) y `ShalevComboBox` aplicaba el `modifier` del llamador al `Surface` de dentro en vez del `ExposedDropdownMenuBox` raíz. Los dos ya aplican el `modifier` del llamante en su raíz.

Además, media fila no da para el combo: en una pantalla de 360 dp el reparto equitativo dejaba 156 dp y, entre el relleno (32 dp), el círculo del icono (40 dp), la flecha (24 dp) y sus dos huecos (16 + 16 dp), a la etiqueta y a la opción elegida les quedaban 28 dp, así que se partían en 2–4 líneas (medido con Robolectric). El bloque de arriba pasa a ser una columna a todo el ancho: el turno, debajo el selector de período y, debajo, el banner de licencia (que sale de la lista y también queda fijo). `FilaTurnoPeriodo` pasa a `BloqueFijo`; `TurnoContenedor` pierde el parámetro `ajustar` y `SelectorPeriodo` el suyo `modifier` (ya no se usaban). Las 14 doradas de Inicio (claro y oscuro) se regeneraron; las demás no cambian, así que el arreglo del combo es neutro en Inventario, Registros, Servicios y Vinculación (todos le pasan `fillMaxWidth`).

**Pruebas:** nueva `InicioPeriodoTest` (Robolectric, JVM): el selector va debajo del turno, el banner debajo del selector y los tres siguen en el mismo sitio tras desplazar la lista hasta el Top 3 (`performScrollToNode`). En rojo antes del arreglo (selector 0×0) y en verde después. `shalevTests` y `verifyRoborazziDebug` en verde; `:app:installDebug` en el teléfono 4bc9f8f3 y comprobado con `uiautomator`.

## 2026-10-16 - Móvil: checklist de permisos en la primera apertura y Ayuda a la izquierda

La primera apertura muestra UNA ventana (`root/PermisosIniciales.kt`) con el checklist de permisos (cámara, aviso de turno y fotos, cada uno con su para qué) en vez de lanzar los diálogos del sistema sin explicación y el diálogo aparte de fotos («Guardar tu periodo de prueba», eliminado junto a `PermisoRegistroPrueba`/`TextosPrueba`). «Continuar» pide los permisos del sistema de una vez; «Ahora no» o cerrar no insiste y cada función los vuelve a pedir donde hacen falta. La lógica no cambia: se marca lo pedido en ambos repositorios y se relee el registro de la prueba con el permiso nuevo. En Ayuda, el detalle de cada sección y los pasos de cada tema van alineados a la izquierda (antes centrados por la card).

**Pruebas:** pendiente `./gradlew shalevTests` y CI.

## 2026-10-16 - Móvil: auditoría visual completa (292 capturas Roborazzi + teléfono real)

Compilado `:app:assembleDebug` con JDK 17, instalado en el teléfono 4bc9f8f3 (Android 15) y regeneradas las capturas Roborazzi en claro y oscuro (146 por tema). Correcciones: título de `ShalevVentana` en `titleMedium` (era `titleLarge`; el design system exige 15sp para diálogos); aviso de la fila de Inventario en 2 líneas (la fecha de vencimiento se cortaba: «Crítico · Vence 0…»); caso `07d_perfil_errores` ahora con `mostrarErrores = true` (antes no mostraba los errores); limpieza de `Icon`/`Checkbox`/`ShalevSize` fully-qualified en PagoElectronicoScreen y ServiciosScreen. Descartados como artefactos del arnés (fondo claro de la ventana de prueba en `Content` sin `Scaffold`): textos de Licencia/Migrar en oscuro. El resto observado (pestañas en 2 líneas a 360dp, franja de exportando, pie fijo sobre formularios largos sin desplazar) es el diseño vigente.

**Pruebas:** `shalevTests` en verde local (incluye la corrección Robolectric offline de `app/build.gradle.kts`, pendiente de CI).

Registro **histórico** de lo implementado en cada fase y de las suposiciones vigentes en ese momento. Se conserva para trazabilidad; no es una lista de productos o tareas actuales.

> **Prevalece la documentación actual.** Si algo de este historial contradice a [README.md](../README.md), [SECURITY.md](../SECURITY.md), [FORMATOS.md](../FORMATOS.md), [LICENSE_CLIENT.md](../LICENSE_CLIENT.md) o [UI_UX_IX.md](../UI_UX_IX.md), vale lo que dicen esos documentos. Las fases posteriores sustituyen a las anteriores. Las entradas de la web de escritorio y Flutter/iOS describen código retirado el 2026-10-15; no indican que esas apps sigan en mantenimiento.

## 2026-10-16 - Móvil: recorte de sobre-ingeniería (auditoría ponytail)

49 ficheros, neto −462 líneas, sin cambio de comportamiento: fuera el flujo envelope v1 muerto en producción (`SolicitudEnvelope`/`RequestPayloadV1` + `EnvelopeV1Test`), campos del payload sin lectores, `texto/esMaestra/ID_MAESTRA` y `formatear()` (a `FakeGl`), `coerceIn`/`movilLocal` duplicados, helper `ByteArray.aHex()` en `:core`, `leerAcotado` único en `:data` (4 bucles + `sha256` por `DigestInputStream`), `SyncJson` desde `ShalevJson`, `ms/inst` de `DtoMappers` a las extensiones de `EntityMappers`, flag muerto `soloHttps`; en UI, FAB expandible, `AccionesVinculacion`, contador/medalla, renderizadores, `EjecutorSeed`, sobrecarga de texto secundario, tokens y params especulativos, todo plegado a sus puntos de uso. No se tocó: `IoDispatcher` (idioma Hilt, churn mayor que el ahorro), unificación `Mutex+editar` (cada mapeo difiere), `MemoriaComandos` (su test la usa directo), consts de cabecera/`cifrarFormatoV3`/`chunked` (peor el remedio), `HexFormat` (exige API 34; minSdk 26 sin desugaring).

**Pruebas:** `shalevTests`, `shalevCheck` (tests + lint + assemble + permisos) y `verifyRoborazziDebug` en verde, doradas intactas. Tres fallos intermedios por ediciones de agentes (import `Text`, variable vecina, `Int`/`Long`) y dos propios del recorte (`open` en renderizadores, colector conflado en `SeedViewModelTest`), todos corregidos.

## 2026-10-16 - Móvil: textos y proporción de Ayuda

Revisión con `@ui-ux-pro-max` y captura real en el teléfono: la sección DATOS decía «…y cambio de teléfono» (resto de Migrar) → «Respaldo y licencia»; el tema «Apps de tus empleados» se llama como la entrada de Ajustes («Apps vinculadas», mismo icono); los pasos bajan de `bodyLarge` a `bodyMedium` (la escala del proyecto es 14/13 y el paso competía con el título); el esquema BARRA gana `Ellipsis` para no cortar «Inventario» en pantallas estrechas. Doradas 07a/07a2 regeneradas (`verifyRoborazziDebug` en verde); la 07g no cambia (el tema renombrado queda bajo el pliegue).

## 2026-10-16 - Móvil: se elimina Migrar

Ajustes → Migrar (asistente de 4 pasos + paso de datos por QR Emisor/Receptor, 0.31.0 P6) desaparece por decisión del dueño. Borrados 31 ficheros (`app/migrar/` con `CanalMigracion`, `MigrarRed`/`MigrarModule` Hilt, pantallas y textos; `MigracionUseCases`, `Migracion.kt`, sus tests; `TransferAuthorizationTest`; 18 doradas Roborazzi 09n–09v). En `domain`, fuera `verificarAutorizacionMigracion`/`cederLicencia` de `LicenciaRepository`; en `:licencia`, fuera `TransferAuthorization`, `checkTransferAuthorization` y `transferOut`. Se conserva todo lo de **recuperación**: `recupera`, `aplicarRevocaciones`/`markRevoked`, `lic.migrated_at`/`Outdated`, «Licencia transferida» y `MantenimientoRepository.borrarTodo` (lo usan semilla, caja y vinculación). Para cambiar de teléfono: respaldo `.shalev` + recuperación de la licencia. Actualizados navegación, Ajustes, onboarding (sin paso final), Ayuda, icono, tests, fakes y documentos (README, MANUAL, Contexto, FORMATOS sin §2, LICENSE_CLIENT §7 como retirado, SECURITY, UI_UX_IX, CAPTURAS, Pendiente, AGENTS, VINCULACION H7).

**Pruebas:** `./gradlew shalevTests` en verde (93 tareas) y `./gradlew shalevCheck` en verde (tests + lintDebug + assembleDebug + shalevPermisos; hubo que limpiar `app/build/generated` por la trampa de Hilt con clases viejas). El test instrumentado de cesión se reescribió por la vía de revocación (pendiente de correrlo en el teléfono).

## 2026-10-15 - Móvil: 2.ª rotación de claves GL del día (captura de pantalla)

La pantalla Claves del teléfono GL (serial 4bc9f8f3) muestra un par nuevo: el de la mañana del mismo día ya no existe (otra reinstalación o borrado de datos). Como `screencap` sale en negro por `FLAG_SECURE`, se leyó el árbol UI (`uiautomator`): ECDH `sha256:eb52:2ee8:…:fea1`, firma `sha256:e693:6c20:…:0a6e`; ambas huellas recalculadas desde el Base64 y coincidentes con las mostradas. `LicenseTrust`: ECDH **sustituida**; firma vigente **añadida** en primer lugar, conservando las 3 anteriores (15/10 1.ª, 05/10, 30/09) para lo ya emitido. Actualizados `LicenseTrustTest` (4 firmas en orden, ECDH distinta de las 3 anteriores), `tools/licencia/probar_gl.py`, README, LICENSE_CLIENT, `docs/GL_CONTEXTO_LICENCIAS.md` y `Contexto.md`. Hay que generar una solicitud nueva con este build: las solicitudes cifradas hacia la ECDH de la mañana GL ya no puede abrirlas.

**Pruebas:** huellas verificadas criptográficamente (SHA-256 del SPKI DER) contra lo mostrado en pantalla; sin JDK/Android SDK local, pendiente `./gradlew shalevTests` y CI. Pendiente también re-verificación de extremo a extremo con GL.

## 2026-10-15 - Móvil: rotación de claves GL (solicitud no reconocida)

La solicitud `SPVIR1:` llegaba a GL como «solicitud dañada o no es para GL». Causa: el teléfono GL regeneró su par al reinstalarse tras el 05/10/2026 y Shalev seguía cifrando hacia la ECDH vieja (`sha256:2a3f:9fce:…`). El formato, HKDF, AAD y campos estaban correctos en ambos lados. `LicenseTrust`: ECDH **sustituida** (`sha256:1d7f:515c:…:39b1`); firma vigente **añadida** (`sha256:d9c4:d821:…:998e`) en primer lugar, conservando las del 05/10/2026 y 30/09/2026 para lo ya emitido. Actualizados `LicenseTrustTest`, `tools/licencia/probar_gl.py`, README, LICENSE_CLIENT y `docs/GL_CONTEXTO_LICENCIAS.md`. Hay que generar una solicitud nueva con este build y reinstalar/actualizar Shalev donde se vaya a pedir licencia.

**Pruebas:** `:licencia:test` en verde (incluye la rotación con 3 firmas). Verificado de extremo a extremo en el teléfono 4bc9f8f3 con test instrumentado temporal en GL (borrado tras correr): la ECDH del Keystore coincide con la fijada y una `SPVIR1:` cifrada hacia ella abre con nombre, tipo y secundarias correctos (2/2 PASSED). La prueba manual por UI quedó pendiente solo por la escritura del código largo por adb; el camino criptográfico real está probado. Pendiente CI.

## 2026-10-15 - Móvil: rejilla de espaciado y tokens (optimización UI)

Se eliminan los valores fuera de la rejilla de 8dp (`ShalevSpacing.xs / 2` = 4dp en las listas densas de Inventario, Servicios, Precios, Registros y Clientes fijos: ahora `ShalevSpacing.xs`) y los literales sueltos (`24.dp` → `ShalevSize.icon`, `48.dp` → `ShalevSize.touchTarget`, `1.dp` → `ShalevSize.strokeHairline`, `2.dp` → `ShalevSize.strokeRegular`). La altura máxima de las listas cortas de fijados (`240.dp`, repetida en 3 pantallas) pasa al token nuevo `ShalevSize.listaCortaMax`. Sin cambios de comportamiento; solo la separación entre filas densas pasa de 4 a 8dp. Se mantiene la paleta AA verificada y todos los componentes `Shalev*` existentes.

**Pruebas:** sin JDK/Android SDK local; pendiente `./gradlew shalevTests` y CI.

## 2026-10-15 - Limpieza del repositorio: solo Android

Se retiraron las aplicaciones `desktop/` (web de escritorio) y `flutter_app/` (Flutter/iOS), además de sus workflows, configuración de Codemagic, dependencias Python de desarrollo y documentos de usuario específicos. Se eliminó también `tools/escaner/`, un auxiliar obsoleto distinto del escáner Android, y archivos locales/candidatos sin referencias (`.kotlin/`, `icono.jpg` y los planes web). Se conservaron el escáner Android, las herramientas activas `tools/licencia/` y `tools/verificacion/`, las licencias GL para Android y el plan móvil que comparte fecha con el plan web.

Se actualizaron los documentos vigentes para reflejar el alcance Android-only y el esquema Room v12. `docs/VERIFICACION.md` registra el estado observado del CI. La corrida 38802995010 falló porque dos tests de `ProcesoActualizarTest` no pudieron descargar el SDK instrumentado de Robolectric; la configuración de `app/build.gradle.kts` ahora prepara el SDK con Gradle y pasa a Robolectric un directorio local en modo offline para todos los tests JVM de `:app`. **Pendiente:** verificar esa corrección en una corrida CI; no hubo JDK/Android SDK para probarla localmente.

## 2026-10-15 - Web: ejes de gráficos y tops en acordeones (verificado en navegador)

`core.js` (`diasSerie`): los períodos 7/30/90 devolvían 1 día (los ejes salían con solo «Hoy»); ahora devuelven sus N días.
`inicio.js` + `app.css`: las 7 listas son `<details>` cerrados con resumen («primero · valor») y flecha, como el móvil.
Verificado en Chrome real contra la app (7 barras, 4 etiquetas, dona con huecos, área de 2 series, acordeón con 3 filas).

**Pruebas:** 132 pruebas Python OK (1 omitida); `node --check` del JS.

## 2026-10-15 - Web: las 7 listas del móvil en Inicio + tipos según tokens

`business.py` (`tops_periodo`, usado por `/api/dashboard`): Más vendido, Lento movimiento (con no vendidos en 0),
Rentabilidad (ganancia > 0), Servicios top/menos, Empleado (empleado o dueño) y Cliente (transferencia con nombre),
con las reglas de `Estadisticas` del móvil (empates por creado/reciente, margen y alfabético). `inicio.js` muestra
las 7 tarjetas (vacías ocultas) con medallas y los formatos de `valorTop/subtituloTop`. `test_business.py`: prueba nueva.
`app.css`: secundario 12→13, botones radio 20→12, campos/buscador/combo 48→56, casilla 40→48, h1 28→24 (tokens UI_UX_IX).

**Pruebas:** 132 pruebas Python OK (1 omitida); `node --check` del JS. Pendiente: verlo en navegador real.

## 2026-10-15 - Móvil: recorte de textos descriptivos excesivos

Se acortan 14 textos (Vinculación, Respaldo, Migrar, cierre de turno, bancos, niveles, acceso con clave,
subtítulo de Precios) y se elimina `BUSCAR_DETALLE` (3 frases que no se mostraban). Segunda pasada: bienvenida,
datos, empleados, objetivo (recorrido inicial), explicación y pasos 1–2 de Migrar, QR, Perfil y clientes fijos.
Tercera pasada: aviso de secundaria, bloqueo de actualización, datos de prueba, fondo pedido, error de caja,
saltar pasos, importar, contraseña lista y QR de vinculación.
Se conservan intactos los avisos de borrado, los de seguridad (contraseña, licencia, recuperación), los consentimientos (cámara, SMS,
notificaciones) y las guías de error. El manual no citaba esos textos.

**Pruebas:** `shalevTests` en verde.

## 2026-10-15 - Web: Inicio en 3 zonas (principal + secundaria de datos)

`inicio.js` + `app.css`: tras el menú lateral, Inicio se parte en `.panel-inicio`: la **principal** trae turno,
indicadores, alertas, top 3 de más vendidos (con medallas oro/plata/bronce, como el móvil), bajo costo y listas;
la **secundaria** (`aside`, solo visualización) trae los 3 gráficos, IP/estado de la red (`/api/network`),
conexiones y tiempo en línea (`/api/uptime`), con enlaces a sus secciones. En estrecho se apila.
Gráficos al estilo del móvil: barras con intensidad por altura, dona con pista y 1 % de huecos, área de
venta + costo con leyenda de puntos y hasta 4 etiquetas en el eje. MANUAL_USUARIO.md y desktop/README.md actualizados.

**Pruebas:** 131 pruebas Python OK (1 omitida); `node --check` del JS. Pendiente: verlo en navegador real.

## 2026-10-15 - Móvil: el cuadro de «Actualizar» ya no se queda colgado al cerrarlo a mitad

`GestorActualizacion`: cerrar el cuadro (`cerrarProceso`) cancela el trabajo en curso (`procesoJob`) y los
resultados tardíos solo se escriben si el cuadro sigue abierto (`resultadoProceso`). Antes, cerrar durante la
comprobación o la descarga no detenía nada y, al terminar, el resultado reabría el cuadro solo, pasara lo que
pasara. Test nuevo `ProcesoActualizarTest` (cerrar a mitad no reabre; sin cerrar sale el resultado y cierra).

De paso: `libs.versions.toml` vuelve a `roborazzi = "1.38.0"` (el que fija AGENTS.md; el 1.76.0 trae metadata de
Kotlin 2.3 y con Kotlin 2.0.21 no compilaba ningún test de `:app`) y se crean en `InicioLogic.kt` los subtítulos
de acordeones (`resumenVentas`, `resumenGanancia`, `resumenInventario`, `resumenMetodos`, `resumenTop`,
`resumenTopPersonas`) que `InicioLogicTest.resumenesDeLosAcordeones` ya exigía.

**Pruebas:** `shalevTests` en verde (todos los módulos JVM, incluido `ProcesoActualizarTest`).

## 2026-10-15 - Web: Inicio sin tarjeta «Sin turno abierto», barra compacta

`inicio.js` + `app.css`: se elimina el segundo contenedor («Sin turno abierto»; abrir/cerrar vive en el
interruptor de la barra). La lista de período mide lo que su texto más largo (`width: auto`, nativa) y el
interruptor queda acoplado a la izquierda (`flex: none` + `margin-left: auto` en la lista).

**Pruebas:** `node --check` del JS; suite Python sin cambios en esta parte (131 OK antes del cambio).

## 2026-10-15 - Web: barra de Inicio con interruptor de turno y combobox de período

`inicio.js` + `core.js` + `app.css`: la barra fija de Inicio ahora es un contenedor con dos mitades, como el
móvil (`FilaTurnoPeriodo`): a la izquierda el turno como **interruptor** (tocarlo abre los diálogos de abrir/cerrar;
se revierte si se cancela; bloqueado sin licencia) y a la derecha el período como **combobox** nativo
(`comboPeriodo`, misma lista que los chips que conserva Registros). La barra lleva su propio `sticky` + `z-index`
(opaca, ya no depende de `.pegajoso`, que se desactiva bajo 1000 px). MANUAL_USUARIO.md y desktop/README.md actualizados.

**Pruebas:** 131 pruebas Python OK (1 omitida); `node --check` del JS.

## 2026-10-15 - Web: Licencia solo activa, y la maestra se puede teclear

`desktop/spvi_web/static/js/ajustes.js` (`vistaLicencia`): se elimina el paso 1 «Solicitar licencia»
(formulario y «Generar QR de solicitud») y el botón «Importar lista de revocaciones»; la sección queda
en estado + «Activar licencia». La API (`/api/license/request`, `/api/license/qr`, revocaciones) no cambia.
El campo de activación acepta la clave maestra `GLMK-PERM-MAXS-9999` (antes el formateo solo dejaba dígitos)
y el envío la deja pasar al backend, que la valida solo con `Shalev_DESARROLLO=1`.
MANUAL_USUARIO.md y desktop/README.md actualizados.

**Pruebas:** 131 pruebas Python OK (1 omitida); `node --check` del JS.

## 2026-10-15 — Web 0.31.0: siete mejoras UI de la revisión visual

Capturas de las 10 secciones y correcciones solo en `desktop/spvi_web/static` (sin API ni modelo):

1. La lista deja 80 px bajo el FAB (`app.css`, `.vista:has(.fab)`): «Nuevo servicio» ya no tapa el precio de la última fila.
2. Banner de licencia global: `Shalev.render` (`core.js`) lo antepone en las 10 secciones; se quitan las llamadas por vista (`inicio.js`, `venta.js`, `vinculadas.js`).
3. Registros: buscador y chips de período en filas separadas (`registros.js`).
4. Interruptor «Costos» en Inventario (`inventario.js`, flag `spvi-ver-costos` en `localStorage`): oculta los costos sin desalinear la fila.
5. Pestañas inactivas con más peso (`font-weight: 600`).
6. «Salir de Shalev» pasa a botón de texto (`kind: 'texto'`) en Ajustes; «Reiniciar» conserva el borde.
7. `duracionCorta()` (`core.js`: «6 min», «2 h 5 min», «3 días») junto a los cronómetros de Tiempo en línea.

**Pruebas:** 131 pruebas Python OK (1 omitida) y las 10 capturas Playwright revisadas.

## 2026-10-14 — Compilación: revierte subidas de Dependabot incompatibles con AGP 8.7.3

`gradle/libs.versions.toml`: el proyecto no compilaba (`:app:assembleDebug`) por cuatro subidas
automáticas incompatibles con la pila documentada en `AGENTS.md` (AGP 8.7.3, compileSdk 35,
Kotlin 2.0.21). Se devuelven a sus valores buenos conocidos, sin cambios de comportamiento:
`kotlin` 2.4.21 → 2.0.21 (no existe KSP para Kotlin 2.4; el último es 2.3.12), `hilt` 2.60.1 → 2.52
(el plugin 2.60 exige AGP 9), `navigation` 2.10.2 → 2.8.5 (arrastraba AndroidX que exigen
compileSdk 36/37 y AGP 8.9.1/9.1) y `coroutines` 1.11.0 → 1.9.0 (su stdlib trae metadatos 2.2,
ilegibles para el compilador 2.0).

**Pruebas:** `assembleDebug` OK con JDK 17; APK `arm64` instalado en el teléfono (23021RAAEG,
API 35) y `MainActivity` en primer plano. No se corrió `shalevTests`.

## 2026-10-14 — Escritorio 0.31.0: las cinco mejoras del informe de feedback móvil

Cierra el informe de feedback móvil (`desktop/INFORME_FEEDBACK_MOVIL.md`, retirado junto con la web de escritorio el 2026-10-15): se implementan las cinco mejoras que el informe recomendaba, en su orden sugerido. **No se toca el modelo de datos ni el protocolo de sincronización**, así que las apps Android no se modifican.

1. **1.1 Selector de período en Inicio** (`static/js/core.js`, `inicio.js`, `app.css`): barra fija
   (`.barra-turno` + `.pegajoso`) con el estado del turno y los chips de período en la misma fila
   (Turno / Hoy / 7 / 30 / 90 días / Todo). `Business.dashboard()` acepta `1, 7, 30, 90`, `0` (todo el
   historial) y `'turno'` (desde la apertura del turno; sin turno abierto, desde el inicio del día);
   `/api/dashboard` pasa el valor tal cual. La serie diaria del gráfico se deriva del período
   (`Shalev.diasSerie`: en «Todo» usa los días con datos, máximo 30 barras). Con «Turno» y «Todo» no hay
   comparación con el período anterior. Registros usa los mismos chips (`chipsPeriodo`) y gana «Turno»
   y «90 días».
2. **1.5 Animaciones** (`static/app.css`, `core.js`): `Shalev.fundir()` desvanece la sección anterior
   120 ms antes de pintar la nueva y los diálogos entran desde el centro-abajo
   (`@keyframes ventana-entra`, `translateY(48px) scale(.97)`). Se respeta `prefers-reduced-motion`.
3. **1.2 Fila de inventario** (`static/js/inventario.js`): nombre, categoría y existencias a la izquierda;
   a la derecha **costo y venta diferenciados** (`.valores`). En elaborados se añade «Se pueden hacer N con
   las existencias», calculado de la receta y de las existencias de insumos (lo que en el móvil hace
   `InventarioLogic.kt`).
4. **1.4 Ayuda y Soporte** (`static/js/ayuda.js`, secciones nuevas del menú): Ayuda con nueve secciones
   desplegables con icono (turno y caja, venta, inventario, servicios, registros y exportar, respaldo,
   apps vinculadas, licencia y ajustes) y Soporte con los datos del desarrollador en filas centradas
   Icono → Descripción → Texto, con WhatsApp y copiar teléfono.
5. **1.3 Casillas en Registros** (`static/js/registros.js`, `business.py`, `exports.py`, `app.css`):
   casilla en todas las listas y barra de selección con **«Marcar todo lo filtrado»** — que marca todo el
   filtro activo, no solo la página visible, porque `ledger(..., ids=True)` devuelve los identificadores de
   todo el filtro —, «Quitar selección» y «Exportar selección». Alcance de exportación nuevo **ventas**
   (PDF/Excel) y filtrado por selección también en **turnos**. En caja, movimientos y clientes la casilla
   está pero no ofrece exportar: no hay informe para ese contenido.

**Pruebas:** 129 pruebas Python (`desktop/tests/`), con nuevas regresiones para `dashboard` por período,
`report('ventas')`, la selección por `ids` en turnos y ventas, y `ledger` con los identificadores del
filtro. La interfaz se verificó con una prueba de humo temporal (jsdom, no incluida en el repositorio) que
recorre Inicio, Registros, Inventario, Ayuda y Soporte y comprueba 31 comportamientos. El workflow
`Escritorio Windows` del PR #14 corrió en un runner Windows y pasó: 129 pruebas, `.exe` empaquetado y
autoprueba verde. **Sin interfaz en navegador real, sin icono junto al reloj y sin inicio con Windows**:
eso solo se puede comprobar en la PC del dueño.

## 2026-10-14 — Android 0.31.0: diez cambios de la app móvil (encargo del propietario)

Implementado en `app/` **sin compilar ni probar** (en este entorno no hay JDK/Android SDK/Gradle ni Dart, y
`dl.google.com` y `repo1.maven.org` están bloqueados). Lo verifica el CI y la PC del propietario.

1. **Inicio** (`inicio/InicioScreen.kt`): «Turno cerrado» y el selector de período en una sola fila del
   mismo alto (`Column` + `height(IntrinsicSize.Min)` + `fillMaxHeight()`, el patrón que ya usaban las
   tarjetas de Pago/Inventario); padding superior `md → xs`; la fila turno + período se queda **fija al
   desplazar** (fuera de la `LazyColumn`, dentro de la `Column`). Fuera la `TurnoCard` muerta.
2. **Inventario** (`inventario/`): `Fila` reescrita como `Row` propia — indicador, miniatura/casilla,
   nombre con «cantidad que alcanza + unidad» y categoría debajo, y a la derecha costo y venta
   (`Money.format`, insumos sin venta la omiten). Ayudas puras nuevas en `InventarioLogic.kt`
   (`costoFila`, `ventaFila`, `categoriaFila`).
3. **Registros** (`registros/`): `seleccion: Set<Long>` en el estado, podada con `seleccionVigente` y
   limpiada al cambiar de pestaña; casilla en ventas, transferencias, movimientos, turnos y clientes
   fijos; barra de selección con «seleccionar todo», exportar solo lo marcado y quitar la selección.
4. **Licencia** (`licencia/LicenciaScreen.kt`): fuera el botón de llave «Ya tengo el mensaje de
   licencia» y su etiqueta; el asistente avanza con «Paso siguiente» del `ShalevStepper`.
5. **Migrar** (`migrar/`): pasa a **Migrar**. Nuevo `MigrarQr.kt` (`CodigoMigracion`, `ShalevM1|…`, puro) y
   `CanalMigracion.kt` (socket TCP directo por la red local; el `.shalev` cifrado de siempre viaja dentro).
   **Emisor** muestra el QR y, tras la confirmación del receptor, se borra el teléfono
   (`MantenimientoRepository.borrarTodo()`); **Receptor** lee el QR con la cámara y aplica el respaldo.
   Todo detrás del **acceso con clave** definido en Ajustes (si no está, se le dice que lo defina) y
   del diálogo de biometría del sistema. Nueva sección en el recorrido inicial (`PasoWizard.MIGRAR`).
6. **Actualizar** (`actualizacion/GestorActualizacion.kt`, `ajustes/AjustesScreen.kt`): «Actualizaciones»
   pasa a **Actualizar** y abre un cuadro con `ShalevCircularProgress` (nuevo en `:designsystem`,
   `ShalevSize.progresoCircular`) y el resultado: «Actualizado», «Estas al día» o «Sin conexión».
   `descargarEInstalar` refleja el avance solo cuando el cuadro lo abrió (`conProceso`).
7. **Ayuda** (`ayuda/`): manual por secciones con icono por tema y esquemas dibujados con formas
   (barra inferior, turno, venta, respaldo y migración). **No lleva capturas**: son esquemas vectoriales
   para que no se desactualicen; las capturas reales se regeneran con `recordRoborazziDebug`.
8. **Soporte** (`soporte/SoporteScreen.kt`): filas centradas en el orden Icono → Descripción → Texto.
9. **Primera apertura** (`root/PermisosIniciales.kt`): `RequestMultiplePermissions` única, **sin textos**,
   con los permisos peligrosos del manifiesto (cámara, notificaciones, fotos). Marca
   `ConfiguracionInicial.permisosInicialesPedidos`.
10. **Animaciones** (`designsystem/token/Tokens.kt`, `navigation/MainScaffold.kt`, `Deslizar.kt`): al tocar
    la barra inferior, fundido (`screenEnter/screenExit` sin zoom); al **deslizar**, desplazamiento lateral
    con la dirección del dedo (`cambioSeccion(dir)`, puro); ventanas y diálogos entran desplazados
    media altura (`ventanaEntra` = `it / 2`).

## 2026-10-14 — Una sola pestaña de Shalev

- Los dos procesos `Shalev.exe` visibles son una sola instancia: el cargador del portable (padre) y la
  app (hija); el candado `shalev.lock` ya impedía un segundo proceso con los mismos datos.
- `static/js/core.js`: control de pestaña única con reclamo en `localStorage`, latido cada 3 s y
  caducidad de 9 s. La segunda pestaña muestra «Shalev ya está abierto en otra pestaña» con **Usar aquí**,
  que transfiere el control (la anterior queda bloqueada por evento `storage`). El identificador vive en
  `sessionStorage` para sobrevivir a la recarga. Sin almacenamiento no se controla y se permite.
- Verificado en Chromium: primera pestaña activa, segunda bloqueada, «Usar aquí» activa la segunda y
  bloquea la primera; sin errores de consola.

## 2026-10-14 — Dominio https://shalev.io y pulido de interfaz web

- `desktop/`: la pantalla principal es `https://shalev.io` por HTTPS y puerto 443 (reemplaza a
  `http://127.0.0.1:8765`). El dominio es parametrizable (`--dominio shalev.io`, `--cert`, `--key`);
  el lanzador exige resolución a 127.0.0.1 (archivo `hosts`) y certificado válido de CA privada
  instalada a mano. `--local` mantiene el modo sin HTTPS de desarrollo. Opción B2 elegida por el dueño
  (sin propiedad del dominio público). `setup_https.py` acepta `--dominio`; `tls.validate_certificate`
  y `TRUSTED_HOSTS` usan `shalev.io` por defecto.
- Inicio suma dos gráficos SVG propios (sin librerías ni rejilla): dona de Formas de cobro
  (Efectivo/Transferencias) y área de Ganancia por día; las barras de Ingresos por día crecen con
  retardo escalonado. Transición de entrada entre secciones y micro-interacciones en botones/listas,
  todo con `prefers-reduced-motion`. Se quitó la referencia a Roboto Mono no empaquetada. Mismos tokens,
  sin rediseño (opción A del dueño).
- Verificación: 106 pruebas Python OK (1 omitida), sintaxis JS, recorrido en Chromium real (1440 y
  estrecho, claro y oscuro) con datos de prueba; EXE reconstruido con `build_windows.ps1` (pruebas +
  autoprueba) y `LEEME.md`/`.sha256` regenerados. HTTPS real en Windows sigue pendiente del dueño
  (hosts + CA + certificado).

## 2026-10-13 — Licencias en las tres apps: clave maestra, sub-tokens y envelope v1 (mitigaciones 1, 7 y 8, Opción A)

- Se implementó en Shalev el sistema de licencias documentado por GL (`SISTEMA_LICENCIAS_CLIENTES.md`,
  `docs/INTEGRATION.md`, `CONTEXTO_LICENCIAS.md`, `SECURITY.md`) con las decisiones del propietario:
  todo el alcance, incluido el flujo v1 y los sub-tokens.
- **Android:** `ModoDesarrollo.esDebug = BuildConfig.DEBUG` (clave maestra solo en depuración);
  `LicenseManager.emitirSubToken` con `FirmanteLicenciaAndroid` (Keystore `PURPOSE_SIGN`, alias
  `spvi_licencia_firma_v1`, respaldo por software envuelto con el Keystore) inyectado por Hilt;
  `VerificadorPermisoSecundaria` (fail-closed) y los cuatro campos nuevos de `LicenciaDto` propagados por
  `ClienteSync`/`ServidorSync`/`ConfigSync`; la principal anota el `deviceId` de cada secundaria al
  vincular y en cada saludo (`Vincular.device`, `Hola.device`). Room sube a **v12** con la columna
  `empleado.device` y su migración explícita (`MIGRACION_11_12`), y `EsquemaTest` ya no fija el número
  de versión. El envelope v1 sella con la AAD canónica y sigue abriendo lo ya emitido con la heredada.
- **Escritorio:** `sync/server.py` anota el `device` de la secundaria al vincular y en `hola`;
  `licensing/subtokens.py` y `licensing/trust.py` ya cubrían el sub-token y `Shalev_DESARROLLO=1`.
  Pruebas nuevas en `test_sync_server.py`. **126 pruebas Python correctas** (`pytest tests -q`).
- **iOS (Flutter):** `flutter_app/lib/src/licensing/` reescrito sobre `pointycastle` 3.9.1 (P-256, ECDH,
  HKDF-SHA256, AES-256-GCM, ECDSA DER/crudo, AAD con longitud prefijada): solicitud `SPVIR1`, licencia
  corta `SPVI2:`, licencia larga v1, maestra en `kDebugMode`, marca de agua del reloj, revocadas,
  sub-tokens, limitador de intentos y renovación. `ClienteLicencia` es ahora el estado real de la
  pantalla de Licencia (se retiró el campo de texto de «clave» inventada). Tests con un GL de prueba en
  `test/` (los corre Codemagic; en esta máquina no hay Dart).
- Verificación: Android compila y pasa `shalevTests` en el CI del repositorio (lint, R8, permisos del manifiesto y API
  mínima 26, en verde). La app iOS **no se compiló aquí** (no hay Dart en el entorno): queda pendiente `flutter test`
  (Codemagic) y commitear `data/schemas/…/12.json`, que Room exporta al compilar.
- Documentación: [LICENSE_CLIENT.md](../LICENSE_CLIENT.md) §11–§13, [SECURITY.md](../SECURITY.md) §4 quater
  y [Pendiente.md](../Pendiente.md).

## 2026-10-11 — Revisión de cambios de OpenCode en móvil y escritorio

- Se conserva la implementación web independiente de `desktop/`; esta revisión no la sustituye ni la mezcla con la app Android.
- En Android se revirtió el traslado accidental de turno, período y acciones de Inicio a la cabecera/FAB.
  También se restauraron en Registros la exportación PDF/Excel de Ventas, Transferencias y Movimientos,
  el texto de ejemplo del buscador y la identificación del vendedor en la lista y en las exportaciones.
- Se devolvieron las pruebas a los escenarios que ya cubrían esos contratos, en vez de cambiar sus datos
  para que validaran las restricciones introducidas por OpenCode.
- Verificación de esta revisión: `PYTHONPATH=desktop python -m unittest discover -s desktop/tests -v` → 103 OK;
  autoprueba del paquete web → OK; Gradle/Android queda sin ejecutar porque este entorno no tiene Java/JDK.

## 2026-10-10 — Escritorio 0.31.0 y licencia de PC de 16 dígitos (sin release)

- `desktop/`: interfaz reescrita (`templates/index.html`, `static/app.css`, `static/js/*.js`; se retiran
  `app.js`, `management.js` y `management.html`) con tokens de color de `ColorTokens.kt`, iconos generados
  desde `ShalevIcons.kt` (`tools/generar_iconos.py`), Roboto/Orbitron y diálogos con el logo. CSP sin estilos
  en línea: los estilos dinámicos se asignan por CSSOM.
- Lanzador con icono de notificación (pystray), instancia única que reabre la pantalla principal,
  reinicio, inicio con Windows (`HKCU\…\Run`) con retraso, clave opcional y red local reanudada.
  `build_windows.ps1` genera el `.exe` con `--noconsole` e icono; la autoprueba devuelve código de salida.
- Licencia de PC: `licensing/pc_code.py` (código de 16 dígitos con `K` de ECDH+HKDF) y solicitud con
  `plataforma`/`transaccion` y QR de WhatsApp. Para GL se escribieron `ShalevPc16`, la rama PC de
  `EmitirShalev2UseCase`, la validación de los campos nuevos, la fila Plataforma/Transacción del Generador y la
  revalidación al importar `.glreg` (sin cambio del contrato v1 ni de Room). **No están en el repositorio GL:**
  la copia de trabajo de GL se perdió al reiniciarse la sesión, antes de publicarla; hay que rehacerlos.
- Datos de prueba `seed.py` (548 días, parecidos al móvil, no idénticos), Registros paginados, Tiempo en
  línea y rutas nuevas (`/api/app*`, `/api/uptime`, `/api/registros/<tipo>`, `/api/license/qr`, `/api/seed`).
- Móvil: pestañas de Registros en varias filas, títulos y selectores sin «…», textos sin términos técnicos.
- Verificación: 103 pruebas Python y autoprueba; recorrido en Chromium sin pantalla a 1440×900, 800×1000 y
  390×844, claro y oscuro, y un flujo de venta/licencia. GL: `ShalevPc16Test` más las pruebas Shalev existentes
  (38) compiladas con el compilador Kotlin del kernel Jupyter y stubs mínimos de JUnit/Truth/coroutines;
  ida y vuelta real PC (Python) → GL (Kotlin) → PC (antes de perderse la copia de GL). **No** se compiló Android ni GL con Gradle (sin JDK/SDK),
  no se regeneraron capturas Roborazzi ni se probó en Windows.

## 2026-10-09 — Segunda entrega de escritorio (sin release)

- PDF/Excel/PNG y promociones textuales con encabezado; ZIP cuando hay varias imágenes. Política en servidor,
  filtro de catálogo, validación de selección y prevención de fórmulas de Excel.
- Respaldo cifrado `.shalevdesk` y restauración atómica con validación previa y copia cifrada de seguridad.
  No se confunde con el formato Android `.shalev`; su interoperabilidad sigue pendiente.
- Dashboard con comparación del período anterior, evolución diaria y precios bajo costo.
- Workflow Windows: pruebas → PyInstaller → autoprueba `.exe` → SHA-256 → artefacto. Aún no ejecutado.
- Generador de CA/certificado local y verificación del dominio, vigencia y clave en el lanzador; sin cambiar
  automáticamente confianza, DNS ni firewall.
- Biblioteca de transporte según Android v1: formato QR, tramas, ECDH/HKDF/HMAC/AES-GCM y protección
  contra repetición. **No hay servidor de negocio ni vinculación utilizable**, tampoco integración GL.
- Notificación Android con número de turnos abiertos y cronómetro del turno propio. Ajustadas pruebas UI
  a las nuevas restricciones de exportación; no ejecutadas sin SDK/JDK.
- 45 tests Python, autoprueba desde Python, sintaxis JS y diff correctos. Empaquetado Linux bloqueado por
  ausencia de libpython3.11.so.1.0. Gradle bloqueado por ausencia de Java. No se generó `.exe` Windows.

## 2026-10-09 — Ajustes de interfaz y primera base de escritorio (sin release)

- Casillas exclusivas por tipo de pago, icono de tarjeta común, acciones de edición/borrado horizontales.
- Inicio reorganizado con cabecera fija (marca, turno, período), resto desplazable y acciones flotantes.
- Retirados placeholders de búsqueda y bloque explicativo redundante de pago. Se mantienen advertencias
  útiles de costos, borrado y datos sensibles; no se hizo una auditoría exhaustiva de todos los textos.
- Transferencias sin vendedor en lista/tabla; exportaciones limitadas y promociones con comentario,
  también para servicios. Iconos en todos los formatos.
- Notificación expandida sin información financiera/personal.
- `desktop/`: Flask/Cheroot/SQLite, acceso local autenticado, turnos, catálogo, ventas, movimientos,
  arqueo y dashboard; lanzador de navegador, script PyInstaller y guía del dominio HTTPS.
- **No implementado en escritorio:** protocolo TCP/QR Android, GL, respaldo `.shalev`, exportaciones,
  funciones contables avanzadas. No se generó un ejecutable Windows ni se cambió DNS/certificados.
- Verificación: 17 pruebas Python pasan, `node --check` y `git diff --check` correctos. `shalevTests`
  no pudo arrancar: faltan Java/JAVA_HOME. Android y Windows requieren compilación y prueba visual real.

## Afirmaciones superadas

| Dónde (abajo) | Decía | Estado actual (0.12.0) |
|---|---|---|
| Suposiciones domain/data, 13 | Exportar como imagen/tarjetas es trabajo de la UI (fase 5) | Implementado: Tarjetas (Prompt 8) e Imagen = lista de precios PNG (Prompt 14) |
| Prompt 5, suposición 1 | Todavía no existe ninguna llamada de red | Desde el Prompt 8 el escáner consulta 4 bases públicas, solo con consentimiento |
| Prompt 5, suposición 3 | La cámara real llega en la fase 3 | CameraX + ML Kit desde el Prompt 8 |
| Prompt 6, suposición 5 | «Nueva venta» con el turno cerrado lo abre | Sustituido en el Prompt 7 (ya indicado allí) |
| Prompt 8, suposición 3 | «Tiempo máximo por base = Ajustes (5 s)» y User-Agent `Shalev/0.7` | El tiempo es la preferencia `timeoutRedSegundos` (5 s por defecto); **no hay control en la pantalla Ajustes** para cambiarlo. El User-Agent actual es `Shalev/0.12 (Android; escaner de codigos)` |
| Prompt 8, Exportar | Imagen = tarjetas PNG de 6 productos | Desde el Prompt 14, Imagen = lista de precios y Tarjetas = tarjetas por producto |
| Prompt 11, suposición 6 | PDF/Excel no están en Registros | Añadido en el Prompt 14 (botón Exportar) |
| Prompt 12, suposición 3 | Importar desde WhatsApp: primero guardar el archivo | Desde el Prompt 14 Shalev recibe el `.shalev` con Compartir / Abrir con (ya indicado allí) |

## Suposiciones de la base (Prompts 1 y 2)

1. **Features como paquetes, no como módulos Gradle.** Con un solo desarrollador, 10 módulos de feature solo añaden tiempo de build. Separarlos después es mecánico.
2. **Hilt en vez de Koin:** errores de grafo en compilación e integración oficial con ViewModel y Navigation.
3. **Transiciones con el `NavHost` estándar:** `AnimatedNavHost` (Accompanist) está obsoleto y sus transiciones ya están integradas en Navigation 2.8.
4. **Sin dynamic color:** se prioriza la marca y el AA verificado.
5. **DataStore cifrado con clave propia en Keystore,** en lugar de EncryptedSharedPreferences (obsoleto) o Tink (dependencia extra innecesaria).
6. **Room + SQLCipher** con passphrase de `:security` (incorporados en el Prompt 3).
7. **Bloqueo solo por licencia** (C9). No hay PIN de app en esta fase.
8. **Los casos de uso de `:domain` no generan fábricas Dagger en su módulo:** Dagger las genera en `:app`, así `:domain` no depende de Dagger.

## Suposiciones de las capas domain y data

Criterio: la opción más segura, simple y accesible, documentada.

**Negocio**
1. **Timeout = red.** Es el tiempo de espera del escáner online (3–30 s, 5 por defecto). No es un bloqueo por inactividad.
2. **El turno activo vive en Room,** no en preferencias: así se verifica en la misma transacción que la venta.
3. **Preajustes de precio:** los aplicables a un producto **se suman**. Filtran por método de pago y por importe mínimo (el total antes de ajustes). El precio resultante nunca baja de 0.
4. **Ajuste permanente de precios:** el precio mínimo resultante es 0.01 CUP.
5. **Insumos con `UnidadMedida`** (u, kg, g, L, ml) y cantidades en milésimas, porque las recetas necesitan fracciones. El precio es el costo por unidad de medida.
6. **Elaborado:** al guardar se anulan foto, caducidad y código. Su costo se calcula desde la receta y sí tiene precio de venta (C4).
7. **CI del cliente:** 11 dígitos.
8. **Licencia en dominio:** `EstadoLicencia`, `TipoLicencia` y `ViaSolicitud` son alias de los tipos de `:licencia`, para no duplicar el contrato.

**Alertas y estadísticas**
9. **"Próximo a caducar"** incluye también los productos ya vencidos.
10. **Top 3:**
    - Rentabilidad: solo cuenta los productos con ganancia > 0.
    - "Lento": incluye los activos con 0 ventas.
    - Empates: se desempata por `creadoEn`.
11. **Agrupación de gráficos:** por hora hasta 48 h, por día hasta 62 días y por mes a partir de ahí.

**Escáner y exportación**
12. **Extracción de datos del SMS de transferencia:** regex heurística. Hay que ajustarla con muestras reales.
13. **Exportar como imagen/tarjetas** es trabajo de la UI (fase 5). La configuración se exporta **solo en PDF**.

**Persistencia**
14. **Respaldo de configuración:** los preajustes que apunten a productos inexistentes en el dispositivo destino pierden esos productos.
15. **Registros:** la búsqueda de texto es literal (se ignoran los comodines `%` y `_`). *Desde el Prompt 11 la pantalla filtra el texto en el dominio, sin tildes ni mayúsculas (ver «Registros (Prompt 11)»).*
16. **Preferencias en un solo JSON** para que cada escritura sea atómica. Restaurar un respaldo no reinicia el onboarding.

## Suposiciones del cliente de licencias (Prompt 4)

1. **"Flujo definido en Prompt 0"** = D3 + D3.1: solo la clave ECDH es configurable; la de firma queda fijada.
2. **Perpetua** no ofrece renovación; cualquier otra licencia instalada (activa, vencida o revocada) ofrece "Renovar o cambiar".
3. **Sincronización Perfil ↔ Licencia:** el panel lee el Perfil en vivo y no pisa lo que el usuario está editando; el Perfil se actualiza al generar la solicitud (antes de enviarla).
4. **Bloqueo en uso:** la re-evaluación usa el reloj del sistema; un retroceso del reloj bloquea (`ClockTampered`) hasta corregir la fecha.
5. **Revocada** se muestra como "Licencia no válida" (sin detallar el motivo).
6. **Versión 0.3.0** (`versionCode 3`).

## Primera ejecución, onboarding y permisos (Prompt 5)

- **Asistente** (`onboarding/`): Bienvenida → Tus datos → Clave del emisor (solo si no viene fijada en el APK) → Avisos de inventario → Periodo de prueba. Cada paso se puede **omitir** y todo el asistente se puede **saltar**; nada bloquea el uso de la app (solo la licencia bloquea, C9).
- **Retomar desde Ajustes**: la entrada «Completar configuración» muestra cuántos pasos faltan y abre solo los pendientes; «Avisos de inventario» abre directamente ese paso.
- **Permisos contextuales**: la cámara se pide **solo** al abrir el escáner y siempre tras una explicación; si se niega, se puede escribir el código a mano o abrir los ajustes del sistema. Internet se usa solo para consultar un código que no está en el catálogo, y solo con el consentimiento del usuario.

### Suposiciones del Prompt 5

1. **INTERNET es un permiso «normal»** de Android (no se pide en tiempo de ejecución). Para cumplir «solo para consultas de códigos» se pide un **consentimiento dentro de la app** (`ConsentimientoRed`: Preguntar / Permitido / Denegado), que se puede cambiar en Ajustes. Todavía no existe ninguna llamada de red: el punto de enganche es `EscanerViewModel.consultarEnLinea` (fase 3).
2. **«Ahora no»** en el diálogo de consentimiento no guarda la decisión: se volverá a preguntar en la próxima consulta. «Sí, buscar» y «No, nunca» sí se guardan.
3. **La cámara real (CameraX + ML Kit) llega en la fase 3**; en esta versión el escáner ya gestiona el permiso y permite escribir el código.
4. **El progreso del asistente** (`setup.v1`: pasos confirmados y si ya se pidió la cámara, para distinguir «denegado para siempre») se guarda cifrado en `SecureDataStore`, igual que las preferencias, y **no entra en los respaldos** (es estado del dispositivo).
5. **Omitir nunca bloquea.** Si se omiten los avisos, se aplican los valores recomendados **5 (bajo) / 1 (crítico)** tanto para productos como para insumos, pero el paso sigue figurando como pendiente en Ajustes.
6. **Datos mínimos**: todos los campos son opcionales; un campo vacío no es un error. El teléfono se normaliza y se añade sin borrar los que ya había.
7. **Pasos «hechos»**: Datos = perfil no vacío; Clave = hay huella del emisor; Avisos y Prueba = confirmados por el usuario. El paso de prueba solo aparece mientras no hay licencia instalada.
8. **Versión 0.4.0** (`versionCode 4`).

## Pantalla Inicio (Prompt 6)

Orden de arriba abajo (`inicio/`): banner de licencia → turno + **Nueva venta** → alertas → accesos a **Pago electrónico** y **Precios** → selector de período → gráficos → Top 3.

| Bloque | Implementación | Regla |
|---|---|---|
| Banner | `bannerInicio` → `bannerText` de `:licencia` | Prueba en días, Mensual en días, Semestral/Anual en meses; **Perpetua: oculto**. |
| Alertas | `ObservarAlertas` + `alertasVisibles` + `ShalevAlertCounter` | Stock bajo (amarillo, 5), Stock crítico (rojo, 1), Insumo bajo (verde claro, 5), Insumo crítico (naranja, 1), Próximo a caducar (púrpura claro). **Un contador en 0 no se muestra.** Al pulsar: Inventario (o Elaboración para insumos) filtrado, con chip «Quitar filtro». |
| Pago electrónico | `pagos/` (hoja en Inicio + pantalla en Ajustes) | Elegir o escribir teléfono y tarjeta/cuenta; los números nuevos se añaden a las listas del Perfil (sin duplicar) y quedan elegidos. Lista editable (alta, edición, borrado, elegir) en Ajustes → «Pago electrónico». |
| Precios | `precios/` | Preajustes: subir/bajar un % a uno o varios productos, opcionalmente solo con un método de pago y/o desde un importe mínimo (envergadura). Pausar, editar y borrar. Vista previa del precio resultante. |
| Turno | Interruptor accesible (`toggleable`, `Role.Switch`) | Abrir es inmediato; **cerrar pide confirmación** y muestra el resumen (ventas y total). |
| Nueva venta | Diálogo **Venta / Elaborado** | Si el turno está cerrado, el diálogo lo avisa y se abre al elegir. |
| Período | Turno · Hoy · 7 días · Este mes · Este año (se conserva al girar) | Afecta a **Ventas** y **Ganancia neta**. |
| Gráficos | `designsystem/component/Charts.kt` (Canvas) | Tarjetas con título centrado, **sin líneas de cuadrícula**, animación corta, descripción accesible. Ventas: barras; Inventario: dona por categorías; Métodos de pago: dona Efectivo/Transferencia; Ganancia neta: área venta vs costo. |
| Top 3 | `ObtenerResumenGeneral` | Más vendido, Lento movimiento, Rentabilidad con los desempates del Prompt Maestro. **Cada lista se oculta si no tiene datos.** |

Estados de carga (`EstadoCarga`): Idle → Cargando → Éxito / Vacío / Error (mensaje genérico + «Reintentar»). Al volver a Inicio se recarga (`ON_RESUME`); al refrescar se conservan los datos anteriores en pantalla en vez de mostrar otra vez el indicador.

### Suposiciones del Prompt 6

1. **Gráficos propios en Canvas en lugar de Vico.** Vico no tiene gráfico de dona y habría que mezclar dos librerías; con Canvas no se añade ninguna dependencia, se controla la ausencia de cuadrícula y la accesibilidad (una descripción textual por gráfico). Se quitó `vico` del catálogo. Paleta de gráficos propia, verificada en `ContrastTest` (≥ 3:1 sobre la superficie, claro y oscuro).
2. **Inventario = stock actual por categorías; Métodos de pago y Top 3 = últimos 30 días.** Son independientes del selector (el Prompt Maestro dice que el selector afecta a Ventas y Ganancia neta).
3. **Período «Turno»** = turno abierto o, si no hay, el último cerrado; sin ningún turno se muestran las ventas de hoy y se avisa.
4. **Más de 6 categorías** en una dona: las 5 mayores + «Otras».
5. ~~«Nueva venta» con el turno cerrado lo abre~~ → **sustituido en el Prompt 7**: ya no se abre implícitamente; la pantalla de venta queda bloqueada con mensaje claro y botón «Abrir turno».
6. **Alertas de insumos → Elaboración**, que es donde viven los insumos; las de productos y caducidad → Inventario.
7. **Pago electrónico**: al dar de alta un número ya existente se avisa «ya está en la lista» en vez de duplicarlo; un campo vacío conserva la selección actual.
8. **Nombre del preajuste opcional**: si no se escribe, se genera (p. ej. «+10 % con Transferencia desde 5,000.00 CUP»). Los preajustes **se aplican al cotizar la venta** (`PlanificadorVenta`), no modifican el precio guardado del producto. Límites: subir hasta 100 %, bajar hasta 90 %.
9. **Venta de «Elaborado»** vende unidades ya producidas en Elaboración (el flujo completo de venta llega con la pantalla Venta).
10. **Portapapeles**: solo se lee al pulsar «Pegar» (nunca en segundo plano ni al abrir una pantalla).
11. **Versión 0.5.0** (`versionCode 5`).

## Turno de venta (Prompt 7)

- **Iniciar / Cerrar turno** desde la tarjeta de Inicio (cerrar pide confirmación). Al cerrar aparece «Turno cerrado: N ventas, total» con la acción **Ver**, que abre el registro del turno.
- **No se vende fuera de turno**, en tres niveles: la UI (`ObservarPermisoVenta`: la pantalla Venta muestra el bloqueo «No hay un turno abierto» con el botón «Abrir turno», y se bloquea al instante si el turno se cierra mientras está abierta), el caso de uso `RegistrarVenta` (`TurnoCerrado`) y la transacción de `:data`.
- **Registro del turno al cerrar**, en una sola transacción: apertura y cierre (hora y usuario), número de ventas, unidades, total, costo, ganancia, **métodos de pago** (número de ventas y total en efectivo y en transferencia), movimientos de inventario (desglose productos / insumos). Las ventas y los movimientos quedan vinculados por `turnoId`, así que el detalle muestra productos vendidos, variación neta de insumos, cada movimiento y cada venta.
- **Persistencia cifrada**: todo va en la base Room + SQLCipher existente (no hay ficheros aparte). Base de datos **v2**: `M_1_2` añade 6 columnas **nulables** a `turno` (`abiertoPor`, `cerradoPor`, `ventasEfectivo`, `ventasTransferencia`, `movimientosProducto`, `movimientosInsumo`). Los turnos antiguos se conservan; su desglose aparece como desconocido y sus movimientos cuentan como «de productos». Los respaldos antiguos siguen importándose (campos nuevos con valor por defecto).
- **Registros → Turnos**: lista con el turno abierto primero y el resto del más reciente al más antiguo. El detalle de un turno abierto es **provisional** (se recalcula al volver a la pantalla); el de uno cerrado es el resumen congelado.
- **Inicio** usa el turno abierto y, si no hay, el **último turno cerrado** (período «Turno» de los gráficos).

**Suposiciones del Prompt 7**

1. **Usuario** = nombre y apellidos del perfil de Ajustes; si está vacío, «Titular del dispositivo» (la app es monousuario, sin cuentas).
2. **No se abre el turno implícitamente**: abrirlo es siempre una acción explícita del usuario (Inicio o botón de la pantalla bloqueada).
3. **Un turno a la vez**; cerrar un turno sin ventas está permitido (queda registrado con cero).
4. **Migración**: `MigrationTestHelper` requiere el JSON de esquema que genera KSP en tu equipo (en el sandbox no puede ejecutarse), así que la migración se validó con una prueba JVM (columnas de `SQL_1_2` = campos nuevos de `TurnoEntity`) y con una simulación en SQLite (tabla v1 con un turno cerrado → `SQL_1_2` → `PRAGMA table_info` igual a la entidad, fila conservada). La prueba instrumentada `TurnoRepositoryInstrumentedTest` cubre el cierre sobre Room real.
5. **Versión 0.6.0** (`versionCode 6`).

## Inventario y escáner (Prompt 8)

- **Tabla cronológica** (más reciente primero) con indicador de color por fila (normal / stock bajo o próximo a caducar / crítico o vencido), descripción del estado para TalkBack, **checkbox** por fila (seleccionar ≠ abrir), **buscador** (nombre, categoría o código, sin tildes ni mayúsculas), **filtro** (estado, tipo, categoría; distintivo con el número de filtros), chip «Quitar filtro» y estados Cargando / Vacío / «Sin resultados» / Error con Reintentar.
- **Selección**: barra «N seleccionados» con Marcar todo (lo visible), Exportar, Eliminar y Quitar selección. Sobrevive a búsquedas y filtros; se poda si un producto desaparece.
- **Agregar**: FAB expandible (Escanear / Escribir) y, con el inventario vacío, una hoja con las dos opciones explicadas.
- **Exportar**: la selección o, sin selección, lo que se ve. PDF y Excel → Compartir o **Guardar en el dispositivo** (selector del sistema, SAF); Imagen → tarjetas PNG de 6 productos (solo foto, nombre, categoría y precio de venta: sin costo ni existencias). Compartir usa el selector del sistema (WhatsApp, Telegram, Bluetooth…) vía `FileProvider` limitado a `cache/compartir/` (se limpia a las 24 h). **Sin permisos de almacenamiento.**
- **Ficha** al tocar una fila: foto, aviso de estado, todos los campos, ganancia por unidad y receta. Acciones **Editar**, **Compartir** (PDF = ficha completa de uso interno; Imagen y Texto = para clientes) y **Eliminar** (con confirmación).
- **Formulario de producto** (crear/editar) con los atributos del Prompt Maestro en su orden: categoría (desplegable editable), foto opcional (selector de fotos del sistema, se copia y re-codifica sin EXIF/GPS), nombre, descripción, **receta** (solo Elaborado; costo calculado en vivo), fecha de caducidad, precio de costo, precio de venta, cantidad, niveles bajo/crítico y código (con botón para escanearlo). **Elaborado oculta foto, caducidad y código.** Validación en tiempo real (tras tocar un campo o intentar guardar), mensajes sin jerga, código duplicado detectado al guardar, fecha **recordada por código** propuesta automáticamente. Guardar en la barra superior y al final del formulario.
- **Escáner** (CameraX + ML Kit con modelo **empaquetado**: funciona sin conexión y no descarga módulos): vista de cámara, Escanear, nombre, imagen, fecha, Guardar, Escanear de nuevo. La cámara **solo existe mientras se escanea**: se detiene al detectar el primer código, al pulsar «Detener», al salir o con la app en segundo plano. Orden de búsqueda: inventario local (Room) → Open Food Facts → Open Beauty Facts → Open Products Facts → UPCitemdb (endpoints exactos del Prompt Maestro; se extrae `product.product_name`/`product.image_url` y `items[0].title`/`items[0].images[0]`). Producto nuevo → «Guardar y completar» abre el formulario prellenado (código, nombre, foto, fecha); producto existente → guarda su fecha de caducidad. Entrada manual siempre disponible.
- **Errores de red** explicados y con Reintentar: sin conexión (corta la cadena), bases que no responden, límite diario de UPCitemdb (no se confunde con «no encontrado»), no encontrado (se escribe el nombre a mano).

**Suposiciones del Prompt 8**

1. **Paquete**: integrado en `cu.shalev.app.escaner` / `cu.shalev.app.producto` / `cu.shalev.app.inventario` (decisión O1). `com.ejemplo.escannerproductos` era un ejemplo; integrarlo evita duplicar tema, Hilt, Room y navegación.
2. **CAMERA solo en uso**: se pide al abrir el escáner con explicación previa (Prompt 5) y la cámara se enlaza solo en la fase «Escaneando». En equipos sin cámara queda la entrada manual.
3. **INTERNET solo para consultas**: solo se consultan **GTIN válidos** (EAN-8/UPC-A/EAN-13/GTIN-14 con dígito de control correcto): un QR o un código interno nunca sale del teléfono. Requiere consentimiento (Preguntar / Sí / Nunca, cambiable en Ajustes). Tiempo máximo por base = Ajustes (5 s). HTTPS obligatorio, sin caché, sin cookies, sin reintentos silenciosos; User-Agent `Shalev/0.7` sin datos del usuario. La foto encontrada se descarga una vez (máx. 5 MB), se re-codifica y Coil solo muestra archivos locales (Coil sin módulo de red).
4. **Telemetría de ML Kit eliminada**: ML Kit fusiona `datatransport` (Google), que añade `ACCESS_NETWORK_STATE` y servicios de envío de métricas. Se eliminan con `tools:node="remove"` en el manifiesto (v2: sin telemetría). Verifica en tu equipo con *Merged Manifest* que solo quedan CAMERA e INTERNET.
5. **Fecha de caducidad por código**: al guardar un producto con código y fecha (o desde el escáner) la fecha queda recordada para ese código y se propone la próxima vez; siempre se puede cambiar.
6. **Tarjetas de imagen** = para clientes: nunca incluyen costo ni existencias. El PDF de la ficha sí (uso interno).
7. **Versión 0.7.0** (`versionCode 7`). Base de datos sin cambios (v2).

## Elaboración: insumos y producción (Prompt 10)

- **Pestaña Insumos**: lista **cronológica** (más reciente primero) con indicador de color (normal / bajo / crítico) y su descripción para TalkBack, **checkbox** por fila (seleccionar ≠ abrir), **buscador** (sin tildes ni mayúsculas), **filtro** (existencias: bajo / crítico; uso: en recetas / sin uso; distintivo con el número de filtros), estados Cargando / Vacío / «Sin resultados» / Error con Reintentar. Cada fila muestra cantidad con su unidad, en cuántas recetas se usa y el precio de costo por unidad.
- **Selección**: barra «N seleccionados» con Marcar todo (lo visible), Exportar, Eliminar y Quitar selección; sobrevive a búsquedas y filtros y se poda si un insumo desaparece.
- **Agregar nuevo insumo**: botón flotante `ShalevFab` (nuevo en el sistema de diseño: mismos colores y elevación que el FAB de Inventario).
- **Exportar**: la selección o, sin selección, lo que se ve. PDF y Excel → Compartir o Guardar en el dispositivo (SAF); **Texto** → selector del sistema. Sin permisos de almacenamiento.
- **Ficha** al tocar: nivel, cantidad, precio, valor de las existencias, niveles y **«Usado en»** (Elaborados y cuánto gasta cada unidad). Acciones **Editar**, **Compartir (Texto)** y **Eliminar** (con confirmación).
- **Formulario de insumo** con los atributos del Prompt Maestro: **Nombre, Precio (costo), Cantidad, Nivel bajo (opcional), Nivel crítico (opcional)**, más la unidad de medida (u, kg, g, L, ml). Validación en tiempo real y mensajes sin jerga; hasta 3 decimales en cantidades.
- **Pestaña Elaborados** (integración con la **Receta**): cada producto de categoría «Elaborado», su costo por unidad calculado desde la receta y **cuántas unidades alcanzan** con los insumos actuales. «Producir» abre un diálogo con las unidades y la lista «Se descontará: Harina: 2 de 10 kg…»; si no alcanza lo dice y ofrece el máximo. Tocar el Elaborado (o uno sin receta) abre su formulario para editar la receta; «Nuevo Elaborado» abre el formulario con la categoría ya elegida.

**Modelo producir ≠ vender.** Los insumos se descuentan **al producir**, no al vender: `ProducirElaborado` (caso de uso existente) descuenta cada insumo según la receta, suma las unidades al stock del Elaborado y actualiza su costo, todo en **una transacción** con un movimiento **CONSUMO** por insumo y uno **PRODUCCION** para el Elaborado. La venta descuenta después el stock del Elaborado como cualquier producto. Así el inventario de insumos refleja lo que realmente queda en el almacén, y una venta nunca falla por insumos.

**Movimientos de inventario de insumos**: alta (ALTA), edición de cantidad (AJUSTE «Edición»), consumo al producir (CONSUMO) y baja (BAJA). Cambiar el precio de un insumo **recalcula el costo** de los Elaborados que lo usan.

**Suposiciones del Prompt 10**

1. **Reutilización**: repositorios y casos de uso existentes (`ObservarInsumos`, `GuardarInsumo`, `EliminarInsumo`, `ProducirElaborado`, `ExportarInsumos`, `Validadores.insumo`). Se añadieron solo composiciones de dominio (`ObservarElaboracion`, `ObservarProduccion`, `ObtenerFichaInsumo`, `EliminarInsumos`) y **ningún SQL ni cambio de esquema** (BD v2).
2. **Unidad de medida**: el Prompt Maestro dice «solo» 5 atributos, pero sin unidad «Cantidad 2» es ambigua y la receta no puede calcular. Se muestra como selector con «u» (unidades) por defecto; no es un campo más que rellenar.
3. **Un insumo usado en una receta no se puede eliminar** (lo impide `:data` con `EnUso`): la app dice qué recetas lo usan. En un borrado múltiple se eliminan los demás y se informa.
4. **Pestañas con chips** (`ShalevChip`): el sistema de diseño no tiene pestañas y los chips ya cumplen tamaño táctil y contraste AA.
5. **Alertas de Inicio**: «Insumo bajo / crítico» abre Elaboración con el filtro puesto (sustituye a la lista provisional `AlertaListaScreen`, eliminada).
6. **Versión 0.8.0** (`versionCode 8`). Base de datos sin cambios (v2).

## Registros (Prompt 11)

- **Una sola pantalla** (barra inferior → Registros) con cuatro pestañas en chips: **Ventas**, **Transferencias** (recibidas), **Movimientos** (de inventario e insumos) y **Turnos** (el registro del Prompt 7, sin cambios). No se añadieron rutas ni pantallas: la información de cada elemento se abre en una **ventana** (hoja inferior) y el detalle de turno sigue siendo el de siempre.
- **Orden cronológico** en todas las tablas (la más reciente arriba) y línea de resumen: «3 ventas · 650.00 CUP», «1 transferencia · 50.00 CUP», «5 movimientos».
- **Buscador** en las tres tablas, sin tildes ni mayúsculas («cafe» encuentra «Café»). Ventas: artículo, categoría, método de pago, cliente o Nº exacto («12» o «#12»). Transferencias: Nº de transacción, cliente, CI, teléfono o Nº de venta. *Desde 0.16.1 ya no se busca por el número interno de la venta (ver «Prompt 27»).* Movimientos: nombre, nota, tipo («consumo», «producción»…) o «producto» / «insumo».
- **Filtro por fecha** en todas: Todo, Hoy, Últimos 7 días, Este mes o Elegir fechas (calendario; «Hasta» incluye el día completo y cualquiera de las dos puede quedar vacía). **Filtro por importe** (desde / hasta, en CUP) solo en Ventas y Transferencias; en Movimientos la hoja lo explica. Distintivo con el nº de filtros y chip «Quitar filtro». Errores claros: fechas invertidas, importe no válido, mínimo mayor que el máximo.
- **Cada pestaña recuerda su búsqueda y su filtro.** Estados Cargando / Vacío («Aún no hay ventas») / «Sin resultados» con Quitar filtros / Error con Reintentar.
- **Ventana del elemento** al tocar una fila: la venta (artículos, precio por unidad, total, costo, ganancia, turno y, si se pagó por transferencia, nº y cliente), la transferencia (nº, fecha, importe, cliente, CI, teléfono, tarjeta o teléfono de cobro, con el botón **Ver la venta**) y el movimiento (tipo, fecha, producto o insumo, cambio y existencia con su unidad, nota, venta y turno).
- **Compartir solo texto**, en tablas y elementos, con el selector del sistema (WhatsApp, Telegram, SMS…). La tabla comparte **lo que se ve** (con búsqueda y filtro, indicados en el título) y su total.

**Suposiciones del Prompt 11**

1. **Reutilización**: `RegistroRepository` (sus consultas por fecha e importe ya existían), `ObtenerVenta`, `TablasExport` y `TurnosViewModel`. Solo se añadió `ObservarRegistro` (compone el repositorio con los insumos para mostrar su unidad) y `RegistrosFiltro` (reglas puras). **Ningún SQL ni cambio de esquema** (BD v2).
2. **Días en la zona del teléfono** (Cuba): «Hoy» va de las 00:00 a las 00:00 del día siguiente, en hora de La Habana, con horario de verano incluido.
3. **El texto se filtra en el dominio** y no con el LIKE de la BD: así el buscador se comporta igual que en Inventario y Elaboración (sin tildes) y busca en más campos. La fecha y el importe sí se filtran en la BD.
4. **Privacidad al compartir**: el texto de **una venta** es un comprobante para el cliente y **no incluye costo ni ganancia**; la tabla de Ventas sí los incluye (informe para el dueño). En los textos de transferencias el **carné de identidad se muestra solo con sus 3 últimas cifras** (empieza por la fecha de nacimiento); en la ventana de la app se ve completo. La ventana avisa de ambas cosas.
5. **Tope de 300 filas** al compartir una tabla como texto (Android y las apps de mensajería rechazan textos enormes). Si hay más, el texto lo dice y sugiere filtrar por fecha; el total sigue contando todas.
6. **Exportar a PDF / Excel** (Shalev.txt, «Exportaciones») no está en esta pantalla porque el Prompt 11 pide «Compartir solo texto». El caso de uso `ExportarRegistros` ya existe y respeta el mismo filtro: añadirlo es solo UI, si lo quieres.
7. **Etiquetas de movimiento con tilde** («Producción») en un solo sitio del dominio (`Estadisticas.etiqueta`), que usan Registros, el detalle de turno y las exportaciones; antes el PDF/Excel mostraba «Produccion».
8. **Versión 0.9.0** (`versionCode 9`). Base de datos sin cambios (v2).

## Ajustes (Prompt 12)

- **Licencia** (ya existía): estado actual, costos (Mensual 6,000 · Semestral 30,000 · Anual 50,000 · Perpetua 90,000 CUP), solicitud o renovación por **WhatsApp o SMS al 51815604** con el payload v1 + `appName`, y activación pegando la respuesta. Los datos salen del Perfil; si está vacío se escriben a mano y se guardan en el Perfil. **Nuevo:** el detalle es seleccionable (mantener pulsado → Copiar), para poder copiar el «ID del dispositivo».
- **Bloqueo total al vencer:** se abre **directamente el panel de Licencia**, al arrancar y también si vence con la app abierta. Desaparece la pantalla intermedia. El aviso de bloqueo ofrece «Contactar soporte». Atrás en Licencia sale de la app; desde Clave o Soporte vuelve a Licencia.
- **Perfil** (antes era un marcador de posición): Nombre, Apellidos y CI con validación (las mismas reglas que GL), y listas de **teléfonos** y de **tarjetas y cuentas bancarias** (agregar, editar, eliminar). Las tarjetas se ven enmascaradas y el número completo solo al editar. Pregunta antes de salir si hay cambios sin guardar.
- **Pago electrónico** y **Precios** (Prompt 6): sin cambios. Las listas de Perfil y de Pago son **las mismas** (mismo ViewModel y misma hoja de edición).
- **Respaldo:** exportar la **Configuración** (Perfil, teléfonos y cuentas, precios, tasas y preferencias) o **Todo**, cifrado con contraseña (mínimo 8 caracteres, repetida). Se puede **guardar en el teléfono** o **enviar a otra app** (WhatsApp, Telegram, Bluetooth, Drive…). **Importar** con el selector de archivos del sistema; la contraseña incorrecta deja reintentar.
- **Migrar a otro teléfono**, en 4 pasos:
  1. Solicitud al desarrollador con el ID del teléfono nuevo.
  2. Respaldo completo.
  3. **Autorización:** pegar la licencia que el desarrollador emitió al teléfono nuevo.
  4. **Borrar este teléfono.** Solo se activa con una autorización válida y escribiendo BORRAR.
- **Ayuda:** manual breve de 10 temas plegables (primeros pasos, productos, vender, transferencias, elaboración, registros, copia, licencia, cambio de teléfono, soporte), un paso por línea.
- **Soporte:** ficha con **Ing. Ronnie Montero Duarte · CI 91040922502 · Teléfono 51815604 · Desarrollo de sistemas y aplicaciones multiplataforma**, botones de WhatsApp y SMS, y la versión de la app.

**Suposiciones del Prompt 12**

1. **Reutilización:** Licencia, Pago electrónico, Precios, el respaldo cifrado (`RespaldoRepository`, PBKDF2 + AES-GCM) y los casos de uso del Perfil ya existían. Perfil reutiliza `PagoElectronicoViewModel` y sus listas en vez de duplicarlas.
2. **Migrar** no existe en el contrato GL v1 (ver `DECISIONES` §O5, v1.5):
   - **La autorización es la licencia del teléfono nuevo.** El teléfono viejo verifica la **firma de GL sin descifrarla** y comprueba que **no** es suya.
   - **Orden: ceder la licencia → borrar → re-evaluar**, sin posibilidad de cancelarse a medias.
   - **Qué se borra:** toda la BD, preferencias, fotos y temporales.
   - **Después:** el teléfono queda bloqueado en Licencia, sin prueba, y rechaza la licencia vieja (el mensaje que siga en WhatsApp). Acepta una licencia emitida **después**.
   - **Contrato GL sin cambios.** La solicitud de migración lleva IDs y nombre, **sin CI**.
3. **Respaldo:**
   - Por defecto «Configuración», que es lo que pide el Prompt. «Todo» existe porque Migrar lo necesita.
   - **Sin permisos de almacenamiento** (SAF y FileProvider). El archivo `.shalev` enviado por otra app queda en `cacheDir/compartir`, cifrado, y se borra a las 24 h.
   - **Importar desde WhatsApp/Telegram:** primero se guarda el archivo y luego se elige. *(Superado en el Prompt 14: ahora Shalev también recibe el `.shalev` desde «Compartir» o «Abrir con»; ver abajo.)*
4. **Sin datos sensibles en vistas recientes:** `FLAG_SECURE` por pantalla en Perfil, Pago electrónico, Licencia, Respaldo, Migrar y, **nuevo**, en Registros mientras se ven transferencias (nombre y carné del cliente). Ayuda y Soporte no muestran datos del usuario.
5. **Sin datos sensibles en logs:** la única llamada de log de la app es un aviso genérico de cámara, y R8 elimina todas en release. El código nuevo no registra nada. Las contraseñas llegan como `CharArray` al caso de uso, que las borra. Viven como `String` solo en el estado de la pantalla, porque el TextField lo exige, y se vacían al terminar.
6. **Ayuda y Soporte no tienen ViewModel:** no tienen estado de negocio, son texto fijo más intents. Su contenido es puro y está testeado (`AyudaContenido`, `SoporteInfo`). Los datos de Soporte vienen de `DeveloperContact`, la misma fuente que Licencia.
7. **Versión 0.10.0** (`versionCode 10`). BD sin cambios (v2). Nueva clave cifrada `lic.migrated_at` en el DataStore de licencia.

## Venta (Prompt 13)

- **Inicio → Vender** abre el diálogo **Venta / Elaborado** (ya existía). Las dos opciones llevan al mismo flujo; cambia el filtro inicial de la selección (Artículos o Elaborados), que el usuario puede cambiar.
- **Selección:** se abre **Inventario en modo venta** (la misma pantalla, sin duplicarla), con casillas, búsqueda y filtros. Los artículos sin existencias aparecen como «Sin existencias: no se puede vender» y no se pueden marcar; los Elaborados sí, porque se elaboran al vender. «Continuar con n» vuelve a la venta. La selección sobrevive a búsquedas y filtros. La barra inferior se oculta durante la venta.
- **Carrito:** los productos elegidos, con **– / +** (tope: existencias o lo producible), «Agregar o quitar productos» y la elección de **Efectivo o Transferencia**.
- **Efectivo → comprobante:** productos, cantidades, importe de cada línea (con preajustes de precio aplicados) y total, con **Confirmar** y **Cancelar**. Si hay producción al vuelo, se indica «Se elaborarán n productos con tus insumos al confirmar».
- **Transferencia → QR:**
  1. QR con los datos de **Pago electrónico** (tarjeta y móvil) y, debajo, el **total en grande**.
  2. Botones «Abrir Transfermóvil» / «Abrir Enzona».
  3. «Ya pagó: pedir datos» pide **Nombre y apellidos, CI, Teléfono y Nº de transacción**. El número se captura con **«Pegar SMS»** o **compartiendo el SMS de PAGOxMOVIL a Shalev** (menú Compartir del sistema). Si el SMS trae importe y no coincide con el total, se avisa sin bloquear.
  4. La venta se registra **solo al confirmar**.
- **Registro atómico** (una transacción Room): venta, detalle, movimientos de inventario **VENTA**, producción al vuelo de Elaborados (**CONSUMO** de insumos + **PRODUCCION**) y datos del cliente si es transferencia. Precios y stock se **recalculan al confirmar** por si cambiaron desde el comprobante.
- **Turno:** sin turno abierto no se vende. Se ve el bloqueo con «Abrir turno», también si el turno se cierra con el comprobante en pantalla. Al terminar se vuelve a Inicio (gráficos al día) con el aviso «Venta registrada: 1,450.00 CUP en efectivo.».
- **ViewModel** con `StateFlow` (pasos CARRITO → COMPROBANTE / QR → CLIENTE), carrito y paso sobreviven a la muerte del proceso (`SavedStateHandle`). Eventos de un solo uso por `Channel`.

**Suposiciones del Prompt 13**

1. **SMS sin permisos (C2):** ni `READ_SMS` ni SMS User Consent API. El portapapeles se lee **solo al tocar «Pegar SMS»**. Shalev recibe texto compartido (`ACTION_SEND text/plain`, `singleTask`) y, si hay una venta esperando el número, lo rellena. Si no lo reconoce, el usuario lo teclea. Formatos reconocidos: «Nro. Transaccion:» y «No. Transaccion:»; si se pega la conversación entera se toma la **última**.
2. **QR (C3, cerrado):** formato **verificado** con el QR real de Transfermóvil: `TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta>,<móvil>,`. **El QR oficial no lleva importe**, así que Shalev no inventa un campo: el cliente teclea el total que se muestra bajo el QR. Enzona no publica su formato, solo se ofrece abrirla (`cu.xetid.apk.enzona`). El QR se genera en el dispositivo con **ZXing core**, sin red. Si no hay tarjeta en Pago electrónico se ofrece configurarla.
3. **Elaborado sin existencias:** se **produce al vuelo** en la misma transacción, consumiendo insumos según la receta. Si ni así alcanza, se informa qué falta.
4. **Abrir Transfermóvil/Enzona:** `getLaunchIntentForPackage` con `<queries>` en el manifiesto (Android 11+), sin permisos. Si no está instalada, se avisa.
5. **Sin permisos nuevos:** siguen CAMERA e INTERNET. `FLAG_SECURE` en el paso de datos del cliente (nombre, CI, teléfono).
6. **Versión 0.11.0** (`versionCode 11`). BD sin cambios (v2): las tablas de venta, cliente y movimientos ya existían.

## Exportación e importación (Prompt 14)

- **Base de datos (Ajustes → Respaldo):** «Configuración» o «Todo», cifrado con contraseña. **Guardar en el teléfono** usa el selector del sistema (SAF), donde también aparecen **Google Drive, OneDrive** y cualquier otro proveedor de documentos instalado. **Enviar** usa el menú Compartir (FileProvider): **WhatsApp, Telegram, Zapya, Bluetooth**, correo…
- **Importar**, de tres maneras:
  1. «Importar» → selector de archivos (almacenamiento, Drive, OneDrive…).
  2. **Compartir** el `.shalev` a Shalev desde WhatsApp, Telegram o Zapya.
  3. **Abrir con → Shalev** desde un gestor de archivos o la descarga de Bluetooth.

  En los casos 2 y 3, Shalev copia el archivo a su caché, abre Respaldo y muestra el diálogo de importación.
- **Antes de pedir la contraseña, se comprueba el archivo:** qué es («Respaldo completo del 29/09/2026»), y si llegó completo e intacto. El aviso dice exactamente qué se reemplazará (todo, o solo la configuración). Si el archivo está **cortado** (envío interrumpido) o **dañado**, o no es de Shalev, se explica sin pedir la contraseña. Tus datos no se tocan.
- **Progreso visible**, paso a paso:
  - Exportar: «Paso 1 de 3 · Reuniendo los datos…» → «Cifrando con tu contraseña…» → «Guardando el archivo…».
  - Importar: leer → comprobar que esté completo → comprobar la contraseña → importar.

  Mientras dura, las demás acciones quedan deshabilitadas.
- **Confirmación:** un diálogo final al terminar. Si sale bien, dice qué se guardó o importó (nombre del archivo y conteos). Si falla, explica qué pasó y qué hacer. La contraseña incorrecta deja el diálogo abierto para reintentar.
- **Perfil, preajustes de precios y tasas de cambio → PDF** (tarjeta «Documento PDF» en Respaldo): guardar o enviar. La tarjeta avisa de que el PDF no lleva contraseña.
- **Registros → Ventas, Transferencias y Movimientos → PDF y Excel:** botón **Exportar** en la barra superior. Se exporta **lo que se ve**, con la búsqueda y el filtro aplicados (se indican en el título). Cada formato se puede **enviar** o **guardar**. Nombres sin datos personales: `Shalev_ventas_2026-09-30.pdf`. En Transferencias se avisa de que incluye datos de clientes.
- **Inventario → PDF, Excel, Imagen y Tarjetas promocionales** (selección o todo):
  - **Imagen:** una **lista de precios** en PNG (categoría, producto y precio), 25 filas por imagen, numeradas si hay varias.
  - **Tarjetas:** una por producto, con foto, nombre y precio (como antes).
- **Registros → compartir como texto** (sin cambios): el «Compartir» de cada tabla y de cada ficha sigue enviando solo texto.

**Suposiciones del Prompt 14**

1. **Sin permisos de almacenamiento:** ni `READ/WRITE_EXTERNAL_STORAGE` ni `MANAGE_EXTERNAL_STORAGE`. Todo pasa por SAF (el usuario elige el destino o el archivo) y por FileProvider (URI temporal de solo lectura para la app elegida). Siguen solo CAMERA e INTERNET.
2. **Drive, OneDrive y Bluetooth no se integran por su API:** se usan por el sistema (proveedor de documentos o destino de Compartir). Sin SDK, sin cuentas y sin red propia. Si la app no está instalada, simplemente no aparece.
3. **Recibir archivos:** dos `intent-filter` (`SEND` y `VIEW` con `content://`, tipo `application/octet-stream`, el tipo genérico con el que se envía un archivo de extensión desconocida como `.shalev`). La etiqueta es «Importar respaldo en Shalev». El archivo recibido:
   - se copia **acotado a 128 MB** a `cacheDir/compartir` (se borra a las 24 h);
   - se acepta solo por `content://`, nunca rutas `file://` ajenas;
   - **no se importa solo**: siempre hace falta la contraseña y la confirmación.

   Si no es un respaldo, se explica y no pasa nada más.
4. **Formato de respaldo v2:**
   - Cabecera con tipo, fecha, largo y SHA-256 del cifrado. Así se distingue «cortado / dañado» de «contraseña incorrecta», y se sabe qué contiene **sin** contraseña. Tipo y fecha no son datos personales.
   - La cabecera va autenticada en el AES-GCM: si se altera, falla.
   - **Los respaldos v1 (Prompt 12) se siguen importando.** En ellos «contraseña incorrecta o archivo alterado» no se puede separar, y así se dice.
5. **Importación atómica:** se descifra y se valida todo **antes** de escribir nada, y la escritura de la BD va en una sola transacción de Room. Cualquier error al leer, descifrar o validar deja los datos como estaban.
6. **Exportar Registros usa el carné completo** en PDF y Excel (es el registro del negocio). **El texto compartido lo sigue enmascarando** (••••••••345), porque suele terminar en un chat.
7. **«Imagen» del Inventario = lista de precios para clientes:** sin costo ni existencias. En el Prompt 8, Imagen y Tarjetas eran lo mismo; ahora cada una tiene su formato.
8. **Imagen y Tarjetas solo se envían, no se guardan:** pueden ser varios archivos, y «Guardar como» del sistema crea uno solo. PDF y Excel se pueden enviar y guardar.
9. **Versión 0.12.0** (`versionCode 12`). BD sin cambios (v2).

## Tests (Prompt 15)

Toda la suite se ejecuta **sin red**. Los tests JVM no necesitan dispositivo; los instrumentados usan Room **en memoria** (nunca la base real ni SQLCipher) y fakes en los bordes del dispositivo.

### Cómo ejecutarlos

| Qué | Terminal | Android Studio |
|---|---|---|
| Todos los JVM (core, licencia, domain, data, designsystem, app) | `./gradlew shalevTests` | Gradle ▸ shalev ▸ Tasks ▸ verification ▸ `shalevTests`, o clic derecho en `src/test` ▸ *Run Tests* |
| Un módulo JVM | `./gradlew :domain:test` · `./gradlew :app:testDebugUnitTest` | Clic derecho en la carpeta `src/test/kotlin` del módulo ▸ *Run* |
| Una clase o un test | `./gradlew :domain:test --tests "cu.shalev.domain.CasosBordeTest"` | Icono ▶ junto a la clase o al método |
| Todos los instrumentados | `./gradlew shalevInstrumentedTests` (emulador o teléfono conectado, API 26+) | Clic derecho en `src/androidTest` ▸ *Run Tests* con el dispositivo elegido |
| Solo los flujos de integración | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=cu.shalev.app.integracion` | Clic derecho en el paquete `integracion` ▸ *Run* |

Los informes quedan en `<módulo>/build/reports/tests/` (JVM) y `<módulo>/build/reports/androidTests/connected/` (instrumentados).

**Sin red:** en la raíz del build, cada tarea `Test` JVM manda el tráfico HTTP/HTTPS a un proxy inexistente (`127.0.0.1:9`). Solo se permite el bucle local (`localhost`, `127.*`, `[::1]` y el nombre canónico de `localhost`), que es lo que usa `MockWebServer` en `EscanerRedTest`. Un test que intentara salir a internet falla al momento, en vez de pasar por suerte. Los instrumentados tampoco abren sockets: las bases públicas de productos son `FuenteMem`, en memoria. El dispositivo puede estar en modo avión.

**Huso horario:** los tests JVM fijan `user.timezone=UTC`, el huso con el que se verificó la suite. Los tests que dependen de La Habana lo indican de forma explícita (`ZoneId.of("America/Havana")`).

**Animaciones:** `testOptions.animationsDisabled = true` en `:app` y `:data`. Los tests de Compose no esperan transiciones.

### Qué cubre cada capa

| Capa | Dónde | Qué prueba |
|---|---|---|
| Dominio y casos de uso | `domain/src/test` | Venta (cotizar, registrar, producción al vuelo), turno, inventario, alertas, Top 3 y gráficos, precios, escáner, respaldo, licencia, onboarding, perfil |
| Cifrado GL y licencia | `licencia/src/test` | ECIES P-256/AES-256-GCM, firma ECDSA, AAD, claves fijadas, vectores dorados, estados (prueba, activa, vencida, perpetua), reloj atrasado, migración |
| Validación y formato monetario | `core/src/test` | `Validators`, `Cup`/`Money` («1,450.00 CUP»), `Cantidad` |
| Repositorios (JVM) | `data/src/test` | Mapeos entidad↔dominio, DTO, códecs, `BackupCipher`, exportadores, red del escáner con `MockWebServer` |
| Repositorios con BD en memoria | `data/src/androidTest` | `RepositoriosRoomTest` (inventario, recetas, producir, venta en Efectivo y por Transferencia, todo o nada, producción al vuelo, Registros, Precios), `RespaldoRoomTest` (ida y vuelta; archivo cortado, alterado o con otra contraseña no toca los datos), `TurnoRepositoryInstrumentedTest`, `LicenciaInstrumentedTest` (Keystore real) |
| ViewModels | `app/src/test` | Todas las pantallas con estado: Inicio, Venta, Inventario, Producto, Escáner, Elaboración, Registros, Turno, Precios, Pago electrónico, Perfil, Ajustes, Licencia, Migrar, Onboarding, Respaldo, raíz y entrada compartida. Ayuda y Soporte no tienen ViewModel (ver Ajustes) |
| UI Compose | `app/src/androidTest` | Pantallas sin estado (`*Content`) con datos fijos |
| Integración | `app/src/androidTest/.../integracion` | Pantalla real + ViewModel real + casos de uso + Room en memoria: `FlujoVentaTest` (Efectivo, Transferencia y sin turno), `FlujoTurnoTest` (apertura y cierre desde Inicio), `FlujoEscanerTest`, `FlujoRespaldoTest`, `FlujoLicenciaTest` (bloqueo → activar → Inicio) |

En la integración solo se simulan los bordes que dependen del dispositivo o de terceros (`EntornoIntegracion`): Keystore y DataStore (perfil, preferencias, licencia), cámara (se escribe el código a mano, el mismo camino que un código leído), el selector de archivos del sistema (se entrega la URI al ViewModel, igual que el callback del launcher) y las bases públicas de productos. `MainScaffold` necesita Hilt, así que `FlujoLicenciaTest` reproduce la puerta de `ShalevRoot` con `RootViewModel` y un marcador en lugar de Inicio.

### Casos borde

| Caso | Tests |
|---|---|
| Sin turno abierto | `CasosBordeTest` (Efectivo y Transferencia rechazadas sin tocar el stock; cerrar sin turno; turno cerrado entre el comprobante y la confirmación), `TurnoUseCasesTest`, `VentaFlujoTest`, `VentaViewModelTest`, `RepositoriosRoomTest`, `TurnoRepositoryInstrumentedTest`, `TurnoUiTest`, `FlujoVentaTest` |
| Licencia vencida | `CasosBordeLicenciaTest` (mensual vencida bloquea y una renovación la reactiva), `CasosBordeTest` (revisión ≤ 15 min), `LicenseFlowTest`, `RootStateTest`, `RootViewModelTest`, `LicenciaUseCasesTest`, `BannerTest`, `LicenciaUiTest`, `FlujoLicenciaTest` |
| Código no encontrado | `CasosBordeTest` (ni local ni en ninguna base; código interno o con dígito de control erróneo no sale a internet; nombre en blanco), `EscanerInventarioTest`, `EscanerViewModelTest`, `EscanerRedTest`, `FlujoEscanerTest` |
| Archivo corrupto | `CasosBordeTest` (el error se propaga y la contraseña se borra igual), `BackupCipherTest`, `RespaldoTest`, `RespaldoRoomTest`, `FlujoRespaldoTest` |
| Empate en el Top 3 | `CasosBordeTest` (empate total determinista; la ganancia empatada la decide el margen; más de 3 empatados; eliminados fuera de «lento»), `StockEstadisticasTest`, `TurnoUseCasesTest` |
| Licencia perpetua | `CasosBordeLicenciaTest` (sigue desbloqueada 20 años después), `CasosBordeTest`, `LicenseFlowTest`, `RuntimeVerificationTest`, `LicenciaFormTest`, `BannerTest`, `FlujoLicenciaTest` |
| `devicePub` faltante | `CasosBordeLicenciaTest`: una solicitud sin `devicePub` hace que GL cifre hacia su propia clave. Shalev la rechaza, no guarda nada y sigue en prueba. La solicitud de Shalev nunca la omite. También `LicenseFlowTest`, `RuntimeVerificationTest` y `LicenciaInstrumentedTest` |

### Suposiciones

1. **Licencia simulada en la integración de `:app`.** `LicMem` acepta un texto fijo. La criptografía GL real se prueba en `:licencia`, y el Keystore en `:data` (`LicenciaInstrumentedTest`). Así los flujos de UI no dependen de claves del dispositivo.
2. **Sin Hilt en los tests instrumentados.** Las pantallas reciben el ViewModel por parámetro (`viewModel = …`). Es más simple que `HiltTestRunner` y no cambia el código de producción.
3. **`FakeGl.issue` sigue el contrato también sin `devicePub`:** cifra hacia el ECDH de GL, como hace GL de verdad.

## Optimización (Prompt 17) — 0.13.0

Plan completo y decisiones en `PLAN_OPTIMIZACION_P17.md` (§9; retirado en la 0.18.0). Sin clientes instalados ni repositorio remoto, así que se eliminó la compatibilidad con versiones de desarrollo.

| Fase | Qué se hizo |
|---|---|
| A. Limpieza | 20 casos de uso sin uso borrados; fuera `TextoWriter`, `sqlite-ktx` y `compose-animation`; OkHttp directo en lugar de Retrofit; iconos propios (trazados de Material Symbols, Apache 2.0) en lugar de `material-icons-extended`, con `ShalevIconsTest`; días de aviso de caducidad (7) y tiempo de espera de red (5 s) pasan a constantes |
| B. Funciones retiradas | *Clave del emisor* (`GlKeyPolicy`, `GlKeyStore`, `ClaveEmisorScreen` y sus tests); botón «Abrir Enzona» y su `<queries>`; caducidad recordada por código; tasas de cambio; respaldo solo «Todo» (formato v3, sin lectura de v1/v2); BD v3 con `fallbackToDestructiveMigrationFrom(1, 2)` y sin `Migraciones.kt` |
| C. Casos de uso que solo reenvían | 20 clases borradas; los ViewModels usan directamente las interfaces de repositorio de `:domain`. Se quedan los que tienen lógica |
| D. Estructura | `:security` se integra en `:data` (`cu.shalev.data.security`), quedan 6 módulos; exportación unificada en `app/common/ExportadorArchivos.kt`; fakes de `:app` compartidos en `app/src/sharedTest` |
| E. Automatización local | `./gradlew shalevCheck` (tests JVM + lint con `app/lint-baseline.xml` + APK debug + control de permisos), `EsquemaTest` (instrumentado) y `.github/workflows/ci.yml` preparado e inactivo |
| F. Documentación | 0.13.0 (13) |

**Desviaciones de Shalev.txt (decididas por el usuario):** tasas de cambio (:11), botón Enzona (:6), caducidad por código (:87) y respaldo «Solo configuración» (:132). Detalle en el README.

### Suposiciones del Prompt 17

1. **`data/schemas/cu.shalev.data.db.ShalevDatabase/3.json`** lo genera Room (KSP) en la primera compilación en Android Studio; hay que subirlo a git. `EsquemaTest` lo lee desde los assets de `androidTest`.
2. **S6 (fakes compartidos):** se compartieron los 3 fakes que usaban a la vez los tests JVM y los instrumentados; los que solo usa un lado se quedaron donde estaban.
3. **Teléfonos de prueba:** al abrir la 0.13.0, una BD v1 o v2 se recrea vacía. Lo más sencillo es desinstalar y reinstalar.
4. **Baseline de lint vacío:** no se pudo ejecutar lint en este entorno. La primera ejecución de `shalevCheck` dirá si hay que regenerarlo con `./gradlew updateLintBaseline`.

## Rediseño UI/UX/IX (Prompt 18) — 0.14.0

Auditoría, plan y especificación por pantalla en `AUDITORIA_UI_UX_IX_P18.md` (hallazgos A01–A24; retirado en la 0.18.0). Sin cambios en lógica de negocio, criptografía, contrato GL, permisos, manifiesto ni arquitectura, y sin dependencias nuevas. El resumen visible para producto está en `UI_UX_IX.md` y la referencia de componentes en `DESIGN_SYSTEM.md`.

| Área | Cambio |
|---|---|
| Design system | `ShalevTabs`, `ShalevStepper`, `ShalevStatusBanner` con `BannerTone` (Info/Aviso/Crítico), `ShalevHaptics`; `bodyMedium` 14sp y `bodySmall` 13sp; modo letra grande (`letraGrande()`, escala ≥ 1.3); tokens `fabClearance`, `qr`, `fotoMiniatura`, `campoCantidad`, `contentMaxWidth`, `tonalBar`; motion direccional y de listas; `shalevAnimateItem()`, `shalevContentWidth()`; `ShalevTextField` con acción de teclado; `ShalevEmptyState` con Ayuda |
| Navegación | Fundido entre pestañas y deslizamiento direccional hacia los detalles; estado leído con `collectAsStateWithLifecycle` |
| Licencia | Asistente de 3 pasos (datos → solicitud → activación) dentro de la misma pantalla; aviso de caducidad en Inicio con tono según los días restantes |
| Respaldo | Exportar en 2 pasos (contraseña → destino) e importar con «Paso n de 3» |
| Formularios | «¿Salir sin guardar?» al volver con cambios (`rememberSalidaProtegida`) en Producto, Insumo y Perfil, y en las hojas de Precios y Pago electrónico; cadena Siguiente/Listo en el teclado |
| Registros y Elaboración | Pestañas accesibles con contador; Registros recuerda pestaña y filtros (`SavedStateHandle` + `FiltrosGuardados`) |
| Venta y Escáner | Vibración de éxito al terminar la venta y al leer un código; `FLAG_SECURE` también en el paso QR |
| Accesibilidad | Pestañas con rol y posición, regiones vivas en asistentes y banners, valor de `ShalevListItem` debajo del título con letra grande, probado con escala 2.0 |

Tests nuevos: `BannerTonoTest`, `FiltrosGuardadosTest`, `LicenciaPasosTest`, `RespaldoPasosTest` (JVM, `:app`), `P18ComponentesTest` (JVM, `:designsystem`) y `DisenoP18UiTest` (instrumentado).

### Suposiciones del Prompt 18

1. **Tipografía +1sp** solo en `bodyMedium` y `bodySmall`; los títulos no cambian.
2. **Sin rutas nuevas:** los asistentes de Licencia y Respaldo viven dentro de sus pantallas.
3. **«Deshacer» = confirmación previa** en las acciones destructivas; no se añadió deshacer posterior.
4. **Exportar respaldo en 2 pasos;** el «Hecho» es el diálogo de resultado que ya existía.
5. **Los botones «Cancelar» de las hojas no preguntan:** son una decisión explícita. Solo deslizar, tocar fuera y atrás piden confirmación. El estado «con cambios» de las hojas se pierde al girar el teléfono.
6. **Vibración:** éxito al terminar la venta y en el escáner; un toque breve con cada snackbar. La producción en Elaboración no vibra porque su resultado es un mensaje genérico.
7. **La Ayuda conserva «Abrir turno»** (lo exige `AyudaSoporteTest`).
8. **Recomposiciones:** no hay test automático; ver `UI_UX_IX.md` §4.
9. **Pendiente opcional:** el asistente de Onboarding y Migrar todavía no usan `ShalevStepper`.

## Revisión final y empaquetado (Prompt 19) — 0.14.1

Sin funciones nuevas. El informe completo está en `REVISION_FINAL.md` y el procedimiento de release en `RELEASE.md`.

- **Build:**
  - `shalevPermisos` revisa debug y release, y admite el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de androidx.core; sin esa excepción, `shalevCheck` habría fallado.
  - Nueva tarea `shalevRelease`.
  - `signingConfig` desde `keystore.properties` o variables `Shalev_*`.
  - `gradlew` ejecutable, también en la CI.
  - Secretos de firma en `.gitignore`.
- **Contrato GL:** el `deviceId` se limita a ASCII `a-z0-9` y a 128 caracteres (`DeviceIdTest`). Con un ANDROID_ID real el valor no cambia.
- **Design system:** trazos de `Charts` y `Buttons` pasan a tokens.
- **Copias de Android:** `data_extraction_rules.xml` con `path="."` explícito.
- **Verificación:**
  - 564 tests JVM correctos.
  - 0 avisos del compilador en producción.
  - SQL de Room contra SQLite: 13 tablas, 69 consultas, sin errores.
  - Instrumentados compilados.

### Suposiciones del Prompt 19

1. **Versión 0.14.1 (15)**, no 1.0.0: el release firmado y los instrumentados aún no se han ejecutado en Android Studio.
2. **APK como canal principal** (distribución directa). El AAB se genera solo por si algún día se publica en Google Play.
3. **Sin firma configurada, el release sale sin firmar** con un aviso, en lugar de fallar. Así el build de debug y la CI no necesitan secretos.

## Cierre del plan UI/UX/IX (Prompt 18, respuestas 1-A, 2-opción 2, 3-opción 1, 4-opción 2) — 0.14.2

Sin cambios en dominio, casos de uso, repositorios, criptografía, contrato GL, permisos, manifiesto ni base de datos. Sin dependencias nuevas.

- **A16, validación homogénea:** `ShalevTextField` gana `validarAlSalir` y `forzarError` (lógica pura en `ShalevValidacion`). Se aplica a Producto, Insumo, Perfil, Pago electrónico (sustituye la heurística por longitud), Precios, Venta→Cliente, Onboarding (datos y niveles), Licencia, Respaldo y Escáner (el «Escribe el nombre» pasa a ayuda, sin rojo antes de escribir).
- **Pregunta 3, Respaldo:** Exportar 1 Contraseña → 2 Destino → 3 «Respaldo listo» (`RespaldoUiState.exportado`, botón «Hacer otro respaldo»); Importar 1 Elegir → 2 Comprobar → 3 Contraseña → 4 Confirmar. El progreso de la operación pasa a llamarse «Etapa N de M».
- **L9:** Onboarding y Migrar usan `ShalevStepper` (`pasoMigrar` deriva el paso en curso). `ShalevStepper` sin flechas ya no reserva huecos.
- **A07:** «Siguiente»/«Listo» en el paso Datos del Onboarding («Listo» = Siguiente).
- **A18 / A22:** `shalevAnimateItem()` en el carrito de Venta y en las listas de Pago electrónico; `shalevContentWidth()` en Onboarding, Perfil, Pago electrónico, Escáner, Venta, Migrar y Soporte.
- **Pregunta 4:** sin «Deshacer» (solo confirmación). **Pregunta 2:** tipografía +1sp ya aplicada, ahora con test.
- **Tests:** +10 JVM (`RespaldoPasosTest` 7, `P18ComponentesTest` +5 entre validación y tipografía, `MigrarTest` +2; `RespaldoTest` amplía 2) y +3 Compose (`DisenoP18UiTest` ×2, `AjustesPantallasUiTest.respaldoPaso3HechoYHacerOtro`); `OnboardingUiTest` y `AjustesPantallasUiTest` actualizados.
- **Verificación:** 219 + 54 + 301 tests JVM correctos; 0 avisos del compilador; instrumentados compilados.

## Prompt 24 — cambios de interfaz (botones solo icono, ventanas centradas, filas con un dato) — 0.15.0

Sin cambios en dominio, casos de uso, repositorios, base de datos, criptografía, contrato GL, permisos ni manifiesto. Sin dependencias nuevas: las ilustraciones se convierten a vectores Compose y viajan en el código.

Respuestas aplicadas: texto solo en «Nueva venta»; ilustraciones libres estilo unDraw adaptadas al color del tema; **todas** las hojas inferiores pasan a ventanas centradas; «viñetas» = pestañas, también en Elaboración y en el detalle del turno.

| # | Pedido | Cómo quedó |
|---|---|---|
| 1–2 | «Nueva venta» centrada; Escanear a su derecha | Inicio: «Nueva venta» (único botón con texto) centrada; Escanear sale de la barra superior y va a su lado. |
| 3, 10 | Botones sin texto; ancho según contenido | `ShalevPrimaryButton`/`ShalevSecondaryButton`/`ShalevTextButton` con `icon` obligatorio; circulares de 48dp; texto como TalkBack + tooltip. Ningún botón ocupa todo el ancho (`fillMaxWidth` retirado en Escáner, Migrar, Respaldo, Soporte, Registros, formularios). |
| 4 | Intensidad de las barras de Ventas | `ChartMath.intensidad`: opacidad 0,30 → 1 según el valor respecto al máximo de la escala Y. |
| 5 | Menos texto en tarjetas | Explicaciones de tarjetas, estados vacíos, ayudas de campos y diálogos reducidas a una frase (Inicio, Inventario, Elaboración, Precios, Registros, Turnos, Venta, Licencia, formularios). |
| 6, 16 | Textos alineados y sin partir | Tipografía `.titulo()`/`.cuerpo()`; `ShalevTextoAjustable` (reduce la letra antes de cortar) en los importes de las filas y en las celdas de Inicio. |
| 7 | Icono de Precios «$» | `ShalevIcons.Precios` = AttachMoney. |
| 8 | Período en combobox | `ShalevComboBox` en Inicio. |
| 9, 12 | Ventanas centradas; Confirmar/Cancelar centrados | `ShalevBottomSheet` y `ShalevDialog` comparten `ShalevVentana` centrada; tocar fuera cierra; pie con `ShalevButtonRow` centrado (16dp). El selector de fecha también centra sus botones. `ShalevDialog` gana `confirmEnabled`/`confirmLoading`/`confirmTag`/`confirmIcon`. |
| 11 | Fitts, Hick, Jakob, Proximidad, Von Restorff | Tabla en DESIGN_SYSTEM.md («Prompt 24»). Ejemplos: botón flotante en Precios, `ShalevBarraAcciones` fija en formularios, un FAB por pestaña en Elaboración, botones juntos a lo que afectan. |
| 13 | Imágenes de «Algo salió mal», «Sin resultados», vacíos | 9 ilustraciones unDraw (npm `undraw-svg` 2.0.0, MIT) en `designsystem/.../ilustracion/ShalevIlustraciones.kt`; acento = `primary`, grises invertidos en oscuro. |
| 14 | Campos redondeados | `ShalevTextField`: 12dp una línea, 16dp multilínea. |
| 15 | Formato de fecha/hora del dispositivo | `Dates.formato` (core) lo fija `FormatoFechaDispositivo` al arrancar; guía del campo de fecha derivada (`FechasUi.guia()`). |
| 17 | Varias tablas → pestañas | Detalle del turno: Resumen · Ventas · Inventario (`PestanaTurno`). Elaboración y Registros ya las tenían. |
| 18 | Botones repetidos | Guardar arriba + abajo → uno solo en `ShalevBarraAcciones` (Insumo, Producto); «Agregar» del estado vacío que repetía el «+» (Inventario, Elaboración, Precios); «Ahora no» duplicado en la consulta en línea del Escáner; «Nuevo elaborado» al final de la lista; editar en la fila de Elaborado sin receta. |
| 19 | Filas con el dato principal | Inventario/Insumos: nombre + existencias; subtítulo solo como aviso (Crítico, Bajo, Vence…). Elaborados: nombre + existencias (aviso «Sin receta» / «Sin insumos suficientes»). Registros: nombre/Nº + fecha + importe. Precios: nombre + porcentaje (el resumen queda para TalkBack). Turno: ventas con hora y total. Carrito: precio c/u y existencias solo al llegar al tope o si se elabora al vender. |

- **Tests:** +3 JVM en core (`FormatoFechaTest`), +4 en app/design system (`P24ComponentesTest`, `ShalevIlustracionesTest`, `FechasUiTest`, `VentaLogicTest.existenciasSoloComoAviso`); actualizados `ElaboracionLogicTest`, `RegistrosLogicTest`, `RegistrosViewModelTest`, `InicioLogicTest`. Compose: los clics por texto en botones pasan a `onNodeWithContentDescription`; `ElaboracionUiTest` (un FAB por pestaña, confirmar Producir desactivado), `InventarioUiTest` (vacío sin «Agregar» repetido), `TurnoUiTest` (pestaña Ventas). Capturas: +2 (`04r2`, `04r3`).
- **Verificación:** 222 + 54 + 305 tests JVM correctos; instrumentados y capturas compilados; SQL 0 errores.


## Ajustes tras el Prompt 24 — 0.15.1 (18)

| Petición | Cambio |
|---|---|
| «Nueva venta» solo icono, centrada con Escanear y espacio moderado | `InicioScreen.TurnoCard`: pareja centrada con `ShalevSpacing.lg` (24dp); se quita el hueco de compensación. `ShalevPrimaryButton` pierde `mostrarTexto`. |
| Combobox de Período acorde a la UI | `ShalevComboBox` rediseñado como tarjeta tonal (ver DESIGN_SYSTEM.md). |
| Guardar → Confirmar | `ShalevIcons.Guardar = Confirmar`. Afecta a Producto, Insumo, Perfil, Pago electrónico, Precios y Escáner. |
| Ventas, transferencias, turnos y movimientos por fecha | `RegistrosLogic` (título = fecha), `TurnoLogic` (`tituloTurno` = fecha, `tituloDetalleTurno`, `horasTurno`) y `TablasExport` (`tituloVenta/Transaccion/Movimiento`; sin «Nº» de venta, «Venta Nº» ni «Turno #» en fichas, textos compartidos y exportaciones). El buscador de Ventas ya no menciona «Nº». |

- **Tests:** se actualizan RegistrosLogicTest, TurnoLogicTest, RegistrosViewModelTest, RegistrosUiTest, RegistrosTest y TablasExportTest.
- **Verificación:** 222 + 54 + 305 tests JVM correctos; instrumentados y capturas compilados; 0 avisos.

## Prompt 26 — Elaborados sin producción previa — 0.16.0 (19)

> «Vamos a redefinir el proceso de elaboración y venta de productos elaborados: estos no se producen previamente en la app; mientras queden insumos suficientes, se venden los productos elaborados sin ningún proceso intermedio.»

Decisiones del usuario: la fila de un Elaborado muestra **«Alcanza para N»** (manda el insumo más escaso; con 0, «Sin insumos suficientes»); **solo los insumos** generan avisos; se **elimina por completo** la acción Producir, y el formulario de un Elaborado ya no pide cantidad ni niveles.

| Capa | Cambio |
|---|---|
| Dominio | `PlanificadorVenta.cotizar` ignora la `cantidad` de un Elaborado y valida contra `alcanza` (unidades que permiten los insumos). Su costo es el de la receta **en ese momento** (`costosReceta`). `Cotizacion.aProducir` → `elaborados` (todas las unidades). `CotizarVenta` lee la receta de **todos** los Elaborados del carrito. `ProduccionEnVenta` → `ElaboradoEnVenta(productoId, nombre, unidades, consumo)`. Se eliminan `ProducirElaborado` y `ProductoRepository.producir`. `Stock.nivel` devuelve NORMAL para un Elaborado: no cuenta en las alertas de stock bajo/crítico. La dona de unidades por categoría excluye los Elaborados. `GuardarProducto` guarda un Elaborado con cantidad 0 y sin niveles; `Validadores.producto` lo exige. Renombres: `ElaboradoProducible` → `ElaboradoDisponible` (`maximo` → `alcanza`), `ObservarProduccion` → `ObservarElaborados`. Nuevo `alcanceElaborados` (flujo productoId → alcanza), que usan `ObservarInventario` (`ItemInventario.alcanza`) y `ObtenerFichaProducto` (`FichaProducto.alcanza`). |
| Datos | `VentaRepositoryImpl.registrar(venta, elaborados)`: dentro de la misma transacción descuenta los insumos (UPDATE condicional, movimientos CONSUMO ligados a la venta con la nota «Venta de X ×N») y **no toca** la existencia del Elaborado ni crea movimientos VENTA/PRODUCCION para él. Si un insumo no alcanza: StockInsuficiente(insumo) y rollback de todo. Se eliminan `producir` del repositorio y `ProductoDao.actualizarCosto`. `TipoMovimiento.PRODUCCION` se conserva solo para leer respaldos y registros antiguos. Sin migración de BD (no hay clientes instalados): la cantidad guardada de un Elaborado se ignora. |
| App | Elaboración: sin botón ni diálogo Producir (`DialogoProducir`, `ProduccionLogic`, sus etiquetas y acciones del ViewModel). La fila muestra `textoAlcance` y tocarla abre la receta. Inventario: fila y ficha con «Alcanza para N». En modo venta un Elaborado solo se puede elegir si alcanza al menos para 1 (motivo «Sin insumos suficientes: no se puede vender.»). Con 0 la fila se pinta en tono de peligro, y el texto acompaña al color. Venta: el tope del carrito de un Elaborado es lo que alcanza (`VentaViewModel` combina productos y `ObservarElaborados`). La línea muestra siempre «Alcanza para N»; desaparecen «Listos…/se elaborarán…» y el aviso del comprobante/QR. Formulario de producto: un Elaborado oculta Cantidad y niveles. Exportar inventario: celda Cantidad vacía para Elaborados; la ficha pone «Alcanza para». Ayuda de Elaboración reescrita. |

- **Tests:** VentaFlujoTest reescrito (cotizar contra lo que alcanza, costo por receta, venta que descuenta solo los insumos, venta mixta, insumo compartido que no alcanza para dos Elaborados → nada se registra, sin turno). Se actualizan ElaboracionTest (alcance reactivo; los Elaborados no generan alertas), UseCasesTest (Elaborado guardado sin existencias ni niveles), EscanerInventarioTest, ValidadoresTest, RepositoriosRoomTest (venta de Elaborado + rollback), ElaboracionLogicTest, ElaboracionViewModelTest, VentaLogicTest (tope por alcance; `SeleccionVenta` con `ItemInventario`), InventarioLogicTest, ProductoFormTest, ElaboracionUiTest e InventarioUiTest. Capturas: −3 (`03l`, `03l2`, `03m`); la lista de Elaborados añade uno «Sin insumos suficientes».
- **Limpieza:** imports sin uso retirados en varios archivos (sin cambio de comportamiento).
- **Verificación:** 224 + 54 + 305 tests JVM correctos; instrumentados y capturas compilados; 0 avisos; SQL 13 tablas / 68 consultas, 0 errores.

## Prompt 27 — sin búsqueda oculta por Nº de venta — 0.16.1 (20)

Desde el Prompt 25 las ventas se identifican por su fecha y el número interno no aparece en pantalla, en el texto compartido ni en las exportaciones. Aun así, el buscador de Registros seguía encontrando una venta si se escribía su número interno («12» o «#12»), y una transferencia si se escribía el número de su venta. Era una búsqueda que el usuario no podía descubrir, y además hacía aparecer resultados que no se explicaban (por ejemplo, «12» mostraba la venta 12 aunque no contuviera nada con «12»).

| Cambio | Dónde |
|---|---|
| `coincide(Venta)` ya no compara el texto con `venta.id` (con o sin `#`) | `domain/.../service/RegistrosFiltro.kt` |
| `coincide(Transaccion)` ya no compara el texto con `ventaId` | ídem |
| Prueba renombrada a `buscadorSinTildesNiMayusculasYSinIdInterno`: «12» y «#12» no encuentran la venta 12; «#7» no encuentra la transferencia de la venta 7 | `domain/src/test/.../RegistrosTest.kt` |

**Se mantiene:** la búsqueda por el **Nº de transacción bancaria** (el del SMS de PAGOxMOVIL), que sí se ve en pantalla y en la ficha. El texto de ayuda de Transferencias («Nº de transacción, cliente, CI o teléfono») ya no mencionaba el número de venta, así que no cambia.

**Textos largos en filas (detectado al preparar las capturas):** «Sin insumos suficientes» no cabía en el 45 % del ancho de la fila ni con la letra reducida a 11 sp, así que en un móvil de 360 dp se habría cortado con «…» (incumple el punto 16 del Prompt 24). `ShalevListItem` tiene ahora `valueMaxLines` y `subtitleMaxLines`, los dos a 1 por defecto, así que el resto de la app no cambia. Se usan así: 2 líneas para el valor de las filas de Elaborados (Inventario, modo venta y Elaboración) y para el subtítulo de la línea del carrito («400.00 CUP c/u · Alcanza para 15»); 4 líneas para los valores de las fichas de Inventario y de Registros (receta, descripción, nota, cliente).

**Capturas:** maquetas HTML de las pantallas de los Prompts 26 y 27 en `CAPTURAS_P26.pdf` (retirado en la 0.18.0, sustituido por `CAPTURAS_P29.pdf`), con el mismo método que en el Prompt 24 (no son capturas de un dispositivo; ver L11/L12 en `REVISION_FINAL.md`).

## Prompt 28 — centrado, total único, sin duplicados — 0.17.0 (21)

Cinco peticiones: (1) en «Cobro por transferencia», quitar «Abrir Transfermóvil», subir y centrar el total y hacer lo mismo en todas las ventanas con total; botones Cancelar/Confirmar centrados, separados y sin texto; (2) centrar o repartir los elementos que van solos en una fila; (3) quitar elementos visuales que repiten la misma información; (4) ocultar el ID del dispositivo y el de la licencia en Licencia; (5) centrar y repartir el asistente inicial.

**1. Venta y totales**

| Cambio | Dónde |
|---|---|
| Fuera el botón «Abrir Transfermóvil», el evento `AbrirApp`, `PagoQr.PAQUETE_TRANSFERMOVIL` y el bloque `<queries>` del manifiesto (Shalev ya no consulta qué apps hay instaladas) | `AndroidManifest.xml`, `venta/*`, `PagoQr.kt` |
| `TotalVenta`: el total va **una sola vez**, centrado arriba, en `headlineSmall` negrita. La etiqueta cambia según el paso: «Total estimado» (carrito, sin cotización), «Total» o «Importe a transferir» (QR). Se quitaron el total inferior, el «Importe a transferir» bajo el QR y la fila Total de los datos del cliente | `venta/VentaScreen.kt`, `TextosVenta.etiquetaTotal` |
| Barra inferior con `ShalevBarraAcciones`: ✕ (tonal) y ✓ (relleno), solo iconos, centrados y separados 16dp; el nombre va en TalkBack/tooltip | ídem |
| La tarjeta del comprobante pierde su título (repetía el de la barra superior) | ídem |
| Ficha de Registros: «Total» (venta) o «Importe» (transferencia) arriba y centrado, y fuera de la tabla | `registros/RegistrosScreen.kt` |
| Detalle de turno: «Total vendido» arriba y centrado, fuera de la tarjeta Totales | `registros/TurnoDetalleScreen.kt` |

**2. Centrado**

- `ShalevCard` centra su contenido y el título (por defecto) y activa `LocalShalevCentrado`. Con eso, `ShalevSecondaryText` se centra dentro de tarjetas y ventanas, pero no dentro de las filas (`ShalevListItem` lo desactiva). Lo mismo para el cuerpo de `ShalevVentana`. «Paso X de Y» del `ShalevStepper` va centrado.
- 25 párrafos sueltos y los títulos de sección (Inicio, Precios, «Método de pago» en Venta, «Se mide en», «Receta») van centrados.
- Contadores con acción («N elegidos» + icono en Inventario, Elaboración, Registros y Precios; barra «Continuar» del modo venta) centrados como grupo. `SeleccionVenta.continuar` dice «1 elegido» / «N elegidos».
- Inicio: una alerta sola va centrada y con el mismo ancho que cuando son dos. En la tarjeta del turno, «Desde las…» sigue alineado a la izquierda, junto a su icono.
- Escáner: la fila «Buscando…» va centrada.

**3. Duplicados eliminados**

| Repetición | Solución |
|---|---|
| Total de la venta en 3–4 sitios | Uno solo, arriba (punto 1) |
| Licencia: tarjeta «Costos» con los mismos precios que los chips de tipo | Fuera la tarjeta. Los precios siguen en los chips |
| Licencia: filas Tipo / Vence / ID que repetían el título del estado | `detalle()` deja solo «Emitida» y, con licencia activa, «Tiempo restante» |
| Asistente: título del paso dentro del contenido y otra vez en la cabecera | Solo en la cabecera (`ShalevStepper`) |
| Inicio, «Ganancia Neta»: «Venta» = total de la tarjeta Ventas | Fuera. Quedan Costo y Ganancia |
| Detalle de turno: fila «Abierto/Cerrado · horario» = Apertura + Cierre; «Ventas» y «Movimientos de inventario» = números de las pestañas | Fuera las tres filas |
| Pago electrónico: «En uso» con barra lateral + fondo + etiqueta | Fuera la barra |
| Teléfonos y tarjetas en Perfil, en la pantalla Pago electrónico y en una ventana de Inicio (**opción A** elegida por el usuario) | Solo en la pantalla Pago electrónico, que ahora se abre también desde la tarjeta de Inicio. Perfil queda con nombre, apellidos y carné (subtítulo en Ajustes: «Nombre, apellidos y carné»). Se borran `PagoElectronicoSheet`, `PagoSheetContent`, `BorradorPago`, `abrirBorrador/editarBorrador/guardarBorrador/cerrarBorrador`, `EventoPago.Cerrar` y sus pruebas y capturas (06a, 06b). **Se aparta a propósito de Shalev.txt 119–123** (listas en Perfil). El caso de uso de dominio `IntroducirPagoElectronico` se conserva con sus pruebas, aunque la app ya no lo usa |

**4. Licencia sin identificadores**: no se muestran el ID del dispositivo ni el de la licencia (fuera `idCorto` y el `SelectionContainer`). La solicitud ya los lleva cifrados, así que el usuario no los necesita. El ID del dispositivo solo hace falta para migrar, y se ve (copiable y centrado) en Ajustes → Migrar a otro teléfono; los textos de Migrar apuntan ahí. La lista «Teléfonos de tu Perfil» pasa a «Tus teléfonos», y el resumen dice «De tu Perfil y de Pago electrónico».

**5. Asistente inicial**: cada paso es una columna centrada que ocupa todo el alto, con el mismo espacio entre los elementos y en los bordes (`Equitativo`; si no cabe, 16dp entre ellos y se desplaza). En Bienvenida no hay cabecera, y «Saltar todo» (o «Cerrar») pasa a la barra inferior. Los puntos llevan el icono encima del texto centrado.

**Pruebas:** JVM 224 · dominio/datos 54 · app 303 (se quitan `abrirTransfermovil…` y `introducirNumerosNuevos…`, que probaban funciones eliminadas). Avisos del compilador: 0. Las pruebas instrumentadas compilan. Se añadieron `pagoElectronicoTocarEligeLaTarjeta` y `perfilMuestraSoloDatosPersonales`, y se actualizaron `VentaUiTest` y `LicenciaFormTest`.

## Prompt 29 — Servicios, insumos en Inventario, deslizar, aviso de turno — 0.18.0 (22)

Ocho peticiones. Respuestas del usuario a las dudas: (1) QR solo aviso, (2) tipografía libre parecida a Anurati, (3) insumos con precio de venta opcional y unidades enteras, (4) Tipo libre como las Categorías, venta de servicios separada y pestaña propia en Registros, (5) deslizar solo entre secciones (y entre pestañas dentro de Registros), (8) solo `POST_NOTIFICATIONS`, sin reinicio, sin importes en el texto.

| # | Petición | Resultado |
|---|---|---|
| 1 | «Transfermóvil captura los datos del QR excepto el importe» | **Sin cambios.** El QR que genera Shalev es el de transferencia de persona (`TRANSFERMOVIL_ETECSA,TRANSFERENCIA,<tarjeta>,<móvil>,`), que no lleva importe. Los QR con importe son de comercios registrados en la pasarela. El total sigue arriba y centrado para que el cliente lo escriba |
| 2 | Tipografía «Anurati» en el título | La licencia de Anurati es contradictoria (en unas fuentes es gratuita, en otras solo personal). Se usa **Orbitron** (SIL OFL 1.1), con un estilo geométrico parecido: `designsystem/res/font/orbitron.ttf`, peso 700, en `ShalevMarca`. `ShalevTopBar` la aplica al título «Shalev». La licencia está en `designsystem/src/main/OFL_Orbitron.txt` |
| 3 | Elaboración → Servicios; insumos en Inventario | Fuera la sección Elaboración (paquete `elaboracion/`). Los insumos aparecen en Inventario como la categoría «Insumos» (id negativo para no mezclarse con productos), con ficha, eliminar, el filtro «Insumos» y la acción «Insumo» en el botón +. El formulario de insumo pasa a `app/…/insumos/` y gana «Precio de venta» (opcional): si lo tiene, se vende en unidades enteras. Ningún producto puede usar la categoría «Insumos» (`NO_PERMITIDO`) |
| 4 | Servicios | Nueva sección (icono: mano que recibe, `VolunteerActivism`). Campos: Nombre, Tipo (libre, con sugerencias), Importe, y opcionales Foto y Descripción. Puede gastar insumos («por vez»): muestra «Alcanza para N» y no se vende si no alcanzan. Venta separada: «Nueva venta» → Productos o Servicios. Un carrito no puede mezclar ambos. Registros tiene la pestaña **Servicios** y la exportación incluye la clase de cada línea. Inicio tiene «Servicios: top ventas» y «Servicios: menos vendidos» («1 vez» / «N veces») |
| 5 | Deslizar | `navigation/Deslizar.kt`: un gesto horizontal de 72dp cambia de sección en la barra inferior. Solo funciona en las 5 secciones, y no en formularios ni en la venta. Se ignoran los toques en las franjas de gestos del sistema y los gestos que ya usa un hijo. En Registros cambia de pestaña y, después de la última, pasa a la sección siguiente. `ShalevTabs` se vuelve desplazable con más de 4 pestañas |
| 6 | Barras de navegación de los lanzadores | `consumeWindowInsets(padding)` en 10 pantallas con `Scaffold` + `imePadding` (antes la barra de navegación se contaba dos veces y quedaba un hueco sobre el teclado o los botones). En `MainActivity`, la barra de navegación es transparente en los 3 modos (gestos, 2 y 3 botones), sin el contraste forzado que pinta algunos fabricantes, y hay margen horizontal para el notch en horizontal |
| 7 | Icono transparente | Las PNG `mipmap-*/ic_launcher*` y el fondo adaptativo son transparentes, y solo se ve la figura. Algunos lanzadores (One UI, MIUI) siempre ponen una placa detrás de los iconos adaptativos, y eso Shalev no lo puede evitar. La pantalla de inicio (splash) sigue usando `@color/ic_launcher_background` |
| 8 | Aviso de turno abierto | `notificacion/AvisoTurno.kt`: notificación fija (canal «Turno», importancia baja, sin sonido) con el texto «Turno abierto desde las HH:MM», sin importes. Aparece cuando Shalev pasa a segundo plano o se cierra con el turno abierto, y se quita al volver o al cerrar el turno. Al tocarla, abre Shalev. Si el proceso termina, el aviso desaparece y no se restaura tras reiniciar (no hay `RECEIVE_BOOT_COMPLETED`). El permiso `POST_NOTIFICATIONS` (Android 13+) se pide al abrir un turno. Si se niega, todo funciona igual |

**Datos:** base de datos **v4** (`MIGRACION_3_4`: tablas `servicio` y `servicio_insumo`, columnas `insumo.precioVentaCent` y `detalle_venta.clase`, con `'PRODUCTO'` por defecto). El respaldo sigue en JSON **v3**: añade servicios, la clase de cada línea y el precio de venta de los insumos, todos con valor por defecto, así que los respaldos de la 0.17.x se restauran sin cambios. «Borrar datos» y «Restaurar» incluyen los servicios. Se quitó el filtro de insumos «por uso» (ahora es un filtro de Inventario). Permisos: `CAMERA`, `INTERNET` y `POST_NOTIFICATIONS` (la tarea `shalevPermisos` lo admite).

**Ayuda:** «Primeros pasos» (Servicios y deslizar), «Vender» (Productos/Servicios y el aviso), «Insumos y elaborados» y «Servicios» (nuevos).

**Pruebas:** JVM 232 (+8 `ServiciosTest`) · dominio/datos 54 · app 293. Se quitan 33 pruebas de Elaboración (5 de lógica, 12 del ViewModel y 16 capturas) y 8 instrumentadas. Se añaden 7 en `ServiciosAppTest` y se conservan las 8 de `InsumoFormTest`. Capturas Roborazzi: 142 casos (+5 `ServiciosCapturas`, +1 carrito de servicios). Instrumentadas: 96, solo compiladas. Avisos del compilador: 0.

**Capturas:** `CAPTURAS_P29.pdf` (retirado en la 0.18.1, sustituido por `CAPTURAS_0.18.1.pdf`). Son 20 pantallas en modo claro y oscuro: Nueva venta, indicadores de servicios, Servicios (lista, vacío, ficha, formulario, elegir, venta), insumos en Inventario (acción +, filtro, ficha, formulario), Registros → Servicios, deslizar, permiso y aviso de turno, icono antes y después, y barra de 3 botones. Son maquetas HTML con los mismos tokens y textos que el código, no capturas de un dispositivo. Las de Roborazzi (`ServiciosCapturas`) se generan con `./gradlew :app:recordRoborazziDebug`.

**Limpieza:** a petición del usuario se borró todo lo que había fuera de `Shalev/`: entregas `ENTREGA_*`, documentos de trabajo, capturas y PDF anteriores, archivos subidos y herramientas auxiliares. El proyecto no depende de ninguno de ellos.

## Prompt 30 — icono de Servicios, «+» centrado, cantidades centradas, icono de la notificación — 0.18.1 (23)

| Petición | Cambio | Dónde |
|---|---|---|
| Icono de Servicios sin corazón, solo la mano | `ShalevIcons.Servicios` usa `MaterialSolido.ManoRecibe`: el trazado de «volunteer_activism» sin el corazón y desplazado 4.5 hacia arriba para centrarlo en el cuadro de 24. Afecta a la barra inferior, «Nueva venta» y Registros. Se elimina `VolunteerActivism` | `designsystem/icon/ShalevIcons.kt` |
| Botón «+» centrado abajo | `floatingActionButtonPosition = FabPosition.Center` en Inventario, Servicios y Precios. En `ShalevExpandableFab`, cada acción se reparte en dos mitades de igual peso: el mini botón queda sobre el «+» y la etiqueta a su izquierda | `InventarioScreen`, `ServiciosScreen`, `PreciosScreen`, `component/Overlays.kt` |
| Números centrados en los selectores de cantidad | `ShalevTextField` acepta `textAlign` (opcional, por defecto sin cambio). Se centra en el carrito de la venta (− [n] +) y en los niveles del asistente inicial | `component/TextFields.kt`, `VentaScreen`, `OnboardingScreen` |
| Icono de la notificación = el del lanzador | El icono pequeño es la silueta del icono de Shalev (`drawable-*dpi/ic_stat_shalev.png`, 24dp, generado del `ic_launcher_monochrome`). Android solo usa su transparencia, así que no puede ir en color. El icono grande es el del lanzador en color (`setLargeIcon`), y `setColor` tiñe la cabecera con el primario de marca. Se elimina `drawable/ic_stat_turno.xml` | `notificacion/AvisoTurno.kt`, `res/drawable-*dpi/` |

**Verificación:** sin compilar. A petición del usuario se borraron las herramientas de compilación del entorno de trabajo en el Prompt 29. Son cambios pequeños, que siguen patrones ya compilados en el proyecto, y ninguna prueba depende de ellos. Hay que comprobarlos con `./gradlew shalevCheck` en Android Studio.

**Capturas (0.18.1):** `CAPTURAS_0.18.1.pdf` (retirado en la 0.18.2, sustituido por `CAPTURAS_0.18.2.pdf`). Son 21 pantallas en claro y oscuro, con un anillo naranja discontinuo en lo que cambió: icono de Servicios (solo la mano), «+» centrado en Servicios, Inventario (abierto y cerrado) y Precios, números centrados en «Cantidad» y en los niveles del asistente, y aviso de turno con el icono de Shalev. Son maquetas HTML con los tokens, textos, iconos e ilustraciones del código, no capturas de un dispositivo: el código 0.18.1 aún no se ha compilado.

## Prompt 31 — icono de insumo, aviso sin icono repetido, «+» de Inventario, insumos como categoría — 0.18.2 (24)

**Pedido:** (1) icono de insumo = un saco o bolsa; (2) el icono de la notificación sale duplicado; (3) en Inventario, Escanear, Escribir y Cancelar se ven debajo de la tabla y sin su texto; (4) «Insumo» es una categoría, no un botón.

**Cambios:**
1. `ShalevIcons.Insumo` = `MaterialSolido.Saco`, un saco atado dibujado a mano en 24×24 (Material no trae ninguno). Se retira `LocalPizza`, que ya no se usaba. Al quitar el botón «Insumo», el saco se muestra en la opción «Insumos» de Categoría (formulario de producto) y en el chip «Insumos» del filtro de Inventario.
2. `AvisoTurno`: se quita `setLargeIcon` (y `iconoLanzador()`). Android ya pinta el icono pequeño (silueta del lanzador, teñida con `#2B4FA3`) en la cabecera; con el grande a la derecha se veía dos veces.
3. `ShalevExpandableFab`: cada acción es `FilaAccionFab`, un `Layout` propio con ancho simétrico respecto al mini botón (etiqueta a la izquierda). Sustituye a `Row(fillMaxWidth)` + `weight`, que dependía de que el hueco del FAB del Scaffold tuviera ancho acotado; si no, la etiqueta quedaba en 0 y solo se veían los iconos. `InventarioContent` añade un velo (`scrim` al 32 %) sobre la tabla mientras el «+» está abierto: las acciones se leen encima de la tabla, y tocar fuera o Atrás lo cierra.
4. Insumos como categoría:
   - Se quita la acción «Insumo» del «+» y de la hoja Agregar (con `TextosInventario.INSUMO`, `InventarioTags.OPCION_INSUMO`, `onAgregarInsumo` y `InventarioViewModel.agregarInsumo`).
   - En el formulario de un producto **nuevo**, «Insumos» es la primera opción de Categoría. Elegirla (o escribirla) emite `EventoForm.EsInsumo(nombre)` y se pasa al formulario de insumo (`Route.InsumoForm(nombre = …)`, sustituyendo al de producto) con el nombre ya escrito.
   - Al **editar** un producto sigue siendo una categoría reservada (regla del dominio sin cambios).
   - Con el filtro «Insumos» puesto, «+ → Escribir» abre directamente el formulario de insumo.
   - Al guardar un insumo se vuelve siempre a Inventario, como con los productos.
5. Textos: ayuda «Insumos y elaborados» (5 pasos), MANUAL §5 y §2, README, DESIGN_SYSTEM. Versión 0.18.2 (24).

**Pruebas nuevas:** `ProductoFormTest` (categoría Insumos en nuevo → evento; al editar → reservada), `InsumoFormTest` (nombre recibido), `InventarioViewModelTest` (Escribir con filtro Insumos → `InsumoForm`).

**Sin verificar:** el código no se ha compilado (no hay toolchain en el entorno de trabajo); compílalo y pasa las pruebas en Android Studio. Lo que se pierde al pasar del formulario de producto al de insumo: foto, código y demás campos ya rellenados (solo se conserva el nombre; los insumos no tienen código ni foto).

**Capturas (0.18.2):** `CAPTURAS_0.18.2.pdf` (retirado en la 0.18.4, sustituido por `CAPTURAS_0.18.4.pdf`). Son 24 pantallas en claro y oscuro: las 21 anteriores al día, más el formulario de producto con «Insumos» como primera categoría, el formulario de insumo con el nombre ya puesto y el filtro de Inventario con el chip del saco. Los cambios de la 0.18.2 llevan un anillo naranja discontinuo. Al montarlas se vio que el desplegable de Categoría desalinearía los textos (solo «Insumos» llevaba icono): las demás opciones reservan ahora el mismo hueco. Son maquetas HTML, no capturas de un dispositivo.

## Prompt 32 — sin icono de insumo, nombre del servicio en Registros, icono de Guardar — 0.18.3 (25)

**Pedido:** eliminar el icono de insumo; mostrar el nombre del servicio en Registros; el icono de Guardar es igual al de Confirmar.

**Cambios:**
1. **Sin icono de insumo:** se quitan `ShalevIcons.Insumo` y el trazado `Saco`. El chip «Insumos» del filtro y la opción «Insumos» de Categoría quedan solo con texto, como las demás.
2. **Nombre del servicio en Registros → Servicios:** nueva `subtituloFila(v: Venta)`. Cada fila sigue titulada con la fecha (regla P25) y debajo lleva el servicio: «Corte de pelo», «Corte de pelo y Lavado» o «Corte de pelo, Lavado y 2 más». Las ventas de productos no llevan subtítulo.
3. **Guardar ≠ Confirmar:** `ShalevIcons.Guardar` vuelve a ser el disquete (`Save`, Material) en lugar del ✓; revierte la decisión P25. Afecta a los formularios de producto, insumo, servicio, Perfil, Pago electrónico, Precios y escáner, y al diálogo «Consultas en internet» de Ajustes (`confirmIcon = ShalevIcons.Guardar`). Los diálogos que confirman una acción siguen con ✓.
4. Textos: MANUAL (§ botones, insumos, servicios), PRUEBAS_DISPOSITIVO y DESIGN_SYSTEM. Versión 0.18.3 (25).

**Pruebas nuevas:** `RegistrosLogicTest.ventaDeServiciosMuestraElNombreDelServicio`.

**Sin verificar:** el código no se ha compilado (no hay toolchain en el entorno de trabajo); compílalo y pasa las pruebas en Android Studio. El PDF `CAPTURAS_0.18.2.pdf` no muestra estos cambios.

## Prompt 33 — Guardar vuelve a ✓; Registros → Servicios muestra la cantidad — 0.18.4 (26)

**Pedido:** «Guardar tiene que ser = ✓»; en Registros, la cantidad de servicios en vez del nombre (los nombres se ven al abrir la información de esa fecha).

**Cambios:**
1. `ShalevIcons.Guardar` = `ShalevIcons.Confirmar` (✓) otra vez (decisión P25 confirmada). Se retira el trazado `Save` y el `confirmIcon` del diálogo «Consultas en internet». Se deshace el punto 3 del Prompt 32.
2. `subtituloFila(v: Venta)`: en ventas de servicios, «1 servicio» / «N servicios» (suma de cantidades: 2 × Lavado cuenta 2). La ficha de la fecha ya enumera cada servicio («2 × Lavado y peinado»). Las ventas de productos siguen sin subtítulo.
3. MANUAL, PRUEBAS_DISPOSITIVO y DESIGN_SYSTEM ajustados. Versión 0.18.4 (26).

**Pruebas:** `RegistrosLogicTest.ventaDeServiciosMuestraCuantosServicios` (sustituye a la del nombre).

**Sin verificar:** sin compilar (no hay toolchain en el entorno de trabajo).

**Capturas (0.18.4):** `CAPTURAS_0.18.4.pdf`, junto a la carpeta del proyecto. Son 24 pantallas en claro y oscuro, más una portada con la lista de cambios. «Insumos» aparece como texto, sin icono, en el desplegable de Categoría y en el filtro. Registros → Servicios muestra «2 servicios» o «1 servicio» bajo cada fecha, y la ficha de la fecha enumera los servicios (anillo naranja en ambas). Guardar = ✓. Son maquetas HTML hechas con los tokens, textos, iconos e ilustraciones del código, no capturas de un dispositivo, y el código 0.18.4 aún no se ha compilado.


## Prompt 35 — Apps Principal y Secundaria — 0.19.0 (27)

**Pedido:** dos tipos de app, definidos en Ajustes:

- **Principal:** la del gerente, administrador o dueño. Tiene la base de datos general y activa la licencia.
- **Secundarias:** las de los empleados. Se vinculan a la principal con un QR cifrado y sincronizan en tiempo real o cuando el usuario decida.

La sección va en Ajustes/Sistema, debajo de Licencia.

**Respuestas del usuario:**

- Transporte: **solo red local** (wifi del local o zona wifi del teléfono principal).
- Permisos configurables por empleado.
- Sin conexión sigue vendiendo hasta cerrar el turno, pero no puede empezar uno nuevo sin sincronizar.
- Secundarias cubiertas por la licencia de la principal, con un máximo de 5.

**Cambios:**

1. **Dominio:** se añaden:
   - `Vinculacion.kt` (tipos, permisos, licencia de la principal, reglas).
   - `VinculacionRepositories.kt` y `PuertaTurno`, que usa `AbrirTurno`.
   - `Turno.empleadoId`.
   - Los errores `SinPermiso`, `SinPrincipal` y `VinculacionRechazada`.
2. **BD v5** (`MIGRACION_4_5`):
   - `uuid`, `empleadoId` y `sincronizado` en `turno` y `venta`.
   - Nueva tabla `empleado`.
   - El turno activo filtra `empleadoId IS NULL`.
3. **`data/sync`:**
   - Protocolo, canal cifrado (ECDH P-256 + HKDF + AES-GCM con secuencia), `ConfigSync`, `RedLocal` (NSD).
   - `AlmacenSync`, `ServidorSync`, `EjecutorComandos`, `ClienteSync`.
   - Decoradores `…SegunTipo`, implementaciones de repositorios y `ArranqueSync` (en `ShalevApplication`).
4. **UI:**
   - Ajustes → **Apps vinculadas**, debajo de Licencia. En una secundaria se ocultan Configurar, Perfil, Licencia, Pago electrónico, Avisos, Respaldo y Migrar, y Precios si no tiene ese permiso.
   - `VinculacionScreen`: lista, agregar con QR, permisos, nuevo código, quitar, usar como secundaria, sincronizar, modo y desvincular.
   - Escáner en modo `VINCULAR`.
   - `RootState.BloqueoSecundaria`.
   - `LocalPermisosApp` oculta exportar y editar según los permisos.
   - Mensajes `mensajeSync` en los formularios, en Inicio, en Venta y en Precios.
5. **Docs:**
   - Nuevos: `docs/VINCULACION.md`, MANUAL §10, PRUEBAS_DISPOSITIVO §4.10 y el tema de Ayuda «Apps de tus empleados».
   - Actualizados: README (BD v5) y SECURITY (red local).
   - Versión 0.19.0 (27).

**Sin permisos nuevos:** `INTERNET` cubre los sockets locales; no hay servicio en primer plano. Es una suposición documentada: la principal atiende mientras la app está abierta.

**Pruebas nuevas:**

- `SyncProtocoloTest` (data):
  - QR de ida y vuelta.
  - La clave ECDH coincide en las dos apps.
  - El canal en orden, y rechazo de mensajes alterados, repetidos, reflejados o con otra clave.
  - Licencia de la principal.
  - Errores remotos.
  - Permisos por acción.
  - La clave no aparece en `toString`.
- `PuertaTurnoTest` (domain).
- `VinculacionLogicTest` (app).
- `RootStateTest.secundariaUsaLaLicenciaDeLaPrincipalYNoTieneOnboarding`.
- Fakes `TipoRepo`, `PrincipalRepoFake` y `SecundariaRepoFake`.

**Sin verificar:**

- Nada se ha compilado (no hay toolchain).
- La sincronización entre dos teléfonos reales se prueba con PRUEBAS_DISPOSITIVO §4.10.
- `5.json` del esquema se genera en el primer build.

> **Nota (0.19.1):** las tres líneas anteriores quedan superadas. Desde el Prompt 36 todo se compila y se verifica con `tools/verificacion/verificar.sh`, y `5.json` ya está versionado.

## Prompt 36 — Verificación sin Gradle y correcciones de la vinculación — 0.19.1 (28)

**Petición:** aplicar las sugerencias de la entrega anterior y proponer soluciones para las limitaciones.

**Verificación (nueva):** `tools/verificacion/verificar.sh` + `resolver_maven.py`. Solo necesitan Linux, bash, python3, curl y unzip; ni Gradle ni Android SDK. El script:
1. Descarga JDK 17, kotlinc 2.0.21, KSP y las dependencias de Maven Central/Google en `~/.cache/spvi-tc`. Se resuelven app y data juntos, con una sola versión por librería.
2. Compila los 6 módulos (el plugin de Compose solo en designsystem/app).
3. Ejecuta Room 2.6.1 por KSP, que valida las consultas y compara el esquema con `5.json`.
4. Ejecuta Hilt 2.52 por KSP, que valida el grafo y compila el Java generado.
5. Aplica `MIGRACION_4_5` en SQLite sobre el esquema v4 y lo compara con `5.json`.
6. Pasa los tests JVM: core 14, licencia 59, domain 162, data 65, designsystem 18, app 285 (**603**).
7. Compila las capturas Roborazzi y los dos juegos de tests instrumentados.

**Errores que solo aparecían al compilar (corregidos):**
- `EscanerUseCases`: smart cast entre módulos.
- `RootViewModel`: tipos explícitos en `flowOf(null)`.
- `TurnoPersistenciaTest` y `EsquemaTest` actualizados a v5 (con una prueba nueva de la migración 4→5).
- `EntornoIntegracion` actualizado por el repositorio de tipo de app.

**Correcciones funcionales:**
- **Permisos en las fichas:** sin «Exportar», las fichas individuales (producto, insumo, servicio, registro) no muestran Compartir. Sin «Editar inventario», no muestran Editar ni Eliminar.
- **Respaldo:** turnos y ventas guardan `uuid`, `empleadoId` y `sincronizado` (campos opcionales; compatible en los dos sentidos).
  - La validación admite un turno abierto **por app** (antes rechazaba los respaldos de una principal con secundarias en turno) y exige `uuid` sin repetir.
  - Prueba nueva en `DtoRoundTripTest`.
- **Red:** el aviso de red de «Apps vinculadas» abre los ajustes de zona wifi (principal) o de wifi (secundaria sin conexión). Lo hace `common/AjustesRed.kt`, que si falta una pantalla usa otra del sistema. Sin permisos nuevos.

**Pendiente con propuesta:**
- Servicio en primer plano para la principal. Necesita permisos nuevos y está a la espera de autorización: ver docs/VINCULACION.md §Limitaciones.
- Gradle, lint, R8 y los tests en dispositivo (REVISION_FINAL L1/L2/L11/L13/L14).

## Prompt 37 — Servicio en primer plano de la principal — 0.19.2 (29)

**Petición:** «A», es decir, la opción A de la entrega anterior: servicio en primer plano para la principal, con los permisos autorizados.

**Hecho:**
- **Permisos** (normales, sin diálogo): `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE`. Añadidos al manifiesto y a la lista de `shalevPermisos`. `verificar.sh` incluye un paso 8 que compara el manifiesto de `:app` con esa lista.
- **`ServicioSync`** (`app/notificacion`):
  - servicio `connectedDevice`, no exportado y `START_NOT_STICKY`;
  - no hace trabajo propio: solo mantiene vivo el proceso donde corre `ServidorSync`.
- **`PlanAviso`** (puro, con `PlanAvisoTest`):
  - El servicio corre si la app es principal con secundarias **y** está a la vista **o** hay algún turno abierto.
  - Notificación única (ID 29): con servicio, siempre; sin servicio, como en P29.
  - Textos: «Turno abierto desde las HH:MM · N apps conectadas», «1 app conectada», «Esperando a tus apps vinculadas».
- **`AvisoTurno`** aplica el plan:
  - Arranca el servicio (Android 12+ puede negarlo en segundo plano; se reintenta con el siguiente cambio de estado).
  - Lo para solo cuando ya está en primer plano, para evitar el fallo de `startForegroundService`.
  - Al terminar, el servicio suelta la notificación con `STOP_FOREGROUND_DETACH` y `AvisoTurno` la actualiza o la quita, sin carreras.
- **Datos:** `SyncDao.observarTurnosAbiertos()` y `ArranqueSync.atencion` (principal con secundarias, turnos abiertos, secundarias conectadas).

**Sin verificar:** el comportamiento real en teléfonos (PRUEBAS_DISPOSITIVO §4.10 paso 14), en especial con el ahorro de batería de cada fabricante.

## Prompt 38 — La principal también hace todas las funciones — 0.19.3 (30)

**Petición:** «la app principal también debe realizar todas las funciones, no solo funcionar como monitor de las secundarias».

**Revisión:** ya era así desde 0.19.0:
- `PermisosApp.PRINCIPAL` incluye todos los permisos.
- Los repositorios «según tipo» trabajan en local en la principal.
- El turno activo de la principal excluye los de las secundarias.
- Ninguna pantalla se oculta en la principal.

**Fallo encontrado y corregido:** los cambios de stock que una secundaria pide a la principal (ajustar existencias, editar la cantidad, alta o baja) se asociaban al **turno abierto de la principal** y entraban en el resumen de su cierre.
- Ahora `EjecutorComandos` ejecuta con `OrigenMovimiento(empleadoId)` y `turnoParaMovimiento()` los asocia al turno abierto de esa secundaria, o a ninguno.
- Consulta nueva: `TurnoDao.activoDeEmpleado`.

**Pruebas:**
- `PuertaTurnoTest.laPrincipalLoPuedeTodo` (JVM).
- `TurnoRepositoryInstrumentedTest.laPrincipalVendeConSuPropioTurnoAunqueHayaSecundariasEnTurno` (instrumentado, compilado). Abre el turno propio con el de una secundaria abierto, vende, ajusta y cierra. Comprueba que el cierre solo cuenta lo propio y que el turno de la secundaria sigue abierto.
- PRUEBAS_DISPOSITIVO §4.10, paso 15.

**Documentación:** MANUAL §10 (la principal sigue haciendo todo y su turno es solo suyo) y VINCULACION.

## Prompt 44 — Capturas 0.19.3

**Capturas:** `CAPTURAS_0.19.3.pdf` sustituye a `CAPTURAS_0.18.4.pdf`. Tiene 38 páginas: la portada, los nº 1–24 de la 0.18.4 y 13 maquetas nuevas en claro y oscuro (nº 25–37). Las nuevas son:
- Ajustes con «Apps vinculadas», en la principal y en la secundaria;
- Apps vinculadas vacía y con dos empleados;
- Agregar empleado con sus permisos;
- el QR de un solo uso;
- Editar empleado y Quitar;
- «Usar esta app como secundaria»;
- la secundaria conectada y sin conexión;
- el bloqueo por licencia de la principal;
- el aviso «Turno abierto desde las 09:30 · 2 apps conectadas».

Los generadores están en `tools/capturas/` (ver CAPTURAS.md). Son maquetas HTML, no capturas de un dispositivo. No cambia el código de la app.

**Observación al maquetar:** `ShalevStatusBanner` con tono Info usa el icono de Licencia (llave). Por eso «Red local activa» y «Conectada con la app principal» llevan una llave. Se deja como está en el código, pendiente de decisión.

## Prompt 45 — 0.20.0 (31): integración de las apps vinculadas

**Pedido:** «procede con la 0.20.0 y actualiza el pdf». Se aplica el plan aprobado de `docs/ANALISIS_IMPACTO_0.19.md` §6 (decisiones de P42 y P43).

**Cambios:**
- **H1 Vendedor:** `Venta.vendedor` (quien abrió el turno). Aparece en la ficha de la venta (no en el texto compartido), en la columna «Vendedor» de Ventas en Excel/PDF y en el filtro «Vendedor» de Registros, que solo sale con 2 o más personas. `FiltrosGuardados` guarda ahora 7 valores y sigue leyendo los de 6.
- **H3:** en una secundaria, el texto bajo «Pegar SMS» explica que el SMS llega al dueño y que el empleado debe escribir el nº.
- **H4 Cobro por empleado:** el dueño elige en la ficha del empleado su tarjeta y su teléfono de cobro. Los combos solo aparecen si tiene más de uno. La secundaria recibe solo esos dos (`Perfil.paraEmpleado`).
- **H5 Pedir cierre:**
  - En la principal, la fila y la ficha del empleado muestran «Turno abierto desde las HH:mm» y el botón «Pedir cierre del turno», con confirmación.
  - El cierre viaja en `SincronizarOk.cerrarTurno`, un campo opcional y compatible con la 0.19.x.
  - La secundaria lo aplica con `CierreRemoto` cuando no hay venta en curso (`SesionVenta`, P43).
  - Mientras está pedido: aviso en Inicio y en la venta, y no se pueden empezar ventas nuevas.
  - Tras cerrarse: aviso descartable.
- **H6:** alerta «Existencias negativas» para productos e insumos por debajo de 0, en Inicio y en el filtro del Inventario.
- **H7:**
  - El respaldo lleva `empleados[]`, sin claves ni QR; al restaurar aparecen «Sin vincular».
  - Migrar → paso 2 avisa de sincronizar antes las apps de los empleados.
- **Ayuda:** «Apps de tus empleados» tiene un 5.º paso.
- **Base de datos v6:** `MIGRACION_5_6` añade `tarjetaId`, `telefonoId` y `cierreSolicitadoEn` a `empleado`. Se exportó `data/schemas/.../6.json`.

**Tests:**
- Nuevos: `Version020Test` (domain, 8) y `Version020SyncTest` (data, 6).
- Ampliados: `TablasExportTest`, `FiltrosGuardadosTest`, `InicioLogicTest` y `VinculacionLogicTest`, más casos nuevos en `VentaViewModelTest` (el cierre espera a la venta) e `InicioViewModelTest`.
- Instrumentados, solo compilados: `EsquemaTest` v6, con su migración 5→6.
- Capturas Roborazzi nuevas: `01g`–`01i`, `04o2`, `04i2` y `VinculacionCapturas` (`07p`–`07s`).
- `verificar.sh`: **627 tests JVM correctos** (core 14, licencia 59, domain 171, data 71, designsystem 18, app 294).
- Los pasos 7–8 (compilación de capturas e instrumentados, y permisos) se terminaron en una segunda pasada sobre las mismas salidas, porque el entorno (2 GB) cortaba el proceso largo. Resultado: «Todo correcto».

**Capturas:** `CAPTURAS_0.20.0.pdf` (48 páginas) sustituye a `CAPTURAS_0.19.3.pdf`.
- Portada nueva.
- Los nº 27 y 30 se actualizaron.
- 10 maquetas nuevas (nº 38–47).
- `tools/capturas/generar.py` ganó los componentes necesarios (combo, chips, tarjeta de turno, alertas, ficha).

**Suposiciones:**
- La garantía de «nunca durante una venta» deja una ventana de milisegundos entre la comprobación y el cierre. Si coincide, el turno cerrado bloquea el «Confirmar» y el carrito se conserva.
- El nombre del vendedor es el `abiertoPor` del turno: si el empleado cambia de nombre, las ventas antiguas conservan el anterior.

**Pendiente:** probar en dos teléfonos reales (PRUEBAS_DISPOSITIVO §4.10, pasos 16–20).

## Prompt 46 — Plan 0.21.0 (propuesta)
Respuestas sobre «Consultas de códigos en internet» y sobre los botones del bloqueo de la secundaria, y el plan `docs/PLAN_0.21.md` (C1–C13). Sin cambios de código.

## Prompt 47 — Precios por secundaria y prompt para GL
- Importe por cada app secundaria: Mensual 1 000, Semestral 5 000, Anual 9 000 y Perpetua 17 000 CUP (`PLAN_0.21.md`).
- `docs/GL_PROMPT_0.21.md`: prompt para opencode desktop que actualiza GL. Añade el campo opcional `secundarias` a la solicitud y a la licencia, sin cambiar el contrato v1 ni el envelope, junto con el precio, el historial y el ID fuera del JSON.
- `tools/licencia/probar_gl.py`: genera la solicitud de prueba (`docs/GL_SOLICITUD_PRUEBA.txt`, MENSUAL con 2 secundarias = 8 000 CUP, dispositivo de `GoldenVectorTest`) y verifica la licencia que devuelva GL como lo hará Shalev. `autoprueba` hace la ida y vuelta con un GL simulado (correcta).
- Suposición: el máximo es 10 secundarias por licencia (constante, fácil de cambiar).

## Prompt 48 — 0.21.0 (32): plan C1–C13
Implementado todo `docs/PLAN_0.21.md` (detalle técnico en `docs/VINCULACION.md` §0.21.0).
- **Licencia (C4/C5):** campo opcional `secundarias` (0–10; sin campo = 5; prueba = 5) en solicitud y licencia; precio = base + n × importe por secundaria; selector en Licencia; una sola principal.
- **Turno (C6):** el empleado solo solicita el cierre; el dueño aprueba (reutiliza `cerrarTurno`, nunca durante una venta) o rechaza. Nada se cierra solo.
- **Teléfono (C2), permisos (C3), Registros (C7/C8), Inicio de la secundaria (C9), filtros (C10), miniaturas (C11), recorrido y módulos (C12), menú + vertical (C13), «Sin existencia» (C1).**
- **Base de datos v7:** `MIGRACION_6_7`, esquema `7.json`.
- **Tests:** nuevos `Version021Test` (domain, 6), `FiltroEntradaTest` (designsystem, 4), `Version021AppTest` (app, 5) e `InicioViewModelTest.secundariaSoloSolicitaElCierre`. `verificar.sh` completo: **644 tests JVM correctos** (core 14, licencia 59, domain 177, data 71, designsystem 22, app 301), «Todo correcto».
- **Después de la verificación**, solo textos: la Ayuda «Apps de tus empleados» se actualizó (5 pasos, ≤ 110 caracteres comprobados). Es un cambio de literales: no se volvió a compilar porque el entorno perdió las herramientas.
- **Docs:** LICENSE_CLIENT, MANUAL_USUARIO, FORMATOS, README, RELEASE, PRUEBAS_DISPOSITIVO (pasos 21–28), REVISION_FINAL, CAPTURAS, VINCULACION.
- **Capturas:** `CAPTURAS_0.21.0.pdf` (60 páginas) sustituye a `CAPTURAS_0.20.0.pdf`; 12 maquetas nuevas (nº 48–59) y nº 28, 30, 45 y 46 actualizados.
- **Pendiente:** GL con `secundarias` (`docs/GL_PROMPT_0.21.md`) y prueba en dos teléfonos reales.

## Prompt 49 — 0.21.1 (33): colores H1–H5
Revisión de la paleta (`docs/PROPUESTA_COLOR.md`) y aplicación de H1–H5:
- **H1:** `L_OUTLINE` `#8A8F96` → `#767B82`: bordes de campos ≥ 3:1 también en diálogos (antes 2.78:1).
- **H2:** `IconActionStyle.Tonal` y mini botones del menú + en `tertiaryContainer`; el naranja (`secondaryContainer`) queda solo para avisos. Desviación documentada: no gris neutro (desaparecía sobre los diálogos).
- **H3:** insumo crítico claro `#B45309` → `#9A3412` (antes igual que el naranja de marca).
- **H4:** `AlertTone.SinExistencia` + `CardTone.Error`: el contador «Sin existencia» va relleno.
- **H5:** paleta de gráficos sin morado/verde/amarillo de las alertas (magenta y oliva; en oscuro, petróleo, rosa y oliva).
- **Tests:** `ContrastTest` amplía `uiComponents` (outline en diálogos, «Sin existencia») y añade `alertasDistintasDeMarcaYGraficos`; `InicioLogicTest` comprueba el tono nuevo. `verificar.sh`: **645 tests JVM correctos** (core 14, licencia 59, domain 177, data 71, designsystem 23, app 301), «Todo correcto». Incluye la Ayuda «Apps de tus empleados» del Prompt 48, ya compilada.
- **Docs:** DESIGN_SYSTEM (roles y contrastes), PROPUESTA_COLOR (estado), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL, CAPTURAS.
- **Capturas:** `CAPTURAS_0.21.1.pdf` (60 páginas) sustituye a `CAPTURAS_0.21.0.pdf`; nº 25–59 rehechos con la paleta nueva (nº 55 con la dona de Inventario). Los nº 1–24 (0.18.4) conservan la paleta anterior.


## Prompt 50 — 0.21.2 (34): H6, «Insumo bajo» sin verde
- «Stock insumo bajo» pasa del verde de Shalev.txt (`#3D7A1F` / `#AED581`, que se confundía con `success`) al azul petróleo del rol terciario (`#2F6F80` / `#7FC4D4`). Cambio pedido por el usuario.
- El tercer color de los gráficos pasa a caramelo (`#8B5E34` / `#D4A373`, ≥ 5:1 sobre las cards) para no repetir el tono de una alerta.
- `ContrastTest.alertasDistintasDeMarcaYGraficos` comprueba además que insumo bajo ≠ éxito y = terciario. `verificar.sh`: **645 tests JVM correctos**, «Todo correcto».
- Docs: DESIGN_SYSTEM, PROPUESTA_COLOR (H6 aplicado; H7 no), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL, CAPTURAS.
- Capturas: `CAPTURAS_0.21.2.pdf` (60 páginas) sustituye a `CAPTURAS_0.21.1.pdf`; el nº 45 muestra «Stock insumo bajo» y el nº 55 la dona con caramelo.

## Prompt 51 — 0.21.3 (35): H7, tema oscuro con tinte de marca
- Los grises del tema oscuro llevan un toque del azul de marca, con la misma escala de luminancia: fondo `#0F1318` (antes `#121212`), surface `#1A1F26` (`#1E1E1E`), containers `#14181E` / `#1F252D` / `#242A33` / `#2A313B` / `#313944`, surfaceVariant `#262C35`, outline `#8C939E`, outlineVariant `#363E4A`, onSurfaceVariant `#B3B9C2`; fondo de ventana (`values-night/colors.xml`) `#0F1318`.
- Contrastes oscuros: onSurface 13.27, onSurfaceVariant 8.39, secundario 65 % ≥ 5.43, outline ≥ 3.8 (también en diálogos), alertas y gráficos ≥ 4.9.
- Test nuevo `ContrastTest.oscuroConTinteDeMarca` (azul > rojo en toda la escala, la elevación sube de luminancia, outline sobre `Highest`). `verificar.sh`: **646 tests JVM correctos** (designsystem 24), «Todo correcto».
- Docs: DESIGN_SYSTEM, PROPUESTA_COLOR (todo aplicado), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL, CAPTURAS.
- Capturas: `CAPTURAS_0.21.3.pdf` (60 páginas) sustituye a `CAPTURAS_0.21.2.pdf`; columna «Oscuro» de los nº 25–59 rehecha.

## Prompt 52 — prueba del escáner con códigos reales (sin cambio de versión)
- 44 códigos públicos (OFF/OBF/OPF/UPCitemdb, Cuba 850, UPC-A, UPC-E, EAN-8, GTIN-14, ISBN, entradas manuales e inválidos) contra las bases en vivo con el código real de dominio/datos (`tools/escaner/ProbarCodigos.kt`, `probar.sh`) y 13 flujos completos escáner → formulario → guardar con los ViewModels reales (`tools/escaner/FlujoEnVivoTest.kt`, `probar_flujo.sh`). Fuera de `verificar.sh` (necesitan internet).
- Resultado: 35 encontrados, 2 no encontrados, 7 no consultables; 12/13 flujos guardan. Hallazgos E1–E10 y propuestas P1–P10 en `docs/PRUEBA_ESCANER.md` (destacan: UPC-E rechazado, códigos con guion que no se reconocen al escanear, fichas de OFF de 154 KB sin `fields`). Código de la app sin cambios: queda pendiente de confirmación.

## Prompt 53 — 0.21.4 (36): arreglos del escáner y prueba repetida
- Aplicadas P1–P10 de `docs/PRUEBA_ESCANER.md`:
  - **UPC-E:** se valida y se consulta como UPC-A; se guarda tal como se leyó.
  - **Guiones:** `normalizar` solo quita guiones en códigos numéricos; `ConsultarCodigo` prueba exacto y sin guiones; el formulario guarda normalizado.
  - **Open Facts:** `fields=product_name,image_url`.
  - **Nombres:** `NombreEnLinea.limpiar` (entidades, espacios, ASIN, corte en palabra), también en el formulario.
  - **Fotos:** http → https y hasta 3 imágenes de UPCitemdb (`ProductoEnLinea.imagenesAlternativas`) dentro de 5 s.
  - **Códigos no comerciales:** prefijos GS1 restringidos y todo ceros no salen a internet.
  - **Filtro de código:** `FiltroEntrada.CODIGO` con 64 caracteres y «_».
  - **QR o URL en modo Producto:** aviso `CODIGO_NO_VALIDO` y formulario sin código.
  - **Nombre antes que la foto:** «Guardar» espera la foto como mucho 5 s; texto «Descargando la imagen…».
  - **User-Agent:** «Shalev/0.21».
- `verificar.sh`: **662 tests JVM correctos** (domain 186, data 72, app 307), «Todo correcto».
- **Prueba en vivo repetida:**
  - UPC-E encontrado; balanza y todo ceros ya no salen a internet;
  - fotos 30/35 (antes 24/35), UPCitemdb 6/10 (antes 1/10); OFF 0,2 KB por consulta (antes hasta 154 KB);
  - 13/13 flujos guardan (antes 12/13).
- Docs: PRUEBA_ESCANER §4–§5, README, RELEASE, PRUEBAS_DISPOSITIVO (casos nuevos en §4.6), REVISION_FINAL.

## Prompt 54 — 0.21.5 (37): ambigüedad EAN-8 / UPC-E y capturas del escáner
- **Dominio:** `SimbologiaCodigo { EAN_8, UPC_E, OTRA }`; `CodigoBarras.candidatosConsulta(codigo, simbologia)` (con simbología, una lectura; sin ella y válido de las dos maneras, EAN-8 y luego UPC-A); `paraConsulta`/`esConsultable` usan el primer candidato; `esAmbiguo`; `completarUpcE` para UPC-E de 6 dígitos.
- **`BuscarProductoEnLinea(codigo, simbologia)`:** recorre la cadena por cada candidato; devuelve el primer «Encontrado»; «Sin conexión» corta; si alguno dio «No encontrado» → `NoEncontrado` (fuentes fallidas acumuladas), si no → `ServicioNoDisponible`.
- **App:** `CamaraEscaner` entrega `Barcode.format` → `SimbologiaCodigo`; `EscanerViewModel.codigoDetectado(raw, simbologia)` la guarda en el estado (sirve para Reintentar y tras el permiso de internet). La búsqueda manual no lleva simbología.
- Suposición documentada: un EAN-8 con prefijo 0 o 2 sigue sin consultarse aunque ML Kit diga EAN-8 (uso restringido GS1).
- Tests nuevos: 4 (dominio 2, app 2). `verificar.sh`: **666 tests JVM correctos** (domain 188, app 309), «Todo correcto».
- Capturas: nº 60–62 del escáner en `tools/capturas/generar.py`; portada 0.21.5; `CAPTURAS_0.21.5.pdf` (63 páginas) sustituye a `CAPTURAS_0.21.3.pdf`.
- Docs: PRUEBA_ESCANER (limitación → resuelta), README, RELEASE, PRUEBAS_DISPOSITIVO §4.6, REVISION_FINAL, CAPTURAS.

## Prompt 55 — 0.21.6 (38): depuración exhaustiva
- Petición: «Realiza un debugging y depuración exhaustiva del proyecto». Opción elegida: corregir lo claro con tests y dejar lo dudoso como propuesta. Informe completo en `docs/DEPURACION_0.21.6.md`.
- Línea base: `verificar.sh` sobre 0.21.5 (666 tests) + detekt sin Gradle + lectura dirigida (corrutinas, fechas, SQL, concurrencia ficha/ventas, protocolo, claves en memoria, Compose).
- **F1** `FiltrosEntrada.decimal()`: la coma se escribe como punto. **F2** `core.result.runCatchingCancelable` (relanza `CancellationException`) en escáner, formularios, inventario, licencia y avisos del servidor. **F4** `Estadisticas.rango/serie` e `InicioUseCases`: días y meses por fecha local (el cambio de hora de Cuba es a las 00:00). **F5** búsqueda literal en Registros (`escaparLike` + `ESCAPE '\'`). **F6** `ClienteSync.pedir`: sin respuesta = `IOException`, no cancelación. **F7** `sumarStock`: sumar siempre se permite. **F8** la ficha abierta no deshace ventas: `Stock.cantidadAlGuardar(leida, enFormulario, actual)` dentro de la transacción; sobrecargas `actualizar(…, cantidadLeida)` en los repositorios (también en `…SegunTipo`); campos opcionales `GuardarProducto.cantidadLeida` / `GuardarInsumo.cantidadLeidaMil` (compatibles con 0.21.5). **F3** clave de `ClienteSync.abrir` borrada en `finally`; `rememberUpdatedState` en `VinculacionScreen`.
- Propuestas sin aplicar: P-a `stopSelf` tras fallo de `startForeground`; P-b reintento de `CierreRemoto`; P-c separar «Caducado» de «Próximo a caducar»; P-d tope de sockets en `ServidorSync`; P-e `Mutex` en abrir/cerrar sesión del cliente.
- Tests nuevos: 6 (core 2, domain 2, data 2) + aserción nueva en `FiltroEntradaTest`. `verificar.sh`: **672 tests JVM correctos** (core 16, licencia 59, domain 190, data 74, designsystem 24, app 309), «Todo correcto». Base de datos v7 sin cambios; sin cambios de interfaz (sigue `CAPTURAS_0.21.5.pdf`).
- Docs: DEPURACION_0.21.6, README, RELEASE, PRUEBAS_DISPOSITIVO §4.11, REVISION_FINAL.

## Prompt 56 — 0.21.7 (39): P-b aplicada y P-e explicada
- Decisión del usuario sobre las propuestas de la depuración: P-a no, **P-b sí**, P-c no, P-d no, P-e «explica detalladamente el fallo».
- **P-b** (`CierreRemoto`): `collectLatest` + `reintentar()` con `esperaReintento` 5 s, 10 s, 20 s… máx. 5 min; una venta que empieza cancela la espera (P43); excepciones = fallo reintentable (`runCatchingCancelable`); `aplicar()` devuelve `Resultado { CERRADO, ESPERA, OLVIDADA, FALLO }`. Antes, un `Err` dejaba el turno abierto hasta otra venta o reabrir la app (el flujo con `distinctUntilChanged` no volvía a emitir).
- **P-e**: explicación en `docs/DEPURACION_0.21.6.md` §6 (`cerrarSesion()` cierra la sesión vigente y no la que falló → puede cerrar una sesión nueva y sana; un comando ya aplicado se informa como fallido y, si se repite, se aplica dos veces). Corrección propuesta, sin aplicar: compare-and-set por sesión y, opcionalmente, comandos idempotentes.
- Test nuevo: 1 (data). `verificar.sh`: **673 tests JVM correctos** (core 16, licencia 59, domain 190, data 75, designsystem 24, app 309), «Todo correcto». Docs: DEPURACION_0.21.6 §3/§5/§6, README, RELEASE, PRUEBAS_DISPOSITIVO §4.11, REVISION_FINAL.

## Prompt 57 — 0.21.8 (40): P-e aplicada (sesión correcta + comandos idempotentes)
- Petición: «P e 2». Interpretación documentada: se aplican **los dos puntos** de la propuesta (el 2 depende del 1).
- **Punto 1** (`ClienteSync`): sesión vigente en `AtomicReference`. `cerrarSesion(s)` cierra solo la sesión usada y cambia el estado solo si seguía vigente (compare-and-set); el lector hace lo mismo. `cerrarSesion()` queda para cierres intencionados.
- **Punto 2:** `Comando.clave` (UUID) y `HolaOk.comandosUnicos`, opcionales y compatibles; `VERSION_PROTOCOLO` sigue en 1. Nueva `MemoriaComandos` en la principal (200 resultados por empleado+clave, en RAM, con `Mutex`); `EjecutorComandos.resultado(comando, empleado)`. La secundaria reenvía una vez con la misma clave por una sesión nueva, y solo si la principal lo anuncia.
- Tests nuevos: 4 (`ComandosUnicosTest`). `verificar.sh`: **677 tests JVM correctos** (core 16, licencia 59, domain 190, data 79, designsystem 24, app 309), «Todo correcto».
- Docs: DEPURACION_0.21.6 §6/§7, VINCULACION §0.21.8, README, RELEASE, PRUEBAS_DISPOSITIVO §4.11 (paso 7), REVISION_FINAL. Sin cambios de interfaz (sigue `CAPTURAS_0.21.5.pdf`).

## Prompt 58 — 0.21.9 (41): pruebas ampliadas
- Petición: «prueba todas las demás funcionalidades y elementos del proyecto exhaustivamente». El usuario eligió **ampliar** (tests JVM nuevos para lo que no tenía prueba, sin Robolectric/Roborazzi) y **corregir** los fallos claros (lo dudoso, como propuesta).
- **Room en la JVM** (`tools/verificacion/jvm-room`, paso 8 de `verificar.sh`): adaptador `SupportSQLite` → `sqlite-jdbc` delante de Room en el classpath. Así se ejecutan por primera vez los tests instrumentados de `:data` que usan Room en memoria, más `MigracionesJvmTest` (versión JVM de `EsquemaTest`, con control negativo). El proyecto Gradle no cambia.
- Tests nuevos: `InventarioRoomTest` 10, `SincronizacionRoomTest` 6, `MigracionesJvmTest` 5, `LicenseValidatorTest` 7, `ServiciosUseCasesTest` 5, `ServiciosViewModelTest` 5, `VinculacionViewModelTest` 8. Los 15 tests de Room que ya existían pasan.
- **B1 corregido** (`VentaRepositoryImpl`): un producto borrado mientras estaba en el carrito daba «sin existencias». Ahora da «ya no existe», igual que los Elaborados y servicios.
- `verificar.sh`: **702 tests JVM correctos** (core 16, licencia 66, domain 195, data 79, designsystem 24, app 322) y **36 tests de Room en la JVM**, «Todo correcto».
- Docs: `docs/PRUEBAS_0.21.9.md` (nuevo), README, RELEASE, PRUEBAS_DISPOSITIVO, REVISION_FINAL (L2 parcialmente cubierta). Sin cambios de interfaz (sigue `CAPTURAS_0.21.5.pdf`).

## Prompts 59–60 — solicitud de prueba y licencia emitida por GL (sin cambios de código)
- Solicitud: `docs/GL_SOLICITUD_PRUEBA.txt` (MENSUAL, 2 secundarias, dispositivo de prueba `SPVI:golden0000000001`).
- Respuesta de GL guardada en `tools/licencia/respuestas/licencia_gl_mensual_2.txt` (ID d53a020b-4062-4825-8219-024f997173f8).
- `probar_gl.py verificar … --tipo MENSUAL --secundarias 2` → **ACEPTADA**: firma `gl-sign-v1` válida, descifrado correcto, `estado` ACTIVA, emitida 2026-10-03T20:03:02.754Z, vence 2026-11-02T20:03:02.754Z (30 días), `secundarias` 2 (GL ya copia el campo), datos de la solicitud intactos.
- Controles negativos: iv alterado → firma inválida; ID cambiado → no abre; esperar 3 secundarias → rechazada.

## Prompt 61 — propuesta de licencia más fluida (sin cambios de código)
- Pregunta: «¿existe algún mecanismo o flujo de licencia más fluido, pero sin perder seguridad?».
- Propuestas: L-a «Compartir → Shalev» con activación directa; L-b licencia corta de un SMS; L-c QR; L-d nº de transacción en la solicitud; L-e renovar sin perder días + «Renovar igual».
- Descartadas por seguridad o por las reglas: servidor de activación, códigos no atados al teléfono, claves simétricas en el APK, leer SMS o portapapeles.

## Prompt 62 — 0.22.0 (42): licencia corta, QR y renovación sin perder días
- Petición: «implementa todas las recomendaciones menos la a y la d» → **L-b, L-c, L-e**. Detalle y análisis de seguridad en `docs/LICENCIA_0.22.md`.
- **L-b** `contract/LicenciaCorta.kt`: `SPVI1:` + Base64url de cuerpo 44 B ‖ firma cruda 64 B (150 caracteres, un SMS). La firma es la de `gl-sign-v1`, con dominio `SPVI-L1|`. La huella es SHA-256(deviceId + devicePub)[0..16]. En la activación también se comprueba que el teléfono posee la clave privada (ECDH de prueba). `LicenseManager`: activación corta/larga, re-verificación al arrancar y Migrar con licencia corta. `EcP256.rawToDer/derToRaw`. Sin cambios de almacenamiento.
- **L-c** `LectorQrLicencia.kt`: diálogo con cámara (solo QR, también en el bloqueo) y lectura desde imagen con `PickVisualMedia` + ML Kit. Sin permisos nuevos. `CamaraEscaner` acepta `formatos` y `descripcion`. `LicenciaViewModel` recibe `ConfiguracionInicialRepository` (permiso ya pedido) y gana `qrLeido`.
- **L-e** `RequestPayload.renueva` (no se escribe si es null) y `licenciaCorta = true`. `LicenseManager.licenciaRenovable`, que `LicenciaRepositoryImpl` añade a la solicitud. Botón «Renovar igual» y frase «no pierdes días» en el paso 2.
- GL: `docs/GL_PROMPT_0.22.md` (especificación, respuesta por SMS/WhatsApp/QR, cálculo de la renovación con sus registros, vector de referencia) y nueva `docs/GL_SOLICITUD_PRUEBA.txt` (renovación de d53a020b…, debe vencer el 2026-12-02). `probar_gl.py`: `--renueva`, verificación de la corta (y de las dos formas a la vez) y autoprueba con corta, QR y renovación.
- Tests: `LicenciaCortaTest` 12 (incluye un vector escrito en Python y un clon con deviceId y clave pública copiados). `GoldenVectorTest.verifyRealLicense` ya usa la **licencia real de GL**. `LicenciaViewModelTest` +4 y `LicenciaFormTest` +3.
- Docs: LICENSE_CLIENT §2/§3.1/§6/§8, MANUAL_USUARIO §9, PRUEBAS_DISPOSITIVO §4.8 punto 6b, Ayuda (Licencia), README, RELEASE, REVISION_FINAL.
- `verificar.sh` (v20): «Todo correcto»: 721 tests JVM (core 16, licencia 78, domain 195, data 79, designsystem 24, app 329) + 36 de Room en la JVM. La v19 encontró un fallo: el tema «Licencia» de la Ayuda tenía 6 pasos (máximo 5); se condensó.
- Maquetas: 4 páginas nuevas (nº 63–66) → `~/CAPTURAS_0.22.0.pdf`, que sustituye a 0.21.5.

## Prompt 64 — 0.23.0 (43): solicitud y licencia cifradas, mensaje «datos + código», QR por WhatsApp
- Petición: «siempre licencia corta, QR de la solicitud solo vía WhatsApp; formato: texto introductorio con datos + renglón + solicitud cifrada; características de la licencia + renglón + licencia cifrada; pruebas, prompt para GL, actualizar PDF». Respuestas del usuario: la corta, **cifrada** (2 SMS está bien); QR en los dos sentidos solo por WhatsApp; el texto de la solicitud con todos los datos; solo se activa la corta. Detalle en `docs/LICENCIA_0.23.md`.
- `:licencia`:
  - `contract/SolicitudCifrada.kt`: `SPVIR1:` (ECDH + HKDF `SPVI-R1` + GCM, epk comprimida, sin firma) y `MensajesLicencia` (solicitud y licencia).
  - `LicenciaCorta.kt` reescrita a `SPVI2:` (cuerpo cifrado hacia la clave del teléfono + firma de GL encima, 216 caracteres).
  - `EcP256.comprimir/descomprimir`.
  - `RequestPayload` v2: `devicePub` comprimida, sin `appName` ni `licenciaCorta`.
  - `LicenseManager`: `buildRequest` → `SolicitudGenerada(texto, codigo)`; activar solo `Shalev2`; la larga instalada se sigue re-verificando.
  - Quitados el sellador del envelope de la solicitud y `LicenseMessageParser`.
- `:domain` / `:data`: `construirSolicitud` devuelve `SolicitudGenerada`.
- `:app`:
  - evento `Enviar(via, texto, codigo)`;
  - `PngQr` (PNG en JVM puro) y `Contacto.whatsappConImagen` (`ACTION_SEND` a WhatsApp/Business con FileProvider y `jid`; si falla, `wa.me`);
  - textos del paso 2 y Ayuda.
  - Sin permisos nuevos.
- Tests:
  - `LicenciaCortaTest` reescrito (13);
  - `RuntimeVerificationTest` reescrito;
  - `FakeGl` con el emisor `Shalev2` y la larga v1 para la compatibilidad;
  - `LicenseFlowTest`: formato de la solicitud, la larga ya no se activa, solo cuenta el código;
  - `GoldenVectorTest`: la larga real de GL instalada sigue activa;
  - `PngQrTest` (2).
  - `TestEmisor` (androidTest) pasa a `SPVIR1`/`Shalev2`.
- GL:
  - `docs/GL_PROMPT_0.23.md`: especificación, descompresión en Java, texto exacto del mensaje, vector con efímera y firma de prueba, compatibilidad.
  - `docs/GL_SOLICITUD_PRUEBA.txt` + `.png` (renovación de d53a020b…, debe vencer el 2026-12-02).
  - `probar_gl.py` reescrito (`solicitud --qr`, `verificar`, `autoprueba`, `vector`); el vector queda en `tools/licencia/vector_0.23.txt`.
- `verificar.sh` (v21/v22): «Todo correcto». 722 tests JVM (core 16, licencia 77, domain 195, data 79, designsystem 24, app 331) + 36 de Room en la JVM.
- Maquetas: nº 64–65 actualizadas y 66–68 nuevas (solicitud por WhatsApp con QR, respuesta de GL por WhatsApp, licencia por SMS) → `~/CAPTURAS_0.23.0.pdf` (69 páginas), que sustituye a 0.22.0.

## Prompt 65 — 0.23.1 (44): licencia solo como texto (sin QR)
- Petición: «el manejo de una imagen de QR en el mismo teléfono puede ser complicado para un usuario no avanzado; elimina la licencia en QR, solo se enviará como texto». El usuario eligió quitar también el lector (cámara e imagen) del paso Activar y el QR de la solicitud.
- `:app`:
  - borrados `LectorQrLicencia.kt` (diálogo de cámara y lectura desde imagen) y `PngQr.kt`;
  - `Contacto.whatsappConImagen` quitado: WhatsApp vuelve a `wa.me` con el texto;
  - `LicenciaViewModel` sin `qrLeido`, `camaraSolicitada` ni `ConfiguracionInicialRepository`;
  - evento `Enviar(via, texto)`;
  - `TextosQrLicencia` → `TextosActivacion.INSTRUCCION`;
  - Ayuda (Licencia) y texto del paso 2 sin QR.
  - `:licencia` sin cambios (formato `SPVIR1`/`Shalev2` igual).
- Tests: quitados `PngQrTest` (2) y los del QR/cámara de Licencia (3). Nuevo `soloSePegaElMensajeElCodigoBastaAunqueVengaSolo`.
- GL:
  - `docs/GL_PROMPT_0.23.md`: solo texto, sin QR ni imágenes, con botón «Copiar mensaje»;
  - borrado `docs/GL_SOLICITUD_PRUEBA.png`;
  - `probar_gl.py` sin `--qr`.
- `verificar.sh` (v23): «Todo correcto». 718 tests JVM (core 16, licencia 77, domain 195, data 79, designsystem 24, app 327) + 36 de Room en la JVM.
- Maquetas: licencia nº 63–67 (sin el diálogo «Escanear licencia» ni QR en los chats) → `~/CAPTURAS_0.23.1.pdf` (68 páginas), que sustituye a 0.23.0.
- Prompt 66: `docs/GL_PROMPT_0.23.md` revisado (punto 0: quitar Shalev1, QR y licencia larga de GL; extracción tolerante a saltos de línea; validaciones; botón «Copiar mensaje»; pruebas d y e). `GL_PROMPT_0.22.md` marcado como obsoleto.

## Prompt 67 — 0.24.0 (45): la Descripción distingue artículos con el mismo nombre
- Petición: «Descripción debe utilizarse para describir de forma resumida características de un producto que faciliten su identificación… ejemplo Nombre (Cerveza Cristal) Descripción (Lata 350 ml Superior); cuando 2 o más productos tienen el mismo nombre se le exige al usuario que los diferencie mediante la descripción antes de ingresarlos».
- Decisiones del usuario (ask_user):
  - se muestra en todas partes;
  - máximo 40 caracteres en una línea;
  - los repetidos existentes se marcan y se exige diferenciarlos al editar;
  - alcance: productos, Elaborados y servicios (insumos sin cambios).
- `:domain`:
  - `model/Identificacion.kt`: `nombreCompleto` («Nombre · Descripción»), `clave` (sin mayúsculas, tildes ni espacios repetidos), `conflicto`, `porDiferenciar`.
  - `Validadores.MAX_DESCRIPCION` = 40 (antes 500) y `Validadores.descripcion` (una línea).
  - `GuardarProducto` / `GuardarServicio` aplican la regla: falta → `Validacion(descripcion, REQUERIDO)`; repetida → `Duplicado(descripcion)`. Parámetro `exigirDiferenciar` (solo el escáner lo apaga al cambiar la caducidad).
  - `TipoAlerta.NOMBRE_REPETIDO`, `ConteoAlertas.nombreRepetido`, filtro en `Stock` e `InventarioFiltro`, `ItemInventario.nombreRepetido`, `VistaServicios.porDiferenciar`.
  - La venta (`PlanificadorVenta`, servicios en `VentaUseCases`), el Top 3, «Usado en» y la lista de precios usan `nombreCompleto`.
  - Ficha: título y primera línea con nombre completo.
  - Inventario en Excel/PDF: nueva columna «Descripción».
- `:data`:
  - movimientos y «sin existencias» con el nombre completo;
  - `EjecutorComandos` repite la regla para los comandos de las secundarias.
- `:app`:
  - formularios de producto y servicio: Descripción de una línea con ejemplo, contador «N/40» y aviso en vivo (con las descripciones existentes); mensajes REQUERIDO / FORMATO / RANGO / repetida;
  - filas de Inventario, Venta, Servicios, Precios, tarjetas y diálogos con el nombre completo;
  - alerta «Nombre repetido: añade descripción» en Inicio (`AlertTone.Revisar`, número en `primary`), chip «Nombre repetido» en el filtro y subtítulo en las filas;
  - Ayuda: «Agregar productos» con un 5.º paso.
- Sin cambios en la base de datos (v7), el respaldo ni el protocolo. Las ventas antiguas conservan el nombre congelado entonces.
- Tests:
  - `IdentificacionTest` (9);
  - `ProductoFormTest`: `nombreRepetidoExigeDescripcionDistintaEnVivoYAlGuardar` y `descripcionResumidaConAyudaYMensajes`;
  - `InicioLogicTest`: tono `Revisar`.
- `verificar.sh` (v24): «Todo correcto». 729 tests JVM (core 16, licencia 77, domain 204, data 79, designsystem 24, app 329) + 36 de Room en la JVM.

## Prompt 70 — 0.25.0 (46): todo lo pendiente del PLAN_0.25
- Petición: «aplicar todo lo pendiente». Orden pedido: primero los pendientes pequeños, después los tests nuevos y luego `verificar.sh` hasta «Todo correcto». Suposiciones 1–4 de PLAN_0.25 aceptadas.
- Especificación de GL:
  - redacción de los puntos 210/216 de `GL_PROMPT_0.23.md`;
  - los parsers de `SPVIR1` toleran espacios (Kotlin y `probar_gl.py`);
  - nuevo `docs/GL_ESPECIFICACION.md`.
- `:licencia`:
  - `RequestPayload.recupera` y texto «Recuperar licencia Shalev»;
  - `ListaRevocaciones` (firma `SPVI-REV1|`, huellas `SPVI-REV|`) e `IdLicencia`;
  - `LicenseManager.aplicarRevocaciones` y marca de revocación (`Revoked`).
- `:domain`:
  - `EstadoApp` (recordatorio de respaldo, actualizaciones, licencia recuperable);
  - `Arqueo`, `MovimientoCaja`, fondo y contado obligatorios;
  - `AnularVenta`, `ModificarVenta` (precios originales, `Devueltos`), `ComprobarActualizaciones`, `FondoSugerido`;
  - las estadísticas usan solo ventas válidas; columna Estado y campos de arqueo en las exportaciones.
- `:data`:
  - BD v8 (`MIGRACION_7_8`) y respaldo v4, que importa también v3;
  - sincronización con campos opcionales (protocolo v1): conteo de la secundaria, cambios de ventas y APK por la red local (`ApkLocal`, bloques de 256 KB);
  - `ActualizacionesRepositoryImpl` (GitHub Releases, SHA-256);
  - `EstadoAppRepositoryImpl`.
- `:app`:
  - diálogos de caja (`CajaUi`/`CajaViewModel`), pestaña Caja del turno;
  - Anular/Modificar en la ficha de venta (`VentaAcciones`), marcas ANULADA / «Corrige #N»;
  - aviso de respaldo;
  - tarjeta de actualización (Inicio/Ajustes) con `PackageInstaller`, y «Buscar actualizaciones» en Ajustes;
  - «Licencia transferida» con `ACTION_DELETE`;
  - campo de recuperación en Licencia (rellenado desde el respaldo);
  - activación automática al compartir `SPVI2:` a Shalev.
- Manifiesto: `REQUEST_INSTALL_PACKAGES` y `REQUEST_DELETE_PACKAGES` (autorizados en P68b; añadidos a `permitidos`).
- Tests nuevos:
  - `Version025Test` (domain, 20): arqueo, caja, anular/modificar, recordatorio, versiones, comprobación y revocación;
  - `RecuperacionTest` (licencia, 7): `recupera`, extracción del ID, firma de la lista (válida, ajena, alterada), aplicación solo a la licencia instalada, y compatibilidad con la lista firmada por `probar_gl.py`;
  - `Version025AppTest` (app, 5).
  - Ajustados `TablasExportTest` (Estado), `TurnoPersistenciaTest` (v8) y `Version020SyncTest` (respaldo v4).
- Correcciones de compilación halladas por la verificación:
  - `ShalevSpacing.sm` no existe (→ `xs`);
  - `shalevAnimateItem()` fuera de una lista;
  - un parámetro de constructor que tapaba la propiedad `licencia` en `EntornoIntegracion`.
- Herramientas: `probar_gl.py` con `solicitud --recupera` y `revocadas firmar|verificar`, y la autoprueba ampliada; `vector_0.25.txt`; `docs/GL_SOLICITUD_RECUPERACION_PRUEBA.txt`.
- Documentación:
  - `docs/GL_PROMPT_0.25.md`;
  - README, RELEASE (§8 publicar en GitHub), SECURITY (§4 bis), MANUAL_USUARIO (caja, anular/modificar, recordatorio, recuperar, actualizar) y FORMATOS (respaldo v4, columnas).
- Desviaciones (README «Novedades de la 0.25.0»):
  - `PackageInstaller` sin FileProvider;
  - Modificar sin cambiar el método ni añadir artículos;
  - el conteo de un cierre pedido no se puede descartar;
  - la tarjeta de actualización solo aparece en Inicio/Ajustes y no se descarga en segundo plano;
  - el campo de recuperación solo aparece sin licencia;
  - el arqueo se ve en la pestaña Caja, sin PDF/Excel del turno;
  - una venta de servicios anulada llega a la secundaria en su siguiente sincronización.
- `verificar.sh` (v9): «Todo correcto». 761 tests JVM (core 16, licencia 84, domain 224, data 79, designsystem 24, app 334) + 37 de Room en la JVM.
- Sin ejecutar: Gradle/lint/R8, Roborazzi, el teléfono real y la prueba con dos teléfonos. El PDF de capturas no se ha regenerado para las pantallas nuevas.

## Prompt 71: PDF de capturas 0.25.0 y limpieza

- `CAPTURAS_0.25.0.pdf` (90 páginas): portada nueva, nº 1–67 de la entrega anterior y 22 maquetas nuevas (`tools/capturas/generar.py`): nº 68–69 Descripción (0.24.0); nº 70–76 arqueo de caja; 77–80 anular/modificar ventas; 81 aviso de respaldo; 82–85 actualizaciones; 86–89 recuperación de licencia y «Licencia transferida».
- Eliminados por estar sustituidos o ya aplicados: `CAPTURAS_0.23.1.pdf`, `docs/GL_PROMPT_0.21.md`, `docs/GL_PROMPT_0.22.md`, `docs/LICENCIA_0.22.md`, `docs/PLAN_0.21.md`, `docs/ANALISIS_IMPACTO_0.19.md`, `docs/PROPUESTA_COLOR.md`, la carpeta temporal `rv/` y las cachés. Se corrigieron las referencias en README, REVISION_FINAL, LICENSE_CLIENT, VINCULACION, LICENCIA_0.23 y GL_PROMPT_0.23.

## Prompt 72 — 0.25.1 (47): exportaciones revisadas, turno compartible y desviaciones de la 0.25.0 resueltas

- Petición: primero capturas de todas las exportaciones (textos, PDF, Excel, imágenes); después aplicar todo `PLAN_0.25.1` (A, B, C, D) más las correcciones de exportación halladas (E, aprobadas con «procede»). Entregar al final un PDF único con pantallas y exportaciones e índice, los .md para compilar y retocar con OpenCode Desktop, y borrar los PDF y .md obsoletos.
- Revisión de exportaciones (0.25.0, `tools/exportaciones/`: `Muestras.kt` con el código real, `render.py` que replica PdfWriter/ImagenTabla/Tarjetas, `paginas.py`):
  - Excel dañado (formato `"CUP"` sin escapar en `styles.xml`);
  - columnas cortadas en los PDF de 7–9 columnas;
  - «Shalev · Shalev · Servicios»;
  - el texto del inventario lleva costos sin avisarlo.
- **A. Compartir el turno:** `TablasExport.turno()` (Resumen, Arqueo de caja, Caja, Ventas, Movimientos) y `nombreArchivoTurno`; icono Compartir en el detalle del turno; hoja «Compartir turno» con PDF/Excel → enviar o guardar (`TurnosViewModel`, `TurnoDetalleScreen`).
- **B. Modificar venta a pantalla completa** (`registros/ModificarVenta.kt`): cantidades, quitar, añadir artículos (precio actual; los originales conservan el suyo), método de pago con los datos de la transferencia obligatorios, y motivo. `ModificarVenta` (dominio) acepta líneas nuevas y método.
- **C.** Campo de recuperación también con la licencia instalada vencida (`Licencia.permiteRecuperar()`).
- **D.** Conteo de la secundaria con «Ahora no»: el aviso de Inicio ofrece «Contar»; vuelve al regresar a Inicio o a los 15 minutos; nada se cierra sin conteo.
- **E. Exportaciones:**
  - formato Excel `#,##0.00 \C\U\P`;
  - `DisenoPdf.kt` (hoja horizontal con más de 5 columnas, anchos naturales para columnas compactas, números a la derecha) usado por `PdfWriter`;
  - título «Servicios»;
  - Inventario → Exportar ofrece Texto y marca cada formato «Uso interno (incluye costos)» / «Para clientes (sin costos)»;
  - nombres de hoja con fecha legible (`/`→`-`, `HH:mm`→`HH.mm`, espacios juntados).
- Tests nuevos o ampliados:
  - `ModificarVentaTest` (5);
  - `DisenoPdfTest`;
  - `EscritoresTest` (styles.xml bien formado, nombres de hoja);
  - `TablasExportTest` (turno completo con arqueo);
  - `TurnoViewModelsTest`, `LicenciaFormTest`, `InicioViewModelTest`, `InventarioLogicTest`;
  - `Version025AppTest` (Servicios sin «Shalev ·» duplicado).
- `verificar.sh` (v11–v13): «Todo correcto». 779 tests JVM (core 16, licencia 84, domain 225, data 84, designsystem 24, app 346) + 37 de Room en la JVM. La v12 detectó que el test del Excel esperaba el nombre de hoja antiguo; se ajustó junto con la regla de espacios.
- Documentación:
  - README (Novedades de la 0.25.1, permisos en `shalevPermisos`), MANUAL_USUARIO, FORMATOS (§2.1, §2.3, §2.4), RELEASE (pruebas 13–16), SECURITY (versión);
  - nuevos `AGENTS.md`, `docs/OPENCODE_DESKTOP.md` y `.opencode/commands/` (compilar, probar, capturas, release, retoque).
- Entrega: `Shalev_0.25.1_capturas_y_exportaciones.pdf` (portada, índice enlazado con marcadores, pantallas nº 1–96 con 8 maquetas nuevas de la 0.25.1, exportaciones E1–E22). `tools/capturas/ensamblar.py` lo arma.
- Eliminados por estar sustituidos o ya aplicados: `CAPTURAS_0.25.0.pdf`, `EXPORTACIONES_0.25.0.pdf`, `docs/PLAN_0.25.1.md` (aplicado entero). `docs/PLAN_0.25.md` se conserva porque el código cita sus apartados (§3, §6).

## Prompt 73 — 0.26.0 (48): PLAN_0.26 (respaldo, botón +, sin texto, fondo asignado, seminegrita, actualizaciones obligatorias)

Plan confirmado: [docs/PLAN_0.26.md](PLAN_0.26.md).
- **§1 Respaldo:** solo el archivo `.shalev` cifrado (se quita el PDF de configuración).
- **§2 Botón +:** abajo a la derecha (`FabPosition.End`) en Inventario, Precios y Servicios.
- **§3 Sin exportaciones de texto:** se quitan `texto*` y `Compartir.texto`. Quedan la solicitud de licencia, el contacto de Soporte y el pegado/compartido de SMS.
- **§4 Fondo asignado:** BD v9 (`MIGRACION_8_9`) y campos opcionales del protocolo ([VINCULACION.md](VINCULACION.md) §0.26.0). La principal asigna el fondo de cada turno de una secundaria; sin él, la secundaria no abre. Una secundaria 0.25.x abre como antes y la principal pide «Actualiza la app de X».
- **§5 Seminegrita:** `ShalevTextos.dato`/`datoEn`/`PESO_DATO` para los datos en la UI; seminegrita en el PDF y negrita en Excel para importes, cantidades y fechas (sin códigos de barras ni teléfonos).
- **§6 Actualizaciones obligatorias:** aplazables 30 días desde la primera detección; luego `BloqueoActualizacion` en toda la app, con Actualizar, Exportar respaldo y Cerrar turno. Se quitan el interruptor y la versión descartada.
- **§8 Excel:** cada columna mide lo que su contenido, de 4 a 80 unidades, con ajuste de texto.
- **Tests:** `Version026Test`, `SeminegritaTest`, migración 8→9, fondo, plazos y anchos.

## Prompt 74 — 0.26.0 (48): la prueba no se reinicia al reinstalar

Ver [PLAN_ANTIREINSTALACION.md](PLAN_ANTIREINSTALACION.md).
- **Registro cifrado** (`licencia/prueba/RegistroPrueba.kt`): AES-GCM con clave PBKDF2 a partir de ANDROID_ID. Se guarda en cuatro sitios: imagen PNG en Imágenes/Shalev, `.bin` en Download y en Documents, y copia interna. Gana la fecha más antigua y una copia ilegible se ignora.
- **`LicenseManager`:** usa `RegistroExterno` y `DetectorRetroceso` (`elapsedRealtime` dentro del mismo arranque, combinado con `lastSeen`).
- **Implementación Android:** `RegistroPruebaAndroid` / `DetectorRelojAndroid` (MediaStore en 10+, `File` en 8–9).
- **Permisos nuevos**, autorizados solo para esto: `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (≤ 32) y `WRITE_EXTERNAL_STORAGE` (≤ 28). El diálogo «Guardar tu periodo de prueba» se muestra una vez, recién instalada.
- **UI:** `TrialViewModel` + `TrialState` como fachada; la pantalla que bloquea al vencer sigue siendo la puerta de Licencia.
- **Rechazado por el usuario:** dar la prueba por vencida por colisión de nombre.
- **Tests:** `RegistroPruebaTest` (14), `EstadoPruebaTest` (3), `TrialViewModelTest` (4).
- **Verificación (P73 + P74):** `verificar.sh` «Todo correcto». 823 tests JVM (core 16, licencia 98, domain 238, data 92, designsystem 26, app 353) + 38 de Room; los permisos están dentro de la lista ampliada. Se entrega `Shalev_0.26.0_capturas_y_exportaciones.pdf` (127 páginas: nº 1–108, E1–E16) y se borra el de la 0.25.1.

## Limpieza para OpenCode (0.26.0)

Se retiraron los documentos de trabajo antiguos: `docs/PLAN_0.25.md`, `docs/DEPURACION_0.21.6.md`, `docs/PRUEBAS_0.21.9.md`, `docs/PRUEBA_ESCANER.md` y `REVISION_FINAL.md`. Su contenido vigente está en este historial (Prompts 53, 55, 58 y 70), en `Contexto.md`, `Pruebas.md` y `Pendiente.md`. Las menciones que quedan arriba son históricas.

## 0.27.0 · Claves de GL del 05/10/2026

- GL regeneró su par de claves al desinstalarse y reinstalarse ([GL_CONTEXTO_LICENCIAS.md](GL_CONTEXTO_LICENCIAS.md)). `LicenseTrust`: ECDH **sustituida** (`sha256:2a3f:9fce:…:666d`); firma vigente **añadida** (`sha256:58d4:3aac:…:8c4e`) en primer lugar, conservando la del 30/09/2026 (`sha256:8a91:…`) para que las licencias ya emitidas sigan verificando.
- Las solicitudes hechas con Shalev anterior a la 0.27.0 van cifradas hacia la ECDH vieja: GL no puede abrirlas; el cliente actualiza Shalev y vuelve a pedirla.
- Las huellas `sha256:4ea0:…` / `sha256:219e:…` que GL da como invalidadas nunca se fijaron en Shalev (Shalev pasó de `7b51`/`8a91` a las nuevas).
- `tools/licencia/probar_gl.py` usa las claves nuevas (y acepta la firma anterior).

## 0.27.0 (49) · Pulido de interfaz, acceso con clave, respaldo sin contraseña y clientes fijos

Especificación: [PROMPT_0.27.0.md](PROMPT_0.27.0.md) (T1–T14) más cuatro pedidos del dueño en P78 (N1–N4). Implementado en la copia propia de la 0.26.0, no en la de OpenCode: **los arreglos que OpenCode hiciera para compilar la 0.26.0 hay que volver a aplicarlos** si siguen haciendo falta.

| Tarea | Qué se hizo |
|---|---|
| T1 | `ShalevTopBar` = `CenterAlignedTopAppBar` sin logo (`showLogo` eliminado, `marca` para «Shalev» en Inicio); diálogos y hojas sin logo, título centrado |
| T2 | Fuera `maxLines = 1` en textos de Inicio (turno, accesos, datos), leyendas, selectores, cabeceras de Inventario; `ShalevListItem` hasta 3 líneas. Capturas con `LetraGrande` (fontScale 2) de Inicio, ficha, Licencia y arqueo |
| T3 | `ShalevMedalla` + tokens `MEDALLA_*` y `ContrastTest` |
| T4 | `ShalevMotion` FAST 150 / LONG 250, `muelle()`; botón con `animateContentSize(muelle)`; barra inferior con muelle; `profileinstaller` |
| T5 | `Deslizar.kt`: regla pura (ruta raíz exacta + sin diálogo, selección, búsqueda ni foco) con `DeslizarTest` |
| T6 | Placeholder centrado en `ShalevTextField` |
| T7 | `ShalevIcons.Pdf`/`Excel`, `iconoFormato()` en todas las hojas de exportar |
| T8 | Textos llanos (tabla abajo). Ningún `e.message` llega al usuario |
| T9 | `Vinculacion.puedeVincularseComoSecundaria`; botón oculto en la principal; `PRINCIPAL_NO_SECUNDARIA` en el ViewModel |
| T10 | `BackupCipher` v4 con indicador; sin contraseña, clave de un secreto interno (10 000 iteraciones); lee v3. Interruptor «Proteger con contraseña» apagado por defecto; Migrar mantiene 4 pasos |
| T11 | `androidx.biometric` 1.1.0, `MainActivity` → `FragmentActivity`, `AccesoClave.kt` (regla pura de 10 min con `elapsedRealtime`, overlay con `FLAG_SECURE`), paso opcional del recorrido y fila en Ajustes. Preferencia en `AjustesDispositivoRepository` (claves `disp.*`, fuera del respaldo) |
| T12 | Soporte con iconos (`ShalevListItem`), pie nuevo |
| T13 | `SelectorFoto` (foto primero, Cámara con `TakePicture` + `FileProvider` `cache/fotos/`, Galería con Photo Picker, Quitar). Los Elaborados no tienen foto (como antes). `LADO_MAX` = 1024 px y JPEG 85 ya cumplen «≤ 1080 px» |
| T14 | Reparto de alertas por filas (regla pura con test) y contenido centrado |
| N1 | Dona de Inventario: los insumos cuentan en su medida (milésimas → unidades de su medida, sin «u»), números neutros (formato `Cantidad`: `12.5`, punto decimal como en el resto de la app) |
| N2 | **Clientes fijos**: BD v10 (`cliente_fijo`, `transaccion.clienteFijo`, `MIGRACION_9_10`), alta/actualización por carné en la misma transacción de la venta (también al recibir ventas de una secundaria), sugerencias (hasta 3, sin tildes ni mayúsculas, primero las que empiezan igual), pestaña Registros → Clientes (ficha, quitar, sin exportar, `FLAG_SECURE`), respaldo con `clientesFijos` opcional (sin subir la versión). La secundaria no recibe la lista de la principal (solo la suya y lo que envía) |
| N3 | Logo con fondo transparente; splash con el color de ventana del tema |
| N4 | Diálogos y hojas suben desde abajo al centro y bajan al cerrarse (`ventanaEntra`/`ventanaSale`). Los cierres que decide el código (no el usuario) siguen siendo inmediatos |
| GL | Claves de GL del 05/10/2026 (ver la entrada anterior) |

**T8 · textos antes → después**

| Dónde | Antes | Después |
|---|---|---|
| Permiso de fotos (prueba) | «Shalev guarda en Imágenes una imagen pequeña y cifrada con la fecha de inicio… para que Shalev la encuentre si reinstalas…» | «Permite el acceso a fotos para que Shalev pueda conservar los datos de tu periodo de prueba.» |
| Turno sin arqueo | «Sin arqueo (turno anterior a 0.25.0)» | «Sin arqueo (turno de una versión anterior de Shalev)» |
| Secundaria antigua | «Su app es anterior a la 0.26.0 y escribe su propio fondo: actualízala» | «Su app necesita actualizarse para recibir el fondo que le asignas» |
| Respaldo antiguo | «…versión de prueba de Shalev anterior a la 0.13.0 y esta versión ya no lo abre.» | «Se creó con una versión muy antigua de Shalev que esta versión ya no abre.» |
| Licencia (envío) | «Se abrirá WhatsApp/Mensajes con tus datos y la solicitud cifrada.» | «…con tu solicitud de licencia.» |
| Manual, paso 4 | Explicación del mecanismo de la imagen y la reinstalación | Solo que el acceso a fotos sirve para conservar los datos de la prueba |

**Verificación** (`verificar.sh`, «Todo correcto»): 846 tests JVM (core 16, licencia 99, domain 242, data 96, designsystem 27, app 366) y 41 de Room; `10.json` = `MIGRACION_9_10`. Dos tests antiguos ajustados: `UseCasesTest` (cuerpo que devolvía valor) y `RespaldoPasosTest` (sin contraseña el formulario ya es válido).

**Suposiciones:** el placeholder centrado no cambia el texto escrito ni la etiqueta; el acceso con clave usa `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` en API 30+ y en 26–29 la combinación que admite la librería; las cantidades siguen el formato de `Cantidad` (Locale.US, «12.5»), no «12,5»; la lista de clientes fijos no se distribuye a las secundarias (protocolo sin cambios de versión: solo un campo opcional en la venta).


## 0.27.1 (versionCode 50) · P79: pruebas pendientes con Gradle real

Primera vez que el proyecto se compila con Gradle real (JDK 17, SDK 35, Gradle 8.11.1, AGP 8.7.3) en vez de solo con `verificar.sh`. No hay emulador ni teléfono en el entorno (sin `/dev/kvm`): las pruebas en dispositivo y entre dos teléfonos siguen pendientes.

**Ejecutado:**
- `assembleDebug`;
- `shalevCheck` (tests, lint y permisos);
- `assembleRelease` (R8);
- `recordRoborazziDebug`: 154 capturas × 2 temas;
- `aapt2` sobre los APK debug y release;
- `tools/verificacion/api_minima.py` (nuevo).

**Errores reales encontrados y corregidos:**

| # | Problema | Corrección |
|---|---|---|
| 1 | `LocalDate.ofInstant` es API 34: Inicio se cerraba en Android 8–13 | `Dates.localDate(instant, zona)` en `core` (9 llamadas) |
| 2 | `BigInteger.longValueExact` es API 31: convertir dinero y cantidades cerraba la app en Android 8–11 | `BigDecimal.movePointRight(n).longValueExact()` en `Money` y `Cantidad`; `ApiMinimaTest` |
| 3 | Letra al 200 %: las alertas de Inicio partían «inven·tario» y las tarjetas Pago/Precios se cortaban | `alertasPorFila` (ancho mínimo de 96 dp por alerta) y accesos apilados si no caben |
| 4 | Letra al 200 %: el importe del arqueo salía «12,35 / 0.00 / CUP» | `CajaUi.Fila` con `FlowRow`: el importe baja entero a la línea siguiente |
| 5 | Subtítulos de lista cortados con «…» (Ajustes, Exportar) | `ShalevListItem.subtitleMaxLines` pasa de 1 a 3. En Exportar, «Incluye costos» en vez de «Uso interno (incluye costos)» |
| 6 | Ajustes: Respaldo decía «y PDF» y Permisos solo hablaba del escáner | «Copia cifrada de todo e importar». «Cámara: se pide solo al escanear o al hacer una foto» |
| 7 | Apps vinculadas: las opciones Automática/Manual quedaban pegadas | Separación y altura táctil mínima de 48 dp |
| 8 | Arnés de capturas: la barra superior de la actividad de prueba tapaba la parte de arriba de las capturas (solo en los tests) | `capturar()` la oculta con `LaunchedEffect`. Lint rechazó la primera versión, que usaba `remember` |

**Resultado:**
- 850 tests JVM sin fallos (core 19, licencia 99, domain 242, data 96, designsystem 27, app 367);
- lint sin errores;
- permisos del APK dentro de la lista;
- R8 sin clases ausentes;
- `api_minima.py`: 0 llamadas no permitidas.

**Límites:**
- Los tests de Room en la JVM (41) se ejecutaron por última vez con `verificar.sh` en la 0.27.0; esta versión no cambia la base de datos.
- Los diálogos con letra grande no se pueden capturar con `LetraGrande` (ver `CAPTURAS.md`).
- Android 14 con acceso parcial a fotos («Seleccionar fotos») no deja leer el registro de la prueba tras reinstalar.

## 0.28.0 · P80: muelles físicos, Registros sin gestos, licencia apilada y seminegrita en importes/fechas

1. **Animaciones con muelles físicos:** en `ShalevMotion`, los deslizamientos de pantalla y de la barra inferior, los giros (flechas, FAB) y el progreso de los gráficos usan `spring()` (rígidez/damping según la pieza) en vez de duraciones fijas; los fundidos y los cambios de color siguen con `tween` porque no tienen desplazamiento.
2. **Registros sin gestos:** eliminados `InterceptorDeslizar`, `LocalInterceptorDeslizar` y `RegistrarDeslizar` (`navigation/Deslizar.kt`); el desplazamiento con el dedo solo cambia de sección (Inicio–Inventario–Servicios–Registros–Ajustes). Las pestañas de Registros se cambian tocándolas (MANUAL_USUARIO.md §navegación actualizado).
3. **Licencia apilada:** los 4 tipos (Mensual, Semestral, Anual, Perpetua) van en `Column` a ancho completo, uno debajo del otro; el precio de cada chip usa `supportingEsDato` (nuevo parámetro de `ShalevChip`).
4. **Seminegrita en importes y fechas:** `ShalevTextos.resaltar(texto, vararg datos)` (marca cada ocurrencia dentro de frases) y `ShalevTextos.datoTexto(dato)` (dato solo en huecos `AnnotatedString`), con tests en `SeminegritaTest`; `ShalevListItem` acepta `titleResaltado`/`subtitleResaltado`. Aplicado en Registros, TurnoDetalle, ModificarVenta, ClientesFijos, Venta, Caja, Vinculación, Inicio, Inventario, Onboarding y Licencia. Criterio: solo UI persistente (fuera: snackbar, SMS/compartir, accesibilidad, PDF/Excel, lienzos de gráficos, filtros, placeholders, campos editables y conteos); solo importes (`Money`/`Cup`) y fechas (`Dates`).

**Nota:** `shalevTests` en Windows deja 1 fallo preexistente y ajeno a esta versión (`EscanerRedTest.almacenSoloBorraDentroDeSuCarpeta`: `uriDe` construye `file://C:\…` y el test espera `file:///`; en Android las rutas empiezan por `/` y funciona). En Linux pasa (850 tests en la 0.27.1).

## 0.28.1 · Animaciones apreciables en el teléfono

El dueño reportó que las animaciones seguían imperceptibles con el teléfono en la mano. Causas encontradas probando en el dispositivo (grabación de pantalla + fotogramas con `ffmpeg`):
1. El teléfono tenía las tres escalas de animación del sistema a `0.0` (se pusieron a `1.0` por `adb`; en MIUI: Ajustes → Accesibilidad → Visión → «Quitar animaciones» apagado, o las tres «Escala de animación» a 1x en Opciones de desarrollador). Con ellas a cero, Compose ejecuta todo al instante y ningún parámetro se ve.
2. El deslizamiento de ventanas recorría solo 1/10 del ancho y el cambio de pestaña era solo un fundido de 200–250 ms (entre pantallas oscuras no se nota).

**Cambios** (todo en `ShalevMotion`, `designsystem/token/Tokens.kt`):
- Nuevo `muelleEntrada()` (rigidez 350, amortiguación 0,85: blando, con leve asentamiento de ~0,4 s) para las ENTRADAS: navegación adelante/atrás, `enterVertical`, `ventanaEntra` (ventanas emergentes) y aparición de la barra inferior. Las salidas siguen inmediatas con `muelle()`.
- Navegación con dirección: recorrido 1/10 → 1/4 del ancho.
- Cambio de pestaña: fundido + zoom sutil 0,96 (`ZOOM_ENTRADA`, sin dirección porque no hay avance ni retroceso).
- Comentarios de `MainScaffold` actualizados. Test nuevo `MovimientoApreciableTest` (fija rigidez, amortiguación y zoom).

**Verificado en el teléfono** (23021RAAEG, Android 15, build debug reinstalada): transición de pestaña con fundido progresivo en 3 fotogramas; deslizamiento Servicios → Nuevo servicio y Servicios → Ayuda capturados a mitad de recorrido (viaje largo + fantasma de la saliente); sin FATAL en logcat. No se creó ningún dato (solo navegación; la app quedó en Inicio). Nota: el codificador de `screenrecord` del equipo pierde cuadros en movimientos rápidos a 1x (avisa `err=-22` y graba a 720p); con cámara lenta a 5x el recorrido se ve completo.

## 0.29.0 · Semilla de datos de prueba (Bodega cubana, 18 meses)

Generador de base de datos de prueba **solo en debug** que satura la app con un historial coherente de 1 año y medio de ventas, para probar rendimiento, filtros y reportes sin cargar datos a mano.

1. **Arquitectura:** paquete nuevo `cu.shalev.domain.seed` en `:domain` con tres piezas — `CatalogoBodega` (catálogo fijo: 11 insumos, 32 productos con 2 elaborados y receta, 8 servicios con 3 que consumen insumos, 12 clientes fijos con CI válido, 2 tarjetas y 2 teléfonos de pago, vendedores María Fernández y Jorge Pérez por rachas de 2 semanas), `GeneradorSeed` (plan determinista con semilla 2907L: ~470 turnos de 6 días/semana, 6–14 ventas por turno, 22 % servicios, 30 % transferencia solo con cliente, caja semanal, descuadres ocasionales, ~1,5 % de ventas anuladas) y `OrquestadorSeed` (ejecuta el plan contra los repositorios públicos con fechas explícitas de La Habana, 8:00–20:00; fondo 2000 CUP encadenado entre turnos).
2. **UI debug:** fila «Generar datos de prueba» en Ajustes, visible solo con `BuildConfig.DEBUG` (precedente `mostrarCatalogo`), con diálogo de progreso por día (`SeedViewModel` + `DialogoSeed` en `app/.../ajustes/seed/`). Borra todo primero (avisa en el diálogo) y re-completa el onboarding con los datos del catálogo. En release no hay rastro: la fila no existe con `mostrarSeed = false`.
3. **Decisiones (R1–R6 del ledger):** sin git en el entorno se omiten los commits; generador determinista en `:domain` por repositorios (rechazados DAO directo y respaldo `.shalev`); el plan truncado se reconstruyó desde la spec como autoridad; `clienteClave` como `claveFija`/`EVT:` + base-26 (los nombres solo admiten letras); `numeroTransaccion` con relleno a 6 (el validador exige 6–30); anuladas por día (15 % de días ≈ 1,5 % de ventas, como pide la spec); `EjecutorSeed` como `fun interface` con `operator fun invoke` (el `typealias` de función no genera binding Hilt) más `ioSeed` para tests deterministas.

**Límites:**
- Los movimientos de inventario ALTA/AJUSTE/BAJA llevan la fecha actual (`clock.now()` dentro de `ProductoRepositoryImpl.mov()`), no la histórica; el stock final sí queda coherente porque las salidas por venta usan la fecha del turno.
- Sin fotos en los datos generados.
- `FakeVentas.anular` no devuelve los insumos consumidos; el colchón del +20 % en la compra inicial lo absorbe.
- Los tests de `:app` usan fakes locales propios (no ven los `Fakes` de `:domain`).

## 0.29.1 · Escáner: solo nombre y caducidad, sin fotos

El dueño reportó que el escáner funcionaba mal: los nombres en línea llegaban con basura (texto de mercadeo de UPCitemdb) y se cortaban a los 80 sin que el resto fuera a ningún lado.
1. **Dividir en vez de truncar:** nuevo `NombreEnLinea.dividir()` (nombre ≤ 80 en palabra completa + resto a la descripción ≤ 40); `BuscarProductoEnLinea` lo usa y `ProductoEnLinea` gana el campo `descripcion`, que viaja por `Route.ProductoForm` hasta el formulario.
2. **Sin fotos en el escáner:** se elimina la descarga (`DescargarFoto` deja de usarse en el escáner; la clase sigue para uso futuro), el `AsyncImage` del resultado y los estados `fotoUri`/`cargandoFoto`. La foto del producto se sigue poniendo a mano en el formulario.
3. La fecha de caducidad sigue poniéndola el usuario (o sale de Room si el producto ya existe): ninguna base pública da caducidad.

**Nota:** `DescargarFoto` y sus tests en `:domain` se conservan (código muerto a propósito, sin wiring en `:app`).

## 0.29.2 · Tops de empleado y cliente en Inicio

Dos listas Top 3 en el resumen general de Inicio (ventana fija de 30 días, como el Top 3 actual; no siguen al selector de período): **Empleado** (vendedores con mayor importe vendido, por el nombre congelado de la venta) y **Cliente** (clientes con mayor importe comprado, solo por transferencia: el efectivo no registra cliente; se distingue por nombre + CI). Solo ventas válidas; empate → alfabético; ocultas si no hay datos. Cálculos puros `Estadisticas.topEmpleados/topClientes` sobre la misma lista que ya carga `ObtenerResumenGeneral` (nuevo `TopPersona` + campos en `ResumenGeneral`); tarjetas con medalla y el importe con seminegrita. Tests en `TopsInicioTest`; fixture y capturas de `InicioCapturas` re-grabadas. (MANUAL_USUARIO.md §5.4)

## 0.29.3 · Escáner solo-nombre sin fecha + Top Empleados visible

1. **Escáner simplificado** (a petición del dueño: del número escaneado solo se extrae el nombre; la fecha sale del escáner): `BuscarProductoEnLinea` devuelve el nombre limpio cortado a 80 (el resto se descarta); se eliminan `NombreEnLinea.dividir` y `ProductoEnLinea.descripcion`. En la app salen `descripcion`/`caducidad` del estado del escáner, el campo de fecha del resultado y el botón Guardar para producto existente (la tarjeta queda informativa); el formulario abre solo con código + nombre. `Route.ProductoForm` pierde los params `descripcion`/`caducidad` (el formulario conserva su propio campo de fecha). Tests reescritos (`EscanerViewModelTest`, `ProductoFormTest`, capturas `08i` re-grabada) y `MANUAL_USUARIO.md` §2 actualizado.
2. **Top Empleados no salía (bug):** `VentaRepositoryImpl.entre`/`deTurno` no rellenaban `vendedor` (solo lo hacía `obtener`), así que `ObtenerResumenGeneral` recibía las ventas con vendedor vacío y `topEmpleados` las filtraba todas. Nueva consulta por lote `VentaDao.vendedoresDe(ids)` + helper `conVendedores` (test `VendedoresVentaTest`); de paso, las exportaciones con columna Vendedor vuelven a mostrarlo.

## 0.30.0 · Sin código de barras (QR solo para vinculación)

A petición del dueño se elimina el código de barras del producto de todo el proyecto; la cámara queda solo para el QR de vinculación y la foto del producto.

1. **Dominio:** fuera `Producto.codigo`, `InventarioRepository.porCodigo`, `ConsultarCodigo`, `BuscarProductoEnLinea`, `DescargarFoto`, `FuenteProductos`/`ProductoEnLinea`/`CodigoBarras`/`SimbologiaCodigo`/`ResultadoBusquedaEnLinea`, `PoliticaConsultaEnLinea` y `consultasEnLinea` de `Preferencias` (con su `guardar` y el consentimiento de Ajustes y onboarding). La búsqueda del Inventario y los preajustes filtran por nombre/descripción/categoría.
2. **Datos:** fuera `codigo` de la entidad, el DAO, los mappers, `ProductoDto` y el respaldo (lector tolerante: los respaldos viejos con `codigo`/`consultasEnLinea` se importan igual); fuera `FuentesRed`/`ScannerDtos` y su test (con él desaparece el fallo Windows de `EscanerRedTest`). Migración 10→11 (recrear tabla sin `codigo`, test `Migracion1011Test`); User-Agent `Shalev/0.27 (Android)`.
3. **App:** borrado `app/.../escaner/`; nuevo lector mínimo `vinculacion/qr/` (`QrVinculacionScreen` + VM + `CamaraEscaner` solo-QR). Sin botón Escanear en Inicio ni hoja Escanear/Escribir en Inventario (FAB único +); sin campo código en el formulario; sin fila de internet en Ajustes.
4. **Registros:** la ficha de venta muestra el unitario debajo (`lineasVenta`: «2 × Pan» + «10.00 CUP c/u», test `RegistrosTest`).
5. Docs actualizados (`README`, `MANUAL_USUARIO` §2, `FORMATOS`, `SECURITY`, Ayuda) y capturas re-grabadas.


## 0.30.0 — Verificación real (F0 del plan de correcciones, 2026-10-07)

Primera vez que el repositorio se verifica solo: GitHub Actions ejecuta Gradle real en cada push
(`.github/workflows/ci.yml`, cinco trabajos independientes). Corridas en verde:
[37656256703](https://github.com/rmdvcd/Shalev/actions/runs/37656256703) y
[37657849682](https://github.com/rmdvcd/Shalev/actions/runs/37657849682); las cifras coinciden entre corridas.

- **825 tests JVM, 0 fallos** — y los 3 archivos de tests instrumentados que no compilaban (`RepositoriosRoomTest`,
  `ConfiguracionInicialInstrumentedTest`, `RespaldoRoomTest`) vuelven a compilar.
- **Lint: 0 errores, 95 avisos** con el baseline vacío (se decidió no tapar los avisos con el baseline).
- **R8 sin clases ausentes**: APK release sin firmar 45,4 MB, APK debug 58,7 MB.
- **Permisos** del manifiesto fusionado dentro de la lista autorizada y **API mínima 26**: 3423 clases, 0 llamadas
  no permitidas.
- `gradlew` y los guiones de `tools/` estaban guardados sin permiso de ejecución (modo 644): el primer intento de CI
  murió con «Permission denied» (exit 126). Corregido.
- Versión a **0.30.0 (`versionCode` 51)**. El historial de verificaciones ya no vive en el README: vive en
  [VERIFICACION.md](VERIFICACION.md).
- **Pendiente (T0.6):** instalar el APK en un teléfono real y probar dos teléfonos (principal + secundaria).

## 0.30.1 — F1 (1/2): trabajo pesado fuera del hilo principal (2026-10-07)

- **T1.1** `@IoDispatcher` se traslada de `cu.shalev.data.di` a `cu.shalev.domain.di` (antes no se podía usar desde
  los casos de uso). `ObtenerGraficosPeriodo` y `ObtenerResumenGeneral` envuelven todo su cuerpo en
  `withContext(io)`: con el período «Año» ya no cargan, mapean y agregan miles de ventas en el hilo de UI.
- **T1.2** `flowOn(io)` en las seis cadenas reactivas que transformaban listas completas en el colector
  (`ObservarAlertas`, `ObservarInventario`, `ObservarRegistro`, `ObservarElaboracion`, `ObservarElaborados`,
  `ObservarServicios`).
- **T1.3** `debounce(250 ms)` en los tres buscadores que consultan Room por texto (Inventario, Registros y
  Servicios); el texto espera el retardo y el resto de filtros pasan al instante; con el texto vacío el
  retardo es 0 (abrir la pantalla no espera). El filtro que ve la UI sigue siendo inmediato. **Los buscadores
  de Precios y ModificarVenta NO** llevan debounce: filtran en memoria sobre un catálogo ya cargado, sin
  consulta que ahorrar (el plan decía 4 ViewModels; la realidad del código son 3).
- **T1.5 (esqueleto)** `RendimientoInicioTest` (instrumentado) que genera los 548 días del seed real y medirá
  la agregación de un año (umbral 250 ms) y los dos casos de uso de Inicio ya con `withContext(io)` (umbral
  3000 ms). Necesita teléfono: los números aparecerán en `docs/VERIFICACION.md`.
- **T1.4 (pendiente)** la agregación sigue siendo en memoria sobre la lista completa (la serie del año
  sigue haciendo `.entre(…).validas()`). Siguiente paso: `@Query` con `GROUP BY` por cubo y rango, manteniendo
  `Estadisticas` como autoridad en los tests y comparando SQL vs memoria.
- CI: **826 tests JVM, 0 fallos** (+1, el test nuevo del debounce); lint 0/95; R8 limpio; permisos OK; API 26 OK.
  Corrida verde: [37704972362](https://github.com/rmdvcd/Shalev/actions/runs/37704972362).

## Cambios solicitados — 2026-10-08 (pendiente de verificación)

- **Avisos y tablas:** el botón de restablecer avisos usa una flecha circular antihoraria; las tablas muestran el icono Compartir para sus acciones de salida. Los filtros de Inventario, Servicios y Registros usan listas desplegables. Las filas marcadas de Inventario, Servicios y la selección de productos de Precios permanecen fijadas antes del buscador y no se duplican en la lista, incluso si una búsqueda o filtro las oculta.
- **Gráficos:** las etiquetas de hora y mes usan los patrones breves del dispositivo; día/mes conserva el formato configurado.
- **Transferencia:** no se inventa un campo de importe para Transfermóvil: el total de la venta se ve debajo del QR oficial. Pago electrónico presenta logotipos orientativos solo para prefijos no ambiguos; la asignación, fuentes y advertencias de licencia están en [LOGOS_BANCOS.md](LOGOS_BANCOS.md).
- **SMS:** captura automática opcional a través de `NotificationListenerService`, solo mientras Shalev espera una transferencia, con habilitación manual de Acceso a notificaciones. No se solicita `READ_SMS`, no se consulta el buzón ni se conserva el cuerpo: al flujo de venta solo llegan el número y el importe reconocidos. Pegar o compartir el SMS sigue disponible.
- **Actualizaciones:** `gradle.properties` fija `shalevGithubRepo=rmdvcd/Shalev`; el build consulta la última Release pública del mismo repositorio (API de GitHub Releases) y descarga desde sus assets. Se puede sobrescribir para un fork. El repositorio es público, pero el 2026-10-08 aún no tenía Releases, así que falta publicar la primera APK firmada.
- **Pruebas añadidas/actualizadas:** formatos locales del gráfico, prefijos bancarios, selección fijada, filtros desplegables y captura SMS condicional. Las imágenes binarias de Roborazzi aún no se regrabaron. Pendiente ejecutar compilación, pruebas y capturas en un entorno con JDK; en esta sesión Gradle no encontró Java.

## Revisión de entradas y textos — 2026-10-09 (sin ejecutar)

- Los subtítulos simples de `ShalevListItem` separan los datos unidos por « · » o saltos de línea en renglones independientes. En Pago electrónico las acciones se apilan para no quitar todo el ancho al número junto al logo.
- `ShalevTextField` rechaza entradas ambiguas en dinero, decimales, enteros y porcentajes conservando el valor anterior; no elimina signos o letras para convertir un valor incorrecto en otro válido. Se admite coma decimal, estados parciales y borrado. El porcentaje queda limitado a 100. Tarjeta/cuenta admite hasta 20 cifras, coherente con el dominio.
- Interpretación del pedido: **venta > costo**. Validación al guardar productos e insumos vendibles, aviso inmediato del precio en el formulario de producto y rechazo de la cotización cuando un descuento (incluso acumulado) deja precio ≤ costo. También compara el costo actual de las recetas. No se reescriben precios históricos ni se elevan automáticamente los precios del usuario.
- Se conservan las implementaciones del 08/10 para Releases, iconos, combobox, selección fijada, fechas, QR, bancos y captura opcional de notificaciones. No se afirma verificación en dispositivo ni nueva investigación web de bancos en esta revisión.
- Se añaden casos de regresión para entradas ambiguas, cuentas de 20 cifras, venta igual/inferior al costo y descuentos. **No se compiló, no se ejecutó la app ni se corrieron pruebas**, por petición del usuario. Ver `docs/REVISION_2026-10-09.md` para la entrega a opencode CLI.

## Inicio, logos, Licencia y actualización — 2026-10-09 (sin compilar)

- **Logos de bancos:** caja única de 96 × 48 dp con esquinas `ShalevRadius.sm` y fondo blanco para BPA, BANDEC y BANMET (`MedidasLogoBanco`, `recursoLogoBanco`); BANDEC se recorta a sangre y los otros dos se ajustan con margen. Los PNG no cambian. Se volvió a contrastar el prefijo con fuentes públicas: no hay fuente oficial única, `9225`/`9235` los usan varios bancos y `9226` tiene atribuciones contradictorias; el mapeo no se cambió y se documenta en [LOGOS_BANCOS.md](LOGOS_BANCOS.md).
- **Inicio:** `ShalevAccordionCard` (nuevo en `:designsystem`) y `ShalevCard(ajustarAlContenido)`. Los gráficos y los Top 3 posteriores al selector de período son acordeones. Su estado vive en `AcordeonesInicio` con `remember` (no `rememberSaveable`): empiezan cerrados y vuelven a cerrarse al salir de Inicio y volver. «Nueva venta» a la izquierda y turno con interruptor a la derecha (apilados con letra muy grande). Teléfono y cuenta de Pago electrónico en seminegrita con `ShalevTextos.resaltar`.
- **Licencia:** tarjeta «Precios de la licencia» con los importes de `TipoLicencia` (base y por app secundaria), siempre visible antes del asistente.
- **Actualizaciones:** `RespaldoPrevio` crea un respaldo `.shalev` sin contraseña antes de instalar y lo restaura al abrir la versión nueva (`GestorActualizacion.alAbrir`); sin respaldo no se instala. Riesgo conocido: lo vendido entre el respaldo y la instalación se pierde al restaurar. Una app que ya estaba instalada antes de esta versión no hace el respaldo al actualizarse a esta, porque el código que lo hace es el nuevo. Los textos visibles de la actualización (y las notas de versión, filtradas con `TextosActualizacion.notasVisibles`) no mencionan repositorio ni alojamiento.
- **Pruebas:** `RespaldoPrevioTest`, `PreciosLicenciaTest`, casos nuevos en `PagoLogicTest`, un test instrumentado de acordeones y la captura `01j_inicio_acordeones_cerrados`. **No se compiló ni se ejecutó nada** (el entorno no tiene JDK ni Android SDK): `./gradlew shalevTests` sigue pendiente.

## Verificación 2026-10-09 en el PC del dueño (OpenCode, main @ 331a9cd)

- Se compiló (`:app:assembleDebug`), instaló y abrió `cu.shalev.app.debug` en un Redmi 23021RAAEG con Android 15 (API 35): Inicio, Inventario y Pago electrónico abren; la pantalla de Pago electrónico sale en negro en las capturas por `FLAG_SECURE` (regla 5, correcto).
- `./gradlew shalevTests` falló primero con 7 errores en `:domain` y 3 en `:app`, todos por la regla nueva **venta > costo** (revisión 2026-10-09): los fixtures vendían al costo o por debajo. Siguiendo la revisión, se actualizaron los fixtures para esperar rechazo, no la regla.
- **Seed:** las recetas del catálogo estaban en kg/L donde tocan g/mL (el costo de la receta salía 1000 veces mayor y toda venta de elaborados y servicios con insumos se rechazaba). Se aplicó el arreglo del dueño (`c2d3cf6`, rama `arena/3c116bea-shalev`): recetas en gramos/mL más sus invariantes en `CatalogoBodegaTest` (costo declarado y costo de receta menores que la venta).
- **Fixtures:** `VentaFlujoTest` (Refresco costo 20, Galleta venta 60 / costo 10), `TurnoUseCasesTest` (Pan costo 20), `VentaViewModelTest` y `ModificarVentaTest` (Galletas costo 20); mensaje esperado actualizado en `ProductoFormTest`; caso nuevo `ValidadoresTest.no se vende al costo ni por debajo` (producto 60/59/61 con costo 60, insumo vendible 10/11 con precio 10).
- Resultado: `./gradlew shalevTests` en verde (856 tests JVM, 0 fallos) y `./gradlew shalevCheck` en verde (lint 0 errores, 127 avisos; `shalevPermisos` OK; APK debug 58,8 MB).
- En el teléfono: Inicio y listas legibles con fuente normal y al 200 % (sin cortes); selección de Inventario fijada sobre el buscador con sus acciones; ficha de producto centrada con Compartir. Quedan `NO VERIFICADO` (requieren al dueño): dinero tecleado con coma y casos límite en el teléfono, descuentos acumulados en el dispositivo, QR de Transfermóvil con total debajo, SMS bancario real con Acceso a notificaciones, y Release firmada con sus assets (`shalevGithubRepo=rmdvcd/Shalev`).

## Verificación 2026-10-10 en el PC del dueño (OpenCode, main @ d2f51b4, PR #9)

- **Móvil:** `./gradlew shalevTests` en verde (**859 tests JVM, 0 fallos**: core 19, licencia 99, domain 243, data 86, designsystem 33, app 379) y `./gradlew shalevCheck` en verde (lint 0 errores, `shalevPermisos` OK; APK debug 62,5 MB). Instalado con `install -r` y abierto en el Redmi 23021RAAEG (Android 15): Inicio con acordeones, turno, alertas y Pago electrónico con cuenta enmascarada, todo legible.
- **Paso 3 de la revisión en el teléfono:** Inicio legible con fuente normal. Con fuente al 200 % la etiqueta `Cantidad` de Nueva venta se parte en dos líneas dentro del campo (`Canti/dad`); queda anotado como pendiente de T2 (textos que no se cortan). Fuente restaurada a 1.0. Se encontró un turno abierto con un carrito ajeno (datos del dueño) y se dejó intacto: el diálogo `¿Descartar la venta?` protege la salida con BACK.
- **Web (`desktop/`):** primera ejecución real de esta ampliación. Entorno `.venv` creado (Python 3.14.7), `requirements.txt` instalado, `unittest discover -s tests`: **84 pruebas OK** sin corregir nada. `build_windows.ps1` generó `dist/Shalev.exe` (29,3 MB, con `Shalev.exe.sha256`) y su `--self-test` interno pasó. El EXE arrancado con `--local` y carpeta temporal sirvió `http://127.0.0.1:8765` con HTTP 200 (acceso `Shalev · Mi negocio`, `Principal · Escritorio`, CSRF presente); la carpeta temporal se borró después. Sin tocar datos reales.
- Siguen `NO VERIFICADO` (requieren al dueño): dinero con coma y casos límite tecleados en el teléfono, descuentos acumulados y QR real de Transfermóvil, SMS bancario real, Release firmada, LAN Android↔PC con GL real, HTTPS con `shalev.minegocio.cu`, y validación visual en navegador real.

## Verificación 2026-10-11 en el PC del dueño (OpenCode, main @ 328cdfa, PR #10, Escritorio 0.31)

- El dueño fusionó la PR #10 después de la sesión anterior (incluyó mis entradas del 2026-10-10 en `desktop/README.md`; las capturas regeneradas quedaron sin commitear). El venv de ayer quedó viejo: el `requirements.txt` nuevo ya trae `tzdata==2026.5`, `pystray` y `six`; bastó `pip install -r requirements.txt`, sin tocar código.
- **Móvil:** `./gradlew shalevTests shalevCheck` en verde (**859 tests JVM, 0 fallos**; lint 0 errores, `shalevPermisos` OK). APK reinstalado y abierto en el Redmi: Inicio con banner de prueba, Pago electrónico enmascarado, acordeones; Registros con pestañas FlowRow en varias filas (0.30.1), ninguna cortada.
- **Instrumentados (primera ejecución real):** `:data` 50 tests, 46 OK y **4 fallos conocidos** en `ConfiguracionInicialInstrumentedTest` (migraciones 4/5/6→11 ausentes y versión esperada 9 vs real 11): es la deuda T3.2 ya documentada, no se tocó. `:app` (94 tests UI) **bloqueado por el dispositivo**: el primer test se cuelga (MIUI no trae la actividad al frente; el teléfono es el de uso diario con los datos del dueño, contra D6). Veredicto: UI instrumentada pendiente de emulador o teléfono dedicado.
- **Roborazzi:** `verify` inicial dio 101 diferencias (referencias del 08/10 + capturas nuevas sin referencia tras los cambios de UI). `record` regeneró `app/capturas` (286 PNG) y `verify --rerun-tasks` pasó en verde. Revisión visual propia: Inicio oscuro y Registros claros correctos y coincidentes con el teléfono; la revisión final de cada captura queda para el dueño.
- **Web:** `unittest` **103 tests OK (1 omitido)**. `build_windows.ps1` regeneró `dist/Shalev.exe` (30,1 MB, `.sha256` actualizado a mano porque el script no lo reescribió) con `--self-test` pasado.
- **Aceptación en Chrome real contra el EXE (datos temporales, borrados después):** abrir turno con fondo 1000, crear producto Pan (costo 20, venta 50, 100 u; la categoría es texto libre con sugerencias y frena el Guardar si falta), vender ×1 en efectivo (stock 99), corregir a ×2 con motivo (2 registros, válido 100.00), anular con motivo (válido 0.00, stock de vuelta a 100), doble anulación imposible (la ficha anulada solo muestra Cerrar), exportar inventario a PDF y XLSX válidos (hoja SPVI: Pan, 50, 20, 100, u), Tiempo en línea con uptime, y cierre de turno con **Cuadra** (1000 = 1000).
- **Observación menor (sin tocar):** la tarjeta de producto sin foto pide `/api/photo/...` y el servidor devuelve 404 que ensucia la consola (3–7 errores). Decisión del dueño: no pedir la foto si no existe, o devolver 204.
- Siguen `NO VERIFICADO`: instrumentados UI en dispositivo dedicado, SMS bancario real, Release firmada, LAN Android↔PC con GL real, HTTPS `shalev.minegocio.cu`, licencia PC GL real de 16 dígitos, y segunda instancia / recuperación de clave (puntos 6, 9, 10, 17 de VERIFICACION_PC).

## Cambio 2026-10-11 (dueño): Inicio con flotantes y pestañas horizontales

- **Pestañas de Registros** (de Ventas a Turnos): una sola fila horizontal desplazable (`ScrollableTabRow`), por decisión del dueño; se revierte el FlowRow de varias filas de la 0.30.1. Solo afecta a Registros (el resto usa ≤4 pestañas). `README.md` actualizado.
- **Inicio:** «Nueva venta» (izquierda) y «Movimiento de caja» (derecha) pasan a botones flotantes (`ShalevFab`) en las esquinas inferiores, visibles solo con el turno abierto (y con permiso de vender). El contenedor del turno se ajusta a su contenido, centrado; la lista deja hueco abajo para que los flotantes no tapen la última fila. Se eliminó `AccionesVenta` de la fila del turno.
- Verificación: `shalevTests` verde, `verifyRoborazziDebug` verde (referencias regeneradas; la de Inicio muestra turno centrado y ambos flotantes), APK instalado y abierto en el Redmi (flotantes visibles con turno abierto).

## Bug 2026-10-11 (dueño): elegía «Secundaria» y arrancaba como principal

- **Causa:** `VinculacionViewModel.pedirSecundaria()` (y `qrLeido()`) leían `eligioSecundaria.value` y `tipoRepo.tipo.value` de forma síncrona. Al abrirse la vinculación sola justo tras el recorrido inicial, el DataStore aún no había emitido y se leía el inicial (`false`) → «Esta app es la principal del negocio…» aunque se había elegido «Secundaria». La bandera en memoria (`TourPendiente`) sí llegaba; el que fallaba era el dato persistido recién guardado.
- **Corrección (`app/.../vinculacion/VinculacionViewModel.kt`):** la regla se evalúa con los valores reales (`base.first()`, suspendida); `pedirSecundaria()` espera dentro de una corrutina. Sin cambios de firmas ni de comportamiento visible salvo el bug. `shalevTests` verde (859/859), APK reinstalado en el Redmi con los datos intactos.
- Para probarlo en un teléfono hace falta borrar sus datos (el tipo queda fijado al instalarse): solo con respaldo previo, o mejor en el teléfono del empleado.

## Cambio 2026-10-11 (dueño): respaldo .shalev solo en almacenamiento interno

- **Regla nueva:** exportar e importar el `.shalev` solo hacia y desde el almacenamiento interno; la ubicación la elige el usuario con el gestor de archivos del dispositivo.
- **Quitado:** botón «Enviar a otra app» (WhatsApp, Telegram, Drive…) y la recepción de `.shalev` compartidos o abiertos desde otras apps (`EventoRespaldo.Compartir`, `RespaldoViewModel.compartir()`, consumo de `Entrada.Archivo`, redirección de MainScaffold, `ArchivosApp.copiarRecibido`, filtros SEND/VIEW de `octet-stream` en el manifiesto y el string `importar_respaldo`). La licencia por texto compartido y el SMS de PAGOxMOVIL no cambian.
- **Queda:** «Guardar en el teléfono» y «Elegir archivo», que abren el gestor del dispositivo enraizado en Descargas internas y limitado a proveedores locales (`EXTRA_INITIAL_URI` + `EXTRA_LOCAL_ONLY`, sin nubes). PDF/Excel/imágenes se siguen enviando como antes.
- Verificación: `shalevTests` 856/856, `shalevCheck` verde (`shalevPermisos` OK con el manifiesto recortado), instrumentados compilan, `verifyRoborazziDebug` verde (referencias 09* regeneradas), APK instalado. Docs: FORMATOS, SECURITY, UI_UX_IX e historial.

## Bug 2026-10-13 (dueño): «Reiniciar Shalev» fallaba con «No module named '_sqlite3'»

- **Síntoma:** al pulsar Ajustes › «Reiniciar Shalev» en el EXE portable, el proceso nuevo moría con `ModuleNotFoundError: No module named '_sqlite3'` (`launcher.py:172`, vía `app.py` → `backups.py:6`). La app quedaba detenida.
- **Causa:** el proceso reiniciado arrancó con la extracción del ejecutable a medio hacer (carpeta temporal `_MEI*` con 28–30 de 810 archivos: estaban los tiempos de ejecución básicos pero faltaba `_sqlite3.pyd`). Los módulos empaquetados dentro del EXE cargan bien, pero las extensiones nativas viven en la carpeta temporal y aún no existían. En la repetición provocada se vio además al extractor detenido en ese punto sin ventana ni CPU (sin registro en Defender). Es una carrera en el arranque, no un paquete roto: el mismo EXE arranca bien cuando la extracción termina (comprobado: autoprueba y servicio correctos con PATH limpio, y 106 pruebas verdes).
- **Corrección (`desktop/launcher.py`):** al arrancar, el proceso espera (hasta 30 s) a que `_sqlite3.pyd` y `sqlite3.dll` existan en su carpeta temporal antes de importar la aplicación; si se agota el tiempo muestra un aviso en español («ábrelo de nuevo») en lugar del diálogo técnico. Además `relaunch()` termina el proceso viejo con `os._exit(0)` tras lanzar al nuevo (la limpieza ya terminó), para no dejarlo colgado. Prueba nueva `desktop/tests/test_launcher.py` (3 pruebas). EXE reconstruido con `build_windows.ps1` (106 pruebas OK con 1 omitida + autoprueba del ejecutable verde; `.sha256` reescrito a mano porque al script en segundo plano le falló `Get-FileHash`) y app relanzada con los datos intactos (verificado: 810 archivos extraídos y API con estado real).
- **Uso:** si al reiniciar aparece el aviso en español, ciérralo y abre Shalev de nuevo (o usa Salir y el acceso directo). Si se repite, avisa: el paso siguiente sería compilar en modo carpeta (sin extracción) o excluir la carpeta del antivirus.
- **Nota:** cada arranque del EXE deja una carpeta `_MEI*` en `%TEMP%` que solo se borra al cerrarse bien; tras cuelgues o apagados conviene borrar las sobrantes. Se limpiaron 8 pendientes.

### Principal web independiente — ampliación en desarrollo

- Incorporados modelo canónico Android, servidor LAN principal, empleados, permisos, fondos,
  QR temporal, cliente GL y respaldo compartido con separación de licencia y emparejamientos.
- Añadida administración web, carrito agrupado, cálculo local de preajustes y recetas; los
  elaborados no descuentan existencias propias. Anular registra devolución del consumo original.
- Cuentas asignables por empleado; eliminar una cuenta limpia las asignaciones afectadas.
- Añadidas regresiones de negocio y guía OpenCode CLI para PC. No se han ejecutado pruebas,
  compilaciones ni validadores en esta fase por indicación del propietario.
- Pendientes de funcionalidad e interoperabilidad documentados expresamente; no se declara
  paridad completa ni servidor compatible verificado antes de probar con Android real.

### 2026-10-09 — Implementación de pendientes de la principal web

- Sustituido el carrito local por una operación canónica compartida: agrupación, insumos sueltos,
  recetas, preajustes, cotización sin persistencia y corrección transaccional en el turno original.
- Transferencias con datos congelados y cuentas asignadas; clientes fijos editables y sugerencias.
  Las correcciones remotas se entregan con `CambioVenta` y se confirman por UUID.
- Editor de metadatos, fotos normalizadas, archivo/restauración de artículos, selección de productos
  en preajustes, perfil/avisos y consulta ampliable de movimientos y turnos.
- Idempotencia HTTP persistida con la mutación; comprobación de identidad del negocio tras restaurar.
  Rechazo de UUID remoto reutilizado con otro contenido; permisos actuales no borran ventas offline.
- Validación de DTO anidados y relaciones, límites de fotos, cuentas 12–20 cifras y teléfonos E.164.
  La instantánea no expone otras cuentas ni datos personales del dueño a la secundaria.
- Sesión TCP anterior conservada hasta prueba criptográfica de la nueva. Diagnóstico sin payloads.
- Recuperación de clave por consola, invalidación de sesiones y bloqueo de doble instancia.
- `.shalevdesk` v2/`.shalev` con extensión web de fotos; límite del contenedor desktop elevado a 128 MiB.
  Android no conserva la extensión `webFotos`: no se afirma transferencia de fotos al móvil.
- Pruebas nuevas y autoprueba de empaquetado ampliadas; guía de OpenCode CLI actualizada.
  **No se ejecutaron pruebas, validadores, compilación, CI ni empaquetado en esta fase.**

## Contraparte iOS nativa `IShalev` — 2026-10-24

- Se creó `IShalev/` como proyecto SwiftUI/Xcode independiente (target iOS 17+), conservando la paleta, iconografía y tipografía de marca del Android.
- Incluye Inicio y gráficos, apertura/cierre de turno, caja, catálogo de productos/insumos/elaborados con recetas, servicios, carrito, cobro efectivo/Transfermóvil con QR compatible, clientes fijos, registros, ajustes de precios y datos de perfil/cobro.
- Los importes del dominio se almacenan en centavos; persistencia local JSON cifrada con AES-GCM y clave en Keychain; respaldo iOS con contraseña independiente del `.shalev` Android.
- Se añadió `.github/workflows/ios-build.yml` para compilar en runner macOS y publicar una app de simulador como artefacto para Appetize.
- Revisión estática: nueve fuentes Swift pasan Tree-sitter; `project.pbxproj`, scheme, `Info.plist`, manifiestos de recursos y YAML del workflow pasan parseo estructural. Esto no equivale a type-check ni compilación.
- **No compilado ni ejecutado:** este entorno no tiene Xcode ni simulador iOS; el primer build queda pendiente del workflow macOS. La licencia GL, la LAN entre teléfonos, captura de SMS/notificaciones, edición/anulación de ventas y exportaciones PDF/Excel Android no se portaron en esta fase. `IShalev/README.md` documenta límites y pasos de compilación/emulación gratuita.

## IShalev — alineación visual con Android: Inicio, Venta y Ajustes (2026-10-26)

Motivo: al ejecutar la build de simulador en Appetize se comprobó que la app iOS se veía y se usaba
distinto de la Android. Se corrigieron las tres pantallas de mayor impacto sin tocar el dominio, la
persistencia ni la licencia; el objetivo fue acercar estructura y textos, no portar los iconos.

- **Inicio**
  - El bloque superior (turno, selector de período y banner de licencia) ya no se desplaza; el resto
    de la pantalla sí, como en Android.
  - El turno muestra «Desde las HH:MM» y el efectivo esperado, y toda la fila abre o cierra el turno
    con un interruptor a la derecha.
  - El selector de período añade **Turno** (el abierto o el último cerrado) como opción por defecto,
    además de Hoy, Esta semana, Este mes y Este año.
  - Banner de licencia en Inicio para la prueba y las licencias con vencimiento; la perpetua no lo
    muestra. Tocar el banner abre Licencia.
  - Accesos con el teléfono y la cuenta enmascarada («Cuenta •••• 1234») en Pago electrónico y el
    número de ajustes activos en Precios.
  - «Top 3» pasa a tres acordeones independientes (Más vendido, Lento movimiento y Rentabilidad) con
    medallas oro, plata y bronce; se ocultan si no hay ventas en el período.
  - **Dos botones flotantes** en las esquinas inferiores (Nueva venta y Movimiento de caja) y solo con
    el turno abierto. «Nueva venta» pregunta Productos o Servicios cuando ambos módulos están activos.
- **Venta**
  - La venta ocupa toda la pantalla en lugar de una hoja: título por paso, total centrado arriba y
    «Turno abierto · Desde las HH:MM · <operador>».
  - El recorrido empieza en el **carrito**: cada línea con menos, cantidad, más y papelera; «Agregar
    artículos» abre el catálogo en otra hoja. Vacío, el carrito muestra el estado vacío y su botón.
  - El método de pago son dos botones (Efectivo y Transferencia), no un selector segmentado.
  - Barra de acciones: **Continuar** en el carrito; cancelar y confirmar en el comprobante; cancelar y
    «Pago recibido» en el QR; volver al QR y «Registrar la venta» en los datos del cliente. La venta
    en efectivo pasa por el comprobante antes de registrarse.
- **Ajustes**
  - Una sola lista con el orden de Android: Completar configuración, Perfil, Licencia, Apps vinculadas,
    Pago electrónico, Precios, Permisos, Acceso con clave, Respaldo, Actualizar, Ayuda y Soporte.
  - «Completar configuración» aparece solo si faltan datos (negocio, titular o cobro), con la insignia
    del número de pasos, y reabre el asistente con los datos actuales.
  - «Acceso con clave» es una fila con interruptor y explicación, como en Android; se quitó el
    interruptor de la tarjeta de Privacidad para no duplicarlo.
  - Se quitó el nombre del negocio como subtítulo bajo «Ajustes» (Android muestra solo el título).
- **Sin cambios de alcance:** no se añadieron «Avisos de inventario» (iOS no tiene umbrales globales,
  solo por artículo) ni la selección múltiple, la hoja de filtros y el botón de compartir de Inventario.
  Los iconos siguen siendo SF Symbols, no los `ShalevIcons` dibujados de Android.
- Verificación: `Compilación iOS de IShalev` en verde para los commits `a1b67b5` (Inicio), `8dac2a3`
  (Venta) y `88093c8` (Ajustes); pruebas de dominio y compilación para simulador en los tres. No hubo
  sesión interactiva en simulador: la comprobación visual queda pendiente de Appetize o un iPhone.

## IShalev — Inventario, Registros, Servicios y asistente inicial (2026-10-26)

Segunda pasada de alineación con la app Android. Se conserva el dominio, la persistencia y la licencia;
solo cambian pantallas y se añaden filtros que ya existían en Android.

- **Inventario**
  - El filtro deja de ser una fila de fichas y pasa a la barra superior (insignia cuando hay filtros
    activos), con una hoja por **tipo** (todos, productos, insumos, elaborados) y por **alerta** (stock
    crítico, stock bajo, sin existencia, próximo a caducar). La búsqueda también mira la descripción.
  - Contador «N productos» o «N de M productos» y chip para quitar el filtro.
  - **Selección múltiple**: casilla en cada fila, barra con «N seleccionados», marcar/quitar todo,
    exportar y eliminar. El botón «+» se oculta mientras hay selección.
  - **Ficha del artículo** al tocar la fila (antes se abría el formulario directamente): datos, costo,
    precio, ganancia por unidad y acciones Editar / Compartir (PDF de la ficha) / Eliminar.
  - Exportación del inventario (o de lo seleccionado) en XLSX, CSV o PDF desde la propia pantalla.
  - Fila con barra de estado, nombre, aviso (crítico, bajo, sin existencia, vence), existencias con
    unidad, categoría y, a la derecha, precio de venta con el costo debajo en un tono más suave.
- **Registros**
  - Barra superior centrada con **filtrar** (insignia) y exportar, como Android.
  - Filtro por **fecha** (todo, hoy, 7 días, este mes o rango personalizado) y por **importe** mínimo y
    máximo, con las mismas validaciones de fechas e importes invertidos. Se aplica a ventas,
    transferencias, movimientos y turnos; el chip del filtro activo permite quitarlo.
  - Estados vacíos propios de cada pestaña («Aún no hay ventas», «Aún no hay transferencias»…) y
    «Sin resultados» con «Quitar filtros» cuando hay búsqueda o filtro.
- **Servicios**
  - El filtro por tipo sale de la fila de fichas y pasa a la barra superior, con hoja por tipo y chip
    del filtro activo; contador centrado.
- **Asistente inicial**
  - El formulario único pasa a **cuatro pasos**: bienvenida, módulos del negocio, datos (negocio,
    titular, Transfermóvil) y resumen. Cabecera con «Paso N de M», barra de progreso, «Atrás» y
    «Guardar y comenzar».
  - «Completar configuración» en Ajustes abre el asistente directamente en el paso de datos.
  - No se portó el paso «tipo de app» (principal/secundaria) ni los pasos de empleados y prueba del
    asistente Android: en iOS la vinculación se sigue haciendo desde Ajustes → Apps vinculadas y la
    licencia tiene su propia pantalla.
- **Verificación:** `Compilación iOS de IShalev` en verde para `0f59151` (Inventario), `a20fa5c`
  (Registros), `1269093` (asistente) y `8a17a74` (Servicios). Sin sesión interactiva en simulador.

### Licencia y Apps vinculadas (2026-10-26)

- **Licencia** deja de ser un formulario largo y pasa a tener la misma estructura que Android: estado y precios
  fijos arriba y tres pasos con barra de progreso.
  - **Revisa tus datos**: resumen tomado del Perfil y de Pago electrónico, con «Editar» para abrir el
    formulario (nombre, apellidos, carné y teléfono) y las mismas validaciones de Android.
  - **Pide la licencia**: tipo con su precio, selector de apps secundarias (− n +) con el total desglosado,
    recuperación de la licencia de otro teléfono cuando aplica, envío por WhatsApp o SMS y botón para crear
    la solicitud cifrada. Si ya hay licencia instalada aparece «Renovar igual» con su precio.
  - **Activa la licencia**: mensaje, «Pegar» y «Activar» con los mismos avisos de resultado.
  - En modo bloqueo se añade la tarjeta de aviso y «Contactar soporte», y se oculta «Cerrar».
- **Apps vinculadas** se reorganiza como en Android: descripción, tarjeta de red local con la dirección del
  teléfono, «Secundarias (N de 10)», filas con icono, nombre y estado («Conectada ahora», «Cierre pedido · se
  cerrará al conectar», «Última vez: …») y botón flotante para agregar. En la app secundaria: tarjeta con
  negocio y empleado, resumen de envío y última sincronización, botón circular para sincronizar, «Lo que
  puedes hacer» con sus permisos y los avisos de cierre como tarjetas.

## IShalev — Perfil, Pago electrónico, Precios y Ayuda (2026-10-26)

Tercera pasada de alineación con Android. Se sigue con el mismo criterio: solo cambian pantallas y textos;
el dominio, la persistencia y la licencia quedan igual.

- **Perfil**
  - Aviso superior con la explicación de Android y tarjeta con **Nombre**, **Apellidos** y **Carné de
    identidad (CI)**, con las mismas validaciones (2–80 letras; 5–20 letras o números) y el texto «Solo se
    envía al pedir la licencia».
  - «Guardar» se habilita solo cuando hay cambios; al cerrar con cambios pendientes aparece «¿Salir sin
    guardar?».
  - **Tu negocio** pasa de Ajustes a Perfil, como en Android: Ventas, Inventario y Servicios en una tarjeta
    con casillas. Ventas arrastra Inventario, desmarcar Inventario desmarca Ventas y no se puede dejar la app
    sin ningún módulo.
- **Pago electrónico**
  - Dos listas al estilo Android: **Teléfonos** y **Tarjetas y cuentas**, con casilla de selección, fila
    resaltada para el elemento en uso, lápiz y papelera.
  - Las tarjetas muestran `•••• 6454` y, debajo, el alias y la sigla del banco estimada por el prefijo
    (BPA 9205, BANDEC 9225, BANMET 9226), con el aviso de que no se verifica el banco ni la titularidad.
  - **+** flotante que despliega «Teléfono» y «Tarjeta o cuenta»; hoja de alta/edición con validación
    (teléfono válido, 12–20 dígitos, duplicados) y confirmación antes de borrar.
  - Cada cambio se guarda al momento, como en Android.
- **Precios**
  - Aviso tonal con la explicación, filas con indicador de activo, porcentaje con signo a la derecha
    («+5 %», «-10 %») e interruptor; estado vacío con los textos de Android y botón flotante que solo
    aparece cuando hay productos.
- **Ayuda**
  - Se porta el manual completo de Android: secciones con icono, título y detalle, y temas desplegables con
    los pasos numerados. Los temas de inventario, ventas y servicios se ocultan si el módulo no está activo.
  - Los esquemas de barra inferior, turno, venta y respaldo se dibujan con formas, sin imágenes.
