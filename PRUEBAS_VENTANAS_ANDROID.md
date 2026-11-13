# Pruebas de ventanas y componentes — Shalev Android

Ejecutado el 2026-11-10 sobre la build debug `versionCode 54` (paquete `cu.shalev.app.debug`), en un Redmi Note 12 con Android 15. El dispositivo se identifica solo por su modelo; el identificador físico se omite. Instrumentos: adb, la jerarquía de accesibilidad de la UI y capturas de pantalla.

> **Informe histórico, no certifica la rama actual.** Se tomó antes de las correcciones del 2026-11-11. Para repetir AND-1 y AND-2 en un teléfono o emulador de pruebas usa [PRUEBAS_IA_SHALEV_DSHALEV.md](PRUEBAS_IA_SHALEV_DSHALEV.md), casos S-12 y S-13 con los comandos de la sección 3. No uses una instalación con datos personales ni guardes el perfil de prueba.

Cada ventana se revisó con tema claro y oscuro, en vertical y horizontal, a resolución completa y reducida. También se comprobó la jerarquía tipográfica y se intentaron entradas inválidas en los formularios.

## Cobertura

| # | Ventana | Captura | Claro | Oscuro | Horizontal |
|---|---|---|---|---|---|
| 1 | Inicio (resumen del negocio) | `an_inicio_claro.png` / `an_inicio_oscuro.png` | sí | sí | — |
| 2 | Inventario (54 productos) | `an_inventario_claro.png` / `an_inventario_oscuro.png` | sí | sí | sí |
| 3 | Detalle de producto (Sándwich de jamón: receta y niveles) | `an_producto_claro.png` | sí | — | — |
| 4 | Servicios (20 servicios) | `an_servicios_claro.png` / `an_servicios_oscuro.png` | sí | sí | — |
| 5 | Registros (Ventas, Servicios, Transferencias, Movimientos, Clientes) | `an_registros_claro.png` / `an_registros_oscuro.png` | sí | sí | — |
| 6 | Herramientas (Fichas de costo, Calculadora de cambio) | `an_herramientas_claro.png` / `an_herramientas_oscuro.png` | sí | sí | — |
| 7 | Abrir turno (fondo de caja) | `an_turno_claro.png` | sí | — | — |
| 8 | POS: selección de productos → carrito | `an_carrito_claro.png` / `an_carrito_oscuro.png` | sí | sí | sí |
| 9 | POS: comprobante de pago (total y método) | `an_cobro_claro.png` | sí | — | — |
| 10 | Cierre de turno: arqueo por denominaciones | `an_arqueo_claro.png` | sí | — | — |
| 11 | Ajustes (lista completa de secciones) | ronda anterior | sí | sí | sí |
| 12 | Ajustes → Perfil (Nombre, Apellidos, CI) | ronda anterior | sí | sí | — |

La barra inferior (Inicio · Inventario · Servicios · Registros · Herramientas) respondió a todos los toques sin dejar estados a medias. Las capturas se guardan como `an_*.png` en `%TEMP%\opencode\` y no se suben a git.

## Tema claro/oscuro

El cambio con `cmd uimode night yes/no` llega a todas las pantallas sin reiniciar la app y sin perder el estado de navegación. Fondos, tarjetas, chips y texto se invierten; en el carrito del POS el chip "Efectivo" conserva su color de acento y sigue siendo legible sobre el fondo oscuro (`an_carrito_oscuro.png`). Iconos y barra inferior mantienen el acento en ambos temas.

## Orientación

Inventario y carrito en horizontal (`an_inventario_landscape.png`, `an_carrito_landscape.png`) reacomodan la lista al ancho completo y alinean los precios a la derecha, sin solapamientos ni cierres inesperados. El carrito conserva el producto y el total tras la rotación.

## Responsive

Con `wm size 720x1600` los textos largos se cortan con elipsis ("Teléfonos y cuentas para cobrar po…", "Pide tu huella… a…") y el resto del layout aguanta. A 1080×2400 el contenido se lee completo; nombre y precio van en líneas separadas, de modo que nunca se pisan.

## FontWeight

Títulos de ventana y precios usan bold (700); subtítulos, descripciones y metadatos usan regular (400). La jerarquía se mantiene en las doce ventanas y en ambos temas.

## Error Input

El campo de fondo de caja en "Abrir turno" es numérico: las letras no llegan a escribirse. El turno se abrió con 123.

La cantidad del carrito se corrige sola. Al pedir 999 unidades de un producto con existencia 67, la app la ajusta a 67 y recalcula el total en 14.740 CUP; no deja vender de más. El teclado también bloquea las letras en ese campo.

Dos observaciones quedaron abiertas. La primera: el Nombre del perfil acepta dígitos y símbolos (`12345!!!`) sin aviso mientras se escribe; no se probó el guardado para no alterar los datos del dueño. La segunda es del teclado, no de la app: Gboard omitió dígitos al escribir el CI muy rápido y el reintento salió bien.

## Estado al terminar

El turno quedó cerrado con arqueo cuadrado: fondo 123 y contado 123, sin ventas ni ajustes de inventario. El cierre deja un movimiento de arqueo en Registros. El teléfono volvió al tema claro con rotación automática. El Perfil conserva sus datos; los valores personales no se reproducen aquí y el botón Guardar aparece deshabilitado porque no hay cambios pendientes. La venta de prueba se descartó, así que no se registró ninguna.

## Hallazgos

**AND-1 (baja, histórico).** En la build probada, Nombre aceptaba dígitos y símbolos sin error en línea. La rama actual añade ese error; falta repetir S-12 en una app debug aislada y confirmar que Guardar no persiste el dato inválido.

**AND-2 (informativa, histórica).** La cantidad se ajustaba al stock sin explicar por qué. La rama actual añade un aviso; falta repetir S-13 y comprobar el texto, el subtotal y el total en un dispositivo o emulador.

**AND-3 (externa).** Gboard perdió dígitos al escribir rápido. No es un defecto de la app.
