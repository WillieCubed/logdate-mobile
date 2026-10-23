package app.logdate.server.migration

import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.sync.SpaceMigrationPhase
import java.util.UUID
import kotlin.time.Clock

/** Why a personal-space write is refused while an account moves between stores. */
sealed interface SpaceWriteRefusal {
    /** A device is downloading the space before switching the account back; send the write again later. */
    data object Held : SpaceWriteRefusal

    /** The account was switched back to legacy storage; the device should read the account again. */
    data object StorageChanged : SpaceWriteRefusal
}

/**
 * Answers the questions the sync routes ask about an account that is moving between stores. Without
 * a store, as when the migration is turned off, nothing is ever held or moved.
 */
class SpaceMigrationHolds(
    private val store: SpaceMigrationStore?,
    private val config: SpaceMigrationConfig,
    private val clock: Clock = StoredPrecisionClock(),
) {
    /** Whether legacy writes for [accountId] wait while a device checks its copy before the switch. */
    suspend fun legacyWritesHeld(accountId: UUID): Boolean {
        val migration = store?.get(accountId) ?: return false
        return migration.phase == SpaceMigrationPhase.UPLOADING && migration.holdsAt(clock)
    }

    /** Whether [accountId] reached its personal space by moving its journal there, rather than starting there. */
    suspend fun movedByMigration(accountId: UUID): Boolean =
        store?.get(accountId)?.phase in setOf(SpaceMigrationPhase.SWITCHED, SpaceMigrationPhase.CLEANED)

    /** Why a write to [accountId]'s personal space must wait or go elsewhere, or null when it may go ahead. */
    suspend fun spaceWriteRefusal(
        accountId: UUID,
        storageMode: AccountStorageMode?,
    ): SpaceWriteRefusal? {
        val migration = store?.get(accountId) ?: return null
        return when {
            migration.phase == SpaceMigrationPhase.SWITCHED && migration.holdsAt(clock) -> SpaceWriteRefusal.Held
            migration.phase == SpaceMigrationPhase.REVERTED && storageMode == AccountStorageMode.LEGACY -> SpaceWriteRefusal.StorageChanged
            else -> null
        }
    }

    private fun SpaceMigration.holdsAt(clock: Clock): Boolean {
        val started = holdStartedAt ?: return false
        return clock.now() < started + config.holdLifetime
    }

    companion object {
        /** Nothing is ever held: the migration is off. */
        val None: SpaceMigrationHolds = SpaceMigrationHolds(null, SpaceMigrationConfig())
    }
}
