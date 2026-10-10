package app.logdate.server.logdate

import app.logdate.server.atproto.HostedRepoCommitSigner
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
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A create-only write must heal the two ways a process can stop between its repo write and its sync index row. */
@OptIn(ExperimentalUuidApi::class)
class RepoConditionalWriterRecoveryTest {
    private val accounts = InMemoryAccountRepository()
    private val signing = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
    private val identity =
        AtprotoIdentityService(
            accountRepository = accounts,
            signingKeyService = signing,
            config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
        )

    @Test
    fun `a create indexes a repo record whose sync metadata was never written`() =
        runTest {
            val account = newAccount("orphan_owner")
            val userId = account.id.toJavaUUID()
            val repoDid = AtprotoDid.require(requireNotNull(account.did))
            val blockStore = InMemoryRepoBlockStore()
            val repository = repository(blockStore, InMemoryLogDateCollectionsMetadataStore())
            DefaultRepoEngine(blockStore, signer = HostedRepoCommitSigner(accounts, signing))
                .putRecord(entryRecordId(repoDid, "orphan"), entry("orphan", "survivor").toRepoJson())
                .getOrThrow()
            assertEquals(LogDateRecordState.Absent, repository.entryState(userId, "orphan"))

            val outcome = repository.createEntryIfAbsent(userId, entry("orphan", "replayed"), respectTombstones = true)

            assertNotNull(assertIs<ConditionalCreate.Exists>(outcome).serverVersion)
            assertEquals("survivor", repository.getEntry(userId, "orphan")?.content)
            assertEquals(1, repository.entryChanges(userId, since = 0L, limit = 10).changes.size)
        }

    @Test
    fun `a failed metadata write removes the new record so a retry can create it`() =
        runTest {
            val account = newAccount("failing_owner")
            val userId = account.id.toJavaUUID()
            val repoDid = AtprotoDid.require(requireNotNull(account.did))
            val blockStore = InMemoryRepoBlockStore()
            val backing = InMemoryLogDateCollectionsMetadataStore()
            var failNext = true
            val metadata =
                object : LogDateCollectionsMetadataStore by backing {
                    override suspend fun upsert(
                        userId: UUID,
                        repoDid: AtprotoDid,
                        collection: LogDateCollectionKind,
                        recordKey: String,
                    ): LogDateCollectionMetadata {
                        check(!failNext) { "Simulated metadata failure" }
                        return backing.upsert(userId, repoDid, collection, recordKey)
                    }
                }
            val repository = repository(blockStore, metadata)
            val reader = DefaultRepoEngine(blockStore)

            assertFailsWith<IllegalStateException> {
                repository.createEntryIfAbsent(userId, entry("retry", "first"), respectTombstones = true)
            }
            assertNull(reader.getRecord(entryRecordId(repoDid, "retry")).getOrThrow())

            failNext = false
            val retried = repository.createEntryIfAbsent(userId, entry("retry", "second"), respectTombstones = true)

            assertEquals("second", assertIs<ConditionalCreate.Created<LogDateEntry>>(retried).record.content)
            assertEquals("second", repository.getEntry(userId, "retry")?.content)
        }

    private suspend fun newAccount(username: String): Account =
        identity.ensureIdentity(
            accounts.save(Account(id = Uuid.random(), username = username, displayName = username, createdAt = Clock.System.now())),
        )

    private fun repository(
        blockStore: InMemoryRepoBlockStore,
        metadataStore: LogDateCollectionsMetadataStore,
    ) = RepoBackedLogDateCollectionsRepository(
        accountRepository = accounts,
        identityService = identity,
        signingKeyService = signing,
        blockStore = blockStore,
        metadataStore = metadataStore,
    )

    private fun entry(
        id: String,
        content: String,
    ) = LogDateEntry(
        id = id,
        type = "TEXT",
        content = content,
        mediaUri = null,
        durationMs = 0L,
        createdAt = 10L,
        lastUpdated = 10L,
        version = 0L,
        deviceId = DeviceId("pixel-watch-3"),
    )
}
