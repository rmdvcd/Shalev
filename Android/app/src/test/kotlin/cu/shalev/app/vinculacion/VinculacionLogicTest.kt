package cu.shalev.app.vinculacion

import cu.shalev.core.result.AppError
import cu.shalev.domain.model.Empleado
import cu.shalev.domain.model.EstadoConexion
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.TipoApp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinculacionLogicTest {
    private val ahora = Instant.parse("2026-10-02T12:00:00Z")
    private fun empleado(vinculado: Instant? = null, vence: Instant? = null, ultima: Instant? = null) =
        Empleado(1, "Luis", PermisoEmpleado.PREDETERMINADOS, ahora, vinculado, ultima, vence)

    @Test fun subtituloDeAjustesSegunTipo() {
        assertEquals("Principal · Vincula las apps de tus empleados", VinculacionLogic.subtituloAjustes(TipoApp.PRINCIPAL, 0, ""))
        assertEquals("Principal · 1 secundaria", VinculacionLogic.subtituloAjustes(TipoApp.PRINCIPAL, 1, ""))
        assertEquals("Principal · 3 secundarias", VinculacionLogic.subtituloAjustes(TipoApp.PRINCIPAL, 3, ""))
        assertEquals("Secundaria de María", VinculacionLogic.subtituloAjustes(TipoApp.SECUNDARIA, 0, "María"))
    }

    @Test fun estadoDeCadaSecundaria() {
        assertEquals("Conectada ahora", VinculacionLogic.estadoEmpleado(empleado(vinculado = ahora), true, ahora))
        assertTrue(VinculacionLogic.estadoEmpleado(empleado(vence = ahora.plusSeconds(300)), false, ahora).startsWith("Esperando el código"))
        assertEquals("Sin vincular · genera un código nuevo", VinculacionLogic.estadoEmpleado(empleado(vence = ahora.minusSeconds(1)), false, ahora))
        assertTrue(VinculacionLogic.estadoEmpleado(empleado(vinculado = ahora, ultima = ahora), false, ahora).startsWith("Última vez:"))
        assertEquals("Vinculada · aún no ha sincronizado", VinculacionLogic.estadoEmpleado(empleado(vinculado = ahora), false, ahora))
    }

    @Test fun turnoYCierrePedidoDeCadaSecundaria() {
        val e = empleado(vinculado = ahora)
        assertEquals(null, VinculacionLogic.turnoEmpleado(e, false))
        val abierto = e.copy(turnoAbiertoDesde = ahora)
        assertTrue(VinculacionLogic.turnoEmpleado(abierto, false)!!.startsWith("Turno abierto desde las "))
        val pedido = abierto.copy(cierreSolicitadoEn = ahora)
        assertEquals("Cierre pedido · se cerrará al conectar", VinculacionLogic.turnoEmpleado(pedido, false))
        assertEquals("Cierre pedido · se cerrará al terminar la venta en curso", VinculacionLogic.turnoEmpleado(pedido, true))
        assertEquals("Cierre pedido · se cerrará al conectar\nVinculada · aún no ha sincronizado", VinculacionLogic.subtituloEmpleado(pedido, false, ahora))
        assertEquals("La predeterminada (•••• 1111)", VinculacionLogic.predeterminada("•••• 1111"))
    }

    @Test fun aliasYNumeroDeTarjetaVanEnFilasSeparadas() {
        assertEquals("Cuenta principal\n•••• 1234", VinculacionLogic.etiquetaTarjeta("Cuenta principal", "•••• 1234"))
        assertEquals("•••• 1234", VinculacionLogic.etiquetaTarjeta(" ", "•••• 1234"))
    }

    @Test fun textosDeConexionYPendientes() {
        assertEquals("Conectada con la app principal", VinculacionLogic.conexion(EstadoConexion.Conectada(ahora)))
        assertEquals("motivo", VinculacionLogic.conexion(EstadoConexion.SinConexion("motivo")))
        assertEquals("Todo enviado", VinculacionLogic.pendientes(0))
        assertEquals("1 pendiente de enviar", VinculacionLogic.pendientes(1))
        assertEquals("4 pendientes de enviar", VinculacionLogic.pendientes(4))
    }

    @Test fun erroresDelNombreVanAlCampoYLosDemasAlAviso() {
        assertEquals("Escribe el nombre del empleado.", VinculacionLogic.errorNombre(AppError.Validacion("nombre", AppError.Regla.REQUERIDO)))
        assertEquals("Ya hay una app con ese nombre.", VinculacionLogic.errorNombre(AppError.Duplicado("nombre")))
        assertNull(VinculacionLogic.errorNombre(AppError.SinPermiso))
        assertTrue(VinculacionLogic.error(AppError.SinPermiso).contains("cubre tu licencia")) // 0.21.0 (C4)
        assertTrue(VinculacionLogic.error(AppError.SinPrincipal(2)).contains("2 envíos pendientes"))
    }
}
