package app.logdate.client.repository.journals

/** The account and server selected when draft recovery begins; null fields identify local anonymous storage. */
data class DraftRecoveryScope(
    val ownerId: String?,
    val serverOrigin: String?,
    val outboxOwnerId: String? = ownerId,
    val outboxServerOrigin: String? = serverOrigin,
)
