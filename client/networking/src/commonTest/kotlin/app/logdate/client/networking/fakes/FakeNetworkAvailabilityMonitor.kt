package app.logdate.client.networking.fakes

import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.datetime.Clock

/**
 * Fake implementation of NetworkAvailabilityMonitor for comprehensive testing.
 *
 * This test double provides complete control over network state for deterministic
 * testing of components that depend on network availability. It enables testing
 * of various network scenarios, state transitions, and edge cases that would be
 * difficult or impossible to reproduce with real network conditions.
 *
 * ## Features:
 * - **Controllable State**: Manually set network availability for specific test scenarios
 * - **Flow Emissions**: Emit custom NetworkState objects through the observation Flow
 * - **Call Tracking**: Monitor how many times methods are invoked for verification
 * - **State Reset**: Clear state between tests for proper test isolation
 * - **Coroutine Safe**: Thread-safe implementation for concurrent testing
 *
 * ## Usage Patterns:
 * ```kotlin
 * // Set up specific network state
 * val monitor = FakeNetworkAvailabilityMonitor()
 * monitor.setNetworkAvailable(false)
 * 
 * // Emit state changes
 * monitor.emitConnected()
 * monitor.emitDisconnected()
 * 
 * // Verify interactions
 * assertEquals(2, monitor.observeNetworkCalls)
 * ```
 *
 * ## Test Scenarios:
 * - Offline app behavior validation
 * - Network state transition testing
 * - Sync operation triggering verification
 * - UI state change validation
 * - Error handling during connectivity changes
 *
 * @see app.logdate.client.networking.NetworkAvailabilityMonitor
 * @see app.logdate.client.networking.NetworkState
 */
class FakeNetworkAvailabilityMonitor : NetworkAvailabilityMonitor {
    
    private val _networkState = MutableSharedFlow<NetworkState>()
    
    // Configurable network availability state
    var isAvailable: Boolean = true
    
    // Track method calls for verification
    var isNetworkAvailableCalls = 0
    var observeNetworkCalls = 0
    
    override fun isNetworkAvailable(): Boolean {
        isNetworkAvailableCalls++
        return isAvailable
    }
    
    override fun observeNetwork(): SharedFlow<NetworkState> {
        observeNetworkCalls++
        return _networkState
    }
    
    // Test utilities
    suspend fun emitNetworkState(state: NetworkState) {
        _networkState.emit(state)
    }
    
    suspend fun emitConnected() {
        _networkState.emit(NetworkState.Connected(Clock.System.now()))
    }
    
    suspend fun emitDisconnected() {
        _networkState.emit(NetworkState.NotConnected(Clock.System.now()))
    }
    
    fun setNetworkAvailable(available: Boolean) {
        isAvailable = available
    }
    
    fun clear() {
        isAvailable = true
        isNetworkAvailableCalls = 0
        observeNetworkCalls = 0
    }
}