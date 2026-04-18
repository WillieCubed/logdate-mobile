package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Complication showing today's journal entry count.
 *
 * Tapping opens the LogDate app.
 */
class EntryCountComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData(
            count = 5,
            contentDescription = getString(R.string.wear_complication_entry_count_description),
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val count = fetchTodayEntryCount()
        return createComplicationData(
            count = count,
            contentDescription =
                resources.getQuantityString(
                    R.plurals.wear_complication_entry_count_full,
                    count,
                    count,
                ),
            tapIntent = createOpenAppIntent(),
        )
    }

    private suspend fun fetchTodayEntryCount(): Int =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            repository.observeNotesForDay(today).first().size
        } catch (e: Exception) {
            0
        }

    private fun createOpenAppIntent(): PendingIntent {
        val intent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent()
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createComplicationData(
        count: Int,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): ShortTextComplicationData {
        val builder =
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(count.toString()).build(),
                contentDescription = PlainComplicationText.Builder(contentDescription).build(),
            )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }
}
