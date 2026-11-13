package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.ElaboradoEnVenta
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PreciosRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.repository.VentaRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

val T0: Instant = Instant.parse("2026-09-01T12:00:00Z")

class FixedClock(var now: Instant = T0) : Clock { override fun now() = now }

fun producto(
    id: Long, nombre: String = "P$id", cantidad: Long = 10, venta: Long = 100, costo: Long = 60,
    categoria: String = "Bebidas", creado: Instant = T0, bajo: Long? = null, critico: Long? = null, cad: LocalDate? = null,
) = Producto(
    id = id, categoria = categoria, nombre = nombre, precioCosto = Cup.ofPesos(costo), precioVenta = Cup.ofPesos(venta),
    cantidad = cantidad, creadoEn = creado, nivelBajo = bajo, nivelCritico = critico, fechaCaducidad = cad,
)

fun insumo(id: Long, nombre: String = "I$id", precio: Long = 10, cantidad: Long = 10) =
    Insumo(id = id, nombre = nombre, precio = Cup.ofPesos(precio), cantidad = Cantidad.enteras(cantidad), creadoEn = T0)

class FakeProductos : ProductoRepository {
    val items = MutableStateFlow<Map<Long, Producto>>(emptyMap())
    val recetas = mutableMapOf<Long, Receta>()
    var insumos: FakeInsumos? = null
    private var next = 100L

    fun put(vararg ps: Producto) { items.value = items.value + ps.associateBy { it.id } }

    override fun observarTodos(): Flow<List<Producto>> = items.map { m -> m.values.filterNot { it.eliminado }.sortedByDescending { it.creadoEn } }
    override fun observar(id: Long) = items.map { it[id] }
    override suspend fun obtener(id: Long) = items.value[id]
    override suspend fun obtenerVarios(ids: Collection<Long>) = ids.mapNotNull { items.value[it] }
    override suspend fun categoriasEnUso() = items.value.values.map { it.categoria }.distinct()
    override suspend fun crear(producto: Producto, receta: Receta?): AppResult<Long> {
        val id = next++
        put(producto.copy(id = id)); receta?.let { recetas[id] = it.copy(productoId = id) }
        return AppResult.Ok(id)
    }
    override suspend fun actualizar(producto: Producto, receta: Receta?): AppResult<Unit> {
        put(producto); if (receta != null) recetas[producto.id] = receta else recetas.remove(producto.id)
        return AppResult.Ok(Unit)
    }
    override suspend fun eliminar(id: Long): AppResult<Unit> {
        val p = items.value[id] ?: return AppResult.Err(AppError.NoEncontrado)
        put(p.copy(eliminado = true)); return AppResult.Ok(Unit)
    }
    override suspend fun ajustarStock(id: Long, delta: Long, nota: String?): AppResult<Long> {
        val p = items.value[id]!!; put(p.copy(cantidad = p.cantidad + delta)); return AppResult.Ok(p.cantidad + delta)
    }
    override suspend fun actualizarPrecios(nuevos: Map<Long, Cup>): AppResult<Unit> {
        nuevos.forEach { (id, c) -> put(items.value[id]!!.copy(precioVenta = c)) }; return AppResult.Ok(Unit)
    }
    override suspend fun receta(productoId: Long) = recetas[productoId]
    override suspend fun productosQueUsan(insumoId: Long) =
        recetas.values.filter { r -> r.lineas.any { it.insumoId == insumoId } }.mapNotNull { items.value[it.productoId] }
}

class FakeInsumos : InsumoRepository {
    val items = MutableStateFlow<Map<Long, Insumo>>(emptyMap())
    private var next = 500L
    fun put(vararg xs: Insumo) { items.value = items.value + xs.associateBy { it.id } }
    override fun observarTodos() = items.map { it.values.toList() }
    override suspend fun obtener(id: Long) = items.value[id]
    override suspend fun obtenerVarios(ids: Collection<Long>) = ids.mapNotNull { items.value[it] }
    override suspend fun todos() = items.value.values.toList()
    override suspend fun crear(insumo: Insumo): AppResult<Long> { val id = next++; put(insumo.copy(id = id)); return AppResult.Ok(id) }
    override suspend fun actualizar(insumo: Insumo): AppResult<Unit> { put(insumo); return AppResult.Ok(Unit) }
    /** Simula la regla de :data: un insumo usado por alguna receta no se borra. */
    var enUso: Set<Long> = emptySet()
    override suspend fun eliminar(id: Long): AppResult<Unit> {
        if (id in enUso) return AppResult.Err(AppError.EnUso("receta"))
        if (id !in items.value) return AppResult.Err(AppError.NoEncontrado)
        items.value = items.value - id; return AppResult.Ok(Unit)
    }
    override suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?): AppResult<Cantidad> {
        val i = items.value[id]!!; put(i.copy(cantidad = i.cantidad + delta)); return AppResult.Ok(i.cantidad + delta)
    }
}

class FakeTurnos : TurnoRepository {
    val turnos = MutableStateFlow<List<Turno>>(emptyList())
    override fun observarActivo() = turnos.map { l -> l.firstOrNull { it.abierto } }
    override suspend fun activo() = turnos.value.firstOrNull { it.abierto }
    override suspend fun ultimoCerrado() = turnos.value.filterNot { it.abierto }.maxByOrNull { it.abiertoEn }
    override fun observarHistorial() = turnos
    override suspend fun obtener(id: Long) = turnos.value.firstOrNull { it.id == id }
    /** Movimientos registrados por el fake de productos/insumos o por el test (turnoId ya asignado). */
    val movimientos = mutableListOf<MovimientoInventario>()
    /** Ventas para congelar el resumen al cerrar (lo asigna FakeVentas). */
    var ventasDe: (Long) -> List<Venta> = { emptyList() }

    override suspend fun abrir(ahora: Instant, usuario: String, fondo: cu.shalev.core.money.Cup?): AppResult<Turno> {
        if (activo() != null) return AppResult.Err(AppError.TurnoYaAbierto)
        val t = Turno(id = turnos.value.size + 1L, abiertoEn = ahora, abiertoPor = usuario, fondo = fondo); turnos.value = turnos.value + t; return AppResult.Ok(t)
    }
    override suspend fun cerrar(ahora: Instant, usuario: String, contado: cu.shalev.core.money.Cup?): AppResult<Turno> {
        val t = activo() ?: return AppResult.Err(AppError.TurnoCerrado)
        val c = t.copy(
            cerradoEn = maxOf(ahora, t.abiertoEn), cerradoPor = usuario, contado = contado ?: t.contado,
            resumen = ResumenTurno.calcular(ventasDe(t.id), movimientosDe(t.id), caja.filter { it.turnoId == t.id }),
        )
        turnos.value = turnos.value.map { if (it.id == t.id) c else it }; return AppResult.Ok(c)
    }
    override suspend fun movimientosDe(turnoId: Long) = movimientos.filter { it.turnoId == turnoId }

    /** 0.25.0: caja en memoria. */
    val caja = mutableListOf<cu.shalev.domain.model.MovimientoCaja>()
    override suspend fun registrarCaja(
        tipo: cu.shalev.domain.model.TipoMovimientoCaja, importe: cu.shalev.core.money.Cup, motivo: String, hechoPor: String, ahora: Instant,
    ): AppResult<Long> {
        val t = activo() ?: return AppResult.Err(AppError.TurnoCerrado)
        val id = caja.size + 1L
        caja += cu.shalev.domain.model.MovimientoCaja(id, t.id, ahora, tipo, importe, motivo, hechoPor)
        return AppResult.Ok(id)
    }
    override suspend fun cajaDe(turnoId: Long) = caja.filter { it.turnoId == turnoId }
    override fun observarArqueoActivo() = turnos.map { l ->
        l.firstOrNull { it.abierto }?.let { t -> cu.shalev.domain.model.Arqueo.de(t, ResumenTurno.calcular(ventasDe(t.id), movimientosDe(t.id), cajaDe(t.id))) }
    }
    override suspend fun declararContado(contado: cu.shalev.core.money.Cup): AppResult<Unit> {
        val t = activo() ?: return AppResult.Err(AppError.TurnoCerrado)
        turnos.value = turnos.value.map { if (it.id == t.id) it.copy(contado = contado) else it }
        return AppResult.Ok(Unit)
    }
}

/**
 * Imita la transacción de :data: turno abierto, stock y descuento. P26: los Elaborados descuentan sus insumos
 * (todo o nada) y no tocan su propia existencia.
 */
class FakeVentas(private val productos: FakeProductos, private val turnos: FakeTurnos) : VentaRepository {
    val ventas = mutableListOf<Venta>()
    val elaborados = mutableListOf<ElaboradoEnVenta>()
    init { turnos.ventasDe = { id -> ventas.filter { it.turnoId == id } } }
    override suspend fun registrar(venta: Venta, elaborados: List<ElaboradoEnVenta>): AppResult<Long> {
        if (turnos.obtener(venta.turnoId)?.abierto != true) return AppResult.Err(AppError.TurnoCerrado)
        val ins = productos.insumos?.items?.value.orEmpty()
        val consumo = elaborados.flatMap { it.consumo.entries }.groupBy({ it.key }, { it.value.milesimas }).mapValues { Cantidad(it.value.sum()) }
        val faltanInsumos = consumo.filter { (id, c) -> (ins[id]?.cantidad ?: Cantidad(0)) < c }.keys.map { ins[it]?.nombre ?: "#$it" }
        if (faltanInsumos.isNotEmpty()) return AppResult.Err(AppError.StockInsuficiente(faltanInsumos))
        val idsElaborados = elaborados.filter { it.clase == ClaseArticulo.PRODUCTO }.map { it.productoId }.toSet()
        // :data solo descuenta stock de PRODUCTO no elaborados; los servicios no tienen existencias.
        val articulos = venta.detalles.filter { it.clase == ClaseArticulo.PRODUCTO && it.productoId !in idsElaborados }
        val faltan = articulos.filter { (productos.obtener(it.productoId)?.cantidad ?: 0) < it.cantidad }.map { it.nombre }
        if (faltan.isNotEmpty()) return AppResult.Err(AppError.StockInsuficiente(faltan))
        consumo.forEach { (id, c) -> productos.insumos!!.ajustarStock(id, Cantidad(-c.milesimas), null) }
        this.elaborados += elaborados
        val id = ventas.size + 1L
        articulos.forEach {
            val existencia = (productos.ajustarStock(it.productoId, -it.cantidad, null) as AppResult.Ok).value
            turnos.movimientos += MovimientoInventario(fecha = venta.fecha, tipo = TipoMovimiento.VENTA, entidad = TipoEntidad.PRODUCTO,
                entidadId = it.productoId, nombre = it.nombre, delta = -it.cantidad, existenciaResultante = existencia, turnoId = venta.turnoId, ventaId = id)
        }
        ventas += venta.copy(id = id, transaccion = venta.transaccion?.copy(ventaId = id))
        return AppResult.Ok(id)
    }
    override suspend fun obtener(id: Long) = ventas.firstOrNull { it.id == id }
    override suspend fun entre(desde: Instant, hasta: Instant) = ventas.filter { it.fecha >= desde && it.fecha < hasta }
    override suspend fun deTurno(turnoId: Long) = ventas.filter { it.turnoId == turnoId }

    /** 0.25.0: imita la anulación de :data (turno abierto, marca y devolución de existencias). */
    override suspend fun anular(ventaId: Long, anulacion: cu.shalev.domain.model.Anulacion): AppResult<Unit> {
        val v = ventas.firstOrNull { it.id == ventaId } ?: return AppResult.Err(AppError.NoEncontrado)
        if (v.anulacion != null) return AppResult.Err(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO))
        if (turnos.obtener(v.turnoId)?.abierto != true) return AppResult.Err(AppError.TurnoCerrado)
        ventas[ventas.indexOf(v)] = v.copy(anulacion = anulacion)
        turnos.movimientos.filter { it.ventaId == ventaId && it.delta < 0 && it.entidad == cu.shalev.domain.model.TipoEntidad.PRODUCTO }.toList().forEach { m ->
            val existencia = (productos.ajustarStock(m.entidadId, -m.delta, null) as AppResult.Ok).value
            turnos.movimientos += m.copy(id = 0, tipo = TipoMovimiento.ANULACION, delta = -m.delta, existenciaResultante = existencia)
        }
        return AppResult.Ok(Unit)
    }
    override suspend fun modificar(
        ventaId: Long, anulacion: cu.shalev.domain.model.Anulacion, nueva: Venta, elaborados: List<ElaboradoEnVenta>,
    ): AppResult<Long> {
        val original = ventas.firstOrNull { it.id == ventaId } ?: return AppResult.Err(AppError.NoEncontrado)
        anular(ventaId, anulacion).let { if (it is AppResult.Err) return it }
        val r = registrar(nueva.copy(turnoId = original.turnoId, corrigeVentaId = ventaId), elaborados)
        if (r is AppResult.Ok) {
            val i = ventas.indexOfFirst { it.id == ventaId }
            ventas[i] = ventas[i].copy(anulacion = anulacion.copy(motivo = cu.shalev.domain.model.Anulacion.motivoModificada(r.value, anulacion.motivo)))
        }
        return r
    }
    override suspend fun movimientosDe(ventaId: Long) = turnos.movimientos.filter { it.ventaId == ventaId }
}

class FakePrecios : PreciosRepository {
    val preajustes = MutableStateFlow<List<PreajustePrecios>>(emptyList())
    override fun observarPreajustes() = preajustes
    override suspend fun preajustesActivos() = preajustes.value.filter { it.activo }
    override suspend fun guardarPreajuste(p: PreajustePrecios): AppResult<Long> {
        val id = if (p.id == 0L) preajustes.value.size + 1L else p.id
        preajustes.value = preajustes.value.filterNot { it.id == id } + p.copy(id = id); return AppResult.Ok(id)
    }
    override suspend fun eliminarPreajuste(id: Long): AppResult<Unit> { preajustes.value = preajustes.value.filterNot { it.id == id }; return AppResult.Ok(Unit) }
}

class FakePerfil(initial: Perfil = Perfil()) : PerfilRepository {
    val state = MutableStateFlow(initial)
    private var next = 1L
    override val perfil: Flow<Perfil> = state
    override suspend fun guardar(perfil: Perfil) {
        state.value = perfil.copy(
            tarjetas = perfil.tarjetas.map { if (it.id == 0L) it.copy(id = next++) else it },
            telefonos = perfil.telefonos.map { if (it.id == 0L) it.copy(id = next++) else it },
        )
    }
}

class FakePreferencias(initial: Preferencias = Preferencias()) : PreferenciasRepository {
    val state = MutableStateFlow(initial)
    override val preferencias: Flow<Preferencias> = state
    override val onboardingCompletado = state.map { it.onboardingCompletado }
    override suspend fun completarOnboarding() { state.value = state.value.copy(onboardingCompletado = true) }
    override suspend fun guardarNiveles(n: NivelesMinimos) { state.value = state.value.copy(niveles = n) }
    override suspend fun guardarModulos(m: Set<cu.shalev.domain.model.Modulo>) { state.value = state.value.copy(modulos = cu.shalev.domain.model.Modulo.normalizar(m)) }
    override suspend fun guardarEmpleadosPrevistos(n: Int) { state.value = state.value.copy(empleadosPrevistos = n) }
    override suspend fun reemplazar(p: Preferencias) { state.value = p }
}

class FakeLicencia(
    var estado: cu.shalev.licencia.LicenseState = cu.shalev.licencia.LicenseState.Trial(7),
    var solicitud: AppResult<cu.shalev.licencia.SolicitudGenerada> = AppResult.Ok(cu.shalev.licencia.SolicitudGenerada("texto\n\nSPVIR1:x", "SPVIR1:x")),
    var activacion: cu.shalev.licencia.ActivationResult = cu.shalev.licencia.ActivationResult.NotFound,
) : cu.shalev.domain.repository.LicenciaRepository {
    var refrescos = 0
    var clave: String? = null
    private val flow = MutableStateFlow<cu.shalev.domain.model.Licencia?>(null)
    override val snapshot: kotlinx.coroutines.flow.StateFlow<cu.shalev.domain.model.Licencia?> = flow
    private fun actual() = cu.shalev.domain.model.Licencia(estado, "SPVI:abc12345", clave != null, clave)
    override suspend fun refrescar() = actual().also { refrescos++; flow.value = it }
    override suspend fun construirSolicitud(input: cu.shalev.licencia.SolicitudInput) = solicitud
    override suspend fun activar(mensaje: String) = activacion
}

class FakeMantenimiento(private val diario: MutableList<String>, var resultado: AppResult<Unit> = AppResult.Ok(Unit)) :
    cu.shalev.domain.repository.MantenimientoRepository {
    override suspend fun borrarTodo(): AppResult<Unit> = resultado.also { diario += "borrar" }
}

class FakeConfiguracionInicial(initial: cu.shalev.domain.model.ConfiguracionInicial = cu.shalev.domain.model.ConfiguracionInicial()) :
    cu.shalev.domain.repository.ConfiguracionInicialRepository {
    val state = MutableStateFlow(initial)
    override val estado: Flow<cu.shalev.domain.model.ConfiguracionInicial> = state
    override suspend fun confirmar(paso: cu.shalev.domain.model.PasoConfiguracion) { state.value = state.value.copy(confirmados = state.value.confirmados + paso) }
    override suspend fun marcarCamaraSolicitada() { state.value = state.value.copy(camaraSolicitada = true) }
    override suspend fun marcarPermisosInicialesPedidos() { state.value = state.value.copy(permisosInicialesPedidos = true) }
}

class FakeFotos : cu.shalev.domain.repository.FotoRepository {
    val importadas = mutableListOf<String>()
    val eliminadas = mutableListOf<String>()
    var enUsoAlLimpiar: Set<String>? = null
    var fallar = false
    override suspend fun importar(origen: String): AppResult<String> {
        if (fallar) return AppResult.Err(AppError.Almacenamiento)
        importadas += origen; return AppResult.Ok("file:///fotos/i${importadas.size}.jpg")
    }
    override suspend fun eliminar(uri: String) { eliminadas += uri }
    override suspend fun limpiarHuerfanas(enUso: Set<String>) { enUsoAlLimpiar = enUso }
}

/** P29: servicios en memoria (crear/actualizar/eliminar lógico + insumos por vez). */
class FakeServicios : cu.shalev.domain.repository.ServicioRepository {
    val items = MutableStateFlow<Map<Long, cu.shalev.domain.model.Servicio>>(emptyMap())
    val lineas = MutableStateFlow<Map<Long, List<cu.shalev.domain.model.RecetaLinea>>>(emptyMap())
    private var next = 900L
    override fun observarTodos() = items.map { m -> m.values.filterNot { it.eliminado }.sortedByDescending { it.creadoEn } }
    override fun observarInsumos() = lineas.map { m -> m.filterValues { it.isNotEmpty() } }
    override suspend fun obtener(id: Long) = items.value[id]
    override suspend fun obtenerVarios(ids: Collection<Long>) = ids.mapNotNull { items.value[it] }
    override suspend fun insumos(servicioId: Long) = lineas.value[servicioId].orEmpty()
    override suspend fun tiposEnUso() = items.value.values.filterNot { it.eliminado }.map { it.tipo }.distinct().sorted()
    override suspend fun crear(servicio: cu.shalev.domain.model.Servicio, insumos: List<cu.shalev.domain.model.RecetaLinea>): AppResult<Long> {
        val id = if (servicio.id > 0) servicio.id else next++
        items.value = items.value + (id to servicio.copy(id = id)); lineas.value = lineas.value + (id to insumos)
        return AppResult.Ok(id)
    }
    override suspend fun actualizar(servicio: cu.shalev.domain.model.Servicio, insumos: List<cu.shalev.domain.model.RecetaLinea>): AppResult<Unit> {
        if (servicio.id !in items.value) return AppResult.Err(AppError.NoEncontrado)
        items.value = items.value + (servicio.id to servicio); lineas.value = lineas.value + (servicio.id to insumos)
        return AppResult.Ok(Unit)
    }
    override suspend fun eliminar(id: Long): AppResult<Unit> {
        val s = items.value[id] ?: return AppResult.Err(AppError.NoEncontrado)
        items.value = items.value + (id to s.copy(eliminado = true)); return AppResult.Ok(Unit)
    }
    override suspend fun serviciosQueUsan(insumoId: Long) =
        items.value.values.filter { s -> !s.eliminado && lineas.value[s.id].orEmpty().any { it.insumoId == insumoId } }
}
