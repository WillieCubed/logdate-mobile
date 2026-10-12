package app.logdate.client.reminders

import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserPreferences
import app.logdate.client.datastore.UserSession
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import app.logdate.shared.config.LogDateConfigRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneReminderEnvironmentReaderTest {
    private val sessions = mockk<SessionStorage>()
    private val config = mockk<LogDateConfigRepository>()
    private val preferences = mockk<LogdatePreferencesDataSource>()
    private val shared = mockk<WatchPreferencesRepository>()
    private val flags = mockk<FeatureFlagStore>()
    private val peers = mockk<PhoneDeliveryPeers>()
    private val states = PeerStateStore()
    private var connected = setOf("watch")
    private var afterNodes: () -> Unit = {}
    private var locationAllowed = true
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val reader =
        PhoneReminderEnvironmentReader(
            sessions,
            config,
            preferences,
            shared,
            flags,
            peers,
            states,
            connectedNodes = { connected.also { afterNodes() } },
            locationAllowed = { locationAllowed },
            zone = { TimeZone.UTC },
        )

    init {
        every { sessions.getSession() } returns null
        every { sessions.getOriginBoundSession() } returns null
        every { config.getCurrentBackendUrl() } returns "https://api.example.com"
        coEvery { preferences.getPreferences() } returns UserPreferences(dayStartHour = 6)
        coEvery { shared.get(SharedPreferenceKey.RemindersEnabled) } returns true
        coEvery { shared.get(SharedPreferenceKey.CapturePlaces) } returns true
        coEvery { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) } returns true
        coEvery { peers.all() } returns listOf(peer)
    }

    private fun document(
        node: String = "watch",
        link: String = peer.linkId,
        features: Set<String> = setOf(WearFeatures.REMINDERS),
    ) {
        states.accept(
            node,
            Decoded.Supported(StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState(features = features, linkId = link))),
            PeerRole.WATCH,
        )
    }

    @Test
    fun `revoked location permission disables arrivals despite old history and shared consent`() =
        runTest {
            locationAllowed = false
            assertFalse(reader.read().capturePlaces)
        }

    @Test
    fun `local reminders use configured day and only the verified nodes advertisement`() =
        runTest {
            document()
            val result = reader.read()
            assertEquals("local", result.audience)
            assertTrue(result.enabled)
            assertTrue(result.capturePlaces)
            assertEquals(6, result.dayStartHour)
            assertEquals(peer, result.watch?.context)
            assertEquals(true, result.watch?.reachable)
            assertEquals(true, result.watch?.supportsReminders)
            assertEquals(false, result.watch?.supportsSupplementalReminders)
        }

    @Test
    fun `foreign and stale link advertisements cannot authorize watch reminders`() =
        runTest {
            document(node = "foreign")
            document(link = "old-link")
            assertNull(reader.read().watch)
            document(features = emptySet())
            assertEquals(false, reader.read().watch?.supportsReminders)
        }

    @Test
    fun `offline verified watch is retained without pretending it is reachable`() =
        runTest {
            document()
            connected = emptySet()
            val result = reader.read()
            assertEquals(peer, result.watch?.context)
            assertEquals(false, result.watch?.reachable)
        }

    @Test
    fun `cloud audience changes with account and server without including credentials`() =
        runTest {
            val session = UserSession("access-secret", "refresh-secret", "account-a")
            every { sessions.getSession() } returns session
            every { sessions.getOriginBoundSession() } returns OriginBoundSession("https://api.example.com", session)
            val first = reader.read().audience
            assertTrue(first.isNotBlank())
            assertFalse(first.contains("secret"))
            val other = session.copy(accountId = "account-b")
            every { sessions.getSession() } returns other
            every { sessions.getOriginBoundSession() } returns OriginBoundSession("https://api.example.com", other)
            assertNotEquals(first, reader.read().audience)
            every { config.getCurrentBackendUrl() } returns "https://other.example.com"
            every { sessions.getOriginBoundSession() } returns OriginBoundSession("https://other.example.com", other)
            assertNotEquals(first, reader.read().audience)
        }

    @Test
    fun `unbound and wrong origin sessions hold delivery`() =
        runTest {
            val session = UserSession("access", "refresh", "a")
            every { sessions.getSession() } returns session
            assertEquals("", reader.read().audience)
            every { sessions.getOriginBoundSession() } returns OriginBoundSession("https://foreign.example.com", session)
            assertEquals("", reader.read().audience)
        }

    @Test
    fun `switching accounts during transport reads holds delivery`() =
        runTest {
            document()
            afterNodes = { every { sessions.getSession() } returns UserSession("access", "refresh", "a") }
            val result = reader.read()
            assertEquals("", result.audience)
            assertFalse(result.enabled)
        }

    @Test
    fun `flag or shared opt out cannot produce an enabled snapshot`() =
        runTest {
            coEvery { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) } returns false
            assertFalse(reader.read().enabled)
            coEvery { flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) } returns true
            coEvery { shared.get(SharedPreferenceKey.RemindersEnabled) } returns false
            assertFalse(reader.read().enabled)
        }

    @Test fun supplementalDeliveryRequiresItsOwnVerifiedAdvertisement() =
        runTest {
            document(features = setOf(WearFeatures.REMINDERS, WearFeatures.REMINDER_SUPPLEMENTS))
            assertEquals(true, reader.read().watch?.supportsSupplementalReminders)
        }
}
