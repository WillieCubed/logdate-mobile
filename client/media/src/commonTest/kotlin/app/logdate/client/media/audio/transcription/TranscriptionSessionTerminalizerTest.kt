package app.logdate.client.media.audio.transcription

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptionSessionTerminalizerTest {
    @Test
    fun `a session accepts realtime progress until exactly one terminal result wins`() =
        runTest {
            val emitted = mutableListOf<TranscriptionResult>()
            val terminalizer = TranscriptionSessionTerminalizer { emitted.add(it) }

            terminalizer.begin()
            assertTrue(terminalizer.progress(TranscriptionResult.Success("hello")))
            assertTrue(
                terminalizer.complete(
                    TranscriptionResult.Success(
                        text = "hello world",
                        isFinal = true,
                    ),
                ),
            )
            assertFalse(terminalizer.fail(TranscriptionFailure.AudioError))

            assertEquals(
                listOf(
                    TranscriptionResult.InProgress,
                    TranscriptionResult.Success("hello"),
                    TranscriptionResult.Success("hello world", isFinal = true),
                ),
                emitted,
            )
        }

    @Test
    fun `cancelling an active session replaces replayed progress with a terminal cancellation`() =
        runTest {
            val emitted = mutableListOf<TranscriptionResult>()
            val terminalizer = TranscriptionSessionTerminalizer { emitted.add(it) }

            terminalizer.begin()
            assertTrue(terminalizer.cancel())
            assertFalse(terminalizer.progress(TranscriptionResult.Success("late result")))

            assertEquals(
                listOf(
                    TranscriptionResult.InProgress,
                    TranscriptionResult.Cancelled,
                ),
                emitted,
            )
        }

    @Test
    fun `a new session can begin after the previous session terminates`() =
        runTest {
            val emitted = mutableListOf<TranscriptionResult>()
            val terminalizer = TranscriptionSessionTerminalizer { emitted.add(it) }

            assertEquals(TranscriptionStartResult.Started, terminalizer.begin())
            assertEquals(TranscriptionStartResult.AlreadyRunning, terminalizer.begin())
            terminalizer.fail(TranscriptionFailure.NotAvailable)
            assertEquals(TranscriptionStartResult.Started, terminalizer.begin())

            assertEquals(TranscriptionResult.InProgress, emitted.last())
        }
}
