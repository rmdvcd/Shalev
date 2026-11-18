package cu.shalev.domain.model

/**
 * 1.0.7: por qué red de este teléfono se anuncia la app principal a las apps secundarias
 * (Ajustes → Apps vinculadas, al inicio; una sola opción). Filtra las direcciones IPv4 que se
 * muestran y viajan en el QR de vinculación:
 * - [LAN]: solo las interfaces de la red del local (wifi o cable). Es la de siempre y va por defecto.
 * - [PUNTO_DE_ACCESO]: solo la zona wifi que crea este teléfono (para un local sin router).
 * - [DATOS_MOVILES]: solo la red celular (si la operadora permite conexiones directas entre abonados).
 */
enum class ModoRed(val etiqueta: String, val detalle: String) {
    LAN("LAN", "La wifi o el cable del local"),
    PUNTO_DE_ACCESO("Punto de acceso", "La zona wifi de este teléfono"),
    DATOS_MOVILES("Datos móviles", "La red celular, si la operadora lo permite"),
}

/**
 * 0.27.0: ajustes propios de ESTE teléfono. No van en el respaldo: cada teléfono decide.
 *
 * - [accesoConClave] (T11): pedir la huella, la cara o el PIN/patrón del teléfono al abrir Shalev.
 * - [eligioSecundaria] (T9): en el recorrido inicial se eligió «App de un empleado». Esa instalación recién iniciada
 *   puede vincularse sin respaldo; las instalaciones con datos deben exportar un respaldo antes de cambiar de tipo.
 * - [modoRed] (1.0.7): por qué red se anuncia la principal a las secundarias (ver [ModoRed]).
 */
data class AjustesDispositivo(
    val accesoConClave: Boolean = false,
    val eligioSecundaria: Boolean = false,
    val modoRed: ModoRed = ModoRed.LAN,
)
