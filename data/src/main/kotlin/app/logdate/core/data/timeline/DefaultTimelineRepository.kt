package app.logdate.core.data.timeline

import app.logdate.model.TimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultTimelineRepository @Inject constructor() : TimelineRepository {

    private val allItems: MutableStateFlow<List<TimelineItem>> = MutableStateFlow(
        listOf()
    )

    override val allItemsObserved: Flow<List<TimelineItem>> = allItems

    override fun observeModelById(id: String): Flow<TimelineItem> = allItemsObserved.map { items ->
        items.firstOrNull { model -> model.uid == id }
            ?: throw NoSuchElementException("$id not found")
    }
}
