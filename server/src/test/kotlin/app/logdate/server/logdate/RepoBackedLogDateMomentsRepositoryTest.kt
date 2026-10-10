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
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RepoBackedLogDateMomentsRepositoryTest {
    private val accountRepository = InMemoryAccountRepository()
    private val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
    private val identityService =
        AtprotoIdentityService(
            accountRepository = accountRepository,
            signingKeyService = signingKeyService,
            config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
        )
    private val blockStore = InMemoryRepoBlockStore()
    private val metadataStore = InMemoryLogDateCollectionsMetadataStore()
    private val collections =
        RepoBackedLogDateCollectionsRepository(accountRepository, identityService, signingKeyService, blockStore, metadataStore)
    private val moments =
        RepoBackedLogDateMomentsRepository(accountRepository, signingKeyService, blockStore, metadataStore, collections::canonicalRepoDid)
    private var users = 0

    @Test
    fun `a moment round trips and its deletion reaches the moment feed`() =
        runTest {
            val (user, _) = newUser()
            val stored = moments.upsertMoment(user, moment("m1"))

            assertEquals(moment("m1").copy(version = stored.version), moments.getMoment(user, "m1"))
            assertEquals(listOf("m1"), moments.momentChanges(user, since = 0L, limit = 10).changes.map { it.id })

            moments.deleteMoment(user, "m1", deletedAt = 99L)

            assertNull(moments.getMoment(user, "m1"))
            val after = moments.momentChanges(user, since = stored.version, limit = 10)
            assertTrue(after.changes.isEmpty())
            assertEquals(listOf("m1" to 99L), after.deletions.map { it.id to it.deletedAt })
        }

    @Test
    fun `moments stay out of entries journals links and status counts`() =
        runTest {
            val (user, _) = newUser()
            collections.upsertEntry(user, entry("e1"))

            moments.upsertMoment(user, moment("m1"))
            moments.upsertMoment(user, moment("m2"))
            moments.deleteMoment(user, "m2", deletedAt = 5L)

            val status = collections.status(user)
            assertEquals(1, status.entryCount)
            assertEquals(0, status.journalCount)
            assertEquals(0, status.associationCount)
            val entries = collections.entryChanges(user, since = 0L, limit = 50)
            assertEquals(listOf("e1"), entries.changes.map { it.id })
            assertTrue(entries.deletions.isEmpty())
            val journals = collections.journalChanges(user, since = 0L, limit = 50)
            assertTrue(journals.changes.isEmpty() && journals.deletions.isEmpty())
            val links = collections.associationChanges(user, since = 0L, limit = 50)
            assertTrue(links.changes.isEmpty() && links.deletions.isEmpty())
            assertEquals(listOf("e1"), collections.listEntries(user).map { it.id })
        }

    @Test
    fun `strict moment creation never brings back a deleted moment`() =
        runTest {
            val (user, did) = newUser()
            val created = moments.createMomentIfAbsent(user, moment("m3"), respectTombstones = true)
            val version = if (created is ConditionalCreate.Created) created.record.version else fail("expected a create, got $created")
            val commits = blockStore.listCommits(did).getOrThrow().size

            val duplicate = moments.createMomentIfAbsent(user, moment("m3"), respectTombstones = true)

            assertEquals(ConditionalCreate.Exists(version), duplicate)
            assertEquals(commits, blockStore.listCommits(did).getOrThrow().size)

            moments.deleteMoment(user, "m3", deletedAt = 7L)
            val outcome = moments.createMomentIfAbsent(user, moment("m3"), respectTombstones = true)
            assertEquals(7L, assertIs<ConditionalCreate.Deleted>(outcome).deletedAt)
            assertIs<LogDateRecordState.Deleted>(moments.momentState(user, "m3"))
        }

    @Test
    fun `purging tombstones removes expired moment tombstones`() =
        runTest {
            val (user, _) = newUser()
            moments.upsertMoment(user, moment("m4"))
            moments.deleteMoment(user, "m4", deletedAt = 10L)
            assertEquals(1, moments.momentChanges(user, since = 0L, limit = 10).deletions.size)

            collections.purgeTombstones(user, olderThan = 11L)

            assertTrue(moments.momentChanges(user, since = 0L, limit = 10).deletions.isEmpty())
            assertEquals(LogDateRecordState.Absent, moments.momentState(user, "m4"))
        }

    private suspend fun newUser(): Pair<UUID, AtprotoDid> {
        users += 1
        val account =
            identityService.ensureIdentity(
                accountRepository.save(
                    Account(id = Uuid.random(), username = "moments_$users", displayName = "Moments", createdAt = Clock.System.now()),
                ),
            )
        return account.id.toJavaUUID() to AtprotoDid.require(requireNotNull(account.did))
    }

    private fun moment(id: String) =
        LogDateMoment(
            id = id,
            capturedAt = 1_789_221_791_412L,
            lastUpdated = 1_789_221_791_412L,
            version = 0L,
            deviceId = DeviceId("pixel-watch-3"),
            payload = "LDSE2:opaque-$id",
            schema = 1,
        )

    private fun entry(id: String) =
        LogDateEntry(
            id = id,
            type = "TEXT",
            content = "LDSE2:entry-$id",
            mediaUri = null,
            durationMs = 0L,
            createdAt = 10L,
            lastUpdated = 10L,
            version = 0L,
            deviceId = DeviceId("pixel-9"),
        )
}
