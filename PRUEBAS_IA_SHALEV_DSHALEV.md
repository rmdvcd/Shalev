# Guía de pruebas para una IA en el equipo del usuario

**Alcance:** Shalev para Android y DShalev para PC. IShalev no forma parte de esta campaña.  
**Objetivo:** verificar compilación, funcionamiento, interfaz, seguridad básica y los cambios actuales del punto de venta, distinguiendo resultados reales de pruebas pendientes. Esta guía es un plan de ejecución, no un informe de pruebas ya realizadas.

## 1. Instrucciones para la IA ejecutora

1. Lee `AGENTS.md`, este documento, `Pruebas.md`, `PRUEBAS_DISPOSITIVO.md` y `DShalev/README.md`. Si una instrucción antigua contradice el código actual, comprueba la implementación y anota la diferencia; no supongas que un resultado histórico sigue vigente.
2. Confirma con el usuario qué equipo se puede usar, si hay un Android de pruebas o un emulador, si hay dos dispositivos para sincronización y si se autoriza instalar software o ejecutar pruebas instrumentadas. No solicites contraseñas, claves ni certificados en el chat; el usuario debe introducirlos directamente en la interfaz cuando sean necesarios.
3. Desde la raíz del repositorio, registra `git branch --show-current`, `git rev-parse HEAD` y `git status --short`. Si hay cambios sin confirmar, **consérvalos** y deja constancia de que los resultados incluyen el árbol de trabajo, no solo el commit. No cambies de rama, no hagas `reset`, `clean`, `checkout` de archivos, ni modifiques el código sin autorización.
4. Usa exclusivamente **datos ficticios** y un entorno de pruebas aislado. No abras, borres ni restaures sobre una instalación o base de datos de negocio. No uses `adb shell pm clear`, desinstalaciones, restauraciones, cambios de red, revocaciones, configuración de licencias ni actualizaciones en equipos de producción sin permiso explícito y respaldo verificado.
5. Para cada caso registra **aprobado, fallido, bloqueado o no ejecutado**, resultado observado, evidencia y motivo. Un caso omitido o una compilación exitosa no demuestra que las funciones estén validadas. No inventes cantidades de pruebas ni resultados.
6. Detén y comunica cualquier riesgo de pérdida de datos, venta duplicada, exposición de información, comportamiento destructivo o fallo repetible. No publiques información privada, claves, QR de vinculación, credenciales, CI, números de tarjeta, SMS o datos de clientes en capturas, logs ni informes. Usa valores ficticios y enmascara cualquier identificador real.
7. Solicita intervención humana cuando la IA no pueda manejar el teléfono, autorizar depuración USB, permisos o la cámara. No afirmes haber visto ni tocado lo que no pudiste observar. No envíes pagos reales ni actives funciones comerciales o licencias reales para la prueba.

### Preparación del entorno

- **Android:** JDK 17, Android SDK con API 35 y build-tools correspondientes; `adb` disponible. La app requiere Android 8/API 26 o superior. Usa una variante debug y un dispositivo o emulador dedicado.
- **DShalev:** Windows con Python 3.11 o 3.12 y navegador actualizado. Para los tests de interacción del carrito se requiere Node.js 18 o posterior y npm. El ejecutable portable se prueba en Windows. En otros sistemas pueden correrse las pruebas Python y del navegador, pero no se debe declarar validado el `.exe` de Windows.
- Primera descarga de dependencias, Gradle o SDK puede requerir acceso a internet; documenta si está bloqueado. Los tests no deben depender de servicios externos salvo en los casos explícitamente autorizados.
- Reserva espacio para builds y reportes. Pide autorización antes de instalar dependencias o modificar el entorno. Si no hay alguna herramienta, marca los casos afectados como **bloqueados** y continúa con lo seguro.

## 2. Preparar datos de prueba aislados

### Android

Usa un emulador nuevo o un teléfono de pruebas con datos prescindibles y autorización. `debug` usa identificador con sufijo `.debug`, pero eso **no garantiza** que ya no existan datos importantes de otra instalación debug. Compruébalo con el usuario. No hagas pagos reales ni pegues SMS reales.

En una terminal desde la raíz (Windows PowerShell: usa `./gradlew.bat` en lugar de `./gradlew`):

```text
java -version
adb devices -l
```

Si aparece `unauthorized`, pide al usuario que autorice el equipo en el teléfono. Para instalar o ejecutar instrumentados, deja conectado **un solo dispositivo de pruebas**; si hay varios, selecciona el dispositivo en el entorno de herramientas o desconecta los demás. Comprueba su API con `adb -s <SERIAL_DE_PRUEBA> shell getprop ro.build.version.sdk`, sustituyendo el marcador por un serial de `adb devices -l`; no publiques el serial completo en el informe.

### DShalev

La ejecución normal en desarrollo guarda `DShalevData/` junto al proyecto y el `.exe` guarda datos junto al ejecutable. **No arranques la instalación habitual para esta campaña.** Para aislar el servidor de desarrollo en Windows, abre PowerShell desde la carpeta `DShalev` del repositorio y, previa autorización para instalar dependencias, ejecuta:

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
npm ci
```

Si solo existe Python 3.12, sustituye `-3.11` por `-3.12`. La primera vez se descargan dependencias. Crea una carpeta nueva en temporales y ejecuta el servidor con esa raíz, sin copiar datos reales:

```powershell
$env:DSHALEV_PRUEBA_ROOT = Join-Path $env:TEMP ("DShalev-pruebas-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $env:DSHALEV_PRUEBA_ROOT | Out-Null
.\.venv\Scripts\python.exe -c "import os; from pathlib import Path; import uvicorn; from dshalev.config import load_config; from dshalev.app import create_app; uvicorn.run(create_app(load_config(Path(os.environ['DSHALEV_PRUEBA_ROOT']))), host='127.0.0.1', port=47812, access_log=False)"
```

Abre `http://127.0.0.1:47812` **en ese mismo PC**. Si el puerto está ocupado, no detengas el proceso ajeno: cambia el puerto del comando y la URL. Crea una cuenta administradora ficticia en la pantalla inicial; el usuario introduce la contraseña directamente. Mantén la terminal abierta durante la prueba y detén el servidor con Ctrl+C al terminar. No registres el contenido de `portable-settings.json`, pues contiene un secreto de sesión.

Para probar el `.exe`, primero compílalo según la sección 3. Copia únicamente `dist\DShalev.exe` a **otra carpeta nueva y vacía de pruebas**, ejecútalo allí y verifica que su `DShalevData` se crea a su lado. Cierra antes el servidor de desarrollo que use el mismo puerto. No copies el ejecutable encima del de producción. Conserva las carpetas de prueba hasta que el usuario autorice su eliminación.

## 3. Pruebas automáticas y compilación

Ejecuta desde la raíz salvo indicación contraria. Guarda el resultado, código de salida, número real de pruebas y ruta del informe; no copies logs completos sin revisar datos sensibles.

| ID | Comando o acción | Resultado esperado / evidencia |
| --- | --- | --- |
| A-01 | `./gradlew shalevTests --no-daemon` | Tests JVM sin fallos. En Windows usa `./gradlew.bat`. Recoge los informes Gradle `*/build/reports/tests/`. |
| A-02 | `./gradlew shalevCheck --no-daemon` | Tests, lint debug, APK debug y revisión de permisos terminan correctamente. Examina los informes si falla. No implica pruebas físicas. |
| A-03 | `./gradlew :app:assembleDebug --no-daemon` | Compilación debug. Ubica los APK existentes en `app/build/outputs/apk/debug/`; puede haber varios por ABI. No confundas un APK de release sin firma con uno instalable. |
| A-04 | Con **solo el dispositivo de pruebas** conectado y autorización: `./gradlew shalevInstrumentedTests --no-daemon` | Tests instrumentados de app y data ejecutados. Busca informes en `app/build/reports/androidTests/connected/` y `data/build/reports/androidTests/connected/`. Pueden instalar/reemplazar aplicaciones de prueba: no usar en equipos con datos importantes. |
| A-05 | `./gradlew :app:verifyRoborazziDebug --no-daemon` | Compara capturas existentes. Si difieren, revisa las imágenes y reporta el diff; no actualices referencias automáticamente para ocultar un fallo. Si procede una actualización visual, solicita autorización y sigue `CAPTURAS.md`. |
| D-01 | En `DShalev`: `.\.venv\Scripts\python.exe -m pytest tests -q` | Suite Python sin fallos; registra conteo y avisos. En macOS/Linux usa el Python del entorno virtual correspondiente. |
| D-02 | En `DShalev`: `npm ci` y `npm test` | Tests de interacción del carrito con DOM simulado sin fallos. No sustituyen un navegador real. |
| D-03 | En Windows, en `DShalev`: `.\build_windows.ps1` | Se genera `dist\DShalev.exe`. Ejecuta después la prueba aislada indicada arriba; compilar no demuestra que el ejecutable funcione. El script instala dependencias en `.venv` y genera artefactos de compilación. |

No firmes releases, no generes certificados ni publiques artefactos. Si algún comando falla por falta de herramientas, conectividad o permisos, anota el error y pasa a las pruebas independientes que sí sean posibles. No ejecutes comandos para borrar datos como medida automática de reparación.

## 4. Pruebas funcionales de Shalev Android

Instala debug en el dispositivo aislado solo con permiso: `./gradlew :app:installDebug`. En Windows utiliza `./gradlew.bat`. Registra modelo, versión/API, tamaño y escala de fuente; no hace falta registrar serial. Crea productos, clientes y cantidades ficticios. Ejecuta los casos en orden y anota cada resultado por separado.

| ID | Procedimiento | Resultado esperado |
| --- | --- | --- |
| S-01 Arranque | Abre la app y completa el asistente con datos ficticios; cierra y vuelve a abrir. | Inicia sin cierres inesperados y conserva la configuración de prueba. |
| S-02 Sin turno | Intenta vender sin turno abierto. | La app bloquea la venta y ofrece el flujo para abrir turno; no registra una operación. |
| S-03 Carrito | Crea dos productos disponibles, abre turno, selecciona uno, cambia su cantidad y agrega el otro desde el carrito. Busca cada uno con filtros diferentes. | La selección previa y sus cantidades se conservan; se muestran cantidades, subtotales y total estimado coherentes. |
| S-04 Acción fija | Con suficientes líneas para desplazar el carrito, localiza el icono de agregar de la barra inferior y selecciónalo. | La acción permanece accesible sin ir al final de la lista; permite volver al selector. Verifica también la etiqueta con TalkBack. |
| S-05 Existencias y recetas | Intenta superar stock; después vende dos elaborados que compartan un insumo ficticio limitado. | No permite registrar cantidades superiores a las existencias o al insumo compartido. No hay descuento parcial si la operación falla. |
| S-06 Descartar | Con artículos en el carrito, pulsa Atrás; repite desde comprobante de efectivo y QR de transferencia usando cancelar. En cada caso cancela primero el diálogo y luego vuelve a solicitar descarte y confirma. | El diálogo aparece, seguir vendiendo conserva la operación y descartar no registra venta ni descuenta stock. |
| S-07 Efectivo | Registra una venta de varias líneas; comprueba historial, existencia y arqueo del turno. | Se registra una sola venta, el total coincide y el stock baja una vez. El efectivo esperado refleja la venta. |
| S-08 Transferencia simulada | Usa datos ficticios, abre el QR y realiza el flujo de número de transacción/cliente sin realizar ningún pago bancario. Si el paso requiere datos válidos, usa exclusivamente datos de prueba autorizados. | Se informa el importe; se valida lo obligatorio y la app no afirma verificar un pago bancario. La operación queda correctamente reflejada. |
| S-09 Interrupciones | Durante el carrito rota la pantalla o recrea la actividad; también prueba volver de la búsqueda. Si se puede, cierra el turno durante el flujo en el entorno de pruebas. | No se pierden las selecciones que el flujo debe conservar; con turno cerrado se bloquea la confirmación. Registra exactamente lo observado. |
| S-10 Doble pulsación | En el equipo de prueba toca rápidamente confirmar y cancelar mientras se procesa una venta; revisa historial y stock. | No se registran dos ventas ni se permite descartar mientras se procesa. Repite solo con datos de prueba. |
| S-11 Accesibilidad | Prueba modo oscuro, fuente grande, TalkBack y orientación disponible. Recorre botones, errores y diálogo sin depender del color. | Texto legible, controles alcanzables, descripciones comprensibles y ninguna acción oculta o recortada. Documenta tamaño/escala y capturas anonimizadas. |

Para cobertura más amplia —cámara, respaldos, migración, actualizaciones y permisos— consulta `PRUEBAS_DISPOSITIVO.md`. Sus operaciones destructivas o que alteren red/licencia requieren autorización específica y dispositivos de prueba. No uses el comando de borrado de datos sugerido allí sobre un equipo con datos importantes.

## 5. Pruebas funcionales y de UX de DShalev

Usa solo la instancia aislada de la sección 2. Si el código ensayado no incluye todavía el nuevo carrito de búsqueda incremental, marca los casos correspondientes **no aplicables a esa revisión** y anota el commit/estado; no declares fallos de una función que no está presente.

| ID | Procedimiento | Resultado esperado |
| --- | --- | --- |
| D-10 Instalación | Inicia el servidor aislado y, por separado, el `.exe` en carpeta vacía. Crea administrador ficticio y vuelve a iniciar sesión. | Se abre la interfaz, no hay contraseña predeterminada y cada instancia guarda sus datos en su carpeta aislada. |
| D-11 Turno | Entra a Ventas sin abrir turno; luego ábrelo desde Inicio con fondo ficticio. | Sin turno no se vende; al abrirlo queda disponible el carrito. |
| D-12 Búsqueda y carrito | Crea al menos tres artículos ficticios. Busca el primero, agrégalo, cambia la búsqueda y agrega el segundo; cambia cantidades y pago. | Los artículos anteriores permanecen, el total estimado cambia correctamente y se puede quitar una línea. No se reserva stock por añadir al carrito. |
| D-13 Teclado y filtro | Usa F2, escribe y navega con Tab/Enter. Busca una cadena sin resultados y, si hay más de 30 coincidencias, precisa la búsqueda. | Foco visible y orden lógico; se informa cuando no hay resultados o se limita la lista. Se puede completar la operación sin ratón. |
| D-14 Validaciones | Prueba cantidad vacía, cero, negativa o excesiva; intenta vender más de la existencia y prueba un elaborado/servicio con receta sin insumos suficientes. | La interfaz o servidor bloquea lo inválido; una venta rechazada no registra movimientos ni descuenta parcialmente. |
| D-15 Confirmación y borrador | Añade líneas, recarga la pestaña y comprueba la recuperación si el navegador permite almacenamiento de sesión. Vacía el carrito: cancela el diálogo y después confirma. | El borrador se recupera en la misma pestaña y sesión; cancelar conserva los datos, confirmar los retira. Si el almacenamiento está bloqueado, documenta el límite observado. |
| D-16 Precio cambiado | Con un carrito preparado, cambia el precio desde otra sesión autorizada de prueba y registra el carrito original. | La venta se rechaza si el precio mostrado cambió; al recuperar el carrito se muestra el precio actualizado para revisar antes de reintentar. Sin venta ni descuento parcial al rechazar. |
| D-17 Registro | Registra una venta con varios artículos y comprueba Ventas, Inventario, Inicio, Reportes y Turnos. | Una sola venta; totales, método y existencias son coherentes. La venta en efectivo se refleja en el efectivo esperado. |
| D-18 Conexión interrumpida | Solo en datos ficticios, interrumpe la conexión durante una solicitud de registro si es posible. | No presupongas que falló ni reenvíes automáticamente. Revisa primero el historial y el stock; documenta si la operación se registró o no. |
| D-19 Sin JavaScript | Deshabilita JavaScript en una sesión de prueba y abre Ventas. | El formulario alternativo permite buscar y vender según sus capacidades; documenta limitaciones. Vuelve a habilitarlo al terminar. |
| D-20 Pantallas y accesibilidad | Prueba escritorio, ventana estrecha, zoom 200 %, modo claro/oscuro y navegación con teclado. Revisa venta, inventario, turnos y reportes. | Controles y mensajes alcanzables y legibles; navegación desplazable si no cabe; sin superposición que impida operar. Registra navegador, resolución y zoom. |
| D-21 CSV/XLSX | Exporta inventario, clientes y ventas con nombres ficticios que empiecen por `=`, `+`, `-` o `@`, incluidos espacios iniciales. Examina el CSV como texto y abre el XLSX en una aplicación de prueba. | CSV contiene protección mediante apóstrofo para texto potencialmente interpretable; XLSX conserva el texto como celda de texto. Los datos almacenados y los importes no cambian. El tratamiento de CSV depende de la herramienta que lo importe. |
| D-22 Respaldo | En dos carpetas de prueba independientes, crea respaldo cifrado, intenta abrir con contraseña incorrecta y restaura con la correcta en la segunda instancia. | La incorrecta se rechaza; la correcta restaura los datos de prueba. La restauración reemplaza los datos de destino: **nunca** usar producción. El usuario introduce la contraseña sin compartirla en el informe. |

## 6. Integración DShalev ↔ Shalev (opcional, requiere autorización)

Requiere PC y Android de pruebas en una red privada confiable, acceso físico a ambos y un respaldo de prueba. No abrir puertos a internet ni cambiar el router o firewall sin autorización. DShalev en modo LAN usa HTTP para el navegador: no usar redes públicas. El servidor nativo usa el puerto indicado en el QR; la conectividad depende de la red. La integración móvil de DShalev sigue en desarrollo y su licencia de prueba local no equivale a activación comercial GL.

1. En la instancia aislada, configura **Red local** y reiníciala como indica `DShalev/README.md`; confirma que el listener está activo. Desde Equipo crea una plaza ficticia y genera QR. El usuario maneja el teléfono y escanea el QR sin compartirlo en el informe.
2. Vincula Shalev de prueba como secundaria. Registra modelo/API y resultado sin publicar el código ni la clave. Si no enlaza, guarda el síntoma y marca el resto bloqueado; no inventes una solución.
3. Si vincula, realiza una venta ficticia en la secundaria, sincroniza y comprueba una sola operación y un solo descuento en escritorio. Desconecta/reconecta y repite sincronización para revisar idempotencia. Compara lo mostrado en historial y reportes.
4. Solo si corresponde a la versión y con autorización, prueba la retirada de la secundaria y su rechazo al reconectar. No retires dispositivos reales. Anota si las capacidades que probaste funcionan o están pendientes; una prueba de loopback no sustituye esta prueba física.
5. Vuelve a modo local en la instancia de prueba, reiníciala si corresponde y comprueba que los servicios de prueba se detienen. No cambies la configuración de una instalación habitual.

## 7. Informe de resultados

Entrega al usuario un informe resumido y, si lo autoriza, guárdalo como archivo separado **fuera de carpetas de datos y sin sobrescribir informes previos**. Incluye:

```text
Fecha y zona horaria:
Rama, commit y estado del árbol (incluidos cambios sin confirmar):
Equipo / Windows / navegador / resolución / zoom:
Python, Node, JDK, SDK / Gradle:
Android: modelo, versión y API (sin serial ni identificadores privados):
Instancias y carpetas aisladas utilizadas (sin secretos):

ID | Estado (aprobado / fallido / bloqueado / no ejecutado) | Observado | Evidencia segura
...

Pruebas automáticas: comando, código de salida, conteo real y ubicación del informe.
Defectos: pasos para reproducir, esperado, observado, frecuencia y gravedad.
Pendientes/bloqueos: causa, herramienta o autorización necesaria.
Advertencias: datos sensibles omitidos, interrupciones, restricciones y limpieza pendiente.
```

Diferencia explícitamente **tests automáticos**, **DOM simulado**, **pruebas visuales en navegador real**, **Android emulado**, **Android físico**, **ejecutable Windows** y **sincronización entre dispositivos**. No declares listo para producción ni paridad completa por haber aprobado una parte de la campaña. Pide autorización antes de borrar carpetas, datos, cuentas o artefactos de prueba.
