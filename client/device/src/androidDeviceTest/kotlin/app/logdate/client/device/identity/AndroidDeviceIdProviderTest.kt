package app.logdate.client.device.identity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class AndroidDeviceIdProviderTest {
    private lateinit var context: Context
    private lateinit var deviceIdProvider: AndroidDeviceIdProvider

    private val prefsName = "app.logdate.device_identifiers"
    private val deviceIdKey = "device_id"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        deviceIdProvider = AndroidDeviceIdProvider(context)
    }

    @Test
    fun `getDeviceId should generate and store new ID if none exists`() =
        runTest {
            // When
            val deviceId = deviceIdProvider.getDeviceId().first()

            // Then
            val storedId =
                context
                    .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .getString(deviceIdKey, null)
            assert(deviceId.toString().isNotBlank()) { "Device ID should not be blank" }
            assertEquals(deviceId.toString(), storedId, "Device ID should be persisted")
        }

    @Test
    fun `getDeviceId should return consistent ID until refreshed`() =
        runTest {
            // When
            val firstId = deviceIdProvider.getDeviceId().first()
            val secondId = deviceIdProvider.getDeviceId().first()

            // Then
            assertEquals(firstId, secondId, "Device ID should remain consistent between reads")
        }

    @Test
    fun `refreshDeviceId should generate a new ID`() =
        runTest {
            // Given
            val initialId = deviceIdProvider.getDeviceId().first()

            // When
            deviceIdProvider.refreshDeviceId()
            val newId = deviceIdProvider.getDeviceId().first()

            // Then
            assertNotEquals(initialId, newId, "Device ID should change after refresh")
        }

    @Test
    fun `should recover from invalid stored UUID`() =
        runTest {
            // Given
            context
                .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .putString(deviceIdKey, "not-a-valid-uuid")
                .commit()

            // When
            val provider = AndroidDeviceIdProvider(context)
            val deviceId = provider.getDeviceId().first()

            // Then
            assert(deviceId.toString().isNotBlank()) { "Should have generated a valid device ID" }
        }
}
