package cu.shalev.app

import cu.shalev.licencia.contract.CodigoLicencia

/**
 * Instalador de clave maestra en compilaciones de depuración (debug).
 *
 * El literal solo viaja en el APK de depuración y no se compila en release (S5).
 */
object DebugMasterKey {
    const val MAESTRA = "GLMK-PERM-MAXS-9999"

    @JvmStatic
    fun instalar() {
        val normalizada = CodigoLicencia.normalizar(MAESTRA)
        CodigoLicencia.validadorMaestra = { it == normalizada }
    }
}
