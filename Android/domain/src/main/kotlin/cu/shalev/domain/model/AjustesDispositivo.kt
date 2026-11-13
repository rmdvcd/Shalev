package cu.shalev.domain.model

/**
 * 0.27.0: ajustes propios de ESTE teléfono. No van en el respaldo: cada teléfono decide.
 *
 * - [accesoConClave] (T11): pedir la huella, la cara o el PIN/patrón del teléfono al abrir Shalev.
 * - [eligioSecundaria] (T9): en el recorrido inicial se eligió «App de un empleado». Solo entonces la app (aún sin
 *   vincular) ofrece vincularse como secundaria; una app principal nunca pasa a secundaria.
 */
data class AjustesDispositivo(
    val accesoConClave: Boolean = false,
    val eligioSecundaria: Boolean = false,
)
