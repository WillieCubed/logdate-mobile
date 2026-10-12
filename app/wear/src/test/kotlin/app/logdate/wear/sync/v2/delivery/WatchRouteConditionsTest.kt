package app.logdate.wear.sync.v2.delivery

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.saver.NetworkConnectionType
import app.logdate.client.networking.saver.NetworkSaverModeProvider
import app.logdate.client.networking.saver.NetworkSaverState
import app.logdate.client.sensor.battery.BatteryInfoProvider
import app.logdate.client.sensor.battery.BatteryState
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.delivery.PhoneReach
import app.logdate.client.sync.peer.delivery.RouteConditions
import app.logdate.client.sync.peer.delivery.WatchNetwork
import app.logdate.client.sync.peer.link.LinkProblem
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import app.logdate.wear.sync.v2.MemoryKeyValueStorage
import app.logdate.wear.sync.v2.MemoryPairKeyVault
import app.logdate.wear.sync.v2.link.WatchLinkStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Tests [WatchRouteConditions]: what the watch knows when it routes a change. */
class WatchRouteConditionsTest {
    private val links = WatchLinkStore(MemoryKeyValueStorage(), MemoryPairKeyVault())
    private val config = mockk<LogDateConfigRepository>()
    private val network = mockk<NetworkSaverModeProvider>()
    private val battery = mockk<BatteryInfoProvider>()
    private val transport = mockk<DataLayerTransportV2>()
    private var bound: OriginBoundSession? = OriginBoundSession("https://logdate.test", UserSession("access", "refresh", "account"))
    private val sessions = mockk<SessionStorage>()
    private val linkId = LinkWire.encodeKey(ByteArray(32) { 1 })
    private var enabled = true
    private var captured = PeerDeliveryContext(linkId, "phone-node", "watch-node", LinkMode.CLOUD, "account", "https://logdate.test")
    private val peers = WatchDeliveryPeer(links, sessions, config, { "watch-node" }, { enabled })
    private val conditions get() =
        WatchRouteConditions(
            transport,
            { CloudPresence.SIGNED_IN },
            config,
            network,
            battery,
            captured,
            peers,
        )

    init {
        every { sessions.getOriginBoundSession() } answers { bound }
        every { sessions.getSession() } answers { bound?.session }
        every { config.getCurrentBackendUrl() } returns "https://logdate.test"
        coEvery { transport.reach() } returns PhoneReach.RELAY_ONLY
        every { config.getCurrentServerDescriptor() } returns
            descriptor(ServerProtocolFeature.DEVICE_LINKING_V1, ServerProtocolFeature.CONDITIONAL_CREATE_V1)
        coEvery { network.getCurrentDataSaverState() } returns NetworkSaverState(false, NetworkConnectionType.WIFI)
        coEvery { battery.getCurrentBatteryState() } returns BatteryState(level = 50, isCharging = true, isPowerSaveMode = false)
    }

    @Test
    fun `a watch linked to the account on a ready server over wifi may upload`() =
        runTest {
            links.saveCloudLink("phone-node", "account", "https://logdate.test", linkId = linkId)

            assertEquals(
                RouteConditions(
                    phone = PhoneReach.RELAY_ONLY,
                    phoneCloud = CloudPresence.SIGNED_IN,
                    linked = true,
                    serverReady = true,
                    network = WatchNetwork.UNMETERED,
                    charging = true,
                ),
                conditions.current(),
            )
        }

    @Test
    fun `a watch only paired with its phone is not linked`() =
        runTest {
            links.savePairing("phone-node", ByteArray(32))

            assertFalse(conditions.current().linked)
        }

    @Test
    fun `a link with a problem is not linked`() =
        runTest {
            links.saveCloudLink("phone-node", "account", "https://logdate.test", linkId = linkId)
            links.recordProblem("phone-node", LinkProblem.SESSION_REVOKED)

            assertFalse(conditions.current().linked)
        }

    @Test
    fun `a server without create if absent is not ready`() {
        assertFalse(serverReady(descriptor(ServerProtocolFeature.DEVICE_LINKING_V1)))
        assertFalse(serverReady(null))
    }

    @Test
    fun `a network that cannot be read counts as none`() =
        runTest {
            coEvery { network.getCurrentDataSaverState() } throws IllegalStateException("No connectivity service")

            assertEquals(WatchNetwork.NONE, conditions.current().network)
        }

    @Test
    fun `missing credentials cannot release the linked watch to cloud`() =
        runTest {
            link()
            bound = null
            assertClosed()
        }

    @Test
    fun `a different installed account cannot use the previous link`() =
        runTest {
            link()
            val current = requireNotNull(bound)
            bound = current.copy(session = current.session.copy(accountId = "different-account"))
            assertClosed()
        }

    @Test
    fun `a session from another server cannot use the previous link`() =
        runTest {
            link()
            bound = bound?.copy(origin = "https://other.test")
            assertClosed()
        }

    @Test
    fun `changing the configured server closes direct routing`() =
        runTest {
            link()
            every { config.getCurrentBackendUrl() } returns "https://other.test"
            assertClosed()
        }

    @Test
    fun `a descriptor from another origin is not server readiness proof`() =
        runTest {
            link()
            every { config.getCurrentServerDescriptor() } returns
                descriptor(ServerProtocolFeature.DEVICE_LINKING_V1, ServerProtocolFeature.CONDITIONAL_CREATE_V1).copy(
                    serverOrigin = "https://other.test",
                )
            assertClosed()
        }

    @Test
    fun `an older link must acquire server provenance before direct routing`() =
        runTest {
            links.saveCloudLink("phone-node", "account")
            assertClosed()
        }

    @Test
    fun `a link replaced while reading the network discards the old phone snapshot`() =
        runTest {
            link()
            coEvery { network.getCurrentDataSaverState() } coAnswers {
                links.saveCloudLink("another-phone", "account", "https://logdate.test")
                NetworkSaverState(false, NetworkConnectionType.WIFI)
            }
            assertClosed()
        }

    @Test
    fun `signing out while reading the charger discards cloud readiness`() =
        runTest {
            link()
            coEvery { battery.getCurrentBatteryState() } answers {
                bound = null
                BatteryState(level = 50, isCharging = true, isPowerSaveMode = false)
            }
            assertClosed()
        }

    @Test
    fun `network cancellation propagates instead of reporting no internet`() =
        runTest {
            link()
            coEvery { network.getCurrentDataSaverState() } throws CancellationException("Stopped")
            assertFailsWith<CancellationException> { conditions.current() }
        }

    @Test
    fun `unreadable charger status never counts as charging`() =
        runTest {
            link()
            coEvery { battery.getCurrentBatteryState() } throws IllegalStateException("Battery unavailable")
            assertFalse(conditions.current().charging)
        }

    @Test
    fun `a phone remains reachable while its linked server descriptor is unavailable`() =
        runTest {
            link()
            every { config.getCurrentServerDescriptor() } returns null
            val current = conditions.current()
            assertEquals(true, current.linked)
            assertEquals(PhoneReach.RELAY_ONLY, current.phone)
            assertFalse(current.serverReady)
        }

    @Test
    fun `pair only routing still reaches the phone without cloud credentials`() =
        runTest {
            bound = null
            captured = captured.copy(mode = LinkMode.PAIR, accountId = null, serverOrigin = null)
            links.savePairing("phone-node", ByteArray(32), linkId = linkId)
            val current = conditions.current()
            assertEquals(PhoneReach.RELAY_ONLY, current.phone)
            assertFalse(current.linked)
            assertFalse(current.serverReady)
        }

    @Test
    fun `stale and unfinished cloud links cannot route captures`() =
        runTest {
            link()
            links.markStale("phone-node")
            assertClosed()
            links.beginCloudInstall("phone-node", ByteArray(32), "account", "https://logdate.test")
            assertClosed()
        }

    @Test
    fun `a link without a verified exchange cannot authorize routing`() =
        runTest {
            links.saveCloudLink("phone-node", "account", "https://logdate.test")
            assertClosed()
        }

    @Test
    fun `a renewed exchange cannot inherit an earlier run's routing conditions`() =
        runTest {
            links.saveCloudLink("phone-node", "account", "https://logdate.test", LinkWire.encodeKey(ByteArray(32) { 2 }))
            assertClosed()
        }

    @Test
    fun `disabling the experience closes the captured route`() =
        runTest {
            link()
            enabled = false
            assertClosed()
        }

    @Test
    fun `a raw session different from the installed session closes routing`() =
        runTest {
            link()
            every { sessions.getSession() } returns UserSession("other-access", "other-refresh", "account")
            assertClosed()
        }

    @Test
    fun `renewing the exchange during network lookup closes the captured route`() =
        runTest {
            link()
            coEvery { network.getCurrentDataSaverState() } coAnswers {
                links.saveCloudLink("phone-node", "account", "https://logdate.test", LinkWire.encodeKey(ByteArray(32) { 2 }))
                NetworkSaverState(false, NetworkConnectionType.WIFI)
            }
            assertClosed()
        }

    private suspend fun link() = links.saveCloudLink("phone-node", "account", "https://logdate.test", linkId = linkId)

    private suspend fun assertClosed() {
        val current = conditions.current()
        assertFalse(current.linked)
        assertFalse(current.serverReady)
        assertEquals(PhoneReach.UNREACHABLE, current.phone)
        assertEquals(CloudPresence.UNKNOWN, current.phoneCloud)
    }

    private fun descriptor(vararg features: String) =
        ServerDescriptor(
            serverOrigin = "https://logdate.test",
            apiBaseUrl = "https://logdate.test/api/v1",
            deploymentKind = DeploymentKind.FIRST_PARTY,
            displayName = "LogDate",
            protocolFeatures = features.toList(),
        )
}
