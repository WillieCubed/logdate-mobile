package app.logdate.client.sync.peer.delivery

/** Durable receipt identity for one outbox operation, including equal-timestamp edits. */
class CaptureIdentity private constructor(
    val recordId: String,
    val operationId: String,
) {
    val ledgerKey: String get() = "$recordId@$operationId"

    companion object {
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
