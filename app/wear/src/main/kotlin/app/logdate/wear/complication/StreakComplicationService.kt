package app.logdate.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.streak.CalculateStreakUseCase
import app.logdate.client.domain.streak.CampfireCalculator
import app.logdate.client.domain.streak.CampfireState
import app.logdate.client.domain.streak.FirePhase
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Complication showing the user's current journaling streak.
 *
 * Displays the count of consecutive days with at least one journal entry.
 * Supports SHORT_TEXT ("7d") and LONG_TEXT ("7 day streak") formats.
 */
class StreakComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                createShortText(
                    streak = 7,
                    contentDescription = getString(R.string.wear_complication_streak_description),
                )
            ComplicationType.LONG_TEXT ->
                createLongText(
                    streak = 7,
                    contentDescription = getString(R.string.wear_complication_streak_description),
                )
            else -> null
        }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (isCampfireEnabled()) {
            val campfire = calculateCampfire() ?: return NoDataComplicationData()
            return createCampfireData(
                content = campfire.toComplicationContent(),
                type = request.complicationType,
                tapIntent = createOpenAppIntent(),
            )
        }

        val streak = calculateStreak() ?: return NoDataComplicationData()
        val description =
            resources.getQuantityString(
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

    /**
     * The watch's Koin graph provides the data layer but not the domain use cases, so the
     * calculator is built here from the repository instead of being resolved.
     */
    private suspend fun calculateStreak(): Int? =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            CalculateStreakUseCase(repository)()
        } catch (e: Exception) {
            Napier.e("Failed to calculate streak for the Wear complication", e)
            null
        }

    private suspend fun isCampfireEnabled(): Boolean =
        try {
            org.koin.java.KoinJavaComponent
                .getKoin()
                .get<FeatureFlagStore>()
                .isEnabled(FeatureFlag.CAMPFIRE_STREAKS)
        } catch (e: Exception) {
            Napier.e("Failed to read the campfire streak flag for the Wear complication", e)
            false
        }

    /**
     * Uses the default 4 AM day start: the phone's day-start preference is not synced to the watch.
     */
    private suspend fun calculateCampfire(): CampfireState? =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            val zone = TimeZone.currentSystemDefault()
            val loggedDays = repository.observeEntryTimestamps().first().mapTo(HashSet()) { it.toStreakDay(zone) }
            CampfireCalculator.calculate(loggedDays = loggedDays, today = Clock.System.now().toStreakDay(zone))
        } catch (e: Exception) {
            Napier.e("Failed to calculate the campfire for the Wear complication", e)
            null
        }

    private fun createCampfireData(
        content: CampfireComplicationContent,
        type: ComplicationType,
        tapIntent: PendingIntent,
    ): ComplicationData {
        val description = campfireDescription(content)
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, content.iconRes)).build()
        return when (type) {
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData
                    .Builder(
                        text = PlainComplicationText.Builder(description).build(),
                        contentDescription = PlainComplicationText.Builder(description).build(),
                    ).setMonochromaticImage(icon)
                    .setTapAction(tapIntent)
                    .build()
            else ->
                ShortTextComplicationData
                    .Builder(
                        text = PlainComplicationText.Builder(content.runDays?.toString() ?: NO_FIRE_TEXT).build(),
                        contentDescription = PlainComplicationText.Builder(description).build(),
                    ).setMonochromaticImage(icon)
                    .setTapAction(tapIntent)
                    .build()
        }
    }

    private fun campfireDescription(content: CampfireComplicationContent): String {
        val runDays = content.runDays ?: 0
        return when (content.phase) {
            FirePhase.BURNING -> resources.getQuantityString(R.plurals.wear_complication_campfire_burning, runDays, runDays)
            FirePhase.EMBERS -> getString(R.string.wear_complication_campfire_embers, runDays)
            FirePhase.OUT -> getString(R.string.wear_complication_campfire_out)
            FirePhase.UNLIT -> getString(R.string.wear_complication_campfire_unlit)
        }
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent =
            packageManager.getLaunchIntentForPackage(packageName)
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
        val builder =
            ShortTextComplicationData.Builder(
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
        val label =
            resources.getQuantityString(
                R.plurals.wear_complication_streak_full,
                streak,
                streak,
            )
        val builder =
            LongTextComplicationData.Builder(
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
        private const val NO_FIRE_TEXT = "–"
    }
}
