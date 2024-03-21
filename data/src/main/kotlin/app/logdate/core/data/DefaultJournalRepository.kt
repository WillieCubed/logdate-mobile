package app.logdate.core.data

import app.logdate.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject

class DefaultJournalRepository @Inject constructor(): JournalRepository {

    private val allItems: MutableStateFlow<List<Journal>> = MutableStateFlow(
        TEST_JOURNALS
    )

    override val allJournalsObserved: Flow<List<Journal>>
        get() = allItems

    override suspend fun create(journal: Journal) {
        TODO("Not yet implemented")
    }

    override suspend fun delete(journalId: String) {
        TODO("Not yet implemented")
    }

    override fun observeJournalById(id: String): Flow<Journal> =
        allJournalsObserved.map { journals ->
            journals.firstOrNull { model -> model.id == id }
                ?: throw NoSuchElementException("$id not found")
        }
}

val TEST_JOURNALS = listOf(
    Journal(
        id = "journal-1",
        title = "Diary",
        description = "Description",
        created = Clock.System.now(),
        isFavorited = false,
        lastUpdated = Clock.System.now(),
    ),
    Journal(
        id = "journal-2",
        title = "Climbing Buddies",
        description = "Description",
        created = Clock.System.now(),
        isFavorited = false,
        lastUpdated = Clock.System.now(),
    ),
    Journal(
        id = "journal-3",
        title = "Burn Book",
        description = "Description",
        created = Clock.System.now(),
        isFavorited = false,
        lastUpdated = Clock.System.now(),
    ),
    Journal(
        id = "journal-4",
        title = "Family Moments",
        description = "Description",
        created = Clock.System.now(),
        isFavorited = false,
        lastUpdated = Clock.System.now(),
    ),
    Journal(
        id = "journal-5",
        title = "Student Government",
        description = "Description",
        created = Clock.System.now(),
        isFavorited = false,
        lastUpdated = Clock.System.now(),
    ),
    Journal(
        id = "journal-6",
        title = "Grad Student Life",
        description = "Description",
        created = Clock.System.now(),
        isFavorited = false,
        lastUpdated = Clock.System.now(),
    ),
)