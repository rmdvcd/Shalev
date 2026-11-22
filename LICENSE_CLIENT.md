# Shalev — Cliente de licencias (contrato GL v1 aplicado)

Cómo cumple la app Android Shalev el contrato v1 del emisor offline **GL** (`CONTEXTO_LICENCIAS.md`). Describe la activación compacta `SPVI2:`, la verificación de licencias heredadas envelope v1 ya instaladas y las mitigaciones actuales. Las decisiones históricas retiradas se resumen aquí junto al comportamiento vigente.

> **Este documento trata de la licencia de uso de la app**, no de la licencia del código fuente.

**Regla rectora:** Shalev se adapta al contrato v1 **sin cambiarlo** (`v = 1`, mismos algoritmos, AAD, info de HKDF y campos) y no reutiliza nada del almacenamiento de GL.

## 1. Piezas

| Pieza | Módulo / archivo | Función |
|---|---|---|
| Constantes del contrato | `:licencia` `contract/GlContract.kt` | `VERSION = 1`, `ALG = "ECIES-P256-AES256GCM-v1"`, `KID = "gl-sign-v1"`, `gl-req-v1`, `gl-lic-v1`, IV 12, tag 16, AES 32; `APP_NAME = "Shalev"`, `DEVICE_ID_PREFIX = "SPVI:"` |
| Modelos | `contract/GlModels.kt` | `Envelope` (8 campos), `RequestPayload`, `LicensePayload`, `TipoLicencia` (con precio), `EstadoLicencia`, `Via` |
| Criptografía | `crypto/Crypto.kt`, `crypto/GlCodec.kt`, `contract/SolicitudCifrada.kt`, `contract/LicenciaCorta.kt` | P-256 (punto comprimido), ECDH, HKDF-SHA256, AES-256-GCM, ECDSA; solicitud `SPVIR1`, licencia `Shalev2`, `GlLicenseOpener` (solo para la larga ya instalada) |
| Claves de GL | `LicenseTrust.kt` | Claves públicas (firma y ECDH) fijadas en el build con su huella |
| Reglas | `LicenseValidator.kt`, `LicenseState.kt` | Validación semántica, estados y texto del banner |
| Orquestación | `LicenseManager.kt` | Evaluar, construir la solicitud y activar |
| Clave del dispositivo | `:data` `security/AndroidDeviceKey.kt` | Par P-256 persistente |
| Persistencia | `:data` `licencia/DataStoreLicenseStore.kt`, `DeviceIdProvider.kt` | DataStore cifrado propio de Shalev |
| UI | `:app` `licencia/`, `bloqueo/BloqueoGate.kt`, `root/RootViewModel.kt` | Panel, bloqueo y re-evaluación |
| Clave maestra y sub-tokens | `licencia/.../ModoDesarrollo.kt`, `contract/CodigoLicencia.kt`, `contract/EnvelopeV1.kt`, `secondary/SubToken.kt` | Modo depuración de la clave maestra, código de 16 caracteres, envelope v1 (AAD canónica y heredada) y permiso firmado de las secundarias |
| Sub-tokens en la app | `:data` `licencia/FirmanteLicenciaAndroid.kt`, `licencia/VerificadorPermisoSecundaria.kt`, `sync/{Protocolo,ClienteSync,ServidorSync}.kt` | Clave de firma de la principal, anotación del `deviceId` de cada secundaria y comprobación del permiso recibido |

## 2. Solicitud (Shalev → GL) — 0.23.0

### Payload (`RequestPayload`, `v = 2`)

| Campo | Valor en Shalev |
|---|---|
| `v` | `2` (`GlContract.SOLICITUD_VERSION`) |
| `nombre`, `apellidos` | Del Perfil (dos campos separados, misma regla que GL: letras Unicode, 2–80) |
| `ci` | Del Perfil, en mayúsculas (CI cubano de 11 dígitos, dentro de 5–20 alfanumérico) |
| `via` | `WHATSAPP` o `SMS`, según el botón pulsado |
| `telefono` | Un teléfono del Perfil elegido por el usuario, normalizado a E.164 (`5XXXXXXX` → `+535XXXXXXX`). No se lee la SIM |
| `deviceId` | `"SPVI:" + ANDROID_ID`, solo `a-z0-9` ASCII, 128 caracteres como máximo. El prefijo identifica la app (ya no va `appName`) |
| `tipo` | `MENSUAL` · `SEMESTRAL` · `ANUAL` · `PERPETUA` |
| `solicitadaEn` | Instante actual en ISO-8601, truncado a segundos |
| `nonce` | 16 bytes de `SecureRandom` en Base64url sin relleno |
| `devicePub` | **Punto comprimido** P-256 (33 B) de la clave del dispositivo, Base64url sin relleno. Nunca `null` |
| `secundarias` | Apps secundarias, 0–10. Precio = base del tipo + `secundarias` × importe del tipo (1 000 / 5 000 / 9 000 / 17 000 CUP) |
| `renueva` | Solo al renovar: `id` de la licencia instalada, auténtica y con vencimiento (`LicenseManager.licenciaRenovable`). Ausente en una primera licencia |
| `tx` | N.º de transferencia bancaria opcional (`^[A-Za-z0-9-]{4,30}$`). Modelado en el payload v=2; por seguridad y compatibilidad, `ENVIAR_TX_EN_SOLICITUD = false` (se omite del JSON cifrado SPVIR1 y se incluye en el texto visible del mensaje como `Transferencia: <tx>`) |

Si el Perfil está vacío, el panel pide los datos y los guarda en el Perfil al solicitar.

**Frescura y ventana de 7 días (0.24.0 / Encargo Complementario):** la emisión exige `solicitadaEn` con
≤ 7 días de antigüedad (ampliada desde la ventana original de 72 h) y ≤ 2 h de adelanto contra el reloj
del emisor, nonce de 16–128 caracteres, `devicePub` de exactamente 33 B (44 en Base64url), `renueva` con
formato `[A-Za-z0-9._:-]{1,64}` y solicitud ≤ 12 288 B. Las tres plataformas comparten la constante
exacta `AVISO_FRESCURA` (estricta en alfabeto básico GSM-7 de 7 bits):
`"Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."`
utilizada en solicitudes nuevas, ampliaciones de secundarias y recuperaciones de licencia.

**Instrucciones de pago en la app:** cada plataforma mantiene una fuente única de métodos de cobro
(`PaymentInstructions` en Android e iOS, `pago.py` en DShalev) con advertencia visible de no exponer
datos bancarios en repositorios públicos. Antes del botón de envío se renderiza la tarjeta «Cómo pagar»
con el desglose canónico de precio según tipo y secundarias (o diferencia de secundarias al ampliar;
oculta en recuperación gratuita) y el aviso obligatorio:
`"Paga solo a los datos que muestra esta pantalla"`.

### Cifrado (`SolicitudCifrada`) y mensaje (`MensajesLicencia.solicitud`)

1. Par efímero P-256; `epk` = su pública comprimida (33 B).
2. ECDH(efímera, ECDH pública de GL) → HKDF-SHA256 (salt = `epk`, info `SPVI-R1`, 44 B) → clave AES-256 ‖ IV.
3. AES-256-GCM con AAD `"SPVI-R1|"` ‖ `epk`. Código = `SPVIR1:` + Base64url(`epk` ‖ ct ‖ tag).
4. Mensaje = datos legibles (nombre, carné, teléfono, tipo, secundarias, precio, `Transferencia: <tx>` si aplica, «Renueva» o «Recupera», y aviso de frescura) + renglón en blanco + código. `LicenseManager.buildRequest` devuelve `SolicitudGenerada(texto, codigo)`.
5. **Solo texto** (0.23.1): WhatsApp (`https://wa.me/5351815604?text=…`) o SMS (`smsto:+5351815604`, `sms_body`). El usuario solo pulsa Enviar. Sin permisos.

## 3. Activación (GL → Shalev) — licencia corta cifrada `SPVI2:`

El usuario pega el mensaje de GL (o solo el código) en *Activar licencia* (desde la 0.23.1 no hay QR). Especificación completa en `LicenciaCorta.kt` y `docs/GL_PROMPT_0.23.md`; resumen en `docs/LICENCIA_0.23.md`.

1. **Extracción:** `SPVI2:` + 210 caracteres Base64url, ignorando espacios y saltos de línea dentro del código. Las líneas de arriba no se leen. Sin código → `NotFound`, también si es una licencia larga.
2. **Firma** ECDSA `gl-sign-v1` (claves fijadas) sobre `"SPVI-L2|"` ‖ epk ‖ ct ‖ tag (r‖s).
3. **Descifrado:** ECDH(clave del dispositivo en el Keystore, `epk`) → HKDF (`SPVI-L2`) → AES-256-GCM.
4. **Huella** = SHA-256(`"SPVI-L2|" + deviceId + "|"` ‖ devicePub comprimida)[0..16], igual a la de este teléfono.
5. **Validación** (`LicenseValidator`): `PERPETUA` sin `venceEn`; el resto con `venceEn` posterior a `emitidaEn`; `secundarias` 0–10.
6. **Antigüedad:** una licencia emitida antes que la instalada, o anterior a la marca de transferencia (`lic.migrated_at`), → `Outdated`.
7. **Guardado:** el código + `id`, `emitidaEn`, `tipo`, `venceEn`, `deviceId`. En cada arranque se repiten los pasos 2–5.

| Resultado | Mensaje al usuario |
|---|---|
| `Accepted` | «Licencia activada» |
| `NotFound` | «No se encontró una licencia en el texto pegado» |
| `Rejected` | «La licencia no es válida para este dispositivo» |
| `Outdated` | «Ya tienes instalada una licencia más reciente» |

## 4. Canje por código comercial (`GL-REDEEM-REQUEST1` / `GL-REDEEM-LICENSE1`)

Implementado como vía paralela y directa en Android, iOS y DShalev para activar códigos alfanuméricos de 16 caracteres (`XXXX-XXXX-XXXX-XXXX`, alfabeto Crockford sin `I` ni `O`).

1. **Identificadores de aplicación autorizados:**
   - Android: `CANJE_APP_ID = "SHALEV-ANDROID"`, `CANJE_HABILITADO = true`.
   - iOS: `CANJE_APP_ID = "SHALEV-IOS"`, `CANJE_HABILITADO = true`.
   - DShalev: `CANJE_APP_ID = "DSHALEV"`, `CANJE_HABILITADO = true`.
   - Flavors derivados / no oficiales: `CANJE_HABILITADO = false` y `CANJE_APP_ID = ""`.
2. **UI condicional:** la tarjeta de canje por código solo se activa si el directorio de confianza contiene al menos un `kid` con `uso == "canje"` y `CANJE_HABILITADO` es verdadero. Si está deshabilitado, la interfaz muestra de forma legible: `«Aún no disponible en esta edición»`.
3. **Verificación estricta:**
   - La firma de la licencia sólo se valida contra claves de `uso == "canje"` resueltas por `kid` exacto en el directorio de confianza. Se rechazan claves de firma ordinaria de licencias manuales.
   - El tipo de licencia es obligatorio y `expiresAt` es obligatorio para tipos no perpetuos. No se inventan estados ni tipos predeterminados.
   - **Límite de secundarias:** los códigos de canje nunca llevan secundarias (`MAX_SECUNDARIAS = 0`). Si una respuesta de canje declara `secundarias > 0`, se rechaza de inmediato como inválida.
4. **Persistencia:** el sobre de canje verificado se almacena en el Keystore/Keychain de la plataforma (ancla de instalación y registro local protegido), manteniendo plena prevalencia (mayor vencimiento; perpetua gana).
5. **Reintento por desvío de reloj (`CLOCK_SKEW`):** si el reloj local difiere más de 5 minutos del servidor, el cliente recupera la hora autorizada mediante la cabecera `Date` o el endpoint `/v1/time`, reajusta la marca temporal y reintenta exactamente una vez con nuevo `nonce` y prueba firmada, preservando el `requestId` original.
6. **Protección contra oráculos de enumeración:** cualquier código de otra aplicación, inexistente o ya utilizado responde al usuario con el mensaje genérico:
   `«Código no válido para esta aplicación o ya utilizado»`.

Nunca se indica qué comprobación falló.

- **Licencia larga instalada** (envelope v1, antes de la 0.23.0): se sigue re-verificando (firma, descifrado con el AAD de su `licenseId`, `LicenseValidator`) hasta que venza. Ya no se puede activar una nueva.

## 4. Estados, banner y bloqueo

| Estado | ¿Desbloquea? | Origen |
|---|---|---|
| `Trial(díasRestantes)` | Sí | Sin licencia, dentro de los **7 días** desde el primer arranque |
| `TrialExpired` | No | Prueba agotada, o teléfono cuya licencia se recuperó en otro teléfono |
| `Active(tipo, venceEn)` | Sí | `estado = ACTIVA` y `venceEn` en el futuro |
| `Perpetual` | Sí | `PERPETUA` |
| `Expired(tipo)` | No | `VENCIDA`, o `venceEn` ya pasado |
| `Revoked` | No | `estado = REVOCADA` en un envelope firmado (se muestra como «Licencia no válida») |
| `ClockTampered` | No | El reloj retrocedió más de **2 h** respecto a la última hora vista |

- **Vencimiento:** lo fija GL desde la emisión (+30 / +180 / +365 días).
- **Licencia larga (envelope v1):** Android **no** la activa desde la 0.23.0; solo vuelve a verificar criptográficamente una licencia heredada que ya estuviera instalada en un teléfono.
- **Apps secundarias:** la secundaria recibe el estado de la licencia de la principal **con el permiso firmado de su ranura** (§11). Si el permiso no la autoriza (firma, `deviceId`, ranura o vencimiento), su licencia queda `Revoked` y no puede vender; si la principal es anterior y no manda permiso, la licencia se acepta como antes.
- **Evaluación** (`LicenseManager.evaluate`), en este orden: reloj → licencia guardada, **re-verificada criptográficamente** → periodo de prueba. No existe un indicador «licenciado».
- **Re-evaluación:** al arrancar, al volver a primer plano y mientras la app está abierta (`VigilarLicencia`: justo al vencer o cada ≤ 15 min).
- **Banner de Inicio:** «Periodo de prueba restante: N días»; Mensual en días; Semestral y Anual en meses (en el último mes, en días); Perpetua sin banner.
- **Bloqueo:** si el estado no desbloquea, `RootViewModel` muestra el panel de Licencia **directamente**, también si vence con la app abierta. Desde ahí se llega a *Soporte* y, con la **prueba vencida**, a **«Seguir como app secundaria»** (`Vinculacion.puedeSeguirComoSecundaria`): el teléfono se vincula a una principal con licencia activa (respaldando antes sus datos si no hay respaldo), pasa a `SECUNDARIA` y trabaja con la licencia de la principal. Una licencia vencida, transferida o un reloj manipulado no ofrecen esa salida. Atrás sale de la app.
- **Precios mostrados:** Mensual 6,000 · Semestral 30,000 · Anual 50,000 · Perpetua 90,000 CUP. Perpetua no ofrece renovación; cualquier otra licencia instalada ofrece «Renovar o cambiar».

## 5. Claves de GL y Directorio de Confianza v1

A partir de la versión 1.1, las tres plataformas (Android, iOS y DShalev) sustituyen el anclaje manual estático por el **Directorio de Confianza v1** (`directorio.json`, Anexo A). Las claves públicas de firma y ECDH se resuelven dinámicamente a través del `TrustDirectory` efectivo:

| Clave | Política |
|---|---|
| Firma (`uso = "firma"`) | Resuelta dinámicamente desde el `TrustDirectory` activo para la fecha de emisión de la licencia, siempre que su estado sea `vigente` (no `comprometida`). Como respaldo de compatibilidad, `LicenseTrust.SIGN_KEYS` conserva las rotaciones anteriores históricas para verificar licencias offline antiguas emitidas previamente. |
| ECDH (`uso = "ecdh"`) | Resuelta desde la clave en estado `vigente` en el `TrustDirectory` activo. Solo puede haber como máximo una clave ECDH vigente a la vez. |
| Canje (`uso = "canje"`) | Resuelta dinámicamente para autenticar respuestas del servicio GL de canje directo v1. |
| Raíces del Directorio | **Fijadas en el build** (Raíz A y Raíz B). Autentican el sobre de directorio con firma cruda ECDSA P-256 (`r‖s`). |

**Reglas del Directorio (Anexo A):**
- **Anti-retroceso e idempotencia:** el número de secuencia `seq` es estrictamente monótono. Un directorio con `seq` menor se rechaza. Mismo `seq` exige contenido idéntico.
- **Tamaño máximo:** 64 KiB (65 536 bytes). Cargas mayores se rechazan antes de procesar.
- **Canal seguro:** descarga periódica únicamente por HTTPS desde `github.com/rmdvcd/Shalev`.
- **Persistencia atómica:** el archivo de caché local se escribe mediante archivo temporal con `fsync` y reemplazo atómico, verificándose en cada arranque.
- **Compatibilidad histórica:** `LicenseTrust` en Android e iOS delega sus consultas al directorio efectivo; si no está inicializado o la licencia es de un emisor previo conocido, consulta la lista histórica de firmas fijadas. `GoldenVectorTest` sigue verificando byte a byte.

## 5 bis. Canje GL v1, Reloj y Sub-tokens

1. **Cliente de canje directo (Anexo B / S7):** además de la activación offline con código `SPVI2:`, Android e iOS incorporan el cliente de canje directo contra el servicio central GL v1 mediante transcripciones canónicas big-endian (`REDEEM-REQ-v1`, `REDEEM-RESP-v1`).
2. **Recuperación de reloj (`CLOCK_SKEW` / S4):** ante un desfase horario entre el dispositivo y el servidor, el cliente extrae `serverTime` de la respuesta, consulta `/v1/time` y permite resetear la marca de agua del reloj de instalación, liberando el bloqueo `ClockTampered` cuando la hora se normaliza.
3. **Renovación dominada (`Absorbed` / S3):** si se canjea una licencia que no extiende la cobertura de la licencia actualmente instalada (por ejemplo, canjear una licencia mensual sobre una anual o perpetua), el sistema no retrocede el vencimiento, conserva la de mayor duración y reporta el resultado `Absorbed`.
4. **Permisos anclados en secundarias (S2):** las apps secundarias comprueban que el sub-token de la primaria esté firmado por una clave de dispositivo válida, verificando que la clave principal no esté marcada como `comprometida` en el directorio ni la licencia revocada.

## 6. Almacenamiento

DataStore cifrado propio (`SecureDataStore`: cada valor con AES-256-GCM del Keystore y AAD = nombre de la clave). Excluido de toda copia del sistema.

| Clave | Contenido |
|---|---|
| `lic.envelope` | Desde 0.23.0, la licencia `SPVI2:…` (reconocida por el prefijo); antes, el envelope completo de la larga + `id`, `emitidaEn`, `tipo`, `venceEn`, `deviceId` (los metadatos se contrastan con lo verificado en cada evaluación) |
| `lic.trial_start` | Inicio del periodo de prueba |
| `lic.last_seen` | Última hora vista (antirretroceso del reloj) |
| `lic.migrated_at` | Instante en que la licencia de este teléfono se transfirió a otro (recuperación) |

Un valor ilegible (Keystore borrado) cuenta como ausente: el teléfono queda en prueba o bloqueado, nunca desbloqueado. **La licencia no entra en los respaldos.**

## 7. Cambio de teléfono (antes «Migrar a otro teléfono», O5; retirado)

El asistente de 4 pasos con paso de datos por QR entre los dos teléfonos se retiró: para cambiar de teléfono,
exporta el respaldo `.shalev` en el viejo, impórtalo en el nuevo y pide la **recuperación** de la licencia con el
«ID de la licencia anterior» (el respaldo v4 ya lo trae escrito). GL revoca la anterior y la publica en
`revocadas.json`; el teléfono viejo la aplica, borra sus datos y se bloquea en «Licencia transferida».

La marca `lic.migrated_at` se conserva: la pone la revocación y sigue rechazando (`Outdated`) cualquier licencia
emitida antes de la transferencia, incluido el mensaje viejo que siga en WhatsApp.

## 8. Cambios históricos del contrato

La tabla registra decisiones de versiones anteriores; **no** describe el payload vigente. El contrato actual de solicitud y activación se resume en §§2–3: `RequestPayload v = 2`, sin `appName`, solicitud `SPVIR1:` y licencia corta `SPVI2:`. Las licencias largas envelope v1 que ya estaban instaladas siguen re-verificándose.

| Decisión | ¿Cambia el contrato? | ¿Hay que tocar GL? |
|---|---|---|
| Payload v1 completo + `appName`, `deviceId` con prefijo `SPVI:` (D2) | No | No |
| `devicePub` siempre presente (D2.5) | No | No |
| Claves fijadas; ECDH configurable solo si falta (D3, D3.1) | No | Recomendado: pantalla «Exportar claves públicas» en GL |
| Destino +5351815604 (D4) | No | No |
| Envelope estricto (D2.6) | No (alineación) | No |
| Migrar (O5) | No | No |
| Campo opcional `secundarias` en solicitud y licencia (0.21.0, C4) | No (campo extra al final; el payload de la licencia se lee con `ignoreUnknownKeys`) | **Sí:** GL copia `secundarias` de la solicitud a la licencia y cobra según la tarifa vigente |
| Licencia corta + QR, `licenciaCorta` (0.22.0, L-b/L-c) | No (forma adicional; la larga sigue igual) | **Sí:** sustituida en la 0.23 por `SPVI2:` |
| Renovación sin perder días, `renueva` (0.22.0, L-e) | No (campo extra al final) | **Sí:** GL calcula `venceEn = max(ahora, venceEn anterior) + duración` con SUS registros |
| `licenseId` fuera del envelope (N1) | No | Resuelto: GL lo pone en la línea «ID:» (solo afecta a la larga ya instalada) |
| **Solicitud `SPVIR1` + licencia corta cifrada `SPVI2` únicas, mensaje «datos + renglón + código», solo texto (0.23.0; QR quitado en la 0.23.1)** | **Sí:** solicitud `v = 2` y la larga deja de activarse; las claves de GL no cambian | **Sí:** ver `docs/GL_ESPECIFICACION.md` |
| **Ampliación de apps secundarias (`amplia`) sobre una licencia instalada (1.0.2)** | No (campo opcional `amplia` mutuamente excluyente con `renueva` y `recupera`; `secundarias` lleva el nuevo total `1..10`) | **Sí:** GL conserva el `venceEn` (o `PERPETUA`) de la licencia original y cobra tarifa fija por secundaria añadida (`1 000 / 5 000 / 9 000 / 17 000 CUP` según tipo); ver `docs/gl-ampliar-secundarias.patch` |

## 9. Pruebas

| Test | Qué cubre |
|---|---|
| `LicenseTrustTest` | Claves fijadas: P-256, huellas, firma ≠ cifrado |
| `GoldenVectorTest` | `printRequest` genera una solicitud real para GL; `verifyRealLicense`: la larga real de GL ya no se activa, pero instalada sigue valiendo |
| `LicenciaCortaTest` | Licencia `Shalev2`: formato, extracción, punto comprimido, rechazos, clon, renovación, vector de Python |
| `LicenseFlowTest`, `RuntimeVerificationTest`, `BannerTest`, `CasosBordeLicenciaTest` | Flujo completo con un GL simulado (`FakeGl`), formato de la solicitud, estados, reloj, bytes alterados, perpetua, marca de transferencia |
| `MasterKeyTest` | Clave maestra `GLMK-PERM-MAXS-9999`: perpetua con 10 secundarias en depuración; **nunca** en release; una licencia auténtica manda sobre ella |
| `CodigoLicenciaTest` | Código de 16 caracteres `[0-9A-Z]` en 4 grupos: normalización, formato y rechazo de lo que no es del contrato |
| `EnvelopeV1Test`, `CanonicalAadTest` | Envelope v1 (ECIES P-256 + HKDF `gl-req-v1`/`gl-lic-v1` + AES-256-GCM): AAD **canónica** con longitud prefijada al sellar, heredada `1\|alg\|…` solo al reabrir lo ya emitido, firma DER y transcript `epk‖iv‖ct‖tag` |
| `SubTokenTest` | Permiso firmado de las secundarias: firma, dispositivo, ranura 1..permitidas, cuota ≤ 10, `PERPETUA`/vencimiento, separadores prohibidos y alteraciones |
| `LicenciaInstrumentedTest` (`:data`) | Keystore real y DataStore cifrado |
| `LicenciaUiTest`, `FlujoLicenciaTest` (`:app`) | Panel y bloqueo → activar → Inicio |

Prueba de extremo a extremo con la app GL real: generar una solicitud en Shalev, emitir la respuesta en GL, pegarla en Licencia → Activar y comprobar el estado. El documento detallado `PRUEBA_COMPATIBILIDAD_GL.md` se retiró en la 0.18.0.

## 10. Clave maestra de desarrollo — solo en builds de depuración

`GLMK-PERM-MAXS-9999` (mitigación 1 del contrato, Opción A) instala una licencia local **PERPETUA con 10 apps
secundarias** sin pasar por GL. No viaja por WhatsApp: la escribe el desarrollador para trabajar. Vale **solo** en
builds de depuración:

| App | Qué la habilita |
|---|---|
| Android | `ShalevApplication.onCreate` → `ModoDesarrollo.esDebug = BuildConfig.DEBUG`. En release es `false` y una licencia instalada con la maestra se descarta al evaluar |

El valor por defecto es `false` (**fail-closed**): si nadie lo activa —o si un día se olvida hacerlo— la maestra no
funciona. El código se acepta con o sin guiones y no dice nada al usuario si no vale (`CodigoLicencia.esMaestra`;
`MasterKeyTest` comprueba los dos caminos). Una licencia auténtica de GL siempre manda sobre la maestra.

## 11. Sub-tokens de apps secundarias (mitigación 7)

La cuota de apps secundarias deja de ser una auto-declaración y pasa a ser **un permiso firmado por la principal**.

```
<licenseId>|<deviceId de la secundaria>|<ranura>|<permitidas>|<venceEn ISO-8601 | PERPETUA>.<firma DER Base64>
```

- `ranura`: 1..`permitidas`; la principal la calcula por el puesto del empleado entre los activos.
- `venceEn`: el de la licencia de la principal (`PERPETUA` = sin vencimiento).
- Firma: ECDSA P-256/SHA-256 (DER) de la principal sobre el texto de la izquierda.

| App | Emisión | Comprobación |
|---|---|---|
| Android | `FirmanteLicenciaAndroid` (Keystore, `PURPOSE_SIGN`, alias `spvi_licencia_firma_v1`; clave por software envuelta con el Keystore si el equipo no lo admite) vía `LicenseManager.emitirSubToken` | `VerificadorPermisoSecundaria` en cada sincronización; un permiso inválido deja la licencia `BLOQUEADA` |

Camino de los datos: la secundaria manda su `deviceId` al vincularse y en cada saludo (`Vincular.device`, `Hola.device`);
la principal lo guarda (columna `empleado.device`, esquema **v12** de Room, migración `MIGRACION_11_12`) y adjunta el
permiso a la licencia que viaja en la instantánea (`LicenciaDto.subToken` + `firmaPub` + `ranura` + `permitidas`).
Los cuatro campos son **opcionales**: una principal anterior no los manda y una secundaria anterior los ignora
(convivencia de versiones).

Reglas de rechazo (fail-closed, sin decirle al usuario qué falló): firma inválida, dispositivo distinto, ranura fuera de
`1..permitidas`, cuota mayor que 10, vencido o campos del mensaje que contradicen al permiso firmado. Sin permiso, la
secundaria acepta la licencia como antes (la principal es anterior).

## 12. Canje comercial directo y consulta de estado (Worker GL v1)

El protocolo de canje comercial permite activar una licencia introduciendo un código alfanumérico de 16 caracteres normalizado (`XXXX-XXXX-XXXX-XXXX`).

### Endpoints del Worker (`v1`)
- `POST /v1/redeem`: canje de código contra prueba de posesión de clave de dispositivo.
- `POST /v1/status`: consulta de estado (`ACTIVA`, `VENCIDA`, `REVOCADA`, etc.) de licencia previamente canjeada.
- `GET /v1/time`: sincronización de reloj en caso de desfase (`CLOCK_SKEW`).

### Reglas de seguridad y verificación
1. **Clave de canje (`uso: canje`):** la firma devuelta por el Worker sobre el sobre de licencia o estado se verifica únicamente contra claves con `uso == "canje"` y estado activo en el `TrustDirectory` (`gl-redeem-v1`). Nunca se usan claves de firma de licencias offline (`uso == "firma"`).
2. **Reintento ante `CLOCK_SKEW`:** si el Worker devuelve `CLOCK_SKEW`, el cliente extrae `serverTime` (o consulta `/v1/time`), actualiza la marca de agua del reloj local (`lastSeen`) y reintenta exactamente una vez firmando con el nuevo `requestedAt` (ISO con milisegundos en UTC), un nuevo `nonce` y el **mismo** `requestId`.
3. **Persistencia del sobre verificable:** se guarda únicamente el sobre firmado de origen `CANJE`, jamás el código comercial de 16 caracteres.
4. **Independencia de activación offline:** la activación tradicional mediante `SPVI2:` se mantiene intacta e independiente.
5. **Restricción de producción:** el Worker central mantiene `ALLOWED_APPS=DSHALEV` y `MAX_SECUNDARIAS=0`. Las ediciones de Android e iOS incluyen el cliente y cableado completo, pero mantienen el servicio deshabilitado por defecto indicando claramente que no está disponible aún para la edición móvil respectiva.

