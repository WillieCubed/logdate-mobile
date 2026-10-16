package app.logdate.client.datastore

import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/** Non-secret account restore authorization that travels in native application backup. */
class NativeRestorePolicy(
    private val read: suspend (String) -> String?,
    private val write: suspend (String, String) -> Unit,
) {
    constructor(storage: KeyValueStorage) : this(storage::getStringOrThrow, storage::putStringOrThrow)

    suspend fun decision(origin: String): String? = read(key(origin))

    suspend fun setAllowed(
        origin: String,
        allowed: Boolean,
    ) {
        write(key(origin), "${if (allowed) "allowed" else "disabled"}:${Uuid.random()}")
    }

    suspend fun canRestore(origin: String): Boolean = allows(decision(origin))

    private fun key(origin: String): String =
        "native.restore.authorization.${Base64.UrlSafe.encode(origin.trim().trimEnd('/').encodeToByteArray()).trimEnd('=')}"

    companion object {
        fun allows(decision: String?): Boolean {
            if (decision == null) return true
            if (!decision.startsWith("allowed:")) return false
            return runCatching { Uuid.parse(decision.substringAfter(':')) }.isSuccess
        }
    }
}
