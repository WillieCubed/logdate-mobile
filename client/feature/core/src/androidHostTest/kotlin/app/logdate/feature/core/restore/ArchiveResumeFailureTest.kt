package app.logdate.feature.core.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveResumeFailureTest {
    @Test
    fun `a resume cancelled after IO failure cannot pause or notify a replacement request`() {
        val operation = Job()
        var paused = false
        var callbacks = 0
        val failure = runCatching<Unit> { throw IOException("Synthetic recovery write failure") }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFailsWith<CancellationException> {
            reportArchiveResumeFailure(
                operation,
                commit = { action ->
                    operation.cancel()
                    action()
                },
                pause = { paused = true },
                notifyFailure = { callbacks++ },
            )
        }
        assertFalse(paused)
        assertEquals(0, callbacks)
    }

    @Test
    fun `late success cannot reach a callback replaced before the commit lock`() {
        val operation = Job()
        val outcomes = mutableListOf<String>()
        var currentCallback: () -> Unit = { outcomes.add("original") }
        assertFailsWith<CancellationException> {
            commitArchiveResumeRequest(
                operation,
                commit = { action ->
                    currentCallback = { outcomes.add("replacement") }
                    operation.cancel()
                    action()
                },
                action = { currentCallback() },
            )
        }
        assertTrue(outcomes.isEmpty())
    }

    @Test
    fun `late document selection cannot open a picker or replace the selected request`() {
        val operation = Job()
        var pickerLaunches = 0
        var selectedRequest = "replacement"
        assertFailsWith<CancellationException> {
            commitArchiveResumeRequest(
                operation,
                commit = { action ->
                    operation.cancel()
                    action()
                },
                action = {
                    pickerLaunches++
                    selectedRequest = "original"
                },
            )
        }
        assertEquals(0, pickerLaunches)
        assertEquals("replacement", selectedRequest)
    }

    @Test
    fun `a live resume delivers its retained retry action inside the pause commit`() {
        val operation = Job()
        var commitActive = false
        var paused = false
        var callbacks = 0
        reportArchiveResumeFailure(
            operation,
            commit = { action ->
                commitActive = true
                try {
                    action()
                } finally {
                    commitActive = false
                }
            },
            pause = {
                assertTrue(commitActive)
                paused = true
            },
            notifyFailure = {
                assertTrue(commitActive)
                assertTrue(paused)
                callbacks++
            },
        )
        assertFalse(commitActive)
        assertEquals(1, callbacks)
    }

    @Test
    fun `cancellation during pause persistence propagates without a failure callback`() {
        var callbacks = 0
        assertFailsWith<CancellationException> {
            reportArchiveResumeFailure(
                Job(),
                commit = { it() },
                pause = { throw CancellationException("Resume cancelled") },
                notifyFailure = { callbacks++ },
            )
        }
        assertEquals(0, callbacks)
    }

    @Test
    fun `a pause write failure still delivers the retained retry action without escaping`() {
        var callbacks = 0
        reportArchiveResumeFailure(
            Job(),
            commit = { it() },
            pause = { throw IOException("Synthetic recovery pause failure") },
            notifyFailure = { callbacks++ },
        )
        assertEquals(1, callbacks)
    }

    @Test
    fun `an enqueue failure cannot notify a request that cancelled it before the commit lock`() {
        val operation = Job()
        var failures = 0
        assertFailsWith<CancellationException> {
            commitArchiveResumeRequest(
                operation,
                commit = { action ->
                    operation.cancel()
                    action()
                },
                action = { failures++ },
            )
        }
        assertEquals(0, failures)
    }

    @Test
    fun `a cancelled reconnect cannot begin old work or enqueue and complete retained work`() {
        val operation = Job()
        var activeWork = "newer-work"
        var enqueues = 0
        var completions = 0
        val beginOld: () -> Unit = { activeWork = "old-work" }
        val enqueueAndComplete: () -> Unit = {
            enqueues++
            completions++
        }
        for (action in listOf(beginOld, enqueueAndComplete)) {
            assertFailsWith<CancellationException> {
                commitArchiveResumeRequest(
                    operation,
                    commit = { guarded ->
                        operation.cancel()
                        guarded()
                    },
                    action = action,
                )
            }
        }
        assertEquals("newer-work", activeWork)
        assertEquals(0, enqueues)
        assertEquals(0, completions)
    }
}
