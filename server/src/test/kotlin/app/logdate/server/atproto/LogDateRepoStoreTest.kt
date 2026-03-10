package app.logdate.server.atproto

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.InMemoryLogDateCollectionsMetadataStore
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LogDateRepoStoreTest {
    @Test
    fun `repo store stays stable across instances and clears when records are deleted`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
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
            val repo = AtprotoDid.require(account.did!!)
            val blockStore = InMemoryRepoBlockStore()
            val collectionsRepository =
                RepoBackedLogDateCollectionsRepository(
                    accountRepository = accountRepository,
                    identityService = identityService,
                    blockStore = blockStore,
                    metadataStore = InMemoryLogDateCollectionsMetadataStore(),
                )
            val firstStore =
                LogDateRepoStore(
                    collectionsRepository = collectionsRepository,
                    identityService = identityService,
                    blockStore = blockStore,
                )

            val created =
                firstStore
                    .createRecord(
                        repo = repo,
                        collection = LogDateRepoStore.contentCollection,
                        recordKey = RecordKey.require("entry-1"),
                        value =
                            buildJsonObject {
                                put("\$type", LogDateRepoStore.contentCollection.toString())
                                put("id", "entry-1")
                                put("type", "TEXT")
                                put("content", "hello")
                                put("createdAt", 10L)
                                put("lastUpdated", 10L)
                                put("deviceId", "device-a")
                            },
                    ).getOrThrow()
            val firstHead = firstStore.loadHead(repo).getOrThrow()

            assertNotNull(firstHead)
            assertTrue(blockStore.listBlocks(repo).getOrThrow().isNotEmpty())

            val secondStore =
                LogDateRepoStore(
                    collectionsRepository = collectionsRepository,
                    identityService = identityService,
                    blockStore = blockStore,
                )

            val secondHead = secondStore.loadHead(repo).getOrThrow()
            val listed = secondStore.listRecords(repo, LogDateRepoStore.contentCollection).getOrThrow()

            assertEquals(firstHead, secondHead)
            assertEquals(listOf(created.uri), listed.records.map { it.uri })

            assertTrue(
                secondStore
                    .deleteRecord(
                        RepoRecordId(
                            repo = repo,
                            collection = LogDateRepoStore.contentCollection,
                            recordKey = RecordKey.require("entry-1"),
                        ),
                    ).getOrThrow(),
            )
            assertNotNull(secondStore.loadHead(repo).getOrThrow())
            assertTrue(
                secondStore
                    .listRecords(repo, LogDateRepoStore.contentCollection)
                    .getOrThrow()
                    .records
                    .isEmpty(),
            )
            assertTrue(blockStore.listBlocks(repo).getOrThrow().isNotEmpty())
            assertTrue(blockStore.listCommits(repo).getOrThrow().isNotEmpty())
        }

    @Test
    fun `repo reads reflect writes performed through the canonical collections repository`() =
        runTest {
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
            val repo = AtprotoDid.require(account.did!!)
            val blockStore: RepoBlockStore = InMemoryRepoBlockStore()
            val collectionsRepository =
                RepoBackedLogDateCollectionsRepository(
                    accountRepository = accountRepository,
                    identityService = identityService,
                    blockStore = blockStore,
                    metadataStore = InMemoryLogDateCollectionsMetadataStore(),
                )
            val store =
                LogDateRepoStore(
                    collectionsRepository = collectionsRepository,
                    identityService = identityService,
                    blockStore = blockStore,
                )

            collectionsRepository.upsertEntry(
                userId = account.id.toJavaUUID(),
                entry =
                    LogDateEntry(
                        id = "entry-sync",
                        type = "TEXT",
                        content = "from-sync",
                        mediaUri = null,
                        durationMs = 0L,
                        createdAt = 20L,
                        lastUpdated = 20L,
                        version = 0L,
                        deviceId = DeviceId("device-sync"),
                    ),
            )

            val listedAfterUpsert = store.listRecords(repo, LogDateRepoStore.contentCollection).getOrThrow()

            assertEquals(
                listOf("entry-sync"),
                listedAfterUpsert.records.map { it.uri.recordKey.toString() },
            )
            assertNotNull(store.loadHead(repo).getOrThrow())

            collectionsRepository.deleteEntry(
                userId = account.id.toJavaUUID(),
                id = "entry-sync",
                deletedAt = 30L,
            )

            val listedAfterDelete = store.listRecords(repo, LogDateRepoStore.contentCollection).getOrThrow()

            assertTrue(listedAfterDelete.records.isEmpty())
            assertNotNull(store.loadHead(repo).getOrThrow())
        }

    private fun identityService(accountRepository: AccountRepository): AtprotoIdentityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
            config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
        )
}
