# Plan de mejoras UI/UX/IX — Shalev y DShalev

**Fecha:** 2026-11-11 · **Rama:** `arena/25158c7e-shalev` · **Estado:** ejecutado el 2026-11-11

## 0. Estado de ejecución (2026-11-11)

**DShalev — 10/10 completado.** `pytest` 209 en verde (201 previos + 8 nuevos de accesibilidad de formularios), `ramplint --no-django` sin hallazgos en las 24 plantillas y como paso del CI de Windows (sin baseline: sale limpio), `npm test` (jsdom) 6/6.

| ID | Estado | Qué quedó hecho |
|---|---|---|
| DS-1 | ✅ | `:focus-visible` global, `prefers-reduced-motion: reduce`, `scroll-padding-top` 90/78px en `app.css` |
| DS-2 | ✅ | Skip-link, `<main id="contenido">`, `<footer class="rail-footer">` (base/login/setup) |
| DS-3 | ✅ | `static/forms.js` (defer) con `data-manual` en las 5 plantillas con fetch propio |
| DS-4 | ✅ | `id`/`aria-describedby` en las 9 ayudas `<small>` de 8 plantillas |
| DS-5 | ✅ fase 1 | `FieldError`/`flash_field`/`flash_redirect(field=)`; inline en `/inventario/nuevo`, `/clientes/nuevo`, `/equipo/nueva`; CSS `[aria-invalid]`+`.campo-error`; tests nuevos. Extensión al resto de formularios: misma maquinaria, pendiente de demanda |
| DS-6 | ✅ | `aria-busy` en `#pos-lines` con `finally` al validar precios y al recuperar el carrito |
| DS-7 | ✅ | `ramplint==0.7.1` en `requirements-dev.txt` + paso en `dshalev-windows.yml` tras pytest (0 hallazgos) |
| DS-8 | ✅ | `/ayuda` con atajos, POS, respaldo, licencia y soporte (+5351815604); enlace en el rail |
| DS-9 | ✅ | `app.css` formateado a 1694 líneas verificando equivalencia canónica de tokens con tinycss2 |
| DS-10 | ✅ | Convenciones documentadas en `docs/DSHALEV_DISENO.md` §«Interfaz y accesibilidad» |

**Shalev — 7/7 completado (código a la espera del CI, sin SDK en el entorno de edición).**

| ID | Estado | Qué quedó hecho |
|---|---|---|
| SK-1 | ✅ | `ui-test-junit4-accessibility` 1.9.5 en el catálogo (no está en la BOM) + `androidTestImplementation` en `:app` + `AccessibilityTest` instrumentado (API 34+, emulador del CI). Nota aprendida: ATF/Compose no funciona en Robolectric (b/332778271), por eso no va en la suite JVM |
| SK-2 | ✅ | Previews privadas Light/Dark en Buttons/Surfaces/Indicators de `:designsystem` (+ `ui-tooling-preview` como `implementation` del módulo); regla documentada en `DESIGN_SYSTEM.md` §9-fin |
| SK-3 | ✅ (medido) | `IndicadorDesplazamiento` ya lee las banderas como lambdas dentro de `drawWithContent` (fase de dibujo): no hay recomposición que amortiguar; decisión registrada y criterio «medir primero» para futuras listas |
| SK-4 | ✅ | Piloto: `calculateWindowSizeClass` en `MainScaffold`; en Medium/Expanded el contenido del `NavHost` se centra a `ShalevSize.contentMaxWidth` (600dp), navegación intacta (el gesto se mueve al `Box` envolvente) |
| SK-5 | ✅ (no implementar) | Decisión documentada en `DESIGN_SYSTEM.md` (barra inferior = alternativa accesible; `BloqueoGestos`; reevaluar solo si se pide paginación visual) |
| SK-6 | ✅ | 3 literales de `CajaUi.kt` → `ShalevRadius.billeteCup` (6dp) y `ShalevSize.billeteCupAncho/Alto/Borde` (88/40/0.8dp); umbral de gesto 72.dp queda en `Deslizar.kt` como comportamiento |
| SK-7 | ✅ | Criterio diálogo-vs-Deshacer documentado en `UI_UX_IX.md` §2.2 |

**Pendiente de CI (requiere SDK/rodeos del repo):** `shalevTests` + compilación de instrumentados en verde con los cambios de `:app`/`:designsystem`; `AccessibilityTest` corriendo en CI; Roborazzi sin cambio de píxel esperado (los tokens de SK-6 conservan valores y el piloto SK-4 solo actúa en Medium/Expanded; las capturas son a 360dp = Compact).

Análisis de las dos aplicaciones contra las skills del proyecto:

| Skill | Ámbito | Aplicada a |
|---|---|---|
| `compose-ui` | Componentes, theming MD3, layout adaptativo | Shalev (Android) |
| `compose-ux` | Flujos, patrones MD3, estados, accesibilidad | Shalev (Android) |
| `compose-ix` | Gestos, animación, feedback físico | Shalev (Android) |
| `webui-ui` | HTML semántico, tokens, formularios accesibles | DShalev (web) |
| `webui-ux` | Estados, validación inline, microcopy, WCAG 2.2 | DShalev (web) |
| `webui-ix` | Animación, foco, reduced-motion, alternativas | DShalev (web) |
| `unslop` | Limpieza de slop (comentarios, código muerto) | Ambas |

Notas de alcance: DShalev usa FastAPI + Jinja2 con CSS propio y JS vanilla (sin HTMX, Tailwind ni Alpine). Las reglas de las skills web se aplican como **principios**; cuando una skill sugiere una biblioteca concreta (HTMX, Alpine) se adapta al stack real. IShalev queda fuera de este análisis.

---

## 1. Lo que ya cumple (verificado, no tocar)

### Shalev (Android)

- **Reutilización total de componentes:** `:designsystem` con ~39 composables; en `:app` hay 0 `TextField`/`OutlinedTextField`/`BasicTextField` crudos y 0 colores `Color(0x…)` fuera del tema; solo 4 literales `.dp` en pantallas (3 en `CajaUi.kt`, 1 umbral de gesto en `Deslizar.kt`).
- **State hoisting / UDF:** 30 archivos con `collectAsStateWithLifecycle`; estado en ViewModels, eventos hacia arriba.
- **Accesibilidad semántica activa:** `heading()` ×26, `mergeDescendants` ×8, `liveRegion` ×37, `contentDescription` obligatorio en `ShalevIconAction`.
- **Movimiento físico gobernado:** `ShalevMotion` (muelles sin rebote, fundidos 150–250 ms, respeta «Quitar animaciones» del sistema); hápticos centralizados (`CONFIRM`/`REJECT` con *fallback* por SDK).
- **Feedback ante doble toque:** `rememberAccionUnica` integrada en botones/overlays del design system.
- **Estados diseñados:** `ShalevEmptyState` ×41, `ShalevLoading` ×30, skeletons; confirmación en acciones destructivas (205 referencias a diálogos/guardias); errores en lenguaje llano vía `MensajesError`.
- **Entrada filtrada:** `FiltroEntrada` aplicado por `ShalevTextField` en cada pulsación + parsers de dominio (`Money.parse`, `Cantidad.parse`, `Validators`), con tests JVM (`FiltroEntradaTest`, `ValidatorsTest`, `MoneyTest`).
- **Regresión visual:** Robolectric + Roborazzi (claro/oscuro) en lugar de `@Preview`.

### DShalev (web)

- **Semántica base correcta:** `lang="es"`, `<header>`/`<nav aria-label>`/`<main>` único por página; exactamente 1 `<h1>` por plantilla (21 plantillas comprobadas); jerarquía h1→h2.
- **Formularios:** 74 `label>input` envolventes + 16 `label>select` (asociación válida WCAG); `maxlength`/`minlength`/`type`/`min`/`step` coherentes con la validación del servidor; `autocomplete` en contraseñas (WCAG 3.3.8: no bloquea pegado ni gestores).
- **Tokens y tema:** variables CSS en `:root`, tema oscuro con `data-theme="oscuro"` (21 reglas) y `theme.js` persistente; botones con `min-height: 42px`; transiciones de 120 ms (dentro de 100–200 ms).
- **Estados:** `empty-state` en 12 plantillas; `aria-live` ×4 (POS, respaldo, distribución); avisos de error en lenguaje llano verificados empíricamente (7 entradas inválidas rechazadas con mensaje claro, solo la válida se guarda).
- **Robustez de envío:** CSRF en las 37 rutas POST; POS deshabilita el botón y muestra «Registrando…»; ventas con deduplicación por `sale_key`; subidas con límite de tamaño y verificación de contenido.
- **Sin gestos de arrastre** → WCAG 2.5.7 no aplica (documentado).
- **CI:** `pytest` (201 tests en verde) en `dshalev-windows.yml`.
- **unslop:** 0 comentarios HTML, 0 TODO/FIXME reales; comentarios Python/Kotlin documentan «por qué» (se conservan según las reglas de la skill).

---

## 2. Plan de cambios — Shalev (Android)

| ID | Prioridad | Skill | Hallazgo | Cambio propuesto | Archivos | Esfuerzo |
|---|---|---|---|---|---|---|
| SK-1 | **P1** | compose-ux §5 | 0 tests automáticos de accesibilidad | Añadir `androidx.compose.ui:ui-test-junit4-accessibility` al catálogo y una regla base con `enableAccessibilityChecks()`; cubrir las 5 pantallas principales + Venta en la suite JVM/Robolectric | `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/sharedTest/`, nuevo `AccesibilidadTest` | M |
| SK-2 | P2 | compose-ui §7 | 0 `@Preview` (Roborazzi lo sustituye para regresión) | Previews privadas Light/Dark solo en componentes públicos de `:designsystem` (feedback rápido en IDE); documentar que Roborazzi sigue siendo la fuente de verdad visual | `designsystem/src/main/…/component/*.kt`, `DESIGN_SYSTEM.md` | S |
| SK-3 | P2 | compose-ui §3 | 0 `derivedStateOf`; indicadores ligados a scroll se recomponen por frame | Medir primero con métricas del compilador de Compose; aplicar `derivedStateOf` en `IndicadorDesplazamiento` y `remember` en lambdas de filas de Inventario/Registros solo donde el conteo lo justifique | `designsystem/…/IndicadorDesplazamiento.kt`, pantallas con listas | M |
| SK-4 | P3 | compose-ui §5 | 0 `WindowSizeClass` (app pensada para teléfono) | Decisión documentada; prueba piloto: `calculateWindowSizeClass` en `MainScaffold` + `widthIn(max = …)` del contenido en Medium/Expanded (tablets/plegables), sin cambiar navegación | `app/…/MainScaffold.kt`, `DESIGN_SYSTEM.md` | M |
| SK-5 | P3 | compose-ix §3 | El deslizar entre secciones no «sigue al dedo» (detección post-gesto) | **No implementar** por ahora: la barra inferior es la alternativa accesible y `BloquearGestos` evita conflictos con campos/listas. Documentar la decisión y reevaluar solo si se pide paginación visual | `docs` | XS |
| SK-6 | P3 | unslop | 4 literales `.dp` en `:app` | Mover los 3 de `CajaUi.kt` a `ShalevSize`; el umbral de 72.dp del gesto queda documentado en el componente (es comportamiento, no tema) | `CajaUi.kt`, `token/Tokens.kt` | XS |
| SK-7 | P3 | compose-ux §3.3 | Sin «Deshacer» (0 usos): hoy confirmación + registro auditable, decisión deliberada | Documentar el criterio: destructivo/irreversible → diálogo; reversible menor → Snackbar con deshacer cuando aparezca el primer caso | `UI_UX_IX.md` | XS |

**Criterio de aceptación (Shalev):** `shalevTests` verde donde haya SDK; `AccessibilityTest` nuevo en verde; capturas Roborazzi regeneradas si cambia píxel; 0 nuevos errores de lint.

---

## 3. Plan de cambios — DShalev (web)

| ID | Prioridad | Skill | Hallazgo | Cambio propuesto | Archivos | Esfuerzo |
|---|---|---|---|---|---|---|
| DS-1 | **P0** | webui-ix §2–3 | `:focus-visible` solo cubre el POS; sin `prefers-reduced-motion`; topbar fija puede tapar el foco (WCAG 2.4.11) | En `app.css`: regla global `:focus-visible` con doble anillo (blanco + `--blue`, offset 2px) para enlaces, botones, `nav-link`, `select`, checkbox/radio; `@media (prefers-reduced-motion: reduce){ *{transition:none!important} }`; `html{scroll-padding-top:90px}` | `static/app.css` | S |
| DS-2 | P1 | webui-ui §1 | Sin skip-link, `<main>` sin `id`, sin landmark `<footer>` | Añadir `<a class="skip-link" href="#contenido">Saltar al contenido</a>` (visible al enfocar), `id="contenido"` en `<main>`, y `<footer>` (o `role="contentinfo"`) para el pie del rail | `templates/base.html`, `static/app.css` | S |
| DS-3 | P1 | webui-ux §2 | Solo el POS deshabilita el botón al enviar; el resto de formularios admite doble envío | `static/forms.js` (defer, en `base.html`): en `submit` de todo `form[method=post]` sin `data-manual`, deshabilitar el botón enviado, rotular «Guardando…» y `aria-busy="true"` en el formulario. POS/backup/distribución se marcan `data-manual` (ya lo gestionan) | nuevo `static/forms.js`, `base.html`, plantillas con formularios JS | M |
| DS-4 | P1 | webui-ui §2 | `aria-describedby` solo ×1; las ayudas `<small class="tiny">` no están vinculadas | Vincular ayuda↔campo (`id` + `aria-describedby`) empezando por `login.html`, `setup.html`, `inventory.html`, `customers.html`, `employees.html`, `tools.html` | plantillas listadas | M |
| DS-5 | P2 | webui-ux §3 | Errores de validación llegan como banner global, sin marca en el campo | Errores inline por fases: `flash_redirect(…, field=)` → plantilla pone `aria-invalid="true"`, borde rojo e icono ⚠ + mensaje bajo el campo; el banner queda como resumen. Fase 1: `/inventario/nuevo`, `/clientes/nuevo`, `/equipo/nueva` | `app.py`, plantillas de esos formularios, `app.css` | M–L |
| DS-6 | P2 | webui-ix §4 | `aria-busy` ausente en zonas actualizadas por JS | `aria-busy` en `#pos-results` durante el fetch del catálogo y en el carrito durante la comprobación de precios | `static/pos.js`, `templates/sales.html` | S |
| DS-7 | P1 | webui-ui §6 | Sin linting de accesibilidad de plantillas | Añadir `ramplint` (0.7.1, PyPI) como job de CI: `ramplint DShalev/dshalev/templates` con baseline versionado; fallan los hallazgos nuevos | `.github/workflows/ci.yml`, `DShalev/requirements-dev.txt` | M |
| DS-8 | P2 | webui-ux §4 (3.2.6) | Sin punto de ayuda consistente | Enlace «Ayuda» fijo al final del rail (misma posición en todas las páginas) hacia una página interna con los atajos (F2 = buscar en Ventas) y el contacto de soporte | `base.html`, nueva ruta/plantilla `ayuda` | S–M |
| DS-9 | P3 | unslop | `app.css` en una sola línea (273 reglas, 20,7 KB) dificulta revisión y diffs | Formatear con sangría estándar; sin cambiar una sola declaración; verificar que la cobertura de capturas/CI no cambia | `static/app.css` | S |
| DS-10 | P3 | webui-ux §4 (2.5.7) | No hay gestos de arrastre | Sin acción; queda documentado aquí | — | — |

**Criterio de aceptación (DShalev):** `pytest` 201/201 en verde (más los nuevos tests de DS-3/DS-5); `ramplint` sin hallazgos tras el ajuste inicial; verificación manual con teclado (Tab por todas las páginas: foco siempre visible y nunca tapado por la topbar); `prefers-reduced-motion` comprobado en SO.

---

## 4. Orden de ejecución propuesto

1. **Fase 1 (rápida, alto valor):** DS-1, DS-2, SK-6, SK-7, DS-10 → accesibilidad de base y documentación, bajo riesgo.
2. **Fase 2 (feedback y formularios):** DS-3, DS-4, DS-6, SK-1 → anti doble envío, ayudas vinculadas y primera cobertura automática de accesibilidad.
3. **Fase 3 (rigor continuo):** DS-7 (ramplint en CI), SK-2 (previews), DS-8 (ayuda).
4. **Fase 4 (mejoras medibles/opcionales):** DS-5 (errores inline por campo, por fases), SK-3 (recomposición, solo si la medición lo justifica), SK-4 (pilot tablet), DS-9 (formato CSS), SK-5 (documentado, sin código).

## 5. Qué no se cambia (decisiones deliberadas preservadas)

- No se introduce HTMX/Tailwind/Alpine en DShalev ni un framework de UI nuevo en Shalev: las mejoras se hacen sobre el stack actual.
- Los comentarios con versión/decisión («0.21.6: la coma del teclado…») se conservan: son el «por qué» que pide `unslop`.
- La confirmación con diálogo en acciones destructivas y el rastro auditable sustituyen a «Deshacer»: patrón correcto para un POS (SK-7 solo lo documenta).
- `@Preview` masivas en `:app`: la regresión visual ya la cubre Roborazzi (SK-2 se limita a `:designsystem`).
