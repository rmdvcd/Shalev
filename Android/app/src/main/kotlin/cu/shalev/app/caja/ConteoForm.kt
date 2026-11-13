package cu.shalev.app.caja

import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.TipoEntidad

/** Entrada estricta: sin separadores de miles; unidades enteras o hasta tres decimales en insumos. */
object ConteoForm {
    fun cantidad(texto: String, entidad: TipoEntidad): Long? = when (entidad) {
        TipoEntidad.PRODUCTO -> if (texto.matches(Regex("[0-9]+"))) texto.toLongOrNull() else null
        TipoEntidad.INSUMO -> if (texto.matches(Regex("[0-9]+([.,][0-9]{1,3})?")))
            Cantidad.parse(texto.replace(',', '.'))?.milesimas else null
    }
    fun formato(valor: Long, entidad: TipoEntidad): String =
        if (entidad == TipoEntidad.INSUMO) Cantidad.format(Cantidad(valor)) else valor.toString()
}
