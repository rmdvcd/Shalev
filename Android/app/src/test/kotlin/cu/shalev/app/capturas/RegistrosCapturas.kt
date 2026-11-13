package cu.shalev.app.capturas

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.registros.AccionesRegistros
import cu.shalev.app.registros.FichaRegistro
import cu.shalev.app.registros.PestanaRegistros
import cu.shalev.app.registros.PestanaTurno
import cu.shalev.app.registros.RegistrosContent
import cu.shalev.app.registros.RegistrosUiState
import cu.shalev.app.registros.TurnoDetalleContent
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleTurno
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.usecase.TipoRegistro
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Registros: tablas de Ventas, Transferencias, Movimientos y Turnos, ventanas, filtro, exportar y detalle de turno. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Captura.TELEFONO, application = Application::class)
class RegistrosCapturas {
    @get:Rule val rule = createComposeRule()

    private val t0 = Captura.AHORA
    private fun det(id: Long, nombre: String, cat: String, n: Long, precio: Long) = DetalleVenta(
        productoId = id, nombre = nombre, categoria = cat, cantidad = n,
        precioBase = Cup.ofPesos(precio), precioUnitario = Cup.ofPesos(precio), costoUnitario = Cup.ofPesos(precio * 6 / 10),
    )
    private val ventas = listOf(
        Venta(id = 15, turnoId = 7, fecha = t0.minusSeconds(600), metodoPago = MetodoPago.TRANSFERENCIA,
            detalles = listOf(det(4, "Pizza napolitana", "Elaborado", 2, 400), det(1, "Refresco de lata 355 ml", "Bebidas", 2, 150))),
        Venta(id = 14, turnoId = 7, fecha = t0.minusSeconds(2400), metodoPago = MetodoPago.EFECTIVO,
            detalles = listOf(det(1, "Refresco de lata 355 ml", "Bebidas", 6, 150))),
        Venta(id = 13, turnoId = 7, fecha = t0.minusSeconds(5400), metodoPago = MetodoPago.EFECTIVO,
            detalles = listOf(det(2, "Galletas de chocolate", "Confituras", 3, 150), det(5, "Malta 330 ml", "Bebidas", 1, 180))),
        Venta(id = 12, turnoId = 7, fecha = t0.minusSeconds(9000), metodoPago = MetodoPago.EFECTIVO,
            detalles = listOf(det(6, "Arroz 1 kg", "Bodega", 2, 320))),
    )
    private val transferencias = listOf(
        Transaccion(id = 3, ventaId = 15, fecha = t0.minusSeconds(600), importe = Cup.ofPesos(1100), numero = "MM10040FEJ987",
            cliente = DatosCliente("Ana Díaz Rodríguez", "90020212345", "53123456")),
        Transaccion(id = 2, ventaId = 9, fecha = t0.minusSeconds(86_400), importe = Cup.ofPesos(450), numero = "MM10040FEA112",
            cliente = DatosCliente("Luis Martínez", "88111509876", "52987654")),
    )
    private val movimientos = listOf(
        ItemMovimiento(MovimientoInventario(id = 9, fecha = t0.minusSeconds(600), tipo = TipoMovimiento.VENTA, entidad = TipoEntidad.PRODUCTO,
            entidadId = 4, nombre = "Pizza napolitana", delta = -2, existenciaResultante = 4), "u"),
        ItemMovimiento(MovimientoInventario(id = 8, fecha = t0.minusSeconds(3600), tipo = TipoMovimiento.CONSUMO, entidad = TipoEntidad.INSUMO,
            entidadId = 1, nombre = "Harina de trigo", delta = -1500, existenciaResultante = 8500), "kg"),
        ItemMovimiento(MovimientoInventario(id = 7, fecha = t0.minusSeconds(3600), tipo = TipoMovimiento.PRODUCCION, entidad = TipoEntidad.PRODUCTO,
            entidadId = 4, nombre = "Pizza napolitana", delta = 6, existenciaResultante = 6), "u"),
    )
    private val turnos = listOf(
        Turno(7, t0.minusSeconds(8 * 3600), abiertoPor = "Ana Pérez"),
        Turno(6, t0.minusSeconds(32 * 3600), t0.minusSeconds(23 * 3600), abiertoPor = "Ana Pérez", cerradoPor = "Ana Pérez",
            resumen = ResumenTurno.VACIO.copy(numVentas = 23, total = Cup.ofPesos(18_450), totalEfectivo = Cup.ofPesos(12_300), totalTransferencia = Cup.ofPesos(6_150))),
        Turno(5, t0.minusSeconds(56 * 3600), t0.minusSeconds(47 * 3600), abiertoPor = "Luis", cerradoPor = "Luis",
            resumen = ResumenTurno.VACIO.copy(numVentas = 17, total = Cup.ofPesos(11_200), totalEfectivo = Cup.ofPesos(11_200))),
    )
    private val ventasState = RegistrosUiState(vista = EstadoCarga.Exito(VistaRegistro.Ventas(ventas)))
    private val transfState = RegistrosUiState(pestana = PestanaRegistros.TRANSFERENCIAS, vista = EstadoCarga.Exito(VistaRegistro.Transferencias(transferencias)))
    private val movState = RegistrosUiState(pestana = PestanaRegistros.MOVIMIENTOS, vista = EstadoCarga.Exito(VistaRegistro.Movimientos(movimientos)))

    private fun reg(id: String, s: RegistrosUiState, t: EstadoCarga<List<Turno>> = EstadoCarga.Exito(turnos)) =
        rule.capturar(id) { RegistrosContent(s, t, AccionesRegistros(), zona = Captura.ZONA) }

    @Test fun tablaVentas() = reg("04a_registros_ventas", ventasState)
    @Test fun ventasCargando() = reg("04a2_registros_cargando", RegistrosUiState())
    @Test fun ventasVacio() = reg("04e_registros_vacio", RegistrosUiState(vista = EstadoCarga.Vacio(VistaRegistro.Ventas(emptyList()))))
    @Test fun ventasSinResultados() = reg(
        "04g_registros_sin_resultados",
        RegistrosUiState(filtros = mapOf(TipoRegistro.VENTAS to FiltroRegistros(texto = "zzz")), vista = EstadoCarga.Exito(VistaRegistro.Ventas(emptyList()))),
    )
    @Test fun ventasError() = reg("04h_registros_error", RegistrosUiState(vista = EstadoCarga.Error("No se pudieron cargar los registros")))
    @Test fun fichaVenta() = reg("04o_registros_ficha_venta", ventasState.copy(ficha = FichaRegistro.DeVenta(ventas[0])))
    @Test fun hojaFiltro() = reg("04i_registros_hoja_filtro", ventasState.copy(hojaFiltro = true))
    // 0.20.0 (H1): con apps secundarias, la ficha dice quién vendió y el filtro permite elegirlo.
    @Test fun fichaVentaVendedor() = reg("04o2_registros_ficha_venta_vendedor", ventasState.copy(ficha = FichaRegistro.DeVenta(ventas[0].copy(vendedor = "Luis"))))
    @Test fun hojaFiltroVendedor() = reg(
        "04i2_registros_hoja_filtro_vendedor", ventasState.copy(hojaFiltro = true, vendedores = listOf("Ana Pérez", "Luis", "Marta")),
    )
    @Test fun hojaExportar() = reg("04m2_registros_hoja_exportar_ventas", ventasState.copy(hojaExportar = true))
    @Test fun exportando() = reg("04n_registros_exportando", ventasState.copy(exportando = true))
    @Test fun tablaTransferencias() = reg("04b_registros_transferencias", transfState)
    @Test fun fichaTransferencia() = reg("04p_registros_ficha_transferencia", transfState.copy(ficha = FichaRegistro.DeTransferencia(transferencias[0])))
    @Test fun exportarTransferencias() = reg("04m_registros_hoja_exportar", transfState.copy(hojaExportar = true))
    @Test fun tablaMovimientos() = reg("04c_registros_movimientos", movState)
    @Test fun fichaMovimiento() = reg("04q_registros_ficha_movimiento", movState.copy(ficha = FichaRegistro.DeMovimiento(movimientos[1])))
    @Test fun filtroMovimientos() = reg("04k_registros_hoja_filtro_movimientos", movState.copy(hojaFiltro = true))
    @Test fun turnos() = reg("04d_registros_turnos", RegistrosUiState(pestana = PestanaRegistros.TURNOS))
    @Test fun turnosVacio() = reg("04f_registros_turnos_vacio", RegistrosUiState(pestana = PestanaRegistros.TURNOS), EstadoCarga.Vacio())

    private fun detalle(id: String, d: EstadoCarga<DetalleTurno>, pestana: PestanaTurno = PestanaTurno.RESUMEN) =
        rule.capturar(id) { TurnoDetalleContent(d, onBack = {}, onReintentar = {}, zona = Captura.ZONA, ahora = Captura.AHORA, pestanaInicial = pestana) }

    @Test fun detalleAbierto() = detalle("04r_turno_detalle_abierto", EstadoCarga.Exito(DetalleTurno(turnos[0], ventas, movimientos.map { it.movimiento }, emptyList())))
    @Test fun detalleVentas() = detalle(
        "04r2_turno_detalle_ventas", EstadoCarga.Exito(DetalleTurno(turnos[0], ventas, movimientos.map { it.movimiento }, emptyList())), PestanaTurno.VENTAS,
    )
    @Test fun detalleInventario() = detalle(
        "04r3_turno_detalle_inventario", EstadoCarga.Exito(DetalleTurno(turnos[0], ventas, movimientos.map { it.movimiento }, emptyList())), PestanaTurno.INVENTARIO,
    )
    @Test fun detalleCerrado() = detalle("04s_turno_detalle_cerrado", EstadoCarga.Exito(DetalleTurno(turnos[1], ventas.take(2), emptyList(), emptyList())))
    @Test fun detalleSinActividad() = detalle("04t_turno_detalle_sin_actividad", EstadoCarga.Exito(DetalleTurno(turnos[0], emptyList(), emptyList(), emptyList())))
    @Test fun detalleError() = detalle("04t2_turno_detalle_error", EstadoCarga.Error("No se pudo cargar el turno"))
}
