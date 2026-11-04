# Informe para arena.ai — Shalev (Android)

**Fecha:** 04/11/2026  
**Repositorio:** `C:\Users\666\Desktop\CODE\SPVI\Shalev`  
**Rama:** `main` (commit `9dea11e` merge #90)  
**Dispositivo de prueba:** `4bc9f8f3` (modelo `23021RAAEG`, API 26+)

---

## 1. Estado general

La app compila, instala y ejecuta correctamente en el dispositivo real.
- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**
- `./gradlew shalevTests` → **BUILD SUCCESSFUL** (883 casos JVM en verde tras correcciones)
- APK debug instalado en `4bc9f8f3`: `cu.shalev.app.debug`, `MainActivity` en primer plano, sin crashes.

---

## 2. Cambios implementados (esta sesión)

### A) Aviso flotante de «operación realizada» (1.0.4)

Se añadió un cuadro flotante automático (no modal, se va solo a los 2 s) con icono ✓ que se muestra tras agregar/guardar/eliminar cualquier elemento (producto, servicio, insumo, venta, ajuste de precios…).

- **Nuevo componente:** `designsystem/src/main/kotlin/cu/shalev/designsystem/component/Overlays.kt` → `ShalevAvisoExito(texto, onFin)`
- **Host global:** `app/src/main/kotlin/cu/shalev/app/common/AvisoExito.kt` → `AvisoExitoHost` (una sola vez en `MainScaffold`, arriba centrado, bajo la barra de estado)
- **Objeto compartido:** `AvisoExito.mostrar(mensaje)` desde cualquier pantalla
- **Conectado a:** `ProductoForm`, `ServicioForm`, `InsumoForm`, `Venta.terminada` (en `MainScaffold.kt`); se eliminó el uso obsoleto de `savedStateHandle` para esos mensajes (`KEY_PRODUCTO_GUARDADO`, `KEY_SERVICIO_MENSAJE`, `KEY_INICIO_MENSAJE` dejaron de usarse para este fin).

### B) Barra de progreso horizontal en Actualización + diálogo de aplicar

En Ajustes → «Actualizar»:
- La barra circular se sustituyó por **`ShalevLinearProgress`** (progreso real 0..1 de la descarga, texto con porcentaje).
- Al terminar la descarga no se instala solo: aparece un cuadro «Actualización descargada» con **Omitir / Confirmar**. Al confirmar se instala y **Shalev se reabre solo** en la versión nueva (vía `ReinicioPendiente` + `ReinicioTrasActualizar` en `MY_PACKAGE_REPLACED`).
- Archivos tocados: `GestorActualizacion.kt` (`ProcesoActualizar.Descargada`, `aplicarActualizacion()`, `esperarConfirmacion`), `ReinicioTrasActualizar.kt` (nuevo), `AndroidManifest.xml` (receptor), `AjustesScreen.kt` (diálogo + `onAplicarActualizar`).

---

## 3. Correcciones de bugs preexistentes (merge roto)

| Archivo | Problema | Corrección |
|---|---|---|
| `data/.../respaldo/RespaldoRepositoryImpl.kt:308` | `c.servicios` es `List<Pair<…>>`; se leía `it.eliminado` / `it.id` sin `.first` | `.first.eliminado` / `.first.id` |
| `app/…/herramientas/HerramientasScreen.kt:75,245` | `CalculadoraCambio { … }` pasaba la lambda como `vm` | `CalculadoraCambio({ … })` |
| `app/…/precios/PreciosScreen.kt:252` | `ShalevSpacing.sm` no existe (solo xs/md/lg) | `xs` |

---

## 4. Ajustes de tests (deuda del merge anterior: formato monetario sin decimales)

`Money.format` ahora muestra enteros sin decimales (`"1450 CUP"` en lugar de `"1450.00 CUP"`). Se actualizaron las expectativas de 6 tests para que coincidan con el formato real:

- `app/…/insumos/InsumoFormTest.kt`
- `app/…/producto/ProductoFormTest.kt`
- `app/…/registros/RegistrosLogicTest.kt`
- `app/…/Version025AppTest.kt`
- `licencia/…/LicenseFlowTest.kt` (2 veces)
- `domain/…/TablasExportTest.kt`

Resultado: `shalevTests` → **883 tests en verde**.

---

## 5. Firma del APK de release — BLOQUEADO

Se ejecutó el procedimiento de `docs/PROMPT_FIRMAR_APK.md`:

**Compilación release:** `./gradlew :app:assembleRelease` → **BUILD SUCCESSFUL** (requirió `-Xmx6g`; `lintVitalAnalyzeRelease` se saltó por `OutOfMemoryError`, no por error de código). Artefactos generados:
- `app/build/outputs/apk/release/app-universal-release.apk` (48,6 MB)
- splits por ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`)
- `app/build/outputs/mapping/release/mapping.txt` (74 MB)

**Verificación de firma (`apksigner verify --verbose --print-certs`):**

```
Verified using v2 scheme (APK Signature Scheme v2): true
Signer #1 certificate DN: CN=GL, O=GL, C=US
Signer #1 SHA-256 digest: 139d3da93bd3abb881a2c55503dae0b059ab4c36a68ad16394a8b526ffd41738
```

**Huella esperada (clave vigente de la app, RELEASE.md):**
`4F:3B:5F:24:15:E7:03:16:27:8A:7C:0D:93:A3:45:2A:88:79:02:68:8E:C7:E0:B8:00:9D:1F:D8:69:AD:E8:37`

**Resultado: NO COINCIDE.** El APK se firmó con `gl-release.jks` (emisor de licencias GL), porque `keystore.properties` apunta a `~/.android/gl-release.jks`. El keystore de la app (`shalev-release.jks`, alias `shalev`) **no está en este equipo** (AGENTS.md, estado de verificación).

**Veredicto:** No distribuir este APK. Firmar con la clave de GL anula todas las licencias GL emitidas (`deviceId = SPVI:` + `ANDROID_ID`, que depende de la clave de firma) y Android obliga a desinstalar la app para actualizar.

**Requisito para publicar:** incorporar `shalev-release.jks` (alias `shalev`, RSA 4096 PKCS12, válido hasta 2054-03-08) y sus credenciales en `keystore.properties` (o variables `Shalev_*`), nunca generar una clave nueva.

---

## 6. Próximos pasos

1. Traer el keystore de la app (`shalev-release.jks`) al equipo y configurar `keystore.properties`.
2. Volver a ejecutar `:app:assembleRelease` y `tools/verificacion/verificar_firma_apk.sh` (debe devolver `OK: firmado con la clave vigente`).
3. Ejecutar `PRUEBAS_DISPOSITIVO.md` (puntos 1–6) en teléfono real con el APK firmado.
4. Publicar (APK directo o AAB a Play) conservando `mapping.txt` con la versión.