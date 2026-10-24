package app.logdate.client.media.storage

/** Returns every durable reference that must keep local media alive. Errors must fail collection. */
fun interface MediaOwnerCensus {
    suspend fun references(): Set<String>
}
