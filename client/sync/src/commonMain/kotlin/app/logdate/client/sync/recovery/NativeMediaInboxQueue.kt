package app.logdate.client.sync.recovery

import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlin.uuid.Uuid

/** Native media work is scoped local recovery, separate from remote record watermarks. */
internal class NativeMediaInboxQueue(
    private val dao: DownloadInboxDao,
    private val transactions: SyncTransactionManager,
    private val scope: () -> DownloadScope,
) {
    suspend fun enqueue(
        type: String,
        id: String,
        version: Long,
        reference: String,
        selected: DownloadScope,
    ) {
        require(type in setOf("MEDIA_NATIVE_NOTE", "MEDIA_NATIVE_DRAFT", "MEDIA_NATIVE_ENTRY_DRAFT"))
        transactions.withTransaction {
            check(selected == scope()) { "Native media recovery scope changed" }
            val prior = dao.get(selected.owner, selected.origin, type, id)
            if (prior != null && prior.state != "APPLIED" && prior.version == version && prior.payload == reference) return@withTransaction
            dao.put(
                DownloadInboxEntity(
                    selected.owner,
                    selected.origin,
                    type,
                    id,
                    version,
                    false,
                    reference,
                    Uuid.random().toString(),
                    "WAITING",
                    0,
                    0,
                    DiagnosticReason.NONE.name,
                ),
            )
            check(selected == scope()) { "Native media recovery scope changed" }
        }
    }
}
