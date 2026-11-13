package cu.shalev.app

import android.app.Application
import cu.shalev.app.notificacion.AvisoSync
import cu.shalev.app.notificacion.AvisoTurno
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

    override fun onCreate() {
        super.onCreate()
        // 0.31.0 (mitigación 1, Opción A): la clave maestra de desarrollo `GLMK-PERM-MAXS-9999` solo vale en builds de
        // depuración. Se fija aquí, en el arranque y ANTES de tocar la licencia (los repositorios se construyen de
        // forma perezosa). En release BuildConfig.DEBUG es false y la comprobación queda como está: fail-closed.
        cu.shalev.licencia.ModoDesarrollo.esDebug = BuildConfig.DEBUG
        avisoTurno.iniciar(this)
        avisoSync.iniciar()
        arranqueSync.iniciar()
    }
}
