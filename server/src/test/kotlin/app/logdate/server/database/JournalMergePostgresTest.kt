@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.JournalMergedException
import app.logdate.server.logdate.LogDateAssociation
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.LogDateJournal
import app.logdate.server.logdate.MergeAwareLogDateCollectionsRepository
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.JournalMergeRequest
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.Assume.assumeTrue
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class JournalMergePostgresTest {
    @Test
    fun `PostgreSQL migration persists interrupted merges redirects and account locks across instances`() =
        runBlocking {
            val url = System.getenv("LOGDATE_JOURNAL_MERGE_TEST_DATABASE_URL")
            assumeTrue(url != null && System.getenv("LOGDATE_JOURNAL_MERGE_TEST_DISPOSABLE") == "true")
            val uri = URI(requireNotNull(url))
            require(uri.host in setOf("localhost", "127.0.0.1") && uri.path.startsWith("/logdate_agent_journal_merge_"))
            val previous = TransactionManager.defaultDatabase
            val dataSource =
                DatabaseConfig.createDataSource(
                    databaseUrl = url,
                    username = "logdate",
                    password = "logdate",
                ) as HikariDataSource
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(dataSource, autoMigrate = true)
                val accounts = PostgreSQLAccountRepository()
                val owner =
                    accounts.save(
                        Account(
                            id = Uuid.random(),
                            username = "merge_${Uuid.random().toString().take(8)}",
                            displayName = "Merge owner",
                            createdAt = Clock.System.now(),
                        ),
                    )
                val keys = SigningKeyService(InMemorySigningKeyRepository(), "journal-merge-test-kek")
                val identity =
                    AtprotoIdentityService(
                        accounts,
                        keys,
                        AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                    )
                identity.ensureIdentity(owner)
                val canonical =
                    RepoBackedLogDateCollectionsRepository(
                        accounts,
                        identity,
                        keys,
                        PostgreSQLRepoBlockStore(),
                        PostgreSQLLogDateCollectionsMetadataStore(),
                    )
                val user = owner.id.toJavaUUID()
                val source = LogDateJournal(Uuid.random().toString(), "Source", "Source metadata", 1L, 1L, 0L, DeviceId("device"))
                val destination = source.copy(id = Uuid.random().toString(), title = "Survivor")
                canonical.upsertJournal(user, source)
                canonical.upsertJournal(user, destination)
                val remote = Uuid.random().toString()
                val offline = Uuid.random().toString()
                canonical.upsertAssociations(user, listOf(LogDateAssociation(source.id, remote, 1L, 0L, DeviceId("device"))))
                val interrupted =
                    object : LogDateCollectionsRepository by canonical {
                        override suspend fun deleteJournal(
                            userId: java.util.UUID,
                            id: String,
                            deletedAt: Long,
                        ) {
                            error("Interrupted after memberships moved")
                        }
                    }
                val request = JournalMergeRequest(Uuid.random().toString(), destination.id, listOf(offline))
                val firstStore = PostgreSQLJournalMergeStore(dataSource)
                assertFailsWith<IllegalStateException> {
                    MergeAwareLogDateCollectionsRepository(
                        interrupted,
                        firstStore,
                    ).merge(user, source.id, request)
                }
                val secondStore = PostgreSQLJournalMergeStore(dataSource)
                val persisted = secondStore.operations(user).single()
                assertTrue(persisted.started)
                assertEquals(setOf(remote, offline), persisted.contentIds.toSet())
                val restarted = MergeAwareLogDateCollectionsRepository(canonical, secondStore)
                assertEquals(destination.id, restarted.merge(user, source.id, request).destinationId)
                assertNull(restarted.getJournal(user, source.id))
                assertEquals(
                    setOf(remote, offline),
                    restarted
                        .listAssociations(user)
                        .filter { it.journalId == destination.id }
                        .map { it.entryId }
                        .toSet(),
                )
                assertEquals(
                    destination.id,
                    restarted
                        .journalChanges(user, 0, 100)
                        .deletions
                        .single { it.id == source.id }
                        .mergedIntoJournalId,
                )
                restarted.purgeTombstones(user, Long.MAX_VALUE)
                val afterRetention = MergeAwareLogDateCollectionsRepository(canonical, PostgreSQLJournalMergeStore(dataSource))
                assertEquals(
                    destination.id,
                    assertFailsWith<JournalMergedException> { afterRetention.upsertJournal(user, source) }.destinationId,
                )
                assertNotNull(afterRetention.getJournal(user, destination.id))
                val recoverableSource = source.copy(id = Uuid.random().toString())
                val deletedTarget = destination.copy(id = Uuid.random().toString())
                val replacementTarget = destination.copy(id = Uuid.random().toString(), title = "Replacement")
                listOf(recoverableSource, deletedTarget, replacementTarget).forEach { canonical.upsertJournal(user, it) }
                canonical.upsertAssociations(user, listOf(LogDateAssociation(recoverableSource.id, remote, 1L, 0L, DeviceId("device"))))
                val failedRequest = JournalMergeRequest(Uuid.random().toString(), deletedTarget.id, listOf(offline))
                assertFailsWith<IllegalStateException> {
                    MergeAwareLogDateCollectionsRepository(interrupted, firstStore).merge(user, recoverableSource.id, failedRequest)
                }
                canonical.deleteJournal(user, deletedTarget.id, System.currentTimeMillis())
                val replacementRequest = JournalMergeRequest(Uuid.random().toString(), replacementTarget.id, listOf(offline))
                assertEquals(replacementTarget.id, afterRetention.merge(user, recoverableSource.id, replacementRequest).destinationId)
                assertEquals(
                    setOf(remote, offline),
                    canonical
                        .listAssociations(user)
                        .filter { it.journalId == replacementTarget.id }
                        .map { it.entryId }
                        .toSet(),
                )
                assertTrue(PostgreSQLJournalMergeStore(dataSource).operations(user).none { it.operationId == failedRequest.operationId })
                assertEquals(
                    listOf(failedRequest.operationId),
                    PostgreSQLJournalMergeStore(
                        dataSource,
                    ).operations(user).single { it.operationId == replacementRequest.operationId }.supersededOperationIds,
                )
                val acquired = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val enteredSecond = CompletableDeferred<Unit>()
                val first =
                    async(Dispatchers.IO) {
                        firstStore.withAccountLock(user) {
                            acquired.complete(Unit)
                            release.await()
                        }
                    }
                acquired.await()
                val second = async(Dispatchers.IO) { secondStore.withAccountLock(user) { enteredSecond.complete(Unit) } }
                try {
                    assertNull(withTimeoutOrNull(150) { enteredSecond.await() }, "Independent store instances must share an account lock")
                } finally {
                    release.complete(Unit)
                }
                withTimeout(5_000) {
                    first.await()
                    second.await()
                }
                assertTrue(enteredSecond.isCompleted)
            } finally {
                TransactionManager.defaultDatabase = previous
                dataSource.close()
            }
        }
}
