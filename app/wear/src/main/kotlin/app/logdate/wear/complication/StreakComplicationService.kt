package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.client.domain.streak.CalculateStreakUseCase
import app.logdate.wear.R

/**
 * Complication showing the user's current journaling streak.
 *
 * Displays the count of consecutive days with at least one journal entry.
 * Supports SHORT_TEXT ("7d") and LONG_TEXT ("7 day streak") formats.
 */
class StreakComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> createShortText(
                streak = 7,
                contentDescription = getString(R.string.wear_complication_streak_description),
            )
            ComplicationType.LONG_TEXT -> createLongText(
                streak = 7,
                contentDescription = getString(R.string.wear_complication_streak_description),
            )
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val streak = calculateStreak()
        val description = resources.getQuantityString(
            R.plurals.wear_complication_streak_full,
            streak,
            streak,
        )
        val tapIntent = createOpenAppIntent()
        return when (request.complicationType) {
            ComplicationType.LONG_TEXT -> createLongText(streak, description, tapIntent)
            else -> createShortText(streak, description, tapIntent)
        }
    }

    private suspend fun calculateStreak(): Int {
        return try {
            val calculateStreakUseCase = org.koin.java.KoinJavaComponent
                .getKoin()
                .get<CalculateStreakUseCase>()
            calculateStreakUseCase()
        } catch (e: Exception) {
            0
        }
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            this,
            STREAK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createShortText(
        streak: Int,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): ShortTextComplicationData {
        val label = getString(R.string.wear_complication_streak_short, streak)
        val builder = ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(label).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build(),
        )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    private fun createLongText(
        streak: Int,
        contentDescription: String,
        tapIntent: PendingIntent? = null,
    ): LongTextComplicationData {
        val label = resources.getQuantityString(
            R.plurals.wear_complication_streak_full,
            streak,
            streak,
        )
        val builder = LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(label).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build(),
        )
        if (tapIntent != null) {
            builder.setTapAction(tapIntent)
        }
        return builder.build()
    }

    companion object {
        private const val STREAK_REQUEST_CODE = 1002
    }
}
