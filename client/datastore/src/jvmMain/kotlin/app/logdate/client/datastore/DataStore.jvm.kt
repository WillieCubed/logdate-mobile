package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

actual fun createDataStore(): DataStore<Preferences> {
    // Get user directory
    // TODO: Find a better way to get the user directory
    val userDirectory = System.getProperty("user.home")
    val haystackDirectory = "$userDirectory/.logdate"
    return createDataStore { "$haystackDirectory/$dataStoreFileName" }
}