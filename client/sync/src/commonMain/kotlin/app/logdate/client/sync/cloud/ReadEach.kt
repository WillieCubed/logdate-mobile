package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import app.logdate.client.sync.crypto.LegacyUnreadablePayloadException
import app.logdate.client.sync.crypto.UnreadablePayloadException
import app.logdate.client.sync.crypto.UnsupportedPayloadVersionException
import app.logdate.client.sync.crypto.WrongKeyPayloadException
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/** Identity and wire version are operational queue data, never diagnostic fields. */
data class RemoteRecordFailure(
    val entityId: String,
    val serverVersion: Long,
    val reason: DiagnosticReason,
)

internal class UnsupportedRemoteFormatException : Exception("Unsupported remote format")

internal data class ReadRecords<T>(
    val readable: List<T>,
    val unreadable: List<Uuid>,
    val failures: List<RemoteRecordFailure>,
    val unreadableVersions: Map<Uuid, Long>,
)

internal suspend fun <C, T> List<C>.readEach(
    idOf: (C) -> String,
    versionOf: (C) -> Long,
    read: suspend (C) -> T,
): ReadRecords<T> {
    val readable = mutableListOf<T>()
    val unreadable = mutableListOf<Uuid>()
    val failures = mutableListOf<RemoteRecordFailure>()
    val unreadableVersions = mutableMapOf<Uuid, Long>()
    for (change in this) {
        try {
            readable += read(change)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (missing: IdentityKeyNotFoundException) {
            throw missing
        } catch (failure: Exception) {
            val id = idOf(change)
            val repairable =
                failure is UnreadablePayloadException &&
                    (failure.cause is WrongKeyPayloadException || failure.cause is LegacyUnreadablePayloadException)
            val parsed = if (repairable) runCatching { Uuid.parse(id) }.getOrNull() else null
            if (parsed != null) {
                unreadable += parsed
                unreadableVersions[parsed] = versionOf(change)
            } else {
                failures +=
                    RemoteRecordFailure(
                        id,
                        versionOf(change),
                        if (failure is UnsupportedRemoteFormatException ||
                            failure is UnsupportedPayloadVersionException
                        ) {
                            DiagnosticReason.UNSUPPORTED_FORMAT
                        } else {
                            DiagnosticReason.CORRUPT_PAYLOAD
                        },
                    )
            }
        }
    }
    return ReadRecords(readable, unreadable, failures, unreadableVersions)
}

/** Result conversion must not turn a cancelled download into an ordinary retry failure. */
internal suspend fun <T, R> Result<T>.mapRecordPage(read: suspend (T) -> R): Result<R> {
    exceptionOrNull()?.let {
        if (it is CancellationException) throw it
        return Result.failure(it)
    }
    return try {
        Result.success(read(getOrThrow()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
