package app.logdate.feature.core.settings.ui.devices

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider

/** The account a phone approves new devices on behalf of. */
data class ApprovingAccount(
    val id: String,
    val displayName: String,
)

/** The account and keys a phone hands to a device it approves. */
interface DeviceApprovalAccess {
    suspend fun account(): Result<ApprovingAccount>

    /** Null when this phone never set up or recovered its identity key. */
    suspend fun identityKey(): ByteArray?

    suspend fun legacyMediaKey(): ByteArray
}

class DefaultDeviceApprovalAccess(
    private val accountRepository: PasskeyAccountRepository,
    private val identityKeyManager: IdentityKeyManager,
    private val mediaKeys: MediaPayloadKeyProvider,
) : DeviceApprovalAccess {
    override suspend fun account(): Result<ApprovingAccount> =
        accountRepository.getAccountInfo().map { ApprovingAccount(it.id.toString(), it.displayName) }

    override suspend fun identityKey(): ByteArray? = if (identityKeyManager.hasIdentityKey()) identityKeyManager.getIdentityKey() else null

    override suspend fun legacyMediaKey(): ByteArray = mediaKeys.getOrCreateKey()
}
