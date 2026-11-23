# Estado de la verificación en el PC — 2026-11-23

Rama local: `pc/2026-11-22-verificacion` (repo `rmdvcd/Shalev`, **PARTE S**).
Todo lo marcado «verificado» se ejecutó en este PC con JDK 17 (`~/.gradle/jdks/jdk17`), `ANDROID_HOME=C:/Users/666/AppData/Local/Android/Sdk` y Python del venv de `Web-Desktop`.

| Tarea | Estado | Evidencia | Notas |
|---|---|---|---|
| T1 — `AVISO_FRESCURA` «7 dias» y GSM-7 | verificado | `SolicitudFrescuraTest` (en `shalevTests`), `gsm7.py` | El aviso no cambió; además se corrigieron nombres e importes que salían con tilde/U+202F |
| T2 — tarjeta «Cómo pagar» | verificado (UI local) | `GET /licencia/solicitar` con sesión devuelve «Cómo pagar» y «Paga solo a los datos que muestra esta pantalla» | `METODOS_COBRO = ()`: faltan los datos de cobro reales (decisión del propietario) |
| T3 — campo de transferencia y `ENVIAR_TX_EN_SOLICITUD` | verificado | `SolicitudTransferenciaTest`; campo `tx` presente en `/licencia/solicitar`; constante en `false` | Se deja en `false` hasta que el APK nuevo de GL esté instalado |
| T4 — `CANJE_APP_ID` por build | presente | Android: `buildConfigField("String","CANJE_APP_ID","\"SHALEV-ANDROID\"")` en `:app` y `:data`; iOS: clave `CANJE_APP_ID` del Info.plist con `RedeemContract.defaultAppID` | Sin prueba de ejecución del canje contra el Worker (prohibido operar remoto) |
| A2 — `solicitud.py`, `corta.py` y rutas `/licencia/*` | verificado | `pytest` 232 passed; en vivo: `/licencia` y `/licencia/solicitar` responden 200 con sesión; sin sesión 303 a `/entrar` | Rutas `/licencia/activar`, `/recuperar`, `/ampliar` existen en `app.py` |
| Vectores (`directorio-v1`, `precios`, `reglas-negocio`) | verificado (Python y Kotlin); estático (Swift) | `test_vectores_paridad.py` + `test_licencia_pago.py` en pytest; precios de `TipoLicencia` (Kotlin) y `GLLicenseType` (Swift) coinciden con `precios.json` | Ningún consumidor lee los JSON en tiempo de ejecución: son valores fijados en código y verificados por pruebas |
| D1 — anclaje estricto de permisos de secundarias | verificado | `VerificadorPermisoSecundariaTest` (6/6) en `shalevTests` | Se corrigió la lectura del sobre firmado del canje (era la causa del único fallo) |
| D2 — `LicenseTrust` no resucita claves comprometidas | verificado | `SubTokenTest.claveComprometidaORevocadaSeRechaza` y `TrustDirectory.esComprometida` en el verificador | iOS: `Directory.isKeyCompromised` restaurado (CI pendiente) |
| D3 — guarda de build con raíces vacías | verificado | `tools/verificacion/tests/test_raices_release.py` dentro de los 37 tests de `unittest` | `RAIZ_A_SPKI`/`RAIZ_B_SPKI` siguen vacías en las tres plataformas: faltan las raíces reales |
| S-1 Python | verificado | pytest 232 passed / 33 skipped (37,18 s); unittest 37 OK (7,08 s); `botones.py` 0 incumplimientos; `probar_gl.py autoprueba` «AUTOPRUEBA CORRECTA» | `ramplint` no está instalado en este PC: no ejecutado |
| S-2 Android | verificado | `shalevTests`: 1160 pruebas, 0 fallos (3 min 18 s); `shalevCheck`: BUILD SUCCESSFUL (8 min 47 s), incluye `shalevPermisos` y lint | Sin permisos nuevos |
| S-3 iOS | no ejecutado | CI de `main` falla en `TrustDirectory.swift` y `SyncRepository.swift` (run 44989769123); corregido en `pc.7` sin compilar aquí | Requiere push + CI para declararlo verificado |
| S-4 vectores cruzados | verificado parcialmente | Ver fila de vectores | Swift solo lectura estática |
| S-5 prueba GSM-7 | verificado | `gsm7.py` → «GSM-7: todas las plataformas conformes»; `test_gsm7.py` (5 pruebas) | Solicitud estándar: Android 4 partes, iOS 4, DShalev 5 (límite 5) |
| S-6 DShalev en vivo | verificado (parcial) | Sin sesión: `/licencia`, `/licencia/solicitar` → 303 a `/entrar`. Con sesión: `/licencia` 200 y `/licencia/solicitar` 200 con «Cómo pagar», aviso y campo `tx` | **Ausente:** en modo LAN (`--lan`, `0.0.0.0:8793`) las rutas `/licencia/*` se sirven en la IP de red (solo protegidas por login). No se implementó: requiere decisión del propietario |
| S-7 datos del propietario | bloqueado | `RAIZ_A_SPKI`/`RAIZ_B_SPKI` vacías; `METODOS_COBRO` vacío | Ver `docs/PASOS_DEL_PROPIETARIO.md` |

## Comandos ejecutados (resumen)

```
cd Android && JAVA_HOME=~/.gradle/jdks/jdk17 ./gradlew.bat shalevTests --console=plain
cd Android && JAVA_HOME=~/.gradle/jdks/jdk17 ./gradlew.bat shalevCheck --console=plain
cd Web-Desktop && ./.venv/Scripts/python.exe -m pytest tests -q
Web-Desktop/.venv/Scripts/python.exe -m unittest discover -s tools/verificacion/tests -p "test_*.py"
cd Web-Desktop && ./.venv/Scripts/python.exe ../tools/verificacion/gsm7.py
Web-Desktop/.venv/Scripts/python.exe tools/verificacion/botones.py
Web-Desktop/.venv/Scripts/python.exe tools/licencia/probar_gl.py autoprueba
```

Salidas completas en `build-logs/` (excluida de git).
