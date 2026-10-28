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

Si el Perfil está vacío, el panel pide los datos y los guarda en el Perfil al solicitar.

**Frescura y límites que GL aplica al emitir (PR #3 de GL, 2026-10-14, commit `005288d`; análisis en
`docs/ANALISIS_GL_2026-10-15.md`):** la emisión Shalev2 exige `solicitadaEn` con ≤ 72 h de antigüedad y
≤ 2 h de adelanto contra el reloj del emisor (comparación exacta; antes el flujo corto no lo aplicaba),
nonce de 16–128 caracteres, `devicePub` de exactamente 33 B (44 en Base64url), `renueva` con formato
`[A-Za-z0-9._:-]{1,64}` y solicitud ≤ 12 288 B. Shalev ya generaba todo eso; lo nuevo es el aviso
`Vale 72 horas…` en la parte legible del mensaje Android. `Validators.nombre` ya es un subconjunto
estricto de la regla de GL. El nonce **no** se consume en GL: la misma solicitud puede
reemitirse dentro de la ventana si el operador vuelve a confirmar el pago.

### Cifrado (`SolicitudCifrada`) y mensaje (`MensajesLicencia.solicitud`)

1. Par efímero P-256; `epk` = su pública comprimida (33 B).
2. ECDH(efímera, ECDH pública de GL) → HKDF-SHA256 (salt = `epk`, info `SPVI-R1`, 44 B) → clave AES-256 ‖ IV.
3. AES-256-GCM con AAD `"SPVI-R1|"` ‖ `epk`. Código = `SPVIR1:` + Base64url(`epk` ‖ ct ‖ tag).
4. Mensaje = datos legibles (nombre, carné, teléfono, tipo, secundarias, precio y, si aplica, «Renueva») + renglón en blanco + código. `LicenseManager.buildRequest` devuelve `SolicitudGenerada(texto, codigo)`.
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
- **Bloqueo:** si el estado no desbloquea, `RootViewModel` muestra el panel de Licencia **directamente**, también si vence con la app abierta. Desde ahí solo se llega a *Soporte*. Atrás sale de la app.
- **Precios mostrados:** Mensual 6,000 · Semestral 30,000 · Anual 50,000 · Perpetua 90,000 CUP. Perpetua no ofrece renovación; cualquier otra licencia instalada ofrece «Renovar o cambiar».

## 5. Claves de GL

| Clave | Política |
|---|---|
| Firma (`kid = gl-sign-v1`) | **Solo fijada en el build**, como lista (`LicenseTrust.SIGN_KEYS`). La primera es la del **emisor vigente** (GL 1.2.0 del teléfono `4bc9f8f3`, pestaña Claves leída el 22/10/2026): `sha256:113f:a5f6:…:4b1c`. Después se conservan las rotaciones anteriores para verificar licencias ya emitidas: `sha256:58d4:3aac:…:8c4e` (bundle `gl-public-keys.txt`, 05/10/2026), `sha256:e693:6c20:…:0a6e` y `sha256:d9c4:d821:…:998e` (GL el 15/10/2026) y `sha256:8a91:bcfb:…:6888` (30/09/2026) |
| ECDH | **Solo fijada en el build** (`LicenseTrust.ECDH_KEY`) a la del **emisor vigente** (misma lectura del 22/10/2026): `sha256:fb3a:f7fc:…:078d`. La solicitud solo puede abrirse con la privada correspondiente en la instalación GL emisora. |

**Importante:** GL genera sus pares localmente en el Keystore; el archivo versionado de GL es un snapshot, no una garantía sobre todas las instalaciones. Si el GL emisor tiene otras claves, hay que comparar con su pestaña **Claves**: agregar su firma a `SIGN_KEYS` y fijar su ECDH requiere un build nuevo. Tras cambiar ECDH, las solicitudes pendientes cifradas para la clave anterior deben generarse de nuevo. Las licencias ya instaladas se re-verifican con las firmas históricas conservadas.

**Rotación del 22/10/2026:** la ECDH fijada hasta entonces (`sha256:2a3f:…:666d`, del bundle del 05/10) ya no era la del GL instalado (1.2.0, alta el 20/10): el emisor rechazaba toda solicitud de Shalev con «solicitud dañada o no es para GL» y ninguna licencia se podía activar. Se fijaron la ECDH y la firma vigentes del teléfono `4bc9f8f3`.

`LicenseTrustTest` comprueba las huellas del bundle GL, que las claves parsean como P-256 y que las cuatro firmas de GL conocidas siguen aceptándose.

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
