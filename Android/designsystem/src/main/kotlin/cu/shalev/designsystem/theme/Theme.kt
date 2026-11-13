package cu.shalev.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import cu.shalev.designsystem.token.ShalevRadius

/**
 * Escala tipográfica estricta y reducida respecto a M3 por defecto.
 * Botones: labelLarge 13sp Medium. Texto secundario: bodySmall 13sp + 65 % de opacidad (ShalevSecondaryText). P18 (A24): bodyMedium 14sp y bodySmall 13sp (+1sp) para un público sin experiencia.
 */
val ShalevTypography: Typography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold).titulo(),
        titleLarge = titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold).titulo(),
        titleMedium = titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold).titulo(),
        titleSmall = titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium).titulo(),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp).cuerpo(),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp).cuerpo(),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp).cuerpo(),
        labelLarge = labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium).titulo(),
        labelMedium = labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium).titulo(),
        labelSmall = labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium).titulo(),
    )
}

/**
 * 0.26.0 (P73 §5): estilo único para los **datos** (precios, importes, fechas, cantidades, contadores, vencimiento e
 * ID de la licencia): seminegrita (600) sobre la escala existente. Mismo color que el texto normal → el contraste no
 * cambia. Usar siempre esto en vez de `copy(fontWeight = …)` suelto.
 */
object ShalevTextos {
    /** Peso de los datos destacados (también lo usa el PDF: Typeface 600). */
    val PESO_DATO: FontWeight = FontWeight.SemiBold

    /** Valor de una fila (lista, ficha, arqueo, licencia): titleSmall en seminegrita. */
    val dato: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = PESO_DATO)

    /** Cualquier estilo de la escala con el peso de los datos (p. ej. bodyMedium para un importe dentro de un texto). */
    fun datoEn(estilo: TextStyle): TextStyle = estilo.copy(fontWeight = PESO_DATO)

    /** Un dato solo («1 250.00 CUP») en seminegrita, para los huecos que piden [AnnotatedString]. */
    fun datoTexto(dato: String): AnnotatedString = buildAnnotatedString {
        append(dato)
        if (dato.isNotBlank()) addStyle(SpanStyle(fontWeight = PESO_DATO), 0, dato.length)
    }

    /**
     * 0.28.0: [texto] con cada ocurrencia de [datos] en seminegrita (importes y fechas dentro de frases como
     * «3 ventas · 1 250.00 CUP»). Ignora los datos en blanco o sin coincidencia. Los textos de la lógica no cambian:
     * el resaltado se aplica donde se muestra.
     */
    fun resaltar(texto: String, vararg datos: String): AnnotatedString = buildAnnotatedString {
        append(texto)
        datos.filter { it.isNotBlank() }.forEach { dato ->
            var desde = 0
            while (true) {
                val i = texto.indexOf(dato, desde)
                if (i < 0) break
                addStyle(SpanStyle(fontWeight = PESO_DATO), i, i + dato.length)
                desde = i + dato.length
            }
        }
    }
}

/**
 * P24, textos alineados y sin partir:
 * - el texto queda centrado verticalmente en su línea (mismo eje en filas, botones, chips y pestañas);
 * - los títulos y etiquetas reparten las palabras en líneas equilibradas (`LineBreak.Heading`): sin una palabra sola
 *   en la segunda línea;
 * - el texto corrido evita líneas huérfanas (`LineBreak.Paragraph`);
 * - nunca se corta una palabra con guion.
 */
private val CENTRADO = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun TextStyle.titulo() = copy(lineHeightStyle = CENTRADO, lineBreak = LineBreak.Heading, hyphens = Hyphens.None)

private fun TextStyle.cuerpo() = copy(lineHeightStyle = CENTRADO, lineBreak = LineBreak.Paragraph, hyphens = Hyphens.None)

val ShalevShapes = Shapes(
    extraSmall = RoundedCornerShape(ShalevRadius.sm),
    small = RoundedCornerShape(ShalevRadius.sm),
    medium = RoundedCornerShape(ShalevRadius.md),
    large = RoundedCornerShape(ShalevRadius.lg),
    extraLarge = RoundedCornerShape(ShalevRadius.xl),
)

/**
 * Tema de Shalev: sigue al sistema (`isSystemInDarkTheme`). Sin dynamic color: la identidad de marca
 * y el contraste AA verificado dependen de esta paleta fija.
 */
@Composable
fun ShalevTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShalevColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = ShalevTypography,
            shapes = ShalevShapes,
            content = content,
        )
    }
}

/** Acceso a los colores extendidos: `ShalevTheme.colors.alertStockBajo`. */
object ShalevTheme {
    val colors: ShalevExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalShalevColors.current
}
