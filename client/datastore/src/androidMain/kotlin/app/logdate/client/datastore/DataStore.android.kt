package app.logdate.client.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import org.koin.java.KoinJavaComponent.inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

actual fun createDataStore(): DataStore<Preferences> {
    val applicationContext: Context by inject(Context::class.java)
    return createDataStore(
        producePath = { applicationContext.filesDir.resolve(dataStoreFileName).absolutePath }
    )
}