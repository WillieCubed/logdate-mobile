package app.logdate.client.repository.journals

/** Recovery changes media availability without becoming an editor save or recreating deleted work. */
interface EntryDraftRecoveryRepository {
    /** Raw durable snapshots, with original timestamps and selection provenance. */
    suspend fun getDraftSnapshotsForRecovery(): List<EntryDraft>

    /** Validates both the snapshot and the captured recovery authority inside the durable write. */
    suspend fun compareAndSetDraftForRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean
}
