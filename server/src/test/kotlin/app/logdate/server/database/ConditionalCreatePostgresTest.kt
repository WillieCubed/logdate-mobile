@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.ConditionalCreate
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.LogDateRecordState
import app.logdate.server.logdate.RepoBackedLogDateCollectionsRepository
import app.logdate.shared.model.sync.DeviceId
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import studio.hypertext.atproto.repo.RepoHeadConflictException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Explicit disposable PostgreSQL only; races real repo-head swaps on one entry ID. */
class ConditionalCreatePostgresTest {
    @Test
    fun `concurrent strict creates of one entry produce exactly one record`() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("localhost", "127.0.0.1"))
            val previous = TransactionManager.defaultDatabase
            val source = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
            val owner = Uuid.random()
            val ownerId = owner.toJavaUUID()
            var did: String? = null
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val accounts = InMemoryAccountRepository()
                val signing = SigningKeyService(InMemorySigningKeyRepository(), "race-kek")
                val config = AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test")
                val identity = AtprotoIdentityService(accounts, signing, config)
                val username = "race_${owner.toHexString().take(8)}"
                val account =
                    identity.ensureIdentity(
                        accounts.save(Account(id = owner, username = username, displayName = "Race", createdAt = Clock.System.now())),
                    )
                did = account.did
                val repository =
                    RepoBackedLogDateCollectionsRepository(
                        accounts,
                        identity,
                        signing,
                        PostgreSQLRepoBlockStore(),
                        PostgreSQLLogDateCollectionsMetadataStore(),
                    )
                val entry =
                    LogDateEntry("race-entry", "TEXT", "LDSE2:opaque", null, 0L, 10L, 10L, 0L, DeviceId("pixel-watch-3"))

                suspend fun create() = runCatching { repository.createEntryIfAbsent(ownerId, entry, respectTombstones = true) }
                val outcomes = (1..4).map { async(Dispatchers.IO) { create() } }.awaitAll()

                assertEquals(1, outcomes.count { it.getOrNull() is ConditionalCreate.Created }, outcomes.toString())
                assertTrue(outcomes.all { it.isSuccess || it.exceptionOrNull() is RepoHeadConflictException }, outcomes.toString())
                assertIs<LogDateRecordState.Live>(repository.entryState(ownerId, "race-entry"))
                assertEquals(1, repository.entryChanges(ownerId, since = 0L, limit = 10).changes.size)
            } finally {
                transaction {
                    LogDateCollectionRecordsTable.deleteWhere { userId eq ownerId }
                    LogDateCollectionStatesTable.deleteWhere { userId eq ownerId }
                    did?.let { repo ->
                        AtprotoRepoHeadsTable.deleteWhere { repoDid eq repo }
                        AtprotoRepoCommitsTable.deleteWhere { repoDid eq repo }
                        AtprotoRepoBlockLinksTable.deleteWhere { repoDid eq repo }
                    }
                }
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }
}
