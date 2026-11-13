# DShalev — versión web de escritorio

DShalev es un **servidor web local en Python** pensado para ejecutarse en Windows como un único `DShalev.exe` portable. El navegador es la interfaz de escritorio: no necesita instalar un cliente separado. La base cifrada y la configuración se guardan en `DShalevData/` junto al ejecutable. En Windows, la clave local queda protegida por DPAPI y ligada a la cuenta/equipo. La sección **Respaldo** exporta e importa base, fotos del catálogo y APK publicado en un archivo protegido por contraseña; al restaurar, se validan hashes y la clave vuelve a protegerse localmente.

> **Estado de implementación en curso:** el escritorio incluye POS web de varias líneas, clave idempotente por intento, actor de venta y anulación administrativa con motivo, inventario fraccionario en milésimas enteras con libro append-only de movimientos y exportación CSV, recetas con descuento atómico, clientes, turnos/arqueo/caja, reportes y exportación CSV/XLSX (sin PDF); respaldo cifrado con restauración, acciones SPVI para productos/insumos/recetas/servicios/preajustes, recibos de venta/caja/turno, autorización de cierres/conteos, corrección/anulación móvil de ventas simples, y transferencia por bloques de fotos y APK provistos por el administrador. El suite DShalev pasa en loopback, pero **no demuestra compatibilidad real en Android/iOS**. Relay externo, licencia GL/sub-token firmado, revisión de firma APK, fotos móviles reales, ejecución Windows y pruebas físicas/redes reales siguen pendientes; no se declara paridad completa ni `.exe` validado.

## Navegación y herramientas de escritorio

- **Inicio:** gráfico diario de los últimos siete días con ventas de escritorio y móviles sincronizadas no anuladas. El Top 3 por unidades incluye solo ventas de escritorio; las ventas móviles no están incluidas en ese ranking. El indicador de ventas de hoy combina escritorio y móvil. El nombre del Top procede de las líneas de venta guardadas.
- **Servicios:** alta, búsqueda y edición de servicios del catálogo web; sus insumos se configuran en Recetas. Los servicios sincronizados de apps móviles siguen en un catálogo separado y no se presentan como equivalentes.
- **Herramientas:** conversión CUP/USD y cálculo de tasa manual sin consulta en línea. Los cálculos del conversor se envían al servidor de la instancia sin modificar las ventas. Las fichas de costo permiten crear y editar partidas con categorías, monedas y margen; calculan cada moneda por separado y se incluyen en el respaldo DShalev. Pueden exportarse a Excel con decimales como texto exacto o al catálogo web como producto/servicio nuevo con receta. Se reutilizan insumos por nombre y unidad; los nuevos comienzan sin existencias. Los productos también requieren existencia propia en escritorio; los servicios no. Si hay partidas en USD, se solicita una tasa positiva en CUP por USD. La ficha no se sincroniza como tal con las apps móviles.
- **Licencia:** panel informativo. La base local ya existe (identidad P-256 por instalación, almacén protegido y defensas ante restauraciones), pero el canje y la verificación GL siguen sin implementarse porque el contrato del servicio no forma parte de este repositorio. Sin endpoint ni claves de producción, la activación comercial está deshabilitada; la prueba local de siete días de las secundarias no es una licencia comercial.
- **Tema:** selector Sistema, Claro u Oscuro en la cabecera. La preferencia se guarda en el navegador de cada equipo, no en la base del negocio; si el navegador bloquea almacenamiento, dura solo durante la página.
- La entrada **Fotos / APK** se retiró de la navegación principal. La transferencia existente se conserva, para administradores, en **Equipo → Opciones avanzadas de sincronización**; no se han eliminado datos ni endpoints.

Los locales independientes por arranque son una función inicial; el selector gráfico, la administración integral y la sincronización por local siguen pendientes. La paridad completa de gráficos, licencias y herramientas con Android también sigue pendiente.

## Diseño visual y accesibilidad

`dshalev/static/app.css` mantiene la paleta Shalev existente y expone tokens semánticos de superficie, texto, borde, acento y estados para los temas claro y oscuro. Los objetivos táctiles principales (botones, campos, enlaces de acción, controles desplegables y nav) miden al menos 44 px; los enlaces de texto usan el acento y subrayado. El foco de teclado conserva un contorno de 3 px y `prefers-reduced-motion` elimina las transiciones. Las pulsaciones usan una escala inmediata de 0.97. En tema oscuro, tarjetas y paneles se separan con superficies tonales, no con sombras negras. La escala base 4 está tokenizada en Web y Android; la geometría heredada fuera de ella se conserva sin migración automática. `blur-glass` y varios campos de la guía siguen vacíos, así que no se inventan. Tampoco se sustituyen la tipografía, los radios o el layout Shalev por los ejemplos de la plantilla.

## Inventario y ventas con trazabilidad

- Las existencias, los niveles bajo/crítico y los consumos de recetas se calculan en enteros de milésimas. Las columnas decimales antiguas se conservan como compatibilidad de lectura; no se usan para las operaciones de stock. La migración normaliza los saldos existentes a 0.001, conserva el valor anterior en el motivo del movimiento de apertura y no inventa movimientos históricos que la base previa no guardaba.
- La cuenta administradora puede consultar el libro de solo lectura desde **Inventario → Movimientos** y exportar hasta 50 000 eventos a CSV. Los cambios manuales de existencia requieren un motivo de 3–160 caracteres; al dar de alta un artículo con saldo inicial distinto de cero también se solicita motivo. El historial registra quién, cuándo, origen, cambio, saldo resultante y referencia de ventas/sincronización.
- Los nombres de artículos nuevos o editados en Inventario tienen entre 3 y 80 caracteres, tanto en los formularios como en el servidor y las acciones remotas; la exportación de fichas aplica el mismo límite al crear artículos e insumos.
- **Retirar** en Inventario oculta el artículo de los catálogos activos, sin borrar ventas ni movimientos previos. Requiere un motivo de 3–160 caracteres y agrega el evento inmutable `RETIRO` al historial/CSV (incluye los retiros remotos sincronizados); el saldo no cambia.
- El POS asigna una clave única a cada intento lógico. Reenviar la misma clave con el mismo contenido devuelve el resultado original; reutilizarla con contenido diferente se rechaza para que no se procese otra operación por accidente. La clave se conserva en el borrador de sesión de la pestaña; el formulario sin JavaScript también incorpora una clave de sesión.
- Una cuenta administradora puede anular una venta web reciente indicando motivo. Se conserva la venta original, se agregan movimientos compensatorios que reponen los productos e insumos y la venta anulada deja de contar en reportes y efectivo esperado. Las ventas históricas anteriores al libro de movimientos no se anulan automáticamente porque la cantidad de insumos consumida entonces no puede reconstruirse con seguridad. Anular en DShalev no devuelve fondos de una transferencia; esa devolución se tramita por el canal de pago correspondiente.
- El POS web requiere conexión con DShalev y turno abierto; no es una cola de ventas offline. La clave idempotente evita duplicar un POST repetido, pero no sustituye la comprobación del historial ni la conciliación operativa ante errores de red.

## Ejecutar en desarrollo

Requiere Python 3.11 o 3.12. Desde esta carpeta:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python DShalev.py
```

En macOS/Linux se puede ejecutar `python3 DShalev.py`. En desarrollo se abre `http://127.0.0.1:47812`. En el primer inicio se crea una cuenta administradora; exige una contraseña de 12 caracteres o más. No existe contraseña predeterminada.

## EXE portable de Windows

En un PC Windows con Python instalado:

```powershell
.\build_windows.ps1
```

El ejecutable se crea en `dist\DShalev.exe`. El script exige **Python 3.11 o 3.12** (lo comprueba antes de crear el entorno) y se detiene si un paso falla; el lanzador `py` puede apuntar a otra versión no soportada. El workflow `.github/workflows/dshalev-windows.yml` compila el EXE, lo **arranca** y comprueba `/healthz` y la configuración inicial, y lo publica como artefacto de Actions en Windows x64. Al ejecutar el EXE no se instala un servicio: se abre el navegador local. Los datos viven en `DShalevData/` al lado del programa. La exportación cifrada desde **Respaldo** está diseñada para restaurarse en otro PC; la copia directa de la carpeta sigue ligada al usuario/equipo Windows original. Falta la revisión manual en un PC Windows real (instalación, firewall y uso diario); el CI no sustituye esa prueba.

## Acceso de otros equipos

1. En **Inicio → Acceso**, selecciona **Red local** y guarda.
2. Reinicia DShalev. El servidor escucha en la interfaz de red del PC en el puerto `47812`.
3. En la otra PC o teléfono conectado a la misma red, abre una de las direcciones mostradas, por ejemplo `http://192.168.1.20:47812`, e inicia sesión.
4. Si no conecta, permite DShalev en el firewall de Windows para redes privadas. DShalev no cambia el router, no abre puertos con UPnP y no crea cuentas en la nube.

La web puede abrirse en el navegador móvil y se adapta a pantalla pequeña. Las apps Shalev/IShalev también pueden emparejarse desde **Equipo → Generar QR**, si DShalev está reiniciado en modo LAN o remoto. El QR SPVI v1 caduca en 10 minutos y solo se consume una vez; el listener nativo usa TCP desde el puerto 47811 y cifra las sesiones con ECDH P-256/HKDF/AES-GCM. Las ventas recibidas quedan en el historial de escritorio y sus movimientos de producto se aplican por UUID idempotente.

La conexión nativa actual es directa: no hay relay DShalev ni acceso por datos garantizado; en redes distintas se recomienda VPN. Una ruta directa exige que el host del QR resuelva y que el TCP al puerto codificado en el QR (47811–47820 por defecto) esté permitido extremo a extremo; DShalev no configura ni reenvía puertos, y NAT/CGNAT puede bloquearla. Para el navegador remoto se requiere HTTPS con certificado vigente. No reenvíes el puerto HTTP a Internet; la contraseña de sesión no cifra el resto del tráfico HTTP.

## Seguridad y límites

- El acceso empieza ligado a `127.0.0.1`. LAN y remoto se activan explícitamente desde la cuenta de administrador y se aplican al reiniciar.
- Contraseñas con PBKDF2-HMAC-SHA256 y salt aleatorio; cookie de sesión firmada, `HttpOnly`, `SameSite=Strict`, CSRF por formulario, límite temporal tras cinco fallos y cabeceras CSP/anti-iframe.
- La base SQLite se mantiene en memoria y se persiste como snapshot cifrado/autenticado AES-256-GCM. En Windows, su clave queda protegida por DPAPI de la cuenta/equipo; el modo de desarrollo no-Windows usa un archivo clave local y no tiene la protección DPAPI.
- El endpoint autenticado `/api/v1/catalog` conserva los campos históricos `stock` y `low_stock` y añade `stock_milli`, `low_stock_milli` y `critical_stock_milli` como cantidades enteras exactas; los consumidores existentes no tienen que migrar de inmediato.
- En LAN todavía se usa HTTP: limitarlo a una red de confianza o usar VPN. El modo remoto solo arranca con rutas a un certificado y clave TLS existentes y la cookie va marcada `Secure`.
- No incluye relay, telemetría, cuenta de servicio, publicación automática de puertos ni integración con el worker remoto del relay. Los empleados nativos se pueden retirar; la app recibirá un rechazo autenticado al reconectarse.
- La sincronización SPVI persiste turnos, ventas, caja y cambios en la base cifrada; aplica movimientos idempotentes. Hay acciones remotas idempotentes para productos, insumos decimales, recetas, servicios y preajustes; cierre/conteo requieren aprobación desde Equipo. La anulación y corrección propagadas cubren ventas simples solo de productos, no líneas con recetas, servicios o insumos vendibles.
- Las fotos JPEG y APK se cargan manualmente por un administrador y se transfieren por bloques autenticados/cifrados SPVI con SHA-256; no existe carga de fotos desde el teléfono a DShalev. DShalev comprueba la estructura ZIP básica del APK, **no valida su firma de desarrollador**; Android verifica la firma al instalar.
- Los comandos SPVI que llevan clave guardan su huella y resultado en la misma transacción que la mutación; las repeticiones devuelven el resultado previo y las claves no se expulsan al superar un límite de caché. Los clientes antiguos que omiten clave no obtienen esa garantía.
- La licencia que DShalev devuelve a las secundarias es una prueba local de siete días; no equivale a una licencia GL activa ni emite sub-tokens firmados. No usar para afirmar habilitación comercial.
- Los precios se guardan en centavos; productos y cantidades POS se venden en unidades enteras; insumos admiten hasta tres decimales almacenados como milésimas enteras. Cada venta requiere turno abierto y descuenta producto/receta dentro de una transacción SQLite para evitar carreras de stock. Las ventas web nuevas quedan atribuidas al usuario y tienen clave idempotente; las anulaciones compensan existencias y conservan motivo/actor/hora.
- No se inventa una licencia GL: el plazo local de prueba es provisional; activación comercial y sub-token firmado requieren integrar el contrato GL en el escritorio.
- Identidad de licencia: un par P-256 por instalación en `DShalevData/licencia/identidad.bin`, con la privada protegida por DPAPI del usuario en Windows. Se rechaza la variante de prueba fuera de Windows si falta `DSHALEV_ALLOW_DEV_IDENTITY=1`, y esa variante nunca se acepta en Windows. Si el archivo existe y no se puede abrir, DShalev no genera otra identidad.
- La licencia local se sella y se liga a la huella del equipo y al `appId`; se escribe de forma atómica. Un resguardo en `%LOCALAPPDATA%\DShalev` guarda la licencia más reciente y la marca de agua del reloj (tolerancia de 2 h), de modo que restaurar datos anteriores no destruye la licencia y una copia de otro equipo no se activa. DPAPI no es atestación de hardware ni hay revocación instantánea sin conexión. Detalle en `../docs/DSHALEV_LICENCIA_GL.md`.

## Estructura

- `dshalev/app.py`: rutas web, autenticación, administración de plazas y QR SPVI.
- `dshalev/sync_protocol.py`, `dshalev/sync_server.py`: encuadre, vinculación ECDH, canal cifrado, lotes/recibos y listener TCP.
- `dshalev/database.py`: SQLite, snapshots cifrados, migraciones y persistencia.
- `dshalev/security.py`: hash de contraseña y validación CSRF.
- `dshalev/licencia/`: identidad P-256 de la instalación, configuración de licencias fijada en compilación y almacén local con ancla y marca de reloj (canje GL todavía pendiente).
- `dshalev/config.py`, `dshalev/run.py`: ubicación portable y modos de red/arranque.
- `dshalev/templates/`, `dshalev/static/`: interfaz HTML accesible y adaptable.
- `tests/`: pruebas de configuración/autenticación/ventas y de encuadre, QR, ECDH, AES-GCM, pairing, sincronización TCP, movimientos idempotentes y revocación (loopback; no sustituyen pruebas con Android/iPhone físicos).

La navegación usa trazados sólidos del sistema de iconos Android de Shalev. En pantallas estrechas se puede desplazar horizontalmente para acceder a todas las secciones; en pantallas bajas, verticalmente.

Los CSV anteponen un apóstrofo a los campos de texto que podrían interpretarse como fórmulas (también tras espacios o si empiezan con tabulador o salto de línea). Esto modifica únicamente la representación exportada; los importes numéricos y los datos almacenados no cambian. El comportamiento al importar CSV depende de la hoja de cálculo; revisa sus opciones de importación.

## Punto de venta en PC

Con un turno abierto, **Ventas** ofrece búsqueda incremental y un carrito separado: agrega artículos de diferentes búsquedas, cambia cantidades y revisa el total y el método de pago. La tecla **F2** lleva el foco a la búsqueda. El catálogo muestra hasta 30 coincidencias; escribe más para encontrar artículos adicionales. Se admiten hasta 60 artículos distintos por venta.

El carrito es un borrador de la pestaña y del turno actual: cuando el navegador permite almacenamiento de sesión, se recupera tras recargar o ante un rechazo del servidor y se borra al confirmar la venta. No reserves inventario con el carrito. Si cambia un precio antes de registrar, la venta se rechaza y el carrito recuperado muestra el nuevo precio para revisarlo. El servidor sigue comprobando turno, existencias, recetas y precio; los importes mostrados antes de registrar son estimados. Sin JavaScript se conserva el formulario anterior. Si se pierde la respuesta del registro, reenviar el mismo formulario conserva su clave y evita crear otra venta; si el servidor rechaza una clave usada con otro contenido, revisa el historial antes de iniciar una operación nueva.

Para probar el comportamiento del carrito con Node.js 18 o posterior: `npm ci` y `npm test` desde `Web-Desktop/`. Las pruebas usan un DOM simulado y no sustituyen la revisión visual y de teclado en un navegador real de Windows.

## Locales independientes por arranque (inicial)

La instalación original sigue abriéndose sin parámetros y conserva sus datos. Para iniciar otro local desde la carpeta `Web-Desktop`, ejecuta, por ejemplo, `python DShalev.py --local centro --port 47813` (o `DShalev.exe --local centro --port 47813` en Windows). Solo se admiten identificadores de 1 a 40 caracteres con letras minúsculas ASCII, números y guiones interiores. Al iniciar uno nuevo se crea su propio administrador, configuración, base cifrada, fotos, equipos y respaldos en `DShalevData/locales/<identificador>/`. El encabezado indica qué local está abierto.

Cada local tiene catálogo, existencias, caja, ventas y cuentas independientes; no hay informes consolidados ni selector gráfico. Crea y restaura el respaldo **dentro del local deseado**: restaurar reemplaza todos los datos del local abierto, no comprueba su procedencia. No ejecutes dos procesos sobre el mismo local: aún no existe bloqueo entre procesos y podrían sobrescribirse datos. Si ejecutas locales distintos a la vez, usa puertos web distintos; la coexistencia de sus servidores SPVI y el vínculo móvil por local aún no se han validado y no deben utilizarse simultáneamente. El EXE aún no se ha probado en Windows.

El diseño propuesto para la futura activación por servicio está en [`docs/DSHALEV_GL_SERVICIO_PROPUESTA.md`](../docs/DSHALEV_GL_SERVICIO_PROPUESTA.md); aún no existe servicio ni activación GL comercial.
