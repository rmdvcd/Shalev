# Bitácora de Implementación — `rmdvcd/Shalev`

Este documento registra las decisiones, estado de verificación por fase, discrepancias encontradas y notas de avance según la especificación de `IMPLEMENTACION_SHALEV.md`.

---

## 1. Registro de Baseline (Fase 0)

- **Fecha:** 2026-11-21
- **Rama activa:** `arena/999b4e57-shalev` (derivada de commit `f0bfbbae9d48200c910e49cdceccb58090d67ebb` de `main`)
- **Entorno de ejecución:** Linux x86_64, Python 3.11.
- **Herramientas disponibles:**
  - Python 3.11 con `cryptography 44.0.3`, `fastapi`, `uvicorn`, `pytest 9.2.0`, `httpx 0.28.1`.
  - JDK / Android SDK: No instalado en el contenedor del sandbox (`java: command not found`).
  - Xcode / Swift: No disponible en entorno Linux.
- **Resultados del Baseline:**
  - `python3 -m unittest discover -s tools/verificacion/tests -p 'test_*.py'`: **24/24 PASSED** (0.488s).
  - `python3 tools/verificacion/botones.py`: **0 incumplimientos** (7 textos calculados en ejecución).
  - `pytest Web-Desktop/tests/ -q`: **215/215 PASSED** (20.29s).
  - `python3 tools/licencia/probar_gl.py autoprueba`: **AUTOPRUEBA CORRECTA** (flujo completo SPVIR1/SPVI2 con renovación y revocación).

---

## 2. Decisiones y constantes de compilación (Sección 2 y Regla 7)

1. **`appId` por plataforma:**
   - DShalev (Web-Desktop): `DSHALEV` (por defecto).
   - Android: `SHALEV-ANDROID` (o `MIPVENTAX-ANDROID` según marca; valor por defecto `SHALEV-ANDROID`).
   - iOS: `SHALEV-IOS` (o `MIPVENTAX-IOS`; valor por defecto `SHALEV-IOS`).
2. **Canje en Android e iOS:** Sí, se implementa como vía paralela conservando `SPVI2`.
3. **Prevalencia de licencias:** Gana la de mayor vencimiento; perpetua gana siempre.
4. **`MAX_SECUNDARIAS` del servicio:** Inicialmente 0 hasta que las primarias emitan sub-tokens de canje (Fase 3).
5. **Entradas del propietario (Regla 7):**
   - `RAIZ_A_SPKI`: Marcador de desarrollo / pruebas.
   - `RAIZ_B_SPKI`: Marcador de desarrollo / pruebas.
   - `REDEEM_BASE_URL`: Marcador de desarrollo / pruebas.
   - `GITHUB_REPO_SLUG`: Marcador (`rmdvcd/Shalev`).
   - Las pruebas utilizan raíces y endpoints inyectables.

---

## 3. Registro de Tareas y Discrepancias

| Fase.Tarea | Estado | Notas |
|---|---|---|
| Fase 0 | `hecho-verificado` | Baseline ejecutado y documentado; bitácora creada. |
| Fase 1.1 | `hecho-verificado` (DShalev) / `hecho-no-ejecutado` (Android, iOS) | Núcleo de TrustDirectory implementado en DShalev (`directorio.py`), Android (`TrustDirectory.kt`) e iOS (`TrustDirectory.swift`). Envoltorios `LicenseTrust` y `GLTrust` delegando al directorio efectivo. |
| Fase 1.2 | `hecho-verificado` | Vector `docs/vectores/directorio-v1.json` generado y documentado en `docs/vectores/README.md`. Bootstrap inicial v1 incrustado en DShalev, Android e iOS. Pruebas unitarias completas en `test_licencia_directorio.py` (15/15 passed). |
| Fase 1.3 | `hecho-verificado` (DShalev) / `hecho-no-ejecutado` (Android, iOS) | Gestores de descarga y caché local (`GestorDirectorio`, `TrustDirectoryManager`) con verificación en cada arranque, límite 64 KiB, solo HTTPS github.com y anti-retroceso atómico. |
| Fase 1.4 | `hecho-verificado` (DShalev) / `hecho-no-ejecutado` (Android, iOS) | Pruebas obligatorias de validación, anti-retroceso, firmas alteradas, huellas y claves comprometidas implementadas y verificadas (17/17 passed). |
| Fase 1.5 | `hecho-verificado` | S5: Literal `GLMK-PERM-MAXS-9999` eliminado de `src/main`; movido a `src/debug` y `src/test`. Unificada la verdad de depuración (`BuildConfig.DEBUG` / `ModoDesarrollo.esDebug`). Añadida prueba `test_clave_maestra_fuera_de_main.py` en `tools/verificacion`. |
