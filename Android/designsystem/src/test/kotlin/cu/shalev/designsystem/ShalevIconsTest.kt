package cu.shalev.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import cu.shalev.designsystem.icon.ShalevIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt 17: los iconos dejaron de venir de `material-icons-extended` y se construyen desde trazados SVG copiados.
 * Comprueba que TODOS se construyen, que son 24×24 y que cada trazado tiene nodos (un trazado mal parseado sale vacío).
 */
class ShalevIconsTest {
    private val iconos: Map<String, ImageVector> = ShalevIcons::class.java.methods
        .filter { it.parameterCount == 0 && it.returnType == ImageVector::class.java }
        .associate { it.name.removePrefix("get") to it.invoke(ShalevIcons) as ImageVector }

    @Test fun todosLosIconosSeConstruyen() {
        assertTrue("se esperaban ≥ 45 iconos y hay ${iconos.size}", iconos.size >= 45)
        iconos.forEach { (nombre, v) ->
            assertEquals(nombre, 24f, v.viewportWidth)
            assertEquals(nombre, 24f, v.viewportHeight)
            val trazados = v.root.filterIsInstance<VectorPath>() + v.root.filterIsInstance<VectorGroup>().flatMap { it.filterIsInstance<VectorPath>() }
            assertTrue("$nombre sin trazados", trazados.isNotEmpty())
            trazados.forEach { assertTrue("$nombre: trazado vacío", it.pathData.size > 2) }
        }
    }

    @Test fun lasFlechasSeInviertenEnRtl() {
        assertTrue(ShalevIcons.Atras.autoMirror)
        assertTrue(ShalevIcons.Abrir.autoMirror)
        assertTrue(!ShalevIcons.Inicio.autoMirror)
    }
}
