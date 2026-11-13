package cu.shalev.app.venta

import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import cu.shalev.domain.usecase.SmsPago

/** Filtrado puro del texto de una notificación: solo mensajes bancarios con una transacción reconocible. */
internal object CapturaSmsLogica {
    private val cabeceraBanco = Regex(
        "(?:Banco\\s+Popular\\s+de\\s+Ahorro|Banco\\s+de\\s+Cr[eé]dito\\s+y\\s+Comercio|Banco\\s+Metropolitano|\\bBPA\\b|\\bBANDEC\\b|\\bBANMET\\b)",
        RegexOption.IGNORE_CASE,
    )

    fun extraer(textos: Iterable<String?>, parser: ExtraerNumeroTransaccion): SmsPago? =
        textos.asSequence()
            .filterNotNull()
            .map { it.trim() }
            .filter(String::isNotEmpty)
            .distinct()
            .mapNotNull { texto -> if (cabeceraBanco.containsMatchIn(texto)) parser.detalle(texto) else null }
            .firstOrNull()
}
