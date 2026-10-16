package app.logdate.client.device.restore

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import java.io.File
import java.io.IOException

/** Records platform callbacks without opening the database or starting application services. */
class NativeBackupAgent : BackupAgent() {
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
        super.onRestoreFinished()
        PostRestoreDetector(this).recordRestoreCompleted()
        val preferencesFile = File(filesDir, "logdate.preferences_pb")
        if (preferencesFile.exists()) {
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.IO)
            try {
                val preferences = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile })
                runBlocking { resetRestoredInstallationState(preferences) }
            } finally {
                scope.cancel()
                runBlocking { job.join() }
            }
        }
    }

    override fun onQuotaExceeded(
        backupDataBytes: Long,
        quotaBytes: Long,
    ) {
        super.onQuotaExceeded(backupDataBytes, quotaBytes)
        NativeBackupQuotaStore(noBackupFilesDir).record(backupDataBytes, quotaBytes)
        Napier.w("Android native backup exceeded the transport quota")
    }
}
