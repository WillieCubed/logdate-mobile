package app.logdate.client.sync.space.migration

import app.logdate.shared.model.sync.LegacyInventory
import app.logdate.shared.model.sync.SpaceMigrationDigests

/** The move found a difference it can't fix by itself; [problem] says which, and nothing changed. */
internal class MigrationMismatchException(
    val problem: JournalMoveProblem,
) : Exception("The journal can't move yet: $problem")

/**
 * Compares what legacy storage holds with this device, kind by kind. A kind whose digest matches is
 * the same set of ids; for any other, the ids are fetched and compared one by one, so the problem
 * says how many items differ and in which direction. A difference that waits in this device's outbox
 * is expected: those changes go to the personal space after the switch.
 */
internal class LegacyComparison(
    private val checks: List<LegacyKindCheck>,
    private val idsOf: suspend (kind: String) -> List<String>,
) {
    /** Returns normally when legacy storage and this device hold the same items; throws [MigrationMismatchException] otherwise. */
    suspend fun compare(inventory: LegacyInventory) {
        val legacyKinds = inventory.kinds.associateBy { it.kind }
        for (check in checks) {
            val legacy = legacyKinds[check.legacyKind] ?: continue
            if (!check.syncedHere()) {
                if (legacy.count > 0) throw MigrationMismatchException(JournalMoveProblem.NotSyncedHere(check.dataKind))
                continue
            }
            val local = check.localIds().map { it.lowercase() }.toSet()
            if (sha256Hex(SpaceMigrationDigests.idsText(local)) == legacy.digest) continue
            val remote = idsOf(check.legacyKind).map { it.lowercase() }.toSet()
            val missingHere = remote - local - check.waitingDeletes().map { it.lowercase() }.toSet()
            if (missingHere.isNotEmpty()) {
                throw MigrationMismatchException(
                    JournalMoveProblem.MissingOnThisDevice(check.dataKind, missingHere.size),
                )
            }
            val notOnServer = local - remote - check.waitingCreates().map { it.lowercase() }.toSet()
            if (notOnServer.isNotEmpty()) throw MigrationMismatchException(JournalMoveProblem.NotOnServer(check.dataKind, notOnServer.size))
        }
    }
}
