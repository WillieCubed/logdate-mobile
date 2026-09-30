package app.logdate.client.location.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationCaptureStatusTrackerTest {
    @Test
    fun `capture is running only after the subscription succeeds`() {
        val tracker = LocationCaptureStatusTracker()
        assertEquals(LocationCaptureStatus.Stopped, tracker.status.value)
        val request = tracker.beginRequest()
        assertEquals(LocationCaptureStatus.Starting, tracker.status.value)
        assertTrue(tracker.completeRequest(request, succeeded = true))
        assertEquals(LocationCaptureStatus.Running, tracker.status.value)
    }

    @Test
    fun `failed subscription can be explicitly retried`() {
        val tracker = LocationCaptureStatusTracker()
        tracker.completeRequest(tracker.beginRequest(), succeeded = false)
        assertEquals(LocationCaptureStatus.Failed, tracker.status.value)
        val retry = tracker.beginRequest()
        assertEquals(LocationCaptureStatus.Starting, tracker.status.value)
        tracker.completeRequest(retry, succeeded = true)
        assertEquals(LocationCaptureStatus.Running, tracker.status.value)
    }

    @Test
    fun `stop invalidates a late successful registration`() {
        val tracker = LocationCaptureStatusTracker()
        val request = tracker.beginRequest()
        tracker.stop()
        assertFalse(tracker.completeRequest(request, succeeded = true))
        assertEquals(LocationCaptureStatus.Stopped, tracker.status.value)
    }

    @Test
    fun `superseded profile request cannot change the current subscription status`() {
        val tracker = LocationCaptureStatusTracker()
        val old = tracker.beginRequest()
        val current = tracker.beginRequest()
        assertFalse(tracker.completeRequest(old, succeeded = true))
        assertEquals(LocationCaptureStatus.Starting, tracker.status.value)
        assertTrue(tracker.completeRequest(current, succeeded = true))
        assertFalse(tracker.completeRequest(old, succeeded = false))
        assertEquals(LocationCaptureStatus.Running, tracker.status.value)
        tracker.stop()
        assertEquals(LocationCaptureStatus.Stopped, tracker.status.value)
    }
}
