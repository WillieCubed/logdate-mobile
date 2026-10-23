package app.logdate.client.sync.space.migration

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.space.SpaceAccess
import app.logdate.client.sync.space.SpaceClient
import app.logdate.client.sync.space.SpaceOutgoing
import app.logdate.client.sync.space.SpaceRecordState
import app.logdate.client.sync.space.SpaceSwapStore
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import app.logdate.space.ChangePage
import app.logdate.space.DataHome
import app.logdate.space.RecordSwap
import app.logdate.space.SealedRecordCodec
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ATTEMPTS = 5
private val FIRST_WAIT = 2.seconds

/**
 * Writes the move's sealed records to the personal space and reads them back. Until the switch only
 * the moving device writes there, so each write replaces whatever the key holds: a resumed move
 * writes an item again without first reading what an interrupted attempt left.
 *
 * A refusal for rate limiting, a busy server or a dropped connection is waited out and tried again a
 * few times, keeping the server's `Retry-After`; anything else is thrown for the move to report.
 */
internal class SpaceMigrationWriter(
    private val client: SpaceClient,
    private val access: SpaceAccess,
    private val swapStore: SpaceSwapStore,
    private val wait: suspend (Duration) -> Unit = { delay(it) },
) {
    /** Seals and writes [records], remembering each one's version so sync after the switch can update it. */
    suspend fun write(records: List<SpaceOutgoing>) {
        for (record in records) {
            val sealed =
                SealedRecordCodec.seal(
                    access.keys,
                    access.space,
                    access.author,
                    SealedPayload(record.collection, record.record, record.rkey),
                )
            val stored =
                call("putRecord(${record.collection})") {
                    putRecord(access.space, sealed.rkey, sealed.record, record.blobs, RecordSwap.Unconditional)
                }
            swapStore.put(access.scope, SpaceRecordState(record.collection, record.rkey, sealed.rkey, stored.cid, stored.seq))
        }
    }

    /** Deletes the record under [sealedRkey], which no item on this device has. */
    suspend fun delete(sealedRkey: String) {
        call("deleteRecord") { deleteRecord(access.space, sealedRkey, RecordSwap.Unconditional) }
    }

    /** One page of the space's change log after [since]. */
    suspend fun changes(since: Long): ChangePage = call("listChanges") { listChanges(access.space, since, PAGE_SIZE) }

    /** The sealed key [collection] and [rkey] are stored under. */
    suspend fun sealedRkey(
        collection: String,
        rkey: String,
    ): String = SealedRecordCodec.sealedRkey(access.keys, collection, rkey)

    private suspend fun <T> call(
        operation: String,
        block: suspend DataHome.() -> T,
    ): T {
        var waitFor = FIRST_WAIT
        repeat(ATTEMPTS - 1) {
            val result = client.call(operation, block)
            val error = result.exceptionOrNull() ?: return result.getOrThrow()
            if (!error.isTransient()) throw error
            wait(maxOf(waitFor, (error as? CloudApiException)?.retryAfterMillis?.milliseconds ?: Duration.ZERO))
            waitFor *= 2
        }
        return client.call(operation, block).getOrThrow()
    }

    private companion object {
        const val PAGE_SIZE = 200
    }
}

/** Whether waiting and sending the same request again can succeed. */
internal fun Throwable.isTransient(): Boolean {
    val refusal = this as? CloudApiException ?: return false
    return refusal.errorCode == "NETWORK_ERROR" || refusal.statusCode in TRANSIENT_STATUSES
}

private val TRANSIENT_STATUSES = setOf(408, 429, 502, 503, 504)
