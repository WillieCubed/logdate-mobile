package app.logdate.client.networking

import app.logdate.client.networking.fakes.FakeNetworkAvailabilityMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for network availability monitoring in the LogDate app.
 *
 * Network connectivity is critical for the LogDate app's synchronization features,
 * journal backup, and real-time collaboration. This test suite validates the
 * network monitoring infrastructure that enables the app to respond appropriately
 * to connectivity changes.
 *
 * ## Test Categories:
 * - **Interface Compliance**: Contract validation for NetworkAvailabilityMonitor
 * - **State Management**: Network state transitions and Flow emissions
 * - **Timing Accuracy**: Timestamp validation for connection events
 * - **Flow Behavior**: Reactive programming patterns for network state
 * - **Fake Implementation**: Test infrastructure validation
 *
 * ## Key Validations:
 * - Real-time network state changes via kotlinx.coroutines.Flow
 * - Proper timestamp tracking for connection/disconnection events
 * - Coroutine-safe state observation patterns
 * - Platform-agnostic network monitoring interface
 *
 * @see app.logdate.client.networking.NetworkAvailabilityMonitor
 * @see app.logdate.client.networking.NetworkState
 * @see app.logdate.client.networking.fakes.FakeNetworkAvailabilityMonitor
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkAvailabilityMonitorTest {
    
    /**
     * Test dispatcher for controlling coroutine execution in network monitoring tests.
     * This allows precise control over timing and ensures deterministic test behavior
     * when testing Flow emissions and coroutine coordination.
     */
    private val testDispatcher = StandardTestDispatcher()

    /**
     * Tests that the monitor correctly reports true when network is available.
     *
     * This validates the basic positive case where the device has network connectivity.
     * The LogDate app uses this information to enable sync operations, show online
     * status indicators, and allow real-time features.
     *
     * **Test Scenario**: Network available state
     * **Expected Behavior**: isNetworkAvailable() returns true
     * **Real App Usage**: Enabling sync, online features, real-time collaboration
     */
    @Test
    fun isNetworkAvailable_whenConnected_returnsTrue() {
        val monitor = FakeNetworkAvailabilityMonitor()
        monitor.setNetworkAvailable(true)
        
        val result = monitor.isNetworkAvailable()
        
        assertTrue(result)
        assertEquals(1, monitor.isNetworkAvailableCalls)
    }

    /**
     * Tests that the monitor correctly reports false when network is unavailable.
     *
     * This validates the basic negative case where the device lacks network connectivity.
     * The LogDate app uses this information to disable sync operations, show offline
     * indicators, and enable offline-only features.
     *
     * **Test Scenario**: Network unavailable state
     * **Expected Behavior**: isNetworkAvailable() returns false
     * **Real App Usage**: Offline mode, sync pause, cached data reliance
     */
    @Test
    fun isNetworkAvailable_whenDisconnected_returnsFalse() {
        val monitor = FakeNetworkAvailabilityMonitor()
        monitor.setNetworkAvailable(false)
        
        val result = monitor.isNetworkAvailable()
        
        assertFalse(result)
        assertEquals(1, monitor.isNetworkAvailableCalls)
    }

    /**
     * Tests that the fake monitor tracks method invocations for verification.
     *
     * Test infrastructure needs to verify that components properly interact with
     * the network monitor. This test ensures the fake accurately counts how many
     * times observeNetwork() is called, enabling test assertions.
     *
     * **Test Scenario**: Multiple calls to observeNetwork()
     * **Expected Behavior**: Call count accurately tracked
     * **Real App Usage**: Test verification, debugging network interactions
     */
    @Test
    fun observeNetwork_whenCalled_tracksInvocations() {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        monitor.observeNetwork()
        monitor.observeNetwork()
        
        assertEquals(2, monitor.observeNetworkCalls)
    }

    /**
     * Tests that network state changes are properly emitted through the Flow.
     *
     * The LogDate app's reactive architecture depends on timely network state
     * updates. This test validates that when network conditions change, the
     * corresponding NetworkState objects are emitted through the Flow for
     * observers to handle.
     *
     * **Test Scenario**: Sequential network state changes with Flow observation
     * **Expected Behavior**: Each state change triggers corresponding Flow emission
     * **Real App Usage**: Real-time UI updates, sync status changes, connectivity alerts
     */
    @Test
    fun observeNetwork_whenNetworkStateChanges_emitsStates() = runTest {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        val states = mutableListOf<NetworkState>()
        val job = launch {
            monitor.observeNetwork().take(3).toList().let { states.addAll(it) }
        }
        
        // Give collection time to start
        yield()
        
        // Emit different network states
        monitor.emitConnected()
        monitor.emitDisconnected()
        monitor.emitConnected()
        
        job.join()
        
        assertEquals(3, states.size)
        assertTrue(states[0] is NetworkState.Connected)
        assertTrue(states[1] is NetworkState.NotConnected)
        assertTrue(states[2] is NetworkState.Connected)
    }

    @Test
    fun observeNetwork_whenConnectedStateEmitted_containsTimestamp() = runTest {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        val beforeEmit = Clock.System.now()
        
        // Start collecting in background
        val deferred = async {
            monitor.observeNetwork().first()
        }
        
        // Give collection time to start
        yield()
        
        monitor.emitConnected()
        val afterEmit = Clock.System.now()
        
        val state = deferred.await() as NetworkState.Connected
        
        assertTrue(state.lastConnected >= beforeEmit)
        assertTrue(state.lastConnected <= afterEmit)
    }

    @Test
    fun observeNetwork_whenNotConnectedStateEmitted_containsTimestamp() = runTest {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        val beforeEmit = Clock.System.now()
        
        // Start collecting before emitting
        val deferred = async {
            monitor.observeNetwork().first()
        }
        
        // Give the collection time to start
        yield()
        
        monitor.emitDisconnected()
        val afterEmit = Clock.System.now()
        
        val state = deferred.await() as NetworkState.NotConnected
        
        assertTrue(state.lastConnected >= beforeEmit)
        assertTrue(state.lastConnected <= afterEmit)
    }

    @Test
    fun networkState_connected_preservesTimestamp() {
        val timestamp = Clock.System.now()
        val state = NetworkState.Connected(timestamp)
        
        assertEquals(timestamp, state.lastConnected)
        assertTrue(state is NetworkState.Connected)
    }

    @Test
    fun networkState_notConnected_preservesTimestamp() {
        val timestamp = Clock.System.now()
        val state = NetworkState.NotConnected(timestamp)
        
        assertEquals(timestamp, state.lastConnected)
        assertTrue(state is NetworkState.NotConnected)
    }

    @Test
    fun fakeNetworkMonitor_clear_resetsState() {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        // Modify state
        monitor.setNetworkAvailable(false)
        monitor.isNetworkAvailable()
        monitor.observeNetwork()
        
        // Clear and verify reset
        monitor.clear()
        
        assertTrue(monitor.isAvailable) // Default is true
        assertEquals(0, monitor.isNetworkAvailableCalls)
        assertEquals(0, monitor.observeNetworkCalls)
    }

    @Test
    fun fakeNetworkMonitor_multipleNetworkStateChanges_allEmitted() = runTest {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        val states = mutableListOf<NetworkState>()
        val job = launch {
            monitor.observeNetwork().take(5).toList().let { states.addAll(it) }
        }
        
        // Give collection time to start
        yield()
        
        // Emit rapid state changes
        monitor.emitConnected()
        monitor.emitDisconnected()
        monitor.emitConnected()
        monitor.emitDisconnected()
        monitor.emitConnected()
        
        job.join()
        
        assertEquals(5, states.size)
        assertTrue(states[0] is NetworkState.Connected)
        assertTrue(states[1] is NetworkState.NotConnected)
        assertTrue(states[2] is NetworkState.Connected)
        assertTrue(states[3] is NetworkState.NotConnected)
        assertTrue(states[4] is NetworkState.Connected)
    }

    @Test
    fun isNetworkAvailable_multipleCallsWithDifferentStates_returnsCorrectValues() {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        // Test multiple state changes
        monitor.setNetworkAvailable(true)
        assertTrue(monitor.isNetworkAvailable())
        
        monitor.setNetworkAvailable(false)
        assertFalse(monitor.isNetworkAvailable())
        
        monitor.setNetworkAvailable(true)
        assertTrue(monitor.isNetworkAvailable())
        
        assertEquals(3, monitor.isNetworkAvailableCalls)
    }

    @Test
    fun observeNetwork_withCustomNetworkState_emitsCorrectly() = runTest {
        val monitor = FakeNetworkAvailabilityMonitor()
        
        val customTimestamp = Clock.System.now()
        val customState = NetworkState.Connected(customTimestamp)
        
        val deferred = async {
            monitor.observeNetwork().first()
        }
        
        // Give collection time to start
        yield()
        
        monitor.emitNetworkState(customState)
        
        val receivedState = deferred.await() as NetworkState.Connected
        assertEquals(customTimestamp, receivedState.lastConnected)
    }

    @Test
    fun networkAvailabilityMonitor_interfaceContract_implementedCorrectly() {
        val monitor: NetworkAvailabilityMonitor = FakeNetworkAvailabilityMonitor()
        
        // Verify interface methods are callable
        val isAvailable = monitor.isNetworkAvailable()
        val networkFlow = monitor.observeNetwork()
        
        // Basic type checks
        assertTrue(isAvailable is Boolean)
        assertTrue(networkFlow != null)
    }
}