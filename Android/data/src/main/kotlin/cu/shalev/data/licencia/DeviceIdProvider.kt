package cu.shalev.data.licencia

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import cu.shalev.licencia.contract.DEVICE_ID_PREFIX
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identidad del equipo para las comprobaciones de licencia. La implementación real es [DeviceIdProvider];
 * la interfaz permite sustituirla en pruebas JVM sin necesitar un `Context` de Android.
 */
interface ProveedorDeviceId {
    val deviceId: String
}

/**
 * deviceId = "SPVI:" + ANDROID_ID (decisión D2.1). ANDROID_ID es por app+firma+usuario desde API 26:
 * no requiere permisos y no identifica al usuario entre apps.
 */
@Singleton
open class DeviceIdProvider @Inject constructor(@ApplicationContext private val context: Context) : ProveedorDeviceId {
    @get:SuppressLint("HardwareIds")
    override val deviceId: String by lazy {
        deDispositivo(Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
    }

    companion object {
        private const val SIN_ID = "0000000000000000"
        private const val MAX_LARGO = 128

        /**
         * P19: contrato GL v1 → `deviceId` de 8 a 128 caracteres `[A-Za-z0-9:_-]`. Solo se conservan `a-z` y `0-9`
         * ASCII (antes `isLetterOrDigit`, que admite letras Unicode) y se limita la longitud. Para un ANDROID_ID real
         * (16 hex) el resultado es idéntico al de versiones anteriores: no invalida licencias ya emitidas.
         */
        fun deDispositivo(androidId: String?): String {
            val limpio = androidId.orEmpty().lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
            return (DEVICE_ID_PREFIX + limpio.ifEmpty { SIN_ID }).take(MAX_LARGO)
        }
    }
}
