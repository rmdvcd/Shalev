package cu.shalev.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.common.LocalPermisosApp
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.servicios.AccionesServicioForm
import cu.shalev.app.servicios.AccionesServicios
import cu.shalev.app.servicios.HojaServicios
import cu.shalev.app.servicios.ServicioForm
import cu.shalev.app.servicios.ServicioFormContent
import cu.shalev.app.servicios.ServicioFormTags
import cu.shalev.app.servicios.ServicioFormUiState
import cu.shalev.app.servicios.ServiciosContent
import cu.shalev.app.servicios.ServiciosTags
import cu.shalev.app.servicios.ServiciosUiState
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.component.GuardiaDobleToque
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.FiltroServicios
import cu.shalev.domain.model.Modulo
import cu.shalev.domain.model.PermisosApp
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.ServicioDisponible
import cu.shalev.domain.model.VistaServicios
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiciosUiTest {
    @get:Rule val rule = createComposeRule()

    private fun avanzarPaso() {
        val desde = android.os.SystemClock.elapsedRealtime()
        rule.onNodeWithTag("alta.siguiente").performClick()
        rule.waitUntil(2_000) { android.os.SystemClock.elapsedRealtime() - desde >= GuardiaDobleToque.VENTANA_MS }
    }

    private val t0 = Instant.parse("2026-09-30T16:00:00Z")
    private fun servicio(id: Long, nombre: String, tipo: String) = ServicioDisponible(
        Servicio(id = id, nombre = nombre, tipo = tipo, importe = Cup.ofPesos(500), creadoEn = t0),
        lineas = emptyList(),
        costo = null,
    )

    private val servicios = listOf(
        servicio(1, "Corte de cabello", "Belleza"),
        servicio(2, "Entrega a domicilio", "Entrega"),
    )

    private fun estado(
        seleccion: Set<Long> = emptySet(),
        hoja: HojaServicios? = null,
    ) = ServiciosUiState(
        vista = EstadoCarga.Exito(VistaServicios(servicios, servicios.size, listOf("Belleza", "Entrega"))),
        seleccion = seleccion,
        hoja = hoja,
        elementosFijados = servicios.filter { it.servicio.id in seleccion },
    )

    private fun mostrar(state: ServiciosUiState, acciones: AccionesServicios = AccionesServicios()) {
        rule.setContent { ShalevTheme(darkTheme = false) { ServiciosContent(state, acciones) } }
    }

    @Test fun serviciosSeleccionadosSeFijanAntesDelBuscadorYNoSeRepiten() {
        mostrar(estado(seleccion = setOf(1)))
        rule.onNodeWithTag(ServiciosTags.FIJADOS).assertIsDisplayed()
        rule.onNodeWithTag(ServiciosTags.fila(1)).assertIsDisplayed()
        rule.onAllNodesWithTag(ServiciosTags.fila(1)).assertCountEquals(1)
        val arriba = rule.onAllNodesWithTag(ServiciosTags.FIJADOS).fetchSemanticsNodes().single().boundsInRoot.top
        val buscador = rule.onAllNodesWithTag(ServiciosTags.BUSCAR).fetchSemanticsNodes().single().boundsInRoot.top
        assertTrue("la selección debe quedar sobre el buscador", arriba < buscador)
    }

    @Test fun filtroDeServiciosUsaCombobox() {
        var aplicado: FiltroServicios? = null
        mostrar(estado(hoja = HojaServicios.FILTRO), AccionesServicios(onFiltro = { aplicado = it }))
        rule.onNodeWithTag(ServiciosTags.FILTRO_TIPO).performClick()
        rule.onNodeWithTag(ServiciosTags.tipoOpcion("Belleza")).performClick()
        rule.onNodeWithTag(ServiciosTags.FILTRO_APLICAR).performClick()
        assertEquals(FiltroServicios(tipo = "Belleza"), aplicado)
    }

    @Test fun accesosContextualesDeServiciosDeleganEnSusAcciones() {
        var fichas = false
        var gestion = false
        mostrar(estado(), AccionesServicios(onFichasCosto = { fichas = true }, onGestionInventario = { gestion = true }))

        rule.onNodeWithTag(ServiciosTags.FICHAS_COSTO).assertIsDisplayed().performClick()
        rule.onNodeWithTag(ServiciosTags.GESTION_INVENTARIO).assertIsDisplayed().performClick()
        assertTrue(fichas)
        assertTrue(gestion)
    }

    @Test fun gestionDeInventarioSeOcultaSiElModuloNoEstaHabilitado() {
        rule.setContent {
            CompositionLocalProvider(LocalPermisosApp provides PermisosApp.PRINCIPAL.copy(modulos = setOf(Modulo.SERVICIOS))) {
                ShalevTheme(darkTheme = false) { ServiciosContent(estado(), AccionesServicios()) }
            }
        }
        rule.onNodeWithTag(ServiciosTags.FICHAS_COSTO).assertIsDisplayed()
        rule.onNodeWithTag(ServiciosTags.GESTION_INVENTARIO).assertDoesNotExist()
    }

    @Test fun formularioDeServicioDistribuyeLosCamposEnTresPasos() {
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                ServicioFormContent(ServicioFormUiState(form = ServicioForm()), AccionesServicioForm())
            }
        }
        rule.onNodeWithTag(ServicioFormTags.NOMBRE).assertIsDisplayed()
        rule.onNodeWithTag(ServicioFormTags.IMPORTE).assertDoesNotExist()

        avanzarPaso()
        rule.onNodeWithTag(ServicioFormTags.INSUMOS).assertExists()
        rule.onNodeWithTag(ServicioFormTags.IMPORTE).assertDoesNotExist()

        avanzarPaso()
        rule.onNodeWithTag(ServicioFormTags.IMPORTE).assertExists()
        rule.onNodeWithTag("alta.guardar").assertExists()
    }
}
