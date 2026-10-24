package app.logdate.client.media.storage

/** Serializes durable media claims with a collector's final reference check and unlink. */
interface MediaClaimFence {
    suspend fun <T> withClaim(block: suspend () -> T): T

    suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T

    object Disabled : MediaClaimFence {
        override suspend fun <T> withClaim(block: suspend () -> T): T = block()

        override suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T = error("Media reclamation is disabled")
    }
}
