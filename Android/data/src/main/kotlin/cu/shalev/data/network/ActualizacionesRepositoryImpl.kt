package cu.shalev.data.network

import android.content.Context
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.data.sync.ApkLocal
import cu.shalev.domain.model.InfoActualizacion
import cu.shalev.domain.model.InfoApp
import cu.shalev.domain.repository.ActualizacionesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 0.25.0 (P70): Releases PÚBLICAS de GitHub del repositorio [InfoApp.repoGithub] (marcador vacío = no se consulta
 * nada). Solo https, sin datos del negocio ni identificadores: la petición es la misma para cualquier teléfono.
 *
 * - Versión: `api.github.com/repos/<repo>/releases/latest` → etiqueta, página, notas y el recurso `Shalev-*.apk` con su
 *   huella (`digest` «sha256:…» de la API o, si falta, el recurso `<apk>.sha256`). Sin huella no se ofrece instalar.
 * - Revocadas: recurso `revocadas.json` de la Release `revocaciones` (lo sube GL; ver ListaRevocaciones).
 * - Descarga reanudable a `cacheDir/actualizacion`, con comprobación de SHA-256; el APK verificado se copia para
 *   repartirlo a las secundarias ([ApkLocal]).
 */
@Singleton
class ActualizacionesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    private val info: InfoApp,
    private val apkLocal: ApkLocal,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ActualizacionesRepository {

    private val api = client
    private val descargas = client.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    override val repoConfigurado: Boolean get() = info.repoConfigurado

    override suspend fun ultimaVersion(): AppResult<InfoActualizacion?> = red {
        val texto = get(api, "https://api.github.com/repos/${info.repoGithub}/releases/latest") ?: return@red null
        val o = json.parseToJsonElement(texto).jsonObject
        val version = o.str("tag_name") ?: return@red null
        val assets = o["assets"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val apk = assets.firstOrNull { a -> a.str("name")?.let { it.startsWith("Shalev", ignoreCase = true) && it.endsWith(".apk") } == true }
        val sha = apk?.let { a ->
            a.str("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
                ?: assets.firstOrNull { it.str("name") == a.str("name") + ".sha256" }?.str("browser_download_url")
                    ?.let { url -> get(descargas, url)?.trim()?.split(Regex("\\s+"))?.firstOrNull() }
        }?.lowercase()?.takeIf { HEX64.matches(it) }
        InfoActualizacion(
            version = version.removePrefix("v").removePrefix("V"),
            pagina = o.str("html_url") ?: "https://github.com/${info.repoGithub}/releases",
            apkUrl = apk?.str("browser_download_url")?.takeIf { it.startsWith("https://") },
            sha256 = sha,
            bytes = apk?.get("size")?.jsonPrimitive?.longOrNull ?: 0,
            notas = o.str("body").orEmpty().take(NOTAS_MAX),
        )
    }

    override suspend fun listaRevocaciones(): AppResult<String> = red {
        get(api, "https://github.com/${info.repoGithub}/releases/download/revocaciones/revocadas.json").orEmpty()
    }

    override suspend fun descargar(info: InfoActualizacion, progreso: (Long, Long) -> Unit): AppResult<File> {
        val url = info.apkUrl ?: return AppResult.Err(AppError.NoEncontrado)
        val sha = info.sha256 ?: return AppResult.Err(AppError.Validacion("sha256", AppError.Regla.REQUERIDO))
        if (!repoConfigurado) return AppResult.Err(AppError.NoEncontrado)
        return withContext(io) {
            val dir = File(context.cacheDir, "actualizacion").apply { mkdirs() }
            val destino = File(dir, "Shalev-${info.version.filter { it.isLetterOrDigit() || it == '.' }}.apk")
            try {
                var hecho = if (destino.length() in 1 until (info.bytes.takeIf { it > 0 } ?: Long.MAX_VALUE)) destino.length() else 0L
                if (hecho == 0L) destino.delete()
                val req = Request.Builder().url(url).apply { if (hecho > 0) header("Range", "bytes=$hecho-") }.build()
                descargas.newCall(req).execute().use { r ->
                    if (r.code == 416) { hecho = 0; destino.delete(); throw IOException("rango") }
                    if (!r.isSuccessful) throw HttpError(r.code)
                    if (r.code != 206) { hecho = 0; destino.delete() } // el servidor no reanuda: desde el principio
                    val cuerpo = r.body ?: throw IOException("sin cuerpo")
                    val total = if (info.bytes > 0) info.bytes else hecho + cuerpo.contentLength().coerceAtLeast(0)
                    RandomAccessFile(destino, "rw").use { f ->
                        f.seek(hecho)
                        cuerpo.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                f.write(buf, 0, n)
                                hecho += n
                                if (hecho > MAX_APK) throw IOException("demasiado grande")
                                progreso(hecho, total)
                            }
                        }
                    }
                }
                if (!ApkLocal.sha256(destino).equals(sha, ignoreCase = true)) {
                    destino.delete()
                    return@withContext AppResult.Err(AppError.Validacion("sha256", AppError.Regla.FORMATO))
                }
                runCatching { apkLocal.guardar(destino, info.version, sha) } // para las secundarias (si es la principal)
                AppResult.Ok(destino)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Err(errorRed(e))
            }
        }
    }

    private fun errorRed(e: Exception): AppError = when (e) {
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> AppError.Red.Timeout
        is HttpError -> AppError.Red.Http(e.code)
        else -> AppError.Red.SinConexion
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    private suspend fun <T> red(bloque: suspend () -> T): AppResult<T> {
        if (!repoConfigurado) return AppResult.Err(AppError.NoEncontrado)
        return withContext(io) {
            try {
                AppResult.Ok(bloque())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Err(errorRed(e))
            }
        }
    }

    /** Texto de la respuesta; null si 404 (no hay Release / recurso). Otros códigos = error de red. */
    private fun get(c: OkHttpClient, url: String): String? =
        c.newCall(Request.Builder().url(url).build()).execute().use { r ->
            when {
                r.code == 404 -> null
                !r.isSuccessful -> throw HttpError(r.code)
                else -> {
                    val cuerpo = r.body ?: return null
                    if (cuerpo.contentLength() > MAX_TEXTO) throw IOException("respuesta demasiado grande")
                    cuerpo.string().also { if (it.length > MAX_TEXTO) throw IOException("respuesta demasiado grande") }
                }
            }
        }

    private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull

    private companion object {
        val HEX64 = Regex("[0-9a-f]{64}")
        const val NOTAS_MAX = 2_000
        const val MAX_TEXTO = 1_048_576L
        const val MAX_APK = 200L * 1024 * 1024
    }
}
