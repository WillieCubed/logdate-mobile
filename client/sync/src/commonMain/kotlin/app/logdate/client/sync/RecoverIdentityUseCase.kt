package app.logdate.client.sync

import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore

/**
 * Restores this device's identity from a recovery phrase and puts everything that depends on the
 * old, wrong identity back in a state to recover: the media key cached under it is forgotten, the
 * download cursors are reset so records the old identity could not read re-arrive and get another
 * chance, and the "needs your recovery phrase" pause clears.
 *
 * The single entry point for recovering an identity outside of first-time onboarding -- Settings
 * uses this, not [IdentityKeyManager.recoverIdentity] directly, so none of these steps can be
 * forgotten at a second call site.
 */
class RecoverIdentityUseCase(
    private val identityKeyManager: IdentityKeyManager,
    private val syncMetadataService: SyncMetadataService,
    private val mediaPayloadKeyProvider: MediaPayloadKeyProvider,
    private val identityRecoveryNeededStore: IdentityRecoveryNeededStore,
    private val unreadableCloudRecordStore: UnreadableCloudRecordStore,
) {
    suspend operator fun invoke(words: List<String>): Result<Unit> =
        runCatching {
            identityKeyManager.recoverIdentity(words)
            mediaPayloadKeyProvider.clearCachedKey()
            syncMetadataService.resetAllCursors()
            identityRecoveryNeededStore.setNeeded(false)
            unreadableCloudRecordStore.clear()
        }
}
