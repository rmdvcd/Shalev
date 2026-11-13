# Auditoría y correcciones de Shalev Android — noviembre de 2026

Alcance solicitado: completar el diagnóstico e implementar las correcciones sin reescribir la app ni modificar DShalev/IShalev.

## Matriz de trabajo

| Bloque | Hallazgo y criterio de aceptación | Estado |
|---|---|---|
| Dinero | Edición y exportación conservan centavos y signo; pérdidas visibles. | En implementación |
| Cobro | Comparar cotización aceptada con la vigente; rechazar referencias ya usadas en ventas válidas; no perder ventas offline en caso de conflicto. | En implementación |
| Locales | Identidad inmutable, atribución al origen, validación de artículos/recetas y bloqueo de cambio con turnos remotos. | En implementación |
| Fotos | Limpieza considera todo el negocio; respaldo incluye archivos y remapea rutas. | En implementación |
| Respaldo/actualización | No restaurar automáticamente una copia vieja sobre una migración correcta; restauración atómica de datos y preferencias. | En implementación |
| Permisos | Validar cambios de precio y reglas completas en la principal, no solo el tipo de comando. | En implementación |
| Fichas de costo | Persistencia cifrada, respaldo/borrado, exportación transaccional, unidades compatibles y costo completo. | En implementación |
| Sincronización | Reintento durable de un comando incierto con la misma clave y aviso de catálogo pendiente. | En implementación |
| Reactividad | Reintentar el mismo filtro después de un error; indicadores reaccionan a ventas de servicios y cambios del catálogo. | En implementación |
| Inventario | Recepción con proveedor/costo/comprobante, lotes/caducidades, motivos y bitácora inmutable de correcciones. | En implementación |
| Indicadores | «Ganancia bruta» y distribución por valor monetario, sin sumar unidades incompatibles. | En implementación |

## Decisiones que se preservan

- Las ventas por debajo del costo siguen permitidas con advertencia; no se elimina esta capacidad.
- La conciliación de ventas offline puede producir existencia negativa, con aviso y revisión del conteo: no es un error de transacción ni se deben descartar ventas reales.
- Los preajustes no usan importe mínimo desde v14.
- Los insumos se venden en unidades enteras de su medida por decisión previa del dueño; las recetas y recepciones sí admiten milésimas. No se cambia silenciosamente el significado de las cantidades históricas para introducir venta fraccionaria.
- No se añaden permisos Android, telemetría, nube ni servicios de fondo nuevos.

## Verificación

No confundir los resultados históricos de otros documentos ni el CI de la base con pruebas de estos cambios. Registrar aquí los comandos y resultados finales al completar la implementación. Este entorno no dispone de SDK Android; la verificación remota de la rama se usará cuando corresponda. Las pruebas físicas de instalación/actualización y sincronización entre teléfonos siguen siendo una comprobación separada.
