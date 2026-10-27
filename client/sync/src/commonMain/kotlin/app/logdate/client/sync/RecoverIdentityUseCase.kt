package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.IdentityRecoveryOwnerMismatchException
import app.logdate.client.device.crypto.IdentityRecoverySessionChangedException
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.metadata.IdentityRecoveryNeededStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UnreadableCloudRecordStore
import kotlinx.coroutines.CancellationException

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
    private val downloadInbox: app.logdate.client.sync.recovery.DownloadInbox? = null,
    private val proof: RecoveryPhraseAccountProof,
    private val sessionStorage: SessionStorage,
) {
    suspend operator fun invoke(words: List<String>): Result<Unit> =
        try {
            val originalBinding = sessionStorage.getOriginBoundSession()
            val originalAccount = originalBinding?.session?.accountId ?: sessionStorage.getSession()?.accountId
            val authorization = sessionStorage.captureAuthenticationGuard()
            if (originalAccount == null || (sessionStorage.supportsGuardedAuthentication && authorization == null)) {
                throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
            }
            val verifiedAccount = proof.verify(words)
            if (originalAccount != verifiedAccount) throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
            val sessionCurrent: suspend () -> Boolean = {
                val currentBinding = sessionStorage.getOriginBoundSession()
                (currentBinding?.session?.accountId ?: sessionStorage.getSession()?.accountId) == verifiedAccount &&
                    (originalBinding == null || currentBinding?.origin == originalBinding.origin) &&
                    (authorization == null || sessionStorage.isAuthenticationGuardCurrent(authorization))
            }
            try {
                identityKeyManager.recoverIdentity(
                    words,
                    accountId = verifiedAccount,
                    sessionCurrent = sessionCurrent,
                )
            } catch (_: IdentityRecoveryOwnerMismatchException) {
                throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.MISMATCH)
            } catch (_: IdentityRecoverySessionChangedException) {
                throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
            }
            if (!sessionCurrent()) throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
            mediaPayloadKeyProvider.clearCachedKey()
            syncMetadataService.resetAllCursors()
            identityRecoveryNeededStore.setNeeded(false)
            downloadInbox?.release(afterRecovery = true)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
}
