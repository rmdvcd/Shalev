# Shalev para Android

Esta carpeta es la raíz independiente del proyecto Android nativo. El proyecto Gradle, su wrapper y todos sus módulos están juntos aquí; no depende de las carpetas de iOS ni de Web-Desktop.

## Módulos

- `app/`: aplicación Android (Compose, pantallas y navegación).
- `core/`, `domain/`, `data/`, `designsystem/` y `licencia/`: módulos de dominio, datos, interfaz y licencia.
- `play_store/`: recursos y ficha de Google Play.
- `pruebas/`: archivos de prueba del flujo de escaneo de inventario.

## Compilar y probar

Desde esta carpeta:

```bash
./gradlew shalevTests
./gradlew :app:assembleDebug
./gradlew shalevCheck
```

También se puede invocar el wrapper de conveniencia `./gradlew` desde la raíz del repositorio. La documentación general está en `../README.md`; instrucciones de release y pruebas están en `../RELEASE.md`, `../Pruebas.md` y `../CAPTURAS.md`.
