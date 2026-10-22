package app.logdate.client.data.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.account.NativeRestoreAccountPolicy
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.restore.nativeRestoreDeliveryGate
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.shared.model.CompleteAuthenticationData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

internal class NativeAccountRecoveryAttempt(
    private val request: NativeAccountRecoveryRequest,
    private val preferences: DataStore<Preferences>,
    private val inbox: NativeAccountRecoveryInbox,
    private val vault: SecureSessionStorage,
    private val deliveryCurrent: () -> Boolean,
    private val hasLocalContent: (Preferences) -> Boolean,
    private val exchange: suspend (suspend () -> Boolean) -> Result<CompleteAuthenticationData>,
    private val markRenewal: suspend (String) -> Unit,
    private val acknowledge: () -> Unit,
    private val privacyEpoch: app.logdate.shared.config.PrivacyScopeEpoch? = null,
    private val selectedConfigurationCurrent: () -> Boolean = { true },
) {
    private val ownerKey = stringPreferencesKey("identity.canonical_owner_id")
    private val boundKey = stringPreferencesKey("identity.canonical_owner_bound")
    private val stampKey = stringPreferencesKey("identity.native_restore_commit")

    private var epochCaptured = false
    private var expectedEpoch: String? = null

    suspend fun canRetry(): Boolean = current(preferences.data.first())

    suspend fun isCurrent(): Boolean = (!epochCaptured || privacyEpoch?.snapshot() == expectedEpoch) && current(preferences.data.first())

    private suspend fun current(snapshot: Preferences): Boolean {
        if (!deliveryCurrent() || !selectedConfigurationCurrent() || inbox.pending() != request) return false
        val actual = nativeAccountRecoveryRequest(request.delivery, request.generation, snapshot) ?: return false
        if (actual == request) return NativeRestoreAccountPolicy(actual.owner, actual.binding, hasLocalContent(snapshot)).canAttempt
        val target = inbox.acceptedTarget(request) ?: return false
        return actual.owner == target &&
            actual.binding == "true" &&
            snapshot[stampKey] == nativeAccountRecoveryCommitStamp(request, target) &&
            actual.copy(owner = request.owner, binding = request.binding) == request
    }

    suspend fun run(): Boolean =
        try {
            val guard =
                nativeRestoreDeliveryGate().withLock {
                    expectedEpoch = privacyEpoch?.snapshot()
                    epochCaptured = true
                    if (!isCurrent()) return@withLock null
                    vault.captureInstallationGuard()
                }
            if (guard == null) {
                false
            } else {
                val existing = vault.getSession()
                val session =
                    existing ?: exchange(::isCurrent).getOrThrow().let {
                        currentCoroutineContext().ensureActive()
                        UserSession(it.tokens.accessToken, it.tokens.refreshToken, it.account.id.toString())
                    }
                val policy = NativeRestoreAccountPolicy(request.owner, request.binding, false)
                val target = inbox.acceptedTarget(request)
                if (!policy.accepts(session.accountId) || (target != null && target != session.accountId)) {
                    false
                } else {
                    if (existing == null) markRenewal(session.accountId)
                    nativeRestoreDeliveryGate().withLock {
                        if (!isCurrent()) return@withLock false
                        val installed =
                            vault.installNativeRestoreSessionIfCurrent(guard, session, privacyEpoch, expectedEpoch) {
                                var admitted = false
                                preferences.edit { snapshot ->
                                    if (current(snapshot) && inbox.acceptTarget(request, session.accountId)) {
                                        snapshot[ownerKey] = session.accountId
                                        snapshot[boundKey] = "true"
                                        snapshot[stampKey] = nativeAccountRecoveryCommitStamp(request, session.accountId)
                                        admitted = true
                                    }
                                }
                                admitted
                            }
                        currentCoroutineContext().ensureActive()
                        if (!installed || !deliveryCurrent()) return@withLock false
                        acknowledge()
                        inbox.acknowledge(request)
                        true
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            Napier.w("Native account recovery will retry from retained state")
            false
        }
}
