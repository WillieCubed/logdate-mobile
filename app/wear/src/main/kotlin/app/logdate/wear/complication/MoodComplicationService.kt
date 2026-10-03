package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val NO_FEELING_TEXT = "—"

/**
 * What the Mood complication shows for [feeling]: the emoji the check-in screen showed for it, or a
 * dash on a day without a check-in.
 */
internal fun feelingComplicationText(feeling: Feeling?): String = feeling?.emoji ?: NO_FEELING_TEXT

/**
 * Complication showing the user's most recent mood for today.
 *
 * Displays the mood as a short emoji. Tapping opens the mood check-in screen.
 */
class MoodComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return createComplicationData(
            text = feelingComplicationText(Feeling.GOOD),
            contentDescription = getString(R.string.wear_complication_mood_description),
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val feeling = fetchTodayFeeling()
        val description =
            if (feeling != null) {
                getString(R.string.wear_complication_mood_current, getString(feeling.labelRes))
            } else {
                getString(R.string.wear_complication_mood_none)
            }
        return createComplicationData(
            text = feelingComplicationText(feeling),
            contentDescription = description,
            tapIntent = createMoodCheckInIntent(),
        )
    }

    private suspend fun fetchTodayFeeling(): Feeling? =
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
            MomentNoteCodec.latestFeeling(repository.observeNotesForDay(today).first())
        } catch (e: Exception) {
            Napier.e("Failed to read today's mood for the Wear complication", e)
            null
        }

    private fun createMoodCheckInIntent(): PendingIntent {
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(packageName, "app.logdate.wear.presentation.MainActivity")
                putExtra("tile_route", "mood")
            }
        return PendingIntent.getActivity(
            this,
            MOOD_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createComplicationData(
        text: String,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): ShortTextComplicationData {
        val builder =
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(text).build(),
                contentDescription = PlainComplicationText.Builder(contentDescription).build(),
            )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    companion object {
        private const val MOOD_REQUEST_CODE = 1001
    }
}
