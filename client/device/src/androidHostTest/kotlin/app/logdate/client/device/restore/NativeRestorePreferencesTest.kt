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
    fun `installation reset marker keeps the original identity until a new restore session`() {
        val directory = createTempDirectory("installation-reset-marker").toFile()
        val marker = NativeInstallationResetMarker(directory)
        try {
            assertEquals("source-device", marker.sourceDeviceId("source-device"))
            assertEquals("source-device", marker.sourceDeviceId("destination-device"))
            marker.clear()
            assertEquals("destination-device", marker.sourceDeviceId("destination-device"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `installation reset marker repairs either damaged copy`() {
        val directory = createTempDirectory("installation-reset-marker-repair").toFile()
        val marker = NativeInstallationResetMarker(directory)
        val primary = directory.resolve(".logdate_installation_reset_source")
        val backup = directory.resolve(".logdate_installation_reset_source_backup")
        try {
            marker.sourceDeviceId("source-device")
            primary.writeText("damaged")
            assertEquals("source-device", marker.sourceDeviceId("destination-device"))

            backup.writeText("damaged")
            assertEquals("source-device", marker.sourceDeviceId("destination-device"))
        } finally {
            directory.deleteRecursively()
        }
    }

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
                val credentialKeys =
                    listOf(
                        "cloud_access_token",
                        "cloud_refresh_token",
                        "cloud_access_token_first_logdate_app",
                        "cloud_refresh_token_second_logdate_app",
                        "access_token",
                        "refresh_token",
                        "access_token_bGVnYWN5",
                        "refresh_token_bGVnYWN5",
                    )
                credentialKeys.forEach { storage.putString(it, "synthetic-token") }

                resetRestoredInstallationState(preferences, null)
                credentialKeys.forEach { assertEquals(null, storage.getString(it)) }
                assertEquals(sourceDevice, DefaultDeviceIdProvider(storage).getDeviceId().value)

                resetRestoredInstallationState(preferences, sourceDevice.toString())

                credentialKeys.forEach { assertEquals(null, storage.getString(it)) }
                assertEquals(sourceOwner, DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId())
                assertEquals("keep this", storage.getString("unrelated.preference"))
                assertEquals("true", storage.getString("identity.canonical_owner_bound"))
                val replacement = DefaultDeviceIdProvider(storage).getDeviceId().value
                assertNotEquals(sourceDevice, replacement)
                resetRestoredInstallationState(preferences, sourceDevice.toString())
                assertEquals(replacement, DefaultDeviceIdProvider(storage).getDeviceId().value)
            } finally {
                scope.cancel()
                job.join()
                directory.deleteRecursively()
            }
        }
}
