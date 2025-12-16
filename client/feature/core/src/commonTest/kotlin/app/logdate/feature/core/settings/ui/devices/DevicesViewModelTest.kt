package app.logdate.feature.core.settings.ui.devices

import app.logdate.client.device.models.DeviceInfo
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.models.DevicePlatform
import app.logdate.client.device.identity.createTestDeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.uuid.Uuid
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicesViewModelTest {
    
    private lateinit var deviceManager: TestDeviceManager
    private lateinit var viewModel: DevicesViewModel
    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var testScope: CoroutineScope
    
    private val currentDeviceId = Uuid.parse("123e4567-e89b-12d3-a456-426614174000")
    private val device1Id = Uuid.parse("223e4567-e89b-12d3-a456-426614174000")
    private val device2Id = Uuid.parse("323e4567-e89b-12d3-a456-426614174000")
    
    @BeforeTest
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        testScope = CoroutineScope(testDispatcher)
        
        // Setup device manager with a current device and two other devices
        deviceManager = TestDeviceManager()
        deviceManager.setCurrentDeviceId(currentDeviceId)
        deviceManager.setCurrentDeviceName("Current Device")
        
        // Add some associated devices
        val device1 = createTestDeviceInfo(
            id = device1Id,
            name = "Device 1",
            platform = DevicePlatform.ANDROID
        )
        val device2 = createTestDeviceInfo(
            id = device2Id,
            name = "Device 2",
            platform = DevicePlatform.IOS
        )
        deviceManager.addAssociatedDevice(device1)
        deviceManager.addAssociatedDevice(device2)
        
        // Create view model
        viewModel = DevicesViewModel(deviceManager, testScope)
    }
    
    @Test
    fun `initial state should show loading`() = runTest {
        // When created
        val initialState = viewModel.uiState.first()
        
        // Then
        assertTrue(initialState.isLoading, "Initial state should show loading")
        assertTrue(initialState.devices.isEmpty(), "Initial state should have no devices")
        assertNull(initialState.error, "Initial state should have no error")
    }
    
    @Test
    fun `loadDevices should load devices`() = runTest(testDispatcher) {
        // When
        viewModel.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.first()
        
        // Then
        assertFalse(state.isLoading, "Loading should be complete")
        assertEquals(3, state.devices.size, "Should load all 3 devices")
        assertNull(state.error, "Should have no error")
        
        // Verify current device is properly marked
        val currentDevice = state.devices.find { it.id == currentDeviceId.toString() }
        assertTrue(currentDevice?.isCurrentDevice ?: false, "Current device should be marked as current")
        assertEquals("Current Device", currentDevice?.name, "Current device should have correct name")
        
        // Verify other devices are not marked as current
        state.devices
            .filter { it.id != currentDeviceId.toString() }
            .forEach { device ->
                assertFalse(device.isCurrentDevice, "Other devices should not be marked as current")
            }
    }
    
    @Test
    fun `renameDevice should update device name`() = runTest(testDispatcher) {
        // Given
        viewModel.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        val initialState = viewModel.uiState.first()
        val initialDeviceCount = initialState.devices.size
        
        // When
        val newName = "Renamed Device"
        viewModel.renameDevice(newName)
        testDispatcher.scheduler.advanceUntilIdle()
        val updatedState = viewModel.uiState.first()
        
        // Then
        assertEquals(initialDeviceCount, updatedState.devices.size, "Device count should not change")
        val currentDevice = updatedState.devices.find { it.isCurrentDevice }
        assertEquals(newName, currentDevice?.name, "Device should be renamed")
        assertEquals(newName, deviceManager.getCurrentDeviceName(), "Device manager should have updated name")
        assertEquals(1, deviceManager.renameCallCount, "Should call rename on the device manager")
    }
    
    @Test
    fun `removeDevice should remove the device`() = runTest(testDispatcher) {
        // Given
        viewModel.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        val initialState = viewModel.uiState.first()
        val initialDeviceCount = initialState.devices.size
        
        // When
        viewModel.removeDevice(device1Id)
        testDispatcher.scheduler.advanceUntilIdle()
        val updatedState = viewModel.uiState.first()
        
        // Then
        assertEquals(initialDeviceCount - 1, updatedState.devices.size, "One device should be removed")
        assertNull(updatedState.devices.find { it.id == device1Id.toString() }, "Device 1 should be removed")
        assertEquals(1, deviceManager.removeCallCount, "Should call remove on the device manager")
        assertEquals(device1Id, deviceManager.lastRemovedDeviceId, "Correct device ID should be removed")
    }
    
    @Test
    fun `resetDeviceId should refresh device ID`() = runTest(testDispatcher) {
        // Given
        viewModel.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        val initialDeviceId = deviceManager.getDeviceId()
        
        // When
        viewModel.resetDeviceId()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        assertNotEquals(initialDeviceId, deviceManager.getDeviceId(), "Device ID should change")
        assertEquals(1, deviceManager.refreshIdCallCount, "Should call refreshDeviceId on the device manager")
    }
    
    @Test
    fun `error handling should work for loadDevices`() = runTest(testDispatcher) {
        // Given
        deviceManager.setShouldFailOnGetCurrentDeviceInfo(true)
        
        // When
        viewModel.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.first()
        
        // Then
        assertFalse(state.isLoading, "Loading should complete even on error")
        assertTrue(state.error?.contains("Failed to load devices") ?: false, "Error message should be set")
    }
    
    @Test
    fun `error handling should work for removeDevice`() = runTest(testDispatcher) {
        // Given
        viewModel.loadDevices()
        testDispatcher.scheduler.advanceUntilIdle()
        deviceManager.setShouldFailDeviceRemoval(true)
        
        // When
        viewModel.removeDevice(device1Id.toString())
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.first()
        
        // Then
        assertTrue(state.error?.contains("Failed to remove device") ?: false, "Error message should be set")
    }
    
    /**
     * A test implementation of DeviceManager for view model tests.
     */
    private class TestDeviceManager : DefaultDeviceManager {
        private var currentDeviceId = Uuid.parse("123e4567-e89b-12d3-a456-426614174000")
        private var currentDeviceName = "Test Device"
        private val deviceInfo = MutableStateFlow<DeviceInfo>(
            createTestDeviceInfo(
                id = currentDeviceId,
                name = currentDeviceName
            )
        )
        private val devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
        
        var renameCallCount = 0
        var removeCallCount = 0
        var refreshIdCallCount = 0
        var lastRemovedDeviceId: Uuid? = null
        var shouldFailGetCurrentDeviceInfo = false
        var shouldFailDeviceRemoval = false
        
        override fun getDeviceId(): MutableStateFlow<Uuid> = MutableStateFlow(currentDeviceId)
        
        override suspend fun getCurrentDeviceInfo(): DeviceInfo {
            if (shouldFailGetCurrentDeviceInfo) {
                throw RuntimeException("Test failure: getCurrentDeviceInfo")
            }
            return deviceInfo.value
        }
        
        override suspend fun updateLastActive() {
            val currentInfo = deviceInfo.value
            deviceInfo.value = currentInfo.copy(lastActive = Clock.System.now())
        }
        
        override suspend fun registerWithCloud(): Result<Boolean> = Result.success(true)
        
        override fun getAssociatedDevices(): MutableStateFlow<List<DeviceInfo>> = devices
        
        override suspend fun renameDevice(newName: String) {
            renameCallCount++
            currentDeviceName = newName
            deviceInfo.value = deviceInfo.value.copy(name = newName)
        }
        
        override suspend fun removeDevice(deviceId: Uuid): Result<Boolean> {
            removeCallCount++
            lastRemovedDeviceId = deviceId
            
            if (shouldFailDeviceRemoval) {
                return Result.failure(RuntimeException("Test failure: removeDevice"))
            }
            
            devices.value = devices.value.filter { it.id != deviceId }
            return Result.success(true)
        }
        
        override suspend fun refreshDeviceId(): Uuid {
            refreshIdCallCount++
            val newId = Uuid.parse("423e4567-e89b-12d3-a456-426614174000")
            currentDeviceId = newId
            deviceInfo.value = deviceInfo.value.copy(id = newId)
            return newId
        }
        
        override suspend fun getPlatformInfo(): Map<String, String> {
            return mapOf(
                "platform" to "Test",
                "version" to "1.0.0",
                "model" to "Test Model"
            )
        }
        
        override suspend fun getNotificationToken(): String? = null
        
        override suspend fun updateNotificationToken(token: String) {}
        
        // Helper methods for testing
        fun setCurrentDeviceId(id: Uuid) {
            currentDeviceId = id
            deviceInfo.value = deviceInfo.value.copy(id = id)
        }
        
        fun setCurrentDeviceName(name: String) {
            currentDeviceName = name
            deviceInfo.value = deviceInfo.value.copy(name = name)
        }
        
        fun getCurrentDeviceName(): String = currentDeviceName
        
        fun addAssociatedDevice(device: DeviceInfo) {
            devices.value = devices.value + device
        }
        
        fun setShouldFailOnGetCurrentDeviceInfo(shouldFail: Boolean) {
            shouldFailGetCurrentDeviceInfo = shouldFail
        }
        
        fun setShouldFailDeviceRemoval(shouldFail: Boolean) {
            shouldFailDeviceRemoval = shouldFail
        }
    }
}