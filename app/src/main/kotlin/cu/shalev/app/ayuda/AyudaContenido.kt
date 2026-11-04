package cu.shalev.app.ayuda

import cu.shalev.domain.model.Modulo

/** Secciones del manual resumido, una por ventana principal de la app. */
enum class CategoriaAyuda(val titulo: String, val detalle: String) {
    INICIO("Inicio", "Barra inferior, selector de turno, período y licencias"),
    INVENTARIO("Inventario", "Productos, insumos y elaborados con receta"),
    SERVICIOS("Servicios", "Catálogo de servicios por especialidad"),
    VENTA("Venta", "Carrito, cobro en efectivo o por transferencia"),
    REGISTROS("Registros", "Historial, correcciones y exportación de informes"),
    HERRAMIENTAS("Herramientas", "Cálculo de costos y cambio"),
    AJUSTES("Ajustes", "Configuración, pago electrónico y precios"),
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

/** Contenuto de ayuda resumido organizado por ventana. */
object AyudaContenido {
    const val INTRO = "Elige la pantalla y abre un tema para ver los pasos."

    /** Texto de introducción mostrado al inicio del manual. */
    val textoIntro = "Manual de usuario resumido: cada ventana tiene su propia ayuda contextual."

    /** Ayuda para la pantalla de Inicio. */
    val inicio = AyudaVentana(
        titulo = "Manual de usuario: Inicio",
        pasos = listOf(
            "Usa la barra inferior para cambiar de sección o desliza entre pantallas principales",
            "Activa el interruptor del turno cuando empieces a trabajar",
            "Consulta el período de gráficos tocando el selector",
            "Accede a Ajustes con el engranaje para configurar tu negocio"
        ),
        categoria = CategoriaAyuda.INICIO
    )

    /** Ayuda para la pantalla de Inventario. */
    val inventario = AyudaVentana(
        titulo = "Manual de usuario: Inventario",
        pasos = listOf(
            "Toca + para agregar un nuevo producto, insumo o elaborado",
            "Sube una foto o elige una de la galería para ilustrarlo",
            "Completa nombre, categoría, precio de costo y precio de venta",
            "Marca la caducidad opcional si el producto vence"
        ),
        categoria = CategoriaAyuda.INVENTARIO
    )

    /** Ayuda para la pantalla de Servicios. */
    val servicios = AyudaVentana(
        titulo = "Manual de usuario: Servicios",
        pasos = listOf(
            "Toca + para agregar un nuevo servicio con nombre y tipo",
            "Escribe el importe en CUP que cobras por cada servicio",
            "Añade insumos que consume el servicio si corresponde",
            "Guarda el servicio para disponerlo en el catálogo"
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
            "Revisa el comprobante y confirma la venta"
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

    /** Ayuda de la ficha de costo con exportación a Inventario/Servicios. */
    val herramientas = AyudaVentana(
        titulo = "Manual de usuario: Herramientas",
        pasos = listOf(
            "Abre Ficha de costo y agrega partidas con categoría, cantidad, unidad, costo y moneda",
            "Guarda y exporta a Inventario (Elaborado) o Servicios con tus partidas como insumos",
            "Si alguna partida está en USD, escribe la tasa CUP por 1 USD antes de exportar",
            "En Calculadora de cambio, introduce la tasa o calcula una a partir de dos importes"
        ),
        categoria = CategoriaAyuda.HERRAMIENTAS
    )

    /** Ayuda para la pantalla de Ajustes. */
    val ajustes = AyudaVentana(
        titulo = "Manual de usuario: Ajustes",
        pasos = listOf(
            "Abre Ajustes con el engranaje de la pantalla Inicio",
            "Completa la configuración: permisos, avisos y perfil",
            "Gestiona pago electrónico y tarifas de precios",
            "Exporta respaldos y administra licencias"
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
            "Para restaurar, importa el archivo .shalev y escribe la contraseña"
        ),
        categoria = CategoriaAyuda.RESPALDO
    )

    /** Ayuda para la pantalla de Licencia. */
    val licencia = AyudaVentana(
        titulo = "Manual de usuario: Licencia",
        pasos = listOf(
            "En Ajustes → Licencia revisa tus datos personales",
            "Solicita la licencia por WhatsApp o SMS desde la pantalla Licencia",
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
            "El dueño revisa permisos y solicitudes de cierre"
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
            "El desarrollador responderá para ayudarte a continuar"
        ),
        categoria = CategoriaAyuda.SOPORTE
    )

    /** Todas las ventanas de ayuda en orden de la barra inferior. */
    val todas = listOf(
        inicio, inventario, servicios, venta, registros, herramientas, ajustes, respaldo, licencia, empleados, soporte
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
        "Herramientas" -> ayudaPara(CategoriaAyuda.HERRAMIENTAS)
        "Ajustes" -> ayudaPara(CategoriaAyuda.AJUSTES)
        "Respaldo" -> ayudaPara(CategoriaAyuda.RESPALDO)
        "Licencia" -> ayudaPara(CategoriaAyuda.LICENCIA)
        "Apps vinculadas" -> ayudaPara(CategoriaAyuda.EMPLEADOS)
        "Soporte" -> ayudaPara(CategoriaAyuda.SOPORTE)
        else -> ayudaPara(CategoriaAyuda.SOPORTE)
    }
}