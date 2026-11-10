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

## En este entorno (checkout Arena, 2026-11-10)

Este contenedor no tiene `java` ni `swift`, `ANDROID_HOME`/`ANDROID_SDK_ROOT` están vacíos y no expone Android SDK ni Xcode; por tanto, `shalevTests`, `shalevCheck`, compilación Android, `swift test` y compilación iOS no se ejecutaron aquí. Las verificaciones de toolchain registradas en otras fechas/equipos no aplican a este contenedor.

Para esta rama se ejecutaron desde `DShalev/` `python -m py_compile dshalev/app.py dshalev/sync_server.py dshalev/database.py dshalev/inventory.py`, `pytest -q` y `npm test`; resultados y limitaciones actuales están en [docs/VERIFICACION.md](docs/VERIFICACION.md). Las carpetas `.venv/` y `node_modules/` se excluyen del snapshot: recrea el entorno con `python3 -m venv .venv`, `pip install -r requirements.txt`, instala `pytest` y `httpx`, y ejecuta `npm ci` antes de repetir las pruebas DShalev. La suite local/loopback no sustituye dispositivos ni redes reales.
