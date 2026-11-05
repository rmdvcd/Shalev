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
        append("No se encuentra la app principal. Usa la misma wifi o un punto de acceso. Para conectar desde lugares distintos, ambas apps necesitan Internet y una versión con conexión remota habilitada.")
        if (pendientes > 0) append(if (pendientes == 1) " Hay 1 envío pendiente." else " Hay $pendientes envíos pendientes.")
    }
}

fun mensajeSync(e: AppError): String? = when (e) {
    is AppError.Validacion -> if (e.campo == "inventarioCambió")
        "El inventario cambió desde el conteo. Rechaza la solicitud y pide un nuevo conteo antes de ajustar."
        else null
    AppError.SinPermiso -> TextosSync.SIN_PERMISO
    is AppError.SinPrincipal -> TextosSync.sinPrincipal(e.pendientes)
    // El motivo viaja por la red: solo se enseña si parece una frase (ver esTextoParaUsuario); si otra versión de
    // la app manda un código interno, la persona lee el texto genérico de mensajeParaUsuario, nunca el código.
    is AppError.VinculacionRechazada -> e.mensajeParaUsuario()
    AppError.Red.SinConexion -> TextosSync.SIN_RED
    AppError.Red.Timeout, is AppError.Red.Http -> e.mensajeParaUsuario()
    else -> null
}
