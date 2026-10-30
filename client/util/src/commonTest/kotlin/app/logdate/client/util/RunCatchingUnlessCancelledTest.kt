package app.logdate.client.util

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RunCatchingUnlessCancelledTest {
    @Test
    fun `a successful block returns its value`() {
        assertEquals(42, runCatchingUnlessCancelled { 42 }.getOrThrow())
    }

    @Test
    fun `a failing block is captured with its original cause`() {
        val failure = IllegalStateException("disk full")

        val result = runCatchingUnlessCancelled<Int> { throw failure }

        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `cancellation is rethrown instead of captured`() {
        assertFailsWith<CancellationException> {
            runCatchingUnlessCancelled<Int> { throw CancellationException("collector left") }
        }
    }
}
