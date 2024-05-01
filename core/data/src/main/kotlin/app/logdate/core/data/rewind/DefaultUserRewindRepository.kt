package app.logdate.core.data.rewind

import app.logdate.model.Rewind
import app.logdate.util.weekOfYear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

class DefaultUserRewindRepository @Inject constructor() : RewindRepository {

    private val allItems: MutableStateFlow<List<Rewind>> = MutableStateFlow(
        listOf()
    )

    override val allItemsObserved: Flow<List<Rewind>> = allItems
    override fun observeMostRecentRewind(): Flow<Rewind> {
        // TODO: Ensure this never throws
        // Generate dummy rewind for current week just so this is never null
        return allItems.map { items ->
            val first = items.firstOrNull()
            return@map first ?: generateTempRewind()
            // TODO: Check if the rewind is ready
        }
    }

    override fun observeModelById(id: String): Flow<Rewind> = allItemsObserved.map { items ->
        items.firstOrNull { model -> model.uid == id }
            ?: throw NoSuchElementException("$id not found")
    }

    private fun generateTempRewind(): Rewind {
        val year: Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val weekOfYear: Int = Clock.System.now().weekOfYear
        val id = "$year#${weekOfYear.toString().padStart(2, '0')}"
        val label = "Rewind $id"
        return Rewind(
            uid = "temp",
            date = Clock.System.now(),
            title = "Just another week",
            label = label
        )
    }
}
