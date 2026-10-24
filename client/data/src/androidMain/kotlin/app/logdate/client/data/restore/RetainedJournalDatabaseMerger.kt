package app.logdate.client.data.restore

import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Merges only the typed journal projection. A result never releases the encrypted source custody. */
internal class RetainedJournalDatabaseMerger(
    internal val generationId: String,
    internal val sourceFiles: Map<String, File>,
    internal val sourceDatabase: LogDateDatabase,
    internal val sourceScope: RetainedJournalSourceScope,
    internal val activePreferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    internal val journals: JournalRepository,
    internal val notes: JournalNotesRepository,
    internal val content: JournalContentRepository,
    internal val media: MediaManager,
    internal val checkpointStore: RetainedJournalMergeCheckpointStore,
    internal val scratchRoot: File,
    internal val ensureSourceSessionCurrent: () -> Unit,
    internal val additionalMediaSources: List<Map<String, File>>,
) {
    internal val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    internal var capturedScope: JournalMergeScope? = null
    internal var draftsCovered = true
    internal val scope: JournalMergeScope
        get() = checkNotNull(capturedScope)

    data class Result(
        val journalsApplied: Int,
        val notesApplied: Int,
        val membershipsApplied: Int,
        val draftsApplied: Int,
        val mediaPending: Int,
        val conflicts: Int,
        val coverageComplete: Boolean = false,
    )

    suspend fun merge(): Result {
        require(generationId.isNotBlank())
        val repositoryScope = checkNotNull(journals.getJournalRecoveryScope())
        check(notes.getNoteRecoveryScope() == repositoryScope) { "Journal repositories disagree on recovery scope" }
        capturedScope = repositoryScope
        requireCurrentScope()
        val progress = RetainedJournalMergeProgress()
        mergeRetainedJournals(progress)
        val sourceNotes = readRetainedSourceNotes()
        progress.conflicts += sourceNotes.unresolvedRows
        mergeRetainedNotes(sourceNotes, progress)
        mergeRetainedTranscriptions(sourceNotes, progress)
        mergeRetainedMemberships(sourceNotes, progress)
        progress.appliedDrafts += mergeDrafts(progress.journalTargets) { progress.pendingMedia++ }
        val coverageComplete =
            RetainedJournalTableCoverage.canComplete(
                sourceTableCounts(),
                checkpointsApplied = checkpointStore.allApplied(generationId),
                unresolvedRows = progress.conflicts,
                mediaPending = progress.pendingMedia,
                draftsCovered = draftsCovered,
            )
        return Result(
            progress.appliedJournals,
            progress.appliedNotes,
            progress.appliedLinks,
            progress.appliedDrafts,
            progress.pendingMedia,
            progress.conflicts,
            coverageComplete,
        )
    }

    internal data class SourceMemberships(
        val links: Set<Pair<Uuid, Uuid>>,
        val unresolvedRows: Int,
    )

    internal suspend fun readSourceMemberships(): SourceMemberships {
        val canonical = sourceDatabase.journalContentDao().getAllLinks()
        val legacy =
            buildList {
                sourceDatabase.journalDao().getAll().forEach { journal ->
                    sourceDatabase.journalNotesDao().getNotesForJournal(journal.id).first().forEach { link ->
                        add(RetainedJournalMembership(link.journalId, link.noteId, deleted = false))
                    }
                }
            }
        val memberships = canonical.map { RetainedJournalMembership(it.journalId, it.contentId, it.deletedAt != null) }
        val deleted = memberships.count { it.deleted }
        return SourceMemberships(retainedJournalMemberships(memberships, legacy), deleted)
    }

    internal fun sourceTableCounts(): Map<String, Long> {
        val database = sourceDatabase.openHelper.readableDatabase
        val names = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
        return names.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    require(table.matches(Regex("[A-Za-z0-9_]+"))) { "Unexpected source table name" }
                    val count =
                        database.query("SELECT COUNT(*) FROM \"$table\"").use { rows ->
                            check(rows.moveToFirst()) { "Could not count retained source rows" }
                            rows.getLong(0)
                        }
                    put(table, count)
                }
            }
        }
    }

    internal suspend fun requireCurrentScope() {
        ensureSourceSessionCurrent()
        val current = currentSourceScope()
        check(current != null && sourceScopeMatchesCurrent(current)) { "Retained journal owner or backend changed" }
        val repositoryScope = scope
        check(
            journals.getJournalRecoveryScope() == repositoryScope &&
                notes.getNoteRecoveryScope() == repositoryScope &&
                current.matchesRepositoryScope(repositoryScope.ownerId, repositoryScope.serverOrigin) &&
                app.logdate.client.device.identity
                    .sameOwnerId(repositoryScope.ownerId, current.ownerId),
        ) { "Journal repository scope changed during retained recovery" }
    }

    internal suspend fun sameScope(scope: DraftRecoveryScope): Boolean {
        val current = currentSourceScope() ?: return false
        return sourceScope.matchesDraftRecoveryScope(current, scope)
    }

    internal suspend fun sourceScopeMatchesCurrent(current: RetainedJournalSourceScope): Boolean {
        if (sourceScope.matchesCurrent(current)) return true
        if (sourceScope.bound || !current.bound || sourceScope.ownerId.isNullOrBlank() || current.ownerId.isNullOrBlank()) return false
        val raw = activePreferences.data.first()[stringPreferencesKey(HISTORY_OWNER_ADOPTION_RECEIPT)] ?: return false
        val receipt = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return false
        val oldOwner = receipt["oldOwner"]?.jsonPrimitive?.contentOrNull ?: return false
        val newOwner = receipt["newOwner"]?.jsonPrimitive?.contentOrNull ?: return false
        val origin = receipt["origin"]?.jsonPrimitive?.contentOrNull ?: return false
        val device = receipt["device"]?.jsonPrimitive?.contentOrNull ?: return false
        return device.isNotBlank() &&
            app.logdate.client.device.identity
                .sameOwnerId(oldOwner, sourceScope.ownerId) &&
            app.logdate.client.device.identity
                .sameOwnerId(newOwner, current.ownerId) &&
            normalizeBackendOrigin(origin) == sourceScope.backendOrigin &&
            sourceScope.backendOrigin == current.backendOrigin
    }

    internal suspend fun currentSourceScope(): RetainedJournalSourceScope? {
        val repositoryScope = journals.getJournalRecoveryScope() ?: return null
        val current = RetainedJournalSourceScope.from(activePreferences.data.first(), repositoryScope.serverOrigin) ?: return null
        if (!app.logdate.client.device.identity
                .sameOwnerId(repositoryScope.ownerId, current.ownerId)
        ) {
            return null
        }
        if (normalizeBackendOrigin(repositoryScope.serverOrigin) != current.backendOrigin) return null
        return current
    }

    internal fun persistCheckpointPlan(
        plan: RetainedJournalMergeCheckpoint.Plan,
        known: RetainedJournalMergeCheckpoint.Entry?,
        expectedTargetSha256: String,
    ): RetainedJournalMergeCheckpoint.Entry {
        if (known?.applied == true) return known
        val planned =
            plan.checkpoint.entries.getValue(plan.key).copy(
                targetSha256 = expectedTargetSha256,
                applied = plan.decision == RetainedJournalMergeCheckpoint.Decision.ALREADY_PRESENT,
            )
        if (planned != known) checkpointStore.write(plan.key, planned)
        return planned
    }

    internal fun checkpointKey(
        type: String,
        sourceId: String,
    ): String = "$generationId\u0000$type\u0000$sourceId"

    private companion object {
        const val HISTORY_OWNER_ADOPTION_RECEIPT = "identity.completed_history_owner_adoption.v1"
    }

    internal fun recoveryId(): String = "retained:$generationId"

    internal fun hash(value: String): String =
        MessageDigest
            .getInstance(
                "SHA-256",
            ).digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    internal fun parseUuid(value: String): Uuid = Uuid.parse(value)
}
