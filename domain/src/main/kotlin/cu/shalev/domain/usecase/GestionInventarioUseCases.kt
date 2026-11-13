package cu.shalev.domain.usecase

import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.MotivoMovimiento
import cu.shalev.domain.model.NuevaRecepcion
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.repository.GestionInventarioRepository
import javax.inject.Inject

class RegistrarRecepcion @Inject constructor(private val repo: GestionInventarioRepository, private val usuario: UsuarioActual) {
    suspend operator fun invoke(r: NuevaRecepcion): AppResult<String> {
        if (!recepcionValida(r)) return AppResult.Err(AppError.Validacion("recepcion"))
        return repo.recibir(r.copy(proveedor = r.proveedor.trim(), comprobante = r.comprobante.trim(), motivo = r.motivo.trim()), usuario())
    }
}

fun recepcionValida(r: NuevaRecepcion): Boolean = r.uuid.isNotBlank() && r.uuid.length <= 64 &&
    r.proveedor.trim().length in 1..80 && r.comprobante.trim().length in 1..80 && r.motivo.trim().length in 3..120 &&
    r.lineas.isNotEmpty() && r.lineas.size <= 200 && r.lineas.all {
        it.id > 0 && it.cantidad.milesimas > 0 && it.costoUnitario.centavos >= 0 && it.lote.trim().length in 1..80 &&
            (it.entidad != TipoEntidad.PRODUCTO || it.cantidad.milesimas % 1000 == 0L)
    }

class RegistrarAjusteInventario @Inject constructor(private val repo: GestionInventarioRepository, private val usuario: UsuarioActual) {
    suspend operator fun invoke(entidad: TipoEntidad, id: Long, delta: Cantidad, motivo: MotivoMovimiento, detalle: String): AppResult<Unit> {
        if (!ajusteValido(id, delta, motivo, detalle) || (entidad == TipoEntidad.PRODUCTO && delta.milesimas % 1000 != 0L))
            return AppResult.Err(AppError.Validacion("ajuste"))
        return repo.ajustar(entidad, id, delta, motivo, detalle.trim(), usuario())
    }
}

fun ajusteValido(id: Long, delta: Cantidad, motivo: MotivoMovimiento, detalle: String): Boolean = id > 0 &&
    delta.milesimas != 0L && detalle.trim().length in 3..120 && motivo != MotivoMovimiento.COMPRA &&
    (motivo !in setOf(MotivoMovimiento.MERMA, MotivoMovimiento.CONSUMO_INTERNO) || delta.milesimas < 0)
