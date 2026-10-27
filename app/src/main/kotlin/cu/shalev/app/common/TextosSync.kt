package cu.shalev.app.common

import cu.shalev.core.result.AppError

/**
 * P37: mensajes de los errores propios de Principal/Secundaria. Las pantallas los usan como respaldo de sus propios
 * textos (null = no es un error de vinculación: la pantalla muestra su mensaje genérico).
 */
object TextosSync {
    const val SIN_PERMISO = "El dueño no te dio permiso para esto. Pídeselo desde su app."
    /** 1.0.1: también por datos móviles (el QR lleva todas las direcciones de la principal). */
    const val SIN_RED = "Conéctate a una wifi, activa la zona wifi de este teléfono o enciende los datos móviles."

    fun sinPrincipal(pendientes: Int): String = buildString {
        append("No se encuentra la app principal. Conecta los dos teléfonos a la misma red: wifi, zona wifi o datos móviles.")
        if (pendientes > 0) append(if (pendientes == 1) " Hay 1 envío pendiente." else " Hay $pendientes envíos pendientes.")
    }
}

fun mensajeSync(e: AppError): String? = when (e) {
    is AppError.Validacion -> if (e.campo == "inventarioCambió")
        "El inventario cambió desde el conteo. Rechaza la solicitud y pide un nuevo conteo antes de ajustar."
        else null
    AppError.SinPermiso -> TextosSync.SIN_PERMISO
    is AppError.SinPrincipal -> TextosSync.sinPrincipal(e.pendientes)
    is AppError.VinculacionRechazada -> e.motivo
    AppError.Red.SinConexion -> TextosSync.SIN_RED
    else -> null
}
