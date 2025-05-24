package app.logdate.client.networking

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive test suite for NetworkState sealed interface and implementations.
 *
 * NetworkState is the core data model for representing connectivity status in the
 * LogDate app. It provides type-safe state representation with accurate timing
 * information, enabling the app to make informed decisions about online/offline
 * behavior and data synchronization.
 *
 * ## Test Categories:
 * - **State Creation**: Proper instantiation with timestamp data
 * - **Type Safety**: Sealed interface behavior and type checking
 * - **Data Integrity**: Immutability and data preservation
 * - **Equality**: Object comparison and hash code behavior
 * - **Timing**: Timestamp accuracy and edge case handling
 *
 * ## Key Validations:
 * - Connected and NotConnected state creation with accurate timestamps
 * - Proper sealed interface behavior for exhaustive when expressions
 * - Data class equality and hash code consistency
 * - Immutable state objects for thread-safe usage
 * - Edge case handling for timestamp boundaries
 *
 * @see app.logdate.client.networking.NetworkState
 * @see app.logdate.client.networking.NetworkState.Connected
 * @see app.logdate.client.networking.NetworkState.NotConnected
 */
class NetworkStateTest {

    /**
     * Tests creation of Connected network state with timestamp preservation.
     *
     * Connected states represent successful network connectivity in the LogDate app.
     * The timestamp indicates when the connection was established, which is crucial
     * for determining cache validity and sync status.
     *
     * **Test Scenario**: Connected state creation with specific timestamp
     * **Expected Behavior**: State preserves exact timestamp and maintains type
     * **Real App Usage**: Recording when sync became available, cache decisions
     */
    @Test
    fun networkStateConnected_createsWithTimestamp() {
        val timestamp = Clock.System.now()
        val state = NetworkState.Connected(timestamp)
        
        assertEquals(timestamp, state.lastConnected)
        assertTrue(state is NetworkState.Connected)
    }

    /**
     * Tests creation of NotConnected network state with timestamp preservation.
     *
     * NotConnected states represent network unavailability in the LogDate app.
     * The timestamp indicates when connectivity was lost, enabling features like
     * "offline since" indicators and data staleness warnings.
     *
     * **Test Scenario**: NotConnected state creation with specific timestamp
     * **Expected Behavior**: State preserves exact timestamp and maintains type
     * **Real App Usage**: Tracking offline duration, user experience decisions
     */
    @Test
    fun networkStateNotConnected_createsWithTimestamp() {
        val timestamp = Clock.System.now()
        val state = NetworkState.NotConnected(timestamp)
        
        assertEquals(timestamp, state.lastConnected)
        assertTrue(state is NetworkState.NotConnected)
    }

    @Test
    fun networkStateConnected_equalityWorks() {
        val timestamp = Clock.System.now()
        val state1 = NetworkState.Connected(timestamp)
        val state2 = NetworkState.Connected(timestamp)
        
        assertEquals(state1, state2)
        assertEquals(state1.hashCode(), state2.hashCode())
    }

    @Test
    fun networkStateNotConnected_equalityWorks() {
        val timestamp = Clock.System.now()
        val state1 = NetworkState.NotConnected(timestamp)
        val state2 = NetworkState.NotConnected(timestamp)
        
        assertEquals(state1, state2)
        assertEquals(state1.hashCode(), state2.hashCode())
    }

    @Test
    fun networkStates_withDifferentTimestamps_areNotEqual() {
        val timestamp1 = Clock.System.now()
        val timestamp2 = timestamp1 + 1.seconds
        
        val connected1 = NetworkState.Connected(timestamp1)
        val connected2 = NetworkState.Connected(timestamp2)
        
        assertNotEquals(connected1, connected2)
        
        val notConnected1 = NetworkState.NotConnected(timestamp1)
        val notConnected2 = NetworkState.NotConnected(timestamp2)
        
        assertNotEquals(notConnected1, notConnected2)
    }

    @Test
    fun networkStates_differentTypes_areNotEqual() {
        val timestamp = Clock.System.now()
        val connected = NetworkState.Connected(timestamp)
        val notConnected = NetworkState.NotConnected(timestamp)
        
        assertNotEquals<NetworkState>(connected, notConnected)
        assertNotEquals(connected.hashCode(), notConnected.hashCode())
    }

    @Test
    fun networkStateConnected_toStringContainsUsefulInfo() {
        val timestamp = Clock.System.now()
        val state = NetworkState.Connected(timestamp)
        
        val stringRepresentation = state.toString()
        
        assertTrue(stringRepresentation.contains("Connected"))
        assertTrue(stringRepresentation.contains(timestamp.toString()))
    }

    @Test
    fun networkStateNotConnected_toStringContainsUsefulInfo() {
        val timestamp = Clock.System.now()
        val state = NetworkState.NotConnected(timestamp)
        
        val stringRepresentation = state.toString()
        
        assertTrue(stringRepresentation.contains("NotConnected"))
        assertTrue(stringRepresentation.contains(timestamp.toString()))
    }

    @Test
    fun networkState_timestampAccuracy() {
        val beforeCreation = Clock.System.now()
        val state = NetworkState.Connected(Clock.System.now())
        val afterCreation = Clock.System.now()
        
        assertTrue(state.lastConnected >= beforeCreation)
        assertTrue(state.lastConnected <= afterCreation)
    }

    @Test
    fun networkState_sealedInterfacePolymorphism() {
        val timestamp = Clock.System.now()
        val states: List<NetworkState> = listOf(
            NetworkState.Connected(timestamp),
            NetworkState.NotConnected(timestamp + 1.minutes)
        )
        
        assertEquals(2, states.size)
        assertTrue(states[0] is NetworkState.Connected)
        assertTrue(states[1] is NetworkState.NotConnected)
        
        // Test when expression coverage
        states.forEach { state ->
            val description = when (state) {
                is NetworkState.Connected -> "Device is connected at ${state.lastConnected}"
                is NetworkState.NotConnected -> "Device is not connected, last seen at ${state.lastConnected}"
            }
            assertTrue(description.isNotEmpty())
        }
    }

    @Test
    fun networkState_copyFunctionality() {
        val originalTimestamp = Clock.System.now()
        val newTimestamp = originalTimestamp + 5.minutes
        
        val originalState = NetworkState.Connected(originalTimestamp)
        val copiedState = originalState.copy(lastConnected = newTimestamp)
        
        assertEquals(newTimestamp, copiedState.lastConnected)
        assertNotEquals(originalState, copiedState)
    }

    @Test
    fun networkState_destructuringDeclaration() {
        val timestamp = Clock.System.now()
        val connected = NetworkState.Connected(timestamp)
        val (lastConnected) = connected
        
        assertEquals(timestamp, lastConnected)
        
        val notConnected = NetworkState.NotConnected(timestamp)
        val (lastConnectedFromNotConnected) = notConnected
        
        assertEquals(timestamp, lastConnectedFromNotConnected)
    }

    @Test
    fun networkState_immutability() {
        val timestamp = Clock.System.now()
        val state = NetworkState.Connected(timestamp)
        
        // Verify that lastConnected is a val (immutable)
        val extractedTimestamp = state.lastConnected
        assertEquals(timestamp, extractedTimestamp)
        
        // Since it's a data class, the timestamp should be immutable
        // We can't modify it directly, which is the expected behavior
        assertTrue(state.lastConnected == timestamp)
    }

    @Test
    fun networkState_withEdgeCaseTimestamps() {
        // Test with very old timestamp
        val veryOldTimestamp = Instant.fromEpochMilliseconds(0)
        val oldState = NetworkState.Connected(veryOldTimestamp)
        assertEquals(veryOldTimestamp, oldState.lastConnected)
        
        // Test with future timestamp (theoretical)
        val futureTimestamp = Clock.System.now() + 365.minutes * 24 * 365 // 1 year from now
        val futureState = NetworkState.NotConnected(futureTimestamp)
        assertEquals(futureTimestamp, futureState.lastConnected)
    }

    @Test
    fun networkState_multipleInstancesWithSameTimestamp() {
        val timestamp = Clock.System.now()
        
        val states = (1..100).map { 
            if (it % 2 == 0) NetworkState.Connected(timestamp) 
            else NetworkState.NotConnected(timestamp)
        }
        
        // Verify all connected states are equal
        val connectedStates = states.filterIsInstance<NetworkState.Connected>()
        assertTrue(connectedStates.all { it == connectedStates.first() })
        
        // Verify all not connected states are equal
        val notConnectedStates = states.filterIsInstance<NetworkState.NotConnected>()
        assertTrue(notConnectedStates.all { it == notConnectedStates.first() })
    }
}