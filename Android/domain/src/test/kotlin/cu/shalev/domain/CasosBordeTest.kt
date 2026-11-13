package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.service.Estadisticas
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.CerrarTurno
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.DatosTransferencia
import cu.shalev.domain.usecase.ImportarRespaldo
import cu.shalev.domain.usecase.ObservarPermisoVenta
import cu.shalev.domain.usecase.ProgramaLicencia
import cu.shalev.domain.usecase.RegistrarVenta
import cu.shalev.domain.usecase.UsuarioActual
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos borde del Prompt 15 reunidos en un solo lugar (los de licencia GL están en :licencia → CasosBordeLicenciaTest).
 * Complementa, sin repetir, VentaFlujoTest, TurnoUseCasesTest, StockEstadisticasTest y EscanerInventarioTest.
 */
class CasosBordeTest {
    private val clock = FixedClock()
    private val insumos = FakeInsumos()
    private val productos = FakeProductos().also { it.insumos = insumos }
    private val turnos = FakeTurnos()
    private val ventas = FakeVentas(productos, turnos)
    private val perfil = FakePerfil()
    private val cotizar = CotizarVenta(productos, FakePrecios(), insumos, FakeServicios())
    private val registrar = RegistrarVenta(cotizar, turnos, ventas, perfil, clock)

    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")

    // ---------------- Sin turno abierto ----------------

    @Test fun sinTurnoNoSeVendeNiEnEfectivoNiPorTransferenciaYNoSeTocaElStock() = runBlocking {
        productos.put(producto(1, "Refresco", cantidad = 5))
        assertEquals(ObservarPermisoVenta.Permiso.SinTurno, ObservarPermisoVenta(turnos)().first())
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO)))
        val datos = DatosTransferencia(DatosCliente("Ana Díaz", "90020212345", "+5353000000"), "BR601ADLM8997")
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.TRANSFERENCIA, datos)))
        assertTrue(ventas.ventas.isEmpty())
        assertEquals(5L, productos.obtener(1)!!.cantidad)
    }

    @Test fun cerrarSinTurnoAbiertoEsUnErrorExplicitoYAbrirDosVecesTambien() = runBlocking {
        val usuario = UsuarioActual(perfil)
        assertEquals(AppError.TurnoCerrado, err(CerrarTurno(turnos, usuario, clock)(Cup.ZERO)))
        AbrirTurno(turnos, usuario, clock)(Cup.ZERO)
        assertEquals(AppError.TurnoYaAbierto, err(AbrirTurno(turnos, usuario, clock)(Cup.ZERO)))
    }

    @Test fun turnoCerradoEntreCotizarYConfirmarNoRegistraLaVenta() = runBlocking {
        productos.put(producto(1, "Refresco", cantidad = 5))
        AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO)
        assertTrue(cotizar(listOf(LineaSolicitada(1, 2)), MetodoPago.EFECTIVO) is AppResult.Ok) // comprobante en pantalla
        CerrarTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO)                                      // otro cierra el turno
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(1, 2)), MetodoPago.EFECTIVO)))
        assertEquals(5L, productos.obtener(1)!!.cantidad)
    }

    // ---------------- Licencia vencida / perpetua (programación de revisiones) ----------------

    @Test fun licenciaVencidaOPerpetuaSeRevisaCadaQuinceMinutosComoMaximo() {
        val ahora = T0
        assertEquals(ProgramaLicencia.MAXIMO, ProgramaLicencia.proximaRevision(LicenseState.Expired(TipoLicencia.MENSUAL), ahora))
        assertEquals(ProgramaLicencia.MAXIMO, ProgramaLicencia.proximaRevision(LicenseState.Perpetual("p"), ahora))
        assertEquals(ProgramaLicencia.MAXIMO, ProgramaLicencia.proximaRevision(LicenseState.TrialExpired, ahora))
    }

    @Test fun activaQueVenceProntoSeRevisaJustoDespuesDelVencimiento() {
        val ahora = T0
        val enDosMinutos = LicenseState.Active(TipoLicencia.MENSUAL, ahora.plusSeconds(120), "id")
        assertEquals(Duration.ofSeconds(121), ProgramaLicencia.proximaRevision(enDosMinutos, ahora))
        // Ya vencida pero aún no re-evaluada (reloj adelantado): mínimo, nunca un intervalo negativo.
        val vencida = LicenseState.Active(TipoLicencia.MENSUAL, ahora.minusSeconds(3_600), "id")
        assertEquals(ProgramaLicencia.MINIMO, ProgramaLicencia.proximaRevision(vencida, ahora))
    }

    // ---------------- Archivo corrupto ----------------

    /** El repositorio real detecta el daño en BackupCipher (ver BackupCipherTest); aquí, que el dominio lo propaga. */
    private class RespaldoDanado(private val incompleto: Boolean) : RespaldoRepository {
        var importaciones = 0
        override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit) =
            AppResult.Err(AppError.Almacenamiento)
        override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = AppResult.Err(AppError.ArchivoDanado(incompleto))
        override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            importaciones++; return AppResult.Err(AppError.ArchivoDanado(incompleto))
        }
    }

    @Test fun archivoCortadoODanadoSePropagaYLaContrasenaSeBorraIgual() = runBlocking {
        listOf(true, false).forEach { incompleto ->
            val repo = RespaldoDanado(incompleto)
            assertEquals(AppError.ArchivoDanado(incompleto), err(repo.inspeccionar("x".byteInputStream())))
            val clave = "secreta12".toCharArray()
            assertEquals(AppError.ArchivoDanado(incompleto), err(ImportarRespaldo(repo)("x".byteInputStream(), clave)))
            assertArrayEquals(CharArray(9), clave)
        }
    }

    /** 0.27.0 (T10): sin contraseña también se intenta (el archivo puede no tenerla); el repositorio decide. */
    @Test fun sinContrasenaLoDecideElArchivo() = runBlocking {
        val repo = RespaldoDanado(true)
        assertEquals(AppError.ArchivoDanado(true), err(ImportarRespaldo(repo)(ByteArray(0).inputStream(), CharArray(0))))
        assertEquals(1, repo.importaciones)
    }

    // ---------------- Empates en el Top 3 ----------------

    private fun venta(vararg d: Triple<Long, Long, Pair<Long, Long>>) = Venta(1, 1, T0, MetodoPago.EFECTIVO, d.map { (pid, cant, pc) ->
        DetalleVenta(productoId = pid, nombre = "P$pid", categoria = "X", cantidad = cant,
            precioBase = Cup.ofPesos(pc.first), precioUnitario = Cup.ofPesos(pc.first), costoUnitario = Cup.ofPesos(pc.second))
    })

    @Test fun empateTotalEsDeterministaYRespetaElOrdenDeAparicion() {
        val ps = (1L..3L).map { producto(it, creado = T0) } // misma fecha de creación
        val vs = listOf(venta(Triple(2, 4, 100L to 50L), Triple(1, 4, 100L to 50L), Triple(3, 4, 100L to 50L)))
        val a = Estadisticas.top3(vs, ps)
        val b = Estadisticas.top3(vs, ps)
        assertEquals(a, b)
        assertEquals(listOf(2L, 1L, 3L), a.masVendidos.map { it.productoId })
        assertEquals(listOf(2L, 1L, 3L), a.rentabilidad.map { it.productoId })
    }

    @Test fun empateDeGananciaLoDecideElMargen() {
        val ps = (1L..2L).map { producto(it, creado = T0.plusSeconds(it)) }
        // Ambos ganan 100: el 1 vende 200 (margen 50 %) y el 2 vende 1000 (margen 10 %).
        val t = Estadisticas.top3(listOf(venta(Triple(1, 1, 200L to 100L), Triple(2, 1, 1000L to 900L))), ps)
        assertEquals(listOf(1L, 2L), t.rentabilidad.map { it.productoId })
    }

    @Test fun masDeTresEmpatadosSoloMuestraTresYNingunoConPerdidaEnRentabilidad() {
        val ps = (1L..5L).map { producto(it, creado = T0.plusSeconds(it)) }
        val vs = listOf(venta(Triple(1, 2, 10L to 5L), Triple(2, 2, 10L to 5L), Triple(3, 2, 10L to 5L), Triple(4, 2, 10L to 5L), Triple(5, 2, 10L to 20L)))
        val t = Estadisticas.top3(vs, ps)
        assertEquals(listOf(5L, 4L, 3L), t.masVendidos.map { it.productoId }) // empate en unidades → más recientes
        assertEquals(listOf(4L, 3L, 2L), t.rentabilidad.map { it.productoId }) // el 5 pierde dinero: fuera
        assertEquals(3, t.lentoMovimiento.size)
    }

    @Test fun productoEliminadoNoEntraEnLentoMovimiento() {
        val ps = listOf(producto(1, creado = T0), producto(2, creado = T0.plusSeconds(1)).copy(eliminado = true), producto(3, creado = T0.plusSeconds(2)))
        val t = Estadisticas.top3(listOf(venta(Triple(3, 1, 10L to 5L))), ps)
        assertEquals(listOf(1L, 3L), t.lentoMovimiento.map { it.productoId })
    }
}
