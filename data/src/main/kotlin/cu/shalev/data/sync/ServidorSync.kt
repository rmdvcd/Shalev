package cu.shalev.data.sync

import cu.shalev.data.dto.conteoDeJson
import cu.shalev.data.dto.toDto

import cu.shalev.core.result.runCatchingCancelable
import androidx.room.InvalidationTracker
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.domain.model.EstadoPrincipal
import cu.shalev.domain.model.FaseSync
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.domain.model.ProgresoSync
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.usecase.UsuarioActual
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import cu.shalev.domain.model.TipoApp

/**
 * P37: servidor de la app PRINCIPAL en la red local (TCP, puerto [PUERTO] o el siguiente libre).
 * Vive mientras el proceso de la app viva; desde 0.19.2 el servicio en primer plano ServicioSync (:app) mantiene ese proceso mientras haya turnos abiertos. Si la principal se
 * cierra, las secundarias siguen vendiendo hasta cerrar su turno y envían todo al volver a conectar.
 *
 * Cada conexión: saludo → (vinculación | sesión cifrada). En sesión atiende Sincronizar, Comando y Ping, y avisa a
 * todas las secundarias conectadas cuando cambia el catálogo, la licencia o sus permisos.
 */
@Singleton
class ServidorSync @Inject constructor(
    private val db: ShalevDatabase,
    private val almacen: AlmacenSync,
    private val ejecutor: EjecutorComandos,
    private val config: ConfigSync,
    private val red: RedLocal,
    private val licencia: LicenciaRepository,
    private val perfil: PerfilRepository,
    private val clock: Clock,
    /** 0.25.0: APK de actualización que se reparte a las secundarias. */
    private val apk: ApkLocal,
    /** 0.32.0: fotos del catálogo que se reparten a las secundarias. */
    private val fotos: cu.shalev.data.foto.FotosLocal,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relay = RelayPrincipal(db, config)
    private val _estado = MutableStateFlow(EstadoPrincipal())

    /** 0.32.0: avance de la sincronización de cada app conectada (id de empleado → avance). */
    private val _progresos = MutableStateFlow<Map<Long, ProgresoSync>>(emptyMap())

    /** 0.32.0: aviso de que una app terminó de sincronizar (id de empleado). */
    private val _sincronizadas = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val sincronizadas: SharedFlow<Long> = _sincronizadas.asSharedFlow()

    /** El estado que ve la app: lo que escucha el servidor + el avance de cada app conectada. */
    val estado: StateFlow<EstadoPrincipal> =
        combine(_estado, _progresos) { e, p -> e.copy(progresos = p) }
            .stateIn(scope, SharingStarted.Eagerly, EstadoPrincipal())

    private val sesiones = ConcurrentHashMap<Long, Sesion>()
    /** Auditoría 2026-10-14 (S-04): tope de conexiones simultáneas (el escritorio ya limita; Android no). */
    private val cupoConexiones = Semaphore(MAX_CONEXIONES)
    private val vinculando = Mutex()
    private val avisos = Channel<Unit>(Channel.CONFLATED)
    private var trabajo: Job? = null
    @Volatile private var socket: ServerSocket? = null

    private class Sesion(val empleadoId: Long, val socket: Socket, val canal: CanalCifrado) {
        val escritura = Mutex()
        suspend fun enviar(m: Mensaje, progreso: ((Long, Long) -> Unit)? = null) =
            escritura.withLock { withContext(Dispatchers.IO) { canal.enviar(m, progreso) } }
        fun cerrar() { runCatching { socket.close() }; canal.borrarClaves() }
    }

    private val observador = object : InvalidationTracker.Observer(arrayOf(
        "producto", "insumo", "servicio", "receta_linea", "servicio_insumo", "preajuste", "preajuste_producto", "preajuste_servicio",
        "perfil", "tarjeta", "telefono",
    )) {
        override fun onInvalidated(tables: Set<String>) { avisar() }
    }

    /** Idempotente. Lo llama ArranqueSync cuando esta app es PRINCIPAL y tiene (o está agregando) secundarias. */
    @Synchronized
    fun iniciar() {
        if (trabajo?.isActive == true) return
        trabajo = scope.launch {
            val ss = abrirSocket() ?: run { _estado.update { it.copy(escuchando = false) }; return@launch }
            socket = ss
            db.invalidationTracker.addObserver(observador)
            red.anunciar(config.negocioId(), ss.localPort)
            _estado.update { it.copy(escuchando = true, puerto = ss.localPort, direcciones = red.direcciones()) }
            launch { refrescarDirecciones() }
            launch {
                relay.ejecutar(this) { remoto ->
                    if (!cupoConexiones.tryAcquire()) remoto.close()
                    else try { atender(remoto) } finally { cupoConexiones.release() }
                }
            }
            launch { repartirAvisos() }
            launch { licencia.snapshot.map { it?.estado }.distinctUntilChanged().collect { avisar() } }
            try {
                while (isActive) {
                    val s = withContext(Dispatchers.IO) { ss.accept() }
                    // S-04: sin cupo, una ráfaga de conexiones agota hilos y memoria; la de más se corta.
                    if (!cupoConexiones.tryAcquire()) {
                        runCatching { s.close() }
                        continue
                    }
                    launch {
                        try {
                            atender(s)
                        } finally {
                            cupoConexiones.release()
                        }
                    }
                }
            } catch (e: IOException) {
                // socket cerrado al detener
            } finally {
                relay.detener()
                db.invalidationTracker.removeObserver(observador)
                red.dejarDeAnunciar()
                runCatching { ss.close() }
                sesiones.values.forEach { it.cerrar() }
                sesiones.clear()
                _estado.value = EstadoPrincipal()
            }
        }
    }

    @Synchronized
    fun detener() {
        relay.detener()
        runCatching { socket?.close() }
        socket = null
        trabajo?.cancel()
        trabajo = null
    }

    /** Algo cambió (catálogo, permisos, licencia): las secundarias conectadas sincronizan. */
    fun avisar() { avisos.trySend(Unit) }

    /**
     * 0.32.0: anota el avance de la sincronización de una app. Solo se publica cuando cambia el porcentaje (o la
     * fase): la barra no se repinta en cada trozo de 64 KB.
     */
    private fun progreso(empleadoId: Long, fase: FaseSync, actual: Long, total: Long) {
        _progresos.update { m ->
            val antes = m[empleadoId]
            if (antes != null && antes.fase == fase && total > 0 && antes.actual * 100 / total == actual * 100 / total) m
            else m + (empleadoId to ProgresoSync(actual, total, fase))
        }
    }

    /** El dueño quitó esta secundaria: si está conectada se le dice ahora; si no, al volver a conectar. */
    fun expulsar(empleadoId: Long) {
        val s = sesiones.remove(empleadoId) ?: return
        scope.launch {
            runCatching { s.enviar(Quitada) }
            s.cerrar()
            _estado.update { it.copy(conectadas = it.conectadas - empleadoId) }
        }
    }

    private fun abrirSocket(): ServerSocket? {
        for (p in PUERTO until PUERTO + 10) {
            try {
                return ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) }
            } catch (e: IOException) {
                continue
            }
        }
        return null
    }

    private suspend fun refrescarDirecciones() {
        while (true) {
            delay(10_000)
            val d = red.direcciones()
            if (d != _estado.value.direcciones) _estado.update { it.copy(direcciones = d) }
        }
    }

    private suspend fun repartirAvisos() {
        for (u in avisos) {
            delay(800) // agrupa ráfagas (una venta toca varias tablas)
            sesiones.values.toList().forEach { s -> runCatchingCancelable { s.enviar(Aviso) }.onFailure { s.cerrar() } }
        }
    }

    // ---------------------------------------------------------------- conexión

    private suspend fun atender(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = LECTURA_MS
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            when (val s = withContext(Dispatchers.IO) { Saludos.recibir(input) }) {
                is Vincular -> { vincular(s, output); socket.close() }
                is Hola -> sesion(s, socket, input, output)
                else -> socket.close()
            }
        } catch (e: CancellationException) {
            runCatching { socket.close() }
            throw e
        } catch (e: Exception) {
            runCatching { socket.close() }
        }
    }

    private suspend fun vincular(v: Vincular, out: OutputStream) = vinculando.withLock {
        val rechazar = { motivo: String -> Saludos.enviar(out, Rechazo(motivo)) }
        if (v.v != VERSION_PROTOCOLO) return@withLock rechazar(Rechazo.VERSION)
        // 0.21.0 (C5): una sola principal por negocio; una secundaria nunca acepta vínculos.
        if (config.tipo.first() != TipoApp.PRINCIPAL) return@withLock rechazar(Rechazo.DESCONOCIDA)
        if (v.negocio != config.negocioId()) return@withLock rechazar(Rechazo.OTRO_NEGOCIO)
        val e = db.syncDao().empleado(v.empleado)?.takeIf { it.activo } ?: return@withLock rechazar(Rechazo.CODIGO_INVALIDO)
        val token = e.codigoToken?.let(CriptoSync::dec) ?: return@withLock rechazar(Rechazo.CODIGO_INVALIDO)
        val vence = e.codigoVence ?: 0
        if (clock.now().toEpochMilli() >= vence) return@withLock rechazar(Rechazo.CODIGO_VENCIDO)
        val pubC = CriptoSync.dec(v.pub)
        if (!CriptoSync.iguales(CriptoSync.macVinculoSecundaria(token, pubC), CriptoSync.dec(v.mac))) {
            return@withLock rechazar(Rechazo.CODIGO_INVALIDO)
        }
        val par = CriptoSync.parNuevo()
        val pubS = par.public.encoded
        val clave = CriptoSync.claveEmpleado(par, pubC, token)
        // El token se gasta aquí: un segundo intento con el mismo QR ya no vale.
        db.syncDao().actualizarEmpleado(
            e.copy(clave = CriptoSync.enc(clave), codigoToken = null, codigoVence = null, vinculadoEn = clock.now().toEpochMilli()),
        )
        // 0.31.0 (mitigación 7): se anota el `deviceId` de la secundaria para poder firmarle su sub-token.
        anotarDevice(e.id, v.device)
        clave.fill(0)
        withContext(Dispatchers.IO) {
            Saludos.enviar(out, VincularOk(CriptoSync.enc(pubS), CriptoSync.enc(CriptoSync.macVinculoPrincipal(token, pubS, pubC)), nombreNegocio()))
        }
    }

    private suspend fun sesion(h: Hola, socket: Socket, input: InputStream, out: OutputStream) {
        val nonceC = CriptoSync.dec(h.nonce)
        if (h.v != VERSION_PROTOCOLO) return Saludos.enviar(out, Rechazo(Rechazo.VERSION)).also { socket.close() }
        if (config.tipo.first() != TipoApp.PRINCIPAL) return Saludos.enviar(out, Rechazo(Rechazo.DESCONOCIDA)).also { socket.close() } // C5
        if (nonceC.size != CriptoSync.NONCE_BYTES) return socket.close()
        if (h.negocio != config.negocioId()) return Saludos.enviar(out, Rechazo(Rechazo.OTRO_NEGOCIO)).also { socket.close() }
        val e = db.syncDao().empleado(h.empleado)
        val clave = e?.clave?.let(CriptoSync::dec)
        if (e == null || clave == null) return Saludos.enviar(out, Rechazo(Rechazo.DESCONOCIDA)).also { socket.close() }
        if (!e.activo) {
            val mac = CriptoSync.enc(CriptoSync.macRechazo(clave, nonceC, Rechazo.QUITADA))
            return Saludos.enviar(out, Rechazo(Rechazo.QUITADA, mac)).also { socket.close() }
        }
        val nonceS = CriptoSync.aleatorio(CriptoSync.NONCE_BYTES)
        withContext(Dispatchers.IO) { Saludos.enviar(out, HolaOk(CriptoSync.enc(nonceS), comandosUnicos = true)) }
        val canal = CanalCifrado.paraPrincipal(input, out, CriptoSync.clavesSesion(clave, nonceC, nonceS))
        clave.fill(0)
        val s = Sesion(e.id, socket, canal)
        try {
            // Auditoría 2026-10-14 (S-03): la sesión NO se registra (ni desplaza a la legítima, ni anota el
            // `deviceId`) hasta recibir el PRIMER mensaje cifrado: hasta ahí, el saludo solo prueba conocer
            // identificadores, no poseer la clave del empleado. El escritorio ya lo hacía así.
            var pendiente: Mensaje? = withContext(Dispatchers.IO) {
                canal.recibir { hecho, total -> progreso(e.id, FaseSync.RECIBIENDO, hecho, total) }
            }
            // 0.31.0 (mitigación 7): el saludo repite el `deviceId` de la secundaria (p. ej. tras reinstalar la
            // principal desde un respaldo, que no guarda los `deviceId`); ya autenticada, se anota el válido.
            if (h.device != null) anotarDevice(e.id, h.device)
            sesiones.put(e.id, s)?.cerrar() // la misma app reconectando: se queda la conexión nueva
            _estado.update { it.copy(conectadas = it.conectadas + e.id) }
            while (true) {
                val m = pendiente ?: withContext(Dispatchers.IO) {
                    canal.recibir { hecho, total -> progreso(e.id, FaseSync.RECIBIENDO, hecho, total) }
                }
                pendiente = null
                val actual = db.syncDao().empleado(e.id)
                if (actual == null || !actual.activo) { runCatching { s.enviar(Quitada) }; break }
                when (m) {
                    is Sincronizar -> {
                        val ok = responder(actual, m) ?: break
                        s.enviar(ok) { hecho, total -> progreso(e.id, FaseSync.ENVIANDO, hecho, total) }
                        _sincronizadas.tryEmit(e.id)
                    }
                    is Comando -> s.enviar(ejecutor.resultado(m, actual))
                    is Ping -> s.enviar(Pong(m.id))
                    is PedirApk -> s.enviar(withContext(Dispatchers.IO) { apk.bloque(m.id, m.sha256, m.desde) })
                    is PedirFoto -> s.enviar(fotos.bloque(m.id, m.nombre, m.desde))
                    else -> Unit
                }
            }
        } finally {
            s.cerrar()
            _progresos.update { it - e.id }
            if (sesiones.remove(e.id, s)) _estado.update { it.copy(conectadas = it.conectadas - e.id) }
        }
    }

    /** null = error al guardar: se corta la conexión y la secundaria reintenta (nada se marcó como recibido). */
    private suspend fun responder(e: EmpleadoEntity, m: Sincronizar): SincronizarOk? {
        val recibidos = when (val r = almacen.aplicarLote(e.id, m.lote)) {
            is AppResult.Ok -> r.value
            is AppResult.Err -> return null
        }
        // 0.25.0: lo que la secundaria ya aplicó deja de enviarse; lo demás va (o vuelve a ir) en esta respuesta.
        runCatchingCancelable { almacen.cambiosConfirmados(e.id, m.cambiosAplicados) }
        val cambios = runCatchingCancelable { almacen.cambiosPara(e.id) }.getOrDefault(emptyList())
        // 0.21.0 (C2): teléfono del empleado (solo si es válido y cambió).
        m.telefono?.trim()?.takeIf { it != e.telefono && cu.shalev.domain.model.Vinculacion.telefonoValido(it) }
            ?.let { db.syncDao().cambiarTelefono(e.id, it) }
        // 0.25.0: versión de la app del empleado (para saber si tiene el APK nuevo).
        if (m.versionCode != e.versionCode) db.syncDao().cambiarVersion(e.id, m.versionCode)
        val rechazado = solicitudCierre(e, m.solicitaCierre)
        val lic = conPermiso(
            licencia.snapshot.value?.estado ?: cu.shalev.licencia.LicenseState.TrialExpired, e,
        )
        val inst = when (val r = almacen.instantanea(e, nombreNegocio(), lic)) {
            is AppResult.Ok -> r.value
            is AppResult.Err -> return null
        }
        val hash = AlmacenSync.hash(inst)
        return SincronizarOk(
            m.id, recibidos, hash, inst.takeIf { hash != m.hash }, cerrarTurno = cierrePendiente(e), cierreRechazado = rechazado,
            conteoAprobado = db.turnoDao().activoDeEmpleado(e.id)?.conteoCierreJson?.conteoDeJson()?.takeIf { it.aprobadoEn != null }?.toDto(),
            conteoTurnoUuid = db.turnoDao().activoDeEmpleado(e.id)?.uuid,
            cambiosVentas = cambios, apk = runCatching { apk.ofrecerA(m.versionCode) }.getOrNull(),
        )
    }

    /**
     * 0.31.0 (mitigación 7, Opción A): la licencia que viaja a la secundaria lleva, además del estado, el **permiso
     * firmado** de su ranura ([LicenciaDto.subToken] + la clave para comprobarlo). Sin `deviceId` conocido o sin firmante
     * en el build se manda solo el estado, como antes (una secundaria 0.31+ lo acepta: es una principal anterior).
     */
    private suspend fun conPermiso(estado: cu.shalev.licencia.LicenseState, e: EmpleadoEntity): LicenciaDto {
        val base = LicenciaPrincipal.de(estado, clock.now())
        val device = e.device?.trim().orEmpty()
        if (device.isEmpty()) return base.aDto()
        // Ranura = puesto del empleado entre los activos, en el orden de vinculación (1..N), como en la app de escritorio.
        val ranura = db.syncDao().empleadosActivosIds().indexOf(e.id) + 1
        if (ranura <= 0) return base.aDto()
        val permiso = licencia.emitirSubToken(device, ranura) ?: return base.aDto()
        return base.copy(
            subToken = permiso.subToken, firmaPub = permiso.firmaPub, ranura = permiso.ranura, permitidas = permiso.permitidas,
        ).aDto()
    }

    /** Anota el `deviceId` de una secundaria si es válido (formato del contrato). */
    private suspend fun anotarDevice(empleadoId: Long, device: String?) {
        val limpio = device?.trim().orEmpty()
        if (limpio.isEmpty() || !cu.shalev.core.validation.Validators.deviceId(limpio)) return
        if (db.syncDao().empleado(empleadoId)?.device == limpio) return
        db.syncDao().cambiarDevice(empleadoId, limpio)
    }

    /**
     * 0.21.0 (C6): solicitud de cierre del empleado. Devuelve true si hay un rechazo que avisarle (y lo da por avisado).
     * Nada se cierra aquí: solo el dueño lo aprueba (y entonces va por [cierrePendiente]).
     */
    private suspend fun solicitudCierre(e: EmpleadoEntity, solicita: Boolean): Boolean {
        val dao = db.syncDao()
        if (e.cierrePedidoPorEmpleadoEn == PrincipalRepositoryImpl.RECHAZO_PENDIENTE) {
            dao.marcarSolicitudCierre(e.id, null)
            return true
        }
        if (solicita && e.cierreSolicitadoEn == null && db.turnoDao().activoDeEmpleado(e.id) != null) {
            dao.registrarSolicitudCierre(e.id, clock.now().toEpochMilli())
        } else if (!solicita && e.cierrePedidoPorEmpleadoEn != null) {
            dao.marcarSolicitudCierre(e.id, null) // la secundaria ya no la mantiene (p. ej. su turno se cerró)
        }
        return false
    }

    /**
     * 0.20.0 (H5): ¿hay que pedirle a esta app que cierre el turno? Se mira DESPUÉS de aplicar el lote: si ya llegó su
     * turno cerrado (o no tiene ninguno abierto), la petición está cumplida y se borra.
     */
    private suspend fun cierrePendiente(e: EmpleadoEntity): Boolean {
        if (e.cierreSolicitadoEn == null) return false
        if (db.turnoDao().activoDeEmpleado(e.id) != null) return true
        db.syncDao().olvidarCierre(e.id)
        return false
    }

    private suspend fun nombreNegocio(): String = UsuarioActual.nombreDe(perfil.perfil.first())

    companion object {
        const val PUERTO = 47_811
        /** La secundaria hace ping cada 30 s; sin noticias en 90 s se da la conexión por perdida. */
        const val LECTURA_MS = 90_000
        /** S-04: conexiones simultáneas máximas (una por secundaria real basta; el resto es ruido o abuso). */
        const val MAX_CONEXIONES = 24
    }
}
