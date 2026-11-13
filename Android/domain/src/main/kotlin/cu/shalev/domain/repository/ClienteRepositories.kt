package cu.shalev.domain.repository

import cu.shalev.domain.model.ClienteFijo
import kotlinx.coroutines.flow.Flow

/** 0.27.0 (N2): clientes fijos (se guardan al vender; aquí se consultan, se corrigen y se quitan). */
interface ClienteFijoRepository {
    /** Ordenados por nombre, con sus compras calculadas. */
    fun observar(): Flow<List<ClienteFijo>>
    /** Deja de ser cliente fijo (sus ventas no cambian). */
    suspend fun quitar(ci: String)

    /**
     * 0.31.0: corrige el nombre, el teléfono o el carné del cliente [ci] (identificado por su carné). Devuelve `false`
     * sin cambiar nada si ese carné ya es de otro cliente. Sus compras anteriores quedan con los datos corregidos.
     */
    suspend fun actualizar(ci: String, nombreApellidos: String, nuevoCi: String, telefono: String): Boolean
}
