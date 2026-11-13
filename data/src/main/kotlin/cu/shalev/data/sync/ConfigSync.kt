package cu.shalev.data.sync

import cu.shalev.core.util.aHex
import cu.shalev.data.local.SecureDataStore
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.TipoApp
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** Lo que una app secundaria guarda de su vinculación (DataStore cifrado con el Keystore; excluido de backups). */
@Serializable
data class DatosSecundaria(
    val negocioId: String,
    val nombreNegocio: String,
    val empleadoId: Long,
    val nombreEmpleado: String,
    /** Clave del empleado (Base64). Solo vive aquí y en la fila del empleado en la principal. */
    val clave: String,
    val direcciones: List<String>,
    val puerto: Int,
    val permisos: List<String> = emptyList(),
    val ultimaSincronizacion: Long? = null,
    val licencia: LicenciaDto? = null,
    /** Hash del último catálogo aplicado (la principal no lo reenvía si no cambió). */
    val hash: String? = null,
    /** 0.20.0 (H5): el dueño pidió cerrar el turno; se cierra en cuanto no haya una venta en curso ([CierreRemoto]). */
    val cierrePendiente: Boolean = false,
    /** 0.20.0 (H5): el último turno lo cerró esa petición; aviso en Inicio hasta que el empleado lo cierre. */
    val avisoCierre: Boolean = false,
    /** 0.21.0 (C2): teléfono del empleado (lo escribió al vincularse). Se envía cifrado en cada sincronización. */
    val telefono: String? = null,
    /** 0.21.0 (C6): el empleado solicitó cerrar su turno; se envía en cada sincronización hasta que se resuelva. */
    val cierreSolicitado: Boolean = false,
    /** 0.21.0 (C6): el dueño rechazó la solicitud (aviso en Inicio hasta que el empleado lo cierre). */
    val avisoRechazo: Boolean = false,
    val localId: String? = null,
    /** Antes de enviar, se persiste cifrado; se conserva hasta obtener un recibo definitivo. */
    val comandoPendiente: ComandoPendiente? = null,
) {
    val permisosEmpleado: Set<PermisoEmpleado> get() = PermisoEmpleado.deNombres(permisos)
    val licenciaPrincipal: LicenciaPrincipal? get() = licencia?.aDominio()

    override fun toString(): String = "DatosSecundaria(empleado=$empleadoId)" // nunca la clave en logs
}

@Serializable
data class ComandoPendiente(val clave: String, val accion: Accion, val localId: String? = null)

fun LicenciaDto.aDominio(): LicenciaPrincipal? {
    val c = LicenciaPrincipal.Clase.entries.firstOrNull { it.name == clase } ?: return null
    return LicenciaPrincipal(
        c, TipoLicencia.entries.firstOrNull { it.name == tipo }, venceEn?.let(Instant::ofEpochMilli),
        // 0.31.0 (mitigación 7): el permiso firmado viaja con la licencia (null en una principal anterior).
        subToken = subToken, firmaPub = firmaPub, ranura = ranura, permitidas = permitidas,
    )
}

fun LicenciaPrincipal.aDto(): LicenciaDto = LicenciaDto(
    clase.name, tipo?.name, venceEn?.toEpochMilli(), subToken = subToken, firmaPub = firmaPub, ranura = ranura, permitidas = permitidas,
)

/** P37: configuración de vinculación de ESTE teléfono. */
@Singleton
class ConfigSync @Inject constructor(private val ds: SecureDataStore) {
    private val mutex = Mutex()

    val tipo: Flow<TipoApp> = ds.observe(K_TIPO).map { v -> TipoApp.entries.firstOrNull { it.name == v } ?: TipoApp.PRINCIPAL }.distinctUntilChanged()

    val secundaria: Flow<DatosSecundaria?> = ds.observe(K_SECUNDARIA).map { raw ->
        raw?.let { runCatching { SyncJson.decodeFromString(DatosSecundaria.serializer(), it) }.getOrNull() }
    }.distinctUntilChanged()

    suspend fun secundariaActual(): DatosSecundaria? = secundaria.first()

    /** Id público del negocio (lo crea la principal la primera vez). No identifica al dispositivo ni a la persona. */
    suspend fun negocioId(): String = mutex.withLock {
        ds.get(K_NEGOCIO) ?: CriptoSync.aleatorio(12).aHex().also { ds.put(K_NEGOCIO, it) }
    }

    /** Pasa a SECUNDARIA con los datos de la vinculación recién hecha. */
    suspend fun hacerSecundaria(d: DatosSecundaria) = mutex.withLock {
        ds.put(K_SECUNDARIA, SyncJson.encodeToString(DatosSecundaria.serializer(), d))
        ds.put(K_TIPO, TipoApp.SECUNDARIA.name)
    }

    suspend fun editarSecundaria(f: (DatosSecundaria) -> DatosSecundaria) = mutex.withLock {
        val actual = secundaria.first() ?: return@withLock
        ds.put(K_SECUNDARIA, SyncJson.encodeToString(DatosSecundaria.serializer(), f(actual)))
    }

    /** Vuelve a PRINCIPAL y olvida la vinculación (la clave se borra). */
    suspend fun hacerPrincipal() = mutex.withLock {
        ds.put(K_TIPO, TipoApp.PRINCIPAL.name)
        ds.put(K_SECUNDARIA, null)
    }

    private companion object {
        const val K_TIPO = "sync.tipo"
        const val K_SECUNDARIA = "sync.secundaria.v1"
        const val K_NEGOCIO = "sync.negocio"
    }
}
