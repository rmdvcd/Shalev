package cu.shalev.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.registros.AccionesRegistros
import cu.shalev.app.registros.FichaRegistro
import cu.shalev.app.registros.PestanaRegistros
import cu.shalev.app.registros.RegistrosContent
import cu.shalev.app.registros.RegistrosTags
import cu.shalev.app.registros.RegistrosUiState
import cu.shalev.app.registros.TextosRegistros
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.usecase.TipoRegistro
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrosUiTest {
    @get:Rule val rule = createComposeRule()

    private val zona = ZoneId.of("America/Havana")
    private val t0 = Instant.parse("2026-09-30T16:00:00Z")
    private val venta = Venta(
        id = 12, turnoId = 1, fecha = t0, metodoPago = MetodoPago.EFECTIVO,
        detalles = listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2,
            precioBase = Cup.ofPesos(725), precioUnitario = Cup.ofPesos(725), costoUnitario = Cup.ofPesos(400))),
    )
    private val transaccion = Transaccion(
        id = 3, ventaId = 12, fecha = t0, importe = Cup.ofPesos(1450), numero = "TM123",
        cliente = DatosCliente("Ana Díaz", "90020212345", "53000000"),
    )
    private val mov = ItemMovimiento(
        MovimientoInventario(id = 5, fecha = t0, tipo = TipoMovimiento.CONSUMO, entidad = TipoEntidad.INSUMO, entidadId = 2,
            nombre = "Harina", delta = -500, existenciaResultante = 9500), "kg",
    )

    private fun ventas(filtro: FiltroRegistros = FiltroRegistros(), ficha: FichaRegistro? = null, hoja: Boolean = false) = RegistrosUiState(
        filtros = mapOf(TipoRegistro.VENTAS to filtro), vista = EstadoCarga.Exito(VistaRegistro.Ventas(listOf(venta))), ficha = ficha, hojaFiltro = hoja,
    )

    private fun pantalla(state: RegistrosUiState, acciones: AccionesRegistros = AccionesRegistros()) =
        rule.setContent { ShalevTheme(darkTheme = false) { RegistrosContent(state, EstadoCarga.Vacio(), acciones, zona = zona) } }

    @Test fun ventasConResumenYTapAbreLaVentana() {
        var abierta: Long? = null
        pantalla(ventas(), AccionesRegistros(onAbrirVenta = { abierta = it }))
        rule.onNodeWithText("30/09/2026 12:00").assertIsDisplayed() // identificada por fecha, no por número
        rule.onNodeWithTag(RegistrosTags.RESUMEN).assertIsDisplayed()
        rule.onNodeWithText("1 venta · 1\u202F450 CUP").assertIsDisplayed()
        rule.onNodeWithTag(RegistrosTags.venta(12)).performClick()
        assertEquals(12L, abierta)
    }

    @Test fun pestanasBuscadorFiltroYCompartir() {
        val hechos = mutableListOf<String>()
        pantalla(
            ventas(),
            AccionesRegistros(
                onPestana = { hechos += it.name }, onBuscar = { hechos += "buscar:$it" },
                onAbrirFiltro = { hechos += "filtro" }, onAbrirExportar = { hechos += "exportar" },
            ),
        )
        rule.onNodeWithTag(RegistrosTags.pestana(PestanaRegistros.MOVIMIENTOS)).performClick()
        rule.onNodeWithTag(RegistrosTags.BUSCAR).performTextInput("pan")
        rule.onNodeWithTag(RegistrosTags.FILTRO).performClick()
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsEnabled().performClick()
        rule.onNodeWithTag(RegistrosTags.COMPARTIR).assertDoesNotExist() // 0.26.0: sin «Compartir como texto»
        assertEquals(listOf("MOVIMIENTOS", "buscar:pan", "filtro", "exportar"), hechos)
    }

    @Test fun sinDatosNoSeComparte() {
        pantalla(RegistrosUiState(vista = EstadoCarga.Vacio(VistaRegistro.Ventas(emptyList()))))
        rule.onNodeWithText(TextosRegistros.vacioTitulo(TipoRegistro.VENTAS)).assertIsDisplayed()
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsNotEnabled()
    }

    @Test fun sinResultadosOfreceQuitarFiltros() {
        var todo: Boolean? = null
        val state = RegistrosUiState(
            filtros = mapOf(TipoRegistro.VENTAS to FiltroRegistros(texto = "zzz")),
            vista = EstadoCarga.Exito(VistaRegistro.Ventas(emptyList())),
        )
        pantalla(state, AccionesRegistros(onQuitarFiltros = { todo = it }))
        rule.onNodeWithTag(RegistrosTags.QUITAR_FILTROS).performClick()
        assertEquals(true, todo)
    }

    @Test fun ventanaDeVentaMuestraCamposSinCompartirTexto() {
        pantalla(ventas(ficha = FichaRegistro.DeVenta(venta)))
        rule.onNodeWithTag(RegistrosTags.FICHA).assertExists()
        rule.onNodeWithText("Ganancia").assertExists() // la ventana es interna
        rule.onAllNodesWithContentDescription("Compartir como texto").assertCountEquals(0)
    }

    @Test fun ventanaDeTransferenciaLlevaASuVenta() {
        var ver = false
        val state = RegistrosUiState(
            pestana = PestanaRegistros.TRANSFERENCIAS,
            vista = EstadoCarga.Exito(VistaRegistro.Transferencias(listOf(transaccion))),
            ficha = FichaRegistro.DeTransferencia(transaccion),
        )
        pantalla(state, AccionesRegistros(onVerVenta = { ver = true }))
        rule.onNodeWithText("90020212345").assertExists() // completo en la ventana; oculto solo al compartir
        rule.onNodeWithTag(RegistrosTags.FICHA_VER_VENTA).performClick()
        assertTrue(ver)
    }

    @Test fun movimientoConUnidadYEstadoParaTalkBack() {
        var abierto: Long? = null
        val state = RegistrosUiState(pestana = PestanaRegistros.MOVIMIENTOS, vista = EstadoCarga.Exito(VistaRegistro.Movimientos(listOf(mov))))
        pantalla(state, AccionesRegistros(onAbrirMovimiento = { abierto = it }))
        rule.onNodeWithText("-0.5 kg", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(RegistrosTags.movimiento(5)).performClick()
        assertEquals(5L, abierto)
    }

    @Test fun hojaFiltroValidaImportesAntesDeAplicar() {
        var aplicado: FiltroRegistros? = null
        pantalla(ventas(hoja = true), AccionesRegistros(onAplicarFiltro = { aplicado = it }))
        rule.onNodeWithTag(RegistrosTags.FILTRO_PERIODO).performClick()
        rule.onNodeWithTag(RegistrosTags.periodo(PeriodoRegistro.HOY)).performClick()
        val minimo = rule.onNodeWithTag(RegistrosTags.FILTRO_MIN)
        minimo.performScrollTo().performTextReplacement("abc")
        minimo.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        minimo.performTextReplacement(".") // se admite al escribir, pero no es un importe final válido
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithTag(RegistrosTags.FILTRO_APLICAR).performClick()
        rule.onNodeWithText(TextosRegistros.IMPORTE_INVALIDO).performScrollTo().assertIsDisplayed()
        assertEquals(null, aplicado)
        minimo.performTextReplacement("100")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        // Reintento deliberado, no doble toque en la misma acción de Aplicar.
        val desde = android.os.SystemClock.elapsedRealtime()
        rule.waitUntil(2_000) { android.os.SystemClock.elapsedRealtime() - desde >= cu.shalev.designsystem.component.GuardiaDobleToque.VENTANA_MS }
        rule.onNodeWithTag(RegistrosTags.FILTRO_APLICAR).performClick()
        assertEquals(FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(100)), aplicado)
    }

    @Test fun filtroDeVendedorUsaCombobox() {
        var aplicado: FiltroRegistros? = null
        val estado = ventas(hoja = true).copy(vendedores = listOf("Ana", "Luis"))
        pantalla(estado, AccionesRegistros(onAplicarFiltro = { aplicado = it }))
        rule.onNodeWithTag(RegistrosTags.FILTRO_VENDEDOR).performClick()
        rule.onNodeWithTag(RegistrosTags.vendedor("Ana")).performClick()
        rule.onNodeWithTag(RegistrosTags.FILTRO_APLICAR).performClick()
        assertEquals("Ana", aplicado?.vendedor)
    }

    @Test fun hojaFiltroDeMovimientosSinImporte() {
        val state = RegistrosUiState(pestana = PestanaRegistros.MOVIMIENTOS, vista = EstadoCarga.Exito(VistaRegistro.Movimientos(listOf(mov))), hojaFiltro = true)
        pantalla(state)
        rule.onNodeWithTag(RegistrosTags.FILTRO_MIN).assertDoesNotExist()
        rule.onNodeWithText("Importe").assertDoesNotExist()
    }

    @Test fun enTurnosNoHayBuscadorNiCompartir() {
        pantalla(RegistrosUiState(pestana = PestanaRegistros.TURNOS))
        rule.onNodeWithTag(RegistrosTags.BUSCAR).assertDoesNotExist()
        rule.onNodeWithTag(RegistrosTags.COMPARTIR).assertDoesNotExist()
    }

    // ---------- Exportar PDF / Excel (Prompt 14)

    @Test fun exportarAbreLaHojaYCadaFormatoSeEnvia() {
        var abrir = 0
        val enviados = mutableListOf<FormatoExport>()
        pantalla(
            ventas().copy(hojaExportar = true),
            AccionesRegistros(onAbrirExportar = { abrir++ }, onEnviar = { enviados += it }),
        )
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsEnabled().performClick()
        assertEquals(1, abrir)
        rule.onNodeWithText(TextosRegistros.tituloExportar(TipoRegistro.VENTAS)).assertIsDisplayed()
        rule.onNodeWithText(TextosRegistros.alcanceExportar(TipoRegistro.VENTAS, 1, null)).assertIsDisplayed()
        // El diseño actual presenta una fila por formato, que abre el selector de compartir del sistema.
        rule.onNodeWithTag(RegistrosTags.formato(FormatoExport.XLSX)).performClick()
        rule.onNodeWithTag(RegistrosTags.formato(FormatoExport.PDF)).performClick()
        assertEquals(listOf(FormatoExport.XLSX, FormatoExport.PDF), enviados)
    }

    @Test fun exportandoMuestraProgresoYDeshabilitaExportar() {
        pantalla(ventas().copy(exportando = true))
        rule.onNodeWithTag(RegistrosTags.EXPORTANDO).assertExists()
        rule.onNodeWithTag(RegistrosTags.EXPORTAR).assertIsNotEnabled()
    }

    @Test fun exportarTransferenciasAvisaDeDatosDeClientes() {
        pantalla(
            RegistrosUiState(
                pestana = PestanaRegistros.TRANSFERENCIAS, hojaExportar = true,
                vista = EstadoCarga.Exito(VistaRegistro.Transferencias(listOf(transaccion))),
            ),
        )
        rule.onNodeWithText(TextosRegistros.AVISO_DATOS_CLIENTES).assertIsDisplayed()
    }
}
