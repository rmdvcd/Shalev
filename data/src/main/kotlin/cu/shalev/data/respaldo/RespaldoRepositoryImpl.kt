package cu.shalev.data.respaldo

import cu.shalev.data.db.entity.ServicioInsumoEntity
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.PerfilEntity
import cu.shalev.data.db.tx
import cu.shalev.data.db.dao.localActivo
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.data.dto.EmpleadoRespaldoDto
import cu.shalev.data.dto.RespaldoDto
import cu.shalev.data.dto.LicenciaRespaldoDto
import cu.shalev.data.dto.MovimientoCajaDto
import cu.shalev.data.db.entity.MovimientoCajaEntity
import cu.shalev.domain.model.LicenciaRecuperable
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.licencia.LicenseState
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.Vinculacion
import cu.shalev.data.dto.toDomain
import cu.shalev.data.dto.toDto
import cu.shalev.data.io.leerAcotado
import cu.shalev.data.local.ShalevJson
import cu.shalev.data.mapper.perfilDe
import cu.shalev.data.mapper.productosEntities
import cu.shalev.data.mapper.serviciosEntities
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.mapper.toEntities
import cu.shalev.data.mapper.toEntity
import cu.shalev.data.mapper.toReceta
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.repository.RespaldoRepository
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Respaldo portable y siempre completo (P17: un solo tipo): base de datos + preferencias.
 * NO incluye la licencia ni claves (atadas al dispositivo, C5). 0.25.0 (v4): sí lleva los datos públicos de la
 * licencia (ID, tipo, secundarias, vencimiento y carné) para pedir su recuperación en el teléfono nuevo. El DataStore de preferencias se restaura después de la transacción
 * de Room (dos almacenes distintos); si fallara, los datos de Room ya están íntegros y solo quedarían las
 * preferencias anteriores.
 */
@Singleton
class RespaldoRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
    /** 0.25.0: bloque de licencia del respaldo v4. */
    private val licencias: LicenciaRepository,
    /** 0.25.0: recordatorio mensual (último respaldo exportado) y licencia recuperable tras importar. */
    private val estadoApp: EstadoAppRepository,
) : RespaldoRepository {

    private val cipher = BackupCipher()

    /** Contenido de dominio de un respaldo (independiente de Room y del JSON). */
    private data class Contenido(
        val productos: List<Producto> = emptyList(),
        val recetas: List<Receta> = emptyList(),
        val insumos: List<Insumo> = emptyList(),
        val turnos: List<Turno> = emptyList(),
        val ventas: List<Venta> = emptyList(),
        val movimientos: List<MovimientoInventario> = emptyList(),
        val perfil: Perfil? = null,
        val preajustes: List<PreajustePrecios> = emptyList(),
        val servicios: List<Pair<Servicio, List<RecetaLinea>>> = emptyList(),
        /** 0.19.0: marcas de sincronización por id de turno / de venta (no están en el modelo de dominio). */
        val marcasTurno: Map<Long, MarcaSync> = emptyMap(),
        val marcasVenta: Map<Long, MarcaSync> = emptyMap(),
        /** 0.20.0 (H7): apps secundarias, sin claves. */
        val empleados: List<EmpleadoRespaldoDto> = emptyList(),
        /** 0.25.0: entradas/salidas de efectivo, con sus marcas de sincronización. */
        val caja: List<MovimientoCajaDto> = emptyList(),
        /** 0.27.0 (N2): clientes fijos. */
        val clientesFijos: List<cu.shalev.data.dto.ClienteFijoDto> = emptyList(),
        val almacenesLocales: List<cu.shalev.data.dto.AlmacenLocalDto> = emptyList(),
        /** v16: local de cada fila (vacío en respaldos anteriores → el local activo al restaurar). */
        val locales: LocalFilas = LocalFilas(),
    )

    /** v16: nombre del local por id de fila. */
    private data class LocalFilas(
        val productos: Map<Long, String> = emptyMap(),
        val insumos: Map<Long, String> = emptyMap(),
        val servicios: Map<Long, String> = emptyMap(),
        val turnos: Map<Long, String> = emptyMap(),
        val ventas: Map<Long, String> = emptyMap(),
        val movimientos: Map<Long, String> = emptyMap(),
    )

    /** uuid + app secundaria de origen + enviado. Se conservan para que restaurar no duplique al sincronizar. */
    private data class MarcaSync(val uuid: String?, val empleadoId: Long?, val sincronizado: Boolean) {
        val vacia get() = uuid == null && empleadoId == null && !sincronizado
    }

    override suspend fun exportar(
        contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit,
    ): AppResult<ResumenRespaldo> {
        progreso(EtapaRespaldo.PREPARANDO)
        val c = when (val r = db.tx { leer() }) {
            is AppResult.Ok -> r.value
            is AppResult.Err -> return r
        }
        return withContext(io) {
            guard {
                val dto = RespaldoDto(
                    creadoEn = clock.now().toEpochMilli(),
                    productos = c.productos.map { it.toDto().copy(local = c.locales.productos[it.id].orEmpty()) },
                    recetas = c.recetas.map { it.toDto() },
                    insumos = c.insumos.map { it.toDto().copy(local = c.locales.insumos[it.id].orEmpty()) },
                    turnos = c.turnos.map { t ->
                        val d = t.toDto().copy(local = c.locales.turnos[t.id].orEmpty())
                        c.marcasTurno[t.id]?.let { m -> d.copy(uuid = m.uuid, sincronizado = m.sincronizado) } ?: d
                    },
                    ventas = c.ventas.map { v ->
                        val d = v.toDto().copy(local = c.locales.ventas[v.id].orEmpty())
                        c.marcasVenta[v.id]?.let { m -> d.copy(uuid = m.uuid, empleadoId = m.empleadoId, sincronizado = m.sincronizado) } ?: d
                    },
                    movimientos = c.movimientos.map { it.toDto().copy(local = c.locales.movimientos[it.id].orEmpty()) },
                    perfil = c.perfil?.toDto(), preajustes = c.preajustes.map { it.toDto() },
                    preferencias = preferencias.preferencias.first().toDto(),
                    servicios = c.servicios.map { (s, l) ->
                        s.toDto(l).copy(local = c.locales.servicios[s.id].orEmpty())
                    },
                    empleados = c.empleados,
                    caja = c.caja,
                    clientesFijos = c.clientesFijos,
                    almacenesLocales = c.almacenesLocales,
                    licencia = bloqueLicencia(c.perfil?.ci.orEmpty()),
                )
                val plano = ShalevJson.encodeToString(RespaldoDto.serializer(), dto).toByteArray(Charsets.UTF_8)
                try {
                    progreso(EtapaRespaldo.CIFRANDO)
                    val cifrado = cipher.cifrar(plano, contrasena, dto.creadoEn)
                    progreso(EtapaRespaldo.ESCRIBIENDO)
                    destino.write(cifrado)
                    destino.flush()
                } finally {
                    plano.fill(0)
                }
                resumen(c)
            }
        }.also { r ->
            // 0.25.0 (§4): solo un respaldo EXPORTADO bien reinicia el recordatorio mensual.
            if (r is AppResult.Ok) runCatching { estadoApp.editar { it.copy(ultimoRespaldo = clock.now()) } }
        }
    }

    /** 0.25.0: datos públicos de la licencia instalada (null si no hay licencia auténtica: prueba). */
    private fun bloqueLicencia(ci: String): LicenciaRespaldoDto? = licencias.snapshot.value?.instalada?.let { l ->
        LicenciaRespaldoDto(id = l.id, tipo = l.tipo.name, secundarias = l.secundarias, venceEn = l.venceEn?.toEpochMilli(), ci = ci)
    }

    override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = withContext(io) {
        guard {
            val datos = leerAcotado(origen)
            val cab = cipher.inspeccionar(datos)
            InfoRespaldo(
                version = cab.version,
                creadoEn = cab.creadoEnMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                bytes = datos.size.toLong(),
                conContrasena = cab.conContrasena,
            )
        }
    }

    override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
        var recuperable: LicenciaRespaldoDto? = null
        val parsed: AppResult<Pair<Contenido, Preferencias?>> = withContext(io) {
            guard {
                progreso(EtapaRespaldo.LEYENDO)
                val datos = leerAcotado(origen)
                progreso(EtapaRespaldo.VERIFICANDO)
                cipher.inspeccionar(datos)
                progreso(EtapaRespaldo.DESCIFRANDO)
                val plano = cipher.descifrar(datos, contrasena)
                val dto = try {
                    ShalevJson.decodeFromString(RespaldoDto.serializer(), String(plano, Charsets.UTF_8))
                } finally {
                    plano.fill(0)
                }
                if (dto.formato != RespaldoDto.FORMATO) throw BackupCipher.FormatoException("no es un respaldo Shalev")
                if (dto.version > RespaldoDto.VERSION) throw BackupCipher.FormatoException("respaldo de una versión más nueva de Shalev")
                if (dto.version < RespaldoDto.VERSION_MINIMA) throw BackupCipher.VersionAnteriorException(dto.version)
                val contenido = Contenido(
                    productos = dto.productos.map { it.toDomain() }, recetas = dto.recetas.map { it.toDomain() },
                    insumos = dto.insumos.map { it.toDomain() }, turnos = dto.turnos.map { it.toDomain() },
                    ventas = dto.ventas.map { it.toDomain() }, movimientos = dto.movimientos.map { it.toDomain() },
                    perfil = dto.perfil?.toDomain(), preajustes = dto.preajustes.map { it.toDomain() },
                    servicios = dto.servicios.map { it.toDomain() },
                    marcasTurno = dto.turnos.associate { it.id to MarcaSync(it.uuid, it.empleadoId, it.sincronizado) }.filterValues { !it.vacia },
                    marcasVenta = dto.ventas.associate { it.id to MarcaSync(it.uuid, it.empleadoId, it.sincronizado) }.filterValues { !it.vacia },
                    empleados = dto.empleados,
                    caja = dto.caja,
                    clientesFijos = dto.clientesFijos.distinctBy { it.ci },
                    almacenesLocales = dto.almacenesLocales.map {
                        it.copy(nombre = cu.shalev.domain.repository.NombresLocal.validar(it.nombre))
                    }.also { xs ->
                        require(xs.count { it.inventario } <= 1 && xs.count { it.servicios } <= 1)
                        require(xs.map { it.nombre.lowercase(java.util.Locale.ROOT) }.distinct().size == xs.size)
                    },
                    locales = LocalFilas(
                        productos = dto.productos.associate { it.id to it.local },
                        insumos = dto.insumos.associate { it.id to it.local },
                        servicios = dto.servicios.associate { it.id to it.local },
                        turnos = dto.turnos.associate { it.id to it.local },
                        ventas = dto.ventas.associate { it.id to it.local },
                        movimientos = dto.movimientos.associate { it.id to it.local },
                    ),
                )
                validarIntegridad(contenido)
                recuperable = dto.licencia
                contenido to dto.preferencias?.toDomain()
            }
        }
        val (c, prefs) = when (parsed) {
            is AppResult.Ok -> parsed.value
            is AppResult.Err -> return parsed
        }
        progreso(EtapaRespaldo.IMPORTANDO)
        return when (val r = db.tx { escribir(c) }) {
            is AppResult.Err -> r
            is AppResult.Ok -> {
                prefs?.let { preferencias.reemplazar(it) }
                recordarLicencia(recuperable)
                AppResult.Ok(resumen(c))
            }
        }
    }

    // ---------------------------------------------------------------- lectura / escritura (en transacción)

    private suspend fun leer(): Contenido {
        val perfilDao = db.perfilDao()
        val ventaDao = db.ventaDao()
        val detalles = ventaDao.todosDetalles().groupBy { it.ventaId }
        val transacciones = ventaDao.todasTransacciones().associateBy { it.ventaId }
        val turnoEntities = db.turnoDao().todos()
        val ventaEntities = ventaDao.todas()
        val productoEntities = db.productoDao().todos()
        val insumoEntities = db.insumoDao().todos()
        val servicioEntities = db.servicioDao().todos()
        val movimientoEntities = db.movimientoDao().todos()
        return Contenido(
            productos = productoEntities.map { it.toDomain() },
            recetas = db.recetaDao().todas().groupBy { it.productoId }.map { (id, l) -> l.toReceta(id) },
            insumos = insumoEntities.map { it.toDomain() },
            turnos = turnoEntities.map { it.toDomain() },
            marcasTurno = turnoEntities.associate { it.id to MarcaSync(it.uuid, it.empleadoId, it.sincronizado) }.filterValues { !it.vacia },
            marcasVenta = ventaEntities.associate { it.id to MarcaSync(it.uuid, it.empleadoId, it.sincronizado) }.filterValues { !it.vacia },
            ventas = ventaEntities.map { v ->
                cu.shalev.data.db.entity.VentaCompleta(v, detalles[v.id].orEmpty(), transacciones[v.id]).toDomain()
            },
            movimientos = movimientoEntities.map { it.toDomain() },
            locales = LocalFilas(
                productos = productoEntities.associate { it.id to it.local },
                insumos = insumoEntities.associate { it.id to it.local },
                servicios = servicioEntities.associate { it.id to it.local },
                turnos = turnoEntities.associate { it.id to it.local },
                ventas = ventaEntities.associate { it.id to it.local },
                movimientos = movimientoEntities.associate { it.id to it.local },
            ),
            perfil = perfilDe(perfilDao.obtener(), perfilDao.tarjetas(), perfilDao.telefonos()),
            preajustes = db.preciosDao().todos().map { it.toDomain() },
            servicios = db.servicioDao().let { dao ->
                val lineas = dao.todosInsumos().groupBy({ it.servicioId }, { it.toDomain() })
                servicioEntities.map { it.toDomain() to lineas[it.id].orEmpty() }
            },
            caja = db.cajaDao().todos().map { m ->
                MovimientoCajaDto(m.id, m.turnoId, m.fecha, m.tipo, m.importeCent, m.motivo, m.hechoPor, m.uuid, m.sincronizado)
            },
            almacenesLocales = db.almacenLocalDao().todos().map {
                cu.shalev.data.dto.AlmacenLocalDto(it.nombre, it.inventario, it.servicios)
            },
            clientesFijos = db.clienteFijoDao().todos().map { e ->
                cu.shalev.data.dto.ClienteFijoDto(e.nombreApellidos, e.ci, e.telefono, e.creadoEn, e.actualizadoEn)
            },
            empleados = db.syncDao().empleados().map { e ->
                EmpleadoRespaldoDto(
                    id = e.id, nombre = e.nombre, permisos = e.permisos.split(',').filter { it.isNotBlank() },
                    creadoEn = e.creadoEn, tarjetaId = e.tarjetaId, telefonoId = e.telefonoId,
                )
            },
        )
    }

    private suspend fun escribir(c: Contenido) {
        val m = db.mantenimientoDao()
        m.borrarOperacion()
        m.borrarConfiguracion()
        // v16: los locales primero; su nombre identifica el catálogo y el historial que se restaura.
        c.almacenesLocales.forEach {
            db.almacenLocalDao().insertar(cu.shalev.data.db.entity.AlmacenLocalEntity(it.nombre, it.inventario, it.servicios))
        }
        val activo = db.almacenLocalDao().localActivo()
        val validos = c.almacenesLocales.map { it.nombre }.toSet()
        fun localDe(nombre: String) = nombre.takeIf { it.isNotEmpty() && it in validos } ?: activo

        db.productoDao().insertarTodos(c.productos.map { it.toEntity().copy(local = localDe(c.locales.productos[it.id].orEmpty())) })
        db.insumoDao().insertarTodos(c.insumos.map { it.toEntity().copy(local = localDe(c.locales.insumos[it.id].orEmpty())) })
        db.recetaDao().insertarTodas(c.recetas.flatMap { it.toEntities() })
        db.servicioDao().insertarTodos(c.servicios.map { it.first.toEntity().copy(local = localDe(c.locales.servicios[it.first.id].orEmpty())) })
        db.servicioDao().insertarInsumos(c.servicios.flatMap { (s, l) -> l.map { ServicioInsumoEntity(s.id, it.insumoId, it.cantidad.milesimas) } })
        db.turnoDao().insertarTodos(c.turnos.map { t ->
            val e = t.toEntity().copy(local = localDe(c.locales.turnos[t.id].orEmpty()))
            c.marcasTurno[t.id]?.let { m -> e.copy(uuid = m.uuid, sincronizado = m.sincronizado) } ?: e
        })
        val ventaDao = db.ventaDao()
        val localesVenta = c.ventas.associate { it.id to localDe(c.locales.ventas[it.id].orEmpty()) }
        ventaDao.insertarVentas(c.ventas.map { v ->
            val e = v.toEntity().copy(local = localesVenta.getValue(v.id))
            c.marcasVenta[v.id]?.let { m -> e.copy(uuid = m.uuid, empleadoId = m.empleadoId, sincronizado = m.sincronizado) } ?: e
        })
        ventaDao.insertarDetalles(c.ventas.flatMap { v -> v.detalles.map { it.toEntity(v.id) } })
        ventaDao.insertarTransacciones(c.ventas.mapNotNull { v ->
            v.transaccion?.toEntity(v.id)?.copy(local = localesVenta.getValue(v.id))
        })
        db.movimientoDao().insertarTodos(c.movimientos.map {
            it.toEntity().copy(local = localDe(c.locales.movimientos[it.id].orEmpty()))
        })
        db.cajaDao().insertarTodos(c.caja.map { d ->
            MovimientoCajaEntity(d.id, d.turnoId, d.fecha, d.tipo, d.importeCent, d.motivo, d.hechoPor, d.uuid, d.sincronizado)
        })

        c.perfil?.let { p ->
            val dao = db.perfilDao()
            p.tarjetas.forEach { dao.insertarTarjeta(it.toEntity()) }
            p.telefonos.forEach { dao.insertarTelefono(it.toEntity()) }
            dao.guardar(PerfilEntity(nombre = p.nombre, apellidos = p.apellidos, ci = p.ci,
                pagoTarjetaId = p.pagoTarjetaId, pagoTelefonoId = p.pagoTelefonoId))
        }
        db.clienteFijoDao().insertarTodos(c.clientesFijos.map { d ->
            cu.shalev.data.db.entity.ClienteFijoEntity(
                nombreApellidos = d.nombreApellidos, ci = d.ci, telefono = d.telefono, creadoEn = d.creadoEn, actualizadoEn = d.actualizadoEn,
            )
        })
        restaurarEmpleados(c.empleados, c.perfil)
        // Un preajuste puede referirse a productos eliminados (borrado lógico): solo se enlazan los vigentes.
        val existentes = c.productos.filterNot { it.eliminado }.map { it.id }.toSet()
        val serviciosExistentes = c.servicios.filterNot { it.first.eliminado }.map { it.first.id }.toSet()
        val precios = db.preciosDao()
        c.preajustes.forEach { pa ->
            precios.guardar(pa.toEntity())
            precios.insertarProductos(pa.copy(productoIds = pa.productoIds intersect existentes).productosEntities())
            precios.insertarServicios(pa.copy(servicioIds = pa.servicioIds intersect serviciosExistentes).serviciosEntities())
        }
    }

    /**
     * 0.20.0 (H7): las fichas de empleado no se borran al restaurar (la tabla `empleado` sobrevive, con sus claves).
     * - Las del respaldo que no existan aquí (ni por id ni por nombre) se crean PENDIENTES de vincular: sin clave,
     *   hay que mostrarles un QR nuevo. Se conserva el id si está libre (sus turnos y ventas siguen siendo suyos).
     * - Nunca más del tope absoluto (10). 0.21.0 (C4): el límite de la licencia se aplica al vincular ([nuevoCodigo]).
     * - La tarjeta o el teléfono de cobro que ya no exista en el Perfil restaurado vuelve a «el predeterminado».
     */
    private suspend fun restaurarEmpleados(lista: List<EmpleadoRespaldoDto>, perfil: Perfil?) {
        val sync = db.syncDao()
        val tarjetas = perfil?.tarjetas?.map { it.id }?.toSet().orEmpty()
        val telefonos = perfil?.telefonos?.map { it.id }?.toSet().orEmpty()
        val actuales = sync.empleados().toMutableList()
        for (e in lista) {
            val nombre = e.nombre.trim()
            if (nombre.isEmpty() || nombre.length > Vinculacion.MAX_NOMBRE) continue
            if (!Vinculacion.puedeAgregar(actuales.size, cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX)) break
            if (actuales.any { it.id == e.id || it.nombre.equals(nombre, ignoreCase = true) }) continue
            val nuevo = EmpleadoEntity(
                id = if (sync.empleado(e.id) == null && e.id > 0) e.id else 0,
                nombre = nombre, permisos = PermisoEmpleado.deNombres(e.permisos).map { it.name }.sorted().joinToString(","),
                creadoEn = e.creadoEn, vinculadoEn = null, ultimaSincronizacion = null, clave = null, codigoToken = null,
                codigoVence = null, activo = true,
                tarjetaId = e.tarjetaId?.takeIf { it in tarjetas }, telefonoId = e.telefonoId?.takeIf { it in telefonos },
            )
            actuales += nuevo.copy(id = sync.insertarEmpleado(nuevo))
        }
        for (e in sync.empleados()) {
            val t = e.tarjetaId?.takeIf { it in tarjetas }
            val f = e.telefonoId?.takeIf { it in telefonos }
            if (t != e.tarjetaId || f != e.telefonoId) sync.cambiarCobro(e.id, t, f)
        }
    }

    /** Rechaza respaldos incoherentes ANTES de borrar nada (las FK fallarían a mitad de la restauración). */
    private fun validarIntegridad(c: Contenido) {
        fun check(ok: Boolean, que: String) { if (!ok) throw BackupCipher.FormatoException("referencias rotas: $que") }
        c.perfil?.let { p ->
            check(p.tarjetas.map { it.id }.let { it.all { id -> id > 0 } && it.distinct().size == it.size }, "tarjetas")
            check(p.telefonos.map { it.id }.let { it.all { id -> id > 0 } && it.distinct().size == it.size }, "teléfonos")
            check(p.tarjetas.map { it.numero }.distinct().size == p.tarjetas.size, "tarjetas repetidas")
            check(p.telefonos.map { it.numero }.distinct().size == p.telefonos.size, "teléfonos repetidos")
        }
        val productos = c.productos.map { it.id }.toSet()
        val insumos = c.insumos.map { it.id }.toSet()
        val turnos = c.turnos.map { it.id }.toSet()
        check(productos.size == c.productos.size && insumos.size == c.insumos.size && turnos.size == c.turnos.size, "ids repetidos")
        check(c.recetas.all { r -> r.productoId in productos && r.lineas.all { it.insumoId in insumos } }, "recetas")
        check(c.ventas.all { it.turnoId in turnos }, "ventas")
        check(c.turnos.all { t -> t.conteoCierre?.let { it.valido() && t.contado == it.efectivo } ?: true }, "conteos de cierre")
        check(c.caja.all { it.turnoId in turnos && it.importeCent > 0 }, "caja")
        check(c.caja.map { it.id }.distinct().size == c.caja.size, "caja repetida")
        check(c.caja.mapNotNull { it.uuid }.let { it.distinct().size == it.size }, "ids globales de caja repetidos")
        val servicios = c.servicios.map { it.first.id }.toSet()
        check(servicios.size == c.servicios.size, "servicios repetidos")
        check(c.servicios.all { (_, l) -> l.all { it.insumoId in insumos } }, "insumos de servicios")
        check(c.ventas.map { it.id }.distinct().size == c.ventas.size, "ventas repetidas")
        // Uno abierto como máximo POR APP: la principal y cada secundaria (0.19.0) tienen su propio turno.
        check(c.turnos.groupBy { it.empleadoId }.values.all { l -> l.count { it.abierto } <= 1 }, "más de un turno abierto")
        for (marcas in listOf(c.marcasTurno, c.marcasVenta)) {
            val uuids = marcas.values.mapNotNull { it.uuid }
            check(uuids.distinct().size == uuids.size, "ids globales repetidos")
        }
    }

    /**
     * 0.25.0 (§3): si este teléfono no tiene una licencia válida y el respaldo traía una, se guarda para ofrecer
     * «Recuperar licencia» con el ID y el carné ya escritos. Con licencia válida no se toca nada.
     */
    private suspend fun recordarLicencia(l: LicenciaRespaldoDto?) {
        if (l == null) return
        val estado = licencias.snapshot.value?.estado
        if (estado is LicenseState.Active || estado is LicenseState.Perpetual) return
        val r = LicenciaRecuperable(l.id, l.tipo, l.secundarias, l.venceEn?.let(Instant::ofEpochMilli), l.ci)
        runCatching { estadoApp.editar { it.copy(licenciaRecuperable = r) } }
    }

    private fun resumen(c: Contenido) =
        ResumenRespaldo(c.productos.count { !it.eliminado }, c.insumos.size, c.ventas.size, c.movimientos.size)

    private fun leerAcotado(input: InputStream): ByteArray =
        input.leerAcotado(BackupCipher.MAX_PLANO) { BackupCipher.FormatoException("archivo demasiado grande") }

    private inline fun <T> guard(block: () -> T): AppResult<T> = try {
        AppResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackupCipher.ContrasenaIncorrectaException) {
        AppResult.Err(AppError.ContrasenaIncorrecta)
    } catch (e: BackupCipher.ArchivoDanadoException) {
        AppResult.Err(AppError.ArchivoDanado(e.incompleto))
    } catch (e: BackupCipher.FormatoException) {
        // Sus mensajes son textos controlados de esta casa («respaldo de una versión más nueva de Shalev (5)»);
        // la capa de pantallas los clasifica para componer el aviso final en lenguaje llano.
        AppResult.Err(AppError.FormatoInvalido(e.message.orEmpty()))
    } catch (e: SerializationException) {
        AppResult.Err(AppError.FormatoInvalido("contenido ilegible"))
    } catch (e: IllegalArgumentException) {
        // NUNCA e.message: enumValueOf/require lo rellenan con nombres y reglas internas («valor desconocido: X»)
        // que para la persona serían texto incomprensible. Detalle fijo, como en SerializationException.
        AppResult.Err(AppError.FormatoInvalido("contenido ilegible"))
    } catch (e: java.time.DateTimeException) {
        AppResult.Err(AppError.FormatoInvalido("fecha inválida"))
    } catch (e: java.io.IOException) {
        AppResult.Err(AppError.Almacenamiento)
    } catch (e: OutOfMemoryError) {
        AppResult.Err(AppError.FormatoInvalido("respaldo demasiado grande"))
    } catch (e: java.security.GeneralSecurityException) {
        AppResult.Err(AppError.Cripto)
    }
}
