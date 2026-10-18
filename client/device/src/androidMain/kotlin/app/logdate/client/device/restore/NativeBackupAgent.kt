package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.device.crypto.AndroidCryptoManager
import app.logdate.client.device.crypto.AndroidIdentityKeyBackupStore
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.storage.AndroidSecureStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

/** Records platform callbacks without opening the database or starting application services. */
open class NativeBackupAgent : BackupAgent() {
    private val nativeRestoreSession = NativeRestoreSessionBoundary { PostRestoreDetector(this).beginNativeRestoreSession() }

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
        super.onFullBackup(data)
    }

    override fun onRestoreFinished() {
        try {
            // This instance may be the first one handling a new, empty, or resumed delivery.
            // Start a fresh generation even when the transport supplied no file callbacks.
            beginNativeRestoreSessionOnce()
            super.onRestoreFinished()
            AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
            try {
                PostRestoreDetector(this).recordRestoreCompletedUnderDeliveryLock()
                val preferencesFile = File(filesDir, "logdate.preferences_pb")
                val job = SupervisorJob()
                val scope = CoroutineScope(job + Dispatchers.IO)
                try {
                    val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile })
                    runBlocking(Dispatchers.IO) {
                        withTimeoutOrNull(15_000) {
                            resetRestoredInstallationState(preferences)
                            PostRestoreDetector(this@NativeBackupAgent).refreshRestoreFingerprint()
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
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        try {
            beginNativeRestoreSessionOnce()
            super.onRestoreFile(data, size, destination, type, mode, mtime)
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
}
