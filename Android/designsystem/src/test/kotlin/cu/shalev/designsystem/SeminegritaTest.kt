package cu.shalev.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cu.shalev.designsystem.theme.ShalevTextos
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.26.0 (P73 §5): los datos van en seminegrita (600) sin cambiar tamaño ni color (el contraste no cambia). */
class SeminegritaTest {
    @Test fun pesoDeLosDatos() {
        assertEquals(FontWeight.SemiBold, ShalevTextos.PESO_DATO)
        assertEquals(600, ShalevTextos.PESO_DATO.weight)
    }

    @Test fun datoEnSoloCambiaElPeso() {
        val base = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
        val dato = ShalevTextos.datoEn(base)
        assertEquals(FontWeight.SemiBold, dato.fontWeight)
        assertEquals(base.fontSize, dato.fontSize)
        assertEquals(base.color, dato.color)
        assertEquals(13.sp, ShalevTextos.datoEn(TextStyle(fontSize = 13.sp)).fontSize)
    }

    /** 0.28.0: `resaltar` marca cada ocurrencia de los datos en seminegrita sin tocar el resto. */
    @Test fun resaltarMarcaCadaOcurrencia() {
        val r = ShalevTextos.resaltar("3 ventas · 1 250.00 CUP · 1 250.00 CUP", "1 250.00 CUP")
        assertEquals("3 ventas · 1 250.00 CUP · 1 250.00 CUP", r.text)
        assertEquals(2, r.spanStyles.size)
        r.spanStyles.forEach { assertEquals(FontWeight.SemiBold, it.item.fontWeight) }
        assertEquals(11, r.spanStyles[0].start)
        assertEquals(23, r.spanStyles[0].end)
        assertEquals(26, r.spanStyles[1].start)
        assertEquals(38, r.spanStyles[1].end)
    }

    @Test fun resaltarIgnoraDatosEnBlancoYSinCoincidencia() {
        val r = ShalevTextos.resaltar("Total: 500.00 CUP", "", "  ", "9 999.00 CUP")
        assertEquals("Total: 500.00 CUP", r.text)
        assertEquals(0, r.spanStyles.size)
    }

    /** 0.28.0: `datoTexto` deja un dato solo en seminegrita (huecos que piden AnnotatedString). */
    @Test fun datoTextoMarcaTodoElDato() {
        val r = ShalevTextos.datoTexto("1 250.00 CUP")
        assertEquals("1 250.00 CUP", r.text)
        assertEquals(1, r.spanStyles.size)
        assertEquals(FontWeight.SemiBold, r.spanStyles[0].item.fontWeight)
        assertEquals(0, r.spanStyles[0].start)
        assertEquals("1 250.00 CUP".length, r.spanStyles[0].end)
    }
}
