package app.logdate.client.sync.recovery

import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.DraftAssetMapping
import app.logdate.client.sync.DraftMediaFailure
import app.logdate.client.sync.JOURNAL_COVER_KIND
import app.logdate.client.sync.SyncMediaTransfer
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.space.SpaceMediaReference
import app.logdate.shared.model.diagnostics.DiagnosticReason
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/**
 * Fetches the cover of a journal applied from the personal space, after the journal itself is
 * visible, and points the journal at the saved file. The journal's record keeps the uploaded file.
 */
internal class SyncJournalCoverRecovery(
    private val inbox: DownloadInbox,
    private val journals: JournalRepository,
    private val transfer: SyncMediaTransfer,
    private val refs: MediaSyncRefStore,
    private val transactions: SyncTransactionManager,
) {
    suspend fun recover(accessToken: String) {
        val scope = inbox.currentScope()
        for (row in inbox.pending("MEDIA_JOURNAL", scope)) {
            if (scope != inbox.currentScope()) return
            recoverRow(accessToken, scope, row)
        }
    }

    private suspend fun recoverRow(
        accessToken: String,
        scope: DownloadScope,
        row: DownloadInboxEntity,
    ) {
        try {
            val id = Uuid.parse(row.entityId)
            val cover =
                journals
                    .getJournalById(id)
                    ?.takeIf { it.id == id }
                    ?.coverImageUri
                    ?.takeIf { SpaceMediaReference.parse(it) != null }
            if (cover == null) {
                inbox.applied("MEDIA_JOURNAL", row.entityId, row.version, scope)
                return
            }
            val mappings = mutableListOf<DraftAssetMapping>()
            val local =
                transfer.hydrateDraftAsset(
                    accessToken,
                    id,
                    id,
                    JOURNAL_COVER_KIND,
                    cover,
                    mappings,
                ) { scope == inbox.currentScope() }
            transactions.withTransaction {
                if (scope != inbox.currentScope()) throw CancellationException("Download scope changed")
                val syncable = journals as? SyncableJournalRepository ?: error("Sync repository unavailable")
                if (syncable.updateCoverImageUri(id, cover, requireNotNull(local))) {
                    mappings.forEach {
                        refs.upsertDraftAsset(
                            it.draftId,
                            it.blockId,
                            it.kind,
                            it.ref.copy(ownerId = scope.owner, serverOrigin = scope.origin),
                        )
                    }
                }
                inbox.applied("MEDIA_JOURNAL", row.entityId, row.version, scope)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: DraftMediaFailure) {
            inbox.failed("MEDIA_JOURNAL", row.entityId, failure.reason.name, row.version, scope)
        } catch (error: Exception) {
            Napier.e("Journal cover recovery failed: ${error::class.simpleName}")
            inbox.failed("MEDIA_JOURNAL", row.entityId, DiagnosticReason.LOCAL_STORAGE.name, row.version, scope)
        }
    }
}
