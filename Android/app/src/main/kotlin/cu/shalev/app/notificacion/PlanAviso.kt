package cu.shalev.app.notificacion

import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
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
 * - Muestra si el turno de esta app está abierto (con el total de sus ventas válidas) o cerrado, y cuántas
 *   secundarias vinculadas están conectadas. Por decisión del usuario, el importe también es visible en la pantalla
 *   bloqueada; no se muestran datos de clientes.
 */
data class PlanAviso(
    val servicio: Boolean,
    val mostrar: Boolean,
    /** Apertura del turno propio, si lo hay. */
    val turnoDesde: Instant?,
    val conectadas: Int,
    val turnosAbiertos: Int = 0,
    /** Total de ventas válidas del turno de esta app; null cuando esta app no tiene un turno abierto. */
    val ventasTurno: Cup? = null,
    /** Secundarias vinculadas (denominador de «conectadas»). */
    val vinculadas: Int = 0,
) {
    companion object {
        fun calcular(
            visible: Boolean,
            turnoPropioDesde: Instant?,
            atencion: AtencionSync,
            ventasTurno: Cup? = null,
        ): PlanAviso {
            val servicio = atencion.principalConSecundarias && (visible || atencion.turnosAbiertos > 0)
            return PlanAviso(
                servicio = servicio,
                mostrar = servicio || (turnoPropioDesde != null && !visible),
                turnoDesde = turnoPropioDesde,
                conectadas = if (servicio) atencion.conectadas else 0,
                turnosAbiertos = if (servicio) atencion.turnosAbiertos else 0,
                ventasTurno = ventasTurno.takeIf { turnoPropioDesde != null },
                vinculadas = if (servicio) atencion.vinculadas.coerceAtLeast(atencion.conectadas) else 0,
            )
        }

        /** Sin plan todavía (arranque): ni servicio ni aviso. */
        val NINGUNO = PlanAviso(servicio = false, mostrar = false, turnoDesde = null, conectadas = 0)

        fun apps(n: Int) = if (n == 1) "1 app conectada" else "$n apps conectadas"

        /** «2 de 3 apps conectadas»; sin total conocido conserva el texto corto. */
        fun apps(conectadas: Int, vinculadas: Int): String =
            if (vinculadas <= 0) apps(conectadas)
            else "$conectadas de $vinculadas ${if (vinculadas == 1) "app conectada" else "apps conectadas"}"

        /**
         * - Turno propio: antepone el total acumulado y, si el servicio está activo, «N de M apps conectadas» antes
         *   de la hora del turno.
         * - Solo servicio: «Turno cerrado» con el conteo de apps o, sin ninguna conectada, la espera de las vinculadas.
         */
        fun texto(plan: PlanAviso, hora: String?): String {
            val conteo = if (plan.servicio && plan.vinculadas > 0) apps(plan.conectadas, plan.vinculadas) else null
            val estado = when {
                hora != null && conteo != null -> "$conteo · ${AvisoTurno.texto(hora)}"
                hora != null -> AvisoTurno.texto(hora)
                conteo != null -> "$TURNO_CERRADO · $conteo"
                else -> ESPERANDO
            }
            return plan.ventasTurno?.let { "Ventas del turno: ${Money.format(it)} · $estado" } ?: estado
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
        const val TURNO_CERRADO = "Turno cerrado"
    }
}
