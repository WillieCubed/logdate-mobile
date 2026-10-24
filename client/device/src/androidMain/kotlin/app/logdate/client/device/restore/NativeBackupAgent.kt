package app.logdate.client.device.restore

import android.annotation.TargetApi
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.app.backup.FullRestoreDataInput
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.crypto.AndroidCryptoManager
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.storage.createAndroidSecureStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

/** Records platform callbacks without opening the database or starting application services. */
open class NativeBackupAgent : BackupAgent() {
    protected open val retainedPreferenceNames: Set<String> = emptySet()
    private val nativeRestoreSession =
        NativeRestoreSessionBoundary {
            PostRestoreDetector(this).beginNativeRestoreSession(retainPreferences = retainedPreferenceNames.isNotEmpty())
            retainOptionalPreferences { begin(retainedPreferenceNames) }
        }
    private var incomingGeneration: String? = null
    private val incomingRestoreSession =
        NativeRestoreSessionBoundary {
            incomingGeneration =
                kotlin.uuid.Uuid
                    .random()
                    .toString()
        }
    private val callbackLock = Any()
    private val restoreFormat = NativeRestoreFormatGuard { noBackupFilesDir }
    private var restoreCompletionRecorded = false
    private var installationStateReset = false

    override fun onCreate() {
        super.onCreate()
        runBlocking(Dispatchers.IO) { createAndroidInstallationWatchDataStore(this@NativeBackupAgent).data.first() }
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor,
    ) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        requireNativeAndroidExport(data.transportFlags)
        runBlocking {
            AndroidNativeRestoreDeliveryGate.withLock {
                requireRetainedJournalBackupReady(
                    this@NativeBackupAgent,
                    deviceTransfer = data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0,
                )
                try {
                    runBlocking {
                        IdentityKeyManager(
                            createAndroidSecureStorage(this@NativeBackupAgent),
                            AndroidCryptoManager(),
                            AndroidIdentityKeyBackupStore(this@NativeBackupAgent),
                        ).ensureExistingIdentityBackup()
                    }
                } catch (error: Exception) {
                    throw IOException("Native encryption recovery material could not be prepared", error)
                }
                // Backup may run before the first normal launch after an upgrade.
                if (PostRestoreDetector(
                        this@NativeBackupAgent,
                    ).hasMissingNativePrivacyPayload()
                ) {
                    throw NativeRestorePrivacyMetadataRequiredException()
                }
                runBlocking(Dispatchers.IO) {
                    NativeRestorePrivacyPolicy.prepareBackup(createAndroidMainDataStore(this@NativeBackupAgent)) { snapshot ->
                        canEstablishNativePrivacyDefaults(this@NativeBackupAgent, snapshot)
                    }
                }
                super.onFullBackup(data)
            }
        }
    }

    override fun onRestoreFinished() {
        AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
        try {
            if (synchronized(callbackLock) { restoreFormat.isRejected() }) {
                super.onRestoreFinished()
                return
            }
            if (finishRetainedNativeDelivery()) {
                // A late delivery must not reset the accepted working installation or its account.
                scheduleRetainedJournalReconciliationSafely()
                super.onRestoreFinished()
                return
            }
            val duplicateCompletion =
                synchronized(callbackLock) {
                    if (restoreCompletionRecorded) {
                        true
                    } else if (!nativeRestoreSession.isStarted() && PostRestoreDetector(this).hasCompletedNativeRestoreDelivery()) {
                        restoreCompletionRecorded = true
                        true
                    } else {
                        beginNativeRestoreSessionOnce(resumeFinalization = true)
                        false
                    }
                }
            super.onRestoreFinished()
            if (duplicateCompletion) {
                retryAccountRecovery()
                return
            }
            val finalDelivery = restoredPreferences().recordFinalDelivery(retainedPreferenceNames)
            retainOptionalPreferences { finish(retainedPreferenceNames, finalDelivery) }
            val detector = PostRestoreDetector(this)
            val preferences = createAndroidMainDataStore(this)
            val admission = detector.reserveAccountRecoveryForCompletion()
            runBlocking(Dispatchers.IO) {
                withTimeout(15_000) { retainAccountRecoveryBeforeCompletion(preferences, admission) }
            }
            detector.recordRestoreCompletedUnderDeliveryLock()
            synchronized(callbackLock) { restoreCompletionRecorded = true }
            try {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(15_000) {
                        retainAccountRecovery(preferences, freshDelivery = true)
                        prepareRestoredInstallationState(preferences)
                        restoreAccount(preferences)
                    }
                }
            } catch (_: Exception) {
                Napier.w("Background native account recovery remains pending")
            }
        } finally {
            nativeRestoreSession.finish()
            incomingRestoreSession.finish()
            AndroidNativeRestoreDeliveryGate.endRestore()
        }
    }

    private fun finishRetainedNativeDelivery(): Boolean =
        synchronized(callbackLock) {
            val workspace = retainedJournalWorkspace(this)
            workspace.prepareNativeDelivery().also { if (it) workspace.finishIncomingDelivery() }
        }

    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long,
    ) {
        restoreNativeFile(destination, data, size, fileType = type) { descriptor, target ->
            super.onRestoreFile(descriptor, size, target, type, mode, mtime)
        }
    }

    @TargetApi(Build.VERSION_CODES.BAKLAVA)
    override fun onRestoreFile(data: FullRestoreDataInput) {
        restoreNativeFile(
            data.destination,
            data.data,
            data.size,
            fileType = data.type,
            nativeFormat =
                data.transportFlags and FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS == 0 && data.contentVersion.isEmpty(),
        ) { descriptor, target ->
            super.onRestoreFile(descriptor, data.size, target, data.type, data.mode, data.modificationTimeSeconds)
        }
    }

    private fun restoreNativeFile(
        destination: File,
        data: ParcelFileDescriptor,
        size: Long,
        nativeFormat: Boolean? = null,
        fileType: Int = TYPE_FILE,
        restore: (ParcelFileDescriptor, File) -> Unit,
    ) {
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        try {
            synchronized(callbackLock) {
                restoreFormat.requireNativeFile(nativeFormat)
                restoreCompletionRecorded = false
                installationStateReset = false
                val workspace = retainedJournalWorkspace(this)
                if (workspace.prepareNativeDelivery()) {
                    if (fileType == TYPE_DIRECTORY) return@synchronized
                    if (fileType != TYPE_FILE) throw IOException("Native journal delivery must contain regular files")
                    incomingRestoreSession.beginIfNeeded()
                    val cohort = workspace.beginIncomingDelivery(checkNotNull(incomingGeneration))
                    ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(data.fileDescriptor)).use { input ->
                        workspace.captureIncomingFile(cohort, retainedNativeDestination(this, destination), size, input)
                    }
                    scheduleRetainedJournalReconciliationSafely()
                    return@synchronized
                }
                beginNativeRestoreSessionOnce()
                val preferenceDelivered =
                    restoredPreferences().restore(
                        destination,
                        retainedPreferenceNames,
                        size,
                        { readNativePreferencePayload(data, size) },
                        { bytes -> restoreNativePreferencePayload(this, bytes) { restore(it, destination) } },
                        { drainDuplicatePreference(data, size) },
                    )
                if (!preferenceDelivered && destination.canonicalFile == File(filesDir, "logdate.preferences_pb").canonicalFile) {
                    restoreMainPreferences(this, data, size) { descriptor -> restore(descriptor, destination) }
                    PostRestoreDetector(this).markNativePrivacyPayloadReceived()
                } else if (!preferenceDelivered) {
                    restore(data, destination)
                }
                retainOptionalPreferences { capture(destination, retainedPreferenceNames) }
                val databaseDelivered =
                    runCatching { destination.canonicalFile == getDatabasePath("logdate").canonicalFile }
                        .getOrDefault(false)
                PostRestoreDetector(this).markNativeRestorePayloadReceived(databaseDelivered)
            }
        } finally {
            AndroidNativeRestoreDeliveryGate.endRestoreCallback()
        }
    }

    private fun restoredPreferences() = NativeRestoredPreferenceFiles(this, ::replayPreference)

    private fun replayPreference(
        destination: File,
        bytes: ByteArray,
    ) {
        restoreNativePreferencePayload(this, bytes) { descriptor ->
            val privateMode = (OsConstants.S_IRUSR or OsConstants.S_IWUSR).toLong()
            super.onRestoreFile(descriptor, bytes.size.toLong(), destination, TYPE_FILE, privateMode, 0L)
        }
    }

    private fun drainDuplicatePreference(
        data: ParcelFileDescriptor,
        size: Long,
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(data.fileDescriptor)).use { input ->
            val buffer = ByteArray(8192)
            var remaining = size
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) throw IOException("Duplicate native preference delivery ended before its declared size")
                remaining -= count
            }
        }
    }

    override fun onDestroy() {
        finishNativeRestoreAgent(
            schedule = {
                if (retainedJournalWorkspace(this).canStageNativeDelivery()) {
                    scheduleRetainedJournalReconciliationSafely()
                }
            },
            finishNativeSession = nativeRestoreSession::finish,
            finishIncomingSession = incomingRestoreSession::finish,
            destroy = { super.onDestroy() },
        )
    }

    private fun beginNativeRestoreSessionOnce(resumeFinalization: Boolean = false) =
        nativeRestoreSession.beginIfNeeded {
            resumeFinalization && NativeRestoredPreferenceFiles(this).canResumeFinalization(retainedPreferenceNames)
        }

    private fun retainOptionalPreferences(operation: NativeRestoredPreferenceFiles.() -> Unit) {
        try {
            NativeRestoredPreferenceFiles(this).operation()
        } catch (_: Exception) {
            Napier.w("Optional restored preference retention remains pending")
        }
    }

    private fun retryAccountRecovery() {
        try {
            val preferences = createAndroidMainDataStore(this)
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(15_000) {
                    retainAccountRecovery(preferences, freshDelivery = false)
                    prepareRestoredInstallationState(preferences)
                    restoreAccount(preferences)
                }
            }
        } catch (_: Exception) {
            Napier.w("Background native account recovery remains pending")
        }
    }

    private suspend fun prepareRestoredInstallationState(preferences: DataStore<Preferences>) {
        if (synchronized(callbackLock) { installationStateReset }) return
        prepareNativeRestoredInstallationState(this, preferences)
        synchronized(callbackLock) { installationStateReset = true }
    }

    private fun scheduleRetainedJournalReconciliationSafely() {
        try {
            scheduleRetainedJournalReconciliation()
        } catch (_: Exception) {
            Napier.w("Retained journal reconciliation remains pending")
        }
    }

    protected open fun scheduleRetainedJournalReconciliation() = Unit

    protected open suspend fun retainAccountRecoveryBeforeCompletion(
        preferences: DataStore<Preferences>,
        deliveryId: String,
    ) = Unit

    protected open suspend fun retainAccountRecovery(
        preferences: DataStore<Preferences>,
        freshDelivery: Boolean,
    ) = Unit

    protected open suspend fun restoreAccount(preferences: DataStore<Preferences>) = Unit

    override fun onQuotaExceeded(
        backupDataBytes: Long,
        quotaBytes: Long,
    ) {
        super.onQuotaExceeded(backupDataBytes, quotaBytes)
        NativeBackupQuotaStore(noBackupFilesDir).record(backupDataBytes, quotaBytes)
        Napier.w("Android native backup exceeded the transport quota")
    }
}
