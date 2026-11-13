package cu.shalev.domain.usecase

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.DetalleTurno
import cu.shalev.domain.model.InsumoEnTurno
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.Turno
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PuertaTurno
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.repository.VentaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Usuario que abre/cierra el turno. Shalev es de un solo usuario por dispositivo (sin cuentas): se usa el
 * nombre del Perfil y, si está vacío, [SIN_NOMBRE]. El texto se congela en el turno.
 */
class UsuarioActual @Inject constructor(private val perfil: PerfilRepository) {
    suspend operator fun invoke(): String = nombreDe(perfil.perfil.first())

    companion object {
        const val SIN_NOMBRE = "Titular del dispositivo"
        const val MAX = 80

        fun nombreDe(p: Perfil): String =
            listOf(p.nombre, p.apellidos).joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ").trim()
                .take(MAX).ifEmpty { SIN_NOMBRE }
    }
}

/**
 * P37: en una app secundaria, antes de abrir se sincroniza con la principal ([PuertaTurno]): no se empieza un turno
 * nuevo con ventas sin enviar ni con un catálogo viejo. Durante el turno se puede vender sin conexión.
 */
class AbrirTurno @Inject constructor(
    private val repo: TurnoRepository,
    private val usuario: UsuarioActual,
    private val clock: Clock,
    private val puerta: PuertaTurno,
) {
    /** Sin puerta (tests y app principal sin vinculación): siempre se puede. */
    constructor(repo: TurnoRepository, usuario: UsuarioActual, clock: Clock) : this(repo, usuario, clock, PuertaTurno.SIEMPRE)

    /** 0.25.0: [fondo] = efectivo con el que empieza la caja (obligatorio; puede ser 0). */
    suspend operator fun invoke(fondo: Cup): AppResult<Turno> {
        if (fondo.isNegative) return AppResult.Err(AppError.Validacion("fondo", AppError.Regla.RANGO))
        if (repo.activo() != null) return AppResult.Err(AppError.TurnoYaAbierto)
        val previa = puerta.antesDeAbrir()
        if (previa is AppResult.Err) return previa
        // 0.32.0: el fondo lo escribe quien abre el turno, también en una app secundaria.
        return repo.abrir(clock.now(), usuario(), fondo)
    }
}

/**
 * 0.25.0: fondo que se propone al abrir: el efectivo CONTADO al cerrar el último turno de esta app (si no se contó,
 * su fondo). null = no hay turno anterior con arqueo (el campo aparece vacío).
 */
class FondoSugerido @Inject constructor(private val repo: TurnoRepository) {
    suspend operator fun invoke(): Cup? = repo.ultimoCerrado()?.let { it.contado ?: it.fondo }
}

/**
 * Cierra el turno y congela su registro: ventas, totales y nº de ventas por método de pago, costo,
 * movimientos de productos e insumos, caja (0.25.0: [contado] obligatorio), hora de cierre y usuario. Todo en una
 * transacción de :data.
 */
class CerrarTurno @Inject constructor(
    private val repo: TurnoRepository,
    private val usuario: UsuarioActual,
    private val clock: Clock,
) {
    suspend operator fun invoke(contado: Cup): AppResult<Turno> {
        if (contado.isNegative) return AppResult.Err(AppError.Validacion("contado", AppError.Regla.RANGO))
        return repo.cerrar(clock.now(), usuario(), contado)
    }
}

/** Historial para Registros → Turnos: el abierto (si hay) y luego los cerrados, más recientes primero. */
class ObservarHistorialTurnos @Inject constructor(private val repo: TurnoRepository) {
    operator fun invoke(): Flow<List<Turno>> = repo.observarHistorial().map { l ->
        l.sortedWith(compareByDescending<Turno> { it.abierto }.thenByDescending { it.abiertoEn }.thenByDescending { it.id })
    }
}

/** Registro completo de un turno (ventas + movimientos de inventario e insumos). */
class ObtenerDetalleTurno @Inject constructor(
    private val turnos: TurnoRepository,
    private val ventas: VentaRepository,
    private val insumos: InsumoRepository,
) {
    suspend operator fun invoke(id: Long): AppResult<DetalleTurno> {
        val turno = turnos.obtener(id) ?: return AppResult.Err(AppError.NoEncontrado)
        val movs = turnos.movimientosDe(id)
        val deInsumo = movs.filter { it.entidad == TipoEntidad.INSUMO }
        val unidades = insumos.obtenerVarios(deInsumo.map { it.entidadId }.toSet()).associate { it.id to it.unidad.simbolo }
        val netos = deInsumo.groupBy { it.entidadId }.map { (insumoId, ms) ->
            // delta de insumo en milésimas (ver MovimientoInventario)
            InsumoEnTurno(insumoId, ms.last().nombre, Cantidad(ms.sumOf { it.delta }), unidades[insumoId].orEmpty())
        }.sortedBy { it.cantidad.milesimas } // mayores consumos primero
        return AppResult.Ok(
            DetalleTurno(
                turno, ventas.deTurno(id).sortedBy { it.fecha }, movs.sortedWith(compareBy({ it.fecha }, { it.id })), netos,
                turnos.cajaDe(id).sortedWith(compareBy({ it.fecha }, { it.id })),
            ),
        )
    }
}

/**
 * ¿Se puede vender ahora? Solo con turno abierto (Shalev.txt). La UI lo usa para bloquear la pantalla de
 * venta; [RegistrarVenta] y la transacción de :data lo vuelven a comprobar al guardar.
 */
class ObservarPermisoVenta @Inject constructor(private val repo: TurnoRepository) {
    sealed interface Permiso {
        data class Permitido(val turno: Turno) : Permiso
        data object SinTurno : Permiso
    }

    operator fun invoke(): Flow<Permiso> = repo.observarActivo().map { t -> if (t != null && t.abierto) Permiso.Permitido(t) else Permiso.SinTurno }
}
