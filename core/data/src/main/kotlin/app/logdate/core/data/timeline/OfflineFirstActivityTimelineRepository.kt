package app.logdate.core.data.timeline

import app.logdate.model.ActivityTimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OfflineFirstActivityTimelineRepository @Inject constructor() : ActivityTimelineRepository {

    private val allItems: MutableStateFlow<List<ActivityTimelineItem>> = MutableStateFlow(
        listOf()
    )

    override val allItemsObserved: Flow<List<ActivityTimelineItem>> = allItems

    override fun observeModelById(id: Uuid): Flow<ActivityTimelineItem> =
        allItemsObserved.map { items ->
            items.firstOrNull { model -> model.uid == id }
                ?: throw NoSuchElementException("$id not found")
        }

    override suspend fun addActivity(item: ActivityTimelineItem) {
        TODO("Not yet implemented")
    }

    override suspend fun removeActivity(item: ActivityTimelineItem) {
        TODO("Not yet implemented")
    }

    override suspend fun updateActivity(item: ActivityTimelineItem) {
        TODO("Not yet implemented")
    }

    override fun fetchActivitiesByType(type: String): Flow<List<ActivityTimelineItem>> {
        TODO("Not yet implemented")
    }
}
