package app.logdate.ui.media

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.logdate.client.media.device.MediaDeviceKind

@Composable
actual fun rememberMediaRouteSettingsAction(kind: MediaDeviceKind): MediaRouteSettingsAction? {
    val context = LocalContext.current
    return remember(context, kind) {
        when (kind) {
            MediaDeviceKind.AUDIO_INPUT ->
                MediaRouteSettingsAction(label = "Open sound settings") {
                    context.openSoundSettings()
                }

            MediaDeviceKind.AUDIO_OUTPUT ->
                MediaRouteSettingsAction(label = "Open output switcher") {
                    val openedOutputSwitcher =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            MediaRouter2
                                .getInstance(context.applicationContext)
                                .showSystemOutputSwitcher()
                        } else {
                            false
                        }

                    if (!openedOutputSwitcher) {
                        context.openSoundSettings()
                    }
                }

            MediaDeviceKind.CAMERA -> null
        }
    }
}

private fun android.content.Context.openSoundSettings() {
    val intent =
        Intent(Settings.ACTION_SOUND_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
