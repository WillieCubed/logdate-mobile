package app.logdate.feature.editor.ui.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaveCoordinatorTest {
    @Test
    fun `save waits for persistence before reporting saved`() =
        runTest {
            val persistenceGate = CompletableDeferred<Unit>()
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states)

            val save =
                backgroundScope.async {
                    coordinator.save(
                        content = "draft",
                        latestContent = { "draft" },
                        latestOnSave = {
                            {
                                persistenceGate.await()
                                true
                            }
                        },
                    )
                }
            runCurrent()

            assertEquals(AutoSaveStatus.SAVING, states.last().status)
            assertFalse(save.isCompleted)

            persistenceGate.complete(Unit)
            save.await()

            assertEquals(AutoSaveStatus.SAVED, states.last().status)
        }

    @Test
    fun `save retries failures with backoff until success`() =
        runTest {
            var calls = 0
            val callTimes = mutableListOf<Long>()
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states)

            val save =
                async {
                    coordinator.save(
                        content = "draft",
                        latestContent = { "draft" },
                        latestOnSave = {
                            {
                                calls++
                                callTimes += testScheduler.currentTime
                                if (calls < 3) error("local write failed")
                                true
                            }
                        },
                    )
                }

            advanceUntilIdle()
            save.await()

            assertEquals(3, calls)
            assertEquals(listOf(0L, 100L, 300L), callTimes)
            assertEquals(AutoSaveStatus.SAVED, states.last().status)
            assertEquals(2, states.last().saveAttempts)
        }

    @Test
    fun `save stops at configured attempt bound`() =
        runTest {
            var calls = 0
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states)

            coordinator.save(
                content = "draft",
                latestContent = { "draft" },
                latestOnSave = {
                    {
                        calls++
                        error("disk full")
                    }
                },
            )

            assertEquals(3, calls)
            assertEquals(AutoSaveStatus.ERROR, states.last().status)
            assertEquals(3, states.last().saveAttempts)
        }

    @Test
    fun `skipped save does not report saved or retry`() =
        runTest {
            var calls = 0
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states)

            coordinator.save(
                content = "draft",
                latestContent = { "draft" },
                latestOnSave = {
                    {
                        calls++
                        false
                    }
                },
            )

            assertEquals(1, calls)
            assertEquals(AutoSaveStatus.IDLE, states.last().status)
            assertEquals(0, states.last().saveAttempts)
            assertTrue(states.last().error == null)
        }

    @Test
    fun `periodic backup uses latest content and callback`() =
        runTest {
            var content = "old content"
            var callbackName = "old callback"
            val writes = mutableListOf<String>()

            val periodicJob =
                backgroundScope.launch {
                    runPeriodicBackups(
                        intervalMs = 100,
                        latestContent = { content },
                        latestOnSave = {
                            val name = callbackName
                            { value -> writes += "$name: $value" }
                        },
                    )
                }
            runCurrent()

            content = "latest content"
            callbackName = "latest callback"
            advanceTimeBy(100)
            runCurrent()

            assertEquals(listOf("latest callback: latest content"), writes)
            periodicJob.cancel()
        }

    @Test
    fun `save operations are serialized`() =
        runTest {
            val firstSaveGate = CompletableDeferred<Unit>()
            var latestContent = "first"
            var activeSaves = 0
            var maximumActiveSaves = 0
            val coordinator = coordinator()
            val persist: suspend (String) -> Boolean = { value ->
                activeSaves++
                maximumActiveSaves = maxOf(maximumActiveSaves, activeSaves)
                if (value == "first") firstSaveGate.await()
                activeSaves--
                true
            }

            val first =
                backgroundScope.async {
                    coordinator.save("first", { latestContent }, { persist })
                }
            runCurrent()
            latestContent = "second"
            val second =
                backgroundScope.async {
                    coordinator.save("second", { latestContent }, { persist })
                }
            runCurrent()

            assertEquals(1, activeSaves)
            firstSaveGate.complete(Unit)
            first.await()
            second.await()

            assertEquals(1, maximumActiveSaves)
        }

    @Test
    fun `stale completion does not mark newer content saved`() =
        runTest {
            val persistenceGate = CompletableDeferred<Unit>()
            var latestContent = "first"
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states)

            val save =
                backgroundScope.async {
                    coordinator.save(
                        content = "first",
                        latestContent = { latestContent },
                        latestOnSave = {
                            {
                                persistenceGate.await()
                                true
                            }
                        },
                    )
                }
            runCurrent()
            latestContent = "second"
            persistenceGate.complete(Unit)
            save.await()

            assertNotEquals(AutoSaveStatus.SAVED, states.last().status)
        }

    @Test
    fun `cancellation does not become error or retry`() =
        runTest {
            var calls = 0
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states)

            val failure =
                runCatching {
                    coordinator.save(
                        content = "draft",
                        latestContent = { "draft" },
                        latestOnSave = {
                            {
                                calls++
                                throw CancellationException("composition left")
                            }
                        },
                    )
                }.exceptionOrNull()

            assertIs<CancellationException>(failure)
            assertEquals(1, calls)
            assertEquals(AutoSaveStatus.IDLE, states.last().status)
            assertEquals(0, states.last().saveAttempts)
            assertTrue(states.last().error == null)
        }

    @Test
    fun `cancellation during retry backoff returns to neutral idle state`() =
        runTest {
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states)

            val save =
                backgroundScope.async {
                    coordinator.save(
                        content = "draft",
                        latestContent = { "draft" },
                        latestOnSave = { { error("local write failed") } },
                    )
                }
            runCurrent()

            assertEquals(AutoSaveStatus.SAVING, states.last().status)
            assertEquals(1, states.last().saveAttempts)
            save.cancel(CancellationException("editor closed during retry"))
            val failure = runCatching { save.await() }.exceptionOrNull()

            assertIs<CancellationException>(failure)
            assertEquals(AutoSaveStatus.IDLE, states.last().status)
            assertEquals(0, states.last().saveAttempts)
            assertTrue(states.last().error == null)
        }

    @Test
    fun `stale failure returns to idle without surfacing error`() =
        runTest {
            val persistenceGate = CompletableDeferred<Unit>()
            var latestContent = "first"
            val states = mutableListOf<AutoSaveState>()
            val coordinator = coordinator(states = states, maxSaveAttempts = 1)

            val save =
                backgroundScope.async {
                    coordinator.save(
                        content = "first",
                        latestContent = { latestContent },
                        latestOnSave = {
                            {
                                persistenceGate.await()
                                error("failed old snapshot")
                            }
                        },
                    )
                }
            runCurrent()
            latestContent = "second"
            persistenceGate.complete(Unit)
            save.await()

            assertEquals(AutoSaveStatus.IDLE, states.last().status)
            assertTrue(states.last().error == null)
        }

    @Test
    fun `editor draft fingerprint includes captions pending audio and journal selection`() {
        val blockId = Uuid.random()
        val image = ImageBlockUiState(id = blockId, uri = "image://one", caption = "first caption")
        val imageCaptionChanged = image.copy(caption = "second caption")
        assertNotEquals(
            getEditorDraftFingerprint(EditorState(blocks = listOf(image))),
            getEditorDraftFingerprint(EditorState(blocks = listOf(imageCaptionChanged))),
        )

        val audio =
            AudioBlockUiState(
                id = blockId,
                captureState = AudioCaptureState.Stopping(filePath = "/recordings/first.m4a"),
            )
        val audioPathChanged =
            audio.copy(captureState = AudioCaptureState.Stopping(filePath = "/recordings/second.m4a"))
        assertNotEquals(
            getEditorDraftFingerprint(EditorState(blocks = listOf(audio))),
            getEditorDraftFingerprint(EditorState(blocks = listOf(audioPathChanged))),
        )

        assertNotEquals(
            getEditorDraftFingerprint(EditorState()),
            getEditorDraftFingerprint(EditorState(selectedJournalIds = listOf(Uuid.random()))),
        )
    }

    private fun coordinator(
        states: MutableList<AutoSaveState> = mutableListOf(),
        maxSaveAttempts: Int = 3,
    ): AutoSaveCoordinator<String> =
        AutoSaveCoordinator(
            maxSaveAttempts = maxSaveAttempts,
            retryInitialDelayMs = 100,
            contentHash = { it },
            hasContentChanged = { content, lastHash -> content != lastHash },
            now = { 42L },
            onStateChange = states::add,
        )
}
