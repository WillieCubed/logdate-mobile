package app.logdate.client.device.identity

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class CanonicalOwnerProviderTest {
    @Test
    fun `canonical owner survives a device identifier reset`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val provider = DefaultCanonicalOwnerProvider(storage)

            val initialOwner = provider.getCanonicalOwnerId()
            storage.putString("device.id", "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e")

            val ownerAfterDeviceReset = DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId()

            assertEquals(initialOwner, ownerAfterDeviceReset)
            assertNotEquals("a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e", ownerAfterDeviceReset)
        }

    @Test
    fun `concurrent initial reads return one persisted owner`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val provider = DefaultCanonicalOwnerProvider(storage)

            val owners =
                coroutineScope {
                    List(20) { async { provider.getCanonicalOwnerId() } }.awaitAll()
                }

            val uniqueOwner = owners.toSet().single()
            assertEquals(uniqueOwner, storage.getString("identity.canonical_owner_id"))
        }

    @Test
    fun `invalid persisted owner fails without replacing the identity`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            storage.putString("identity.canonical_owner_id", "corrupt")

            assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> {
                DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId()
            }

            assertEquals("corrupt", storage.getString("identity.canonical_owner_id"))
        }

    @Test
    fun `owner creation fails when persistence cannot be verified`() =
        runTest {
            val storage = CanonicalOwnerTestStorage(dropWrites = true)

            assertFailsWith<IllegalStateException> {
                DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId()
            }

            assertEquals(null, storage.getString("identity.canonical_owner_id"))
        }

    @Test
    fun `fresh installation adopts the remote owner`() =
        runTest {
            val remoteOwner = "b930bf49-d1e9-4a65-b84b-8e74af45012b"
            val freshStorage = CanonicalOwnerTestStorage()

            val adopted = DefaultCanonicalOwnerProvider(freshStorage).adoptRemoteOwnerIfUninitialized(remoteOwner)

            assertEquals(true, adopted)
            assertEquals(remoteOwner, DefaultCanonicalOwnerProvider(freshStorage).getCanonicalOwnerId())
        }

    /**
     * A device used before signing in has already minted itself a local owner ID. Signing in has to
     * be able to replace it, or that device could never join the account it belongs to -- an ID it
     * generated on its own can never match the one the Cloud issued.
     */
    @Test
    fun `signing in replaces an owner the device generated for itself`() =
        runTest {
            val remoteOwner = "b930bf49-d1e9-4a65-b84b-8e74af45012b"
            val selfGeneratedOwner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            val storage =
                CanonicalOwnerTestStorage().apply {
                    putString("identity.canonical_owner_id", selfGeneratedOwner)
                }

            val adopted = DefaultCanonicalOwnerProvider(storage).adoptRemoteOwnerIfUninitialized(remoteOwner)

            assertEquals(true, adopted)
            assertEquals(remoteOwner, DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId())
        }

    /**
     * Once an account has claimed the installation the binding is final. Adopting a different owner
     * would fold two people's journals together on one device.
     */
    @Test
    fun `an owner an account has claimed is never replaced by a different one`() =
        runTest {
            val boundOwner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            val otherOwner = "b930bf49-d1e9-4a65-b84b-8e74af45012b"
            val storage =
                CanonicalOwnerTestStorage().apply {
                    putString("identity.canonical_owner_id", boundOwner)
                    putString("identity.canonical_owner_bound", "true")
                }

            val adopted = DefaultCanonicalOwnerProvider(storage).adoptRemoteOwnerIfUninitialized(otherOwner)

            assertEquals(false, adopted)
            assertEquals(boundOwner, DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId())
        }

    @Test
    fun `signing back in to the same account is accepted`() =
        runTest {
            val boundOwner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            val storage =
                CanonicalOwnerTestStorage().apply {
                    putString("identity.canonical_owner_id", boundOwner)
                    putString("identity.canonical_owner_bound", "true")
                }

            val adopted = DefaultCanonicalOwnerProvider(storage).adoptRemoteOwnerIfUninitialized(boundOwner)

            assertEquals(true, adopted)
        }

    @Test
    fun `adoption marks the installation as claimed`() =
        runTest {
            val remoteOwner = "b930bf49-d1e9-4a65-b84b-8e74af45012b"
            val storage = CanonicalOwnerTestStorage()
            val provider = DefaultCanonicalOwnerProvider(storage)

            assertEquals(false, provider.hasBoundOwner())
            provider.adoptRemoteOwnerIfUninitialized(remoteOwner)

            assertEquals(true, DefaultCanonicalOwnerProvider(storage).hasBoundOwner())
        }

    @Test
    fun `a device wipe forgets the owner and its account binding`() =
        runTest {
            val provider = DefaultCanonicalOwnerProvider(CanonicalOwnerTestStorage())
            val accountOwner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            provider.adoptRemoteOwnerIfUninitialized(accountOwner)

            provider.resetForDeviceWipe()

            assertFalse(provider.hasBoundOwner())
            assertNotEquals(accountOwner, provider.getCanonicalOwnerId())
        }

    @Test
    fun `existing owner read on empty installation does not create an identity`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val observed = DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage)).readExistingOwner()
            assertEquals(null, observed)
            assertEquals(null, storage.getString("identity.canonical_owner_id"))
            assertEquals(null, storage.getString("identity.canonical_owner_bound"))
        }

    @Test
    fun `existing local owner preserves exact spelling without claiming a Cloud account`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val owner = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
            storage.putString("identity.canonical_owner_id", owner)
            assertEquals(
                ExistingCanonicalOwner(owner, false),
                DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage)).readExistingOwner(),
            )
            assertEquals(null, storage.getString("identity.canonical_owner_bound"))
        }

    @Test
    fun `existing bound owner is read without adoption or persistence`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val owner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            storage.putString("identity.canonical_owner_id", owner)
            storage.putString("identity.canonical_owner_bound", "true")
            assertEquals(
                ExistingCanonicalOwner(owner, true),
                DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage)).readExistingOwner(),
            )
        }

    @Test
    fun `bound marker with missing owner is corruption and cannot mint during observation`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            storage.putString("identity.canonical_owner_bound", "true")
            assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> {
                DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage)).readExistingOwner()
            }
            assertEquals(null, storage.getString("identity.canonical_owner_id"))
            assertEquals("true", storage.getString("identity.canonical_owner_bound"))
        }

    @Test
    fun `malformed existing owner is refused without replacing its bytes`() =
        runTest {
            for (raw in listOf("", " ", "invalid")) {
                val storage = CanonicalOwnerTestStorage()
                storage.putString("identity.canonical_owner_id", raw)
                assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> {
                    DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage)).readExistingOwner()
                }
                assertEquals(raw, storage.getString("identity.canonical_owner_id"))
            }
        }

    @Test
    fun `unknown binding marker cannot downgrade a retained owner to local`() =
        runTest {
            for (raw in listOf("", "false", "unknown")) {
                val storage = CanonicalOwnerTestStorage()
                storage.putString("identity.canonical_owner_id", "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e")
                storage.putString("identity.canonical_owner_bound", raw)
                assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> {
                    DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage)).readExistingOwner()
                }
                assertEquals(raw, storage.getString("identity.canonical_owner_bound"))
            }
        }

    @Test
    fun `unreadable owner or binding cannot become absent or local ownership`() =
        runTest {
            for (failedKey in listOf("identity.canonical_owner_id", "identity.canonical_owner_bound")) {
                val storage = CanonicalOwnerTestStorage()
                storage.putString("identity.canonical_owner_id", "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e")
                storage.putString("identity.canonical_owner_bound", "true")
                val failing =
                    object : KeyValueStorage by readOnlyCanonicalOwnerStorage(storage) {
                        override suspend fun getString(key: String): String? = if (key == failedKey) null else storage.getString(key)

                        override suspend fun getStringsOrThrow(keys: Set<String>): Map<String, String?> {
                            if (failedKey in keys) error("Synthetic durable read failure")
                            return keys.associateWith { storage.getString(it) }
                        }

                        override suspend fun getStringOrThrow(key: String): String? {
                            if (key == failedKey) throw IllegalStateException("Synthetic durable read failure")
                            return storage.getString(key)
                        }
                    }
                assertFailsWith<IllegalStateException> { DefaultCanonicalOwnerProvider(failing).readExistingOwner() }
                assertEquals("true", storage.getString("identity.canonical_owner_bound"))
                assertEquals("a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e", storage.getString("identity.canonical_owner_id"))
            }
        }

    @Test
    fun `normal owner access cannot replace identity after a hidden read failure`() =
        runTest {
            for ((operation, failedKey) in listOf(
                "read" to "identity.canonical_owner_id",
                "bound" to "identity.canonical_owner_bound",
                "adopt" to "identity.canonical_owner_id",
            )) {
                val storage = CanonicalOwnerTestStorage()
                storage.putString("identity.canonical_owner_id", "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e")
                storage.putString("identity.canonical_owner_bound", "true")
                var writes = 0
                val failing =
                    object : KeyValueStorage by storage {
                        override suspend fun getString(key: String): String? = if (key == failedKey) null else storage.getString(key)

                        override suspend fun getStringsOrThrow(keys: Set<String>): Map<String, String?> {
                            if (failedKey in keys) error("Synthetic durable read failure")
                            return keys.associateWith { storage.getString(it) }
                        }

                        override suspend fun getStringOrThrow(key: String): String? {
                            if (key == failedKey) error("Synthetic durable read failure")
                            return storage.getString(key)
                        }

                        override suspend fun putString(
                            key: String,
                            value: String,
                        ) {
                            writes++
                            error("Unexpected identity write")
                        }
                    }
                val provider = DefaultCanonicalOwnerProvider(failing)
                assertFailsWith<IllegalStateException> {
                    when (operation) {
                        "read" -> provider.getCanonicalOwnerId()
                        "bound" -> provider.hasBoundOwner()
                        else -> provider.adoptRemoteOwnerIfUninitialized("b930bf49-d1e9-4a65-b84b-8e74af45012b")
                    }
                }
                assertEquals(0, writes)
                assertEquals("a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e", storage.getString("identity.canonical_owner_id"))
            }
        }

    @Test
    fun `normal accessor cannot mint a replacement for a bound missing owner`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            storage.putString("identity.canonical_owner_bound", "true")
            assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> {
                DefaultCanonicalOwnerProvider(readOnlyCanonicalOwnerStorage(storage)).getCanonicalOwnerId()
            }
            assertEquals(null, storage.getString("identity.canonical_owner_id"))
        }

    @Test
    fun `binding write failure is not acknowledged as successful owner adoption`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val failing =
                object : KeyValueStorage by storage {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (key != "identity.canonical_owner_bound") storage.putString(key, value)
                    }

                    override suspend fun updateStringsOrThrow(values: Map<String, String?>) {
                        error("Synthetic atomic binding write failure")
                    }

                    override suspend fun compareAndSetStringsOrThrow(
                        expected: Map<String, String?>,
                        values: Map<String, String?>,
                    ): Boolean {
                        error("Synthetic atomic binding write failure")
                    }

                    override suspend fun putStringOrThrow(
                        key: String,
                        value: String,
                    ) {
                        if (key == "identity.canonical_owner_bound") error("Synthetic durable binding write failure")
                        storage.putString(key, value)
                    }
                }
            assertFailsWith<IllegalStateException> {
                DefaultCanonicalOwnerProvider(failing).adoptRemoteOwnerIfUninitialized("b930bf49-d1e9-4a65-b84b-8e74af45012b")
            }
            assertEquals(null, storage.getString("identity.canonical_owner_bound"))
            assertEquals(null, storage.getString("identity.canonical_owner_id"))
        }

    @Test
    fun `failed owner removal cannot acknowledge a successful device wipe`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val owner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            storage.putString("identity.canonical_owner_id", owner)
            storage.putString("identity.canonical_owner_bound", "true")
            val failing =
                object : KeyValueStorage by storage {
                    override suspend fun remove(key: String) = Unit

                    override suspend fun updateStringsOrThrow(values: Map<String, String?>) = Unit

                    override suspend fun compareAndSetStringsOrThrow(
                        expected: Map<String, String?>,
                        values: Map<String, String?>,
                    ) = true
                }
            assertFailsWith<IllegalStateException> { DefaultCanonicalOwnerProvider(failing).resetForDeviceWipe() }
            assertEquals(owner, storage.getString("identity.canonical_owner_id"))
            assertEquals("true", storage.getString("identity.canonical_owner_bound"))
        }

    @Test
    fun `asymmetric wipe failure cannot downgrade a bound owner after recreation`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val owner = "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"
            storage.putString("identity.canonical_owner_id", owner)
            storage.putString("identity.canonical_owner_bound", "true")
            val failing =
                object : KeyValueStorage by storage {
                    override suspend fun remove(key: String) {
                        if (key != "identity.canonical_owner_id") storage.remove(key)
                    }

                    override suspend fun updateStringsOrThrow(values: Map<String, String?>) {
                        error("Synthetic atomic removal failure")
                    }

                    override suspend fun compareAndSetStringsOrThrow(
                        expected: Map<String, String?>,
                        values: Map<String, String?>,
                    ): Boolean {
                        error("Synthetic atomic removal failure")
                    }
                }
            assertFailsWith<IllegalStateException> { DefaultCanonicalOwnerProvider(failing).resetForDeviceWipe() }
            assertEquals("true", storage.getString("identity.canonical_owner_bound"))
            val reopened = DefaultCanonicalOwnerProvider(storage)
            assertEquals(false, reopened.adoptRemoteOwnerIfUninitialized("b930bf49-d1e9-4a65-b84b-8e74af45012b"))
            assertEquals(owner, reopened.getCanonicalOwnerId())
        }

    @Test
    fun `separate provider instances cannot mint two owners from the same empty snapshot`() =
        runTest {
            val storage = CanonicalOwnerTestStorage()
            val gate = CompletableDeferred<Unit>()
            var emptyReads = 0

            suspend fun rendezvous(owner: String?) {
                if (owner == null && emptyReads < 2) {
                    emptyReads++
                    if (emptyReads == 2) gate.complete(Unit)
                    gate.await()
                }
            }
            val shared =
                object : KeyValueStorage by storage {
                    override suspend fun getString(key: String): String? {
                        val value = storage.getString(key)
                        if (key == "identity.canonical_owner_id") rendezvous(value)
                        return value
                    }

                    override suspend fun getStringOrThrow(key: String): String? = getString(key)

                    override suspend fun getStringsOrThrow(keys: Set<String>): Map<String, String?> {
                        val value = storage.getStringsOrThrow(keys)
                        rendezvous(value["identity.canonical_owner_id"])
                        return value
                    }
                }
            val owners = coroutineScope { List(2) { async { DefaultCanonicalOwnerProvider(shared).getCanonicalOwnerId() } }.awaitAll() }
            assertEquals(1, owners.distinct().size)
            assertEquals(owners.first(), storage.getString("identity.canonical_owner_id"))
        }
}
