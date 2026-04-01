package app.logdate.client.sharing

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import io.github.aakira.napier.Napier

/**
 * A receiver that acts as a callback for the system share sheet.
 */
class ShareReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            CustomIntents.ACTION_COPY_LINK -> {
                copyLinkToClipboard(context, intent.getStringExtra(CustomIntents.EXTRA_SHARE_TEXT))
                return
            }
            CustomIntents.ACTION_CHOOSER_RESULT -> {
                logChosenComponent(intent)
                return
            }
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedImage =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        val sharedImages =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
        if (sharedText != null) {
            // Handle text
        } else if (sharedImage != null) {
            // Handle single image
        } else if (sharedImages != null) {
            val totalImages = sharedImages.size
            Napier.d("Share sheet returned $totalImages image(s)")
        }

        logChosenComponent(intent)
    }

    private fun logChosenComponent(intent: Intent) {
        val clickedComponent: ComponentName? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
            }
        clickedComponent?.let { component ->
            Napier.i("User shared via ${component.flattenToShortString()}")
        }
    }

    private fun copyLinkToClipboard(
        context: Context,
        text: String?,
    ) {
        val linkText = text?.takeIf(String::isNotBlank) ?: return
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("LogDate share", linkText))
        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
    }
}
