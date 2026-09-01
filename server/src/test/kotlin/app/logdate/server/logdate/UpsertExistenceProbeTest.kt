package app.logdate.server.logdate

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.shared.model.sync.DeviceId
import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoBlock
import studio.hypertext.atproto.repo.RepoBlockStore
import studio.hypertext.atproto.repo.RepoHead
import studio.hypertext.atproto.repo.SignedRepoCommit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Upserting decided between 200 and 201 by fetching the whole record, which opens the repo and
 * therefore costs a pass over every block in it. The route only needs to know whether the record
 * exists, and the metadata row answers that from an indexed lookup - so a device re-uploading
 * entries the server already has was paying for a full repo read per entry and discarding it.
 */
@OptIn(ExperimentalUuidApi::class)
class UpsertExistenceProbeTest {
    /** Counts repo opens, which is what a full read costs. */
    private class CountingBlockStore : RepoBlockStore by InMemoryRepoBlockStore() {
        private val delegate = InMemoryRepoBlockStore()
        var listBlocksCalls = 0
            private set

        override suspend fun readHead(repo: AtprotoDid) = delegate.readHead(repo)

        override suspend fun writeHead(head: RepoHead) = delegate.writeHead(head)

        override suspend fun compareAndSwapHead(
            head: RepoHead,
            expectedRevision: Long?,
        ) = delegate.compareAndSwapHead(head, expectedRevision)

        override suspend fun readBlock(cid: Cid) = delegate.readBlock(cid)

        override suspend fun writeBlock(
            repo: AtprotoDid,
            block: RepoBlock,
        ) = delegate.writeBlock(repo, block)

        override suspend fun clearRepo(repo: AtprotoDid) = delegate.clearRepo(repo)

        override suspend fun listBlocks(repo: AtprotoDid): Result<List<RepoBlock>> {
            listBlocksCalls++
            return delegate.listBlocks(repo)
        }

        override suspend fun appendCommit(
            repo: AtprotoDid,
            commit: SignedRepoCommit,
        ) = delegate.appendCommit(repo, commit)

        override suspend fun listCommits(repo: AtprotoDid) = delegate.listCommits(repo)
    }

    private fun entry(id: String) =
        LogDateEntry(
            id = id,
            type = "TEXT",
            content = "hello",
            mediaUri = null,
            durationMs = 0L,
            createdAt = 10L,
            lastUpdated = 10L,
            version = 0L,
            deviceId = DeviceId("device-a"),
        )

    @Test
    fun `checking whether an entry exists does not open the repo`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val identityService =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = signingKeyService,
                    config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
                )
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
            val blockStore = CountingBlockStore()
            val repository =
                RepoBackedLogDateCollectionsRepository(
                    accountRepository = accountRepository,
                    identityService = identityService,
                    signingKeyService = signingKeyService,
                    blockStore = blockStore,
                    metadataStore = InMemoryLogDateCollectionsMetadataStore(),
                )
            val userId: UUID = account.id.toJavaUUID()
            repository.upsertEntry(userId, entry("entry-1"))

            val before = blockStore.listBlocksCalls
            assertTrue(repository.entryExists(userId, "entry-1"))
            assertFalse(repository.entryExists(userId, "never-written"))

            assertEquals(
                0,
                blockStore.listBlocksCalls - before,
                "an existence check should be answered from the metadata row alone",
            )
        }
}
