package cu.shalev.app.respaldo

import cu.shalev.domain.repository.EtapaRespaldo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P18 (A09) + pregunta 3, opción 1: asistente de Respaldo en la misma pantalla (exportar 3 pasos, importar 4). */
class RespaldoPasosTest {
    private val valida = ExportForm(true, "clave-segura", "clave-segura")

    @Test fun exportarSoloLlegaAlDestinoConContrasenaValida() {
        assertEquals(1, PasosRespaldo.pasoExportar(1, valida))
        assertEquals(2, PasosRespaldo.pasoExportar(2, valida))
        assertEquals(1, PasosRespaldo.pasoExportar(2, ExportForm(true, "corta", "corta")))
        assertEquals(1, PasosRespaldo.pasoExportar(2, ExportForm(true, "clave-segura", "otra-clave")))
    }

    @Test fun trasExportarSeMuestraElPaso3AunqueElFormularioSeVacie() {
        assertEquals(3, PasosRespaldo.pasoExportar(2, ExportForm(), hecho = true))
        assertEquals(1, PasosRespaldo.pasoExportar(2, ExportForm(true), hecho = false))
        assertEquals(2, PasosRespaldo.pasoExportar(2, ExportForm(conContrasena = false), hecho = false)) // sin contraseña (apagada a propósito) ya es válido
        assertEquals(1, PasosRespaldo.pasoExportar(2, ExportForm(), hecho = false)) // S-01: por defecto pide contraseña; sin escribirla no avanza
        assertEquals(3, PasosRespaldo.TOTAL_EXPORTAR)
    }

    @Test fun titulosDeExportar() {
        assertEquals(PasosRespaldo.EXPORTAR_1, PasosRespaldo.tituloExportar(1))
        assertEquals(PasosRespaldo.EXPORTAR_2, PasosRespaldo.tituloExportar(2))
        assertEquals(PasosRespaldo.EXPORTAR_3, PasosRespaldo.tituloExportar(3))
    }

    @Test fun textoHechoRecuerdaLaContrasenaSinMostrarla() {
        val g = PasosRespaldo.textoHecho("Shalev_respaldo_2026-09-30.shalev", enviado = false)
        assertTrue(g.startsWith("Shalev_respaldo_2026-09-30.shalev guardado."))
        assertTrue(g.contains("contraseña"))
        assertTrue(PasosRespaldo.textoHecho("x.shalev", enviado = true).contains("enviarlo"))
    }

    @Test fun importarTieneCuatroPasos() {
        assertEquals(4, PasosRespaldo.TOTAL_IMPORTAR)
        assertEquals(1, PasosRespaldo.pasoImportarTarjeta(comprobando = false, imp = null))
        assertEquals(2, PasosRespaldo.pasoImportarTarjeta(comprobando = true, imp = null))
        val imp = ImportForm(uri = "content://x")
        assertEquals(3, PasosRespaldo.pasoImportarTarjeta(comprobando = false, imp = imp))
        assertEquals(4, PasosRespaldo.pasoImportarTarjeta(comprobando = false, imp = imp.copy(contrasena = "a")))
    }

    @Test fun importarPideContrasenaYLuegoConfirmacion() {
        val imp = ImportForm(uri = "content://x")
        assertEquals(3, PasosRespaldo.pasoImportar(imp))
        assertEquals("Paso 3 de 4: escribe la contraseña del respaldo", PasosRespaldo.textoImportar(imp))
        assertEquals(4, PasosRespaldo.pasoImportar(imp.copy(contrasena = "a")))
        assertEquals("Paso 4 de 4: pulsa Importar para reemplazar tus datos", PasosRespaldo.textoImportar(imp.copy(contrasena = "a")))
        assertEquals("Paso 2 de 4: archivo comprobado", PasosRespaldo.COMPROBADO)
    }

    @Test fun lasEtapasDeLaOperacionNoSeLlamanPaso() {
        assertTrue(EtapasRespaldo.progreso(EtapaRespaldo.CIFRANDO).etiqueta.startsWith("Etapa 2 de 3"))
    }
}
