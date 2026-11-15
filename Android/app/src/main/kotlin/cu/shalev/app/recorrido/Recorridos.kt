package cu.shalev.app.recorrido

import cu.shalev.designsystem.component.CoachMarkObjetivos
import cu.shalev.designsystem.component.CoachMarkPaso

/**
 * Claves de los elementos que resaltan los recorridos. Cada pantalla o ventana las pone con
 * [cu.shalev.designsystem.component.coachMark]; fuera de un recorrido el modificador no hace nada.
 *
 * Las de las ventanas emergentes (`VENTANA_*`, `FILTRO_*`…) se repiten entre ventanas: cada ventana tiene su propio
 * recorrido, así que no chocan. El sistema de diseño marca además el botón «Ayuda» de la barra superior y la
 * confirmación de cada diálogo ([CoachMarkObjetivos]).
 */
object ObjetivosRecorrido {
    // Inicio (turno y caja)
    const val CAJA_LOCAL = "caja_local"
    const val CAJA_TURNO = "caja_turno"
    const val CAJA_VENDER = "caja_vender"
    const val CAJA_MOVIMIENTO = "caja_movimiento"
    const val CAJA_ALERTAS = "caja_alertas"
    const val CAJA_ACCESOS = "caja_accesos"
    const val CAJA_RESUMEN = "caja_resumen"

    // Venta
    const val VENTA_CARRITO = "venta_carrito"
    const val VENTA_AGREGAR = "venta_agregar"
    const val VENTA_TOTAL = "venta_total"
    const val VENTA_METODO = "venta_metodo"
    const val VENTA_COBRAR = "venta_cobrar"

    // Inventario
    const val INVENTARIO_LISTA = "inventario_lista"
    const val INVENTARIO_BUSCAR = "inventario_buscar"
    const val INVENTARIO_FIJADOS = "inventario_fijados"
    const val INVENTARIO_FILTRO = "inventario_filtro"
    const val INVENTARIO_EXPORTAR = "inventario_exportar"
    const val INVENTARIO_AGREGAR = "inventario_agregar"

    // Servicios
    const val SERVICIOS_LISTA = "servicios_lista"
    const val SERVICIOS_BUSCAR = "servicios_buscar"
    const val SERVICIOS_FIJADOS = "servicios_fijados"
    const val SERVICIOS_FILTRO = "servicios_filtro"
    const val SERVICIOS_EXPORTAR = "servicios_exportar"
    const val SERVICIOS_AGREGAR = "servicios_agregar"

    // Registros
    const val REGISTROS_PESTANAS = "registros_pestanas"
    const val REGISTROS_BUSCAR = "registros_buscar"
    const val REGISTROS_RESUMEN = "registros_resumen"
    const val REGISTROS_LISTA = "registros_lista"
    const val REGISTROS_FILTRO = "registros_filtro"
    const val REGISTROS_EXPORTAR = "registros_exportar"

    // Detalle de un turno
    const val TURNO_PESTANAS = "turno_pestanas"
    const val TURNO_TOTAL = "turno_total"
    const val TURNO_DETALLE = "turno_detalle"
    const val TURNO_COMPARTIR = "turno_compartir"

    // Formularios de producto, servicio e insumo
    const val FORM_NOMBRE = "form_nombre"
    const val FORM_PRECIOS = "form_precios"
    const val FORM_EXISTENCIAS = "form_existencias"
    const val FORM_AVISOS = "form_avisos"
    const val FORM_CATEGORIA = "form_categoria"
    const val FORM_FOTO = "form_foto"
    const val FORM_INSUMOS = "form_insumos"
    const val FORM_GUARDAR = "form_guardar"

    // Ajustes (grupos de filas)
    const val AJUSTES_CONFIGURACION = "ajustes_configuracion"
    const val AJUSTES_NEGOCIO = "ajustes_negocio"
    const val AJUSTES_VENTAS = "ajustes_ventas"
    const val AJUSTES_SEGURIDAD = "ajustes_seguridad"
    const val AJUSTES_DATOS = "ajustes_datos"
    const val AJUSTES_AYUDA = "ajustes_ayuda"

    // Módulos de Ajustes
    const val PERFIL_DATOS = "perfil_datos"
    const val PERFIL_GUARDAR = "perfil_guardar"
    const val PERFIL_MODULOS = "perfil_modulos"
    const val PERFIL_ID_LICENCIA = "perfil_id_licencia"
    const val LICENCIA_ESTADO = "licencia_estado"
    const val LICENCIA_PRECIOS = "licencia_precios"
    const val LICENCIA_RENOVAR = "licencia_renovar"
    const val LICENCIA_PASOS = "licencia_pasos"
    const val LICENCIA_DATOS = "licencia_datos"
    const val LICENCIA_ENVIAR = "licencia_enviar"
    const val VINCULACION_RED = "vinculacion_red"
    const val VINCULACION_EMPLEADOS = "vinculacion_empleados"
    const val VINCULACION_AGREGAR = "vinculacion_agregar"
    const val VINCULACION_SECUNDARIA = "vinculacion_secundaria"
    const val VINCULACION_SINCRONIZAR = "vinculacion_sincronizar"
    const val VINCULACION_CAMBIOS = "vinculacion_cambios"
    const val VINCULACION_DESVINCULAR = "vinculacion_desvincular"
    const val QR_CAMARA = "qr_camara"
    const val PAGO_LISTA = "pago_lista"
    const val PAGO_AGREGAR = "pago_agregar"
    const val PRECIOS_EXPLICACION = "precios_explicacion"
    const val PRECIOS_LISTA = "precios_lista"
    const val PRECIOS_NUEVO = "precios_nuevo"
    const val RESPALDO_EXPORTAR = "respaldo_exportar"
    const val RESPALDO_CONTRASENA = "respaldo_contrasena"
    const val RESPALDO_IMPORTAR = "respaldo_importar"
    const val AYUDA_SECCION = "ayuda_seccion"
    const val AYUDA_TEMA = "ayuda_tema"
    const val SOPORTE_FICHA = "soporte_ficha"
    const val SOPORTE_CONTACTO = "soporte_contacto"
    const val ONBOARDING_PROGRESO = "onboarding_progreso"
    const val ONBOARDING_OMITIR = "onboarding_omitir"
    const val ONBOARDING_SIGUIENTE = "onboarding_siguiente"

    // Ventanas emergentes: piezas comunes
    const val VENTANA_DATOS = "ventana_datos"
    const val VENTANA_LISTA = "ventana_lista"
    const val VENTANA_OPCIONES = "ventana_opciones"
    const val VENTANA_NOMBRE = "ventana_nombre"
    const val VENTANA_NUMERO = "ventana_numero"
    const val VENTANA_IMPORTE = "ventana_importe"
    const val VENTANA_CANTIDAD = "ventana_cantidad"
    const val VENTANA_MOTIVO = "ventana_motivo"
    const val VENTANA_TIPO = "ventana_tipo"
    const val VENTANA_METODO = "ventana_metodo"
    const val VENTANA_TOTAL = "ventana_total"
    const val VENTANA_BUSCAR = "ventana_buscar"
    const val VENTANA_CONTRASENA = "ventana_contrasena"
    const val VENTANA_QR = "ventana_qr"
    const val VENTANA_ACCIONES = "ventana_acciones"
    const val VENTANA_AGREGAR = "ventana_agregar"
    const val VENTANA_EDITAR = "ventana_editar"
    const val VENTANA_COMPARTIR = "ventana_compartir"
    const val VENTANA_ELIMINAR = "ventana_eliminar"
    const val VENTANA_GUARDAR = "ventana_guardar"
    const val VENTANA_LIMPIAR = "ventana_limpiar"
    const val VENTANA_APLICAR = "ventana_aplicar"
    const val VENTANA_ALCANCE = "ventana_alcance"
    const val VENTANA_ENCABEZADO = "ventana_encabezado"
    const val VENTANA_FORMATOS = "ventana_formatos"

    // Filtros
    const val FILTRO_ESTADO = "filtro_estado"
    const val FILTRO_TIPO = "filtro_tipo"
    const val FILTRO_CATEGORIA = "filtro_categoria"
    const val FILTRO_PERIODO = "filtro_periodo"
    const val FILTRO_IMPORTE = "filtro_importe"
    const val FILTRO_VENDEDOR = "filtro_vendedor"

    // Conteo de efectivo y cierre de turno
    const val CONTEO_ESPERADO = "conteo_esperado"
    const val CONTEO_CONTADO = "conteo_contado"
    const val CONTEO_CUADRA = "conteo_cuadra"
    const val CONTEO_MODO = "conteo_modo"
    const val CIERRE_CONTEO = "cierre_conteo"
    const val CIERRE_OMITIR = "cierre_omitir"
    const val CIERRE_ATRAS = "cierre_atras"

    // Ajuste de precios (ventana)
    const val PRECIO_CAMBIO = "precio_cambio"
    const val PRECIO_CUANDO = "precio_cuando"
    const val PRECIO_ARTICULOS = "precio_articulos"
    const val PRECIO_VISTA = "precio_vista"

    // App de un empleado (ventana)
    const val EMPLEADO_CIERRE = "empleado_cierre"
    const val EMPLEADO_PERMISOS = "empleado_permisos"
    const val EMPLEADO_COBRO = "empleado_cobro"
    const val EMPLEADO_CODIGO = "empleado_codigo"
}

private fun paso(objetivo: String, titulo: String, descripcion: String) = CoachMarkPaso(objetivo, titulo, descripcion)

/** «Ayuda» de la barra superior: cierra el recorrido de cada pestaña principal. */
private val PASO_AYUDA = paso(CoachMarkObjetivos.AYUDA, "Ayuda", "Toca Ayuda para leer la guía de esta pantalla o repetir este recorrido.")

/** Confirmación de un diálogo ([cu.shalev.designsystem.component.ShalevDialog] la marca sola). */
private fun confirmar(titulo: String, descripcion: String) = paso(CoachMarkObjetivos.CONFIRMAR, titulo, descripcion)

private val FILTRO_PIE = listOf(
    paso(ObjetivosRecorrido.VENTANA_LIMPIAR, "Limpiar", "Toca Limpiar para quitar todos los filtros."),
    paso(ObjetivosRecorrido.VENTANA_APLICAR, "Aplicar", "Toca Aplicar para ver la lista filtrada."),
)

private fun exportar(alcance: String) = listOf(
    paso(ObjetivosRecorrido.VENTANA_ALCANCE, "Qué se exporta", alcance),
    paso(ObjetivosRecorrido.VENTANA_ENCABEZADO, "Encabezado", "Escribe un título para la promoción. Es opcional."),
    paso(ObjetivosRecorrido.VENTANA_FORMATOS, "Elige el formato", "Toca PDF, Excel o imagen para enviarlo o guardarlo."),
)

/**
 * Un recorrido por pantalla o ventana emergente, con hasta 8 pasos agrupados (los controles relacionados comparten
 * paso). [clave] da nombre al indicador guardado en DataStore (`<clave>_tour_shown`, ver [OnboardingPreferences]).
 * Los pasos cuyo objetivo no esté al empezar se saltan (p. ej. «Vender» con el turno cerrado o «Exportar» sin permiso).
 *
 * Textos: título corto (máx. 28 caracteres) y una frase (máx. 120) que empieza por lo que hay que hacer.
 */
enum class Recorrido(val clave: String, val pasos: List<CoachMarkPaso>) {
    // ---------------- Pestañas principales ----------------
    CAJA(
        "caja",
        listOf(
            paso(ObjetivosRecorrido.CAJA_LOCAL, "Local", "Elige en qué local trabajas. Cada uno tiene su inventario."),
            paso(ObjetivosRecorrido.CAJA_TURNO, "Turno", "Toca para abrir el turno con el fondo de caja. Al terminar, tócalo para contar y cerrar."),
            paso(ObjetivosRecorrido.CAJA_VENDER, "Vender", "Toca Vender para cobrar productos o servicios. Solo con el turno abierto."),
            paso(ObjetivosRecorrido.CAJA_MOVIMIENTO, "Entradas y salidas", "Toca Caja para anotar efectivo que entra o sale sin ser una venta."),
            paso(ObjetivosRecorrido.CAJA_ALERTAS, "Alertas", "Toca una alerta para ver los artículos agotados, escasos o por caducar."),
            paso(ObjetivosRecorrido.CAJA_ACCESOS, "Pagos y precios", "Toca para ver tus cuentas de cobro y los ajustes de precio activos."),
            paso(ObjetivosRecorrido.CAJA_RESUMEN, "Resumen", "Elige el período para ver ventas, ganancia y lo más vendido."),
            PASO_AYUDA,
        ),
    ),
    VENTAS(
        "ventas",
        listOf(
            paso(ObjetivosRecorrido.VENTA_CARRITO, "Carrito", "Ajusta cada cantidad con −1 y +1, o toca Quitar."),
            paso(ObjetivosRecorrido.VENTA_AGREGAR, "Agregar", "Toca Agregar para volver a la lista y sumar más artículos."),
            paso(ObjetivosRecorrido.VENTA_TOTAL, "Total", "Revisa aquí el importe que vas a cobrar."),
            paso(ObjetivosRecorrido.VENTA_METODO, "Forma de pago", "Toca Efectivo o Transferencia antes de cobrar."),
            paso(ObjetivosRecorrido.VENTA_COBRAR, "Cobrar", "Toca Continuar: en efectivo ves el comprobante; con transferencia, el QR."),
        ),
    ),
    INVENTARIO(
        "inventario",
        listOf(
            paso(ObjetivosRecorrido.INVENTARIO_LISTA, "Tus productos", "Toca un artículo para ver su ficha. Marca la casilla para elegir varios."),
            paso(ObjetivosRecorrido.INVENTARIO_BUSCAR, "Buscar", "Escribe parte del nombre para encontrar un artículo."),
            paso(ObjetivosRecorrido.INVENTARIO_FIJADOS, "Seleccionados", "Revisa aquí los artículos marcados antes de exportarlos o borrarlos."),
            paso(ObjetivosRecorrido.INVENTARIO_FILTRO, "Filtrar", "Toca para ver solo un estado, un tipo o una categoría."),
            paso(ObjetivosRecorrido.INVENTARIO_EXPORTAR, "Exportar", "Toca para enviar la lista en PDF, Excel o como promoción."),
            paso(ObjetivosRecorrido.INVENTARIO_AGREGAR, "Agregar", "Toca Agregar para crear un producto o un insumo."),
            PASO_AYUDA,
        ),
    ),
    SERVICIOS(
        "servicios",
        listOf(
            paso(ObjetivosRecorrido.SERVICIOS_LISTA, "Tus servicios", "Toca un servicio para ver su ficha. Marca la casilla para elegir varios."),
            paso(ObjetivosRecorrido.SERVICIOS_BUSCAR, "Buscar", "Escribe parte del nombre para encontrar un servicio."),
            paso(ObjetivosRecorrido.SERVICIOS_FIJADOS, "Seleccionados", "Revisa aquí los servicios marcados antes de exportarlos o borrarlos."),
            paso(ObjetivosRecorrido.SERVICIOS_FILTRO, "Filtrar", "Toca para ver solo un tipo de servicio."),
            paso(ObjetivosRecorrido.SERVICIOS_EXPORTAR, "Exportar", "Toca para enviar la lista en PDF, Excel o como promoción."),
            paso(ObjetivosRecorrido.SERVICIOS_AGREGAR, "Agregar", "Toca Agregar para crear un servicio con su precio."),
            PASO_AYUDA,
        ),
    ),
    REGISTROS(
        "registros",
        listOf(
            paso(ObjetivosRecorrido.REGISTROS_PESTANAS, "Pestañas", "Toca una pestaña para ver ventas, transferencias, turnos u otros registros."),
            paso(ObjetivosRecorrido.REGISTROS_BUSCAR, "Buscar", "Escribe para encontrar un registro concreto."),
            paso(ObjetivosRecorrido.REGISTROS_RESUMEN, "Resumen", "Lee aquí cuántos registros ves y su total."),
            paso(ObjetivosRecorrido.REGISTROS_LISTA, "Registros", "Toca una fila para ver su detalle y corregirla."),
            paso(ObjetivosRecorrido.REGISTROS_FILTRO, "Filtrar", "Toca para acotar por fechas, importe o vendedor."),
            paso(ObjetivosRecorrido.REGISTROS_EXPORTAR, "Exportar", "Toca para enviar o guardar la tabla en PDF o Excel."),
            PASO_AYUDA,
        ),
    ),
    AJUSTES(
        "ajustes",
        listOf(
            paso(ObjetivosRecorrido.AJUSTES_CONFIGURACION, "Configuración", "Toca para completar los pasos pendientes de la puesta en marcha."),
            paso(ObjetivosRecorrido.AJUSTES_NEGOCIO, "Tu negocio", "Edita tu perfil, tu licencia y las apps de tus empleados."),
            paso(ObjetivosRecorrido.AJUSTES_VENTAS, "Cobros y precios", "Configura cuentas de cobro, locales, ajustes de precio y avisos de existencias."),
            paso(ObjetivosRecorrido.AJUSTES_SEGURIDAD, "Seguridad", "Revisa los permisos del teléfono y activa la clave de acceso."),
            paso(ObjetivosRecorrido.AJUSTES_DATOS, "Datos", "Haz una copia de seguridad o busca una versión nueva."),
            paso(ObjetivosRecorrido.AJUSTES_AYUDA, "Ayuda", "Abre el manual o escribe a soporte si tienes dudas."),
        ),
    ),

    // ---------------- Pantallas secundarias ----------------
    TURNO_DETALLE(
        "turno_detalle",
        listOf(
            paso(ObjetivosRecorrido.TURNO_PESTANAS, "Pestañas", "Toca Resumen, Ventas, Inventario o Caja para ver cada parte del turno."),
            paso(ObjetivosRecorrido.TURNO_TOTAL, "Total", "Lee aquí lo vendido en el turno."),
            paso(ObjetivosRecorrido.TURNO_DETALLE, "Detalle", "Revisa horas, métodos de pago y el arqueo de caja."),
            paso(ObjetivosRecorrido.TURNO_COMPARTIR, "Exportar", "Toca para enviar el turno completo en PDF o Excel."),
        ),
    ),
    PRODUCTO_FORM(
        "producto_form",
        listOf(
            paso(ObjetivosRecorrido.FORM_NOMBRE, "Nombre", "Escribe el nombre y, si quieres, una descripción."),
            paso(ObjetivosRecorrido.FORM_PRECIOS, "Precios", "Escribe el costo y el precio de venta. La ganancia se calcula sola."),
            paso(ObjetivosRecorrido.FORM_EXISTENCIAS, "Existencias", "Escribe cuántas unidades tienes ahora."),
            paso(ObjetivosRecorrido.FORM_AVISOS, "Avisos", "Elige con cuántas unidades avisar de existencias bajas o críticas."),
            paso(ObjetivosRecorrido.FORM_CATEGORIA, "Categoría", "Elige una categoría o escribe una nueva para agrupar."),
            paso(ObjetivosRecorrido.FORM_FOTO, "Foto", "Toca Cámara o Galería para añadir una foto."),
            paso(ObjetivosRecorrido.FORM_INSUMOS, "Insumos", "Añade los insumos si lo elaboras tú. Se descuentan al vender."),
            paso(ObjetivosRecorrido.FORM_GUARDAR, "Guardar", "Toca Guardar cuando termines."),
        ),
    ),
    SERVICIO_FORM(
        "servicio_form",
        listOf(
            paso(ObjetivosRecorrido.FORM_NOMBRE, "Nombre", "Escribe el nombre del servicio."),
            paso(ObjetivosRecorrido.FORM_PRECIOS, "Importe", "Escribe lo que cobras por el servicio."),
            paso(ObjetivosRecorrido.FORM_CATEGORIA, "Tipo", "Elige un tipo o escribe uno nuevo para agrupar."),
            paso(ObjetivosRecorrido.FORM_FOTO, "Foto", "Toca Cámara o Galería para añadir una foto."),
            paso(ObjetivosRecorrido.FORM_INSUMOS, "Insumos", "Añade los insumos que gasta. Se descuentan al vender."),
            paso(ObjetivosRecorrido.FORM_GUARDAR, "Guardar", "Toca Guardar cuando termines."),
        ),
    ),
    INSUMO_FORM(
        "insumo_form",
        listOf(
            paso(ObjetivosRecorrido.FORM_NOMBRE, "Nombre", "Escribe el nombre del insumo."),
            paso(ObjetivosRecorrido.FORM_EXISTENCIAS, "Cantidad", "Escribe cuánto tienes y elige la unidad de medida."),
            paso(ObjetivosRecorrido.FORM_PRECIOS, "Precios", "Escribe lo que te cuesta y, si lo vendes suelto, su precio."),
            paso(ObjetivosRecorrido.FORM_AVISOS, "Avisos", "Elige con qué cantidad avisar de existencias bajas o críticas."),
            paso(ObjetivosRecorrido.FORM_GUARDAR, "Guardar", "Toca Guardar cuando termines."),
        ),
    ),
    PERFIL(
        "perfil",
        listOf(
            paso(ObjetivosRecorrido.PERFIL_DATOS, "Tus datos", "Escribe nombre, apellidos y carné. Solo se envían al pedir la licencia."),
            paso(ObjetivosRecorrido.PERFIL_GUARDAR, "Guardar", "Toca Guardar para conservar los cambios."),
            paso(ObjetivosRecorrido.PERFIL_ID_LICENCIA, "ID", "Toca Mostrar para ver y copiar el ID con que recuperas tu licencia."),
            paso(ObjetivosRecorrido.PERFIL_MODULOS, "Secciones", "Marca las secciones que usas. Las demás se ocultan."),
        ),
    ),
    LICENCIA(
        "licencia",
        listOf(
            paso(ObjetivosRecorrido.LICENCIA_ESTADO, "Estado", "Toca para ver hasta cuándo vale tu licencia."),
            paso(ObjetivosRecorrido.LICENCIA_PRECIOS, "Precios", "Toca para ver el precio de cada licencia y de cada app extra."),
            paso(ObjetivosRecorrido.LICENCIA_RENOVAR, "Renovar", "Toca Renovar para pedir la misma licencia otra vez."),
            paso(ObjetivosRecorrido.LICENCIA_PASOS, "Pasos", "Usa las flechas para ir de Datos a Solicitar y Activar."),
            paso(ObjetivosRecorrido.LICENCIA_DATOS, "Tus datos", "Revisa tus datos. Toca Editar para corregirlos."),
            paso(ObjetivosRecorrido.LICENCIA_ENVIAR, "Enviar", "Toca para enviar la solicitud o activar la licencia recibida."),
        ),
    ),
    VINCULACION(
        "vinculacion",
        listOf(
            paso(ObjetivosRecorrido.VINCULACION_RED, "Red", "Comprueba que los teléfonos comparten wifi, zona wifi o datos."),
            paso(ObjetivosRecorrido.VINCULACION_EMPLEADOS, "Apps vinculadas", "Toca una app para ver su turno, sus permisos y su código."),
            paso(ObjetivosRecorrido.VINCULACION_SECUNDARIA, "Usar como empleado", "Toca para vincular este teléfono a la app del dueño."),
            paso(ObjetivosRecorrido.VINCULACION_SINCRONIZAR, "Sincronizar", "Toca Sincronizar para enviar y recibir los cambios ahora."),
            paso(ObjetivosRecorrido.VINCULACION_CAMBIOS, "Pendientes", "Revisa cuántos cambios faltan por enviar."),
            paso(ObjetivosRecorrido.VINCULACION_DESVINCULAR, "Desvincular", "Toca para separar este teléfono de la app del dueño."),
            paso(ObjetivosRecorrido.VINCULACION_AGREGAR, "Agregar", "Toca Agregar para crear el código de un empleado nuevo."),
        ),
    ),
    QR(
        "qr",
        listOf(
            paso(ObjetivosRecorrido.QR_CAMARA, "Escanear", "Apunta la cámara al código QR de la app del dueño."),
        ),
    ),
    PAGO(
        "pago",
        listOf(
            paso(ObjetivosRecorrido.PAGO_LISTA, "Tus cuentas", "Marca la cuenta que usas al cobrar. Usa Editar o Eliminar en cada fila."),
            paso(ObjetivosRecorrido.PAGO_AGREGAR, "Agregar", "Toca Agregar y elige Teléfono o Tarjeta."),
        ),
    ),
    PRECIOS(
        "precios",
        listOf(
            paso(ObjetivosRecorrido.PRECIOS_EXPLICACION, "Ajustes de precio", "Sube o baja precios según el pago, sin tocar el inventario."),
            paso(ObjetivosRecorrido.PRECIOS_LISTA, "Tus ajustes", "Toca un ajuste para editarlo. Usa el interruptor para pausarlo."),
            paso(ObjetivosRecorrido.PRECIOS_NUEVO, "Nuevo", "Toca Nuevo para crear un ajuste de precio."),
        ),
    ),
    RESPALDO(
        "respaldo",
        listOf(
            paso(ObjetivosRecorrido.RESPALDO_EXPORTAR, "Exportar", "Sigue los pasos para guardar una copia de todo el negocio."),
            paso(ObjetivosRecorrido.RESPALDO_CONTRASENA, "Contraseña", "Protege la copia con una contraseña. Sin ella no se puede abrir."),
            paso(ObjetivosRecorrido.RESPALDO_IMPORTAR, "Importar", "Toca Elegir para restaurar una copia. Reemplaza los datos actuales."),
        ),
    ),
    MANUAL(
        "manual",
        listOf(
            paso(ObjetivosRecorrido.AYUDA_SECCION, "Secciones", "El manual sigue el orden de las pestañas de la app."),
            paso(ObjetivosRecorrido.AYUDA_TEMA, "Temas", "Toca un tema para ver sus pasos. Tócalo otra vez para cerrarlo."),
        ),
    ),
    SOPORTE(
        "soporte",
        listOf(
            paso(ObjetivosRecorrido.SOPORTE_FICHA, "Contacto", "Mantén pulsado un dato para copiarlo."),
            paso(ObjetivosRecorrido.SOPORTE_CONTACTO, "Escribir", "Toca SMS o WhatsApp para escribir al desarrollador."),
        ),
    ),
    CONFIGURACION(
        "configuracion",
        listOf(
            paso(ObjetivosRecorrido.ONBOARDING_PROGRESO, "Progreso", "Mira en qué paso estás y cuántos quedan."),
            paso(ObjetivosRecorrido.ONBOARDING_OMITIR, "Omitir", "Toca Omitir para dejar este paso para más tarde."),
            paso(ObjetivosRecorrido.ONBOARDING_SIGUIENTE, "Seguir", "Toca el botón principal para guardar y pasar al siguiente paso."),
        ),
    ),

    // ---------------- Ventanas emergentes ----------------
    VENTANA_NUEVA_VENTA(
        "ventana_nueva_venta",
        listOf(paso(ObjetivosRecorrido.VENTANA_OPCIONES, "Qué vendes", "Toca Venta para productos o Servicio para servicios.")),
    ),
    VENTANA_FONDO(
        "ventana_fondo",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_IMPORTE, "Fondo de caja", "Escribe el efectivo con el que empiezas el turno."),
            confirmar("Abrir", "Toca Abrir para empezar a vender."),
        ),
    ),
    VENTANA_MOVIMIENTO(
        "ventana_movimiento",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_TIPO, "Entrada o salida", "Toca Entrada si metes efectivo o Salida si lo sacas."),
            paso(ObjetivosRecorrido.VENTANA_IMPORTE, "Importe", "Escribe cuánto dinero es."),
            paso(ObjetivosRecorrido.VENTANA_MOTIVO, "Motivo", "Escribe por qué, por ejemplo «cambio» o «compra de bolsas»."),
            confirmar("Registrar", "Toca Registrar para guardarlo en el turno."),
        ),
    ),
    VENTANA_CONTEO(
        "ventana_conteo",
        listOf(
            paso(ObjetivosRecorrido.CONTEO_ESPERADO, "Esperado", "Lee cuánto efectivo debería haber en la caja."),
            paso(ObjetivosRecorrido.CONTEO_CONTADO, "Contar", "Usa −1 y +1 o escribe cuántos billetes hay de cada valor."),
            paso(ObjetivosRecorrido.CONTEO_CUADRA, "Cuadra", "Toca Cuadra si contaste justo lo esperado."),
            paso(ObjetivosRecorrido.CONTEO_MODO, "Total o billetes", "Toca Omitir para escribir solo el total, o Billetes para contarlos."),
            confirmar("Confirmar", "Toca para guardar el conteo y seguir."),
        ),
    ),
    VENTANA_CIERRE(
        "ventana_cierre",
        listOf(
            paso(ObjetivosRecorrido.CIERRE_CONTEO, "Contar artículos", "Escribe cuánto queda de cada artículo que cambió en el turno."),
            paso(ObjetivosRecorrido.CIERRE_OMITIR, "Omitir", "Toca Omitir para cerrar sin ajustar existencias."),
            paso(ObjetivosRecorrido.CIERRE_ATRAS, "Atrás", "Toca Atrás para volver al conteo anterior."),
            confirmar("Revisar y cerrar", "Toca para revisar las diferencias y cerrar el turno."),
        ),
    ),
    VENTANA_FICHA_INVENTARIO(
        "ventana_ficha_inventario",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_DATOS, "Ficha", "Revisa precio, existencias, receta y ganancia."),
            paso(ObjetivosRecorrido.VENTANA_EDITAR, "Editar", "Toca Editar para cambiar sus datos o existencias."),
            paso(ObjetivosRecorrido.VENTANA_COMPARTIR, "Compartir", "Toca Compartir para enviar la ficha."),
            paso(ObjetivosRecorrido.VENTANA_ELIMINAR, "Eliminar", "Toca Eliminar para borrarlo. Pide confirmación."),
        ),
    ),
    VENTANA_FILTRO_INVENTARIO(
        "ventana_filtro_inventario",
        listOf(
            paso(ObjetivosRecorrido.FILTRO_ESTADO, "Estado", "Elige ver solo agotados, escasos o por caducar."),
            paso(ObjetivosRecorrido.FILTRO_TIPO, "Tipo", "Elige productos, elaborados o insumos."),
            paso(ObjetivosRecorrido.FILTRO_CATEGORIA, "Categoría", "Elige una categoría."),
        ) + FILTRO_PIE,
    ),
    VENTANA_EXPORTAR_INVENTARIO(
        "ventana_exportar_inventario",
        exportar("Lee si se exporta lo que ves o solo lo marcado."),
    ),
    VENTANA_COMPARTIR_FICHA(
        "ventana_compartir_ficha",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_ENCABEZADO, "Encabezado", "Escribe un título para la promoción. Es opcional."),
            paso(ObjetivosRecorrido.VENTANA_FORMATOS, "Elige el formato", "Toca un formato para enviar la ficha."),
        ),
    ),
    VENTANA_AGREGAR_INSUMO(
        "ventana_agregar_insumo",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_BUSCAR, "Buscar", "Escribe parte del nombre del insumo."),
            paso(ObjetivosRecorrido.VENTANA_LISTA, "Elegir", "Toca un insumo para añadirlo."),
        ),
    ),
    VENTANA_FICHA_SERVICIO(
        "ventana_ficha_servicio",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_DATOS, "Ficha", "Revisa importe, insumos y cuántas veces puedes venderlo."),
            paso(ObjetivosRecorrido.VENTANA_EDITAR, "Editar", "Toca Editar para cambiar sus datos."),
            paso(ObjetivosRecorrido.VENTANA_ELIMINAR, "Eliminar", "Toca Eliminar para borrarlo. Pide confirmación."),
        ),
    ),
    VENTANA_FILTRO_SERVICIOS(
        "ventana_filtro_servicios",
        listOf(paso(ObjetivosRecorrido.FILTRO_TIPO, "Tipo", "Elige un tipo de servicio.")) + FILTRO_PIE,
    ),
    VENTANA_EXPORTAR_SERVICIOS(
        "ventana_exportar_servicios",
        exportar("Lee si se exporta lo que ves o solo lo marcado."),
    ),
    VENTANA_FICHA_REGISTRO(
        "ventana_ficha_registro",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_DATOS, "Detalle", "Revisa importe, artículos, cliente y hora."),
            paso(ObjetivosRecorrido.VENTANA_ACCIONES, "Acciones", "Toca Modificar, Anular o Editar para corregir el registro."),
        ),
    ),
    VENTANA_FILTRO_REGISTROS(
        "ventana_filtro_registros",
        listOf(
            paso(ObjetivosRecorrido.FILTRO_PERIODO, "Período", "Elige hoy, esta semana, este mes o unas fechas."),
            paso(ObjetivosRecorrido.FILTRO_IMPORTE, "Importe", "Escribe un mínimo o un máximo. Puedes dejarlos vacíos."),
            paso(ObjetivosRecorrido.FILTRO_VENDEDOR, "Vendedor", "Elige quién vendió."),
        ) + FILTRO_PIE,
    ),
    VENTANA_EXPORTAR_REGISTROS(
        "ventana_exportar_registros",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_ALCANCE, "Qué se exporta", "Lee si se exporta lo filtrado o solo lo marcado."),
            paso(ObjetivosRecorrido.VENTANA_FORMATOS, "Elige el formato", "Toca PDF o Excel para enviarlo o guardarlo."),
        ),
    ),
    VENTANA_EXPORTAR_TURNO(
        "ventana_exportar_turno",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_ALCANCE, "Qué se exporta", "Lee qué incluye el documento del turno."),
            paso(ObjetivosRecorrido.VENTANA_FORMATOS, "Elige el formato", "Toca PDF o Excel para enviarlo o guardarlo."),
        ),
    ),
    VENTANA_MODIFICAR(
        "ventana_modificar",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_LISTA, "Artículos", "Cambia cantidades con −1 y +1, o toca Quitar."),
            paso(ObjetivosRecorrido.VENTANA_AGREGAR, "Añadir", "Toca Añadir para sumar artículos a la venta."),
            paso(ObjetivosRecorrido.VENTANA_TOTAL, "Total", "Revisa el nuevo total."),
            paso(ObjetivosRecorrido.VENTANA_METODO, "Forma de pago", "Toca Efectivo o Transferencia si cambió."),
            paso(ObjetivosRecorrido.VENTANA_MOTIVO, "Motivo", "Escribe por qué corriges la venta."),
            paso(ObjetivosRecorrido.VENTANA_GUARDAR, "Guardar", "Toca Guardar para aplicar la corrección."),
        ),
    ),
    VENTANA_ANULAR(
        "ventana_anular",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_MOTIVO, "Motivo", "Escribe por qué anulas la venta."),
            confirmar("Anular", "Toca Anular. Las existencias vuelven al inventario."),
        ),
    ),
    VENTANA_CORREGIR(
        "ventana_corregir",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_CANTIDAD, "Cantidad", "Escribe la cantidad correcta del movimiento."),
            paso(ObjetivosRecorrido.VENTANA_MOTIVO, "Motivo", "Escribe por qué lo corriges."),
            confirmar("Guardar", "Toca Guardar para corregir las existencias."),
        ),
    ),
    VENTANA_FICHA_CLIENTE(
        "ventana_ficha_cliente",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_DATOS, "Cliente", "Revisa sus datos y su historial de compras."),
            paso(ObjetivosRecorrido.VENTANA_EDITAR, "Editar", "Toca Editar para corregir nombre, carné o teléfono."),
            paso(ObjetivosRecorrido.VENTANA_ELIMINAR, "Quitar", "Toca Quitar para sacarlo de tus clientes fijos."),
        ),
    ),
    VENTANA_EDITAR_CLIENTE(
        "ventana_editar_cliente",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_DATOS, "Datos", "Corrige nombre, carné y teléfono. Se actualizan sus compras."),
            confirmar("Guardar", "Toca Guardar para aplicar los cambios."),
        ),
    ),
    VENTANA_CUENTA(
        "ventana_cuenta",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_NUMERO, "Número", "Escribe el teléfono o la tarjeta. El banco se reconoce solo."),
            paso(ObjetivosRecorrido.VENTANA_NOMBRE, "Nombre", "Ponle un nombre para reconocerla. Es opcional."),
            paso(ObjetivosRecorrido.VENTANA_GUARDAR, "Guardar", "Toca Guardar para añadirla a la lista."),
        ),
    ),
    VENTANA_PRECIO(
        "ventana_precio",
        listOf(
            paso(ObjetivosRecorrido.PRECIO_CAMBIO, "Cuánto", "Elige subir o bajar y escribe el porcentaje."),
            paso(ObjetivosRecorrido.PRECIO_CUANDO, "Cuándo", "Elige con qué forma de pago se aplica."),
            paso(ObjetivosRecorrido.PRECIO_ARTICULOS, "Artículos", "Toca Todos o busca y marca los artículos uno a uno."),
            paso(ObjetivosRecorrido.VENTANA_NOMBRE, "Nombre", "Ponle un nombre. Si lo dejas vacío se usa el sugerido."),
            paso(ObjetivosRecorrido.PRECIO_VISTA, "Vista previa", "Mira cómo quedaría un precio al cobrar."),
            paso(ObjetivosRecorrido.VENTANA_ELIMINAR, "Eliminar", "Toca Eliminar para borrar este ajuste."),
            paso(ObjetivosRecorrido.VENTANA_GUARDAR, "Guardar", "Toca Guardar para activarlo."),
        ),
    ),
    VENTANA_IMPORTAR(
        "ventana_importar",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_DATOS, "Archivo", "Comprueba la fecha y el nombre de la copia."),
            paso(ObjetivosRecorrido.VENTANA_CONTRASENA, "Contraseña", "Escribe la contraseña con la que se guardó."),
            confirmar("Importar", "Toca Importar. Reemplaza todos los datos actuales."),
        ),
    ),
    VENTANA_LOCALES(
        "ventana_locales",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_LISTA, "Tus locales", "Toca un local para cambiarle el nombre."),
            paso(ObjetivosRecorrido.VENTANA_AGREGAR, "Añadir", "Toca Añadir para crear otro local."),
        ),
    ),
    VENTANA_LOCAL(
        "ventana_local",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_NOMBRE, "Nombre", "Escribe el nombre del local."),
            confirmar("Guardar", "Toca Guardar para conservarlo."),
        ),
    ),
    VENTANA_EMPLEADO(
        "ventana_empleado",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_NOMBRE, "Nombre", "Escribe el nombre del empleado."),
            paso(ObjetivosRecorrido.EMPLEADO_CIERRE, "Turno", "Aprueba, rechaza o pide el cierre de su turno."),
            paso(ObjetivosRecorrido.EMPLEADO_PERMISOS, "Permisos", "Marca lo que puede hacer en su app."),
            paso(ObjetivosRecorrido.EMPLEADO_COBRO, "Cobro", "Elige con qué tarjeta cobra este empleado."),
            paso(ObjetivosRecorrido.EMPLEADO_CODIGO, "Código y quitar", "Toca Código para un QR nuevo o Quitar para desvincular su app."),
            confirmar("Guardar", "Toca para crear el código o guardar los cambios."),
        ),
    ),
    VENTANA_QR(
        "ventana_qr",
        listOf(paso(ObjetivosRecorrido.VENTANA_QR, "Código QR", "Pide al empleado que lo escanee. Sirve una sola vez.")),
    ),
    VENTANA_SECUNDARIA(
        "ventana_secundaria",
        listOf(
            paso(ObjetivosRecorrido.VENTANA_NUMERO, "Tu teléfono", "Escribe tu número. Va en el QR con el que cobras."),
            confirmar("Escanear", "Toca Escanear y apunta al código del dueño."),
        ),
    ),
}
