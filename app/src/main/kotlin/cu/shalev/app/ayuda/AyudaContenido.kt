package cu.shalev.app.ayuda

import cu.shalev.domain.model.Modulo

/** Secciones del manual resumido, organizadas por la ventana donde se realiza cada tarea. */
enum class CategoriaAyuda(val titulo: String, val detalle: String) {
    INICIO("Inicio y turno", "Jornada, caja y resumen del negocio"),
    INVENTARIO("Inventario", "Productos, insumos y recetas"),
    SERVICIOS("Servicios", "Catálogo de servicios"),
    VENTA("Nueva venta", "Carrito y cobro"),
    REGISTROS("Registros", "Historial, correcciones y exportación"),
    AJUSTES("Ajustes", "Configuración, pago electrónico y precios"),
    RESPALDO("Respaldo", "Copias y restauración"),
    LICENCIA("Licencia", "Solicitud y activación"),
    EMPLEADOS("Apps vinculadas", "Teléfonos de empleados"),
    SOPORTE("Soporte", "Contacto con el desarrollador"),
}

enum class EsquemaAyuda { TURNO, VENTA, RESPALDO }

data class TemaAyuda(
    val titulo: String,
    val pasos: List<String>,
    val categoria: CategoriaAyuda = CategoriaAyuda.INICIO,
    val esquema: EsquemaAyuda? = null,
)

/** Resumen del manual de usuario incluido en el repositorio (MANUAL_USUARIO.pdf). */
object AyudaContenido {
    fun temasPara(modulos: Set<Modulo>): List<TemaAyuda> {
        val inv = Modulo.VENTAS in modulos || Modulo.INVENTARIO in modulos
        val ventas = Modulo.VENTAS in modulos
        val serv = Modulo.SERVICIOS in modulos
        return temas.filter { t ->
            when (t.titulo) {
                "Agregar productos", "Buscar y filtrar el inventario", "Insumos y elaborados" -> inv
                "Vender", "Cobrar por transferencia" -> ventas || serv
                "Servicios" -> serv
                else -> true
            }
        }
    }

    fun seccionesPara(modulos: Set<Modulo>): List<Pair<CategoriaAyuda, List<TemaAyuda>>> =
        CategoriaAyuda.entries.mapNotNull { c ->
            temasPara(modulos).filter { it.categoria == c }.takeIf { it.isNotEmpty() }?.let { c to it }
        }

    const val INTRO = "Elige la pantalla y abre un tema para ver los pasos."

    val temas: List<TemaAyuda> = listOf(
        TemaAyuda("Primeros pasos", listOf(
            "Usa la barra inferior para cambiar de sección o desliza entre las pantallas principales.",
            "Abre Ajustes con el engranaje de Inicio. Las secciones dependen de los módulos elegidos.",
            "En Ajustes → Completar configuración puedes terminar los pasos pendientes.",
        ), CategoriaAyuda.INICIO),
        TemaAyuda("Abrir y cerrar el turno", listOf(
            "En Inicio activa el interruptor del turno, escribe el fondo de caja y confirma.",
            "Sin turno abierto no se puede vender. Usa Caja para registrar entradas y salidas de efectivo.",
            "Al terminar, desactiva el interruptor y revisa el efectivo y el inventario antes de confirmar.",
            "Puedes omitir el conteo por billetes o el de inventario. La secundaria solicita el cierre al dueño.",
        ), CategoriaAyuda.INICIO, EsquemaAyuda.TURNO),
        TemaAyuda("Consultar el negocio", listOf(
            "En Inicio elige un período para consultar los gráficos y rankings.",
            "Toca una alerta de inventario para ver los artículos correspondientes.",
            "Desde Inicio también puedes abrir Pago electrónico y Precios.",
        ), CategoriaAyuda.INICIO),
        TemaAyuda("Agregar productos", listOf(
            "En Inventario toca + y completa nombre, precios y cantidad.",
            "Si hay nombres repetidos, usa la descripción para distinguir los artículos.",
            "Toca ✓ para guardar.",
        ), CategoriaAyuda.INVENTARIO),
        TemaAyuda("Buscar y filtrar el inventario", listOf(
            "Busca por nombre, descripción o categoría; usa Filtrar para limitar la lista.",
            "Abre un artículo para consultar sus datos y avisos. Quita los filtros para volver a la lista completa.",
            "Selecciona filas para exportarlas; sin selección se exporta lo visible.",
        ), CategoriaAyuda.INVENTARIO),
        TemaAyuda("Insumos y elaborados", listOf(
            "En Inventario, usa + para crear un insumo o un producto elaborado con receta.",
            "Al vender un elaborado se descuentan sus insumos; si no alcanzan, no se puede vender.",
            "Un insumo con precio de venta también se puede vender.",
        ), CategoriaAyuda.INVENTARIO),
        TemaAyuda("Servicios", listOf(
            "En Servicios toca + y escribe el nombre, el tipo y el importe.",
            "Agrega los insumos que consume: se descuentan al vender el servicio.",
            "Busca, filtra o selecciona servicios para consultar y exportar el catálogo.",
        ), CategoriaAyuda.SERVICIOS),
        TemaAyuda("Vender", listOf(
            "Con el turno abierto, toca Vender en Inicio y elige productos o servicios.",
            "Selecciona artículos, ajusta cantidades y elige efectivo o transferencia.",
            "Revisa el comprobante y confirma. La venta queda en Registros.",
        ), CategoriaAyuda.VENTA, EsquemaAyuda.VENTA),
        TemaAyuda("Cobrar por transferencia", listOf(
            "En Pago electrónico selecciona un teléfono y una tarjeta o cuenta para cobrar.",
            "Muestra el QR y el importe al cliente: el QR no incluye el importe.",
            "Completa los datos del cliente y registra el número de transacción del SMS.",
        ), CategoriaAyuda.VENTA),
        TemaAyuda("Ver lo vendido", listOf(
            "En Registros consulta ventas, servicios, transferencias, movimientos, clientes y turnos.",
            "Abre una fila para ver sus detalles. Usa Filtrar para acotar los resultados disponibles.",
            "Selecciona filas para exportarlas; sin selección se exporta lo visible.",
        ), CategoriaAyuda.REGISTROS),
        TemaAyuda("Corregir operaciones", listOf(
            "En la app principal, abre una venta del turno abierto para modificarla o anularla con un motivo.",
            "Los turnos cerrados no se modifican. Anular una venta no devuelve dinero por Transfermóvil.",
            "En Movimientos puedes editar un ajuste del turno abierto desde su ficha.",
        ), CategoriaAyuda.REGISTROS),
        TemaAyuda("Configurar el negocio", listOf(
            "Abre Ajustes desde el engranaje de Inicio para completar la configuración y consultar tu perfil.",
            "Desde allí también puedes administrar avisos, permisos y el acceso con clave del teléfono.",
        ), CategoriaAyuda.AJUSTES),
        TemaAyuda("Pago electrónico", listOf(
            "Agrega un teléfono y una tarjeta o cuenta; marca uno de cada tipo para cobrar.",
            "El teléfono elegido recibe la confirmación; verifica la tarjeta antes de guardarla.",
        ), CategoriaAyuda.AJUSTES),
        TemaAyuda("Precios", listOf(
            "Crea ajustes para subir o bajar precios según el pago, el importe o los productos elegidos.",
            "Revisa la vista previa, guarda y usa el interruptor para activar o desactivar cada regla.",
        ), CategoriaAyuda.AJUSTES),
        TemaAyuda("Guardar una copia", listOf(
            "En Ajustes → Respaldo prepara la exportación y protege la copia con contraseña si lo deseas.",
            "Anota la contraseña: sin ella no podrás recuperar una copia protegida.",
            "Toca Siguiente y guarda el archivo o envíalo a otra aplicación segura.",
        ), CategoriaAyuda.RESPALDO, EsquemaAyuda.RESPALDO),
        TemaAyuda("Restaurar una copia", listOf(
            "En Ajustes → Respaldo elige el archivo .shalev y escribe su contraseña si tiene una.",
            "Importar reemplaza los datos actuales. Revisa la copia antes de confirmar.",
            "Las fotos y la licencia activa no se trasladan en el archivo.",
        ), CategoriaAyuda.RESPALDO),
        TemaAyuda("Licencia", listOf(
            "En Ajustes → Licencia revisa el estado y tus datos, y solicita la licencia por WhatsApp o SMS.",
            "Copia el código recibido, toca Pegar y luego Activar. Si llegó en varios SMS, copia todos.",
            "Para renovar con las mismas condiciones, toca Renovar igual.",
        ), CategoriaAyuda.LICENCIA),
        TemaAyuda("Apps vinculadas", listOf(
            "En la app principal abre Ajustes → Apps vinculadas y toca + para generar el código.",
            "En la secundaria elige Usar esta app como secundaria, indica su teléfono y escanea el código.",
            "Usa wifi, zona wifi o datos móviles; si la operadora impide la conexión, prueba una red wifi.",
            "Desde la principal revisa permisos y solicitudes de cierre de los empleados.",
        ), CategoriaAyuda.EMPLEADOS),
        TemaAyuda("¿Necesitas ayuda?", listOf(
            "En Ajustes → Soporte puedes escribir al desarrollador por WhatsApp o SMS.",
        ), CategoriaAyuda.SOPORTE),
    )
}
