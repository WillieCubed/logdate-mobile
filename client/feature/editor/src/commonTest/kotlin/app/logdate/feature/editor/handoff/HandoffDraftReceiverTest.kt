package app.logdate.feature.editor.handoff

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class HandoffDraftReceiverTest {
    private val draftId = Uuid.random()

    @Test
    fun `a draft already on this device opens at once`() =
        runTest {
            val receiver =
                HandoffDraftReceiver(
                    hasDraft = { it == draftId },
                    refresher = { error("Nothing needs fetching") },
                )

            assertEquals(listOf(HandoffDraftArrival.Ready(draftId)), receiver.arrival(draftId).toList())
        }

    @Test
    fun `the editor waits while drafts are fetched, then opens the one that arrived`() =
        runTest {
            var fetches = 0
            val receiver =
                HandoffDraftReceiver(
                    hasDraft = { fetches >= 2 },
                    refresher = {
                        fetches++
                        DraftRefreshResult.Refreshed
                    },
                )

            assertEquals(
                listOf(HandoffDraftArrival.Waiting, HandoffDraftArrival.Ready(draftId)),
                receiver.arrival(draftId).toList(),
            )
            assertEquals(2, fetches)
        }

    @Test
    fun `without backup on this device the draft can never arrive, so the editor says so at once`() =
        runTest {
            var fetches = 0
            val receiver =
                HandoffDraftReceiver(
                    hasDraft = { false },
                    refresher = {
                        fetches++
                        DraftRefreshResult.Unavailable
                    },
                )

            assertEquals(
                listOf(HandoffDraftArrival.Waiting, HandoffDraftArrival.Missing(HandoffDraftMissingReason.BackupUnavailable)),
                receiver.arrival(draftId).toList(),
            )
            assertEquals(1, fetches)
        }

    @Test
    fun `a draft that never uploads is reported once the wait runs out`() =
        runTest {
            val receiver =
                HandoffDraftReceiver(
                    hasDraft = { false },
                    refresher = { DraftRefreshResult.Refreshed },
                    timeout = 30.seconds,
                    retryInterval = 5.seconds,
                )

            assertEquals(
                listOf(HandoffDraftArrival.Waiting, HandoffDraftArrival.Missing(HandoffDraftMissingReason.NotUploaded)),
                receiver.arrival(draftId).toList(),
            )
        }

    @Test
    fun `the last fetch error is the reason given when the wait runs out`() =
        runTest {
            val receiver =
                HandoffDraftReceiver(
                    hasDraft = { false },
                    refresher = { DraftRefreshResult.Failed("The server could not be reached") },
                    timeout = 30.seconds,
                    retryInterval = 5.seconds,
                )

            assertEquals(
                listOf(
                    HandoffDraftArrival.Waiting,
                    HandoffDraftArrival.Missing(HandoffDraftMissingReason.FetchFailed("The server could not be reached")),
                ),
                receiver.arrival(draftId).toList(),
            )
        }
}
