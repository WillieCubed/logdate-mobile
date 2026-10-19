package app.logdate.client.device.restore

import android.annotation.TargetApi
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.app.backup.FullRestoreDataInput
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.LegacyCloudCredentialMigration
import app.logdate.client.device.crypto.AndroidCryptoManager
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.storage.AndroidSecureStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

/** Records platform callbacks without opening the database or starting application services. */
open class NativeBackupAgent : BackupAgent() {
    private val nativeRestoreSession = NativeRestoreSessionBoundary { PostRestoreDetector(this).beginNativeRestoreSession() }
    private val callbackLock = Any()
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
                    AndroidSecureStorage(this@NativeBackupAgent),
                    AndroidCryptoManager(),
                    AndroidIdentityKeyBackupStore(this@NativeBackupAgent),
                ).ensureExistingIdentityBackup()
            }
        } catch (error: Exception) {
            throw IOException("Native encryption recovery material could not be prepared", error)
        }
        // Backup may run before the first normal launch after an upgrade.
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        try {
            val preferences =
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    migrations = listOf(LegacyCloudCredentialMigration()),
                    produceFile = { File(filesDir, "logdate.preferences_pb") },
                )
            runBlocking(Dispatchers.IO) { preferences.data.first() }
        } finally {
            scope.cancel()
            runBlocking { job.join() }
        }
        super.onFullBackup(data)
    }

    override fun onRestoreFinished() {
        try {
            val duplicateCompletion =
                synchronized(callbackLock) {
                    if (restoreCompletionRecorded) {
                        true
                    } else if (!nativeRestoreSession.isStarted() && PostRestoreDetector(this).hasCompletedNativeRestoreDelivery()) {
                        restoreCompletionRecorded = true
                        true
                    } else {
                        beginNativeRestoreSessionOnce()
                        false
                    }
                }
            super.onRestoreFinished()
            if (duplicateCompletion) {
                retryAccountRecovery()
                return
            }
            AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
            try {
                PostRestoreDetector(this).recordRestoreCompletedUnderDeliveryLock()
                synchronized(callbackLock) { restoreCompletionRecorded = true }
                val preferencesFile = File(filesDir, "logdate.preferences_pb")
                val job = SupervisorJob()
                val scope = CoroutineScope(job + Dispatchers.IO)
                try {
                    val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile })
                    runBlocking(Dispatchers.IO) {
                        withTimeoutOrNull(15_000) {
                            prepareRestoredInstallationState(preferences)
                            restoreAccount(preferences)
                        }
                    }
                } catch (_: Exception) {
                    Napier.w("Background native account recovery remains pending")
                } finally {
                    scope.cancel()
                    runBlocking { job.join() }
                }
            } finally {
                AndroidNativeRestoreDeliveryGate.endRestore()
            }
        } finally {
            nativeRestoreSession.finish()
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
        restoreNativeFile(destination) {
            super.onRestoreFile(data, size, destination, type, mode, mtime)
        }
    }

    @TargetApi(Build.VERSION_CODES.BAKLAVA)
    override fun onRestoreFile(data: FullRestoreDataInput) {
        restoreNativeFile(data.destination) {
            super.onRestoreFile(data)
        }
    }

    private inline fun restoreNativeFile(
        destination: File,
        restore: () -> Unit,
    ) {
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        try {
            synchronized(callbackLock) {
                restoreCompletionRecorded = false
                installationStateReset = false
                beginNativeRestoreSessionOnce()
            }
            restore()
            val databaseDelivered =
                runCatching { destination.canonicalFile == getDatabasePath("logdate").canonicalFile }
                    .getOrDefault(false)
            PostRestoreDetector(this).markNativeRestorePayloadReceived(databaseDelivered)
        } finally {
            AndroidNativeRestoreDeliveryGate.endRestoreCallback()
        }
    }

    override fun onDestroy() {
        nativeRestoreSession.finish()
        AndroidNativeRestoreDeliveryGate.endRestore()
        super.onDestroy()
    }

    private fun beginNativeRestoreSessionOnce() = nativeRestoreSession.beginIfNeeded()

    private fun retryAccountRecovery() {
        val preferencesFile = File(filesDir, "logdate.preferences_pb")
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile })
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(15_000) {
                    prepareRestoredInstallationState(preferences)
                    restoreAccount(preferences)
                }
            }
        } catch (_: Exception) {
            Napier.w("Background native account recovery remains pending")
        } finally {
            scope.cancel()
            runBlocking { job.join() }
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

internal class NativeRestoreSessionBoundary(
    private val begin: () -> Unit,
) {
    private val lock = Any()
    private var started = false

    fun beginIfNeeded() =
        synchronized(lock) {
            if (!started) {
                begin()
                started = true
            }
        }

    fun finish() =
        synchronized(lock) {
            started = false
        }

    fun isStarted(): Boolean = synchronized(lock) { started }
}
