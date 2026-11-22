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
