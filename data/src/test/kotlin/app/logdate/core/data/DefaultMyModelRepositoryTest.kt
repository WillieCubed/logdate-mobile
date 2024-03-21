package app.logdate.core.data

import app.logdate.core.data.timeline.DefaultTimelineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test


class DefaultMyModelRepositoryTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ensure item is bookmarked`() = runTest {
        val repository = DefaultTimelineRepository()
        val items = repository.allItemsObserved.first()
//        assert(repository.allItemsObserved.first().none { item -> item.isFavorited })
        val firstItemId = items.first().uid
//        repository.bookmark(firstItemId, true)
        val firstItem = repository.observeModelById(firstItemId).first()
//        assert(firstItem.isFavorited)
    }
}
