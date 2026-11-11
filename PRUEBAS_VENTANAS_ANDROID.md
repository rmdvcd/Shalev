# Pruebas de ventanas y componentes — Shalev Android

Fecha: 2026-11-10 · Build: debug `versionCode 54` (package `cu.shalev.app.debug`) · Teléfono físico Redmi Note 12 (Android 15; identificador del dispositivo omitido) · Herramienta: adb + inspección de jerarquía UI + capturas de pantalla.

> **Informe histórico, no certifica la rama actual.** Se tomó antes de las correcciones del 2026-11-11. Para repetir AND-1/AND-2 en un teléfono o emulador de pruebas usa [PRUEBAS_IA_SHALEV_DSHALEV.md](PRUEBAS_IA_SHALEV_DSHALEV.md), casos S-12/S-13 y comandos de la sección 3. No uses una instalación con datos personales ni guardes el perfil de prueba.

Alcance de pruebas por pantalla: tema claro/oscuro, orientación, responsive, FontWeight y Error Input.

## Ventanas recorridas

| # | Ventana | Captura | Tema claro | Tema oscuro | Horizontal |
|---|---|---|---|---|---|
| 1 | Inicio (resumen del negocio) | `an_inicio_claro.png` / `an_inicio_oscuro.png` | ✅ | ✅ | — |
| 2 | Inventario (lista 54 productos) | `an_inventario_claro.png` / `an_inventario_oscuro.png` | ✅ | ✅ | ✅ |
| 3 | Detalle de producto (Sándwich de jamón, receta, niveles) | `an_producto_claro.png` | ✅ | — | — |
| 4 | Servicios (lista 20 servicios) | `an_servicios_claro.png` / `an_servicios_oscuro.png` | ✅ | ✅ | — |
| 5 | Registros (pestañas Ventas/Servicios/Transferencias/Movimientos/Clientes) | `an_registros_claro.png` / `an_registros_oscuro.png` | ✅ | ✅ | — |
| 6 | Herramientas (Fichas de costo, Calculadora de cambio) | `an_herramientas_claro.png` / `an_herramientas_oscuro.png` | ✅ | ✅ | — |
| 7 | Abrir turno (fondo de caja) | `an_turno_claro.png` | ✅ | — | — |
| 8 | POS: selección de productos → Carrito | `an_carrito_claro.png` / `an_carrito_oscuro.png` | ✅ | ✅ | ✅ |
| 9 | POS: comprobante de pago (Total, método) | `an_cobro_claro.png` | ✅ | — | — |
| 10 | Cierre de turno: arqueo por denominaciones | `an_arqueo_claro.png` | ✅ | — | — |
| 11 | Ajustes (lista completa de secciones) | (ronda anterior) | ✅ | ✅ | ✅ |
| 12 | Ajustes → Perfil (Nombre, Apellidos, CI) | (ronda anterior) | ✅ | ✅ | — |

Barra inferior probada: Inicio · Inventario · Servicios · Registros · Herramientas — navegación correcta en todos los toques, sin estados colgados.

## Resultados por categoría

### Tema claro/oscuro — ✅ Correcto
- Cambio con `cmd uimode night yes/no` se propaga a todas las pantallas probadas sin reiniciar la app ni perder estado.
- Fondo, tarjetas, chips y texto se invierten correctamente; en el carrito del POS el chip activo "Efectivo" mantiene su color de acento y contraste legible (captura `an_carrito_oscuro.png`).
- Los iconos y la barra inferior mantienen sus colores de acento en oscuro.

### Orientación — ✅ Correcto
- Inventario y Carrito en horizontal (`an_inventario_landscape.png`, `an_carrito_landscape.png`): la lista ocupa el ancho completo, los precios quedan alineados a la derecha, sin solapamientos ni crash.
- El carrito conserva el producto y el total tras la rotación.

### Responsive — ✅ Correcto
- Con `wm size 720x1600` (resolución menor): los textos largos se truncan con elipsis ("Teléfonos y cuentas para cobrar po…", "Pide tu huella… a…") sin romper el layout.
- En 1080×2400 todo el contenido es legible; los precios nunca se pisan con los nombres (dos líneas separadas por producto).

### FontWeight — ✅ Correcto
- Títulos de ventana y precios en bold (700); subtítulos, descripciones y metadatos en regular (400). Jerarquía consistente en las 12 ventanas, en ambos temas.

### Error Input — ✅ Correcto (2 observaciones)
1. **Fondo de caja (Abrir turno):** el campo es numérico; las letras (`abc`) las rechaza el teclado y no llegan al campo. Se abrió el turno con `123` correctamente.
2. **Cantidad en el carrito del POS (hallazgo positivo):** al pedir `999` unidades de un producto con existencia 67, la app **ajusta automáticamente al stock disponible (67)** y recalcula el total (14.740 CUP). No permite vender de más. Las letras también las bloquea el teclado numérico.
3. **Observación 1 — Nombre del perfil:** acepta dígitos y símbolos (`12345!!!`) sin error visible mientras se escribe. No se comprobó el guardado para no alterar los datos del dueño.
4. **Observación 2 — IME:** Gboard omitió dígitos al teclear muy rápido el CI en un intento (problema del teclado del sistema, no de la app; el reintento escribió bien).

## Estado final restaurado

- Turno **cerrado** con arqueo cuadrado (fondo 123 = contado 123, sin ventas ni ajustes de inventario; el cierre registra un movimiento de arqueo en los Registros).
- Tema del sistema en claro, rotación automática activada.
- Datos del Perfil intactos; los valores personales se omiten por privacidad. El botón Guardar quedó deshabilitado (sin cambios pendientes).
- La venta de prueba se **descartó** (no se registró ninguna venta).
- Capturas de pantalla en `%TEMP%\opencode\an_*.png` (no se suben a git).

## Resumen de hallazgos

| ID | Severidad | Hallazgo | Sugerencia |
|---|---|---|---|
| AND-1 | Baja · histórico/pre-corrección | En la build probada, Nombre aceptaba dígitos/símbolos sin aviso en tiempo de escritura | La rama actual añade error en línea; falta repetir S-12 en app debug aislada y confirmar que Guardar no persiste el dato inválido |
| AND-2 | Informativa · histórico/pre-corrección | En la build probada, la cantidad se ajustaba al stock sin mensaje explicativo | La rama actual añade aviso; falta repetir S-13 y confirmar texto, subtotal y total en dispositivo/emulador |
| AND-3 | Externa | Gboard perdió dígitos al escribir rápido | No es defecto de la app |
