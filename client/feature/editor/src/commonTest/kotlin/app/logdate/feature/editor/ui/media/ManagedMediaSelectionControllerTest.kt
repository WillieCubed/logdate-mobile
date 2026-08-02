package app.logdate.feature.editor.ui.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedMediaSelectionControllerTest {
    @Test
    fun `failed import never returns the transient source URI and remains retryable`() =
        runTest {
            val sourceUri = "content://picker/images/transient"
            val controller =
                ManagedMediaSelectionController { uri ->
                    error("Lost access to $uri")
                }
            val publishedUris = mutableListOf<String>()

            controller.select(sourceUri)?.let(publishedUris::add)

            assertEquals(ManagedMediaSelectionState.Failed, controller.state.value)
            assertEquals(emptyList(), publishedUris)
            assertNull(controller.retry())
            assertEquals(ManagedMediaSelectionState.Failed, controller.state.value)
        }

    @Test
    fun `retry publishes only the managed URI after a prior failure`() =
        runTest {
            val sourceUri = "content://picker/videos/transient"
            val managedUri = "content://media/external/video/media/9001"
            var attemptCount = 0
            val controller =
                ManagedMediaSelectionController { uri ->
                    assertEquals(sourceUri, uri)
                    attemptCount++
                    if (attemptCount == 1) error("Temporary import failure")
                    managedUri
                }
            val publishedUris = mutableListOf<String>()

            controller.select(sourceUri)?.let(publishedUris::add)
            controller.retry()?.let(publishedUris::add)

            assertEquals(listOf(managedUri), publishedUris)
            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
            assertEquals(2, attemptCount)
        }

    @Test
    fun `picker cancellation remains idle and publishes nothing`() =
        runTest {
            var importCount = 0
            val controller =
                ManagedMediaSelectionController {
                    importCount++
                    "managed://unexpected"
                }
            val publishedUris = mutableListOf<String>()

            controller.cancel()

            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
            assertEquals(0, importCount)
            assertEquals(emptyList(), publishedUris)
            assertNull(controller.retry())
        }

    @Test
    fun `composition cancellation clears importing state and cannot be retried`() =
        runTest {
            val importStarted = CompletableDeferred<Unit>()
            val controller =
                ManagedMediaSelectionController {
                    importStarted.complete(Unit)
                    awaitCancellation()
                }

            val importJob = launch { controller.select("content://picker/images/cancelled") }
            importStarted.await()

            assertEquals(ManagedMediaSelectionState.Importing, controller.state.value)

            importJob.cancelAndJoin()

            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
            assertNull(controller.retry())
        }

    @Test
    fun `managed media preparation remains importing until metadata is ready`() =
        runTest {
            val preparationStarted = CompletableDeferred<Unit>()
            val controller = ManagedMediaSelectionController { "content://media/images/ready" }

            val selectionJob =
                launch {
                    controller.selectPrepared("content://picker/images/source") {
                        preparationStarted.complete(Unit)
                        awaitCancellation()
                    }
                }
            preparationStarted.await()

            assertEquals(ManagedMediaSelectionState.Importing, controller.state.value)

            selectionJob.cancelAndJoin()
        }

    @Test
    fun `metadata failure discards managed copy and remains retryable`() =
        runTest {
            val sourceUri = "content://picker/videos/metadata-failure"
            val managedUris = ArrayDeque(listOf("content://media/video/first", "content://media/video/retry"))
            val discardedManagedUris = mutableListOf<String>()
            val controller =
                ManagedMediaSelectionController(
                    importMedia = { managedUris.removeFirst() },
                    discardManagedMedia = discardedManagedUris::add,
                )

            val firstAttempt =
                runCatching {
                    controller.selectPrepared(sourceUri) {
                        error("Duration metadata is unreadable")
                    }
                }

            assertTrue(firstAttempt.isSuccess)
            assertNull(firstAttempt.getOrThrow())
            assertEquals(ManagedMediaSelectionState.Failed, controller.state.value)
            assertEquals(listOf("content://media/video/first"), discardedManagedUris)

            val retry =
                controller.retryPrepared { managedUri ->
                    managedUri to 42_000L
                }

            assertEquals("content://media/video/retry" to 42_000L, retry)
            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
        }

    @Test
    fun `ownership transfer callback runs before importing state closes`() =
        runTest {
            val statesObservedByCallback = mutableListOf<ManagedMediaSelectionState>()
            val discardedManagedUris = mutableListOf<String>()
            val controller =
                ManagedMediaSelectionController(
                    importMedia = { "content://media/video/owned" },
                    discardManagedMedia = discardedManagedUris::add,
                )

            controller.selectPreparedAndTransfer(
                sourceUri = "content://picker/video/source",
                prepareManagedMedia = { managedUri -> managedUri to 12_000L },
                transferOwnership = { _: Pair<String, Long> ->
                    statesObservedByCallback += controller.state.value
                },
            )

            assertEquals(
                listOf<ManagedMediaSelectionState>(ManagedMediaSelectionState.Importing),
                statesObservedByCallback,
            )
            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
            assertEquals(emptyList(), discardedManagedUris)
        }

    @Test
    fun `failed ownership callback discards managed media and remains retryable`() =
        runTest {
            val discardedManagedUris = mutableListOf<String>()
            val importedUris = ArrayDeque(listOf("content://media/video/first", "content://media/video/retry"))
            val controller =
                ManagedMediaSelectionController(
                    importMedia = { importedUris.removeFirst() },
                    discardManagedMedia = discardedManagedUris::add,
                )

            controller.selectPreparedAndTransfer(
                sourceUri = "content://picker/video/source",
                prepareManagedMedia = { it },
                transferOwnership = { error("Editor rejected the attachment") },
            )

            assertEquals(ManagedMediaSelectionState.Failed, controller.state.value)
            assertEquals(listOf("content://media/video/first"), discardedManagedUris)
            assertEquals("content://media/video/retry", controller.retry())
            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
        }

    @Test
    fun `rapid second selection is rejected while the first import owns admission`() =
        runTest {
            val firstImportStarted = CompletableDeferred<Unit>()
            val releaseFirstImport = CompletableDeferred<Unit>()
            val importedSources = mutableListOf<String>()
            val preparedUris = mutableListOf<String>()
            val transferredUris = mutableListOf<String>()
            val controller =
                ManagedMediaSelectionController(
                    importMedia = { sourceUri ->
                        importedSources += sourceUri
                        if (importedSources.size == 1) {
                            firstImportStarted.complete(Unit)
                            releaseFirstImport.await()
                        }
                        "file:///managed/${sourceUri.substringAfterLast('/')}"
                    },
                    discardManagedMedia = { error("A rejected selection must not own media") },
                )

            val firstSelection =
                launch {
                    controller.selectPreparedAndTransfer(
                        sourceUri = "content://picker/images/first",
                        prepareManagedMedia = { managedUri ->
                            preparedUris += managedUri
                            managedUri
                        },
                        transferOwnership = transferredUris::add,
                    )
                }
            firstImportStarted.await()
            val secondSelection =
                launch {
                    controller.selectPreparedAndTransfer(
                        sourceUri = "content://picker/images/second",
                        prepareManagedMedia = { managedUri ->
                            preparedUris += managedUri
                            managedUri
                        },
                        transferOwnership = transferredUris::add,
                    )
                }

            releaseFirstImport.complete(Unit)
            joinAll(firstSelection, secondSelection)

            assertEquals(listOf("content://picker/images/first"), importedSources)
            assertEquals(listOf("file:///managed/first"), preparedUris)
            assertEquals(listOf("file:///managed/first"), transferredUris)
            assertEquals(ManagedMediaSelectionState.Idle, controller.state.value)
        }

    @Test
    fun `retry and cancel are ignored while an import owns admission`() =
        runTest {
            val firstImportStarted = CompletableDeferred<Unit>()
            val releaseFirstImport = CompletableDeferred<Unit>()
            val importedSources = mutableListOf<String>()
            val controller =
                ManagedMediaSelectionController { sourceUri ->
                    importedSources += sourceUri
                    firstImportStarted.complete(Unit)
                    releaseFirstImport.await()
                    error("First import failed")
                }

            val selection = launch { controller.select("content://picker/images/first") }
            firstImportStarted.await()
            val retry = launch { controller.retry() }
            val cancel = launch { controller.cancel() }

            releaseFirstImport.complete(Unit)
            joinAll(selection, retry, cancel)

            assertEquals(listOf("content://picker/images/first"), importedSources)
            assertEquals(ManagedMediaSelectionState.Failed, controller.state.value)
        }
}
