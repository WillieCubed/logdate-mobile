package app.logdate.ui.provenance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import app.logdate.client.repository.journals.CaptureActivity
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.captured_in_vehicle
import logdate.client.ui.generated.resources.captured_while_moving
import org.jetbrains.compose.resources.stringResource

/**
 * Whether moments show what the person was doing when they captured them.
 *
 * Off by default. The navigation root turns it on with activity awareness in Labs, so leaf composables
 * and screenshot scenes never read feature flags themselves.
 */
val LocalActivityContextVisible: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** "While moving" or "In a vehicle" for [activity], or null when there is none or activity context is off. */
@Composable
fun captureActivityText(activity: CaptureActivity?): String? {
    if (activity == null || !LocalActivityContextVisible.current) return null
    return when (activity) {
        CaptureActivity.MOVING -> stringResource(Res.string.captured_while_moving)
        CaptureActivity.IN_VEHICLE -> stringResource(Res.string.captured_in_vehicle)
    }
}
