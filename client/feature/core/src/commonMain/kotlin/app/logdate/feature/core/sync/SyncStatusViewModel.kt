package app.logdate.feature.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteType
import app.logdate.client.sync.BackupRequestState
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.QueuedUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.textContent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** How many items of a group the sheet names by title or snippet before it just says "N more". */
internal const val SYNC_STATUS_PREVIEW_LIMIT = 3

/** A queued item's title or snippet is trimmed to this many characters, then ellipsized. */
private const val PREVIEW_LABEL_MAX_LENGTH = 60

/** Kinds a title or snippet can be looked up for. Associations, media, and health have none. */
private val PREVIEWABLE_KINDS = setOf(EntityType.JOURNAL, EntityType.NOTE, EntityType.DRAFT)

/**
 * Backs the backup status sheet: what is waiting to upload, grouped by kind, and why it is not
 * moving if something is holding it back.
 */
class SyncStatusViewModel(
    private val syncManager: SyncManager,
    syncMetadataService: SyncMetadataService,
    private val sessionStorage: SessionStorage,
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
) : ViewModel() {
    private val queueReadFailed = MutableStateFlow(false)

    val uiState: StateFlow<SyncStatusUiState> =
        combine(
            syncManager.syncStatusFlow,
            syncMetadataService
                .observePendingUploads()
                .catch { error ->
                    Napier.e("Could not read the backup queue", error)
                    queueReadFailed.value = true
                    emit(emptyList())
                },
            syncManager.observeDeadLetters().map { it.size },
            queueReadFailed,
        ) { status, queue, failedCount, readFailed ->
            val state = buildSyncStatusUiState(status, queue, failedCount, readFailed)
            state.copy(groups = state.groups.map { it.withResolvedPreviews() })
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SyncStatusUiState(),
        )

    private val _feedback = MutableStateFlow<SyncStatusFeedback?>(null)
    val feedback: StateFlow<SyncStatusFeedback?> = _feedback.asStateFlow()

    init {
        viewModelScope.launch {
            syncManager.syncStatusFlow.collect { status ->
                if ((status.requestState != BackupRequestState.NONE || status.isSyncing) &&
                    _feedback.value == SyncStatusFeedback.Requested
                ) {
                    _feedback.value = null
                }
            }
        }
    }

    /**
     * Asks the platform to back up now, and says so. Before this, tapping the timeline's backup
     * indicator queued a sync with nothing on screen changing, which read as the tap being ignored.
     */
    fun syncNow() {
        viewModelScope.launch {
            if (sessionStorage.getSession() == null) {
                _feedback.value = SyncStatusFeedback.NeedsAccount
                return@launch
            }
            val request = runCatching { syncManager.requestBackup() }
            if (request.isSuccess) {
                _feedback.value =
                    SyncStatusFeedback.Requested.takeIf {
                        syncManager.syncStatusFlow.value.requestState == BackupRequestState.NONE &&
                            !syncManager.syncStatusFlow.value.isSyncing
                    }
            } else {
                Napier.e("Could not start a backup", request.exceptionOrNull())
                _feedback.value = SyncStatusFeedback.CouldNotStart
            }
        }
    }

    /**
     * Looks up a title or snippet for each of [QueuedGroup.previewIds], the small capped set the
     * sheet actually shows -- never the whole queue, so this stays cheap on every tick.
     */
    private suspend fun QueuedGroup.withResolvedPreviews(): QueuedGroup {
        if (previewIds.isEmpty()) return this
        val previews = previewIds.mapNotNull { id -> resolvePreview(kind, id) }
        return copy(previews = previews)
    }

    /**
     * `null` when the item itself can't be found any more -- e.g. deleted locally after it was
     * queued -- in which case the sheet simply shows one fewer preview line for that group rather
     * than a stale or broken one.
     */
    private suspend fun resolvePreview(
        kind: EntityType?,
        entityId: String,
    ): QueuedItemPreview? {
        val uid = runCatching { Uuid.parse(entityId) }.getOrNull() ?: return null
        return runCatching {
            when (kind) {
                EntityType.JOURNAL ->
                    journalRepository.getJournalById(uid)?.let { journal ->
                        QueuedItemPreview(entityId, journal.title.toPreviewLabelOrNull())
                    }
                EntityType.DRAFT ->
                    journalRepository.getDraft(uid)?.let { draft ->
                        QueuedItemPreview(entityId, draft.textContent().toPreviewLabelOrNull())
                    }
                EntityType.NOTE ->
                    journalNotesRepository.getNoteById(uid)?.let { note ->
                        QueuedItemPreview(entityId, note.explicitLabelOrNull(), note.type)
                    }
                else -> null
            }
        }.onFailure { error ->
            Napier.w("Could not look up a title for the backup status sheet", error)
        }.getOrNull()
    }
}

/** What the sheet says after "Back up now" is tapped. */
enum class SyncStatusFeedback {
    Requested,
    NeedsAccount,
    CouldNotStart,
}

data class SyncStatusUiState(
    val isSyncing: Boolean = false,
    val requestState: BackupRequestState = BackupRequestState.NONE,
    val backgroundWorkLimited: Boolean = false,
    val completedInRun: Int = 0,
    val totalForRun: Int? = null,
    val pendingCount: Int = 0,
    val pausedReason: SyncPausedReason? = null,
    val lastSyncTime: Instant? = null,
    /** The last attempt failed and will be retried. */
    val lastAttemptFailed: Boolean = false,
    val groups: List<QueuedGroup> = emptyList(),
    /** Items that used up their retries and need a decision in Sync issues. */
    val failedCount: Int = 0,
    /** The queue itself could not be read, so [groups] is not a real answer. */
    val queueUnavailable: Boolean = false,
    /**
     * Records on the server this device cannot read and has no local copy to repair from. There
     * is nothing to retry here -- only entering the recovery phrase for the identity that wrote
     * them can bring them back.
     */
    val unreadableCloudCount: Int = 0,
)

/** Everything of one [kind] waiting to upload. [kind] is null for types this build doesn't know. */
data class QueuedGroup(
    val kind: EntityType?,
    val count: Int,
    /** How many of [count] have already failed at least once and are waiting to retry. */
    val retrying: Int,
    /**
     * Ids of up to [SYNC_STATUS_PREVIEW_LIMIT] items in this group, oldest-queued first. Empty
     * for a [kind] with nothing to show a title for -- see [PREVIEWABLE_KINDS].
     */
    val previewIds: List<String> = emptyList(),
    /**
     * [previewIds] resolved to a short label, filled in asynchronously after the group is built.
     * Empty until resolved, and always empty when [previewIds] is.
     */
    val previews: List<QueuedItemPreview> = emptyList(),
)

/**
 * One queued item's title or snippet, so "3 entries waiting" can also say which three.
 *
 * [label] is null when the item itself has no title or snippet to show -- a voice note has
 * neither a caption nor a transcript here -- in which case the sheet shows a generic label for
 * [noteType] instead.
 */
data class QueuedItemPreview(
    val entityId: String,
    val label: String?,
    val noteType: NoteType? = null,
)

internal fun buildSyncStatusUiState(
    status: SyncStatus,
    queue: List<QueuedUpload>,
    failedCount: Int,
    queueUnavailable: Boolean,
): SyncStatusUiState =
    SyncStatusUiState(
        isSyncing = status.isSyncing,
        requestState = status.requestState,
        backgroundWorkLimited = status.backgroundWorkLimited,
        completedInRun = status.completedInRun,
        totalForRun = status.totalForRun,
        // The live queue is the source of truth for what is waiting; the status snapshot's count
        // only refreshes on sync transitions.
        pendingCount = if (queueUnavailable) status.pendingUploads else queue.size,
        pausedReason = status.pausedReason,
        lastSyncTime = status.lastSyncTime,
        lastAttemptFailed = status.lastError != null || status.requestState == BackupRequestState.FAILED,
        groups =
            queue
                .groupBy { it.entityType }
                .map { (kind, items) ->
                    QueuedGroup(
                        kind = kind,
                        count = items.size,
                        retrying = items.count { it.retryCount > 0 },
                        previewIds =
                            if (kind != null && kind in PREVIEWABLE_KINDS) {
                                items.take(SYNC_STATUS_PREVIEW_LIMIT).map { it.entityId }
                            } else {
                                emptyList()
                            },
                    )
                }.sortedByDescending { it.count },
        failedCount = failedCount,
        queueUnavailable = queueUnavailable || !status.queueReadable,
        unreadableCloudCount = status.unreadableCloudCount,
    )

/** This item's own title or snippet, or null when it has none (e.g. a voice note). */
private fun JournalNote.explicitLabelOrNull(): String? =
    when (this) {
        is JournalNote.Text -> content.toPreviewLabelOrNull()
        is JournalNote.Image -> caption.toPreviewLabelOrNull()
        is JournalNote.Video -> caption.toPreviewLabelOrNull()
        is JournalNote.Audio -> null
    }

/** Trimmed and length-capped for a one-line preview, or null when there is nothing to show. */
private fun String.toPreviewLabelOrNull(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return if (trimmed.length <= PREVIEW_LABEL_MAX_LENGTH) {
        trimmed
    } else {
        trimmed.take(PREVIEW_LABEL_MAX_LENGTH).trimEnd() + "…"
    }
}
