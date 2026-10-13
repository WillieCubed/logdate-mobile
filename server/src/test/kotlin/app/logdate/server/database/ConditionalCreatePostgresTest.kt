@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.ConditionalCreate
import app.logdate.server.logdate.LogDateCollectionKind
import app.logdate.server.logdate.LogDateCollectionMetadata
import app.logdate.server.logdate.LogDateCollectionsMetadataStore
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Explicit disposable PostgreSQL only; races real repo-head swaps on one entry ID. */
class ConditionalCreatePostgresTest {
    @Test
    fun `failed moment updates and deletions preserve the previously committed record`() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("localhost", "127.0.0.1"))
            val previous = TransactionManager.defaultDatabase
            val source = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val accounts = InMemoryAccountRepository()
                val signing = SigningKeyService(InMemorySigningKeyRepository(), "moment-rollback-kek")
                val identity =
                    AtprotoIdentityService(
                        accounts,
                        signing,
                        AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
                    )
                val account =
                    identity.ensureIdentity(
                        accounts.save(
                            Account(
                                id = Uuid.random(),
                                username = "rollback_${Uuid.random().toHexString().take(8)}",
                                displayName = "Rollback",
                                createdAt = Clock.System.now(),
                            ),
                        ),
                    )
                val owner = account.id.toJavaUUID()
                val backing = PostgreSQLLogDateCollectionsMetadataStore()
                val blocks = PostgreSQLRepoBlockStore()
                val canonical = RepoBackedLogDateCollectionsRepository(accounts, identity, signing, blocks, backing)
                val mutations = PostgreSQLJournalMergeStore(source)

                fun moments(metadata: LogDateCollectionsMetadataStore) =
                    app.logdate.server.logdate.RepoBackedLogDateMomentsRepository(
                        accounts,
                        signing,
                        PostgreSQLRepoBlockStore(),
                        metadata,
                        canonical::canonicalRepoDid,
                        mutations,
                    )
                val initial =
                    moments(backing).upsertMoment(
                        owner,
                        app.logdate.server.logdate
                            .LogDateMoment("rollback-mark", 100, 100, 0, DeviceId("watch"), "LDSE2:initial", 1),
                    )
                val persisted = moments(backing).getMoment(owner, initial.id)
                val failing =
                    object : LogDateCollectionsMetadataStore by backing {
                        override suspend fun upsert(
                            userId: java.util.UUID,
                            repoDid: studio.hypertext.atproto.identity.AtprotoDid,
                            collection: LogDateCollectionKind,
                            recordKey: String,
                        ): LogDateCollectionMetadata {
                            backing.upsert(userId, repoDid, collection, recordKey)
                            error("Stop after index write")
                        }

                        override suspend fun delete(
                            userId: java.util.UUID,
                            repoDid: studio.hypertext.atproto.identity.AtprotoDid,
                            collection: LogDateCollectionKind,
                            recordKey: String,
                            deletedAt: Long,
                            allowMissing: Boolean,
                        ): LogDateCollectionMetadata? {
                            backing.delete(userId, repoDid, collection, recordKey, deletedAt, allowMissing)
                            error("Stop after tombstone write")
                        }
                    }
                val repoDid =
                    studio.hypertext.atproto.identity.AtprotoDid
                        .require(requireNotNull(account.did))
                val before = blocks.readHead(repoDid).getOrThrow()
                assertFailsWith<IllegalStateException> {
                    moments(failing).updateMomentIfVersion(owner, initial.id, initial.version) { it.copy(payload = "LDSE2:changed") }
                }
                assertEquals(persisted, moments(backing).getMoment(owner, initial.id))
                assertEquals(before, PostgreSQLRepoBlockStore().readHead(repoDid).getOrThrow())
                assertFailsWith<IllegalStateException> { moments(failing).deleteMoment(owner, initial.id, 500) }
                assertEquals(persisted, moments(backing).getMoment(owner, initial.id))
                assertEquals(before, PostgreSQLRepoBlockStore().readHead(repoDid).getOrThrow())
            } finally {
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }

    @Test
    fun `failure after indexing rolls back body and index together`() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("localhost", "127.0.0.1"))
            val previous = TransactionManager.defaultDatabase
            val source = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val accounts = InMemoryAccountRepository()
                val signing = SigningKeyService(InMemorySigningKeyRepository(), "rollback-kek")
                val identity =
                    AtprotoIdentityService(
                        accounts,
                        signing,
                        AtprotoIdentityConfig(handleDomain = "logdate.test", pdsServiceEndpoint = "https://logdate.test"),
                    )
                val account =
                    identity.ensureIdentity(
                        accounts.save(
                            Account(
                                id = Uuid.random(),
                                username = "rollback_${Uuid.random().toHexString().take(8)}",
                                displayName = "Rollback",
                                createdAt = Clock.System.now(),
                            ),
                        ),
                    )
                val backing = PostgreSQLLogDateCollectionsMetadataStore()
                val failing =
                    object : LogDateCollectionsMetadataStore by backing {
                        override suspend fun upsert(
                            userId: java.util.UUID,
                            repoDid: studio.hypertext.atproto.identity.AtprotoDid,
                            collection: LogDateCollectionKind,
                            recordKey: String,
                        ): LogDateCollectionMetadata {
                            backing.upsert(userId, repoDid, collection, recordKey)
                            error("Stop after index write")
                        }
                    }
                val blocks = PostgreSQLRepoBlockStore()
                val repository = RepoBackedLogDateCollectionsRepository(accounts, identity, signing, blocks, failing)
                val repoDid =
                    studio.hypertext.atproto.identity.AtprotoDid
                        .require(requireNotNull(account.did))
                val before = blocks.readHead(repoDid).getOrThrow()
                assertFailsWith<IllegalStateException> {
                    repository.createEntryIfAbsent(
                        account.id.toJavaUUID(),
                        LogDateEntry("rollback-entry", "TEXT", "LDSE2:opaque", null, 0, 10, 10, 0, DeviceId("watch")),
                        true,
                    )
                }
                val restartedBlocks = PostgreSQLRepoBlockStore()
                assertEquals(before, restartedBlocks.readHead(repoDid).getOrThrow())
                assertNull(backing.metadata(account.id.toJavaUUID(), LogDateCollectionKind.ENTRY, "rollback-entry"))
                val restarted = RepoBackedLogDateCollectionsRepository(accounts, identity, signing, restartedBlocks, backing)
                assertIs<ConditionalCreate.Created<LogDateEntry>>(
                    restarted.createEntryIfAbsent(
                        account.id.toJavaUUID(),
                        LogDateEntry("rollback-entry", "TEXT", "LDSE2:retry", null, 0, 10, 10, 0, DeviceId("watch")),
                        true,
                    ),
                )
                Unit
            } finally {
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }

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
