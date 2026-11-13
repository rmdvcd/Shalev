package cu.shalev.designsystem.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import cu.shalev.designsystem.R

/**
 * P29 · Tipografía de marca, SOLO para el nombre de la app («Shalev» en la barra de Inicio).
 *
 * El usuario pidió «Anurati»; su licencia comercial es contradictoria según la fuente, así que se usa Orbitron
 * (SIL Open Font License 1.1, ver OFL_Orbitron.txt): mismo estilo geométrico y futurista, libre para apps comerciales.
 * Es una fuente variable (peso 400–900); se fija el peso 700. El resto de la app sigue con la tipografía del sistema.
 */
@OptIn(ExperimentalTextApi::class) // ajuste de eje variable (peso); estable en la práctica desde Compose 1.4
object ShalevMarca {
    val fuente: FontFamily = FontFamily(
        Font(R.font.orbitron, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
}
