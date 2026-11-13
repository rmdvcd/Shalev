package cu.shalev.app.integracion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.respaldo.RespaldoScreen
import cu.shalev.app.respaldo.RespaldoTags
import cu.shalev.app.respaldo.RespaldoViewModel
import cu.shalev.app.respaldo.TextosRespaldo
import cu.shalev.designsystem.component.ShalevStepperTextos
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.Producto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 15 — exportación e importación con la pantalla real, RespaldoRepositoryImpl y Room en memoria.
 * El selector del sistema ("Guardar como" / "Abrir") se simula entregando la URI al ViewModel, que es
 * exactamente lo que hace el callback del launcher en RespaldoScreen.
 */
@RunWith(AndroidJUnit4::class)
class FlujoRespaldoTest {
    @get:Rule val rule = createComposeRule()
    private val env = EntornoIntegracion()
    private lateinit var vm: RespaldoViewModel
    private val clave = "Respaldo-2026"
    private val uri = "content://shalev.test/Shalev_completo.shalev"

    @After fun fin() = env.cerrar()

    private fun crear(nombre: String) = runBlocking {
        env.productos.crear(Producto(categoria = "Bebidas", nombre = nombre, precioCosto = cup(60), precioVenta = cup(100), cantidad = 4, creadoEn = env.ahora), null)
    }

    private fun nombres() = runBlocking { env.productos.observarTodos().first().map { it.nombre }.sorted() }

    private fun mostrar() {
        vm = env.respaldoVm()
        rule.setContent { ShalevTheme { RespaldoScreen(onBack = {}, viewModel = vm) } }
    }

    private fun esperarResultado() = rule.waitUntil(30_000) { rule.onAllNodesWithTag(RespaldoTags.RESULTADO).fetchSemanticsNodes().isNotEmpty() }

    private fun cerrarResultado() {
        rule.onNodeWithContentDescription(TextosRespaldo.ACEPTAR).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(RespaldoTags.RESULTADO).fetchSemanticsNodes().isEmpty() }
    }

    /** Escribe la contraseña en la pantalla y "elige" el destino. */
    private fun exportar() {
        // S-01: protección ENCENDIDA por defecto. No apagarla para intentar escribir una contraseña.
        assertTrue(vm.state.value.export.conContrasena)
        rule.onNodeWithTag(RespaldoTags.SIN_CONTRASENA).assertDoesNotExist()
        rule.onNodeWithTag(RespaldoTags.CONTRASENA).performScrollTo().performTextInput(clave)
        rule.onNodeWithTag(RespaldoTags.REPETIR).performScrollTo().performTextInput(clave)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performScrollTo().performClick()
        rule.onNodeWithTag(RespaldoTags.GUARDAR).performScrollTo().assertIsDisplayed()
        // Solo se sustituye el resultado del selector del sistema, no la edición ni los pasos de la pantalla.
        rule.runOnIdle { vm.destinoElegido(uri) }
        esperarResultado()
        rule.onNodeWithText("Respaldo guardado").assertIsDisplayed()
        cerrarResultado()
        // Pregunta 3 (opción 1): el asistente queda en «Paso 3 de 3: Respaldo listo».
        rule.onNodeWithTag(RespaldoTags.HECHO).performScrollTo().assertIsDisplayed()
    }

    private fun importar(archivo: String, contrasena: String) {
        rule.runOnIdle { vm.archivoElegido(archivo) }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag(RespaldoTags.CONTRASENA_IMPORTAR).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(RespaldoTags.CONTRASENA_IMPORTAR).performTextInput(contrasena)
        rule.onNodeWithContentDescription("Importar").performClick()
    }

    @Test fun exportarEImportarRestauraLosDatos() {
        crear("Refresco")
        mostrar()
        exportar()
        crear("Galletas")
        assertEquals(listOf("Galletas", "Refresco"), nombres())

        importar(uri, clave)
        esperarResultado()
        rule.onNodeWithText("Respaldo importado").assertIsDisplayed()
        assertEquals(listOf("Refresco"), nombres())
    }

    @Test fun archivoCortadoSeExplicaAntesDePedirLaContrasenaYNoTocaNada() {
        crear("Refresco")
        mostrar()
        exportar()
        crear("Galletas")
        val completo = env.archivos.bytes(uri)
        env.archivos.poner("content://shalev.test/cortado.shalev", completo.copyOf(completo.size / 2))

        rule.runOnIdle { vm.archivoElegido("content://shalev.test/cortado.shalev") }
        esperarResultado()
        rule.onNodeWithText("El archivo está incompleto").assertIsDisplayed()
        rule.onNodeWithTag(RespaldoTags.CONTRASENA_IMPORTAR).assertDoesNotExist()
        assertEquals(listOf("Galletas", "Refresco"), nombres())
    }

    @Test fun contrasenaIncorrectaPermiteReintentarSinTocarNada() {
        crear("Refresco")
        mostrar()
        exportar()
        crear("Galletas")

        importar(uri, "otra-clave-1")
        rule.waitUntil(30_000) { vm.state.value.importacion?.error != null }
        rule.onNodeWithTag(RespaldoTags.CONTRASENA_IMPORTAR).assertExists()   // el diálogo sigue abierto
        assertEquals(listOf("Galletas", "Refresco"), nombres())
    }

    @Test fun archivoQueNoEsDeShalevNoSeImporta() {
        crear("Refresco")
        env.archivos.poner("content://shalev.test/foto.jpg", ByteArray(2048) { (it % 251).toByte() })
        mostrar()
        rule.runOnIdle { vm.archivoElegido("content://shalev.test/foto.jpg") }
        esperarResultado()
        rule.onNodeWithTag(RespaldoTags.CONTRASENA_IMPORTAR).assertDoesNotExist()
        assertEquals(listOf("Refresco"), nombres())
    }
}
