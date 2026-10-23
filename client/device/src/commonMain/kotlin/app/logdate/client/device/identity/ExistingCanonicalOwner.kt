package app.logdate.client.device.identity

/** A persisted journal owner; an unbound local owner is not a Cloud account identifier. */
data class ExistingCanonicalOwner(
    val rawId: String,
    val isBound: Boolean,
)
