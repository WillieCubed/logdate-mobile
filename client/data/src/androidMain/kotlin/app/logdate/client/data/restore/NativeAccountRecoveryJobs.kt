package app.logdate.client.data.restore

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.os.UserManager
import app.logdate.client.data.account.RestoreCredentialExchange
import app.logdate.client.data.account.RestoreCredentialRenewal
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.completeNativeAccountRecoveryDelivery
import app.logdate.client.device.restore.nativeRestoreDeliveryGate
import app.logdate.client.device.restore.prepareNativeRestoredInstallationState
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.device.storage.createAndroidSecureStorage
import app.logdate.client.networking.PasskeyApiClient
import app.logdate.client.networking.createNativeRestoreHttpClient
import app.logdate.client.permissions.AndroidRestoreCredentialManager
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.config.PrivacyScopeEpoch
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

private const val NATIVE_ACCOUNT_RECOVERY_JOB = 0x4e415252

internal fun scheduleNativeAccountRecovery(
    context: Context,
    request: NativeAccountRecoveryRequest,
): Boolean =
    try {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val payload = Json.encodeToString(request)
        val existing = scheduler.getPendingJob(NATIVE_ACCOUNT_RECOVERY_JOB)?.extras?.getString("request")
        val retained =
            existing?.let {
                check(it.length <= 4096)
                Json.decodeFromString<NativeAccountRecoveryRequest>(it)
            }
        if (retained?.generation == request.generation) {
            retained == request
        } else {
            val job =
                JobInfo
                    .Builder(NATIVE_ACCOUNT_RECOVERY_JOB, ComponentName(context, NativeAccountRecoveryJobService::class.java))
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .setExtras(PersistableBundle().apply { putString("request", payload) })
                    .build()
            scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        }
    } catch (_: Exception) {
        Napier.w("Native account recovery scheduling remains pending")
        false
    }

/** Reconciles an already retained request without replacing its selected account or server. */
fun resumeNativeAccountRecovery(context: Context) {
    if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
    try {
        val request = NativeAccountRecoveryInbox(context.noBackupFilesDir).pending() ?: return
        val detector = PostRestoreDetector(context)
        if (detector.nativeRestoreSessionId() == request.generation && !detector.hasMissingNativePrivacyPayload()) {
            scheduleNativeAccountRecovery(context, request)
        }
    } catch (_: Exception) {
        Napier.w("Retained native account recovery is awaiting storage")
    }
}

private fun deliveryCurrent(
    detector: PostRestoreDetector,
    request: NativeAccountRecoveryRequest,
): Boolean =
    detector.isNativeRestoreDeliveryComplete() &&
        !detector.hasMissingNativePrivacyPayload() &&
        detector.pendingAccountRecoveryId() == request.delivery &&
        detector.nativeRestoreSessionId() == request.generation

class NativeAccountRecoveryJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null
    private var active: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running?.cancel()
        active = params
        running =
            scope.launch {
                var retry = true
                try {
                    val payload = params.extras.getString("request")
                    retry =
                        if (payload == null || payload.length > 4096) {
                            false
                        } else {
                            val request = Json.decodeFromString<NativeAccountRecoveryRequest>(payload)
                            withTimeout(90_000) { recover(request) }
                        }
                } catch (_: TimeoutCancellationException) {
                    retry = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Napier.w("Native account recovery will retry when dependencies are available")
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        if (active === params) {
                            active = null
                            running = null
                            jobFinished(params, retry)
                        }
                    }
                }
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (active === params) {
            active = null
            running?.cancel()
            running = null
        }
        return true
    }

    override fun onDestroy() {
        active = null
        running = null
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun recover(request: NativeAccountRecoveryRequest): Boolean =
        coroutineScope {
            if (!getSystemService(UserManager::class.java).isUserUnlocked) return@coroutineScope true
            val detector = PostRestoreDetector(this@NativeAccountRecoveryJobService)
            if (!detector.isNativeRestoreDeliveryComplete()) {
                completeNativeAccountRecoveryDelivery(this@NativeAccountRecoveryJobService, request.delivery, request.generation)
            }
            if (!deliveryCurrent(detector, request)) return@coroutineScope false
            val preferences = createAndroidMainDataStore(this@NativeAccountRecoveryJobService)
            val inbox = NativeAccountRecoveryInbox(noBackupFilesDir)
            nativeRestoreDeliveryGate().withLock {
                val snapshot = preferences.data.first()
                if (!deliveryCurrent(detector, request) ||
                    !recoverNativeAccountRecoveryCustody(
                        request,
                        snapshot,
                        inbox,
                        hasRetainedNativeContent(filesDir, getDatabasePath("logdate"), snapshot),
                    )
                ) {
                    return@withLock
                }
                prepareNativeRestoredInstallationState(this@NativeAccountRecoveryJobService, preferences)
            }
            // This is the normal unlocked JobService process. The restricted BackupAgent never resolves DI.
            val koin = GlobalContext.getOrNull() ?: return@coroutineScope true
            koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
            val selected = koin.get<LogDateConfigRepository>()
            val config = nativeRestoreConfiguration(preferences.data.first()) ?: return@coroutineScope true
            if (!nativeRestoreSelectedConfigurationMatches(config, selected)) return@coroutineScope true
            exchangeAndPublish(request, preferences, inbox, detector, koin, config, selected)
        }

    private suspend fun CoroutineScope.exchangeAndPublish(
        request: NativeAccountRecoveryRequest,
        preferences: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        inbox: NativeAccountRecoveryInbox,
        detector: PostRestoreDetector,
        koin: org.koin.core.Koin,
        config: LogDateConfigRepository,
        selected: LogDateConfigRepository,
    ): Boolean {
        val secure = createAndroidSecureStorage(this@NativeAccountRecoveryJobService)
        val observer = SupervisorJob(coroutineContext[Job])
        val client = createNativeRestoreHttpClient()
        return try {
            val vault =
                SecureSessionStorage(
                    secure,
                    config,
                    CoroutineScope(coroutineContext + observer),
                    nativeRestorePolicy = NativeRestorePolicy(DataStoreKeyValueStorage(preferences)),
                    connectedVault = koin.get<SecureSessionStorage>(),
                )
            val attempt =
                NativeAccountRecoveryAttempt(
                    request,
                    preferences,
                    inbox,
                    vault,
                    deliveryCurrent = { deliveryCurrent(detector, request) },
                    hasLocalContent = { hasRetainedNativeContent(filesDir, getDatabasePath("logdate"), it) },
                    exchange = { current ->
                        RestoreCredentialExchange(
                            PasskeyApiClient(client, config),
                            AndroidRestoreCredentialManager(this@NativeAccountRecoveryJobService),
                            current,
                        )()
                    },
                    markRenewal = { RestoreCredentialRenewal(secure).mark(config.getCurrentBackendUrl(), it) },
                    acknowledge = { detector.acknowledgeAccountRecovery(request.delivery) },
                    privacyEpoch = koin.get<PrivacyScopeEpoch>(),
                    selectedConfigurationCurrent = { nativeRestoreSelectedConfigurationMatches(config, selected) },
                )
            if (attempt.run()) {
                false
            } else {
                val current = nativeRestoreConfiguration(preferences.data.first())
                attempt.canRetry() || current == null || !nativeRestoreSelectedConfigurationMatches(current, selected)
            }
        } finally {
            client.close()
            observer.cancelAndJoin()
        }
    }
}

class NativeAccountRecoveryRestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val completion = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                resumeNativeAccountRecovery(context)
            } finally {
                completion.finish()
                scope.cancel()
            }
        }
    }
}
