package app.logdate.client.data.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.account.BackgroundRestoreCredentialRecovery
import app.logdate.client.data.account.NativeRestoreAccountPolicy
import app.logdate.client.data.account.RestoreCredentialExchange
import app.logdate.client.data.account.RestoreCredentialRenewal
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.restore.NativeBackupAgent
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.storage.AndroidSecureStorage
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.networking.PasskeyApiClient
import app.logdate.client.networking.createNativeRestoreHttpClient
import app.logdate.client.permissions.AndroidRestoreCredentialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/** Uses only restore-safe dependencies; no Koin, Room, application services, or archive import. */
class NativeAccountBackupAgent : NativeBackupAgent() {
    override suspend fun restoreAccount(preferences: DataStore<Preferences>) =
        coroutineScope {
            val detector = PostRestoreDetector(this@NativeAccountBackupAgent)
            val delivery = detector.pendingAccountRecoveryId() ?: return@coroutineScope
            val snapshot = preferences.data.first()
            val config = nativeRestoreConfiguration(snapshot) ?: return@coroutineScope
            val ownerKey = stringPreferencesKey("identity.canonical_owner_id")
            val boundKey = stringPreferencesKey("identity.canonical_owner_bound")
            val hasLocalData = hasRetainedNativeContent(filesDir, getDatabasePath("logdate"), snapshot)
            val policy = NativeRestoreAccountPolicy(snapshot[ownerKey], snapshot[boundKey], hasLocalData)
            if (!policy.canAttempt) return@coroutineScope
            val secure = AndroidSecureStorage(this@NativeAccountBackupAgent)
            val observerJob = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
            try {
                val vault = SecureSessionStorage(secure, config, CoroutineScope(coroutineContext + observerJob))
                val origin = config.getCurrentBackendUrl()
                val existing = vault.read(origin)
                if (existing != null) {
                    if (policy.accepts(existing.accountId)) detector.acknowledgeAccountRecovery(delivery)
                    return@coroutineScope
                }
                val client = createNativeRestoreHttpClient()
                try {
                    BackgroundRestoreCredentialRecovery(
                        policy = policy,
                        exchange = RestoreCredentialExchange(
                            PasskeyApiClient(client, config),
                            AndroidRestoreCredentialManager(this@NativeAccountBackupAgent),
                        )::invoke,
                        persist = { authenticated ->
                            check(detector.pendingAccountRecoveryId() == delivery)
                            val accountId = authenticated.account.id.toString()
                            preferences.edit { current ->
                                check(current == snapshot) { "Restored configuration changed during authentication" }
                                current[ownerKey] = accountId
                                current[boundKey] = "true"
                            }
                            RestoreCredentialRenewal(secure).mark(origin, accountId)
                            vault.write(origin, UserSession(authenticated.tokens.accessToken, authenticated.tokens.refreshToken, accountId))
                        },
                        acknowledge = { detector.acknowledgeAccountRecovery(delivery) },
                    )()
                    Unit
                } finally {
                    client.close()
                }
            } finally {
                observerJob.cancelAndJoin()
            }
        }
}
