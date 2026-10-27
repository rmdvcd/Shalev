package cu.shalev.app.ayuda

/**
 * 0.31.0 (P7): una sección del manual. Agrupa temas que se usan juntos, cada una con su icono, para que el manual
 * se lea como un libro y no como una lista sin orden.
 */
enum class CategoriaAyuda(val titulo: String, val detalle: String) {
    EMPEZAR("Empezar", "Qué es cada botón y cómo dejar Shalev listo"),
    VENDER("Vender y cobrar", "El turno, la venta y el cobro por transferencia"),
    INVENTARIO("Inventario", "Productos, insumos y elaborados"),
    DATOS("Tus datos y tu licencia", "Respaldo y licencia"),
    EMPLEADOS("Empleados", "Las apps de tus empleados"),
    AYUDA("Ayuda", "A quién escribir cuando algo no sale"),
}

/** 0.31.0 (P7): esquema que acompaña a un tema (se dibuja con formas: nunca queda desactualizado). */
enum class EsquemaAyuda { BARRA, TURNO, VENTA, RESPALDO }

/** Un tema del manual: título corto y pasos de una línea, en lenguaje llano (usuario sin conocimientos previos). */
data class TemaAyuda(
    val titulo: String,
    val pasos: List<String>,
    val categoria: CategoriaAyuda = CategoriaAyuda.EMPEZAR,
    val esquema: EsquemaAyuda? = null,
)

/** Manual breve de Shalev. Texto puro (testeado): sin jerga, frases cortas, un paso por línea. */
object AyudaContenido {
    /**
     * 0.21.0 (C12): temas visibles según los módulos del negocio (lo desactivado tampoco aparece en la Ayuda).
     * Los temas generales (primeros pasos, copia, licencia…) se muestran siempre.
     */
    fun temasPara(modulos: Set<cu.shalev.domain.model.Modulo>): List<TemaAyuda> {
        val inv = cu.shalev.domain.model.Modulo.VENTAS in modulos || cu.shalev.domain.model.Modulo.INVENTARIO in modulos
        val ventas = cu.shalev.domain.model.Modulo.VENTAS in modulos
        val serv = cu.shalev.domain.model.Modulo.SERVICIOS in modulos
        return temas.filter { t ->
            when (t.titulo) {
                "Agregar productos", "Buscar y filtrar el inventario", "Insumos y elaborados" -> inv
                "Vender", "Cobrar por transferencia", "Ver lo vendido" -> ventas || serv
                "Servicios" -> serv
                else -> true
            }
        }
    }

    /**
     * 0.31.0 (P7): los temas ya agrupados por sección, en el orden del manual. Las secciones sin temas se omiten.
     */
    fun seccionesPara(modulos: Set<cu.shalev.domain.model.Modulo>): List<Pair<CategoriaAyuda, List<TemaAyuda>>> =
        CategoriaAyuda.entries.mapNotNull { c -> temasPara(modulos).filter { it.categoria == c }.takeIf { it.isNotEmpty() }?.let { c to it } }

    const val INTRO = "Shalev te ayuda a vender, controlar tu inventario y ver cuánto ganas. " +
        "Elige una sección y toca un tema para ver los pasos."

    val temas: List<TemaAyuda> = listOf(
        TemaAyuda(
            "Primeros pasos",
            listOf(
                "Abajo tienes 5 botones: Inicio, Inventario, Servicios, Registros y Ajustes.",
                "Desliza a la izquierda o a la derecha para pasar de una sección a otra.",
                "En Ajustes → Completar configuración escribe tus datos y elige los avisos.",
                "Tienes 7 días de prueba gratis. Después hace falta una licencia.",
                "Opcional: Ajustes → Acceso con clave pide tu huella o el PIN del teléfono al abrir Shalev.",
            ),
            CategoriaAyuda.EMPEZAR, EsquemaAyuda.BARRA,
        ),
        TemaAyuda(
            "Abrir y cerrar el turno",
            listOf(
                "El turno es la jornada de trabajo: sin turno abierto no se puede vender.",
                "En Inicio toca Abrir turno. Arriba verás el tiempo que lleva abierto.",
                "Al terminar el día toca Cerrar turno: Shalev guarda un resumen con todo lo vendido.",
                "Si sales de Shalev con el turno abierto, verás un aviso arriba en la barra del teléfono.",
            ),
            CategoriaAyuda.VENDER, EsquemaAyuda.TURNO,
        ),
        TemaAyuda(
            "Vender",
            listOf(
                "Toca Nueva venta, elige Productos o Servicios (van por separado) y la cantidad.",
                "Elige cómo te pagan: efectivo o transferencia.",
                "Revisa el total y confirma. La venta queda en Registros.",
            ),
            CategoriaAyuda.VENDER, EsquemaAyuda.VENTA,
        ),
        TemaAyuda(
            "Cobrar por transferencia",
            listOf(
                "En Ajustes → Pago electrónico agrega tu teléfono y tu tarjeta.",
                "Toca el que quieras usar para cobrar: queda marcado \"En uso\".",
                "En la venta por transferencia anota los datos del cliente y el nº de transacción.",
            ),
            CategoriaAyuda.VENDER,
        ),
        TemaAyuda(
            "Ver lo vendido",
            listOf(
                "En Registros ves ventas, servicios, transferencias, movimientos y turnos.",
                "Toca Filtrar para elegir las fechas: hoy, 7 días, este mes…",
                "En ventas, transferencias y servicios también puedes filtrar por importe.",
                "En Movimientos solo se filtra por fecha: un movimiento no tiene importe.",
                "Marca con la casilla lo que quieras exportar y toca Compartir.",
            ),
            CategoriaAyuda.VENDER,
        ),
        TemaAyuda(
            "Agregar productos",
            listOf(
                "Ve a Inventario y toca +.",
                "Escribe el nombre, el precio de compra, el precio de venta y la cantidad.",
                "Si ya hay otro con el mismo nombre, escribe en Descripción qué lo distingue (p. ej. Lata 350 ml).",
                "Toca el botón ✓ (Guardar) arriba a la derecha.",
            ),
            CategoriaAyuda.INVENTARIO,
        ),
        TemaAyuda(
            "Buscar y filtrar el inventario",
            listOf(
                "El buscador encuentra por nombre, categoría o código.",
                "Toca Filtrar y elige el Estado: Todos, Stock bajo, Stock crítico, Próximos a caducar o Sin existencia.",
                "También puedes filtrar por Tipo (artículos, elaborados o insumos) y por Categoría.",
                "El chip bajo el buscador resume el filtro activo; Quitar filtros lo borra.",
            ),
            CategoriaAyuda.INVENTARIO,
        ),
        TemaAyuda(
            "Insumos y elaborados",
            listOf(
                "Los insumos (harina, azúcar…) están en Inventario, en la categoría Insumos.",
                "Para crear uno: Inventario → + → Escribir y elige la categoría Insumos.",
                "Si le pones precio de venta a un insumo, también lo puedes vender.",
                "Crea el producto elaborado con su receta. Al venderlo, sus insumos se descuentan solos.",
                "Verás para cuántos alcanzan los insumos; si no alcanzan, no se puede vender.",
            ),
            CategoriaAyuda.INVENTARIO,
        ),
        TemaAyuda(
            "Servicios",
            listOf(
                "Sirve para lo que ofreces que no es un producto (cortes de pelo, arreglos, entregas…).",
                "Ve a Servicios y toca +. Escribe el nombre, el tipo y el importe.",
                "Si el servicio gasta insumos, agrégalos: se descuentan al venderlo.",
                "Para venderlo: Nueva venta → Servicios. En Inicio verás los más y menos vendidos.",
            ),
            CategoriaAyuda.INVENTARIO,
        ),
        TemaAyuda(
            "Guardar una copia",
            listOf(
                "Ajustes → Respaldo → Exportar y toca Siguiente.",
                "Opcional: activa Proteger con contraseña, escríbela dos veces y anótala.",
                "Toca Guardar en el teléfono o Enviar a otra app (Drive, Telegram…).",
                "Para recuperarla: Respaldo → Elegir archivo (y la contraseña, si la tiene).",
            ),
            CategoriaAyuda.DATOS, EsquemaAyuda.RESPALDO,
        ),
        TemaAyuda(
            "Licencia",
            listOf(
                "Ajustes → Licencia muestra cuántos días te quedan. Sigue los 3 pasos.",
                "Paso 1: revisa tus datos. Paso 2: elige el tipo, WhatsApp o SMS, y toca Solicitar. Solo pulsa Enviar.",
                "Paso 3: copia todo el mensaje que te respondan, toca Pegar y luego Activar.",
                "Si la activación llega en varios SMS, copia y pega todos.",
                "Para renovar con lo mismo, toca Renovar igual. Si renuevas antes de que venza no pierdes días.",
            ),
            CategoriaAyuda.DATOS,
        ),
        TemaAyuda(
            "Apps vinculadas",
            listOf(
                "En tu teléfono: Ajustes → Apps vinculadas → +. Tu licencia dice cuántas secundarias puedes tener.",
                "En el del empleado: Usar esta app como secundaria, escribe su teléfono y escanea tu código (misma red: wifi, zona wifi o datos móviles).",
                "Su teléfono va en su QR de cobro: el SMS de la transferencia le llega a él.",
                "El empleado no cierra su turno: lo solicita y tú lo apruebas o rechazas. Sin red sigue vendiendo.",
                "Toca su app para sus permisos, su tarjeta de cobro o «Pedir cierre» de su turno abierto.",
            ),
            CategoriaAyuda.EMPLEADOS,
        ),
        TemaAyuda(
            "¿Necesitas ayuda?",
            listOf("Ajustes → Soporte: escribe al desarrollador por WhatsApp o SMS."),
            CategoriaAyuda.AYUDA,
        ),
    )
}
