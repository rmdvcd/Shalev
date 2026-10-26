# Shalev · Video promocional de 90 segundos

**Duración:** 1:33.4 min · **93.4 s**  
**Formato:** 1920 × 1080 · **60 fps** · H.264 High + AAC  
**Idioma:** español · narración y subtítulos integrados (también en `.srt`)  

Promoción de las funciones clave de Shalev construida con las capturas reales de la app (`app/capturas/claro` y `promocion/capturas`). El zoom, los pulsos naranja y el punto de toque señalan el control que se está usando; la música de fondo es original y baja de volumen sola mientras hay voz.

## Escenas

| # | Escena | Inicio | Duración | Capturas |
|---|---|---:|---:|---|
| 01 | Presentación | 0:00.0 | 7.5 s | shot_inicio_claro.png, shot_inicio_claro.png |
| 02 | Todo el negocio, en una pantalla | 0:07.5 | 7.6 s | shot_inicio_claro.png, shot_inicio_claro.png, shot_inicio_claro.png |
| 03 | De carrito a cobro verificado | 0:15.1 | 10.8 s | shot_carrito.png, 05a2_venta_carrito_transferencia.png, 05f_venta_qr.png, 05h_venta_datos_cliente.png |
| 04 | Existencias, insumos y recetas | 0:25.9 | 11.5 s | shot_inventario_claro.png, 02k2_inventario_ficha_elaborado.png, 08m_producto_elaborado_receta.png, shot_inventario_claro.png |
| 05 | Cobra tu trabajo sin cuentas a mano | 0:37.4 | 7.3 s | shot_servicios_claro.png, 06i_precios_lista.png |
| 06 | Revisa, corrige y comparte | 0:44.7 | 7.2 s | shot_registros.png, 04o_registros_ficha_venta.png, 04m_registros_hoja_exportar.png |
| 07 | Hasta 10 teléfonos en red local | 0:51.8 | 7.4 s | shot_vinculadas.png, 07p_vinculacion_principal_turnos.png |
| 08 | Tus datos, solo en tu teléfono | 0:59.2 | 6.5 s | 07a2_ajustes_configuracion_completa.png, 09a_respaldo_paso1.png, 09c_respaldo_paso2.png |
| 09 | Tres pasos y estás trabajando | 1:05.7 | 13.5 s | 07i_licencia_prueba_paso1.png, 07k_licencia_paso2_solicitar.png, 07l_licencia_paso3_activar.png, 07n_licencia_activa.png, 07n_licencia_activa.png |
| 10 | Planes claros, sin sorpresas | 1:19.2 | 9.2 s | — |
| 11 | Cierre y Telegram | 1:28.4 | 5.0 s | — |

## Narración

**01. Presentación** — Shalev: punto de venta, inventario y servicios para tu negocio. Cien por ciento sin internet.

**02. Todo el negocio, en una pantalla** — En Inicio ves alertas de stock y caducidad, las ventas del turno, la ganancia neta y tus rankings.

**03. De carrito a cobro verificado** — Vende en segundos: arma el carrito, cobra en efectivo o por transferencia con el QR de Transfermóvil y verifica el pago con el número de transacción.

**04. Existencias, insumos y recetas** — Controla existencias, insumos y recetas: Shalev calcula el costo, te dice para cuántas unidades alcanza y descuenta cada ingrediente al vender.

**05. Cobra tu trabajo sin cuentas a mano** — Cobra tus servicios y deja que Shalev aplique recargos o descuentos automáticos según el método de pago.

**06. Revisa, corrige y comparte** — Revisa, corrige y exporta ventas, turnos y ganancias en PDF o Excel, listos para compartir.

**07. Hasta 10 teléfonos en red local** — Trabaja en equipo: conecta hasta diez teléfonos de empleados por la Wi-Fi del local, sin gastar datos.

**08. Tus datos, solo en tu teléfono** — Tus datos viven en tu teléfono, protegidos con huella o PIN y respaldos con contraseña.

**09. Tres pasos y estás trabajando** — Activar la licencia toma tres pasos: revisa tus datos, elige el plan y envía la solicitud. Cuando llegue el código, lo pegas, confirmas, y listo.

**10. Planes claros, sin sorpresas** — Planes mensual, semestral, anual y perpetua, desde seis mil, con siete días de prueba gratis y todas las funciones.

**11. Cierre** — «Únete al grupo de Telegram: t.me/spviapp» · «Shalev: vende mejor, controla más».

## Precios mostrados (CUP)

| Plan | App principal | Por cada app secundaria (0–10) |
|---|---:|---:|
| Mensual | 6 000 | +1 000 |
| Semestral | 30 000 | +5 000 |
| Anual | 50 000 | +9 000 |
| Perpetua | 90 000 | +17 000 |

Cifras tomadas de `MANUAL_USUARIO.md` §14.1 y de `LICENSE_CLIENT.md`. La activación se explica tal como aparece en la app: Ajustes → Licencia → tres pasos (revisar datos, solicitar, activar).

## Grupo de Telegram

- Enlace: <https://t.me/spviapp/1> (`t.me/spviapp`)
- El cierre muestra el código QR del grupo y el enlace en pantalla.

## Archivos

- `Shalev_promo_90s_1080p60.mp4`: video final (narración, música, animaciones y subtítulos).
- `Shalev_promo_90s.srt`: subtítulos para reeditar o publicar aparte.
- `Shalev_promo_90s_guion.md`: este documento.

## Corrección de narración

- Voz masculina latina (español de México), elegida por el usuario en la audición de Arena; sustituye las diez locuciones originales.
- En los planes se dice «desde seis mil», sin pronunciar «CUP». Los precios y la moneda visibles no cambian.
- Se conservan íntegros el flujo H.264 original (1080p60), el montaje, los subtítulos y el cierre. Solo se sustituye la pista de audio, con música original atenuada y efectos reconstruidos.
- Cada locución se ajusta al tiempo de su escena mediante `atempo`, sin cambiar el tono de la voz.

Para repetir esta actualización con las diez voces nuevas en la caché:

```bash
.venv/bin/python tools/video_promo/actualizar_narracion.py
```

El comando comprueba que los fotogramas y los subtítulos no cambien antes de reemplazar el MP4.

## Cómo se regenera

```bash
.venv/bin/python tools/video_promo/generar_promo_90.py --preview   # fotogramas de control
.venv/bin/python tools/video_promo/generar_promo_90.py --render    # MP4 completo
```

La voz se lee de `.cache/shalev_promo90/voz/01…10.mp3` (no versionada). Para una voz nueva, reemplaza esos archivos y vuelve a renderizar.