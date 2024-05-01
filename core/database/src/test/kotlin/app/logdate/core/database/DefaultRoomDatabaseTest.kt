package app.logdate.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Test


class DefaultRoomDatabaseTest {

    @Test
    fun `ensure timeline item is added`() = runTest {
//        val repository = DefaultTimelineRepository()
//        val items = repository.allItemsObserved.first()
//        assert(repository.allItemsObserved.first().none { item -> item.isFavorited })
//        val firstItemId = items.first().id
//        repository.bookmark(firstItemId, true)
//        val firstItem = repository.observeModelById(firstItemId).first()
//        assert(firstItem.isFavorited)
    }

    @Test
    fun `ensure timeline item is removed`() = runTest {  }
}
