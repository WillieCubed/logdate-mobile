package app.logdate.client.sync.cloud

import kotlin.time.Instant

/**
 * What a create-if-absent request found on the server.
 *
 * The server answers `?ifAbsent=true` with `201` when it created the record, `412` with the record's
 * version when it already exists, and `410` with the deletion time when it was deleted, so a repeated
 * create can never overwrite a record or bring a deleted one back.
 */
sealed interface ConditionalCreateResult {
    data class Created(
        val serverVersion: Long,
        val syncedAt: Instant,
    ) : ConditionalCreateResult

    data class AlreadyExists(
        val serverVersion: Long,
    ) : ConditionalCreateResult

    data class Deleted(
        val deletedAt: Instant,
    ) : ConditionalCreateResult
}

/** What the server holds for one content record, read with `HEAD /contents/{id}`. */
sealed interface ContentState {
    data class Present(
        val serverVersion: Long,
    ) : ContentState

    data object Absent : ContentState

    data class Deleted(
        val deletedAt: Instant,
    ) : ContentState
}

/**
 * Answers `HEAD /contents/{id}` carry the record's version here; a deleted record also carries
 * [DELETED_AT_HEADER].
 */
internal const val SERVER_VERSION_HEADER = "X-LogDate-Server-Version"

/** When a deleted record was deleted, in epoch milliseconds. */
internal const val DELETED_AT_HEADER = "X-LogDate-Deleted-At"

/**
 * A request for create-if-absent went to a server that does not list
 * `ServerProtocolFeature.CONDITIONAL_CREATE_V1`. Such a server ignores the condition and overwrites, so
 * the client does not send it.
 */
class ConditionalCreateUnsupportedException : UnsupportedOperationException("This server does not support create-if-absent requests")
