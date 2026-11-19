package cu.shalev.data.repository

import cu.shalev.data.local.SecureDataStore
import cu.shalev.domain.model.AjustesDispositivo
import cu.shalev.domain.model.ModoRed
import cu.shalev.domain.repository.AjustesDispositivoRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 0.27.0: ajustes de este teléfono en el DataStore cifrado, con claves propias. El respaldo no los lee ni los
 * escribe (solo usa [cu.shalev.domain.repository.PreferenciasRepository]).
 */
@Singleton
class AjustesDispositivoRepositoryImpl @Inject constructor(private val ds: SecureDataStore) : AjustesDispositivoRepository {

    override val ajustes: Flow<AjustesDispositivo> =
        combine(ds.observe(K_CLAVE), ds.observe(K_SECUNDARIA), ds.observe(K_MODO_RED), ds.observe(K_SINC_AUTO)) { c, s, m, sa ->
            AjustesDispositivo(
                accesoConClave = c == "1",
                eligioSecundaria = s == "1",
                // 1.0.7: valor ilegible o ausente → LAN (la de siempre).
                modoRed = m?.let { runCatching { ModoRed.valueOf(it) }.getOrNull() } ?: ModoRed.LAN,
                sincronizacionAutomatica = sa != "0",
            )
        }
            .distinctUntilChanged()

    override suspend fun guardarAccesoConClave(activo: Boolean) = ds.put(K_CLAVE, if (activo) "1" else "0")
    override suspend fun guardarEligioSecundaria(valor: Boolean) = ds.put(K_SECUNDARIA, if (valor) "1" else "0")
    override suspend fun guardarModoRed(modo: ModoRed) = ds.put(K_MODO_RED, modo.name)
    override suspend fun guardarSincronizacionAutomatica(activa: Boolean) = ds.put(K_SINC_AUTO, if (activa) "1" else "0")

    private companion object {
        const val K_CLAVE = "disp.acceso_clave"
        const val K_SECUNDARIA = "disp.eligio_secundaria"
        const val K_MODO_RED = "disp.modo_red"
        const val K_SINC_AUTO = "disp.sinc_automatica"
    }
}
