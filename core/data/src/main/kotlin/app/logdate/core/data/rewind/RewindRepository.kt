package app.logdate.core.data.rewind

import app.logdate.model.Rewind
import kotlinx.coroutines.flow.Flow

interface RewindRepository {
    val allItemsObserved: Flow<List<Rewind>>

    fun observeMostRecentRewind(): Flow<Rewind>

    fun observeModelById(id: String): Flow<Rewind>
}