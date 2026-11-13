package cu.shalev.data.licencia.prueba

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.data.local.SecureDataStore
import cu.shalev.domain.model.InfoRegistroPrueba
import cu.shalev.domain.repository.PruebaRepository
import cu.shalev.licencia.LicenseManager
import cu.shalev.licencia.prueba.CifradoPrueba
import cu.shalev.licencia.prueba.CopiasCombinadas
import cu.shalev.licencia.prueba.DetectorRetroceso
import cu.shalev.licencia.prueba.LecturaCopia
import cu.shalev.licencia.prueba.MarcaReloj
import cu.shalev.licencia.prueba.Ofuscado
import cu.shalev.licencia.prueba.PngRegistro
import cu.shalev.licencia.prueba.RegistroExterno
import cu.shalev.licencia.prueba.RegistroPrueba
import cu.shalev.licencia.prueba.combinarCopias
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Dónde vive cada copia del registro de la prueba. */
enum class CopiaPrueba { INTERNA, IMAGENES, DESCARGAS, DOCUMENTOS }

/**
 * 0.26.0 (P74): copias cifradas de la fecha de inicio de la prueba que sobreviven a desinstalar Shalev.
 *  - INTERNA: filesDir (se borra al desinstalar).
 *  - IMAGENES: `Pictures/Shalev/sys_<huella>.png` (PNG con el blob en un fragmento privado). En Android 10+ es la única
 *    copia que una instalación NUEVA puede volver a leer, y solo con el permiso de fotos (READ_MEDIA_IMAGES en 13+,
 *    READ_EXTERNAL_STORAGE en 10–12). Si el usuario lo niega (o en Android 14 elige «algunas fotos»), no se ve.
 *  - DESCARGAS / DOCUMENTOS: `.sys_<huella>.bin`. En Android 8–9 se leen siempre (con el permiso de almacenamiento);
 *    en 10+ Android no deja a una instalación nueva leer las de la anterior: sirven dentro de la misma instalación.
 * Todo en [io]. Nunca lanza: una copia inaccesible no cuenta, una que no descifra se ignora y se reescribe (no bloquea).
 */
@Singleton
class RegistroPruebaAndroid @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ds: SecureDataStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : RegistroExterno, PruebaRepository {

    private val cr: ContentResolver get() = context.contentResolver
    private val cifrado: CifradoPrueba by lazy { CifradoPrueba(androidId()) }
    private val _info = MutableStateFlow<InfoRegistroPrueba?>(null)
    override val info: StateFlow<InfoRegistroPrueba?> = _info.asStateFlow()
    /** Se fija en la primera lectura del proceso: ¿faltaba la copia interna? */
    @Volatile private var instalacionNueva: Boolean? = null

    override val permisoLectura: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else if (Build.VERSION.SDK_INT >= 29) Manifest.permission.READ_EXTERNAL_STORAGE
        else Manifest.permission.WRITE_EXTERNAL_STORAGE // en 8–9 incluye la lectura

    override fun lecturaPermitida(): Boolean = concedido(permisoLectura)

    override suspend fun permisoPedido(): Boolean = ds.get(K_PEDIDO) != null
    override suspend fun marcarPermisoPedido() = ds.put(K_PEDIDO, "1")

    override suspend fun sincronizar(trialStart: Long?, lastSeen: Long?, trialDays: Int): CopiasCombinadas<*> = withContext(io) {
        val lecturas = leerTodas()
        val comb = combinarCopias(lecturas.mapValues { (_, v) -> v.map { it.second } })
        if (instalacionNueva == null) instalacionNueva = lecturas[CopiaPrueba.INTERNA].orEmpty().none { it.second is LecturaCopia.Valida }
        val inicio = listOfNotNull(comb.firstInstall, trialStart).minOrNull()
        if (inicio != null) {
            val ultima = listOfNotNull(comb.lastSeen, lastSeen).maxOrNull()
            val objetivo = RegistroPrueba(firstInstall = inicio, trialDays = trialDays, lastSeen = ultima)
            for (copia in CopiaPrueba.entries) {
                val actual = lecturas[copia] ?: continue // inaccesible (sin permiso en Android 8–9)
                if (actual.none { (_, l) -> l is LecturaCopia.Valida && alDia(l.registro, objetivo) }) escribir(copia, objetivo, actual.map { it.first })
            }
        }
        _info.value = InfoRegistroPrueba(
            instalacionNueva = instalacionNueva == true,
            primeraInstalacion = comb.primeraInstalacion,
            copiasDanadas = lecturas.values.sumOf { l -> l.count { it.second == LecturaCopia.Ilegible } },
        )
        comb
    }

    /** Se reescribe solo si cambia el inicio o la última fecha vista avanzó más de una hora (pocas escrituras). */
    private fun alDia(r: RegistroPrueba, o: RegistroPrueba): Boolean =
        r.firstInstall == o.firstInstall && (o.lastSeen ?: 0L) - (r.lastSeen ?: 0L) < UNA_HORA

    // ------------------------------------------------------------------------------------------------ lectura

    /** Por copia: (uri o null, lectura). Copia ausente del mapa = no accesible (no se puede leer ni escribir). */
    private fun leerTodas(): Map<CopiaPrueba, List<Pair<Uri?, LecturaCopia>>> {
        val m = LinkedHashMap<CopiaPrueba, List<Pair<Uri?, LecturaCopia>>>()
        m[CopiaPrueba.INTERNA] = listOf(null to leerBin(runCatching { interna().takeIf(File::exists)?.readBytes() }.getOrNull(), interna().exists()))
        if (Build.VERSION.SDK_INT >= 29) {
            m[CopiaPrueba.IMAGENES] = consultar(imagenes(), RUTA_IMAGENES, Ofuscado.nombreImagen()) { PngRegistro.extraer(it) }
            m[CopiaPrueba.DESCARGAS] = consultar(descargas(), Environment.DIRECTORY_DOWNLOADS, Ofuscado.nombreBin()) { it }
            m[CopiaPrueba.DOCUMENTOS] = consultar(archivos(), Environment.DIRECTORY_DOCUMENTS, Ofuscado.nombreBin()) { it }
        } else if (concedido(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            m[CopiaPrueba.IMAGENES] = listOf(null to leerArchivo(legado(CopiaPrueba.IMAGENES)) { PngRegistro.extraer(it) })
            m[CopiaPrueba.DESCARGAS] = listOf(null to leerArchivo(legado(CopiaPrueba.DESCARGAS)) { it })
            m[CopiaPrueba.DOCUMENTOS] = listOf(null to leerArchivo(legado(CopiaPrueba.DOCUMENTOS)) { it })
        }
        return m
    }

    private fun leerBin(blob: ByteArray?, existe: Boolean): LecturaCopia = when {
        !existe -> LecturaCopia.Ausente
        else -> cifrado.descifrar(blob)?.let(LecturaCopia::Valida) ?: LecturaCopia.Ilegible
    }

    private fun leerArchivo(f: File, blob: (ByteArray) -> ByteArray?): LecturaCopia =
        if (!f.exists()) LecturaCopia.Ausente
        else leerBin(runCatching { f.readBytes() }.getOrNull()?.let(blob), true)

    /**
     * Todas las entradas visibles con ese nombre (también «nombre (1).ext», que crea Android si una instalación anterior
     * dejó el archivo y esta no puede verlo). Sin el permiso solo se ven las de esta instalación.
     */
    private fun consultar(coleccion: Uri, ruta: String, nombre: String, blob: (ByteArray) -> ByteArray?): List<Pair<Uri?, LecturaCopia>> = runCatching {
        val base = nombre.substringBeforeLast('.')
        val ext = nombre.substringAfterLast('.')
        val res = ArrayList<Pair<Uri?, LecturaCopia>>()
        cr.query(
            coleccion, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$ruta%", "$base%.$ext"), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(coleccion, c.getLong(0))
                val datos = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                res += uri to leerBin(datos?.let(blob), true)
            }
        }
        res.ifEmpty { listOf(null to LecturaCopia.Ausente) }
    }.getOrDefault(listOf(null to LecturaCopia.Ausente))

    // ------------------------------------------------------------------------------------------------ escritura

    private fun escribir(copia: CopiaPrueba, r: RegistroPrueba, uris: List<Uri?>) {
        val blob = cifrado.cifrar(r)
        val datos = if (copia == CopiaPrueba.IMAGENES) PngRegistro.crear(blob) else blob
        runCatching {
            when {
                copia == CopiaPrueba.INTERNA -> atomico(interna(), datos)
                Build.VERSION.SDK_INT >= 29 -> escribirMediaStore(copia, datos, uris.filterNotNull())
                else -> legado(copia).let { f -> f.parentFile?.mkdirs(); atomico(f, datos) }
            }
        }
    }

    /** Primero sobre una entrada propia (las de otra instalación no dejan escribir); si no hay, una nueva. */
    private fun escribirMediaStore(copia: CopiaPrueba, datos: ByteArray, existentes: List<Uri>) {
        for (uri in existentes) {
            val ok = runCatching { cr.openOutputStream(uri, "wt")?.use { it.write(datos) } != null }.getOrDefault(false)
            if (ok) return
        }
        val (coleccion, ruta, nombre, mime) = when (copia) {
            CopiaPrueba.IMAGENES -> Destino(imagenes(), RUTA_IMAGENES, Ofuscado.nombreImagen(), "image/png")
            CopiaPrueba.DESCARGAS -> Destino(descargas(), Environment.DIRECTORY_DOWNLOADS, Ofuscado.nombreBin(), MIME_BIN)
            else -> Destino(archivos(), Environment.DIRECTORY_DOCUMENTS, Ofuscado.nombreBin(), MIME_BIN)
        }
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, ruta)
        }
        val uri = cr.insert(coleccion, v) ?: return
        val ok = runCatching { cr.openOutputStream(uri, "wt")?.use { it.write(datos) } != null }.getOrDefault(false)
        if (!ok) runCatching { cr.delete(uri, null, null) }
    }

    private data class Destino(val coleccion: Uri, val ruta: String, val nombre: String, val mime: String)

    private fun atomico(f: File, datos: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(datos)
        if (!tmp.renameTo(f)) { f.writeBytes(datos); tmp.delete() }
    }

    // ------------------------------------------------------------------------------------------------ rutas

    private fun interna() = File(context.filesDir, Ofuscado.nombreInterno())

    @Suppress("DEPRECATION")
    private fun legado(copia: CopiaPrueba): File = when (copia) {
        CopiaPrueba.IMAGENES -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Shalev/" + Ofuscado.nombreImagen())
        CopiaPrueba.DESCARGAS -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Ofuscado.nombreBin())
        else -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), Ofuscado.nombreBin())
    }

    @SuppressLint("NewApi") private fun imagenes(): Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    @SuppressLint("NewApi") private fun descargas(): Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    @SuppressLint("NewApi") private fun archivos(): Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun concedido(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("HardwareIds")
    private fun androidId(): String = Settings.Secure.getString(cr, Settings.Secure.ANDROID_ID).orEmpty()

    private companion object {
        const val K_PEDIDO = "prueba.permiso_pedido"
        const val RUTA_IMAGENES = "Pictures/Shalev"
        const val MIME_BIN = "application/octet-stream"
        const val UNA_HORA = 3_600_000L
    }
}

/**
 * 0.26.0 (P74): reloj monótono. Guarda (fecha, `elapsedRealtime`, nº de arranque) y en el MISMO arranque comprueba que
 * la fecha avanzó al menos lo que pasó de verdad (con la tolerancia de [LicenseManager.CLOCK_TOLERANCE]). Si se detecta
 * un retroceso, la marca anterior se conserva: sigue bloqueado hasta corregir la fecha.
 */
@Singleton
class DetectorRelojAndroid @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ds: SecureDataStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DetectorRetroceso {
    override suspend fun retrocedio(ahoraMs: Long): Boolean = withContext(io) {
        val arranque = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
        val ahora = MarcaReloj(ahoraMs, SystemClock.elapsedRealtime(), arranque)
        val previa = MarcaReloj.decodificar(ds.get(K_MARCA))
        val atras = previa != null && ahora.retrocedioDesde(previa, LicenseManager.CLOCK_TOLERANCE.toMillis())
        if (!atras) ds.put(K_MARCA, ahora.codificar())
        atras
    }

    private companion object { const val K_MARCA = "lic.marca_reloj" }
}
