package app.logdate.client.e2e

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.LegacyCloudCredentialMigration
import app.logdate.client.device.storage.AndroidSecureStorage
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.peer.link.SecureStoragePairKeyVault
import app.logdate.client.sync.v2.link.LinkPlan
import app.logdate.client.sync.v2.link.LinkTarget
import app.logdate.client.sync.v2.link.PhoneLinkDecision
import app.logdate.client.sync.v2.link.PhoneWatchLinkStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Uses isolated preference files and uniquely named secure keys; never opens the journal database. */
@RunWith(AndroidJUnit4::class)
class TransferredInstallationTrustTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

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
                PhoneWatchLinkStore(storage, sourceKeys).recordSuccess(target, plan, ByteArray(32) { 3 }, 1, 1, linkId = "old-link")
                assertNotNull(sourceKeys.get(peer))
                assertNull(destinationKeys.get(peer))

                val destination = PhoneWatchLinkStore(storage, destinationKeys)
                assertEquals(PhoneLinkDecision.Exchange, PhoneLinkDecision.decide(destination.getForReconciliation(peer), plan, null, 2))
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
}
