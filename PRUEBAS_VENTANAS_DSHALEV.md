# Pruebas de ventanas y componentes — DShalev (Web-Escritorio)

Fecha: 2026-11-10 · Servidor local: `DShalev.py` en `http://127.0.0.1:47812` (FastAPI/uvicorn, `/healthz` OK) · Sesión: usuario administrador `Manuel` · Herramienta: Playwright (Chromium), viewports 1280×800, 768×1024 y 375×667.

Alcance de pruebas por página: tema claro/oscuro, FontWeight, responsive, y Error Input en los formularios de cada ventana.

## Ventanas recorridas (todas las entradas del menú)

| # | Ventana | Ruta | Tema claro | Tema oscuro | h1/h2 | Scroll X |
|---|---|---|---|---|---|---|
| 1 | Inicio (resumen del negocio) | `/` | ✅ | ✅ | 700/700 | No |
| 2 | Inventario (catálogo + alta rápida) | `/inventario` | ✅ | ✅ | 700/700 | No (desktop) |
| 3 | Servicios | `/servicios` | ✅ | ✅ | 700/700 | No |
| 4 | Ventas (POS web: carrito + actividad) | `/ventas` | ✅ | ✅ | 700/700 | No |
| 5 | Turnos (caja y arqueo) | `/turnos` | ✅ | ✅ | 700/— | No |
| 6 | Reportes | `/reportes` | ✅ | ✅ | 700/700 | No |
| 7 | Recetas | `/recetas` | ✅ | ✅ | 700/700 | No |
| 8 | Herramientas (conversor CUP/USD, fichas de costo) | `/herramientas` | ✅ | ✅ | 700/700 | No |
| 9 | Clientes | `/clientes` | ✅ | ✅ | 700/700 | No |
| 10 | Equipo | `/equipo` | ✅ | ✅ | 700/700 | No |
| 11 | Acceso y red | `/red` | ✅ | ✅ | 700/700 | No |
| 12 | Respaldo y restauración | `/respaldo` | ✅ | ✅ | 700/700 | No |
| 13 | Licencia | `/licencia` | ✅ | ✅ | 700/— | No |

Sin errores de consola ni errores de página en las 13 navegaciones ×2 (claro y oscuro).

## Resultados por categoría

### Tema claro/oscuro — ✅ Correcto
- Selector **Tema** (Sistema/Claro/Oscuro) en la cabecera; el valor se persiste en el servidor (sobrevive la navegación completa a las 13 rutas).
- Claro: fondo `rgb(248,249,250)`, texto `rgb(26,28,30)`. Oscuro: fondo `rgb(15,19,24)`, texto `rgb(230,230,230)`; atributo `data-theme` correcto en todas las páginas.

### FontWeight — ✅ Correcto
- Body 400, h1 y h2 en 700 (verificado por estilos computados en las 13 páginas). Familia tipográfica consistente: `Inter` con fallback de sistema.

### Responsive — ⚠️ Funciona con 1 hallazgo
- **Móvil (375×667):** el sidebar se sustituye por una **barra inferior de navegación** (Inicio, Inventario, Servicios, Ventas, Turnos) y la cabecera se compacta — bien (captura `ds_movil_inventario.png`).
- **Hallazgo WEB-1 (media):** en `/inventario` a 375 px hay **desbordamiento horizontal** (`scrollWidth > clientWidth`): las columnas de la derecha (Historial/CSV/Excel) quedan parcialmente cortadas y la página se desplaza de lado. En `/ventas` y `/clientes` a 375 px no ocurre.
- Tablet (768×1024) y escritorio (1280×800): sin scroll horizontal en ninguna página.

### Error Input — ✅ Correcto (1 hallazgo)
| Formulario | Entrada inválida | Resultado |
|---|---|---|
| Alta rápida de artículo (`/inventario`) | Precio de venta `-5` (envío forzado sin validación HTML) | ✅ Rechazado por el servidor: *"Escribe un importe positivo con hasta dos decimales."* |
| Alta rápida de artículo | `costo` = `abc` | ✅ Bloqueado por HTML5 (`input[type=number]`); mensaje nativo "El valor debe ser mayor o igual que 0." |
| Alta rápida de artículo | Nombre de 2 caracteres `AB` | ⚠️ **WEB-2:** aceptado y guardado ("Artículo guardado."); el texto "De 3 a 160 caracteres" solo aparece junto al motivo |
| Cliente nuevo (`/clientes`) | Nombre vacío + teléfono `abc-notaphone` + email `no-es-email` | ✅ Rechazado por el servidor: *"Revisa el nombre, teléfono, correo y nota del cliente."* |
| Nuevo servicio (`/servicios`) | Precio `-5` | ✅ Rechazado por el servidor (mismo mensaje de importe positivo); no se creó el artículo |
| Calculadora de cambio (`/herramientas`) | Importe `no-es-numero`, tasa `0` | ✅ Mensaje: *"Introduce importes y tasas válidos, sin signos ni separadores de miles."* |
| Seguridad (transversal) | POST sin token CSRF (`/inventario/1/eliminar`) | ✅ **403 Forbidden** — el CSRF protege correctamente los formularios |
| POS web (`/ventas`) | — | Limitado: el inventario web estaba vacío para probar cantidad inválida en el carrito (el único artículo quedó tras la sesión anterior) |

## Estado final restaurado

- Tema en **oscuro** (el estado original de la sesión).
- Queda 1 artículo identificado como `Articulo de prueba QA` en el inventario web (creado durante las pruebas; la UI no ofrece botón de borrar/anular de artículo — ver WEB-3). Su motivo es "Prueba automatizada de calidad".
- El nombre `AB` fue renombrado; ningún servicio ni cliente de prueba quedó creado.
- Capturas en `%TEMP%\opencode\ds_*.png` (no se suben a git). Nota menor de accesibilidad del DOM en `/respaldo`: el formulario de contraseña no incluye campo de usuario (aviso de Chromium, no bloqueante).

## Resumen de hallazgos

| ID | Severidad | Hallazgo | Sugerencia |
|---|---|---|---|
| WEB-1 | Media | Desbordamiento horizontal en `/inventario` a 375 px | Revisar anchos mínimos de la tabla/acciones CSV-Excel; envolver o comprimir columnas en móvil |
| WEB-2 | Media | El nombre del artículo acepta 2 caracteres aunque la guía indica 3–160 | Añadir `minlength`/validación de servidor coherente con el texto de ayuda |
| WEB-3 | Baja | No hay forma visible de borrar o anular un artículo del inventario web | Ofrecer anulación con motivo (como la anulación de ventas) o aclarar en el manual |
| WEB-4 | Informativa | Aviso de Chromium sobre formulario de contraseña sin campo de usuario en `/respaldo` | Añadir username (oculto) o `autocomplete` adecuado |
