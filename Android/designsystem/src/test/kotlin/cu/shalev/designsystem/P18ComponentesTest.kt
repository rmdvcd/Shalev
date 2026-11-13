package cu.shalev.designsystem

import android.view.HapticFeedbackConstants
import cu.shalev.designsystem.component.ShalevHaptics
import cu.shalev.designsystem.component.ShalevStepperTextos
import cu.shalev.designsystem.component.ShalevTab
import cu.shalev.designsystem.component.ShalevValidacion
import cu.shalev.designsystem.component.TipoHaptico
import cu.shalev.designsystem.theme.ShalevTypography
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** P18: partes puras de los componentes nuevos (sin Compose ni dispositivo). */
class P18ComponentesTest {
    @Test fun hapticaUsaConfirmYRejectDesdeAndroid11YEquivalentesAntes() {
        assertEquals(HapticFeedbackConstants.CONFIRM, ShalevHaptics.constante(TipoHaptico.Exito, 30))
        assertEquals(HapticFeedbackConstants.REJECT, ShalevHaptics.constante(TipoHaptico.Error, 35))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, ShalevHaptics.constante(TipoHaptico.Exito, 26))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, ShalevHaptics.constante(TipoHaptico.Error, 29))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, ShalevHaptics.constante(TipoHaptico.Seleccion, 26))
    }

    @Test fun textosDelAsistente() {
        assertEquals("Paso 2 de 3", ShalevStepperTextos.paso(2, 3))
        assertEquals("Paso 2 de 3: Enviar solicitud", ShalevStepperTextos.anuncio(2, 3, "Enviar solicitud"))
    }

    @Test fun pestanaConContadorLoLeeTrasElNombre() {
        assertEquals("Insumos", ShalevTab("Insumos").texto)
        assertEquals("Insumos (2)", ShalevTab("Insumos", 2).texto)
    }

    // ---- Pregunta 2 (opción 2): texto de lectura +1sp ----

    @Test fun tipografiaDeLecturaSubida1sp() {
        assertEquals(14f, ShalevTypography.bodyMedium.fontSize.value)
        assertEquals(13f, ShalevTypography.bodySmall.fontSize.value)
        assertEquals(15f, ShalevTypography.bodyLarge.fontSize.value)
        assertEquals(11f, ShalevTypography.labelSmall.fontSize.value)
    }

    // ---- A16: regla común de validación ----

    @Test fun sinValidarAlSalirElErrorSeVeAlMomento() {
        assertTrue(ShalevValidacion.errorVisible(hayError = true, validarAlSalir = false, forzar = false, salio = false))
        assertFalse(ShalevValidacion.errorVisible(hayError = false, validarAlSalir = false, forzar = true, salio = true))
    }

    @Test fun conValidarAlSalirSoloTrasSalirDelCampoEditadoOAlGuardar() {
        assertFalse(ShalevValidacion.errorVisible(true, validarAlSalir = true, forzar = false, salio = false))
        assertTrue(ShalevValidacion.errorVisible(true, validarAlSalir = true, forzar = false, salio = true))
        assertTrue(ShalevValidacion.errorVisible(true, validarAlSalir = true, forzar = true, salio = false))
    }

    @Test fun campoVacioSinFocoSoloSeMarcaAlGuardar() {
        assertFalse(ShalevValidacion.errorVisible(true, true, forzar = false, salio = true, vacioSinFoco = true))
        assertTrue(ShalevValidacion.errorVisible(true, true, forzar = true, salio = true, vacioSinFoco = true))
    }

    @Test fun salirSinHaberEscritoNoCuentaYUnaVezSalidoSeQueda() {
        assertFalse(ShalevValidacion.salioTrasEditar(salio = false, teniaFoco = true, tieneFoco = false, editado = false))
        assertFalse(ShalevValidacion.salioTrasEditar(salio = false, teniaFoco = false, tieneFoco = true, editado = true))
        assertTrue(ShalevValidacion.salioTrasEditar(salio = false, teniaFoco = true, tieneFoco = false, editado = true))
        assertTrue(ShalevValidacion.salioTrasEditar(salio = true, teniaFoco = false, tieneFoco = true, editado = true))
    }
}
