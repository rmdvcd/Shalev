package cu.shalev.app.ayuda

import cu.shalev.domain.model.Modulo

/** Secciones del manual resumido, una por ventana principal de la app. */
enum class CategoriaAyuda(val titulo: String, val detalle: String) {
    INICIO("Inicio", "Barra inferior, turno, período y gráficos"),
    INVENTARIO("Inventario", "Productos, insumos, elaborados con receta y existencias"),
    SERVICIOS("Servicios", "Catálogo, importe e insumos consumidos"),
    VENTA("Venta", "Carrito, cobro en efectivo o por transferencia"),
    REGISTROS("Registros", "Historial, correcciones y exportación de informes"),
    AJUSTES("Ajustes", "Configuración, locales, actualizaciones y precios"),
    RESPALDO("Respaldo", "Copias de seguridad y restauración"),
    LICENCIA("Licencia", "Solicitud, activación y renovación"),
    EMPLEADOS("Apps vinculadas", "Trabajo en equipo con empleados en red local"),
    SOPORTE("Soporte", "Contacto con el desarrollador")
}

/** Ayuda resumido por ventana: título y pasos breves. */
data class AyudaVentana(
    val titulo: String,
    val pasos: List<String>,
    val categoria: CategoriaAyuda = CategoriaAyuda.INICIO
)

/** Contenido de ayuda resumido organizado por ventana. */
object AyudaContenido {
    const val INTRO = "Elige la pantalla y abre un tema para ver los pasos."

    /** Ayuda para la pantalla de Inicio. */
    val inicio = AyudaVentana(
        titulo = "Manual de usuario: Inicio",
        pasos = listOf(
            "Usa la barra inferior para cambiar de sección o desliza entre pantallas principales",
            "Activa el interruptor del turno cuando empieces a trabajar",
            "Elige el período para actualizar las alertas, los gráficos y el Top 3",
            "Toca una barra de Ventas para ver su importe; vuelve a tocarla para ocultarlo",
            "Ganancia bruta no descuenta otros gastos; el inventario se distribuye por valor al costo en CUP"
        ),
        categoria = CategoriaAyuda.INICIO
    )

    /** Ayuda para la pantalla de Inventario. */
    val inventario = AyudaVentana(
        titulo = "Manual de usuario: Inventario",
        pasos = listOf(
            "Toca + y elige la categoría; Insumos abre su propio formulario",
            "Completa nombre, costos, precio de venta, cantidad y caducidad opcional en un solo formulario",
            "En un Elaborado agrega insumos a la receta: el costo se calcula solo y verás «Alcanza para N»",
            "Los niveles bajo y crítico son opcionales; si quedan vacíos, se usan los de Ajustes",
            "Usa el buscador, Filtrar y la selección múltiple para exportar o borrar artículos"
        ),
        categoria = CategoriaAyuda.INVENTARIO
    )

    /** Ayuda para la pantalla de Servicios. */
    val servicios = AyudaVentana(
        titulo = "Manual de usuario: Servicios",
        pasos = listOf(
            "Toca + y completa nombre, tipo, descripción e importe en un solo formulario",
            "Agrega los insumos que consume cada prestación y su cantidad",
            "Suma mano de obra y otros costos para calcular la ganancia bruta",
            "Pulsa Guardar servicio para que aparezca en el catálogo",
            "Al venderlo, Shalev descuenta sus insumos automáticamente"
        ),
        categoria = CategoriaAyuda.SERVICIOS
    )

    /** Ayuda para la pantalla de Venta. */
    val venta = AyudaVentana(
        titulo = "Manual de usuario: Venta",
        pasos = listOf(
            "Con el turno abierto, toca Vender en Inicio",
            "Selecciona productos o servicios para el carrito",
            "Ajusta cantidades y elige efectivo o transferencia",
            "Si cambió la cotización, revísala antes de confirmar. Un SMS no prueba el ingreso bancario"
        ),
        categoria = CategoriaAyuda.VENTA
    )

    /** Ayuda para la pantalla de Registros. */
    val registros = AyudaVentana(
        titulo = "Manual de usuario: Registros",
        pasos = listOf(
            "Consulta ventas, servicios, transferencias y movimientos",
            "Usa Filtrar para acotar los resultados por período o importe",
            "Abre una fila para ver detalles y aplicar correcciones",
            "Exporta informes a PDF o Excel cuando los necesites"
        ),
        categoria = CategoriaAyuda.REGISTROS
    )

    /** Ayuda para la pantalla de Ajustes. */
    val ajustes = AyudaVentana(
        titulo = "Manual de usuario: Ajustes",
        pasos = listOf(
            "Abre Ajustes desde la última posición de la barra inferior",
            "Completa la configuración: permisos, avisos y perfil",
            "En Local, el local seleccionado aparece en verde; toca uno para editar su nombre",
            "Actualizar muestra un indicador circular; luego cierra el resultado o confirma la instalación",
            "Gestiona pago electrónico, precios, respaldos y licencia"
        ),
        categoria = CategoriaAyuda.AJUSTES
    )

    /** Ayuda para la pantalla de Respaldo. */
    val respaldo = AyudaVentana(
        titulo = "Manual de usuario: Respaldo",
        pasos = listOf(
            "Ve a Ajustes → Respaldo para exportar tus datos",
            "Protege la copia con contraseña y anótala en un lugar seguro",
            "Elige Guardar en el teléfono o Enviar a otra aplicación",
            "La copia incluye fichas, lotes y fotos privadas; importarla reemplaza datos y preferencias",
            "La copia previa a actualizar no se restaura sola. Usa Guardar copia previa para recuperarla explícitamente"
        ),
        categoria = CategoriaAyuda.RESPALDO
    )

    /** Ayuda para la pantalla de Licencia. */
    val licencia = AyudaVentana(
        titulo = "Manual de usuario: Licencia",
        pasos = listOf(
            "En Ajustes → Licencia revisa tus datos personales",
            "Elige el tipo de licencia; cada fila muestra su precio alineado a la derecha",
            "En «Enviar por», toca WhatsApp o SMS y solicita la licencia",
            "Copia el código recibido (SPVI2:) y tócalo en Pegar",
            "Confirma la activación para desbloquear todas las funciones"
        ),
        categoria = CategoriaAyuda.LICENCIA
    )

    /** Ayuda para la pantalla de Apps vinculadas. */
    val empleados = AyudaVentana(
        titulo = "Manual de usuario: Apps vinculadas",
        pasos = listOf(
            "En Ajustes → Apps vinculadas toca + para generar un código QR",
            "En la secundaria indica tu teléfono y escanea el código",
            "Usa Wi-Fi o datos móviles para mantener la conexión",
            "El dueño revisa permisos y cierres. El empleado conserva su local asignado",
            "Si hay una operación sin confirmar, sincroniza y revisa su resultado antes de hacer otro cambio"
        ),
        categoria = CategoriaAyuda.EMPLEADOS
    )

    /** Ayuda para la pantalla de Soporte. */
    val soporte = AyudaVentana(
        titulo = "Manual de usuario: Soporte",
        pasos = listOf(
            "En Ajustes → Soporte puedes escribir al desarrollador",
            "Usa WhatsApp o SMS para enviar tu consulta",
            "Incluye el ID de la licencia y el teléfono del negocio",
            "El desarrollador responderá por el mismo canal"
        ),
        categoria = CategoriaAyuda.SOPORTE
    )

    /** Temas del manual, agrupados por las pantallas actuales de la app. */
    val todas = listOf(
        inicio, inventario, servicios, venta, registros, ajustes, respaldo, licencia, empleados, soporte
    )

    /** Obtiene la ayuda de la ventana que corresponde a la CategoríaAyuda. */
    fun ayudaPara(categoria: CategoriaAyuda): AyudaVentana = todas
        .filter { it.categoria == categoria }
        .takeIf { it.isNotEmpty() }
        ?.first()!!

    /** Lista de todas las AyudaVentana (para compatibilidad con tests). */
    val temas: List<AyudaVentana> = todas

    /** Filtra las ventanas de módulos desactivados; las generales están siempre disponibles. */
    fun temasPara(modulos: Set<Modulo>): List<AyudaVentana> = todas.filter { ayuda ->
        when (ayuda.categoria) {
            CategoriaAyuda.INVENTARIO -> Modulo.VENTAS in modulos || Modulo.INVENTARIO in modulos
            CategoriaAyuda.SERVICIOS -> Modulo.SERVICIOS in modulos
            CategoriaAyuda.VENTA -> Modulo.VENTAS in modulos || Modulo.SERVICIOS in modulos
            else -> true
        }
    }

    fun seccionesPara(modulos: Set<Modulo>): List<Pair<CategoriaAyuda, List<AyudaVentana>>> =
        CategoriaAyuda.entries.mapNotNull { categoria ->
            temasPara(modulos).filter { it.categoria == categoria }.takeIf { it.isNotEmpty() }?.let { categoria to it }
        }

    /** Obtiene la ayuda de la ventana por nombre de categoría. */
    fun ayudaPorTitulo(titulo: String): AyudaVentana = when (titulo) {
        "Inicio" -> ayudaPara(CategoriaAyuda.INICIO)
        "Inventario" -> ayudaPara(CategoriaAyuda.INVENTARIO)
        "Servicios" -> ayudaPara(CategoriaAyuda.SERVICIOS)
        "Venta" -> ayudaPara(CategoriaAyuda.VENTA)
        "Registros" -> ayudaPara(CategoriaAyuda.REGISTROS)
        "Ajustes" -> ayudaPara(CategoriaAyuda.AJUSTES)
        "Respaldo" -> ayudaPara(CategoriaAyuda.RESPALDO)
        "Licencia" -> ayudaPara(CategoriaAyuda.LICENCIA)
        "Apps vinculadas" -> ayudaPara(CategoriaAyuda.EMPLEADOS)
        "Soporte" -> ayudaPara(CategoriaAyuda.SOPORTE)
        else -> ayudaPara(CategoriaAyuda.SOPORTE)
    }
}