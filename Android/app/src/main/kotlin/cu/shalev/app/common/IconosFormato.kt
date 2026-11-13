package cu.shalev.app.common

import androidx.compose.ui.graphics.vector.ImageVector
import cu.shalev.app.inventario.FormatoSalida
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.domain.service.FormatoExport

/** 0.27.0 (T7): PDF y Excel llevan su icono en todas las hojas de exportar. Imagen y Tarjetas conservan los suyos. */
fun iconoFormato(f: FormatoExport): ImageVector = if (f == FormatoExport.PDF) ShalevIcons.Pdf else ShalevIcons.Excel

fun iconoFormato(f: FormatoSalida): ImageVector = when (f) {
    FormatoSalida.PDF -> ShalevIcons.Pdf
    FormatoSalida.EXCEL -> ShalevIcons.Excel
    FormatoSalida.IMAGEN -> ShalevIcons.Imagen
    FormatoSalida.TARJETAS -> ShalevIcons.Promocion
}
