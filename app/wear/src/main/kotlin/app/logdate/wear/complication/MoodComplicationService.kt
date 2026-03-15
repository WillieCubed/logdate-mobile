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
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Complication showing the user's most recent mood for today.
 *
 * Displays the mood as a short emoji. Tapping opens the mood check-in screen.
 */
class MoodComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return createComplicationData(
            moodEmoji = MOOD_EMOJI_GOOD,
            contentDescription = getString(R.string.wear_complication_mood_description),
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val mood = fetchTodayMood()
        val emoji = moodToEmoji(mood)
        val description = if (mood != null) {
            getString(R.string.wear_complication_mood_current, mood)
        } else {
            getString(R.string.wear_complication_mood_none)
        }
        return createComplicationData(
            moodEmoji = emoji,
            contentDescription = description,
            tapIntent = createMoodCheckInIntent(),
        )
    }

    private suspend fun fetchTodayMood(): String? {
        return try {
            val repository = org.koin.java.KoinJavaComponent
                .getKoin()
                .get<JournalNotesRepository>()
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            val notes = repository.observeNotesForDay(today).first()
            notes.filterIsInstance<JournalNote.Text>()
                .filter { it.content.startsWith("#mood:") }
                .maxByOrNull { it.creationTimestamp }
                ?.content
                ?.removePrefix("#mood:")
                ?.trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun createMoodCheckInIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
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
        moodEmoji: String,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): ShortTextComplicationData {
        val builder = ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(moodEmoji).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build(),
        )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    companion object {
        private const val MOOD_REQUEST_CODE = 1001

        private const val MOOD_EMOJI_GREAT = "\uD83D\uDE04"
        private const val MOOD_EMOJI_GOOD = "\uD83D\uDE42"
        private const val MOOD_EMOJI_OK = "\uD83D\uDE10"
        private const val MOOD_EMOJI_SAD = "\uD83D\uDE1E"
        private const val MOOD_EMOJI_ROUGH = "\uD83D\uDE23"
        private const val MOOD_EMOJI_NONE = "\u2014" // em dash

        fun moodToEmoji(mood: String?): String = when (mood) {
            "great" -> MOOD_EMOJI_GREAT
            "good" -> MOOD_EMOJI_GOOD
            "ok" -> MOOD_EMOJI_OK
            "sad" -> MOOD_EMOJI_SAD
            "rough" -> MOOD_EMOJI_ROUGH
            else -> MOOD_EMOJI_NONE
        }
    }
}
