package app.logdate.client.feature.widgets

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WidgetLifecycleCompletionTest {
    @Test
    fun `failed durable scheduling keeps the event and retry completes it in order`() =
        runTest {
            val order = mutableListOf<String>()
            var retained = true
            assertFailsWith<IOException> {
                completeWidgetLifecycle(
                    persistRefresh = { throw IOException("WorkManager persistence failed") },
                    dispatch = { order += "dispatch" },
                    acknowledge = { retained = false },
                )
            }
            assertTrue(retained)
            assertTrue(order.isEmpty())
            completeWidgetLifecycle(
                persistRefresh = { order += "persisted" },
                dispatch = { order += "dispatch" },
                acknowledge = {
                    order += "acknowledge"
                    retained = false
                },
            )
            assertEquals(listOf("persisted", "dispatch", "acknowledge"), order)
            assertEquals(false, retained)
        }

    @Test
    fun `missing runtime acknowledgement retains the event after refresh persistence`() =
        runTest {
            var retained = true
            var persisted = false
            assertFailsWith<IOException> {
                completeWidgetLifecycle(
                    persistRefresh = { persisted = true },
                    dispatch = { throw IOException("The runtime receiver did not acknowledge") },
                    acknowledge = { retained = false },
                )
            }
            assertTrue(persisted)
            assertTrue(retained)
        }
}
