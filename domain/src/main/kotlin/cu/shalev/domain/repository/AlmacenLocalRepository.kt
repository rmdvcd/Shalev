package cu.shalev.domain.repository

import kotlinx.coroutines.flow.Flow

enum class VentanaUbicacion { INVENTARIO, SERVICIOS }

data class AlmacenLocal(val nombre: String, val inventario: Boolean, val servicios: Boolean)

/** Ubicación general por ventana; no asigna ni filtra artículos. Historial compartido local al equipo. */
interface AlmacenLocalRepository {
    fun observar(): Flow<List<AlmacenLocal>>
    suspend fun seleccionar(ventana: VentanaUbicacion, nombre: String)
}

/** Evita nombres vacíos, saltos de línea y duplicados por espacios o mayúsculas. */
object NombresAlmacenLocal {
    fun normalizar(nombre: String): String = nombre.trim().replace(Regex("\\s+"), " ")
    fun validar(nombre: String): String = normalizar(nombre).also {
        require(it.isNotEmpty() && it.length <= 80) { "Escribe un almacén/local de 1 a 80 caracteres." }
    }
}
