# Logotipos y prefijos bancarios cubanos

**Estado:** integración visual orientativa. Las fuentes consultadas no permiten asegurar una tabla completa y actual de BIN por banco ni se verificaron los derechos de redistribución de los tres archivos gráficos. Antes de publicar una versión, confirmar ambos puntos con los bancos o sustituir las imágenes por recursos autorizados.

## Archivos incluidos

| Recurso | Banco | Fuente consultada para el logotipo |
|---|---|---|
| `app/src/main/res/drawable-nodpi/logo_banco_bpa.png` | Banco Popular de Ahorro (BPA) | [Artículo de Wikipedia sobre BPA](https://es.wikipedia.org/wiki/Banco_Popular_de_Ahorro), con una imagen servida desde Wikimedia Commons. No se verificó la licencia específica del archivo descargado. |
| `app/src/main/res/drawable-nodpi/logo_banco_bandec.png` | Banco de Crédito y Comercio (BANDEC) | [Artículo de EcuRed sobre BANDEC](https://www.ecured.cu/Banco_de_Cr%C3%A9dito_y_Comercio). EcuRed no se tomó como prueba de licencia de redistribución. |
| `app/src/main/res/drawable-nodpi/logo_banco_banmet.png` | Banco Metropolitano (BANMET) | [Ficha de Banco Metropolitano en el portal de La Habana](https://www.lahabana.gob.cu/entity_detalles/es/20/banco-metropolitano-s-a). La ficha no acredita por sí sola permiso para reutilizar el logotipo. |

Que una imagen sea visible o descargable desde una página pública **no significa que sea de dominio público**. Los nombres, emblemas y marcas siguen perteneciendo a sus titulares; no se presume autorización comercial. El recorte y tratamiento de los recursos para la interfaz no cambia esa situación. Conservar esta advertencia hasta que se verifiquen las licencias y, si corresponde, añadir la atribución que exijan.

## Prefijos

La fuente oficial de [ETECSA sobre Transfermóvil](https://www.etecsa.cu/es/transfermovil) confirma la participación de BPA, BANDEC y Banco Metropolitano en la plataforma, pero no publica una tabla de BIN que permita atribuir cada tarjeta a un banco. Las asignaciones de abajo son deliberadamente conservadoras y **solo sirven para elegir un logotipo sugerido**:

| Prefijo de tarjeta | Logo sugerido en Shalev | Base y nivel de confianza |
|---|---|---|
| `9205` | BPA | Un comentario publicado en el sitio del [Banco Central de Cuba](https://www.bc.gob.cu/noticia/canales-de-atencion-a-la-poblacion/925) identifica un ejemplo de tarjeta BPA con ese prefijo. Es un ejemplo, no un registro oficial de BIN completo. |
| `9225` | BANDEC | Atribuido a BANDEC por una [fuente comercial](https://veltropay.es/bancos); las fuentes oficiales consultadas no ofrecen una tabla vigente que confirme esa asignación de forma exhaustiva. |
| `9226` | BANMET | Atribuido a BANMET por la misma [fuente comercial](https://veltropay.es/bancos); no se encontró una tabla oficial de BIN vigente para corroborarlo. |
| `9224` | **Sin logo** | Hay atribuciones contradictorias: el comentario del sitio del BCC citado arriba lo usa como ejemplo de BANDEC, mientras que la fuente comercial lo asigna a BPA. No se elige ninguna. |
| `9204` y cualquier otro | **Sin logo** | Una referencia periodística menciona `9204`/`9205` entre tarjetas CUP, pero no asigna `9204` a un banco. Los prefijos no documentados se dejan sin clasificar. |

La app ignora separadores y espacios, compara los primeros cuatro dígitos y deja sin logo las tarjetas incompletas, los prefijos desconocidos y `9224`. El mapeo implementado vive en `PagoElectronicoLogic.kt`, en `BancoCubano`/`bancoPorTarjeta`. No es una comprobación de emisor, titularidad, moneda ni capacidad de transferencia.

En Pago electrónico se muestra un aviso de que el logo se estima por el prefijo y no verifica el banco ni la titularidad. La confirmación real debe hacerse en Transfermóvil o con el titular, independientemente del logo mostrado.

## Presentación (0.30.1)

- Los tres logos se muestran en una **caja de 96 × 48 dp** (`MedidasLogoBanco`), con esquinas redondeadas del token `ShalevRadius.sm` y fondo blanco, para que BPA, BANDEC y BANMET ocupen exactamente el mismo espacio en la lista y en la hoja de edición. Los PNG no se modificaron.
- BANDEC (rojo a sangre, sin margen propio) se recorta para llenar la caja (`ContentScale.Crop`); BPA (transparente) y BANMET (fondo blanco y formato muy alargado) se ajustan completos (`ContentScale.Fit`) con 6 dp de margen, sin deformarse.
- El recurso de cada tarjeta lo decide `recursoLogoBanco(banco)`, que parte del mismo `bancoPorTarjeta` (primeros 4 dígitos) que el resto de la pantalla; no hay una segunda tabla.

## Revisión de la asignación por prefijo (0.30.1)

Se volvió a contrastar el mapeo con fuentes públicas. **No existe una fuente oficial única que ligue un prefijo a un banco**, y hay discrepancias que conviene conocer:

- `9225` y `9235`: las comunicaciones del BANDEC los citan para personas naturales, pero el Banco Central de Cuba pidió en 2021 que las tarjetas MLC de destino empezaran por `9225` en los **tres** bancos, y hay referencias de BPA con `9225`/`9235`. El logo de BANDEC para `9225` es por tanto una **sugerencia**, no una certeza.
- `9226`: la fuente comercial lo asigna a Banco Metropolitano, pero comunicaciones del BANDEC lo citan para trabajadores por cuenta propia y agricultores. Puede mostrar el logo de BANMET en una tarjeta de BANDEC.
- `9227` y otros: sin logo.

Por eso el mapeo sigue siendo conservador y la pantalla de Pago electrónico mantiene el aviso de que el logo es una estimación. Si el banco confirma una tabla de BIN, se cambia solo `BancoCubano`/`bancoPorTarjeta` y sus pruebas (`PagoLogicTest`).

## Miniaturas en Pago electrónico (2026-11-01)

Se corrigió la correspondencia entre banco, nombre de recurso y contenido de las tres miniaturas. Los prefijos y la advertencia de identificación orientativa no cambian.
