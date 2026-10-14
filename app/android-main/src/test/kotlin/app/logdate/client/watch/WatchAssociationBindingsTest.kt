package app.logdate.client.watch

import app.logdate.client.datastore.KeyValueStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchAssociationBindingsTest {
    @Test
    fun `consented ID survives recreation and removal preserves other watch`() =
        runTest {
            var value: String? = null
            val storage = mockk<KeyValueStorage>()
            every { storage.getStringSync(any()) } answers { value }
            coEvery { storage.putString(any(), any()) } coAnswers { value = secondArg() }
            val bindings = WatchAssociationBindings(storage)
            bindings.bind(WatchAssociationBinding(42, "watch-a", "A"))
            bindings.bind(WatchAssociationBinding(81, "watch-b", "B"))
            val recreated = WatchAssociationBindings(storage)
            assertEquals(42, recreated.current()["watch-a"]?.associationId)
            recreated.forget("watch-a")
            assertEquals(setOf("watch-b"), recreated.current().keys)
            assertEquals(81, recreated.current()["watch-b"]?.associationId)
        }

    @Test
    fun `one system association cannot authorize two nodes`() =
        runTest {
            var value: String? = null
            val storage = mockk<KeyValueStorage>()
            every { storage.getStringSync(any()) } answers { value }
            coEvery { storage.putString(any(), any()) } coAnswers { value = secondArg() }
            val bindings = WatchAssociationBindings(storage)
            bindings.bind(WatchAssociationBinding(42, "watch-a", "A"))
            bindings.bind(WatchAssociationBinding(42, "watch-b", "B"))
            assertEquals(setOf("watch-b"), bindings.current().keys)
        }
}
