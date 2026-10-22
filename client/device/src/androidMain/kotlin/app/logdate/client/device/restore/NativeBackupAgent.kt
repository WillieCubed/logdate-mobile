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
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.crypto.AndroidCryptoManager
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.storage.createAndroidSecureStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    private val callbackLock = Any()
    private val restoreFormat = NativeRestoreFormatGuard { noBackupFilesDir }
    private var restoreCompletionRecorded = false
    private var installationStateReset = false

    override fun onCreate() {
        super.onCreate()
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
        if (PostRestoreDetector(this).hasMissingNativePrivacyPayload()) throw NativeRestorePrivacyMetadataRequiredException()
        runBlocking(Dispatchers.IO) {
            NativeRestorePrivacyPolicy.prepareBackup(createAndroidMainDataStore(this@NativeBackupAgent)) { snapshot ->
                canEstablishNativePrivacyDefaults(this@NativeBackupAgent, snapshot)
            }
        }
        super.onFullBackup(data)
    }

    override fun onRestoreFinished() {
        AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
        try {
            if (synchronized(callbackLock) { restoreFormat.isRejected() }) {
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
            PostRestoreDetector(this).recordRestoreCompletedUnderDeliveryLock()
            synchronized(callbackLock) { restoreCompletionRecorded = true }
            try {
                val preferences = createAndroidMainDataStore(this)
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(15_000) {
                        prepareRestoredInstallationState(preferences)
                        restoreAccount(preferences)
                    }
                }
            } catch (_: Exception) {
                Napier.w("Background native account recovery remains pending")
            }
        } finally {
            nativeRestoreSession.finish()
            AndroidNativeRestoreDeliveryGate.endRestore()
        }
    }

    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long,
    ) {
        restoreNativeFile(destination, data, size) { descriptor, target ->
            super.onRestoreFile(descriptor, size, target, type, mode, mtime)
        }
    }

    @TargetApi(Build.VERSION_CODES.BAKLAVA)
    override fun onRestoreFile(data: FullRestoreDataInput) {
        restoreNativeFile(
            data.destination,
            data.data,
            data.size,
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
        restore: (ParcelFileDescriptor, File) -> Unit,
    ) {
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        try {
            synchronized(callbackLock) {
                restoreFormat.requireNativeFile(nativeFormat)
                restoreCompletionRecorded = false
                installationStateReset = false
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
        nativeRestoreSession.finish()
        AndroidNativeRestoreDeliveryGate.endRestore()
        super.onDestroy()
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
        val deviceIdKey = stringPreferencesKey("device.id")
        val currentDeviceId = preferences.data.first()[deviceIdKey]
        val sourceDeviceId = NativeInstallationResetMarker(noBackupFilesDir).sourceDeviceId(currentDeviceId)
        resetRestoredInstallationState(preferences, sourceDeviceId)
        PostRestoreDetector(this).refreshRestoreFingerprint()
        synchronized(callbackLock) { installationStateReset = true }
    }

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
