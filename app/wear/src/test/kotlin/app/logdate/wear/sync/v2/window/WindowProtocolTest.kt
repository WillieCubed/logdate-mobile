package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowProtocolTest {
    private val f = WindowFixture()
    private var enabled = true
    private val states = PeerStateStore()
    private val protocol = WindowProtocol({ enabled }, f.peers, states, f.storage)

    private fun phone(
        node: String = "phone",
        hour: Int = 4,
        window: Boolean = true,
        at: Long = 1,
    ) {
        states.accept(
            node,
            Decoded.Supported(
                StateDocument(
                    role = PeerRole.PHONE,
                    writtenAt = at,
                    phone = PhoneState(dayStartHour = hour, features = if (window) setOf(WearFeatures.WINDOW) else emptySet()),
                ),
            ),
            PeerRole.PHONE,
        )
    }

    @Test fun `only the verified phone can negotiate the window and day boundary`() =
        runTest {
            phone("stranger", hour = 22, at = 100)
            assertFalse(protocol.active())
            assertNull(protocol.dayStartHour())
            phone()
            assertTrue(protocol.active())
            assertEquals(4, protocol.dayStartHour())
        }

    @Test fun `negotiated windows keep legacy deletions held when a link becomes unavailable`() =
        runTest {
            phone()
            assertTrue(protocol.active())
            f.current = null
            assertTrue(protocol.active())
            assertNull(protocol.dayStartHour())
            enabled = false
            assertFalse(protocol.active())
        }

    @Test fun `negotiation from a previous exchange cannot authorize a new peer`() =
        runTest {
            phone()
            assertTrue(protocol.active())
            f.current = f.peer.copy(phoneNode = "new phone")
            assertFalse(protocol.active())
            assertNull(protocol.dayStartHour())
        }

    @Test fun `invalid day boundaries and older phone features use the local default`() =
        runTest {
            phone(hour = 24)
            assertNull(protocol.dayStartHour())
            phone(window = false)
            assertFalse(protocol.supports(f.peer))
            assertNull(protocol.dayStartHour())
            enabled = false
            phone()
            assertFalse(protocol.supports(f.peer))
        }

    @Test fun `startup support negotiation holds old deletes after the phone disappears`() =
        runTest {
            phone()
            assertTrue(protocol.supports(f.peer))
            f.current = null
            assertTrue(protocol.active())
        }
}
