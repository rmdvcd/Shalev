# Pruebas de ventanas y componentes — DShalev (Web-Escritorio)

Fecha: 2026-11-10 · Servidor local: `DShalev.py` en `http://127.0.0.1:47812` (FastAPI/uvicorn, `/healthz` OK) · Sesión: administrador de pruebas (nombre omitido) · Herramienta: Playwright (Chromium), viewports 1280×800, 768×1024 y 375×667.

> **Informe histórico, no certifica la rama actual.** Se tomó antes de las correcciones del 2026-11-11. El overflow móvil, el límite de nombres y la retirada auditable requieren repetición real en navegador/PC: sigue [PRUEBAS_IA_SHALEV_DSHALEV.md](PRUEBAS_IA_SHALEV_DSHALEV.md), casos D-23/D-24/D-25 y, si hay Android secundario de prueba, D-26. Usa una raíz temporal nueva; no vuelvas a usar la sesión ni el artículo de prueba que quedaron en el entorno del informe anterior.

Alcance de pruebas por página: tema claro/oscuro, FontWeight, responsive, y Error Input en los formularios de cada ventana.

## Ventanas recorridas (todas las entradas del menú)

| # | Ventana | Ruta | Tema claro | Tema oscuro | h1/h2 | Scroll X |
|---|---|---|---|---|---|---|
| 1 | Inicio (resumen del negocio) | `/` | sí | sí | 700/700 | No |
| 2 | Inventario (catálogo + alta rápida) | `/inventario` | sí | sí | 700/700 | No (desktop) |
| 3 | Servicios | `/servicios` | sí | sí | 700/700 | No |
| 4 | Ventas (POS web: carrito + actividad) | `/ventas` | sí | sí | 700/700 | No |
| 5 | Turnos (caja y arqueo) | `/turnos` | sí | sí | 700/— | No |
| 6 | Reportes | `/reportes` | sí | sí | 700/700 | No |
| 7 | Recetas | `/recetas` | sí | sí | 700/700 | No |
| 8 | Herramientas (conversor CUP/USD, fichas de costo) | `/herramientas` | sí | sí | 700/700 | No |
| 9 | Clientes | `/clientes` | sí | sí | 700/700 | No |
| 10 | Equipo | `/equipo` | sí | sí | 700/700 | No |
| 11 | Acceso y red | `/red` | sí | sí | 700/700 | No |
| 12 | Respaldo y restauración | `/respaldo` | sí | sí | 700/700 | No |
| 13 | Licencia | `/licencia` | sí | sí | 700/— | No |

Sin errores de consola ni errores de página en las 13 navegaciones ×2 (claro y oscuro).

## Resultados por categoría

### Tema claro/oscuro — correcto
- Selector **Tema** (Sistema/Claro/Oscuro) en la cabecera; el valor se persiste en el servidor (sobrevive la navegación completa a las 13 rutas).
- Claro: fondo `rgb(248,249,250)`, texto `rgb(26,28,30)`. Oscuro: fondo `rgb(15,19,24)`, texto `rgb(230,230,230)`; atributo `data-theme` correcto en todas las páginas.

### FontWeight — correcto
- Body 400, h1 y h2 en 700 (verificado por estilos computados en las 13 páginas). Familia tipográfica consistente: `Inter` con fallback de sistema.

### Responsive — funciona, con 1 hallazgo
- **Móvil (375×667):** el sidebar se sustituye por una **barra inferior de navegación** (Inicio, Inventario, Servicios, Ventas, Turnos) y la cabecera se compacta (captura `ds_movil_inventario.png`).
- **Hallazgo WEB-1 (media):** en `/inventario` a 375 px hay **desbordamiento horizontal** (`scrollWidth > clientWidth`): las columnas de la derecha (Historial/CSV/Excel) quedan parcialmente cortadas y la página se desplaza de lado. En `/ventas` y `/clientes` a 375 px no ocurre.
- Tablet (768×1024) y escritorio (1280×800): sin scroll horizontal en ninguna página.

### Error Input — correcto, con 1 hallazgo
| Formulario | Entrada inválida | Resultado |
|---|---|---|
| Alta rápida de artículo (`/inventario`) | Precio de venta `-5` (envío forzado sin validación HTML) | Rechazado por el servidor: *"Escribe un importe positivo con hasta dos decimales."* |
| Alta rápida de artículo | `costo` = `abc` | Bloqueado por HTML5 (`input[type=number]`); mensaje nativo "El valor debe ser mayor o igual que 0." |
| Alta rápida de artículo | Nombre de 2 caracteres `AB` | **WEB-2:** aceptado y guardado ("Artículo guardado."); el texto "De 3 a 160 caracteres" solo aparece junto al motivo |
| Cliente nuevo (`/clientes`) | Nombre vacío + teléfono `abc-notaphone` + email `no-es-email` | Rechazado por el servidor: *"Revisa el nombre, teléfono, correo y nota del cliente."* |
| Nuevo servicio (`/servicios`) | Precio `-5` | Rechazado por el servidor (mismo mensaje de importe positivo); no se creó el artículo |
| Calculadora de cambio (`/herramientas`) | Importe `no-es-numero`, tasa `0` | Mensaje: *"Introduce importes y tasas válidos, sin signos ni separadores de miles."* |
| Seguridad (transversal) | POST sin token CSRF (`/inventario/1/eliminar`) | **403 Forbidden**: el CSRF protege los formularios |
| POS web (`/ventas`) | — | Limitado: el inventario web estaba vacío para probar cantidad inválida en el carrito (el único artículo quedó tras la sesión anterior) |

## Estado final restaurado

- Tema en **oscuro** (el estado original de la sesión).
- En la instancia de aquella corrida quedó 1 artículo ficticio identificado como `Articulo de prueba QA`, con motivo "Prueba automatizada de calidad". No se limpió esa instalación; no la reutilices para la campaña nueva. En esa versión no había retiro visible (hallazgo histórico WEB-3); la rama actual sí incorpora **Retirar**, que debe probarse en raíz temporal independiente.
- El nombre `AB` fue renombrado; ningún servicio ni cliente de prueba quedó creado.
- Capturas en `%TEMP%\opencode\ds_*.png` (no se suben a git). Nota menor de accesibilidad del DOM en `/respaldo`: el formulario de contraseña no incluye campo de usuario (aviso de Chromium, no bloqueante).

## Resumen de hallazgos

| ID | Severidad | Hallazgo | Sugerencia |
|---|---|---|---|
| WEB-1 | Media · histórico/pre-corrección | Desbordamiento horizontal en `/inventario` a 375 px en la versión probada | CSS del inventario actualizado en la rama actual; repetir D-25 en navegador real y medir overflow del documento frente al scroll interno de la tabla |
| WEB-2 | Media · histórico/pre-corrección | La versión probada aceptaba nombre de 2 caracteres | La rama actual requiere 3–80 en formulario/servidor; repetir D-23 en alta, edición y servicio, incluidos límites y no-mutación al rechazar |
| WEB-3 | Baja · histórico/pre-corrección | La versión probada no ofrecía retirada visible | La rama actual añade **Retirar** con motivo y evento auditable; repetir D-24 y, opcionalmente, D-26. Verificar que venta e inventario no desaparecen del historial |
| WEB-4 | Informativa · histórico | Chromium advirtió sobre el formulario de contraseña en `/respaldo` | La plantilla actual usa `autocomplete="new-password"` al crear/confirmar y `off` al restaurar; comprobar comportamiento/DOM en Chromium real sin exponer contraseñas. No es bloqueo de la campaña prioritaria |
