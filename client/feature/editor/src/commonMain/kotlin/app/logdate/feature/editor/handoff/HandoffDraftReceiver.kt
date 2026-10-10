package app.logdate.feature.editor.handoff

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Where an unfinished entry handed off from another device is, while the editor waits to open it. */
sealed interface HandoffDraftArrival {
    /** The draft is not on this device yet, and drafts are being fetched from the account. */
    data object Waiting : HandoffDraftArrival

    /** The draft is on this device, so the editor can open it. */
    data class Ready(
        val draftId: Uuid,
    ) : HandoffDraftArrival

    /** The draft did not arrive, for [reason]. */
    data class Missing(
        val reason: HandoffDraftMissingReason,
    ) : HandoffDraftArrival
}

/** Why a handed-off draft did not arrive. Each reason tells the person something different to do. */
sealed interface HandoffDraftMissingReason {
    /** Backup is off, signed out, or needs attention on this device, so drafts cannot reach it. */
    data object BackupUnavailable : HandoffDraftMissingReason

    /** Fetching drafts failed; [message] is the backup error. */
    data class FetchFailed(
        val message: String,
    ) : HandoffDraftMissingReason

    /** Drafts were fetched, but the other device has not uploaded this one. */
    data object NotUploaded : HandoffDraftMissingReason
}

/** What fetching drafts from the person's account did. */
sealed interface DraftRefreshResult {
    /** Drafts were fetched; any new ones are now on this device. */
    data object Refreshed : DraftRefreshResult

    /** Backup is off, signed out, or needs attention on this device, so nothing can be fetched. */
    data object Unavailable : DraftRefreshResult

    /** Fetching failed with [message]. */
    data class Failed(
        val message: String,
    ) : DraftRefreshResult
}

/** Fetches drafts started on the person's other devices into this one. */
fun interface DraftRefresher {
    suspend fun refresh(): DraftRefreshResult
}

/**
 * Waits for a draft handed off from another device to reach this one.
 *
 * Android Handoff opens the editor with the draft's id as soon as the person picks LogDate on the
 * new device, but the draft itself travels through backup, which may not have delivered it yet.
 * [arrival] opens the draft at once when it is already here; otherwise it fetches drafts every
 * [retryInterval] until the draft arrives or [timeout] runs out, and says why when it does not.
 */
class HandoffDraftReceiver(
    private val hasDraft: suspend (Uuid) -> Boolean,
    private val refresher: DraftRefresher,
    private val timeout: Duration = 60.seconds,
    private val retryInterval: Duration = 3.seconds,
) {
    fun arrival(draftId: Uuid): Flow<HandoffDraftArrival> =
        flow {
            if (hasDraft(draftId)) {
                emit(HandoffDraftArrival.Ready(draftId))
                return@flow
            }
            emit(HandoffDraftArrival.Waiting)
            var lastReason: HandoffDraftMissingReason = HandoffDraftMissingReason.NotUploaded

            suspend fun awaitDraft(): HandoffDraftArrival {
                while (true) {
                    when (val result = refresher.refresh()) {
                        DraftRefreshResult.Unavailable -> return HandoffDraftArrival.Missing(HandoffDraftMissingReason.BackupUnavailable)
                        is DraftRefreshResult.Failed -> lastReason = HandoffDraftMissingReason.FetchFailed(result.message)
                        DraftRefreshResult.Refreshed -> lastReason = HandoffDraftMissingReason.NotUploaded
                    }
                    if (hasDraft(draftId)) return HandoffDraftArrival.Ready(draftId)
                    delay(retryInterval)
                }
            }

            val arrival = withTimeoutOrNull(timeout) { awaitDraft() }
            emit(arrival ?: HandoffDraftArrival.Missing(lastReason))
        }
}
