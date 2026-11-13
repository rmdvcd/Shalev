package cu.shalev.designsystem

import cu.shalev.designsystem.token.ColorTokens as T
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.1 AA sobre los tokens reales:
 *  - Texto: ≥ 4.5:1 (1.4.3).
 *  - Componentes de UI/iconos y bordes: ≥ 3:1 (1.4.11).
 * Si alguien cambia un token y rompe el contraste, este test falla.
 */
class ContrastTest {

    private fun channel(c: Long, shift: Int): Double {
        val v = ((c shr shift) and 0xFF) / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Long) = 0.2126 * channel(c, 16) + 0.7152 * channel(c, 8) + 0.0722 * channel(c, 0)

    private fun ratio(a: Long, b: Long): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Mezcla fg con alfa sobre bg (texto secundario al 65 %). */
    private fun blend(fg: Long, bg: Long, alpha: Float): Long {
        fun ch(c: Long, s: Int) = ((c shr s) and 0xFF).toDouble()
        fun mix(s: Int) = Math.round(ch(fg, s) * alpha + ch(bg, s) * (1 - alpha))
        return (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun check(name: String, fg: Long, bg: Long, min: Double) {
        val r = ratio(fg, bg)
        assertTrue("%s: %.2f < %.1f".format(name, r, min), r >= min)
    }

    private val TEXT = 4.5
    private val UI = 3.0

    @Test fun lightText() {
        check("primary/onPrimary", T.L_ON_PRIMARY, T.L_PRIMARY, TEXT)
        check("secondary/onSecondary", T.L_ON_SECONDARY, T.L_SECONDARY, TEXT)
        check("tertiary/onTertiary", T.L_ON_TERTIARY, T.L_TERTIARY, TEXT)
        check("primaryContainer", T.L_ON_PRIMARY_CONTAINER, T.L_PRIMARY_CONTAINER, TEXT)
        check("secondaryContainer", T.L_ON_SECONDARY_CONTAINER, T.L_SECONDARY_CONTAINER, TEXT)
        check("tertiaryContainer", T.L_ON_TERTIARY_CONTAINER, T.L_TERTIARY_CONTAINER, TEXT)
        check("onBackground", T.L_ON_BACKGROUND, T.L_BACKGROUND, TEXT)
        check("onSurface", T.L_ON_SURFACE, T.L_SURFACE, TEXT)
        check("onSurfaceVariant/surface", T.L_ON_SURFACE_VARIANT, T.L_SURFACE, TEXT)
        check("onSurfaceVariant/containerHighest", T.L_ON_SURFACE_VARIANT, T.L_SURFACE_CONTAINER_HIGHEST, TEXT)
        check("primary/surfaceContainer (icono seleccionado fuera de indicador)", T.L_PRIMARY, T.L_SURFACE_CONTAINER, TEXT)
        check("error/surface", T.L_ERROR, T.L_SURFACE, TEXT)
        check("errorContainer", T.L_ON_ERROR_CONTAINER, T.L_ERROR_CONTAINER, TEXT)
        check("inverse (snackbar)", T.L_INVERSE_ON_SURFACE, T.L_INVERSE_SURFACE, TEXT)
        check("secundario 65% / surface", blend(T.L_ON_SURFACE, T.L_SURFACE, T.SECONDARY_ALPHA), T.L_SURFACE, TEXT)
        check("secundario 65% / background", blend(T.L_ON_SURFACE, T.L_BACKGROUND, T.SECONDARY_ALPHA), T.L_BACKGROUND, TEXT)
        check("secundario 65% / containerHigh", blend(T.L_ON_SURFACE, T.L_SURFACE_CONTAINER_HIGH, T.SECONDARY_ALPHA), T.L_SURFACE_CONTAINER_HIGH, TEXT)
    }

    @Test fun darkText() {
        check("primary/onPrimary", T.D_ON_PRIMARY, T.D_PRIMARY, TEXT)
        check("secondary/onSecondary", T.D_ON_SECONDARY, T.D_SECONDARY, TEXT)
        check("tertiary/onTertiary", T.D_ON_TERTIARY, T.D_TERTIARY, TEXT)
        check("primaryContainer", T.D_ON_PRIMARY_CONTAINER, T.D_PRIMARY_CONTAINER, TEXT)
        check("secondaryContainer", T.D_ON_SECONDARY_CONTAINER, T.D_SECONDARY_CONTAINER, TEXT)
        check("tertiaryContainer", T.D_ON_TERTIARY_CONTAINER, T.D_TERTIARY_CONTAINER, TEXT)
        check("onBackground", T.D_ON_BACKGROUND, T.D_BACKGROUND, TEXT)
        check("onSurface", T.D_ON_SURFACE, T.D_SURFACE, TEXT)
        check("onSurface/containerHighest", T.D_ON_SURFACE, T.D_SURFACE_CONTAINER_HIGHEST, TEXT)
        check("onSurfaceVariant/surface", T.D_ON_SURFACE_VARIANT, T.D_SURFACE, TEXT)
        check("onSurfaceVariant/containerHighest", T.D_ON_SURFACE_VARIANT, T.D_SURFACE_CONTAINER_HIGHEST, TEXT)
        check("primary/surface", T.D_PRIMARY, T.D_SURFACE, TEXT)
        check("error/surface", T.D_ERROR, T.D_SURFACE, TEXT)
        check("error/onError", T.D_ON_ERROR, T.D_ERROR, TEXT)
        check("errorContainer", T.D_ON_ERROR_CONTAINER, T.D_ERROR_CONTAINER, TEXT)
        check("inverse (snackbar)", T.D_INVERSE_ON_SURFACE, T.D_INVERSE_SURFACE, TEXT)
        check("secundario 65% / surface", blend(T.D_ON_SURFACE, T.D_SURFACE, T.SECONDARY_ALPHA), T.D_SURFACE, TEXT)
        check("secundario 65% / background", blend(T.D_ON_SURFACE, T.D_BACKGROUND, T.SECONDARY_ALPHA), T.D_BACKGROUND, TEXT)
        check("secundario 65% / containerHigh", blend(T.D_ON_SURFACE, T.D_SURFACE_CONTAINER_HIGH, T.SECONDARY_ALPHA), T.D_SURFACE_CONTAINER_HIGH, TEXT)
    }

    @Test fun uiComponents() {
        check("L outline (bordes de campos)", T.L_OUTLINE, T.L_SURFACE, UI)
        check("D outline", T.D_OUTLINE, T.D_SURFACE, UI)
        check("L indicador nav (primary/surfaceContainer)", T.L_PRIMARY, T.L_SURFACE_CONTAINER, UI)
        check("D indicador nav (primary/surfaceContainer)", T.D_PRIMARY, T.D_SURFACE_CONTAINER, UI)
        check("WhatsApp icono blanco", T.ON_WHATSAPP, T.WHATSAPP, TEXT)
        check("L error como fondo de icono", T.L_ON_ERROR, T.L_ERROR, TEXT)
        // 0.21.1 (H1): bordes de campos dentro de diálogos y menús.
        check("L outline / containerHigh (diálogos)", T.L_OUTLINE, T.L_SURFACE_CONTAINER_HIGH, UI)
        check("L outline / containerHighest", T.L_OUTLINE, T.L_SURFACE_CONTAINER_HIGHEST, UI)
        check("D outline / containerHigh (diálogos)", T.D_OUTLINE, T.D_SURFACE_CONTAINER_HIGH, UI)
        // 0.21.1 (H4): contador «Sin existencia» relleno.
        check("L sin existencia (onError/error)", T.L_ON_ERROR, T.L_ERROR, TEXT)
        check("D sin existencia (onError/error)", T.D_ON_ERROR, T.D_ERROR, TEXT)
    }

    /** 0.21.1 (H3/H5): las alertas no se confunden con el naranja de marca ni con los gráficos. */
    @Test fun alertasDistintasDeMarcaYGraficos() {
        assertTrue(T.L_ALERT_INSUMO_CRITICO != T.L_SECONDARY && ratio(T.L_ALERT_INSUMO_CRITICO, T.L_SECONDARY) > 1.3)
        val alertasL = setOf(T.L_ALERT_STOCK_BAJO, T.L_ALERT_INSUMO_BAJO, T.L_ALERT_INSUMO_CRITICO, T.L_ALERT_CADUCIDAD)
        val alertasD = setOf(T.D_ALERT_STOCK_BAJO, T.D_ALERT_INSUMO_BAJO, T.D_ALERT_INSUMO_CRITICO, T.D_ALERT_CADUCIDAD)
        assertTrue(T.L_CHART.none { it in alertasL })
        assertTrue(T.D_CHART.none { it in alertasD })
        // 0.21.2 (H6): «insumo bajo» ya no es verde (no se confunde con «éxito»).
        assertTrue(T.L_ALERT_INSUMO_BAJO != T.L_SUCCESS && T.D_ALERT_INSUMO_BAJO != T.D_SUCCESS)
        assertTrue(T.L_ALERT_INSUMO_BAJO == T.L_TERTIARY && T.D_ALERT_INSUMO_BAJO == T.D_TERTIARY)
    }

    @Test fun alertCountersAreReadable() {
        listOf(T.L_ALERT_STOCK_BAJO, T.L_ALERT_STOCK_CRITICO, T.L_ALERT_INSUMO_BAJO, T.L_ALERT_INSUMO_CRITICO, T.L_ALERT_CADUCIDAD)
            .forEachIndexed { i, c -> check("alerta L#$i", c, T.L_SURFACE, TEXT) }
        listOf(T.D_ALERT_STOCK_BAJO, T.D_ALERT_STOCK_CRITICO, T.D_ALERT_INSUMO_BAJO, T.D_ALERT_INSUMO_CRITICO, T.D_ALERT_CADUCIDAD)
            .forEachIndexed { i, c -> check("alerta D#$i", c, T.D_SURFACE, TEXT) }
        check("éxito L", T.L_SUCCESS, T.L_SURFACE, TEXT)
        check("éxito D", T.D_SUCCESS, T.D_SURFACE, TEXT)
    }

    /** Barras, porciones y líneas de los gráficos contra la card (surface) y el fondo tonal. */
    @Test fun chartPaletteIsVisible() {
        T.L_CHART.forEachIndexed { i, c ->
            check("gráfico L#$i / surface", c, T.L_SURFACE, UI)
            check("gráfico L#$i / surfaceContainer", c, T.L_SURFACE_CONTAINER, UI)
        }
        T.D_CHART.forEachIndexed { i, c ->
            check("gráfico D#$i / surface", c, T.D_SURFACE, UI)
            check("gráfico D#$i / surfaceContainerHigh", c, T.D_SURFACE_CONTAINER_HIGH, UI)
        }
    }

    /** 0.21.3 (H7): el oscuro lleva un toque de azul (azul > rojo) y la elevación sigue subiendo de luminancia. */
    @Test fun oscuroConTinteDeMarca() {
        val escala = listOf(T.D_BACKGROUND, T.D_SURFACE, T.D_SURFACE_CONTAINER_LOW, T.D_SURFACE_CONTAINER,
            T.D_SURFACE_CONTAINER_HIGH, T.D_SURFACE_CONTAINER_HIGHEST)
        escala.forEach { c -> assertTrue("sin tinte azul: %08X".format(c), (c and 0xFF) > ((c shr 16) and 0xFF)) }
        escala.zipWithNext().forEach { (a, b) -> assertTrue("elevación no sube: %08X → %08X".format(a, b), luminance(b) > luminance(a)) }
        check("D outline / containerHighest", T.D_OUTLINE, T.D_SURFACE_CONTAINER_HIGHEST, UI)
    }

    @Test fun brandDecorativesAreNotUsedAsTextRoles() {
        // Documenta por qué los colores de marca puros no son roles de texto: fallan AA con blanco.
        assertTrue(ratio(T.ORANGE, T.WHITE) < TEXT)
        assertTrue(ratio(T.TEAL, T.WHITE) < TEXT)
    }

    /** 0.27.0 (T3): el número de cada medalla del Top 3 se lee (AA) sobre su círculo. */
    @Test fun medallasLegibles() {
        check("oro", T.ON_MEDALLA_ORO, T.MEDALLA_ORO, TEXT)
        check("plata", T.ON_MEDALLA_PLATA, T.MEDALLA_PLATA, TEXT)
        check("bronce", T.ON_MEDALLA_BRONCE, T.MEDALLA_BRONCE, TEXT)
    }
}
