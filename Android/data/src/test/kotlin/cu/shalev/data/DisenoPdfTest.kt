package cu.shalev.data

import cu.shalev.data.export.DisenoPdf
import cu.shalev.domain.service.TablaExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.25.1 (E2): maquetación del PDF (orientación, anchos mínimos y alineación). */
class DisenoPdfTest {
    /** Medida aproximada: 5 puntos por carácter (6 en negrita). */
    private val medir: (String, Boolean) -> Float = { s, negrita -> s.length * if (negrita) 6f else 5f }

    private val ventas = TablaExport(
        titulo = "Ventas",
        columnas = listOf("Fecha", "Vendedor", "Pago", "Productos", "Unidades", "Importe", "Costo", "Ganancia", "Estado"),
        filas = listOf(
            listOf("02/10/2026 08:30", "Ana Pérez", "Efectivo", "Refresco de cola ×2, Pan con jamón ×1, Galletas de chocolate ×3", "6", "1,440 CUP", "900 CUP", "540 CUP", "Válida"),
            listOf("02/10/2026 09:10", "Luis Gómez Ruiz", "Transferencia", "Pizza ×1", "1", "12,500 CUP", "8,000 CUP", "4,500 CUP", "Anulada"),
        ),
    )

    @Test fun masDeCincoColumnasVaHorizontal() {
        assertTrue(DisenoPdf.horizontal(ventas))
        assertFalse(DisenoPdf.horizontal(ventas.copy(columnas = ventas.columnas.take(5), filas = ventas.filas.map { it.take(5) })))
    }

    @Test fun fechasEImportesNoSeCortanYElTextoLibreAbsorbe() {
        val util = 842f - 72f
        val a = DisenoPdf.anchos(ventas, util, medir)
        assertEquals(util, a.sum(), 0.5f)
        val fecha = medir("02/10/2026 09:10", false) + DisenoPdf.RELLENO
        val importe = medir("12,500 CUP", false) + DisenoPdf.RELLENO
        assertTrue(a[0] >= fecha - 0.01f)
        assertTrue(a[5] >= importe - 0.01f)
        assertTrue(a[6] >= medir("8,000 CUP", false) + DisenoPdf.RELLENO - 0.01f)
        assertTrue(a.all { it >= DisenoPdf.MIN_LIBRE - 0.01f || it >= medir("Pago", true) })
        assertTrue(DisenoPdf.compacta(ventas, 0) && DisenoPdf.compacta(ventas, 5) && !DisenoPdf.compacta(ventas, 3))
    }

    @Test fun importesYCantidadesALaDerecha() {
        assertTrue(DisenoPdf.derecha(ventas, 4))
        assertTrue(DisenoPdf.derecha(ventas, 5))
        assertFalse(DisenoPdf.derecha(ventas, 0))
        assertFalse(DisenoPdf.derecha(ventas, 3))
        val mov = TablaExport("M", listOf("Cambio"), listOf(listOf("-0.150 kg"), listOf("+2")))
        assertTrue(DisenoPdf.derecha(mov, 0))
    }

    @Test fun codigosYCarneCompactos() {
        val t = TablaExport("T", listOf("Código", "CI", "Nº"), listOf(listOf("7501055363056", "••••••••345", "MM10040FEJ987")))
        assertTrue((0..2).all { DisenoPdf.compacta(t, it) })
        assertEquals(0, DisenoPdf.anchos(t.copy(columnas = emptyList(), filas = emptyList()), 500f, medir).size)
    }

    /** 0.26.0 (§5): importes, cantidades y fechas en seminegrita; también el Valor de las tablas Dato/Valor. */
    @Test fun codigoDeBarrasNoDestacado() {
        val inv = TablaExport("Inventario", listOf("Nombre", "Código", "Cantidad"),
            listOf(listOf("Galletas", "7501031311309", "40"), listOf("Café", "", "8"), listOf("Jabón", "85000012", "15")))
        assertFalse(DisenoPdf.destacada(TablaExport("T", listOf("Teléfono"), listOf(listOf("+5352345678"))), 0))
        assertFalse(DisenoPdf.destacada(inv, 1))
        assertTrue(DisenoPdf.destacada(inv, 2))
    }

    @Test fun destacadasImportesFechasYValor() {
        assertTrue(DisenoPdf.destacada(ventas, 0))
        assertTrue(DisenoPdf.destacada(ventas, 5))
        assertFalse(DisenoPdf.destacada(ventas, 3))
        val resumen = TablaExport("R", listOf("Dato", "Valor"), listOf(listOf("Apertura", "02/10/2026 08:00 · Ana Pérez"), listOf("Ventas", "3")))
        assertTrue(DisenoPdf.destacada(resumen, 1))
        assertFalse(DisenoPdf.destacada(resumen, 0))
    }
}
