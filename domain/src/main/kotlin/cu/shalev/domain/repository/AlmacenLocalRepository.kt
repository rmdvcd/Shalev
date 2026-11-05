package cu.shalev.domain.repository

import kotlinx.coroutines.flow.Flow

/** Ubicación general; no asigna ni filtra artículos. Historial compartido local al equipo. */
interface LocalRepository {
    fun observar(): Flow<List<Local>>
    suspend fun seleccionar(nombre: String)
}

data class Local(val nombre: String)

/** Evita nombres vacíos, saltos de línea y duplicados por espacios o mayúsculas. */
object NombresLocal {
    fun normalizar(nombre: String): String = nombre.trim().replace(Regex("\\s+"), " ")
    fun validar(nombre: String): String = normalizar(nombre).also {
        require(it.isNotEmpty() && it.length <= 80) { "Escribe un local de 1 a 80 caracteres." }
    }
}
