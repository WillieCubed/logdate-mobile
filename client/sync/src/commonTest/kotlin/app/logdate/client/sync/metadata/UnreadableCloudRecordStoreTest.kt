package app.logdate.client.sync.metadata

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class UnreadableCloudRecordStoreTest {
    @Test
    fun `recording the same id twice only counts it once`() =
        runTest {
            val store = InMemoryUnreadableCloudRecordStore()
            val id = Uuid.random()

            store.record(EntityType.NOTE, listOf(id))
            store.record(EntityType.NOTE, listOf(id))

            assertEquals(1, store.count())
        }

    @Test
    fun `clearing forgets every recorded id`() =
        runTest {
            val store = InMemoryUnreadableCloudRecordStore()
            store.record(EntityType.NOTE, listOf(Uuid.random(), Uuid.random()))

            store.clear()

            assertEquals(0, store.count())
        }

    @Test
    fun `the same id under different entity types counts separately`() =
        runTest {
            val store = InMemoryUnreadableCloudRecordStore()
            val id = Uuid.random()

            store.record(EntityType.NOTE, listOf(id))
            store.record(EntityType.JOURNAL, listOf(id))

            assertEquals(2, store.count())
        }
}
