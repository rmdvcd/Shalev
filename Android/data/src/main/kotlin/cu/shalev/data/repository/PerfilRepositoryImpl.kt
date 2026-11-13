package cu.shalev.data.repository

import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.PerfilEntity
import cu.shalev.data.db.entity.TarjetaEntity
import cu.shalev.data.db.entity.TelefonoEntity
import cu.shalev.data.db.tx
import cu.shalev.data.mapper.perfilDe
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.repository.PerfilRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class PerfilRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
) : PerfilRepository {

    private val dao = db.perfilDao()

    override val perfil: Flow<Perfil> =
        combine(dao.observar(), dao.observarTarjetas(), dao.observarTelefonos(), ::perfilDe).distinctUntilChanged()

    /**
     * Reemplazo completo en una transacción. Los ids existentes se conservan (la selección de pago sigue
     * apuntando a la misma tarjeta); los nuevos (id = 0) reciben uno. Números repetidos: gana el primero.
     * Una selección de pago que ya no existe se anula.
     */
    override suspend fun guardar(perfil: Perfil) {
        db.tx {
            dao.borrarTarjetas()
            dao.borrarTelefonos()
            val tarjetas = perfil.tarjetas.distinctBy { it.numero }
                .map { dao.insertarTarjeta(TarjetaEntity(it.id, it.numero, it.alias)) }
            val telefonos = perfil.telefonos.distinctBy { it.numero }
                .map { dao.insertarTelefono(TelefonoEntity(it.id, it.numero, it.alias)) }
            dao.guardar(
                PerfilEntity(
                    nombre = perfil.nombre, apellidos = perfil.apellidos, ci = perfil.ci,
                    pagoTarjetaId = perfil.pagoTarjetaId?.takeIf { it in tarjetas },
                    pagoTelefonoId = perfil.pagoTelefonoId?.takeIf { it in telefonos },
                ),
            )
        }
    }
}
