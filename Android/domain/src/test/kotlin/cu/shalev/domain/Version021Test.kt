package cu.shalev.domain

import cu.shalev.domain.model.Modulo
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.PermisosApp
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.TipoLicencia
import cu.shalev.domain.model.Vinculacion
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.secundariasPermitidas
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.21.0 (P46/P47): precio por secundaria, permisos por defecto, módulos y teléfono del empleado en el cobro. */
class Version021Test {

    @Test fun `C4 precio = base + importe por secundaria (tabla de P47)`() {
        assertEquals(6_000L, TipoLicencia.MENSUAL.precio(0))
        assertEquals(8_000L, TipoLicencia.MENSUAL.precio(2))
        assertEquals(30_000L + 3 * 5_000L, TipoLicencia.SEMESTRAL.precio(3))
        assertEquals(50_000L + 10 * 9_000L, TipoLicencia.ANUAL.precio(10))
        assertEquals(90_000L + 17_000L, TipoLicencia.PERPETUA.precio(1))
        assertEquals(6_000L, TipoLicencia.MENSUAL.precio(-3)) // nunca resta
    }

    @Test fun `C4 la prueba cubre 5 secundarias y la licencia las que diga`() {
        assertEquals(5, LicenseState.Trial(7).secundariasPermitidas)
        assertEquals(2, LicenseState.Active(TipoLicencia.MENSUAL, Instant.EPOCH, "id", secundarias = 2).secundariasPermitidas)
        assertEquals(0, LicenseState.Perpetual("id", secundarias = 0).secundariasPermitidas)
        assertTrue(Vinculacion.puedeAgregar(1, 2)); assertFalse(Vinculacion.puedeAgregar(2, 2))
        assertEquals(10, Vinculacion.SECUNDARIAS_MAX)
    }

    @Test fun `C3 permisos por defecto sin editar inventario ni cambiar precios`() {
        assertEquals(
            setOf(PermisoEmpleado.VENDER_PRODUCTOS, PermisoEmpleado.VENDER_SERVICIOS, PermisoEmpleado.EXPORTAR),
            PermisoEmpleado.PREDETERMINADOS,
        )
    }

    @Test fun `C12 Ventas arrastra Inventario y nunca quedan 0 modulos guardados`() {
        assertEquals(setOf(Modulo.VENTAS, Modulo.INVENTARIO), Modulo.normalizar(setOf(Modulo.VENTAS)))
        assertEquals(Modulo.TODOS, Modulo.normalizar(emptySet()))
        assertEquals(setOf(Modulo.SERVICIOS), Modulo.alternar(Modulo.TODOS - Modulo.SERVICIOS, Modulo.INVENTARIO) + Modulo.SERVICIOS)
        // Desmarcar Inventario desmarca Ventas; marcar Ventas marca Inventario.
        assertEquals(setOf(Modulo.SERVICIOS), Modulo.alternar(Modulo.TODOS, Modulo.INVENTARIO))
        assertEquals(Modulo.TODOS, Modulo.alternar(setOf(Modulo.SERVICIOS), Modulo.VENTAS))
        assertEquals(Modulo.TODOS, Modulo.deNombres(null))
        assertEquals(setOf(Modulo.SERVICIOS), Modulo.deNombres(listOf("SERVICIOS", "XYZ")))
    }

    @Test fun `C12 lo no elegido se oculta tambien en los permisos`() {
        val soloServicios = PermisosApp.PRINCIPAL.copy(modulos = setOf(Modulo.SERVICIOS))
        assertFalse(soloServicios.verInventario); assertFalse(soloServicios.venderProductos); assertFalse(soloServicios.verVentas)
        assertTrue(soloServicios.verServicios); assertTrue(soloServicios.venderServicios)
        val soloInventario = PermisosApp(TipoApp.PRINCIPAL, emptySet(), setOf(Modulo.INVENTARIO))
        assertTrue(soloInventario.verInventario); assertFalse(soloInventario.vender)
    }

    @Test fun `C2 el QR del empleado lleva SU telefono con la tarjeta del duenno`() {
        val perfil = Perfil(
            nombre = "Ana", tarjetas = listOf(TarjetaBancaria(1, "9200069999999999")),
            telefonos = listOf(Telefono(1, "+5351111111")), pagoTarjetaId = 1, pagoTelefonoId = 1,
        )
        val p = perfil.paraEmpleado(tarjetaId = null, telefonoId = null, telefonoEmpleado = "52223333")
        assertEquals("+5352223333", p.telefonoPago?.numero)
        assertEquals("9200069999999999", p.tarjetaPago?.numero)
        assertEquals(1, p.telefonos.size)
        // Sin teléfono del empleado (o no válido), el del dueño, como en 0.20.0.
        assertEquals("+5351111111", perfil.paraEmpleado(null, null, telefonoEmpleado = null).telefonoPago?.numero)
        assertEquals("+5351111111", perfil.paraEmpleado(null, null, telefonoEmpleado = "12").telefonoPago?.numero)
    }
}
