package cu.shalev.data

import cu.shalev.domain.model.EstadoApp
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.LicenciaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 0.25.0: estado propio de la app en memoria (recordatorio de respaldo, licencia recuperable). */
class EstadoAppEnMemoria(inicial: EstadoApp = EstadoApp()) : EstadoAppRepository {
    val flujo = MutableStateFlow(inicial)
    override val estado: Flow<EstadoApp> = flujo
    override suspend fun actual(): EstadoApp = flujo.value
    override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { flujo.value = cambio(flujo.value) }
    override suspend fun borrar() { flujo.value = EstadoApp() }
}

/** 0.25.0: licencia fija para los tests de respaldo ([snapshot] null = sin licencia instalada). */
class LicenciaFija(inicial: Licencia? = null) : LicenciaRepository {
    override val snapshot: StateFlow<Licencia?> = MutableStateFlow(inicial)
    override suspend fun refrescar(): Licencia = snapshot.value ?: error("sin licencia")
    override suspend fun construirSolicitud(input: cu.shalev.licencia.SolicitudInput) = error("no usado")
    override suspend fun activar(mensaje: String) = error("no usado")
}
