package cu.shalev.designsystem

import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.token.ShalevSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class ShalevSpacingTest {
    @Test fun escalaBaseCuatroConservaLosValoresAprobados() {
        assertEquals(4.dp, ShalevSpacing.xxs)
        assertEquals(8.dp, ShalevSpacing.xs)
        assertEquals(12.dp, ShalevSpacing.sm)
        assertEquals(16.dp, ShalevSpacing.md)
        assertEquals(24.dp, ShalevSpacing.lg)
        assertEquals(32.dp, ShalevSpacing.xl)
        assertEquals(48.dp, ShalevSpacing.xxl)
        assertEquals(80.dp, ShalevSpacing.xxxl)
    }
}
