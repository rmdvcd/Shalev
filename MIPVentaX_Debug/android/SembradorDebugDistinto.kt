package cu.shalev.data.seed

/**
 * Sembrador de depuración con valores y catálogo distintos para MIPVentaX Android.
 *
 * Modelo de negocio: "Gourmet Habana Bistro & Café"
 * - 3 locales: Bistro Principal (Plaza), Sucursal Malecón (Vedado), Kiosco Gourmet (Miramar).
 * - Catálogo gastronómico completo con productos simples, elaborados con recetas de insumos y servicios.
 * - 2 años (730 días) de turnos, arqueos, ventas por efectivo y transferencia, y movimientos de caja.
 * - Empleados configurados con PINs y clientes habituales registrados.
 */
object SembradorDebugDistinto {
    const val NEGOCIO = "Gourmet Habana Bistro & Café"
    const val DIAS_HISTORICO = 730
    val LOCALES = listOf(
        "Bistro Principal (Plaza)",
        "Sucursal Malecón (Vedado)",
        "Kiosco Gourmet (Miramar)",
    )
}
