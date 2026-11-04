package cu.shalev.app.inicio

import cu.shalev.core.money.Cup
import cu.shalev.core.time.Dates
import cu.shalev.core.time.FormatoFecha
import cu.shalev.designsystem.theme.AlertTone
import cu.shalev.domain.model.ConteoAlertas
import cu.shalev.domain.model.GraficosPeriodo
import cu.shalev.domain.model.Granularidad
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Periodo
import cu.shalev.domain.model.Porcion
import cu.shalev.domain.model.PuntoSerie
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.Serie
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.Top3
import cu.shalev.domain.model.TopItem
import cu.shalev.domain.model.TopPersona
import cu.shalev.domain.model.Turno
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InicioLogicTest {
    private val zona = ZoneId.of("America/Havana")
    private val ahora = Instant.parse("2026-09-30T16:00:00Z")
    private fun lic(e: LicenseState) = Licencia(e, "SPVI:x", true, "ab")

    @Test fun bannerSigueLasReglasDelPromptMaestro() {
        assertEquals("Periodo de prueba restante: 5 días", bannerInicio(lic(LicenseState.Trial(5)), ahora, zona))
        val mensual = LicenseState.Active(TipoLicencia.MENSUAL, ahora.plusSeconds(86_400L * 20), "L1")
        assertTrue(bannerInicio(lic(mensual), ahora, zona)!!.contains("20 días"))
        val anual = LicenseState.Active(TipoLicencia.ANUAL, ahora.plusSeconds(86_400L * 200), "L2")
        assertTrue(bannerInicio(lic(anual), ahora, zona)!!.contains("meses"))
        assertNull("Perpetua oculta el banner", bannerInicio(lic(LicenseState.Perpetual("L3")), ahora, zona))
        assertNull(bannerInicio(null, ahora, zona))
    }

    @Test fun alertasEnOrdenYLasDeCeroOcultas() {
        val v = alertasVisibles(ConteoAlertas(stockBajo = 3, stockCritico = 0, insumoBajo = 0, insumoCritico = 2, proximosACaducar = 1))
        assertEquals(listOf(TipoAlerta.STOCK_BAJO, TipoAlerta.INSUMO_CRITICO, TipoAlerta.PROXIMO_A_CADUCAR), v.map { it.tipo })
        assertEquals(listOf(3, 2, 1), v.map { it.cantidad })
        assertTrue(alertasVisibles(ConteoAlertas()).isEmpty())
        // 0.20.0 (H6): las existencias negativas van primero y solo si hay alguna.
        val neg = alertasVisibles(ConteoAlertas(stockBajo = 1, sinExistencia = 2))
        assertEquals(TipoAlerta.SIN_EXISTENCIA, neg.first().tipo)
        assertEquals("Sin existencia", neg.first().tipo.etiqueta())
    }

    @Test fun coloresYEtiquetasDeLasAlertas() {
        assertEquals(AlertTone.StockBajo, TipoAlerta.STOCK_BAJO.tono())         // amarillo
        assertEquals(AlertTone.StockCritico, TipoAlerta.STOCK_CRITICO.tono())   // rojo
        assertEquals(AlertTone.InsumoBajo, TipoAlerta.INSUMO_BAJO.tono())       // petróleo (0.21.2)
        assertEquals(AlertTone.InsumoCritico, TipoAlerta.INSUMO_CRITICO.tono()) // naranja
        assertEquals(AlertTone.Caducidad, TipoAlerta.PROXIMO_A_CADUCAR.tono())  // púrpura claro
        assertEquals(AlertTone.SinExistencia, TipoAlerta.SIN_EXISTENCIA.tono()) // 0.21.1: relleno
        assertEquals(AlertTone.Revisar, TipoAlerta.NOMBRE_REPETIDO.tono())      // 0.24.0: color primario
        assertEquals("Stock inventario bajo", TipoAlerta.STOCK_BAJO.etiqueta())
    }

    // 0.31.0: pulsar una alerta ya no empuja una pantalla de Inventario (`destinoAlerta` se retiró): abre la pestaña
    // de Inventario y le pasa el nombre de la alerta en su estado; lo cubre InventarioViewModelTest.

    @Test fun pagoElectronicoResumido() {
        assertFalse(Perfil().pagoResumen().configurado)
        val p = Perfil(
            telefonos = listOf(Telefono(1, "+5351234567")), tarjetas = listOf(TarjetaBancaria(2, "9205129900001234")),
            pagoTelefonoId = 1, pagoTarjetaId = 2,
        )
        assertEquals("Tel. +53 5123 4567\nCuenta •••• 1234", p.pagoResumen().texto)
        assertEquals("Cuenta •••• 1234", PagoResumen(null, "•••• 1234").texto)
        assertEquals("+34600111222", formatoTelefono("+34600111222"))
    }

    @Test fun textoDePrecios() {
        assertEquals("Sin ajustes", textoPrecios(0, 0)) // P24: texto corto en la tarjeta
        assertEquals("Ningún ajuste activo", textoPrecios(0, 2))
        assertEquals("1 ajuste activo", textoPrecios(1, 2))
        assertEquals("3 ajustes activos", textoPrecios(3, 3))
    }

    @Test fun descripcionDelPeriodo() {
        val serie = Serie(Granularidad.HORA, emptyList())
        val rango = Periodo.Rango(ahora, ahora.plusSeconds(3600))
        assertEquals("Aún no hay turnos: se muestran las ventas de hoy", descripcionPeriodo(GraficosPeriodo(rango, serie, sinTurnos = true), OpcionPeriodo.TURNO, zona))
        val abierto = Turno(1, abiertoEn = Instant.parse("2026-09-30T12:30:00Z"))
        assertEquals("Turno actual, desde las 08:30", descripcionPeriodo(GraficosPeriodo(Periodo.DeTurno(1), serie, abierto), OpcionPeriodo.TURNO, zona))
        val cerrado = abierto.copy(cerradoEn = Instant.parse("2026-09-30T21:00:00Z"))
        assertEquals("Último turno: 30/09 08:30 a 17:00", descripcionPeriodo(GraficosPeriodo(Periodo.DeTurno(1), serie, cerrado), OpcionPeriodo.TURNO, zona))
        assertEquals("7 días", descripcionPeriodo(GraficosPeriodo(rango, serie), OpcionPeriodo.SEMANA, zona))
    }

    @Test fun etiquetasDelEjeXRespetanElFormatoDelDispositivo() {
        val anterior = Dates.formato
        try {
            Dates.formato = FormatoFecha.crear("yyyy/MM/dd", "MM-dd", "HH.mm", "'M'MM")
            val i = Instant.parse("2026-09-30T13:00:00Z")
            assertEquals("09.00", etiquetaPunto(i, Granularidad.HORA, zona))
            assertEquals("09-30", etiquetaPunto(i, Granularidad.DIA, zona))
            assertEquals("M09", etiquetaPunto(i, Granularidad.MES, zona))
        } finally {
            Dates.formato = anterior
        }
    }

    @Test fun donaAgrupaElRestoEnOtras() {
        val ps = (1..8).map { Porcion("C$it", (9 - it).toLong(), (9 - it) / 36.0) }
        val s = porcionesUi(ps, max = 6) { "$it u" }
        assertEquals(6, s.size)
        assertEquals("Otras", s.last().label)
        assertEquals(6.0 / 36, s.last().fraction, 1e-9) // 3 + 2 + 1
        assertEquals("8 u · 22 %", s.first().valueText)
        assertEquals(1.0, s.sumOf { it.fraction }, 1e-9)
    }

    @Test fun descripcionesAccesiblesDeLosGraficos() {
        val g = GraficosPeriodo(
            Periodo.Rango(ahora, ahora.plusSeconds(7200)),
            Serie(Granularidad.HORA, listOf(PuntoSerie(ahora, Cup.ofPesos(100), Cup.ofPesos(60)), PuntoSerie(ahora.plusSeconds(3600), Cup.ofPesos(300), Cup.ofPesos(140)))),
        )
        assertEquals("Gráfico de barras de ventas. Total 400 CUP. Mayor venta en 13:00: 300 CUP.", descripcionVentas(g, zona))
        assertEquals("Gráfico de área. Ventas 400 CUP, costo 200 CUP, ganancia neta 200 CUP, margen 50 %.", descripcionGanancia(g))
    }

    @Test fun valoresDelTop3() {
        val t = TopItem(1, "Pan", 1, Cup.ofPesos(50), Cup.ofPesos(20))
        assertEquals("1 unidad", valorTop(TipoTop.MAS_VENDIDO, t))
        assertEquals("20 CUP", valorTop(TipoTop.RENTABILIDAD, t))
        assertEquals("1 unidad vendida", subtituloTop(TipoTop.RENTABILIDAD, t))
        assertEquals("12 unidades", valorTop(TipoTop.LENTO, t.copy(unidades = 12)))
    }

    @Test fun resumenesDeLosAcordeones() {
        val g = GraficosPeriodo(
            Periodo.Rango(ahora, ahora.plusSeconds(7200)),
            Serie(Granularidad.HORA, listOf(PuntoSerie(ahora, Cup.ofPesos(100), Cup.ofPesos(60)), PuntoSerie(ahora.plusSeconds(3600), Cup.ofPesos(300), Cup.ofPesos(140)))),
        )
        assertEquals("400 CUP", resumenVentas(g))
        assertEquals("200 CUP", resumenGanancia(g))
        val r = ResumenGeneral(
            categorias = listOf(Porcion("Abarrotes", 1000, 0.6), Porcion("Otros", 500, 0.4)),
            metodosPago = listOf(Porcion("Efectivo", 300, 0.75), Porcion("Transferencia", 100, 0.25)),
            top3 = Top3(listOf(TopItem(1, "Pan", 12, Cup.ofPesos(50), Cup.ofPesos(20))), emptyList(), emptyList()),
            dias = 7,
        )
        assertEquals("2 categorías", resumenInventario(r))
        assertEquals("Efectivo", resumenMetodos(r))
        assertEquals("Pan · 12 unidades", resumenTop(TipoTop.MAS_VENDIDO, r.top3.masVendidos))
        assertNull(resumenTop(TipoTop.LENTO, r.top3.lentoMovimiento))
        val p = listOf(TopPersona("Ana", Cup.ofPesos(500), 3))
        assertEquals("Ana · 500 CUP", resumenTopPersonas(p))
        assertNull(resumenTopPersonas(emptyList()))
    }

    /** 0.27.0 (T14): reparto equitativo de las alertas por filas. */
    @Test fun repartoDeAlertasPorFilas() {
        assertEquals(emptyList<Int>(), repartoAlertas(0))
        assertEquals(listOf(1), repartoAlertas(1))
        assertEquals(listOf(2), repartoAlertas(2))
        assertEquals(listOf(3), repartoAlertas(3))
        assertEquals(listOf(2, 2), repartoAlertas(4))
        assertEquals(listOf(3, 2), repartoAlertas(5))
        assertEquals(listOf(3, 3), repartoAlertas(6))
        assertEquals(listOf(3, 2, 2), repartoAlertas(7))
        (1..12).forEach { n -> assertEquals(n, repartoAlertas(n).sum()) }
        // Con letra grande: una o dos por fila.
        assertEquals(listOf(1, 1, 1), repartoAlertas(3, maxPorFila = 1))
        assertEquals(listOf(2, 1), repartoAlertas(3, maxPorFila = 2))
        assertEquals(listOf(2, 2, 1), repartoAlertas(5, maxPorFila = 2))
    }

    /** 0.27.0 (T2): las capturas al 200 % partían «inven·tario» con 3 tarjetas por fila. */
    @Test fun alertasPorFilaSegunLetra() {
        assertEquals(3, alertasPorFila(328f, 1f))   // 360 dp, letra normal
        assertEquals(2, alertasPorFila(328f, 1.3f))
        assertEquals(1, alertasPorFila(328f, 2f))
        assertEquals(3, alertasPorFila(800f, 2f))   // tableta: con letra grande siguen cabiendo 3
        assertEquals(1, alertasPorFila(100f, 1f))   // nunca 0
    }

    /** 0.27.0 (N1): la dona Inventario muestra números sin unidad (milésimas → cantidad). */
    @Test fun cantidadesDeLaDonaSinUnidad() {
        assertEquals("3", cantidadInventario(3_000))
        assertEquals("12.5", cantidadInventario(12_500))
        assertEquals("0.25", cantidadInventario(250))
    }

    /** 0.27.0 (T3): medallas solo para los 3 primeros. */
    @Test fun medallasDelTop3() {
        assertEquals(1, puestoTop(0))
        assertEquals(3, puestoTop(2))
        assertEquals(null, puestoTop(3))
    }
}
