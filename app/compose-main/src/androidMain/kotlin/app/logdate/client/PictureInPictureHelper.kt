package app.logdate.client

import android.app.Activity
import android.app.PictureInPictureParams
import android.util.Rational
import io.github.aakira.napier.Napier

/**
 * Enters picture-in-picture mode for media playback.
 *
 * @param activity The current Activity.
 * @param aspectRatio Width-to-height ratio for the PiP window (default 16:9).
 */
fun enterPictureInPicture(
    activity: Activity,
    aspectRatio: Rational = Rational(16, 9),
) {
    try {
        val params =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(aspectRatio)
                .build()
        activity.enterPictureInPictureMode(params)
    } catch (e: Exception) {
        Napier.w("Could not enter picture-in-picture mode", e)
    }
}
