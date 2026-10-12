package app.logdate.wear.sync.v2

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerDocument
import app.logdate.client.sync.peer.state.PeerLinkState
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.StateMerger
import app.logdate.client.sync.peer.state.WatchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [WatchStateMirror]: the watch takes the Labs switch from the phone's document and nothing
 * else, and publishes its own document and capability.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchStateMirrorTest {
    @Test
    fun `failed capability update retries when shared state changes`() =
        runTest {
            val shared = MutableStateFlow<Map<String, Stamped>>(emptyMap())
            val mirror =
                WatchStateMirror(
                    flags,
                    channel,
                    capabilities,
                    PeerStateStore(),
                    StateMerger(FixedClock(1_000)),
                    sharedPreferences = shared,
                )
            capabilities.succeeds = false
            mirror.start(backgroundScope)
            runCurrent()
            capabilities.succeeds = true
            shared.value = mapOf("reminders.enabled" to Stamped(JsonPrimitive(true), 900))
            runCurrent()
            assertEquals(listOf("remove $WATCH_V2", "remove $WATCH_V2"), capabilities.calls)
        }

    @Test
    fun `posting a reminder publishes its time without changing capability`() =
        runTest {
            val posted = MutableStateFlow<Long?>(null)
            val mirror =
                WatchStateMirror(
                    flags,
                    channel,
                    capabilities,
                    PeerStateStore(),
                    StateMerger(FixedClock(1_000)),
                    lastReminderAt = posted,
                )
            mirror.start(backgroundScope)
            runCurrent()
            posted.value = 900
            runCurrent()
            assertEquals(900L, channel.published.last().lastReminderAt)
            assertEquals(listOf("remove $WATCH_V2"), capabilities.calls)
        }

    @Test
    fun `shared choices publish without changing the Labs switch`() =
        runTest {
            val shared = MutableStateFlow<Map<String, Stamped>>(emptyMap())
            val mirror =
                WatchStateMirror(
                    flags,
                    channel,
                    capabilities,
                    PeerStateStore(),
                    StateMerger(FixedClock(1_000)),
                    sharedPreferences = shared,
                )
            mirror.start(backgroundScope)
            runCurrent()
            shared.value = mapOf("reminders.enabled" to Stamped(JsonPrimitive(true), 900))
            runCurrent()
            assertEquals(shared.value, channel.published.last().shared)
            assertEquals(listOf("remove $WATCH_V2"), capabilities.calls)
        }

    @Test
    fun `phone preferences are adopted even when the Labs switch is unchanged`() =
        runTest {
            flags.value.value = true
            val adopted = mutableListOf<Pair<String, StateDocument>>()
            val mirror =
                WatchStateMirror(
                    flags,
                    channel,
                    capabilities,
                    PeerStateStore(),
                    StateMerger(FixedClock(1_000)),
                    adoptPhonePreferences = { node, document -> adopted += node to document },
                )
            val document = phoneDocument(switchOn = true)
            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(document))
            assertEquals(listOf(PHONE_NODE to document), adopted)
            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `saved reminder migration runs before first publication`() =
        runTest {
            val shared = MutableStateFlow<Map<String, Stamped>>(emptyMap())
            val mirror =
                WatchStateMirror(
                    flags,
                    channel,
                    capabilities,
                    PeerStateStore(),
                    StateMerger(FixedClock(1_000)),
                    sharedPreferences = shared,
                    initializeShared = { shared.value = mapOf("reminders.enabled" to Stamped(JsonPrimitive(true), 900)) },
                )
            mirror.start(backgroundScope)
            runCurrent()
            assertEquals(
                JsonPrimitive(true),
                channel.published
                    .first()
                    .shared["reminders.enabled"]
                    ?.value,
            )
        }

    private val flags = InMemoryFlags()
    private val channel = FakeChannel()
    private val capabilities = FakeCapabilities()
    private val mirror =
        WatchStateMirror(
            featureFlags = flags,
            channel = channel,
            capabilities = capabilities,
            peerStates = PeerStateStore(),
            merger = StateMerger(FixedClock(1_000)),
            clock = FixedClock(1_000),
        )

    @Test
    fun `the phone turning the switch on turns it on here`() =
        runTest {
            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = true)))

            assertEquals(listOf(true), flags.writes)
        }

    @Test
    fun `the phone turning the switch off turns it off here`() =
        runTest {
            flags.value.value = true

            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = false)))

            assertEquals(listOf(false), flags.writes)
        }

    @Test
    fun `a document from this watch's own node changes nothing`() =
        runTest {
            mirror.onPhoneDocument(WATCH_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = true)))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `a document from another watch changes nothing`() =
        runTest {
            val watchClaimingTheSwitch =
                StateDocument(role = PeerRole.WATCH, writtenAt = 1, phone = PhoneState(flags = mapOf(FLAG_KEY to true)))

            mirror.onPhoneDocument("other-watch", WATCH_NODE, Decoded.Supported(watchClaimingTheSwitch))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `fields the phone does not own are ignored`() =
        runTest {
            val phoneWithAWatchSection =
                StateDocument(
                    role = PeerRole.PHONE,
                    writtenAt = 1,
                    phone = PhoneState(),
                    watch = WatchState(features = setOf("receipts.v1")),
                )

            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneWithAWatchSection))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `a phone on a newer schema leaves the switch as it is`() =
        runTest {
            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Unsupported(2, "newer"))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `an unchanged switch is not written again`() =
        runTest {
            flags.value.value = true

            mirror.onPhoneDocument(PHONE_NODE, WATCH_NODE, Decoded.Supported(phoneDocument(switchOn = true)))

            assertTrue(flags.writes.isEmpty())
        }

    @Test
    fun `starting catches up with the phone and publishes this watch's state`() =
        runTest {
            channel.peers = listOf(PeerDocument(PHONE_NODE, Decoded.Supported(phoneDocument(switchOn = true))))

            mirror.start(backgroundScope)
            runCurrent()

            assertEquals(listOf(true), flags.writes)
            assertEquals(listOf(StateDocument(role = PeerRole.WATCH, writtenAt = 1_000, watch = WatchState())), channel.published)
            assertEquals(listOf("add $WATCH_V2"), capabilities.calls)
        }

    @Test
    fun `this watch advertises version 2 only while its switch is on`() =
        runTest {
            mirror.start(backgroundScope)
            runCurrent()

            flags.value.value = true
            runCurrent()

            assertEquals(listOf("remove $WATCH_V2", "add $WATCH_V2"), capabilities.calls)
            assertEquals(2, channel.published.size)
        }

    @Test
    fun `a link change publishes the watch state without toggling the flag`() =
        runTest {
            val own = MutableStateFlow(WatchState())
            val mirror = WatchStateMirror(flags, channel, capabilities, PeerStateStore(), StateMerger(FixedClock(1_000)), ownState = own)
            mirror.start(backgroundScope)
            runCurrent()
            own.value = WatchState(link = PeerLinkState.PAIRED)
            runCurrent()
            assertEquals(
                PeerLinkState.PAIRED,
                channel.published
                    .last()
                    .watch
                    ?.link,
            )
        }

    private fun phoneDocument(switchOn: Boolean) =
        StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState(flags = mapOf(FLAG_KEY to switchOn)))

    private class InMemoryFlags : FeatureFlagStore {
        val value = MutableStateFlow(false)
        val writes = mutableListOf<Boolean>()

        override fun observe(flag: FeatureFlag): Flow<Boolean> = value

        override suspend fun setEnabled(
            flag: FeatureFlag,
            enabled: Boolean,
        ) {
            writes += enabled
            value.value = enabled
        }
    }

    private class FakeChannel : StateDocumentChannel {
        val published = mutableListOf<StateDocument>()
        var peers = emptyList<PeerDocument>()

        override suspend fun publish(document: StateDocument): Boolean {
            published += document
            return true
        }

        override suspend fun peerDocuments(): List<PeerDocument> = peers
    }

    private class FakeCapabilities : LocalCapabilities {
        val calls = mutableListOf<String>()
        var succeeds = true

        override suspend fun add(capability: String): Boolean {
            calls += "add $capability"
            return succeeds
        }

        override suspend fun remove(capability: String): Boolean {
            calls += "remove $capability"
            return succeeds
        }
    }

    private class FixedClock(
        private val millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val FLAG_KEY = "watch_experience_v2_enabled"
        const val PHONE_NODE = "phone-node"
        const val WATCH_NODE = "watch-node"
        const val WATCH_V2 = "logdate_watch_sync_v2"
    }
}
