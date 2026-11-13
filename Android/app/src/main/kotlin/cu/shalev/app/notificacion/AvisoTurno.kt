package cu.shalev.app.notificacion

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cu.shalev.app.MainActivity
import cu.shalev.app.R
import cu.shalev.data.sync.ArranqueSync
import cu.shalev.data.sync.AtencionSync
import cu.shalev.domain.repository.TurnoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * P29 · Aviso en la barra de estado mientras hay un turno abierto y Shalev no está a la vista (minimizada o cerrada
 * con Atrás). Decisiones (respuestas del usuario):
 * - Solo POST_NOTIFICATIONS (Android 13+); sin RECEIVE_BOOT_COMPLETED: si el teléfono
 *   se reinicia, el aviso no vuelve hasta abrir la app.
 * - Texto sin importes ni datos de clientes: «Turno abierto desde las HH:MM» (formato de hora del dispositivo).
 * - Canal «Turno» de importancia baja: sin sonido ni vibración. Tocarlo abre Shalev donde estaba.
 * - Se quita al volver a la app o al cerrar el turno.
 *
 * 0.19.2 (P40, opción A autorizada): en la PRINCIPAL con secundarias, esta misma notificación es la del servicio en
 * primer plano [ServicioSync], que mantiene vivo el servidor de red local mientras haya turnos abiertos. Las reglas
 * están en [PlanAviso]; esta clase solo las aplica. Una sola notificación: nunca dos.
 */
@Singleton
class AvisoTurno @Inject constructor(
    @ApplicationContext private val context: Context,
    private val turnos: TurnoRepository,
    private val arranque: ArranqueSync,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val enPrimerPlano = MutableStateFlow(false)
    private var iniciado = false
    /** Último plan aplicado (lo lee ServicioSync al arrancar y al terminar). Solo se toca en el hilo principal. */
    private var plan = PlanAviso.NINGUNO
    /** Se pidió arrancar ServicioSync y todavía no se ha pedido pararlo. */
    private var servicioPedido = false
    /** ServicioSync está en primer plano con nuestra notificación. */
    private var servicioActivo = false

    /** Desde [android.app.Application.onCreate]: cuenta las actividades visibles y vigila el turno activo. */
    fun iniciar(app: Application) {
        if (iniciado) return
        iniciado = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var visibles = 0
            override fun onActivityStarted(activity: Activity) { visibles++; enPrimerPlano.value = true }
            override fun onActivityStopped(activity: Activity) {
                visibles = (visibles - 1).coerceAtLeast(0)
                // Un giro de pantalla para y vuelve a crear la actividad: no cuenta como salir de la app.
                if (visibles == 0 && !activity.isChangingConfigurations) enPrimerPlano.value = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        scope.launch {
            val atencion: Flow<AtencionSync> = arranque.atencion.catch { emit(AtencionSync()) }
            combine(enPrimerPlano, turnos.observarActivo().catch { emit(null) }, atencion) { visible, turno, at ->
                PlanAviso.calcular(visible, turno?.takeIf { it.abierto }?.abiertoEn, at)
            }
                .distinctUntilChanged()
                .collect { aplicar(it) }
        }
    }

    private fun aplicar(nuevo: PlanAviso) {
        plan = nuevo
        if (nuevo.servicio && !servicioPedido) {
            servicioPedido = ServicioSync.arrancar(context)
        } else if (!nuevo.servicio && servicioPedido) {
            servicioPedido = false
            if (servicioActivo) {
                // ServicioSync.onDestroy suelta la notificación y llama a servicioTerminado(), que la deja como toque.
                ServicioSync.parar(context)
                return
            }
            // Aún no entró en primer plano: pararlo ahora haría fallar startForegroundService. Se para solo al
            // arrancar (servicioNecesario() == false).
        }
        refrescar()
    }

    /** Publica o quita la notificación según [plan]. Con el servicio activo, actualiza la suya (mismo ID). */
    private fun refrescar() {
        if (plan.mostrar) mostrar() else if (!servicioActivo) quitar()
    }

    /** ServicioSync, en onStartCommand: la notificación con la que entra en primer plano. */
    internal fun notificacionServicio(): android.app.Notification {
        crearCanal()
        servicioActivo = true
        servicioPedido = true
        return construir(plan.copy(servicio = true, mostrar = true))
    }

    /** ServicioSync, al terminar (o si Android no le dejó entrar en primer plano). */
    internal fun servicioTerminado() {
        servicioActivo = false
        // Si lo paró Android (o no lo dejó entrar en primer plano), se reintenta con el próximo cambio de estado.
        servicioPedido = false
        refrescar()
    }

    /** ServicioSync, al arrancar: si el estado cambió mientras arrancaba, se para enseguida. */
    internal fun servicioNecesario(): Boolean = plan.servicio

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, NOMBRE_CANAL, NotificationManager.IMPORTANCE_LOW).apply {
                description = DESCRIPCION_CANAL
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
        }
    }

    private fun mostrar() {
        if (!puedeNotificar(context)) return
        crearCanal()
        try {
            NotificationManagerCompat.from(context).notify(ID, construir(plan))
        } catch (_: SecurityException) {
            // El usuario retiró el permiso entre la comprobación y el aviso: no se muestra nada.
        }
    }

    private fun construir(p: PlanAviso): android.app.Notification {
        // MAIN/LAUNCHER + singleTask: devuelve la tarea existente tal como estaba (no reinicia la venta en curso).
        val abrir = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, 0, abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val hora = p.turnoDesde?.let { DateFormat.getTimeFormat(context).format(Date.from(it)) }
        return NotificationCompat.Builder(context, CANAL)
            // P31: un solo icono, la silueta del lanzador (la barra de estado solo usa el alfa). Sin icono grande:
            // Android ya muestra el pequeño, teñido con COLOR_MARCA, en la cabecera; con los dos se veía repetido.
            .setSmallIcon(R.drawable.ic_stat_shalev)
            .setColor(COLOR_MARCA)
            .setContentTitle(TITULO)
            .setContentText(PlanAviso.texto(p, hora))
            .setStyle(NotificationCompat.BigTextStyle().bigText(PlanAviso.detalle(p, hora)))
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(p.turnoDesde?.toEpochMilli() ?: 0L)
            .setUsesChronometer(p.turnoDesde != null)
            .setShowWhen(p.turnoDesde != null)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // no contiene datos sensibles
            // Android 12+: sin el retraso de 10 s de las notificaciones de servicio (es la misma del turno).
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun quitar() = NotificationManagerCompat.from(context).cancel(ID)

    companion object {
        const val CANAL = "turno"
        const val NOMBRE_CANAL = "Turno"
        const val DESCRIPCION_CANAL = "Aviso mientras hay un turno abierto y Shalev está en segundo plano, o atiende a tus apps vinculadas."
        const val TITULO = "Shalev"
        /** Primario de marca (tema claro): tiñe el icono pequeño en la cabecera de la notificación. */
        private const val COLOR_MARCA = 0xFF2B4FA3.toInt()
        internal const val ID = 29

        fun texto(hora: String) = "Turno abierto desde las $hora"

        /** Android 12 o anterior: no hace falta permiso (salvo que el usuario haya apagado las notificaciones). */
        fun puedeNotificar(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }
}
