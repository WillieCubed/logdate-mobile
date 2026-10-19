package app.logdate.server.auth

import app.logdate.shared.model.AccountStorageMode
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

/** How each account's journal data is kept, for routes that serve accounts in only one storage mode. */
fun interface AccountStorageModes {
    /** The stored mode of [accountId], or null when there is no such account. */
    suspend fun of(accountId: UUID): AccountStorageMode?
}

/** Reads each account's storage mode from this repository on every request, so a move takes effect at once. */
@OptIn(ExperimentalUuidApi::class)
fun AccountRepository.storageModes(): AccountStorageModes = AccountStorageModes { id -> findById(id.toKotlinUuid())?.storageMode }
