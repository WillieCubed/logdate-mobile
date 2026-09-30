package app.logdate.client.repository.location

/** Reconciles only a previously consented, durably recorded local-owner adoption. */
interface HistoryOwnerAdoptionRepair {
    suspend fun reconcile(
        ownerId: String,
        origin: String,
    )
}
