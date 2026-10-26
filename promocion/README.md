# Material de promoción

## Video promocional de 90 segundos · 25 de octubre de 2026

[`video_promo_90/`](video_promo_90/Shalev_promo_90s_guion.md) reemplaza al video manual de 3 minutos para
difusión: **1:33, 1920 × 1080 a 60 fps**, narración en español, música de fondo original que se atenúa
sola bajo la voz y subtítulos integrados (también en `.srt`).

| Archivo | Uso |
|---|---|
| `Shalev_promo_90s_1080p60.mp4` | Pieza final para YouTube, Facebook, Instagram TV y presentaciones |
| `Shalev_promo_90s.srt` | Subtítulos para reeditar o publicar aparte |
| `Shalev_promo_90s_guion.md` | Guion, tiempos, capturas usadas y precios mostrados |

Recorrido: presentación → Inicio y turno → venta y cobro verificado (Transfermóvil) → inventario, insumos y
recetas → servicios y precios automáticos → registros y exportación → equipo por Wi-Fi local → privacidad y
respaldo → **activación de la licencia en 3 pasos** → **precios (mensual, semestral, anual, perpetua)** →
cierre con el **grupo de Telegram <https://t.me/spviapp/1>** y su código QR.

Las capturas son reales; el zoom, el recuadro naranja y el punto de toque son recursos del montaje para que
cada función se lea con claridad. Todo se genera con `tools/video_promo/generar_promo_90.py`.

## Campaña con predominio claro · 23 de octubre de 2026

Nueva entrega en [`campana_social/`](campana_social/README.md): supercollage vertical,
collage 4:5, versiones JPG para WhatsApp, reel de 30 segundos, escenas reutilizables y
textos descriptivos. Usa las mismas capturas reales conservadas aquí, prioriza el modo
claro e incluye gráficos y registros en oscuro. El reel es un montaje animado, no una
nueva grabación del teléfono. Véanse las notas sobre derechos de las miniaturas antes
de publicar.

## Material anterior

Collages con capturas reales de la app en el teléfono `4bc9f8f3`, con los datos de demostración
del sembrador de depuración (18 meses de ventas, 54 artículos de inventario, 20 servicios y
8 apps secundarias).

| Archivo | Uso |
|---|---|
| `collage_feed_1080x1350.png` | Publicación de Facebook/Instagram (4:5) |
| `collage_historia_1080x1920.png` | Historia de Instagram/Facebook (9:16) |
| `capturas/` | Pantallas originales (1080×2400, tema claro y oscuro) |

Las capturas son de la versión de depuración (`cu.shalev.app.debug`); la app publicada es la misma
interfaz con el paquete `cu.shalev.app`.

## Cómo se regeneran

```powershell
# 1. Sembrar los datos en el teléfono (APK de depuración instalada)
adb shell am start -n cu.shalev.app.debug/cu.shalev.app.seed.SeedActivity

# 2. Capturas nuevas: guardarlas en app_usb_check/shot_*.png (1080x2400)
# 3. Montar los collages
python tools/seed_demo/collage.py
```

El sembrador vive en `data/src/debug` (no entra en el APK de release) y las fotos de los artículos
están en `tools/seed_demo/seed_fotos` (créditos en `tools/seed_demo/CREDITOS.md`).
