package cu.shalev.domain.fichacosto

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.UnidadMedida
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Ficha de UNA unidad de producto o de UNA prestación del servicio. */
data class FichaCostoGuardada(
    val id: Long,
    val nombre: String,
    val margen: String,
    val unidadMargen: UnidadMargen,
    val tasaCupPorUsd: String,
    val partidas: List<PartidaCosto>,
)

enum class DestinoFicha { INVENTARIO, SERVICIO }

/** Exportación atómica: insumos nuevos + receta + artículo, o ninguno. */
interface FichaCostoRepository {
    fun observar(): Flow<List<FichaCostoGuardada>>
    suspend fun guardar(ficha: FichaCostoGuardada): AppResult<Unit>
    suspend fun eliminar(id: Long): AppResult<Unit>
    suspend fun exportar(ficha: FichaCostoGuardada, destino: DestinoFicha): AppResult<Long>
}

/** Reglas puras compartidas por la exportación y sus pruebas; nunca adivina una unidad desconocida. */
object ReglasFichaCosto {
    fun unidad(texto: String): UnidadMedida? = when (texto.trim().lowercase(Locale.ROOT)) {
        "u", "ud", "unidad", "unidades" -> UnidadMedida.UNIDAD
        "kg", "kilogramo", "kilogramos", "kilo", "kilos" -> UnidadMedida.KILOGRAMO
        "g", "gr", "gramo", "gramos" -> UnidadMedida.GRAMO
        "l", "lt", "litro", "litros" -> UnidadMedida.LITRO
        "ml", "mililitro", "mililitros" -> UnidadMedida.MILILITRO
        else -> null
    }

    /** Conversión exacta: masa con masa, volumen con volumen; máximo tres decimales de la medida destino. */
    fun convertir(cantidad: BigDecimal, origen: UnidadMedida, destino: UnidadMedida): Cantidad? {
        fun familia(u: UnidadMedida) = when (u) {
            UnidadMedida.KILOGRAMO, UnidadMedida.GRAMO -> 1
            UnidadMedida.LITRO, UnidadMedida.MILILITRO -> 2
            UnidadMedida.UNIDAD -> 3
        }
        fun factor(u: UnidadMedida) = when (u) {
            UnidadMedida.KILOGRAMO, UnidadMedida.LITRO -> BigDecimal(1000)
            else -> BigDecimal.ONE
        }
        if (cantidad.signum() <= 0 || familia(origen) != familia(destino)) return null
        return runCatching {
            Cantidad(cantidad.multiply(factor(origen)).divide(factor(destino))
                .movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact())
                .takeIf { it.milesimas > 0 }
        }.getOrNull()
    }

    fun validar(f: FichaCostoGuardada): Boolean = f.id > 0 && f.nombre.trim().length in 1..80 &&
        f.partidas.isNotEmpty() && f.partidas.size <= 500 &&
        f.partidas.all { it.descripcion.trim().length in 1..80 && it.cantidad.signum() > 0 && it.costoUnitario.signum() >= 0 } &&
        (f.margen.isBlank() || CalculoFichaCosto.numero(f.margen) != null) &&
        (f.tasaCupPorUsd.isBlank() || CalculoFichaCosto.numero(f.tasaCupPorUsd)?.signum() == 1)

    fun importeCup(partida: PartidaCosto, tasa: BigDecimal?): Cup? = if (partida.moneda == MonedaCosto.CUP) Cup.of(partida.subtotal)
        else tasa?.takeIf { it.signum() > 0 }?.let { Cup.of(partida.subtotal.multiply(it)) }

    fun precio(f: FichaCostoGuardada): Cup? {
        val tasa = CalculoFichaCosto.numero(f.tasaCupPorUsd)
        val t = CalculoFichaCosto.calcular(f.partidas, CalculoFichaCosto.numero(f.margen), f.unidadMargen)
        val totales = t.precioEstimado ?: t.costoTotal
        val usd = totales.getValue(MonedaCosto.USD)
        if (usd.signum() != 0 && tasa?.signum() != 1) return null
        return Cup.of(totales.getValue(MonedaCosto.CUP) + usd.multiply(tasa ?: BigDecimal.ZERO))
    }
}
