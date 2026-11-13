package cu.shalev.data.dto

import cu.shalev.core.money.Cup
import cu.shalev.data.local.ShalevJson
import cu.shalev.domain.model.ConteoCierre
import cu.shalev.domain.model.LineaConteo
import cu.shalev.domain.model.TipoEntidad
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class LineaConteoDto(
    val entidad: String, val id: Long, val nombre: String, val unidad: String,
    val esperado: Long, val contado: Long, val actualizadoEn: Long,
    /** 0.32.0: categoría del artículo (para agrupar el conteo). Vacía en los conteos anteriores. */
    val categoria: String = "",
)
@Serializable
data class ConteoCierreDto(
    val billetes: Map<Int, Long> = emptyMap(), val otroCent: Long = 0,
    val inventario: List<LineaConteoDto> = emptyList(), val motivo: String = "",
    val aprobadoPor: String? = null, val aprobadoEn: Long? = null,
)
fun ConteoCierre.toDto() = ConteoCierreDto(billetes, otroEfectivo.centavos,
    inventario.map { LineaConteoDto(it.entidad.name, it.id, it.nombre, it.unidad, it.esperado, it.contado, it.actualizadoEn, it.categoria) },
    motivo, aprobadoPor, aprobadoEn)
fun ConteoCierreDto.toDomain() = ConteoCierre(billetes, Cup(otroCent),
    inventario.map { LineaConteo(TipoEntidad.valueOf(it.entidad), it.id, it.nombre, it.unidad, it.esperado, it.contado, it.actualizadoEn, it.categoria) },
    motivo, aprobadoPor, aprobadoEn)
fun ConteoCierre.aJson(): String = ShalevJson.encodeToString(toDto())
fun String.conteoDeJson(): ConteoCierre = ShalevJson.decodeFromString<ConteoCierreDto>(this).toDomain()
