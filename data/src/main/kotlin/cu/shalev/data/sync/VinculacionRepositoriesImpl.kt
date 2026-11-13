package cu.shalev.data.sync

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.dao.idActivo
import cu.shalev.data.db.tx
import cu.shalev.data.db.abortar
import cu.shalev.data.dto.conteoDeJson
import cu.shalev.data.repository.aplicarConteo
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.model.CodigoVinculacion
import cu.shalev.domain.model.Empleado
import cu.shalev.domain.model.EstadoPrincipal
import cu.shalev.domain.model.EstadoSecundaria
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.PermisosApp
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.Vinculacion
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.MantenimientoRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.PrincipalRepository
import cu.shalev.domain.repository.PuertaTurno
import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.domain.repository.TipoAppRepository
import cu.shalev.domain.usecase.UsuarioActual
import cu.shalev.licencia.LicenseState
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private fun EmpleadoEntity.toDomain() = Empleado(
    id = id, nombre = nombre, permisos = PermisoEmpleado.deNombres(permisos.split(',')),
    creadoEn = Instant.ofEpochMilli(creadoEn), vinculadoEn = vinculadoEn?.let(Instant::ofEpochMilli),
    ultimaSincronizacion = ultimaSincronizacion?.let(Instant::ofEpochMilli),
    codigoVence = codigoVence?.let(Instant::ofEpochMilli),
    tarjetaId = tarjetaId, telefonoId = telefonoId,
    cierreSolicitadoEn = cierreSolicitadoEn?.let(Instant::ofEpochMilli),
    telefono = telefono,
    // 0 = rechazo pendiente de avisar: para la principal ya no es una solicitud.
    cierrePedidoPorEmpleadoEn = cierrePedidoPorEmpleadoEn?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    versionCode = versionCode,
)

private fun Set<PermisoEmpleado>.csv() = map { it.name }.sorted().joinToString(",")

// ======================================================================================== tipo de app

@Singleton
class TipoAppRepositoryImpl @Inject constructor(
    private val config: ConfigSync,
    private val preferencias: PreferenciasRepository,
    @IoDispatcher io: CoroutineDispatcher,
) : TipoAppRepository {
    private val scope = CoroutineScope(SupervisorJob() + io)

    override val tipo: StateFlow<TipoApp> = config.tipo.stateIn(scope, SharingStarted.Eagerly, TipoApp.PRINCIPAL)

    /** 0.21.0 (C12): con los módulos del negocio (en la secundaria, los que mandó la principal). */
    override val permisos: Flow<PermisosApp> = combine(config.tipo, config.secundaria, preferencias.preferencias) { t, d, p ->
        if (t == TipoApp.PRINCIPAL || d == null) PermisosApp.PRINCIPAL.copy(modulos = p.modulos)
        else PermisosApp(TipoApp.SECUNDARIA, d.permisosEmpleado, p.modulos)
    }.distinctUntilChanged()

    override val licenciaPrincipal: Flow<LicenciaPrincipal?> = combine(config.tipo, config.secundaria) { t, d ->
        if (t == TipoApp.SECUNDARIA) d?.licenciaPrincipal ?: LicenciaPrincipal(LicenciaPrincipal.Clase.BLOQUEADA) else null
    }
}

// ======================================================================================== principal

@Singleton
class PrincipalRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
    private val config: ConfigSync,
    private val servidor: ServidorSync,
    private val red: RedLocal,
    private val licencia: LicenciaRepository,
    private val perfil: PerfilRepository,
    private val clock: Clock,
) : PrincipalRepository {
    private val sync get() = db.syncDao()

    override fun observarEmpleados(): Flow<List<Empleado>> =
        combine(sync.observarEmpleados(), sync.observarTurnosDeEmpleados()) { l, turnos ->
            val porEmpleado = turnos.associateBy { it.empleadoId }
            l.map { e ->
                val tu = porEmpleado[e.id]
                e.toDomain().copy(
                    turnoAbiertoDesde = tu?.let { Instant.ofEpochMilli(it.abiertoEn) },
                    conteoCierre = tu?.conteoCierreJson?.conteoDeJson(),
                    arqueoTurno = tu?.fondoCent?.let { f ->
                        cu.shalev.domain.model.Arqueo(
                            cu.shalev.core.money.Cup(f), cu.shalev.core.money.Cup(tu.efectivoCent), cu.shalev.core.money.Cup(tu.entradasCent),
                            cu.shalev.core.money.Cup(tu.salidasCent), tu.contadoCent?.let { c -> cu.shalev.core.money.Cup(c) },
                        )
                    },
                )
            }
        }
    override val estado: StateFlow<EstadoPrincipal> get() = servidor.estado

    override suspend fun agregarEmpleado(nombre: String, permisos: Set<PermisoEmpleado>): AppResult<CodigoVinculacion> {
        licenciaPermite()?.let { return AppResult.Err(it) }
        noEsSecundaria()?.let { return AppResult.Err(it) }
        val actuales = sync.empleados()
        when (Vinculacion.validarNombre(nombre, actuales.map { it.nombre })) {
            Vinculacion.ErrorNombre.VACIO -> return AppResult.Err(AppError.Validacion("nombre", AppError.Regla.REQUERIDO))
            Vinculacion.ErrorNombre.LARGO -> return AppResult.Err(AppError.Validacion("nombre", AppError.Regla.RANGO))
            Vinculacion.ErrorNombre.REPETIDO -> return AppResult.Err(AppError.Duplicado("nombre"))
            null -> Unit
        }
        // 0.21.0 (C4): el tope lo pone la licencia (X secundarias pagadas; prueba = 5).
        if (!Vinculacion.puedeAgregar(actuales.size, limiteSecundarias())) return AppResult.Err(AppError.SinPermiso)
        val id = sync.insertarEmpleado(
            EmpleadoEntity(
                nombre = nombre.trim(), permisos = permisos.csv(), creadoEn = clock.now().toEpochMilli(), localId = db.almacenLocalDao().idActivo(),
                vinculadoEn = null, ultimaSincronizacion = null, clave = null, codigoToken = null, codigoVence = null, activo = true,
            ),
        )
        return nuevoCodigo(id)
    }

    override suspend fun nuevoCodigo(empleadoId: Long): AppResult<CodigoVinculacion> {
        licenciaPermite()?.let { return AppResult.Err(it) }
        noEsSecundaria()?.let { return AppResult.Err(it) }
        val e = sync.empleado(empleadoId)?.takeIf { it.activo } ?: return AppResult.Err(AppError.NoEncontrado)
        // 0.21.0 (C4): una ficha pendiente (p. ej. restaurada) solo se vincula si la licencia cubre otra secundaria.
        if (e.vinculadoEn == null && sync.empleados().count { it.vinculadoEn != null } >= limiteSecundarias()) {
            return AppResult.Err(AppError.SinPermiso)
        }
        servidor.iniciar()
        val est = withTimeoutOrNull(3_000) { servidor.estado.first { it.escuchando } }
            ?: return AppResult.Err(AppError.Red.SinConexion)
        val direcciones = red.direcciones().ifEmpty { est.direcciones }
        if (direcciones.isEmpty() && !RelaySync.habilitado) return AppResult.Err(AppError.Red.SinConexion)
        val token = CriptoSync.aleatorio(CodigoQr.TOKEN_BYTES)
        val vence = clock.now().plus(Vinculacion.VALIDEZ_CODIGO)
        // Un QR nuevo anula la clave anterior: el teléfono viejo deja de poder sincronizar.
        servidor.expulsar(e.id)
        sync.actualizarEmpleado(e.copy(clave = null, vinculadoEn = null, codigoToken = CriptoSync.enc(token), codigoVence = vence.toEpochMilli()))
        return AppResult.Ok(
            CodigoVinculacion(
                negocioId = config.negocioId(), nombreNegocio = UsuarioActual.nombreDe(perfil.perfil.first()),
                direcciones = direcciones, puerto = est.puerto, empleadoId = e.id, nombreEmpleado = e.nombre,
                token = token, venceEn = vence,
            ),
        )
    }

    override suspend fun cambiarPermisos(empleadoId: Long, permisos: Set<PermisoEmpleado>): AppResult<Unit> {
        val e = sync.empleado(empleadoId)?.takeIf { it.activo } ?: return AppResult.Err(AppError.NoEncontrado)
        sync.actualizarEmpleado(e.copy(permisos = permisos.csv()))
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun cambiarCobro(empleadoId: Long, tarjetaId: Long?, telefonoId: Long?): AppResult<Unit> {
        if (sync.cambiarCobro(empleadoId, tarjetaId, telefonoId) == 0) return AppResult.Err(AppError.NoEncontrado)
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun pedirCierre(empleadoId: Long): AppResult<Unit> {
        if (db.turnoDao().activoDeEmpleado(empleadoId) == null) return AppResult.Err(AppError.TurnoCerrado)
        if (sync.pedirCierre(empleadoId, clock.now().toEpochMilli()) == 0) return AppResult.Err(AppError.NoEncontrado)
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun aprobarCierre(empleadoId: Long): AppResult<Unit> {
        val usuario = UsuarioActual.nombreDe(perfil.perfil.first())
        val r = db.tx {
            val t = db.turnoDao().activoDeEmpleado(empleadoId) ?: abortar(AppError.TurnoCerrado)
            db.aplicarConteo(t.id, usuario, clock.now().toEpochMilli())
            if (sync.pedirCierre(empleadoId, clock.now().toEpochMilli()) == 0) abortar(AppError.NoEncontrado)
            sync.marcarSolicitudCierre(empleadoId, null)
        }
        if (r is AppResult.Ok) servidor.avisar()
        return r
    }

    override suspend fun rechazarCierre(empleadoId: Long): AppResult<Unit> {
        val e = sync.empleado(empleadoId)?.takeIf { it.activo } ?: return AppResult.Err(AppError.NoEncontrado)
        if (e.cierrePedidoPorEmpleadoEn == null && e.cierreSolicitadoEn == null) return AppResult.Ok(Unit)
        sync.olvidarCierre(empleadoId)
        sync.marcarSolicitudCierre(empleadoId, RECHAZO_PENDIENTE)
        servidor.avisar()
        return AppResult.Ok(Unit)
    }

    override suspend fun quitarEmpleado(empleadoId: Long): AppResult<Unit> {
        val e = sync.empleado(empleadoId) ?: return AppResult.Err(AppError.NoEncontrado)
        // Se conserva la clave: así, al volver a conectar, esa app recibe un «quitada» firmado y borra sus datos.
        sync.actualizarEmpleado(e.copy(activo = false, codigoToken = null, codigoVence = null))
        servidor.expulsar(empleadoId)
        return AppResult.Ok(Unit)
    }

    companion object {
        /** 0.21.0 (C6): valor de `cierrePedidoPorEmpleadoEn` para «rechazada, falta avisar al empleado». */
        const val RECHAZO_PENDIENTE = 0L
    }

    private fun limiteSecundarias(): Int = licencia.snapshot.value?.secundariasPermitidas ?: Vinculacion.SECUNDARIAS_DEFECTO

    /** 0.21.0 (C5, P46): una app secundaria no crea secundarias (solo hay una principal por negocio). */
    private suspend fun noEsSecundaria(): AppError? =
        if (config.tipo.first() == TipoApp.SECUNDARIA) AppError.SinPermiso else null

    private fun licenciaPermite(): AppError? = when (licencia.snapshot.value?.estado) {
        is LicenseState.Trial, is LicenseState.Active, is LicenseState.Perpetual -> null
        else -> AppError.LicenciaBloqueada
    }
}

// ======================================================================================== secundaria

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SecundariaRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
    private val config: ConfigSync,
    private val cliente: ClienteSync,
    private val mantenimiento: MantenimientoRepository,
    private val preferencias: PreferenciasRepository,
    @IoDispatcher io: CoroutineDispatcher,
    /** 0.25.0: conteo declarado al solicitar el cierre. */
    private val turnos: cu.shalev.data.repository.TurnoRepositoryImpl,
) : SecundariaRepository {
    private val scope = CoroutineScope(SupervisorJob() + io)

    init {
        cliente.alSerQuitada = { olvidar() }
    }

    override val estado: StateFlow<EstadoSecundaria> =
        config.secundaria.flatMapLatest { d ->
            if (d == null) flowOf(EstadoSecundaria())
            else combine(
                cliente.conexion, db.syncDao().observarPendientes(), cliente.apkPrincipal,
                cliente.progreso, cliente.cambiosPendientes,
            ) { c, p, apk, prog, cambios ->
                EstadoSecundaria(
                    vinculada = true,
                    nombreNegocio = d.nombreNegocio, nombreEmpleado = d.nombreEmpleado, permisos = d.permisosEmpleado,
                    conexion = c, ultimaSincronizacion = d.ultimaSincronizacion?.let(Instant::ofEpochMilli),
                    pendientes = p, licencia = d.licenciaPrincipal,
                    cierrePendiente = d.cierrePendiente, turnoCerradoPorPrincipal = d.avisoCierre,
                    cierreSolicitado = d.cierreSolicitado, cierreRechazado = d.avisoRechazo, telefono = d.telefono,
                    apk = apk?.let { cu.shalev.domain.model.ApkEnPrincipal(it.nombre, it.code, it.bytes) },
                    cambiosPendientes = cambios, progreso = prog,
                    operacionPendiente = d.comandoPendiente != null, operacionRecuperada = d.operacionRecuperada,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, EstadoSecundaria())

    override suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit> {
        // 0.21.0 (C2): el teléfono del empleado va en su QR de cobro (el SMS de Transfermóvil le llega a él).
        if (!Vinculacion.telefonoValido(telefono)) return AppResult.Err(AppError.Validacion("telefono", AppError.Regla.FORMATO))
        val codigo = CodigoQr.decodificar(contenidoQr)
            ?: return AppResult.Err(AppError.VinculacionRechazada(ClienteSync.TEXTO_CODIGO_INVALIDO))
        if (!codigo.venceEn.isAfter(Instant.now())) {
            return AppResult.Err(AppError.VinculacionRechazada(ClienteSync.textoRechazo(Rechazo.CODIGO_VENCIDO)))
        }
        val datos = when (val r = cliente.vincular(codigo)) {
            is AppResult.Ok -> r.value.copy(telefono = telefono.trim())
            is AppResult.Err -> return r
        }
        // Vinculada: desde aquí este teléfono trabaja con los datos de la principal.
        val borrado = mantenimiento.borrarTodo()
        if (borrado is AppResult.Err) return borrado
        db.syncDao().empleados().forEach { db.syncDao().actualizarEmpleado(it.copy(activo = false, clave = null)) }
        config.hacerSecundaria(datos)
        preferencias.completarOnboarding()
        val primera = cliente.sincronizar()
        // Si la primera descarga falla, la app ya está vinculada: lo reintenta el usuario («Sincronizar ahora»).
        return if (primera is AppResult.Err && primera.error !is AppError.SinPrincipal) primera else AppResult.Ok(Unit)
    }

    override suspend fun sincronizarAhora(): AppResult<Unit> = cliente.sincronizar()

    override suspend fun desvincular(): AppResult<Unit> = olvidar()

    override suspend fun descartarAvisoCierre() = config.editarSecundaria { it.copy(avisoCierre = false, avisoRechazo = false) }

    override suspend fun descargarApk(progreso: (Long, Long) -> Unit): AppResult<java.io.File> = cliente.descargarApk(progreso)

    override suspend fun solicitarCierre(contado: cu.shalev.core.money.Cup): AppResult<Unit> {
        if (db.turnoDao().activo() == null) return AppResult.Err(AppError.TurnoCerrado)
        // 0.25.0 (§5.3): el conteo viaja con la solicitud (en el turno abierto, que vuelve a quedar pendiente de enviar).
        when (val r = turnos.declararContado(contado)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> Unit
        }
        config.editarSecundaria { it.copy(cierreSolicitado = true, avisoRechazo = false) }
        // Sin conexión no es un error: la solicitud espera y se envía en la próxima sincronización.
        cliente.sincronizar()
        return AppResult.Ok(Unit)
    }

    private suspend fun olvidar(): AppResult<Unit> {
        cliente.cerrarSesion()
        val r = mantenimiento.borrarTodo()
        config.hacerPrincipal()
        return r
    }
}

// ======================================================================================== turno

/** «No se empieza un turno nuevo sin sincronizar»: solo aplica a la secundaria. */
@Singleton
class PuertaTurnoImpl @Inject constructor(
    private val config: ConfigSync,
    private val cliente: ClienteSync,
) : PuertaTurno {
    override suspend fun antesDeAbrir(): AppResult<Unit> =
        if (config.tipo.first() == TipoApp.PRINCIPAL) AppResult.Ok(Unit) else cliente.sincronizar()
}

// ======================================================================================== arranque

/**
 * Pone en marcha la sincronización al abrir la app: en la principal, el servidor (si tiene secundarias); en una
 * secundaria en modo automático, el cliente. Lo llama ShalevApplication.onCreate.
 */
@Singleton
class ArranqueSync @Inject constructor(
    private val db: ShalevDatabase,
    private val config: ConfigSync,
    private val servidor: ServidorSync,
    private val cliente: ClienteSync,
    private val secundaria: SecundariaRepository, // instancia el manejador de «quitada»
    private val cierre: CierreRemoto,
    @IoDispatcher io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)

    /**
     * 0.19.2: lo que necesita el servicio en primer plano de la principal (ServicioSync en :app). Solo emite
     * `principalConSecundarias = true` en la app PRINCIPAL con al menos una secundaria vinculada.
     */
    val atencion: Flow<AtencionSync> = combine(
        config.tipo,
        db.syncDao().observarEmpleados().map { it.isNotEmpty() }.distinctUntilChanged(),
        db.syncDao().observarTurnosAbiertos(),
        servidor.estado.map { it.conectadas.size }.distinctUntilChanged(),
    ) { tipo, hayEmpleados, abiertos, conectadas ->
        val principal = tipo == TipoApp.PRINCIPAL && hayEmpleados
        AtencionSync(principal, if (principal) abiertos else 0, if (principal) conectadas else 0)
    }.distinctUntilChanged()

    fun iniciar() {
        cierre.iniciar()
        scope.launch {
            combine(config.tipo, db.syncDao().observarEmpleados()) { t, e -> t to e.isNotEmpty() }
                .distinctUntilChanged()
                .collect { (tipo, hayEmpleados) ->
                    if (tipo == TipoApp.PRINCIPAL) {
                        if (hayEmpleados) servidor.iniciar() else servidor.detener()
                    } else {
                        // En una secundaria no hay nada automático: el servidor de la principal es el que escucha.
                        servidor.detener()
                        // 0.32.0: la secundaria mantiene la conexión con la principal abierta (el ping la refresca),
                        // para que el dueño vea si su app está conectada. Va en su propia corrutina: buscar la
                        // principal puede tardar unos segundos.
                        scope.launch { cliente.conectar() }
                    }
                }
        }
    }
}

/** 0.19.2: estado que decide si la principal mantiene el servicio en primer plano y qué dice la notificación. */
data class AtencionSync(
    val principalConSecundarias: Boolean = false,
    /** Turnos abiertos en la BD de la principal (el suyo y los de las secundarias). */
    val turnosAbiertos: Int = 0,
    /** Secundarias conectadas ahora mismo. */
    val conectadas: Int = 0,
)
