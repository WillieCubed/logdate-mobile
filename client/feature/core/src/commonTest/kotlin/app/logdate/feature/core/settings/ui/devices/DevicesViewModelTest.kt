package app.logdate.feature.core.settings.ui.devices

import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.identity.DeviceRepository
import app.logdate.client.device.models.DeviceInfo
import app.logdate.client.device.models.DevicePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {
    private lateinit var deviceIdProvider: FakeDeviceIdProvider
    private lateinit var repository: FakeDeviceRepository
    private lateinit var deviceManager: DefaultDeviceManager
    private lateinit var viewModel: DevicesViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val currentDeviceId = Uuid.parse("123e4567-e89b-12d3-a456-426614174000")
    private val device1Id = Uuid.parse("223e4567-e89b-12d3-a456-426614174000")
    private val device2Id = Uuid.parse("323e4567-e89b-12d3-a456-426614174000")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Setup device manager with a current device and two other devices
        deviceIdProvider = FakeDeviceIdProvider(currentDeviceId)
        repository = FakeDeviceRepository()
        deviceManager =
            DefaultDeviceManager(
                deviceIdProvider = deviceIdProvider,
                deviceRepository = repository,
                initialDeviceName = "Current Device",
                platform = DevicePlatform.ANDROID,
                appVersion = "1.0.0",
            )

        // Add some associated devices
        val device1 =
            deviceInfo(
                id = device1Id,
                name = "Device 1",
                platform = DevicePlatform.ANDROID,
            )
        val device2 =
            deviceInfo(
                id = device2Id,
                name = "Device 2",
                platform = DevicePlatform.IOS,
            )
        repository.addAssociatedDevice(device1)
        repository.addAssociatedDevice(device2)

        // Create view model
        viewModel = DevicesViewModel(deviceManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be idle`() =
        testScope.runTest {
            // When created
            val initialState = viewModel.uiState.first()

            // Then
            assertFalse(initialState.isLoading, "Initial state should not be loading")
            assertTrue(initialState.devices.isEmpty(), "Initial state should have no devices")
            assertNull(initialState.error, "Initial state should have no error")
        }

    @Test
    fun `loadDevices should load devices`() =
        testScope.runTest {
            // When
            viewModel.loadDevices()
            advanceUntilIdle()
            val state = viewModel.uiState.first()

            // Then
            assertFalse(state.isLoading, "Loading should be complete")
            assertEquals(3, state.devices.size, "Should load all 3 devices")
            assertNull(state.error, "Should have no error")

            // Verify current device is properly marked
            val currentDevice = assertNotNull(state.devices.find { it.id == currentDeviceId })
            assertTrue(currentDevice.isCurrentDevice, "Current device should be marked as current")
            assertEquals("Current Device", currentDevice.name, "Current device should have correct name")

            // Verify other devices are not marked as current
            state.devices
                .filter { it.id != currentDeviceId }
                .forEach { device ->
                    assertFalse(device.isCurrentDevice, "Other devices should not be marked as current")
                }
        }

    @Test
    fun `renameDevice should update device name`() =
        testScope.runTest {
            // Given
            viewModel.loadDevices()
            advanceUntilIdle()
            val initialState = viewModel.uiState.first()
            val initialDeviceCount = initialState.devices.size

            // When
            val newName = "Renamed Device"
            viewModel.renameDevice(newName)
            advanceUntilIdle()
            val updatedState = viewModel.uiState.first()

            // Then
            assertEquals(initialDeviceCount, updatedState.devices.size, "Device count should not change")
            val currentDevice = updatedState.devices.find { it.isCurrentDevice }
            assertEquals(newName, currentDevice?.name, "Device should be renamed")
            assertEquals(1, repository.updateDeviceInfoCallCount, "Should update device info in repository")
        }

    @Test
    fun `removeDevice should remove the device`() =
        testScope.runTest {
            // Given
            viewModel.loadDevices()
            advanceUntilIdle()
            val initialState = viewModel.uiState.first()
            val initialDeviceCount = initialState.devices.size

            // When
            viewModel.removeDevice(device1Id)
            advanceUntilIdle()
            val updatedState = viewModel.uiState.first()

            // Then
            assertEquals(initialDeviceCount - 1, updatedState.devices.size, "One device should be removed")
            assertNull(updatedState.devices.find { it.id == device1Id }, "Device 1 should be removed")
            assertEquals(1, repository.removeCallCount, "Should call remove on the device repository")
            assertEquals(device1Id, repository.lastRemovedDeviceId, "Correct device ID should be removed")
        }

    @Test
    fun `resetDeviceId should refresh device ID`() =
        testScope.runTest {
            // Given
            viewModel.loadDevices()
            advanceUntilIdle()
            val initialDeviceId = deviceIdProvider.getDeviceId().value

            // When
            viewModel.resetDeviceId()
            advanceUntilIdle()

            // Then
            assertNotEquals(initialDeviceId, deviceIdProvider.getDeviceId().value, "Device ID should change")
            assertEquals(1, deviceIdProvider.refreshCallCount, "Should call refreshDeviceId on the provider")
        }

    @Test
    fun `error handling should work for loadDevices`() =
        testScope.runTest {
            // Given
            repository.shouldFailGetAssociatedDevices = true

            // When
            viewModel.loadDevices()
            advanceUntilIdle()
            val state = viewModel.uiState.first()

            // Then
            assertFalse(state.isLoading, "Loading should complete even on error")
            assertTrue(state.error?.contains("Failed to load devices") ?: false, "Error message should be set")
        }

    @Test
    fun `error handling should work for removeDevice`() =
        testScope.runTest {
            // Given
            viewModel.loadDevices()
            advanceUntilIdle()
            repository.shouldFailDeviceRemoval = true

            // When
            viewModel.removeDevice(device1Id)
            advanceUntilIdle()
            val state = viewModel.uiState.first()

            // Then
            assertTrue(state.error?.contains("Failed to remove device") ?: false, "Error message should be set")
        }

    /**
     * A test implementation of DeviceManager for view model tests.
     */
    private class FakeDeviceIdProvider(
        initialDeviceId: Uuid,
    ) : DeviceIdProvider {
        private val deviceId = MutableStateFlow(initialDeviceId)
        var refreshCallCount = 0

        override fun getDeviceId(): StateFlow<Uuid> = deviceId

        override suspend fun refreshDeviceId() {
            refreshCallCount++
            deviceId.value = Uuid.parse("423e4567-e89b-12d3-a456-426614174000")
        }
    }

    private class FakeDeviceRepository : DeviceRepository {
        private val associatedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())

        var updateDeviceInfoCallCount = 0
        var removeCallCount = 0
        var lastRemovedDeviceId: Uuid? = null
        var shouldFailGetAssociatedDevices = false
        var shouldFailDeviceRemoval = false

        override suspend fun registerDevice(
            deviceInfo: DeviceInfo,
            notificationToken: String?,
        ): Boolean = true

        override fun getAssociatedDevices(): Flow<List<DeviceInfo>> {
            if (shouldFailGetAssociatedDevices) {
                return flow { throw RuntimeException("Test failure: getAssociatedDevices") }
            }
            return associatedDevices
        }

        override suspend fun updateDeviceInfo(deviceInfo: DeviceInfo): Boolean {
            updateDeviceInfoCallCount++
            return true
        }

        override suspend fun updateDeviceToken(
            deviceId: Uuid,
            token: String,
        ): Boolean = true

        override suspend fun removeDevice(deviceId: Uuid): Boolean {
            removeCallCount++
            lastRemovedDeviceId = deviceId

            if (shouldFailDeviceRemoval) {
                throw RuntimeException("Test failure: removeDevice")
            }

            associatedDevices.value = associatedDevices.value.filter { it.id != deviceId }
            return true
        }

        fun addAssociatedDevice(device: DeviceInfo) {
            associatedDevices.value = associatedDevices.value + device
        }
    }

    private fun deviceInfo(
        id: Uuid,
        name: String,
        platform: DevicePlatform,
    ): DeviceInfo =
        DeviceInfo(
            id = id,
            name = name,
            platform = platform,
            createdAt = Clock.System.now(),
            lastActive = Clock.System.now(),
            appVersion = "1.0.0",
            isCurrentDevice = false,
        )
}
