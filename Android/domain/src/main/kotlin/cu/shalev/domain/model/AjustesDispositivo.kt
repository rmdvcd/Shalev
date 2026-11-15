package cu.shalev.domain.model

/**
 * 0.27.0: ajustes propios de ESTE teléfono. No van en el respaldo: cada teléfono decide.
 *
 * - [accesoConClave] (T11): pedir la huella, la cara o el PIN/patrón del teléfono al abrir Shalev.
 * - [eligioSecundaria] (T9): en el recorrido inicial se eligió «App de un empleado». Esa instalación recién iniciada
 *   puede vincularse sin respaldo; las instalaciones con datos deben exportar un respaldo antes de cambiar de tipo.
 */
data class AjustesDispositivo(
    val accesoConClave: Boolean = false,
    val eligioSecundaria: Boolean = false,
)
