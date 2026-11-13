package cu.shalev.data.sync

import cu.shalev.core.time.Clock
import cu.shalev.data.db.abortar
import cu.shalev.data.db.dao.localActivo
import cu.shalev.data.repository.localOperacion
import cu.shalev.domain.usecase.GuardarProducto as CasoGuardarProducto
import cu.shalev.domain.usecase.GuardarInsumo as CasoGuardarInsumo
import cu.shalev.domain.usecase.GuardarServicio as CasoGuardarServicio
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.db.entity.ReciboComandoEntity
import cu.shalev.data.db.tx
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.dto.toDomain
import cu.shalev.data.repository.InsumoRepositoryImpl
import cu.shalev.data.repository.OrigenMovimiento
import cu.shalev.data.repository.PreciosRepositoryImpl
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.ServicioRepositoryImpl
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.identidad
import cu.shalev.domain.usecase.errorIdentificacion
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * P37: en la PRINCIPAL, ejecuta lo que pide una secundaria con los repositorios de siempre (mismas validaciones y
 * transacciones). El permiso se comprueba AQUÍ con los datos de la principal: la secundaria no puede saltárselo.
 */
@Singleton
class EjecutorComandos @Inject constructor(
    private val db: ShalevDatabase,
    private val productos: ProductoRepositoryImpl,
    private val insumos: InsumoRepositoryImpl,
    private val servicios: ServicioRepositoryImpl,
    private val precios: PreciosRepositoryImpl,
    private val clock: Clock,
) {
    /**
     * Aplica y registra en una única transacción un comando con clave. El recibo vive en Room, sin poda, así que un
     * reintento tras reiniciar la principal devuelve la respuesta original. Reutilizar la clave para otra acción se
     * rechaza sin alterar el recibo ni aplicar la segunda acción.
     */
    suspend fun resultado(c: Comando, empleado: EmpleadoEntity): ResultadoComando {
        if (c.localId != null && c.localId != empleado.localId) return ResultadoComando(c.id, error = ErroresRemotos.PERMISO)
        val clave = c.clave
        if (clave.isNullOrBlank() || clave.length > MAX_CLAVE) return resultado(c.id, empleado, c.accion)
        val huella = try {
            HuellaComando.calcular(c.accion)
        } catch (_: Exception) {
            return ResultadoComando(c.id, error = ErroresRemotos.OTRO)
        }
        return when (val guardado = db.tx {
            val anterior = db.syncDao().reciboComando(empleado.id, clave)
            when {
                anterior != null && anterior.huella != huella ->
                    ResultadoComando(c.id, error = ErroresRemotos.CLAVE_REUTILIZADA)
                anterior != null -> ResultadoComando(c.id, valor = anterior.valor, error = anterior.error)
                else -> resultado(c.id, empleado, c.accion).also { r ->
                    db.syncDao().guardarReciboComando(
                        ReciboComandoEntity(empleado.id, clave, huella, r.valor, r.error),
                    )
                }
            }
        }) {
            is AppResult.Ok -> guardado.value
            is AppResult.Err -> ResultadoComando(c.id, error = codigo(guardado.error))
        }
    }

    suspend fun resultado(id: Long, empleado: EmpleadoEntity, accion: Accion): ResultadoComando {
        val permisos = PermisoEmpleado.deNombres(empleado.permisos.split(','))
        if (permisoDe(accion) !in permisos) return ResultadoComando(id, error = ErroresRemotos.PERMISO)
        val r: AppResult<Long?> = db.tx {
            val asignado = empleado.localId?.let { db.almacenLocalDao().porId(it) }
            val local = asignado?.nombre ?: if (empleado.localId == null) db.almacenLocalDao().localActivo()
                else abortar(AppError.SinPermiso)
            withContext(OrigenMovimiento(empleado.id, empleado.nombre, local)) {
                validarAlcance(accion, local)
                if (PermisoEmpleado.CAMBIAR_PRECIOS !in permisos && cambiaPrecio(accion)) abortar(AppError.SinPermiso)
                when (val aplicado = aplicar(accion, empleado.nombre)) {
                    is AppResult.Ok -> aplicado.value
                    is AppResult.Err -> abortar(aplicado.error)
                }
            }
        }
        return when (r) {
            is AppResult.Ok -> ResultadoComando(id, valor = r.value)
            is AppResult.Err -> ResultadoComando(id, error = codigo(r.error))
        }
    }

    private suspend fun aplicar(a: Accion, quien: String): AppResult<Long?> {
        val nota = { n: String? -> listOfNotNull(n?.takeIf { it.isNotBlank() }, "Desde la app de $quien").joinToString(" · ") }
        return when (a) {
            is GuardarProducto -> {
                // Las fotos no viajan: al editar desde una secundaria se conserva la de la principal.
                val p = a.producto.toDomain().let { it.copy(fotoUri = productos.obtener(it.id)?.fotoUri) }
                val receta = a.receta?.toDomain()
                // 0.24.0: la secundaria ya lo comprobó con su copia; aquí se repite por si otra app guardó antes.
                errorIdentificacion(p.id, p.nombre, p.descripcion, productos.observarTodos().first().map { it.identidad })
                    ?.let { return AppResult.Err(it) }
                CasoGuardarProducto(productos, insumos, clock)(p, receta, a.cantidadLeida).ok()
            }
            is EliminarProducto -> productos.eliminar(a.id).conId(a.id)
            is AjustarStockProducto -> productos.ajustarStock(a.id, a.delta, nota(a.nota)).ok()
            is CambiarPrecios -> productos.actualizarPrecios(a.precios.mapValues { Cup(it.value) }).conId(null)
            is GuardarInsumo -> {
                val i = a.insumo.toDomain()
                CasoGuardarInsumo(insumos, productos, clock)(i, a.cantidadLeidaMil?.let { Cantidad(it) }).ok()
            }
            is EliminarInsumo -> insumos.eliminar(a.id).conId(a.id)
            is AjustarStockInsumo -> when (val r = insumos.ajustarStock(a.id, Cantidad(a.deltaMil), nota(a.nota))) {
                is AppResult.Ok -> AppResult.Ok(r.value.milesimas)
                is AppResult.Err -> r
            }
            is GuardarServicio -> {
                val (s0, lineas) = a.servicio.toDomain()
                val s = s0.copy(fotoUri = servicios.obtener(s0.id)?.fotoUri)
                errorIdentificacion(s.id, s.nombre, s.descripcion, servicios.observarTodos().first().map { it.identidad })
                    ?.let { return AppResult.Err(it) }
                CasoGuardarServicio(servicios, clock)(s, lineas).ok()
            }
            is EliminarServicio -> servicios.eliminar(a.id).conId(a.id)
            is GuardarPreajuste -> {
                val p = a.preajuste.toDomain()
                cu.shalev.domain.validation.Validadores.preajuste(p).firstOrNull()?.let { return AppResult.Err(it) }
                precios.guardarPreajuste(p).ok()
            }
            is EliminarPreajuste -> precios.eliminarPreajuste(a.id).conId(a.id)
        }
    }

    /** Precio de VENTA: EDITAR_INVENTARIO no implica CAMBIAR_PRECIOS ni siquiera dentro de Guardar*. */
    private suspend fun cambiaPrecio(a: Accion): Boolean = when (a) {
        is GuardarProducto -> db.productoDao().obtener(a.producto.id)?.precioVentaCent != a.producto.precioVentaCent
        is GuardarInsumo -> db.insumoDao().obtener(a.insumo.id)?.precioVentaCent != a.insumo.precioVentaCent
        is GuardarServicio -> db.servicioDao().obtener(a.servicio.id)?.importeCent != a.servicio.importeCent
        else -> false
    }

    private suspend fun validarAlcance(a: Accion, local: String) {
        suspend fun producto(id: Long) { if (db.productoDao().obtener(id)?.local != local) abortar(AppError.SinPermiso) }
        suspend fun insumo(id: Long) { if (db.insumoDao().obtener(id)?.local != local) abortar(AppError.SinPermiso) }
        suspend fun servicio(id: Long) { if (db.servicioDao().obtener(id)?.local != local) abortar(AppError.SinPermiso) }
        suspend fun preajuste(id: Long) {
            val p = db.preciosDao().todos().firstOrNull { it.preajuste.id == id }?.toDomain() ?: abortar(AppError.NoEncontrado)
            p.productoIds.forEach { producto(it) }; p.servicioIds.forEach { servicio(it) }
        }
        when (a) {
            is GuardarProducto -> {
                if (a.producto.id != 0L) producto(a.producto.id)
                a.receta?.lineas?.forEach { insumo(it.insumoId) }
            }
            is GuardarInsumo -> if (a.insumo.id != 0L) insumo(a.insumo.id)
            is GuardarServicio -> { if (a.servicio.id != 0L) servicio(a.servicio.id); a.servicio.insumos.forEach { insumo(it.insumoId) } }
            is EliminarProducto -> producto(a.id)
            is AjustarStockProducto -> producto(a.id)
            is EliminarInsumo -> insumo(a.id)
            is AjustarStockInsumo -> insumo(a.id)
            is EliminarServicio -> servicio(a.id)
            is CambiarPrecios -> a.precios.keys.forEach { producto(it) }
            is GuardarPreajuste -> {
                if (a.preajuste.id != 0L) preajuste(a.preajuste.id)
                a.preajuste.productoIds.forEach { producto(it) }; a.preajuste.servicioIds.forEach { servicio(it) }
            }
            is EliminarPreajuste -> preajuste(a.id)
        }
    }

    private fun AppResult<Long>.ok(): AppResult<Long?> = when (this) {
        is AppResult.Ok -> AppResult.Ok(value)
        is AppResult.Err -> this
    }

    private fun AppResult<Unit>.conId(id: Long?): AppResult<Long?> = when (this) {
        is AppResult.Ok -> AppResult.Ok(id)
        is AppResult.Err -> this
    }

    companion object {
        const val MAX_CLAVE = 64

        fun permisoDe(a: Accion): PermisoEmpleado = when (a) {
            is CambiarPrecios, is GuardarPreajuste, is EliminarPreajuste -> PermisoEmpleado.CAMBIAR_PRECIOS
            else -> PermisoEmpleado.EDITAR_INVENTARIO
        }

        fun codigo(e: AppError): String = when (e) {
            AppError.SinPermiso -> ErroresRemotos.PERMISO
            AppError.NoEncontrado -> ErroresRemotos.NO_ENCONTRADO
            is AppError.StockInsuficiente -> ErroresRemotos.STOCK
            is AppError.EnUso -> ErroresRemotos.EN_USO
            is AppError.Duplicado -> ErroresRemotos.DUPLICADO + e.campo
            is AppError.Validacion -> ErroresRemotos.VALIDACION + e.campo + ":" + e.regla.name
            else -> ErroresRemotos.OTRO
        }

        /** Inverso de [codigo] en la secundaria (los formularios muestran el mismo mensaje que en la principal). */
        fun error(codigo: String): AppError = when {
            codigo == ErroresRemotos.CLAVE_REUTILIZADA -> AppError.Duplicado("clave")
            codigo == ErroresRemotos.PERMISO -> AppError.SinPermiso
            codigo == ErroresRemotos.NO_ENCONTRADO -> AppError.NoEncontrado
            codigo == ErroresRemotos.STOCK -> AppError.StockInsuficiente()
            codigo == ErroresRemotos.EN_USO -> AppError.EnUso("principal")
            codigo.startsWith(ErroresRemotos.DUPLICADO) -> AppError.Duplicado(codigo.removePrefix(ErroresRemotos.DUPLICADO))
            codigo.startsWith(ErroresRemotos.VALIDACION) -> {
                val partes = codigo.removePrefix(ErroresRemotos.VALIDACION).split(':')
                AppError.Validacion(partes[0], AppError.Regla.entries.firstOrNull { it.name == partes.getOrNull(1) } ?: AppError.Regla.FORMATO)
            }
            else -> AppError.Desconocido()
        }
    }
}
