package cu.shalev.domain.usecase

import cu.shalev.core.money.Cup
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Actualizaciones
import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.InfoActualizacion
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoCaja
import cu.shalev.domain.model.TipoMovimientoCaja
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.ActualizacionesRepository
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.MantenimientoRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.repository.VentaRepository
import cu.shalev.domain.service.LineaSolicitada
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Texto de motivo normalizado (espacios) y validado [min]..[max]. */
internal fun motivoValido(motivo: String, min: Int, max: Int): AppResult<String> {
    val m = motivo.trim().replace(Regex("\\s+"), " ")
    return when {
        m.isEmpty() -> AppResult.Err(AppError.Validacion("motivo", AppError.Regla.REQUERIDO))
        m.length !in min..max -> AppResult.Err(AppError.Validacion("motivo", AppError.Regla.RANGO))
        else -> AppResult.Ok(m)
    }
}

/**
 * 0.25.0: entrada o salida de efectivo en el turno abierto de ESTA app (principal o secundaria; quien puede vender puede
 * registrarla en su turno). Importe > 0 y motivo de 3 a 60 caracteres. No se borra: se corrige con el contrario.
 */
class RegistrarMovimientoCaja @Inject constructor(
    private val turnos: TurnoRepository,
    private val usuario: UsuarioActual,
    private val clock: Clock,
) {
    suspend operator fun invoke(tipo: TipoMovimientoCaja, importe: Cup, motivo: String): AppResult<Long> {
        if (importe.centavos <= 0) return AppResult.Err(AppError.Validacion("importe", AppError.Regla.RANGO))
        val m = when (val r = motivoValido(motivo, MovimientoCaja.MOTIVO_MIN, MovimientoCaja.MOTIVO_MAX)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        return turnos.registrarCaja(tipo, importe, m, usuario(), clock.now())
    }
}

/** 0.25.0: anular y modificar ventas es SOLO de la app principal (las secundarias no tienen la acción). */
private fun SecundariaRepository.esPrincipal(): Boolean = !estado.value.vinculada

/**
 * 0.25.0 (Prompt 69): anula una venta de un turno ABIERTO (también las de secundarias ya recibidas). Motivo obligatorio;
 * la venta queda en Registros como ANULADA y se devuelven las existencias. Solo en la app principal.
 */
class AnularVenta @Inject constructor(
    private val ventas: VentaRepository,
    private val usuario: UsuarioActual,
    private val clock: Clock,
    private val secundaria: SecundariaRepository,
) {
    suspend operator fun invoke(ventaId: Long, motivo: String): AppResult<Unit> {
        if (!secundaria.esPrincipal()) return AppResult.Err(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO))
        val m = when (val r = motivoValido(motivo, Anulacion.MOTIVO_MIN, Anulacion.MOTIVO_MAX)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        return ventas.anular(ventaId, Anulacion(clock.now(), m, usuario()))
    }
}

/**
 * 0.31.0: corrige la cantidad de un ajuste de existencias del turno ABIERTO desde Registros → Movimientos. Solo la
 * app principal (como anular o modificar ventas) y con motivo de 3 a 60 caracteres: la corrección queda en la nota
 * del movimiento.
 */
class CorregirMovimiento @Inject constructor(
    private val turnos: TurnoRepository,
    private val clock: Clock,
    private val secundaria: SecundariaRepository,
) {
    suspend operator fun invoke(movimientoId: Long, delta: Long, motivo: String): AppResult<Unit> {
        if (!secundaria.esPrincipal()) return AppResult.Err(AppError.Validacion("movimiento", AppError.Regla.NO_PERMITIDO))
        val m = when (val r = motivoValido(motivo, Anulacion.MOTIVO_MIN, Anulacion.MOTIVO_MAX)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        return turnos.corregirMovimiento(movimientoId, delta, m, clock.now().toEpochMilli())
    }
}

/**
 * 0.25.0: «Modificar» = anular la venta + registrar la corregida en el MISMO turno (mismo vendedor), en una transacción.
 * Las líneas que ya estaban conservan su precio y costo ORIGINALES; las nuevas usan el precio actual. Las existencias se
 * comprueban contando lo que devolvería la anulación ([Devueltos]).
 */
class ModificarVenta @Inject constructor(
    private val cotizar: CotizarVenta,
    private val ventas: VentaRepository,
    private val turnos: TurnoRepository,
    private val perfil: PerfilRepository,
    private val usuario: UsuarioActual,
    private val clock: Clock,
    private val secundaria: SecundariaRepository,
) {
    suspend operator fun invoke(
        ventaId: Long, lineas: List<LineaSolicitada>, metodo: MetodoPago, transferencia: DatosTransferencia?, motivo: String,
    ): AppResult<Long> {
        if (!secundaria.esPrincipal()) return AppResult.Err(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO))
        val m = when (val r = motivoValido(motivo, Anulacion.MOTIVO_MIN, Anulacion.MOTIVO_MAX)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        if (lineas.isEmpty()) return AppResult.Err(AppError.Validacion("lineas", AppError.Regla.REQUERIDO))
        val original = ventas.obtener(ventaId) ?: return AppResult.Err(AppError.NoEncontrado)
        if (original.anulada) return AppResult.Err(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO))
        val turno = turnos.obtener(original.turnoId) ?: return AppResult.Err(AppError.NoEncontrado)
        if (!turno.abierto) return AppResult.Err(AppError.TurnoCerrado)
        val cliente = when (val r = validarTransferencia(metodo, transferencia)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        val plan = when (val r = cotizar.planificar(lineas, metodo, Devueltos.de(ventas.movimientosDe(ventaId)))) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        val elaborados = when (val r = plan.elaborados()) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        val detalles = conPreciosOriginales(plan.cotizacion.detalles, original)
        val now = clock.now()
        val transaccion = cliente?.let { t ->
            val p = perfil.perfil.first()
            transaccion(
                t, now, detalles.sumOfCup { it.subtotal },
                original.transaccion?.tarjetaCobro ?: p.tarjetaPago?.numero,
                original.transaccion?.telefonoCobro ?: p.telefonoPago?.numero,
            )
        }
        val nueva = Venta(turnoId = turno.id, fecha = now, metodoPago = metodo, detalles = detalles, transaccion = transaccion, corrigeVentaId = ventaId)
        return ventas.modificarVerificada(ventaId, Anulacion(now, m, usuario()), nueva, elaborados) {
            when (val vigente = cotizar.planificar(lineas, metodo, Devueltos.de(ventas.movimientosDe(ventaId)))) {
                is AppResult.Err -> vigente
                is AppResult.Ok -> if (conPreciosOriginales(vigente.value.cotizacion.detalles, original) == detalles && vigente.value.elaborados() == AppResult.Ok(elaborados))
                    AppResult.Ok(Unit) else AppResult.Err(AppError.Validacion("cotizacion", AppError.Regla.NO_PERMITIDO))
            }
        }
    }

    companion object {
        /** Líneas que ya estaban en [original] (misma clase y artículo): precio base, precio y costo de entonces. */
        fun conPreciosOriginales(detalles: List<cu.shalev.domain.model.DetalleVenta>, original: Venta): List<cu.shalev.domain.model.DetalleVenta> {
            val previos = original.detalles.associateBy { it.clase to it.productoId }
            return detalles.map { d ->
                previos[d.clase to d.productoId]?.let { o ->
                    d.copy(precioBase = o.precioBase, precioUnitario = o.precioUnitario, costoUnitario = o.costoUnitario)
                } ?: d
            }
        }
    }
}

/**
 * 0.25.0 (Prompt 69): consulta semanal a GitHub al abrir la app (sin trabajo en segundo plano).
 *  1. Si hay una licencia instalada, descarga la lista pública de revocadas: si la incluye, este teléfono es el ANTIGUO
 *     de una recuperación → la licencia queda revocada y se BORRAN los datos del negocio (la app muestra «Licencia
 *     transferida» y pide desinstalarse). Esto no se puede desactivar.
 *  2. Mira la última Release y guarda si es más nueva (0.26.0: siempre; ya no hay interruptor).
 * Los errores de red se ignoran sin molestar (se reintenta en la próxima apertura).
 */
class ComprobarActualizaciones @Inject constructor(
    private val estado: EstadoAppRepository,
    private val github: ActualizacionesRepository,
    private val licencia: LicenciaRepository,
    private val mantenimiento: MantenimientoRepository,
    private val clock: Clock,
) {
    data class Resultado(val aviso: InfoActualizacion?, val revocada: Boolean = false, val error: Boolean = false)

    suspend operator fun invoke(instalada: String, forzar: Boolean = false): Resultado {
        val ahora = clock.now()
        val e = estado.actual()
        if (!github.repoConfigurado) return Resultado(null)
        if (!forzar && !Actualizaciones.debeComprobar(ahora, e, true)) return Resultado(Actualizaciones.aviso(e, instalada))

        var correcto = true
        if (licencia.snapshot.value?.instalada != null) {
            when (val r = github.listaRevocaciones()) {
                is AppResult.Ok -> if (licencia.aplicarRevocaciones(r.value)) {
                    mantenimiento.borrarTodo()
                    estado.borrar()
                    licencia.refrescar()
                    return Resultado(null, revocada = true)
                }
                is AppResult.Err -> correcto = false
            }
        }
        var disponible = e.disponible
        // 0.26.0 (P73 §6): sin interruptor: la consulta semanal siempre mira la última Release.
        when (val r = github.ultimaVersion()) {
            is AppResult.Ok -> disponible = r.value?.takeIf { Actualizaciones.esMasNueva(it.version, instalada) }
            is AppResult.Err -> correcto = false
        }
        if (correcto) estado.editar { it.copy(ultimaComprobacion = ahora, disponible = disponible) }
        return Resultado(Actualizaciones.aviso(estado.actual(), instalada), error = !correcto)
    }
}
