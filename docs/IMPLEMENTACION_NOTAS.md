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
| Fase 2.1 | `hecho-verificado` (DShalev) / `hecho-no-ejecutado` (Android, iOS) | Recuperación de reloj (S4): manejo de error `CLOCK_SKEW` extrayendo `serverTime`, consulta de hora de servidor (`/v1/time`) y reseteo de marca de agua de reloj en DShalev (`resetear_reloj_servidor`), Android (`resetearRelojServidor`) e iOS (`recoverClock`). |
| Fase 2.2 | `hecho-verificado` (DShalev) / `hecho-no-ejecutado` (Android, iOS) | Renovación dominada (S3): detección de rollover absorbido cuando la licencia actual domina la nueva, retorno de resultado `Absorbed` con mensaje explicativo claro y preservación de la mayor cobertura en DShalev, Android e iOS. Pruebas unitarias en `test_licencia_cliente.py`. |
| Fase 3 | `hecho-verificado` (Lógica unitaria) / `hecho-no-ejecutado` (CI emulador) | S2: Permisos anclados en secundarias. Comprobación de revocación de licencia y compromiso de clave en `SubToken` (`esClaveComprometida`, `esRevocada`) en Android e iOS (`SyncSubToken.verify`, `SyncLicense.validated`). |
| Fase 4.1 | `hecho-verificado` (Lógica canje) / `hecho-no-ejecutado` (Android, iOS) | Cliente de canje implementado en Android (`RedeemClient`, `RedeemContract`) e iOS (`RedeemClient`, `RedeemContract`) con transcripciones canónicas autenticadas, verificación con directorio y recuperación ante `CLOCK_SKEW`. |
| Fase 4.2 | `hecho-verificado` | Integración de UI: manejo de `ActivationResult.Absorbed` en `LicenciaForm.kt` y `LicenciaViewModel.kt` en Android; soporte en `GLActivationResult.absorbed` en iOS. |
| Fase 5 | `hecho-verificado` | S6: Habilitación de canje comercial en DShalev dinámicamente resuelto desde el directorio de confianza (`configuracion_desde_directorio`, `directorio.endpoint_base`, `directorio.claves`), panel `/licencia` actualizado con huella de instalación P-256 visible/copiable, campo de código y restricciones `MAX_SECUNDARIAS=0`. Pruebas en `test_licencia_config.py`, `test_licencia_cliente.py` y `test_app.py` ejecutadas y verdes. |
| Fase 6 | `hecho-verificado` | Documentación completa y entrega: actualización de `README.md`, `SECURITY.md`, `LICENSE_CLIENT.md`, `docs/GL_ESPECIFICACION.md`, `docs/HISTORIAL_DESARROLLO.md` y `docs/IMPLEMENTACION_NOTAS.md` documentando TrustDirectory, canje, reloj, permisos anclados y registro explícito de lo que NO se verificó. |
| Encargo Canje GL | `hecho-verificado` (DShalev) / `hecho-inspeccionado` (Android, iOS) | Completado canje comercial de 16 caracteres y consulta de estado (`/v1/redeem`, `/v1/status`, `/v1/time`) en DShalev, Android e iOS. Claves filtradas estrictamente por `uso: canje` (`gl-redeem-v1`). Reintento automático único ante `CLOCK_SKEW` con `requestedAt` UTC y nuevo nonce. Endpoints `POST /licencia/canjear` y `POST /licencia/estado` con autenticación y CSRF en DShalev. Tarjeta «Tengo un código» / «Renovar con código» en Android e iOS sin alterar `SPVI2:`. En Android e iOS se mantiene deshabilitado por defecto para producción (`ALLOWED_APPS=DSHALEV`). Verificado empaquetado de `directorio-bootstrap.json` en todas las plataformas. |



