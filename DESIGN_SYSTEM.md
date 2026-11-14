# Shalev — Design System

Módulo `:designsystem` (`cu.shalev.designsystem`). **Regla única: ninguna pantalla define colores, tamaños, radios, duraciones ni estilos de texto propios.** Todo sale de los tokens y los componentes descritos aquí. Si hace falta algo nuevo, se añade primero al design system.

```
Android/designsystem/src/main/kotlin/cu/shalev/designsystem/
├── token/ColorTokens.kt   ← valores ARGB crudos (única fuente; ContrastTest los verifica)
├── token/Tokens.kt        ← ShalevSpacing, ShalevRadius, ShalevElevation, ShalevOpacity, ShalevSize, ShalevMotion
├── theme/Color.kt         ← LightColorScheme, DarkColorScheme, ShalevExtendedColors, AlertTone
├── theme/Theme.kt         ← ShalevTheme, ShalevTypography, ShalevShapes
├── icon/ShalevIcons.kt      ← iconos semánticos rellenos (+ WhatsApp vectorial)
├── component/             ← Buttons, TextFields, Surfaces, Indicators, Overlays, Navigation, Charts,
│                             Tabs, Stepper, Banner, Haptics (P18)
```

## 1. Tema

`ShalevTheme { … }` sigue al sistema con `isSystemInDarkTheme()`. **Sin dynamic color (Material You)**: la paleta de Android 12+ cambiaría los colores de marca y el contraste AA verificado dejaría de estar garantizado.

| Rol | Claro | Oscuro |
|---|---|---|
| background | `#F8F9FA` | `#0F1318` (0.21.3; antes `#121212`) |
| surface | `#FFFFFF` | `#1A1F26` (0.21.3; antes `#1E1E1E`) |
| onSurface | `#1A1C1E` (casi negro) | `#E6E6E6` (blanco suave) |
| onSurfaceVariant | `#5F6368` (gris oscuro) | `#B3B9C2` (gris azulado claro) |
| primary / onPrimary | `#2B4FA3` / blanco | `#66D1EF` / `#00212C` |
| secondary | `#B4541A` | `#F6A15C` |
| tertiary | `#2F6F80` | `#7FC4D4` |
| error | `#C62828` | `#EF9A9A` |
| outline | `#767B82` (0.21.1; antes `#8A8F96`) | `#8C939E` (0.21.3) |

**Elevación:**
- En modo **claro** la elevación se expresa con **sombras sutiles** (`ShalevElevation.low = 1dp` en las cards) y `surfaceTint = Transparent`.
- En modo **oscuro** la elevación es **tonal**: cuanto más alto el elemento, más clara su superficie, y no se usan sombras.
  - 0.21.3: todos los grises llevan un toque del azul de marca (antes neutros); la escala de luminancia es la misma.
  - `surfaceContainerLowest`: `#14181E`
  - `surfaceContainerLow`: `#1F252D`
  - `surfaceContainer`: `#242A33`
  - `surfaceContainerHigh`: `#2A313B`
  - `surfaceContainerHighest`: `#313944`
  - `outlineVariant`: `#363E4A`; ventana (`values-night/colors.xml`): `#0F1318`.

**Colores de marca:**
- El naranja `#EE7F3B` y el teal `#478EA1` de SVIPicono **no alcanzan AA con texto blanco** (2.72 y 3.72).
- Por eso solo se usan como decoración: el icono y el fondo del splash.
- Para texto y botones se usan sus variantes oscurecidas: secondary `#B4541A` y tertiary `#2F6F80`.

**Un significado por color (0.21.1):**
- `secondaryContainer` (naranja claro) = **solo avisos** (`BannerTone.Aviso`).
- `tertiaryContainer` (petróleo claro) = acciones secundarias: `IconActionStyle.Tonal` (✕ Cancelar, − / +) y los mini botones del menú +.
- `primary` = acción principal, chip elegido e indicador de la barra inferior. `error` = destructivo y «Sin existencia».

**Colores extendidos** (`ShalevTheme.colors`):
- `alert*`: un color para cada `AlertTone` (StockBajo, StockCritico, InsumoBajo, InsumoCritico, Caducidad). `SinExistencia` (0.21.1) usa el rojo de StockCritico, pero el contador de alertas de Inicio lo pinta **relleno** (`CardTone.Error`: fondo `error`, texto `onError`). `Revisar` (0.24.0, «Nombre repetido: añade descripción») no es de existencias: el contador pinta el número en `colorScheme.primary` (AA ya verificado en `ContrastTest`); no añade colores a la paleta.
- Insumo bajo: claro `#2F6F80`, oscuro `#7FC4D4` (= tertiary; 0.21.2, antes verde `#3D7A1F` / `#AED581`, que se confundía con `success`).
- Insumo crítico claro: `#9A3412` (0.21.1; antes `#B45309`, indistinguible del naranja de marca `#B4541A`).
- `chart`: claro `#2B4FA3 #B4541A #8B5E34 #A23B72 #6B6B00 #80868D`, oscuro `#66D1EF #F6A15C #D4A373 #F48FB1 #C5C56A #9E9E9E` (0.21.2: caramelo en vez de petróleo). 0.21.1: sin los tonos de las alertas (morado, verde, amarillo), comprobado en `ContrastTest.alertasDistintasDeMarcaYGraficos`.
- `success`.
- `whatsapp` (`#0B7A5E`) y `onWhatsapp`.

Se accede con `ShalevTheme.colors.of(AlertTone.StockCritico)`.

### Contraste (WCAG 2.1 AA)

Mínimos: 4.5:1 para texto normal y 3:1 para elementos de UI. `ContrastTest` falla el build si algún par baja de su mínimo.

| Par | Ratio |
|---|---|
| primary / blanco (claro) | 7.64 |
| secondary / blanco | 4.97 |
| tertiary / blanco | 5.67 |
| onSurfaceVariant claro | 6.05 |
| texto secundario 65 % claro | 5.33 |
| outline (UI) sobre blanco / diálogo (`surfaceContainerHigh`) / `Highest` | 4.26 / 3.63 / 3.47 |
| error claro | 5.62 |
| primary oscuro / onPrimary | 9.52 |
| onSurface oscuro sobre `#1A1F26` | 13.27 |
| onSurfaceVariant oscuro | 8.39 |
| texto secundario 65 % oscuro | 6.33 |
| error oscuro | 7.70 |
| tonal: onTertiaryContainer / tertiaryContainer (claro / oscuro) | 10.62 / 8.64 |
| «Sin existencia» onError / error (claro / oscuro) | 5.62 / 8.23 |
| alertas claro | ≥ 5.0 |
| alertas oscuro | ≥ 6.9 |
| WhatsApp `#0B7A5E` / blanco | 5.30 |

> El verde oficial de WhatsApp `#128C7E` da 4.14 con blanco y **no pasa**. Por eso se usa `#0B7A5E`.

## 2. Tipografía (`ShalevTypography`)

La escala es más pequeña que la de M3 por defecto: la interfaz es densa y de trabajo.

| Estilo | Tamaño / interlínea | Peso | Uso |
|---|---|---|---|
| headlineSmall | 20 / 26 | SemiBold | Títulos de bloqueo y onboarding |
| titleLarge | 18 / 24 | SemiBold | TopBar |
| titleMedium | 15 / 20 | SemiBold | Título de Card y diálogo |
| titleSmall | 13 / 18 | Medium | Banners |
| bodyLarge | 15 / 21 | Normal | Texto destacado |
| bodyMedium | 14 / 20 | Normal | Texto normal (P18: +1sp) |
| bodySmall | 13 / 18 | Normal | Texto secundario (P18: +1sp) |
| labelLarge | 13 / 18 | Medium | **Botones** |
| labelMedium / labelSmall | 12 / 11 | Medium | Chips y badges |
| **`ShalevTextos.dato`** (0.26.0) | 13 / 18 | **SemiBold (600)** | **Datos**: precios, importes, fechas, cantidades, contadores, vencimiento e ID de la licencia |

- **Seminegrita de datos (0.26.0, P73 §5):** `ShalevTextos.dato` (titleSmall en 600) es el valor de `ShalevListItem` (listas, fichas, licencia), los totales parciales, el «Esperado» del arqueo, la vista previa de precios y los datos de Inicio. `ShalevTextos.datoEn(estilo)` aplica el mismo peso a otro tamaño de la escala; lo usan la fecha de las filas de Registros y Turnos (`ShalevListItem(tituloEsDato = true)`) y los valores de la tarjeta de licencia. Los contadores (`ShalevBadge`) usan `ShalevTextos.PESO_DATO`. Mismo color que el texto normal: el contraste no cambia. No se escriben `fontWeight` sueltos. En el **PDF**, las columnas de importes/números/fechas, la columna Valor de las tablas Dato/Valor y los totales van en seminegrita (Typeface 600 en Android 9+, negrita en 8.x); en **Excel**, en negrita (sigue siendo número).

- **Texto secundario:** se escribe con `ShalevSecondaryText(...)`, que aplica bodySmall y `onSurface` al **65 %** de opacidad (`ShalevOpacity.secondary`, dentro del rango 60–70 %).
- **Placeholder:** usa 60 % de opacidad.
- **Deshabilitado:** usa 38 %.
- Todos los tamaños están en `sp`, así que respetan el tamaño de fuente que el usuario elija en el sistema.
- **Letra grande:** `letraGrande()` es true cuando la escala de fuente del sistema es ≥ `ShalevFontScale.GRANDE` (1.3). Entonces los botones no tienen alto máximo y su etiqueta puede ocupar 2 líneas, y `ShalevListItem` pone el valor debajo del título.

## 3. Espaciado: escala base 4dp

| Token | Valor |
|---|---:|
| `ShalevSpacing.xxs` | 4dp |
| `ShalevSpacing.xs` | 8dp |
| `ShalevSpacing.sm` | 12dp |
| `ShalevSpacing.md` | 16dp |
| `ShalevSpacing.lg` | 24dp |
| `ShalevSpacing.xl` | 32dp |
| `ShalevSpacing.xxl` | 48dp |
| `ShalevSpacing.xxxl` | 80dp |

La escala Android se amplía según la guía. Los tokens existentes `xs`, `md` y `lg` conservan sus valores y usos; añadir los demás no migra automáticamente los espaciados ya aplicados en pantallas.

## 4. Radios, elevación, tamaños y motion

- **Radios:**
  - `sm` = 8dp: chips y campos.
  - `md` = 12dp: botones y snackbar.
  - `lg` = 16dp: **cards**.
  - `xl` = 24dp: sheets y diálogos.
- **Elevación:**
  - `none` = 0.
  - `low` = 1dp: cards.
  - `mid` = 3dp: FAB.
  - `high` = 6dp: FAB presionado.
  - `tonalBar` = 3dp: barras inferiores fijas de Inventario y Venta.
- **Tamaños:**
  - Botón: entre 40 y 48dp de alto (`buttonMin`/`buttonMax`).
  - Campo de texto: 56dp.
  - Área táctil: 48dp como mínimo.
  - Pulsación: `ShalevMotion.PRESS_SCALE` = 0.97, aplicada directamente desde `PressInteraction` (sin interpolar el pointerdown); no cambia el área táctil.
  - **Altura visual Android:** se conserva el mínimo de 40dp (rango 40–48dp) para botones con texto y el área táctil mínima de 48dp. Web usa 44px según la guía; se mantienen deliberadamente como criterios por plataforma.
  - Iconos: 24dp, o 18dp en la versión pequeña.
  - Logo: 72dp en bloqueo y onboarding. **0.27.0:** ya no aparece en la TopBar ni en diálogos u hojas.
  - `fotoFormulario` = 112dp (0.27.0): foto centrada al principio de Nuevo producto/servicio (`SelectorFoto`).
  - Medalla del Top 3: 28–32dp (círculo local de Inicio).
  - Trazos internos (P19): `strokeHairline` = 1dp (línea base de gráficos), `strokeRegular` = 2dp (área y spinner), `chartBarMax` = 28dp.
  - `fabClearance` = 88dp (hueco al final de las listas con FAB), `qr` = 240dp, `campoCantidad` = 112dp, `contentMaxWidth` = 600dp (ancho máximo de formularios, con `Modifier.shalevContentWidth()`).
  - Billete CUP del arqueo (plan UI/UX/IX 2026-11, SK-6): `ShalevRadius.billeteCup` = 6dp, `billeteCupAncho` = 88dp, `billeteCupAlto` = 40dp, `billeteCupBorde` = 0.8dp (antes literales en `CajaUi.kt`).
- **Motion** (`ShalevMotion`). Los tokens reúnen las transiciones propias; Material también anima algunos componentes. Sin bucles con `delay(16)`: Compose sigue el refresco real y la escala de animación del sistema. Las ventanas centradas recorren media altura al entrar y al salir; los gráficos se funden al actualizar datos sin reiniciar su entrada.
  - Pulsación inmediata: `PRESS_SCALE` = 0.97 desde `PressInteraction`; la escala se aplica directamente, sin bloquear ni interpolar el `pointerdown`.
  - Duraciones: `FAST` 150, `SHORT` 200, `MEDIUM` 250 y `LONG` 250 ms (antes 300).
  - `muelle()`: `spring(DampingRatioNoBouncy, StiffnessMediumLow)` para lo que cambia de tamaño o posición (botones con `loading`, barra inferior).
  - `ventanaEntra`/`ventanaSale` (N4): diálogos y hojas suben desde abajo hasta el centro con fundido (muelle) y, al cerrarse, bajan con fundido (200 ms); el velo aparece detrás.
  - Curva: `FastOutSlowInEasing`.
  - Entre las secciones principales disponibles en Android (Inicio, Inventario, Servicios, Registros y Ajustes): `screenEnter`/`screenExit` (fundido).
  - Hacia un detalle y de vuelta: `screenForwardEnter/Exit` y `screenBackEnter/Exit` (deslizamiento de 1/10 + fundido).
  - Filas de listas: `listFade` y `listPlacement`, aplicados con `shalevAnimateItem()`.
  - Formularios, modales y FAB: `enterVertical`/`exitVertical` (deslizamiento de ¼ + fundido).

## 5. Componentes

| Componente | Descripción |
|---|---|
| `ShalevPrimaryButton` | Acción principal compacta con icono y etiqueta accesible (`text`); área táctil de 48dp. `loading` muestra progreso. `mostrarTexto` también muestra la etiqueta fuera de ventanas; dentro de diálogos/ventanas el texto siempre es visible y reemplaza al icono. |
| `ShalevSecondaryButton` | Igual, con contorno y sin relleno; dentro de diálogos/ventanas muestra texto en vez de icono. |
| `ShalevTextButton` | Acción terciaria compacta, sin fondo y con descripción accesible; dentro de diálogos/ventanas muestra su etiqueta escrita. |
| `ShalevIconAction` | Acción compacta con descripción accesible; dentro de diálogos/ventanas muestra `etiqueta` o, si no se especifica, su descripción escrita (sin icono). Estilos Standard, Tonal y Filled, o `containerColor` (WhatsApp). Con `selected` se invierte el resaltado; **0.31.0:** solo se anuncia `selected` cuando es `true`. |
| `ShalevButtonRow` | Fila de acciones **centrada** con separación media (16dp), orden Cancelar → Confirmar. En los pies de ventanas se usa `FlowRow` para ajustar y centrar etiquetas en varias filas si hace falta. Úsese también para un botón suelto: así no queda pegado a la izquierda. |
| `ShalevBarraAcciones` | **P24:** pie fijo de formularios, centrado y al alcance del pulgar. En los asistentes de producto, insumo y servicio aloja «Siguiente» o las acciones finales; el retroceso está en `ShalevStepper`. Si hay dos formas de guardar, se apilan con texto visible para evitar recortes en teléfonos estrechos; no se repite «Guardar» arriba y abajo. |
| `ShalevTextField` | **1.0.2: se ajusta a su contenido** (ancho mínimo de 112dp) salvo los **buscadores**, que pasan `anchoCompleto = true` y ocupan todo el ancho. Con contorno y 56dp de alto; **P24: esquinas redondeadas** (12dp una línea, 16dp multilínea). **0.27.0 (T6):** el placeholder va **centrado** (el texto escrito y la etiqueta no cambian). Placeholder claro y **X pequeña** para borrar cuando hay texto; en diálogos/ventanas la acción se rotula «Limpiar». Muestra el error debajo, con `isError` y `errorText`. `imeAction` + `onImeAction`: «Siguiente» en los campos intermedios y «Listo» (guardar) en el último. **Regla común de validación (A16):** con `validarAlSalir = true` el error aparece cuando el campo se ha editado y pierde el foco, o con `forzarError = true` (tras Guardar/Siguiente); un campo vacío y sin foco solo se marca al forzar. Las decisiones están en `ShalevValidacion` (puras, con test JVM). Sin `validarAlSalir`, `isError` se muestra tal cual (filtros, errores del servidor). |
| `ShalevCard` | Radio de 16dp, padding de 16dp y elevación baja. Surface tonal Default, Tonal, Highlight o Error (0.21.1). Título opcional, `onClick` y **0.31.0:** el título se anuncia como encabezado. |
| `ShalevAccordionCard` | (0.30.1) Card plegable: cabecera de ≥ 48 dp con título centrado y flecha que gira, contenido con `AnimatedVisibility`. El estado (`expanded`/`onToggle`) lo lleva quien la usa, para recordarlo fuera de listas que reciclan filas. Semántica: encabezado + «Expandido»/«Contraído». **0.31.0:** el subtítulo se oculta al abrir para no repetir el dato. Lo usa Inicio. |
| `ShalevCard(ajustarAlContenido = true)` | (0.30.1) La card mide lo que mide su contenido en vez de ocupar todo el ancho (turno de Inicio). |
| `ShalevListItem` | Indicador de color a la izquierda (4×32dp), nombre/subtítulo y **máximo 3 líneas de texto internas**, incluido el valor cuando se apila con letra grande. El valor normal queda alineado a la derecha (máx. 45 % del ancho; **P24:** la letra se reduce con `ShalevTextoAjustable`). Admite leading, trailing y `selected`; **0.31.0:** `selected` solo se anuncia cuando es `true`. |
| `ShalevBadge` / contador de alertas | Contador numérico, y contador de alerta de Inicio con su tono (local a la pantalla: `ShalevCard`+`Text`). **0.27.0 (T14):** en Inicio las alertas van centradas (icono, contador y texto) y repartidas por filas con una regla pura: 1, 2, 3 → una fila; 4 → 2 + 2; 5 → 3 + 2; 6 → 3 + 3; mismo ancho y alto dentro de la fila, fila incompleta centrada. **0.31.0:** en los filtros, el contador va en la descripción del botón y el badge no se anuncia aparte. |
| Medalla del Top 3 | **0.27.0 (T3):** número del puesto (1, 2, 3) dentro de un círculo oro, plata o bronce (`MEDALLA_ORO/PLATA/BRONCE`, iguales en claro y oscuro) con el número en el tono oscuro de su familia (≥ 4.5:1, `ContrastTest`). Peso de dato. TalkBack: «Primer/Segundo/Tercer puesto». Vive en Inicio (un solo uso). |
| `ShalevChip` | FilterChip con `supportingLabel` (por ejemplo, el precio). |
| `ShalevCheckbox` | **0.32.0:** casilla propia, **más pequeña** que la de Material 3 (28 dp de lado / 18 dp de cuadro → aquí 24 dp / 16 dp: `ShalevSize.casilla` y `ShalevSize.casillaCuadro`). Cuadro redondeado (`ShalevRadius.sm / 2`), borde de 2 dp en `onSurfaceVariant` y relleno `primary` con la ✓ de Material. Sin `onCheckedChange` no es pulsable (la fila entera responde). Sustituye a `Checkbox` de Material en toda la app. **Pendiente (auditoría 0.32.0):** cuando la casilla es la que responde, el área táctil es la de 24 dp (el dueño deja el cambio a 48 dp para otro lote). |
| `ShalevSnackbarHost` | Snackbar sobre `inverseSurface`, con vibración breve al aparecer. |
| `ShalevDialog` | Ventana centrada con título arriba. Las acciones comunes que contiene muestran su etiqueta escrita y descripción accesible, también durante una carga. Las secundarias de `secundarias` van antes de Confirmar; el pie las centra y las envuelve en varias filas si hace falta, sin alterar el orden. No añade Cancelar: se cierra tocando fuera o con Atrás. `confirmEnabled`, `confirmLoading`, `confirmTag` y `confirmIcon` configuran la confirmación. Respeta barras, recorte y teclado. |
| `ShalevBottomSheet` | **P24: ya no es una hoja inferior sino una ventana centrada** (92 % del ancho, máx. 560dp; alto máx. 90 %), con contenido desplazable y **pie fijo `FlowRow`**: centra las acciones y las envuelve en filas si el ancho no alcanza. Tocar fuera cierra. Con `sinGuardar = true`, tocar fuera o atrás preguntan «¿Salir sin guardar?». Se mantiene el nombre para no tocar las llamadas. **0.32.0 (A13):** misma área segura que el diálogo (barras, recorte y teclado). |
| `ShalevComboBox` | **P24:** lista desplegable de solo lectura para elegir UNA opción (Período en Inicio). **P25:** aspecto de tarjeta tonal (radio 16dp, icono en círculo `primaryContainer`, etiqueta + valor en negrita, flecha que gira); menú con radio 16dp y la opción elegida resaltada con ✓. |
| `ShalevTextoAjustable` | **P24:** texto que no se parte ni se corta: si no cabe, reduce la letra hasta 11sp; solo por debajo aparece «…». Importes de filas y celdas de Inicio. |
| `ShalevIlustracionImagen` + `ShalevIlustracion` | **P24:** 9 ilustraciones vectoriales (Error, SinResultados, Inventario, Elaboracion, Carrito, Registros, Precios, SinTurno, NoExiste); **P29:** añade Servicios. Son 10 en total, de **unDraw** (paquete npm `undraw-svg` 2.0.0, MIT) y están dentro del APK. El acento toma el `primary` del tema; en oscuro los grises se invierten. Decorativas (TalkBack no las lee). |
| Acciones del «+» de pago electrónico | Dos `ShalevIconAction` tonales (teléfono y tarjeta/cuenta) con descripción para TalkBack, desplegadas sobre un `ShalevFab` «+» iconográfico. **0.26.0 (P73):** abajo a la derecha (`FabPosition.End`), como pide Material. Viven en la propia pantalla (un solo uso). |
| `ShalevFab` | FAB de una acción con icono visible y área táctil de 48dp fuera de ventanas; su descripción completa se anuncia con TalkBack. Dentro de un diálogo o ventana muestra su etiqueta en vez del icono; `soloIcono = false` también lo presenta con texto fuera de esas ventanas. |
| `ShalevLoading` / `ShalevLinearProgress` | `ShalevLoading` dibuja huesos estáticos de inspiración Boneyard alineados con filas, tarjetas y gráficos, con descripción «Cargando»; sin animaciones infinitas. Progreso lineal determinado o indeterminado. |
| `ShalevNavigationBar` + `ShalevNavItem` | Barra inferior de hasta **5 destinos** (Inicio, Inventario, Servicios, Registros y Ajustes); Inventario y Servicios dependen de los módulos habilitados. Icono relleno, texto debajo y selección invertida. Ajustes es la quinta sección; Inicio abre la Calculadora de cambio desde su acción superior. |
| `ShalevTopBar` | `CenterAlignedTopAppBar`: título centrado (máx. 2 líneas), flecha atrás y acciones a los lados. Con `marca = true` (Inicio) «Shalev» usa la fuente de marca. Fuera de ventanas, Ayuda y Atrás son compactos y accesibles; dentro de una ventana muestran sus etiquetas. El icono superior izquierdo usa `ShalevIcons.Ayuda`; el acceso textual al manual de Ajustes no duplica el icono. |
| `ShalevLogo` | SVIPicono (`R.drawable.shalev_logo`). |
| `ShalevEmptyState` | Ilustración (P24, `ilustracion`) o logo, título (encabezado), detalle opcional, botón de Ayuda opcional (`ayuda`) y acción opcional. **No repite acciones que ya están en pantalla** (p. ej. el botón flotante «+»). |
| `ShalevTabs` + `ShalevTab` | Pestañas con rol Tab y posición («2 de 4»), resaltado invertido, número opcional («Insumos (2)») y `testTag` por pestaña. |
| `ShalevStepper` | Cabecera de asistente: «Paso N de M», título, barra de progreso y flechas Atrás/Siguiente opcionales de 48dp; anuncia el paso con región viva. En las altas guiadas, Atrás está en la cabecera y «Siguiente» en el pie. Sin flechas (Onboarding, Migrar, Importar) el título usa todo el ancho; con una sola, se reserva el hueco de la otra. |
| `ShalevStatusBanner` + `BannerTone` | Banner Info / Aviso / Crítico con icono propio por tono (Licencia, Reloj, Alerta), texto, detalle y acción u `onClick`. Colores del tema por pares contenedor/contenido. **0.31.0:** si es clicable y no tiene acción propia, muestra un chevron. |
| `ShalevHaptics` / `rememberShalevHaptics()` | `exito()`, `error()`, `seleccion()` con `View.performHapticFeedback` (CONFIRM/REJECT en Android 11+). Sin permiso VIBRATE. |
| `shalevAnimateItem()` / `Modifier.shalevContentWidth()` | Animación común de filas en listas perezosas; ancho máximo centrado para formularios. |
| `ShalevSecondaryText` | Texto secundario al 65 %. |
| `ShalevBarChart` / `ShalevDonutChart` / `ShalevAreaChart` | Gráficos propios en Canvas (`component/Charts.kt`): barras y áreas con ejes X/Y, marcas numéricas y unidad opcional; las etiquetas textuales de los ejes también quedan disponibles en Semantics junto con el resumen para TalkBack. Sin cuadrícula; animación corta (el área usa `drawWithCache`: el trazo se calcula una vez y la animación solo escala). Paleta `ShalevTheme.colors.chart` (≥ 3:1 sobre la superficie, verificada en `ContrastTest`). **P24:** en las barras, la intensidad del color depende del valor respecto al máximo de la escala Y (`ChartMath.intensidad`: opacidad de 0,30 a 1, lineal). Ventas y Ganancia bruta usan etiquetas X para hora/día/mes y cortan los cubos futuros no transcurridos, sin cambiar la granularidad elegida. Alto `ShalevSize.chartHeight` (160dp); dona de 136dp con trazo de 24dp. |

## 6. Iconografía

- **0.27.0:** `Pdf` y `Excel` (tabla, sin logotipos de marca) en todas las hojas de exportar (`iconoFormato`); `Huella`/`Bloqueo` (acceso con clave), `Desarrollador`, `Identidad`, `Telefono`, `Especialidad` (Soporte), `Camara`/`Galeria` (foto) y `ClientesFijos`.
- Todos los iconos son **rellenos** (trazados propios de Material Symbols, Apache 2.0, desde el Prompt 17) y se exponen con nombres de negocio (`ShalevIcons.Inventario`, `ShalevIcons.Venta`, …). Las pantallas nunca importan `Icons.*` directamente.
- **0.32.0: «Apps vinculadas» se dibuja con un móvil** (`ShalevIcons.Movil`, el mismo del recorrido inicial de la app secundaria): en Ajustes, en la Ayuda y en **cada app vinculada** de la lista de la principal.
- **Estado seleccionado = resaltado invertido.** Vale para la barra inferior, `ShalevIconAction(selected = true)` y la selección WhatsApp/SMS.
- **WhatsApp:** es un vector propio con los arcos SVG expandidos, verificado contra el SVG de origen.

## 7. SVIPicono

- **Launcher:** icono adaptativo con fondo **transparente** (P29) y capa **monochrome** para los iconos temáticos de Android 13+.
- **Splash (0.27.0, N3):** fondo del color de ventana del tema (`shalev_window_background`: claro `#F8F9FA`, oscuro `#0F1318`) con el icono; se quitó `ic_launcher_background`.
- **En la app:** `ShalevLogo` (`drawable-nodpi/spvi_logo.png`, **fondo transparente** desde la 0.27.0, sin recorte circular) en bloqueo, onboarding y estados vacíos. Ya no en barras ni diálogos.

## 8. Accesibilidad

- Contraste AA verificado por test.
- Área táctil de 48dp.
- Todos los botones compactos de solo icono llevan descripción.
- En diálogos y ventanas emergentes, las acciones muestran su etiqueta escrita (también al cargar); los pies se envuelven si el ancho no alcanza.
- Los iconos decorativos van con `contentDescription = null`.
- Los tamaños en `sp` escalan con la fuente del sistema.
- El indicador de páginas del onboarding anuncia "Página X de N".
- `ShalevLoading` anuncia "Cargando" y dibuja un marcador tipo skeleton (1.0.2).
- El icono de Ayuda de la barra superior anuncia la pestaña («Ayuda de Inventario»…). Ajustes se anuncia como destino de la navegación inferior.
- Pestañas con rol y posición; asistentes y banners de Aviso/Crítico con región viva.
- Probado con escala de fuente 2.0 (`DisenoP18UiTest`).

## 9. Cómo usar

```kotlin
ShalevCard(title = "Existencias") {
    ShalevListItem(
        title = "Harina",
        value = "12 kg",
        indicatorColor = ShalevTheme.colors.of(AlertTone.InsumoBajo),
    )
    ShalevSecondaryText("Actualizado hoy")
    ShalevButtonRow {
        ShalevIconAction(ShalevIcons.Editar, "Editar", onClick = {})
        ShalevPrimaryButton("Reponer", onClick = {})
    }
}
```

## Prompt 24 — botones solo icono, ventanas centradas y leyes de UX — 0.15.0

> **Decisión histórica revisada (2026-11-13):** las reglas de icono-only que siguen describen el diseño original de 0.15.0; hoy las acciones de diálogos y ventanas emergentes muestran texto y sus pies se envuelven si no caben. La regla vigente está en la tabla de componentes anterior.

| Ley | Cómo se aplica |
|---|---|
| **Fitts** | Acciones principales abajo y grandes: botón flotante para crear, abajo a la derecha (Inventario, Servicios, **Precios**; 0.26.0), `ShalevBarraAcciones` fija en los formularios, botones de 48dp. «Nueva venta» centrada en Inicio. |
| **Hick** | Menos opciones a la vista: un solo botón flotante por pestaña, sin acciones repetidas en los estados vacíos, filas con un solo dato, Período en combobox. |
| **Jakob** | Patrones de Android: crear = botón flotante, orden Cancelar → Confirmar, pestañas para varias tablas, selector de fecha del sistema. |
| **Proximidad** | Cada botón junto a lo que afecta (pegar SMS con su explicación, las acciones de la ficha junto a sus datos); acciones del diálogo agrupadas y centradas. |
| **Von Restorff** | «Nueva venta» es el único botón relleno de la tarjeta del turno (P25: también solo icono); la confirmación de cada diálogo va rellena y el resto sin fondo. |

- **Botones:** solo icono, sin excepciones desde P25. El texto queda como descripción para TalkBack y tooltip.
- **Ventanas:** todas centradas, tocar fuera cierra, acciones centradas con 16dp de separación. El selector de fecha (`CampoFecha`) también centra Cancelar/Aceptar.
- **Pestañas:** Elaboración (Insumos/Elaborados), Registros y, nuevo, el **detalle del turno** (Resumen · Ventas · Inventario).
- **Fechas y horas:** formato del dispositivo (`Dates.formato`, `FormatoFechaDispositivo`); la guía del campo de fecha se deriva de él (`FechasUi.guia()`, p. ej. «m/d/aa»).
- **Textos:** descripciones de tarjetas y estados vacíos acortadas a una frase; los textos ya no nombran botones por su etiqueta («toca el botón de pegar»).


## Ajustes 0.15.1

- `ShalevPrimaryButton`: se elimina `mostrarTexto`; todos los botones son solo icono.
- `ShalevComboBox`: superficie tonal con radio 16dp (`surfaceContainer` en claro, `surfaceContainerHigh` en oscuro) y borde `primary` de 2dp mientras está abierto. El icono va en un círculo `primaryContainer` de 40dp; la etiqueta es `labelMedium` y el valor `titleMedium`. La flecha `ShalevIcons.Desplegar` gira 180°. El menú tiene radio 16dp y la opción elegida lleva fondo `primaryContainer` y ✓.
- `ShalevIcons.Guardar` = `ShalevIcons.Confirmar` (✓); se elimina el trazado `Save`. (En la 0.18.3 pasó a ser un disquete; la 0.18.4 lo devuelve a ✓ por decisión del usuario.)

## Ajustes 0.16.0

- Sin componentes nuevos. La fila de Elaborado (`ShalevListItem`) usa `value = textoAlcance(n)` y `indicatorColor = error` cuando no alcanza. La fila del Inventario aplica `TonoFila.PELIGRO` con `alcanza == 0`.
- Se retira el diálogo Producir (era un `ShalevDialog` con campo de unidades); no queda ningún uso de `ShalevIcons.Agregar` como acción de fila en Elaboración.

## Ajustes 0.16.1

- `ShalevListItem(valueMaxLines = 1, subtitleMaxLines = 1)`: si un texto debe leerse entero, pasa a varias líneas en vez de cortarse. Se usan 2 líneas en las filas de Elaborados y en el carrito, y 4 en las fichas. Los importes cortos siguen en 1 línea y reducen la letra (`ShalevTextoAjustable`).

## Ajustes 0.17.0 — centrado y totales

- **`LocalShalevCentrado`** (`Surfaces.kt`): `ShalevCard` y el cuerpo de `ShalevVentana` lo ponen a `true` y centran su columna (`CenterHorizontally`). Con él, `ShalevSecondaryText` sin `textAlign` explícito se centra. `ShalevListItem` lo vuelve a poner a `false` dentro de su fila, así que subtítulos y valores de filas no cambian. Si un texto debe ir a la izquierda dentro de una tarjeta (p. ej. junto a un icono), se pasa `textAlign = TextAlign.Start`.
- **`ShalevCard(titleCentered = true)`** es ahora el valor por defecto.
- **Párrafos sueltos** (`bodyMedium` fuera de tarjetas) y **títulos de sección**: `textAlign = Center` + `fillMaxWidth()`.
- **Contadores con acción** («N elegidos» + icono, chips de filtro): una fila `fillMaxWidth` con `spacedBy(md, CenterHorizontally)`, sin `weight`, para que vayan centrados como grupo.
- **Total único**: en cada pantalla o ventana con un importe total, se muestra una sola vez, centrado arriba: etiqueta `ShalevSecondaryText` y valor `ShalevTextoAjustable` en `headlineSmall` negrita, agrupados para TalkBack (`mergeDescendants`). Lo usan Venta (`TotalVenta`, etiqueta «Total estimado», «Total» o «Importe a transferir» según el paso), la ficha de Registros y el detalle de turno («Total vendido»). No se repite en tablas ni en títulos.
- **Barra de Venta**: Cancelar (✕, tonal) y Confirmar (✓, relleno), solo iconos, centrados con `ShalevBarraAcciones` (16dp entre ellos). El nombre de la acción va en la descripción de TalkBack y en el tooltip.
- **`Equitativo`** (`OnboardingScreen.kt`): `Arrangement.Vertical` que reparte el mismo espacio entre los elementos y en los bordes; si no caben, deja 16dp entre ellos y la columna se desplaza.
- **Una alerta sola** en Inicio va centrada con el mismo ancho que cuando son dos.
- La lista de Pago electrónico ya no usa la barra lateral (`indicatorColor`) para «En uso»: lo indican el fondo seleccionado y la etiqueta.

## Ajustes 0.27.0

- **T1** títulos centrados sin logo (TopBar, diálogos, hojas). **T2** textos que crecen en lugar de cortarse; capturas con letra al 200 % (`LetraGrande` en las pruebas de capturas). **T3** medalla del Top 3 (local a Inicio). **T4/N4** motion (arriba). **T6** placeholder centrado. **T7** iconos PDF/Excel. **T14** reparto de alertas. **N3** logo transparente.
- **Clientes fijos (N2):** casilla «Cliente fijo» como `ShalevListItem` con `Checkbox` a la izquierda; sugerencias (hasta 3) como filas con icono de persona justo debajo del nombre; pestaña «Clientes» en Registros con ficha en `ShalevBottomSheet` y «Quitar» con `ShalevDialog` destructivo. Desde la 0.31.0 la ficha lleva **Editar** y **Quitar centrados en su pie** (`ShalevBottomSheet(footer = …)`, siempre a la vista) y la corrección usa el mismo `ShalevDialog` con los tres campos del cliente (`CampoClienteField`: nombre, carné y teléfono) y el botón de guardar (✓).

## Plan UI/UX/IX 2026-11 (Shalev)

Reglas y decisiones que salen de `docs/PLAN_MEJORAS_UI_UX_IX.md`:

- **Previews (SK-2):** las `@Preview` son **privadas** y viven **solo en `:designsystem`**, en pareja Light/Dark, para feedback rápido en el IDE (botones, cards, indicadores). En `:app` no se escriben previews: la regresión visual la cubre **Roborazzi** (`CAPTURAS.md`), que sigue siendo la fuente de verdad de píxeles. Esto evita mantener dos expectativas visuales que puedan divergir.
- **Recomposición (SK-3):** `IndicadorDesplazamiento` ya lee sus banderas (`canScrollBackward`/`canScrollForward`) como **lambdas diferidas dentro de `drawWithContent`**, es decir, en la fase de dibujo: el cambio de visibilidad de los degradados no provoca recomposición, solo redibujado. Medición previa (2026-11): no hay `derivedStateOf` porque la lectura ya está fuera de la composición; si una lista futura muestra recomposiciones por scroll en las métricas del compilador, se aplicará `derivedStateOf` en ese punto concreto (criterio: medir primero, no optimizar a ciegas).
- **Piloto tablet/plegable (SK-4):** `MainScaffold` calcula `WindowWidthSizeClass` (material3-window-size-class). En `Compact` todo queda igual; en `Medium`/`Expanded` el contenido del `NavHost` se limita a `ShalevSize.contentMaxWidth` (600dp) centrado. La navegación **no cambia**: la barra inferior sigue a todo lo ancho y el deslizar entre secciones se detecta en toda la pantalla (el modificador está en el `Box` envolvente, no en el `NavHost`). Piloto deliberadamente pequeño: no hay paneles dobles ni rail lateral.
- **Deslizar entre secciones (SK-5): decisión de NO seguir el dedo.** El gesto actual es de detección post-gesto (umbral 72dp en `Deslizar.kt`). No se convierte en arrastre con paginación visual porque: la barra inferior ya es la alternativa accesible para cambiar de sección, `BloqueoGestos` evita conflictos con campos y listas, y un Pager introduciría estado nuevo en las 4 secciones. Reevaluar solo si se pide paginación visual explícita.
- **Tests de accesibilidad (SK-1):** `Android/app/src/androidTest/…/AccessibilityTest.kt` con `enableAccessibilityChecks()` (ATF sobre el árbol de Compose; artefacto `ui-test-junit4-accessibility` fijado aparte en el catálogo porque no está en la BOM 2024.12.01). **Instrumentado, no JVM**: la API requiere Android 14 (API 34+) y a fecha de hoy no funciona en Robolectric (b/332778271), así que se compila en cada push (`:app:compileDebugAndroidTestKotlin`) y corre en el trabajo de emulador del CI (semanal/a mano). Los componentes comunes se prueban montados con `ShalevTheme`; un aviso legítimo de ATF se arregla en `:designsystem`, nunca silenciando el check. En JVM la capa equivalente son las aserciones semánticas de `AuditoriaUiTest`, que no necesitan ATF.
