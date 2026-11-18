package cu.shalev.data.export

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.result.appCatching
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.repository.ExportadorDocumentos
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablaExport
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class ExportadorDocumentosImpl @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val perfilRepo: PerfilRepository? = null,
) : ExportadorDocumentos {

    override suspend fun exportar(tablas: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> =
        withContext(io) {
            val perfil = perfilRepo?.perfil?.first()
            val negocio = perfil?.nombreNegocio?.takeIf { it.isNotBlank() }
                ?: perfil?.let { "${it.nombre} ${it.apellidos}".trim() }?.takeIf { it.isNotBlank() }
                ?: "Shalev"
            val tablasConNegocio = tablas.map {
                if (it.nombreNegocio.isNullOrBlank()) it.copy(nombreNegocio = negocio) else it
            }
            appCatching(onError = { if (it is java.io.IOException) AppError.Almacenamiento else AppError.Desconocido(it) }) {
                when (formato) {
                    FormatoExport.XLSX -> XlsxWriter.escribir(tablasConNegocio, destino)
                    FormatoExport.PDF -> PdfWriter.escribir(tablasConNegocio, destino)
                }
            }
        }
}
