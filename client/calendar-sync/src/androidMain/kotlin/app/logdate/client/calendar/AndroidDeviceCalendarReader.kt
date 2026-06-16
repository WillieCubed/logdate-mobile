package app.logdate.client.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * [DeviceCalendarReader] backed by Android's `CalendarContract` content provider.
 *
 * The provider exposes whatever calendars the OS knows about — Google Calendar, Samsung,
 * Outlook, locally-defined calendars, anything that has a `CalendarContract.Calendars`
 * row. We never write back; the import worker mirrors a snapshot into LogDate's own event
 * store and the user's actual calendars stay untouched.
 *
 * All content-provider queries hop onto [ioDispatcher] so the worker thread doesn't block
 * on disk I/O while iterating cursors.
 */
class AndroidDeviceCalendarReader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceCalendarReader {
    override suspend fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    override suspend fun listCalendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        return withContext(ioDispatcher) {
            val projection =
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.IS_PRIMARY,
                )
            val results = mutableListOf<DeviceCalendar>()
            runCatching {
                context.contentResolver
                    .query(
                        CalendarContract.Calendars.CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                        val accountNameColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                        val accountTypeColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                        val colorColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                        val primaryColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
                        while (cursor.moveToNext()) {
                            results +=
                                DeviceCalendar(
                                    id = cursor.getLong(idColumn).toString(),
                                    displayName = cursor.getString(nameColumn) ?: "(unnamed calendar)",
                                    accountName = cursor.getString(accountNameColumn) ?: "Local",
                                    accountType = cursor.getString(accountTypeColumn) ?: "local",
                                    color = if (cursor.isNull(colorColumn)) null else cursor.getInt(colorColumn).toLong(),
                                    isPrimary = cursor.getInt(primaryColumn) == 1,
                                )
                        }
                    }
            }.onFailure { error -> Napier.w("Failed to list device calendars", error) }
            results
        }
    }

    override suspend fun readEvents(
        calendarIds: Set<String>,
        start: Instant,
        end: Instant,
    ): List<DeviceCalendarEvent> {
        if (calendarIds.isEmpty() || !hasPermission()) return emptyList()
        return withContext(ioDispatcher) {
            // Use the Instances table so recurring events are expanded into their concrete
            // occurrences in the requested window — querying Events directly would only
            // return the recurrence rule, not the individual dates.
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, start.toEpochMilliseconds())
            ContentUris.appendId(builder, end.toEpochMilliseconds())
            val uri = builder.build()
            val projection =
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_LOCATION,
                )
            val placeholders = calendarIds.joinToString(",") { "?" }
            val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            val selectionArgs = calendarIds.toTypedArray()

            val accountNameByCalendarId = listCalendars().associate { it.id to it.accountName }

            val events = mutableListOf<DeviceCalendarEvent>()
            runCatching {
                context.contentResolver
                    .query(uri, projection, selection, selectionArgs, null)
                    ?.use { cursor ->
                        val eventIdColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                        val calendarIdColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                        val titleColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                        val descriptionColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                        val beginColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                        val endColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                        val allDayColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                        val locationColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                        while (cursor.moveToNext()) {
                            val eventId = cursor.getLong(eventIdColumn).toString()
                            val calendarId = cursor.getLong(calendarIdColumn).toString()
                            val begin = cursor.getLong(beginColumn)
                            val rawEnd = cursor.getLong(endColumn)
                            // CalendarContract encodes all-day instances at UTC midnight of the
                            // date, which is already the canonical anchor LogDate stores, so the
                            // raw BEGIN/END pass through unchanged — only the flag is new.
                            events +=
                                DeviceCalendarEvent(
                                    externalId = eventId,
                                    calendarId = calendarId,
                                    accountName = accountNameByCalendarId[calendarId] ?: "Local",
                                    title = cursor.getString(titleColumn) ?: "(untitled)",
                                    description = cursor.getString(descriptionColumn),
                                    startTime = Instant.fromEpochMilliseconds(begin),
                                    endTime = if (rawEnd <= 0L) null else Instant.fromEpochMilliseconds(rawEnd),
                                    isAllDay = cursor.getInt(allDayColumn) == 1,
                                    placeName = cursor.getString(locationColumn),
                                )
                        }
                    }
            }.onFailure { error -> Napier.w("Failed to read device calendar events", error) }
            events
        }
    }
}
