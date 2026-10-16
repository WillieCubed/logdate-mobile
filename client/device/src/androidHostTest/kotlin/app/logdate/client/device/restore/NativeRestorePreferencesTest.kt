package app.logdate.client.device.restore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import app.logdate.client.device.identity.DefaultDeviceIdProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NativeRestorePreferencesTest {
    @Test
    fun `native restore keeps the journal owner but makes a distinct device`() =
        runTest {
            val directory = createTempDirectory("restored-preferences").toFile()
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.IO)
            val preferences =
                PreferenceDataStoreFactory.createWithPath(scope = scope) {
                    directory.resolve("logdate.preferences_pb").path.toPath()
                }
            try {
                val storage = DataStoreKeyValueStorage(preferences)
                val sourceOwner = DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId()
                val sourceDevice = DefaultDeviceIdProvider(storage).getDeviceId().value
                storage.putString("identity.canonical_owner_bound", "true")
                storage.putString("unrelated.preference", "keep this")

                resetRestoredInstallationState(preferences)

                assertEquals(sourceOwner, DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId())
                assertEquals("keep this", storage.getString("unrelated.preference"))
                assertEquals("true", storage.getString("identity.canonical_owner_bound"))
                assertNotEquals(sourceDevice, DefaultDeviceIdProvider(storage).getDeviceId().value)
            } finally {
                scope.cancel()
                job.join()
                directory.deleteRecursively()
            }
        }
}
