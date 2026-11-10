# Auditoría comparativa IPV/POS: Shalev Android, IShalev y DShalev

- **Fecha:** 2026-11-10
- **Revisión inicial:** `main` en `e602ba4` (base de este checkout); remediación en la rama `arena/ddc0bb64-shalev`
- **Referencia:** skill IPV/POS adjunta a la solicitud.
- **Objeto:** contrastar operación offline, movimientos e idempotencia, SKU/códigos de barras, compras y recepción, checkout, caja, informes, locales, roles, seguridad y PCI.

> **Conclusión ejecutiva (hallazgos en la revisión inicial).** Android e iOS muestran una base móvil offline-first con almacenamiento local, operaciones de venta ligadas al inventario y colas/identificadores para sincronizar. DShalev tiene validaciones y transacciones de venta en servidor, pero no convierte el navegador en un POS offline: conserva un borrador de carrito, no una venta pendiente de envío. La revisión inicial identificó riesgos en la precisión y trazabilidad del inventario fraccionario de DShalev, la repetición de una venta web tras un resultado de red ambiguo y la falta de atribución/anulación auditable de ventas web. **Actualización de remediación (2026-11-10, esta rama):** esos tres hallazgos se corrigieron en DShalev; Android añade recibos persistentes de comandos. Falta ejecutar la suite instrumentada Android. Siguen sin SKU/barcode, flujo de compra/recepción ni estado de sincronización visible completo; estos puntos no se inventan sin decisiones de negocio. **Este informe no declara cumplimiento PCI DSS ni evalúa una certificación.**

## Alcance y método

- Revisión estática de los modelos, repositorios, rutas y pantallas relevantes de las tres aplicaciones, contrastada con la skill IPV/POS. Se incluyen referencias `ruta:línea` para facilitar la comprobación.
- En la pasada inicial se ejecutaron las suites disponibles de DShalev y no se modificó lógica de aplicación. Después, a solicitud del usuario, esta rama implementó remediaciones concretas; su estado y verificaciones se anotan al pie de cada hallazgo.
- **Comprobado** significa visible en código o reproducido en una prueba puntual. **Brecha/no encontrado** significa que no se encontró implementación en el código revisado; no prueba que no exista un proceso externo del comercio. **Pendiente de validar** indica que requiere toolchain, dispositivo, red o proceso real.
- No se hicieron pruebas en teléfonos físicos, terminales de pago, redes de tienda/Internet, Windows empaquetado, restauración de respaldos en otros equipos ni integración real Android↔iOS↔DShalev. La revisión no es una prueba de penetración ni una auditoría formal de controles.

## Matriz resumida

| Dominio de la skill | Shalev Android | IShalev (iOS) | DShalev (escritorio) |
|---|---|---|---|
| Operación offline | Venta local durante el turno; sincronización diferida de secundarias | Venta y datos locales; sincronización LAN diferida | El POS del navegador requiere servidor alcanzable; `sessionStorage` restaura el borrador, no una venta offline |
| Inventario e idempotencia | Movimientos y ventas transaccionales; recibos persistentes para comandos con clave. Una venta secundaria puede dejar una existencia negativa en la principal si compite con otras ventas | Existencias enteras en milésimas, movimientos y registros UUID/sync | Existencias canónicas en milésimas, libro de movimientos append-only, POS idempotente y atribuido; siguen pendientes las ventas offline |
| SKU / código de barras | Campo no encontrado; buscador por nombre, categoría y descripción | Campo no encontrado; buscador por nombre, categoría y descripción | Campo/columna no encontrado; búsqueda por nombre |
| Compras y recepción | No se encontró orden de compra, proveedor ni recepción de artículos | No se encontró flujo de recepción de inventario/proveedores | No se encontró orden/recepción; hay fichas de costo y movimientos de efectivo, que no equivalen a recibir mercancía |
| Checkout | Venta exige turno y aplica reglas de stock; operación secundaria puede quedar pendiente de sincronización | Venta local, comprobación de stock y cambios/anulaciones con motivo en los casos previstos | Validaciones de precio, turno, stock e insumos; clave idempotente evita duplicados tras reintento, pero el navegador sigue requiriendo conexión |
| Caja / arqueo | Apertura, entradas/salidas, conteo y diferencia | Apertura, entradas/salidas, conteo y diferencia | Apertura, movimientos, contado/diferencia; no se encontró conteo de inventario en el cierre web |
| Informes | Más vendidos, lento movimiento, rentabilidad, servicios, empleados/clientes | Ventas, ganancia y rankings | Ventas por período y CSV/XLSX; Top 3 solo de ventas web, no se encontró informe de exactitud/rotación de inventario |
| Locales | Datos etiquetados por local | Productos, ventas y movimientos asociados a local | Locales separados por arranque/base; sin selector ni consolidación, con advertencia de no ejecutar dos procesos sobre el mismo local |
| Roles | Cinco permisos base de secundaria y verificación de comandos en principal | Permisos locales más granulares y PIN protegido; no se sincronizan con Android | Esquema `admin/staff`, pero el alta web crea admin y no se encontró flujo de altas de `staff`; ventas web ya registran actor |
| PCI / pagos | Efectivo y transferencia manual | Efectivo y transferencia manual; datos de destino de transferencia congelados en venta | Efectivo y transferencia; no se encontró adquirencia/terminal ni liquidación de lotes |

La matriz refleja código inspeccionado, no paridad certificada ni resultado de pruebas end-to-end.

## Hallazgos priorizados

### Hallazgo P1 (remediado en DShalev) — Integridad del inventario fraccionario y ausencia de libro de movimientos

**Hechos de la revisión inicial (antes de la remediación)**

- En Android, los productos se almacenan en unidades enteras y los insumos en milésimas. Las altas, ediciones y ajustes de existencias se guardan junto con movimientos; al editar una cantidad, el motivo se guarda como el texto genérico `Edición` (`domain/src/main/kotlin/cu/shalev/domain/model/Producto.kt:8-27`, `data/src/main/kotlin/cu/shalev/data/repository/ProductoRepositoryImpl.kt:49-80`, `data/src/main/kotlin/cu/shalev/data/repository/InsumoRepositoryImpl.kt:45-78`). La venta agrupa líneas y descuenta producto/insumo dentro de una transacción; si falta stock, revierte la operación (`data/src/main/kotlin/cu/shalev/data/repository/VentaRepositoryImpl.kt:36-46, 72-110`).
- IShalev usa `Int64` en milésimas y conserva movimientos con fecha, razón, turno, actor y local (`IShalev/Sources/Domain/Domain.swift:207-238, 314-327`). El cierre y los movimientos por venta actualizan el estado y escriben el movimiento correspondiente antes de persistir (`IShalev/Sources/Domain/Domain.swift:1450-1498, 1930-1985`). Al editar stock desde la ficha, el motivo puede ser el genérico `Ajuste manual` (`IShalev/Sources/Domain/Domain.swift:1225-1238`); el flujo de ajuste dedicado sí exige motivo (`IShalev/Sources/Domain/Domain.swift:1254-1267`).
- En DShalev, el esquema declara `items.stock INTEGER` sin escala fija ni restricción entera estricta (`DShalev/dshalev/database.py:31-43`). Para insumos, el validador Decimal devuelve `float` (`DShalev/dshalev/app.py:181-188`) y los movimientos de recetas descuentan fracciones (`DShalev/dshalev/app.py:869-889`). La edición web sobrescribe `stock` en una transacción, pero no crea un evento de inventario ni solicita motivo (`DShalev/dshalev/app.py:624-653`); el esquema inspeccionado no contiene una tabla general de movimientos de stock.
- Se reprodujo en SQLite el caso equivalente `0.3 - 0.1`: el tipo `INTEGER` con afinidad SQLite conserva `0.19999999999999998` como `REAL`; al convertir ese valor a milésimas con truncamiento da `199`, no `200`. El código convierte de esa forma el saldo de insumo al revisar receta (`DShalev/dshalev/app.py:871-875`). Es una reproducción aislada del riesgo de representación/truncamiento, no evidencia de pérdida histórica en datos de producción.

**Prioridad original:** alta para DShalev por posible deriva de existencias y falta de evidencia para explicar/reconstruir ajustes. **Estado actual:** remediado y cubierto por la suite Python de DShalev; permanecen los riesgos distintos enumerados en el estado de remediación y en los hallazgos P2.

**Recomendación inicial:** migrar cantidades fraccionarias a enteros en escala fija (por ejemplo, milésimas), usar aritmética decimal/entera de extremo a extremo, añadir restricciones y migración con conciliación de saldos; registrar cambios de inventario como eventos append-only con actor, motivo, origen, referencia y saldo resultante.

**Estado tras remediación (2026-11-10):** implementado en DShalev con `stock_milli`, `low_stock_milli` y `critical_stock_milli` enteros, migración desde los campos heredados, restricciones de rango, aritmética decimal de milésimas y tabla `inventory_movements` con triggers que impiden UPDATE/DELETE. El saldo anterior se registra como `MIGRACION` sin inventar historia. El API conserva `stock`/`low_stock` y añade los valores enteros canónicos. El ajuste inicial, los formularios, POS, recetas, correcciones, anulaciones y sincronización registran eventos; pruebas cubren migración, fracciones, límites, libro append-only y exportación CSV (**112 pruebas Python pasan**). Se mantiene la advertencia de que productos vendidos en secundarias pueden quedar negativos en la principal por concurrencia offline; esa conciliación no se cambió.

### Hallazgo P1 (remediado en DShalev) — La venta web no tenía idempotencia de reintento

**Hechos de la revisión inicial (antes de la remediación)**

- La venta web de DShalev se crea con `POST /ventas/nueva`; valida sesión/CSRF y ejecuta cabecera, líneas y descuentos dentro de una transacción (`DShalev/dshalev/app.py:807-814, 843-889`). Sin embargo, la inserción no incluye UUID ni clave idempotente (`DShalev/dshalev/app.py:876-879`), y la tabla `sales` no define esa clave (`DShalev/dshalev/database.py:148-165`). El carrito guardado por `pos.js` en `sessionStorage` contiene solo artículos, cliente y método de pago, y se borra al confirmarse el guardado (`DShalev/dshalev/static/pos.js:23-45`). El README advierte que, tras una interrupción al registrar, se consulte el historial antes de volver a intentar (`DShalev/README.md:85-89`).
- En Android, la venta tiene UUID y estado de sincronización; la principal omite UUID ya recibidos al aplicar el lote (`data/src/main/kotlin/cu/shalev/data/repository/VentaRepositoryImpl.kt:55-64`, `data/src/main/kotlin/cu/shalev/data/sync/AlmacenSync.kt:168-174`). IShalev mantiene ID UUID y bandera `synced` en la venta (`IShalev/Sources/Domain/Domain.swift:280-305`), con sincronización diferida descrita en `IShalev/README.md:32`.
- **Controles de checkout observados:** Android exige turno abierto y aplica el descuento en la transacción de la venta (`data/src/main/kotlin/cu/shalev/data/repository/VentaRepositoryImpl.kt:50-53, 72-110`); IShalev exige turno, calcula una vista previa y aplica stock antes de confirmar la nueva venta (`IShalev/Sources/Domain/Domain.swift:1640-1684, 1930-1985`). DShalev valida método de pago, turno, precio, stock e insumos antes de confirmar (`DShalev/dshalev/app.py:840-889`). Son controles contra ventas inválidas o descuentos parciales, pero no sustituyen idempotencia ante reintento.

**Riesgo original:** si el servidor persistía una venta pero se perdía la respuesta, el cajero podía reintentar y crear otra venta. Ese riesgo se mitiga en DShalev con la clave persistente descrita a continuación; las pruebas ejecutadas no sustituyen fallos de red en producción.

**Recomendación inicial:** asignar un UUID a cada intento lógico de venta en el cliente; persistirlo con restricción única y respuesta en el servidor dentro de la misma transacción. Repetir la misma clave y el mismo contenido debe devolver el resultado original; reutilizarla con contenido distinto debe rechazarse.

**Estado tras remediación (2026-11-10):** el formulario POS guarda la clave de intento en el borrador y el servidor persiste la respuesta y valida la huella dentro de la transacción de venta. Un replay idéntico no descuenta dos veces; una clave con otro contenido se rechaza. Pruebas cubren replay al recrear app/base, conflicto de contenido, rutas con JavaScript/no JavaScript y el ciclo de checkout. DShalev pasa las 112 pruebas Python y 6 pruebas Node.

### Hallazgo P1 (remediado para nuevas ventas web) — Atribución y reversión insuficientes

**Hechos de la revisión inicial (antes de la remediación)**

- La tabla web de ventas no tiene un campo dedicado a `user_id`/actor ni campos de anulación web (`DShalev/dshalev/database.py:148-157`). El manejador web crea la venta sin guardar el usuario autenticado (`DShalev/dshalev/app.py:807-814, 876-879`). Aunque existe una columna `employee_id`, el formulario web no la establece; el campo corresponde también al esquema móvil.
- En el historial de DShalev, `sales.html` ofrece anulación/corrección solo para ventas móviles elegibles; las ventas web muestran `—` (`DShalev/dshalev/templates/sales.html:30`). No se encontró una acción equivalente de reversión auditable para venta web.
- Android registra actor, UUID y anulaciones en la venta; IShalev conserva vendedor, estado y motivo de anulación/corrección (`data/src/main/kotlin/cu/shalev/data/repository/VentaRepositoryImpl.kt:59-64`; `IShalev/Sources/Domain/Domain.swift:280-305, 1687-1706`).

**Recomendación inicial:** guardar actor de la sesión en cada venta y movimiento; implementar reversión/corrección mediante eventos compensatorios con motivo, usuario, hora y vínculo a la operación original. Evitar borrar/modificar silenciosamente registros de venta. Incluir permisos y exportes de historial de auditoría.

**Estado tras remediación (2026-11-10):** las ventas web guardan `actor_id` y el registro de venta/stock queda atribuido; la anulación conserva la venta, agrega movimientos compensatorios con motivo, actor, hora y referencia, y los agregados de informes/efectivo excluyen anuladas. El historial mantiene su estado para mostrar anuladas. El suite incluye atribución y anulación/corrección; continúan pendientes las decisiones de permisos/roles web señaladas en P2.

### P2 — SKU/código de barras ausente (texto de búsqueda ya corregido)

**Hechos comprobados**

- El modelo Android `Producto` no incluye SKU ni barcode (`domain/src/main/kotlin/cu/shalev/domain/model/Producto.kt`); el filtro sí compara nombre, categoría y descripción (`domain/src/main/kotlin/cu/shalev/domain/service/InventarioFiltro.kt`) y el texto actual ya no promete buscar por código. Se amplió una prueba para comprobar búsqueda por los tres campos.
- IShalev tampoco tiene propiedad barcode/SKU; el placeholder se corrigió para coincidir con sus filtros de nombre, categoría y descripción (`IShalev/Sources/Features/InventoryView.swift`).
- El catálogo web DShalev no tiene columna de código en el esquema de artículos (`DShalev/dshalev/database.py`) y la búsqueda de artículos del POS aplica `LIKE` al nombre (`DShalev/dshalev/app.py`).
- **Estado:** corregidas las etiquetas engañosas en Android/iOS y verificada por inspección la implementación del filtro Android; el test JVM ampliado no se pudo ejecutar por falta de JDK. SKU/barcode sigue fuera del alcance implementado y requiere una definición de unicidad/local/variantes.

**Recomendación:** el texto engañoso ya se corrigió. Antes de implementar SKU/barcode, definir si el dato es SKU, GTIN/EAN/UPC u otro, cuántos códigos puede tener cada artículo y el alcance de unicidad entre locales/variantes; después añadir almacenamiento, índice, captura por escáner/cámara, alta manual y pruebas de duplicados y dígitos de control.

### P2 — Conflictos de sincronización móvil pueden producir saldo negativo en la principal

**Hechos comprobados**

- Android permite vender durante el turno sin conexión; antes de abrir otro turno, la secundaria sincroniza y exige catálogo/ventas al día (`domain/src/main/kotlin/cu/shalev/domain/usecase/TurnoUseCases.kt:40-43`). La principal aplica movimientos de ventas recibidas y añade `NOTA_SIN_EXISTENCIAS` si el saldo resultante queda negativo (`data/src/main/kotlin/cu/shalev/data/sync/AlmacenSync.kt:168-194, 377-379`). Esto cubre un conflicto entre ventas concurrentes/offline con una nota de revisión; no es una conciliación automática del saldo.
- Android mantiene estado y cantidad de operaciones pendientes en el estado de vinculación (`domain/src/main/kotlin/cu/shalev/domain/model/Vinculacion.kt:208-230`; `app/src/main/kotlin/cu/shalev/app/vinculacion/VinculacionScreen.kt:94-97`). No se encontró una señal persistente equivalente en la vista de checkout examinada que distinga claramente «guardado localmente» de «recibido por la principal».
- IShalev mantiene ventas sin sincronizar en almacenamiento local y sincroniza turnos, ventas, inventario y caja de forma diferida (`IShalev/Sources/Domain/Domain.swift:280-305`; `IShalev/README.md:32-33`). La sesión real iOS↔Android y el uso en dos dispositivos físicos siguen pendientes de validar (`IShalev/README.md:5, 111`).
- DShalev funciona como servidor web local; en modo remoto el cliente requiere conexión con el servidor. El borrador de sesión no es una cola offline de ventas (`DShalev/README.md:29, 85-89`).

**Recomendación:** mostrar en checkout y recibo el estado local/pendiente/confirmado, número de pendientes y última sincronización; alertar y abrir tarea de conciliación cuando haya saldo negativo o catálogo obsoleto. No tratar un movimiento como aplicado/confirmado hasta registrar su recibo de forma durable en ambos lados. Probar varios equipos vendiendo el último artículo y la recuperación tras cierre/reinicio.

### P2 — Idempotencia de comandos remotos y alcance de retención

**Hallazgo original:** las claves de comandos remotos podían perder su recibo por reinicio o poda; una misma clave con otro contenido debía rechazarse sin mutar el catálogo. Esto no afecta a la idempotencia de ventas móviles por UUID descrita arriba.

**Estado de implementación en esta rama (2026-11-10):**

- **Android:** eliminado el LRU `MemoriaComandos`. Room v17 guarda `(empleadoId, clave, huella SHA-256 de la acción, resultado)` y aplica la acción y el recibo dentro de una transacción SQLite. Un replay tras reiniciar la principal reproduce la respuesta; otra acción bajo la misma clave devuelve `clave_reutilizada` sin tocar el recibo original. La migración 16→17 es aditiva. Hay pruebas instrumentadas de reapertura de la base, conflicto de contenido, respuestas con error y reintentos concurrentes; no se pudieron ejecutar aquí por falta de JDK/Android SDK.
- **DShalev:** los recibos de `sync_commands` son persistentes, verifican la huella, se escriben junto con la mutación y ya no se podan a las 512 claves. La suite cubre 520 claves, replay tras cerrar/reabrir la base y conflicto de contenido sin repetir el ajuste.
- **Compatibilidad:** los clientes que ya envían UUID conservan la clave a través de los reintentos; los clientes SPVI antiguos que no mandan clave siguen ejecutando como antes y no obtienen deduplicación.
- **Retención:** los recibos no se podan, para no fijar una ventana de reintento arbitraria; las tablas crecerán con el uso. La importación de respaldo Android limpia los recibos porque reemplaza el estado de negocio, por lo que un respaldo no incluye el historial transitorio de comandos.

**Verificación pendiente:** ejecutar las pruebas instrumentadas Android en un entorno con JDK y SDK. Antes de definir una retención finita habría que acordar un límite de reintento compatible con las credenciales y el funcionamiento offline.

### P2 — No se encontró flujo de compra, proveedor ni recepción de mercancía

En el código revisado no se encontró un modelo/flujo de órdenes de compra, proveedor, recepción parcial ni cotejo entre pedido, recepción y factura para Android, iOS o DShalev. En Android, los movimientos de caja son entradas/salidas de efectivo con motivo, no líneas de inventario (`domain/src/main/kotlin/cu/shalev/domain/model/Turno.kt:131-149`). DShalev define `cash_movements` como tipo, importe y motivo (`DShalev/dshalev/database.py:114-122`); sus fichas de costo son hojas de costeo, no órdenes ni recepción (`DShalev/dshalev/database.py:83-105`).

**Decisión pendiente de negocio:** confirmar si compras y recepción forman parte del alcance requerido o si se procesan en otro sistema. Si se requieren, priorizar proveedor, orden, recepción parcial, costo real, diferencias, adjuntos y creación idempotente de movimientos de inventario con trazabilidad offline. Si no, documentarlo expresamente como fuera de alcance.

### P2 — Permisos no equivalentes entre aplicaciones; riesgo latente en cuentas web `staff`

- Android define cinco permisos base de secundaria y la app principal comprueba autorización antes de ejecutar comandos (`domain/src/main/kotlin/cu/shalev/domain/model/Vinculacion.kt:22-40`; `data/src/main/kotlin/cu/shalev/data/sync/EjecutorComandos.kt:22-45,101-105`). DShalev también valida permisos de comandos SPVI en el servidor (`DShalev/dshalev/sync_server.py:594-652`); la idempotencia de esos comandos se conserva en `sync_commands`, como se detalla en el hallazgo P2 correspondiente.
- IShalev tiene permisos locales más granulares, PIN de 6–12 dígitos, verificador PBKDF2 y bloqueo tras cinco fallos por 15 minutos; estos perfiles/permisos no se sincronizan con Android (`IShalev/Sources/Features/EmployeeSettingsView.swift:73-78`; `IShalev/Sources/Domain/Domain.swift:1102-1120`; `IShalev/README.md:110`). El mapeo iOS concede, por ejemplo, caja y registros al permiso base de venta (`IShalev/Sources/Domain/SyncManager.swift:115-122`). Debe confirmarse que esos derechos implícitos son una decisión intencional.
- DShalev declara roles `admin` y `staff` (`DShalev/dshalev/database.py:23-29`), pero el flujo de configuración crea solo el admin (`DShalev/dshalev/app.py:276-314`) y «Equipo» administra plazas móviles, no cuentas de personal del navegador (`DShalev/dshalev/templates/employees.html:1-10`). Varias rutas web comprobadas solo requieren sesión autenticada; `require_admin` existe y se usa en rutas seleccionadas (`DShalev/dshalev/app.py:146-152, 215-217`). No se encontró flujo para crear/configurar usuarios web `staff`.

**Recomendación:** publicar una matriz de permisos por operación y plataforma; alinear explícitamente venta, caja, consulta de registros, ajustes, anulaciones, exportación y administración. Antes de habilitar cuentas web `staff`, aplicar autorización del lado servidor a cada ruta (no solo ocultar controles) y probar accesos directos, CSRF, revocación y operaciones de alto riesgo. Mantener perfiles/PIN locales claramente diferenciados de las credenciales de sincronización.

### P2 — Multiubicación parcial, sin consolidación en DShalev

Android marca productos/insumos, turnos y ventas con local (`data/src/main/kotlin/cu/shalev/data/db/entity/Entities.kt:40-58, 98-114, 148-149`); IShalev asigna `localName` a productos y ventas (`IShalev/Sources/Domain/Domain.swift:207-218, 280-305`). DShalev inicia bases independientes por local, pero documenta que el selector visual, la administración integral y los informes consolidados no están implementados; también advierte no ejecutar dos procesos sobre el mismo local por falta de bloqueo entre procesos (`DShalev/README.md:16, 91-95`).

**Recomendación:** definir primero el modelo operativo de locales y la propiedad de cada saldo; después añadir selector, permisos por ubicación, reportes consolidados y transferencias entre locales como movimientos enlazados (salida y recepción), con prevención de doble aplicación. Mientras tanto, documentar que las bases independientes no son una vista consolidada del negocio.

### P2 — Arqueo no ciego y conteo de inventario de cierre parcial

Las tres interfaces muestran el efectivo esperado antes de registrar el conteo: en Android dentro del diálogo de cierre (`app/src/main/kotlin/cu/shalev/app/caja/CajaUi.kt:274-285`), en iOS en el paso de caja (`IShalev/Sources/Features/HomeView.swift:771-783`) y en el panel web DShalev (`DShalev/dshalev/templates/dashboard.html:13-15`). Esto facilita cuadrar, pero puede sesgar un conteo independiente. Android e iOS concilian al cierre solo los artículos movidos durante el turno; sus propios comentarios aclaran que lo demás se da por bueno (`data/src/main/kotlin/cu/shalev/data/repository/ConciliacionInventario.kt:14-19`; `IShalev/Sources/Domain/Domain.swift:1430-1447`). En el formulario de cierre web inspeccionado se registra efectivo contado, no un conteo de inventario (`DShalev/dshalev/templates/dashboard.html:14-15`).

**Recomendación:** ofrecer conteo ciego configurable, guardar diferencia, motivo y aprobación cuando aplique; agregar conteos físicos completos/cíclicos independientes del cierre de caja. No presentar el conteo parcial móvil como validación de todo el inventario.

### P2 — Diferencias de alcance en informes de escritorio

Android muestra tops de más vendido, lento movimiento, rentabilidad, servicios, empleados y clientes (`app/src/main/kotlin/cu/shalev/app/inicio/InicioScreen.kt:892-899`). IShalev muestra ventas, costos/ganancia y rankings (`IShalev/Sources/Features/HomeView.swift:394-498`). DShalev ofrece ventas por fechas y exportación CSV/XLSX (`DShalev/dshalev/app.py:1424-1510`); el gráfico diario combina ventas web y móviles ya sincronizadas, pero el Top 3 es solo de ventas de escritorio (`DShalev/dshalev/app.py:244-256`; `DShalev/dshalev/templates/dashboard.html:9-11`). No se encontró en el informe de escritorio examinado un informe de exactitud/diferencias de stock ni de rotación/margen por artículo comparable con las vistas móviles.

**Recomendación:** distinguir siempre fecha/origen/estado de sincronización y cancelación; decidir métricas de paridad que necesita cada rol; agregar informe de movimientos y conciliaciones de inventario antes de usar el dashboard como fuente única de control.

## Seguridad y alcance PCI

### Controles de seguridad observados

- Android configura Room con SQLCipher y una passphrase aleatoria envuelta por Android Keystore (`data/src/main/kotlin/cu/shalev/data/db/ShalevDatabase.kt:315-324`; `data/src/main/kotlin/cu/shalev/data/security/DatabasePassphrase.kt:10-31`). IShalev cifra el almacén local con AES-GCM y clave en Keychain (`IShalev/Sources/Domain/Domain.swift:2017-2038`; `IShalev/README.md:32-34`). DShalev cifra el snapshot SQLite con AES-GCM y en Windows protege la clave con DPAPI (`DShalev/dshalev/database.py:352-384, 295-313`). La ruta no Windows de desarrollo guarda la clave localmente sin el envoltorio DPAPI (`DShalev/dshalev/database.py:295-321`); no debe confundirse con protección de Windows.
- DShalev inicia en localhost por defecto; el modo LAN escucha en red y usa HTTP, mientras el modo remoto exige certificado/clave TLS (`DShalev/dshalev/config.py:34-40`; `DShalev/dshalev/run.py:50-73`). El README advierte no exponer HTTP a Internet y limitar LAN a red confiable o VPN (`DShalev/README.md:41-57`). No se comprobó la configuración física de firewall ni una instalación publicada.
- La existencia de cifrado en reposo, autenticación o TLS no demuestra por sí sola seguridad integral ni cumplimiento PCI.

### PCI DSS: no hay base para declarar cumplimiento

En los modelos y pantallas revisados, los métodos de pago disponibles son efectivo y transferencia; no se encontró integración de adquirente/terminal, captura de PAN para autorizar compras, procesamiento de tarjetas ni liquidación/cierre de lotes (`domain/src/main/kotlin/cu/shalev/domain/model/Venta.kt:8`; `IShalev/Sources/Domain/Domain.swift:138-140`; `DShalev/dshalev/database.py:148-165`; `IShalev/README.md:23-24`).

IShalev conserva un campo llamado `paymentCard` en la venta y guarda el número de una `TransferCard` del perfil, de 12–20 dígitos (`IShalev/Sources/Domain/Domain.swift:280-305, 433-480`). El contexto visible es el destino de una transferencia; **no hay evidencia en el código revisado de que sea un PAN de pago con tarjeta**. Confirmar su semántica, tratamiento y exigencias con el comercio/adquirente. Si fuera un PAN, minimizar almacenamiento y copias en historial/sincronización, y evaluar el alcance con el adquirente/QSA antes de conservarlo.

Por tanto, este repositorio y esta auditoría **no permiten concluir cumplimiento ni incumplimiento PCI DSS**. Si el comercio utiliza terminal externo, quedan fuera de esta revisión su inventario, control de acceso físico, inspecciones antimanipulación, conexión con adquirente, gestión de lotes y controles del proveedor; deben evaluarse con las partes responsables. No presentar estas apps como «PCI compliant» sobre la base de este informe.

## Verificación ejecutada y límites

- **DShalev:** `python -m py_compile` — OK; `pytest -q` — **112 passed**, 1 warning; `npm test` — **6/6**. Incluye movimientos de stock, aritmética en milésimas, POS idempotente, actor/anulación, comandos después de 520 claves y compatibilidad de los alias del API.
- **Android:** no se pudo compilar ni ejecutar pruebas aquí: `./gradlew :data:testDebugUnitTest` no inicia porque falta Java/JDK; `ANDROID_HOME` y `ANDROID_SDK_ROOT` están vacíos. El `identityHash` v16/v17 y el SQL 16→17 se contrastaron estáticamente contra los JSON versionados, y SQLite/Python confirmó que la migración produce el esquema de v17 para 21 entidades; esto no es una ejecución de Room/KSP. Se añadieron pruebas instrumentadas de Room v17, reapertura, conflicto de contenido y concurrencia, pero permanecen **pendientes de ejecución**.
- **iOS:** no se pudo compilar ni ejecutar pruebas aquí: Swift/Xcode no está instalado (`swift: command not found`).
- Los resultados locales no sustituyen pruebas visuales/teclado en navegador Windows ni pruebas en dispositivos, redes reales, base existente migrada o terminal de pago. No se afirma sincronización iOS↔Android comprobada en hardware.
- `git diff --check` se repite al terminar las ediciones de esta rama.

## Plan recomendado tras las remediaciones

1. **Verificación de plataforma:** ejecutar Room migration/instrumented tests con JDK/Android SDK; validar DShalev en Windows/LAN y probar cambios de respaldo, reinicio y recuperación de red en dispositivos.
2. **SKU/barcode:** solo tras definir formato, cantidad por artículo y unicidad entre local/variante; el texto de búsqueda engañoso ya fue corregido.
3. **Sincronización offline:** formalizar y mostrar estado local/pendiente/confirmado, última sincronización y procedimiento de conciliación cuando ventas concurrentes dejan saldo negativo.
4. **Decisiones de negocio:** confirmar si compras/recepción, roles web `staff`, múltiples locales consolidados y conteo ciego/inventario completo están dentro del alcance antes de implementarlos.
5. **PCI:** confirmar por escrito si se almacena algún PAN o dato de autenticación sensible y obtener del adquirente/asesor la determinación de alcance. No hacer afirmaciones de cumplimiento hasta completar una evaluación formal aplicable.
