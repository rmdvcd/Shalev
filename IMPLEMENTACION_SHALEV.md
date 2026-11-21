# IMPLEMENTACIÓN — Repositorio `rmdvcd/Shalev`

> **Para quién es este documento:** un agente de IA (arena.ai) con acceso de lectura/escritura a `rmdvcd/Shalev`.
> **Objetivo:** que los clientes (Android, iOS y DShalev/Windows) dejen de depender de claves fijadas a mano, corrijan los fallos de reloj y de renovación, anclen correctamente los permisos de apps secundarias y puedan activar/renovar con **canje de código** contra el servicio Cloudflare Workers + D1 (repo `rmdvcd/GL`), conservando `SPVI2` como respaldo offline.
> **Documento hermano:** `IMPLEMENTACION_GL.md`. Comparten los Anexos A y B (idénticos). No dependas de que el otro repo esté terminado: este documento incluye la especificación necesaria.

---

## 0. Reglas de trabajo (obligatorias)

1. **Lee primero** `README.md`, `SECURITY.md`, `LICENSE_CLIENT.md`, `docs/GL_ESPECIFICACION.md`, `docs/HISTORIAL_DESARROLLO.md`, `docs/DSHALEV_GL_SERVICIO_PROPUESTA.md` y `.github/workflows/ci.yml` (de ahí salen los comandos reales de prueba). Si existe `AGENTS.md`, respétalo.
2. **No rompas lo desplegado:** el formato `SPVIR1:`/`SPVI2:` (210 caracteres, `SPVI-L2|`, huella `SPVI-L2|deviceId|devicePub`), el vector dorado y la verificación de licencias ya instaladas siguen funcionando **igual**. El canje es una vía **paralela**; nunca lo llames `SPVI2`.
3. **Cero secretos** en el repo. Claves de prueba solo en tests y marcadas. No registres códigos de canje, tokens ni datos personales en logs.
4. **Honestidad de verificación.** Si no hay JDK/Android SDK/Xcode, escribe código y pruebas y márcalos **«no ejecutados»**. Ejecuta lo que sí puedas (pruebas Python de `Web-Desktop/tests`). No afirmes que compilan sin haberlo hecho.
5. **Un commit por tarea:** `fase.tarea: resumen`. Rama propia `arena/<id>-impl`. No toques `main`.
6. **Si el código real contradice este documento, gana el código:** detente en esa tarea, anótalo en `docs/IMPLEMENTACION_NOTAS.md` y sigue con la siguiente.
7. **Entradas que solo puede dar el propietario** (no las inventes; usa marcadores y tests con claves de prueba): `RAIZ_A_SPKI`, `RAIZ_B_SPKI`, `REDEEM_BASE_URL` (URL del Worker), `GITHUB_REPO_SLUG`. Hasta que existan, `comercial_habilitada()`/equivalentes siguen devolviendo `false`.
8. Si añades una dependencia nueva, justifícala en el PR; prefiere las ya presentes (Python: `cryptography`; Android/iOS: las del proyecto).

## 1. Estado de partida (hechos verificados, no los re-discutas)

| ID | Hecho | Dónde |
|---|---|---|
| S1 | Las claves de GL están **fijadas a mano** en cada plataforma: 1 ECDH + 6 claves de firma sin fecha de corte. Cada rotación obliga a publicar APK/IPA/EXE. El 22/10 ninguna licencia se podía activar por esto. El comentario de `LicenseTrust.kt` está obsoleto (menciona `fb3a`/`113f` como vigentes; el código fija `0289`/`d252`, y las huellas declaradas **sí** coinciden con el Base64). | `Android/licencia/.../LicenseTrust.kt`, `iOS/Sources/Domain/License.swift` (`GLTrust`), `Web-Desktop/dshalev/licencia/config_licencia.py` |
| S2 | `VerificadorPermisoSecundaria` valida el sub-token con la clave `firmaPub` **que viene en el mismo mensaje** y, sin sub-token, acepta la licencia «tal cual». Cualquiera puede firmar su propio permiso: la cuota de secundarias no queda protegida. | `Android/data/.../licencia/VerificadorPermisoSecundaria.kt` |
| S3 | `LicenseManager.instalar()` descarta como `Outdated` toda licencia con `emitidaEn` anterior a la instalada. Si GL emitió una vez con el reloj adelantado, las renovaciones pagadas posteriores se rechazan. | `LicenseManager.kt` |
| S4 | `evaluate()` devuelve `ClockTampered` si `now < lastSeen − 2 h`, y `lastSeen` se fusiona con un registro externo que **sobrevive a la reinstalación**. Un único arranque con reloj adelantado bloquea la app hasta esa fecha; no hay vía de recuperación. | `LicenseManager.kt`, `prueba/RegistroPrueba.kt` |
| S5 | La clave maestra `GLMK-PERM-MAXS-9999` sigue en `src/main` (`CodigoLicencia.MAESTRA`). Solo actúa con `esDebug`, pero el literal viaja en el release. GL ya eliminó la suya (GL-02). Además hay dos fuentes de verdad para debug (`ModoDesarrollo.esDebug` y el parámetro `esDebug` del `LicenseManager`). | `contract/CodigoLicencia.kt`, `ModoDesarrollo.kt` |
| S6 | DShalev ya trae cliente de canje (`Web-Desktop/dshalev/licencia/`: `cliente_gl.py`, `verificador.py`, `almacen.py`, `identidad.py`, `proteccion.py`, `contrato.py`) pero `APP_ID`, `ENDPOINT_REDEEM`, `ENDPOINT_ESTADO` y `KIDS_FIRMA` están **vacíos** → activación comercial deshabilitada. Tiene tests en `Web-Desktop/tests/test_licencia_*.py`. | `config_licencia.py` |
| S7 | Android e iOS **no** tienen cliente de canje; solo `SPVI2`. | — |
| S8 | La revocación de `SPVI2` depende de `revocadas.json` (Release `revocaciones`), consultada semanalmente al abrir la app (`MainScaffold.kt`, `ActualizacionesRepositoryImpl.kt`). | — |

## 2. Decisiones que debe confirmar el propietario (valores por defecto entre paréntesis)

1. `appId` por plataforma (`DSHALEV`, `SHALEV-ANDROID`, `SHALEV-IOS`).
2. Si Android/iOS ofrecen canje además de `SPVI2` (sí) o solo DShalev (no recomendado).
3. Política cuando hay licencia `SPVI2` **y** licencia de canje: gana la de mayor vencimiento; perpetua gana siempre.
4. `MAX_SECUNDARIAS` del servicio sigue en **0** hasta que cada app primaria emita sub-tokens de canje (Fase 3).

## 3. Plan por fases

Orden estricto. **No avances si fallan las pruebas de la fase actual.**

### FASE 0 — Preparación

- Crea la rama. Ejecuta y registra el **baseline**: pruebas Python (`python -m pytest Web-Desktop/tests -q`, o el comando de `ci.yml`); si hay JDK, la tarea Gradle documentada (`shalevTests`/`shalevCheck` en `Android/`). Guarda resultados y lo que no puedas ejecutar.
- Crea `docs/IMPLEMENTACION_NOTAS.md` (bitácora de decisiones y discrepancias).

---

### FASE 1 — Directorio de confianza (Android, iOS, DShalev)

Implementa el **Anexo A** en las tres plataformas, con la misma semántica.

#### 1.1 Núcleo compartido por plataforma

- **Android** (`Android/licencia/`): nuevo `TrustDirectory` (parser, verificador, `seq` anti-retroceso, consultas `signKeysFor(emitidaEn)`, `ecdhVigente()`, `redeemKey(kid)`). `LicenseTrust` pasa a ser un envoltorio que lee del directorio efectivo (mayor `seq` entre bootstrap y caché).
- **iOS** (`iOS/Sources/Domain/`): equivalente en Swift (CryptoKit); `GLTrust` consulta el directorio efectivo.
- **DShalev** (`Web-Desktop/dshalev/licencia/`): nuevo `directorio.py` con `cryptography`; `config_licencia.py` deja de tener `KIDS_FIRMA` fijo: la clave `canje` sale del directorio.
- Fijar **dos raíces** (`RAIZ_A_SPKI`, `RAIZ_B_SPKI`) como constantes de compilación. Mientras sean marcadores, las pruebas usan raíces de prueba inyectadas; el build de producción debe **fallar** (no degradar) si las raíces siguen siendo marcadores.

#### 1.2 Bootstrap embebido

- Genera `directorio-bootstrap.json` **a partir de las claves actuales** (1 ECDH + 6 firmas de `LicenseTrust.kt`), con estas reglas: cada firma histórica `estado:"retirada"`; la vigente `estado:"vigente"`; `desde` según fechas de `docs/HISTORIAL_DESARROLLO.md` (30/09, 05/10, 15/10 ×2, 22/10 y la actual); `hasta` **solo si la fecha de relevo está documentada**, si no `null`. El archivo debe ir **firmado por la raíz**: pídeselo al propietario con `IMPLEMENTACION_GL.md` fase 2 (`tools/directorio`). Mientras no exista, deja el bootstrap de prueba y marca la tarea **bloqueada por el propietario** (no firmes con claves tuyas).
- Todas las licencias ya emitidas (vector dorado, licencia larga del 03/10) deben seguir verificando: añade/ajusta pruebas.

#### 1.3 Descarga, caché y anti-retroceso

- URL: `https://github.com/<GITHUB_REPO_SLUG>/releases/download/revocaciones/directorio.json` (mismo patrón que `revocadas.json`; reutiliza `ActualizacionesRepositoryImpl` y la consulta semanal de `MainScaffold.kt`; en iOS y DShalev, su equivalente).
- Solo HTTPS, solo ese host, límite 64 KiB, tiempo de espera 10 s. Un archivo inválido **no** sustituye al actual y no bloquea nada.
- Caché en almacenamiento privado (Android: DataStore/archivo interno; iOS: Application Support; DShalev: `DShalevData/licencia/` protegido con DPAPI como el resto). Re-verifica la firma **en cada arranque**, no solo al descargar.

#### 1.4 Pruebas obligatorias (cada plataforma)

Vector `docs/vectores/directorio-v1.json` (lo genera GL; si no está en este repo, créalo con claves de prueba propias siguiendo el Anexo A y marca la verificación cruzada con el CLI de GL como pendiente). Casos: válido; firma alterada; `seq` menor (rechazar); mismo `seq` idéntico (ok); SPKI no P-256; huella incorrecta; > 64 KiB; dos `ecdh` vigentes; clave `comprometida` ⇒ licencia firmada por ella rechazada; licencia con `emitidaEn` fuera de `[desde,hasta]` ⇒ rechazada.

#### 1.5 Limpieza asociada (S5)

- Corrige el comentario de `LicenseTrust.kt`.
- Mueve `CodigoLicencia.MAESTRA` y la lógica de clave maestra a `src/debug` (o variante equivalente) para que el literal **no esté** en el APK release; añade una prueba que escanee el release (o el código fuente de `main`) buscando `GLMK-PERM-MAXS-9999`.
- Unifica la fuente de verdad de debug (elimina `ModoDesarrollo.esDebug` o el parámetro del `LicenseManager`, no ambos).

**Cierre Fase 1:** pruebas del directorio verdes; licencias legado verificando; sin literal maestro en `main`.

---

### FASE 2 — Reloj y renovación

#### 2.1 `Outdated` por reloj (S3) — Android, iOS, DShalev (si aplica)

En `LicenseManager.instalar()` (y equivalentes) sustituye la regla `payload.emitidaEn < current.emitidaEn` por **dominancia**: rechazar como `Outdated` solo si la nueva licencia está dominada por la instalada, es decir
`nueva.emitida ≤ actual.emitida` **y** `nueva.vence ≤ actual.vence` (perpetua = ∞) **y** `nueva.secundarias ≤ actual.secundarias` **y** `nueva.id ≠ actual.id`.
En cualquier otro caso se instala (renovaciones con `emitidaEn` anterior pero vencimiento mayor, ampliaciones con más secundarias, etc.). Mantén el rechazo de licencias anteriores a `migratedAt`.
Pruebas: renovación con emitida anterior y vencimiento mayor → instalada; ampliación (mismo vencimiento, más secundarias) → instalada; licencia idéntica o más vieja y dominada → `Outdated`; posterior a transferencia → igual que antes.

#### 2.2 Salida del `ClockTampered` (S4)

- Nueva interfaz `RelojConfiable { suspend fun ahora(): Instant? }` (HTTPS con TLS a host fijado, tiempo de espera 3 s, sin reintentos en el arranque). Orden de fuentes: `GET {REDEEM_BASE_URL}/v1/time` (campo `now`), y como alternativa el encabezado `Date` de la respuesta de `github.com`. Acepta solo si la respuesta es válida y el reloj local no se usa para validarla.
- En `evaluate()`, **antes** de devolver `ClockTampered`:
  1. Pide hora confiable `T`.
  2. Si `T` existe y `T ≥ lastSeen − tolerancia`: el reloj local estaba mal pero el registro no → **evalúa la licencia con `T` como `now`** y no marques manipulación.
  3. Si `T` existe y `T < lastSeen − tolerancia`: `lastSeen` quedó contaminado por un reloj adelantado → **restablece `lastSeen = T`** (también en el registro externo) y continúa.
  4. Si no hay `T` (sin red): mantén `ClockTampered`, con mensaje que indique «conéctate a internet para verificar la hora».
- La hora confiable **nunca** puede extender una licencia más allá de lo firmado: solo corrige la referencia de «ahora».
- Pruebas: reloj +30 días (lastSeen contaminado) con `T` real → se recupera; reloj −30 días con `T` real → evalúa con `T`; sin red → `ClockTampered`; `T` inválida/ausente → no cambia nada.

#### 2.3 UI y mensajes

- Pantalla de Licencia: estado «Reloj desajustado» con botón «Verificar hora» y texto claro; elimina estados sin salida.
- `MensajesError`: mapea `CLOCK_SKEW` del servicio a «Ajusta la fecha y hora de tu dispositivo (diferencia de N min)» usando `serverTime`.

**Cierre Fase 2:** pruebas verdes (JVM/Python/Swift según el entorno); registro en notas.

---

### FASE 3 — Apps secundarias con permisos anclados (S2)

> **Puerta de decisión.** Lee `Android/app/.../vinculacion/**`, `Android/data/.../licencia/**` (`LicenciaRepositoryImpl`, `FirmanteLicenciaAndroid`, `VerificadorPermisoSecundaria`) y `LicenciaPrincipal`. Si la realidad del handshake difiere de lo descrito, **para, documenta y propón alternativa** en las notas antes de codificar.

Hoy el permiso se firma con la clave de `FirmanteLicenciaAndroid` (Keystore `PURPOSE_SIGN`), cuya pública viaja **sin anclaje**. Hay dos orígenes de licencia primaria y cada uno se ancla distinto:

#### 3.1 Primaria con licencia de **canje** (anclaje directo, preferido)

La licencia de canje (firmada por el servicio) contiene `devicePublicKey` y `secundarias`. Reutiliza el diseño ya implementado en el cliente de referencia JS de GL (`createSubToken`/`exportSubLicense`/`activateSubLicense`, sub-token `GL-REDEEM-SUB1|licenseId|thumbprintSecundaria|slot|allowed|venceEn`):
- La **identidad de canje** de la primaria es una clave P-256 persistente con `PURPOSE_SIGN` (puede ser la de `FirmanteLicenciaAndroid`; la **misma** clave debe usarse en `deviceKey` del canje y en el firmado de sub-tokens).
- La secundaria verifica: firma del servicio sobre la licencia primaria (clave `canje` del directorio) → `SHA-256(devicePublicKey)==deviceThumbprint` → firma del sub-token con `devicePublicKey` → su propio thumbprint → `allowed`/`venceEn` idénticos a la licencia → `slot ∈ 1..allowed`.
- El paquete offline es el JSON de `services/redeem/README.md` («Sub-tokens»).

#### 3.2 Primaria con licencia **SPVI2** (legado) — anclaje por divulgación + MAC designado

`SPVI2` va cifrada hacia la clave ECDH de la primaria; la secundaria no puede abrirla. Diseño propuesto (**confírmalo en la puerta de decisión**):
1. La primaria calcula `okm` (44 B: clave AES + IV derivados con HKDF de la licencia, como en `LicenciaCorta.descifrar`) y entrega a la secundaria: el texto `SPVI2:…`, `okm` (Base64url), su `deviceId` y su `devicePub` (comprimida, 33 B).
2. La secundaria: verifica la firma GL de `SPVI2` con el directorio (`emitidaEn` desconocido hasta descifrar: prueba claves vigentes+retiradas), descifra con `okm` (AES-GCM, AAD `SPVI-L2|`‖`epk`), lee el cuerpo (tipo, estado, secundarias, fechas, huella) y exige `huella == SHA-256("SPVI-L2|"+deviceId+"|"‖devicePub)[0..16]`. Así **GL ha firmado** que esa primaria tiene esa cuota y vencimiento.
3. El permiso de ranura es un **MAC designado**: `k = HKDF-SHA256(ECDH(privSecundaria, devicePubPrimaria), salt="SPVI-SUB2", info=licenseId‖deviceIdSecundaria, 32)`; `mac = HMAC-SHA256(k, "SPVI-SUB2|"+licenseId+"|"+deviceIdSecundaria+"|"+slot+"|"+allowed+"|"+venceEn)`. La primaria calcula la misma `k` con `DeviceKey.agree(devicePubSecundaria)`. Esto prueba posesión de la clave de dispositivo de la primaria **sin** necesitar que esa clave pueda firmar. Requiere que la primaria conozca `devicePub` de la secundaria: si el handshake de vinculación no la transporta, añádela (campo opcional, compatible hacia atrás).
4. Sin permiso válido → **bloquear** (ya no se acepta «sin sub-token» para primarias ≥ la versión nueva). Para primarias antiguas que no envían permiso, conserva la compatibilidad solo durante una ventana configurable (`PERMISO_OBLIGATORIO_DESDE_VERSION`), documentada y con aviso en la UI de la secundaria.
- Si el análisis muestra que 3.2 no es viable con el handshake actual, la alternativa mínima aceptable es: **no sumar secundarias** con licencias `SPVI2` hasta que el propietario migre a canje, y documentarlo. No dejes el comportamiento actual sin dejar constancia.

#### 3.3 Pruebas obligatorias

Sub-token propio con clave del atacante → bloqueado; permiso de otra ranura/dispositivo/cuota → bloqueado; vencimiento distinto al de la licencia → bloqueado; `okm` incorrecto → GCM falla → bloqueado; huella que no corresponde a la primaria declarada → bloqueado; primaria antigua sin permiso → comportamiento de la ventana de compatibilidad.

**Cierre Fase 3:** los 3 ataques «firmo yo mi permiso», «quito el permiso», «cambio de cuota» fallan en pruebas.

---

### FASE 4 — Cliente de canje en Android e iOS (S7)

Portar `services/redeem/src/client/redeem-client.js` (repo GL) respetando el contrato de `docs/REDEEM_SERVICE.md` y el **Anexo B**:

- **Identidad:** clave P-256 persistente con `PURPOSE_SIGN` (Android Keystore / iOS Secure Enclave o Keychain). `deviceKey` = SPKI Base64 estándar; `thumbprint = Base64url(SHA-256(SPKI))` sin relleno.
- **Petición** `POST {REDEEM_BASE_URL}/v1/redeem` con `v,appId,code,deviceKey,requestId,requestedAt,nonce,proof`. `proof` = ECDSA DER Base64 sobre `canonical(["GL-REDEEM-REQUEST1","1",appId,códigoNormalizado,deviceKey,requestId,requestedAt,nonce])` (cada campo UTF-8 con prefijo `uint32` big-endian). Código normalizado: sin espacios ni guiones, mayúsculas, 16 caracteres del alfabeto de 34 símbolos (sin `I` ni `O`). `requestedAt` ISO UTC con milisegundos y `Z`.
- **Reintentos idempotentes:** conserva el `requestId` mientras no sepas si el servidor procesó; pérdida de respuesta ⇒ mismo `requestId` (o uno nuevo con la misma clave: devuelve la misma licencia).
- **Verificación previa a guardar:** firma del servicio con la clave `canje` del directorio; `alg`, `kid`, `appId`, `deviceThumbprint == tu thumbprint`, `SHA-256(devicePublicKey) == deviceThumbprint`, no vencida. Re-verifica en **cada arranque**. No guardes el código ni datos personales.
- **Estado:** `POST /v1/status` (`GL-REDEEM-STATUS-REQUEST1`) al abrir la pantalla de licencia con red; `REVOCADA` bloquea; un fallo de red o respuesta inválida **no** borra la licencia vigente.
- **Errores:** mapea `CODE_UNAVAILABLE`, `CODE_ALREADY_REDEEMED`, `CODE_EXPIRED`, `RATE_LIMITED`, `CLOCK_SKEW`, `INVALID_PROOF`, red caída, a mensajes genéricos en español (sin revelar si el código existe).
- **Convivencia:** nuevo origen `CANJE` en `LicenseState`/almacén junto a `SPVI2`. Estado efectivo = el de mayor vencimiento; perpetua gana. Un bloqueo por `REVOCADA` de canje no afecta a una licencia `SPVI2` válida y viceversa. No cambies el flujo de `SPVI2`.
- **UI:** en la pantalla de Licencia, opción «Tengo un código» (campo con máscara `XXXX-XXXX-XXXX-XXXX`, pegado desde portapapeles, `FLAG_SECURE`/ocultar en capturas ya existente) y botón «Renovar con código». El mismo flujo sirve para activar y renovar (rollover lo hace el servidor).
- **Pruebas:** usa `Web-Desktop/tests/fixtures/` (copia del fixture `dshalev-protocol-v1.json` de GL) como vector cruzado de `canonical` y transcript de prueba; si no existe, solicita al propietario el archivo `services/redeem/test/fixtures/dshalev-protocol-v1.json` de GL y mientras tanto marca «sin vector cruzado». Casos: éxito; red perdida + reintento; respuesta manipulada; thumbprint ajeno; reloj `CLOCK_SKEW`; revocada; sin red; licencia previa intacta ante error.
- iOS: misma lógica en Swift (CryptoKit `P256.Signing`, firma DER: usa `.derRepresentation`). Pruebas XCTest en `iOS/Tests/IShalevDomainTests`.

**Cierre Fase 4:** flujo completo probado con un servicio simulado (fetch inyectable). La prueba contra el Worker real solo tras el despliegue (ver `GUIA_CLOUDFLARE_WORKERS_D1.md`).

---

### FASE 5 — DShalev (Windows): activar el canje (S6)

- `config_licencia.py`: `APP_ID = "DSHALEV"`, `ENDPOINT_REDEEM = REDEEM_BASE_URL + "/v1/redeem"`, `ENDPOINT_ESTADO = REDEEM_BASE_URL + "/v1/status"` — **solo** cuando el propietario aporte la URL; el validador ya exige HTTPS, sin credenciales ni parámetros. `KIDS_FIRMA` se alimenta del directorio (Fase 1), no a mano.
- `verificador.py`/`cliente_gl.py`: manejar `CLOCK_SKEW` + `serverTime`, `GET /v1/time` para la salida de `ClockTampered` (S4) con el ancla actual (`almacen.AnclaInstalacion`, tolerancia 2 h), y la regla de dominancia de 2.1.
- Panel `/licencia` (`templates/license.html` + ruta en `app.py`): estado, campo de código, «Renovar», mensaje de reloj, y el **thumbprint** de la instalación visible/copiable (lo necesita el operador para una transferencia).
- `MAX_SECUNDARIAS=0` del servicio ⇒ el panel **no** debe ofrecer secundarias comerciales; la prueba local de 7 días de `sync_server.py` sigue marcada como no comercial.
- Política documentada y probada: restauración de respaldo en otro equipo no activa licencia (identidad DPAPI distinta); respaldo antiguo en el mismo equipo no destruye la licencia vigente (ya cubierto por el ancla; añade pruebas si faltan).
- Pruebas: extiende `test_licencia_cliente.py`, `test_licencia_almacen.py`, `test_licencia_contrato.py`; añade `test_licencia_directorio.py`. La **matriz** de `docs/DSHALEV_GL_SERVICIO_PROPUESTA.md` (primera activación, otro equipo, respuesta manipulada, sin red, red perdida tras canje, reinstalación, reloj, vencida, revocación online, SPVI2 intacto) debe estar cubierta con una prueba por fila.

**Cierre Fase 5:** `python -m pytest Web-Desktop/tests -q` verde (ejecutado).

---

### FASE 6 — Documentación y entrega

- `README.md`, `SECURITY.md`, `LICENSE_CLIENT.md` (§ claves fijadas → directorio), `docs/GL_ESPECIFICACION.md`: documentan directorio, canje, reloj, sub-tokens anclados y la política de compatibilidad.
- `docs/HISTORIAL_DESARROLLO.md`: entrada con fecha, cambios y qué no se verificó.
- PR único con: tareas con estado (`hecho-verificado` / `hecho-no-ejecutado` / `bloqueado: propietario` / `omitido: motivo`), resultados de pruebas ejecutadas y sección **«Qué NO se verificó»** (Kotlin/Swift sin toolchain, servicio real, directorio firmado con raíz real).

---

## 4. Definición de terminado

- [ ] Directorio en las 3 plataformas, con vector, `seq` anti-retroceso y arranque verificando de nuevo.
- [ ] Licencias legado (vector dorado) siguen verificando.
- [ ] Reglas de dominancia y salida de `ClockTampered` con pruebas.
- [ ] Los 3 ataques de la Fase 3 fallan en pruebas o hay decisión documentada del propietario.
- [ ] Canje funcionando contra servicio simulado en Android/iOS y DShalev; DShalev con pruebas Python ejecutadas.
- [ ] Literal `GLMK-PERM-MAXS-9999` fuera de `src/main`.
- [ ] Sin secretos ni códigos en logs/repo.

---

## Anexo A — Directorio de confianza v1 (idéntico en ambos repos)

Archivo `directorio.json`, publicado como *asset* de la Release `revocaciones` del repositorio de Shalev:

```json
{ "datos": "<JSON como texto>", "firma": "<Base64url r‖s>", "raiz": "A" }
```

- `firma`: ECDSA P-256/SHA-256, formato crudo `r‖s` (64 B), de una **clave raíz** sobre `UTF-8("SPVI-DIR1|") ‖ UTF-8(datos)`. Se firma el texto exacto de `datos` (sin canonicalización JSON). `raiz` (`"A"`/`"B"`) es informativo; el cliente puede probar ambas.
- `datos`:

```json
{"v":1,"seq":7,"emitidaEn":1790000000,
 "claves":[{"kid":"gl-sign-v1","uso":"firma","spki":"<Base64 estándar SPKI P-256>",
            "huella":"sha256:xxxx:…","desde":1790000000,"hasta":null,"estado":"vigente"}]}
```

Validación del cliente: tamaño ≤ 64 KiB; `v == 1`; `seq` **estrictamente mayor** que el guardado (anti-retroceso; mismo `seq` con contenido idéntico es idempotente); cada `spki` es P-256 y `huella == SHA-256(DER)` en hex agrupado; a lo sumo una clave `ecdh` con `estado: "vigente"`; campos desconocidos se ignoran.

Semántica:
- `uso:"firma"`: valida licencias y listas con `emitidaEn` en `[desde, hasta]` (`hasta:null` = sin límite). `estado:"comprometida"` ⇒ se rechaza todo lo firmado por esa clave.
- `uso:"ecdh"`: la `vigente` cifra solicitudes nuevas; las `retirada` no se usan para cifrar.
- `uso:"canje"`: clave pública del servicio (`kid` p. ej. `gl-redeem-v1`).
- **Raíces:** dos claves públicas (A y B) fijadas en el build. **Bootstrap:** el build embebe un `directorio-bootstrap.json` firmado; en cada arranque se re-verifica y se usa el de mayor `seq` entre bootstrap y caché descargada.

Límites: no protege contra el robo de una raíz con su passphrase; una clave retirada sigue válida para licencias con `emitidaEn` dentro de su ventana (un atacante con esa clave podría fechar una licencia dentro de ella: se mitiga marcándola `comprometida`).

## Anexo B — Cambios del protocolo de canje (v1 compatible)

- Nuevo error `CLOCK_SKEW` (HTTP 400) para `requestedAt` fuera de la ventana; el JSON de error incluye `serverTime` (ISO ms `Z`) solo en este error.
- Nuevo `GET /v1/time` → `{"v":1,"ok":true,"now":"<ISO ms Z>"}`; sin autenticación; `cache-control: no-store`; limitado por IP.
- `/v1/redeem` y `/v1/status` sin cambios de forma. Tras revocación o transferencia, `/v1/status` devuelve `REVOCADA` también para las licencias absorbidas por rollover.
- La licencia (`GL-REDEEM-LICENSE1`), el sub-token (`GL-REDEEM-SUB1`) y los transcripts **no cambian**.
