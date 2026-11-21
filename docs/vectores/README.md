# Vectores de Prueba — Directorio de Confianza v1 (Anexo A)

Este directorio contiene vectores de prueba y especificación de claves para el Directorio de Confianza v1 (`directorio.json`).

## Claves Raíz de Prueba (Test Roots)

Claves fijas deterministas utilizadas para la suite de pruebas unitarias en Android, iOS y DShalev:

### Raíz A (Test)
- **Escalar privado:** `0x41414141...` (32 bytes `0x41`)
- **SPKI DER (Base64):** `MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEZpI3VGYrivhlu/KoncHtr3sZZHVE/Vse1PFOL4htit+HdH/JfpmjR67vlmMY7Bok5yHXzvc3HCDFCt9TWNgDCg==`
- **Huella SHA-256:** `sha256:1153:e76a:3972:8a27:817e:faaa:4413:42d5:5ccc:8629:6fe1:a437:b643:7c1f:0d1a:4bdb`

### Raíz B (Test)
- **Escalar privado:** `0x42424242...` (32 bytes `0x42`)
- **SPKI DER (Base64):** `MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEOtOGGpViE5JRa7WT7wVYPtLlhm9ctiYKMBcjf9ibkK/Qlhx+NwdaZ5GjnGH1YpWwK20mVnthXmCqQe4cjoM4jQ==`
- **Huella SHA-256:** `sha256:8baa:634a:1a6e:7ea7:94ae:b8c4:6d1d:e59c:9d7a:1243:e92d:3630:1805:adc0:a12a:10a4`

## Archivos de Vectores

- `directorio-v1.json`: Directorio válido con `v=1`, `seq=1`, firmado por la Raíz A de prueba.
  Contiene la clave ECDH vigente de PR #148 y la clave de firma vigente de PR #148.

## Nota de Verificación Cruzada
Generado conforme al Anexo A de `IMPLEMENTACION_SHALEV.md`. La verificación cruzada con el CLI oficial de GL queda registrada como pendiente de ejecución cuando el CLI remoto esté accesible.
