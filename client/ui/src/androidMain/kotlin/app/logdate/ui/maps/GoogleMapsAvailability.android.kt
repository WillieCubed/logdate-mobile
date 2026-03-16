package app.logdate.ui.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalGoogleMapsAvailabilityOverride = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun rememberGoogleMapsEnabled(): Boolean {
    val override = LocalGoogleMapsAvailabilityOverride.current
    val context = LocalContext.current

    return override ?: remember(context) {
        val resourceId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
        resourceId != 0 && runCatching { context.getString(resourceId) }.getOrDefault("").isNotBlank()
    }
}
