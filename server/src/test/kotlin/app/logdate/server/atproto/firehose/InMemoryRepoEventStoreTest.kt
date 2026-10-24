package app.logdate.server.atproto.firehose

import kotlin.test.Test

/** The event log held in memory, which servers without a database and most tests use. */
class InMemoryRepoEventStoreTest {
    private fun store() = InMemoryRepoEventStore()

    @Test
    fun `events are numbered from one without gaps`() = RepoEventStoreContract.numbersEventsFromOneWithoutGaps(store())

    @Test
    fun `the frame is built for the number the store assigned`() = RepoEventStoreContract.storesTheFrameBuiltForTheAssignedNumber(store())

    @Test
    fun `reading returns events after a number up to a limit`() =
        RepoEventStoreContract.readsOnlyEventsAfterTheGivenNumberUpToTheLimit(store())

    @Test
    fun `the kind of each event is remembered`() = RepoEventStoreContract.remembersTheKindOfEachEvent(store())

    @Test
    fun `the newest commit or sync revision of a repository is reported`() =
        RepoEventStoreContract.reportsTheNewestCommitOrSyncRevisionOfARepository(store())

    @Test
    fun `a repository with any event is known`() = RepoEventStoreContract.knowsWhichRepositoriesHaveEvents(store())

    @Test
    fun `deleting old events never reuses a number`() = RepoEventStoreContract.deletingOldEventsNeverReusesANumber(store())

    @Test
    fun `deleting every event keeps the latest number`() = RepoEventStoreContract.deletingEverythingKeepsTheLatestNumber(store())

    @Test
    fun `a repository stays known after all its events are deleted`() =
        RepoEventStoreContract.remembersARepositoryAfterAllItsEventsAreDeleted(store())

    @Test
    fun `concurrent appends get distinct numbers without gaps`() =
        RepoEventStoreContract.concurrentAppendsGetDistinctNumbersWithoutGaps(store())
}
