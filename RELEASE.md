# Shalev — Release firmado (APK y AAB)

Cómo generar la versión que se entrega a los clientes. La versión de trabajo es **1.0.1/54**; no se ha publicado una release firmada por la auditoría del 13/11/2026. La primera publicación fue **v1.0/53** (22/10/2026). Todo se hace en el ordenador del desarrollador con JDK 17 y SDK 35. El repositorio **no contiene** keystore ni contraseñas. CI genera APK debug y release **sin firmar**: no constituyen una actualización de producción.

> **Regla de oro:** firma **siempre con el mismo keystore**. En Android 8+ el `ANDROID_ID` depende de la clave de firma, y el `deviceId` de la licencia GL es `SPVI:` + `ANDROID_ID`. Si cambias de clave:
> - todas las licencias emitidas dejan de valer;
> - Android no deja actualizar la app instalada: hay que desinstalar, y con eso se pierden los datos (salvo que haya respaldo `.shalev`).

## 1. Usar el keystore vigente, sin generar otro

Shalev ya tiene instalaciones y licencias emitidas. **No ejecutar `keytool -genkeypair` para esta app** ni
sustituir la clave al faltar el archivo: recuperar la copia custodiada del keystore vigente. El nombre del
archivo no basta; comparar certificado, alias y huella. Consulta local del almacén (pide la contraseña
interactivamente, nunca escribirla en el repositorio ni enviarla por chat):

```bash
keytool -list -v -keystore "$HOME/.android/gl-release.jks" -alias gl
```

> **Clave vigente (la que firmó la Release v1.0 publicada y la misma con la que se firma GL.apk):**
>
> | | |
> |---|---|
> | Fichero | `~/.android/gl-release.jks` (fuera del repositorio) |
> | Alias | `gl` |
> | Certificado | `CN=GL, O=GL, C=US` |
> | Huella SHA-256 | `13:9D:3D:A9:3B:D3:AB:B8:81:A2:C5:55:03:DA:E0:B0:59:AB:4C:36:A6:8A:D1:63:94:A8:B5:26:FF:D4:17:38` |
> | Credenciales | `Android/keystore.properties` (ignorado por git) o variables `Shalev_*` |
>
> Verificado contra la Release `v1.0` publicada (`Shalev-1.0.apk`): `apksigner` confirma ese mismo
> certificado (`docs/HISTORIAL_DESARROLLO.md`, 2026-10-22). Es la **misma clave con la que se firma
> GL.apk** (el generador de licencias firma en local con `gl.jks`, alias `gl`), así que es la prioridad
> al elegir clave.
>
> ⚠️ **No uses `shalev-release.jks` (huella `4F:3B:…:E8:37`)**: nunca se publicó ninguna Release con esa
> clave y no está en el equipo de compilación; firmar con ella cambiaría el `ANDROID_ID`, anularía todas
> las licencias emitidas y obligaría a desinstalar la app para actualizar.
>
> **Copia el `.jks` y su contraseña a dos sitios fuera del ordenador** (memoria USB cifrada y un gestor de contraseñas). Sin ellos no se puede volver a publicar una actualización: cambiar la firma anula las licencias ya emitidas y obliga a desinstalar la app (y con ella, sus datos).

## 2. Configurar la firma en Gradle

`Android/app/build.gradle.kts` ya trae el `signingConfig` de release. Lee los datos, por orden, de:

**Opción A — `Android/keystore.properties`** en la raíz del proyecto Android. Está en `.gitignore`, así que nunca se sube:

```properties
storeFile=/ruta/absoluta/gl-release.jks
storePassword=CAMBIAR
keyAlias=gl
keyPassword=CAMBIAR
```

**Opción B — variables de entorno** (útil en CI):

| Variable | Contenido |
|---|---|
| `Shalev_KEYSTORE` | Ruta absoluta del `.jks` |
| `Shalev_KEYSTORE_PASSWORD` | Contraseña del almacén |
| `Shalev_KEY_ALIAS` | `gl` (alias vigente) |
| `Shalev_KEY_PASSWORD` | Contraseña de la clave (igual que la del almacén con PKCS12) |

Si falta algún dato, Gradle avisa con «firma de release no configurada» y el release sale **sin firmar** (`app-*-release-unsigned.apk`), que no se puede instalar.

Fragmento real del build, como referencia:

```kotlin
signingConfigs {
    if (firmaCompleta) {
        create("release") {
            storeFile = file(firmaStoreFile!!)
            storePassword = firmaStorePassword
            keyAlias = firmaKeyAlias
            keyPassword = firmaKeyPassword
        }
    }
}
buildTypes {
    release {
        isMinifyEnabled = true        // R8: reduce, optimiza y ofusca
        isShrinkResources = true
        isDebuggable = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        if (firmaCompleta) signingConfig = signingConfigs.getByName("release")
    }
}
```

## 3. Antes de compilar

1. Sube `versionCode` (siempre mayor que el anterior) y `versionName` en `Android/app/build.gradle.kts`.
   - **Actualizaciones automáticas:** por defecto `Android/gradle.properties` apunta `shalevGithubRepo` a `rmdvcd/Shalev`; no hace falta configurarlo para este repositorio. La app consulta la última Release pública en [github.com/rmdvcd/Shalev/releases](https://github.com/rmdvcd/Shalev/releases), tanto para encontrar el APK como para descargar la lista de revocaciones. Para probar un fork/repo distinto, sobrescribe con `-PshalevGithubRepo=usuario/repositorio`; un valor vacío desactiva las consultas.
2. Ejecuta `./gradlew shalevCheck shalevInstrumentedTests` con un emulador o teléfono conectado.
   Después, `python3 tools/verificacion/api_minima.py` (con el JDK 17 en el PATH). Comprueba que ninguna clase compilada llama a una API de Android o de Java posterior a la 26 sin comprobar la versión, también en core/domain/licencia, que lint no analiza. Debe terminar con «0 llamadas no permitidas».
3. No regenerar el baseline de lint para ocultar errores. Verificar que el esquema actual **v18** está
   generado por Room/KSP y versionado, y que las migraciones pasan en el emulador. Un cambio de entidades
   posterior exige su propia migración y el nuevo JSON; nunca sustituir el esquema a mano.

## 4. Compilar

```bash
./gradlew shalevRelease          # shalevCheck + AAB + APK, en un solo comando
# o por separado:
./gradlew :app:assembleRelease # APK
./gradlew :app:bundleRelease   # AAB
```

| Artefacto | Ruta | Para qué |
|---|---|---|
| APK firmado | `Android/app/build/outputs/apk/release/app-universal-release.apk` | **Distribución directa** (WhatsApp, Telegram, Zapya, Bluetooth, USB). Es lo habitual para Shalev |
| AAB | `Android/app/build/outputs/bundle/release/app-release.aab` | Solo para subir a Google Play. No se instala directamente |
| Mapping de R8 | `Android/app/build/outputs/mapping/release/mapping.txt` | **Guárdalo con cada versión**: sin él las trazas de error de release no se pueden leer |

También se generan splits firmados `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk` y
`app-x86_64-release.apk`. Elegir la ABI del teléfono o el universal. Si aparecen con `-unsigned`, detenerse:
no son instalables y **no** se deben subir como release.

En Android Studio, el mismo resultado: **Build → Generate Signed App Bundle / APK…**, con el mismo `.jks`.

> **Google Play y Play App Signing:** Play vuelve a firmar con **su** clave. El `ANDROID_ID`, y con él el `deviceId`, de una instalación desde Play es distinto del de un APK instalado a mano. Por eso las licencias no se pueden pasar de un canal a otro. Elige un canal por cliente.

¿Necesitas un APK a partir del AAB? Usa `bundletool build-apks --bundle=app-release.aab --output=shalev.apks --mode=universal --ks=/ruta/al/gl-release.jks --ks-key-alias=gl`, siempre con la clave vigente y sus credenciales solo en el equipo de firma.

## 5. Verificar el APK

```bash
BT=$ANDROID_HOME/build-tools/35.0.0
$BT/apksigner verify --verbose --print-certs Android/app/build/outputs/apk/release/app-universal-release.apk
  # → "Verified using v2 scheme (APK Signature Scheme v2): true" y la huella SHA-256 vigente
# Automático: compara la huella con la clave vigente (falla si el APK salió con otra clave):
bash tools/verificacion/verificar_firma_apk.sh Android/app/build/outputs/apk/release/app-universal-release.apk
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer manifest permissions Android/app/build/outputs/apk/release/app-universal-release.apk
  # → android.permission.CAMERA, android.permission.INTERNET, android.permission.POST_NOTIFICATIONS,
  #   android.permission.FOREGROUND_SERVICE, android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE,
  #   android.permission.CHANGE_NETWORK_STATE, android.permission.REQUEST_INSTALL_PACKAGES,
  #   android.permission.REQUEST_DELETE_PACKAGES, android.permission.READ_MEDIA_IMAGES,
  #   android.permission.READ_EXTERNAL_STORAGE (maxSdk 32), android.permission.WRITE_EXTERNAL_STORAGE (maxSdk 28),
  #   android.permission.USE_BIOMETRIC, android.permission.USE_FINGERPRINT (0.27.0, acceso con clave)
  #   (y cu.shalev.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, permiso propio de androidx.core)
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer manifest debuggable Android/app/build/outputs/apk/release/app-universal-release.apk
  # → false
```

## 6. Prueba de humo en un teléfono real (APK de release)

> La guía completa por USB (comandos adb, tests instrumentados, pruebas manuales por flujo y checklist de 45 puntos) está en [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md).

```bash
adb install -r Android/app/build/outputs/apk/release/app-universal-release.apk
adb logcat -c && adb logcat | grep -i shalev    # en release no debe aparecer ningún log propio
```

| # | Comprobación | Esperado |
|---|---|---|
| 1 | Primera apertura | Splash → asistente; se puede saltar |
| 2 | Licencia → solicitar por WhatsApp | Se abre WhatsApp con el mensaje cifrado |
| 3 | Pegar una licencia de GL emitida para **este** teléfono | Activa; banner correcto en Inicio |
| 4 | Abrir turno → venta en efectivo y por transferencia (QR) | Venta registrada; stock descontado |
| 5 | Inventario → agregar foto con cámara/galería | Se pide acceso solo al usarlo; la cámara se apaga al salir. No hay escáner/SKU |
| 6 | Respaldo → exportar e importar el `.shalev` | Datos restaurados; contraseña incorrecta = error claro |
| 7 | Registros → exportar PDF y Excel | Se abren en otra app |
| 8 | Recientes con Licencia, Perfil o el QR abiertos | Miniatura en blanco (`FLAG_SECURE`) |
| 9 | Tema claro y oscuro del sistema; letra grande al 200 % | Legible y sin cortes |
| 10 | (0.25.0) Abrir turno con fondo 100 → entrada 50 → venta en efectivo → cerrar con **Cuadra** | Registros → Turnos → Caja: esperado = contado, «Diferencia (cuadra)» |
| 11 | (0.25.0) Registros → venta → **Anular** / **Modificar** | ANULADA / «Corrige #N»; existencias devueltas |
| 12 | (0.25.0) Instalar la versión anterior, publicar esta en GitHub → Ajustes → **Buscar ahora** → **Actualizar** | Descarga, confirmación de Android y datos intactos |
| 13 | (0.25.1) Registros → Turnos → turno → **Compartir** → PDF y Excel (enviar y guardar) | El PDF alterna vertical/horizontal sin columnas cortadas; el Excel se abre sin avisos de reparación |
| 14 | (0.25.1) Modificar una venta: añadir un artículo, quitar otro y pasar a Transferencia | «Corrige #N» con el total y método nuevos; caja y existencias ajustadas |
| 15 | (0.25.1) Secundaria: pedir el cierre desde la principal → **Ahora no** → **Contar** | No se cierra hasta contar; luego llega el conteo y se cierra |
| 16 | (0.25.1) Licencia vencida → Licencia | Aparece «ID de la licencia anterior (opcional)» |
| 17 | (0.26.0) Secundaria: Abrir turno sin fondo → **Pedir fondo**; en la principal, Apps vinculadas → **Asignar fondo** | La secundaria abre con ese fondo, sin poder cambiarlo; el siguiente turno vuelve a pedirlo |
| 18 | (0.26.0) Instalar → aceptar el permiso de fotos → usar 1 día → desinstalar → reinstalar → aceptar el permiso | Licencia muestra los días que quedaban, no 7. Con la prueba vencida, sigue vencida. Existe `Imágenes/Shalev/sys_….png` |
| 19 | (0.26.0) Igual que 18, pero **negando** el permiso en la reinstalación | La prueba vuelve a empezar (limitación aceptada: sin permiso no se puede leer el registro en Android 10+) |
| 20 | (0.26.0) Publicar una versión nueva y adelantar el reloj 30 días | Inicio avisa «Obligatoria desde…»; al vencer, pantalla de bloqueo con Actualizar, Exportar respaldo y Cerrar turno |
| 21 | (0.27.0) Ajustes → Acceso con clave → activar; cerrar la app del todo y abrirla; luego 10 min en segundo plano | Pide huella o PIN las dos veces; en release también (R8 no rompe `BiometricPrompt`) |
| 22 | (0.27.0) Exportar respaldo sin contraseña e importarlo en otro teléfono; importar uno de la 0.26.0 | El primero no pide contraseña; el de la 0.26.0 sí |
| 23 | (0.27.0) Nuevo producto → **Cámara** | Pide el permiso de cámara, guarda la foto (≤ 1024 px) |
| 24 | (0.27.0) Venta por transferencia con **Cliente fijo** → otra venta | Se sugiere el cliente; aparece en Registros → Clientes |

Si R8 rompe algo en release que en debug funciona, es casi siempre por reflexión o serialización:

1. Revisa `Android/app/build/outputs/mapping/release/missing_rules.txt`.
2. Añade la regla en `app/proguard-rules.pro`.
3. Repite la prueba de humo.

## 7. Distribuir

- **Actualizaciones obligatorias (0.26.0):** toda versión nueva publicada es obligatoria a los 30 días de que cada teléfono la detecte (no hay que marcar nada en la Release). Publica solo versiones estables.

- Envía el APK universal firmado con el nombre de versión (`Shalev-1.0.1.apk`, por ejemplo) y, aparte, su SHA-256, para que el cliente pueda comprobarlo.
- El cliente tiene que permitir «Instalar apps desconocidas» para la app desde la que abre el APK.
- **Actualizar:** instala el APK nuevo por encima del anterior. Los datos se conservan si la firma es la misma y el `versionCode` es mayor.

## 8. Publicar una actualización en GitHub (0.25.0)

Shalev busca la **última Release pública** del repositorio configurado (`GITHUB_REPO`; por defecto [`rmdvcd/Shalev`](https://github.com/rmdvcd/Shalev/releases)), mediante la API `repos/{owner}/{repo}/releases/latest`.

**Forma automática (recomendada), desde el PC del desarrollador:**

```bash
bash tools/verificacion/publicar_release.sh notas.md   # notas.md = novedades de la versión
```

El script lee `versionName` de `Android/app/build.gradle.kts`, compila `:app:assembleRelease`, **verifica la firma** con `verificar_firma_apk.sh` (clave vigente `gl-release.jks`, alias `gl`; si falla, no publica), prepara `Shalev-<version>.apk` (solo el universal) + `Shalev-<version>.apk.sha256` y crea la Release pública `v<version>`. Se niega a publicar si la etiqueta ya existe.

**Forma manual.** Para que la ofrezca e instale:

1. Crea la etiqueta `vX.Y.Z` (por ejemplo `v0.25.1`) y una Release **no** marcada como borrador ni pre-release.
2. Sube el APK firmado con el nombre `Shalev-X.Y.Z.apk`. GitHub calcula su huella (`digest: sha256:…`), que Shalev usa para comprobar la descarga. Si tu cuenta no muestra `digest`, sube también `Shalev-X.Y.Z.apk.sha256`, con la huella en hex (`sha256sum Shalev-X.Y.Z.apk | cut -d' ' -f1 > Shalev-X.Y.Z.apk.sha256`). **Sin huella, Shalev avisa pero no instala.**
3. Escribe las novedades en la descripción: se muestran como notas.
4. **No toques la Release `revocaciones`**: la gestiona GL (lista firmada `revocadas.json`, ver [docs/GL_ESPECIFICACION.md](docs/GL_ESPECIFICACION.md)) y debe tener «Set as latest» desactivado para no tapar la versión de la app.
5. Firma **siempre con el mismo keystore**: Android rechaza instalar encima una versión con otra firma.

Las apps principales lo ven en su próxima consulta semanal (o con **Ajustes → Buscar ahora**). Las secundarias reciben el APK de su principal por la red local, sin internet.

