package app.logdate.client.data.restore

import app.logdate.client.data.journals.LocalDeletionType
import app.logdate.client.data.journals.RoomArchiveRecoveryReceipts
import app.logdate.client.data.notes.withStoredMediaRef
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.SyncMetadataService
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

internal class RetainedTranscriptionTarget {
    private val koin = GlobalContext.get()
    val database = koin.get<LogDateDatabase>()
    val transactions = koin.get<SyncTransactionManager>()
    val receipts = RoomArchiveRecoveryReceipts(database.downloadInboxDao())

    fun persistence(merger: RetainedJournalDatabaseMerger) =
        RetainedTranscriptionRecoveryPersistence(
            database.transcriptionDao(),
            database.audioNoteDao(),
            transactions,
            koin.get<SyncMetadataService>(),
            RoomArchiveRecoveryReceipts(database.downloadInboxDao()),
            { merger.notes.getNoteRecoveryScope() },
        )
}

internal suspend fun RetainedJournalDatabaseMerger.retainedTranscript(noteId: Uuid): RetainedTranscriptionSnapshot? {
    val row = sourceDatabase.transcriptionDao().getTranscriptionByNoteId(noteId) ?: return null
    val audio = sourceDatabase.audioNoteDao().findNoteOneOff(noteId) ?: return null
    if (audio.deletedAt != null || !row.matchesRetainedAudio(audio)) return null
    return RetainedTranscriptionSnapshot(row, sourceDatabase.transcriptionDao().getSegmentsByNoteId(noteId))
}

/** Note, original transcript fields, segment index, receipt and outbox commit before observers see audio. */
internal suspend fun RetainedJournalDatabaseMerger.persistRetainedNote(
    source: JournalNote,
    replacement: JournalNote,
): Boolean {
    if (source !is JournalNote.Audio) return notes.compareAndSetNoteForRetainedRestore(source.uid, null, replacement, scope, recoveryId())
    val transcript =
        retainedTranscript(source.uid)
            ?: return notes.compareAndSetNoteForRetainedRestore(source.uid, null, replacement, scope, recoveryId())
    val sourceHash = hash(transcript.contentFingerprint())
    val key = checkpointKey("transcription", transcript.row.id.toString())
    val known = checkpointStore.read(key)
    if (known != null && (known.sourceSha256 != sourceHash || known.targetId != replacement.uid.toString())) return false
    val target = RetainedTranscriptionTarget()
    if (target.persistence(this).wasDeleted(scope, source.uid)) {
        return notes.compareAndSetNoteForRetainedRestore(
            source.uid,
            null,
            (replacement as JournalNote.Audio).copy(transcript = null),
            scope,
            recoveryId(),
        )
    }
    val checkpoint =
        known ?: RetainedJournalMergeCheckpoint.Entry(
            generationId,
            "transcription",
            transcript.row.id.toString(),
            sourceHash,
            replacement.uid.toString(),
        )
    if (known == null) checkpointStore.write(key, checkpoint)
    val targetHash =
        target.applyRetainedTranscription(this, source, replacement as JournalNote.Audio, transcript)
            ?: return false
    if (!checkpoint.applied) checkpointStore.markApplied(key, targetHash)
    return true
}

private suspend fun RetainedTranscriptionTarget.applyRetainedTranscription(
    merger: RetainedJournalDatabaseMerger,
    source: JournalNote.Audio,
    replacement: JournalNote.Audio,
    transcript: RetainedTranscriptionSnapshot,
): String? =
    transactions.withTransaction {
        merger.requireCurrentScope()
        if (receipts.containsLocalDeletion(merger.scope, LocalDeletionType.NOTE, source.uid.toString()) ||
            receipts.containsLocalDeletion(merger.scope, LocalDeletionType.NOTE, replacement.uid.toString())
        ) {
            return@withTransaction null
        }
        if (!merger.notes.compareAndSetNoteForRetainedRestore(
                source.uid,
                null,
                replacement.copy(transcript = null),
                merger.scope,
                merger.recoveryId(),
            )
        ) {
            return@withTransaction null
        }
        val audio = checkNotNull(database.audioNoteDao().findNoteOneOff(replacement.uid))
        val targetPersistence = persistence(merger)
        val current = targetPersistence.snapshot(audio.uid)
        val rowId = merger.availableRetainedTranscriptId(transcript, audio.uid, database)
        val rebound = transcript.rebound(audio, rowId)
        check(targetPersistence.compareAndSet(current, rebound, audio, merger.scope, merger.recoveryId(), merger::requireCurrentScope)) {
            "Retained transcript application must commit with its audio note"
        }
        merger.hash(rebound.contentFingerprint())
    }

internal suspend fun RetainedJournalDatabaseMerger.availableRetainedTranscriptId(
    source: RetainedTranscriptionSnapshot,
    targetNote: Uuid,
    database: LogDateDatabase,
): Uuid {
    val existing = database.transcriptionDao().getTranscriptionById(source.row.id)
    return if (existing == null || existing.noteId == targetNote) {
        source.row.id
    } else {
        parseUuid(RetainedJournalMergeCheckpoint.stableVariantId(generationId, "transcription_row", source.row.id.toString()))
    }
}

internal suspend fun RetainedJournalDatabaseMerger.mergeRetainedTranscriptions(
    sourceNotes: RetainedSourceNotes,
    progress: RetainedJournalMergeProgress,
) {
    val target = RetainedTranscriptionTarget()
    val rows = sourceDatabase.transcriptionDao().getAllTranscriptions()
    var segmentCount = 0L
    for (row in rows) {
        requireCurrentScope()
        segmentCount += sourceDatabase.transcriptionDao().getSegmentsByNoteId(row.noteId).size
        val source = sourceNotes.values.map { it.second }.singleOrNull { it.uid == row.noteId } as? JournalNote.Audio
        val transcript = retainedTranscript(row.noteId)
        val mapped = progress.noteTargets[row.noteId]
        if (source == null || transcript == null || mapped == null) {
            progress.conflicts++
            continue
        }
        if (!mergeRetainedTranscription(source, transcript, mapped, target, progress)) progress.conflicts++
    }
    val actual = sourceTableCounts().getValue("transcription_segments")
    if (actual != segmentCount) progress.conflicts++
}

private suspend fun RetainedJournalDatabaseMerger.mergeRetainedTranscription(
    source: JournalNote.Audio,
    transcript: RetainedTranscriptionSnapshot,
    mapped: Uuid,
    target: RetainedTranscriptionTarget,
    progress: RetainedJournalMergeProgress,
): Boolean {
    val key = checkpointKey("transcription", transcript.row.id.toString())
    val sourceHash = hash(transcript.contentFingerprint())
    val known = checkpointStore.read(key)
    if (known != null && known.sourceSha256 != sourceHash) return false
    val persistence = target.persistence(this)
    if (persistence.wasDeleted(scope, source.uid) || persistence.wasDeleted(scope, mapped)) return false
    resumeAppliedRetainedTranscription(source, known, target, persistence, progress)?.let { return it }
    val imported = importNoteMedia(source).first.withStoredMediaRef(GlobalContext.get().get<StoredMediaReferences>()) as JournalNote.Audio
    val currentNote = notes.getNoteById(mapped) as? JournalNote.Audio ?: return false
    val currentAudio = target.database.audioNoteDao().findNoteOneOff(mapped) ?: return false
    val current = persistence.snapshot(mapped)
    val expected = transcript.rebound(currentAudio, current?.row?.id ?: transcript.row.id)
    val same =
        currentNote.copy(transcript = null) == imported.copy(uid = mapped, transcript = null) &&
            (current == null || current == expected)
    val selected =
        known?.targetId?.takeIf { it != mapped.toString() || same }?.let(::parseUuid) ?: if (same) {
            mapped
        } else {
            parseUuid(RetainedJournalMergeCheckpoint.stableVariantId(generationId, "transcription_audio", source.uid.toString()))
        }
    if (known != null && selected.toString() != known.targetId) {
        checkpointStore.write(key, known.copy(targetId = selected.toString(), targetSha256 = null))
    }
    if (selected != mapped) {
        val replacement = imported.copy(uid = selected)
        val existing = notes.getNoteById(selected)
        if (existing == null) {
            if (!persistRetainedNote(source, replacement)) return false
            progress.appliedNotes++
        } else if ((existing as? JournalNote.Audio)?.copy(transcript = null) != replacement.copy(transcript = null)) {
            return false
        }
    }
    val audio = target.database.audioNoteDao().findNoteOneOff(selected) ?: return false
    val existing = persistence.snapshot(selected)
    val rowId = existing?.row?.id ?: availableRetainedTranscriptId(transcript, selected, target.database)
    val rebound = transcript.rebound(audio, rowId)
    if (existing != null && existing != rebound) return false
    val entry =
        checkpointStore.read(key)
            ?: RetainedJournalMergeCheckpoint.Entry(
                generationId,
                "transcription",
                transcript.row.id.toString(),
                sourceHash,
                selected.toString(),
            )
    if (checkpointStore.read(key) == null) checkpointStore.write(key, entry)
    if (!persistence.compareAndSet(existing, rebound, audio, scope, recoveryId(), ::requireCurrentScope)) return false
    checkpointStore.markApplied(key, hash(rebound.contentFingerprint()))
    progress.noteTargets[source.uid] = selected
    return true
}

private suspend fun RetainedJournalDatabaseMerger.resumeAppliedRetainedTranscription(
    source: JournalNote.Audio,
    known: RetainedJournalMergeCheckpoint.Entry?,
    target: RetainedTranscriptionTarget,
    persistence: RetainedTranscriptionRecoveryPersistence,
    progress: RetainedJournalMergeProgress,
): Boolean? {
    if (known == null || (!known.applied && !persistence.wasApplied(scope, recoveryId(), parseUuid(known.targetId)))) return null
    val accepted = parseUuid(known.targetId)
    val audio = target.database.audioNoteDao().findNoteOneOff(accepted)
    if (audio == null || audio.deletedAt != null) return false
    val snapshot = persistence.snapshot(accepted) ?: return false
    if (!known.applied) checkpointStore.markApplied(checkpointKey("transcription", known.sourceId), hash(snapshot.contentFingerprint()))
    progress.noteTargets[source.uid] = accepted
    return true
}

internal suspend fun RetainedJournalDatabaseMerger.retainedNoteRecoveryProjection(note: JournalNote): JournalNote {
    val stored = note.withStoredMediaRef(GlobalContext.get().get<StoredMediaReferences>())
    if (stored !is JournalNote.Audio) return stored
    return if (RetainedTranscriptionTarget().persistence(this).wasDeleted(scope, stored.uid)) stored.copy(transcript = null) else stored
}
