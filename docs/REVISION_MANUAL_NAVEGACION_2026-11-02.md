# Revisión de navegación, manual y ventanas — 2026-11-02

## Alcance y evidencia

Revisión estática de las rutas y la navegación principal de Android, el manual incluido,
las pantallas de Inicio, Inventario, Servicios, Registros y Ajustes y los componentes
compartidos de barras y ventanas. Se consultó también la auditoría anterior
[AUDITORIA_UI_UX_IX_2026-10-28.md](AUDITORIA_UI_UX_IX_2026-10-28.md), que inventaría
las demás pantallas y emergentes de Android e iOS. Esa auditoría es histórica y no
sustituye una inspección visual de esta versión.

## Hallazgos tratados

- El esquema de ayuda anterior incluía Ajustes en la barra inferior; se abre desde Inicio.
  Se retiró el esquema fijo, que además no podía reflejar los módulos o permisos visibles.
- La ayuda de Ajustes se llamaba «Ayuda» mientras contenía un manual; ahora se identifica
  como «Manual de usuario» y agrupa el resumen por ventana. Se añadieron tareas del manual
  antes ausentes: caja, correcciones, precios y restauración.
- El esquema de respaldo decía que el archivo estaba cifrado siempre, aunque la contraseña
  es opcional. Se retiró esa afirmación.
- La ventana contextual anidaba dos desplazamientos verticales y no mostraba el mensaje
  alternativo si no tenía temas. Ahora usa el desplazamiento de la ventana común y muestra
  la orientación. El icono de su acción ya no parece una confirmación de cambios.
- La acción de omitir de los diálogos de cierre estaba centrada pese a documentarse en la
  esquina inferior derecha; se alinea al final de su línea, dejando la confirmación centrada.
- El README y el manual Markdown afirmaban que Ajustes tenía un botón contextual superior;
  se corrigió: el acceso se encuentra en la lista de Ajustes.

## Pendiente de verificación

No se ha ejecutado la aplicación ni se han inspeccionado visualmente todas las pantallas,
orientaciones y diálogos en este entorno. Tampoco se ha verificado IShalev en esta revisión.
Antes de dar por completa la auditoría, comprobar en Android la navegación de vuelta, la
selección durante la venta y los permisos de secundarias; revisar ventanas y pies en modo
claro/oscuro, horizontal, teclado visible, texto grande y TalkBack. Regrabar las capturas
Roborazzi afectadas y regenerar el PDF de usuario desde el Markdown cuando se disponga del
entorno de documentación. El PDF existente no se ha modificado.
