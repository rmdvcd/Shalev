# Prompt para firmar el APK en el PC del desarrollador con OpenCode

Copia el bloque de abajo tal cual en OpenCode (CLI o Desktop) **en tu PC**, donde ya están el JDK 17, el SDK 35 y el keystore. No pegues contraseñas en el chat: se leen de `keystore.properties` o de variables de entorno.

```text
Firma el APK de release de Shalev SIN cambiar ninguna clave, siguiendo RELEASE.md:

1. Condiciones obligatorias (no negociables):
   - Firma SOLO con el keystore existente documentado en RELEASE.md: `~/.android/gl-release.jks`, alias «gl»
     (certificado CN=GL, O=GL, C=US), huella SHA-256
     13:9D:3D:A9:3B:D3:AB:B8:81:A2:C5:55:03:DA:E0:B0:59:AB:4C:36:A6:8A:D1:63:94:A8:B5:26:FF:D4:17:38.
     Es la MISMA clave con la que se firma GL.apk y la que firmó la Release v1.0 publicada de Shalev
     (prioridad: las claves de GL.apk en las releases de GL). NUNCA uses shalev-release.jks (4F:3B:…:E8:37):
     nunca se publicó con ella y cambiaría el ANDROID_ID, anulando las licencias.
   - NUNCA ejecutes keytool -genkeypair ni crees/rotures un keystore: el deviceId de la licencia GL es
     «SPVI:» + ANDROID_ID y depende de la clave de firma; cambiarla anula todas las licencias emitidas y
     obliga a desinstalar la app. Tampoco cambies applicationId ni versionCode salvo que vayas a publicar
     una versión nueva.
   - Las credenciales se toman SOLO de keystore.properties (en la raíz, ignorado por git) o de las
     variables de entorno Shalev_KEYSTORE, Shalev_KEYSTORE_PASSWORD, Shalev_KEY_ALIAS, Shalev_KEY_PASSWORD.
     No las imprimas en pantalla, no las escribas en el repo ni en ningún archivo versionado.

2. Compila el release firmado:
   ./gradlew :app:assembleRelease
   (o ./gradlew shalevRelease si quieres check + AAB + APK). Si Gradle avisa «firma de release no
   configurada», el APK sale SIN FIRMAR y no se puede instalar: soluciónanos solo aportando las
   credenciales EXISTENTES por keystore.properties o Shalev_*, nunca generando una clave nueva.

3. Verifica la firma antes de entregar nada:
   bash tools/verificacion/verificar_firma_apk.sh app/build/outputs/apk/release/app-release.apk
   Debe terminar en «OK: firmado con la clave vigente» con la huella de arriba; si falla, NO distribuyas
   ese APK. Comprueba también a mano:
   $ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
   (esperado: «Verified using v2 scheme: true» y la misma huella SHA-256).

4. Entrega los artefactos:
   - APK firmado: app/build/outputs/apk/release/app-release.apk (distribución directa).
   - AAB si se sube a Play: app/build/outputs/bundle/release/app-release.aab.
   - Guarda SIEMPRE app/build/outputs/mapping/release/mapping.txt con la versión (sin él no se leen
     las trazas de error de release).

5. Prueba de humo rápida en un teléfono real (adb install -r del APK y la checklist de
   PRUEBAS_DISPOSITIVO.md, al menos los puntos 1–6).

Dime al final: ruta del APK firmado, huella SHA-256 que devolvió apksigner y si la verificación
automática quedó en OK.
```
