# Informe de auditoría — Shalev Android

**Fecha:** 13/11/2026 · **Versión de trabajo:** 1.0.1/54
**Rama:** `arena/5d323d0e-shalev` · **Base:** `edde212323332cc97781fcf220c98a67641627b6`
**Estado:** análisis concluido, correcciones implementadas; cierre de regresiones UI en curso.

> Este informe sustituye al del 04/11/2026. No se reutilizan su prueba en teléfono ni sus cifras como
> evidencia de este trabajo. Su instrucción de usar una firma distinta era incorrecta: la referencia
> vigente es **RELEASE.md**, `gl-release.jks`, alias `gl`, huella `13:9D:…:17:38`. No cambiar la clave.

## Qué se corrigió

1. **Dinero y cobro:** centavos/signo en edición, calculadora y Excel; sumas fuera de rango detectadas;
   cotización y consumos revalidados en la transacción, sin alterar precios/costos históricos.
   Referencia de transferencia normalizada y duplicados locales rechazados; conflictos offline
   conservados y señalados, no ventas descartadas ni una falsa confirmación bancaria por SMS.
2. **Locales y autorización:** identidad estable al renombrar, empleado ligado a su local y no al selector
   de la principal, bloqueo de cambio con turnos propios/remotos abiertos. Guardar un artículo no elude
   el permiso de cambiar precios. Una ausencia se distingue de un recurso de otro local.
3. **Fichas de costo:** persistencia Room por local, migración legada idempotente, respaldo/reset completos;
   exportación atómica a insumos/receta/artículo, conversiones exactas y costo adicional de trabajo/otros.
4. **Respaldo y actualización:** fotos verificadas/acotadas, locales, fichas, lotes, historial y preferencias
   en DTO v5; datos+preferencias en un commit, archivos preparados antes. Copia previa conservada y
   exportable, **nunca autoimportada** sobre operaciones nuevas. Protección con contraseña **encendida por
   defecto**; el modo sin contraseña exige opt-out y no ofrece confidencialidad real.
5. **Sincronización y recuperación:** bandeja durable de comandos inciertos, misma clave tras reconectar o
   reiniciar, mutex/compare-and-set y recuperación al conectar. Fallar el refresco no convierte un éxito
   confirmado en una operación que se pueda repetir como nueva. Reintentos conservan filtro y edición.
6. **Inventario operativo:** recepción con proveedor, comprobante textual, costo/promedio, motivo y autor;
   lotes/caducidades y FEFO contable; devolución de lotes al anular/corregir, ajustes append-only, límites
   e idempotencia. La venta de insumos sigue siendo **entera por decisión del dueño**; recetas y recepción
   admiten milésimas. No se inventa escaneo de lote físico ni comprobantes adjuntos.
7. **Indicadores y verificación:** ganancia **bruta**, distribución por valor al costo CUP; Room v18 con
   migración y esquema KSP; pruebas de regresión JVM/Room/UI, versiones Compose alineadas y emulador que
   exige resultados XML reales de app y data. Parser de firma actualizado sin cambiar la huella vigente.

La matriz completa con archivos, prioridades y regresiones está en
[docs/AUDITORIA_SHALEV_2026-11.md](docs/AUDITORIA_SHALEV_2026-11.md).

## Resultados observados (no mezclar SHA)

Última corrida concluida: **`808caf0b15e31234da852ccfe1494651fe04556b`**, [CI 43347828403](https://github.com/rmdvcd/Shalev/actions/runs/43347828403).

- **988 pruebas JVM**, sin fallos, errores ni omisiones; instrumentados compilan.
- **100 pruebas data en emulador**, sin fallos ni omisiones; incluye Room y migraciones.
- **104 pruebas UI/integración**, **10 fallos** y 0 omitidas: no se declara todavía aprobado el cierre UI.
- Lint: **0 errores, 102 avisos**; permisos: OK; API 26: **4016 clases, 0 llamadas prohibidas**.
- APK debug/release y R8: OK, sin clases ausentes. El release de CI está **sin firmar**, no es instalable
  como actualización de producción.

Las correcciones posteriores a esa corrida resuelven el redondeo visual de la calculadora y los fixtures
UI fallidos (protección por defecto, pie fijo del QR, cabecera del acordeón y selectores/validación). Su
nueva ejecución está pendiente. En local pasaron **14 pruebas Python** de herramientas, `bash -n` y
`git diff --check`; son fixtures de XML/apksigner, no sustituyen Android ni una firma real. El manual MD/PDF
fue actualizado; el PDF regenerado tiene 32 páginas y 66 marcadores válidos.

Evidencia y comandos por SHA: [docs/VERIFICACION.md](docs/VERIFICACION.md).

## Qué falta para distribuir

- Concluir en verde la nueva corrida automatizada, sin quitar aserciones ni checks para ocultar fallos.
- En equipo con la clave custodiada, generar/verificar el release firmado y actualizar una instalación
  de prueba v1.0 sin desinstalar ni perder datos/licencia.
- Revisión visual, TalkBack/letra grande, recuperación con GL y pruebas de principal/secundaria en dos
  teléfonos y redes reales. **No se han hecho en esta sesión.**

No se publicó una release, no se pidieron secretos ni se cambió la firma de producción. Los procedimientos
seguros están en [Pruebas.md](Pruebas.md), [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md) y
[RELEASE.md](RELEASE.md). Un emulador no valida teléfonos físicos ni significa «cero errores posibles».
