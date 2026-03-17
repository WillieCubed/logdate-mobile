package app.logdate.feature.remotedisplay

import android.content.Context
import app.logdate.client.media.display.ExternalDisplay
import app.logdate.client.media.display.RemoteDisplayManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.view.Display
import io.github.aakira.napier.Napier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android implementation of [RemoteDisplayManager] using the [DisplayManager] and
 * [android.app.Presentation] APIs.
 *
 * Detects external displays (HDMI, wireless, Chromecast dongle) and renders media
 * full-screen on them via [MediaPresentation].
 */
class AndroidRemoteDisplayManager(
    private val context: Context,
) : RemoteDisplayManager {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var activePresentation: MediaPresentation? = null
    private val isPresentingState = MutableStateFlow(false)

    override fun observeExternalDisplays(): Flow<List<ExternalDisplay>> =
        callbackFlow {
            fun emitDisplays() {
                val displays =
                    displayManager
                        .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                        .mapNotNull { display ->
                            if (display.state == Display.STATE_OFF) return@mapNotNull null
                            ExternalDisplay(
                                id = display.displayId,
                                name = display.name ?: "External display",
                            )
                        }
                trySend(displays)
            }

            val listener =
                object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = emitDisplays()

                    override fun onDisplayRemoved(displayId: Int) {
                        if (activePresentation?.display?.displayId == displayId) {
                            dismiss()
                        }
                        emitDisplays()
                    }

                    override fun onDisplayChanged(displayId: Int) = emitDisplays()
                }

            displayManager.registerDisplayListener(listener, null)
            emitDisplays()

            awaitClose {
                displayManager.unregisterDisplayListener(listener)
            }
        }

    override fun present(
        displayId: Int,
        mediaUri: String,
        mimeType: String,
    ) {
        dismiss()

        val display = displayManager.getDisplay(displayId)
        if (display == null) {
            Napier.w("Display $displayId not found")
            return
        }

        try {
            val presentation = MediaPresentation(context, display)
            presentation.showMedia(Uri.parse(mediaUri), mimeType)
            presentation.show()
            activePresentation = presentation
            isPresentingState.value = true
            Napier.i("Presenting on display: ${display.name}")
        } catch (e: Exception) {
            Napier.e("Failed to present on display $displayId", e)
            isPresentingState.value = false
        }
    }

    override fun updatePresentation(
        mediaUri: String,
        mimeType: String,
    ) {
        val presentation = activePresentation ?: return
        try {
            presentation.showMedia(Uri.parse(mediaUri), mimeType)
        } catch (e: Exception) {
            Napier.e("Failed to update presentation", e)
        }
    }

    override fun dismiss() {
        activePresentation?.let { presentation ->
            try {
                presentation.dismiss()
            } catch (e: Exception) {
                Napier.w("Error dismissing presentation", e)
            }
        }
        activePresentation = null
        isPresentingState.value = false
    }

    override fun observeIsPresenting(): Flow<Boolean> = isPresentingState
}
