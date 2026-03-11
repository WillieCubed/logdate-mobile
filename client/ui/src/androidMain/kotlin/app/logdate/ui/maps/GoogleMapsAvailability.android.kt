package app.logdate.ui.maps

import android.content.pm.PackageManager
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
        context.readGoogleMapsApiKey().isNotBlank()
    }
}

private fun android.content.Context.readGoogleMapsApiKey(): String =
    runCatching {
        val metaData =
            packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData

        val resourceId = metaData?.getInt("com.google.android.geo.API_KEY") ?: 0
        when {
            resourceId != 0 -> readStringResource(resourceId)
            else -> metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        }
    }.getOrElse {
        readStringResource("google_api_key")
    }.ifBlank {
        readStringResource("google_api_key")
    }

private fun android.content.Context.readStringResource(name: String): String {
    val resourceId = resources.getIdentifier(name, "string", packageName)
    return readStringResource(resourceId)
}

private fun android.content.Context.readStringResource(resourceId: Int): String =
    if (resourceId == 0) {
        ""
    } else {
        runCatching {
            getString(resourceId)
        }.getOrDefault("")
    }
