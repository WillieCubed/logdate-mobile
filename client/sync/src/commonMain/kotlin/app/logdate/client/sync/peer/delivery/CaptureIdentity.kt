package app.logdate.client.sync.peer.delivery

/** Durable receipt identity for one outbox operation, including equal-timestamp edits. */
class CaptureIdentity private constructor(
    val recordId: String,
    val operationId: String,
) {
    val ledgerKey: String get() = "$recordId@$operationId"

    val removalLedgerKey: String get() = "$ledgerKey:remove"

    companion object {
        fun fromLedgerKey(key: String): CaptureIdentity? {
            val split = key.indexOf('@')
            if (split < 0) return null
            return of(key.substring(0, split), key.substring(split + 1))
        }

        fun of(
            recordId: String,
            operationId: String?,
        ): CaptureIdentity? {
            if (recordId.isBlank() || recordId.length > 128 || '@' in recordId) return null
            if (operationId == null || !operationId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return null
            return CaptureIdentity(recordId, operationId)
        }
    }
}
