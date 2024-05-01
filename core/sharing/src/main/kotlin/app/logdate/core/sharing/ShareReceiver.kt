package app.logdate.core.sharing

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

/**
 * A receiver that acts as a callback for the system share sheet.
 */
@AndroidEntryPoint
class ShareReceiver : BroadcastReceiver() {
    // TODO: Log share usage patterns to analytics
    override fun onReceive(context: Context, intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedImage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val sharedImages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (sharedText != null) {
            // Handle text
        } else if (sharedImage != null) {
            // Handle single image
        } else if (sharedImages != null) {
            val totalImages = sharedImages.size
            Log.d("ShareReceiver", "User send $totalImages images")
            // Handle multiple images
        }

        val clickedComponent: ComponentName? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
            }
        // TODO: Log the clicked component to analytics
    }
}