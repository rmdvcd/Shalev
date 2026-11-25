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
| Fase 3 | `corregido-verificado` (D1 resuelto: anclaje GL §3.1/§3.2 y fail-closed sin permiso) | S2: Permisos anclados en secundarias. VerificadorPermisoSecundaria corregido para exigir anclaje estricto de la clave pública a la licencia firmada por GL (§3.1 canje con devicePublicKey y §3.2 SPVI2 con okm divulgado y huella de primaria) y rechazar mensajes sin permiso o con clave ajena (D1). |
| Fase 4.1 | `hecho-verificado` (Lógica canje) / `hecho-no-ejecutado` (Android, iOS) | Cliente de canje implementado en Android (`RedeemClient`, `RedeemContract`) e iOS (`RedeemClient`, `RedeemContract`) con transcripciones canónicas autenticadas, verificación con directorio y recuperación ante `CLOCK_SKEW`. |
| Fase 4.2 | `hecho-verificado` | Integración de UI: manejo de `ActivationResult.Absorbed` en `LicenciaForm.kt` y `LicenciaViewModel.kt` en Android; soporte en `GLActivationResult.absorbed` en iOS. |
| Fase 5 | `hecho-verificado` | S6: Habilitación de canje comercial en DShalev dinámicamente resuelto desde el directorio de confianza (`configuracion_desde_directorio`, `directorio.endpoint_base`, `directorio.claves`), panel `/licencia` actualizado con huella de instalación P-256 visible/copiable, campo de código y restricciones `MAX_SECUNDARIAS=0`. Pruebas en `test_licencia_config.py`, `test_licencia_cliente.py` y `test_app.py` ejecutadas y verdes. |
| Fase 6 | `hecho-verificado` | Documentación completa y entrega: actualización de `README.md`, `SECURITY.md`, `LICENSE_CLIENT.md`, `docs/GL_ESPECIFICACION.md`, `docs/HISTORIAL_DESARROLLO.md` y `docs/IMPLEMENTACION_NOTAS.md` documentando TrustDirectory, canje, reloj, permisos anclados y registro explícito de lo que NO se verificó. |
| Encargo Canje GL | `hecho-verificado` (DShalev) / `hecho-inspeccionado` (Android, iOS) | Completado canje comercial de 16 caracteres y consulta de estado (`/v1/redeem`, `/v1/status`, `/v1/time`) en DShalev, Android e iOS. Claves filtradas estrictamente por `uso: canje` (`gl-redeem-v1`). Reintento automático único ante `CLOCK_SKEW` con `requestedAt` UTC y nuevo nonce. Endpoints `POST /licencia/canjear` y `POST /licencia/estado` con autenticación y CSRF en DShalev. Tarjeta «Tengo un código» / «Renovar con código» en Android e iOS sin alterar `SPVI2:`. En Android e iOS se mantiene deshabilitado por defecto para producción (`ALLOWED_APPS=DSHALEV`). Verificado empaquetado de `directorio-bootstrap.json` en todas las plataformas. |
| Encargo Complementario T1 | `hecho-verificado` | Ventana de frescura ampliada de 72 h a 7 días. Constante única `AVISO_FRESCURA = "Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."` en juego básico GSM-7 de 7 bits, unificada en `MensajesLicencia` (Android), `GLLicenseMessage` (iOS) y `solicitud.py` (DShalev) para solicitud inicial, ampliación y recuperación. Pruebas unitarias en las tres plataformas. |
| Encargo Complementario T2 | `hecho-verificado` | Instrucciones de pago en la app y tarjeta «Cómo pagar» antes del botón de envío. Fuente única de cobro por plataforma (`PaymentInstructions.kt`, `PaymentInstructions.swift`, `pago.py`) con advertencia de no exponer datos reales en repositorios públicos. Cálculo dinámico de precio según tipo y secundarias (o diferencia al ampliar; oculta en recuperación) según `docs/vectores/precios.json`. Aviso obligatorio «Paga solo a los datos que muestra esta pantalla». Pruebas en `test_licencia_pago.py` y comprobación de plantillas HTML con `ramplint`. |
| Encargo Complementario T3 | `hecho-verificado` | Campo opcional de N.º de transferencia (`^[A-Za-z0-9-]{6,30}$` desde 2026-11-25; nació con 4–30) con botón de pegado y extracción de referencia bancaria. Texto visible incluye `Transferencia: <tx>` antes del aviso de frescura. `RequestPayload` v=2 modela el campo opcional `tx`, protegiendo el cifrado `SPVIR1` mediante la bandera `ENVIAR_TX_EN_SOLICITUD = false`. Pruebas en Android (`SolicitudTransferenciaTest.kt`), iOS (`LicenseContractTests.swift`) y DShalev (`test_licencia_spvi.py`). Regla de botones (`botones.py`) respetada en Android (`etiqueta = "Pegar"`). |
| Verificación de paridad (2026-11-24) | `hecho-verificado` (DShalev, pruebas ejecutadas) / `hecho-no-ejecutado` (Android, iOS: sin Gradle ni Xcode) | Cierre de la paridad tras PR #153. El canje estaba inerte en las tres plataformas porque nadie cargaba el directorio al arrancar y ningún directorio publicaba `canje`: se añadió la carga en `ShalevApplication.onCreate` (Android), `IShalevApp.init` (iOS) y `create_app.lifespan` (DShalev, vía `registrar_directorio_efectivo`), una regla única de apertura (`RedeemContract.canjeActivo`, `RedeemClient.make`, `comercial_habilitada`) y se corrigió la memorización del cliente cerrado en `LicenciaRepositoryImpl`. C1 (GSM-7) se alineó en Android (`Carne de identidad`, `Telefono`, `Amplia`) con los tests correspondientes. DShalev muestra ahora el importe en la tarjeta de pago (`app._importe_pagar`). El vector de prueba se re-firmó con `gl-redeem-vector`, el bloque `canje` y las cinco firmas históricas acotadas por `hasta` (sin ellas, al ser el directorio autoritativo el arranque invalidaría las licencias ya emitidas). Suites: Web 267/267 (incluye `test_licencia_pago`), `tools/verificacion` 31/31, `botones.py` sin incumplimientos y `ramplint` sin hallazgos (26 plantillas). |
| Encargo Complementario T4 | `hecho-verificado` (DShalev, pruebas de contrato y build) / `hecho-no-ejecutado` (Android, iOS en runtime) | Canje por código habilitado en Android (`CANJE_APP_ID="SHALEV-ANDROID"`, `CANJE_HABILITADO=true`) e iOS (`CANJE_APP_ID="SHALEV-IOS"`, `CANJE_HABILITADO=true`). UI condicional activa solo si el directorio incluye claves de `uso == "canje"` (muestra «Aún no disponible en esta edición» si está deshabilitado). Verificación estricta de `kid` con `uso == "canje"`, tipo y vencimiento obligatorios (sin defaults inventados), persistencia en Keystore/Keychain, reintento por `CLOCK_SKEW`, mensajes opacos a oráculos («Código no válido para esta aplicación o ya utilizado») y rechazo estricto de secundarias (`MAX_SECUNDARIAS = 0`). Verificación D3 (`verificar_raices_release.py`) y pruebas de contrato añadidas en las tres plataformas. |

---

## 4. Decisiones donde el código mandó sobre el prompt

1. **Regla de botones del Design System en Android (T3):**
   - El prompt sugería un botón para pegar la confirmación de pago. En Android, la herramienta de verificación estática de botones (`tools/verificacion/botones.py`) exige de manera estricta que todas las etiquetas visibles de `ShalevSecondaryButton` y acciones tengan exactamente una palabra.
   - Decisión: Se utilizó `etiqueta = "Pegar"` con `descripcion = "Pegar confirmación de pago"` y `testTag = "licencia_pegar_transferencia"`.
2. **Protección del payload SPVIR1 v=2 (T3):**
   - El prompt solicitaba modelar `tx` en el payload v=2 opcionalmente, pero no exponerlo en el cifrado hacia versiones de GL que aún esperan el esquema v1 puro sin campos no reconocidos.
   - Decisión: Se introdujo la bandera de compilación explícita `ENVIAR_TX_EN_SOLICITUD = false` en las tres plataformas (`SolicitudCifrada.kt`, `License.swift` y `solicitud.py`), incluyendo la referencia bancaria en la parte de texto legible del mensaje (`Transferencia: <tx>`) sin alterar la compatibilidad del sobre ECIES-P256-AES256GCM.
3. **El directorio de prueba publica claves de canje (verificación de paridad):**
   - DShalev autoriza solo los `kid` de `uso == "canje"` cuando el directorio los publica (`config_licencia.definir`), de modo que añadir `gl-redeem-vector` al vector de prueba cambia qué kid se acepta: `gl-sign-vigente` deja de valer como kid de canje.
   - Decisión: se acepta ese efecto, es el comportamiento diseñado, y se actualizaron `test_licencia_directorio` y `test_licencia_cliente` (3 claves; `gl-redeem-vector` autorizado, `gl-sign-vigente` no) en lugar de relajar la regla.
4. **Puntuación y oráculos en mensajes de error de canje (T4):**
   - El diccionario `MENSAJES` en `Web-Desktop/dshalev/licencia/cliente_gl.py` originalmente finalizaba sus textos en punto (`.`), mientras que el contrato unificado y el prompt especificaban la cadena literal `"Código no válido para esta aplicación o ya utilizado"` y `"Aún no disponible en esta edición"`.
   - Decisión: Se homologó la puntuación en DShalev para que la cadena devuelta sea idéntica carácter a carácter a las constantes de Android e iOS (`MENSAJE_CODIGO_INVALIDO_O_USADO` y `MOTIVO_AUN_NO_DISPONIBLE`), garantizando la misma opacidad ante intentos de enumeración de códigos en las tres plataformas.



