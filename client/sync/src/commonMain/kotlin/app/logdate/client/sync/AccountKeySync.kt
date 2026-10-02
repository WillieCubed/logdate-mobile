package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.cloud.AccountKeyMaterialDto
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import app.logdate.client.sync.recovery.DownloadInbox
import io.github.aakira.napier.Napier
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Keeps this device's encryption keys and the server's stored copy of the account's keys in
 * agreement, so a new signed-in device can read the account without a recovery phrase.
 */
internal class AccountKeySync(
    private val mediaPayloadKeyProvider: MediaPayloadKeyProvider?,
    private val syncMetadataService: SyncMetadataService,
    private val identityRecoveryNeededStore: IdentityRecoveryNeededStore,
    private val unreadableCloudRecordStore: UnreadableCloudRecordStore,
    private val downloadInbox: DownloadInbox?,
) {
    /** A key this device already holds must belong to the signed-in account and match the account's stored copy. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun reconcile(
        manager: IdentityKeyManager,
        client: CloudApiClient?,
        accessToken: String,
        accountId: String?,
        remote: AccountKeyMaterialDto?,
        vaultRead: Boolean,
    ) {
        if (accountId != null && !manager.bindToAccount(accountId)) {
            Napier.e("This device's encryption key belongs to another account; sync paused")
            identityRecoveryNeededStore.setNeeded(true)
            return
        }
        if (remote != null) {
            val localMedia = mediaPayloadKeyProvider?.getOrCreateKey()
            val matching =
                runCatching {
                    manager.getIdentityKey().contentEquals(Base64.decode(remote.identityKey)) &&
                        (localMedia == null || localMedia.contentEquals(Base64.decode(remote.mediaKey)))
                }.getOrDefault(false)
            if (!matching) {
                Napier.e("Local encryption keys differ from this account's keys; sync paused")
                identityRecoveryNeededStore.setNeeded(true)
                return
            }
        } else if (vaultRead && client != null) {
            publish(client, accessToken, manager)
        }
        if (identityRecoveryNeededStore.isNeeded()) identityRecoveryNeededStore.setNeeded(false)
    }

    /** Signs a new device into the account's existing keys without asking for the recovery phrase. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun install(
        manager: IdentityKeyManager,
        accountId: String,
        remote: AccountKeyMaterialDto,
    ) {
        val identity = runCatching { Base64.decode(remote.identityKey) }.getOrNull()
        val media = runCatching { Base64.decode(remote.mediaKey) }.getOrNull()
        if (identity?.size != KEY_LENGTH_BYTES || media?.size != KEY_LENGTH_BYTES) {
            Napier.e("The server returned invalid account key material; sync paused")
            identityRecoveryNeededStore.setNeeded(true)
            return
        }
        val installed =
            runCatching {
                manager.installAccountKey(accountId, identity)
                mediaPayloadKeyProvider?.installAccountKey(media)
            }
        if (installed.isFailure) {
            Napier.e("Could not install the account key on this device; sync paused")
            identityRecoveryNeededStore.setNeeded(true)
            return
        }
        syncMetadataService.resetAllCursors()
        identityRecoveryNeededStore.setNeeded(false)
        unreadableCloudRecordStore.clear()
        downloadInbox?.release()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun publish(
        client: CloudApiClient,
        accessToken: String,
        manager: IdentityKeyManager,
    ) {
        val media = mediaPayloadKeyProvider?.getOrCreateKey() ?: return
        val material = AccountKeyMaterialDto(Base64.encode(manager.getIdentityKey()), Base64.encode(media))
        client
            .putAccountKeys(accessToken, material)
            .onFailure { Napier.w("Could not store the account key; will retry on the next sync") }
    }

    private companion object {
        const val KEY_LENGTH_BYTES = 32
    }
}
