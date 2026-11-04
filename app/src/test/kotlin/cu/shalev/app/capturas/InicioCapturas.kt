package cu.shalev.app.capturas

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import cu.shalev.app.inicio.AccionesInicio
import cu.shalev.app.inicio.AlertaUi
import cu.shalev.app.inicio.DialogoInicio
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.inicio.InicioContent
import cu.shalev.app.inicio.InicioUiState
import cu.shalev.app.inicio.PagoResumen
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.domain.model.GraficosPeriodo
import cu.shalev.domain.model.Granularidad
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.Periodo
import cu.shalev.domain.model.Porcion
import cu.shalev.domain.model.PuntoSerie
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.Serie
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.Top3
import cu.shalev.domain.model.TopItem
import cu.shalev.domain.model.TopPersona
import cu.shalev.domain.model.Turno
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Inicio: banner, alertas, turno, gráficos (barras/área/donut), Top 3 y los diálogos de venta y cierre. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Captura.TELEFONO, application = Application::class)
class InicioCapturas {
    @get:Rule val rule = createComposeRule()

    private val acciones = AccionesInicio(
        onNavigate = {}, onTurno = {}, onNuevaVenta = {}, onElegirVenta = {}, onConfirmarCierre = {},
        onCerrarDialogo = {}, onPeriodo = {}, onPago = {}, onReintentar = {},
    )
    private val abierto = Turno(7, Captura.AHORA.minusSeconds(8 * 3600), abiertoPor = "Ana Pérez")

    private val serie = Serie(
        Granularidad.HORA,
        listOf(9, 10, 11, 12, 13, 14, 15, 16).mapIndexed { i, h ->
            val ventas = longArrayOf(1450, 3200, 2100, 5600, 4300, 2900, 6100, 3800)[i]
            PuntoSerie(Captura.AHORA.minusSeconds((16L - h) * 3600), Cup.ofPesos(ventas), Cup.ofPesos(ventas * 6 / 10))
        },
    )
    private val graficos = GraficosPeriodo(Periodo.DeTurno(7), serie, abierto)
    private fun top(n: String, u: Long, i: Long, g: Long) = TopItem(u, n, u, Cup.ofPesos(i), Cup.ofPesos(g))
    private val resumen = ResumenGeneral(
        categorias = listOf(Porcion("Bebidas", 52, 0.52), Porcion("Elaborado", 28, 0.28), Porcion("Confituras", 20, 0.20)),
        metodosPago = listOf(Porcion("Efectivo", 70, 0.70), Porcion("Transferencia", 30, 0.30)),
        top3 = Top3(
            listOf(top("Refresco de lata", 48, 7200, 2400), top("Pizza napolitana", 22, 8800, 3400), top("Galletas", 15, 2250, 750)),
            listOf(top("Aceite 1 L", 1, 900, 120)),
            listOf(top("Pizza napolitana", 22, 8800, 3400), top("Refresco de lata", 48, 7200, 2400)),
        ),
        dias = 30,
        empleados = listOf(
            TopPersona("Ana Pérez", Cup.ofPesos(15200), 9),
            TopPersona("Jorge Ruiz", Cup.ofPesos(9800), 6),
            TopPersona("María Torres", Cup.ofPesos(3400), 2),
        ),
        clientes = listOf(
            TopPersona("Juan Ruiz", Cup.ofPesos(8300), 3),
            TopPersona("Ana Pérez", Cup.ofPesos(5100), 2),
        ),
    )
    private val completo = InicioUiState(
        banner = "Periodo de prueba restante: 5 días", bannerTono = BannerTone.Info,
        alertas = listOf(AlertaUi(TipoAlerta.STOCK_CRITICO, 2), AlertaUi(TipoAlerta.STOCK_BAJO, 5), AlertaUi(TipoAlerta.PROXIMO_A_CADUCAR, 1)),
        pago = PagoResumen("5234 5678", "•••• 1234"), preajustesActivos = 1, preajustesTotal = 2,
        turno = abierto, graficos = EstadoCarga.Exito(graficos), resumen = EstadoCarga.Exito(resumen),
    )

    private fun inicio(id: String, s: InicioUiState) = rule.capturar(id) { InicioContent(s, acciones, zona = Captura.ZONA, acordeonesAbiertos = true) }

    /** 0.27.0 (T2): letra al 200 % en 360 dp: nada cortado ni partido. */
    @Test @Config(qualifiers = Captura.LARGA) fun inicioLetraGrande() =
        rule.capturar("01z_inicio_letra_200") { LetraGrande { InicioContent(completo, acciones, zona = Captura.ZONA, acordeonesAbiertos = true) } }
    /** 0.30.1: al entrar, los gráficos y listas posteriores al selector de período están cerrados. */
    @Test fun inicioAcordeonesCerrados() =
        rule.capturar("01j_inicio_acordeones_cerrados") { InicioContent(completo, acciones, zona = Captura.ZONA) }
    @Test fun inicioConDatos() = inicio("01a2_inicio_con_datos_pantalla", completo)
    @Test @Config(qualifiers = Captura.LARGA) fun inicioCompletoLargo() = inicio("01a_inicio_con_datos", completo)
    @Test fun inicioSinTurno() = inicio(
        "01b_inicio_vacio",
        InicioUiState(turno = null, graficos = EstadoCarga.Vacio(), resumen = EstadoCarga.Vacio(), opcion = OpcionPeriodo.HOY),
    )
    @Test fun inicioCargando() = inicio(
        "01c_inicio_cargando_aviso",
        InicioUiState(banner = "Licencia mensual: 3 días restantes", bannerTono = BannerTone.Aviso, turno = abierto, graficos = EstadoCarga.Cargando, resumen = EstadoCarga.Cargando),
    )
    @Test fun inicioError() = inicio(
        "01d_inicio_error_critico",
        InicioUiState(
            banner = "Licencia mensual: 1 día restante", bannerTono = BannerTone.Critico,
            graficos = EstadoCarga.Error("No se pudieron cargar los datos"), resumen = EstadoCarga.Error("No se pudieron cargar los datos"),
        ),
    )
    @Test fun bannerCritico() = inicio(
        "01d2_inicio_banner_critico",
        completo.copy(banner = "Licencia mensual: 1 día restante", bannerTono = BannerTone.Critico),
    )
    @Test fun snackbarTurno() = rule.capturar("01g_inicio_snackbar_turno") {
        InicioContent(completo.copy(turno = null), acciones, snackbar = snackbarCon("Turno cerrado: 23 ventas, 18,450 CUP."), zona = Captura.ZONA, acordeonesAbiertos = true)
    }
    @Test fun dialogoNuevaVenta() = inicio("01e_inicio_dialogo_nueva_venta", completo.copy(dialogo = DialogoInicio.NuevaVenta))
    @Test fun dialogoCerrarTurno() = inicio("01f_inicio_dialogo_cerrar_turno", completo.copy(dialogo = DialogoInicio.CerrarTurno))

    // 0.20.0: alerta de existencias negativas (H6) y cierre de turno pedido por el encargado (H5, app secundaria).
    @Test fun alertaNegativos() = inicio(
        "01g_inicio_existencias_negativas",
        completo.copy(banner = null, alertas = listOf(AlertaUi(TipoAlerta.SIN_EXISTENCIA, 2), AlertaUi(TipoAlerta.STOCK_BAJO, 5))),
    )
    @Test fun cierrePedido() = inicio("01h_inicio_cierre_pedido", completo.copy(banner = null, cierrePedido = true))
    @Test fun cierreHecho() = inicio(
        "01i_inicio_turno_cerrado_por_encargado",
        completo.copy(banner = null, turno = null, avisoCierre = true, graficos = EstadoCarga.Vacio(), resumen = EstadoCarga.Vacio()),
    )
}
