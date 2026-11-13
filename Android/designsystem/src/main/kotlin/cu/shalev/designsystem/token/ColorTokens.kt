package cu.shalev.designsystem.token

/**
 * Tokens de color como ARGB puros (sin dependencias de Compose) para poder verificar el contraste WCAG
 * en tests JVM (ContrastTest). Ningún componente usa hex directos: todo pasa por aquí.
 *
 * Marca extraída de SVIPicono.png. Orange/Amber/Teal de marca son DECORATIVOS: no alcanzan AA con texto
 * blanco; los roles de texto usan variantes oscurecidas (L_SECONDARY, L_TERTIARY).
 */
object ColorTokens {
    // ---- Marca
    const val NAVY = 0xFF2B4FA3
    const val SKY = 0xFF66D1EF
    const val TEAL = 0xFF478EA1
    const val ORANGE = 0xFFEE7F3B
    const val AMBER = 0xFFF6B645
    const val WHITE = 0xFFFFFFFF

    // ---- Light
    const val L_PRIMARY = NAVY
    const val L_ON_PRIMARY = WHITE
    const val L_PRIMARY_CONTAINER = 0xFFDCE4FA
    const val L_ON_PRIMARY_CONTAINER = 0xFF0F2660
    const val L_SECONDARY = 0xFFB4541A
    const val L_ON_SECONDARY = WHITE
    const val L_SECONDARY_CONTAINER = 0xFFFFE0CC
    const val L_ON_SECONDARY_CONTAINER = 0xFF3A1A00
    const val L_TERTIARY = 0xFF2F6F80
    const val L_ON_TERTIARY = WHITE
    const val L_TERTIARY_CONTAINER = 0xFFCDEBF3
    const val L_ON_TERTIARY_CONTAINER = 0xFF0B3440
    const val L_BACKGROUND = 0xFFF8F9FA
    const val L_ON_BACKGROUND = 0xFF1A1C1E
    const val L_SURFACE = WHITE
    const val L_ON_SURFACE = 0xFF1A1C1E
    const val L_SURFACE_VARIANT = 0xFFEEF1F4
    const val L_ON_SURFACE_VARIANT = 0xFF5F6368
    const val L_SURFACE_CONTAINER_LOWEST = WHITE
    const val L_SURFACE_CONTAINER_LOW = 0xFFF6F7F9
    const val L_SURFACE_CONTAINER = 0xFFF1F3F5
    const val L_SURFACE_CONTAINER_HIGH = 0xFFEBEDF0
    const val L_SURFACE_CONTAINER_HIGHEST = 0xFFE5E8EB
    const val L_OUTLINE = 0xFF767B82          // 0.21.1 (H1): ≥3:1 también sobre los diálogos (surfaceContainerHigh/Highest), WCAG 1.4.11
    const val L_OUTLINE_VARIANT = 0xFFE0E3E7  // separadores decorativos
    const val L_ERROR = 0xFFC62828
    const val L_ON_ERROR = WHITE
    const val L_ERROR_CONTAINER = 0xFFFDE7E7
    const val L_ON_ERROR_CONTAINER = 0xFF5F1111
    const val L_INVERSE_SURFACE = 0xFF2F3033
    const val L_INVERSE_ON_SURFACE = 0xFFF1F0F4
    const val L_INVERSE_PRIMARY = SKY
    const val L_SCRIM = 0xFF000000

    // ---- Dark (elevación = superficies más claras, nunca sombras)
    // 0.21.3 (H7): grises con un toque del azul de marca (antes neutros #121212 / #1E1E1E / #282828…). Misma escala de
    // luminancia: los contrastes de texto no bajan de AA (ContrastTest.darkText y oscuroConTinteDeMarca).
    const val D_PRIMARY = SKY
    const val D_ON_PRIMARY = 0xFF00212C
    const val D_PRIMARY_CONTAINER = 0xFF1F3A4A
    const val D_ON_PRIMARY_CONTAINER = 0xFFCDEFFB
    const val D_SECONDARY = 0xFFF6A15C
    const val D_ON_SECONDARY = 0xFF3A1A00
    const val D_SECONDARY_CONTAINER = 0xFF5A2F10
    const val D_ON_SECONDARY_CONTAINER = 0xFFFFDCC2
    const val D_TERTIARY = 0xFF7FC4D4
    const val D_ON_TERTIARY = 0xFF00363F
    const val D_TERTIARY_CONTAINER = 0xFF12434F
    const val D_ON_TERTIARY_CONTAINER = 0xFFCDEBF3
    const val D_BACKGROUND = 0xFF0F1318
    const val D_ON_BACKGROUND = 0xFFE6E6E6
    const val D_SURFACE = 0xFF1A1F26
    const val D_ON_SURFACE = 0xFFE6E6E6
    const val D_SURFACE_VARIANT = 0xFF262C35
    const val D_ON_SURFACE_VARIANT = 0xFFB3B9C2
    const val D_SURFACE_CONTAINER_LOWEST = 0xFF14181E
    const val D_SURFACE_CONTAINER_LOW = 0xFF1F252D
    const val D_SURFACE_CONTAINER = 0xFF242A33
    const val D_SURFACE_CONTAINER_HIGH = 0xFF2A313B
    const val D_SURFACE_CONTAINER_HIGHEST = 0xFF313944
    const val D_OUTLINE = 0xFF8C939E
    const val D_OUTLINE_VARIANT = 0xFF363E4A
    const val D_ERROR = 0xFFEF9A9A
    const val D_ON_ERROR = 0xFF3B0000
    const val D_ERROR_CONTAINER = 0xFF5C1A1A
    const val D_ON_ERROR_CONTAINER = 0xFFFFDAD6
    const val D_INVERSE_SURFACE = 0xFFE6E6E6
    const val D_INVERSE_ON_SURFACE = 0xFF1A1F26
    const val D_INVERSE_PRIMARY = NAVY
    const val D_SCRIM = 0xFF000000

    // ---- Alertas de inventario (Shalev.txt). Variante L = texto sobre blanco; D = texto sobre #1E1E1E. Todas ≥4.5:1.
    const val L_ALERT_STOCK_BAJO = 0xFF8A6A00      // amarillo
    const val L_ALERT_STOCK_CRITICO = 0xFFC62828   // rojo
    const val L_ALERT_INSUMO_BAJO = 0xFF2F6F80     // petróleo (= L_TERTIARY). 0.21.2 (H6): antes verde #3D7A1F, se leía como «todo bien»
    const val L_ALERT_INSUMO_CRITICO = 0xFF9A3412  // naranja rojizo. 0.21.1 (H3): antes #B45309, igual que el naranja de marca (L_SECONDARY)
    const val L_ALERT_CADUCIDAD = 0xFF7B4FB8       // púrpura claro
    const val D_ALERT_STOCK_BAJO = 0xFFFFD54F
    const val D_ALERT_STOCK_CRITICO = 0xFFEF9A9A
    const val D_ALERT_INSUMO_BAJO = 0xFF7FC4D4     // petróleo (= D_TERTIARY). 0.21.2 (H6): antes verde #AED581
    const val D_ALERT_INSUMO_CRITICO = 0xFFFFB74D
    const val D_ALERT_CADUCIDAD = 0xFFCE93D8

    // ---- Gráficos (Inicio). Elementos gráficos no textuales: ≥3:1 contra la superficie de la card (WCAG 1.4.11).
    // El color nunca es el único portador de información: cada dona lleva leyenda con nombre y porcentaje.
    const val L_CHART_1 = NAVY
    const val L_CHART_2 = 0xFFB4541A
    const val L_CHART_3 = 0xFF8B5E34  // caramelo. 0.21.2: el petróleo pasó a «insumo bajo»
    // 0.21.1 (H5) / 0.21.2: sin los tonos de las alertas (morado = caducidad, petróleo = insumo bajo, amarillo = stock bajo).
    const val L_CHART_4 = 0xFFA23B72  // magenta
    const val L_CHART_5 = 0xFF6B6B00  // oliva
    const val L_CHART_6 = 0xFF80868D  // "Otras"
    const val D_CHART_1 = SKY
    const val D_CHART_2 = 0xFFF6A15C
    const val D_CHART_3 = 0xFFD4A373  // caramelo. 0.21.2: el petróleo pasó a «insumo bajo»
    const val D_CHART_4 = 0xFFF48FB1  // magenta
    const val D_CHART_5 = 0xFFC5C56A  // oliva
    const val D_CHART_6 = 0xFF9E9E9E
    val L_CHART = longArrayOf(L_CHART_1, L_CHART_2, L_CHART_3, L_CHART_4, L_CHART_5, L_CHART_6)
    val D_CHART = longArrayOf(D_CHART_1, D_CHART_2, D_CHART_3, D_CHART_4, D_CHART_5, D_CHART_6)

    // ---- 0.27.0 (T3): medallas del Top 3 de Inicio. Iguales en claro y en oscuro (oro, plata y bronce se reconocen
    // por sí mismos); el número va en un tono muy oscuro de la misma familia, ≥4.5:1 sobre cada medalla (ContrastTest).
    const val MEDALLA_ORO = 0xFFF2C94C
    const val ON_MEDALLA_ORO = 0xFF3D2E00
    const val MEDALLA_PLATA = 0xFFC5CCD6
    const val ON_MEDALLA_PLATA = 0xFF1F2933
    const val MEDALLA_BRONCE = 0xFFC98A4B
    const val ON_MEDALLA_BRONCE = 0xFF2B1600

    // ---- Semánticos
    const val L_SUCCESS = 0xFF2E7D32
    const val D_SUCCESS = 0xFF81C784
    /** Verde WhatsApp oscurecido: 5.3:1 con icono blanco (el #25D366 oficial da 1.98:1). */
    const val WHATSAPP = 0xFF0B7A5E
    const val ON_WHATSAPP = WHITE

    /** Opacidad del texto secundario (60–70 %). */
    const val SECONDARY_ALPHA = 0.65f
}
