# Análisis de Shalev Android — UX, flujo y datos

**Fecha:** 2026-11-08  
**Rama analizada:** `arena/c2a22952-shalev`  
**Alcance:** lectura estática de la app Android del repositorio; navegación, pantallas, reglas de dominio, persistencia, sincronización y documentación.

## Nota sobre las skills solicitadas

No encontré en el checkout ni en los archivos disponibles del entorno definiciones ejecutables de las skills **Ponytail** o **UI IX Pro MAX**. Por eso no atribuyo a esas skills reglas particulares que no puedo verificar ni afirmo haberlas ejecutado. Para no detener el análisis, presento dos pasadas diferenciadas: **Ponytail (pasada estructural)** —producto, arquitectura, dependencias, flujos y riesgos— y **UI IX Pro MAX (pasada de interfaz)** —usabilidad, accesibilidad, consistencia y fricción—. La pasada UI contrasta además el código con las decisiones y hallazgos ya documentados en `UI_UX_IX.md` y `docs/AUDITORIA_UI_UX_IX_2026-10-28.md`.

Esto es una **revisión de fuentes y documentación**, no una prueba manual sobre teléfono ni una nueva ejecución de `shalevCheck`. Las observaciones están separadas entre hechos del código, deuda ya documentada y recomendaciones por validar.

## Resumen ejecutivo

Shalev es un POS Android offline-first para pequeños comercios cubanos. El diseño está centrado en el ciclo **abrir turno → vender/cobrar → registrar caja e inventario → cerrar y revisar**. La base es local, cifrada y exportable; las secundarias operan con una principal mediante sincronización autenticada. El código presenta una separación de responsabilidades razonablemente sólida: Compose en `:app`, reglas y casos de uso en `:domain`, Room/SQLCipher y sincronización en `:data`, y componentes comunes en `:designsystem`.

**Fortalezas principales**

- Protege la regla de negocio crítica —no vender sin turno— en más de una capa, y registra venta, detalles, cobro y movimientos de inventario de forma atómica.
- Los importes y costos quedan congelados en cada detalle; los registros no dependen del catálogo actual para reconstruir ventas pasadas.
- Mantiene el control de datos en el dispositivo y tiene migraciones explícitas de Room, respaldo portable cifrado y una estrategia de principal/secundarias.
- La interfaz cuenta con un sistema de diseño, estados de vacío/error, formularios adaptables a tamaño de letra, lenguaje español y pruebas de contraste/semántica según la auditoría existente.

**Riesgos/oportunidades más relevantes**

1. La casilla de selección múltiple de varias listas sigue siendo el hallazgo de accesibilidad táctil **AND-001** documentado (24 dp visual/táctil en el componente citado por la auditoría), y hace más difícil seleccionar filas con precisión.
2. La solicitud inicial agrupada de permisos sigue siendo una fricción documentada (**AND-002**); la cámara y las fotos tienen una justificación contextual más clara que un diálogo en primera apertura.
3. Fotos de productos/servicios no incluidas en el respaldo: al migrar de teléfono puede perderse información visual aunque se recupere el catálogo.
4. Hay una discrepancia importante de documentación sobre versión/esquema: el código actual dice Android `1.0.1`/`versionCode 54` y Room `v16`, mientras `README.md` declara `1.0`/`v13` y `AGENTS.md` habla de `1.0.1`/`v12`.
5. El modelo de datos es funcional pero ha acumulado complejidad: campos de sincronización y locales, borrado lógico, IDs polimórficos y configuración repartida entre Room y DataStore. Conviene reforzar invariantes y documentación de propiedad de datos antes de ampliar el número de locales o las reglas de sync.

## 1. Pasada Ponytail — producto, arquitectura y riesgos

### 1.1 Producto y usuarios

El producto prioriza operación sin Internet, caja e inventario cotidianos, transferencia vía QR, y control del dueño sobre empleados. El enfoque es apropiado para conectividad irregular: el flujo principal no espera un backend, mientras Internet queda para usos acotados (actualizaciones/catálogos; el relay remoto es una opción de sync, no almacenamiento del negocio).

Hay dos perfiles operativos distintos:

- **Principal/dueño:** configuración, licencia, empleados, permisos, precios, inventario, servicios y supervisión.
- **Secundaria/empleado:** selección de operación permitida y envío de eventos a la principal; algunos estados de catálogo/licencia se reciben desde ella.

Esa distinción no es solo de presentación: se refleja en permisos de módulos y repositorios que delegan a implementaciones locales o sincronizadas.

### 1.2 Arquitectura observada

| Capa/módulo | Responsabilidad | Lectura |
|---|---|---|
| `:core` | Dinero CUP, cantidades, fechas, resultados | Núcleo pequeño y reutilizable; evita `Float` para dinero. |
| `:domain` | Modelos, validación, repositorios abstractos, reglas y casos de uso | Buena ubicación de reglas como turno requerido, cotización y anulación. |
| `:data` | Room, SQLCipher, DAOs, mapeo, respaldo, exportación, red y sync | Capa extensa, pero concentra correctamente efectos externos. |
| `:designsystem` | Tema Material 3, tokens, iconos y componentes | Da consistencia y centraliza accesibilidad/estilo. |
| `:app` | Compose, ViewModels, navegación, permisos y flujos de UI | Adecuada para coordinación; vigilar que el crecimiento no desplace lógica al UI. |
| `:licencia` | Contrato y validación de licencias | Separación útil del dominio de POS y de su almacenamiento Android. |

La raíz funciona como **puerta de estados**, no como destino navegable: carga → bloqueo/licencia → onboarding → contenido principal (o bloqueo de secundaria). Esto evita que Back/deep links eludan el bloqueo o el asistente (`app/root/ShalevRoot.kt`, `RootViewModel.kt`). La navegación principal es type-safe; onboarding y bloqueo quedan fuera del `NavHost` (`app/navigation/Routes.kt`).

**Conclusión estructural:** el límite entre UI y reglas parece bien planteado. Los puntos de mayor riesgo arquitectónico no son la falta de capas, sino el coste de coordinación creciente en `:data` (sync y locales) y las invariantes que Room no puede expresar por sí solo.

### 1.3 Flujo operacional dominante

```text
Arranque
  ├─ licencia no desbloqueada → Licencia/bloqueo
  ├─ negocio aún no configurado → onboarding (opcional y retomable)
  └─ principal o secundaria habilitada → Inicio
       ├─ abrir turno (y declarar fondo, según el flujo actual)
       ├─ Venta → escoger productos o servicios → carrito/cotización
       │    ├─ efectivo → guardar venta
       │    └─ transferencia → datos de cliente + referencia + QR → guardar venta
       ├─ caja → entrada/salida con motivo
       ├─ Inventario/Servicios → altas, ajustes, recetas/consumos
       └─ cierre → conteo físico → resumen/archeo → historial
```

**Venta, según el dominio:** `RegistrarVenta` exige turno activo, valida los datos de transferencia cuando aplican, recalcula la cotización con precio/stock actuales, congela los datos de cada línea y delega el registro transaccional. En `VentaRepository` el contrato incluye comprobación del turno, revalidación del stock, venta + detalles + transacción, descuento y movimientos; para elaborados/servicios con receta se descuentan insumos en la misma transacción. El stock puede cambiar entre la pantalla del carrito y el guardado, por lo que cotizar de nuevo y fallar con rollback es una decisión correcta de consistencia.

**Corrección de venta:** el enfoque documentado no destruye el registro: anula con motivo y registra la venta corregida en una operación atómica. La auditoría conserva la original y movimientos compensatorios; esto es mejor para trazabilidad que editar importes históricos silenciosamente.

**Cierre:** el turno congela el resumen de ventas, costo, movimientos, caja y conteo. La secundaria puede pedir cierre y esperar aprobación; el dueño conserva el control sin perder el estado local de la operación.

### 1.4 Ciclo de datos y distribución

- **En reposo:** Room se abre con SQLCipher; la passphrase aleatoria se envuelve con Android Keystore y se guarda en `noBackupFilesDir`. Esto vincula la base viva al dispositivo. No equivale a respaldo portable: el `.shalev` se vuelve a cifrar con contraseña del usuario.
- **Respaldo:** archivo comprimido/cifrado, con autenticación AES-GCM y derivación PBKDF2 cuando hay contraseña. El propio código/documentación califica el modo sin contraseña como ofuscación, no confidencialidad; es importante conservar esa advertencia en la UI y en soporte.
- **Exportación:** PDF/Excel/imagen son salidas de uso/compartición y no sustituyen al backup cifrado completo.
- **Sincronización:** principal/secundaria intercambian catálogo, turnos, ventas y cambios de forma controlada e idempotente (UUID/flags `sincronizado`); sincronía local es la base. El relay Cloudflare, si se compila/configura, es transporte y no repositorio de la base del negocio.
- **Datos fuera de Room:** preferencias, licencia, tipo/estado local y parte de configuración se guardan por otras abstracciones/DataStore. El usuario puede no percibir diferencias, pero respaldo, reinstalación y sincronización deben cubrirlas de manera explícita.

### 1.5 Riesgos estructurales y recomendaciones

**P1 — Alinear documentación con el artefacto actual.** Fuente de verdad inspeccionada: `Android/app/build.gradle.kts` declara 1.0.1/54; `ShalevDatabase.VERSION` es 16 y contiene migraciones 15→16. README y AGENTS contradicen esos valores. Antes de publicar o pedir una auditoría externa, actualizar versión, alcance de migración, módulos y estado verificado en los documentos de entrada. No inferir desde un texto de README qué esquema está instalado.

**P1 — Fotos no viajan en backup.** `UI_UX_IX.md` ya lo lista como pendiente funcional. Ofrecer exportación opcional de fotos dentro del respaldo, o hacer que la importación presente con claridad que las fotos quedan fuera y cómo re-vincularlas. Debe manejarse el límite de tamaño y el URI de origen perdido.

**P1 — Invariantes de locales.** Desde v16 catálogo e historial incluyen `local` como texto, y `almacen_local.nombre` es la clave. En las entidades consultadas, `local` no tiene FK; las relaciones de recetas, servicios y preajustes tampoco llevan `local`. Los repositorios preservan el local al editar y lo asignan al crear, pero eso no demuestra por sí solo que nunca se cree una receta/ajuste cruzado entre locales. Añadir pruebas de dominio/repositorio para no mezclar inventario/servicios entre locales y decidir si renombrar/borrar un local debe actualizar o bloquear referencias históricas. **Es riesgo a validar, no un bug probado.**

**P2 — IDs polimórficos en líneas de venta.** `DetalleVentaEntity.productoId` no tiene FK porque, según `clase`, apunta a tabla producto, servicio o insumo. `clase` permite distinguir la tabla y la UI usa IDs negativos para representar insumos en la lista combinada, pero el almacenamiento conserva la clase/ID de dominio. Es flexible y evita duplicar tablas de líneas, a cambio de integridad referencial no garantizable por Room/SQLite. Mantener tests de mapeo, borrado lógico y export/import para cada clase; evitar reutilizar `productoId` sin mirar `clase`.

**P2 — Clarificar límites de autoridad en sincronización.** El flujo principal/secundaria incluye licencias firmadas, permisos, cambios pendientes, operaciones idempotentes y actualizaciones. Mantener una tabla corta que especifique autoridad por agregado (quién crea/edita/cierra, quién puede corregir, qué ocurre offline/conflicto, cuándo se reintenta), y pruebas de reenvío duplicado y desconexión en cada operación crítica.

**P2 — Portabilidad de URI de fotos.** El contenido de fotos se conserva mediante URI/recurso local, mientras backup no incluye el blob; una URI concedida por proveedor puede dejar de ser legible. El proyecto ya reconoce la ausencia de fotos en backup: conviene comprobar también la vida útil de permisos persistidos/normalización y dar estados de imagen faltante.

## 2. Pasada UI IX Pro MAX — UX, UI y accesibilidad

### 2.1 Evaluación global

La interfaz tiene un lenguaje visual propio sobre Material 3, con componentes centralizados, tema claro/oscuro, tokens, mensajes en español y diseño para teléfono. El producto trabaja en contextos de uso real —una mano, teclado, atención rápida y posibles cortes de conexión— y las decisiones de “no vender sin turno”, feedback por snackbar y CTA de venta/caja son coherentes con ese contexto.

La guía de interfaz existente consigna cumplimiento para tipografía escalable, contraste AA, TalkBack, ventanas centradas, estados vacíos/carga/error y reducción de movimiento. También registra una buena regla de interacción en listas: **marcar no equivale a abrir**; el checkbox selecciona y el toque principal abre ficha, evitando acciones gestuales ambiguas.

### 2.2 Hallazgos priorizados

| Prioridad | Hallazgo | Efecto en usuario | Recomendación |
|---|---|---|---|
| **Alta / conocido** | Casilla múltiple de tamaño táctil reducido (**AND-001**, auditoría 28-oct) en Inventario, Servicios, Registros, Clientes y Pago electrónico. | Selección errónea o difícil; el área pequeña penaliza baja destreza motora. | Elevar el target a `ShalevSize.touchTarget` (48 dp) sin aumentar el dibujo del check; agregar test de tamaño táctil y regrabar capturas. La guía del repo dice que la decisión/capturas siguen pendientes. |
| **Media / conocido** | Solicitud de cámara, avisos y fotos en el primer arranque (**AND-002**). | El usuario recibe permisos antes de experimentar el valor y puede denegarlos sin saber qué desbloquean. | Preferir solicitudes contextuales; mantener solo un permiso inicial si la recuperación del período de prueba lo exige realmente y documentar el motivo. La auditoría mantiene una decisión pendiente. |
| **Media** | Cinco secciones principales con barra de iconos sin etiquetas visibles; etiquetas disponibles en tooltip/TalkBack (desviación aceptada). | Descubribilidad menor para usuarios nuevos; el icono requiere aprendizaje. | Mantener decisión documentada, pero probar onboarding breve con nombres de pestañas y ofrecer rótulos persistentes en pantallas/ancho donde el diseño lo permita. |
| **Media / producto** | Recorrido inicial con bienvenida, tipo de app, seguridad opcional, objetivo, empleados, datos, alertas y prueba según aplicabilidad. | Es útil para configurar, pero puede percibirse largo antes de llegar al POS. | Mostrar duración/progreso total y separar “necesario para empezar” de configuración opcional; medir tasa de omisión y retorno a Ajustes. No quitar pasos sin evaluar la vinculación de secundarias. |
| **Media / conocido** | Respaldo no incluye fotos de productos. | Importación válida, pero fichas aparecen visualmente incompletas en otro teléfono. | Explicar explícitamente el alcance antes de exportar/importar y ofrecer restauración de imágenes o un flujo para re-vincularlas. |
| **Baja / conocido** | No hay pull-to-refresh para listas locales; solo se consideró en Vinculación (AND-003). | No perjudica datos locales que se actualizan automáticamente; en sincronización sí puede faltar affordance de actualización manual. | No agregarlo indiscriminadamente a cada lista local. En Vinculación, priorizar un control/gesto visible de sincronizar y estado “última sincronización”; el refresh es una opción si resulta familiar. |

### 2.3 Aspectos UX sólidos

- El flujo de venta se ancla a un turno: explica y hace visible el prerequisito de manera operativa.
- Los elaborados/servicios comunican disponibilidad basada en insumos (“alcanza para N”), evitando la abstracción de una producción previa.
- Transferencia solicita datos de cliente y número de referencia; el parser ofrece entrada asistida, sin leer la bandeja SMS ni pedir `READ_SMS`.
- La edición/corrección conserva historial; confirmación antes de lo destructivo es una política consistente.
- La experiencia local no simula “refrescar” datos que no dependen de la red, reduciendo affordances engañosos.
- La accesibilidad está respaldada por decisiones comprobables (fontScale, contrast test, etiquetas TalkBack, respeto a gestos del sistema) según la auditoría del repositorio.

### 2.4 Flujo UX: puntos de abandono a probar

1. **Primera apertura → primer valor:** medir cuánto tarda una instalación limpia en ver Inicio y cuánto tarda una secundaria en vincularse. El flujo incluye licencia/onboarding y puede tener dos recorridos muy distintos.
2. **Inicio → abrir turno:** confirmar que el CTA de abrir turno y el de vender dejan clara la dependencia y el fondo requerido, con lenguaje de negocio.
3. **Venta en condiciones de carrera:** cambiar stock/precio desde otra secundaria mientras el carrito está abierto; el rechazo al guardar debe identificar el artículo y permitir corregir sin perder el carrito.
4. **Transferencia:** verificar el caso de SMS sin importe, duplicado o referencia inválida y que el usuario siempre pueda editar manualmente; no se debe aceptar referencia como verificación bancaria del pago.
5. **Cierre/arqueo:** comprobar que “esperado”, “contado” y diferencia se entiendan sin cálculos externos, y que las transferencias no se mezclen con efectivo.
6. **Respaldo/restauración:** simular cambio de teléfono, contraseña incorrecta, archivo dañado y ausencia de fotos; comunicar de forma anticipada qué se recuperará.
7. **Offline y secundaria:** hacer venta offline, reconectar, repetir sync y verificar que no haya duplicados ni cambios silenciosos de catálogo.

Estas son propuestas de prueba, no resultados de una ejecución manual en este turno.

## 3. Estructura de datos

### 3.1 Entidades principales y relaciones

```text
AlmacenLocal(nombre)
   ├── Producto(local) ──< RecetaLinea >── Insumo(local)
   ├── Servicio(local) ──< ServicioInsumo >── Insumo
   └── Turno(local) ──< Venta(local) ──< DetalleVenta
                              ├── 0..1 Transaccion
                              └── Movimientos inventario (ventaId, turnoId, entidad/id)
Turno ──< MovimientoCaja
Preajuste ──< PreajusteProducto / PreajusteServicio
Perfil ── tarjetas + teléfonos seleccionados para cobro
Empleado ── secundarios vinculados/permisos/estado de sincronización
ClienteFijo ── CI único; autocompletado de transferencia
```

Es un diagrama conceptual resumido, no reproduce todas las columnas ni cardinalidades implementadas. El esquema Room actual declara versión **16** en `data/db/ShalevDatabase.kt`; la migración 15→16 añade locales al catálogo e historial. Las entidades visibles están en `data/db/entity/Entities.kt`.

### 3.2 Decisiones de modelado relevantes

- **Dinero:** CUP en dominio como `Cup`; almacenamiento como centavos `Long` (p. ej. `precioVentaCent`, `importeCent`). Evita errores binarios de coma flotante.
- **Cantidades:** existencias de productos en unidades enteras; de insumos en milésimas (`cantidadMil`), que soportan recetas fraccionarias.
- **Histórico inmutable:** las líneas guardan nombre, categoría, cantidad, precio base, precio unitario y costo unitario. La venta anterior no cambia si cambia el artículo.
- **Relación receta:** N:M producto/insumo con cantidad por unidad; servicio/insumo equivalente. Los elaborados se venden sin stock propio y consumen insumos al vender, todo atómico.
- **Inventario auditable:** `movimiento` registra delta, existencia posterior, tipo, artículo, turno/venta, nota y autor; anular genera movimiento compensatorio en vez de borrar la historia.
- **Borrado lógico:** productos y servicios tienen `eliminado`, protegiendo registros y referencias de catálogo.
- **Transferencia:** una venta tiene una transacción opcional (única por `ventaId`) que conserva cliente, importe, referencia y datos de cobro utilizados.
- **Empleados/sync:** UUID únicos en eventos clave, banderas de sincronización y `empleadoId` modelan origen y deduplicación; la secundaria recibe permisos y licencia de la principal.
- **Persistencia separada:** configuración de pantalla, preferencias, licencia y parte del estado no son entidades de negocio de Room; para mapa completo de respaldo y privacidad hay que considerar también DataStore/Keystore.

### 3.3 Evaluación de calidad de datos

**Bien resuelto**

- La representación de cantidades y dinero usa enteros en la persistencia.
- Hay claves/índices para consultas e idempotencia; varias relaciones declaran FK y cascadas/restricciones.
- La migración está explícita y hay esquemas exportados en el repositorio hasta el esquema actual observado.
- Respaldo portable no depende de la clave Keystore del dispositivo; hay protección de integridad/confidencialidad documentada.

**Límites que requieren atención**

- No hay FK posible para la referencia polimórfica `DetalleVentaEntity.productoId`; la coherencia depende de la columna `clase` y del código de mapeo.
- `local` se modela como texto en varias tablas y no como FK declarada en las entidades inspeccionadas. El modelo permite evolucionar locales rápidamente, pero conviene probar borrado, renombre, importación y asignación cruzada.
- El mismo producto/insumo puede referenciarse en una receta; no se aprecia local compuesto en la clave de receta. Validar por lógica que ambos pertenecen al mismo local.
- Los estados de sincronización y actualizaciones distribuidas son complejos; hacer visibles invariantes de idempotencia/conflicto en pruebas automatizadas, no solo en comentarios.
- Fotos almacenadas por URI son una dependencia externa al registro relacional y no se transportan en el respaldo actual.

## 4. Plan de acción sugerido

### Antes de la próxima entrega

1. Corregir `README.md`, `AGENTS.md` y documentos de verificación para que todos reflejen Android 1.0.1/54, Room 16 y el estado real de pruebas/release.
2. Resolver AND-001 y regenerar las capturas Roborazzi afectadas; añadir/verificar el test de área táctil.
3. Asegurar que UI de respaldo explica las fotos no incluidas y la importancia de usar contraseña.
4. Ejecutar las pruebas automatizadas prescritas en `Pruebas.md`/`PRUEBAS_DISPOSITIVO.md`, incluyendo migración desde versiones soportadas y sync de dos teléfonos.

### Próxima iteración de producto/arquitectura

1. Decidir si los permisos iniciales se mueven completamente a contexto (AND-002), con prueba del impacto en recuperación de trial.
2. Añadir pruebas de aislamiento por local: recetas, servicios, preajustes, ventas, movimientos, respaldo/import y sincronización.
3. Considerar soporte de imágenes en respaldo con límites y UX explícitos.
4. Publicar un mapa de autoridad/conflictos de sync y automatizar duplicados, reintentos, offline y corrección/anulación entre dispositivos.
5. Validar con usuarios nuevos el descubrimiento de pestañas solo iconográficas y el tiempo de onboarding.

## 5. Fuentes inspeccionadas

- Arquitectura/estado: `AGENTS.md`, `README.md`, `UI_UX_IX.md`, `docs/AUDITORIA_UI_UX_IX_2026-10-28.md`.
- UI y navegación: `Android/app/src/main/kotlin/cu/shalev/app/root/`, `navigation/`, `onboarding/`, `venta/`, `inicio/`, `inventario/`, `registros/`.
- Dominio: `Android/domain/src/main/kotlin/cu/shalev/domain/model/`, `repository/`, `usecase/VentaUseCases.kt`, `usecase/OnboardingUseCases.kt`.
- Persistencia: `Android/data/src/main/kotlin/cu/shalev/data/db/`, `db/entity/Entities.kt`, `db/dao/`, `repository/`, `sync/`, `security/`, `respaldo/`.
- Build versionada: `Android/app/build.gradle.kts`, `Android/data/src/main/kotlin/cu/shalev/data/db/ShalevDatabase.kt`, `Android/data/schemas/`.
