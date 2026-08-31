package app.logdate.client.device.identity

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CanonicalOwnerProviderTest {
    @Test
    fun `canonical owner survives a device identifier reset`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
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
            val storage = InMemoryKeyValueStorage()
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
            val storage = InMemoryKeyValueStorage()
            storage.putString("identity.canonical_owner_id", "corrupt")

            assertFailsWith<CanonicalOwnerProvider.CorruptCanonicalOwnerException> {
                DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId()
            }

            assertEquals("corrupt", storage.getString("identity.canonical_owner_id"))
        }

    @Test
    fun `owner creation fails when persistence cannot be verified`() =
        runTest {
            val storage = InMemoryKeyValueStorage(dropWrites = true)

            assertFailsWith<IllegalStateException> {
                DefaultCanonicalOwnerProvider(storage).getCanonicalOwnerId()
            }

            assertEquals(null, storage.getString("identity.canonical_owner_id"))
        }

    @Test
    fun `fresh installation adopts the remote owner`() =
        runTest {
            val remoteOwner = "b930bf49-d1e9-4a65-b84b-8e74af45012b"
            val freshStorage = InMemoryKeyValueStorage()

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
                InMemoryKeyValueStorage().apply {
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
                InMemoryKeyValueStorage().apply {
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
                InMemoryKeyValueStorage().apply {
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
            val storage = InMemoryKeyValueStorage()
            val provider = DefaultCanonicalOwnerProvider(storage)

            assertEquals(false, provider.hasBoundOwner())
            provider.adoptRemoteOwnerIfUninitialized(remoteOwner)

            assertEquals(true, DefaultCanonicalOwnerProvider(storage).hasBoundOwner())
        }

    private class InMemoryKeyValueStorage(
        private val dropWrites: Boolean = false,
    ) : KeyValueStorage {
        private val values = mutableMapOf<String, String>()
        private val strings = mutableMapOf<String, MutableStateFlow<String?>>()

        override suspend fun getString(key: String): String? = values[key]

        override fun getStringSync(key: String): String? = values[key]

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            if (dropWrites) {
                return
            }
            values[key] = value
            strings.getOrPut(key) { MutableStateFlow(null) }.value = value
        }

        override suspend fun getBoolean(
            key: String,
            defaultValue: Boolean,
        ): Boolean = defaultValue

        override suspend fun putBoolean(
            key: String,
            value: Boolean,
        ) = Unit

        override suspend fun getInt(
            key: String,
            defaultValue: Int,
        ): Int = defaultValue

        override suspend fun putInt(
            key: String,
            value: Int,
        ) = Unit

        override suspend fun getLong(
            key: String,
            defaultValue: Long,
        ): Long = defaultValue

        override suspend fun putLong(
            key: String,
            value: Long,
        ) = Unit

        override suspend fun getFloat(
            key: String,
            defaultValue: Float,
        ): Float = defaultValue

        override suspend fun putFloat(
            key: String,
            value: Float,
        ) = Unit

        override suspend fun remove(key: String) {
            values.remove(key)
            strings.getOrPut(key) { MutableStateFlow(null) }.value = null
        }

        override suspend fun contains(key: String): Boolean = key in values

        override suspend fun clear() {
            values.clear()
        }

        override fun observeString(key: String): Flow<String?> = strings.getOrPut(key) { MutableStateFlow(values[key]) }

        override fun observeBoolean(
            key: String,
            defaultValue: Boolean,
        ): Flow<Boolean> = MutableStateFlow(defaultValue)

        override fun observeInt(
            key: String,
            defaultValue: Int,
        ): Flow<Int> = MutableStateFlow(defaultValue)

        override fun observeLong(
            key: String,
            defaultValue: Long,
        ): Flow<Long> = MutableStateFlow(defaultValue)

        override fun observeFloat(
            key: String,
            defaultValue: Float,
        ): Flow<Float> = MutableStateFlow(defaultValue)
    }
}
