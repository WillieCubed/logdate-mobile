package app.logdate.client.repository.journals

import kotlinx.datetime.TimeZone

/**
 * Supplies the time zone to record on a note at the moment it is captured.
 *
 * Capture sites take this as a dependency so tests can pin the zone and so a site never has to
 * read the system zone itself.
 */
fun interface CaptureTimeZoneProvider {
    /** IANA id of the zone the device is in right now, or null when it cannot be determined. */
    fun currentTimeZoneId(): String?
}

/** Reads the zone the device is set to. */
object SystemCaptureTimeZone : CaptureTimeZoneProvider {
    override fun currentTimeZoneId(): String? = runCatching { TimeZone.currentSystemDefault().id }.getOrNull()
}

/**
 * The zone this note was captured in, or null when none was recorded or this device's tz data does
 * not know the recorded id (a zone added after this build shipped).
 */
fun JournalNote.captureTimeZoneOrNull(): TimeZone? = timeZoneId?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() }
