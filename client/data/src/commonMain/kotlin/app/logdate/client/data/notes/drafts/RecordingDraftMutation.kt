package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.QuietSaveOutcome
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal fun EntryDraft.checkpointRecording(
    blockId: Uuid,
    path: String,
    ownerId: String,
): EntryDraft? {
    val pending = pendingMedia.find { it.blockId == blockId } ?: return null
    if (pending.recordingOwnerId != null && pending.recordingOwnerId != ownerId) return null
    val now = Clock.System.now()
    return copy(
        updatedAt = now,
        pendingMedia =
            pendingMedia.map {
                if (it.blockId == blockId) it.copy(filePath = path, recordingOwnerId = ownerId, checkpointAt = now) else it
            },
    )
}

internal fun EntryDraft.completeRecording(
    blockId: Uuid,
    path: String,
    durationMs: Long,
    ownerId: String,
    quietly: Boolean,
): EntryDraft? {
    val pending =
        pendingMedia.find { it.blockId == blockId }
            ?: return takeIf {
                (recordingOwners[blockId.toString()] ?: quietSave?.takeIf { it.blockId == blockId.toString() }?.ownerId) == ownerId &&
                    notes.any { it is JournalNote.Audio && it.uid == blockId && it.mediaRef == path }
            }
    if (pending.recordingOwnerId != ownerId || pending.filePath != path || durationMs <= 0) return null
    val now = Clock.System.now()
    val audio =
        JournalNote.Audio(
            uid = blockId,
            mediaRef = path,
            durationMs = durationMs,
            creationTimestamp = pending.createdAt,
            lastUpdated = now,
            timeZoneId = pending.timeZoneId,
            caption = pending.caption,
            transcription = pending.transcription,
        )
    val updatedNotes = notes.filterNot { it.uid == blockId }.toMutableList()
    // Pending positions include other unfinished blocks, which are not stored in notes.
    val earlierPending =
        pendingMedia.count {
            it.blockId != blockId && (it.position ?: Int.MAX_VALUE) < (pending.position ?: Int.MAX_VALUE)
        }
    val position = pending.position?.minus(earlierPending)?.coerceIn(0, updatedNotes.size) ?: updatedNotes.size
    updatedNotes.add(position, audio)
    return copy(
        notes = updatedNotes,
        recordingOwners = recordingOwners + (blockId.toString() to ownerId),
        pendingMedia = pendingMedia.filterNot { it.blockId == blockId },
        updatedAt = now,
        quietSave = if (quietly) QuietSaveOutcome(now, ownerId, blockId = blockId.toString()) else quietSave,
    )
}

/** Reconciles an editor snapshot with a recording completed or checkpointed by the service. */
internal fun EntryDraft.mergeRecordingSnapshot(snapshot: EntryDraft): EntryDraft {
    val completed =
        notes
            .filterIsInstance<JournalNote.Audio>()
            .filter { audio ->
                snapshot.pendingMedia.any { pending ->
                    pending.blockId == audio.uid &&
                        !(pending.filePath != null && pending.filePath != audio.mediaRef && pending.fallbackFilePath == audio.mediaRef)
                }
            }
    val completedIds = completed.map { it.uid }.toSet()
    val mergedNotes = snapshot.notes.filterNot { it.uid in completedIds }.toMutableList()
    completed.forEach { audio ->
        val position =
            snapshot.pendingMedia
                .find { it.blockId == audio.uid }
                ?.position
                ?.coerceIn(0, mergedNotes.size) ?: mergedNotes.size
        mergedNotes.add(position, audio)
    }
    val mergedPending =
        snapshot.pendingMedia.filterNot { it.blockId in completedIds }.map { pending ->
            val previous = pendingMedia.find { it.blockId == pending.blockId }
            pending.copy(
                filePath = previous?.filePath ?: pending.filePath,
                fallbackFilePath = previous?.fallbackFilePath ?: pending.fallbackFilePath,
                recordingOwnerId = previous?.recordingOwnerId ?: pending.recordingOwnerId,
                checkpointAt = previous?.checkpointAt ?: pending.checkpointAt,
            )
        }
    val recoveredOwners =
        pendingMedia
            .filter { pending ->
                pending.recordingOwnerId != null &&
                    mergedNotes.any { it is JournalNote.Audio && it.uid == pending.blockId }
            }.associate { it.blockId.toString() to requireNotNull(it.recordingOwnerId) }
    return snapshot.copy(
        notes = mergedNotes,
        pendingMedia = mergedPending,
        quietSave = quietSave ?: snapshot.quietSave,
        recordingOwners = snapshot.recordingOwners + recordingOwners + recoveredOwners,
    )
}
