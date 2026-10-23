package app.logdate.client.e2e

import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.device.identity.DefaultDeviceManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PairKeyVault
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.client.sync.peer.link.valueOrNull
import app.logdate.client.sync.v2.link.LinkPlan
import app.logdate.client.sync.v2.link.LinkTarget
import app.logdate.client.sync.v2.link.PhoneWatchLink
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import app.logdate.client.sync.v2.receipts.PhoneDeliveryPeers
import app.logdate.client.sync.v2.window.WindowManifestStore
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.koin.core.Koin
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic source trust crosses Android's real backup boundary with the journal fixture. */
internal class NativeInstallationTrustFixture(
    private val koin: Koin,
) {
    private val portable get() = koin.get<KeyValueStorage>()
    private val keys get() = koin.get<PairKeyVault>()
    private val links get() = koin.get<PhoneWatchLinkStore>()
    private val manifests get() = koin.get<WindowManifestStore>()
    private val device get() =
        koin
            .get<DeviceIdProvider>()
            .getDeviceId()
            .value
            .toString()
    private val oldLink = LinkWire.encodeKey(ByteArray(32) { 9 })
    private val oldKey get() = ByteArray(32) { 3 }
    private val receipt get() =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
            "native-installation-trust.json",
        )

    suspend fun seed() {
        assertNull(keys.get(PEER), "Trust preparation requires a fresh synthetic source")
        assertNull(links.get(PEER))
        assertNotNull(links.recordSuccess(LinkTarget(PEER, "Fixture watch"), LinkPlan(LinkMode.PAIR), oldKey, 1, 1, oldLink))
        assertTrue(links.markConfirmed(PEER, 2, 2, oldLink, SOURCE_SESSION))
        repeat(3) { manifests.reserveRevision(sourcePeer(device)) }
        // Retain the old portable format so native delivery exercises released backups too.
        portable.putString("phone.watch_links.v1", Json.encodeToString(mapOf(PEER to assertNotNull(links.get(PEER)))))
        repeat(3) { WindowManifestStore(portable).reserveRevision(sourcePeer(device)) }
        links.forget(FORGOTTEN_PEER)
        koin.get<DefaultDeviceManager>().updateNotificationToken(SOURCE_NOTIFICATION)
        assertEquals(SOURCE_NOTIFICATION, koin.get<DefaultDeviceManager>().getNotificationToken())
        verifySource(device)
    }

    suspend fun verifySource(sourceDevice: String) {
        assertEquals(sourceDevice, device)
        assertTrue(oldKey.contentEquals(keys.get(PEER)), "Backup must retain the source's own trusted-device key")
        val record = assertNotNull(links.getForReconciliation(PEER))
        assertEquals(oldLink, record.linkId)
        assertEquals(SOURCE_SESSION, record.sessionId)
        assertEquals(3L, manifests.revision(sourcePeer(sourceDevice)))
        assertTrue(links.removalSuppressed(FORGOTTEN_PEER))
    }

    suspend fun verifyDestination(sourceDevice: String) {
        assertNotEquals(sourceDevice, device)
        val copied = Json.decodeFromString<Map<String, PhoneWatchLink>>(assertNotNull(portable.getString("phone.watch_links.v1")))
        assertEquals(oldLink, copied[PEER]?.linkId, "Android must actually deliver the legacy source metadata")
        assertEquals(3L, WindowManifestStore(portable).revision(sourcePeer(sourceDevice)))
        assertNull(koin.get<DefaultDeviceManager>().getNotificationToken(), "Source push registration must not become destination state")
        assertEquals(0L, manifests.revision(sourcePeer(sourceDevice)), "A replacement must not inherit installation counters")
        assertTrue(links.removalSuppressed(FORGOTTEN_PEER), "Transfer must retain an explicit forget choice")
        val firstVerification = !receipt.exists()
        if (firstVerification) {
            assertNull(keys.get(PEER), "A fresh destination must not contain the source's pair key")
            val peers =
                PhoneDeliveryPeers(links, koin.get<SessionStorage>(), koin.get<LogDateConfigRepository>(), { device }, { true })
            assertNull(peers.current(PEER), "Copied link metadata must not authorize delivery on the replacement")
            assertNull(peers.forRemoval(PEER), "Copied link metadata must not authorize removal on the replacement")
            assertNull(links.getForReconciliation(PEER), "Copied link metadata must not authenticate the replacement installation")
            assertFalse(links.markConfirmed(PEER, 3, 3, oldLink, SOURCE_SESSION))
            createDestinationTrust()
        }
        val key = assertNotNull(keys.get(PEER))
        assertEquals(32, key.size)
        assertFalse(oldKey.contentEquals(key), "The replacement must negotiate a fresh trusted-device key")
        val record = assertNotNull(links.getForReconciliation(PEER))
        assertNotEquals(oldLink, record.linkId)
        assertEquals(destinationSession(), assertNotNull(record.sessionId))
        assertNotNull(record.confirmedAt)
        val destinationPeer = PeerDeliveryContext(assertNotNull(record.linkId), device, PEER, LinkMode.PAIR)
        if (firstVerification) {
            assertEquals(0L, manifests.revision(destinationPeer))
            assertEquals(1L, manifests.reserveRevision(destinationPeer), "The replacement exchange starts with its own counter")
        }
        assertEquals(1L, manifests.revision(destinationPeer), "Duplicate restore and restart must retain the new exchange's counter")
        assertEquals(0L, manifests.revision(sourcePeer(sourceDevice)))
        val observed =
            JSONObject()
                .put("sourceDevice", sourceDevice)
                .put("destinationDevice", device)
                .put("pairKeyFingerprint", IdentityKeyFingerprint.of(key, koin.get<CryptoManager>()))
                .put("linkId", record.linkId)
                .put("sessionId", record.sessionId)
                .put("revision", manifests.revision(destinationPeer))
        if (firstVerification) {
            val pending = File(receipt.parentFile, "${receipt.name}.part")
            FileOutputStream(pending).use { output ->
                output.write(observed.toString().encodeToByteArray())
                output.fd.sync()
            }
            Files.move(pending.toPath(), receipt.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        val expected = JSONObject(receipt.readText())
        assertEquals(observed.keys().asSequence().toSet(), expected.keys().asSequence().toSet())
        val revision = expected.get("revision")
        assertTrue(revision is Int || revision is Long, "Retained destination revision must be an integer")
        assertEquals(observed.getLong("revision"), (revision as Number).toLong())
        observed.keys().asSequence().filter { it != "revision" }.forEach { field ->
            assertEquals(expected.get(field), observed.get(field), "Retained destination trust changed: $field")
        }
    }

    private suspend fun createDestinationTrust() {
        val handshake = BouncyCastleLinkHandshake()
        val phone = PhoneLinkExchange(handshake)
        val watch = WatchLinkExchange(handshake, LinkNonceLedger())
        val started = phone.start(LinkMode.PAIR, device)
        val accepted = assertNotNull(watch.accept(started.offer, PEER, device).valueOrNull())
        val sealed = assertNotNull(phone.seal(started, accepted.reply, PEER, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
        val opened = assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull())
        assertTrue(phone.verify(sealed, opened.confirm))
        assertTrue(sealed.pairKey.contentEquals(opened.pairKey), "Both endpoints must derive the same fresh pair key")
        assertNotNull(links.recordSuccess(LinkTarget(PEER, "Fixture watch"), LinkPlan(LinkMode.PAIR), sealed.pairKey, 3, 3, sealed.linkId))
        assertTrue(links.markConfirmed(PEER, 4, 4, sealed.linkId, destinationSession()))
    }

    private fun destinationSession() = "synthetic-destination-watch-session-$device"

    private fun sourcePeer(sourceDevice: String) = PeerDeliveryContext(oldLink, sourceDevice, PEER, LinkMode.PAIR)

    private companion object {
        const val PEER = "native-transfer-fixture-watch"
        const val FORGOTTEN_PEER = "native-transfer-forgotten-watch"
        const val SOURCE_SESSION = "synthetic-source-watch-session"
        const val SOURCE_NOTIFICATION = "synthetic-source-push-registration"
    }
}
