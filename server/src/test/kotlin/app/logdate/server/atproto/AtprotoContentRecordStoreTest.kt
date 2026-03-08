package app.logdate.server.atproto

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.sync.ContentRecord
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.shared.model.sync.DeviceId
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.InvalidRepoCursorException
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.UnsupportedCollectionException
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AtprotoContentRecordStoreTest {
    @Test
    fun `collectionsForDid reflects whether content exists`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val syncRepository = InMemorySyncRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "alice",
                            displayName = "Alice",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val store = AtprotoContentRecordStore(syncRepository = syncRepository, identityService = identityService)

            assertTrue(store.collectionsForDid(account.did!!).isEmpty())

            syncRepository.upsertContent(
                userId = account.id.toJavaUUID(),
                record =
                    ContentRecord(
                        id = "entry-1",
                        type = "TEXT",
                        content = "hello",
                        mediaUri = null,
                        durationMs = 0,
                        createdAt = 1L,
                        lastUpdated = 1L,
                        serverVersion = 0L,
                        deviceId = DeviceId("device-a"),
                    ),
            )

            assertEquals(listOf(AtprotoContentRecordStore.contentCollection), store.collectionsForDid(account.did))
        }

    @Test
    fun `create get list and delete record round trips`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "brie",
                            displayName = "Brie",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val repoDid =
                studio.hypertext.atproto.identity.AtprotoDid
                    .require(account.did!!)
            val store = AtprotoContentRecordStore(syncRepository = InMemorySyncRepository(), identityService = identityService)
            val created =
                store
                    .createRecord(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        recordKey = RecordKey.require("entry-1"),
                        value =
                            buildJsonObject {
                                put("\$type", AtprotoContentRecordStore.contentCollection.toString())
                                put("type", "TEXT")
                                put("content", "hello")
                                put("createdAt", 10L)
                                put("lastUpdated", 10L)
                                put("deviceId", "device-a")
                            },
                    ).getOrThrow()

            assertTrue(created.cid.startsWith("b"))

            val recordId =
                RepoRecordId(
                    repo = repoDid,
                    collection = AtprotoContentRecordStore.contentCollection,
                    recordKey = RecordKey.require("entry-1"),
                )
            val fetched = store.getRecord(recordId).getOrThrow()
            assertNotNull(fetched)
            assertEquals(created.uri, fetched.uri)
            assertEquals("hello", fetched.value["content"]?.toString()?.trim('"'))

            val listed =
                store
                    .listRecords(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                    ).getOrThrow()
            assertEquals(1, listed.records.size)
            assertEquals(
                "entry-1",
                listed.records
                    .single()
                    .uri.recordKey
                    ?.toString(),
            )

            assertTrue(store.deleteRecord(recordId).getOrThrow())
            assertNull(store.getRecord(recordId).getOrThrow())
            assertTrue(store.deleteRecord(recordId).getOrThrow().not())
        }

    @Test
    fun `putRecord clears explicit null fields and validates swap record`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "cora",
                            displayName = "Cora",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val repoDid =
                studio.hypertext.atproto.identity.AtprotoDid
                    .require(account.did!!)
            val store = AtprotoContentRecordStore(syncRepository = InMemorySyncRepository(), identityService = identityService)
            val recordId =
                RepoRecordId(
                    repo = repoDid,
                    collection = AtprotoContentRecordStore.contentCollection,
                    recordKey = RecordKey.require("entry-2"),
                )
            val firstWrite =
                store
                    .putRecord(
                        recordId = recordId,
                        value =
                            buildJsonObject {
                                put("type", "AUDIO")
                                put("content", "waveform")
                                put("mediaUri", "gcs://clip")
                                put("durationMs", 42L)
                                put("createdAt", 100L)
                                put("lastUpdated", 100L)
                            },
                    ).getOrThrow()

            val invalidSwap =
                store
                    .putRecord(
                        recordId = recordId,
                        value =
                            buildJsonObject {
                                put("type", "AUDIO")
                                put("content", JsonNull)
                                put("mediaUri", JsonNull)
                                put("durationMs", 0L)
                                put("lastUpdated", 200L)
                            },
                        swapRecord = "bafk-invalid",
                    ).exceptionOrNull()
            val invalidSwapException = assertIs<InvalidSwapException>(invalidSwap)
            assertEquals(firstWrite.cid, invalidSwapException.expectedCid)
            assertEquals("bafk-invalid", invalidSwapException.providedCid)

            val updated =
                store
                    .putRecord(
                        recordId = recordId,
                        value =
                            buildJsonObject {
                                put("type", "AUDIO")
                                put("content", JsonNull)
                                put("mediaUri", JsonNull)
                                put("durationMs", 0L)
                                put("lastUpdated", 200L)
                            },
                        swapRecord = firstWrite.cid,
                    ).getOrThrow()
            assertTrue(updated.cid.startsWith("b"))
            val fetched = store.getRecord(recordId).getOrThrow()
            assertNotNull(fetched)
            assertEquals(JsonNull, fetched.value["content"])
            assertEquals(JsonNull, fetched.value["mediaUri"])
        }

    @Test
    fun `unsupported collection and invalid cursor fail explicitly`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "dana",
                            displayName = "Dana",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val repoDid =
                studio.hypertext.atproto.identity.AtprotoDid
                    .require(account.did!!)
            val store = AtprotoContentRecordStore(syncRepository = InMemorySyncRepository(), identityService = identityService)

            val unsupported =
                store
                    .listRecords(
                        repo = repoDid,
                        collection = Nsid.require("studio.hypertext.logdate.journal"),
                    ).exceptionOrNull()
            assertIs<UnsupportedCollectionException>(unsupported)

            val invalidCursor =
                store
                    .listRecords(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        cursor = "not-a-number",
                    ).exceptionOrNull()
            assertIs<InvalidRepoCursorException>(invalidCursor)
        }

    @Test
    fun `listRecords supports reverse pagination and createRecord generates record keys`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val syncRepository = InMemorySyncRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "erin",
                            displayName = "Erin",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val repoDid =
                studio.hypertext.atproto.identity.AtprotoDid
                    .require(account.did!!)
            val store = AtprotoContentRecordStore(syncRepository = syncRepository, identityService = identityService)
            val userId = account.id.toJavaUUID()

            syncRepository.upsertContent(
                userId = userId,
                record =
                    ContentRecord(
                        id = "entry-1",
                        type = "TEXT",
                        content = "first",
                        mediaUri = null,
                        durationMs = 0,
                        createdAt = 1L,
                        lastUpdated = 1L,
                        serverVersion = 1L,
                        deviceId = DeviceId("device-a"),
                    ),
            )
            syncRepository.upsertContent(
                userId = userId,
                record =
                    ContentRecord(
                        id = "entry-2",
                        type = "TEXT",
                        content = "second",
                        mediaUri = null,
                        durationMs = 0,
                        createdAt = 2L,
                        lastUpdated = 2L,
                        serverVersion = 2L,
                        deviceId = DeviceId("device-a"),
                    ),
            )
            syncRepository.upsertContent(
                userId = userId,
                record =
                    ContentRecord(
                        id = "entry-3",
                        type = "TEXT",
                        content = "third",
                        mediaUri = null,
                        durationMs = 0,
                        createdAt = 3L,
                        lastUpdated = 3L,
                        serverVersion = 3L,
                        deviceId = DeviceId("device-a"),
                    ),
            )

            val reversePage =
                store
                    .listRecords(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        limit = 2,
                        reverse = true,
                    ).getOrThrow()
            val reverseKeys = reversePage.records.map { it.uri.recordKey.toString() }
            assertEquals(2, reverseKeys.size)
            assertNotNull(reversePage.cursor)

            val secondPage =
                store
                    .listRecords(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        limit = 2,
                        cursor = reversePage.cursor,
                        reverse = true,
                    ).getOrThrow()
            assertTrue(secondPage.records.size <= 1)
            assertNull(secondPage.cursor)

            val created =
                store
                    .createRecord(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        recordKey = null,
                        value =
                            buildJsonObject {
                                put("type", "TEXT")
                                put("content", "generated")
                            },
                    ).getOrThrow()
            assertTrue(created.uri.toString().contains("/content-"))
        }

    @Test
    fun `ascending pagination and delete swap failures cover repo store interface paths`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val syncRepository = InMemorySyncRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "gwen",
                            displayName = "Gwen",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val repoDid =
                studio.hypertext.atproto.identity.AtprotoDid
                    .require(account.did!!)
            val store: studio.hypertext.atproto.repo.RepoRecordStore =
                AtprotoContentRecordStore(syncRepository = syncRepository, identityService = identityService)

            store
                .createRecord(
                    repo = repoDid,
                    collection = AtprotoContentRecordStore.contentCollection,
                    recordKey = RecordKey.require("entry-1"),
                    value =
                        buildJsonObject {
                            put("type", "TEXT")
                            put("content", "one")
                        },
                ).getOrThrow()
            store
                .createRecord(
                    repo = repoDid,
                    collection = AtprotoContentRecordStore.contentCollection,
                    recordKey = RecordKey.require("entry-2"),
                    value =
                        buildJsonObject {
                            put("type", "TEXT")
                            put("content", "two")
                        },
                ).getOrThrow()
            val third =
                store
                    .createRecord(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        recordKey = RecordKey.require("entry-3"),
                        value =
                            buildJsonObject {
                                put("type", "TEXT")
                                put("content", "three")
                            },
                    ).getOrThrow()

            val firstPage =
                store
                    .listRecords(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        limit = 2,
                    ).getOrThrow()
            assertEquals(listOf("entry-1", "entry-2"), firstPage.records.map { it.uri.recordKey.toString() })
            assertNotNull(firstPage.cursor)

            val secondPage =
                store
                    .listRecords(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        limit = 2,
                        cursor = firstPage.cursor,
                    ).getOrThrow()
            assertTrue(secondPage.records.size <= 1)

            val invalidSwap =
                store
                    .deleteRecord(
                        recordId =
                            RepoRecordId(
                                repo = repoDid,
                                collection = AtprotoContentRecordStore.contentCollection,
                                recordKey = RecordKey.require("entry-3"),
                            ),
                        swapRecord = "bafk-invalid",
                    ).exceptionOrNull()
            val swapException = assertIs<InvalidSwapException>(invalidSwap)
            assertEquals(third.cid, swapException.expectedCid)
        }

    @Test
    fun `record store preserves existing values and surfaces unknown repo errors`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val syncRepository = InMemorySyncRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "finn",
                            displayName = "Finn",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val store = AtprotoContentRecordStore(syncRepository = syncRepository, identityService = identityService)
            val recordId =
                RepoRecordId(
                    repo =
                        studio.hypertext.atproto.identity.AtprotoDid
                            .require(account.did!!),
                    collection = AtprotoContentRecordStore.contentCollection,
                    recordKey = RecordKey.require("entry-keep"),
                )

            store
                .putRecord(
                    recordId = recordId,
                    value =
                        buildJsonObject {
                            put("type", "TEXT")
                            put("content", "keep-me")
                            put("mediaUri", "gcs://media")
                            put("durationMs", 15L)
                            put("createdAt", 10L)
                            put("lastUpdated", 10L)
                            put("deviceId", "device-a")
                        },
                ).getOrThrow()

            store
                .putRecord(
                    recordId = recordId,
                    value =
                        buildJsonObject {
                            put("lastUpdated", 11L)
                        },
                ).getOrThrow()

            val updated = requireNotNull(store.getRecord(recordId).getOrThrow())
            assertEquals("keep-me", updated.value["content"]?.toString()?.trim('"'))
            assertEquals("gcs://media", updated.value["mediaUri"]?.toString()?.trim('"'))
            assertEquals("device-a", updated.value["deviceId"]?.toString()?.trim('"'))

            val unsupportedType =
                store
                    .putRecord(
                        recordId = recordId,
                        value =
                            buildJsonObject {
                                put("\$type", "studio.hypertext.logdate.other")
                            },
                    ).exceptionOrNull()
            assertIs<UnsupportedCollectionException>(unsupportedType)

            val unknownDidRecordId =
                RepoRecordId(
                    repo =
                        studio.hypertext.atproto.identity.AtprotoDid
                            .require("did:web:missing.logdate.app"),
                    collection = AtprotoContentRecordStore.contentCollection,
                    recordKey = RecordKey.require("entry-keep"),
                )
            assertFailsWith<IllegalArgumentException> {
                store.collectionsForDid("did:web:missing.logdate.app")
            }
            assertTrue(store.getRecord(unknownDidRecordId).isFailure)
            assertTrue(store.listRecords(repo = unknownDidRecordId.repo, collection = unknownDidRecordId.collection).isFailure)
            assertTrue(store.putRecord(recordId = unknownDidRecordId, value = buildJsonObject {}).isFailure)
            assertTrue(store.deleteRecord(recordId = unknownDidRecordId).isFailure)
        }

    @Test
    fun `createRecord covers coroutine resume path when identity lookup suspends`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = YieldingAccountRepository()
            val identityService = identityService(accountRepository)
            val account =
                identityService.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "haze",
                            displayName = "Haze",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )
            val repoDid =
                studio.hypertext.atproto.identity.AtprotoDid
                    .require(account.did!!)
            val store = AtprotoContentRecordStore(syncRepository = InMemorySyncRepository(), identityService = identityService)
            val writeResult =
                store
                    .createRecord(
                        repo = repoDid,
                        collection = AtprotoContentRecordStore.contentCollection,
                        value =
                            buildJsonObject {
                                put("type", "TEXT")
                                put("content", "reflective")
                            },
                        recordKey = RecordKey.require("entry-reflective"),
                    ).getOrThrow()
            assertEquals("entry-reflective", writeResult.uri.recordKey.toString())
        }

    @Test
    fun `synthetic cid helpers cover empty base32 and multi byte varints`() {
        val syntheticCidClass = Class.forName("app.logdate.server.atproto.SyntheticCid")
        val instance = syntheticCidClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)

        val encodeVarint = syntheticCidClass.declaredMethod("encodeVarint", Int::class.javaPrimitiveType!!).apply { isAccessible = true }
        val encodeBase32 = syntheticCidClass.declaredMethod("encodeBase32", ByteArray::class.java).apply { isAccessible = true }

        val encodedVarint = encodeVarint.invoke(instance, 300) as ByteArray
        val encodedEmpty = encodeBase32.invoke(instance, byteArrayOf()) as String
        val encodedSingle = encodeBase32.invoke(instance, byteArrayOf(0x66)) as String
        val encodedAligned = encodeBase32.invoke(instance, byteArrayOf(1, 2, 3, 4, 5)) as String

        assertTrue(encodedVarint.contentEquals(byteArrayOf(0xac.toByte(), 0x02)))
        assertEquals("", encodedEmpty)
        assertEquals("my", encodedSingle)
        assertEquals("aebagbaf", encodedAligned)
    }

    private fun identityService(accountRepository: AccountRepository): AtprotoIdentityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
            config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
        )

    private class YieldingAccountRepository(
        private val delegate: InMemoryAccountRepository = InMemoryAccountRepository(),
    ) : AccountRepository by delegate {
        override suspend fun findByDid(did: String): Account? {
            kotlinx.coroutines.yield()
            return delegate.findByDid(did)
        }
    }

    private fun Class<*>.declaredMethod(
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method = getDeclaredMethod(name, *parameterTypes)
}
