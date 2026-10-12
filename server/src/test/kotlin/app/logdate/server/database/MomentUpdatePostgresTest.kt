@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.LogDateMoment
import app.logdate.server.logdate.MomentUpdateResult
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.server.logdate.RepoBackedLogDateMomentsRepository
import app.logdate.shared.model.sync.DeviceId
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Real, disposable localhost PostgreSQL; independent repository and advisory-lock instances. */
class MomentUpdatePostgresTest {
    @Test fun knownVersionPatchIsAtomicAcrossIndependentServerStores() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(url != null && System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true")
            val uri = URI(requireNotNull(url))
            require(uri.host in setOf("localhost", "127.0.0.1") && uri.path.startsWith("/logdate_agent_moment_"))
            val previous = TransactionManager.defaultDatabase
            val source = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val accounts = PostgreSQLAccountRepository()
                val account =
                    accounts.save(
                        Account(
                            id = Uuid.random(),
                            username = "moment_${Uuid.random().toString().take(8)}",
                            displayName = "Isolated moment race",
                            createdAt = Clock.System.now(),
                        ),
                    )
                val signing = SigningKeyService(InMemorySigningKeyRepository(), "isolated-moment-race-kek")
                val identity =
                    AtprotoIdentityService(
                        accounts,
                        signing,
                        AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
                    )
                identity.ensureIdentity(account)
                val canonical =
                    RepoBackedLogDateCollectionsRepository(
                        accounts,
                        identity,
                        signing,
                        PostgreSQLRepoBlockStore(),
                        PostgreSQLLogDateCollectionsMetadataStore(),
                    )

                fun repository() =
                    RepoBackedLogDateMomentsRepository(
                        accounts,
                        signing,
                        PostgreSQLRepoBlockStore(),
                        PostgreSQLLogDateCollectionsMetadataStore(),
                        canonical::canonicalRepoDid,
                        PostgreSQLJournalMergeStore(source),
                    )
                val firstStore = repository()
                val secondStore = repository()
                val owner = account.id.toJavaUUID()
                val initial =
                    firstStore.upsertMoment(
                        owner,
                        LogDateMoment("atomic-mark", 100, 100, 0, DeviceId("test-watch"), "LDSE2:initial", 1),
                    )
                val firstRead = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val secondTransform = CompletableDeferred<Unit>()
                val first =
                    async(Dispatchers.IO) {
                        firstStore.updateMomentIfVersion(owner, initial.id, initial.version) {
                            firstRead.complete(Unit)
                            release.await()
                            it.copy(payload = "LDSE2:winner")
                        }
                    }
                withTimeout(10_000) { firstRead.await() }
                val second =
                    async(Dispatchers.IO) {
                        secondStore.updateMomentIfVersion(owner, initial.id, initial.version) {
                            secondTransform.complete(Unit)
                            it.copy(payload = "LDSE2:stale")
                        }
                    }
                try {
                    assertNull(
                        withTimeoutOrNull(150) { secondTransform.await() },
                        "A second server must not pass the held account boundary",
                    )
                } finally {
                    release.complete(Unit)
                }
                withTimeout(10_000) {
                    val winner = assertIs<MomentUpdateResult.Updated>(first.await()).moment
                    assertEquals(MomentUpdateResult.Conflict, second.await())
                    assertTrue(winner.version > initial.version)
                    assertEquals("LDSE2:winner", secondStore.getMoment(owner, initial.id)?.payload)
                    val changes = secondStore.momentChanges(owner, initial.version, 10)
                    assertEquals(listOf(winner.version), changes.changes.map { it.version })
                    assertEquals(listOf("LDSE2:winner"), changes.changes.map { it.payload })
                }
            } finally {
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }
}
