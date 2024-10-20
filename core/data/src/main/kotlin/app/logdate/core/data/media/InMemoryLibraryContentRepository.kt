package app.logdate.core.data.media

import app.logdate.model.LibraryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class InMemoryLibraryContentRepository @Inject constructor() : LibraryContentRepository {

    private val allItems: MutableStateFlow<List<LibraryItem>> = MutableStateFlow(
        TEST_ITEMS
    )

    override val allItemsObserved: Flow<List<LibraryItem>>
        get() = allItems
}

val TEST_ITEMS = listOf<LibraryItem>()