package app.logdate.client.repository.journals

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class CaptureTimeZoneTest {
    private val created = Instant.fromEpochMilliseconds(1_710_000_000_000)

    private fun textNote(timeZoneId: String?) =
        JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Walk", timeZoneId = timeZoneId)

    @Test
    fun theSystemProviderReportsAZoneIdThisDeviceCanResolve() {
        val id = SystemCaptureTimeZone.currentTimeZoneId()

        assertNotNull(id)
        assertEquals(id, TimeZone.of(id).id)
    }

    @Test
    fun aRecordedZoneResolvesToATimeZone() {
        assertEquals(TimeZone.of("America/Denver"), textNote("America/Denver").captureTimeZoneOrNull())
    }

    @Test
    fun aNoteWithoutARecordedZoneHasNoCaptureZone() {
        assertNull(textNote(null).captureTimeZoneOrNull())
    }

    @Test
    fun aZoneIdThisDeviceDoesNotKnowIsTreatedAsUnknownInsteadOfFailing() {
        assertNull(textNote("Nowhere/Newly_Created").captureTimeZoneOrNull())
    }
}
