package app.logdate.client.sync.v2.link

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import app.logdate.client.sync.v2.MemoryPairKeyVault
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneWatchLinkStoreTest {
    private val store = PhoneWatchLinkStore(MemoryKeyValueStorage(), MemoryPairKeyVault())
    private val target = LinkTarget("watch", "Watch")
    private val pair = LinkPlan(LinkMode.PAIR)
    private val cloud = LinkPlan(LinkMode.CLOUD, "new-account")

    @Test
    fun `portable forget choices survive while link metadata stays local`() =
        runTest {
            val local = MemoryKeyValueStorage()
            val portable = MemoryKeyValueStorage()
            val keys = MemoryPairKeyVault()
            val links = PhoneWatchLinkStore(local, keys, portable)
            links.recordSuccess(target, pair, ByteArray(32), 100, 100, linkId = "destination")
            portable.putString("phone.watch_links.v1", "source metadata")
            assertEquals("destination", links.get(target.nodeId)?.linkId)
            links.forget(target.nodeId)
            assertTrue(portable.getBooleanOrThrow("watch.removed.${target.nodeId}"))
            assertFalse(local.getBooleanOrThrow("watch.removed.${target.nodeId}"))
            assertNull(keys.get(target.nodeId))
            assertNull(links.get(target.nodeId))
        }

    @Test
    fun `a fresh exchange replaces link identity and a failed changed plan cannot retain it`() =
        runTest {
            store.recordSuccess(target, pair, ByteArray(32), 100, 100, linkId = "first")
            assertEquals("first", store.get(target.nodeId)?.linkId)
            store.recordSuccess(target, pair, ByteArray(32), 200, 200, linkId = "second")
            assertEquals("second", store.get(target.nodeId)?.linkId)
            store.recordFailure(target, cloud, PhoneLinkFailure.SIGNED_OUT, 300, 300)
            assertNull(store.get(target.nodeId)?.linkId)
        }

    @Test
    fun `a fresh exchange requires a new cloud installation confirmation`() =
        runTest {
            store.recordSuccess(target, cloud, ByteArray(32), 100, 100, linkId = "first")
            store.markConfirmed(target.nodeId, 100, 100, expectedLinkId = store.get(target.nodeId)?.linkId)
            val renewed = assertNotNull(store.recordSuccess(target, cloud, ByteArray(32), 200, 200, linkId = "second"))
            assertNull(renewed.confirmedAt)
        }

    @Test
    fun `another server for the same account starts its own confirmation and retry history`() =
        runTest {
            val first = cloud.copy(serverOrigin = "https://first.test")
            val second = cloud.copy(serverOrigin = "https://second.test")
            store.recordSuccess(target, first, ByteArray(32), 100, 100)
            store.markConfirmed(target.nodeId, 100, 100, expectedLinkId = store.get(target.nodeId)?.linkId)
            val changed = assertNotNull(store.recordFailure(target, second, PhoneLinkFailure.WATCH_UNREACHABLE, 200, 200))
            assertNull(changed.confirmedAt)
            assertNull(changed.linkedAt)
            assertEquals(second.serverOrigin, changed.serverOrigin)
            val linked = assertNotNull(store.recordSuccess(target, second, ByteArray(32), 300, 300))
            assertEquals(second.serverOrigin, linked.serverOrigin)
        }

    @Test
    fun `a failed new plan cannot inherit confirmation from the previous plan`() =
        runTest {
            store.recordSuccess(target, pair, ByteArray(32), 100, 100)
            store.markConfirmed(target.nodeId, 100, 100, expectedLinkId = store.get(target.nodeId)?.linkId)
            val failure = assertNotNull(store.recordFailure(target, cloud, PhoneLinkFailure.WATCH_UNREACHABLE, 200, 200))
            assertNull(failure.confirmedAt)
            assertNull(failure.linkedAt)
            val linked = assertNotNull(store.recordSuccess(target, cloud, ByteArray(32), 300, 300))
            assertNull(linked.confirmedAt)
        }

    @Test
    fun `an older confirmation cannot confirm a replacement link`() =
        runTest {
            store.recordSuccess(target, cloud, ByteArray(32), 100, 100, linkId = "first")
            store.recordSuccess(target, cloud, ByteArray(32), 200, 200, linkId = "second")

            assertFalse(store.markConfirmed(target.nodeId, 300, 300, expectedLinkId = "first"))
            assertNull(store.get(target.nodeId)?.confirmedAt)
        }

    @Test
    fun `secure storage read failure never discards the recorded link`() =
        runTest {
            val failingKeys =
                object : app.logdate.client.sync.peer.link.PairKeyVault by MemoryPairKeyVault() {
                    override suspend fun get(peer: String): ByteArray? = throw java.io.IOException("Temporarily unavailable")
                }
            val links = PhoneWatchLinkStore(MemoryKeyValueStorage(), failingKeys)
            links.recordSuccess(target, pair, ByteArray(32), 100, 100, linkId = "first")
            assertFailsWith<java.io.IOException> { links.getForReconciliation(target.nodeId) }
            assertEquals("first", links.get(target.nodeId)?.linkId)
        }

    @Test
    fun `interrupted success cannot leave an old successful link with a replacement pair key`() =
        runTest {
            val preferences = MemoryKeyValueStorage()
            val keys = MemoryPairKeyVault()
            val failingPreferences =
                object : app.logdate.client.datastore.KeyValueStorage by preferences {
                    override suspend fun putStringOrThrow(
                        key: String,
                        value: String,
                    ) {
                        if (value.contains("\"linkId\":\"second\"")) throw java.io.IOException("write interrupted")
                        preferences.putStringOrThrow(key, value)
                    }
                }
            val links = PhoneWatchLinkStore(failingPreferences, keys)
            links.recordSuccess(target, pair, ByteArray(32) { 1 }, 100, 100, linkId = "first")
            assertFailsWith<java.io.IOException> {
                links.recordSuccess(target, pair, ByteArray(32) { 2 }, 200, 200, linkId = "second")
            }
            val pending = assertNotNull(links.get(target.nodeId))
            assertEquals(PhoneLinkFailure.KEYS_UNAVAILABLE, pending.failure)
            assertNull(pending.linkedAt)
            assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(pending, pair, null, pending.nextAttemptAt))
        }

    @Test
    fun `a new plan starts its own retry wait`() =
        runTest {
            store.recordFailure(target, pair, PhoneLinkFailure.WATCH_UNREACHABLE, 100, 100)
            val failure = assertNotNull(store.recordFailure(target, cloud, PhoneLinkFailure.WATCH_UNREACHABLE, 200, 200))
            assertEquals(1, failure.attempts)
            assertEquals(60_200L, failure.nextAttemptAt)
        }
}
