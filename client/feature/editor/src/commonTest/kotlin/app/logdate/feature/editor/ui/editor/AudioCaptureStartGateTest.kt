package app.logdate.feature.editor.ui.editor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_recording_not_finalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AudioCaptureStartGateTest {
    @Test
    fun backBoundaryUsesTheSynchronousOwnerBeforeCombinedUiStateEmits() {
        val audio = AudioBlockUiState()
        val rendered = EditorState(blocks = listOf(audio, TextBlockUiState()))
        val state = MutableStateFlow(rendered)
        val gate = EditorAudioCaptureStartGate(state) { rendered }
        var exits = 0
        val token = assertNotNull(gate.claim(audio.id))
        assertTrue(rendered.audioCaptureStartReservations.isEmpty(), "The rendered combined snapshot deliberately lags the claim")
        gate.runWhenIdle { exits++ }
        assertEquals(0, exits, "System Back must not navigate away before the claimed start publishes ownership")
        gate.release(audio.id, token)
        gate.runWhenIdle { exits++ }
        assertEquals(1, exits)
    }

    @Test
    fun startReservesTheLatestBlockBeforeCoroutineAdmissionWithoutPretendingToRecord() =
        runTest {
            val block = AudioBlockUiState()
            val original = EditorState(blocks = listOf(block), expandedBlockId = block.id)
            val state = MutableStateFlow(original)
            val gate = EditorAudioCaptureStartGate(state)
            val pending = CompletableDeferred<Unit>()
            var starts = 0
            val job =
                assertNotNull(
                    startReservedAudioCapture(gate, block.id) {
                        starts++
                        launch(StandardTestDispatcher(testScheduler)) { pending.await() }
                    },
                )
            assertEquals(1, starts)
            assertNotNull(state.value.audioCaptureStartReservations[block.id])
            assertSame(block, state.value.blocks.single())
            assertEquals(AudioCaptureState.Empty, block.captureState)
            assertFalse(state.value.isRecordingAudio)
            assertFalse(state.value.isDirty)
            assertEquals(original.contentRevision, state.value.contentRevision)
            assertNull(startReservedAudioCapture(gate, block.id) { error("A second request must not launch") })
            pending.complete(Unit)
            job.join()
            assertTrue(state.value.audioCaptureStartReservations.isEmpty())
        }

    @Test
    fun cancellationBeforeCoroutineRunsReleasesReservationAndAllowsRetry() =
        runTest {
            val block = AudioBlockUiState()
            val state = MutableStateFlow(EditorState(blocks = listOf(block)))
            val gate = EditorAudioCaptureStartGate(state)
            var ran = false
            val job =
                assertNotNull(
                    startReservedAudioCapture(gate, block.id) {
                        launch(StandardTestDispatcher(testScheduler)) { ran = true }
                    },
                )
            job.cancel()
            runCurrent()
            assertFalse(ran)
            assertTrue(state.value.audioCaptureStartReservations.isEmpty())
            assertEquals(AudioCaptureState.Empty, (state.value.blocks.single() as AudioBlockUiState).captureState)
            assertNotNull(gate.claim(block.id))
        }

    @Test
    fun staleCompletionCannotReleaseANewerAttemptEvenWhileEditorBecomesLocked() {
        val block = AudioBlockUiState()
        val state = MutableStateFlow(EditorState(blocks = listOf(block)))
        val gate = EditorAudioCaptureStartGate(state)
        val first = assertNotNull(gate.claim(block.id))
        gate.release(block.id, first)
        val second = assertNotNull(gate.claim(block.id))
        assertNotEquals(first, second)
        state.value = state.value.copy(isSaving = true, isEditingLocked = true)
        gate.release(block.id, first)
        assertEquals(second, state.value.audioCaptureStartReservations[block.id])
        gate.release(block.id, second)
        assertTrue(state.value.audioCaptureStartReservations.isEmpty())
        assertTrue(state.value.isSaving)
    }

    @Test
    fun missingGateAndRejectedClaimsNeverInvokeCapture() {
        val block = AudioBlockUiState()
        assertNull(startReservedAudioCapture(null, block.id) { error("No production fallback") })
        val failures =
            listOf(
                EditorState(),
                EditorState(blocks = listOf(TextBlockUiState(id = block.id))),
                EditorState(blocks = listOf(block), isLoading = true),
                EditorState(blocks = listOf(block), isSaving = true),
                EditorState(blocks = listOf(block), isEditingLocked = true),
                EditorState(blocks = listOf(block), shouldExit = true),
                EditorState(blocks = listOf(block), readOnlyBlocks = mapOf(block.id to true)),
                EditorState(blocks = listOf(block), audioCaptureStartReservations = mapOf(Uuid.random() to Uuid.random())),
                EditorState(blocks = listOf(block, AudioBlockUiState(captureState = AudioCaptureState.Recording()))),
            ) +
                listOf(
                    AudioCaptureState.Recording(),
                    AudioCaptureState.Stopping(),
                    AudioCaptureState.Ready("file:///kept.aac", 1200),
                    AudioCaptureState.Failed(Res.string.editor_error_recording_not_finalized, "file:///kept.aac"),
                ).map { EditorState(blocks = listOf(block.copy(captureState = it))) }
        for (state in failures) {
            val flow = MutableStateFlow(state)
            assertNull(startReservedAudioCapture(EditorAudioCaptureStartGate(flow), block.id) { error("Rejected capture must not launch") })
            assertSame(state, flow.value)
        }
    }

    @Test
    fun combinedLoadingAndReadOnlyStateAreUsedWithoutTrustingTheStaleMutableLoadingFlag() {
        val block = AudioBlockUiState()
        val state = MutableStateFlow(EditorState(blocks = listOf(block), isLoading = true))
        var observed = state.value.copy(isLoading = false, readOnlyBlocks = mapOf(block.id to true))
        val gate = EditorAudioCaptureStartGate(state) { observed }
        assertNull(gate.claim(block.id))
        observed = observed.copy(readOnlyBlocks = emptyMap())
        assertNotNull(gate.claim(block.id))
        assertTrue(state.value.isLoading, "Reservation must not rewrite observed-data loading policy")
    }

    @Test
    fun aSynchronousLaunchFailureReleasesOnlyItsReservationAndPropagatesTheFailure() {
        val block = AudioBlockUiState()
        val state = MutableStateFlow(EditorState(blocks = listOf(block)))
        assertFailsWith<IllegalStateException> {
            startReservedAudioCapture(EditorAudioCaptureStartGate(state), block.id) { error("Launch refused") }
        }
        assertTrue(state.value.audioCaptureStartReservations.isEmpty())
    }

    @Test
    fun reservationChangesParticipateInStateEqualityAndCopiesWithoutChangingDurableInput() {
        val block = AudioBlockUiState(caption = "Keep this caption")
        val original = EditorState(blocks = listOf(block), contentRevision = 17)
        val reserved = original.copy(audioCaptureStartReservations = mapOf(block.id to Uuid.random()))
        assertNotEquals(original, reserved)
        assertEquals(reserved, reserved.copy())
        assertEquals(reserved.hashCode(), reserved.copy().hashCode())
        assertSame(original.blocks, reserved.blocks)
        assertEquals(17L, reserved.contentRevision)
    }
}
