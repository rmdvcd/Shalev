# Recursos para Google Play

Material listo para la ficha de Shalev, generado desde capturas reales del teléfono (`4bc9f8f3`)
con los datos de demostración.

| Archivo | Uso | Requisito de Play |
|---|---|---|
| `icono_512.png` | Icono de la ficha | 512×512, PNG de 32 bits |
| `grafico_1024x500.png` | Gráfico destacado | 1024×500, PNG o JPG |
| `capturas/01..08.png` | Capturas del teléfono | 1080×1920 (9:16), 2 a 8 imágenes |
| `ficha.md` | Nombre, descripción corta y completa, novedades | Nombre ≤ 30, corta ≤ 80, completa ≤ 4000 |
| `icono_fuente_foreground.png` | Copia del icono del lanzador (referencia) | — |

## Qué falta para publicar

Play Store no acepta un APK, sino un **AAB firmado** con la clave de subida:

- El AAB de la versión 1.0 está en `Android/app/build/outputs/bundle/release/app-release.aab`.
- **Debe firmarse siempre con la misma clave**: la firma define el `deviceId` de la licencia
  (ver [`RELEASE.md`](../../RELEASE.md)). No cambies de keystore entre subidas.
- Si es la primera vez, activa **Play App Signing** y guarda el certificado de subida.

Además de este material hacen falta, en la consola:

1. Política de privacidad publicada (URL). La app no recoge datos, pero Play la pide igualmente.
2. Clasificación de contenido y cuestionario de seguridad de los datos (marcar «no recoge datos»).
3. Capturas por tipo de dispositivo (**obligatorias** las de teléfono; las de tableta son opcionales).
4. Ficha en español (la app solo está en español).

## Regenerar

```powershell
python tools/seed_demo/play_store.py
```

Toma las capturas de `app_usb_check/shot_*.png` (1080×2400) y las recorta a 9:16. Si cambias alguna
pantalla de la app, vuelve a capturarla con ese nombre y ejecuta el script.
