package app.logdate.client.device.restore

import android.content.Context
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.AndroidInstallationIdProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

class AndroidInstallationIdProviderTest {
    private val directory = createTempDirectory("installation-id").toFile()
    private val legacyId = Uuid.random()
    private val storage = mockk<KeyValueStorage>(relaxed = true).also { coEvery { it.getString("device.id") } returns legacyId.toString() }
    private val context = mockk<Context>().also { every { it.noBackupFilesDir } returns directory }

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `restored preferences cannot supply the replacement installation identity`() {
        val replacement = AndroidInstallationIdProvider(context, storage).getDeviceId().value
        assertNotEquals(legacyId, replacement)
        assertEquals(replacement, AndroidInstallationIdProvider(context, storage).getDeviceId().value)
        coVerify(exactly = 0) { storage.putString("device.id", any()) }
    }

    @Test
    fun `an initialized installation preserves its existing identity on upgrade`() {
        File(directory, ".logdate_device_initialized").createNewFile()
        assertEquals(legacyId, AndroidInstallationIdProvider(context, storage).getDeviceId().value)
    }

    @Test
    fun `interrupted restore callback cannot revive the source identity`() {
        File(directory, ".logdate_device_initialized").createNewFile()
        File(directory, ".logdate_native_restore_pending").writeText("content-present")
        assertNotEquals(legacyId, AndroidInstallationIdProvider(context, storage).getDeviceId().value)
    }

    @Test
    fun `a refreshed installation identity persists independently of portable preferences`() =
        runTest {
            val provider = AndroidInstallationIdProvider(context, storage)
            provider.refreshDeviceId()
            assertEquals(provider.getDeviceId().value, AndroidInstallationIdProvider(context, storage).getDeviceId().value)
        }
}
