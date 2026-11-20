# Contexto GL — análisis y clientes de licencia compatibles

Para **otras IAs** o equipos que: (a) auditen GL, o (b) implementen una **app cliente** cuyo sistema de licencias hable el mismo contrato que este generador.

GL es un **emisor offline** en Android (Kotlin, Compose, minSdk 26, target 35). El desarrollador genera licencias para **su propio** software. No hay backend, Firebase, analytics ni permiso `INTERNET`.

## Qué es / qué no es

- **Es:** descifrado de solicitudes + emisión de envelopes + registro SQLCipher cifrado en reposo + export `.glreg` atado al Keystore **de este teléfono**. No hay autenticación de usuario.
- **No es:** store, pasarela de pago, servidor de activación, SDK publicado, ni formato portable entre teléfonos.

Si construyes un cliente, **tú** cifras la solicitud hacia la clave ECDH pública de **esa** instalación de GL. GL no publica un directorio de claves: **tú tienes que fijarlas**, se sacan de la propia app.

### Cómo conseguir las claves públicas

Abre la app → pestaña **Claves** → *Copiar ambas claves*. Sale un JSON listo para pegar:

```json
{
  "contractVersion": 1,
  "encryptionKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELQaSJKdqp6b/uqGc+0juRctWGdb9mHQZj2CZRHsrKNsux9UhBbTHirLhF7q6KupNGc9PwwiqNAVcpAT7MR2EvA==",
  "encryptionFingerprint": "sha256:0289:7025:2266:8423:1613:2ccd:18a7:3ecd:e0ec:878f:767f:e9a4:916b:d356:6771:a2e0",
  "signingKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAED3b2+vZldNNAkjW9X6hKZebUnQ4ycS7lqCK1Z6IMdRHlhhXq1/h7eBRj/4VYAzIjdSFKRknFaATttWM4NTlEQg==",
  "signingFingerprint": "sha256:d252:9bd9:0aa3:e8bb:d120:06e6:b4d0:7a23:9092:9a9e:7501:1a05:a80c:d994:aacb:64be"
}
```

Claves vigentes del emisor en el teléfono 4bc9f8f3, leídas de su pantalla **Claves** el 22/10/2026 por árbol UI (`uiautomator`) y con las huellas recalculadas desde el Base64 (coinciden). Son las de la instalación GL 1.2.0 (`dev.gl.license`, alta el 20/10/2026). Los pares anteriores (`sha256:eb52:…` / `sha256:e693:…` de la 2.ª rotación del 15/10, `sha256:1d7f:515c:…` / `sha256:d9c4:d821:…` de la mañana del 15/10 y `sha256:2a3f:9fce:…` / `sha256:58d4:3aac:…` del 05/10/2026) ya no descifran solicitudes; las firmas anteriores se conservan en el cliente solo para verificar lo ya emitido).

**Guárdalas en el cliente y trátalas como ancla de confianza fija.** Compara `Fingerprint` con la que enseña la pantalla antes de fijarlas: es la comprobación de que no te han colado otras claves.

Solo son claves públicas. No hay nada secreto en este JSON, y no por eso dejes de verificar la firma de cada licencia.

> **Aviso importante:** las claves son **de una instalación concreta**, no del producto GL. Android Keystore las genera localmente la primera vez que se usan. Si el usuario borra los datos de GL, reinstala la app o cambia de teléfono, sale un par distinto y **el cliente rechazará todo lo emitido con el par anterior**. Decide cómo vas a comunicar ese cambio (reinstalar el cliente con las claves nuevas suele ser lo más simple) antes de dar por cerrado el flujo.

## Stack relevante

Clean Architecture: `presentation` / `domain` / `data` / `security`.  
Contrato v1: `CONTRACT_VERSION = 1` en `domain/model/Modelos.kt`.  
Primitivas: `security/CryptoEngine.kt`, `HybridBox.kt`, `RegistryPack.kt`.  
Referencia de armado de solicitud: `security/ClientRequestHelper.kt`.

## Contrato v1 (obligatorio para compatibilidad)

### Algoritmos

| Pieza | Valor |
|-------|--------|
| `alg` | `ECIES-P256-AES256GCM-v1` |
| Acuerdo | ECDH P-256 (`secp256r1`) |
| KDF | HKDF-SHA256, 32 B AES |
| Info HKDF solicitud | `gl-req-v1` |
| Info HKDF licencia | `gl-lic-v1` |
| AEAD | AES-256-GCM, IV 12, tag 16 |
| Firma | ECDSA P-256 SHA-256, DER |
| `kid` | `gl-sign-v1` (identificador, **no** secreto) |

Transcript firmado (bytes concatenados): `epkDER || iv || ct || tag`.

### Envelope JSON (ida y vuelta)

Campos: `v`, `alg`, `epk` (Base64 SPKI), `iv`, `ct`, `tag`, `sig`, `kid`.  
kotlinx.serialization, `ignoreUnknownKeys = false`.

**AAD GCM solicitud:** `v|alg|epk|kid` (el `epk` es el **mismo string** Base64 que en JSON).  
**AAD GCM licencia:** `1|ECIES-P256-AES256GCM-v1|<licenseId>`.

Verificación de solicitud en GL: firma de la efímera **o** de la clave de firma de GL. El cliente debe firmar con su **efímera**.

### Payload solicitud (plaintext UTF-8 JSON)

| JSON | Significado | Validación en GL |
|------|-------------|------------------|
| `v` | 1 | exacto |
| `nombre`, `apellidos` | Unicode letras 2–80 | regex |
| `ci` | 5–20 `[A-Za-z0-9]` | |
| `via` | `WHATSAPP` \| `SMS` | enum |
| `telefono` | E.164 `+` opcional, 8–15 dígitos | |
| `deviceId` | 8–128 `[A-Za-z0-9:_-]` | |
| `appName` | 2–80 `[\p{L}0-9 ._'-]` | **obligatorio** |
| `secundarias` | entero 0–10, ausente o `null` | **opcional; otras apps, flujo v1** |
| `tipo` | `MENSUAL` \| `SEMESTRAL` \| `ANUAL` \| `PERPETUA` | |
| `solicitadaEn` | ISO-8601 Instant | `Instant.parse` |
| `nonce` | 16–128 Base64-url-ish | |
| `devicePub` | SPKI P-256 PEM/Base64 o `null` | opcional |

En el flujo v1 (otras apps), `secundarias` se ignora salvo que aplique a esa app; ausente o `null` significa «no indicado». `-1`, `11` o un valor no entero se rechazan con `InvalidSecundarias`. **Shalev ya no usa este flujo**: una solicitud v1 con `appName == "Shalev"` o `deviceId` con prefijo `SPVI:` se rechaza con «Shalev desactualizada: pide al cliente que instale Shalev 0.23.1 o posterior». Ver «Shalev 0.23.1» abajo.

Si `devicePub` es null, GL cifra la licencia hacia **su propio** ECDH: el cliente **no** puede descifrar en exclusivo. Un cliente compatible **debe enviar `devicePub`**.

### Cómo cifra el cliente la solicitud

1. Par efímero P-256.
2. ECDH(efímera_priv, **pública ECDH de GL**).
3. HKDF-SHA256(shared, salt=IV de 12 B, info=`gl-req-v1`) → AES-256.
4. GCM con AAD canónico; IV el mismo salt.
5. `sig` = ECDSA(efímera_priv, epkDER||iv||ct||tag).
6. Envelope JSON; el usuario lo copia; GL lo lee **solo en primer plano** (o pega).

Las dos claves (cifrado y firma) salen de la pestaña **Claves** de la propia app. Ver "Cómo conseguir las claves públicas" arriba.

### Payload licencia (plaintext)

Campos de la solicitud más: `id` (UUID), `emitidaEn`, `venceEn` (`null` si perpetua), `estado` (`ACTIVA`|`VENCIDA`|`REVOCADA`|`PERPETUA`), `secundarias` (solo cuando corresponde; se omite su clave si es `null`), `nonce` nuevo. `precioCobrado` existe solo en memoria/registro de GL y **no** viaja en el JSON firmado.

## Shalev 0.23.1 (sustituye todo lo anterior de Shalev)

Shalev 0.23.1 no usa envelopes JSON ni códigos QR: solicitud `SPVIR1` y licencia corta `Shalev2`, solo texto. Las claves de GL no cambian (la misma ECDH que descifra y la misma firma `gl-sign-v1`).

**Solicitud (Shalev → GL).** El operador pega el mensaje en el Generador (cuadro de texto + botón «Pegar»). GL busca `SPVIR1:` en cualquier parte del texto, toma lo que sigue ignorando espacios y saltos de línea y para en el primer carácter fuera de `A-Z a-z 0-9 - _`. Sin marcador → «No es una solicitud de Shalev 0.23.» El descifrado: Base64url → `epk (33 B) || ct || tag (16 B)`, `epk` punto P-256 comprimido SEC1; `ikm = ECDH(privada ECDH de GL, epk)`; `okm = HKDF-SHA256(ikm, salt = epk, info = "SPVI-R1", 44 B)` → AES-256 = `okm[0..32)`, IV = `okm[32..44)`; `plano = AES-256-GCM-decrypt(clave, IV, AAD = "SPVI-R1|" || epk, ct || tag)`. Fallo GCM → «solicitud dañada o no es para GL». El plano es JSON `v: 2` (`nombre`, `apellidos`, `ci`, `via`, `telefono`, `deviceId` con prefijo `SPVI:`, `tipo`, `solicitadaEn`, `nonce`, `devicePub` de 33 B comprimidos, `secundarias` 0–10 obligatorio, `renueva` opcional). Sin `appName`: la app es el prefijo del `deviceId`. GL muestra al operador los datos **descifrados** y calcula el precio con la misma tabla (6 000/30 000/50 000/90 000 + 1 000/5 000/9 000/17 000 CUP por secundaria).

**Licencia (GL → Shalev).** Solo la corta: `"SPVI2:" + Base64url(epk 33 B || ct 44 B || tag 16 B || firma 64 B)` = 157 B → **210 caracteres**. Cuerpo en claro de 44 B big-endian: `0x02`, `licenseId` (16 B), huella (primeros 16 B de `SHA-256("SPVI-L2|" + deviceId + "|" || devicePub33)`), tipo (0–3), estado (0–3), secundarias, emitidaEn y venceEn en segundos Unix (`0` = perpetua). Cifrado con efímera P-256 fresca hacia `devicePub` (HKDF info `"SPVI-L2"`, AAD `"SPVI-L2|" || epk`), firma ECDSA cruda r‖s sobre `"SPVI-L2|" || epk || ct || tag`. Se guarda en la tabla `licenses` con `version = 2`, `appName = "Shalev"` y `codigoCorto` (el código completo); el `.glreg` las incluye y al importar revalida la firma.

**Renovación.** Si la solicitud trae `renueva` y ese `id` existe en el registro con el mismo `deviceId`, sin revocar y no perpetua → `vence = max(ahora, vence anterior) + duración` (30/180/365 días), `emitida = ahora`. Si no, se ignora y se avisa al operador. Nunca se usan fechas del cliente.

**Respuesta exacta** (fechas `dd/MM/yyyy` en `America/Havana`, sin tildes ni ñ; 6 líneas + renglón en blanco + código):

```text
Licencia Shalev
Tipo: Mensual
Apps secundarias: 2
Emitida: 03/10/2026
Vence: 02/11/2026
ID: 7c9e6679-7425-40de-944b-e07fc1f90ae7

SPVI2:…(210 caracteres)
```

`Vence: nunca` si es perpetua. Por WhatsApp va el texto tal cual (sin QR ni imágenes); por SMS el mismo texto o solo la línea `Shalev2`. En GL el mensaje se muestra en un cuadro de solo lectura con botón «Copiar mensaje».

**`appName` es obligatorio** en la solicitud (`^[\p{L}0-9 ._'-]{2,80}$`). Sin ese campo, GL rechaza el envelope con `InvalidPayload`. Se copia tal cual a la licencia.

Vencimiento desde **emisión** (reloj del emisor): +30 / +180 / +365 días.

Cliente: ECDH(su_priv, epk del envelope de licencia), HKDF info=`gl-lic-v1`, AAD de licencia, verificar ECDSA con la **pública de firma de GL** (no la efímera).

Cualquier fallo → rechazar. No filtrar detalles.

## Entrega al usuario final

GL abre WhatsApp (`https://wa.me/<digits>?text=`) o SMS (`smsto:`) con un mensaje de 3 líneas para las apps del flujo v1. La primera dice el tipo y el cobro, la segunda lleva el `id` fuera del JSON firmado, y la tercera es el envelope:

```text
Licencia MiApp MENSUAL · 2 secundarias · 8 000 CUP
ID: <uuid>
{"v":1,"alg":"ECIES-P256-AES256GCM-v1",...}
```

Sin secundarias, la primera línea es, por ejemplo, `Licencia MiApp MENSUAL · 6 000 CUP`. El canal no es confidencial. (Shalev 0.23.1 usa el formato de respuesta de su propia sección, no este.)

Precios en CUP (Shalev y resto de apps con secundarias): `MENSUAL` 6 000 + 1 000 por secundaria; `SEMESTRAL` 30 000 + 5 000; `ANUAL` 50 000 + 9 000; `PERPETUA` 90 000 + 17 000. El precio queda congelado al emitir.

## `.glreg`

Backup **del emisor**, no del cliente. Magia `GLRG`, AES-GCM AAD `glreg-v1`, firma Keystore local. **No** es API para apps licenciadas. Detalle: `docs/GLREG.md`.

## Dispositivo emisor (no aplica al cliente)

Registro en SQLCipher con la passphrase envuelta por AES del Keystore; `.glreg` firmado con el par EC local. **No hay PIN ni biometría**: el emisor abre la app directamente. Irrelevante para verificar licencias en el cliente.

## Checklist para una app cliente compatible

1. Generar par P-256 persistente del dispositivo; meter SPKI en `devicePub`.
2. Obtener SPKI ECDH y SPKI de firma de la instalación GL del emisor (pestaña **Claves**, ver arriba), comprobar las huellas y fijarlas en el cliente.
3. Armar payload + envelope v1 como arriba.
4. Mostrar/copiar el JSON para pegar en GL.
5. Al recibir el envelope de licencia: verificar firma GL, descifrar con `devicePub` propio, persistir `id`, `venceEn`, `tipo`, `deviceId`.
6. En runtime: no fiarse solo del reloj si te importa el fraude; GL **no** ofrece revocación online.
7. Rechazar `v≠1`, `alg` distinto, tag/firma malos, `deviceId` que no coincida.

## Archivos a leer primero (otra IA)

| Archivo | Por qué |
|---------|---------|
| `README.md` / `SECURITY.md` | Límites y amenazas |
| `domain/model/Modelos.kt` | Campos exactos |
| `security/HybridBox.kt` | Seal/open |
| `security/ClientRequestHelper.kt` | Ejemplo de armado de solicitud (no se llama en runtime de GL) |
| `security/CryptoGatewayImpl.kt` | AAD reales |
| `docs/GLREG.md` | Solo emisor |
| `docs/DESIGN_SYSTEM.md` | UI; no afecta contrato |
| `GUIA_IA_TESTS_USB.md` | Cómo correr tests en USB |

## Incompatibilidades deliberadas

- Subir `v` o cambiar AAD/info HKDF rompe clientes.
- Un `.glreg` de otro teléfono no importa.
- No hay JSON “licencia en claro” por WhatsApp: siempre envelope.

Al implementar un cliente, **no** copies SQLCipher ni el almacenamiento de GL; solo el contrato de envelopes.
