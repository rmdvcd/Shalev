# DShalev — licencia de canje GL: estado, decisiones y límites

**Fecha:** 2026-11-10 · **Ámbito:** `Web-Desktop/` (servidor web Python/FastAPI, EXE portable de Windows)
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

- Valores de compilación `APP_ID`, `ENDPOINT_REDEEM`, `ENDPOINT_ESTADO`, `KIDS_FIRMA` (vacíos en este build;
  el `appId` del contrato es `DSHALEV`, público, y solo se fija junto con el resto en un build aprobado).
- `definir(...)` valida HTTPS sin credenciales/query/fragmento y exige que cada `kid` sea SPKI DER P-256.
- `comercial_habilitada` exige las cuatro piezas; si falta algo, `motivo_deshabilitada()` explica qué.
- `POLITICA_IDENTIDAD = "instalacion"` y `TOLERANCIA_RELOJ = 2 h` como constantes de política.

### 3.4 `dshalev/licencia/almacen.py`

- `LicenciaLocal` guarda el sobre JSON firmado como **texto opaco**, su fecha de guardado, el último estado
  verificado y una marca de revocación **pegajosa**. El almacén no interpreta la firma; eso lo hace
  `verificador.py` antes de llamarlo.
- `AlmacenLicencia.observar()` devuelve la licencia utilizable y avisos: restauración antigua, licencia de otro
  equipo, recuperación desde el ancla y retroceso de reloj. Nunca borra archivos.
- `guardar_licencia()` escribe primero el ancla (resguardo) y luego el espejo local; si falla el segundo, la
  licencia se recupera al reiniciar y el error lo dice.
- `registrar_estado()` aplica un estado ya verificado y **rechaza documentos con `checkedAt` anterior o igual**
  al último aceptado; una revocación confirmada no se borra con un documento posterior que diga `ACTIVA`.
- `AnclaInstalacion` usa una huella propia; un ancla de otra instalación falla cerrado y no se borra.

### 3.5 `dshalev/licencia/contrato.py`

- Reproduce `canonical()` de GL: cada campo UTF-8 con longitud **uint32 big-endian**, sin JSON ni delimitadores.
- Transcripts de canje y estado, bytes firmados de la licencia y del documento de estado, en el orden exacto
  del contrato.
- Normalización del código (sin espacios ni guiones, mayúsculas, alfabeto de 34 símbolos sin `I`/`O`, 16
  caracteres) y formato `XXXX-XXXX-XXXX-XXXX` solo para mostrar.
- Huella de instalación `Base64url(SHA-256(SPKI))` sin relleno; validación de tipos, límites y coherencia
  (perpetua sin vencimiento, vencimiento posterior a la emisión, cupo 0–10, fechas ISO en `Z`).
- Sub-token `GL-REDEEM-SUB1` con separadores `|`, implementado y probado, **sin activación comercial**.

### 3.6 `dshalev/licencia/verificador.py`

- Valida sobre, versión, `alg`, `kid` contra el mapa fijado y firma ECDSA P-256/SHA-256 en DER antes de leer
  los campos.
- `verificar_licencia()` exige `appId`, fechas y estado coherentes; comprueba que
  `SHA-256(devicePublicKey) == deviceThumbprint == huella local`.
- `verificar_estado()` exige que la licencia, la app, el equipo y los campos inmutables coincidan con la
  licencia guardada, y rechaza `checkedAt` más de 5 minutos en el futuro.
- `verificar_subtoken()` comprueba cupo, plazo, estado de la primaria, huella secundaria y firma con la clave
  de la instalación primaria. No se expone en ninguna ruta.

### 3.7 `dshalev/licencia/cliente_gl.py`

- `canjear()` normaliza el código, genera `requestId` (UUID) y `nonce` (16 bytes), firma el transcript y envía
  `POST /v1/redeem` al endpoint fijado. El código no se escribe en disco ni se registra.
- Si se pierde la respuesta, conserva **en memoria** `requestId`, instante y nonce junto a una huella del
  código; al reintentar con el mismo código reutiliza la solicitud exacta. Si el proceso termina, el usuario
  debe volver a escribir el código (se explica en la UI).
- Verifica la respuesta completa antes de guardar; una licencia vencida o revocada no sustituye a la anterior.
- `consultar_estado()` firma `POST /v1/status` con la licencia guardada, verifica el documento y persiste la
  revocación. Un fallo de red no borra ni habilita nada; una respuesta antigua no reactiva.
- HTTPS obligatorio, contexto TLS por defecto con certificado y nombre verificados, sin redirecciones
  (`3xx` se rechaza), tiempo de espera de 15 s y límites de 16 KiB de petición y 64 KiB de respuesta.
- Nunca se registran códigos, pruebas, firmas, tokens, IP ni datos personales.

## 4. Lo que **no** está implementado (pendiente)

1. **Enforcement en el backend**: rutas web, sincronización, comandos y operaciones en curso. El cliente ya
   produce un estado verificable, pero todavía no hay una compuerta que lo exija; debe dejar accesibles
   activación y recuperación y no bloquear operaciones en curso a medias.
2. **UI**: panel con huella del equipo, estado, último estado conocido, política offline, mensaje de activación
   deshabilitada y campo de código. El panel actual sigue siendo informativo y no filtra secretos.
3. **Transferencia manual**: mostrar la huella del equipo nuevo y exigir autorización del operador. No se
   presenta restaurar una carpeta o un respaldo como migración.
4. **Sub-token / apps secundarias**: se implementó y probó la verificación, pero **no** se activa
   comercialmente, no se emite en ninguna ruta ni se pide subir `MAX_SECUNDARIAS`. La verificación en las apps
   reales, la distribución y la convivencia con `SPVI2:` no están demostradas; el slot no impide por sí solo
   reutilizarlo en varios dispositivos y no hay revocación instantánea sin conexión. Cualquier cambio en
   clientes secundarios va por separado.
5. **Configuración de producción**: no hay endpoint ni claves aprobadas; `comercial_habilitada` sigue en
   `False` y la activación comercial está deshabilitada con mensaje explícito.
6. **Windows/DPAPI, EXE y extremo a extremo real**: las pruebas se ejecutaron en Linux. La carpeta de pruebas
   incluye un servidor HTTPS local con certificado propio para verificar TLS, hostname y rechazo de
   redirecciones, pero **no** se probó contra el servicio GL real ni en Windows.

## 5. Pruebas añadidas (Linux, `pytest`)

| Archivo | Qué cubre |
|---|---|
| `tests/test_licencia_identidad.py` | P-256 persistente, huella = SHA-256(SPKI) Base64url, firma DER verificable, no regeneración con archivo dañado, no reemplazo, concurrencia, aislamiento del modo de prueba (incluido rechazo en Windows simulado), archivo DPAPI inabrible fuera de Windows, ruta por instalación, `repr` sin clave. |
| `tests/test_licencia_config.py` | Build sin endpoint ni claves ⇒ activación deshabilitada y motivo; build válido habilita; HTTPS obligatorio; rechazo de credenciales/query/fragmento/puerto/host inválidos; `kid` inválido, Base64 inválido, clave no P-256; ajustes y respaldos no cambian la configuración; el código no contiene claves privadas ni endpoints de prueba. |
| `tests/test_app.py` (nuevo caso) | El panel `/licencia` muestra el motivo de activación deshabilitada y no pide ni filtra códigos, claves ni URLs. |
| `tests/test_licencia_almacen.py` | Guardado sellado (sin documento en claro), restauración antigua conserva la licencia más reciente y la reescribe, licencia de otro equipo no se activa y no destruye la propia, archivo dañado con/sin resguardo, reloj retrocedido falla cerrado sin bajar la marca, ancla de otra instalación, fallo al escribir el espejo no pierde la licencia, concurrencia, documentos inválidos, ancla fuera de `DShalevData`. |
| `tests/test_licencia_contrato.py` | Bytes exactos del vector GL (canje, estado, licencia, estado firmado, sub-token), firmas DER, huellas SPKI, normalización y códigos malformados, caducidad/perpetuidad/revocación, mutaciones de campos, firmas, `kid`, app, dispositivo, estructura y fechas, sub-token propio y mutado. |
| `tests/test_licencia_cliente.py` | Canje completo con prueba verificable, código inválido sin red, respuesta perdida y reintento con la misma solicitud, error del servicio sin guardar, respuestas mutadas (firma/app/equipo/`kid`), caducidad y revocación, redirección y cuerpo excesivo, estado con revocación persistente, fallo de red con licencia previa, respuesta antigua, reloj retrocedido, concurrencia local, configuración deshabilitada/HTTP y servidor HTTPS real con certificado de prueba (hostname verificado y `302` rechazado). |

Ejecución: `python -m pytest -q` → **197 passed** (112 previas + 85 nuevas). Node: **6 passed**. Sin red
externa; el servidor HTTPS de las pruebas escucha solo en `127.0.0.1`.

## 6. Riesgos abiertos

- **Contrato GL**: resuelto; `rmdvcd/GL` es accesible y el vector `dshalev-protocol-v1.json` se reproduce byte
  a byte. Aun así, el servicio tiene riesgos propios que no se corrigen desde el cliente:
  - dos renovaciones simultáneas sobre la misma instalación no están serializadas;
  - el `slot` del sub-token no impide reutilizarlo en varios dispositivos y no hay revocación instantánea
    offline;
  - una licencia perpetua desconectada puede no enterarse nunca de una revocación.
- **Sin configuración de producción** aprobada no hay activación real; no se declara listo para producción.
- **Windows/DPAPI y EXE** siguen sin probarse en este entorno; las pruebas de identidad no Windows se omiten
  en Windows y las de permisos POSIX se omiten allí.
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

1. ✅ Contrato GL disponible y vector `test/fixtures/dshalev-protocol-v1.json` reproducido byte a byte en
   Python, con verificación de firmas y mutaciones.
2. ⬜ Endpoint y claves de **producción** aprobados, fijados en el build, con TLS y hostname verificados.
3. ✅ Pruebas de respuesta perdida/reintento idempotente, concurrencia, red caída con licencia previa,
   reloj, revocación verificada seguida de desconexión, estado antiguo, restauraciones y aislamiento de locales
   (las de reinicio se cubren al releer el almacén desde disco).
4. ⬜ Enforcement probado en rutas web y sincronización; UI sin secretos.
5. ⬜ Ejecución real en Windows con DPAPI y EXE portable verificado, más extremo a extremo contra el servicio GL.
6. ⬜ Decisión comercial confirmada sobre apps secundarias (la de instalación ya está tomada).
