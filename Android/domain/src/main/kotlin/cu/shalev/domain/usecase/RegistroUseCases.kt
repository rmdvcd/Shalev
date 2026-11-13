package cu.shalev.domain.usecase

import cu.shalev.core.time.Dates
import cu.shalev.domain.model.Insumo
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.ExportadorDocumentos
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.RegistroRepository
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablaExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.validation.Validadores
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId
import javax.inject.Inject
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.service.RegistrosFiltro
import cu.shalev.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

// ---------------- Registros ----------------

/**
 * Una tabla de la pantalla Registros ya filtrada (más recientes primero). Fecha e importe van a la consulta
 * del repositorio; el texto se filtra con [RegistrosFiltro] (sin tildes ni mayúsculas). «Hoy» se calcula
 * con [clock] en la zona del teléfono al suscribirse.
 * Movimientos: se cruzan con los insumos (en vivo) solo para mostrar su unidad («-0.5 kg»).
 */
class ObservarRegistro @Inject constructor(
    private val registros: RegistroRepository,
    private val insumos: InsumoRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** 0.20.0 (H1): opciones del filtro «Vendedor» (la hoja lo muestra solo con más de un nombre). */
    fun vendedores(): Flow<List<String>> = registros.vendedores()

    /**
     * 0.30.0 (F1): el filtro de texto recorre todas las filas del período (hasta un año de registros) y esto se
     * ejecutaba en el hilo del colector (Main). [io] lo saca de ahí; la firma y el resultado no cambian.
     */
    operator fun invoke(tipo: TipoRegistro, filtro: FiltroRegistros, zona: ZoneId = ZoneId.systemDefault()): Flow<VistaRegistro> {
        val consulta = RegistrosFiltro.consulta(filtro, tipo, Dates.localDate(clock.now(), zona), zona)
        return when (tipo) {
            // P29: las ventas de servicios van en su propia pestaña.
            TipoRegistro.VENTAS -> registros.ventas(consulta).map { l ->
                VistaRegistro.Ventas(l.filter { !it.esServicio && RegistrosFiltro.deVendedor(it.vendedor, filtro) && RegistrosFiltro.coincide(it, filtro.texto) })
            }
            TipoRegistro.SERVICIOS -> registros.ventas(consulta).map { l ->
                VistaRegistro.Ventas(l.filter { it.esServicio && RegistrosFiltro.deVendedor(it.vendedor, filtro) && RegistrosFiltro.coincide(it, filtro.texto) })
            }
            TipoRegistro.TRANSACCIONES -> registros.transacciones(consulta).map { l ->
                VistaRegistro.Transferencias(l.filter { RegistrosFiltro.deVendedor(it.vendedor, filtro) && RegistrosFiltro.coincide(it, filtro.texto) })
            }
            TipoRegistro.MOVIMIENTOS -> combine(registros.movimientos(consulta), insumos.observarTodos()) { ms, xs ->
                val simbolos = xs.associate { it.id to it.unidad.simbolo }
                VistaRegistro.Movimientos(
                    ms.map { m -> ItemMovimiento(m, if (m.entidad == TipoEntidad.INSUMO) simbolos[m.entidadId].orEmpty() else "") }
                        .filter { RegistrosFiltro.coincide(it, filtro.texto) },
                )
            }
        }.flowOn(io)
    }
}

// ---------------- Exportar ----------------

enum class TipoRegistro { VENTAS, SERVICIOS, TRANSACCIONES, MOVIMIENTOS }

/**
 * Prompt 14 · Registros → Exportar: PDF o Excel de EXACTAMENTE lo que se ve (pestaña, búsqueda y filtro ya
 * aplicados por [ObservarRegistro]), sin el tope de filas del texto compartido.
 */
class ExportarTablas @Inject constructor(private val exportador: ExportadorDocumentos) {
    suspend operator fun invoke(tablas: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> {
        if (tablas.isEmpty() || tablas.all { it.filas.isEmpty() }) return AppResult.Err(AppError.Validacion("filas", AppError.Regla.REQUERIDO))
        return exportador.exportar(tablas, formato, destino)
    }
}

/** 0.25.1: el turno completo (resumen, arqueo, caja, ventas e inventario) en PDF o Excel. */
class ExportarTurno @Inject constructor(
    private val obtenerDetalle: ObtenerDetalleTurno,
    private val exportador: ExportadorDocumentos,
) {
    suspend operator fun invoke(turnoId: Long, formato: FormatoExport, destino: OutputStream, zona: java.time.ZoneId = java.time.ZoneId.systemDefault()): AppResult<Unit> =
        when (val d = obtenerDetalle(turnoId)) {
            is AppResult.Ok -> exportador.exportar(TablasExport.turno(d.value, zona), formato, destino)
            is AppResult.Err -> d
        }
}

/** Inventario → ver [ExportarInventario] en InventarioUseCases.kt (selección o vista filtrada). */
class ExportarInsumos @Inject constructor(
    private val insumos: InsumoRepository,
    private val exportador: ExportadorDocumentos,
) {
    suspend operator fun invoke(formato: FormatoExport, destino: OutputStream): AppResult<Unit> =
        exportador.exportar(listOf(TablasExport.insumos(insumos.todos())), formato, destino)

    /** Botón Exportar de Elaboración: la selección (o, sin selección, lo que se ve filtrado). */
    suspend operator fun invoke(seleccion: List<Insumo>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> {
        if (seleccion.isEmpty()) return AppResult.Err(AppError.Validacion("insumos", AppError.Regla.REQUERIDO))
        return exportador.exportar(listOf(TablasExport.insumos(seleccion)), formato, destino)
    }
}


// ---------------- Respaldo ----------------

/**
 * La contraseña se borra de memoria al terminar, pase lo que pase.
 * 0.27.0 (T10): vacía = respaldo sin contraseña (opcional); si se escribe, mínimo [Validadores.MIN_CONTRASENA].
 */
class ExportarRespaldo @Inject constructor(private val repo: RespaldoRepository) {
    suspend operator fun invoke(
        contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit = {},
    ): AppResult<ResumenRespaldo> {
        try {
            if (contrasena.isNotEmpty()) Validadores.contrasena(contrasena)?.let { return AppResult.Err(it) }
            return repo.exportar(contrasena, destino, progreso)
        } finally {
            contrasena.fill('\u0000')
        }
    }
}

/** 0.27.0 (T10): contraseña vacía solo vale para archivos sin contraseña (si la tienen: [AppError.ContrasenaIncorrecta]). */
class ImportarRespaldo @Inject constructor(private val repo: RespaldoRepository) {
    suspend operator fun invoke(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit = {}): AppResult<ResumenRespaldo> {
        try {
            return repo.importar(origen, contrasena, progreso)
        } finally {
            contrasena.fill('\u0000')
        }
    }
}

