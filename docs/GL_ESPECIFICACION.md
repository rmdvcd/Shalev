# Especificación del contrato Shalev ⇄ GL (referencia oficial, 0.24.0+)

Texto aportado por el usuario en el Prompt 70 y comprobado contra el código de Shalev y el vector dorado
(`GoldenVectorTest.kt` y `tools/licencia/probar_gl.py`, reproducido byte a byte en Kotlin, Swift y Python).
Única corrección respecto al original: el punto 2a (fin del código `SPVI2:`), marcado con **[corregido]**.
Las ampliaciones posteriores cubren recuperación (`recupera`), lista firmada de revocaciones (`revocadas.json`) y
ampliación de apps secundarias (`amplia`, parche para `rmdvcd/GL` en `docs/gl-ampliar-secundarias.patch`).
Las mitigaciones del sistema de licencias de GL (clave maestra solo en builds de depuración, sub-tokens de las apps
secundarias y AAD canónica del envelope v1) están en `LICENSE_CLIENT.md` §10–§11.

```
Contexto. Shalev es la app cliente; GL es el emisor offline en otro teléfono.
Las claves de GL NO cambian: la misma clave pública ECDH P-256 de GL (hacia
la que cifras las solicitudes) y la misma clave pública de firma gl-sign-v1
(con la que verificas las licencias). Ambas están fijadas (pinned) en el
cliente. GL descifra con su privada ECDH y firma con su privada gl-sign-v1;
tú nunca ves esas privadas. El transporte (WhatsApp/SMS, texto pegado a mano)
puede partir líneas largas e insertar espacios o saltos: el parser debe
tolerarlo.

Alfabeto y codificaciones.
- "Base64url sin relleno" en todo el documento = RFC 4648 §5 (A-Z a-z 0-9
  '-' '_'), SIN '=' final. Nunca Base64 estándar.
- Enteros multibyte: big-endian. "uint32" = 4 bytes big-endian.
- Punto comprimido SEC1: 33 bytes = prefijo 0x02 (y par) o 0x03 (y impar)
  seguido de x en 32 bytes big-endian con ceros a la izquierda.
- UUID en binario: 16 bytes = decodificar el hex del UUID sin guiones
  ("7c9e6679-7425-40de-944b-e07fc1f90ae7" → bytes 7c 9e 66 79 …). Al mostrar,
  formato canónico 8-4-4-4-12 en minúsculas.
- Fechas en binario: segundos Unix UTC en uint32. Solo para MOSTRAR se
  convierten a dd/MM/yyyy en zona America/Havana.

Primitivas (implementa exactamente así en tu lenguaje).
- Curva P-256 (secp256r1). ECDH: el secreto compartido son EXACTAMENTE los
  32 bytes big-endian de la coordenada x (con ceros a la izquierda).
- HKDF-SHA256(ikm, salt, info, L) según RFC 5869: PRK = HMAC-SHA256(salt, ikm);
  T(1) = HMAC-SHA256(PRK, info || 0x01); T(2) = HMAC-SHA256(PRK, T(1) || info
  || 0x02); OKM = primeros L bytes de T(1) || T(2). Aquí siempre L = 44. El
  salt NO es vacío: son los 33 bytes del epk.
- AES-256-GCM con tag de 16 bytes (en Java "AES/GCM/NoPadding", 128 bits:
  devuelve ct||tag). El AAD son BYTES CRUDOS: UTF-8("SPVI-R1|") o
  UTF-8("SPVI-L2|") CONCATENADO con los 33 bytes crudos del epk.
- ECDSA P-256 con SHA-256, firma en crudo r||s (32 + 32 bytes). La firma es
  aleatoria: se VERIFICA, nunca se compara.
- Descompresión: x = últimos 32 bytes, rhs = (x³ - 3x + b) mod p,
  y = rhs^((p+1)/4) mod p; exige y² mod p == rhs; si la paridad de y no
  coincide con el prefijo (0x03 = impar), y = p - y.

1) SOLICITUD (Shalev → GL). Cada solicitud genera un par efímero P-256 NUEVO
(el dispositivo tiene su par estable devicePriv/devicePub).
  a) JSON UTF-8 del plano ("renueva", "recupera" y "amplia" son mutuamente excluyentes):
     {"v":2,"nombre":"…","apellidos":"…","ci":"…","via":"WHATSAPP"|"SMS",
      "telefono":"+53…","deviceId":"SPVI:…","tipo":"MENSUAL"|"SEMESTRAL"|
      "ANUAL"|"PERPETUA","solicitadaEn":"ISO-8601 UTC",
      "nonce":"…","devicePub":"<pública comprimida 33 B en Base64url>",
      "secundarias":0..10,"renueva":"<licenseId previo>",
      "recupera":"<licenseId previo>","amplia":"<licenseId vigente>"}
     nonce ≥ 128 bits aleatorios en Base64url. NO incluyas "appName" ni
     "licenciaCorta". En ampliación de secundarias ("amplia"), "secundarias" es
     el nuevo total deseado (actuales + adicionales, entre 1 y 10), la fecha
     "venceEn" (o PERPETUA) se conserva intacta y el precio cobra solo las
     secundarias adicionales a tarifa fija (1 000 / 5 000 / 9 000 / 17 000 CUP).
  b) epk = efímera pública comprimida (33 B).
     ikm = ECDH(efímera privada, pública ECDH de GL).
     okm = HKDF-SHA256(ikm, salt = epk, info = UTF-8("SPVI-R1"), 44).
     clave = okm[0..32), IV = okm[32..44).
     ct||tag = AES-256-GCM(clave, IV, AAD = UTF-8("SPVI-R1|") || epk, plano).
  c) Código = "SPVIR1:" + Base64url-sin-relleno(epk || ct || tag).
     Al leerlo, GL ignora espacios y saltos de línea dentro del código.
  d) Mensaje de texto EXACTO:
     Solicitud de licencia Shalev
     Nombre: <nombre y apellidos>
     Carné de identidad: <ci>
     Teléfono: <telefono>
     Tipo: <Mensual|Semestral|Anual|Perpetua>
     Apps secundarias: <N>
     Precio: <base+N*extra, formato 8,000.00 CUP>
     Renueva: <licenseId>      ← solo si renueva
     <renglón en blanco>
     SPVIR1:<código>
     Precios CUP: MENSUAL 6000+1000N, SEMESTRAL 30000+5000N,
     ANUAL 50000+9000N, PERPETUA 90000+17000N.

2) LICENCIA (GL → Shalev).
  a) [corregido] Localiza "SPVI2:" y toma los 210 PRIMEROS caracteres de
     [A-Za-z0-9-_] que siguen, IGNORANDO espacios y saltos de línea; si antes
     de reunir 210 aparece otro carácter, o el texto se acaba, ese "SPVI2:" no
     vale (se prueba el siguiente, si lo hay). Lo que venga después de los 210
     se ignora (así un "Gracias" en la línea siguiente no estropea el código).
     210 caracteres = 157 bytes (33 + 44 + 16 + 64); 216 contando "SPVI2:".
  b) Parte: epk (33 B) || ct (44 B) || tag (16 B) || firma (64 B).
     Exige prefijo epk 0x02/0x03.
  c) VERIFICA PRIMERO: ECDSA con gl-sign-v1 sobre
     UTF-8("SPVI-L2|") || epk || ct || tag. Si no verifica → rechaza sin descifrar.
  d) ikm = ECDH(devicePriv, epk descomprimida).
     okm = HKDF-SHA256(ikm, salt = epk, info = UTF-8("SPVI-L2"), 44).
     cuerpo = AES-256-GCM-decrypt(okm[0..32), okm[32..44),
     AAD = UTF-8("SPVI-L2|") || epk, ct || tag). Si GCM falla → rechaza.
  e) Cuerpo 44 B: byte 0 = 0x02; bytes 1–16 licenseId; bytes 17–32 huella;
     byte 33 tipo (0 MENSUAL, 1 SEMESTRAL, 2 ANUAL, 3 PERPETUA);
     byte 34 estado (0 ACTIVA, 1 VENCIDA, 2 REVOCADA, 3 PERPETUA);
     byte 35 secundarias (0..10); bytes 36–39 emitidaEn; bytes 40–43 venceEn
     (0 = nunca, solo PERPETUA).
  f) huella = primeros 16 B de SHA-256(UTF-8("SPVI-L2|" + deviceId + "|") ||
     devicePub-comprimida-33B), con TU deviceId y TU devicePub; exige igualdad.
     Valida rangos, formato 0x02, venceEn == 0 solo si PERPETUA y
     venceEn == 0 o mayor que emitidaEn.
  g) Mensaje de respuesta EXACTO (dd/MM/yyyy en America/Havana, sin tildes):
     Licencia Shalev
     Tipo: Mensual
     Apps secundarias: 2
     Emitida: 03/10/2026
     Vence: 02/11/2026            ("Vence: nunca" si perpetua)
     ID: 7c9e6679-7425-40de-944b-e07fc1f90ae7
     <renglón en blanco>
     SPVI2:…(210 caracteres)

3) VECTOR DORADO (claves de PRUEBA): ver `GoldenVectorTest.kt` y `LicenseContractTests.swift`
   deviceId = SPVI:golden0000000001
   devicePub = AiuZGHe7sUgbFLfqufeF1BMCvKuYZQuJKYSxnIoc0zMK
   licenseId = 7c9e6679-7425-40de-944b-e07fc1f90ae7, MENSUAL, ACTIVA, 2 secundarias,
   emitidaEn = 2026-10-03T20:00:00Z, venceEn = 2026-11-02T20:00:00Z
   → huella = fb445359b06179d282c7970ea4adab32
   → cuerpo = 027c9e6679742540de944be07fc1f90ae7fb445359b06179d282c7970ea4adab320000026ac15ec06ae8ebc0
   efímera d = 0a0b0c0d0e0f10111213141516171819a0b0c0d0e0f1f2f3f4f5f6f7f8f9fafb
   → epk = 03c36dbaadc01f9f92d0554cd88b70a31e4494cf092e4747d6ff74fd226c17a2b3
   → ct||tag = 0adcdd15829c196616a17a75442d35f75cec1626c4263329266096c095a390b9d8eca0d37cf0c5c3896bc13af9545a3f75be5ff3834ff507d2b42512
   firma de PRUEBA d = 1f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a79881f2e3d4c5b6a7988
   SPKI = MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvXxzuIsum0ztpiAistqL4TGTpbVu3Cbn33hC4kzQtesGBa2nvag6xqK4DX4xQED6R/8WuDushc7bAURRu3znGg==

4) TESTS (todos existen en SPVI: GoldenVectorTest, LicenciaCortaTest, SolicitudCifradaTest).
   a) vector dorado byte a byte; b) roundtrip R1 y mutaciones; c) roundtrip L2,
   firma/epk mutados, otro deviceId; d) código partido con espacios, texto sin
   "SPVI2:"; e) PERPETUA → "Vence: nunca".

5) ERRORES TÍPICOS: 210 caracteres sin prefijo (216 con él); AAD con bytes
   crudos del epk; salt = epk; la firma cubre epk||ct||tag con "SPVI-L2|";
   verificar antes de descifrar; bytes del UUID tal cual; firmas por
   verificación, nunca por igualdad.
```
