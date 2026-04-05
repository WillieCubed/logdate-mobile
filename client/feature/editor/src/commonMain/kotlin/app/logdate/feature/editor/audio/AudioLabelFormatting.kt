package app.logdate.feature.editor.audio

import androidx.compose.runtime.Composable
import app.logdate.feature.editor.audio.model.DaylightPeriod
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.audio_label_period_at_location
import logdate.client.feature.editor.generated.resources.audio_label_period_recording
import logdate.client.feature.editor.generated.resources.daylight_period_afternoon
import logdate.client.feature.editor.generated.resources.daylight_period_dawn
import logdate.client.feature.editor.generated.resources.daylight_period_evening
import logdate.client.feature.editor.generated.resources.daylight_period_golden_hour
import logdate.client.feature.editor.generated.resources.daylight_period_midday
import logdate.client.feature.editor.generated.resources.daylight_period_morning
import logdate.client.feature.editor.generated.resources.daylight_period_night
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Formats an [AudioLabelResult] into a localized title string using Compose string resources.
 *
 * Use in composable contexts.
 */
@Composable
fun formatAudioLabel(result: AudioLabelResult): String =
    when (result) {
        is AudioLabelResult.Caption -> result.text
        is AudioLabelResult.Contextual -> {
            val periodName = stringResource(result.period.stringRes)
            if (result.locationName != null) {
                stringResource(Res.string.audio_label_period_at_location, periodName, result.locationName)
            } else {
                stringResource(Res.string.audio_label_period_recording, periodName)
            }
        }
    }

/**
 * Formats an [AudioLabelResult] into a localized title string.
 *
 * Use in non-composable suspend contexts (e.g., ViewModels).
 */
suspend fun formatAudioLabelAsync(result: AudioLabelResult): String =
    when (result) {
        is AudioLabelResult.Caption -> result.text
        is AudioLabelResult.Contextual -> {
            val periodName = getString(result.period.stringRes)
            if (result.locationName != null) {
                getString(Res.string.audio_label_period_at_location, periodName, result.locationName)
            } else {
                getString(Res.string.audio_label_period_recording, periodName)
            }
        }
    }

/**
 * Maps a [DaylightPeriod] to its Compose string resource.
 */
val DaylightPeriod.stringRes: StringResource
    get() =
        when (this) {
            DaylightPeriod.DAWN -> Res.string.daylight_period_dawn
            DaylightPeriod.MORNING -> Res.string.daylight_period_morning
            DaylightPeriod.MIDDAY -> Res.string.daylight_period_midday
            DaylightPeriod.AFTERNOON -> Res.string.daylight_period_afternoon
            DaylightPeriod.GOLDEN_HOUR -> Res.string.daylight_period_golden_hour
            DaylightPeriod.EVENING -> Res.string.daylight_period_evening
            DaylightPeriod.NIGHT -> Res.string.daylight_period_night
        }
