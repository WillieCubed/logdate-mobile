package app.logdate.client.sync.recovery

import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.shared.model.sync.AssociationChange
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.AssociationDeletion
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.ContentDeletion
import app.logdate.shared.model.sync.JournalChange
import app.logdate.shared.model.sync.JournalChangesResponse
import app.logdate.shared.model.sync.JournalDeletion
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stages encrypted wire records and the checkpoint before any domain conversion. */
class DurableCloudApiClient(
    private val delegate: CloudApiClient,
    private val inbox: DownloadInbox,
) : CloudApiClient by delegate {
    private val json = Json { encodeDefaults = true }

    override suspend fun getContentChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<ContentChangesResponse> =
        safely {
            val scope = inbox.currentScope()
            val diagnosticSource = inbox.captureDiagnosticSource(scope)
            val fetched = delegate.getContentChanges(accessToken, inbox.cursor("NOTE", scope), limit)
            val page = fetched.getOrNull()
            if (page != null) {
                inbox.stage(
                    "NOTE",
                    page.lastTimestamp,
                    page.changes.map { WireDownload(it.id, it.serverVersion, false, json.encodeToString(it)) } +
                        page.deletions.map { WireDownload(it.id, it.serverVersion, true, json.encodeToString(it)) },
                    scope,
                )
            }
            val pending = inbox.pending("NOTE", scope)
            handleFetchFailure(fetched, "NOTE", scope, pending.isNotEmpty())
            check(scope == inbox.currentScope()) { "Download scope changed" }
            ContentChangesResponse(
                changes = decodeRows<ContentChange>(pending.filterNot { it.deleted }, "NOTE", scope, diagnosticSource),
                deletions = decodeRows<ContentDeletion>(pending.filter { it.deleted }, "NOTE", scope, diagnosticSource),
                lastTimestamp = inbox.cursor("NOTE", scope),
                hasMore = page?.hasMore ?: false,
            )
        }

    override suspend fun getJournalChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<JournalChangesResponse> =
        safely {
            val scope = inbox.currentScope()
            val diagnosticSource = inbox.captureDiagnosticSource(scope)
            val fetched = delegate.getJournalChanges(accessToken, inbox.cursor("JOURNAL", scope), limit)
            val page = fetched.getOrNull()
            if (page != null) {
                inbox.stage(
                    "JOURNAL",
                    page.lastTimestamp,
                    page.changes.map { WireDownload(it.id, it.serverVersion, false, json.encodeToString(it)) } +
                        page.deletions.map { WireDownload(it.id, it.serverVersion, true, json.encodeToString(it)) },
                    scope,
                )
            }
            val pending = inbox.pending("JOURNAL", scope)
            handleFetchFailure(fetched, "JOURNAL", scope, pending.isNotEmpty())
            check(scope == inbox.currentScope()) { "Download scope changed" }
            JournalChangesResponse(
                changes = decodeRows<JournalChange>(pending.filterNot { it.deleted }, "JOURNAL", scope, diagnosticSource),
                deletions = decodeRows<JournalDeletion>(pending.filter { it.deleted }, "JOURNAL", scope, diagnosticSource),
                lastTimestamp = inbox.cursor("JOURNAL", scope),
                hasMore = page?.hasMore ?: false,
            )
        }

    override suspend fun getAssociationChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<AssociationChangesResponse> =
        safely {
            val scope = inbox.currentScope()
            val diagnosticSource = inbox.captureDiagnosticSource(scope)
            val fetched = delegate.getAssociationChanges(accessToken, inbox.cursor("ASSOCIATION", scope), limit)
            val page = fetched.getOrNull()
            if (page != null) {
                inbox.stage(
                    "ASSOCIATION",
                    page.lastTimestamp,
                    page.changes.map {
                        WireDownload(
                            it.journalId + "::" + it.contentId,
                            it.serverVersion,
                            false,
                            json.encodeToString(it),
                        )
                    } +
                        page.deletions.map {
                            WireDownload(
                                it.journalId + "::" + it.contentId,
                                it.serverVersion,
                                true,
                                json.encodeToString(it),
                            )
                        },
                    scope,
                )
            }
            val pending = inbox.pending("ASSOCIATION", scope)
            handleFetchFailure(fetched, "ASSOCIATION", scope, pending.isNotEmpty())
            check(scope == inbox.currentScope()) { "Download scope changed" }
            AssociationChangesResponse(
                changes = decodeRows<AssociationChange>(pending.filterNot { it.deleted }, "ASSOCIATION", scope, diagnosticSource),
                deletions = decodeRows<AssociationDeletion>(pending.filter { it.deleted }, "ASSOCIATION", scope, diagnosticSource),
                lastTimestamp = inbox.cursor("ASSOCIATION", scope),
                hasMore = page?.hasMore ?: false,
            )
        }

    override suspend fun getDraftChanges(
        accessToken: String,
        since: Long,
        limit: Int?,
    ): Result<app.logdate.shared.model.sync.DraftChangesResponse> =
        safely {
            val scope = inbox.currentScope()
            val diagnosticSource = inbox.captureDiagnosticSource(scope)
            val fetched = delegate.getDraftChanges(accessToken, inbox.cursor("DRAFT", scope), limit)
            val page = fetched.getOrNull()
            if (page != null) {
                val records =
                    page.drafts.map { draft ->
                        if (draft.isDeleted) {
                            val deletion =
                                app.logdate.shared.model.sync
                                    .DraftDeletion(draft.id, draft.lastUpdated, draft.serverVersion)
                            WireDownload(draft.id, draft.serverVersion, true, json.encodeToString(deletion))
                        } else {
                            WireDownload(draft.id, draft.serverVersion, false, json.encodeToString(draft))
                        }
                    } + page.deletions.map { WireDownload(it.id, it.serverVersion, true, json.encodeToString(it)) }
                inbox.stage("DRAFT", page.lastTimestamp, records, scope)
            }
            val pending = inbox.pending("DRAFT", scope)
            handleFetchFailure(fetched, "DRAFT", scope, pending.isNotEmpty())
            check(scope == inbox.currentScope()) { "Download scope changed" }
            app.logdate.shared.model.sync.DraftChangesResponse(
                drafts =
                    decodeRows<app.logdate.shared.model.sync.DraftChange>(
                        pending.filterNot { it.deleted },
                        "DRAFT",
                        scope,
                        diagnosticSource,
                    ),
                deletions =
                    decodeRows<app.logdate.shared.model.sync.DraftDeletion>(
                        pending.filter { it.deleted },
                        "DRAFT",
                        scope,
                        diagnosticSource,
                    ),
                lastTimestamp = inbox.cursor("DRAFT", scope),
                hasMore = page?.hasMore ?: false,
            )
        }

    private suspend inline fun <reified T : Any> decodeRows(
        rows: List<DownloadInboxEntity>,
        type: String,
        selected: DownloadScope,
        diagnosticSource: app.logdate.client.sync.diagnostics.DiagnosticSource?,
    ): List<T> {
        val readable = mutableListOf<T>()
        for (row in rows) {
            val decoded =
                try {
                    json.decodeFromString<T>(row.payload)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            if (decoded != null) {
                readable += decoded
            } else {
                // Persistence errors must fail the operation, not masquerade as malformed input.
                inbox.failed(type, row.entityId, DiagnosticReason.CORRUPT_PAYLOAD.name, row.version, selected, source = diagnosticSource)
            }
        }
        return readable
    }

    private suspend fun <T> handleFetchFailure(
        result: Result<T>,
        type: String,
        scope: DownloadScope,
        canReplay: Boolean,
    ) {
        val error = result.exceptionOrNull() ?: return
        if (error is CancellationException) throw error
        val reason =
            when ((error as? CloudApiException)?.statusCode) {
                401, 403 -> DiagnosticReason.SIGN_IN_REQUIRED
                429 -> DiagnosticReason.RATE_LIMITED
                else -> DiagnosticReason.SERVER_UNAVAILABLE
            }
        inbox.fetchFailed(type, reason, scope)
        if (!canReplay) throw error
    }

    private suspend fun <T> safely(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
}
