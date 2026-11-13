package cu.shalev.app

import cu.shalev.app.common.ArchivosApp
import cu.shalev.app.inventario.TarjetasAndroid
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.ConfiguracionInicial
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.Producto
import cu.shalev.domain.repository.ConfiguracionInicialRepository
import cu.shalev.domain.repository.ExportadorDocumentos
import cu.shalev.domain.repository.FotoRepository
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablaExport
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFotos : FotoRepository {
    var fallarImportar = false
    override suspend fun importar(origen: String): AppResult<String> =
        if (fallarImportar) AppResult.Err(AppError.FormatoInvalido("foto")) else AppResult.Ok("file:///fotos/importada.jpg")
    override suspend fun eliminar(uri: String) = Unit
    override suspend fun limpiarHuerfanas(enUso: Set<String>) = Unit
}

/** Archivos en una carpeta temporal real; los destinos SAF se simulan con memoria. */
class FakeArchivos : ArchivosApp {
    val dir: File = Files.createTempDirectory("shalev").toFile().apply { deleteOnExit() }
    val destinos = mutableMapOf<String, ByteArrayOutputStream>()
    var destinoDisponible = true
    override fun temporal(nombre: String): File = File(dir, nombre)
    override fun abrirDestino(uri: String): OutputStream? = if (destinoDisponible) ByteArrayOutputStream().also { destinos[uri] = it } else null
    val origenes = mutableMapOf<String, ByteArray>()
    /** Como el selector del sistema: lo «guardado» en una URI se puede volver a «abrir». */
    override fun abrirOrigen(uri: String): java.io.InputStream? = (origenes[uri] ?: destinos[uri]?.toByteArray())?.inputStream()
    fun poner(uri: String, bytes: ByteArray) { origenes[uri] = bytes }
    fun bytes(uri: String): ByteArray = (origenes[uri] ?: destinos.getValue(uri).toByteArray())
}

class FakeRenderer(private val archivos: FakeArchivos) : TarjetasAndroid(archivos, kotlinx.coroutines.Dispatchers.Unconfined) {
    val renderizados = mutableListOf<List<Producto>>()
    val comentarios = mutableListOf<String>()
    override suspend fun renderizar(productos: List<Producto>, comentario: String): List<File> {
        comentarios += comentario
        return renderizar(productos)
    }
    override suspend fun renderizar(productos: List<Producto>): List<File> {
        renderizados += productos
        return productos.chunked(6).mapIndexed { i, _ -> archivos.temporal("Shalev_productos_${i + 1}.png").apply { writeText("png") } }
    }
}

/** Prompt 14: tabla → PNG. Registra la tabla y crea una "imagen" por cada 25 filas. */
class FakeTabla(private val archivos: FakeArchivos) : cu.shalev.app.common.ImagenTablaAndroid(archivos, kotlinx.coroutines.Dispatchers.Unconfined) {
    val renderizadas = mutableListOf<TablaExport>()
    override suspend fun renderizar(tabla: TablaExport, nombreBase: String): List<File> {
        renderizadas += tabla
        return cu.shalev.app.common.DisenoTabla.paginas(tabla.filas).mapIndexed { i, _ -> archivos.temporal("${nombreBase}_${i + 1}.png").apply { writeText("png") } }
    }
}

/** Registra lo exportado y escribe algo en el destino para poder comprobarlo. */
class FakeExportador : ExportadorDocumentos {
    val llamadas = mutableListOf<Pair<List<TablaExport>, FormatoExport>>()
    var fallar = false
    override suspend fun exportar(tablas: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> {
        if (fallar) return AppResult.Err(AppError.Almacenamiento)
        llamadas += tablas to formato
        destino.write(formato.name.toByteArray())
        return AppResult.Ok(Unit)
    }
}

class ConfRepo(inicial: ConfiguracionInicial = ConfiguracionInicial()) : ConfiguracionInicialRepository {
    val state = MutableStateFlow(inicial)
    override val estado = state
    override suspend fun confirmar(paso: PasoConfiguracion) { state.value = state.value.copy(confirmados = state.value.confirmados + paso) }
    override suspend fun marcarCamaraSolicitada() { state.value = state.value.copy(camaraSolicitada = true) }
    // 0.31.0 (P9)
    override suspend fun marcarPermisosInicialesPedidos() { state.value = state.value.copy(permisosInicialesPedidos = true) }
}
