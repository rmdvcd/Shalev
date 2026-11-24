# Matriz de Paridad Funcional: Shalev Android, IShalev (iOS) y DShalev (Escritorio)

**Fecha:** 2026-11-21  
**Referencia funcional:** Shalev Android (v1.0.1, `versionCode 54`).  
**Reglas del contrato:** Android es la referencia funcional pero no un dogma. Si la referencia tiene un fallo de diseño, de seguridad o criptográfico, no se reproduce; se documenta en la sección [Defectos de la referencia](#defectos-de-la-referencia).

---

## 1. Matriz de Paridad por Módulo

Estados utilizados:
- **Igual**: Misma función, flujos, estados, validaciones o contratos (exige evidencia: prueba, vector o archivo+símbolo).
- **Diferencia de plataforma (aprobada)**: Adaptación necesaria a las capacidades/limitaciones del sistema operativo (iOS/macOS/Windows/Web).
- **Diferencia a decidir**: Divergencia de producto o persistencia que se conserva sin eliminar hasta decisión del propietario.
- **Falta en iOS**: Capacidad requerida de Android aún no completada en iOS.
- **Falta en DShalev**: Capacidad requerida de Android aún no completada en DShalev.
- **Defecto de la referencia**: Fallo o vulnerabilidad en Android documentado para su corrección.

| Módulo | Función | Android | iOS | DShalev | Estado | Evidencia |
|---|---|---|---|---|---|---|
| **acceso** | Acceso opcional con PIN o credencial local | `AccesoViewModel.kt` | `DeviceLockView.swift` (LocalAuthentication) | `security.py: login_required`, sesión por cookie | Diferencia de plataforma (aprobada) | `Android/app/src/main/kotlin/cu/shalev/app/acceso/` vs `iOS/Sources/Features/DeviceLockView.swift` vs `Web-Desktop/dshalev/security.py` |
| **acceso** | Límite de 5 intentos fallidos y bloqueo temporal | `AccesoViewModel.kt` | Bloqueo gestionado por SO / PIN local | `security.py: registrar_intento_fallido` (5 fallos → 5 min espera) | Igual | `Android/core/src/test/kotlin/cu/shalev/core/AccesoTest.kt` y `Web-Desktop/dshalev/security.py` |
| **actualizacion** | Consulta de versiones y notas en GitHub Releases | `ActualizacionRepository.kt` | `UpdatesView.swift` (informativo) | Verificación manual (sin auto-updater web) | Diferencia de plataforma (aprobada) | `Android/app/src/main/kotlin/cu/shalev/app/actualizacion/` vs `iOS/Sources/Features/UpdatesView.swift` |
| **actualizacion** | Distribución de APK firmada por red local (LAN) | `ApkDistribution.kt` | N/A (iOS no instala paquetes arbitrarios) | `sync_server.py: /apk` | Diferencia de plataforma (aprobada) | `Android/app/src/main/kotlin/cu/shalev/app/actualizacion/ApkDistribution.kt` y `Web-Desktop/dshalev/sync_server.py` |
| **ajustes** | Tema claro y oscuro | `AjustesScreen.kt` | `SettingsView.swift` | `theme.js`, `base.html` (`data-theme`) | Igual | `Android/designsystem/src/main/kotlin/cu/shalev/designsystem/theme/Theme.kt` y `Web-Desktop/dshalev/static/theme.js` |
| **ajustes** | Avisos de inventario (niveles mínimo y crítico recomendados 5/1) | `AppSettings.kt` | `SettingsView.swift: stockWarningThreshold` | `config.py: default_stock_threshold` | Igual | `Android/domain/src/main/kotlin/cu/shalev/domain/model/AppSettings.kt` y `iOS/Sources/Domain/Domain.swift` |
| **ajustes** | Modo de red (Local vs LAN) | `ModoRed.kt` | `SyncNetwork.swift` | `network.html`, `portable-settings.json` | Diferencia de plataforma (aprobada) | `Web-Desktop/dshalev/templates/network.html` |
| **ayuda** | Preguntas frecuentes, atajos y resumen POS | `AyudaScreen.kt` | `SettingsView.swift` | `ayuda.html` (`/ayuda`, atajo F2) | Igual | `Web-Desktop/dshalev/templates/ayuda.html` (DS-8) |
| **ayuda** | Teléfono de soporte (+5351815604) y enlaces | `DeveloperContact.kt` | `SupportView.swift` | `ayuda.html` | Igual | `Android/core/src/main/kotlin/cu/shalev/core/contact/DeveloperContact.kt` y `tools/licencia/probar_gl.py` |
| **bloqueo** | Bloqueo por inactividad | `BloqueoScreen.kt` | `DeviceLockView.swift` | Expiración de cookie de sesión (30 min) | Diferencia de plataforma (aprobada) | `Android/app/src/main/kotlin/cu/shalev/app/bloqueo/` |
| **bloqueo** | Privacidad de pantalla en selector de apps y captura | `FLAG_SECURE` en Compose | `UIScreen.isCaptured` y ocultamiento en app switcher | N/A (ejecución web en navegador del usuario) | Diferencia a decidir | `Android/app/src/main/kotlin/cu/shalev/app/licencia/LicenciaScreen.kt` |
| **caja** | Apertura de turno con fondo de caja (`opening_cash_cents`) | `TurnoRepository.kt` | `Domain.swift: Shift` | `database.py: open_shift` | Igual | `Android/domain/src/main/kotlin/cu/shalev/domain/model/Turno.kt` y `Web-Desktop/tests/test_database.py` |
| **caja** | Registro de entradas y salidas de efectivo con motivo | `MovimientosCaja.kt` | `Domain.swift: CashMovement` | `database.py: add_cash_movement` | Igual | `Web-Desktop/tests/test_database.py: test_movimientos_caja` |
| **caja** | Arqueo y conteo de efectivo al cerrar turno | `CierreTurnoViewModel.kt` | `Domain.swift: closeShift` | `database.py: close_shift` | Igual | `Android/app/src/main/kotlin/cu/shalev/app/caja/` y `Web-Desktop/dshalev/templates/shifts.html` |
| **inicio** | Resumen métricas del negocio (ventas, beneficio, operaciones) | `InicioScreen.kt` | `HomeView.swift` | `dashboard.html` (`/`) | Igual | `Web-Desktop/dshalev/app.py: dashboard` y `iOS/Sources/Features/HomeView.swift` |
| **inicio** | Ranking de artículos más vendidos | `InicioTopArticulos.kt` | `HomeView.swift: topItems` | `dashboard.html` (Top 3) | Igual | `Web-Desktop/tests/test_paridad_escritorio.py` |
| **inicio** | Banner de estado de licencia (prueba restante / vencimiento) | `LicenseState.bannerText` | `HomeView.swift: licenseBanner` | `base.html: banner_licencia` | Igual | `Android/licencia/src/main/kotlin/cu/shalev/licencia/LicenseState.kt` y `Web-Desktop/dshalev/templates/base.html` |
| **insumos** | Catálogo de materias primas con stock decimal (milésimas) | `InsumosRepository.kt` | `Domain.swift: Supply` | `inventory.py: category == 'Insumo'` | Igual | `Android/core/src/main/kotlin/cu/shalev/core/money/Money.kt: porMilesimas` y `Web-Desktop/dshalev/inventory.py` |
| **insumos** | Historial de movimientos de insumos (recepción/consumo) | `MovimientoInsumo.kt` | `Domain.swift: SupplyMovement` | `inventory_movements.html` | Igual | `Web-Desktop/dshalev/templates/inventory_movements.html` |
| **inventario** | Catálogo de productos, precio, costo y stock | `InventarioScreen.kt` | `InventoryView.swift` | `inventory.html` | Igual | `Android/app/src/main/kotlin/cu/shalev/app/inventario/` y `Web-Desktop/tests/test_inventory_pos.py` |
| **inventario** | Avisos de stock bajo y crítico | `StockAlert.kt` | `InventoryView.swift` | `inventory.html` (alertas visuales) | Igual | `Web-Desktop/dshalev/templates/inventory.html` |
| **inventario** | Búsqueda y filtrado por categoría y local | `InventarioFiltros.kt` | `InventoryView.swift` | `inventory.html?q=...&categoria=...` | Igual | `Web-Desktop/tests/test_paridad_escritorio.py` |
| **licencia** | Asistente de 3 pasos (DATOS -> SOLICITAR -> ACTIVAR) | `LicenciaScreen.kt`, `PasoLicencia` | `LicenseView.swift` | `license.html`, `app.py: /licencia` | Igual | `Android/app/src/main/kotlin/cu/shalev/app/licencia/LicenciaForm.kt: PasoLicencia` |
| **licencia** | Ventana de frescura de 7 días (`AVISO_FRESCURA` GSM-7) | `MensajesLicencia.AVISO_FRESCURA` | `GLLicenseMessage.AVISO_FRESCURA` | `solicitud.AVISO_FRESCURA` | Igual | `SolicitudFrescuraTest.kt`, `LicenseContractTests.swift`, `test_licencia_pago.py` |
| **licencia** | Instrucciones de pago y tarjeta «Cómo pagar» con cálculo | `PaymentInstructions.kt`, `LicenciaScreen.kt` | `PaymentInstructions.swift`, `LicenseView.swift` | `pago.py`, `license_solicitar.html` | Igual | `docs/vectores/precios.json` y `test_licencia_pago.py` |
| **licencia** | N.º de transferencia opcional y pegado (`tx`, payload v=2) | `RequestPayload.tx`, `SolicitudCifrada.kt` | `GLLicenseManager.buildRequest`, `LicenseView.swift` | `validar_transferencia`, `solicitud.py` | Igual | `SolicitudTransferenciaTest.kt`, `test_licencia_spvi.py` |
| **licencia** | Solicitud compacta `SPVIR1:` (v=2, ECIES P-256) | `SolicitudCifrada.kt` | `License.swift: makeRequest` | `solicitud.py: sellar` | Igual | `docs/GL_ESPECIFICACION.md` y `tools/licencia/probar_gl.py` |
| **licencia** | Activación cifrada `SPVI2:` (216 chars, ECDSA + AES-GCM) | `LicenciaCorta.kt` | `License.swift: activate` | `corta.py: abrir` | Igual | `GoldenVectorTest.kt` y `docs/vectores/` |
| **licencia** | Canje comercial de 16 caracteres y consulta de estado | `RedeemClient.kt` (`SHALEV-ANDROID`) | `RedeemClient.swift` (`SHALEV-IOS`) | `cliente_gl.py` (`DSHALEV`) | Igual | `RedeemContractTest.kt`, `LicenseContractTests.swift`, `test_licencia_cliente.py` |
| **licencia** | Prevalencia de licencias (mayor vencimiento; perpetua gana) | `LicenseState` | `License.swift: chooseDominant` | `almacen.py: guardar_o_dominar` | Igual | `Android/app/src/test/kotlin/cu/shalev/app/licencia/LicenciaFormTest.kt` |
| **licencia** | Renovación ("Renovar igual") y ampliación de secundarias | `TextosRenovacion.kt` | `LicenseView.swift` | `app.py: /licencia/solicitar` | Igual | `Android/app/src/main/kotlin/cu/shalev/app/licencia/LicenciaForm.kt` |
| **licencia** | Recuperación de licencia anterior por ID | `RequestPayload.recupera` | `LicenseView.swift` | `solicitud.py: recuperar` | Igual | `Android/licencia/src/main/kotlin/cu/shalev/licencia/contract/GlModels.kt` |
| **licencia** | Pantalla / estado "Licencia transferida" con desinstalación | `LicenciaTransferida.kt` | `LicenseView.swift` | `license.html` (aviso de revocación/transferencia) | Igual | `Android/app/src/main/kotlin/cu/shalev/app/licencia/LicenciaTransferida.kt` |
| **notificacion** | Notificaciones locales de stock bajo y vencimiento | `ShalevNotifier.kt` | `UNUserNotificationCenter` | Avisos en dashboard / banners | Diferencia de plataforma (aprobada) | `Android/app/src/main/kotlin/cu/shalev/app/notificacion/` |
| **notificacion** | Captura automática de SMS de pago (Transfermóvil) | `SmsReceiver.kt` | N/A (no permitido en sandbox iOS) | N/A (entorno escritorio sin módem celular) | Diferencia de plataforma (aprobada) | `Android/app/src/main/kotlin/cu/shalev/app/notificacion/SmsReceiver.kt` |
| **onboarding** | Asistente de bienvenida y elección Principal / Secundaria | `OnboardingScreen.kt` | `OnboardingView.swift` | `setup.html` (primer inicio) | Igual | `Android/app/src/main/kotlin/cu/shalev/app/onboarding/` y `iOS/Sources/Features/OnboardingView.swift` |
| **onboarding** | Selección de módulos y empleados previstos | `OnboardingModulos.kt` | `OnboardingView.swift` | Configuración inicial de usuario y catálogo | Igual | `docs/COMPROBACION_PARIDAD_ISHALEV_2026-11-08.md` |
| **pagos** | Métodos Efectivo y Transferencia bancaria | `PaymentMethod.kt` | `SaleView.swift` | `database.py: payment_method` | Igual | `Android/domain/src/main/kotlin/cu/shalev/domain/model/Venta.kt` y `Web-Desktop/tests/test_database.py` |
| **pagos** | Generación de QR Transfermóvil / EnZona | `QrTransfermovil.kt` | `SaleView.swift` | `dashboard.html: qrcode.js` | Igual | `Android/core/src/main/kotlin/cu/shalev/core/payment/` |
| **pagos** | Validación de tarjeta cubana con algoritmo Luhn | `Validators.luhn` | `Domain.swift: luhn` | `forms.py: validar_luhn` | Igual | `Android/core/src/main/kotlin/cu/shalev/core/validation/Validators.kt` y `docs/vectores/reglas-negocio.json` |
| **perfil** | Datos del negocio y titular (nombre, CI, teléfonos) | `PerfilScreen.kt` | `SettingsView.swift` | `database.py: settings` | Igual | `Android/app/src/main/kotlin/cu/shalev/app/perfil/` |
| **perfil** | Tarjetas bancarias para cobro por transferencia | `TarjetaBancaria.kt` | `SettingsView.swift` | `database.py: bank_cards` | Igual | `Android/domain/src/main/kotlin/cu/shalev/domain/model/Perfil.kt` |
| **precios** | Ajuste masivo por porcentaje (puntos básicos) o importe fijo | `AjustesPreciosScreen.kt` | `ToolsView.swift: PriceAdjustments` | `tools.html: conversor / ajustes` | Igual | `Android/app/src/main/kotlin/cu/shalev/app/precios/` |
| **precios** | Tarifas canónicas de licencias GL | `TipoLicencia` | `License.swift` | `config_licencia.py`, `docs/vectores/precios.json` | Igual | `docs/vectores/precios.json` |
| **producto** | Formulario de producto con precio de venta y costo | `ProductoScreen.kt` | `InventoryView.swift: ItemEditor` | `inventory_edit.html` | Igual | `Web-Desktop/dshalev/templates/inventory_edit.html` |
| **producto** | Fichas de costo / recetas con descuento de insumos | Temporal en memoria | `ToolsView.swift` (persistente en AppStorage) | `cost_sheets.py` (persistente en SQLite) | Diferencia a decidir | `iOS/Sources/Features/ToolsView.swift` y `Web-Desktop/dshalev/cost_sheets.py` |
| **recorrido** | Guías interactivas para nuevos usuarios | `RecorridoVisual.kt` | Resumen informativo en onboarding | Resumen de teclado y ayuda en `/ayuda` | Diferencia de plataforma (aprobada) | `Android/app/src/main/kotlin/cu/shalev/app/recorrido/` |
| **registros** | Listado de ventas con filtros de fecha, turno y empleado | `RegistrosVentas.kt` | `RecordsView.swift` | `sales.html` | Igual | `Web-Desktop/dshalev/templates/sales.html` |
| **registros** | Anulación y corrección de ventas con reversión de inventario | `AnularVentaUseCase.kt` | `RecordsView.swift: cancelSale` | `database.py: cancel_sale` | Igual | `Web-Desktop/tests/test_database.py: test_cancel_sale` |
| **respaldo** | Exportación e importación `.shalev` (DTO v5/v4 cifrado AES-GCM) | `RespaldoRepository.kt` | `AndroidBackup.swift` | `backup.html`, `database.py: export_backup` | Igual | `Android/data/src/main/kotlin/cu/shalev/data/respaldo/` y `iOS/Sources/Domain/AndroidBackup.swift` |
| **respaldo** | Extensión de fotos en backup | No incluye fotos en DTO v5 | `AndroidBackup.swift: fotoData` opcional | Carpeta manual de activos publicados | Diferencia a decidir | `docs/COMPROBACION_PARIDAD_ISHALEV_2026-11-08.md` |
| **servicios** | Catálogo de servicios sin inventario físico | `ServiciosScreen.kt` | `ServicesView.swift` | `services.html` | Igual | `Web-Desktop/tests/test_paridad_escritorio.py` |
| **soporte** | Contacto oficial WhatsApp (`https://wa.me/5351815604`) | `DeveloperContact.kt` | `SupportView.swift` | `ayuda.html` | Igual | `Android/core/src/test/kotlin/cu/shalev/core/ValidatorsTest.kt` |
| **soporte** | Enlace de soporte SMS (`smsto:+5351815604`) | `DeveloperContact.kt` | `SupportView.swift` | N/A (escritorio sin módem) | Diferencia de plataforma (aprobada) | `Android/core/src/main/kotlin/cu/shalev/core/contact/DeveloperContact.kt` |
| **ubicacion** | Gestión de múltiples locales y puntos de venta | `LocalesRepository.kt` | `Domain.swift: StoreLocation` | `database.py: locations` | Igual | `Android/domain/src/main/kotlin/cu/shalev/domain/model/Local.kt` y `Web-Desktop/tests/test_locales.py` |
| **ubicacion** | Aislamiento y alcance de inventario y turnos por local | `LocalScope.kt` | `SyncManager.swift: local filtering` | `database.py: location_id` | Igual | `Web-Desktop/tests/test_locales.py` |
| **venta** | Carrito de venta, cálculo de total y descuento de stock | `VentaScreen.kt` | `SaleView.swift` | `dashboard.html: pos.js` | Igual | `Web-Desktop/tests/test_pos.py` |
| **venta** | Prohibición estricta: No se vende sin turno abierto | `VentaSinTurnoException` | `SaleView.swift: requireOpenShift` | `database.py: require_open_shift` | Igual | `Web-Desktop/tests/test_database.py` |
| **vinculacion** | Emparejamiento por QR y protocolo LAN v1 SPVI | `SyncServer.kt` | `SyncNetwork.swift` | `sync_server.py`, `sync_protocol.py` | Igual | `Web-Desktop/tests/test_sync_protocol.py` |
| **vinculacion** | Modelo de autorización de secundarias | Empleado vinculado por QR en secundaria | Perfiles locales con PIN en iOS | Listener central de secundarias en escritorio | Diferencia a decidir | `docs/COMPROBACION_PARIDAD_ISHALEV_2026-11-08.md` |
| **extra_ios** | OCR e importación documental | Retirado el 2026-10-31 | `OcrImportView.swift`, `OcrDocumentParser.swift` | N/A | Diferencia a decidir | `iOS/Sources/Features/OcrImportView.swift` |

---

## 2. Diferencias Conocidas Sembradas (sin alterar código de forma arbitraria)

1. **OCR en iOS (`OcrImportView`, `OcrDocumentParser`):** Conservado en iOS como función adicional nativa para digitalización de documentos; Android lo retiró en octubre de 2026. Se clasifica como *Diferencia a decidir* (no eliminar de iOS).
2. **Perfiles con PIN local (iOS) vs Secundaria por empleado (Android):** iOS permite configurar múltiples perfiles en el mismo dispositivo con PIN local y bloqueo; Android asigna una secundaria por teléfono de empleado con permisos gobernados desde la principal.
3. **Captura automática de SMS:** Solo en Android mediante servicio en segundo plano y permiso de lectura de SMS/notificaciones; en iOS y DShalev el texto se procesa pegándolo o compartiéndolo manualmente.
4. **Distribución de instaladores por LAN:** Android y DShalev distribuyen el archivo APK a través de la red local (`/apk`); iOS no puede instalar paquetes arbitrarios fuera de TestFlight o App Store.
5. **Persistencia de fichas de costo:** Android mantiene la ficha de costos como cálculo temporal en memoria; iOS (`ToolsView`) y DShalev (`cost_sheets.py`) ofrecen almacenamiento persistente y exportación.
6. **Fotos en copias de seguridad:** iOS admite extensión `fotoData` opcional en respaldos; Android no transporta imágenes dentro del DTO v5 estándar.
7. **Canales nativos de mensaje:** DShalev corre en navegador de escritorio y carece de integración nativa directa con SMS o WhatsApp app; genera enlaces web `https://wa.me/...` y botones de copiado al portapapeles.

---

## 3. Defectos de la Referencia

Los siguientes defectos han sido identificados en el código de referencia de Android (`Android/`) y **no deben ser copiados** a iOS ni DShalev, sino corregidos según el encargo:

1. **D1 — Validación defectuosa de permisos en secundarias (`VerificadorPermisoSecundaria`):**  
   En Android, el verificador de permisos de secundarias toma la clave pública de firma (`firmaPub`) del propio mensaje entrante y acepta la licencia de forma incondicional si el permiso no llega. Esto permite que una secundaria no autorizada o un atacante falsifique permisos o emita cuotas arbitrarias.  
   *Solución:* Anclar la clave pública del dispositivo firmada por el servicio (canje) y verificar divulgación de OKM con MAC designado y ECDH estático (SPVI2).

2. **D2 — Resurrección de claves comprometidas por fallback ciego a claves fijas (`LicenseTrust`):**  
   Cuando el directorio de confianza efectivo (`TrustDirectory`) devuelve una lista de claves vacía (p. ej. porque todas las claves fijas fueron revocadas o están fuera del intervalo de vigencia `[desde, hasta]`), `LicenseTrust.signSpkis()` y `ecdhSpki()` caen ciegamente a las claves fijas de compilación. Esto resucita claves comprometidas o expiradas.  
   *Solución:* Si existe un directorio efectivo válido, su respuesta (incluso vacía) es autoritativa. El respaldo fijo solo debe utilizarse si no existe ningún directorio válido.

3. **D3 — Raíces de confianza vacías en build release (`RAIZ_A_SPKI` / `RAIZ_B_SPKI`):**  
   Las constantes de raíces públicas en Android e iOS se dejan vacías o con marcadores sin una comprobación de compilación que falle explícitamente en modo release, lo que dejaría la validación de canje inoperante en producción sin avisar al operador.  
   *Solución:* Añadir comprobación de release que falle si las raíces están vacías o son las de prueba.

4. **C1 — Incompatibilidad de solicitud con alfabeto GSM-7:**  
   En Android, `MensajesLicencia.kt` utiliza palabras con tilde como `"envíala"` y `"Amplía"`, que no pertenecen al alfabeto estándar GSM-7 (7 bits). Esto obliga a los operadores de telecomunicaciones a codificar el mensaje en UCS-2 (16 bits), disparando el número de partes de SMS de 5 a 12 fragmentos, encareciendo y aumentando el riesgo de corte en el envío en Cuba.  
   *Solución:* Normalizar las palabras legibles a `"enviala"` y `"Amplia"`, y retirar acentos/ñ en las líneas informativas legibles manteniendo el payload cifrado intacto.

---

## 4. Correcciones aplicadas en la verificación de paridad (2026-11-24)

Referencia: `main@5ce64ef` (PR #153, tareas T1–T5). Las correcciones se revisaron primero por lectura (en el
entorno de trabajo no hay Gradle, Android SDK ni Xcode) y después se confirmaron en CI: el commit `fe3d75a` deja
`CI` en verde (API mínima 26, manifiesto, Lint, Tests JVM, Release/APK) y `Compilación iOS de IShalev` en verde
desde `bb7cab3` (pruebas del dominio, app, IPA y simulador). Ver §4.9 para los defectos que solo se vieron al
compilar.

### 4.1 Canje comercial (T4) — estaba cerrado en las tres plataformas

El contrato del directorio admite un bloque opcional `canje: { endpoint_base, app_ids }` y claves con
`uso == "canje"`, pero **ningún directorio del repo lo publicaba** y **nadie cargaba el directorio al arrancar**.
Resultado: `TrustDirectory.effectiveDirectory` / `Directory.effectiveDirectory` / `configuracion()` quedaban
vacíos y el canje se mostraba como «Aún no disponible en esta edición» en APK, IPA y DShalev.

| Pieza | Android | iOS | DShalev |
|---|---|---|---|
| Carga al arrancar | `ShalevApplication.onCreate` → `TrustDirectoryManager.cacheFile` + `loadEffective()` | `IShalevApp.init` → `TrustDirectoryManager.cacheURL` + `loadEffective()` | `create_app.lifespan` → `_cargar_directorio_confianza()` |
| Actualización remota | hilo daemon `directorio-confianza` | `Task.detached(priority: .utility)` | no se hace en el arranque (decisión: el panel no bloquea) |
| Regla única de apertura | `RedeemContract.canjeActivo(...)` | `RedeemClient.make(...)` | `ConfiguracionLicencia.comercial_habilitada` |
| Base sin barras finales | `RedeemContract.endpointBase(...)` | `RedeemClient.normalizedBase(...)` | `_validar_endpoint` + `BASE_PUBLICA_AUTORIZADA` |
| appId del producto | `BuildConfig.CANJE_APP_ID` = `SHALEV-ANDROID` | `Info.plist` `CANJE_APP_ID` = `SHALEV-IOS` | `config_licencia` = `DSHALEV` |

Detalles que corregían defectos reales:

- `LicenciaRepositoryImpl.redeemClientLocked()` memorizaba el cliente **aunque estuviera cerrado**, de modo que
  una actualización del directorio no abría el canje hasta reiniciar. Ahora solo se memoriza si `canjeActivo`.
- `LicenseView.isCanjeActive` recalculaba la regla por su cuenta (y con `CANJE_HABILITADO` por defecto `true`);
  ahora delega en `RedeemClient.configured().canRedeem`, la misma que usa el envío real.
- La pantalla Android (`LicenciaScreen`, paso ACTIVAR) y el repositorio usaban dos copias de la regla; ahora ambas
  llaman a `RedeemContract.canjeActivo`.

### 4.2 C1 (alfabeto GSM-7) — seguía abierto en la referencia

`docs/PARIDAD_ANDROID.md` §3.4 documenta la solución (`"enviala"`, `"Amplia"`, sin tildes), y DShalev la aplica
(`normalizar_gsm7` en `licencia/solicitud.py`), pero Android seguía emitiendo `Carné de identidad:`, `Teléfono:`
y `Amplía:` en `SolicitudCifrada.solicitud/ampliacion/recuperacion`, lo que fuerza UCS-2 y multiplica las partes
del SMS. Se alineó Android a la solución ya documentada y se ajustaron las expectativas de `LicenseFlowTest`
(líneas 59-60 y 253). El payload cifrado no cambia.

### 4.3 Panel de pago en DShalev

`license_solicitar.html` mostraba «Cómo pagar» con métodos de cobro pero **sin importe**, mientras APK e IPA
muestran el precio.

- Se añadió `importe_pagar` con un único helper `app._importe_pagar` (tarifas canónicas de
  `solicitud.calcular_precio` / `calcular_precio_ampliacion`), usado tanto en el GET como en el POST; antes el POST
  tenía su propia copia del cálculo.
- La advertencia se sirve desde la constante `FRASE_PAGA_SOLO` (`frase_paga_solo`) y ahora se muestra **siempre**
  en la tarjeta; antes era un literal en la plantilla y desaparecía cuando no había métodos de cobro configurados.
- El script `actualizarCalculoPrecio` mantiene el desglose existente y sincroniza el nuevo `#importe-pagar`, para
  que la tarjeta y el desglose no discrepen mientras se edita el plan o las secundarias.

### 4.4 Directorio de prueba con canje

`tools/licencia/generar_vectores_directorio.py` ahora firma un vector con `gl-redeem-vector` (`uso: "canje"`,
escalar determinista `0x43…`), el bloque `canje` con base `https://gl-redeem.example.test` y los tres `app_ids`,
y las cinco firmas históricas (ver §4.5). La firma ECDSA se fija en `FIRMA_V1` porque `ec.ECDSA` usa nonce
aleatorio: sin fijarla, cada ejecución produciría un vector distinto y los tres bootstrap dejarían de coincidir.
Se regeneraron `docs/vectores/directorio-v1.json` y los tres `directorio-bootstrap.json`
(`Android/licencia/src/main/resources`, `iOS/Resources`, `iOS/Sources/Domain`). Es un vector de **prueba**:
la base no es producción y la clave privada solo la tendría GL.

> Consecuencia esperada y deliberada: al haber ahora claves `uso == "canje"` en el directorio, DShalev autoriza
> **solo** ese kid (`config_licencia.definir` prioriza las claves de canje). Las pruebas `test_licencia_cliente`
> y `test_licencia_directorio` se actualizaron en consecuencia (8 claves, `gl-redeem-vector` autorizado,
> `gl-sign-vigente` no autorizado como kid de canje).

### 4.5 Riesgo que abrió la carga del directorio: firmas históricas (D2)

`LicenseTrust.signSpkis()` (Android) y `GLTrust.signingPublicKeys` (iOS) son autoritativos en cuanto existe
directorio efectivo (es la mitigación del defecto D2: sin respaldo ciego a las claves fijadas). Pero las claves
fijadas del build son **seis** (la vigente y cinco rotaciones anteriores que aún validan licencias ya emitidas),
mientras el vector solo publicaba una. Al empezar a cargarse el directorio, las licencias antiguas habrían
dejado de verificar en el siguiente arranque.

Solución en los datos, no en el código: el vector publica las cinco firmas históricas con `estado: "retirada"` y
`hasta` = fecha de retirada. Así:

- una licencia emitida dentro de su intervalo sigue verificando (`signKeysFor(emitidaEn)` devuelve 6 claves);
- pasada la retirada solo queda la vigente (1 clave), de modo que ninguna licencia nueva se firma con ellas.

Pruebas: `LicenseTrustTest.unDirectorioEfectivoNoPierdeLasFirmasHistoricasDentroDeSuIntervalo` (Android),
`LicenseContractTests.testBootstrapDirectoryNowPublishesRedeemBaseAndAppIDs` (iOS) y
`test_vector_directorio_v1_valido` / `test_flujo_completo_canje_con_directorio_v1` (DShalev).

### 4.6 Raíces de compilación vacías (D3) y degradación

`TrustDirectory.RAIZ_A_SPKI` / `RAIZ_B_SPKI` (Android) y `rootASPKI` / `rootBSPKI` (iOS) siguen vacías en el
repositorio, como exige D3 (las reales se inyectan al publicar y `tools/verificacion/verificar_raices_release.py`
falla en modo release si faltan). Consecuencia esperada de la carga en el arranque: sin raíces, el bootstrap no
verifica, `effectiveDirectory` queda en `null` y `LicenseTrust` usa las claves fijadas. La app **no** se rompe:
pierde la rotación dinámica y el canje (que exige base y clave de canje del directorio), no la validación normal.

### 4.7 Pruebas de la costura nueva

- Web: `test_licencia_config.test_registrar_directorio_efectivo_habilita_la_configuracion_del_directorio`.
- Android: `RedeemContractTest.canjeSeCierraSiFaltaBaseHttpsOClaveDeCanje` (regla pura, sin red).
- iOS: `LicenseContractTests.testRedeemClientStaysClosedWithoutBuildFlagDirectoryBaseOrRedeemKey`,
  `testEndpointBaseDropsTrailingSlashesWithoutBreakingTheScheme` y
  `testBootstrapDirectoryNowPublishesRedeemBaseAndAppIDs` (parsea el bootstrap real del bundle).

### 4.8 Diferencias de plataforma que se mantienen (no son defectos)

1. `CANJE_APP_ID` distinto por producto (`SHALEV-ANDROID` / `SHALEV-IOS` / `DSHALEV`): el servicio emite por appId.
2. Origen del interruptor: `BuildConfig` (Android), `Info.plist` (iOS), `config_licencia` (DShalev).
3. Copia al portapapeles y SMS/WhatsApp nativos: solo en apps nativas; DShalev usa enlaces `wa.me` y el botón Copiar.
4. DShalev no refresca el directorio en el arranque (no bloquea el panel); Android e iOS sí lo hacen en segundo plano.

### 4.9 Defectos que solo aparecieron al compilar (CI)

La revisión estática daba por buenos tres puntos que el compilador y las pruebas rechazaron. Se listan porque
son lecciones del contrato, no ruido de entorno:

1. **`DeviceIdProvider` es una clase, no una interfaz.** `VerificadorPermisoSecundariaTest` usaba
   `object : DeviceIdProvider { override val deviceId = … }` y el compilador pedía el `context` del constructor.
   La prueba JVM pasa un `ContextWrapper(null)`: `deviceId` está sobrescrito y la propiedad real (que lee
   `Settings.Secure`) nunca se evalúa. En instrumentadas el patrón correcto sigue siendo el contexto real
   (`RespaldoRoomTest.kt:190`).
2. **`org.json` del `android.jar` de pruebas unitarias es un stub.** Con `unitTests.isReturnDefaultValues = true`
   los métodos devuelven `null` y las pruebas de anclaje fallaban por NPE, no por lógica. Se añadió
   `org.json:json` como `testImplementation` de `:data`; no entra en el APK ni cambia el código de producción.
3. **Defecto real de D1 en el anclaje de canje:** `verificarAnclajeCanje` exigía siempre una capa interna
   `{license, sig}`, mientras el sobre del canje llega tal cual (`{v, alg, kid, license, sig}`, la misma forma que
   valida DShalev en `_validar_sobre`). El flujo legítimo se bloqueaba. Ahora se elige la capa que trae contenido
   **y** firma; sin ninguna, falla por firma ausente en lugar de por NPE.

---

## 5. Auditoría de ejes transversales: módulos, iconos, recorridos, listas, entrada y gráficos (2026-11-24)

Extiende la matriz de §1, que cubre módulos y funciones, a los seis ejes que el propietario pidió verificar por
separado. **Método:** lectura dirigida del código de las tres plataformas y conteo de símbolos reales. En este
entorno no hay Gradle, Xcode ni navegador, así que nada de lo que sigue es una prueba de ejecución: es inventario
verificado en el árbol. Los recuentos son reproducibles con los comandos que se citan.

### 5.1 Módulos

Los módulos del negocio son **tres** y se llaman igual en las tres plataformas (`VENTAS`, `INVENTARIO`,
`SERVICIOS`), con la misma regla de arrastre (VENTAS activa INVENTARIO).

| Aspecto | Android | iOS | DShalev | Estado |
|---|---|---|---|---|
| Definición | `Modulo.kt:12` (`etiqueta` + `detalle`) | `SettingsView.swift` (`moduleRow`) con los mismos textos | Sin selector: el módulo es implícito (todo visible) | Igual en Android/iOS; **Falta en DShalev** el selector |
| Regla de arrastre | `Modulo.alternar/normalizar` (VENTAS↔INVENTARIO) | `toggleSales`/`toggleInventory`/`toggleServices` | — | Igual en Android/iOS |
| Efecto en navegación | `topLevelVisibles()` filtra Inventario/Servicios | `visibleSections` en `AppRoot.swift:347-356` | El rail de `base.html` muestra todo siempre | **Falta en DShalev** |
| Persistencia | DataStore (`OnboardingPreferences`) | `@AppStorage("shalev.module.*")` | — | Igual en Android/iOS |
| Pestañas de la barra | 5: Inicio, Inventario, Servicios, Registros, Ajustes | **6**: las 5 + **Herramientas** (`MainSection.tools`, `AppRoot.swift:66`) | **14** enlaces en el rail (`base.html:18-31`) | **Diferencia de plataforma (aprobada)** en iOS: Herramientas agrupa fichas de costo y ajustes de precio. **Falta en DShalev**: módulos |

Conclusión del eje: los módulos y su efecto están alineados en las dos apps nativas; DShalev no tiene el concepto
de módulo (muestra todo el rail), lo que es una diferencia de producto pendiente de decisión, no un defecto de
implementación.

### 5.2 Iconos

| Aspecto | Android | iOS | DShalev | Estado |
|---|---|---|---|---|
| Catálogo propio | `ShalevIcons.kt`: **184** trazados `ImageVector` | **7** `imageset` con el SVG exacto de Android (`ShalevHome`, `ShalevInventory2`, `ShalevManoRecibe`, `ShalevHistory`, `ShalevBuild`, `ShalevSettings`, `ShalevLogo`) | **14** SVG en línea, todos en `base.html` | Diferencia de plataforma (aprobada) |
| Iconos de la barra | `TopLevel` usa `ShalevIcons.*` | `MainSection.iconAsset` usa los `imageset` (mismo trazado) | SVG con los mismos `path` (Inicio, Inventario, Servicios…) | **Igual** en los 6 de navegación |
| Resto de la interfaz | 184 trazados reutilizables | **32** símbolos SF (`systemName:`) | Sin iconos fuera del rail | Diferencia de plataforma |

Evidencia de la igualdad de los iconos de navegación: el `path` de `ShalevHome.imageset/icon.svg` es
`M 10 20 v -6 h 4 v 6 h 5 v -8 h 3 L 12 3 2 12 h 3 v 8 z`, idéntico al de `base.html:18` y al `Home` de
`ShalevIcons`. No hay discrepancia de pictograma en la navegación principal; sí en la densidad (iOS usa SF
Symbols donde Android usa su catálogo).

### 5.3 Recorridos (walkthrough)

| Aspecto | Android | iOS | DShalev | Estado |
|---|---|---|---|---|
| Recorridos guiados | **51** `Recorrido` en `Recorridos.kt:197` (pestañas, formularios y ventanas), con **127** claves de objetivo (`ObjetivosRecorrido`) y persistencia `*_tour_shown` | **Ninguno**: no hay coach marks ni overlay de pasos | **Ninguno** | **Falta en iOS** y **Falta en DShalev** |
| Asistente de bienvenida | `OnboardingLogic.kt:16`: 8 pasos (`BIENVENIDA`, `TIPO_APP`, `ACCESO_CLAVE`, `OBJETIVO`, `EMPLEADOS`, `DATOS`, `ALERTAS`, `PRUEBA`) | `OnboardingView.swift:71-78`: 7 pasos (sin `ACCESO_CLAVE` como paso aparte; el bloqueo se ofrece en Ajustes y en un `Toggle` del propio asistente) | `setup.html` (primer inicio, sin pasos guiados) | Diferencia de plataforma (aprobada) en el asistente |
| Ayuda por pantalla | `PestanaAyuda` (9 pestañas) + 10 `AyudaVentana` con pasos numerados | Un solo manual con 13 `HelpTopic` agrupados por sección | `/ayuda` con guía y atajos | Diferencia de plataforma (aprobada) |

El walkthrough es la carencia más grande de esta auditoría: Android guía paso a paso 51 flujos (incluidas las
ventanas), mientras iOS y DShalev solo ofrecen el asistente inicial y un manual de lectura. No es un defecto de
implementación de iOS/DShalev, sino una funcionalidad que no se trasladó.

### 5.4 Listas

| Aspecto | Android | iOS | DShalev | Estado |
|---|---|---|---|---|
| Búsqueda | `TextosInventario.BUSCAR`, en Inventario, Servicios y Registros | `ShalevSearchField` en Inventario (`:119`), Servicios, Registros y Venta | Buscador en `/inventario`, `/ventas`, `/clientes` | Igual |
| Filtro | `InventarioFiltros` (tipo + alerta + categoría), `Ventana.FILTRO_*` | `InventoryFilterSheet` (tipo + alerta), `RecordsFilterState` (fechas, importe, pago) | `?q=&categoria=` y filtros de fecha en reportes | Igual, con menos dimensiones en DShalev |
| **Ordenación** | `ModoOrden` con **4 modos** ciclables (`CRONOLOGICO → ALFABETICO → IMPORTE_DESC → IMPORTE_ASC`), botón `InventarioTags.ORDENAR` | **No existe**: las listas se ordenan fijas por fecha (`RecordsView.swift:135`) o por nombre (`:161`) | Orden fijo por servidor | **Falta en iOS** y **Falta en DShalev** |
| Selección múltiple | Casillas + barra «N seleccionados» con exportar/borrar | Selección por pulsación larga (`InventoryView.swift:281-318`) | Formularios de una fila | Diferencia de plataforma (aprobada) |
| Exportación de la lista | `FormatoSalida`: PDF, Excel, **Imagen (lista de precios)**, **Tarjetas promocionales** | XLSX, CSV, PDF, **PNG de precios** y **tarjetas PNG** (`ExportService.swift:418-435`) | Solo **XLSX** (`/inventario.xlsx`); sin PDF, imagen ni tarjetas | **Falta en DShalev** |
| Exportación de registros | PDF/Excel con selección | XLSX/CSV/PDF | `/reportes/ventas.xlsx` | Igual salvo CSV extra en iOS |

### 5.5 Prevención de errores de entrada

Android centraliza la política en `FiltrosEntrada.kt`: un `enum` con **teclado + longitud máxima + filtro** por
campo (`TELEFONO`, `TARJETA`, `CARNE`, `ENTERO`, `DECIMAL`, `CANTIDAD`, `DINERO`, `PORCENTAJE`, `TRANSACCION`…), y
`aceptar(anterior, escrito)` que **rechaza el estado inválido mientras se escribe** (no solo al guardar), incluida
`fechaParcialCarne` para que el carné no admita un mes 13.

| Campo | Android | iOS | DShalev | Estado |
|---|---|---|---|---|
| Carné de identidad (licencia y perfil) | `FiltroEntrada.CARNE` (11 dígitos, fecha AAMMDD progresiva) + `Validators.carneCubano` (fecha real, no futura) | `License.swift:878 validCI`: **5–20 alfanuméricos**; en el perfil, `SettingsView.swift:353` usa la misma regla laxa. El cliente fijo sí exige 11 dígitos (`CustomerValidator`) | `solicitud.validar_carne_cubano`: 11 dígitos, siglo y fecha real, no futura | **Falta en iOS**: dos validadores de carné distintos y el del perfil acepta lo que la licencia y DShalev rechazan |
| Nombre | `NOMBRE` (letras Unicode, apóstrofo, guion, 2–80) | `validName` (`License.swift:870`), equivalente | `validar_nombre`, equivalente | Igual |
| Teléfono | `FiltroEntrada.TELEFONO` (8 dígitos, empieza por 5 o 6) + `Phone.esMovilCubano` | `CustomerValidator.normalizedPhone` (8→+53); sin comprobación de móvil 5/6 para SMS | `normalizedPhone` equivalente | Diferencia menor: iOS no distingue fijo de móvil cubano |
| Importes y cantidades | `DINERO` (2 decimales), `DECIMAL`/`CANTIDAD` (3), `PORCENTAJE` (0–100) con rechazo progresivo | `Decimal(string:)` + `.numberPad` por campo; validación al guardar | `decimal_quantity_milli` en servidor | Igual en el resultado, distinto en el momento de avisar |
| N.º de transferencia | `^[A-Za-z0-9-]{4,30}$` en `LicenciaForm` | `validTransactionNumber` (6–30) | `_RE_TX` (4–30) | **Discrepancia menor** iOS (6–30 frente a 4–30) |

### 5.6 Gráficos

| Gráfico de Android | Android | iOS | DShalev | Estado |
|---|---|---|---|---|
| Ventas por período | `ShalevBarChart` (`Charts.kt:139`) | `Chart { BarMark …}` (`HomeView.swift:397`) | `<progress>` por día (`dashboard.html:10`) | Igual en intención |
| Ganancia (venta vs costo) | `ShalevAreaChart` con **dos series** (`InicioScreen.kt:858`) | **No hay**: solo bloques de texto Costo/Ganancia (`HomeView.swift:451-476`, `metricBlock`) | No hay | **Falta en iOS**: el área de dos series no existe; iOS muestra los importes sin gráfico |
| Inventario por categorías | `ShalevDonutChart` con centro y leyenda | **No hay**: barras por tipo de artículo (`HomeView.swift:456`) | No hay | **Falta en iOS** el desglose por categoría (usa tipo, no categoría) |
| Métodos de pago | `ShalevDonutChart` | **No hay**: dos filas de texto (`paymentAccordion`) | No hay | **Falta en iOS** |
| Rankings (Top 3, servicios, empleados, clientes, locales) | 8 listas con medalla oro/plata/bronce (`InicioScreen.kt:905-968`) | 3 (`Más vendido`, `Lento movimiento`, `Rentabilidad`) | Top 3 en el panel | **Falta en iOS**: servicios, empleados, clientes y **ranking de locales** (`topPersonas(TipoTop.LOCAL, …)`) |
| Períodos | 5: `TURNO, HOY, SEMANA, MES, ANIO` (`Estadisticas.kt:19`) | 5 equivalentes (`HomePeriod`, `HomeView.swift:6`) | Fechas libres | Igual Android/iOS |
| Accesibilidad del gráfico | Descripción textual por gráfico (`descripcionDona`, `description`) | `chartYAxis(.hidden)` + texto explicativo | `aria-label` en cada barra | Igual |

Los gráficos son el segundo hueco: iOS y DShalev **no tienen donut** ni el área de dos series, y a iOS le faltan
cinco de las ocho listas de ranking, entre ellas el ranking de locales que Android añadió en 1.0.6–1.0.7.

### 5.7 Resumen de carencias por plataforma

Prioridad alta (funcionalidad ausente que el usuario nota):

1. **iOS:** recorridos guiados (0 de 51), ordenación de listas (0 de 4 modos), gráfico de área de ganancia, donuts
   de inventario y métodos de pago, y 5 listas de ranking (servicios, empleados, clientes, locales).
2. **DShalev:** selector de módulos y su efecto en la navegación, exportación de inventario a PDF/imagen/tarjetas
   (solo hay XLSX), ajuste masivo de precios (no existe) y recorridos guiados.
3. **iOS (corrección, no carencia):** el validador de carné del perfil acepta 5–20 alfanuméricos donde la licencia
   y DShalev exigen 11 dígitos con fecha real.

No se ha cambiado código en esta auditoría: es un inventario con evidencia. Cualquier corrección de los puntos
anteriores debe decidirse con el propietario, porque varios son diferencias de producto (DShalev sin módulos) y no
defectos.
