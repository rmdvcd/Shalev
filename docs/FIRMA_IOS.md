# Firma de la app iOS IShalev

El workflow `.github/workflows/ios-build.yml` firma `IShalev.ipa` cuando tiene una identidad Apple y un provisioning profile válidos. Si no están configurados, conserva el build de CI y sube un IPA **sin firma**, señalado explícitamente en el resumen de Actions; ese IPA no se puede instalar directamente en un iPhone.

## Configuración de GitHub Actions

En el repositorio, abre **Settings → Secrets and variables → Actions** y configura estos *repository secrets*:

- `IOS_CERTIFICATE_BASE64`: certificado de firma Apple exportado como `.p12`, codificado en Base64.
- `IOS_CERTIFICATE_PASSWORD`: contraseña del `.p12`.
- `IOS_PROVISIONING_PROFILE_BASE64`: provisioning profile `.mobileprovision`, codificado en Base64.
- `IOS_TEAM_ID`: Team ID asociado al certificado y al profile.

El workflow por defecto usa `app-store-connect` y la identidad `Apple Distribution`. Opcionalmente, en **Actions → Variables**, define:

- `IOS_EXPORT_METHOD`: `app-store-connect`, `ad-hoc`, `development` o `enterprise`.
- `IOS_SIGNING_IDENTITY`: identidad/certificado que corresponda al tipo de profile; si se omite, usa `Apple Distribution` excepto para `development`, donde usa `Apple Development`.

El profile debe autorizar el bundle ID fijo `cu.shalev.ishalev` y corresponder al Team ID indicado. Para un IPA instalable directamente, elige un método/profile apropiado para los dispositivos de destino (por ejemplo, `ad-hoc` con sus UDID registrados); `app-store-connect` está destinado a App Store Connect/TestFlight.

En macOS, se puede copiar el Base64 directamente al portapapeles sin añadir saltos de línea y pegarlo en el campo del secreto:

```sh
base64 -i IShalev-Distribution.p12 | tr -d '\n' | pbcopy
base64 -i IShalev.mobileprovision | tr -d '\n' | pbcopy
```

No se deben subir los archivos `.p12` ni `.mobileprovision` al repositorio ni incluir sus contenidos en incidencias o chats. El workflow los instala temporalmente en el runner, valida Team ID/bundle ID, exporta y verifica la firma, y elimina los materiales temporales al finalizar.

## Activación GL

La firma Apple del paquete y la firma criptográfica de licencias GL son capas distintas. La firma del IPA no cambia el protocolo GL ni sustituye las claves públicas fijadas en IShalev; activar una licencia continúa dependiendo de que la respuesta SPVI2 esté firmada por una clave GL reconocida y ligada a la identidad del dispositivo.
