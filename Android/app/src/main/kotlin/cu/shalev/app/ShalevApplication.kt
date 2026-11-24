package cu.shalev.app

import android.app.Application
import cu.shalev.app.notificacion.AvisoSync
import cu.shalev.app.notificacion.AvisoTurno
import cu.shalev.app.notificacion.NotificadorOperaciones
import cu.shalev.data.sync.ArranqueSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShalevApplication : Application() {

    /** P29: aviso «Turno abierto» en la barra de estado mientras la app está en segundo plano. */
    @Inject lateinit var avisoTurno: AvisoTurno

    /** P37: servidor (principal con secundarias) o cliente automático (secundaria) mientras la app esté abierta. */
    @Inject lateinit var arranqueSync: ArranqueSync

    /** 0.32.0: barra de avance y sonidos de la sincronización. */
    @Inject lateinit var avisoSync: AvisoSync

    /** Barra de progreso de la actualización y de los respaldos en la barra de notificaciones. */
    @Inject lateinit var notificadorOperaciones: NotificadorOperaciones

    override fun onCreate() {
        super.onCreate()
        // 0.31.0 (mitigación 1, Opción A / S5): la clave maestra de desarrollo solo vale en builds de
        // depuración. Se fija aquí, en el arranque y ANTES de tocar la licencia (los repositorios se construyen de
        // forma perezosa). En release BuildConfig.DEBUG es false y la comprobación queda como está: fail-closed.
        cu.shalev.licencia.ModoDesarrollo.esDebug = BuildConfig.DEBUG
        if (BuildConfig.DEBUG) {
            runCatching {
                Class.forName("cu.shalev.app.DebugMasterKey").getMethod("instalar").invoke(null)
            }
        }
        // Fase 1.3: el directorio de confianza (bootstrap del APK + caché local anti-retroceso) debe quedar
        // efectivo antes de tocar la licencia. Sin él, `LicenseTrust` cae a las claves fijadas y el canje
        // comercial queda cerrado; con él, las claves y la base de canje se resuelven dinámicamente.
        // La descarga remota (GitHub Releases) nunca bloquea el arranque: si falla, se conserva el bootstrap.
        cu.shalev.licencia.TrustDirectoryManager.cacheFile =
            java.io.File(filesDir, "licencia/directorio.json")
        runCatching { cu.shalev.licencia.TrustDirectoryManager.loadEffective() }
        Thread {
            runCatching { cu.shalev.licencia.TrustDirectoryManager.updateFromNetwork() }
        }.apply { isDaemon = true; name = "directorio-confianza" }.start()
        avisoTurno.iniciar(this)
        avisoSync.iniciar()
        notificadorOperaciones.iniciar()
        arranqueSync.iniciar()
    }
}
