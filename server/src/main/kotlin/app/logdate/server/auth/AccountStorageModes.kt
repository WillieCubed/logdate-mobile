package app.logdate.server.auth

import app.logdate.server.migration.SpaceMigrationHolds
import app.logdate.shared.model.AccountStorageMode
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

/** How each account's journal data is kept, for routes that serve accounts in only one storage mode. */
fun interface AccountStorageModes {
    /** The stored mode of [accountId], or null when there is no such account. */
    suspend fun of(accountId: UUID): AccountStorageMode?

    /** Whether legacy writes for [accountId] wait while its journal is checked before moving into its personal space. */
    suspend fun legacyWritesHeld(accountId: UUID): Boolean = false

    /** Whether [accountId]'s journal was moved into its personal space, rather than starting there. */
    suspend fun movedByMigration(accountId: UUID): Boolean = false
}

/**
 * Reads each account's storage mode from this repository on every request, so a move takes effect at
 * once, and asks [holds] about accounts moving between stores.
 */
@OptIn(ExperimentalUuidApi::class)
fun AccountRepository.storageModes(holds: SpaceMigrationHolds = SpaceMigrationHolds.None): AccountStorageModes {
    val repository = this
    return object : AccountStorageModes {
        override suspend fun of(accountId: UUID): AccountStorageMode? = repository.findById(accountId.toKotlinUuid())?.storageMode

        override suspend fun legacyWritesHeld(accountId: UUID): Boolean = holds.legacyWritesHeld(accountId)

        override suspend fun movedByMigration(accountId: UUID): Boolean = holds.movedByMigration(accountId)
    }
}
