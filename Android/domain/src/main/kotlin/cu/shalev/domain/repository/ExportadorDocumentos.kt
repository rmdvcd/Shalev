package cu.shalev.domain.repository

import cu.shalev.core.result.AppResult
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablaExport
import java.io.OutputStream

/** Genera el archivo (PDF/XLSX) de una o varias tablas. Implementado en :data. */
interface ExportadorDocumentos {
    suspend fun exportar(tablas: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit>
}
