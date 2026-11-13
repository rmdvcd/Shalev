package cu.shalev.app.common

import cu.shalev.core.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los errores se leen en lenguaje llano: ningún texto para la persona puede llevar códigos, nombres de campos,
 * reglas internas ni motivos que parezcan identificadores. Esta prueba recorre TODOS los subtipos de [AppError].
 */
class MensajesErrorTest {

    private val todos: List<AppError> = listOf(
        AppError.Validacion("precioVenta", AppError.Regla.FORMATO),
        AppError.NoEncontrado,
        AppError.Duplicado("nombre"),
        AppError.EnUso("Pan suave, Croqueta"),
        AppError.EnUso("en_uso_por_receta_42"),
        AppError.StockInsuficiente(listOf("Harina")),
        AppError.StockInsuficiente(),
        AppError.TurnoCerrado,
        AppError.TurnoYaAbierto,
        AppError.LicenciaBloqueada,
        AppError.Cripto,
        AppError.ContrasenaIncorrecta,
        AppError.ArchivoDanado(incompleto = true),
        AppError.ArchivoDanado(incompleto = false),
        AppError.FormatoInvalido("respaldo de una versión más nueva de Shalev (9)"),
        AppError.Almacenamiento,
        AppError.SinPermiso,
        AppError.SinPrincipal(2),
        AppError.VinculacionRechazada("El código ya se usó. Pide uno nuevo."),
        AppError.VinculacionRechazada("codigo_vencido"),
        AppError.Red.SinConexion,
        AppError.Red.Timeout,
        AppError.Red.Http(503),
        AppError.Desconocido(IllegalStateException("boom interno")),
    )

    @Test fun ningunTextoPareceCodigo() {
        val prohibido = listOf(
            "_", "AppError", "Exception", "cu.shalev", "Validacion", "FORMATO", "Http", "503",
            "precioVenta", "boom interno", "codigo_vencido", "más nueva", "null", "{", "}",
        )
        todos.forEach { e ->
            val texto = e.mensajeParaUsuario()
            assertTrue("texto vacío para $e", texto.isNotBlank())
            prohibido.forEach { marca ->
                assertFalse("«$texto» (de $e) contiene la marca técnica «$marca»", texto.contains(marca))
            }
        }
    }

    @Test fun elMotivoDeVinculacionSoloSeMuestraSiPareceUnaFrase() {
        assertEquals(
            "El código ya se usó. Pide uno nuevo.",
            AppError.VinculacionRechazada("El código ya se usó. Pide uno nuevo.").mensajeParaUsuario(),
        )
        val generico = AppError.VinculacionRechazada("codigo_vencido").mensajeParaUsuario()
        assertNotEquals("codigo_vencido", generico)
        assertTrue(generico.contains("rechazó la vinculación"))
    }

    @Test fun mensajeSyncSaneaTambienElMotivo() {
        assertEquals(
            "El código ya se usó. Pide uno nuevo.",
            mensajeSync(AppError.VinculacionRechazada("El código ya se usó. Pide uno nuevo.")),
        )
        assertNotEquals("sin-licencia-para-secundarias", mensajeSync(AppError.VinculacionRechazada("sin-licencia-para-secundarias")))
    }

    @Test fun enUsoPuedeNombrarLosProductosDelUsuario() {
        // Los nombres son datos de la persona (sus productos), no identificadores internos: ayudan.
        assertTrue(AppError.EnUso("Pan suave").mensajeParaUsuario().contains("Pan suave"))
        // Si el detalle pareciera un identificador interno, cae al texto genérico.
        assertFalse(AppError.EnUso("en_uso_por_receta").mensajeParaUsuario().contains("en_uso"))
    }

    @Test fun esTextoParaUsuarioDistingueFrasesDeCodigos() {
        assertTrue(esTextoParaUsuario("La app principal está cerrada."))
        assertFalse(esTextoParaUsuario("codigo_vencido"))
        assertFalse(esTextoParaUsuario("sin-licencia"))
        assertFalse(esTextoParaUsuario("cu.shalev.Algo"))
        assertFalse(esTextoParaUsuario("SocketTimeoutException"))
        assertFalse(esTextoParaUsuario(""))
        assertFalse(esTextoParaUsuario("   "))
    }
}
