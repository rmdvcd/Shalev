# Integración de GFC en Shalev

## Referencia revisada

Repositorio `rmdvcd/GFC`, rama `main`, commit `4db82cbeb468ef12afeefc7e2d225fb21ef364a3`. GFC Mobile usa Flutter y su propia base SQLCipher; Shalev es Kotlin/Compose. No es posible copiar sus pantallas o su almacenamiento directamente sin adaptar el dominio y sus contratos.

GFC gestiona fichas (borrador/finalizada), partidas, catálogos de insumos y tarifas, cálculo por categoría en CUP y MLC, margen por porcentaje/factor, `.ficha148`, PDF, CSV, Excel, respaldo y licencia propios. El coeficiente indirecto se conserva sin aplicarse automáticamente. Sus PDF son referenciales, no una certificación normativa. El intercambio `.ficha148` es un ZIP no cifrado y exige validación estructural y semántica antes de confirmar la importación.

## Estado en Shalev

Se ha incorporado **Herramientas → Ficha de costo como calculadora temporal**, no como la integración completa de GFC. Las cuatro categorías, subtotal redondeado por partida y estimación por margen siguen el modelo de GFC. A diferencia de GFC, Shalev usa CUP y USD (no MLC); sus totales siguen separados y no se aplica un coeficiente indirecto. La calculadora de cambio manual es independiente y no modifica las partidas ni los totales. No se debe interpretar MLC como USD en una futura importación; el contrato `.ficha148` de GFC requiere resolver explícitamente esa incompatibilidad.

Los datos viven solo en memoria mientras la pantalla está abierta y no están en el respaldo. La interfaz lo advierte y pide confirmación antes de salir. Todavía no hay fichas guardadas, catálogos, estados, coeficiente indirecto, exportación, importación ni sincronización. No usar como ficha oficial.

## Pendiente para paridad

1. Modelar fichas y catálogos persistentes en la base SQLCipher existente, con migración Room, esquema generado por Room y pruebas de migración; integrar su copia/restauración transaccional en `.shalev` y comprobar compatibilidad con respaldos anteriores.
2. Crear el listado, edición y borrado de fichas, estados, catálogos y protección contra pérdidas de cambios. Definir acceso y sincronización para secundarias sin exponer datos no autorizados.
3. Implementar el contrato v1 de `.ficha148` con comprobación acotada de ZIP, esquema y semántica, previsualización y confirmación antes de guardar. Conservar `extensionData` y catálogos de intercambio.
4. Adaptar exportación PDF referencial, CSV y Excel, sin presentar fórmulas o campos no confirmados como obligaciones legales. Mantener el sistema de licencias y permisos de Shalev; no copiar claves de GFC.
5. Ejecutar pruebas unitarias, integración y UI en dispositivo; revisar modo oscuro, pantallas estrechas, letra grande, TalkBack y navegación.

## Verificación de esta etapa

Las pruebas aisladas del cálculo se compilaron con Kotlin 2.0.21 y se ejecutaron con JDK 17 mediante un runner mínimo para las aserciones JUnit. La suite Gradle y la compilación Android no pudieron ejecutarse: el entorno no tiene el SDK y el acceso de red a la distribución de Gradle y Google Maven está bloqueado. La prueba de interfaz añadida queda pendiente de ejecución en emulador o dispositivo.
