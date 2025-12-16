package app.logdate.client.data.timeline

import app.logdate.client.repository.timeline.ActivityTimelineRepository
import app.logdate.shared.model.ActivityTimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OfflineFirstActivityTimelineRepository : ActivityTimelineRepository {

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
        val currentItems = allItems.value.toMutableList()
        currentItems.add(item)
        allItems.value = currentItems.sortedByDescending { it.timestamp }
    }

    override suspend fun removeActivity(item: ActivityTimelineItem) {
        val currentItems = allItems.value.toMutableList()
        currentItems.removeAll { it.uid == item.uid }
        allItems.value = currentItems
    }

    override suspend fun updateActivity(item: ActivityTimelineItem) {
        val currentItems = allItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.uid == item.uid }
        if (index != -1) {
            currentItems[index] = item
            allItems.value = currentItems.sortedByDescending { it.timestamp }
        } else {
            throw NoSuchElementException("Activity with id ${item.uid} not found")
        }
    }

    override fun fetchActivitiesByType(type: String): Flow<List<ActivityTimelineItem>> {
        return allItemsObserved.map { items ->
            items.filter { item ->
                // For now, return all items as we don't have type classification
                // This could be enhanced later to filter by activity type
                true
            }
        }
    }
}
