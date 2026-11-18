package cu.shalev.app.notificacion

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cu.shalev.app.MainActivity
import cu.shalev.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Pinta en la barra de notificaciones las operaciones largas de [OperacionesEnCurso] (actualización y respaldos):
 * barra de progreso horizontal mientras duran y, al terminar, la misma notificación pasa a éxito o error.
 * Las decisiones están en [PlanOperacion] (sin Android); esta clase solo las aplica.
 */
@Singleton
class NotificadorOperaciones @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operaciones: OperacionesEnCurso,
    private val visibilidad: VisibilidadApp,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var iniciado = false
    /** Último plan publicado de cada operación: solo se vuelve a publicar si cambia. */
    private val publicados = HashMap<Operacion, PlanNotificacion>()

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        scope.launch {
            combine(operaciones.estados, visibilidad.aLaVista) { e, v -> e to v }
                .distinctUntilChanged()
                .collect { (estados, visible) -> Operacion.entries.forEach { aplicar(it, estados[it], visible) } }
        }
    }

    /** Aplica la decisión para [op]. Público para las pruebas (Robolectric) y para [AccionNotificacionReceiver]. */
    internal fun aplicar(op: Operacion, estado: EstadoOperacion?, visible: Boolean) {
        when (val d = PlanOperacion.decidir(op, estado, visible)) {
            is DecisionOperacion.Quitar -> {
                if (publicados.remove(op) != null || d.consumir) cancelar(op.idNotificacion)
                if (d.consumir) operaciones.quitar(op)
            }
            is DecisionOperacion.Mostrar -> if (publicados[op] != d.plan) {
                if (publicar(d.plan, op)) publicados[op] = d.plan
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun publicar(plan: PlanNotificacion, op: Operacion): Boolean {
        if (!AvisoTurno.puedeNotificar(context)) return false
        CanalesAvisos.crear(context)
        return runCatching { NotificationManagerCompat.from(context).notify(plan.id, construir(context, plan, op)) }.isSuccess
    }

    private fun cancelar(id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    companion object {
        /** La notificación de [plan]. Separada para poder comprobarla en Robolectric. */
        internal fun construir(context: Context, plan: PlanNotificacion, op: Operacion): Notification {
            val b = NotificationCompat.Builder(context, plan.canal)
                .setSmallIcon(iconoOperacion(op))
                .setColor(ReglasNotificacion.color(context, plan.tipo))
                .setContentTitle(plan.titulo)
                .setContentText(plan.texto)
                // El texto breve ya contiene toda la información útil; una BigTextStyle idéntica solo lo duplicaba.
                .setContentIntent(abrirApp(context))
                .setOngoing(plan.fija)
                .setAutoCancel(!plan.fija)
                .setOnlyAlertOnce(plan.soloAlertarUnaVez)
                .setPriority(plan.prioridad)
                .setCategory(plan.categoria)
                .setShowWhen(!plan.fija)
            when {
                plan.progreso == null -> Unit
                plan.progreso < 0 -> b.setProgress(0, 0, true)
                else -> b.setProgress(100, plan.progreso, false)
            }
            if (plan.fija) b.setSilent(true)
            plan.expiraMs?.let { b.setTimeoutAfter(it) }
            plan.accion?.let { a -> b.addAction(iconoOperacion(op), a.texto, intentAccion(context, op, a)) }
            return b.build()
        }

        private fun iconoOperacion(op: Operacion): Int = R.drawable.ic_stat_shalev

        private fun intentAccion(context: Context, op: Operacion, a: AccionOperacion): PendingIntent = when (a) {
            AccionOperacion.ABRIR -> abrirApp(context)
            // Solo la descarga de la actualización se puede cancelar desde fuera; el respaldo se gestiona en su pantalla.
            AccionOperacion.CANCELAR -> if (op == Operacion.ACTUALIZACION) {
                AccionNotificacionReceiver.intent(context, AccionNotificacionReceiver.CANCELAR_ACTUALIZACION)
            } else {
                abrirApp(context)
            }
        }

        /** MAIN/LAUNCHER + singleTask: devuelve la tarea tal como estaba (no reinicia lo que se estaba haciendo). */
        internal fun abrirApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Canales compartidos de los avisos con la urgencia de Notificacion.md: las operaciones y sus resultados discretos
 * van mudos; los problemas (advertencias y errores) suenan una vez con el sonido del sistema.
 */
object CanalesAvisos {
    const val NOMBRE_OPERACIONES = "Actualización y respaldos"
    const val NOMBRE_PROBLEMAS = "Problemas"

    fun crear(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(PlanOperacion.CANAL_OPERACIONES, NOMBRE_OPERACIONES, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Barra de progreso de la actualización y de los respaldos, y su resultado. Sin sonido."
                setShowBadge(false); enableVibration(false); setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(PlanOperacion.CANAL_PROBLEMAS, NOMBRE_PROBLEMAS, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Errores y advertencias que necesitan que hagas algo: una descarga que falló, una sincronización cortada…"
                setShowBadge(true)
            },
        )
    }
}
