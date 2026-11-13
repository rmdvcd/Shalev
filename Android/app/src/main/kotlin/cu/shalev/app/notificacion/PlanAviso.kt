package cu.shalev.app.notificacion

import cu.shalev.data.sync.AtencionSync
import java.time.Instant

/**
 * 0.19.2 · Decide, sin Android, si la app principal mantiene el servicio en primer plano (ServicioSync) y qué muestra
 * la notificación única de Shalev (ID 29, canal «Turno»).
 *
 * Reglas (autorizadas por el usuario en P40, opción A):
 * - **Servicio:** solo en la PRINCIPAL con secundarias vinculadas, y mientras la app esté a la vista o haya algún turno
 *   abierto (el suyo o el de una secundaria). Sin turnos y fuera de la app se detiene: no gasta batería de noche.
 * - **Notificación:** una sola. Con servicio siempre se ve (Android lo exige). Sin servicio, como en P29: solo con un
 *   turno propio abierto y Shalev fuera de la vista.
 * - Texto sin importes ni datos de clientes.
 */
data class PlanAviso(
    val servicio: Boolean,
    val mostrar: Boolean,
    /** Apertura del turno propio, si lo hay. */
    val turnoDesde: Instant?,
    val conectadas: Int,
    val turnosAbiertos: Int = 0,
) {
    companion object {
        fun calcular(visible: Boolean, turnoPropioDesde: Instant?, atencion: AtencionSync): PlanAviso {
            val servicio = atencion.principalConSecundarias && (visible || atencion.turnosAbiertos > 0)
            return PlanAviso(
                servicio = servicio,
                mostrar = servicio || (turnoPropioDesde != null && !visible),
                turnoDesde = turnoPropioDesde,
                conectadas = if (servicio) atencion.conectadas else 0,
                turnosAbiertos = if (servicio) atencion.turnosAbiertos else 0,
            )
        }

        /** Sin plan todavía (arranque): ni servicio ni aviso. */
        val NINGUNO = PlanAviso(servicio = false, mostrar = false, turnoDesde = null, conectadas = 0)

        fun apps(n: Int) = if (n == 1) "1 app conectada" else "$n apps conectadas"

        /**
         * - Turno propio: «Turno abierto desde las 9:30», más « · 2 apps conectadas» si el servicio está activo y hay
         *   secundarias conectadas.
         * - Solo servicio: «2 apps conectadas» o, sin ninguna conectada, «Esperando a tus apps vinculadas».
         */
        fun texto(plan: PlanAviso, hora: String?): String {
            val conectadas = plan.servicio && plan.conectadas > 0
            return when {
                hora != null && conectadas -> "${AvisoTurno.texto(hora)} · ${apps(plan.conectadas)}"
                hora != null -> AvisoTurno.texto(hora)
                conectadas -> apps(plan.conectadas)
                else -> ESPERANDO
            }
        }

        fun detalle(plan: PlanAviso, hora: String?): String = buildList {
            add(texto(plan, hora))
            if (hora == null) add("Turno de esta app cerrado")
            if (plan.servicio) {
                add("Sincronización local activa")
                add(if (plan.turnosAbiertos == 1) "1 turno abierto en el negocio" else "${plan.turnosAbiertos} turnos abiertos en el negocio")
            }
            add("Toca para volver a Shalev")
        }.joinToString("\n")

        const val ESPERANDO = "Esperando a tus apps vinculadas"
    }
}
