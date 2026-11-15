package cu.shalev.app.notificacion

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cu.shalev.app.R
import cu.shalev.data.sync.ClienteSync
import cu.shalev.data.sync.ConfigSync
import cu.shalev.data.sync.ServidorSync
import cu.shalev.designsystem.component.TipoAviso
import cu.shalev.domain.model.FaseSync
import cu.shalev.domain.model.ProgresoSync
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.repository.PrincipalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 0.32.0: avisos de la sincronización, con las reglas de Notificacion.md ([ReglasNotificacion]):
 *
 * - **Barra de avance** horizontal (canal «Sincronización», sin sonido) mientras la principal reparte el catálogo o
 *   la secundaria lo recibe. Al terminar, **esa misma notificación** pasa a «Sincronización terminada» (éxito, se
 *   quita sola al rato). Si la barra de la secundaria se corta sin terminar y Shalev no está a la vista, pasa a
 *   «La sincronización se cortó» (advertencia, se queda) con **Reintentar**.
 * - **Conexiones:** una notificación por app vinculada que cambia en su sitio de «se conectó» (información, se quita
 *   sola) a «se desconectó» (advertencia, se queda). Las repeticiones seguidas cuentan «(×N)» y no vuelven a sonar.
 *   Con dos o más, van agrupadas bajo un resumen «N avisos de las apps vinculadas».
 * - **Sonidos propios** (decisión E/F de 0.32.0, se conservan): conectada, desconectada y sincronizada tienen cada
 *   una su canal y su archivo en `res/raw`.
 *
 * En la principal se oye conectar/desconectar/sincronizado; en la secundaria, solo «sincronización terminada».
 * Si el usuario no ha permitido las notificaciones (Android 13+), no se muestra ninguna: la app sigue igual.
 */
@Singleton
class AvisoSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val servidor: ServidorSync,
    private val cliente: ClienteSync,
    private val config: ConfigSync,
    private val principal: PrincipalRepository,
    private val visibilidad: VisibilidadApp,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var iniciado = false
    /** Nombres de los empleados, para los avisos («la app de Luis se conectó»). */
    private var nombres: Map<Long, String> = emptyMap()
    private val rafagas = ContadorRafagas()

    /** La barra está publicada (o lo estaba hace un momento). */
    private var barraVisible = false
    /** Desde que empezó la barra actual, un resultado ya ocupó su sitio. */
    private var resultadoEnLaBarra = false
    /** La barra actual es la de esta app como secundaria (la única que se puede reintentar desde aquí). */
    private var barraDeCliente = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        // Los canales existen desde el principio cuando hay algo que sincronizar: así el usuario puede ajustar el
        // volumen de cada sonido en los ajustes de notificaciones de Android.
        scope.launch {
            combine(config.tipo, principal.observarEmpleados()) { t, e -> t == TipoApp.SECUNDARIA || e.isNotEmpty() }
                .distinctUntilChanged()
                .collect { if (it) crearCanales() }
        }
        scope.launch {
            principal.observarEmpleados().collect { lista ->
                nombres = lista.associate { it.id to it.nombre }
            }
        }
        // 1) Barra de avance (la de la principal o la de esta app si es secundaria).
        scope.launch {
            combine(
                servidor.estado.map { e -> Avance(e.progresos.values.firstOrNull(), e.progresos.size, cliente = false) },
                cliente.progreso.map { Avance(it, 1, cliente = true) },
            ) { a, b -> if (a.progreso != null) a else b }
                .collectLatest { a ->
                    if (a.progreso != null) {
                        barraVisible = true
                        resultadoEnLaBarra = false
                        barraDeCliente = a.cliente
                        mostrarProgreso(a.progreso, a.apps)
                    } else if (barraVisible) {
                        // El resultado llega justo después de que la barra se vacíe: se le da un margen para que
                        // ocupe su sitio. collectLatest cancela la espera si vuelve a haber avance.
                        delay(GRACIA_MS)
                        barraVisible = false
                        if (!resultadoEnLaBarra) {
                            if (barraDeCliente && !visibilidad.aLaVista.value) avisarCortada() else quitarProgreso()
                        }
                    }
                }
        }
        // 2) Conexiones: en la principal, cada app que entra o sale.
        scope.launch {
            servidor.estado.map { it.conectadas }.distinctUntilChanged().collect { conectadas ->
                val antes = conectadasAnteriores
                conectadasAnteriores = conectadas
                conectadas.forEach { id -> if (id !in antes) avisarConexion(id, conectado = true) }
                antes.forEach { id -> if (id !in conectadas) avisarConexion(id, conectado = false) }
            }
        }
        // 3) Fin de sincronización.
        scope.launch {
            servidor.sincronizadas.collect { id -> avisarSincronizada(nombreDe(id), "sync:$id") }
        }
        scope.launch {
            cliente.sincronizada.collect { avisarSincronizada(null, "sync:propia") }
        }
    }

    private var conectadasAnteriores: Set<Long> = emptySet()

    /** El avance que se muestra: de qué app es y cuántas hay. */
    private data class Avance(val progreso: ProgresoSync?, val apps: Int, val cliente: Boolean)

    private fun nombreDe(id: Long): String? = nombres[id]

    // ---------------------------------------------------------------- avisos

    // Las notificaciones se pueden perder en cualquier momento: el permiso se comprueba con
    // [AvisoTurno.puedeNotificar] y cada aviso va envuelto en runCatching (el usuario puede retirarlo entre medias).
    @SuppressLint("MissingPermission")
    private fun publicar(id: Int, n: Notification): Boolean {
        if (!AvisoTurno.puedeNotificar(context)) return false
        crearCanales()
        return runCatching { NotificationManagerCompat.from(context).notify(id, n) }.isSuccess
    }

    private fun mostrarProgreso(p: ProgresoSync, apps: Int) {
        publicar(ID_PROGRESO, NotificacionesSync.progreso(context, p, apps))
    }

    private fun quitarProgreso() {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_PROGRESO) }
    }

    /** La app de un empleado se conectó o se desconectó: la misma notificación de esa app cambia en su sitio. */
    private fun avisarConexion(empleadoId: Long, conectado: Boolean) {
        val n = rafagas.contar("conexion:$empleadoId:$conectado", System.currentTimeMillis())
        val aviso = NotificacionesSync.conexion(context, nombreDe(empleadoId), conectado, n)
        if (publicar(idConexion(empleadoId), aviso)) actualizarResumen()
    }

    /** Terminó una sincronización: ocupa el sitio de la barra. [nombre] = el empleado (null = esta app es la secundaria). */
    private fun avisarSincronizada(nombre: String?, clave: String) {
        val n = rafagas.contar(clave, System.currentTimeMillis())
        if (publicar(ID_PROGRESO, NotificacionesSync.sincronizada(context, nombre, n))) resultadoEnLaBarra = true
    }

    /** La barra de esta secundaria se cortó sin terminar: advertencia que se queda, con «Reintentar». */
    private fun avisarCortada() {
        CanalesAvisos.crear(context)
        publicar(ID_PROGRESO, NotificacionesSync.cortada(context))
    }

    /** Con dos o más avisos de conexión a la vista, un resumen agrupado con el contador (Notificacion.md §2.1). */
    @SuppressLint("MissingPermission")
    private fun actualizarResumen() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val hijos = runCatching { nm.activeNotifications.toList() }.getOrDefault(emptyList())
            .map { it.notification }
            .filter { it.group == GRUPO_SYNC && it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        if (hijos.size < 2) {
            runCatching { NotificationManagerCompat.from(context).cancel(ID_RESUMEN) }
            return
        }
        val lineas = hijos.mapNotNull { it.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() }
        val persistente = hijos.any { it.timeoutAfter == 0L }
        publicar(ID_RESUMEN, NotificacionesSync.resumen(context, lineas, persistente))
    }

    /** Los canales: el sonido queda congelado en el canal, por eso hay uno por evento. */
    private fun crearCanales() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CANAL_PROGRESO, "Sincronización", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Barra de avance mientras se sincroniza con las apps vinculadas."
                setShowBadge(false); enableVibration(false); setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_CONECTADA, "App conectada", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso sonoro cuando una app vinculada se conecta."
                setShowBadge(false); enableVibration(false); setSound(sonido(R.raw.conectada), null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_DESCONECTADA, "App desconectada", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso sonoro cuando una app vinculada se desconecta."
                setShowBadge(false); enableVibration(false); setSound(sonido(R.raw.desconectada), null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_SINCRONIZADA, "Sincronización terminada", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso sonoro cuando termina una sincronización."
                setShowBadge(false); enableVibration(false); setSound(sonido(R.raw.sincronizada), null)
            },
        )
    }

    /** El archivo de sonido del canal (`res/raw`). */
    private fun sonido(id: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$id")

    companion object {
        const val CANAL_PROGRESO = "sync_progreso"
        const val CANAL_CONECTADA = "sync_conectada"
        const val CANAL_DESCONECTADA = "sync_desconectada"
        const val CANAL_SINCRONIZADA = "sync_terminada"

        const val ID_PROGRESO = 30
        /** Resumen del grupo de conexiones. (31–33 eran los ID fijos de antes: ya no se usan.) */
        const val ID_RESUMEN = 34
        private const val ID_CONEXION_BASE = 3_100

        const val GRUPO_SYNC = "cu.shalev.app.sync"

        /** Margen para que el resultado ocupe el sitio de la barra antes de darla por cortada. */
        const val GRACIA_MS = 1_500L

        /** Una notificación por app vinculada (cambia en su sitio de conectada a desconectada). */
        fun idConexion(empleadoId: Long): Int = ID_CONEXION_BASE + Math.floorMod(empleadoId, 1_000L).toInt()
    }
}

/** Las notificaciones de la sincronización, separadas para comprobarlas en Robolectric. */
internal object NotificacionesSync {
    const val TITULO = "Shalev"
    const val CORTADA = "La sincronización se cortó"
    const val CORTADA_DETALLE = "No terminó de recibir los datos. Comprueba la wifi del local y toca Reintentar."
    const val REINTENTAR = "Reintentar"

    fun textoConexion(nombre: String?, conectado: Boolean, repeticiones: Int): String {
        val quien = nombre ?: "un empleado"
        val texto = if (conectado) "La app de $quien se conectó" else "La app de $quien se desconectó"
        return ReglasNotificacion.conRafaga(texto, repeticiones)
    }

    fun textoSincronizada(nombre: String?, repeticiones: Int): String = ReglasNotificacion.conRafaga(
        if (nombre != null) "Sincronización con $nombre terminada" else "Sincronización terminada", repeticiones,
    )

    fun textoFase(p: ProgresoSync): String = when {
        !p.conTotal -> when (p.fase) {
            FaseSync.RECIBIENDO -> "Recibiendo el catálogo…"
            FaseSync.ENVIANDO -> "Enviando tus datos…"
            FaseSync.APLICANDO -> "Guardando…"
        }
        else -> ReglasNotificacion.porcentaje((p.fraccion * 100).toInt())
    }

    private fun base(context: Context, canal: String, tipo: TipoAviso, texto: String) =
        NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_stat_shalev)
            .setColor(ReglasNotificacion.color(tipo))
            .setContentTitle(TITULO)
            .setContentText(texto)
            .setPriority(ReglasNotificacion.prioridad(tipo))
            .setCategory(ReglasNotificacion.categoria(tipo))
            .setContentIntent(NotificadorOperaciones.abrirApp(context))
            .apply { ReglasNotificacion.expira(tipo)?.let { setTimeoutAfter(it) } }

    fun progreso(context: Context, p: ProgresoSync, apps: Int): Notification {
        val total = p.total.toInt().coerceAtLeast(1)
        return base(context, AvisoSync.CANAL_PROGRESO, TipoAviso.CARGA, textoFase(p))
            .setContentTitle(if (apps > 1) "Sincronizando $apps apps" else "Sincronizando")
            .setProgress(total, p.actual.toInt().coerceIn(0, total), !p.conTotal)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    fun conexion(context: Context, nombre: String?, conectado: Boolean, repeticiones: Int): Notification {
        val tipo = if (conectado) TipoAviso.INFO else TipoAviso.ADVERTENCIA
        val canal = if (conectado) AvisoSync.CANAL_CONECTADA else AvisoSync.CANAL_DESCONECTADA
        return base(context, canal, tipo, textoConexion(nombre, conectado, repeticiones))
            .setAutoCancel(true)
            // Ráfaga: la repetición actualiza el mismo aviso sin volver a sonar.
            .setOnlyAlertOnce(repeticiones > 1)
            .setGroup(AvisoSync.GRUPO_SYNC)
            .build()
    }

    fun sincronizada(context: Context, nombre: String?, repeticiones: Int): Notification =
        base(context, AvisoSync.CANAL_SINCRONIZADA, TipoAviso.EXITO, textoSincronizada(nombre, repeticiones))
            .setAutoCancel(true)
            .setOnlyAlertOnce(repeticiones > 1)
            .build()

    fun cortada(context: Context): Notification =
        base(context, PlanOperacion.CANAL_PROBLEMAS, TipoAviso.ADVERTENCIA, CORTADA)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$CORTADA. $CORTADA_DETALLE"))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_stat_shalev, REINTENTAR,
                AccionNotificacionReceiver.intent(context, AccionNotificacionReceiver.REINTENTAR_SYNC),
            )
            .build()

    fun resumen(context: Context, lineas: List<String>, persistente: Boolean): Notification {
        val estilo = NotificationCompat.InboxStyle().setSummaryText(ReglasNotificacion.resumen(lineas.size))
        lineas.take(5).forEach { estilo.addLine(it) }
        // Con alguna desconexión dentro, el resumen tampoco se va solo (su tipo manda la permanencia).
        val tipo = if (persistente) TipoAviso.ADVERTENCIA else TipoAviso.INFO
        return base(context, AvisoSync.CANAL_CONECTADA, tipo, ReglasNotificacion.resumen(lineas.size))
            .setStyle(estilo)
            .setNumber(lineas.size)
            .setGroup(AvisoSync.GRUPO_SYNC)
            .setGroupSummary(true)
            // El resumen no suena: ya sonó el aviso que lo provocó.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
    }
}
