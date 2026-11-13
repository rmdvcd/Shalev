package cu.shalev.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cu.shalev.designsystem.token.ColorTokens as T

internal val LightColorScheme = lightColorScheme(
    primary = Color(T.L_PRIMARY), onPrimary = Color(T.L_ON_PRIMARY),
    primaryContainer = Color(T.L_PRIMARY_CONTAINER), onPrimaryContainer = Color(T.L_ON_PRIMARY_CONTAINER),
    secondary = Color(T.L_SECONDARY), onSecondary = Color(T.L_ON_SECONDARY),
    secondaryContainer = Color(T.L_SECONDARY_CONTAINER), onSecondaryContainer = Color(T.L_ON_SECONDARY_CONTAINER),
    tertiary = Color(T.L_TERTIARY), onTertiary = Color(T.L_ON_TERTIARY),
    tertiaryContainer = Color(T.L_TERTIARY_CONTAINER), onTertiaryContainer = Color(T.L_ON_TERTIARY_CONTAINER),
    background = Color(T.L_BACKGROUND), onBackground = Color(T.L_ON_BACKGROUND),
    surface = Color(T.L_SURFACE), onSurface = Color(T.L_ON_SURFACE),
    surfaceVariant = Color(T.L_SURFACE_VARIANT), onSurfaceVariant = Color(T.L_ON_SURFACE_VARIANT),
    surfaceTint = Color.Transparent, // sin tinte tonal: en claro la elevación es por sombra
    surfaceBright = Color(T.L_SURFACE), surfaceDim = Color(T.L_SURFACE_CONTAINER_HIGHEST),
    surfaceContainerLowest = Color(T.L_SURFACE_CONTAINER_LOWEST), surfaceContainerLow = Color(T.L_SURFACE_CONTAINER_LOW),
    surfaceContainer = Color(T.L_SURFACE_CONTAINER), surfaceContainerHigh = Color(T.L_SURFACE_CONTAINER_HIGH),
    surfaceContainerHighest = Color(T.L_SURFACE_CONTAINER_HIGHEST),
    outline = Color(T.L_OUTLINE), outlineVariant = Color(T.L_OUTLINE_VARIANT),
    error = Color(T.L_ERROR), onError = Color(T.L_ON_ERROR),
    errorContainer = Color(T.L_ERROR_CONTAINER), onErrorContainer = Color(T.L_ON_ERROR_CONTAINER),
    inverseSurface = Color(T.L_INVERSE_SURFACE), inverseOnSurface = Color(T.L_INVERSE_ON_SURFACE),
    inversePrimary = Color(T.L_INVERSE_PRIMARY), scrim = Color(T.L_SCRIM),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(T.D_PRIMARY), onPrimary = Color(T.D_ON_PRIMARY),
    primaryContainer = Color(T.D_PRIMARY_CONTAINER), onPrimaryContainer = Color(T.D_ON_PRIMARY_CONTAINER),
    secondary = Color(T.D_SECONDARY), onSecondary = Color(T.D_ON_SECONDARY),
    secondaryContainer = Color(T.D_SECONDARY_CONTAINER), onSecondaryContainer = Color(T.D_ON_SECONDARY_CONTAINER),
    tertiary = Color(T.D_TERTIARY), onTertiary = Color(T.D_ON_TERTIARY),
    tertiaryContainer = Color(T.D_TERTIARY_CONTAINER), onTertiaryContainer = Color(T.D_ON_TERTIARY_CONTAINER),
    background = Color(T.D_BACKGROUND), onBackground = Color(T.D_ON_BACKGROUND),
    surface = Color(T.D_SURFACE), onSurface = Color(T.D_ON_SURFACE),
    surfaceVariant = Color(T.D_SURFACE_VARIANT), onSurfaceVariant = Color(T.D_ON_SURFACE_VARIANT),
    surfaceTint = Color.Transparent, // elevación explícita con surfaceContainer* (más claros)
    surfaceBright = Color(T.D_SURFACE_CONTAINER_HIGHEST), surfaceDim = Color(T.D_BACKGROUND),
    surfaceContainerLowest = Color(T.D_SURFACE_CONTAINER_LOWEST), surfaceContainerLow = Color(T.D_SURFACE_CONTAINER_LOW),
    surfaceContainer = Color(T.D_SURFACE_CONTAINER), surfaceContainerHigh = Color(T.D_SURFACE_CONTAINER_HIGH),
    surfaceContainerHighest = Color(T.D_SURFACE_CONTAINER_HIGHEST),
    outline = Color(T.D_OUTLINE), outlineVariant = Color(T.D_OUTLINE_VARIANT),
    error = Color(T.D_ERROR), onError = Color(T.D_ON_ERROR),
    errorContainer = Color(T.D_ERROR_CONTAINER), onErrorContainer = Color(T.D_ON_ERROR_CONTAINER),
    inverseSurface = Color(T.D_INVERSE_SURFACE), inverseOnSurface = Color(T.D_INVERSE_ON_SURFACE),
    inversePrimary = Color(T.D_INVERSE_PRIMARY), scrim = Color(T.D_SCRIM),
)

/** Colores fuera del esquema M3: alertas, éxito, WhatsApp. */
@Immutable
data class ShalevExtendedColors(
    val isDark: Boolean,
    val alertStockBajo: Color,
    val alertStockCritico: Color,
    val alertInsumoBajo: Color,
    val alertInsumoCritico: Color,
    val alertCaducidad: Color,
    val success: Color,
    val whatsapp: Color,
    val onWhatsapp: Color,
    /** Paleta de series/porciones de los gráficos; el último color se reserva para "Otras". */
    val chart: List<Color>,
)

internal val LightExtended = ShalevExtendedColors(
    isDark = false,
    alertStockBajo = Color(T.L_ALERT_STOCK_BAJO), alertStockCritico = Color(T.L_ALERT_STOCK_CRITICO),
    alertInsumoBajo = Color(T.L_ALERT_INSUMO_BAJO), alertInsumoCritico = Color(T.L_ALERT_INSUMO_CRITICO),
    alertCaducidad = Color(T.L_ALERT_CADUCIDAD), success = Color(T.L_SUCCESS),
    whatsapp = Color(T.WHATSAPP), onWhatsapp = Color(T.ON_WHATSAPP),
    chart = T.L_CHART.map { Color(it) },
)

internal val DarkExtended = ShalevExtendedColors(
    isDark = true,
    alertStockBajo = Color(T.D_ALERT_STOCK_BAJO), alertStockCritico = Color(T.D_ALERT_STOCK_CRITICO),
    alertInsumoBajo = Color(T.D_ALERT_INSUMO_BAJO), alertInsumoCritico = Color(T.D_ALERT_INSUMO_CRITICO),
    alertCaducidad = Color(T.D_ALERT_CADUCIDAD), success = Color(T.D_SUCCESS),
    whatsapp = Color(T.WHATSAPP), onWhatsapp = Color(T.ON_WHATSAPP),
    chart = T.D_CHART.map { Color(it) },
)

val LocalShalevColors = staticCompositionLocalOf { LightExtended }

/** Las 5 alertas de Inicio (Shalev.txt), con su color semántico. */
enum class AlertTone {
    StockBajo, StockCritico, InsumoBajo, InsumoCritico, Caducidad,
    /** 0.21.1 (H4): «Sin existencia». Mismo rojo que Stock crítico, pero el contador va RELLENO (fondo error). */
    SinExistencia,
    /** 0.24.0: «Nombre repetido». Dato por completar: el número va en el color primario (AA ya verificado). */
    Revisar,
}

fun ShalevExtendedColors.of(tone: AlertTone): Color = when (tone) {
    AlertTone.StockBajo -> alertStockBajo
    AlertTone.StockCritico, AlertTone.SinExistencia -> alertStockCritico
    AlertTone.InsumoBajo -> alertInsumoBajo
    AlertTone.InsumoCritico -> alertInsumoCritico
    AlertTone.Caducidad -> alertCaducidad
    AlertTone.Revisar -> alertInsumoBajo // no se usa: el contador de Inicio pinta Revisar con colorScheme.primary
}
