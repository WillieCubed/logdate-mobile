package app.logdate.feature.editor.ui.camera

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the synchronization logic for waiting for a fresh camera preview stream.
 *
 * This suite validates [CameraPreviewStreamingGate]'s ability to handle already-streaming
 * states, detect fresh false-to-true cycles, and support caller-defined timeouts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraPreviewStreamingGateTest {
    @Test
    fun `already streaming waits for a fresh false to true cycle`() =
        runTest {
            val previewStreaming = MutableStateFlow(true)
            var completed = false

            val job =
                launch {
                    awaitFreshPreviewStreaming(previewStreaming)
                    completed = true
                }

            runCurrent()
            assertFalse(completed)

            previewStreaming.value = false
            advanceUntilIdle()
            assertFalse(completed)

            previewStreaming.value = true
            advanceUntilIdle()
            assertTrue(completed)

            job.cancel()
        }

    @Test
    fun `idle preview completes when streaming starts`() =
        runTest {
            val previewStreaming = MutableStateFlow(false)
            var completed = false

            val job =
                launch {
                    awaitFreshPreviewStreaming(previewStreaming)
                    completed = true
                }

            runCurrent()
            assertFalse(completed)

            previewStreaming.value = true
            advanceUntilIdle()
            assertTrue(completed)

            job.cancel()
        }

    @Test
    fun `caller can time out if preview never restarts`() =
        runTest {
            val previewStreaming = MutableStateFlow(true)

            val result =
                async {
                    withTimeoutOrNull(1_000) {
                        awaitFreshPreviewStreaming(previewStreaming)
                    }
                }

            runCurrent()
            advanceTimeBy(1_000)
            advanceUntilIdle()

            assertNull(result.await())
        }
}
