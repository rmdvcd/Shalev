package cu.shalev.data.local

import cu.shalev.domain.model.EstadoApp
import cu.shalev.domain.model.InfoActualizacion
import cu.shalev.domain.model.LicenciaRecuperable
import cu.shalev.domain.repository.EstadoAppRepository
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

/**
 * 0.25.0: estado propio de la app (recordatorio de respaldo, versiones de GitHub, licencia recuperable) en el DataStore
 * cifrado, como un único JSON bajo [CLAVE]. No va en el respaldo. Un valor ilegible equivale a «sin estado».
 */
@Singleton
class EstadoAppRepositoryImpl @Inject constructor(private val store: SecureDataStore) : EstadoAppRepository {

    private val escritura = Mutex()

    override val estado: Flow<EstadoApp> = store.observe(CLAVE).map(::leer).distinctUntilChanged()

    override suspend fun actual(): EstadoApp = estado.first()

    override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) = escritura.withLock {
        val nuevo = cambio(leer(store.get(CLAVE)))
        store.put(CLAVE, ShalevJson.encodeToString(EstadoDto.serializer(), EstadoDto.de(nuevo)))
    }

    override suspend fun borrar() = escritura.withLock { store.put(CLAVE, null) }

    private fun leer(json: String?): EstadoApp =
        json?.let { runCatching { ShalevJson.decodeFromString(EstadoDto.serializer(), it).aDominio() }.getOrNull() } ?: EstadoApp()

    @Serializable
    internal data class ActualizacionDto(
        val version: String, val pagina: String, val apkUrl: String? = null, val sha256: String? = null,
        val bytes: Long = 0, val notas: String = "",
    )

    @Serializable
    internal data class RecuperableDto(val id: String, val tipo: String, val secundarias: Int, val venceEn: Long? = null, val ci: String = "")

    @Serializable
    internal data class EstadoDto(
        val ultimoRespaldo: Long? = null,
        val cuentaRespaldoDesde: Long? = null,
        val ultimaComprobacion: Long? = null,
        val disponible: ActualizacionDto? = null,
        val obligatoriaDesde: Long? = null,
        val obligatoriaPara: String? = null,
        val aplazadaHasta: Long? = null,
        val licenciaRecuperable: RecuperableDto? = null,
    ) {
        fun aDominio() = EstadoApp(
            ultimoRespaldo = ultimoRespaldo?.let(Instant::ofEpochMilli),
            cuentaRespaldoDesde = cuentaRespaldoDesde?.let(Instant::ofEpochMilli),
            ultimaComprobacion = ultimaComprobacion?.let(Instant::ofEpochMilli),
            disponible = disponible?.let { InfoActualizacion(it.version, it.pagina, it.apkUrl, it.sha256, it.bytes, it.notas) },
            obligatoriaDesde = obligatoriaDesde?.let(Instant::ofEpochMilli),
            obligatoriaPara = obligatoriaPara,
            aplazadaHasta = aplazadaHasta?.let(Instant::ofEpochMilli),
            licenciaRecuperable = licenciaRecuperable?.let {
                LicenciaRecuperable(it.id, it.tipo, it.secundarias, it.venceEn?.let(Instant::ofEpochMilli), it.ci)
            },
        )

        companion object {
            fun de(e: EstadoApp) = EstadoDto(
                ultimoRespaldo = e.ultimoRespaldo?.toEpochMilli(),
                cuentaRespaldoDesde = e.cuentaRespaldoDesde?.toEpochMilli(),
                ultimaComprobacion = e.ultimaComprobacion?.toEpochMilli(),
                disponible = e.disponible?.let { ActualizacionDto(it.version, it.pagina, it.apkUrl, it.sha256, it.bytes, it.notas) },
                obligatoriaDesde = e.obligatoriaDesde?.toEpochMilli(),
                obligatoriaPara = e.obligatoriaPara,
                aplazadaHasta = e.aplazadaHasta?.toEpochMilli(),
                licenciaRecuperable = e.licenciaRecuperable?.let {
                    RecuperableDto(it.id, it.tipo, it.secundarias, it.venceEn?.toEpochMilli(), it.ci)
                },
            )
        }
    }

    companion object {
        const val CLAVE = "app.estado"
    }
}
