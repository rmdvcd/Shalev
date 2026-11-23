# Pasos del propietario — rama `pc/2026-11-22-verificacion`

El agente no ejecutó ninguno de estos pasos. Los comandos son para PowerShell en el PC del propietario.

## 1. Publicar la rama y pasar el CI de iOS

```powershell
cd C:\ruta\a\Shalev
git switch pc/2026-11-22-verificacion
git log --oneline -8
git push -u origin pc/2026-11-22-verificacion
```

Cuando termine el workflow:

```powershell
gh run list --workflow ios-build.yml --limit 5
gh run view <ID> --log-failed
```

Si el runner señala más errores Swift, pasarlos al agente; **no** se declara iOS verificado hasta que el workflow esté en verde.

## 2. Raíces públicas del directorio (solo las públicas)

Generar las dos raíces **fuera del repo** con `tools\directorio` (repo GL) y guardar las copias cifradas y la passphrase por separado. Entregar al agente únicamente:
- `RAIZ_A_SPKI` (SPKI DER en Base64)
- `RAIZ_B_SPKI`

Se escribirán en:
- `Android/licencia/src/main/kotlin/cu/shalev/licencia/TrustDirectory.kt`
- `iOS/Sources/Domain/TrustDirectory.swift`
- `Web-Desktop/dshalev/licencia/directorio.py`

Después, comprobar que la guarda de build pasa con las raíces reales y falla con vacías:

```powershell
python -m unittest discover -s tools\verificacion\tests -p "test_*.py"
```

## 3. Datos de cobro (T2)

Entregar los métodos y destinos reales para `Web-Desktop/dshalev/licencia/pago.py` (`METODOS_COBRO`), y su equivalente en `PaymentInstructions.kt` / `PaymentInstructions.swift`. Hoy está vacío a propósito: `AVISO_SIN_METODOS` se muestra en la tarjeta «Cómo pagar». **Si el repositorio es público, usar datos de cobro que puedan publicarse.**

## 4. APK nuevo de GL en el teléfono emisor

1. Instalar el APK de GL que acepta 7 días y el campo `tx`.
2. Solo entonces, cambiar a `true`:
   - `Android/licencia/src/main/kotlin/cu/shalev/licencia/contract/SolicitudCifrada.kt` → `ENVIAR_TX_EN_SOLICITUD`
   - `iOS/Sources/Domain/License.swift` → equivalente
3. Recompilar y publicar los clientes.

Mientras no se confirme la instalación, la constante se queda en `false`.

## 5. Decisión sobre el listener LAN de DShalev

En modo `--lan`, `/licencia/*` se sirve en la IP de red (protegido por inicio de sesión). Elegir:
- **(a)** restringir las rutas `/licencia/*` a `127.0.0.1` aun en modo LAN, o
- **(b)** aceptar el acceso autenticado desde la red local.

Sin esa decisión no se toca el código.

## 6. Pruebas físicas pendientes

- SMS reales: contar partes de la solicitud (esperado: 4 en Android/iOS, 5 en DShalev).
- WhatsApp: mensaje + QR legibles.
- Sin conexión y con reloj desfasado ±1 h.
- Sesión iPhone ↔ Android pendiente desde 2026-11-08.

## 7. Recompilar antes de entregar

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\jdk17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd C:\ruta\a\Shalev\Android
.\gradlew.bat shalevCheck --console=plain
cd ..
Web-Desktop\.venv\Scripts\python.exe -m pytest Web-Desktop\tests -q
Web-Desktop\.venv\Scripts\python.exe -m unittest discover -s tools\verificacion\tests -p "test_*.py"
Web-Desktop\.venv\Scripts\python.exe tools\verificacion\gsm7.py
```
