package cu.shalev.app.common

/*
 * Lógica PURA de permisos y consentimientos (sin Android): se prueba en JVM (PermisosLogicTest).
 * Textos para usuarios sin experiencia: qué se pide, para qué y qué pasa si dice que no.
 */

/**
 * Estado de un permiso peligroso visto desde la UI. Shalev SIEMPRE explica antes de que aparezca el diálogo
 * del sistema, y solo cuando el usuario abre la función que lo necesita (nunca al arrancar).
 */
enum class EstadoPermiso {
    /** Concedido: se puede usar. */
    CONCEDIDO,
    /** Nunca pedido: mostrar nuestra explicación y un botón "Permitir". */
    EXPLICAR,
    /** Denegado una vez: explicar de nuevo; el sistema aún permite volver a pedirlo. */
    DENEGADO,
    /** El sistema ya no mostrará su diálogo: solo se puede activar desde los ajustes del teléfono. */
    BLOQUEADO,
    /** El teléfono no tiene el hardware (p. ej. sin cámara): ofrecer la alternativa manual. */
    SIN_HARDWARE,
}

/** Decisión pura. [sistemaSugiereExplicar] = shouldShowRequestPermissionRationale. */
fun estadoPermiso(tieneHardware: Boolean, concedido: Boolean, yaSolicitado: Boolean, sistemaSugiereExplicar: Boolean): EstadoPermiso = when {
    !tieneHardware -> EstadoPermiso.SIN_HARDWARE
    concedido -> EstadoPermiso.CONCEDIDO
    !yaSolicitado -> EstadoPermiso.EXPLICAR
    sistemaSugiereExplicar -> EstadoPermiso.DENEGADO
    else -> EstadoPermiso.BLOQUEADO
}

enum class AccionPermiso { SOLICITAR, ABRIR_AJUSTES, NINGUNA }

data class Explicacion(val titulo: String, val detalle: String, val boton: String?, val accion: AccionPermiso)

/** Justificación de la cámara según el estado (lector QR de vinculación). */
fun explicacionCamara(e: EstadoPermiso): Explicacion = when (e) {
    EstadoPermiso.CONCEDIDO -> Explicacion("Cámara lista", "Apunta al código QR de la otra app.", null, AccionPermiso.NINGUNA)
    EstadoPermiso.EXPLICAR -> Explicacion(
        "Usar la cámara para leer el QR",
        "Shalev usa la cámara solo mientras esta pantalla está abierta, para leer el código QR de vinculación. No se guardan fotos ni videos.",
        "Permitir cámara", AccionPermiso.SOLICITAR,
    )
    EstadoPermiso.DENEGADO -> Explicacion(
        "La cámara no está permitida",
        "Sin la cámara no se puede leer el QR automáticamente. Puedes permitirla ahora.",
        "Permitir cámara", AccionPermiso.SOLICITAR,
    )
    EstadoPermiso.BLOQUEADO -> Explicacion(
        "La cámara está desactivada para Shalev",
        "Para activarla: toca el botón de ajustes (engranaje), entra en «Permisos» y activa «Cámara» para leer el QR.",
        "Abrir ajustes", AccionPermiso.ABRIR_AJUSTES,
    )
    EstadoPermiso.SIN_HARDWARE -> Explicacion(
        "Este teléfono no tiene cámara",
        "La vinculación por QR necesita cámara.",
        null, AccionPermiso.NINGUNA,
    )
}
