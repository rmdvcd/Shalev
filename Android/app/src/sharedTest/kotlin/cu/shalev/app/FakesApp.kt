package cu.shalev.app

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.EstadoPantallasRepository
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PreciosRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.repository.VentaRepository
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.SolicitudInput
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fakes en memoria para los ViewModels de :app (los de :domain no son visibles desde aquí).
 * P17 S6: viven en `src/sharedTest` y los usan tanto los tests JVM como los instrumentados (EntornoIntegracion).
 */

val T0: Instant = Instant.parse("2026-09-30T16:00:00Z") // 12:00 en La Habana

class RelojFijo(var ahora: Instant = T0) : Clock { override fun now() = ahora }

fun prod(id: Long, nombre: String = "P$id", cantidad: Long = 10, venta: Long = 100, costo: Long = 60, categoria: String = "Bebidas") = Producto(
    id = id, categoria = categoria, nombre = nombre, precioCosto = Cup.ofPesos(costo), precioVenta = Cup.ofPesos(venta),
    cantidad = cantidad, creadoEn = T0.minusSeconds(id * 60),
)

/**
 * Licencia simulada. Por defecto: prueba de 5 días, solicitud vacía, activación "no encontrada".
 * Las respuestas son configurables (Prompt 15) y se registra lo recibido.
 */
class LicRepo(estado: LicenseState = LicenseState.Trial(5)) : LicenciaRepository {
    val flow = MutableStateFlow<Licencia?>(Licencia(estado, "SPVI:x", true, "ab"))
    override val snapshot: StateFlow<Licencia?> = flow
    var refrescos = 0
    val solicitudes = mutableListOf<SolicitudInput>()
    val activaciones = mutableListOf<String>()
    var respuestaSolicitud: AppResult<cu.shalev.licencia.SolicitudGenerada> = AppResult.Ok(cu.shalev.licencia.SolicitudGenerada("", ""))
    /** Si devuelve Accepted, el estado pasa a ese [LicenseState] (como la implementación real tras refrescar). */
    var respuestaActivar: ActivationResult = ActivationResult.NotFound
    override suspend fun refrescar(): Licencia { refrescos++; return flow.value!! }
    override suspend fun construirSolicitud(input: SolicitudInput): AppResult<cu.shalev.licencia.SolicitudGenerada> { solicitudes += input; return respuestaSolicitud }
    override suspend fun activar(mensaje: String): ActivationResult {
        activaciones += mensaje
        (respuestaActivar as? ActivationResult.Accepted)?.let { a -> flow.value = flow.value!!.copy(estado = a.state) }
        return respuestaActivar
    }
}

class ProdRepo : ProductoRepository {
    val items = MutableStateFlow<List<Producto>>(emptyList())
    override fun observarTodos(): Flow<List<Producto>> = items
    override fun observar(id: Long) = items.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun obtener(id: Long) = items.value.firstOrNull { it.id == id }
    override suspend fun obtenerVarios(ids: Collection<Long>) = items.value.filter { it.id in ids }
    override suspend fun categoriasEnUso() = items.value.map { it.categoria }.distinct()
    val recetas = mutableMapOf<Long, Receta>()
    val guardados = mutableListOf<Producto>()
    var fallarEliminar = emptySet<Long>()
    override suspend fun crear(producto: Producto, receta: Receta?): AppResult<Long> {
        val id = (items.value.maxOfOrNull { it.id } ?: 0) + 1
        items.value = items.value + producto.copy(id = id)
        guardados += producto.copy(id = id)
        receta?.let { recetas[id] = it.copy(productoId = id) }
        return AppResult.Ok(id)
    }
    override suspend fun actualizar(producto: Producto, receta: Receta?): AppResult<Unit> {
        items.value = items.value.map { if (it.id == producto.id) producto else it }
        guardados += producto
        receta?.let { recetas[producto.id] = it }
        return AppResult.Ok(Unit)
    }
    override suspend fun eliminar(id: Long): AppResult<Unit> {
        if (id in fallarEliminar) return AppResult.Err(AppError.Almacenamiento)
        items.value = items.value.filterNot { it.id == id }
        return AppResult.Ok(Unit)
    }
    override suspend fun ajustarStock(id: Long, delta: Long, nota: String?): AppResult<Long> {
        items.value = items.value.map { if (it.id == id) it.copy(cantidad = it.cantidad + delta) else it }
        return AppResult.Ok(0)
    }
    override suspend fun actualizarPrecios(nuevos: Map<Long, Cup>): AppResult<Unit> = AppResult.Ok(Unit)
    override suspend fun receta(productoId: Long): Receta? = recetas[productoId]
    override suspend fun productosQueUsan(insumoId: Long): List<Producto> =
        recetas.values.filter { r -> r.lineas.any { it.insumoId == insumoId } }.mapNotNull { r -> items.value.firstOrNull { it.id == r.productoId } }

    /** Producción como en :data: descuenta los insumos (si hay [insumos]) y suma las unidades al Elaborado. */
}

class InsRepo : InsumoRepository {
    val items = MutableStateFlow<List<Insumo>>(emptyList())
    override fun observarTodos(): Flow<List<Insumo>> = items
    override suspend fun obtener(id: Long) = items.value.firstOrNull { it.id == id }
    override suspend fun obtenerVarios(ids: Collection<Long>) = items.value.filter { it.id in ids }
    override suspend fun todos() = items.value
    override suspend fun crear(insumo: Insumo): AppResult<Long> = AppResult.Ok(0)
    override suspend fun actualizar(insumo: Insumo): AppResult<Unit> = AppResult.Ok(Unit)
    override suspend fun eliminar(id: Long): AppResult<Unit> = AppResult.Ok(Unit)
    override suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?): AppResult<Cantidad> = AppResult.Ok(delta)
}

/** Ajustes de pantalla en memoria: arranca vacío, como un teléfono recién instalado. */
class FakeEstadoPantallas : EstadoPantallasRepository {
    private val datos = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observar(clave: String): Flow<String?> = datos.map { it[clave] }
    override suspend fun guardar(clave: String, valor: String?) {
        datos.update { if (valor == null) it - clave else it + (clave to valor) }
    }
    fun valor(clave: String): String? = datos.value[clave]
}

class PrefRepo(inicial: Preferencias = Preferencias()) : PreferenciasRepository {
    val state = MutableStateFlow(inicial)
    override val preferencias = state
    override val onboardingCompletado = state.map { it.onboardingCompletado }
    override suspend fun completarOnboarding() { state.value = state.value.copy(onboardingCompletado = true) }
    override suspend fun guardarNiveles(n: NivelesMinimos) { state.value = state.value.copy(niveles = n) }
    override suspend fun guardarModulos(m: Set<cu.shalev.domain.model.Modulo>) { state.value = state.value.copy(modulos = cu.shalev.domain.model.Modulo.normalizar(m)) }
    override suspend fun guardarEmpleadosPrevistos(n: Int) { state.value = state.value.copy(empleadosPrevistos = n) }
    override suspend fun reemplazar(p: Preferencias) { state.value = p }
}

/** Asigna ids a los elementos nuevos, como la implementación de Room. */
class PerfilRepo(inicial: Perfil = Perfil()) : PerfilRepository {
    val state = MutableStateFlow(inicial)
    private var next = 100L
    override val perfil: Flow<Perfil> = state
    override suspend fun guardar(perfil: Perfil) {
        state.value = perfil.copy(
            tarjetas = perfil.tarjetas.map { if (it.id == 0L) it.copy(id = next++) else it },
            telefonos = perfil.telefonos.map { if (it.id == 0L) it.copy(id = next++) else it },
        )
    }
}

class PreciosRepo : PreciosRepository {
    val preajustes = MutableStateFlow<List<PreajustePrecios>>(emptyList())
    override fun observarPreajustes() = preajustes
    override suspend fun preajustesActivos() = preajustes.value.filter { it.activo }
    override suspend fun guardarPreajuste(p: PreajustePrecios): AppResult<Long> {
        val id = if (p.id == 0L) (preajustes.value.maxOfOrNull { it.id } ?: 0L) + 1 else p.id
        preajustes.value = preajustes.value.filterNot { it.id == id } + p.copy(id = id)
        return AppResult.Ok(id)
    }
    override suspend fun eliminarPreajuste(id: Long): AppResult<Unit> { preajustes.value = preajustes.value.filterNot { it.id == id }; return AppResult.Ok(Unit) }
}

class TurnoRepo : TurnoRepository {
    val turnos = MutableStateFlow<List<Turno>>(emptyList())
    override fun observarActivo() = turnos.map { l -> l.firstOrNull { it.abierto } }
    override suspend fun activo() = turnos.value.firstOrNull { it.abierto }
    override suspend fun ultimoCerrado() = turnos.value.filterNot { it.abierto }.maxByOrNull { it.abiertoEn }
    override fun observarHistorial() = turnos
    override suspend fun obtener(id: Long) = turnos.value.firstOrNull { it.id == id }
    val movimientos = mutableListOf<MovimientoInventario>()
    var fallarAbrir = false
    override suspend fun abrir(ahora: Instant, usuario: String, fondo: Cup?): AppResult<Turno> {
        if (fallarAbrir) return AppResult.Err(AppError.Almacenamiento)
        if (activo() != null) return AppResult.Err(AppError.TurnoYaAbierto)
        val t = Turno(id = turnos.value.size + 1L, abiertoEn = ahora, abiertoPor = usuario, fondo = fondo); turnos.value = turnos.value + t; return AppResult.Ok(t)
    }
    override suspend fun cerrar(ahora: Instant, usuario: String, contado: Cup?): AppResult<Turno> {
        val t = activo() ?: return AppResult.Err(AppError.TurnoCerrado)
        val c = t.copy(cerradoEn = ahora, cerradoPor = usuario, contado = contado ?: t.contado, resumen = ResumenTurno.VACIO.copy(numVentas = 2, total = Cup.ofPesos(300)))
        turnos.value = turnos.value.map { if (it.id == t.id) c else it }; return AppResult.Ok(c)
    }
    override suspend fun movimientosDe(turnoId: Long) = movimientos.filter { it.turnoId == turnoId }

    /** 0.31.0: correcciones de un ajuste pedidas desde la ficha de un movimiento. */
    val correcciones = mutableListOf<Triple<Long, Long, String>>()
    var errorCorregir: AppError? = null
    override suspend fun corregirMovimiento(movimientoId: Long, delta: Long, motivo: String, ahora: Long): AppResult<Unit> {
        errorCorregir?.let { return AppResult.Err(it) }
        correcciones += Triple(movimientoId, delta, motivo)
        return AppResult.Ok(Unit)
    }

    /** 0.25.0. */
    val caja = mutableListOf<cu.shalev.domain.model.MovimientoCaja>()
    override suspend fun registrarCaja(
        tipo: cu.shalev.domain.model.TipoMovimientoCaja, importe: Cup, motivo: String, hechoPor: String, ahora: Instant,
    ): AppResult<Long> {
        val t = activo() ?: return AppResult.Err(AppError.TurnoCerrado)
        caja += cu.shalev.domain.model.MovimientoCaja(caja.size + 1L, t.id, ahora, tipo, importe, motivo, hechoPor)
        return AppResult.Ok(caja.size.toLong())
    }
    override suspend fun cajaDe(turnoId: Long) = caja.filter { it.turnoId == turnoId }
    val arqueo = MutableStateFlow<cu.shalev.domain.model.Arqueo?>(null)
    override fun observarArqueoActivo(): Flow<cu.shalev.domain.model.Arqueo?> = arqueo
    override suspend fun declararContado(contado: Cup): AppResult<Unit> {
        val t = activo() ?: return AppResult.Err(AppError.TurnoCerrado)
        turnos.value = turnos.value.map { if (it.id == t.id) it.copy(contado = contado) else it }
        return AppResult.Ok(Unit)
    }
}

/** [fallar] = simula un error de la base de datos. */
class VentaRepo : VentaRepository {
    val ventas = mutableListOf<Venta>()
    private val versionVentas = MutableStateFlow(0)
    override fun observarCambios() = versionVentas.map { Unit }
    override fun observarTotalDeTurno(turnoId: Long) = versionVentas.map {
        ventas.asSequence().filter { it.turnoId == turnoId && !it.anulada }
            .fold(Cup.ZERO) { total, venta -> total + venta.total }
    }
    private fun notificarCambio() { versionVentas.value++ }
    var fallar = false
    /** No null = el registro devuelve este error (stock, turno…) sin guardar nada. */
    var errorRegistro: AppError? = null
    override suspend fun registrar(venta: Venta, elaborados: List<cu.shalev.domain.model.ElaboradoEnVenta>): AppResult<Long> {
        errorRegistro?.let { return AppResult.Err(it) }
        ventas += venta.copy(id = ventas.size + 1L); notificarCambio(); return AppResult.Ok(ventas.size.toLong())
    }
    override suspend fun obtener(id: Long) = ventas.firstOrNull { it.id == id }
    override suspend fun entre(desde: Instant, hasta: Instant): List<Venta> {
        if (fallar) error("disco")
        return ventas.filter { it.fecha >= desde && it.fecha < hasta }
    }
    override suspend fun deTurno(turnoId: Long): List<Venta> {
        if (fallar) error("disco")
        return ventas.filter { it.turnoId == turnoId }
    }

    /** 0.25.0. */
    override suspend fun anular(ventaId: Long, anulacion: cu.shalev.domain.model.Anulacion): AppResult<Unit> {
        errorRegistro?.let { return AppResult.Err(it) }
        val i = ventas.indexOfFirst { it.id == ventaId }
        if (i < 0) return AppResult.Err(AppError.NoEncontrado)
        ventas[i] = ventas[i].copy(anulacion = anulacion); notificarCambio(); return AppResult.Ok(Unit)
    }
    override suspend fun modificar(
        ventaId: Long, anulacion: cu.shalev.domain.model.Anulacion, nueva: Venta, elaborados: List<cu.shalev.domain.model.ElaboradoEnVenta>,
    ): AppResult<Long> {
        anular(ventaId, anulacion).let { if (it is AppResult.Err) return it }
        return registrar(nueva.copy(corrigeVentaId = ventaId), elaborados)
    }
    override suspend fun movimientosDe(ventaId: Long): List<MovimientoInventario> = emptyList()
}

/** P29: servicios en memoria para los tests de la app. */
class ServRepo : cu.shalev.domain.repository.ServicioRepository {
    val items = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, cu.shalev.domain.model.Servicio>>(emptyMap())
    val lineas = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, List<cu.shalev.domain.model.RecetaLinea>>>(emptyMap())
    private var next = 900L
    override fun observarTodos(): kotlinx.coroutines.flow.Flow<List<cu.shalev.domain.model.Servicio>> =
        kotlinx.coroutines.flow.combine(items, lineas) { m, _ -> m.values.filterNot { it.eliminado }.sortedByDescending { it.creadoEn } }
    override fun observarInsumos(): kotlinx.coroutines.flow.Flow<Map<Long, List<cu.shalev.domain.model.RecetaLinea>>> =
        kotlinx.coroutines.flow.combine(items, lineas) { _, l -> l.filterValues { it.isNotEmpty() } }
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

// ------------------------------------------------------------------ P37: Principal / Secundaria

class TipoRepo(tipo: cu.shalev.domain.model.TipoApp = cu.shalev.domain.model.TipoApp.PRINCIPAL) : cu.shalev.domain.repository.TipoAppRepository {
    val flow = MutableStateFlow(tipo)
    val permisosFlow = MutableStateFlow(cu.shalev.domain.model.PermisosApp.PRINCIPAL)
    override val tipo: StateFlow<cu.shalev.domain.model.TipoApp> = flow
    override val permisos: Flow<cu.shalev.domain.model.PermisosApp> = permisosFlow
    override val licenciaPrincipal: Flow<cu.shalev.domain.model.LicenciaPrincipal?> = MutableStateFlow(null)
}

class PrincipalRepoFake : cu.shalev.domain.repository.PrincipalRepository {
    val empleados = MutableStateFlow<List<cu.shalev.domain.model.Empleado>>(emptyList())
    override fun observarEmpleados(): Flow<List<cu.shalev.domain.model.Empleado>> = empleados
    override val estado: StateFlow<cu.shalev.domain.model.EstadoPrincipal> = MutableStateFlow(cu.shalev.domain.model.EstadoPrincipal())
    override suspend fun agregarEmpleado(nombre: String, permisos: Set<cu.shalev.domain.model.PermisoEmpleado>): AppResult<cu.shalev.domain.model.CodigoVinculacion> =
        AppResult.Err(AppError.Red.SinConexion)
    override suspend fun nuevoCodigo(empleadoId: Long): AppResult<cu.shalev.domain.model.CodigoVinculacion> = AppResult.Err(AppError.Red.SinConexion)
    override suspend fun cambiarPermisos(empleadoId: Long, permisos: Set<cu.shalev.domain.model.PermisoEmpleado>): AppResult<Unit> = AppResult.Ok(Unit)
    override suspend fun quitarEmpleado(empleadoId: Long): AppResult<Unit> = AppResult.Ok(Unit)
    override suspend fun cambiarCobro(empleadoId: Long, tarjetaId: Long?, telefonoId: Long?): AppResult<Unit> = AppResult.Ok(Unit)
    override suspend fun pedirCierre(empleadoId: Long): AppResult<Unit> = AppResult.Ok(Unit)
    val aprobados = mutableListOf<Long>()
    val rechazados = mutableListOf<Long>()
    override suspend fun aprobarCierre(empleadoId: Long): AppResult<Unit> { aprobados += empleadoId; return AppResult.Ok(Unit) }
    override suspend fun rechazarCierre(empleadoId: Long): AppResult<Unit> { rechazados += empleadoId; return AppResult.Ok(Unit) }
}

class SecundariaRepoFake : cu.shalev.domain.repository.SecundariaRepository {
    val flow = MutableStateFlow(cu.shalev.domain.model.EstadoSecundaria())
    override val estado: StateFlow<cu.shalev.domain.model.EstadoSecundaria> = flow
    var telefonoVinculado: String? = null
    override suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit> {
        telefonoVinculado = telefono
        return AppResult.Err(AppError.SinPrincipal())
    }
    var contadoDeclarado: Cup? = null
    override suspend fun solicitarCierre(contado: Cup): AppResult<Unit> {
        contadoDeclarado = contado
        flow.value = flow.value.copy(cierreSolicitado = true); return AppResult.Ok(Unit)
    }
    override suspend fun sincronizarAhora(): AppResult<Unit> = AppResult.Err(AppError.SinPrincipal())
    override suspend fun desvincular(): AppResult<Unit> = AppResult.Ok(Unit)
    override suspend fun descartarAvisoCierre() { flow.value = flow.value.copy(turnoCerradoPorPrincipal = false, cierreRechazado = false) }
}

/** 0.25.0: estado propio de la app en memoria (recordatorio de respaldo, versiones, licencia recuperable). */
class EstadoAppRepo(inicial: cu.shalev.domain.model.EstadoApp = cu.shalev.domain.model.EstadoApp()) : cu.shalev.domain.repository.EstadoAppRepository {
    val flujo = MutableStateFlow(inicial)
    override val estado: Flow<cu.shalev.domain.model.EstadoApp> = flujo
    override suspend fun actual() = flujo.value
    override suspend fun editar(cambio: (cu.shalev.domain.model.EstadoApp) -> cu.shalev.domain.model.EstadoApp) { flujo.value = cambio(flujo.value) }
    override suspend fun borrar() { flujo.value = cu.shalev.domain.model.EstadoApp() }
}

/** 0.27.0: ajustes de este teléfono (acceso con clave, tipo elegido en el recorrido). */
class AjustesDispRepo(inicial: cu.shalev.domain.model.AjustesDispositivo = cu.shalev.domain.model.AjustesDispositivo()) :
    cu.shalev.domain.repository.AjustesDispositivoRepository {
    val estado = kotlinx.coroutines.flow.MutableStateFlow(inicial)
    override val ajustes: kotlinx.coroutines.flow.Flow<cu.shalev.domain.model.AjustesDispositivo> = estado
    override suspend fun guardarAccesoConClave(activo: Boolean) { estado.value = estado.value.copy(accesoConClave = activo) }
    override suspend fun guardarEligioSecundaria(valor: Boolean) { estado.value = estado.value.copy(eligioSecundaria = valor) }
}

/** 0.27.0 (N2): clientes fijos en memoria. */
class ClientesFijosRepo(inicial: List<cu.shalev.domain.model.ClienteFijo> = emptyList()) : cu.shalev.domain.repository.ClienteFijoRepository {
    val estado = kotlinx.coroutines.flow.MutableStateFlow(inicial)
    override fun observar(): kotlinx.coroutines.flow.Flow<List<cu.shalev.domain.model.ClienteFijo>> = estado
    override suspend fun quitar(ci: String) { estado.value = estado.value.filterNot { it.ci == ci } }

    /** 0.31.0: como en la base, el carné es único y `false` = ya es de otro cliente. */
    override suspend fun actualizar(ci: String, nombreApellidos: String, nuevoCi: String, telefono: String): Boolean {
        if (nuevoCi != ci && estado.value.any { it.ci == nuevoCi }) return false
        estado.value = estado.value.map {
            if (it.ci == ci) it.copy(nombreApellidos = nombreApellidos, ci = nuevoCi, telefono = telefono) else it
        }
        return true
    }
}
