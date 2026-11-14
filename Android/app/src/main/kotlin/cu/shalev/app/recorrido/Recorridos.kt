package cu.shalev.app.recorrido

import cu.shalev.designsystem.component.CoachMarkPaso

/**
 * Claves de los elementos que resaltan los recorridos. Cada pantalla las pone con
 * [cu.shalev.designsystem.component.coachMark]; fuera de un recorrido el modificador no hace nada.
 */
object ObjetivosRecorrido {
    // Inicio (turno y caja)
    const val CAJA_TURNO = "caja_turno"
    const val CAJA_VENDER = "caja_vender"
    const val CAJA_MOVIMIENTO = "caja_movimiento"
    const val CAJA_RESUMEN = "caja_resumen"

    // Venta
    const val VENTA_CARRITO = "venta_carrito"
    const val VENTA_METODO = "venta_metodo"
    const val VENTA_AGREGAR = "venta_agregar"
    const val VENTA_COBRAR = "venta_cobrar"

    // Inventario
    const val INVENTARIO_LISTA = "inventario_lista"
    const val INVENTARIO_BUSCAR = "inventario_buscar"
    const val INVENTARIO_FILTRO = "inventario_filtro"
    const val INVENTARIO_AGREGAR = "inventario_agregar"

    // Servicios
    const val SERVICIOS_LISTA = "servicios_lista"
    const val SERVICIOS_BUSCAR = "servicios_buscar"
    const val SERVICIOS_FILTRO = "servicios_filtro"
    const val SERVICIOS_AGREGAR = "servicios_agregar"

    // Registros
    const val REGISTROS_PESTANAS = "registros_pestanas"
    const val REGISTROS_BUSCAR = "registros_buscar"
    const val REGISTROS_FILTRO = "registros_filtro"
    const val REGISTROS_EXPORTAR = "registros_exportar"
}

/**
 * Un recorrido por módulo, con 3–4 pasos cortos. [clave] da nombre al indicador guardado en DataStore
 * (`<clave>_tour_shown`, ver [OnboardingPreferences]). Los pasos cuyo objetivo no esté en pantalla al empezar se
 * saltan (p. ej. «Vender» con el turno cerrado o «Exportar» sin permiso).
 *
 * Textos: título de 2–4 palabras y una frase que empieza por lo que hay que hacer («Toca…», «Escribe…»).
 */
enum class Recorrido(val clave: String, val pasos: List<CoachMarkPaso>) {
    CAJA(
        "caja",
        listOf(
            CoachMarkPaso(
                ObjetivosRecorrido.CAJA_TURNO, "Abre y cierra el turno",
                "Toca aquí para abrir el turno con el fondo de caja. Al terminar, tócalo otra vez para contar y cerrar la caja.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.CAJA_VENDER, "Nueva venta",
                "Toca aquí para vender productos o servicios. Solo aparece con el turno abierto.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.CAJA_MOVIMIENTO, "Entradas y salidas",
                "Toca aquí para anotar el efectivo que entra o sale de la caja sin ser una venta.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.CAJA_RESUMEN, "Resumen de ventas",
                "Elige el período para ver ventas, ganancia y lo más vendido. Toca una barra para ver su importe.",
            ),
        ),
    ),
    VENTAS(
        "ventas",
        listOf(
            CoachMarkPaso(
                ObjetivosRecorrido.VENTA_CARRITO, "Tu carrito",
                "Ajusta aquí la cantidad de cada artículo con −1 y +1, o toca Quitar para sacarlo de la venta.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.VENTA_METODO, "Forma de pago",
                "Toca Efectivo o Transferencia antes de cobrar.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.VENTA_AGREGAR, "Agregar artículos",
                "Toca aquí para volver a la lista, buscar por nombre y sumar más productos.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.VENTA_COBRAR, "Cobrar",
                "Toca Continuar para cobrar: en efectivo ves el comprobante; con transferencia, el código QR.",
            ),
        ),
    ),
    INVENTARIO(
        "inventario",
        listOf(
            CoachMarkPaso(
                ObjetivosRecorrido.INVENTARIO_LISTA, "Tus productos",
                "Toca un artículo para ver su ficha. Desde Editar ajustas existencias, costo y precio.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.INVENTARIO_BUSCAR, "Buscar",
                "Escribe parte del nombre para encontrar un artículo al momento.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.INVENTARIO_FILTRO, "Filtrar",
                "Toca aquí para ver solo un estado (sin existencias, por caducar…), un tipo o una categoría.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.INVENTARIO_AGREGAR, "Agregar producto",
                "Toca aquí para crear un producto con su foto, precio y existencias.",
            ),
        ),
    ),
    SERVICIOS(
        "servicios",
        listOf(
            CoachMarkPaso(
                ObjetivosRecorrido.SERVICIOS_LISTA, "Tus servicios",
                "Toca un servicio para ver su ficha, editarlo o compartirlo.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.SERVICIOS_BUSCAR, "Buscar",
                "Escribe parte del nombre para encontrar un servicio.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.SERVICIOS_FILTRO, "Filtrar",
                "Toca aquí para ver solo un tipo de servicio.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.SERVICIOS_AGREGAR, "Agregar servicio",
                "Toca aquí para crear un servicio con su precio.",
            ),
        ),
    ),
    REGISTROS(
        "registros",
        listOf(
            CoachMarkPaso(
                ObjetivosRecorrido.REGISTROS_PESTANAS, "Qué quieres ver",
                "Toca una pestaña para cambiar entre ventas, transferencias, turnos y los demás registros.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.REGISTROS_BUSCAR, "Buscar",
                "Escribe para encontrar un registro concreto.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.REGISTROS_FILTRO, "Filtrar",
                "Toca aquí para acotar por fechas, importe o vendedor.",
            ),
            CoachMarkPaso(
                ObjetivosRecorrido.REGISTROS_EXPORTAR, "Compartir",
                "Toca aquí para guardar o enviar la tabla en PDF o Excel.",
            ),
        ),
    ),
}
