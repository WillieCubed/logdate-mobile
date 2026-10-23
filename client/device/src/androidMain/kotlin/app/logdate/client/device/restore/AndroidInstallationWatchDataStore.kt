package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.createAndroidMainDataStore
import app.logdate.client.device.storage.createAndroidSecureStorage
import app.logdate.client.device.storage.getBytes
import kotlinx.coroutines.flow.first
import java.io.File

private val installationWatchStores = mutableMapOf<String, DataStore<Preferences>>()

/** Links, sessions, and delivery revisions belong to the installation, never a portable backup. */
fun createAndroidInstallationWatchDataStore(context: Context): DataStore<Preferences> =
    synchronized(installationWatchStores) {
        val file = File(context.noBackupFilesDir, "watch-installation.preferences_pb").canonicalFile
        installationWatchStores.getOrPut(file.path) {
            val keys = createAndroidSecureStorage(context)
            PreferenceDataStoreFactory.create(
                migrations =
                    listOf(
                        InstallationWatchMigration(
                            legacy = { createAndroidMainDataStore(context).data.first() },
                            pairKey = { node -> keys.getBytes("phone.watch_link.pair_key.$node") },
                            canAdoptLegacy = { !PostRestoreDetector(context).hasNativeRestoreHistory() },
                        ),
                    ),
                produceFile = { file },
            )
        }
    }

fun createAndroidInstallationWatchStorage(context: Context): KeyValueStorage =
    DataStoreKeyValueStorage(createAndroidInstallationWatchDataStore(context))
