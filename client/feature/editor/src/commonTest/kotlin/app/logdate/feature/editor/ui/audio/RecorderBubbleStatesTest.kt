package app.logdate.feature.editor.ui.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RecorderBubbleStatesTest {
    @Test
    fun `the bubble draws at once from neutral values`() =
        runTest {
            val state =
                recorderBubbleStates(
                    elapsed = emptyFlow(),
                    levels = emptyFlow(),
                    paused = emptyFlow(),
                    transcript = emptyFlow(),
                    initiallyPaused = true,
                ).first()

            assertEquals(Duration.ZERO, state.elapsed)
            assertTrue(state.levels.isEmpty())
            assertTrue(state.paused)
            assertNull(state.transcript)
        }

    @Test
    fun `the waveform keeps only the latest levels`() =
        runTest {
            val state =
                recorderBubbleStates(
                    elapsed = emptyFlow(),
                    levels = flowOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f),
                    paused = emptyFlow(),
                    transcript = emptyFlow(),
                    initiallyPaused = false,
                    levelHistory = 3,
                ).last()

            assertEquals(listOf(0.3f, 0.4f, 0.5f), state.levels)
        }

    @Test
    fun `elapsed time and pause and transcript follow the recorder`() =
        runTest {
            val elapsed = MutableStateFlow(42.seconds)
            val paused = MutableStateFlow(true)
            val transcript = MutableStateFlow<String?>("Walked the long way home")

            val state =
                recorderBubbleStates(elapsed, emptyFlow(), paused, transcript, initiallyPaused = false)
                    .first { it.transcript != null && it.paused && it.elapsed > Duration.ZERO }

            assertEquals(42.seconds, state.elapsed)
            assertTrue(state.paused)
            assertEquals("Walked the long way home", state.transcript)
        }
}
