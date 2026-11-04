package cu.shalev.domain

import cu.shalev.domain.validation.Validadores
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.UsuarioActual
import cu.shalev.domain.usecase.CerrarTurno
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.DatosTransferencia
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import cu.shalev.domain.usecase.GuardarInsumo
import cu.shalev.domain.usecase.GuardarPerfil
import cu.shalev.domain.usecase.GuardarProducto
import cu.shalev.domain.usecase.ObservarAlertas
import cu.shalev.domain.usecase.RegistrarVenta
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers

class UseCasesTest {
    private val clock = FixedClock()
    private val insumos = FakeInsumos()
    private val productos = FakeProductos().also { it.insumos = insumos }
    private val turnos = FakeTurnos()
    private val ventas = FakeVentas(productos, turnos)
    private val precios = FakePrecios()
    private val perfil = FakePerfil()

    private val registrar = RegistrarVenta(CotizarVenta(productos, precios, FakeInsumos(), FakeServicios()), turnos, ventas, perfil, clock)
    private val guardarProducto = GuardarProducto(productos, insumos, clock)

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")

    @Test fun `sin turno abierto no se vende`() = runBlocking {
        productos.put(producto(1))
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO)))
    }

    @Test fun `venta en efectivo descuenta stock y abrir dos turnos falla`() = runBlocking {
        productos.put(producto(1, cantidad = 5))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        assertEquals(AppError.TurnoYaAbierto, err(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO)))
        val id = ok(registrar(listOf(LineaSolicitada(1, 2)), MetodoPago.EFECTIVO))
        assertEquals(3L, productos.obtener(1)!!.cantidad)
        assertEquals(Cup.ofPesos(200), ventas.obtener(id)!!.total)
        ok(CerrarTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO)))
    }

    @Test fun `transferencia exige y normaliza datos del cliente y congela la cuenta de cobro`() = runBlocking {
        productos.put(producto(1))
        perfil.state.value = Perfil(tarjetas = listOf(TarjetaBancaria(7, "9200129912345678")), telefonos = listOf(Telefono(8, "+5351815604")), pagoTarjetaId = 7, pagoTelefonoId = 8)
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        assertTrue(err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.TRANSFERENCIA)) is AppError.Validacion)
        val malo = DatosTransferencia(DatosCliente("Ana", "123", "5123"), "TMW1234567")
        assertEquals("clienteCi", (err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.TRANSFERENCIA, malo)) as AppError.Validacion).campo)

        val bueno = DatosTransferencia(DatosCliente("  Ana   Pérez ", "85010112345", "51234567"), "tmw1234567")
        val v = ventas.obtener(ok(registrar(listOf(LineaSolicitada(1, 2)), MetodoPago.TRANSFERENCIA, bueno)))!!
        val t = v.transaccion!!
        assertEquals("Ana Pérez", t.cliente.nombreApellidos)
        assertEquals("+5351234567", t.cliente.telefono)
        assertEquals("TMW1234567", t.numero)
        assertEquals(v.total, t.importe)
        assertEquals("9200129912345678", t.tarjetaCobro)
        assertEquals("+5351815604", t.telefonoCobro)
    }

    @Test fun `elaborado calcula su costo desde la receta y limpia campos no permitidos`() = runBlocking {
        insumos.put(insumo(1, precio = 40), insumo(2, precio = 8))
        val receta = Receta(0, listOf(RecetaLinea(1, Cantidad(250)), RecetaLinea(2, Cantidad.enteras(2))))  // 10 + 16
        val p = producto(0, categoria = "elaborado").copy(fotoUri = "x")
        val id = ok(guardarProducto(p, receta))
        val guardado = productos.obtener(id)!!
        assertEquals(Categorias.ELABORADO, guardado.categoria)
        assertEquals(Cup.ofPesos(26), guardado.precioCosto)
        assertNull(guardado.fotoUri)
        assertEquals(AppError.Validacion("receta", AppError.Regla.REQUERIDO), err(guardarProducto(p, null)))
    }

    @Test fun `cambiar precio de un insumo recalcula el costo de los elaborados`() = runBlocking {
        insumos.put(insumo(1, precio = 10))
        val id = ok(guardarProducto(producto(0, categoria = Categorias.ELABORADO), Receta(0, listOf(RecetaLinea(1, Cantidad.enteras(3))))))
        assertEquals(Cup.ofPesos(30), productos.obtener(id)!!.precioCosto)
        ok(GuardarInsumo(insumos, productos, clock)(insumo(1, precio = 12)))
        assertEquals(Cup.ofPesos(36), productos.obtener(id)!!.precioCosto)
    }

    @Test fun `elaborado se guarda sin existencias ni niveles propios`() = runBlocking {
        insumos.put(insumo(1, nombre = "Harina", cantidad = 1))
        val p = producto(0, categoria = Categorias.ELABORADO, cantidad = 12, bajo = 5, critico = 1)
        val id = ok(guardarProducto(p, Receta(0, listOf(RecetaLinea(1, Cantidad(300))))))
        val g = productos.obtener(id)!!
        assertEquals(0L, g.cantidad); assertNull(g.nivelBajo); assertNull(g.nivelCritico)
        assertTrue(Validadores.producto(g.copy(cantidad = 3)).any { it.campo == "cantidad" })
    }

    @Test fun `alertas reaccionan a los niveles configurados`() = runBlocking {
        productos.put(producto(1, cantidad = 8))
        val prefs = FakePreferencias()
        val alertas = ObservarAlertas(productos, insumos, prefs, clock, Dispatchers.Unconfined)
        assertEquals(0, alertas().first().stockBajo)
        prefs.guardarNiveles(prefs.state.value.niveles.copy(productoBajo = 10))
        assertEquals(1, alertas().first().stockBajo)
    }

    @Test fun `perfil se normaliza y valida`() = runBlocking {
        val r = GuardarPerfil(perfil)(Perfil(nombre = " Ana ", ci = "85010112345", tarjetas = listOf(TarjetaBancaria(numero = "9200 1299 1234 5678")), telefonos = listOf(Telefono(numero = "5123 4567")), pagoTarjetaId = 99))
        ok(r)
        val p = perfil.state.value
        assertEquals("Ana", p.nombre)
        assertEquals("9200129912345678", p.tarjetas.single().numero)
        assertEquals("+5351234567", p.telefonos.single().numero)
        assertNull(p.pagoTarjetaId)
        assertEquals(AppError.Validacion("tarjetas"), err(GuardarPerfil(perfil)(Perfil(tarjetas = listOf(TarjetaBancaria(numero = "12"))))))
    }

    @Test fun `numero de transaccion desde el SMS`() {
        val x = ExtraerNumeroTransaccion()
        assertEquals("TMW12345678", x("La transferencia fue completada. Transaccion: TMW12345678. Monto: 1450 CUP"))
        assertEquals("KW98765432", x("Nro. Transacción kw98765432 realizada"))
        assertNull(x("Hola, ¿cómo estás?"))
    }

    @Test fun `el respaldo borra la contrasena de memoria incluso si es invalida`() = runBlocking {
        val repo = object : RespaldoRepository {
            override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit) =
                AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
            override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = error("no usado")
            override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> = error("no usado")
        }
        val corta = "1234".toCharArray()
        assertTrue(err(ExportarRespaldo(repo)(corta, ByteArrayOutputStream())) is AppError.Validacion)
        assertTrue(corta.all { it == '\u0000' })
        val buena = "clave-segura".toCharArray()
        ok(ExportarRespaldo(repo)(buena, ByteArrayOutputStream()))
        assertTrue(buena.all { it == '\u0000' })
        ok(ExportarRespaldo(repo)(CharArray(0), ByteArrayOutputStream())) // 0.27.0 (T10): sin contraseña
        Unit
    }
}
