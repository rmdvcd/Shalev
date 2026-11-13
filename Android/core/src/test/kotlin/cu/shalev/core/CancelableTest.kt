package cu.shalev.core

import cu.shalev.core.result.runCatchingCancelable
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelableTest {
    @Test fun exitoYFalloComoRunCatching() {
        assertEquals(3, runCatchingCancelable { 1 + 2 }.getOrNull())
        assertTrue(runCatchingCancelable<Int> { error("x") }.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun laCancelacionSeRelanza() {
        runCatchingCancelable<Unit> { throw CancellationException("cerrada") }
    }
}
