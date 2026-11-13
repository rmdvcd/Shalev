package cu.shalev.app.venta

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/** Módulos del QR (true = negro). Pura: ZXing core es Java puro, sin cámara ni red. */
class MatrizQr(val lado: Int, private val celdas: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = celdas[y * lado + x]

    companion object {
        /** Corrección M (15 %) y zona blanca de 2 módulos: legible en pantallas con brillo bajo. */
        fun generar(contenido: String): MatrizQr {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            val m = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, hints)
            val lado = m.width
            return MatrizQr(lado, BooleanArray(lado * lado) { i -> m.get(i % lado, i / lado) })
        }
    }
}

/**
 * QR dibujado en un Canvas (sin Bitmap ni archivos): negro sobre blanco SIEMPRE, también en tema oscuro
 * (los lectores fallan con colores invertidos). [descripcion] = lo que TalkBack lee (sin el número completo).
 */
@Composable
fun QrCodigo(contenido: String, descripcion: String, modifier: Modifier = Modifier, tamano: Dp = ShalevSize.qr) {
    val matriz = remember(contenido) { MatrizQr.generar(contenido) }
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(ShalevRadius.md))
            .padding(ShalevSpacing.xs)
            .semantics { contentDescription = descripcion },
    ) {
        Canvas(Modifier.size(tamano)) {
            val celda = size.minDimension / matriz.lado
            drawRect(Color.White)
            for (y in 0 until matriz.lado) for (x in 0 until matriz.lado) {
                if (matriz[x, y]) drawRect(Color.Black, topLeft = Offset(x * celda, y * celda), size = Size(celda + 0.5f, celda + 0.5f))
            }
        }
    }
}
