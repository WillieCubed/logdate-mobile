package app.logdate.client.sync.space.migration

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A kind of journal data, as the move counts and reports it. */
@Serializable
enum class JournalDataKind {
    ENTRIES,
    JOURNALS,
    MEMBERSHIPS,
    DRAFTS,
    MOMENTS,
    LOCATION_HISTORY,
    MEDIA,
}

/**
 * Why moving the journal stopped. Each says what is wrong in terms of the person's own data and
 * what fixes it; none of them changes anything, because the account stays where it was.
 */
@Serializable
sealed interface JournalMoveProblem {
    /** The server holds [count] items of [kind] this device doesn't have yet. Finishing a sync fixes it. */
    @Serializable
    data class MissingOnThisDevice(
        val kind: JournalDataKind,
        val count: Int,
    ) : JournalMoveProblem

    /** This device has [count] items of [kind] that never reached the server. Syncing them first fixes it. */
    @Serializable
    data class NotOnServer(
        val kind: JournalDataKind,
        val count: Int,
    ) : JournalMoveProblem

    /** The new copy of [count] items of [kind] didn't match this device's after it was uploaded again. */
    @Serializable
    data class CopyDiffers(
        val kind: JournalDataKind,
        val count: Int,
    ) : JournalMoveProblem

    /** The account has [kind] that this device doesn't sync, so it can't check them. Another device can. */
    @Serializable
    data class NotSyncedHere(
        val kind: JournalDataKind,
    ) : JournalMoveProblem

    /** [count] media files are neither on this device nor readable on the server. */
    @Serializable
    data class MediaUnavailable(
        val count: Int,
    ) : JournalMoveProblem

    /** Another of the person's devices is moving the journal. */
    @Serializable
    data object AnotherDeviceMoving : JournalMoveProblem

    /** The account's storage is full. */
    @Serializable
    data object StorageFull : JournalMoveProblem

    /** This device doesn't have the account's identity key, so it can't seal the journal. */
    @Serializable
    data object IdentityKeyMissing : JournalMoveProblem

    /** The server couldn't be reached. */
    @Serializable
    data object Offline : JournalMoveProblem

    /** The server refused the move with [code]; trying again later may help. */
    @Serializable
    data class ServerRefused(
        val code: String,
    ) : JournalMoveProblem

    /** The previous copy is no longer kept, so the journal can't be switched back. */
    @Serializable
    data object SwitchBackWindowClosed : JournalMoveProblem
}

/** Where this device's move of the signed-in account's journal into its personal space stands. */
sealed interface JournalMoveState {
    /** Nothing to show: the move is off in this build, the server doesn't offer it, or nobody is signed in. */
    data object NotOffered : JournalMoveState

    /** The move starts once this device has finished syncing everything. */
    data object WaitingForSync : JournalMoveState

    /** Sealing and uploading the journal: [done] of [total] items. */
    data class Uploading(
        val done: Int,
        val total: Int,
    ) : JournalMoveState

    /** Reading the new copy back and comparing it with this device's and the server's. */
    data object Checking : JournalMoveState

    /** The journal is in the personal space. Until [previousCopyKeptUntil] it can be switched back. */
    data class Moved(
        val previousCopyKeptUntil: Instant?,
    ) : JournalMoveState

    /** Downloading the personal space and switching the journal back. */
    data object SwitchingBack : JournalMoveState

    /** The journal was switched back to the previous storage. */
    data object SwitchedBack : JournalMoveState

    /** The move stopped with [problem]; the account stays where it was. It tries again on its own at [retryAt], or now. */
    data class Stopped(
        val problem: JournalMoveProblem,
        val retryAt: Instant?,
    ) : JournalMoveState
}

/** What the person can see and do about moving their journal. */
interface JournalStorageMove {
    val state: StateFlow<JournalMoveState>

    /** Tries the move again now, after it stopped. */
    suspend fun retry()

    /** Switches the journal back to the previous storage while the previous copy is kept. */
    suspend fun switchBack()

    /** Reads where the move stands, without changing anything. */
    suspend fun refresh()

    companion object {
        /** A build that never moves journals. */
        val Off: JournalStorageMove =
            object : JournalStorageMove {
                override val state: StateFlow<JournalMoveState> =
                    kotlinx.coroutines.flow.MutableStateFlow(JournalMoveState.NotOffered)

                override suspend fun retry() = Unit

                override suspend fun switchBack() = Unit

                override suspend fun refresh() = Unit
            }
    }
}
