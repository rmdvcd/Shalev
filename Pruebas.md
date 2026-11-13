# Orden de pruebas de Shalev (Android)

1. Leer `AGENTS.md`, `Contexto.md` y `Pendiente.md`. Revisar el estado medido en [docs/VERIFICACION.md](docs/VERIFICACION.md).
2. En un entorno con JDK 17 y Android SDK 35, ejecutar `./gradlew shalevTests`. Las pruebas JVM deben funcionar sin red; Robolectric usa el SDK preparado por Gradle.
3. Ejecutar `./gradlew shalevCheck`: incluye tests JVM, lint, compilación debug y revisión de permisos del manifiesto.
4. Ejecutar `./gradlew :app:recordRoborazziDebug` y revisar las capturas de referencia claro/oscuro cuando se modifique la UI.
5. Ejecutar `./gradlew shalevInstrumentedTests` en un dispositivo o emulador compatible para cubrir Room y pruebas instrumentadas.
6. Antes de publicar, seguir [PRUEBAS_DISPOSITIVO.md](PRUEBAS_DISPOSITIVO.md): probar venta, turnos, respaldos, permisos, actualización y sincronización principal/secundaria en teléfonos reales.
7. Verificar migración por QR entre dos teléfonos Android. Si cambia el cliente de licencias, completar también una prueba de extremo a extremo con GL real y preservar la compatibilidad con licencias heredadas instaladas.
8. Para una publicación, seguir [RELEASE.md](RELEASE.md): firmar con el keystore de release, verificar el APK/AAB y completar la prueba de humo.
9. Anotar en `docs/VERIFICACION.md` el SHA, comandos ejecutados y resultados observados. Diferenciar explícitamente pruebas que pasaron, fallaron, no se pudieron ejecutar o están pendientes.
10. Para las repruebas de Perfil/cantidad Android y de nombres/retiro/responsive DShalev de la rama actual, seguir [PRUEBAS_IA_SHALEV_DSHALEV.md](PRUEBAS_IA_SHALEV_DSHALEV.md). `PRUEBAS_VENTANAS_ANDROID.md` y `PRUEBAS_VENTANAS_DSHALEV.md` contienen líneas base históricas, no validan los cambios posteriores.

## Regresiones de la auditoría Android — 2026-11-13

Consultar el SHA y resultados en [docs/VERIFICACION.md](docs/VERIFICACION.md), sin reutilizar resultados históricos de otra rama. La matriz de aceptación está en [docs/AUDITORIA_SHALEV_2026-11.md](docs/AUDITORIA_SHALEV_2026-11.md). El emulador de CI se activa con `workflow_dispatch` o añadiendo `[instrumentados]` al mensaje del commit publicado; no necesita ampliar permisos de Actions.

- `AuditoriaIntegridadTest`, `MoneyTest`, formularios y escritores: centavos/signo, costo adicional, cotización cambiada antes del cobro/commit y modificación, desbordamientos, conversiones y reglas de recepción/motivos.
- `BandejaComandosTest`: pérdida de respuesta, reinicio, misma clave, cancelación, rechazo, acción diferente y respuesta tardía de otra vinculación.
- `InventarioOperativoRoomTest`: recepción/promedio/idempotencia/rollback, FEFO/anulación/devolución, merma/revisiones, deuda offline, costos completos y unidades/overflow.
- `LocalesPermisosCobroRoomTest`: aislamiento de locales, renombrado/turno remoto, stock sin permiso de precio, referencias duplicadas locales y conflictos offline.
- `AuditoriaDatosRoomTest`: migración de fichas/colisiones/reaparición, exportación atómica, costo vigente/conversión, respaldo/reset completo, fotos/hash/limpieza global y cancelación antes/después del commit.
- `EsquemaTest`: abrir JSON generado y migraciones históricas hasta v18; 17→18 conserva datos y atribuye local estable al empleado.
- Reintentos de ViewModels: mismo filtro y selección después de error, Fichas mantiene edición y puede reabrir su Flow.

**Manual en teléfonos antes de publicar:** crear dos locales y dos empleados, vender/cambiar precios/recetas durante un cobro, desconectar tras enviar un comando y reconectar/reiniciar; anular/modificar una venta con lotes; importar copia con fotos en otro teléfono; actualizar con operaciones posteriores a crear la copia previa; confirmar que esta no las sobrescribe. Revisar nuevo formulario en claro/oscuro, teclado, TalkBack y pantallas pequeñas. Estas comprobaciones no se declaran hechas solo por ejecutar CI.

## En este entorno (checkout Arena, 2026-11-11)

Este contenedor no tiene `java`/JDK, Android SDK ni Xcode; por tanto, `shalevTests`, `shalevCheck`, compilación/instrumentados Android y `swift test` no se ejecutaron aquí. La IA en el PC real debe comprobar su propio toolchain; los resultados de otros equipos no aplican automáticamente.

En esta revisión se ejecutó desde `DShalev/` la suite Python (`.venv/bin/python -m pytest -q`: **201 passed**, un aviso de deprecación) y `compileall`; `git diff --check` pasó. No consta ejecución en esta revisión de `npm test`, del EXE Windows ni de una prueba visual con navegador operativo. El intento de iniciar Chromium portátil en Arena quedó bloqueado por `libnspr4.so`/`libnss3.so`; sigue pendiente validar el layout real. Las carpetas `.venv/` y `node_modules/` se excluyen del snapshot: recrea el entorno con `python3 -m venv .venv`, `.venv/bin/python -m pip install -r requirements-dev.txt` (Windows: `py -3.11 -m venv .venv` y `.venv\Scripts\python.exe -m pip install -r requirements-dev.txt`) y `npm ci` desde `DShalev/`. La suite local/loopback no sustituye Android físico, navegador real, Windows EXE ni redes/dispositivos reales.
