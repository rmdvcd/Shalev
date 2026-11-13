package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.ErrorFiltroRegistro
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.repository.FiltroRegistro
import cu.shalev.domain.repository.RegistroRepository
import cu.shalev.domain.service.RegistrosFiltro
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.ObservarRegistro
import cu.shalev.domain.usecase.TipoRegistro
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers

/** Mismas reglas que las consultas de :data: fecha en [desde, hasta), importe inclusivo, más recientes primero. */
class FakeRegistros : RegistroRepository {
    val ventas = MutableStateFlow<List<Venta>>(emptyList())
    val transacciones = MutableStateFlow<List<Transaccion>>(emptyList())
    val movimientos = MutableStateFlow<List<MovimientoInventario>>(emptyList())
    val consultas = mutableListOf<FiltroRegistro>()

    private fun FiltroRegistro.fecha(i: Instant) = (desde == null || i >= desde) && (hasta == null || i < hasta)
    private fun FiltroRegistro.importe(c: Cup) = (importeMin == null || c >= importeMin) && (importeMax == null || c <= importeMax)

    override fun ventas(filtro: FiltroRegistro): Flow<List<Venta>> { consultas += filtro
        return ventas.map { l -> l.filter { filtro.fecha(it.fecha) && filtro.importe(it.total) }.sortedByDescending { it.fecha } } }
    override fun transacciones(filtro: FiltroRegistro): Flow<List<Transaccion>> { consultas += filtro
        return transacciones.map { l -> l.filter { filtro.fecha(it.fecha) && filtro.importe(it.importe) }.sortedByDescending { it.fecha } } }
    override fun movimientos(filtro: FiltroRegistro): Flow<List<MovimientoInventario>> { consultas += filtro
        return movimientos.map { l -> l.filter { filtro.fecha(it.fecha) }.sortedByDescending { it.fecha } } }
    override fun vendedores(): Flow<List<String>> = ventas.map { l -> l.map { it.vendedor }.filter { it.isNotBlank() }.distinct().sorted() }
}

class RegistrosTest {
    private val habana = ZoneId.of("America/Havana")
    private val hoy = LocalDate.of(2026, 9, 30)

    private fun venta(id: Long, total: Long, fecha: Instant = T0, nombre: String = "Café molido", metodo: MetodoPago = MetodoPago.EFECTIVO) = Venta(
        id = id, turnoId = 3, fecha = fecha, metodoPago = metodo,
        detalles = listOf(DetalleVenta(productoId = 1, nombre = nombre, categoria = "Víveres", cantidad = 2, precioBase = Cup.ofPesos(total / 2),
            precioUnitario = Cup.ofPesos(total / 2), costoUnitario = Cup.ofPesos(total / 4))),
        transaccion = if (metodo == MetodoPago.TRANSFERENCIA) transaccion(id, total) else null,
    )

    private fun transaccion(ventaId: Long, importe: Long, fecha: Instant = T0) = Transaccion(
        id = ventaId, ventaId = ventaId, fecha = fecha, importe = Cup.ofPesos(importe), numero = "TMA$ventaId",
        cliente = DatosCliente("José Pérez", "85010112345", "53123456"), tarjetaCobro = "9204 1299 7654 3210",
    )

    private fun mov(id: Long, tipo: TipoMovimiento, entidad: TipoEntidad, nombre: String, delta: Long, existencia: Long, fecha: Instant = T0) =
        MovimientoInventario(id = id, fecha = fecha, tipo = tipo, entidad = entidad, entidadId = 1, nombre = nombre, delta = delta,
            existenciaResultante = existencia, turnoId = 3, nota = if (tipo == TipoMovimiento.CONSUMO) "Producción de Pan" else null)

    // ---------------- Fechas ----------------

    @Test fun periodosSonDiasDeCalendarioInclusivos() {
        assertEquals(null to null, RegistrosFiltro.rango(FiltroRegistros(), hoy))
        assertEquals(hoy to hoy, RegistrosFiltro.rango(FiltroRegistros(periodo = PeriodoRegistro.HOY), hoy))
        assertEquals(LocalDate.of(2026, 9, 24) to hoy, RegistrosFiltro.rango(FiltroRegistros(periodo = PeriodoRegistro.SIETE_DIAS), hoy))
        assertEquals(LocalDate.of(2026, 9, 1) to hoy, RegistrosFiltro.rango(FiltroRegistros(periodo = PeriodoRegistro.ESTE_MES), hoy))
        val p = FiltroRegistros(periodo = PeriodoRegistro.PERSONALIZADO, desde = LocalDate.of(2026, 9, 10))
        assertEquals(LocalDate.of(2026, 9, 10) to null, RegistrosFiltro.rango(p, hoy))
    }

    @Test fun consultaUsaLaZonaDeCubaYHastaIncluyeTodoElDia() {
        val q = RegistrosFiltro.consulta(
            FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(100), texto = "café"), TipoRegistro.VENTAS, hoy, habana,
        )
        assertEquals(Instant.parse("2026-09-30T04:00:00Z"), q.desde) // 00:00 en La Habana (UTC-4 en septiembre)
        assertEquals(Instant.parse("2026-10-01T04:00:00Z"), q.hasta) // exclusivo: incluye el 30 completo
        assertEquals(Cup.ofPesos(100), q.importeMin)
        assertNull(q.texto) // el texto se filtra en el dominio (sin tildes)
    }

    @Test fun movimientosIgnoranElImporte() {
        val q = RegistrosFiltro.consulta(FiltroRegistros(importeMin = Cup.ofPesos(1), importeMax = Cup.ofPesos(9)), TipoRegistro.MOVIMIENTOS, hoy, habana)
        assertNull(q.importeMin); assertNull(q.importeMax)
    }

    @Test fun validacionYContadorDeFiltros() {
        val invertidas = FiltroRegistros(periodo = PeriodoRegistro.PERSONALIZADO, desde = LocalDate.of(2026, 9, 20), hasta = LocalDate.of(2026, 9, 10))
        assertEquals(ErrorFiltroRegistro.FECHAS_INVERTIDAS, RegistrosFiltro.validar(invertidas))
        assertEquals(ErrorFiltroRegistro.IMPORTES_INVERTIDOS, RegistrosFiltro.validar(FiltroRegistros(importeMin = Cup.ofPesos(9), importeMax = Cup.ofPesos(1))))
        assertNull(RegistrosFiltro.validar(FiltroRegistros(importeMin = Cup.ofPesos(5), importeMax = Cup.ofPesos(5))))
        assertEquals(2, FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMax = Cup.ofPesos(5)).activos)
        assertEquals(0, FiltroRegistros(periodo = PeriodoRegistro.PERSONALIZADO, texto = "x").activos) // personalizado sin fechas = sin filtro
        assertEquals(FiltroRegistros(texto = "x"), FiltroRegistros(texto = "x", periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(1)).sinFiltros())
    }

    // ---------------- Buscador ----------------

    @Test fun buscadorSinTildesNiMayusculasYSinIdInterno() {
        val v = venta(12, 100, metodo = MetodoPago.TRANSFERENCIA)
        assertTrue(RegistrosFiltro.coincide(v, "CAFE"))
        assertTrue(RegistrosFiltro.coincide(v, "viveres"))
        assertTrue(RegistrosFiltro.coincide(v, "transferencia"))
        assertTrue(RegistrosFiltro.coincide(v, "jose"))
        // Sin búsqueda oculta por Nº de venta: el id interno no se muestra, así que tampoco se busca.
        assertFalse(RegistrosFiltro.coincide(venta(12, 100), "12"))
        assertFalse(RegistrosFiltro.coincide(venta(12, 100), "#12"))
        assertTrue(RegistrosFiltro.coincide(v, "  "))

        val t = transaccion(7, 50)
        assertTrue(RegistrosFiltro.coincide(t, "tma7"))
        assertTrue(RegistrosFiltro.coincide(t, "perez"))
        assertTrue(RegistrosFiltro.coincide(t, "5312"))
        assertFalse(RegistrosFiltro.coincide(t, "#7")) // id de la venta: ya no se busca
        assertFalse(RegistrosFiltro.coincide(t, "ana"))

        val m = ItemMovimiento(mov(1, TipoMovimiento.CONSUMO, TipoEntidad.INSUMO, "Harina", -500, 9500), "kg")
        assertTrue(RegistrosFiltro.coincide(m, "consumo"))
        assertTrue(RegistrosFiltro.coincide(m, "produccion de pan")) // nota, sin tilde
        assertTrue(RegistrosFiltro.coincide(m, "insumo"))
        assertFalse(RegistrosFiltro.coincide(m, "producto"))
    }

    // ---------------- Caso de uso ----------------

    @Test fun observarRegistroFiltraPorFechaImporteYTexto() = runTest {
        val repo = FakeRegistros()
        val reloj = FixedClock(Instant.parse("2026-09-30T16:00:00Z"))
        val ayer = Instant.parse("2026-09-29T16:00:00Z")
        val hoyI = Instant.parse("2026-09-30T15:00:00Z")
        repo.ventas.value = listOf(venta(1, 100, ayer), venta(2, 500, hoyI, nombre = "Pan"), venta(3, 50, hoyI.plusSeconds(60)))
        val uc = ObservarRegistro(repo, FakeInsumos(), reloj, Dispatchers.Unconfined)

        val todas = uc(TipoRegistro.VENTAS, FiltroRegistros(), habana).first() as VistaRegistro.Ventas
        assertEquals(listOf(3L, 2L, 1L), todas.items.map { it.id }) // más recientes primero
        assertEquals(Cup.ofPesos(650), todas.total)

        val hoyCaras = uc(TipoRegistro.VENTAS, FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(100)), habana).first() as VistaRegistro.Ventas
        assertEquals(listOf(2L), hoyCaras.items.map { it.id })

        val texto = uc(TipoRegistro.VENTAS, FiltroRegistros(texto = "cafe"), habana).first() as VistaRegistro.Ventas
        assertEquals(listOf(3L, 1L), texto.items.map { it.id })
    }

    @Test fun movimientosLlevanLaUnidadDelInsumo() = runTest {
        val repo = FakeRegistros()
        val insumos = FakeInsumos().apply { put(insumo(1, "Harina").copy(unidad = UnidadMedida.KILOGRAMO)) }
        repo.movimientos.value = listOf(
            mov(1, TipoMovimiento.CONSUMO, TipoEntidad.INSUMO, "Harina", -500, 9500),
            mov(2, TipoMovimiento.VENTA, TipoEntidad.PRODUCTO, "Pan", -2, 8, T0.plusSeconds(60)),
        )
        val v = ObservarRegistro(repo, insumos, FixedClock(), Dispatchers.Unconfined)(TipoRegistro.MOVIMIENTOS, FiltroRegistros(importeMin = Cup.ofPesos(1))).first() as VistaRegistro.Movimientos
        assertEquals(listOf("", "kg"), v.items.map { it.simbolo }) // el producto 1 no toma la unidad del insumo 1
        assertNull(repo.consultas.last().importeMin)
    }

    // ---------------- Campos de las fichas (0.26.0: sin exportar como texto) ----------------

    @Test fun camposDeVentaInternosYParaElCliente() {
        val v = venta(12, 3000, metodo = MetodoPago.TRANSFERENCIA)
        val cliente = TablasExport.camposVenta(v, habana, interno = false).toMap()
        assertNull(cliente["Costo"]); assertNull(cliente["Ganancia"])
        assertEquals("3,000 CUP", cliente["Total"])
        val interno = TablasExport.camposVenta(v, habana).toMap()
        assertEquals("1,500 CUP", interno["Costo"]) // 2 × 750
        assertEquals("1,500 CUP", interno["Ganancia"])
        assertNull(interno["Turno"]) // identificada por fecha: sin ids internos
        assertNull(interno["Fecha"]) // la fecha va en el título
    }

    @Test fun lineasVentaPonenElUnitarioDebajo() {
        val v = venta(12, 3000)
        assertEquals(
            listOf(Triple("2 × Café molido", "1,500 CUP c/u", "3,000 CUP")),
            TablasExport.lineasVenta(v),
        )
    }

    @Test fun carneOcultoSoloSiSePide() {        assertEquals("85010112345", TablasExport.camposTransaccion(transaccion(7, 50), habana).toMap()["CI"]) // la ventana lo muestra completo
        assertEquals("••••••••345", TablasExport.camposTransaccion(transaccion(7, 50), habana, ocultarCi = true).toMap()["CI"])
        assertEquals("12", TablasExport.ciOculto("12"))
    }

    @Test fun movimientoYTablaConUnidadesYTildes() {
        val i = ItemMovimiento(mov(1, TipoMovimiento.CONSUMO, TipoEntidad.INSUMO, "Harina", -500, 9500), "kg")
        assertEquals(
            listOf("Tipo" to "Consumo", "Insumo" to "Harina", "Cambio" to "-0.5 kg", "Existencia después" to "9.5 kg", "Nota" to "Producción de Pan"),
            TablasExport.camposMovimiento(i, habana),
        )
        assertEquals("Movimiento del 01/09/2026 08:00", TablasExport.tituloMovimiento(i, habana))
        val prod = ItemMovimiento(mov(2, TipoMovimiento.PRODUCCION, TipoEntidad.PRODUCTO, "Pan", 4, 4))
        val tabla = TablasExport.movimientosItems(listOf(i, prod), habana)
        assertEquals(listOf("-0.5 kg", "+4"), tabla.filas.map { it[4] })
        assertEquals("Producción", tabla.filas[1][1])
    }
}
