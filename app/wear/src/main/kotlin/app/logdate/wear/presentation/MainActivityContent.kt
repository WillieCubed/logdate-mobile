package app.logdate.wear.presentation

import androidx.compose.runtime.Composable
import app.logdate.wear.launch.WearExperience
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.v2.WearAppV2Placeholder
import kotlinx.coroutines.flow.Flow

/** What [MainActivity] shows: nothing while the splash screen decides, then the app [experience] names. */
@Composable
internal fun MainActivityContent(
    experience: WearExperience?,
    initialRequest: WearLaunchRequest?,
    newRequests: Flow<WearLaunchRequest>,
) {
    when (experience) {
        null -> Unit
        WearExperience.V1 -> WearApp(initialRequest = initialRequest, newRequests = newRequests)
        WearExperience.V2 -> WearAppV2Placeholder()
    }
}
