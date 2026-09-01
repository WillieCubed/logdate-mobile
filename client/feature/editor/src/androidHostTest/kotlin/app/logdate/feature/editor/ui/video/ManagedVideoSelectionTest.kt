package app.logdate.feature.editor.ui.video

import app.logdate.feature.editor.ui.media.ManagedMediaSelectionController
import app.logdate.feature.editor.ui.media.ManagedMediaSelectionState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ManagedVideoSelectionTest {
    @Test
    fun `duration and editor callback receive only the managed uri`() =
        runTest {
            val transientUri = "content://picker/video/transient"
            val managedUri = "content://media/external/video/media/42"
            val durationRequests = mutableListOf<String>()
            val publishedSelections = mutableListOf<Pair<String, Long>>()
            val controller =
                ManagedMediaSelectionController { selectedUri ->
                    assertEquals(transientUri, selectedUri)
                    managedUri
                }

            controller.selectPreparedAndTransfer(
                sourceUri = transientUri,
                prepareManagedMedia = { uri ->
                    durationRequests += uri
                    ManagedVideoSelection(uri, 84_000L)
                },
                transferOwnership = { selection ->
                    publishedSelections += selection.uri to selection.durationMs
                },
            )

            assertEquals(listOf(managedUri), durationRequests)
            assertEquals(listOf(managedUri to 84_000L), publishedSelections)
        }

    @Test
    fun `editor callback runs inside the import transaction`() =
        runTest {
            val statesObservedByCallback = mutableListOf<ManagedMediaSelectionState>()
            val controller = ManagedMediaSelectionController { "content://media/video/managed" }

            controller.selectPreparedAndTransfer(
                sourceUri = "content://picker/video/transient",
                prepareManagedMedia = { uri -> ManagedVideoSelection(uri, 2_000L) },
                transferOwnership = {
                    statesObservedByCallback += controller.state.value
                },
            )

            assertEquals(
                listOf<ManagedMediaSelectionState>(ManagedMediaSelectionState.Importing),
                statesObservedByCallback,
            )
            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
        }

    @Test
    fun `callback failure before acceptance discards the managed copy`() =
        runTest {
            val discardedManagedUris = mutableListOf<String>()
            val controller =
                ManagedMediaSelectionController(
                    importMedia = { "content://media/external/video/media/owned" },
                    discardManagedMedia = discardedManagedUris::add,
                )

            controller.selectPreparedAndTransfer(
                sourceUri = "content://picker/video/transient",
                prepareManagedMedia = { uri -> ManagedVideoSelection(uri, 1_000L) },
                transferOwnership = { error("Editor rejected the attachment") },
            )

            assertEquals(ManagedMediaSelectionState.Failed, controller.state.value)
            assertEquals(listOf("content://media/external/video/media/owned"), discardedManagedUris)
        }
}
