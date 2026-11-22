package cu.shalev.core.contact

/**
 * Representa un método de pago disponible para la adquisición de licencias.
 */
data class MetodoCobro(
    val nombre: String,
    val destino: String,
    val nota: String? = null,
)

/**
 * Fuente única de datos de cobro en Android (T2).
 *
 * AVISO DE SEGURIDAD:
 * Si el repositorio es público, los datos de cobro no deben comitearse con información real.
 * Se dejan vacíos con marcadores para que el propietario decida su despliegue y alojamiento.
 */
object PaymentInstructions {
    const val TITULO = "Cómo pagar"
    const val FRASE_PAGA_SOLO = "Paga solo a los datos que muestra esta pantalla"
    const val AVISO_SIN_METODOS = "Te enviaremos los datos de pago al recibir tu solicitud."

    /**
     * Lista de métodos configurados (p. ej. Transfermóvil, EnZona, tarjeta CUP).
     * Por defecto vacía con marcadores.
     */
    val METODOS: List<MetodoCobro> = emptyList()
}
