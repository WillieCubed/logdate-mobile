package app.logdate.client.data.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.device.restore.NativeJournalMediaInventory
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.NativeJournalSourceReceipt
import app.logdate.client.media.ConditionalMediaSaver
import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.isRecoveredOriginal
import app.logdate.client.repository.journals.EntryDraftRecoveryRepository
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.flow.first
import java.io.File

internal data class NativeLateMediaRepositories(
    val journals: JournalRepository,
    val notes: JournalNotesRepository,
    val entryDrafts: EntryDraftRecoveryRepository,
    val preferences: DataStore<Preferences>,
    val media: MediaManager,
    val references: StoredMediaReferences,
)

/** Captures current objects only; no missing object is ever inserted by this repair. */
internal class NativeLateMediaTargets(
    private val repositories: NativeLateMediaRepositories,
    private val scope: JournalMergeScope,
    private val captured: RetainedJournalSourceScope,
    private val sourceScope: RetainedJournalSourceScope,
    private val isDeliveryCurrent: () -> Boolean,
) {
    private val journals get() = repositories.journals
    private val notes get() = repositories.notes
    private val entryDrafts get() = repositories.entryDrafts
    private val preferences get() = repositories.preferences

    suspend fun requireCurrent() {
        check(isDeliveryCurrent()) { "Native media delivery or backend changed" }
        check(journals.getJournalRecoveryScope() == scope && notes.getNoteRecoveryScope() == scope) {
            "Native media repository scope changed"
        }
        requirePreferences(preferences.data.first())
    }

    suspend fun read(): List<NativeLateMediaRepair.Target> {
        requireCurrent()
        val targets = noteTargets() + journalTargets() + editorDraftTargets() + entryDraftTargets()
        requireCurrent()
        return targets.filter { it.references.isNotEmpty() }
    }

    private suspend fun noteTargets(): List<NativeLateMediaRepair.Target> =
        notes.allNotesObserved.first().map { expected ->
            NativeLateMediaRepair.Target(expected.lateMediaReferences()) { replacements ->
                requireCurrent()
                notes.compareAndSetNoteForRetainedRestore(expected.uid, expected, expected.withLateMedia(replacements), scope)
            }
        }

    private suspend fun journalTargets(): List<NativeLateMediaRepair.Target> =
        journals.allJournalsObserved.first().map { expected ->
            NativeLateMediaRepair.Target(setOfNotNull(expected.coverImageUri)) { replacements ->
                requireCurrent()
                journals.compareAndSetJournalForRetainedRestore(
                    expected.id,
                    expected,
                    expected.copy(coverImageUri = replacements[expected.coverImageUri] ?: expected.coverImageUri),
                    scope,
                )
            }
        }

    private suspend fun editorDraftTargets(): List<NativeLateMediaRepair.Target> {
        val draftScope = checkNotNull(journals.getDraftRecoveryScope())
        check(captured.matchesDraftRecoveryScope(captured, draftScope)) { "Native media draft scope changed" }
        return journals.getAllDraftsForSync().map { expected ->
            NativeLateMediaRepair.Target(expected.lateMediaReferences()) { replacements ->
                requireCurrent()
                check(journals.getDraftRecoveryScope() == draftScope) { "Native media draft scope changed" }
                journals.compareAndSetDraftForRetainedRestore(
                    expected.id,
                    expected.id,
                    expected,
                    expected.withLateMedia(replacements),
                    draftScope,
                )
            }
        }
    }

    private suspend fun entryDraftTargets(): List<NativeLateMediaRepair.Target> =
        entryDrafts.getDraftSnapshotsForRecovery().map { expected ->
            NativeLateMediaRepair.Target(expected.lateMediaReferences()) { replacements ->
                requireCurrent()
                // Acquire the draft mutex before main preferences, matching normal editor saves.
                entryDrafts.compareAndSetDraftForScopedRecovery(
                    expected,
                    expected.withLateMedia(replacements),
                    isDeliveryCurrent,
                ) { commit ->
                    var applied = false
                    preferences.updateData { current ->
                        requirePreferences(current)
                        applied = commit()
                        current
                    }
                    applied
                }
            }
        }

    private fun requirePreferences(current: Preferences) {
        check(isDeliveryCurrent()) { "Native media delivery or backend changed" }
        val actual = RetainedJournalSourceScope.from(current, scope.serverOrigin)
        check(
            actual != null &&
                captured.matchesCurrent(actual) &&
                actual.matchesRepositoryScope(scope.ownerId, scope.serverOrigin) &&
                sourceScope.matchesCurrentOrAdopted(actual, current),
        ) {
            "Native media owner, binding or backend changed"
        }
    }
}

internal suspend fun repairNativeLateMedia(
    candidates: List<Map<String, File>>,
    repositories: NativeLateMediaRepositories,
    custody: NativeJournalReplayCustody,
    scope: JournalMergeScope,
    completedSource: NativeJournalSourceReceipt?,
    completedInventory: NativeJournalMediaInventory? = null,
    sourceReceiptSha256: String? = null,
    packageName: String = "",
    legacyReceiptsRoot: File? = null,
    isDeliveryCurrent: () -> Boolean,
): NativeLateMediaRepair.Result {
    val preferences = repositories.preferences.data.first()
    val captured = checkNotNull(RetainedJournalSourceScope.from(preferences, scope.serverOrigin))
    val source =
        completedSource?.let {
            normalizeBackendOrigin(it.backendOrigin)?.let { origin ->
                RetainedJournalSourceScope(it.canonicalOwnerId, it.canonicalOwnerBound == "true", origin)
            }
        } ?: return NativeLateMediaRepair.Result(0, 0, emptySet(), retry = false)
    if (!source.matchesCurrentOrAdopted(captured, preferences)) return NativeLateMediaRepair.Result(0, 0, emptySet(), retry = false)
    val targets = NativeLateMediaTargets(repositories, scope, captured, source, isDeliveryCurrent)
    val media = repositories.media
    val repair =
        NativeLateMediaRepair(
            candidates,
            requireCurrent = targets::requireCurrent,
            available = { media.isRecoveredOriginal(it, StoredMediaReferences.Unchanged) },
            importOriginal = { asset ->
                repositories.references.storedReference(importNativeLateMediaOriginal(media, asset, isDeliveryCurrent))
            },
            canonicalReference = repositories.references::storedReference,
            authorizeUnreferenced = { reference, asset ->
                val path = "files/media/" + reference.removePrefix("logdate-media://library/")
                val expected = completedInventory?.entry(path)
                expected != null && expected.size == asset.file.length() && expected.sha256 == retainedCanonicalMediaDigest(reference)
            },
        )
    val result = repair.repair(targets.read())
    acknowledgeNativeLateMedia(
        custody,
        result.recoveredSources,
        verify = { media.isRecoveredOriginal(it, StoredMediaReferences.Unchanged) },
        requireCurrent = targets::requireCurrent,
    )
    val legacyRetry =
        if (completedInventory != null && sourceReceiptSha256 != null && legacyReceiptsRoot != null) {
            repairNativeLateLegacyMedia(
                targets,
                repositories,
                custody,
                completedInventory,
                sourceReceiptSha256,
                packageName,
                legacyReceiptsRoot,
                isDeliveryCurrent,
                acknowledge = !result.retry,
            )
        } else {
            false
        }
    return result.copy(retry = result.retry || legacyRetry)
}

internal suspend fun acknowledgeNativeLateMedia(
    custody: NativeJournalReplayCustody,
    recoveredSources: Set<NativeLateMediaRepair.RecoveredSource>,
    verify: suspend (String) -> Boolean,
    requireCurrent: suspend () -> Unit,
) {
    val verified = mutableSetOf<String>()
    for (payload in custody.sealedPayloads()) {
        if (!payload.complete || !payload.path.startsWith("files/media/")) continue
        val reference = "logdate-media://library/" + payload.path.removePrefix("files/media/")
        val recovered = recoveredSources.firstOrNull { it.path == payload.path && it.file == payload.file.canonicalPath } ?: continue
        requireCurrent()
        val source = RetainedManagedMediaSource(mapOf(payload.path to payload.file))
        if (
            source.resolve(reference) != null &&
            retainedCanonicalMediaDigest(recovered.reference) == retainedCanonicalMediaDigest(reference) &&
            verify(recovered.reference)
        ) {
            verified += payload.file.canonicalPath
        }
        requireCurrent()
    }
    requireCurrent()
    custody.acknowledgeRecoveredMedia(verified)
}

internal suspend fun importNativeLateMediaOriginal(
    media: MediaManager,
    asset: RetainedManagedMediaSource.Asset,
    canCommit: () -> Boolean,
): String =
    checkNotNull(media as? ConditionalMediaSaver) { "Guarded native media storage unavailable" }
        .saveMediaFromFile(asset.file.absolutePath, asset.fileName, asset.mimeType, canCommit)
