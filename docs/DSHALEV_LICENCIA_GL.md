# DShalev — licencia de canje GL: estado, decisiones y límites

**Fecha:** 2026-11-10 · **Ámbito:** `DShalev/` (servidor web Python/FastAPI, EXE portable de Windows)
**Estado:** base local implementada y probada; **canje, estado y enforcement pendientes del contrato GL**.

Este documento sustituye a `docs/DSHALEV_GL_SERVICIO_PROPUESTA.md` como referencia de lo construido. La propuesta
sigue siendo válida como intención, pero ya no describe el repositorio: aquí se separa lo implementado de lo que
falta y de las decisiones que requieren aprobación.

## 1. Lo que se verificó en el repositorio real

- DShalev es **Python 3.11+ / FastAPI**, empaquetado con PyInstaller en un único `DShalev.exe`. No es .NET.
- `dshalev/templates/license.html` era un panel **informativo**: no activa ni valida nada.
- `dshalev/sync_server.py` entrega a las apps secundarias una **prueba local de siete días** (`TRIAL_MS`,
  `_trial_license`). No es una licencia GL, no está firmada por GL y no es un sub-token.
- `dshalev/database.py` protege la clave de la base con DPAPI en Windows (`DSPAPI1`) y usa un archivo de clave
  local fuera de Windows (`DSKEY1`). Esa protección **no** crea ni implica una identidad de licencia.
- `dshalev/config.py` guarda `portable-settings.json` con la clave de sesión y el modo de red. Nada de eso debe
  gobernar el endpoint, el `appId` ni las claves de confianza de la licencia.
- No existe ningún verificador `SPVI2:` en DShalev, ni una implementación del canje GL.
- **El contrato GL no está en este repositorio.** No hay `services/redeem/src/protocol.js`, `canonical.js`,
  `keys.js`, `license.js`, `subtoken.js`, `client/redeem-client.js`, `services/redeem/README.md` ni
  `test/fixtures/dshalev-protocol-v1.json`. El repositorio `rmdvcd/GL` no es accesible con las credenciales de
  esta sesión. Por eso **no se ha escrito ni una línea del formato de cable**: inventarlo produciría licencias
  incompatibles y una falsa sensación de seguridad.
- Conjunto del repositorio antes de este cambio: `54` pruebas Python y `5` de Node en verde. Después: `107`
  Python y `5` Node en verde (Linux; el EXE y DPAPI no se ejecutaron aquí).

## 2. Decisiones tomadas

| Decisión | Valor | Motivo |
|---|---|---|
| Unidad licenciada | **Una identidad y una licencia por instalación** (`DShalevData/licencia/`), compartida por todos los `--local` | Evita conceder licencias por local sin querer y permite que un negocio con varios locales en el mismo PC tenga una sola activación. Es una decisión comercial **pendiente de confirmar**; si se quiere cobrar por local, el contrato GL debe admitir el vínculo y hará falta migración. |
| Identidad | P-256 (`secp256r1`), clave privada PKCS#8 protegida con DPAPI del usuario en Windows | Requisito del contrato de canje. DPAPI liga el archivo al usuario y equipo, pero **no** es atestación de hardware. |
| Exportación pública | Solo SPKI DER. Huella = `Base64url(SHA-256(SPKI))` sin `=` | Formato pedido por el contrato; no se exporta la privada en ningún camino. |
| Protección fuera de Windows | Modo `PRUEBA` (AES-256-GCM con clave local) **solo** con `DSHALEV_ALLOW_DEV_IDENTITY=1` y **nunca** en Windows | Permite probar en Linux/macOS sin abrir una puerta en la distribución real. El modo se rechaza en Windows incluso si el archivo lo pide. |
| Regeneración de identidad | Nunca automática | Si el archivo existe y falla la carga, se lanza error y se conserva. Regenerar invalidaría la licencia emitida y parecería una activación nueva. |
| Endpoint, `appId`, `kid` → SPKI | Fijados en `dshalev/licencia/config_licencia.py`, vacíos en este build | No se leen de formularios, de `portable-settings.json` ni de un respaldo. Con valores vacíos la activación comercial queda **deshabilitada** con mensaje explícito. |
| Validación de endpoint | Solo `https://`, con host, sin credenciales en la URL, sin query ni fragmento, puerto válido | La verificación TLS y de nombre se hará en el cliente HTTP cuando exista el canje; nunca se desactivará ni se seguirán redirecciones a otros hosts. |
| Estado local | `DShalevData/licencia/licencia.bin`, sellado y ligado a huella + `appId`, escritura atómica (temporal + `fsync` + `replace`) | Un archivo copiado de otro equipo no se activa. No hay API para borrar una licencia válida ante un error. |
| Resguardo (ancla) | Fuera de `DShalevData`: `%LOCALAPPDATA%\DShalev\ancla-licencia.bin` en Windows; `XDG_DATA_HOME`/`~/.local/share` fuera de Windows | Guarda la licencia más reciente y la marca de agua del reloj. Así una restauración de datos anterior no destruye ni sustituye la licencia vigente, y se detecta la copia vieja. |
| Reloj | Marca de agua que nunca retrocede, tolerancia de **2 h**; retroceso mayor ⇒ `reloj_retrocedido` y la licencia no se habilita | Detecta un ajuste burdo. **No** es un reloj confiable antimanipulación; el usuario con control del equipo puede esperar o manipular el sistema. |
| Restauración de datos | El flujo de respaldo existente **no se toca**. Restaurar una carpeta no es transferir la identidad | Una copia de otro equipo no activa (huella/DPAPI); una copia anterior no destruye la licencia del ancla. Si el ancla falta, no se habilita nada nuevo y se documenta la recuperación. |

### 2.1 Consecuencias prácticas

- Instalación nueva: `DShalevData/licencia/identidad.bin` se crea la primera vez que se pide la identidad
  (hoy solo desde las pruebas; todavía no hay activación).
- Copiar `DShalevData` a otro PC: la licencia **no** se activa (DPAPI y huella). No es una migración.
- Restaurar un respaldo `.shalev` anterior: no borra la licencia; el ancla conserva la más reciente y la vuelve
  a escribir en la carpeta de datos.
- Prueba local de siete días de las secundarias: sigue igual y **no** se presenta como licencia comercial.
- `--local`: comparte identidad y licencia con la instalación. No se emite una licencia por local.

## 3. Lo implementado

### 3.1 `dshalev/licencia/proteccion.py`

- DPAPI de usuario (`CryptProtectData`, sin interfaz) con cabecera `DSLDPAPI1`.
- Modo `PRUEBA` (`DSLPRUEBA1`) con AES-256-GCM y clave local `*.devkey`; rechazado en Windows y sin la variable
  de entorno; espera acotada si otra ejecución está creando la clave.
- Escritura atómica con temporal, `fsync`, `chmod 0600` y `os.replace`.
- Los errores no incluyen material de clave ni contenido sellado.

### 3.2 `dshalev/licencia/identidad.py`

- Creación con `O_CREAT | O_EXCL` (nunca sobrescribe), `fsync` y limpieza si falla la escritura.
- Carga estricta: cabecera, versión, PKCS#8 y curva P-256; error explícito si está dañada.
- `firmar(bytes)` → ECDSA P-256/SHA-256 en DER (lo exigirá el transcript del canje).
- `huella`, `spki_der`, `spki_b64url`; la privada no se expone ni aparece en `repr`.
- Concurrencia: varias ejecuciones obtienen la misma identidad; la que pierde la carrera espera a que la
  ganadora termine de escribir.

### 3.3 `dshalev/licencia/config_licencia.py`

- Valores de compilación `APP_ID`, `ENDPOINT_REDEEM`, `ENDPOINT_ESTADO`, `KIDS_FIRMA` (vacíos en este build).
- `definir(...)` valida HTTPS sin credenciales/query/fragmento y exige que cada `kid` sea SPKI DER P-256.
- `comercial_habilitada` exige las cuatro piezas; si falta algo, `motivo_deshabilitada()` explica qué.
- `POLITICA_IDENTIDAD = "instalacion"` y `TOLERANCIA_RELOJ = 2 h` como constantes de política.

### 3.4 `dshalev/licencia/almacen.py`

- `LicenciaLocal` guarda el documento firmado como **texto opaco** (el formato lo definirá GL) y su fecha de
  guardado. No se interpretan campos que aún no están especificados.
- `AlmacenLicencia.observar()` devuelve la licencia utilizable y avisos: restauración antigua, licencia de otro
  equipo, recuperación desde el ancla y retroceso de reloj. Nunca borra archivos.
- `guardar_licencia()` escribe primero el ancla (resguardo) y luego el espejo local; si falla el segundo, la
  licencia se recupera al reiniciar y el error lo dice.
- `AnclaInstalacion` usa una huella propia; un ancla de otra instalación falla cerrado y no se borra.

## 4. Lo que **no** está implementado (bloqueado o pendiente)

1. **Cliente `POST /v1/redeem`**: normalización del código, `requestId`, nonce, transcript con longitud
   `uint32` big-endian, firma ECDSA DER, ventana de `requestedAt`, idempotencia ante respuesta perdida. Requiere
   `protocol.js`, `canonical.js`, `keys.js`, `license.js` y el vector.
2. **Validación de la licencia firmada**: versión, `alg`, `kid`, firma DER, estructura, tipos, límites, `appId`,
   fechas, estado y `deviceThumbprint` contra la SPKI local. Requiere el esquema real.
3. **`POST /v1/status` autenticado** y persistencia de la revocación verificada. Requiere el contrato.
4. **Enforcement en el backend**: rutas web, sincronización, comandos y operaciones en curso. Se hará cuando
   exista un estado de licencia con derechos y fechas que aplicar; dejar activación y recuperación accesibles.
5. **UI**: panel con huella del equipo, estado, último estado conocido, política offline y mensaje de activación
   deshabilitada. El panel actual sigue siendo informativo y no filtra secretos.
6. **Transferencia manual**: mostrar la huella del equipo nuevo y exigir autorización del operador. No se
   presenta restaurar una carpeta o un respaldo como migración.
7. **Sub-token / apps secundarias**: **no** se activa comercialmente, **no** se emite el sub-token `GL-REDEEM-SUB1`
   ni se pide subir `MAX_SECUNDARIAS`. La verificación en las apps reales, la distribución y la convivencia con
   `SPVI2:` no están demostradas; el slot no impide por sí solo reutilizarlo en varios dispositivos y no hay
   revocación instantánea sin conexión. Cualquier cambio en clientes secundarios va por separado.
8. **Pruebas de extremo a extremo**: vector GL reproducido byte a byte, servicio de prueba HTTPS, Windows con
   DPAPI y EXE portable verificado. Nada de eso se ejecutó aquí.

## 5. Pruebas añadidas (Linux, `pytest`)

| Archivo | Qué cubre |
|---|---|
| `tests/test_licencia_identidad.py` | P-256 persistente, huella = SHA-256(SPKI) Base64url, firma DER verificable, no regeneración con archivo dañado, no reemplazo, concurrencia, aislamiento del modo de prueba (incluido rechazo en Windows simulado), archivo DPAPI inabrible fuera de Windows, ruta por instalación, `repr` sin clave. |
| `tests/test_licencia_config.py` | Build sin endpoint ni claves ⇒ activación deshabilitada y motivo; build válido habilita; HTTPS obligatorio; rechazo de credenciales/query/fragmento/puerto/host inválidos; `kid` inválido, Base64 inválido, clave no P-256; ajustes y respaldos no cambian la configuración; el código no contiene claves privadas ni endpoints de prueba. |
| `tests/test_app.py` (nuevo caso) | El panel `/licencia` muestra el motivo de activación deshabilitada y no pide ni filtra códigos, claves ni URLs. |
| `tests/test_licencia_almacen.py` | Guardado sellado (sin documento en claro), restauración antigua conserva la licencia más reciente y la reescribe, licencia de otro equipo no se activa y no destruye la propia, archivo dañado con/sin resguardo, reloj retrocedido falla cerrado sin bajar la marca, ancla de otra instalación, fallo al escribir el espejo no pierde la licencia, concurrencia, documentos inválidos, ancla fuera de `DShalevData`. |

Ejecución: `python -m pytest -q` → **107 passed** (54 previas + 53 nuevas). Node: **5 passed**. Sin red.

## 6. Riesgos abiertos

- **Contrato GL**: sin los archivos no hay interoperabilidad demostrable. No se declara listo para producción.
- **Renovaciones simultáneas** sobre la misma instalación y **reutilización de slots** de secundarias siguen
  siendo riesgos abiertos del servicio GL; este cambio no los resuelve ni supone cambios en el servidor.
- **DPAPI**: protege contra copia a otro usuario/equipo, no contra un administrador del mismo equipo ni contra
  código que se ejecute como ese usuario. No es atestación.
- **Un proceso por instalación**: el almacén usa un cerrojo en memoria; el README ya prohíbe dos procesos sobre
  el mismo local. No hay bloqueo entre procesos.
- **Reloj**: la marca de agua detecta retrocesos burdos; no impide esperar ni adelantar el reloj dentro de la
  tolerancia. Un salto hacia adelante (a mano o por error) deja la marca en el futuro y la licencia queda sin
  habilitar hasta que la hora real la alcance; es el precio de fallar cerrado.
- **Revocación offline**: una licencia perpetua desconectada puede no enterarse nunca. La UI deberá explicarlo.
- **Ancla perdida** (perfil de Windows reinstalado): se conserva la licencia de `DShalevData`, pero se pierde la
  detección de restauraciones antiguas hasta que se vuelva a escribir. No se habilita nada nuevo sin licencia
  válida.

## 7. Recuperación y transferencia (borrador operativo)

- **Mismo equipo, carpeta restaurada**: el ancla recupera la licencia más reciente; no hay que reactivar.
- **Equipo nuevo**: la licencia no se hereda copiando archivos. Hace falta una **transferencia manual autorizada
  por el operador** (pendiente de implementar con el contrato): el operador ve la huella del equipo nuevo y el
  servicio emite/transfiere. Restaurar un respaldo **no** es migración de identidad.
- **Identidad dañada**: no se regenera. Se conserva `DShalevData` y el usuario de Windows; si es irrecuperable,
  la licencia debe reemitirse por transferencia manual.
- **Ancla de otra instalación**: no se borra automáticamente; se restaura el perfil original o se usa la
  transferencia manual.

## 8. Criterios para declarar la activación comercial lista

1. Contrato GL disponible y vector `test/fixtures/dshalev-protocol-v1.json` reproducido byte a byte en Python.
2. Endpoint y claves de **producción** aprobados, fijados en el build, con TLS y hostname verificados.
3. Pruebas de respuesta perdida/reintento idempotente, concurrencia, red caída con licencia previa, reinicio,
   reloj, revocación verificada seguida de desconexión, estado antiguo, restauraciones y aislamiento de locales.
4. Enforcement probado en rutas web y sincronización; UI sin secretos.
5. Ejecución real en Windows con DPAPI y EXE portable verificado, más extremo a extremo contra el servicio GL.
6. Decisión comercial confirmada sobre instalación frente a locales y sobre apps secundarias.
