package app.logdate.ui.platform

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry

actual fun plainTextClipEntry(
    text: String,
    sensitive: Boolean,
): ClipEntry {
    val clip = ClipData.newPlainText("text", text)
    if (sensitive) {
        val key =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
        clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
    }
    return ClipEntry(clip)
}
