package app.logdate.client.sync.v2.link

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.device.restore.InstallationWatchMigration
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.MemoryPairKeyVault
import app.logdate.client.sync.v2.window.WindowManifestStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallationWatchMigrationBoundaryTest {
    @Test
    fun `upgrading restored legacy trust renews real exchanges without accepting a mismatched key`() =
        runTest {
            for (replayed in listOf(true, false)) {
                val clock = MutableClock(1_000_000)
                val handshake = BouncyCastleLinkHandshake()
                val watch = FakeWatch(handshake, clock)
                val keys = MemoryPairKeyVault()
                val portable = MemoryKeyValueStorage()
                val old = PhoneWatchLinkStore(portable, keys)

                fun initiator(store: PhoneWatchLinkStore) =
                    PhoneLinkInitiator(
                        v2Enabled = flowOf(true),
                        watchCapable = flowOf(true),
                        watchDocuments = MutableStateFlow(emptyMap()),
                        targets = { listOf(LinkTarget("watch", "Watch")) },
                        requester = watch,
                        exchange = PhoneLinkExchange(handshake, clock),
                        store = store,
                        notifier = RecordingNotifier(),
                        localNodeId = { "phone" },
                        clock = clock,
                    )
                initiator(old).reconcile(emptyMap())
                val sourceLink = assertNotNull(old.get("watch")?.linkId)
                val originalMetadata = portable.getString("phone.watch_links.v1")!!
                val oldPeer = PeerDeliveryContext(sourceLink, "phone", "watch", LinkMode.PAIR)
                repeat(3) { WindowManifestStore(portable).reserveRevision(oldPeer) }
                if (replayed) {
                    keys.remove("watch")
                    initiator(old).reconcile(emptyMap())
                    assertNotEquals(sourceLink, old.get("watch")?.linkId)
                    portable.putString("phone.watch_links.v1", originalMetadata)
                }
                val existingKey = assertNotNull(keys.get("watch"))
                val legacy =
                    emptyPreferences().toMutablePreferences().apply {
                        portable.values.forEach { (key, value) -> this[stringPreferencesKey(key)] = value }
                    }
                val migrated = InstallationWatchMigration({ legacy }, keys::get, canAdoptLegacy = { false }).migrate(emptyPreferences())
                val local =
                    MemoryKeyValueStorage().apply {
                        migrated.asMap().forEach { (key, value) -> if (value is String) putString(key.name, value) }
                    }
                val replacement = PhoneWatchLinkStore(local, keys, portable)
                assertNull(replacement.getForReconciliation("watch"))
                assertEquals(0L, WindowManifestStore(local).revision(oldPeer))
                assertEquals(originalMetadata, portable.getString("phone.watch_links.v1"))
                watch.reachable = false
                val recovery = initiator(replacement)
                recovery.reconcile(emptyMap())
                assertTrue(existingKey.contentEquals(keys.get("watch")), "A failed renewal retains the local key")
                assertEquals(PhoneLinkFailure.WATCH_UNREACHABLE, replacement.get("watch")?.failure)
                clock.millis += 60_000
                watch.reachable = true
                recovery.reconcile(emptyMap())
                val repaired = assertNotNull(replacement.get("watch"))
                assertNotEquals(sourceLink, repaired.linkId)
                assertTrue(assertNotNull(watch.pairKey).contentEquals(keys.get("watch")), "Renewal must verify the watch exchange")
                assertTrue(!existingKey.contentEquals(keys.get("watch")))
                val newPeer = oldPeer.copy(linkId = assertNotNull(repaired.linkId))
                assertEquals(1L, WindowManifestStore(local).reserveRevision(newPeer))
                val before = watch.requests.size
                initiator(PhoneWatchLinkStore(local, keys, portable)).reconcile(emptyMap())
                assertEquals(before, watch.requests.size)
                assertEquals(repaired, replacement.get("watch"))
                assertEquals(1L, WindowManifestStore(local).revision(newPeer))
            }
        }

    @Test
    fun `one malformed legacy record cannot hide a valid retained link`() =
        runTest {
            val fields = listOf("displayName", "confirmedAt", "attempts", "attentionShown", "lastFailure")
            val invalidFields =
                fields.map { it to JsonArray(listOf(JsonObject(emptyMap()))) } +
                    listOf("lastAttemptAt", "nextAttemptAt", "seenWatchDocAt").map { it to JsonNull }
            for ((field, invalid) in invalidFields) {
                val good =
                    Json
                        .parseToJsonElement(
                            """{"nodeId":"watch","displayName":"Watch","mode":"PAIR","linkedAt":100,""" +
                                """"linkId":"CQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQk="}""",
                        ).jsonObject
                val bad = JsonObject(good + mapOf("nodeId" to JsonPrimitive("broken"), field to invalid))
                val key = stringPreferencesKey("phone.watch_links.v1")
                val legacy =
                    emptyPreferences().toMutablePreferences().apply {
                        this[key] =
                            JsonObject(mapOf("watch" to good, "broken" to bad)).toString()
                    }
                val keys =
                    MemoryPairKeyVault().apply {
                        put("watch", ByteArray(32))
                        put("broken", ByteArray(32))
                    }
                val migrated = InstallationWatchMigration({ legacy }, keys::get).migrate(emptyPreferences())
                val local = MemoryKeyValueStorage().apply { putString(key.name, migrated[key]!!) }
                val links = PhoneWatchLinkStore(local, keys)
                assertEquals("Watch", links.get("watch")?.displayName, field)
                assertNull(links.get("broken"), field)
            }
        }
}
