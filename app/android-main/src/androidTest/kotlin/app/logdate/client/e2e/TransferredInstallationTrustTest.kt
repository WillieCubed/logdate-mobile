package app.logdate.client.e2e

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.LegacyCloudCredentialMigration
import app.logdate.client.device.crypto.link.BouncyCastleLinkHandshake
import app.logdate.client.device.storage.AndroidSecureStorage
import app.logdate.client.sync.datalayer.v2.LinkBundle
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LinkNonceLedger
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.peer.link.PhoneLinkExchange
import app.logdate.client.sync.peer.link.SecureStoragePairKeyVault
import app.logdate.client.sync.peer.link.WatchLinkExchange
import app.logdate.client.sync.peer.link.valueOrNull
import app.logdate.client.sync.v2.link.LinkPlan
import app.logdate.client.sync.v2.link.LinkTarget
import app.logdate.client.sync.v2.link.PhoneLinkDecision
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import app.logdate.client.sync.v2.window.WindowManifestStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Uses isolated preference files and uniquely named secure keys; never opens the journal database. */
@RunWith(AndroidJUnit4::class)
class TransferredInstallationTrustTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val oldLinkId = LinkWire.encodeKey(ByteArray(32) { 9 })

    @Test
    fun backup_first_read_removes_legacy_credentials_for_every_server() =
        runBlocking {
            val folder = File(context.cacheDir, "trust-migration-${Uuid.random()}").apply { mkdirs() }
            val file = File(folder, "preferences.preferences_pb")
            val keys =
                listOf(
                    "cloud_access_token",
                    "cloud_refresh_token",
                    "cloud_access_token_first",
                    "cloud_refresh_token_second",
                    "access_token",
                    "refresh_token",
                    "access_token_aHR0cHM6Ly9maXJzdA",
                    "refresh_token_c2Vjb25k",
                )
            val sourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val source = PreferenceDataStoreFactory.create(scope = sourceScope, produceFile = { file })
                source.edit { values ->
                    keys.forEach { values[stringPreferencesKey(it)] = "synthetic-token" }
                    values[stringPreferencesKey("identity.canonical_owner")] = "journal-owner"
                    values[stringPreferencesKey("cloud_account_did_first")] = "did:web:synthetic.invalid"
                }
                sourceScope.cancel()
                sourceScope.coroutineContext[kotlinx.coroutines.Job]?.join()
                val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                try {
                    val backup =
                        PreferenceDataStoreFactory.create(
                            scope = backupScope,
                            migrations = listOf(LegacyCloudCredentialMigration()),
                            produceFile = { file },
                        )
                    val values = backup.data.first()
                    keys.forEach { assertNull(values[stringPreferencesKey(it)], it) }
                    assertEquals("journal-owner", values[stringPreferencesKey("identity.canonical_owner")])
                    assertEquals("did:web:synthetic.invalid", values[stringPreferencesKey("cloud_account_did_first")])
                    assertTrue(file.isFile)
                } finally {
                    backupScope.cancel()
                    backupScope.coroutineContext[kotlinx.coroutines.Job]?.join()
                }
            } finally {
                sourceScope.cancel()
                sourceScope.coroutineContext[kotlinx.coroutines.Job]?.join()
                folder.deleteRecursively()
            }
        }

    @Test
    fun copied_watch_record_requires_new_local_trust_and_preserves_forget_choice() =
        runBlocking {
            val folder = File(context.cacheDir, "trust-peer-${Uuid.random()}").apply { mkdirs() }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val secure = AndroidSecureStorage(context)
            val peer = "fixture-${Uuid.random()}"
            val sourceKeys = SecureStoragePairKeyVault(secure, "source-$peer-")
            val destinationKeys = SecureStoragePairKeyVault(secure, "destination-$peer-")
            try {
                val preferences =
                    PreferenceDataStoreFactory.create(
                        scope = scope,
                        produceFile = { File(folder, "preferences.preferences_pb") },
                    )
                val storage = DataStoreKeyValueStorage(preferences)
                val plan = LinkPlan(LinkMode.PAIR)
                val target = LinkTarget(peer, "Fixture watch")
                PhoneWatchLinkStore(storage, sourceKeys).recordSuccess(target, plan, ByteArray(32) { 3 }, 1, 1, linkId = oldLinkId)
                assertNotNull(sourceKeys.get(peer))
                assertNull(destinationKeys.get(peer))

                val oldPeer = PeerDeliveryContext(oldLinkId, "source-phone", peer, LinkMode.PAIR)
                repeat(3) { WindowManifestStore(storage).reserveRevision(oldPeer) }
                val destination = PhoneWatchLinkStore(storage, destinationKeys)
                assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(destination.getForReconciliation(peer), plan, null, 2))
                verifyFreshTrust(storage, target, plan, destination, destinationKeys)
                assertContentEquals(ByteArray(32) { 3 }, sourceKeys.get(peer), "Replacement linking must not change source trust")
                destination.forget(peer)
                assertTrue(PhoneWatchLinkStore(storage, destinationKeys).removalSuppressed(peer))
                assertNull(destination.recordSuccess(target, plan, ByteArray(32), 3, 3, linkId = "late-link"))
                assertNull(destinationKeys.get(peer))
            } finally {
                sourceKeys.remove(peer)
                destinationKeys.remove(peer)
                scope.cancel()
                scope.coroutineContext[kotlinx.coroutines.Job]?.join()
                folder.deleteRecursively()
            }
        }

    private suspend fun verifyFreshTrust(
        storage: KeyValueStorage,
        target: LinkTarget,
        plan: LinkPlan,
        destination: PhoneWatchLinkStore,
        destinationKeys: SecureStoragePairKeyVault,
    ) {
        val oldPeer = PeerDeliveryContext(oldLinkId, "source-phone", target.nodeId, LinkMode.PAIR)
        val manifests = WindowManifestStore(storage)
        val handshake = BouncyCastleLinkHandshake()
        val phone = PhoneLinkExchange(handshake)
        val watch = WatchLinkExchange(handshake, LinkNonceLedger())
        val started = phone.start(LinkMode.PAIR, "replacement-phone")
        val accepted = assertNotNull(watch.accept(started.offer, target.nodeId, "replacement-phone").valueOrNull())
        val sealed = assertNotNull(phone.seal(started, accepted.reply, target.nodeId, LinkBundle(mode = LinkMode.PAIR)).valueOrNull())
        val opened = assertNotNull(watch.open(accepted, sealed.bundle).valueOrNull())
        assertTrue(phone.verify(sealed, opened.confirm))
        assertContentEquals(sealed.pairKey, opened.pairKey)
        assertFalse(sealed.pairKey.contentEquals(ByteArray(32) { 3 }), "The replacement must negotiate a fresh pair key")
        assertNotEquals(oldPeer.linkId, sealed.linkId)
        assertNotNull(destination.recordSuccess(target, plan, sealed.pairKey, 3, 3, linkId = sealed.linkId))
        assertTrue(destination.markConfirmed(target.nodeId, 4, 4, sealed.linkId))
        assertFalse(destination.markConfirmed(target.nodeId, 5, 5, sealed.linkId), "Repeated confirmation must be idempotent")
        val freshKeys = SecureStoragePairKeyVault(AndroidSecureStorage(context), "destination-${target.nodeId}-")
        val recreated = PhoneWatchLinkStore(storage, freshKeys)
        assertEquals(sealed.linkId, assertNotNull(recreated.getForReconciliation(target.nodeId)).linkId)
        assertContentEquals(sealed.pairKey, freshKeys.get(target.nodeId))
        assertContentEquals(sealed.pairKey, destinationKeys.get(target.nodeId))
        val newPeer = oldPeer.copy(linkId = sealed.linkId, phoneNode = "replacement-phone")
        assertTrue(newPeer.isValid())
        assertEquals(0L, manifests.revision(newPeer))
        assertEquals(1L, manifests.reserveRevision(newPeer))
        assertEquals(1L, WindowManifestStore(storage).revision(newPeer))
        assertEquals(3L, manifests.revision(oldPeer), "Copied counters remain scoped to the old exchange")
    }
}
