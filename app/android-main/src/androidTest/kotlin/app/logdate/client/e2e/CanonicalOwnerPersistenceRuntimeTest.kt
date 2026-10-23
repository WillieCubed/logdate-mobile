package app.logdate.client.e2e

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Isolated files exercise real Android DataStore persistence without opening the journal database. */
@RunWith(AndroidJUnit4::class)
class CanonicalOwnerPersistenceRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun observing_an_unclaimed_installation_does_not_create_an_owner() =
        runBlocking {
            withPreferenceFile { file ->
                withStorage(file) { it.updateStringsOrThrow(mapOf("unrelated" to "preserved")) }
                val before = file.readBytes()
                withStorage(file) { storage ->
                    val provider = DefaultCanonicalOwnerProvider(storage)
                    assertNull(provider.readExistingOwner())
                    assertEquals(false, provider.hasBoundOwner())
                    assertEquals(mapOf(OWNER to null, BOUND to null), storage.getStringsOrThrow(setOf(OWNER, BOUND)))
                }
                assertContentEquals(before, file.readBytes(), "Observation must not rewrite durable preferences")
            }
        }

    @Test
    fun competing_providers_keep_one_owner_and_one_account_after_reopening() =
        runBlocking {
            withPreferenceFile { file ->
                var claimedOwner: String? = null
                withStorage(file) { storage ->
                    val owners =
                        coroutineScope {
                            List(32) { async(Dispatchers.IO) { DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId() } }.awaitAll()
                        }
                    assertEquals(1, owners.toSet().size, "Concurrent creation must not split the local journal identity")
                    val accounts = listOf("b930bf49-d1e9-4a65-b84b-8e74af45012b", "a930bf49-d1e9-4a65-b84b-8e74af45012b")
                    val adopted =
                        coroutineScope {
                            val claims =
                                accounts.map { account ->
                                    async(Dispatchers.IO) {
                                        DefaultCanonicalOwnerProvider(storage).adoptRemoteOwnerIfUninitialized(account)
                                    }
                                }
                            claims.awaitAll()
                        }
                    assertEquals(1, adopted.count { it }, "Two accounts must not both claim this installation")
                    claimedOwner = accounts[adopted.indexOf(true)]
                    assertEquals(mapOf(OWNER to claimedOwner, BOUND to "true"), storage.getStringsOrThrow(setOf(OWNER, BOUND)))
                }
                withStorage(file) { storage ->
                    val provider = DefaultCanonicalOwnerProvider(storage)
                    val existing = assertNotNull(provider.readExistingOwner())
                    assertTrue(existing.isBound)
                    assertEquals(claimedOwner, existing.rawId)
                    assertEquals(claimedOwner, provider.getCanonicalOwnerId())
                }
            }
        }

    @Test
    fun incomplete_retained_binding_is_preserved_instead_of_replaced() =
        runBlocking {
            withPreferenceFile { file ->
                withStorage(file) { it.updateStringsOrThrow(mapOf(BOUND to "true", "unrelated" to "preserved")) }
                val before = file.readBytes()
                withStorage(file) { storage ->
                    val provider = DefaultCanonicalOwnerProvider(storage)
                    assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> { provider.readExistingOwner() }
                    assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> { provider.getCanonicalOwnerId() }
                    assertEquals(mapOf(OWNER to null, BOUND to "true"), storage.getStringsOrThrow(setOf(OWNER, BOUND)))
                }
                assertContentEquals(before, file.readBytes(), "Refusal must preserve the retained identity for recovery")
            }
        }

    @Test
    fun same_account_capitalization_does_not_rewrite_a_restored_owner() =
        runBlocking {
            withPreferenceFile { file ->
                val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
                withStorage(file) { it.updateStringsOrThrow(mapOf(OWNER to raw, BOUND to "true")) }
                val before = file.readBytes()
                withStorage(file) { storage ->
                    val accepted =
                        coroutineScope {
                            List(32) { index ->
                                async(Dispatchers.IO) {
                                    val spelling = if (index % 2 == 0) raw else raw.lowercase()
                                    DefaultCanonicalOwnerProvider(storage).adoptRemoteOwnerIfUninitialized(spelling)
                                }
                            }.awaitAll()
                        }
                    assertTrue(accepted.all { it })
                    assertFalse(DefaultCanonicalOwnerProvider(storage).adoptRemoteOwnerIfUninitialized(Uuid.random().toString()))
                }
                assertContentEquals(before, file.readBytes(), "Same-account recognition must preserve the stored raw owner")
                withStorage(file) { storage ->
                    val observed = assertNotNull(DefaultCanonicalOwnerProvider(storage).readExistingOwner())
                    assertEquals(raw, observed.rawId)
                    assertTrue(observed.isBound)
                }
            }
        }

    private suspend fun withPreferenceFile(block: suspend (File) -> Unit) {
        val directory = File(context.cacheDir, "canonical-owner-${Uuid.random()}")
        check(directory.mkdirs())
        try {
            block(File(directory, "owner.preferences_pb"))
        } finally {
            check(directory.deleteRecursively())
        }
    }

    private suspend fun withStorage(
        file: File,
        block: suspend (DataStoreKeyValueStorage) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            block(DataStoreKeyValueStorage(store))
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }
    }

    private companion object {
        const val OWNER = "identity.canonical_owner_id"
        const val BOUND = "identity.canonical_owner_bound"
    }
}
