package cu.shalev.app.capturas

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import cu.shalev.app.vinculacion.FormEmpleado
import cu.shalev.app.vinculacion.VinculacionContent
import cu.shalev.app.vinculacion.VinculacionUi
import cu.shalev.domain.model.Empleado
import cu.shalev.domain.model.EstadoConexion
import cu.shalev.domain.model.EstadoPrincipal
import cu.shalev.domain.model.EstadoSecundaria
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.model.TipoApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 0.20.0 (H10): Ajustes → Apps vinculadas en la principal (lista, ficha con cobro, pedir cierre) y en la secundaria. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Captura.TELEFONO, application = Application::class)
class VinculacionCapturas {
    @get:Rule val rule = createComposeRule()

    private val hace = Captura.AHORA.minusSeconds(3 * 3600)
    private val ana = Empleado(1, "Ana", PermisoEmpleado.PREDETERMINADOS, hace, vinculadoEn = hace, ultimaSincronizacion = Captura.AHORA)
    private val luis = Empleado(
        2, "Luis", PermisoEmpleado.PREDETERMINADOS, hace, vinculadoEn = hace, ultimaSincronizacion = hace, turnoAbiertoDesde = hace,
    )
    private val principal = VinculacionUi(
        tipo = TipoApp.PRINCIPAL,
        empleados = listOf(ana, luis.copy(cierreSolicitadoEn = Captura.AHORA)),
        principal = EstadoPrincipal(escuchando = true, direcciones = listOf("192.168.43.1"), conectadas = setOf(1L)),
        tarjetas = listOf(TarjetaBancaria(1, "9200000000001234", "BANDEC"), TarjetaBancaria(2, "9200000000005678", "Metro")),
        telefonos = listOf(Telefono(1, "+5352345678"), Telefono(2, "+5356667777", "Kiosco")),
        tarjetaPredeterminada = TarjetaBancaria(1, "9200000000001234", "BANDEC"),
        telefonoPredeterminado = Telefono(1, "+5352345678"),
    )

    private fun cap(id: String, s: VinculacionUi) = rule.capturar(id) { VinculacionContent(s) }

    @Test fun principalConEmpleados() = cap("07p_vinculacion_principal_turnos", principal)
    @Test @Config(qualifiers = Captura.LARGA) fun fichaConCobroYCierre() = cap(
        "07q_vinculacion_ficha_cobro_cierre",
        principal.copy(
            empleados = listOf(ana, luis),
            form = FormEmpleado(luis.id, luis.nombre, luis.permisos, tarjetaId = 2),
        ),
    )
    @Test fun confirmarCierre() = cap("07r_vinculacion_pedir_cierre", principal.copy(empleados = listOf(ana, luis), cerrarTurno = luis))
    @Test fun secundariaConectada() = cap(
        "07s_vinculacion_secundaria",
        VinculacionUi(
            tipo = TipoApp.SECUNDARIA,
            secundaria = EstadoSecundaria(
                nombreNegocio = "Cafetería María", nombreEmpleado = "Luis", permisos = PermisoEmpleado.PREDETERMINADOS,
                conexion = EstadoConexion.Conectada(Captura.AHORA), ultimaSincronizacion = Captura.AHORA,
            ),
        ),
    )
}
