package app.logdate.client.device.restore

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeRestoreDeliveryCompletionTest {
    @Test
    fun completion_is_published_after_callbacks_and_mutex_release() {
        val before = nativeRestoreDeliveryCompletions().value
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        assertEquals(before, nativeRestoreDeliveryCompletions().value)
        AndroidNativeRestoreDeliveryGate.endRestoreCallback()
        assertEquals(before, nativeRestoreDeliveryCompletions().value)
        AndroidNativeRestoreDeliveryGate.beginRestoreCompletion()
        AndroidNativeRestoreDeliveryGate.endRestore()
        assertEquals(before + 1, nativeRestoreDeliveryCompletions().value)
        assertTrue(runBlocking { AndroidNativeRestoreDeliveryGate.withLock { true } })
    }

    @Test
    fun duplicate_end_has_no_event_and_next_batch_has_exactly_one_new_event() {
        val before = nativeRestoreDeliveryCompletions().value
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        AndroidNativeRestoreDeliveryGate.endRestoreCallback()
        AndroidNativeRestoreDeliveryGate.endRestore()
        AndroidNativeRestoreDeliveryGate.endRestore()
        assertEquals(before + 1, nativeRestoreDeliveryCompletions().value)
        AndroidNativeRestoreDeliveryGate.beginRestoreCallback()
        AndroidNativeRestoreDeliveryGate.endRestoreCallback()
        AndroidNativeRestoreDeliveryGate.endRestore()
        assertEquals(before + 2, nativeRestoreDeliveryCompletions().value)
    }
}
