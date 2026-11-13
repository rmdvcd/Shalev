package cu.shalev.domain

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.usecase.EditarTarjeta
import cu.shalev.domain.usecase.EditarTelefono
import cu.shalev.domain.usecase.GuardarPerfil
import cu.shalev.domain.usecase.IntroducirPagoElectronico
import cu.shalev.domain.usecase.SeleccionarPagoElectronico
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PagoElectronicoTest {
    private val repo = FakePerfil()
    private val guardar = GuardarPerfil(repo)
    private val introducir = IntroducirPagoElectronico(repo, guardar)
    private val p get() = repo.state.value

    @Test fun `escribir telefono y cuenta los anade a las listas y los selecciona`() = runBlocking<Unit> {
        assertEquals(AppResult.Ok(Unit), introducir("5123 4567", "9205-1299-0000-1234"))
        assertEquals("+5351234567", p.telefonoPago!!.numero)
        assertEquals("9205129900001234", p.tarjetaPago!!.numero)
        assertEquals(1, p.telefonos.size)
        assertEquals(1, p.tarjetas.size)
    }

    @Test fun `un numero ya existente se reutiliza sin duplicar`() = runBlocking<Unit> {
        repo.state.value = Perfil(telefonos = listOf(Telefono(7, "+5351234567")), tarjetas = listOf(TarjetaBancaria(8, "9205129900001234")))
        assertEquals(AppResult.Ok(Unit), introducir("+53 5123-4567", "9205129900001234"))
        assertEquals(1, p.telefonos.size)
        assertEquals(7L, p.pagoTelefonoId)
        assertEquals(8L, p.pagoTarjetaId)
    }

    @Test fun `campo vacio conserva la seleccion de ese tipo`() = runBlocking<Unit> {
        repo.state.value = Perfil(telefonos = listOf(Telefono(7, "+5351234567")), pagoTelefonoId = 7)
        assertEquals(AppResult.Ok(Unit), introducir("", "9205129900001234"))
        assertEquals(7L, p.pagoTelefonoId)
        assertEquals("9205129900001234", p.tarjetaPago!!.numero)
    }

    @Test fun `formatos invalidos no guardan nada`() = runBlocking<Unit> {
        assertEquals(AppResult.Err(AppError.Validacion("telefonos")), introducir("123", null))
        assertEquals(AppResult.Err(AppError.Validacion("tarjetas")), introducir(null, "12ab"))
        assertEquals(Perfil(), p)
    }

    @Test fun `editar valida formato y duplicados`() = runBlocking<Unit> {
        repo.state.value = Perfil(
            telefonos = listOf(Telefono(1, "+5351234567"), Telefono(2, "+5359999999")),
            tarjetas = listOf(TarjetaBancaria(3, "9205129900001234"), TarjetaBancaria(4, "9205129900005678")),
        )
        assertEquals(AppResult.Err(AppError.Duplicado("telefonos")), EditarTelefono(repo, guardar)(2, "51234567", null))
        assertEquals(AppResult.Ok(Unit), EditarTelefono(repo, guardar)(2, "58888888", "Casa"))
        assertEquals(Telefono(2, "+5358888888", "Casa"), p.telefonos[1])
        assertEquals(AppResult.Err(AppError.Duplicado("tarjetas")), EditarTarjeta(repo, guardar)(4, "9205129900001234", null))
        assertEquals(AppResult.Err(AppError.Validacion("tarjetas")), EditarTarjeta(repo, guardar)(4, "1", null))
        assertEquals(AppResult.Err(AppError.NoEncontrado), EditarTarjeta(repo, guardar)(99, "9205129900001234", null))
    }

    @Test fun `seleccionar ninguno deja el pago sin configurar`() = runBlocking<Unit> {
        repo.state.value = Perfil(telefonos = listOf(Telefono(1, "+5351234567")), pagoTelefonoId = 1)
        assertEquals(AppResult.Ok(Unit), SeleccionarPagoElectronico(repo, guardar)(null, null))
        assertNull(p.telefonoPago)
    }
}
