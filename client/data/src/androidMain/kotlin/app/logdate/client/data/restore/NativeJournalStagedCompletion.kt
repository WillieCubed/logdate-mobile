package app.logdate.client.data.restore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.device.restore.NATIVE_JOURNAL_TRANSFER_PREFIX
import app.logdate.client.device.restore.NATIVE_MEDIA_INVENTORY_PATH
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.NativeJournalRestoreAuthority
import app.logdate.client.repository.journals.JournalMergeScope

/** A late manifest is retained only against the digest already committed with its source scope. */
internal fun refreshStagedNativeMediaInventories(context: Context) {
    val index = NativeJournalStagedSourceIndex(context)
    val authority = NativeJournalRestoreAuthority(context)
    val custody = NativeJournalReplayCustody(context)
    val payloads =
        custody.sealedPayloads().filter {
            it.complete && it.path.removePrefix(NATIVE_JOURNAL_TRANSFER_PREFIX) == NATIVE_MEDIA_INVENTORY_PATH
        }
    for (payload in payloads) {
        val hash = nativeSnapshotChecksum(payload.file)
        if (authority.completedSourceReceipt()?.mediaInventorySha256 == hash) authority.retainCompletedMediaInventory(payload.file)
        for ((receipt, record) in index.admittedSources()) {
            if (record.receipt.mediaInventorySha256 == hash) index.retainMediaInventory(receipt, payload.file)
        }
        if (authority.completedSourceReceipt()?.mediaInventorySha256 == hash && authority.completedMediaInventory() != null) {
            custody.acknowledgeStagedAuxiliary(setOf(payload.file.canonicalPath))
        }
    }
}

/** No key is retired while any complete or interrupted database cohort may still need it. */
internal suspend fun retireStagedNativeAuxiliary(
    context: Context,
    scope: JournalMergeScope,
    preferences: DataStore<Preferences>,
    sourcesCovered: Boolean,
) {
    if (!sourcesCovered) return
    val custody = NativeJournalReplayCustody(context)
    val payloads = custody.sealedPayloads()
    val index = NativeJournalStagedSourceIndex(context)
    val admitted = index.admittedSources()
    if (payloads.any { payload ->
            val path = payload.path.removePrefix(NATIVE_JOURNAL_TRANSFER_PREFIX)
            path.startsWith("database/") &&
                (
                    !payload.complete ||
                        admitted.values.none { record ->
                            payload.file.canonicalPath in record.sourceCorePhysicalPaths[path].orEmpty() &&
                                record.pathSha256[path] == nativeSnapshotChecksum(payload.file)
                        }
                )
        }
    ) {
        return
    }
    for ((receipt, record) in admitted) {
        val expected = record.receipt
        val source =
            normalizeBackendOrigin(expected.backendOrigin)?.let {
                RetainedJournalSourceScope(expected.canonicalOwnerId, expected.canonicalOwnerBound == "true", it)
            } ?: continue
        val verified =
            index
                .verifiedAuxiliary(receipt, payloads)
                .values
                .map { it.canonicalPath }
                .toSet()
        if (verified.isEmpty()) continue
        preferences.updateData { current ->
            val captured = RetainedJournalSourceScope.from(current, scope.serverOrigin)
            if (captured != null &&
                captured.matchesRepositoryScope(scope.ownerId, scope.serverOrigin) &&
                source.matchesCurrentOrAdopted(captured, current)
            ) {
                custody.acknowledgeStagedAuxiliary(verified, record.pathSha256)
            }
            current
        }
    }
}
