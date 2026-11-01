# Shalev — Formatos de respaldo y de exportación

Formatos de archivo que la app Android Shalev escribe y lee (versión declarada **0.30.0**): contenedor `.shalev` v4 (lee v3 y v4) y contenido `RespaldoDto` v4 (importa v3 y v4).

| Formato | Extensión | Tipo MIME | Cifrado | Se importa |
|---|---|---|---|---|
| Respaldo | `.shalev` | `application/octet-stream` | Sí (contraseña) | Sí |
| PDF | `.pdf` | `application/pdf` | No | No |
| Excel | `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | No | No |
| Imagen / Tarjetas | `.png` | `image/png` | No | No |
| Registro de la prueba (0.26.0) | `.png` / `.bin` | `image/png` / `application/octet-stream` | Sí (AES-GCM) | Solo Shalev |

Nombres de archivo sin datos personales: `Shalev_<qué>_<aaaa-mm-dd>.<ext>`, con la fecha del teléfono.

---

## Alcance de exportación vigente (revisado el 2026-10-09)

En Inventario y Servicios, la opción Imagen genera una tarjeta promocional para un elemento o una lista de precios para varios. PDF, Excel e Imagen abren el menú de compartir del sistema; en Registros y el detalle de turno se ofrecen PDF y Excel.

Esta tabla sustituye los alcances de las versiones anteriores descritas más abajo; no cambia el formato
binario de los respaldos ni los escritores PDF/XLSX.

| Origen Android | PDF / Excel | Imagen PNG | Tarjetas promocionales PNG |
|---|---|---|---|
| Informe de inventario (incluye insumos) | Sí | Solo productos | Solo productos |
| Catálogo / informe de servicios | Sí | Sí, catálogo | Sí, catálogo |
| Turnos | Sí | No | No |
| Ventas de productos, transferencias y movimientos en Registros | Sí | No | No |
| Compartir ficha de producto | No | Sí | Sí |

- Una selección mixta excluye insumos al generar imágenes/promociones; PDF/Excel conserva todo el informe.
- Las imágenes comerciales llevan nombres, categorías/tipos y precios de venta, no costos, existencias
  ni recetas. Las tarjetas añaden foto cuando existe.
- El campo «Encabezado de promoción (opcional)» admite 160 caracteres, solo afecta a tarjetas y se repite
  encima de cada PNG. Vacío mantiene el diseño sin encabezado. El texto se ajusta en altura sin recortar.
- La tabla de transferencias conserva la columna Vendedor; el registro original y su ficha también se conservan.

## 1. Respaldo `.shalev`

Implementación: `data/.../respaldo/BackupCipher.kt` (contenedor cifrado), `data/.../dto/RespaldoDto.kt` (contenido) y `data/.../respaldo/RespaldoRepositoryImpl.kt` (lectura y escritura).

### 1.1 Contenedor binario v4 (0.27.0; se leen también los v3)

Desde la 0.27.0 se escribe la **v4**, que añade un byte indicador (con o sin contraseña). Los archivos **v3** (0.13.0–0.26.0, siempre con contraseña) se siguen abriendo: son iguales sin ese byte (cabecera de 88 B, AAD de 56 B). Todos los enteros van en **big-endian**.

| Desplazamiento | Bytes | Campo | Valor |
|---|---|---|---|
| 0 | 7 | Magia | `SPVIBAK` (ASCII) |
| 7 | 1 | Versión | `4` (`3` en archivos anteriores) |
| 8 | 1 | Indicador (solo v4) | `1` = con contraseña · `0` = sin contraseña |
| 9 | 8 | `creadoEn` | Epoch ms |
| 17 | 4 | Iteraciones PBKDF2 | `310000` con contraseña; `10000` sin contraseña (se aceptan de 10 000 a 5 000 000) |
| 21 | 16 | Salt | Aleatorio por archivo |
| 37 | 12 | IV de GCM | Aleatorio por archivo |
| 49 | 8 | Largo del cifrado | Bytes del bloque cifrado, tag incluido |
| 57 | 32 | SHA-256 del cifrado | Suma de control |
| 89 | largo | Cifrado | `AES-256-GCM( gzip(JSON UTF-8) )` + tag de 16 B |

- **Clave:** `PBKDF2WithHmacSHA256(contraseña, salt, iteraciones, 256 bits)`. **Sin contraseña** (indicador `0`), la «contraseña» es un secreto interno ofuscado de la app: el archivo sigue cifrado y protegido contra alteraciones, pero **cualquiera con Shalev puede abrirlo** (riesgo aceptado, SECURITY.md §4 ter).
- **El indicador** se lee sin contraseña y va dentro del AAD: cambiarlo invalida el tag. Así la app sabe si debe pedir la contraseña al importar.
- **AAD de GCM:** los **57 primeros bytes** en v4 (56 en v3), es decir, todo salvo la suma de control. Si se cambia la fecha, las iteraciones, el salt, el IV o el largo, el tag deja de ser válido.
- **El largo y el SHA-256 no aportan seguridad:** cualquiera puede recalcularlos, y la integridad la da GCM. Sirven para saber **sin contraseña** si el archivo llegó **cortado** o **dañado**, y no confundirlo con una contraseña incorrecta.
- **La fecha** se lee sin contraseña para avisar qué se va a reemplazar («Respaldo del 30/09/2026»). No es un dato personal.
- **Tope:** 128 MB, tanto para el archivo leído como para el JSON descomprimido (defensa ante «zip bombs»).
- **Versiones anteriores (v1 y v2):** las escribieron versiones de prueba anteriores a la 0.13.0 y ya no se abren: la app responde «Respaldo de una versión anterior». No había clientes con esas versiones (decisión P17). Una versión **mayor que 4** muestra «Respaldo de una versión más nueva».

### 1.2 Contenido (JSON)

`RespaldoDto` (versión `4` desde la 0.25.0; se importan también los `3`), serializado con kotlinx.serialization. Es independiente del esquema Room: una migración de la base de datos no rompe los respaldos.

- Importes en **centavos**; cantidades de insumo en **milésimas**; instantes en **epoch ms**; fechas de calendario en ISO-8601 (`aaaa-mm-dd`); enums por nombre.
- Los campos desconocidos se ignoran (`ignoreUnknownKeys`) y los nuevos siempre llevan valor por defecto. Así, añadir un campo en el futuro no obliga a cambiar de versión.

| Campo | Contenido |
|---|---|
| `formato` | `"spvi-respaldo"` |
| `version` | `4` (0.25.0). Un respaldo `3` se importa igual: turnos sin arqueo, ventas válidas y sin bloque de licencia |
| `creadoEn` | epoch ms |
| `appVersion` | texto o `null` |
| `perfil` | nombre, apellidos, CI, `tarjetas[]`, `telefonos[]`, tarjeta y teléfono elegidos para cobrar |
| `preajustes[]` | nombre, `puntosBasicos`, `productoIds[]`, método de pago, importe mínimo, activo |
| `preferencias` | niveles bajo y crítico de productos e insumos. Desde 0.21.0 (opcionales): `modulos[]` (`VENTAS`, `INVENTARIO`, `SERVICIOS`; ausente = todos) y `empleadosPrevistos` |
| `productos[]` | todos los campos de `producto`, incluidos `fotoUri` y `eliminado` |
| `recetas[]` | `productoId` + `lineas[]` (`insumoId`, `cantidadMil`) |
| `insumos[]` | todos los campos de `insumo` |
| `turnos[]` | apertura, cierre, usuarios y `resumen` (nulo si está abierto). Desde 0.19.x: `empleadoId`, `uuid`, `sincronizado` (opcionales). v4: `fondoCent` y `contadoCent` (null = sin arqueo / sin contar); campo opcional `conteoCierre` (ver abajo); el `resumen` añade `entradasCent`, `salidasCent` y `numAnuladas` |
| `ventas[]` | venta + `detalles[]` + `transaccion` (si fue por transferencia). Desde 0.19.1: `uuid`, `empleadoId`, `sincronizado` (opcionales). v4: `anuladaEn`, `motivoAnulacion`, `anuladaPor` (null = válida) y `corrigeVentaId` (venta original que corrige una modificación) |
| `movimientos[]` | todos los campos de `movimiento`. Desde 0.21.0: `hechoPor` (quién lo hizo; vacío en respaldos anteriores) |
| `empleados[]` | desde 0.20.0 (opcional): `id`, `nombre`, `permisos[]`, `creadoEn`, `tarjetaId`, `telefonoId`. **Sin** clave, token del QR, fechas de vínculo ni el teléfono del empleado (0.21.0): al restaurar quedan «Sin vincular» y el empleado lo vuelve a escribir al vincularse |
| `caja[]` | v4: entradas y salidas de efectivo: `id`, `turnoId`, `fecha`, `tipo` (`ENTRADA`/`SALIDA`), `importeCent`, `motivo`, `hechoPor`, `uuid`, `sincronizado` |
| `clientesFijos[]` | 0.27.0 (opcional; `[]` en respaldos anteriores, sin subir la versión): `nombreApellidos`, `ci` (único), `telefono`, `creadoEn`, `actualizadoEn`. Las transferencias llevan además `clienteFijo` (booleano, `false` por defecto) |
| `licencia` | v4 (opcional): datos **públicos** de la licencia instalada: `id`, `tipo`, `secundarias`, `venceEn` (epoch ms; null = perpetua), `ci` del titular. **No** es la licencia (está atada a la clave del teléfono): solo rellena el campo «ID de la licencia anterior» para pedir la recuperación en otro teléfono |

### Conteo de cierre (en desarrollo, 2026-10-17)

`turnos[].conteoCierre` es opcional, `null` en datos antiguos, sin cambiar las versiones del contenedor ni del DTO:

- `billetes`: mapa denominación CUP → número de billetes (0–999999); admite 1, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000 y 20000; el orden visual del contador no cambia el formato.
- `otroCent`: monedas/otro efectivo en centavos. Si se introduce únicamente un total, el mapa está vacío y el total va aquí.
- `inventario[]`: `entidad` (PRODUCTO/INSUMO), `id`, `nombre`, `unidad`, `esperado`, `contado`, `actualizadoEn`. Cantidades nativas: unidades para productos, milésimas para insumos. El nombre, la unidad y las cantidades son la instantánea del conteo, no se recalculan al exportar.
- `motivo`: obligatorio con diferencias; `aprobadoPor` y `aprobadoEn` son nulos hasta que la principal aplica la conciliación. El efectivo calculado debe coincidir con `contadoCent`.

Room v13 añade únicamente `turno.conteoCierreJson TEXT NULL` (migración 12→13). **`13.json` debe generarse con Room/KSP y verificarse antes de publicar; no se ha generado en este entorno sin JDK/SDK.**

En el protocolo LAN v1, el conteo viaja con el DTO del turno. La respuesta añade los campos opcionales `conteoAprobado` y `conteoTurnoUuid`: la secundaria solo acepta la aprobación si coincide el turno y la declaración completa. La principal ignora cualquier autorización que declare la propia secundaria y conserva la que ya otorgó ante reenvíos. Las dos apps deben estar actualizadas para el nuevo flujo.

El PDF/Excel del turno añade **Conteo de efectivo** (Denominación, Billetes, Subtotal; otro efectivo y total) y **Conciliación de inventario** (Categoría, Nombre, Unidad, Esperado, Contado, Diferencia; motivo y quién aprobó). Las cantidades fraccionarias se presentan en la unidad del insumo. Los ajustes son movimientos AJUSTE asociados al turno, con motivo y autor, y ya viajan en el respaldo ordinario. Desde la 0.32.0 el conteo es **parcial** (solo los artículos que cambiaron en el turno) y cada línea lleva su **categoría** (campo opcional `categoria` de la línea del DTO: vacío en los conteos anteriores).

**No van en el respaldo:**
- **la licencia** (cada teléfono necesita la suya; desde la v4 sí van su ID, tipo, secundarias, vencimiento y CI, para recuperarla);
- el estado de la app de la 0.25.0 (fecha del último respaldo, última consulta de actualizaciones, versión descartada);
- el progreso del asistente de primera ejecución (es estado del dispositivo);
- las claves de las apps secundarias y sus códigos QR (tabla `empleado`): tras restaurar, los empleados aparecen con sus permisos pero hay que volver a vincular cada app con un código nuevo;
- **las imágenes de las fotos**: se guarda la ruta (`fotoUri`), pero no el archivo. En otro teléfono los productos aparecen sin foto.

### 1.3 Exportar

El respaldo es siempre **completo**. Desde la 0.13.0 ya no existe «Solo configuración»: es una desviación de Shalev.txt:132, decidida en P17.

1. *Reuniendo los datos…*: lectura en una transacción.
2. *Cifrando con tu contraseña…*: JSON → gzip → AES-GCM. Al terminar, el JSON en claro se sobrescribe con ceros.
3. *Guardando el archivo…*: va al destino elegido con el gestor de archivos del dispositivo (**Guardar en el teléfono**, enraizado en Descargas internas, solo proveedores locales). Lo hace `ExportadorArchivos`, compartido con el resto de exportaciones.

Nombre: `Shalev_respaldo_<fecha>.shalev`.

### 1.4 Importar

1. *Leyendo el archivo…* (máx. 128 MB).
2. *Comprobando que el archivo esté completo…*: magia, versión, iteraciones, largo y SHA-256, **sin contraseña**.
3. *Comprobando la contraseña…*: AES-GCM. Si el indicador dice «sin contraseña» (v4), no se pide: se importa tras la confirmación.
4. Validación **antes de escribir nada**:
   - `formato` correcto y `version` no superior a la soportada;
   - ids únicos, y tarjetas y teléfonos sin repetir;
   - recetas y ventas que apuntan a productos, insumos y turnos existentes;
   - como mucho un turno abierto **por app** (la principal y cada secundaria tienen el suyo);
   - `uuid` de turnos y de ventas sin repetir.
5. *Importando los datos…*: en **una sola transacción** de Room. Se borra toda la operación y la configuración, se inserta todo y se reemplazan las preferencias.

Cualquier error deja los datos como estaban.

| Error | Mensaje |
|---|---|
| Archivo cortado | «El archivo está incompleto» |
| Archivo alterado | «El archivo está dañado» |
| No es de Shalev | «El archivo no es un respaldo de Shalev o está dañado» |
| Versión más nueva | «Respaldo de una versión más nueva» |
| Versión 1 o 2 (anterior a la 0.13.0) | «Se creó con una versión muy antigua de Shalev que esta versión ya no abre.» |

Tras importar un respaldo v4 con bloque `licencia` en un teléfono sin licencia instalada, Licencia muestra el ID ya escrito para **Recuperar** (MANUAL §9). Exportar un respaldo pone a cero el recordatorio mensual de Inicio.
| Contraseña incorrecta | «Contraseña incorrecta» (el diálogo queda abierto para reintentar) |

**Cómo llega el archivo:** «Importar» → «Elegir archivo» abre el gestor de archivos del dispositivo (enraizado en Descargas internas, solo proveedores locales). El `.shalev` no se envía a otras apps ni se recibe de ellas: siempre se piden contraseña y confirmación.

---

### 1.5 Respaldo previo a la actualización (0.30.1)

Antes de abrir el instalador, la app escribe un `.shalev` **sin contraseña** con el formato normal en `filesDir/respaldo_actualizacion/previo.shalev`, más `previo.version` (`versionName/versionCode` instalada). Es privado de la app (no se comparte ni se exporta) y se borra al restaurarse o al cancelarse la instalación. Al abrir otra versión, `RespaldoPrevio` lo importa con la misma ruta que «Importar» y vuelve a dejar como estaban `ultimoRespaldo` y la licencia recuperable del estado de la app.

## 3. Exportaciones

Todas las tablas salen de una misma fuente neutra, `TablaExport` (título, columnas, filas y pie), definida en `domain/.../service/TablasExport.kt`. Los escritores están en `data/.../export/`.

### 3.1 Qué se exporta y desde dónde

| Pantalla | Contenido | Formatos | Nombre del archivo |
|---|---|---|---|
| Inventario | La selección o, sin selección, lo que se ve (búsqueda y filtro) | PDF, Excel → enviar o guardar | `Shalev_inventario_<fecha>.pdf/.xlsx` |
| Inventario | Lista de precios para clientes | Imagen PNG → enviar | `Shalev_precios_<fecha>[_n].png` |
| Inventario | Tarjetas promocionales | PNG → enviar | `Shalev_productos_<n>.png` |
| Ficha de producto | Ficha completa (uso interno) | PDF → enviar | `Ficha <nombre>.pdf` |
| Ficha de producto | Tarjeta del producto para clientes | PNG → enviar | — |
| Servicios | La selección o lo que se ve | PDF, Excel → enviar o guardar | `Shalev_servicios_<fecha>.pdf/.xlsx` |
| Registros → Ventas, Transferencias, Movimientos | **Exactamente lo que se ve** (pestaña, búsqueda y filtro; el filtro se indica en el título) | PDF, Excel → enviar o guardar | `Shalev_ventas_…`, `Shalev_transferencias_…`, `Shalev_movimientos_<fecha>.pdf/.xlsx` |
| Registros → Turnos → detalle → Compartir (0.25.1) | El turno completo: resumen, arqueo de caja, caja (entradas/salidas), ventas con estado y movimientos de inventario e insumos | PDF, Excel → enviar o guardar | `Shalev_Turno_<aaaa-MM-dd_HHmm>.pdf/.xlsx` |

«Guardar» usa el selector del sistema (SAF: almacenamiento, Drive, OneDrive…). «Enviar» crea el archivo en `cacheDir/compartir` y abre el menú Compartir mediante FileProvider. Imagen y Tarjetas solo se envían, porque pueden ser varios archivos.

### 3.2 Columnas

| Tabla | Columnas | Pie |
|---|---|---|
| Inventario | Categoría, Nombre, Descripción (0.24.0), Cantidad, Precio costo, Precio venta, Caducidad | — |
| Lista de precios | Categoría, Producto («Nombre · Descripción» desde 0.24.0), Precio (orden: categoría y nombre) | — |
| Ficha | Campo, Valor (solo campos con dato) | — |
| Insumos | Nombre, Precio (costo), Cantidad, Nivel bajo, Nivel crítico | — |
| Ventas | Fecha, Vendedor (0.20.0: quien abrió el turno; vacío si no se sabe), Método, Artículos, Unidades, Importe, Costo, Ganancia, Estado (0.25.0: «Válida», «Anulada» o «Corrige #N») | Total (sin las anuladas) |
| Ficha de una venta | además, si está anulada: Estado («Anulada el …»), Motivo, Anulada por; y «Corrige: Venta #N» | — |
| Caja de un turno (0.25.0, pestaña **Caja** del detalle del turno) | Arqueo: Fondo inicial, Ventas en efectivo, Entradas, Salidas, Esperado en caja, Contado, Sobrante/Faltante. Movimientos: Fecha, Tipo, Importe, Motivo, Hecho por | — |
| Transferencias recibidas | Fecha, Nº transacción, Cliente, CI, Teléfono, Importe, Venta | Total |
| Movimientos de inventario e insumos | Fecha, Tipo, Entidad, Nombre, Cambio, Existencia, Hecho por (0.21.0; vacío en los anteriores), Nota | — |
| Perfil | Dato, Valor (nombre y apellidos, carné, tarjetas/cuentas, teléfonos) | — |
| Preajustes de precios | Nombre, Ajuste, Método, Importe mínimo, Productos, Activo | — |

Formatos de celda: importes «1,450.00 CUP»; fechas con hora `dd/MM/aaaa HH:mm` en la zona del teléfono; caducidad `dd/MM/aaaa`; cantidades de insumo con hasta 3 decimales y su unidad.

**Datos personales:** en PDF y Excel de Transferencias el carné va **completo** (es el registro del negocio). Desde la 0.26.0 no hay exportaciones de texto (solo la licencia se envía como mensaje); las fichas se ven en la app.

### 3.3 PDF

- `android.graphics.pdf.PdfDocument` del sistema, sin dependencias.
- Tamaño **A4**, márgenes de 36 pt, filas de 16 pt. Desde la 0.25.1 (`DisenoPdf.kt`): una tabla de **más de 5 columnas** va en una hoja **horizontal** (842 × 595 pt); las demás, en vertical. En un mismo PDF pueden alternarse (p. ej. el turno: resumen y arqueo en vertical, ventas en horizontal).
- Una o varias tablas por documento; paginación automática con la **cabecera repetida** en cada página.
- Anchos (0.25.1): las columnas compactas (fechas, importes, CI, Sí/No) reciben su ancho natural y el resto del espacio se reparte entre las de texto libre; solo el texto largo que aun así no cabe se corta con «…». Los números e importes van **alineados a la derecha**.
- 0.26.0 (§5): las columnas de importes, cantidades y fechas, y la columna «Valor» de las tablas Dato/Valor, van en **seminegrita** (Roboto 600; en Android 8 negrita) y se miden así para no cortarse. Los teléfonos (8 o más cifras seguidas) no se destacan.
- Escala de grises de alto contraste. Pie de página «Shalev · página N».

### 3.4 Excel (`.xlsx`)

- **fastexcel** (sin Apache POI). Una hoja por tabla; el nombre de la hoja es el título (máx. 31 caracteres, sin `[]:*?/\`, sin repetir; desde la 0.25.1 `/` pasa a `-` y `:` a `.`, p. ej. «Turno del 02-10-2026 08.00»).
- Fila 1 = cabecera en negrita con fondo `#DDE3F0`, **inmovilizada**.
- Las celdas con importe se escriben como **número** con formato `#,##0.00 \C\U\P` (0.25.1: el texto escapado con barras; la versión con comillas dejaba `xl/styles.xml` inválido porque fastexcel no las escapa), para que se puedan sumar.
- El pie (totales) va dos filas debajo de la tabla.
- 0.26.0 (§8): cada columna mide lo que su contenido más largo (cabecera incluida, de 4 a 80 unidades); el texto que no cabe en 80 baja de línea (ajuste de texto). Importes, cantidades y fechas en **negrita** (las mismas columnas que la seminegrita del PDF).

### 3.5 Imágenes PNG

| | Lista de precios (Imagen) | Tarjetas promocionales |
|---|---|---|
| Para | Clientes: **sin costo ni existencias** | Clientes: **sin costo ni existencias** |
| Contenido | Título, tabla Categoría / Producto / Precio, pie «Shalev» | Foto (o la inicial si no tiene), nombre, categoría y precio de venta |
| Tamaño | 1080 px de ancho, **25 filas** por imagen; si hay más, varias imágenes numeradas | 1080 px de ancho, **6 tarjetas** por imagen (una imagen con un solo producto lo muestra en una tarjeta grande) |

### 3.6 Texto

Desde la 0.26.0 Shalev no exporta texto: Registros, Inventario, Servicios y las fichas usan PDF y Excel (e Imagen/Tarjetas en Inventario). Solo la **solicitud de licencia** (y el contacto de Soporte) se envían como mensaje; ver [docs/LICENCIA_0.23.md](docs/LICENCIA_0.23.md).

### 3.7 Registro de la prueba (0.26.0)

Para que el periodo de prueba no vuelva a empezar al desinstalar y reinstalar, la app principal guarda la fecha de inicio cifrada en cuatro sitios:

| Copia | Ruta | Quién la puede leer tras reinstalar |
|---|---|---|
| Interna | `filesDir/.sys_<huella>.bin` | Nadie (se borra al desinstalar) |
| Imágenes | `Pictures/Shalev/sys_<huella>.png` (PNG de 48 × 48 con el registro en un fragmento privado `spVi`) | Shalev con el permiso de fotos (`READ_MEDIA_IMAGES` en 13+, `READ_EXTERNAL_STORAGE` en 10–12; `WRITE_EXTERNAL_STORAGE` en 8–9) |
| Download / Documents | `.sys_<huella>.bin` | Solo en Android 8–9 (en 10+ Android no deja a una instalación nueva leer las de la anterior) |

- Contenido: JSON `{"firstInstall": <ms>, "trialDays": 7, "version": 1, "lastSeen": <ms>}` (`lastSeen` es opcional: última fecha vista, para detectar un reloj atrasado también tras reinstalar).
- Cifrado: clave PBKDF2WithHmacSHA256(ANDROID_ID, sal fija ofuscada, 10 000 iteraciones, 256 bits); AES/GCM/NoPadding con IV aleatorio de 12 bytes y etiqueta de 128 bits; archivo = IV ‖ texto cifrado.
- Al arrancar se leen todas las copias, gana el `firstInstall` más antiguo y el `lastSeen` más reciente, y se reescriben las que falten o estén desfasadas. Una copia que no descifra (otro teléfono, dañada) se ignora y se reescribe: **no bloquea**.
- Es una protección débil a propósito (ANDROID_ID no es secreto; la ofuscación solo frena a curiosos): pensada para un usuario promedio.

No se generan archivos `.txt`: el escritor de texto plano (`TextoWriter`) se eliminó en la 0.13.0 porque ninguna pantalla lo usaba.

### Entrada numérica en pantalla (09/10/2026)

Los campos de dinero admiten punto o coma decimal y hasta dos decimales, sin separadores de miles; las cantidades decimales admiten hasta tres. Una entrada ambigua se rechaza conservando el texto anterior, sin cambiar los formatos de archivos exportados/importados. Tarjeta/cuenta: hasta 20 cifras en edición (validación final de 12 a 20). La venta debe superar el costo, incluso tras descuentos; no se cambian comprobantes históricos.
