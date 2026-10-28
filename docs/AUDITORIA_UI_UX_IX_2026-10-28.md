# Auditoría UI · UX · IX — Shalev (Android) e IShalev (iOS) — 2026-10-28

Auditoría de **todas las ventanas y componentes** de las dos apps contra la skill
`ios-ui-ux-ix-design` (HIG, accesibilidad y Liquid Glass), con alcance explícito a las ventanas
emergentes: hojas, diálogos, alertas, `fullScreenCover`, hojas inferiores, FAB y sus menús,
tooltips, snackbars y velos.

- **Reglas aplicadas:** el documento adjunto `UI-UX-IX.md` como checklist operativo. Las reglas de
  iOS se aplican tal cual en IShalev; en Shalev (Android/Compose) se **mapean a su equivalente
  nativo** (decisión del dueño): Safe Areas → `WindowInsets`, Dynamic Type → `fontScale`/`sp`,
  Reduce Motion → `ANIMATOR_DURATION_SCALE`, VoiceOver → TalkBack, SF Symbols → `ShalevIcons`,
  Liquid Glass → no aplica.
- **Prioridad usada en caso de conflicto** (la del documento): accesibilidad → seguridad y
  privacidad → claridad → consistencia con la plataforma → estética.
- **Alcance medido:** IShalev 16 ficheros de UI (`Sources/App`, `Sources/Design`,
  `Sources/Features`, 10 000 líneas) y Shalev 143 ficheros Kotlin de `:app` + `:designsystem`
  (24 907 líneas).
- **Las líneas citadas son del commit auditado `d125513`**, anterior a las correcciones de esta
  rama; el estado de cada hallazgo dice si ya se corrigió aquí.
- **Lo que NO se pudo verificar en este entorno:** no hay JDK, Android SDK ni Xcode/Swift, así que
  no se ejecutaron `shalevTests`, `shalevCheck` ni `xcodebuild`. Tampoco se probó con VoiceOver o
  TalkBack reales, ni en dispositivo. La verificación queda en el CI
  (`.github/workflows/ios-build.yml` y `ci.yml`) y en las pruebas de `Pruebas.md`.
- **Liquid Glass:** fuera del alcance por decisión del dueño (el `deployment target` de IShalev es
  iOS 17.0 y el documento solo lo aplica con target 26+).

## Resumen

IShalev tenía **dos incumplimientos bloqueantes** de las reglas MUST del documento (tipografía fija
en toda la app y 19 controles por debajo del mínimo táctil) y siete altos; **se corrigieron aquí los
dos bloqueantes y seis de los siete altos** —el séptimo, IX-013, queda mitigado con un tope de escala
y pendiente de portar el reflow que Android ya tiene—. Shalev sale bien parada en el checklist
mapeado, pero la auditoría destapó que **la build estaba rota en `main`**: un paso de ayuda de 136
caracteres incumple el máximo de 110 que exige `AyudaSoporteTest` y hacía fallar `shalevTests` desde
el commit base (AND-009, corregido aquí). Los otros dos hallazgos de Android son decisiones
pendientes del dueño (casilla de 24 dp y permisos en lote al abrir la app), no descuidos.

| App | Bloqueante | Alta | Media | Baja | Corregidos aquí | Pendientes |
|---|---|---|---|---|---|---|
| IShalev (iOS) | 2 | 7 | 4 | 2 | 10 | 3 |
| Shalev (Android) | 0 | 2 | 3 | 1 | 1 | 3 |

Contabilizan como hallazgo las filas con severidad; las desviaciones aceptadas (UI-009, AND-005) y
las justificadas (AND-004) no suman en «pendientes», y las filas de verificación (AND-006, AND-007,
AND-008, UX-015) tampoco: son cumplimiento comprobado, no hallazgos.

---

## 1 · Cobertura: ventanas y emergentes auditados

### IShalev (iOS 17.0, SwiftUI)

| Ventana | Fichero | Emergentes que presenta |
|---|---|---|
| Raíz + barra de pestañas | `App/AppRoot.swift` | `fullScreenCover` de venta, hoja de Licencia, 2 alertas, pantalla de bloqueo y onboarding |
| Inicio | `Features/HomeView.swift` | hoja de turno (medium/large), 3 alertas |
| Inventario | `Features/InventoryView.swift` | 4 hojas (filtro, exportar, ficha/alta-edición, compartir), 5 alertas |
| Servicios | `Features/ServicesView.swift` | 2 hojas (filtro, ficha), 2 alertas |
| Registros | `Features/RecordsView.swift` | 4 hojas (ficha, filtro, compartir, corrección), 6 alertas |
| Ajustes | `Features/SettingsView.swift` | 6 hojas (asistente, rutas, edición, alta, ajuste, contraseña), 10 alertas |
| Venta (pantalla completa) | `Features/SaleView.swift` | hoja de catálogo, 1 alerta, pie fijo translúcido |
| Licencia | `Features/LicenseView.swift` | 1 alerta |
| Asistente inicial | `Features/OnboardingView.swift` | pie fijo translúcido |
| Bloqueo del dispositivo | `Features/DeviceLockView.swift` | — |
| Empleados y permisos | `Features/EmployeeSettingsView.swift` | 1 hoja, 3 alertas |
| Sincronización LAN | `Features/SyncView.swift` | 4 hojas (alta, vincular, empleado, QR), 7 alertas |
| Actualizar | `Features/UpdatesView.swift` | — |
| Soporte | `Features/SupportView.swift` | — |
| Exportar / compartir | `Features/ExportService.swift` | hoja `ShareSheet` (`UIActivityViewController`), 1 alerta |
| **Design system** | `Design/DesignSystem.swift` | 11 componentes reutilizables + QR |

**Total: 15 ventanas y 59 emergentes** (25 `sheet`, 1 `fullScreenCover`, 34 `alert`,
7 `confirmationDialog`, 1 hoja de compartir).

### Shalev (Android, Compose + Material 3)

| Ventana | Fichero | Emergentes |
|---|---|---|
| Inicio | `app/inicio/InicioScreen.kt` | 3 diálogos, FAB + menú expandible, 2 snackbars |
| Inventario | `app/inventario/InventarioScreen.kt` | 1 diálogo, 5 hojas inferiores, FAB, 2 snackbars |
| Producto / Insumo / Servicio (formularios) | `app/producto/`, `app/insumos/`, `app/servicios/` | guardia de cambios sin guardar, snackbars |
| Servicios | `app/servicios/ServiciosScreen.kt` | 1 diálogo, 3 hojas, FAB |
| Venta | `app/venta/VentaScreen.kt` | 1 diálogo («Nueva venta»), barra inferior de acciones, 2 snackbars |
| Registros / Turno / Corrección / Movimientos / Clientes | `app/registros/` | 4 hojas, 3 diálogos, 1 tooltip |
| Ajustes, Perfil, Precios, Pago electrónico, Licencia, Respaldo, Ayuda, Soporte, Onboarding | `app/ajustes/`, `app/perfil/`, `app/precios/`, `app/pagos/`, `app/licencia/`, `app/respaldo/`, `app/ayuda/`, `app/soporte/`, `app/onboarding/` | 6 diálogos, 3 hojas, snackbars |
| Vinculación + lector QR + bloqueo de secundaria | `app/vinculacion/` | 8 diálogos, 2 FAB, 3 snackbars |
| Puertas de la app | `app/root/`, `app/acceso/`, `app/bloqueo/` | diálogo de permisos iniciales, pantalla de bloqueo por clave |
| **Design system** | `designsystem/.../component/` | 15 componentes + `Overlays.kt` (diálogo, hoja inferior, snackbar, FAB simple y expandible) |

**Total: 20 rutas (`navigation/Routes.kt`) + 2 puertas, 47 diálogos `ShalevDialog`, 26 hojas
inferiores, snackbars en todas las listas, 12 FAB y 6 tooltips.**

---

## 2 · Hallazgos en IShalev (iOS)

Formato del documento: severidad, regla incumplida, evidencia, recomendación y criterio de
aceptación.

| ID | Pantalla/Flujo | Severidad | Regla | Evidencia | Recomendación | Criterio de aceptación | Estado |
|---|---|---|---|---|---|---|---|
| IX-001 | **Toda la app** (15 ventanas, 11 componentes) | **Bloqueante** | MUST «Soportar Dynamic Type y tamaños de accesibilidad»; MUST NOT «fuentes fijas que ignoren Dynamic Type» | **483 tamaños fijos**: 482 `.font(.system(size:))` (9–54 pt) + 1 `.custom("Orbitron-Bold", size: 23)` en `Design/DesignSystem.swift:78`. Cero usos de `dynamicTypeSize`. Distribución: 131 de 12 pt, 88 de 13, 82 de 11, 71 de 14… | Añadir `ShalevDynamicType` (escala leída del entorno en la raíz) y `Font.shalev(_:weight:design:)` / `.shalevMarca(_:)`; convertir las 483 llamadas. Los tamaños base no cambian (categoría Large = factor 1) | Con la letra del sistema en XXXL el texto crece y nada se corta; en Large la interfaz es **idéntica** a la anterior | **Corregido** |
| IX-002 | Design system, Inventario, Registros, Servicios, Licencia, Ajustes | **Bloqueante** | MUST «áreas táctiles mínimas de 44×44 pt»; MUST NOT «áreas menores de 44×44» | **19 controles por debajo del mínimo**: `ShalevIconButton` 42×42 (`DesignSystem.swift:125`), chevrons del stepper 36×36 y 38×38 (`:153`, `:176`), chip de filtro 36 pt (`:282`), botón de filtrar y de exportar 42×42 (`InventoryView.swift:191`, `:208`; `RecordsView.swift:204`, `:222`; `ServicesView.swift:52`), casilla de selección **30×44** (`InventoryView.swift:475`), chips «Quitar filtro» de 26–27 pt (`InventoryView.swift:235`, `RecordsView.swift:250`, `ServicesView.swift:79`), stepper de Licencia 38/42 (`LicenseView.swift:243`, `:274`), −/+ de cantidad 40×40 (`:532`), editar y borrar de cobro **34×34** (`SettingsView.swift:690`, `:697`) y botón redondo 40×40 (`:741`) | `View.shalevToque()`: `frame(minWidth: 44, minHeight: 44)` + `contentShape(Rectangle())` aplicado **fuera** del dibujo, para que crezca la zona del dedo y no el icono | Ningún control interactivo mide menos de 44×44 pt; el tamaño visible de los iconos no cambia | **Corregido** |
| IX-003 | Pestañas, Inventario, Ajustes (pago electrónico y Ayuda), botón primario | **Alta** | MUST «Respetar Reduce Motion»; MUST NOT «abusar de animaciones si el usuario activó Reduce Motion» | Cero usos de `accessibilityReduceMotion`. Animaciones incondicionales: `AppRoot.swift:340` (pestañas), `InventoryView.swift:228` (quitar filtro), `SettingsView.swift:722` (FAB expandible), `SettingsView.swift:1897` (acordeones de Ayuda) y `scaleEffect` al pulsar (`DesignSystem.swift:210`) | `ShalevMotion.cambio(_:)` devuelve `nil` con Reduce Motion (el cambio se aplica de inmediato) y el `scaleEffect` del botón primario se anula | Con «Reducir movimiento» activo, cambiar de pestaña, quitar un filtro, abrir el FAB y un acordeón no desplazan ni funden nada; el botón sigue dando respuesta inmediata por opacidad | **Corregido** |
| IX-004 | Barra de pestañas, pie del asistente, pie de la venta | **Alta** | MUST «Respetar Reduce Transparency cuando esté activo» | `.ultraThinMaterial` incondicional en `AppRoot.swift:358`, `OnboardingView.swift:267` y `SaleView.swift:504`. Cero usos de `accessibilityReduceTransparency` | `ShalevBarBackground`: material translúcido u opaco (`Color.shalevSurface`) según el ajuste | Con «Reducir transparencia» las tres barras son opacas y el contraste del texto de encima no cambia; sin el ajuste se ve igual que antes | **Corregido** |
| IX-005 | Venta, Vinculación (lector QR) | **Alta** | MUST «feedback visual o háptico inmediato tras una interacción» | Cero generadores hápticos en toda la app (`UIImpactFeedbackGenerator`/`notificationOccurred`: 0 usos), mientras Android vibra al leer un código, al terminar la venta y con cada mensaje (`designsystem/.../Haptics.kt`) | `ShalevHaptics` (`exito`, `aviso`, `seleccion`) y llamada en `SaleView.completeSale()` y en `SyncView.submit(_:)`, éxito y error | Al registrar una venta y al leer/pegar un código de vinculación el teléfono vibra una vez; si falla, vibra distinto. Sin permiso nuevo en `Info.plist` | **Corregido** |
| A11Y-006 | Inventario, Servicios, Ajustes, Vinculación, Onboarding | **Alta** | MUST «etiquetas accesibles en todos los elementos interactivos para VoiceOver» | `ProgressView()` sin etiqueta: `InventoryView.swift:994`, `ServicesView.swift:277`, `SettingsView.swift:1171`, `SyncView.swift:669`; `ProgressView(value:total:)` del asistente sin valor (`OnboardingView.swift:81`); el botón de sincronizar siempre se anuncia «Sincronizar ahora» aunque esté trabajando (`SyncView.swift:170`) | `accessibilityLabel("Cargando")` / `("Vinculando…")`, `accessibilityLabel("Progreso del asistente") + accessibilityValue("Paso N de M")` y etiqueta dinámica en el botón | VoiceOver anuncia el estado de cada progreso; ningún indicador queda mudo | **Corregido** |
| A11Y-007 | Stepper (`ShalevStepper`) | **Alta** | Accesibilidad: «añadir etiquetas y valores cuando aplique» | El indicador «Paso N de M» solo combinaba los dos textos (`DesignSystem.swift:184`) y la barra de progreso no exponía nada; en toda la app había **1** solo `accessibilityValue` | Etiqueta «Progreso del asistente» + valor «Paso 2 de 3: título» y barra marcada como decorativa (`accessibilityHidden(true)`) para no duplicar | VoiceOver lee el paso una sola vez, con el número y el título; la barra no se anuncia | **Corregido** |
| A11Y-008 | Barra de pestañas | **Alta** | Accesibilidad: «los controles personalizados exponen semántica accesible» | La barra es un `HStack` de botones (`AppRoot.swift:331-361`): anuncia el nombre y la selección, pero no la posición; Android sí lo hace con pestañas reales («pestaña, seleccionada, 2 de 4») | `accessibilityValue("Pestaña N de M")` recorriendo `visibleSections.enumerated()` | VoiceOver dice «Inventario, Pestaña 2 de 5, seleccionada» | **Corregido** |
| UI-009 | Barra de pestañas | Media | MUST «Mostrar etiquetas de texto en la Tab Bar; no usar solo iconos» | Barra propia de 5 destinos, solo iconos (`AppRoot.swift:331-361`), igual que Android (`Navigation.kt:52-53`, `label = null`) | **Desviación aceptada por el dueño**: se mantienen los iconos en las dos apps. Mitigaciones: nombre por VoiceOver, posición (A11Y-008), selección por relleno + inversión (no solo color) y SF Symbols de significado inequívoco (`house.fill`, `archivebox.fill`, `storefront.fill`, `clock.arrow.circlepath`, `gearshape.fill`) | Documentado en `UI_UX_IX.md` con las mitigaciones; VoiceOver anuncia nombre, posición y estado | **Desviación aceptada** |
| UI-010 | Componentes con texto en altura fija | Media | UI: «evitar densidades excesivas», «adaptar el layout» | Con la letra escalada, los contenedores de altura fija recortan: campo de búsqueda `height: 50` (`DesignSystem.swift:265`), chip `height: 36` (`:282`), insignias de 26–27 pt (`InventoryView.swift:235`, `RecordsView.swift:250`, `ServicesView.swift:79`) | `frame(minHeight:)` en el design system y en las insignias, para que crezcan con la letra | En XXXL el campo de búsqueda y los chips crecen en vez de recortar el texto | **Corregido** (parcial: falta el reflow completo, ver IX-013) |
| UI-011 | Sincronización (QR de empleado) | Baja | Consistencia: «sin estilos sueltos» (regla 4 de `AGENTS.md`) | Tipografías y colores del sistema fuera de la paleta: `.font(.headline)` y `.font(.caption)` con `.foregroundStyle(.secondary)` y `.red` (`SyncView.swift:606-611`), `.stroke(.secondary)` (`:665`) y `.buttonStyle(.borderedProminent)` (`:668`) | Pasar a `Font.shalev`, `Color.shalevSecondaryText`, `Color.shalevError`, `Color.shalevOutline` y `ShalevPrimaryButtonStyle` | Ninguna pantalla usa tipografías, colores ni estilos de botón del sistema fuera del design system | **Corregido** |
| IX-012 | Formularios (Producto, Insumo, Perfil, Licencia, Respaldo) | Media | SHOULD «minimizar escritura»; teclado que avanza | Cero `.submitLabel` y cero `@FocusState`: el teclado siempre muestra «return», mientras Android tiene «Siguiente» entre campos y «Listo» en el último (`TextFields.kt`, `imeAction`). Solo 2 `.textContentType` en toda la app, así que el autocompletado de iOS no se aprovecha | `@FocusState` por formulario + `.submitLabel(.next/.done)` + `.textContentType` en nombre, teléfono y correo | En cada formulario el teclado avanza al campo siguiente y el último guarda; iOS sugiere datos de contacto | Pendiente |
| IX-013 | Toda la app | **Alta** | MUST «soportar tamaños de texto de accesibilidad» | La escala nueva tiene **tope 1,6×** (categoría AX1). Por encima (AX2–AX5) el texto se serviría en AX1 porque faltan disposiciones alternativas: Android sí las tiene (`letraGrande()` a partir de escala 1,3: botones sin altura máxima y filas que apilan el importe bajo el nombre, `theme/Theme.kt`, `Surfaces.kt`) | Portar el reflow de Android: filas que apilan valor bajo el título, barras que pasan a columna y alturas máximas que desaparecen con `isAccessibilitySize` | En AX5 el texto crece de verdad y ninguna fila, tarjeta ni barra se recorta | Pendiente (mitigado por el tope) |
| UX-014 | Listas (Inventario, Registros, Servicios, Inicio) | Media | SHOULD «pull to refresh cuando el contenido pueda cambiar»; «swipe actions para acciones secundarias» | Cero `.refreshable`, cero `.swipeActions` y cero `.searchable` sobre 21 `List`/`ScrollView`. La búsqueda usa `ShalevSearchField` propio en el contenido, no en la barra | Decisión de producto: la app es local y sin servidor, así que no hay nada que «refrescar» de la red; las acciones secundarias ya están en la barra de selección y en la ficha. Se anota como **desviación justificada por paridad con Android** | Documentado; si se añade la sincronización LAN a las listas, `.refreshable` ahí sí aplica | Pendiente (decisión) |
| UX-015 | Hojas y `fullScreenCover` | Baja | Modales: «incluir una acción clara para cerrar», «confirmar antes de descartar cambios» | Cumple: las 25 hojas llevan `.presentationDragIndicator(.visible)` y detents explícitos; la venta (pantalla completa) tiene «Cancelar» en la barra y «xmark» en el pie (`SaleView.swift:86`, `:488`); 23 acciones con `role: .destructive` | No hace falta cambio. Se anota como verificado | — | Verificado |

---

## 3 · Hallazgos en Shalev (Android) — reglas mapeadas

| ID | Pantalla/Flujo | Severidad | Regla (equivalente Android) | Evidencia | Recomendación | Criterio de aceptación | Estado |
|---|---|---|---|---|---|---|---|
| AND-001 | Inventario, Servicios, Registros, Clientes fijos, Pago electrónico | **Alta** | MUST área táctil ≥ 44 pt (Android: `ShalevSize.touchTarget = 48.dp`) | La casilla mide **24×24 dp** (`designsystem/.../Casillas.kt:39-51`, token `casilla = 24.dp` en `token/Tokens.kt`) y es la única entrada a la selección múltiple; ya lo recogía `docs/AUDITORIA_UI_2026-10-21.md` (hallazgo 1, medido con `createComposeRule`: 48×48 px a xhdpi = 24 dp) | `size(ShalevSize.touchTarget)` conservando el dibujo de 16 dp (el cuadro ya se pinta centrado) y **regrabar** las capturas de Inventario, Servicios, Registros, Clientes y Pago electrónico | `AuditoriaUiTest` mide el nodo ≥ 48 dp y `verifyRoborazziDebug` pasa con las capturas nuevas | **Pendiente** (necesita JDK + Android SDK para regrabar; decisión del dueño desde 0.32.0) |
| AND-002 | Permisos de la primera apertura | Media | MUST «pedir permisos just-in-time»; MUST NOT «solicitar permisos al abrir la app sin contexto» | `root/PermisosIniciales.kt:40-49` agrupa CÁMARA + AVISOS + FOTOS y los pide en **una** ventana al abrir por primera vez (`:96-124`). Mitigaciones reales: cada permiso se explica con su para qué, «Ahora no» no insiste y cada función vuelve a pedirlo donde hace falta | Mover CÁMARA y FOTOS a la petición contextual (ya existe en el escáner y en la foto del artículo) y dejar en la ventana inicial solo el aviso de turno, o mantenerlo como decisión documentada | Ningún permiso peligroso se pide sin que el usuario haya iniciado la tarea que lo necesita | **Pendiente** (decisión del dueño; el lote inicial es deliberado desde 0.26.0) |
| AND-003 | Listas | Media | SHOULD «pull to refresh» | Cero `PullToRefresh`/`pullRefresh` en `:app` y `:designsystem` | Igual que en iOS: app local sin servidor. Se anota como desviación justificada | — | Pendiente (decisión) |
| AND-004 | Listas (Inventario, Registros) | Media | SHOULD «swipe actions para acciones secundarias» | Cero `swipeToDismiss`. Las acciones secundarias viven en la barra de selección («N seleccionados»: Marcar todo, Exportar, Eliminar, Quitar) y en la ficha | Desviación justificada: el patrón documentado es **«seleccionar ≠ abrir»** (casilla para marcar, toque para abrir) y añadir swipe sobre la misma fila crearía gestos ambiguos junto a `deslizarEntreSecciones` | — | Desviación justificada |
| AND-005 | Barra inferior | Baja | MUST «etiquetas de texto en la Tab Bar» | `Navigation.kt:52-53`: `label = null`, `alwaysShowLabel = false`, solo iconos con resaltado invertido; el nombre queda en el tooltip (`TooltipBox`) y en TalkBack | Misma **desviación aceptada** que en iOS (UI-009), por requisito de producto y paridad entre las dos apps | Documentado en `UI_UX_IX.md` | Desviación aceptada |
| AND-006 | Movimiento | — | MUST «respetar Reduce Motion» | **Verificado en la fuente del framework**, no de memoria: Compose observa `Settings.Global.ANIMATOR_DURATION_SCALE` (`androidx/compose/ui/ui/src/androidMain/.../WindowRecomposer.android.kt:95-119`) y lo expone como `MotionDurationScale`, cuyo `scaleFactor = 0` hace que «the motion will end in the next frame callback» (`androidx/compose/ui/.../MotionDurationScale.kt`). Todas las animaciones de Shalev salen de `ShalevMotion` (`token/Tokens.kt:116-179`: `tween`, `spring`, `EnterTransition`) y no hay `rememberInfiniteTransition` en la app | Ninguna | Con «Quitar animaciones» del sistema, pestañas, ventanas y listas cambian sin movimiento | Verificado (cumple) |
| AND-007 | Toda la app | — | MUST Safe Areas, Dynamic Type, contraste, etiquetas TalkBack, sin estilos sueltos | `MainActivity.kt:65` `enableEdgeToEdge` + recorte de notch en horizontal (`:73-76`); `imePadding` en los 7 formularios y en `VentaScreen`/`ModificarVenta`; `safeDrawingPadding` en los diálogos (`Overlays.kt:238`); tipografía íntegra en `sp` (`theme/Theme.kt:26-37`) con disposición alternativa a partir de escala 1,3; contraste AA verificado por test (`designsystem/src/test/.../ContrastTest.kt`); **0 literales `.sp` y 1 solo `.dp`** en `:app` (el umbral del gesto, `navigation/Deslizar.kt:58`); 109 `contentDescription` y 45 iconos decorativos con `contentDescription = null` | Ninguna | — | Verificado (cumple) |
| AND-008 | Navegación por gestos | — | MUST NOT «romper gestos nativos como deslizar desde el borde izquierdo» | `navigation/Deslizar.kt:38-41` lee `WindowInsets.systemGestures` y **descarta** los toques que empiezan en las franjas del sistema, así el gesto propio de cambiar de sección no choca con «atrás» | Ninguna | — | Verificado (cumple) |
| AND-009 | Ayuda → tema «Apps vinculadas» | **Alta** | Principio de **Claridad** («la interfaz debe ser limpia, legible y autoexplicativa») y la regla propia del proyecto: ningún paso de ayuda pasa de 110 caracteres (`app/src/test/.../AyudaSoporteTest.kt:36`) | Un paso de **136 caracteres** (`app/ayuda/AyudaContenido.kt:173`), añadido por el cambio de textos de 1.0.1 (2026-10-27) que admitió «wifi, zona wifi o datos móviles». El paso se lee de corrido, sin pausas, en una app para usuarios sin conocimientos técnicos. **Rompe la build**: `shalevTests` falla en `AyudaSoporteTest > manualBreveYCompleto` y el CI de `main` está en rojo desde el commit base `d125513` (run `40437981165`, PR #63), porque ese cambio se hizo sin poder ejecutar los tests | Reescribir el paso en 110 caracteres conservando la etiqueta real del botón («Usar esta app como secundaria», `vinculacion/VinculacionLogic.kt:20`), el teléfono, la acción de escanear y las tres redes | `shalevTests` en verde y el paso mide ≤ 110. **Ojo:** cambia el texto de la pantalla de Ayuda, así que hay que regrabar `app/capturas/*/07g_ayuda.png` (ya estaba pendiente por los textos de 1.0.1) | **Corregido** |

---

## 4 · Cambios aplicados en esta rama

Todo en ficheros existentes: el `project.pbxproj` lista los ficheros uno a uno, así que **no se añadió
ningún fichero nuevo** y el proyecto no necesita cambios.

| Pieza nueva | Dónde | Qué resuelve |
|---|---|---|
| `ShalevDynamicType` + `Font.shalev(_:weight:design:)` + `Font.shalevMarca(_:)` | `Design/DesignSystem.swift` | IX-001: **483 tipografías** pasan a escalar con Dynamic Type. La raíz instala la categoría (`AppRoot.swift`: `@Environment(\.dynamicTypeSize)` + `onAppear` + `onChange`) |
| `View.shalevToque(_:)` | `Design/DesignSystem.swift` | IX-002: **18 controles** ganan área táctil de 44 pt sin cambiar el dibujo; la casilla de Inventario pasa de 30×44 a 44×44 |
| `ShalevMotion.cambio(_:duracion:)` | `Design/DesignSystem.swift` | IX-003: **5 sitios** de movimiento respetan Reduce Motion |
| `ShalevBarBackground` | `Design/DesignSystem.swift` | IX-004: **3 barras** flotantes respetan Reduce Transparency |
| `ShalevHaptics` (`exito` / `aviso` / `seleccion`) | `Design/DesignSystem.swift` | IX-005: **4 llamadas** (venta registrada, venta fallida, código leído, código inválido) |
| Etiquetas y valores de VoiceOver | 6 ficheros | A11Y-006, A11Y-007, A11Y-008 |
| `frame(minHeight:)` en campo de búsqueda, chips e insignias | Design system + 3 pantallas | UI-010 |
| Paleta y estilos de Shalev en la pantalla de QR | `Features/SyncView.swift` | UI-011 |

Cifras de la conversión: 482 `.font(.system(size:))` → `.font(.shalev(...))` + 1 fuente de marca;
18 `.shalevToque()`; 4 `ShalevMotion.cambio`; 3 `ShalevBarBackground()`; 4 `ShalevHaptics`;
6 etiquetas/valores de accesibilidad nuevos. Los tamaños base no cambian (factor 1 en categoría
Large), así que **la interfaz en la configuración por defecto es idéntica a la anterior**.

### Shalev (Android): dos arreglos que desbloquean la build

El cambio 1.0.1 (2026-10-27) se hizo sin poder ejecutar los tests y dejó **dos** pruebas rotas; las
dos se arreglaron aquí. `main` fallaba en ambas desde el commit base `d125513` (run `40437981165`).

**1. `app/ayuda/AyudaContenido.kt:173` (AND-009).** El paso de «Apps vinculadas» medía **136
caracteres** y `AyudaSoporteTest` exige ≤ 110. Se reescribió en 110 conservando la etiqueta real del
botón («Usar esta app como secundaria», `vinculacion/VinculacionLogic.kt:20`), el teléfono, la acción
de escanear y las tres redes que 1.0.1 añadió a propósito (wifi, zona wifi y datos):

```
- "En el del empleado: Usar esta app como secundaria, escribe su teléfono y escanea tu código (misma red: wifi, zona wifi o datos móviles)."   (136)
+ "En el del empleado: «Usar esta app como secundaria», su teléfono, escanea tu código (wifi, zona wifi o datos)."                              (110)
```

Verificado sin JVM repasando **todas** las aserciones del test sobre el fichero: 13 temas (dentro de
6–14), títulos únicos, ningún tema con más de 5 pasos, longitud máxima 110 y las seis palabras clave
(«Abrir turno», «Nueva venta», «Inventario», «Respaldo», «Licencia», «Soporte») presentes. Ese paso
era la única aserción incumplida en todo el fichero. El CI lo confirmó: en la siguiente ejecución
`AyudaSoporteTest > manualBreveYCompleto` ya no aparece entre los fallos.

**2. `app/src/test/.../actualizacion/ProcesoActualizarTest.kt:114`.** `sinConexionNoSeCierraSolo()`
está escrita como `= runBlocking { … }` y su última expresión es `withTimeout { g.estado.first { … } }`,
que **devuelve el estado** en vez de `Unit`. El método JVM resultante no es `void`, y JUnit 4 exige
que todo `@Test` lo sea: la clase entera se rechaza al inicializar (`InvalidTestClassError`,
reportado como `initializationError`) y **ninguno de sus cuatro tests llegaba a correr**. Se añadió el
parámetro de tipo explícito `runBlocking<Unit>`, con lo que Kotlin descarta el valor de la última
expresión y el método vuelve a ser `void`, sin tocar la lógica. Los otros tres tests de la clase
terminan en `assertNull`/`assertTrue`, que sí devuelven `Unit`, por eso solo fallaba este.

Se rastreó el mismo patrón (`@Test fun x() = runBlocking { … }` con última expresión que no sea
`Unit`) en **todos** los tests JVM e instrumentados del repositorio: no hay ningún otro caso. El único
candidato, `RendimientoInicioTest.inicioConDieciochoMesesDeDatos`, termina en un `try/finally` cuyo
bloque acaba en `assertTrue` → `Unit`, y además el CI solo lo compila.

**Consecuencia a anotar:** el arreglo 1 cambia el texto de la pantalla de Ayuda, así que hay que
regrabar `app/capturas/claro/07g_ayuda.png` y `app/capturas/oscuro/07g_ayuda.png`, que **ya estaban
pendientes** de regrabar por los textos de 1.0.1. Este entorno no tiene JDK ni Android SDK para
hacerlo. El arreglo 2 hace que `ProcesoActualizarTest` corra de verdad por primera vez desde 1.0.1:
si alguna de sus cuatro pruebas falla ahora, será un fallo real de comportamiento que estaba oculto
tras el error de inicialización, no un daño de esta rama.

### IShalev: nombre de hápticos alineado con Android

`ShalevHaptics.aviso()` pasó a llamarse `.error()`, y su feedback de `.warning` a `.error`: Android
expone `exito()` / `error()` / `seleccion()` y el design system de iOS ahora tiene los tres mismos
nombres para el mismo concepto. Se actualizaron sus dos usos (`SaleView.swift:585`, `SyncView.swift:691`)
y `IShalev/README.md`.

---

## 5 · Checklist rápida del documento

| Pregunta | IShalev | Shalev |
|---|---|---|
| ¿Respeta Safe Areas? | ✅ `safeAreaInset` en la barra, `ignoresSafeArea` solo en fondos (7 sitios, todos `.background`) | ✅ edge-to-edge + insets + notch en horizontal |
| ¿Áreas táctiles ≥ 44 pt? | ✅ tras IX-002 | ⚠️ AND-001 (casilla 24 dp) |
| ¿Soporta Dynamic Type? | ✅ tras IX-001, con tope AX1 (IX-013) | ✅ `sp` + reflow a escala ≥ 1,3 |
| ¿Contraste en claro y oscuro? | ✅ paleta adaptativa heredada de Android (verificada por `ContrastTest`) | ✅ verificado por test |
| ¿Color como único indicador? | ✅ pastillas con icono + texto; la selección se ve por relleno e inversión | ✅ igual |
| ¿Etiquetas VoiceOver/TalkBack? | ✅ tras A11Y-006/007/008 (57 → 63 etiquetas) | ✅ 109 descripciones |
| ¿Respeta Reduce Motion? | ✅ tras IX-003 | ✅ por el framework (AND-006) |
| ¿Respeta Reduce Transparency? | ✅ tras IX-004 | n/a |
| ¿Permisos just-in-time? | ✅ cámara al abrir el lector, Face ID al cambiar de perfil, textos de uso en `Info.plist` | ⚠️ AND-002 (lote inicial) |
| ¿3–5 pestañas? | ✅ 5 (se ocultan las que el perfil no permite) | ✅ 5 |
| ¿Etiquetas de texto en la barra? | ⚠️ desviación aceptada (UI-009) | ⚠️ desviación aceptada (AND-005) |
| ¿Navegación Atrás clara? | ✅ `NavigationStack` en las 15 ventanas + «Cancelar» en la venta | ✅ `ShalevTopBar` con Atrás |
| ¿Acciones al alcance del pulgar? | ✅ barra inferior, pie fijo de la venta y FAB abajo | ✅ igual |
| ¿Feedback inmediato? | ✅ opacidad al pulsar + hápticos (IX-005) | ✅ `ShalevHaptics` + snackbar con vibración |
| ¿Estados vacío, carga, error y éxito? | ✅ `ShalevEmptyState` en 6 pantallas, «Sin resultados», errores con acción | ✅ en todas las listas |
| ¿Formularios minimizan escritura? | ⚠️ IX-012 (sin `submitLabel` ni `textContentType`) | ✅ `imeAction` y autocompletado |
| ¿Liquid Glass con moderación? | Fuera de alcance (target iOS 17) | n/a |

---

## 6 · Pendientes y orden sugerido

1. **IX-013 (Alta)** — reflow completo con tamaños de accesibilidad (AX2–AX5) en IShalev, copiando
   el `letraGrande()` de Android. Es lo que permite quitar el tope de 1,6×.
2. **AND-001 (Alta)** — casilla de 48 dp en Android + regrabar las 274 capturas afectadas
   (requiere JDK y Android SDK).
3. **IX-012 (Media)** — `@FocusState` + `.submitLabel` + `.textContentType` en los formularios de
   IShalev.
4. **AND-002 (Media)** — decidir si los permisos de la primera apertura siguen en lote o pasan a
   petición contextual.
5. **UX-014 / AND-003 (Media)** — decidir si se añade `.refreshable`/`PullToRefresh` en las pantallas
   con sincronización LAN (en el resto no hay nada que refrescar).

## 7 · Supuestos

- Se asume que IShalev mantiene el `deployment target` en iOS 17.0 y que Shalev mantiene Material 3
  (no se han tocado ni el tema ni los tokens de color de ninguna de las dos).
- Se asume que la paridad entre las dos apps pesa más que la diferencia de plataforma en los cuatro
  puntos donde iOS y Android se separan de la HIG a la vez (barra sin etiquetas, sin pull to refresh,
  sin swipe actions, permisos iniciales en lote); todos quedan documentados como desviación.
- La escala de Dynamic Type se instala en `ShalevRootView`; las ventanas presentadas antes de que la
  raíz aparezca (bloqueo, onboarding) usan el factor por defecto hasta el primer `onAppear`.
