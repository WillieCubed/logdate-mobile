package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.NavKey
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.journals.ui.detail.FakeDetailJournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class JournalDetailRedirectTest {
    @Test
    fun `redirect replaces its source detail without popping unrelated top routes`() {
        val source = Uuid.random()
        val destination = Uuid.random()
        val unrelated = JournalSettingsRoute(Uuid.random())
        val stack = mutableListOf<NavKey>(JournalsOverviewRoute, JournalDetailsRoute(source), unrelated)
        stack.redirectJournalDetail(source, destination)
        assertEquals(listOf<NavKey>(JournalsOverviewRoute, JournalDetailsRoute(destination), unrelated), stack)
        stack.redirectJournalDetail(source, Uuid.random())
        assertEquals(listOf<NavKey>(JournalsOverviewRoute, JournalDetailsRoute(destination), unrelated), stack)
    }

    @Test
    fun `an open source detail follows a remote merge without reopening`() =
        runTest {
            val source = Journal(title = "Travel")
            val destination = Journal(title = "Archive")
            val journals = MutableStateFlow(listOf(source, destination))
            var canonical = source.id
            val repository =
                object : JournalRepository by FakeDetailJournalRepository() {
                    override val allJournalsObserved = journals

                    override suspend fun resolveJournalId(journalId: Uuid) = canonical
                }
            val observed = mutableListOf<Uuid?>()
            val collector = launch { repository.observeJournalRouteDestination(source.id).collect(observed::add) }
            advanceUntilIdle()
            canonical = destination.id
            journals.value = listOf(destination)
            advanceUntilIdle()
            assertEquals(listOf<Uuid?>(source.id, destination.id), observed)
            collector.cancel()
        }

    @Test
    fun `a redirect waits for its destination to download before navigating`() =
        runTest {
            val source = Journal(title = "Travel")
            val destination = Journal(title = "Archive")
            val journals = MutableStateFlow(listOf(source))
            var canonical = source.id
            val repository =
                object : JournalRepository by FakeDetailJournalRepository() {
                    override val allJournalsObserved = journals

                    override suspend fun resolveJournalId(journalId: Uuid) = canonical
                }
            val observed = mutableListOf<Uuid?>()
            val collector = launch { repository.observeJournalRouteDestination(source.id).collect(observed::add) }
            advanceUntilIdle()
            canonical = destination.id
            journals.value = emptyList()
            advanceUntilIdle()
            assertEquals(listOf<Uuid?>(source.id, null), observed)
            journals.value = listOf(destination)
            advanceUntilIdle()
            assertEquals(listOf<Uuid?>(source.id, null, destination.id), observed)
            collector.cancel()
        }
}
