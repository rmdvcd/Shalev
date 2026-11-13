package cu.shalev.app.notificacion

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cu.shalev.app.MainActivity
import cu.shalev.app.R
import cu.shalev.data.sync.ClienteSync
import cu.shalev.data.sync.ConfigSync
import cu.shalev.data.sync.ServidorSync
import cu.shalev.domain.model.ProgresoSync
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.repository.PrincipalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 0.32.0: avisos de la sincronización.
 *
 * - **Barra de avance** horizontal en una notificación propia (canal «Sincronización», sin sonido): mientras la
 *   app principal reparte el catálogo o la secundaria lo recibe.
 * - **Sonidos propios**: la app de un empleado se conectó, se desconectó, o terminó de sincronizar. Cada evento
 *   tiene su canal (el sonido queda congelado en el canal: por eso son tres) y su archivo en `res/raw`.
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var iniciado = false
    /** Nombres de los empleados, para los avisos («la app de Luis se conectó»). */
    private var nombres: Map<Long, String> = emptyMap()

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
                servidor.estado.map { e -> Avance(e.progresos.values.firstOrNull(), e.progresos.size) },
                cliente.progreso.map { Avance(it, 1) },
            ) { a, b -> if (a.progreso != null) a else b }
                .collectLatest { a -> if (a.progreso != null) mostrarProgreso(a.progreso, a.apps) else quitarProgreso() }
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
            servidor.sincronizadas.collect { id -> avisarSincronizada(nombreDe(id)) }
        }
        scope.launch {
            cliente.sincronizada.collect { avisarSincronizada(null) }
        }
    }

    private var conectadasAnteriores: Set<Long> = emptySet()

    /** El avance que se muestra: de qué app es y cuántas hay. */
    private data class Avance(val progreso: ProgresoSync?, val apps: Int)

    private fun nombreDe(id: Long): String? = nombres[id]

    // ---------------------------------------------------------------- avisos

    // Lanotifications se pueden perder en cualquier momento: el permiso se comprueba con
    // [AvisoTurno.puedeNotificar] y cada aviso va envuelto en runCatching (el usuario puede retirarlo entre medias).
    @SuppressLint("MissingPermission")
    private fun mostrarProgreso(p: ProgresoSync, apps: Int) {
        if (!AvisoTurno.puedeNotificar(context)) return
        crearCanales()
        val titulo = if (apps > 1) "Sincronizando $apps apps" else "Sincronizando"
        val total = p.total.toInt().coerceAtLeast(1)
        val valor = p.actual.toInt().coerceIn(0, total)
        val n = NotificationCompat.Builder(context, CANAL_PROGRESO)
            .setSmallIcon(R.drawable.ic_stat_shalev)
            .setColor(COLOR_MARCA)
            .setContentTitle(titulo)
            .setContentText(textoFase(p))
            .setProgress(total, valor, !p.conTotal)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(abrir())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_PROGRESO, n) }
    }

    private fun quitarProgreso() {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_PROGRESO) }
    }

    /** La app de un empleado se conectó o se desconectó. */
    @SuppressLint("MissingPermission")
    private fun avisarConexion(empleadoId: Long, conectado: Boolean) {
        if (!AvisoTurno.puedeNotificar(context)) return
        crearCanales()
        val nombre = nombreDe(empleadoId) ?: "un empleado"
        val texto = if (conectado) "La app de $nombre se conectó" else "La app de $nombre se desconectó"
        val n = NotificationCompat.Builder(context, if (conectado) CANAL_CONECTADA else CANAL_DESCONECTADA)
            .setSmallIcon(R.drawable.ic_stat_shalev)
            .setColor(COLOR_MARCA)
            .setContentTitle("Shalev")
            .setContentText(texto)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(abrir())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(if (conectado) ID_CONECTADA else ID_DESCONECTADA, n) }
    }

    /** Terminó una sincronización. [nombre] = el empleado (null = esta app es la secundaria). */
    @SuppressLint("MissingPermission")
    private fun avisarSincronizada(nombre: String?) {
        if (!AvisoTurno.puedeNotificar(context)) return
        crearCanales()
        val texto = if (nombre != null) "Sincronización con $nombre terminada" else "Sincronización terminada"
        val n = NotificationCompat.Builder(context, CANAL_SINCRONIZADA)
            .setSmallIcon(R.drawable.ic_stat_shalev)
            .setColor(COLOR_MARCA)
            .setContentTitle("Shalev")
            .setContentText(texto)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(abrir())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SINCRONIZADA, n) }
    }

    private fun textoFase(p: ProgresoSync): String = when {
        !p.conTotal -> when (p.fase) {
            cu.shalev.domain.model.FaseSync.RECIBIENDO -> "Recibiendo el catálogo…"
            cu.shalev.domain.model.FaseSync.ENVIANDO -> "Enviando tus datos…"
            cu.shalev.domain.model.FaseSync.APLICANDO -> "Guardando…"
        }
        else -> "${(p.fraccion * 100).toInt()} %"
    }

    private fun abrir() = android.app.PendingIntent.getActivity(
        context, 0,
        android.content.Intent(context, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

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
        const val ID_CONECTADA = 31
        const val ID_DESCONECTADA = 32
        const val ID_SINCRONIZADA = 33

        private const val COLOR_MARCA = 0xFF2B4FA3.toInt()
    }
}
