# Recorridos guiados (coach marks con spotlight)

La primera vez que el usuario abre una pantalla o una ventana emergente con varios controles, un recorrido resalta
todos sus componentes, agrupados (los controles relacionados comparten paso; como máximo 8 pasos). La pantalla se
oscurece con un velo negro al 50 % que deja un hueco de luz sobre el objetivo, y un globo muestra título, una frase
de acción y los botones **Omitir**, **Anterior** y **Siguiente** (en el último paso, **Finalizar**). El recorrido se
guarda como visto en Preferences DataStore solo al pulsar *Finalizar*, *Omitir* o Atrás.

## 1. Librería: implementación nativa

| Opción | Encaje con Shalev | Decisión |
|---|---|---|
| **Reveal** (`svenjacobs/reveal` 5.2.3, MIT, mantenida) | La versión actual se compila con Kotlin 2.4.21, Compose BOM 2026.09 y AGP 9.5. Shalev usa Kotlin 2.0.21, BOM 2025.06.01 y AGP 8.7.3: los metadatos de Kotlin 2.4 no los lee el compilador 2.0, así que habría que actualizar toda la cadena de herramientas o fijar una versión antigua de Reveal. Además, su velo intercepta los toques por defecto y exige un `RevealCanvas` en la raíz. | Descartada por ahora |
| **ComposeCoach** (`com.ruviapps.coachmark:compose-coach-android`) | Repositorio pequeño (5 estrellas, último cambio en diciembre de 2025). Riesgo de mantenimiento para una app que se distribuye sin Play Store y tiene que funcionar sin conexión. | Descartada |
| **Nativa** (`Canvas` + `Layout`) | No añade dependencias, no toca R8 ni los permisos del manifiesto, y usa los botones, tokens, accesibilidad y letra grande de `:designsystem`. Unas 390 líneas, probadas en JVM y con Robolectric. | **Elegida** |

## 2. Piezas

| Archivo | Qué hace |
|---|---|
| `Android/designsystem/.../component/CoachMarks.kt` | Componente genérico: `CoachMarkPaso`, `CoachMarkState`, `rememberCoachMarkState`, `Modifier.coachMark(id)`, `CoachMarkHost` y la geometría del globo (`CoachMarkGeometria`). No sabe nada de DataStore. |
| `Android/app/.../recorrido/OnboardingPreferences.kt` | `RecorridosVistos` (interfaz) y `OnboardingPreferences`, su implementación con Preferences DataStore (`shalev_recorridos`), más el módulo Hilt. |
| `Android/app/.../recorrido/Recorridos.kt` | `ObjetivosRecorrido` (claves de los objetivos) y `enum class Recorrido` con los pasos de cada módulo. |
| `Android/app/.../recorrido/RecorridoModulo.kt` | `RecorridosViewModel` y `RecorridoModulo(...)`: observa el indicador con `collectAsState()`, inicia el recorrido en un `LaunchedEffect` y guarda al terminar. `LocalRecorridos` (lo provee `MainScaffold`) y `recorridoVentana(Recorrido.X)` para las ventanas emergentes. |
| `Android/designsystem/.../component/Overlays.kt` | `ShalevDialog` y `ShalevBottomSheet` aceptan `recorrido`: un `EnvoltorioRecorrido` que pone un `CoachMarkHost` **dentro** de la ventana (el velo de la pantalla no puede cubrir otra ventana). `ShalevDialog` marca su botón de confirmar con `CoachMarkObjetivos.CONFIRMAR`; la barra superior marca **Ayuda** con `CoachMarkObjetivos.AYUDA`. |

### Cómo funciona

1. Cada pantalla marca sus elementos con `Modifier.coachMark(ObjetivosRecorrido.X)`. **Sin un host alrededor el
   modificador no hace nada**, así que los `*Content` se siguen probando y capturando como antes.
2. El `*Screen` (con estado) envuelve su `*Content` en `RecorridoModulo(Recorrido.X, listo = …)`. `listo` dice cuándo
   tiene sentido mostrarlo: datos cargados y sin diálogos, hojas, fichas ni selección abiertos. Con `listo = false`
   el recorrido se pausa sin perder el paso.
3. `RecorridoModulo` lee `visto` (con valor inicial `true` para no parpadear). Si es `false` y la pantalla está
   lista, espera 600 ms, espera a que algún objetivo esté en pantalla y llama a `iniciar()`.
4. `iniciar()` fija los pasos de la vuelta: solo los que tienen el objetivo visible (por ejemplo, «Nueva venta» no
   sale con el turno cerrado). Así el contador «2 de 3» no cambia a mitad del recorrido.
5. El velo es un `Canvas` con un `Path` par-impar (rectángulo completo menos un rectángulo redondeado) y **no tiene
   `pointerInput`**: los toques lo atraviesan y la pantalla sigue funcionando. Solo el globo (`Surface`) consume
   toques.
6. *Finalizar*, *Omitir* y Atrás cierran el recorrido y llaman a `marcarVisto()` en `viewModelScope`. Si la app se
   cierra a mitad, no se guarda nada y el recorrido vuelve a salir.
7. El diálogo **Ayuda** (`DialogoAyudaPestana`) ofrece **Ver recorrido** cuando la pantalla tiene uno; repetirlo no
   modifica el indicador.

### Indicadores en DataStore

Una clave por recorrido: `<clave>_tour_shown` (`caja_tour_shown`, `ventas_tour_shown`, `ajustes_tour_shown`,
`ventana_fondo_tour_shown`…). La lista completa son las entradas de `enum class Recorrido`; «Ayuda → Ver recorrido»
y `reiniciar()` las recorren todas.

El archivo es `files/datastore/shalev_recorridos.preferences_pb`. Va sin cifrar porque solo contiene booleanos de
interfaz. La copia en la nube y la transferencia entre teléfonos ya están desactivadas para toda la app
(`data_extraction_rules.xml`). Si el archivo se corrompe, se reemplaza por uno vacío (`ReplaceFileCorruptionHandler`):
lo peor que puede pasar es volver a ver un recorrido.

## 3. Contenido de los recorridos

Los pasos se ajustan a la app tal como es hoy. Lo que la petición nombraba y la app no tiene se resolvió así:

- **Ventas.** No hay barra de búsqueda dentro de la venta: se busca al agregar artículos. El paso «Agregar
  artículos» lo explica. El botón «Cobrar» es **Continuar**.
- **Inventario.** No hay botón de «Escanear código» (la cámara solo se usa para vincular teléfonos) ni de «Ajustar
  stock»: las existencias se cambian desde la ficha, con **Editar**. El paso de la lista lo explica.
- **Caja.** Abrir turno y cerrar la caja son el mismo interruptor de la tarjeta del turno, en Inicio.

Los pasos y textos están en `Recorridos.kt`. Resumen de la cobertura:

| Grupo | Recorridos |
|---|---|
| Pestañas | `CAJA` (Inicio: local, turno, vender, caja, alertas, pagos y precios, resumen, Ayuda) · `VENTAS` · `INVENTARIO` · `SERVICIOS` · `REGISTROS` (cada una con buscar, seleccionados, filtrar, exportar, agregar y Ayuda) |
| Ajustes y sus módulos | `AJUSTES` (un paso por grupo de filas) · `PERFIL` · `LICENCIA` · `VINCULACION` · `QR` · `PAGO` · `PRECIOS` · `RESPALDO` · `MANUAL` · `SOPORTE` · `CONFIGURACION` (asistente) |
| Pantallas secundarias | `TURNO_DETALLE` · `PRODUCTO_FORM` · `SERVICIO_FORM` · `INSUMO_FORM` |
| Caja | `VENTANA_NUEVA_VENTA` · `VENTANA_FONDO` · `VENTANA_MOVIMIENTO` · `VENTANA_CONTEO` · `VENTANA_CIERRE` |
| Fichas, filtros y exportar | `VENTANA_FICHA_*` (inventario, servicio, registro, cliente) · `VENTANA_FILTRO_*` · `VENTANA_EXPORTAR_*` (inventario, servicios, registros, turno) · `VENTANA_COMPARTIR_FICHA` |
| Formularios en ventana | `VENTANA_AGREGAR_INSUMO` · `VENTANA_MODIFICAR` · `VENTANA_ANULAR` · `VENTANA_CORREGIR` · `VENTANA_EDITAR_CLIENTE` · `VENTANA_CUENTA` · `VENTANA_PRECIO` · `VENTANA_IMPORTAR` · `VENTANA_LOCALES` · `VENTANA_LOCAL` · `VENTANA_EMPLEADO` · `VENTANA_QR` · `VENTANA_SECUNDARIA` |

**Sin recorrido** (confirmaciones simples de Sí/No): eliminar, quitar, desvincular, cerrar el turno de un empleado,
descartar cambios, resultado de un respaldo, buscar actualización y permisos iniciales.

Cada pantalla pasa `listo = false` mientras tiene una ventana propia abierta, para que su recorrido no se mezcle con
el de la ventana.

## 4. Dependencia en `Android/app/build.gradle.kts`

El recorrido es nativo: no hace falta ninguna librería de coach marks. Solo se añade DataStore al módulo `app`, con
la misma versión del catálogo que ya usa `:data`:

```kotlin
// gradle/libs.versions.toml (ya existía)
// datastore = "1.1.1"
// androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

dependencies {
    // …
    implementation(libs.androidx.datastore.preferences)
}
```

Si más adelante se actualiza a Kotlin 2.4 o posterior y se prefiere Reveal, la API de pasos (`Recorrido`), el
indicador (`OnboardingPreferences`) y los puntos de integración (`RecorridoModulo`, `Modifier.coachMark`) siguen
sirviendo. Solo cambiaría `CoachMarks.kt`:

```kotlin
implementation("com.svenjacobs.reveal:reveal-core:<versión>")
```

## 5. Añadir un recorrido a otro módulo

1. Añade las claves en `ObjetivosRecorrido` y una entrada en `Recorrido` con hasta 8 `CoachMarkPaso` agrupados.
2. Pon `.coachMark(ObjetivosRecorrido.…)` en el `Modifier` de cada objetivo dentro del `*Content`.
3. En el `*Screen`, envuelve el `*Content`:

   ```kotlin
   RecorridoModulo(Recorrido.NUEVO, listo = state.cargado && state.dialogo == null) {
       NuevoContent(state = state, acciones = …)
   }
   ```

4. En una ventana emergente, pasa `recorrido = recorridoVentana(Recorrido.VENTANA_NUEVA)` a `ShalevDialog` o
   `ShalevBottomSheet`. Las claves `VENTANA_*` se pueden repetir entre ventanas: cada una tiene su recorrido.
5. La prueba `OnboardingPreferencesTest.cadaRecorridoTieneHastaOchoPasosAgrupadosConObjetivosUnicos` comprueba el
   límite de pasos y la longitud de los textos.

## 6. Recomendaciones de UX

- **Hasta 8 pasos, agrupados.** Los controles relacionados (los campos de un formulario, los botones de una fila)
  comparten paso. Si hace falta más, es material para el manual (el botón **Ayuda** ya enlaza a él).
- **Título de 2 a 4 palabras y una sola frase que empiece por el verbo**: «Toca aquí para…», «Escribe…». Las pruebas
  limitan el título a 28 caracteres y la descripción a 120.
- **En el momento justo, no al instalar.** Cada módulo enseña lo suyo la primera vez que se abre, y la venta espera a
  que haya artículos en el carrito para que todo lo que se explica esté en pantalla.
- **Nunca bloquear.** El velo deja usar la pantalla, *Omitir* está siempre visible y Atrás cierra el recorrido. Lo
  omitido cuenta como visto: insistir molesta más de lo que enseña.
- **Saltar lo que no está**, en lugar de señalar el vacío (por ejemplo, «Exportar» sin permiso o «Vender» con el
  turno cerrado).
- **Repetible a demanda**: **Ayuda → Ver recorrido**, sin volver a mostrarlo solo.
- **Accesible.** El globo es un panel (`paneTitle`) que TalkBack anuncia y lee primero. Los botones muestran su texto
  y crecen con la letra grande (`FlowRow`). El anillo usa `inversePrimary` para contrastar sobre el velo.
- **Sin sorpresas al volver.** El paso en curso sobrevive a la rotación y a la navegación (`rememberSaveable`).
  Reinstalar la app vuelve a mostrar los recorridos una vez.

## 7. Pruebas

- `designsystem/src/test/.../CoachMarkGeometriaTest.kt`: posición del globo (debajo, encima, centrado, dentro de los
  márgenes) y estado sin objetivos.
- `app/src/test/.../recorrido/OnboardingPreferencesTest.kt`: DataStore real en una carpeta temporal (claves,
  valores por defecto, marcar uno, reiniciar) y límites de contenido.
- `app/src/test/.../recorrido/RecorridoModuloTest.kt` (Robolectric): solo sale si no se vio; Siguiente, Anterior y
  Finalizar; Omitir; salta objetivos ausentes; espera con `listo = false`; el velo no bloquea; Ayuda → Ver
  recorrido.
