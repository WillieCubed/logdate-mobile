package app.logdate.client.media.storage

/** A retained attachment whose original bytes have not been recovered. */
object UnavailableMediaReferences {
    private const val PREFIX = "logdate-unavailable://archive/"

    fun isUnavailable(reference: String): Boolean = reference.startsWith(PREFIX)

    fun archive(
        entityId: String,
        blockId: String? = null,
        role: String = "original",
        generation: String? = null,
    ): String = PREFIX + listOfNotNull(generation, entityId, blockId, role).joinToString("/")
}
