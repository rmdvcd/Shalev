package cu.shalev.app.notificacion

import androidx.core.app.NotificationCompat
import cu.shalev.designsystem.component.TipoAviso
import cu.shalev.designsystem.token.ColorTokens

/**
 * Notificacion.md aplicado a las notificaciones del sistema. Android dibuja la notificación (no hay píldora ni tarjeta
 * propias), así que se trasladan las reglas, no la forma:
 *
 * - **La urgencia decide la permanencia (§3):** éxito e información son discretas (prioridad baja) y se quitan
 *   solas al rato ([EXPIRA_MS]); advertencia y error se quedan hasta que se tocan o se descartan; la carga es fija
 *   mientras dura y lleva barra de progreso horizontal.
 * - **Una promesa muta (§2.8):** la notificación de una operación cambia en su sitio de «cargando» a éxito o error.
 * - **Ráfagas (§2.3):** el mismo aviso repetido no apila otro: actualiza el mismo con «(×N)» y no vuelve a sonar.
 * - **Pila con contador (§2.1):** los avisos de las apps vinculadas van en un grupo con resumen «N avisos».
 * - **Contenido (§2.5, §8):** como mucho una acción con verbo; el color de estado tiñe solo el icono.
 * - **Sin duplicar (§7):** si la app está a la vista, el resultado de una operación lo enseña la isla de la app y la
 *   notificación se retira; solo queda en la barra cuando Shalev está en segundo plano.
 */
object ReglasNotificacion {
    /** Éxito e información se retiran solos pasado este tiempo (en la barra no hace falta que duren para siempre). */
    const val EXPIRA_MS = 15 * 60_000L

    /** Dos avisos iguales separados por menos de esto son una ráfaga («×N»). */
    const val VENTANA_RAFAGA_MS = 2 * 60_000L

    /** Primario de marca: información y carga. */
    const val COLOR_MARCA = 0xFF2B4FA3.toInt()

    fun expira(tipo: TipoAviso): Long? = if (tipo.persistente) null else EXPIRA_MS

    fun prioridad(tipo: TipoAviso): Int =
        if (tipo.urgente) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW

    fun categoria(tipo: TipoAviso): String = when (tipo) {
        TipoAviso.CARGA -> NotificationCompat.CATEGORY_PROGRESS
        TipoAviso.ERROR -> NotificationCompat.CATEGORY_ERROR
        else -> NotificationCompat.CATEGORY_STATUS
    }

    /** Color de estado del icono pequeño (tema claro de la barra; Android lo ajusta en oscuro). */
    fun color(tipo: TipoAviso): Int = when (tipo) {
        TipoAviso.EXITO -> ColorTokens.L_SUCCESS.toInt()
        TipoAviso.ERROR -> ColorTokens.L_ERROR.toInt()
        TipoAviso.ADVERTENCIA -> ColorTokens.L_ALERT_STOCK_BAJO.toInt()
        TipoAviso.INFO, TipoAviso.CARGA -> COLOR_MARCA
    }

    /** «La app de Luis se conectó (×3)». */
    fun conRafaga(texto: String, repeticiones: Int): String = if (repeticiones > 1) "$texto (×$repeticiones)" else texto

    /** «Etapa 2 de 4 · Cifrando… · 50 %» → solo el porcentaje cuando la barra es determinada. */
    fun porcentaje(p: Int): String = "$p %"

    fun resumen(n: Int): String = if (n == 1) "1 aviso de las apps vinculadas" else "$n avisos de las apps vinculadas"
}

/** Cuenta repeticiones seguidas del mismo aviso dentro de [ventanaMs] (ráfagas «×N»). */
class ContadorRafagas(private val ventanaMs: Long = ReglasNotificacion.VENTANA_RAFAGA_MS) {
    private val vistos = HashMap<String, Pair<Int, Long>>()

    /** Devuelve cuántas veces seguidas llegó [clave] (1 = la primera). */
    @Synchronized
    fun contar(clave: String, ahoraMs: Long): Int {
        val antes = vistos[clave]
        val n = if (antes != null && ahoraMs - antes.second <= ventanaMs) antes.first + 1 else 1
        vistos[clave] = n to ahoraMs
        return n
    }

    /** Olvida [clave] (p. ej., el aviso cambió de tipo: conectada → desconectada). */
    @Synchronized
    fun olvidar(clave: String) { vistos.remove(clave) }
}

/** Lo que hay que publicar para una notificación, ya decidido (sin Android: se prueba en la JVM). */
data class PlanNotificacion(
    val id: Int,
    val canal: String,
    val tipo: TipoAviso,
    val titulo: String,
    val texto: String,
    /** Barra horizontal: null = sin barra; -1 = indeterminada; 0..100 = porcentaje. */
    val progreso: Int?,
    val fija: Boolean,
    val expiraMs: Long?,
    val accion: AccionOperacion?,
    /** Una repetición no vuelve a sonar ni vibrar. */
    val soloAlertarUnaVez: Boolean,
) {
    val prioridad: Int get() = ReglasNotificacion.prioridad(tipo)
    val categoria: String get() = ReglasNotificacion.categoria(tipo)
    val color: Int get() = ReglasNotificacion.color(tipo)
}

/** Qué hacer con la notificación de una operación. */
sealed interface DecisionOperacion {
    /** Retirarla. [consumir] = además se olvida el resultado (ya lo vio dentro de la app). */
    data class Quitar(val consumir: Boolean) : DecisionOperacion
    data class Mostrar(val plan: PlanNotificacion) : DecisionOperacion
}

object PlanOperacion {
    /** Canal de las operaciones en curso y de sus resultados discretos (sin sonido). */
    const val CANAL_OPERACIONES = "operaciones"

    /** Canal de los problemas: advertencias y errores que el usuario debe ver (suena una vez). */
    const val CANAL_PROBLEMAS = "problemas"

    fun decidir(op: Operacion, estado: EstadoOperacion?, appVisible: Boolean): DecisionOperacion {
        if (estado == null) return DecisionOperacion.Quitar(consumir = false)
        // Con la app delante, el resultado lo enseña la isla o el diálogo de la propia pantalla: no se duplica.
        if (!estado.enCurso && appVisible) return DecisionOperacion.Quitar(consumir = true)
        val texto = when {
            estado.enCurso && estado.porcentaje != null -> "${estado.texto} · ${ReglasNotificacion.porcentaje(estado.porcentaje!!)}"
            else -> estado.texto
        }
        return DecisionOperacion.Mostrar(
            PlanNotificacion(
                id = op.idNotificacion,
                canal = if (estado.tipo.urgente) CANAL_PROBLEMAS else CANAL_OPERACIONES,
                tipo = estado.tipo,
                titulo = estado.titulo,
                texto = texto,
                progreso = if (estado.enCurso) estado.porcentaje ?: -1 else null,
                fija = estado.enCurso,
                expiraMs = ReglasNotificacion.expira(estado.tipo),
                accion = estado.accion,
                // La barra se repinta muchas veces y no debe alertar en cada paso; el resultado, en cambio, sí avisa
                // una vez aunque sustituya a la barra (mismo ID).
                soloAlertarUnaVez = estado.enCurso,
            ),
        )
    }
}
