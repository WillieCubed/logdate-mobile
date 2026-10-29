package app.logdate.client.e2e

import android.app.backup.BackupAgent
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.device.restore.NativeBackupAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import java.io.File
import kotlin.uuid.Uuid

internal class NativeBackgroundRestorePreferences(
    private val context: Context,
    private val folder: File,
) {
    fun deliver(
        agent: NativeBackupAgent,
        source: File,
    ) {
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            if (supportsFullRestoreInput()) {
                val inputType = Class.forName("android.app.backup.FullRestoreDataInput")
                val input =
                    nativeModernRestoreInput(
                        inputType,
                        descriptor,
                        File(context.filesDir, "logdate.preferences_pb"),
                        source.length(),
                        0x180L,
                    )
                NativeBackupAgent::class.java.getDeclaredMethod("onRestoreFile", inputType).invoke(agent, input)
                return
            }
            agent.onRestoreFile(
                descriptor,
                source.length(),
                File(context.filesDir, "logdate.preferences_pb"),
                BackupAgent.TYPE_FILE,
                0x180L,
                1L,
            )
        }
    }

    fun supportsFullRestoreInput(): Boolean =
        Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1

    suspend fun incoming(
        policy: String?,
        deviceId: String? = null,
    ): File {
        val file = File(folder, "incoming-${Uuid.random()}.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
            store.edit {
                it[stringPreferencesKey("identity.canonical_owner_id")] = "native-test-owner"
                if (policy != null) it[stringPreferencesKey("security_level")] = policy
                if (deviceId != null) it[stringPreferencesKey("device.id")] = deviceId
            }
        } finally {
            job.cancelAndJoin()
        }
        return file
    }
}
