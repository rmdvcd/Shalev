# Apps Principal y Secundaria (0.19.0)

Desde la 0.19.0, cada instalación de Shalev es una **app principal** o una **app secundaria**. Se configura en **Ajustes → Apps vinculadas** (sección Sistema, justo debajo de Licencia).

| | Principal | Secundaria |
|---|---|---|
| Quién la usa | El dueño, gerente o administrador | Cada empleado |
| Base de datos | La general: catálogo, todas las ventas y todos los turnos (también los de las secundarias) | Una copia del catálogo y solo sus propios turnos y ventas |
| Licencia | Se activa aquí (como siempre) | No se activa: la cubre la de la principal, con un máximo de **5** secundarias |
| Si la licencia de la principal vence | Pantalla de Licencia | Pantalla «La licencia de la app principal no está activa» (solo Sincronizar o Desvincular) |
| Ajustes visibles | Todos | Apps vinculadas, Consultas en internet, Permisos del teléfono, Ayuda, Soporte (y Precios si tiene ese permiso) |

Una instalación nueva o una actualización es **principal**. Una instalación pasa a secundaria solo al escanear el código de una principal.

## Decisiones del usuario (P38)

1. **Transporte: la red de los dos teléfonos, sin servidor ni relé.** Puede ser la wifi del local, la zona wifi del teléfono principal **o los datos móviles de la operadora (1.0.1)**. No hay servidor ni relé: la conexión es directa entre los dos teléfonos.
   - **1.0.1 (datos móviles).** Antes solo se anunciaban las IPv4 privadas y los textos pedían «la misma wifi». Ahora el QR `SPVI-VINC1:` lleva **todas** las IPv4 activas de la principal (privadas primero y después las de datos móviles, ver `RedLocal.utiles`), la secundaria prueba por orden y guarda como respaldo las direcciones del QR además de la que funcionó. El descubrimiento NSD (mDNS) sigue solo en la misma red local.
   - **Límite:** al no haber relé, la conexión por datos móviles depende de que la operadora enrute entre los dos teléfonos (en Cuba, CGNAT: puede no funcionar entre abonados). Con la misma wifi o zona wifi funciona como siempre.
2. **Permisos configurables por empleado** desde la principal:
   - Vender productos.
   - Vender servicios.
   - Editar inventario y servicios.
   - Cambiar precios.
   - Exportar y compartir.
   
   Los predeterminados son vender productos y vender servicios.
3. **Sin conexión, la secundaria sigue vendiendo hasta cerrar el turno.** No puede empezar un turno nuevo sin sincronizar. `AbrirTurno` llama a `PuertaTurno.antesDeAbrir()`: en la secundaria envía lo pendiente y recibe el catálogo; si no lo logra, devuelve `SinPrincipal(pendientes)`.
4. **Licencia:** las secundarias están cubiertas por la de la principal, con un máximo de 5 (`Vinculacion.MAX_SECUNDARIAS`). Dejan de funcionar si la licencia de la principal vence. La secundaria reevalúa cada minuto la licencia recibida, así que el vencimiento llega aunque no haya sincronizado.

## Suposiciones (regla v2: lo más seguro, accesible y simple)

- **Servicio en primer plano solo en la principal (0.19.2, autorizado por el usuario).** El cliente automático de las secundarias sigue viviendo mientras la app está abierta o en memoria: cada secundaria vende sin conexión y envía lo pendiente al volver a ver a la principal.
  - Si la principal no está disponible, las secundarias guardan todo y lo envían al volver a verla.
  - La regla 3 garantiza que nada se pierde entre turnos.
- **La app no activa la zona wifi.** Android no lo permite sin permisos extra; el usuario la activa a mano.
- **El código QR caduca a los 10 minutos y es de un solo uso.** Generar uno nuevo para la misma persona anula la clave anterior (por ejemplo, si cambió de teléfono).
- **Al vincular se borran los datos del teléfono secundario.** Se usa `MantenimientoRepository.borrarTodo`, igual que «Borrar todo». Solo ocurre si la vinculación tuvo éxito; si algo falla, no se toca nada. La app avisa antes.
- **Las fotos no viajan.** Al editar desde una secundaria se conserva la foto de la principal.
- **El nombre y el carné del dueño no viajan.** La secundaria recibe solo las tarjetas y teléfonos de cobro (para el QR de Transfermóvil). Su «Perfil» es el nombre del empleado, que queda en los turnos que abre y cierra.
- **Existencias negativas.** Si dos apps venden el último artículo a la vez, la principal acepta las dos ventas, deja la existencia en negativo y anota en el movimiento «Vendido en otra app sin existencias suficientes: revisa el conteo». Rechazar una venta ya cobrada sería peor.

## Arquitectura

```
domain/model/Vinculacion.kt           TipoApp, PermisoEmpleado, PermisosApp, Empleado, CodigoVinculacion,
                                      ModoSincronizacion, LicenciaPrincipal, Estado*, Vinculacion (reglas)
domain/repository/VinculacionRepositories.kt
                                      TipoAppRepository, PrincipalRepository, SecundariaRepository, PuertaTurno
data/db/dao/SyncDaos.kt               SyncDao (empleados, pendientes, aplicar por uuid, stock forzado)
data/sync/Protocolo.kt                Mensajes (kotlinx.serialization), Instantanea, Lote, Accion, CodigoQr
data/sync/CanalCifrado.kt             CriptoSync (ECDH/HKDF/HMAC), Tramas, CanalCifrado (AES-GCM), Saludos
data/sync/ConfigSync.kt               Tipo y datos de la secundaria (DataStore cifrado con el Keystore)
data/sync/RedLocal.kt                 IPv4 privadas + NSD (_shalev._tcp)
data/sync/AlmacenSync.kt              Leer/escribir la BD para sincronizar (siempre en db.tx)
data/sync/ServidorSync.kt             Servidor TCP de la principal (puerto 47811…47820)
data/sync/EjecutorComandos.kt         Acciones remotas con los repositorios de siempre + permiso
data/sync/ClienteSync.kt              Cliente de la secundaria (automático / manual)
data/sync/RepositoriosSegunTipo.kt    Decoradores de Producto/Insumo/Servicio/Precios/Venta/Turno/Exportador
data/sync/VinculacionRepositoriesImpl.kt
                                      Implementaciones + PuertaTurnoImpl + ArranqueSync
app/vinculacion/                      VinculacionScreen/ViewModel/Logic, BloqueoSecundaria
app/common/PermisosLocales.kt         LocalPermisosApp (oculta botones según permisos)
```

**Decoradores.** Hilt enlaza `ProductoRepository`, `InsumoRepository`, `ServicioRepository`, `PreciosRepository`, `VentaRepository`, `TurnoRepository` y `ExportadorDocumentos` a sus versiones `…SegunTipo`.

- En la principal delegan sin cambios.
- En la secundaria:
  - Las lecturas son locales.
  - Los cambios al catálogo se envían como `Comando` a la principal. La principal comprueba el permiso con **sus** datos y ejecuta la acción con los repositorios de siempre (mismas validaciones y transacciones). El catálogo cambiado vuelve con la sincronización.
  - Vender y abrir o cerrar turnos son operaciones locales: funcionan sin red y se envían después.

## Base de datos v5 (`MIGRACION_4_5`)

- `turno` y `venta` ganan:
  - `uuid` (único; se asigna al insertar).
  - `empleadoId` (null = de esta app).
  - `sincronizado` (0/1).
- Nueva tabla `empleado`, solo en la principal: nombre, permisos (CSV), clave (Base64), token y vencimiento del QR, activo, fechas.
- El turno activo de cada app se busca con `empleadoId IS NULL`, así que los turnos de las secundarias recibidos en la principal nunca bloquean su propio turno.
- `TurnoDto` del respaldo lleva `empleadoId` por la misma razón.

## Protocolo (versión 1)

- **Trama:** 4 bytes de longitud más los datos, como máximo 16 MB.
- **Saludo en claro (JSON):**
  - Vincular: `Vincular` → `VincularOk | Rechazo`.
  - Sesión: `Hola` → `HolaOk | Rechazo`.
- **Mensajes de sesión:** van comprimidos con gzip y cifrados.
  - `Sincronizar(lote, hash)` → `SincronizarOk(recibidos, hash, instantanea?)`
  - `Comando(accion, clave?)` → `ResultadoComando(valor | error)`. Desde 0.21.8, `clave` (UUID) hace el comando idempotente: ver §0.21.8.
  - `Aviso` (principal → secundarias: «hay cambios, sincroniza»)
  - `Quitada`
  - `Ping`/`Pong`, cada 30 s; sin noticias en 90 s se corta.

**Sincronizar es idempotente.**

- La principal aplica turnos y ventas por `uuid`: un reenvío no duplica nada.
- Responde solo con lo que guardó, y la secundaria marca como enviado solo eso.
- Si algo falla, no se marca nada y se reintenta.
- La principal solo reenvía el catálogo si su hash SHA-256 cambió.
- Al reemplazar el catálogo, la secundaria vuelve a descontar sus ventas aún no enviadas, para que lo ya vendido no «resucite».

**Modos de la secundaria:**

- **Automática** (predeterminado): mantiene la conexión, envía cada venta al momento y sincroniza con cada `Aviso` y, como mínimo, cada 30 s. Sin red reintenta con espera creciente: 5 s, 10 s, 20 s… hasta 60 s.
- **Manual:** conecta solo al tocar «Sincronizar ahora», al abrir un turno o al hacer una acción que necesita a la principal, y después cierra.

## Seguridad

1. **QR:** `SPVI-VINC1:` + Base64url(JSON). Contiene:
   - El id del negocio: 12 bytes aleatorios que no identifican al dispositivo ni a la persona.
   - Las IPv4 y el puerto.
   - El id y el nombre del empleado.
   - Un **token de 16 bytes**.
   - El vencimiento.
   
   La clave definitiva **no** está en el QR.
2. **Vinculación** (`Vincular`): ECDH P-256 con claves efímeras de ambos lados.
   - La secundaria demuestra que leyó el QR con `HMAC(token, pubC)`.
   - La principal responde con `HMAC(token, pubS‖pubC)`.
   - Clave del empleado = `HKDF-SHA256(ECDH, sal = token, info = "spvi-clave-empleado-v1")`.
   - Quien vea el QR después de usarlo no puede hacer nada, porque el token ya se gastó. Quien solo escuche la red no obtiene la clave.
3. **Sesión:** un nonce de 16 bytes de cada lado, y `HKDF(clave, nonceC‖nonceS)` produce una clave por sentido.
   - Cifrado AES-256-GCM con IV aleatorio.
   - AAD = `"SPVI-S1\0"`, sentido y número de secuencia.
   - Un mensaje alterado, repetido, reordenado o reflejado no se descifra y la sesión se corta. Lo cubre `SyncProtocoloTest`.
4. **Quitar una secundaria:** la fila se conserva, con `activo = 0` y la clave.
   - Al reconectar, esa app recibe un `Rechazo(QUITADA)` firmado con HMAC sobre su nonce, borra sus datos y vuelve a ser principal.
   - Un rechazo sin firma válida se ignora, así que nadie en la red puede borrar una secundaria.
5. **Almacenamiento:**
   - La clave del empleado se guarda en el DataStore cifrado con el Keystore en la secundaria, y en la BD SQLCipher en la principal.
   - Nunca va a logs (`DatosSecundaria.toString()` la oculta), ni al respaldo, ni a la copia de Android (`allowBackup=false`).
6. **Permisos de Android:** `INTERNET`, que ya estaba, cubre los sockets (red local y datos móviles). NSD no pide permisos. Desde 0.19.2, la principal añade los tres permisos normales del servicio en primer plano (ver §Limitaciones). Las secundarias no los usan.

## Limitaciones

**Resueltas en 0.19.1:**

- ~~Sin compilar~~: los 6 módulos compilan, Room y Hilt (KSP) validan consultas y grafo de inyección, y el protocolo pasa sus pruebas JVM (`SyncProtocoloTest`). Ver `tools/verificacion/verificar.sh`.
- ~~Compartir la ficha no se restringía~~: sin el permiso «Exportar» desaparece también el botón Compartir de las fichas de producto, insumo, servicio y registro. Sin «Editar inventario» desaparecen Editar y Eliminar de esas fichas.
- ~~Restaurar perdía los `uuid`~~: el respaldo guarda ahora `uuid`, `empleadoId` y `sincronizado` de turnos y ventas (campos opcionales: los respaldos 0.18.x se siguen leyendo igual y las versiones anteriores ignoran los campos nuevos). Restaurar ya no duplica el turno abierto de una secundaria.
- **Error corregido:** la validación del respaldo rechazaba los que tenían más de un turno abierto. Con secundarias es normal (la principal y cada secundaria tienen el suyo): ahora se admite uno abierto **por app**.
- ~~Encender la red a mano sin ayuda~~: el aviso de red de «Apps vinculadas» tiene un botón que abre los ajustes de **zona wifi** (principal) o de **wifi** (secundaria sin conexión). Sin permisos: la app no puede encenderlas por sí misma, solo abre la pantalla del sistema.

**Resuelta en 0.19.2 (opción A autorizada):**

- ~~La principal solo atendía con Shalev abierta~~: `ServicioSync` (app/notificacion) es un servicio en primer plano de tipo `connectedDevice`. Mantiene vivo el proceso, y con él `ServidorSync`, cuando:
  - la app es **principal** con al menos una secundaria vinculada;
  - y Shalev está a la vista **o** hay algún turno abierto (el suyo o el de una secundaria ya recibido).
- Sin turnos y fuera de la app se detiene, así que no gasta batería de noche. Las reglas están en `PlanAviso` y se prueban en `PlanAvisoTest`.
- **Una sola notificación:** es la del turno (ID 29, canal «Turno»):
  - «Turno abierto desde las 9:30 · 2 apps conectadas»;
  - «1 app conectada»;
  - «Esperando a tus apps vinculadas».
- Con el servicio activo, la notificación se ve también con Shalev abierta, porque Android lo exige.
- **Permisos** (normales, sin diálogo): `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` y `CHANGE_NETWORK_STATE`. Este último es requisito de Android 14 para el tipo `connectedDevice`; Shalev no cambia ninguna red. Están añadidos a la lista de `shalevPermisos` y la comprobación del script.

**La principal es un punto de venta completo (0.19.3):**

- Tiene todos los permisos siempre (`PermisosApp.PRINCIPAL`). Vende productos y servicios con **su propio turno**, edita inventario, servicios y precios, y usa registros, exportaciones, respaldo, licencia y ajustes. Las secundarias no le quitan ninguna función.
- **Su turno es independiente:** «turno activo» = `empleadoId IS NULL`. Los turnos de las secundarias que recibe no le impiden abrir, vender ni cerrar el suyo.
- **Corregido:** un ajuste de stock pedido por una secundaria (`EjecutorComandos`) se asociaba al turno abierto de la principal y entraba en su cierre. Ahora va al turno abierto de esa secundaria (`OrigenMovimiento` + `TurnoDao.activoDeEmpleado`), o a ninguno si no tiene. Lo prueba `TurnoRepositoryInstrumentedTest.laPrincipalVendeConSuPropioTurnoAunqueHayaSecundariasEnTurno`.

**Pendientes:**

- **Sin probar entre dos teléfonos reales.** Hay que hacer a mano PRUEBAS_DISPOSITIVO §4.10, pasos 1–14.
- **Arranque del servicio:**
  - Android 12+ no deja arrancarlo con la app en segundo plano. Si la condición se cumple estando fuera (p. ej. una secundaria abre turno mientras la principal está minimizada), arranca la próxima vez que se abra Shalev. Mientras tanto, el servidor funciona si el proceso sigue vivo.
  - Sin `RECEIVE_BOOT_COMPLETED`, tras reiniciar el teléfono hay que abrir Shalev una vez.
  - `START_NOT_STICKY`: si Android lo mata, no se reinicia solo.
- **Sin `WAKE_LOCK`:** con la pantalla apagada, algunos fabricantes duermen el wifi o la CPU pese al servicio. Si pasa, el usuario puede quitar Shalev del «ahorro de batería» en los ajustes del teléfono. Pedirlo desde la app exigiría `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, que no está autorizado.

## 0.20.0 — Integración con el resto de la app (P42–P45)

Decisiones: docs/HISTORIAL_DESARROLLO.md (Prompts 38 a 45). Base de datos **v6** (`MIGRACION_5_6`: 3 columnas en `empleado`).

| Hueco | Qué hace | Dónde |
|---|---|---|
| H1 Vendedor | `Venta.vendedor` = `turno.abiertoPor`. Sale en la ficha (no en el texto compartido), en la columna «Vendedor» de Ventas (Excel/PDF) y en el filtro de Registros (solo con ≥ 2 vendedores). `FiltrosGuardados` pasa a 7 valores y sigue leyendo los de 6 | `TablasExport`, `VentaDaos`, `RegistrosLogic`, `HojaFiltro` |
| H3 SMS | En la secundaria, «Pegar SMS» explica que el SMS llega al dueño: el empleado escribe el nº | `TextosVenta.PEGAR_AYUDA_SECUNDARIA` |
| H4 Cobro | `empleado.tarjetaId/telefonoId` (null = predeterminados). La instantánea que recibe la secundaria lleva **solo** esa tarjeta y ese teléfono (`Perfil.paraEmpleado`) | `AlmacenSync`, ficha del empleado |
| H5 Pedir cierre | `empleado.cierreSolicitadoEn`. La principal lo envía en `SincronizarOk.cerrarTurno` (campo opcional: una 0.19.x lo ignora). La secundaria lo guarda en `DatosSecundaria.cierrePendiente` y `CierreRemoto` cierra el turno cuando **no hay venta en curso** (`SesionVenta`, P43), con «Pedido desde la app principal» como quien cerró; luego sincroniza. Si ya no hay turno, la petición se olvida. Inicio desactiva Nueva venta/Escanear mientras está pedido | `CierreRemoto.decidir` (regla pura), `VentaViewModel`, `InicioViewModel` |
| H6 Negativos | `TipoAlerta.EXISTENCIAS_NEGATIVAS` (productos e insumos < 0, nunca elaborados) en Inicio e Inventario | `Stock`, `InventarioFiltro`, `InsumosFiltro` |
| H7 Respaldo | `RespaldoDto.empleados` sin claves ni QR; al importar quedan «Sin vincular». Antes de cambiar de teléfono, sincroniza las secundarias | `RespaldoRepositoryImpl` |

**Garantía de «nunca durante una venta»:** `SesionVenta` cuenta las pantallas de venta vivas (Productos/Servicios, incluida la selección en Inventario/Servicios, que solo se abre desde Venta). Se suelta al registrar, al descartar o al cerrar la pantalla. `CierreRemoto` vuelve a mirar la petición, la venta y el turno justo antes de cerrar; queda una ventana de milisegundos entre la comprobación y el cierre (aceptada: el turno cerrado bloquea el «Confirmar» de la venta y el carrito se conserva, ver `turnoCerradoAMitadDeVentaBloqueaElConfirmar`).

**Tests:** `Version020Test` (domain), `Version020SyncTest` (data), `VinculacionLogicTest.turnoYCierrePedidoDeCadaSecundaria`, `FiltrosGuardadosTest`, `InicioLogicTest`, `VentaViewModelTest.cierrePedidoEsperaAQueTermineLaVenta`, `InicioViewModelTest.cierrePedidoBloqueaNuevasVentasYAvisaAlCerrar`, `EsquemaTest.migracion5a6AnadeElCobroYElCierreDelEmpleado` (instrumentado, compilado). Prueba manual: PRUEBAS_DISPOSITIVO §4.10, pasos 16–20.

## 0.21.0 — Plan C1–C13 (P46–P48)

Decisiones: docs/HISTORIAL_DESARROLLO.md (Prompts 46 a 48). Base de datos **v7** (`MIGRACION_6_7`: `empleado.telefono`, `empleado.cierrePedidoPorEmpleadoEn`, `movimiento.hechoPor`).

| Punto | Qué hace | Dónde |
|---|---|---|
| C2 Teléfono | La secundaria lo escribe al vincularse (8 dígitos, 5/6). Viaja cifrado en `Sincronizar.telefono` y se guarda en `empleado.telefono`. La instantánea lleva la tarjeta del empleado (o la predeterminada) y **su** teléfono (`Perfil.paraEmpleado(…, telefonoEmpleado)`, id sintético −1) | `SecundariaRepositoryImpl.vincular`, `ServidorSync`, `AlmacenSync` |
| C3 Permisos | `PermisoEmpleado.PREDETERMINADOS` = todos − Editar inventario − Cambiar precios | `Vinculacion.kt` |
| C4 Límite | `secundariasPermitidas` de la licencia (prueba y licencias sin el campo: 5; máx. 10). `agregarEmpleado`/`nuevoCodigo` lo comprueban; la UI muestra «Secundarias (n de N)» | `VinculacionRepositoriesImpl.limiteSecundarias`, `LicenciaScreen.SelectorSecundarias` |
| C5 Una principal | `agregarEmpleado`/`nuevoCodigo` devuelven `SinPermiso` en una secundaria; la UI oculta «+» y, con empleados, «Usar esta app como secundaria» | `VinculacionRepositoriesImpl.noEsSecundaria` |
| C6 Solicitar cierre | La secundaria marca `cierreSolicitado` y lo repite en `Sincronizar.solicitaCierre` hasta que se resuelve. La principal guarda `cierrePedidoPorEmpleadoEn`; **Aprobar** reutiliza `cierreSolicitadoEn` → `SincronizarOk.cerrarTurno` (cierre con `CierreRemoto`, nunca durante una venta); **Rechazar** envía `SincronizarOk.cierreRechazado`. Nada se cierra solo | `InicioViewModel`, `SolicitudesCierreViewModel`, `VinculacionViewModel` |
| C7/C8 Registros | `movimiento.hechoPor` (usuario actual u origen sincronizado); en la secundaria, solo lo de su empleado | `OrigenMovimiento`, `RegistrosLogic`, `TablasExport` |
| C9 Inicio | La secundaria: turno, Nueva venta + Escanear y alertas | `InicioScreen` |
| C12 Módulos | `Preferencias.modulos` viaja en la instantánea; `PermisosApp.modulos` oculta secciones | `AlmacenSync`, `MainScaffold` |

**Compatibilidad:** todos los campos nuevos del protocolo son opcionales. Una 0.20.x y una 0.21.0 se entienden, pero una principal 0.20.x no ve las solicitudes de cierre ni el teléfono. Recomendación: actualizar todas a la vez.

**Suposiciones:** el teléfono del empleado no va en el respaldo (se vuelve a escribir al revincular). Si el proceso muere entre el recorrido inicial («Secundaria») y la vinculación, el empleado entra a Inicio y se vincula desde Ajustes. El aviso de solicitudes de cierre está en Inicio y en Apps vinculadas; la notificación del servicio no cambia (sigue sin importes ni nombres).


## 0.21.8 — Comandos sin duplicar y cierre de la sesión correcta (P-e)

Detalle en `docs/HISTORIAL_DESARROLLO.md` (Prompts 55–57, 0.21.6–0.21.8).
- **Cerrar la sesión correcta:** la sesión vigente es un `AtomicReference`. Tras un error, `cerrarSesion(s)` cierra solo la sesión `s` que se usó y, solo si sigue siendo la vigente, la olvida y pone «Desconectada» (compare-and-set). Lo mismo hace el lector al caerse el socket. `cerrarSesion()` sin argumento queda para los cierres intencionados (detener el modo automático, desvincular).
- **Comandos idempotentes:** cada acción de la secundaria lleva `Comando.clave` (UUID). La principal anuncia `HolaOk.comandosUnicos = true` y recuerda los últimos 200 resultados por (empleado, clave) en `MemoriaComandos`, en memoria. Si la respuesta se pierde, la secundaria reenvía **una vez** con la misma clave por una sesión nueva: si ya se aplicó, recibe el resultado guardado y no se aplica dos veces.
- **Compatibilidad** (sin cambiar `VERSION_PROTOCOLO = 1`): con una principal anterior (sin `comandosUnicos`), la secundaria no manda clave ni reenvía, como antes. Una secundaria anterior no manda clave: la principal ejecuta siempre.
- **Límites:** si falla también el reenvío, se informa del fallo y repetir a mano puede duplicar (con otra clave). Si la principal se reinicia entre el envío y el reenvío, la memoria se pierde.

## 0.26.0 — Fondo de caja asignado por el encargado (P73 §4)

- **Base de datos v9** (`MIGRACION_8_9`, esquema `9.json`): `empleado.fondoAsignadoCent`, `fondoAsignadoEn` (también es el token del fondo), `aperturaSolicitadaEn` y `versionCode` (versión de la app del empleado).
- **Protocolo** (sin cambiar `VERSION_PROTOCOLO = 1`; todos los campos son opcionales):
  - `Sincronizar.pideFondo` (el empleado tocó «Pedir fondo»), `Sincronizar.fondoUsado` (token del fondo con el que abrió) y `Sincronizar.versionCode`.
  - `SincronizarOk.fondo: FondoAsignado(cent, token)?` y `SincronizarOk.asignaFondos = true` (la principal 0.26.0 anuncia que asigna los fondos).
- **Secundaria 0.26.0:** sin fondo asignado no abre turno: «Pedir fondo» → lo recibe al sincronizar → abre con ese fondo, sin poder cambiarlo. Cada fondo sirve para **un** turno (`fondoUsado`). Se guarda en `DatosSecundaria` (no en Room). Con una principal anterior (sin `asignaFondos`) escribe su fondo como antes (`FondoApertura.Libre`).
- **Principal:** Apps vinculadas → empleado → «Fondo del próximo turno» → Asignar fondo (puede ser 0; propone el asignado, si no lo contado al cerrar su último turno, si no el último fondo). Se puede asignar sin que lo pidan. Inicio avisa «Luis pide abrir turno: asígnale el fondo».
- **Compatibilidad:** una secundaria 0.25.x sigue abriendo turno con su propio fondo; la principal lo detecta por `versionCode` (< 48) y muestra «Actualiza la app de Luis». El fondo asignado no va en el respaldo.

## 0.32.0 — Sincronización manual, fotos, barra de avance y sonidos

- **Ya no hay sincronización automática.** Se borra el bucle automático del cliente, los disparos por evento y el conmutador Automática/Manual. La app secundaria sincroniza cuando el empleado toca «Sincronizar ahora», al **abrir turno** (obligatorio) y al **pedir el cierre**. La sesión con la principal **se mantiene abierta** (ya no se cierra al terminar la ronda) y **se reconecta sola** si se cae (espera creciente de 5 s a 60 s): el dueño ve si la app del empleado está conectada o no, y los sonidos de conectar/desconectar tienen sentido. Cuando la principal avisa de un cambio de catálogo (`Aviso`), la secundaria lo muestra («Sincroniza para recibirlo») en vez de sincronizar sola.
- **El fondo lo escribe quien abre el turno** (se invierte la decisión de 0.26.0 P73 §4): en una secundaria el empleado escribe su fondo, igual que en la principal. Se retiran del protocolo `Sincronizar.pideFondo`, `Sincronizar.fondoUsado`, `SincronizarOk.asignaFondos`, `SincronizarOk.fondo` y el tipo `FondoAsignado`; también `DatosSecundaria.principalAsignaFondo/fondoCent/fondoToken/fondoUsado/pideFondo` y `PuertaTurno.fondoObligado/fondoUsado`. **Compatibilidad:** como todos los campos del protocolo son opcionales y `ignoreUnknownKeys = true`, una principal o una secundaria anteriores se siguen entendiendo (sus datos guardados con `modo`/`pideFondo` se leen sin problema; la 0.25.x sigue escribiendo su propio fondo).
- **Las fotos viajan** (antes no): la `Instantanea` lleva la **lista** de fotos del catálogo (`Instantanea.fotos`: entidad, id, nombre del archivo y tamaño) y los **bytes van aparte, por bloques de 256 KB**, con el mismo mecanismo del APK: `PedirFoto(id, nombre, desde)` → `BloqueFoto(id, desde, datos, total, sha256, error)`. La secundaria pide solo las fotos que no tiene, las guarda en su álbum (`filesDir/fotos`) con el mismo nombre y comprueba el SHA-256; después apunta el artículo a su propio archivo. Los insumos no tienen foto (siguen sin tenerla).
- **Barra de avance:** el tamaño de cada trama ya viaja en la cabecera (4 bytes), así que `Tramas.leer`/`escribir` informan del avance en bytes y `CanalCifrado` lo propaga. La principal lleva el avance de cada app conectada (`EstadoPrincipal.progresos`) y la secundaria el suyo (`EstadoSecundaria.progreso`), con las fases `RECIBIENDO`, `ENVIANDO` y `APLICANDO`. En la notificación hay una barra horizontal (canal «Sincronización», sin sonido) y en «Apps vinculadas» una barra por app.
- **Sonidos:** tres canales nuevos (el sonido queda congelado en el canal): «App conectada», «App desconectada» y «Sincronización terminada», con los audios de `app/src/main/res/raw` (`conectada.wav`, `desconectada.wav`, `sincronizada.wav`, generados por `tools/sonidos/generar-sonidos.ps1`). El aviso de turno (canal «Turno») sigue mudo.
- **El conteo de inventario es parcial**: solo se cuentan los artículos con movimientos **de ese turno** y la pantalla los separa por pestañas de categoría (`LineaConteo.categoria`, campo opcional del DTO). En la principal, la diferencia se aplica sobre el valor actual (no se rechaza el conteo si el artículo se movió después).

## 0.27.0

- **Una principal nunca pasa a secundaria** (T9): la opción no aparece y `VinculacionViewModel` la rechaza (`PRINCIPAL_NO_SECUNDARIA`). El tipo se elige en el recorrido inicial; cambiarlo exige exportar el respaldo y borrar los datos de la app.
- **Acceso con clave** en una secundaria: solo desde Ajustes (su recorrido termina al vincularse).
- **Clientes fijos:** la transferencia de cada venta lleva `clienteFijo` (opcional, `false` por defecto; protocolo sigue en v1). Al recibir una venta con la marca, la principal registra o actualiza el cliente por carné en la misma transacción. La lista de la principal **no** se envía a las secundarias.
