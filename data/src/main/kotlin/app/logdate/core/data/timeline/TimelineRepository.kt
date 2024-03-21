package app.logdate.core.data.timeline

import app.logdate.model.TimelineItem
import kotlinx.coroutines.flow.Flow

interface TimelineRepository {
    val allItemsObserved: Flow<List<TimelineItem>>

    fun observeModelById(id: String): Flow<TimelineItem>
}
