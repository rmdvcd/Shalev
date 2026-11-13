# Pruebas de ventanas y componentes — DShalev (web-escritorio)

Ejecutado el 2026-11-10 contra el servidor local `DShalev.py` en `http://127.0.0.1:47812` (FastAPI sobre uvicorn, `/healthz` respondiendo). La sesión usó un administrador de pruebas creado para la ocasión; su nombre no se reproduce. Instrumento: Playwright con Chromium, viewports de 1280×800, 768×1024 y 375×667.

> **Informe histórico, no certifica la rama actual.** Se tomó antes de las correcciones del 2026-11-11. El overflow móvil, el límite de nombres y la retirada auditable requieren repetición real en navegador y PC: sigue [PRUEBAS_IA_SHALEV_DSHALEV.md](PRUEBAS_IA_SHALEV_DSHALEV.md), casos D-23, D-24 y D-25, y D-26 si hay un Android secundario de prueba. Usa una raíz temporal nueva; no reutilices la sesión ni el artículo de prueba que quedaron del informe anterior.

Se recorrieron las trece entradas del menú: Inicio, Inventario, Servicios, Ventas, Turnos, Reportes, Recetas, Herramientas, Clientes, Equipo, Acceso y red, Respaldo y Licencia. En cada una se cambió el tema, se midieron los pesos tipográficos, se probó el ancho de ventana y se enviaron datos inválidos a los formularios disponibles. Las trece rutas, en claro y en oscuro, no generaron errores de consola ni de página.

## Tema claro/oscuro

El selector Tema (Sistema, Claro, Oscuro) vive en la cabecera y su valor se guarda en el servidor, así que sobrevive a la navegación por las trece rutas. En claro el fondo es `rgb(248,249,250)` con texto `rgb(26,28,30)`; en oscuro, `rgb(15,19,24)` con texto `rgb(230,230,230)`. El atributo `data-theme` aparece correcto en todas las páginas.

## FontWeight

El body usa 400 y los encabezados h1 y h2 usan 700, comprobado con estilos computados en las trece páginas. En Turnos y Licencia no hay h2, así que solo se mide el h1. La familia es Inter, con la pila del sistema como respaldo.

## Responsive

A 375 px el sidebar se convierte en una barra inferior (Inicio, Inventario, Servicios, Ventas, Turnos) y la cabecera se compacta (`ds_movil_inventario.png`). En tablet y escritorio no aparece scroll horizontal en ninguna página.

Queda un defecto en `/inventario` a 375 px: el documento es más ancho que la ventana (`scrollWidth > clientWidth`), las columnas de la derecha (Historial, CSV, Excel) se cortan y la página se desplaza de lado. `/ventas` y `/clientes` a ese ancho no lo reproducen.

## Error Input

El precio negativo se rechaza dos veces. HTML5 lo bloquea con "El valor debe ser mayor o igual que 0." y, al forzar el envío, el servidor responde *"Escribe un importe positivo con hasta dos decimales."*. Un costo con letras no se puede escribir porque el campo es `number`. Un servicio con precio `-5` corrió la misma suerte y no se creó.

El cliente nuevo con nombre vacío, teléfono `abc-notaphone` y correo `no-es-email` fue rechazado con *"Revisa el nombre, teléfono, correo y nota del cliente."*. La calculadora de cambio, con importe `no-es-numero` y tasa 0, respondió *"Introduce importes y tasas válidos, sin signos ni separadores de miles."*.

Un POST sin token CSRF a `/inventario/1/eliminar` devolvió 403, que confirma que los formularios están protegidos.

La prueba del POS quedó limitada: el inventario web estaba vacío y no había artículos para forzar una cantidad inválida en el carrito.

Sí apareció un hueco de validación. Un artículo con nombre de dos caracteres (`AB`) se guardó con el aviso "Artículo guardado.". El texto "De 3 a 160 caracteres" acompaña solo al motivo, no al nombre.

## Estado al terminar

El tema quedó en oscuro, que era el estado original de la sesión. En aquella instancia permaneció un artículo ficticio, `Articulo de prueba QA`, con el motivo "Prueba automatizada de calidad"; no se limpió y no conviene reutilizar esa instalación. El nombre `AB` se renombró y no se creó ningún servicio ni cliente de prueba. Chromium dejó además un aviso menor en `/respaldo`: el formulario de contraseña no incluye campo de usuario. No bloquea nada. Las capturas están en `%TEMP%\opencode\ds_*.png` y no se suben a git.

## Hallazgos

**WEB-1 (media, histórico).** A 375 px, `/inventario` desborda horizontalmente en la versión probada. El CSS del inventario cambió en la rama actual; falta repetir D-25 en un navegador real y medir el overflow del documento frente al scroll interno de la tabla.

**WEB-2 (media, histórico).** La versión probada aceptaba nombres de dos caracteres. La rama actual exige de 3 a 80 en formulario y servidor; falta repetir D-23 en alta, edición y servicio, incluidos los límites y la no mutación al rechazar.

**WEB-3 (baja, histórico).** La versión probada no ofrecía retirada visible. La rama actual añade **Retirar** con motivo y evento auditable; falta repetir D-24, y D-26 si se puede, y verificar que venta e inventario no desaparecen del historial.

**WEB-4 (informativa).** Chromium advirtió sobre el formulario de contraseña de `/respaldo`. La plantilla actual usa `autocomplete="new-password"` al crear y confirmar, y `off` al restaurar; conviene comprobar el DOM en Chromium real sin exponer contraseñas.
