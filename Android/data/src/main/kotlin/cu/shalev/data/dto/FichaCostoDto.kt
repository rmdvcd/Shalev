package cu.shalev.data.dto

import cu.shalev.domain.fichacosto.CategoriaCosto
import cu.shalev.domain.fichacosto.FichaCostoGuardada
import cu.shalev.domain.fichacosto.MonedaCosto
import cu.shalev.domain.fichacosto.PartidaCosto
import cu.shalev.domain.fichacosto.UnidadMargen
import kotlinx.serialization.Serializable

/** Compatible con el JSON legado de SharedPreferences; números como texto decimal exacto. */
@Serializable
data class FichaCostoDto(
    val id: Long, val nombre: String, val margen: String = "", val unidadMargen: String = "PORCENTAJE",
    val tasa: String = "", val partidas: List<PartidaCostoDto> = emptyList(),
)
@Serializable
data class PartidaCostoDto(
    val descripcion: String, val categoria: String, val cantidad: String, val unidad: String,
    val costo: String, val moneda: String,
)
fun FichaCostoGuardada.toDto() = FichaCostoDto(id, nombre, margen, unidadMargen.name, tasaCupPorUsd, partidas.map {
    PartidaCostoDto(it.descripcion, it.categoria.name, it.cantidad.toPlainString(), it.unidad, it.costoUnitario.toPlainString(), it.moneda.name)
})
fun FichaCostoDto.toDomain() = FichaCostoGuardada(id, nombre, margen, UnidadMargen.valueOf(unidadMargen), tasa, partidas.map {
    PartidaCosto(it.descripcion, CategoriaCosto.valueOf(it.categoria), it.cantidad.toBigDecimal(), it.unidad, it.costo.toBigDecimal(), MonedaCosto.valueOf(it.moneda))
})
