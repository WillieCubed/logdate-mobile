package app.logdate.client.sync.v2

import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.peer.state.LocalCapabilities
import app.logdate.client.sync.peer.state.PeerDocument
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentChannel
import app.logdate.client.sync.peer.state.WatchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [PhoneStatePublisher]: the phone always publishes its state, and advertises version 2 only
 * while the Labs switch is on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneStatePublisherTest {
    @Test
    fun `the phone lists the features it supports`() =
        runTest {
            val withFeatures =
                PhoneStatePublisher(
                    v2Enabled = enabled,
                    channel = channel,
                    capabilities = capabilities,
                    peerStates = peerStates,
                    clock = FixedClock(1_000),
                    timeZone = { TimeZone.of("Europe/Paris") },
                    features = setOf("link.cloud.v1"),
                )

            withFeatures.start(backgroundScope)
            runCurrent()

            assertEquals(
                setOf("link.cloud.v1"),
                channel.published
                    .last()
                    .phone
                    ?.features,
            )
        }

    @Test
    fun `signing in publishes the account presence without advertising again`() =
        runTest {
            val parts = MutableStateFlow(PhoneStateParts(cloud = CloudPresence.NO_ACCOUNT))
            val withParts =
                PhoneStatePublisher(
                    v2Enabled = enabled,
                    channel = channel,
                    capabilities = capabilities,
                    peerStates = peerStates,
                    clock = FixedClock(1_000),
                    timeZone = { TimeZone.of("Europe/Paris") },
                    parts = parts,
                )
            withParts.start(backgroundScope)
            runCurrent()

            parts.value = PhoneStateParts(cloud = CloudPresence.SIGNED_IN)
            runCurrent()

            assertEquals(
                CloudPresence.SIGNED_IN,
                channel.published
                    .last()
                    .phone
                    ?.cloud,
            )
            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }

    private val enabled = MutableStateFlow(false)
    private val preferences = MutableStateFlow<Map<String, Stamped>>(emptyMap())
    private val adopted = mutableListOf<Map<String, Stamped>>()
    private val channel = FakeChannel()
    private val capabilities = FakeCapabilities()
    private val peerStates = PeerStateStore()
    private val publisher =
        PhoneStatePublisher(
            v2Enabled = enabled,
            channel = channel,
            capabilities = capabilities,
            peerStates = peerStates,
            clock = FixedClock(1_000),
            timeZone = { TimeZone.of("Europe/Paris") },
            sharedPreferences = preferences,
            adoptWatchPreferences = { adopted += it },
        )

    @Test
    fun `the phone publishes its state with the switch off`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()

            assertEquals(
                listOf(
                    StateDocument(
                        role = PeerRole.PHONE,
                        writtenAt = 1_000,
                        phone = PhoneState(flags = mapOf(FLAG_KEY to false), zone = "Europe/Paris", features = setOf("prefs.v1")),
                    ),
                ),
                channel.published,
            )
            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }

    @Test
    fun `turning the switch on publishes it and advertises version 2`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()

            enabled.value = true
            runCurrent()

            assertEquals(
                true,
                channel.published
                    .last()
                    .phone
                    ?.flags
                    ?.get(FLAG_KEY),
            )
            assertEquals(listOf("remove $PHONE_V2", "add $PHONE_V2"), capabilities.calls)
        }

    @Test
    fun `starting reads the state the watches already published`() =
        runTest {
            val watch = StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState(features = setOf("receipts.v1")))
            channel.peers = listOf(PeerDocument("watch-node", Decoded.Supported(watch)))

            publisher.start(backgroundScope)
            runCurrent()

            assertEquals(setOf("receipts.v1"), peerStates.features(PeerRole.WATCH).first())
        }

    @Test
    fun `a failed publish still updates the capability`() =
        runTest {
            channel.succeeds = false

            publisher.start(backgroundScope)
            runCurrent()

            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }

    @Test
    fun `a preference change republishes the document without touching the capability`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()

            preferences.value = mapOf("window.days" to Stamped(JsonPrimitive(30), 900))
            runCurrent()

            assertEquals(2, channel.published.size)
            assertEquals(mapOf("window.days" to Stamped(JsonPrimitive(30), 900)), channel.published.last().shared)
            assertEquals(listOf("remove $PHONE_V2"), capabilities.calls)
        }

    @Test
    fun `preferences a watch wrote are handed over to be adopted`() =
        runTest {
            publisher.start(backgroundScope)
            runCurrent()
            val shared = mapOf("capture.places" to Stamped(JsonPrimitive(true), 950))

            peerStates.accept(
                "watch-node",
                Decoded.Supported(StateDocument(role = PeerRole.WATCH, writtenAt = 950, watch = WatchState(), shared = shared)),
                PeerRole.WATCH,
            )
            runCurrent()

            assertEquals(listOf(shared), adopted)
        }

    private class FakeChannel : StateDocumentChannel {
        val published = mutableListOf<StateDocument>()
        var peers = emptyList<PeerDocument>()
        var succeeds = true

        override suspend fun publish(document: StateDocument): Boolean {
            published += document
            return succeeds
        }

        override suspend fun peerDocuments(): List<PeerDocument> = peers
    }

    private class FakeCapabilities : LocalCapabilities {
        val calls = mutableListOf<String>()

        override suspend fun add(capability: String): Boolean {
            calls += "add $capability"
            return true
        }

        override suspend fun remove(capability: String): Boolean {
            calls += "remove $capability"
            return true
        }
    }

    private class FixedClock(
        private val millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val FLAG_KEY = "watch_experience_v2_enabled"
        const val PHONE_V2 = "logdate_phone_sync_v2"
    }
}
