# Informe del PC — 2026-11-23 (Shalev, PARTE S)

Repo: `rmdvcd/Shalev` · rama local `pc/2026-11-22-verificacion` · sin `push`.

## Commits creados

| Commit | Contenido |
|---|---|
| `pc.1`, `pc.2` (sesión previa) | Reparación de la compilación del canje y adaptación de pruebas al contrato v1; URL oficial del canje como constante |
| `pc.3` | `ProveedorDeviceId` (identidad sustituible en pruebas JVM), `org.json` para pruebas, y corrección de la lectura del sobre firmado del canje |
| `pc.4` | Mensajes de solicitud 100 % GSM-7 con nombres e importes sin tildes + `MensajesGsm7Test` |
| `pc.5` | `tools/verificacion/gsm7.py` y `tests/test_gsm7.py` |
| `pc.6` | Herramientas y pruebas tolerantes a Windows (zona horaria, consola UTF-8, rutas, bash de Git) |
| `pc.7` | iOS: utilidades compartidas para `TrustDirectory`, `Directory.isKeyCompromised` y mensajes GSM-7 |

## Lo corregido y por qué

1. **Sobre firmado del canje (fallo real).** `verificarAnclajeCanje` tomaba como sobre el objeto `license` interior y luego pedía `getJSONObject("license")` dentro de él: toda licencia con `primariaLicenseJson` quedaba BLOQUEADA. Ahora acepta el sobre directo `{license,sig,kid}` y la respuesta completa `{license:{…}}`. Era el único test rojo de `:data`.
2. **Mensajes fuera de GSM-7 (fallo real, S-5).** Android e iOS escribían el nombre tal cual y el literal «Amplía:», y los importes con U+202F de la pantalla. Cada carácter fuera de GSM-7 fuerza UCS-2 (70 caracteres por parte). Se normaliza el nombre a ASCII en la parte legible (como ya hacía DShalev), se corrige el literal y se usa el formato de exportación para el precio. El payload cifrado conserva el nombre original.
3. **Pruebas JVM del verificador.** `DeviceIdProvider` necesitaba un `Context` de Android; se introdujo la interfaz `ProveedorDeviceId` y se añadió `org.json` real (el `android.jar` de pruebas devuelve `null`).
4. **iOS (compilación).** El CI fallaba con `Base64URL`/`SignatureFormat` fuera de alcance (eran `private` en otro archivo) y `Directory.isKeyCompromised` inexistente (lo llamaba `SyncRepository`). Corregido; **no compilado en este PC**.
5. **Windows.** `probar_gl.py` fallaba sin tzdata y con consola cp1252; `resumen_instrumentados.py` perdía el módulo por separadores `\`; `test_firma_apk.py` usaba un `bash` de WSL; dos pruebas de licencia usaban el ancla real de `LOCALAPPDATA`.

## Verificado en este PC

| Comprobación | Resultado |
|---|---|
| `shalevTests` | 1160 pruebas, 0 fallos (3 min 18 s) |
| `shalevCheck` | BUILD SUCCESSFUL (8 min 47 s); incluye `shalevPermisos` y lint, sin permisos nuevos |
| `pytest Web-Desktop/tests` | 232 passed, 33 skipped (37,18 s) |
| `unittest discover tools/verificacion/tests` | 37 OK (7,08 s) |
| `tools/verificacion/botones.py` | 0 incumplimientos |
| `tools/licencia/probar_gl.py autoprueba` | «AUTOPRUEBA CORRECTA» |
| `tools/verificacion/gsm7.py` | Todas las plataformas conformes; solicitudes: Android 4 partes, iOS 4, DShalev 5 (límite 5) |
| DShalev en vivo | Sin sesión `/licencia*` → 303 a `/entrar`; con sesión `/licencia` y `/licencia/solicitar` 200, tarjeta «Cómo pagar», aviso y campo `tx` |
| Vectores | `precios.json` coincide con `TipoLicencia` (Kotlin) y `GLLicenseType` (Swift); pruebas Python de precios, reglas y directorio en verde |

## No verificado

- **iOS:** nada. El CI de `main` (run 44989769123) está en rojo; `pc.7` corrige esos errores pero no se ha compilado aquí. Se declara verificado solo lo que pase por CI tras el push.
- **Instrumentados Android:** no ejecutados (requieren emulador/dispositivo).
- **Worker de Cloudflare / D1 / canje real:** prohibido operar remoto.
- **`ramplint`:** no instalado en este PC.
- **Pruebas físicas** (SMS reales, WhatsApp, reloj desfasado, sesión iPhone ↔ Android).

## Decisión pendiente

En modo LAN (`--lan`) el servidor escucha en `0.0.0.0` y las rutas `/licencia/*` se sirven en la IP de red, protegidas solo por inicio de sesión. El encargo pedía que ninguna ruta `/licencia/*` respondiera en el listener LAN. **No se implementó** ninguna guarda: requiere decisión del propietario (¿solo `127.0.0.1`, o basta con autenticación?).

## Datos que faltan (bloqueado por el propietario)

- Raíces **públicas** reales (`RAIZ_A_SPKI`, `RAIZ_B_SPKI`) en las tres plataformas.
- Datos de cobro reales (`METODOS_COBRO`).
- `ENVIAR_TX_EN_SOLICITUD`: se deja en `false`.
