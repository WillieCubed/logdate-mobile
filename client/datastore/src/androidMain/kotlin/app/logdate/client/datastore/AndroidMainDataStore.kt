package app.logdate.client.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

private val mainDataStores = AndroidMainDataStoreRegistry()

/** Native backup callbacks may share a process with normal application preferences consumers. */
fun createAndroidMainDataStore(context: Context): DataStore<Preferences> = mainDataStores.get(File(context.filesDir, DATA_STORE_FILE_NAME))

internal class AndroidMainDataStoreRegistry(
    private val create: (File) -> DataStore<Preferences> = { file -> createDataStore { file.path } },
) {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()

    fun get(file: File): DataStore<Preferences> =
        synchronized(stores) {
            val canonical = file.canonicalFile
            stores.getOrPut(canonical.path) { create(canonical) }
        }
}
