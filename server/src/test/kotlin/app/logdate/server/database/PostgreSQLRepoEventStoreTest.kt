package app.logdate.server.database

import app.logdate.server.atproto.firehose.RepoEventStoreContract
import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.withH2Database
import app.logdate.server.database.support.withOwnPostgresDatabase
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.test.Test

/**
 * The repository event log against H2 in PostgreSQL mode and against the real PostgreSQL through the
 * real migrations. Both run the same [RepoEventStoreContract], so the log numbers, reads and prunes
 * events the same way the in-memory log does.
 */
class PostgreSQLRepoEventStoreTest {
    private fun onH2(test: (PostgreSQLRepoEventStore) -> Unit) =
        withH2Database(
            AtprotoRepoEventsTable,
            AtprotoRepoEventSequenceTable,
            AtprotoRepoAnnouncedTable,
        ) { test(PostgreSQLRepoEventStore()) }

    private fun onPostgres(test: (PostgreSQLRepoEventStore) -> Unit) =
        withOwnPostgresDatabase("repo_events_") { url ->
            localTestPostgresDataSource(url).use { source ->
                val previous = TransactionManager.defaultDatabase
                try {
                    TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                    test(PostgreSQLRepoEventStore())
                } finally {
                    TransactionManager.defaultDatabase = previous
                }
            }
        }

    @Test
    fun `h2 numbers events from one without gaps`() = onH2(RepoEventStoreContract::numbersEventsFromOneWithoutGaps)

    @Test
    fun `h2 stores the frame built for the assigned number`() = onH2(RepoEventStoreContract::storesTheFrameBuiltForTheAssignedNumber)

    @Test
    fun `h2 reads events after a number up to a limit`() = onH2(RepoEventStoreContract::readsOnlyEventsAfterTheGivenNumberUpToTheLimit)

    @Test
    fun `h2 remembers the kind of each event`() = onH2(RepoEventStoreContract::remembersTheKindOfEachEvent)

    @Test
    fun `h2 reports the newest commit or sync revision`() = onH2(RepoEventStoreContract::reportsTheNewestCommitOrSyncRevisionOfARepository)

    @Test
    fun `h2 knows which repositories have events`() = onH2(RepoEventStoreContract::knowsWhichRepositoriesHaveEvents)

    @Test
    fun `h2 never reuses a number after deleting old events`() = onH2(RepoEventStoreContract::deletingOldEventsNeverReusesANumber)

    @Test
    fun `h2 keeps the latest number after deleting every event`() = onH2(RepoEventStoreContract::deletingEverythingKeepsTheLatestNumber)

    @Test
    fun `h2 remembers a repository after all its events are deleted`() =
        onH2(RepoEventStoreContract::remembersARepositoryAfterAllItsEventsAreDeleted)

    @Test
    fun `postgres remembers a repository after all its events are deleted`() =
        onPostgres(RepoEventStoreContract::remembersARepositoryAfterAllItsEventsAreDeleted)

    @Test
    fun `h2 gives concurrent appends distinct numbers without gaps`() =
        onH2(RepoEventStoreContract::concurrentAppendsGetDistinctNumbersWithoutGaps)

    @Test
    fun `postgres numbers events from one without gaps`() = onPostgres(RepoEventStoreContract::numbersEventsFromOneWithoutGaps)

    @Test
    fun `postgres stores the frame built for the assigned number`() =
        onPostgres(RepoEventStoreContract::storesTheFrameBuiltForTheAssignedNumber)

    @Test
    fun `postgres reads events after a number up to a limit`() =
        onPostgres(RepoEventStoreContract::readsOnlyEventsAfterTheGivenNumberUpToTheLimit)

    @Test
    fun `postgres remembers the kind of each event`() = onPostgres(RepoEventStoreContract::remembersTheKindOfEachEvent)

    @Test
    fun `postgres reports the newest commit or sync revision`() =
        onPostgres(RepoEventStoreContract::reportsTheNewestCommitOrSyncRevisionOfARepository)

    @Test
    fun `postgres knows which repositories have events`() = onPostgres(RepoEventStoreContract::knowsWhichRepositoriesHaveEvents)

    @Test
    fun `postgres never reuses a number after deleting old events`() =
        onPostgres(RepoEventStoreContract::deletingOldEventsNeverReusesANumber)

    @Test
    fun `postgres keeps the latest number after deleting every event`() =
        onPostgres(RepoEventStoreContract::deletingEverythingKeepsTheLatestNumber)

    @Test
    fun `postgres gives concurrent appends distinct numbers without gaps`() =
        onPostgres(RepoEventStoreContract::concurrentAppendsGetDistinctNumbersWithoutGaps)
}
