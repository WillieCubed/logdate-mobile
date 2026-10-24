package app.logdate.client.data.restore

import app.logdate.client.data.journals.toModel
import app.logdate.client.data.notes.toModel
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.mediaRefOrNull
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlin.uuid.Uuid

internal class RetainedJournalMergeProgress {
    var appliedJournals = 0
    var appliedNotes = 0
    var appliedLinks = 0
    var appliedDrafts = 0
    var pendingMedia = 0
    var conflicts = 0
    val journalTargets = mutableMapOf<Uuid, Uuid>()
    val noteTargets = mutableMapOf<Uuid, Uuid>()
}

internal suspend fun RetainedJournalDatabaseMerger.mergeRetainedJournals(progress: RetainedJournalMergeProgress) {
    val allSourceJournals = sourceDatabase.journalDao().getAll()
    progress.conflicts += allSourceJournals.count { it.deletedAt != null }
    for (entity in allSourceJournals.filter { it.deletedAt == null }) {
        requireCurrentScope()
        val source = entity.toModel()
        val key = checkpointKey("journal", source.id.toString())
        val known = checkpointStore.read(key)
        val current = journals.getJournalById(source.id)
        val mapped = known?.targetId?.let { parseUuid(it) }?.let { journals.getJournalById(it) }
        val sourceHash = hash(json.encodeToString(Journal.serializer(), source))
        val plan =
            RetainedJournalMergeCheckpoint(entries = known?.let { mapOf(key to it) }.orEmpty()).plan(
                generationId,
                "journal",
                source.id.toString(),
                sourceHash,
                current?.let { hash(json.encodeToString(Journal.serializer(), it)) },
                mapped?.let { hash(json.encodeToString(Journal.serializer(), it)) },
                sourceHash,
            )
        applyRetainedJournal(source, plan, known, progress)
    }
}

internal suspend fun RetainedJournalDatabaseMerger.applyRetainedJournal(
    source: Journal,
    plan: RetainedJournalMergeCheckpoint.Plan,
    known: RetainedJournalMergeCheckpoint.Entry?,
    progress: RetainedJournalMergeProgress,
) {
    if (plan.decision == RetainedJournalMergeCheckpoint.Decision.SOURCE_CHANGED ||
        plan.decision == RetainedJournalMergeCheckpoint.Decision.TARGET_CONFLICT ||
        plan.targetId == null
    ) {
        progress.conflicts++
        return
    }
    val targetId = parseUuid(plan.targetId)
    val replacement = source.copy(id = targetId)
    val targetHash = hash(json.encodeToString(Journal.serializer(), replacement))
    val checkpointEntry = persistCheckpointPlan(plan, known, targetHash)
    if (plan.decision == RetainedJournalMergeCheckpoint.Decision.INSERT) {
        val existing = journals.getJournalById(targetId)
        if (existing == null) {
            if (!journals.compareAndSetJournalForRetainedRestore(source.id, null, replacement, scope, recoveryId())) {
                progress.conflicts++
                return
            }
            progress.appliedJournals++
        } else if (hash(json.encodeToString(Journal.serializer(), existing)) != targetHash) {
            progress.conflicts++
            return
        }
    } else if (journals.getJournalById(targetId) == null) {
        // A previously recovered journal may have been deliberately deleted in working state.
        // Its checkpoint must not recreate it or let child rows attach to a missing target.
        progress.conflicts++
        return
    }
    progress.journalTargets[source.id] = targetId
    if (!checkpointEntry.applied) checkpointStore.markApplied(plan.key, targetHash)
}

internal suspend fun RetainedJournalDatabaseMerger.mergeRetainedNotes(
    source: RetainedSourceNotes,
    progress: RetainedJournalMergeProgress,
) {
    for ((entityType, note) in source.values) mergeRetainedNote(entityType, note, progress)
}

internal suspend fun RetainedJournalDatabaseMerger.mergeRetainedNote(
    entityType: String,
    note: JournalNote,
    progress: RetainedJournalMergeProgress,
) {
    requireCurrentScope()
    val sourceId = note.uid
    val sourceHash = hash(json.encodeToString(JournalNote.serializer(), note))
    val key = checkpointKey(entityType, sourceId.toString())
    val known = checkpointStore.read(key)
    val knownTargetId = known?.targetId?.let { parseUuid(it) }
    val current = notes.getNoteById(sourceId)
    val mapped = knownTargetId?.takeIf { it != sourceId }?.let { notes.getNoteById(it) }
    val accepted = if (knownTargetId != null && knownTargetId != sourceId) mapped else current
    val needsMediaImport =
        shouldReimportRetainedMedia(
            known,
            sourceHash,
            accepted != null,
            listOf(accepted?.mediaRefOrNull()),
            { media.exists(it) },
        )
    val importedResult =
        if (needsMediaImport) {
            importNoteMedia(note)
        } else {
            (accepted?.mediaRefOrNull()?.let { note.withRetainedMediaRef(it) } ?: note) to false
        }
    if (importedResult.second) progress.pendingMedia++
    val imported = retainedNoteRecoveryProjection(importedResult.first)
    val targetHash = hash(json.encodeToString(JournalNote.serializer(), imported))
    val plan =
        RetainedJournalMergeCheckpoint(entries = known?.let { mapOf(key to it) }.orEmpty()).plan(
            generationId,
            entityType,
            sourceId.toString(),
            sourceHash,
            current?.let { hash(json.encodeToString(JournalNote.serializer(), it)) },
            mapped?.let { hash(json.encodeToString(JournalNote.serializer(), it)) },
            targetHash,
        )
    applyRetainedNote(note, imported, plan, known, progress)
}

internal suspend fun RetainedJournalDatabaseMerger.applyRetainedNote(
    note: JournalNote,
    imported: JournalNote,
    plan: RetainedJournalMergeCheckpoint.Plan,
    known: RetainedJournalMergeCheckpoint.Entry?,
    progress: RetainedJournalMergeProgress,
) {
    val sourceId = note.uid
    if (plan.decision == RetainedJournalMergeCheckpoint.Decision.SOURCE_CHANGED ||
        plan.decision == RetainedJournalMergeCheckpoint.Decision.TARGET_CONFLICT ||
        plan.targetId == null
    ) {
        progress.conflicts++
        return
    }
    val targetId = parseUuid(plan.targetId)
    val replacement = imported.withRetainedMergeId(targetId)
    val expectedTargetHash = hash(json.encodeToString(JournalNote.serializer(), replacement))
    val checkpointEntry = persistCheckpointPlan(plan, known, expectedTargetHash)
    if (plan.decision == RetainedJournalMergeCheckpoint.Decision.INSERT) {
        val existing = notes.getNoteById(targetId)
        if (existing == null) {
            if (!persistRetainedNote(note, replacement)) {
                progress.conflicts++
                return
            }
            progress.appliedNotes++
        } else if (hash(json.encodeToString(JournalNote.serializer(), existing)) != expectedTargetHash) {
            progress.conflicts++
            return
        }
    } else if (plan.decision == RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT) {
        if (!repairRetainedNote(targetId, note, imported)) {
            progress.conflicts++
            return
        }
    }
    progress.noteTargets[sourceId] = targetId
    if (!checkpointEntry.applied) checkpointStore.markApplied(plan.key, expectedTargetHash)
}

internal suspend fun RetainedJournalDatabaseMerger.repairRetainedNote(
    targetId: Uuid,
    source: JournalNote,
    imported: JournalNote,
): Boolean {
    val existing = notes.getNoteById(targetId)
    if (existing == null) {
        // Never resurrect a row removed by the user after a prior recovery attempt.
        return false
    }
    val sourceMediaRef = source.mediaRefOrNull()
    val importedMediaRef = imported.mediaRefOrNull()
    val repairedReference = repairRetainedMediaReference(existing.mediaRefOrNull(), sourceMediaRef, importedMediaRef)
    if (repairedReference != null && repairedReference != existing.mediaRefOrNull()) {
        val mediaRepair = existing.withRetainedMediaRef(repairedReference)
        val repaired =
            notes.compareAndSetNoteForRetainedRestore(
                source.uid,
                existing,
                mediaRepair,
                scope,
                retainedMediaRepairId(
                    generationId,
                    targetId.toString(),
                    hash(json.encodeToString(JournalNote.serializer(), mediaRepair)),
                ),
            )
        if (!repaired) return false
    }
    return true
}

internal suspend fun RetainedJournalDatabaseMerger.mergeRetainedMemberships(
    sourceNotes: RetainedSourceNotes,
    progress: RetainedJournalMergeProgress,
) {
    val sourceMemberships = readSourceMemberships()
    progress.conflicts += sourceMemberships.unresolvedRows
    val links = sourceMemberships.links
    val duplicateSourceNoteIds =
        sourceNotes.values
            .groupBy { it.second.uid }
            .filterValues { it.size > 1 }
            .keys
    val activeDatabase =
        org.koin.core.context.GlobalContext
            .get()
            .get<LogDateDatabase>()
    for ((sourceJournalId, sourceNoteId) in links) {
        requireCurrentScope()
        if (sourceNoteId in duplicateSourceNoteIds) {
            progress.conflicts++
            continue
        }
        val journalId = progress.journalTargets[sourceJournalId]
        val noteId = progress.noteTargets[sourceNoteId]
        if (journalId == null || noteId == null) {
            progress.conflicts++
            continue
        }
        applyRetainedMembership(sourceJournalId, sourceNoteId, journalId, noteId, activeDatabase, progress)
    }
}

internal suspend fun RetainedJournalDatabaseMerger.applyRetainedMembership(
    sourceJournalId: Uuid,
    sourceNoteId: Uuid,
    journalId: Uuid,
    noteId: Uuid,
    activeDatabase: LogDateDatabase,
    progress: RetainedJournalMergeProgress,
) {
    val linkHash = hash("$journalId\u0000$noteId")
    val key = checkpointKey("membership", "$sourceJournalId/$sourceNoteId")
    val known = checkpointStore.read(key)
    val plan =
        RetainedJournalMergeCheckpoint(entries = known?.let { mapOf(key to it) }.orEmpty()).plan(
            generationId,
            "membership",
            "$sourceJournalId/$sourceNoteId",
            linkHash,
            null,
            expectedTargetSha256 = linkHash,
        )
    val checkpointEntry = persistCheckpointPlan(plan, known, linkHash)
    if (plan.decision == RetainedJournalMergeCheckpoint.Decision.INSERT) {
        val present =
            activeDatabase
                .journalContentDao()
                .getContentForJournal(journalId)
                .first()
                .contains(noteId)
        if (!present) {
            if (!content.addContentToJournalForRetainedRestore(noteId, journalId, scope, recoveryId())) {
                progress.conflicts++
                return
            }
            progress.appliedLinks++
        }
    }
    if (!checkpointEntry.applied) checkpointStore.markApplied(plan.key, linkHash)
}
