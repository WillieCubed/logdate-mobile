package app.logdate.core.data

import app.logdate.model.LibraryItem
import kotlinx.coroutines.flow.Flow

interface LibraryContentRepository {
    val allItemsObserved: Flow<List<LibraryItem>>
}