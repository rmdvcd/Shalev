package cu.shalev.app.actualizacion

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import cu.shalev.app.BuildConfig
import cu.shalev.core.util.aHex
import cu.shalev.domain.model.InfoApp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** 0.25.0: versión instalada y repositorio de GitHub (marcador `GITHUB_REPO`; vacío = consultas desactivadas). */
@Module
@InstallIn(SingletonComponent::class)
object ActualizacionModule {
    @Provides @Singleton
    fun infoApp(): InfoApp = InfoApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.GITHUB_REPO)
}

/** Motivo por el que un APK descargado no se instala (se borra y se avisa). */
enum class RechazoApk(val texto: String) {
    ILEGIBLE("El archivo descargado no es una actualización válida de Shalev."),
    OTRO_PAQUETE("El archivo no es Shalev."),
    VERSION_NO_MAYOR("El archivo no es una versión más nueva que la instalada."),
    OTRA_FIRMA("El archivo no está firmado por el mismo desarrollador. No se instala."),
}

/**
 * 0.25.0 (§6.3): comprueba e instala un APK de Shalev con PackageInstaller. Android SIEMPRE muestra «¿Actualizar Shalev?» y
 * el usuario toca Instalar (no hay instalación silenciosa); la primera vez pide además «Permitir de esta fuente».
 * Antes de abrir el instalador: paquete `cu.shalev.app`, versionCode mayor que el instalado y MISMO certificado de firma.
 */
@Singleton
class InstaladorApk @Inject constructor(@ApplicationContext private val context: Context) {

    /** «Permitir de esta fuente» activado para Shalev. */
    fun puedeInstalar(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Pantalla del sistema para activarlo. */
    fun intentPermiso(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** null = se puede instalar. */
    fun comprobar(apk: File): RechazoApk? {
        val pm = context.packageManager
        val nuevo = archivo(pm, apk) ?: return RechazoApk.ILEGIBLE
        if (nuevo.packageName != context.packageName) return RechazoApk.OTRO_PAQUETE
        val actual = runCatching { instalado(pm) }.getOrNull() ?: return RechazoApk.ILEGIBLE
        if (codigo(nuevo) <= codigo(actual)) return RechazoApk.VERSION_NO_MAYOR
        val a = huellas(actual)
        val n = huellas(nuevo)
        if (a.isEmpty() || n.isEmpty() || a != n) return RechazoApk.OTRA_FIRMA
        return null
    }

    /**
     * Abre el instalador del sistema. [alTerminar] recibe true si Android confirmó la sesión (el usuario tocó Instalar
     * y terminó bien) o false si se canceló o falló. La app se reinicia sola tras actualizarse.
     */
    fun instalar(apk: File, alTerminar: (Boolean) -> Unit = {}) {
        val instalador = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = instalador.createSession(params)
        try {
            instalador.openSession(id).use { s ->
                s.openWrite("shalev.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val accion = "${context.packageName}.INSTALACION.$id"
                registrarRespuesta(accion, alTerminar)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(context, id, Intent(accion).setPackage(context.packageName), flags)
                s.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            runCatching { instalador.abandonSession(id) }
            alTerminar(false)
        }
    }

    private fun registrarRespuesta(accion: String, alTerminar: (Boolean) -> Unit) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirmar = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT))
                        confirmar?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { c.startActivity(it) } }
                    }
                    PackageInstaller.STATUS_SUCCESS -> { runCatching { c.unregisterReceiver(this) }; alTerminar(true) }
                    else -> { runCatching { c.unregisterReceiver(this) }; alTerminar(false) }
                }
            }
        }
        ContextCompat.registerReceiver(context, receptor, IntentFilter(accion), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @Suppress("DEPRECATION")
    private fun archivo(pm: PackageManager, apk: File): PackageInfo? = runCatching {
        val f = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        pm.getPackageArchiveInfo(apk.absolutePath, f)?.also { info ->
            info.applicationInfo?.let { it.sourceDir = apk.absolutePath; it.publicSourceDir = apk.absolutePath }
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun instalado(pm: PackageManager): PackageInfo {
        val f = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return pm.getPackageInfo(context.packageName, f)
    }

    @Suppress("DEPRECATION")
    private fun codigo(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun huellas(p: PackageInfo): Set<String> {
        val firmas = if (Build.VERSION.SDK_INT >= 28) {
            val s = p.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else p.signatures
        return firmas.orEmpty().map { sha256(it.toByteArray()) }.toSet().let { todas ->
            // Con rotación de clave, basta con que la actual del instalado esté en el historial del nuevo.
            if (Build.VERSION.SDK_INT >= 28 && p.signingInfo?.hasMultipleSigners() == false) setOfNotNull(todas.lastOrNull()) else todas
        }
    }

    private fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).aHex()
}
