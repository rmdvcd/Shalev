package cu.shalev.data.repository

import androidx.room.withTransaction
import cu.shalev.core.money.Cup
import cu.shalev.core.time.Clock
import cu.shalev.core.validation.Phone
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.domain.model.ClienteFijo
import cu.shalev.domain.repository.ClienteFijoRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 0.27.0 (N2): clientes fijos con sus compras (transferencias de ventas no anuladas con su carné). */
@Singleton
class ClienteFijoRepositoryImpl @Inject constructor(private val db: ShalevDatabase, private val clock: Clock) : ClienteFijoRepository {
    private val dao = db.clienteFijoDao()

    override fun observar(): Flow<List<ClienteFijo>> = combine(dao.observar(), dao.observarCompras()) { clientes, compras ->
        val porCi = compras.associateBy { it.ci }
        clientes.map { e ->
            val c = porCi[e.ci]
            ClienteFijo(
                id = e.id, nombreApellidos = e.nombreApellidos, ci = e.ci, telefono = e.telefono,
                creadoEn = Instant.ofEpochMilli(e.creadoEn), actualizadoEn = Instant.ofEpochMilli(e.actualizadoEn),
                compras = c?.compras ?: 0, total = Cup(c?.totalCent ?: 0), ultimaCompra = c?.ultima?.let(Instant::ofEpochMilli),
            )
        }
    }

    override suspend fun quitar(ci: String) { dao.quitar(ci) }

    override suspend fun actualizar(ci: String, nombreApellidos: String, nuevoCi: String, telefono: String): Boolean {
        val carne = nuevoCi.trim()
        val actual = dao.porCi(ci) ?: return true
        if (carne != ci && dao.porCi(carne) != null) return false
        // Se guarda como en la venta: nombre sin espacios de más y teléfono en E.164.
        val nombre = nombreApellidos.trim().replace(Regex("\\s+"), " ")
        val tel = Phone.normalize(telefono) ?: telefono.trim()
        db.withTransaction {
            dao.actualizar(actual.copy(nombreApellidos = nombre, ci = carne, telefono = tel, actualizadoEn = clock.now().toEpochMilli()))
            // 0.31.0: sus compras anteriores quedan con los datos corregidos (y con el carné nuevo, si cambió).
            dao.corregirCompras(ci, carne, nombre, tel)
        }
        return true
    }
}
