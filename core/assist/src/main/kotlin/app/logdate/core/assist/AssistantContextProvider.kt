package app.logdate.core.assist

import android.content.ClipData

/**
 * An interface for providing context to an on-device assistant based on current screen state.
 */
interface AssistantContextProvider {

    /**
     * Returns a JSON-LD object representing relevant JSON entity data for the current screen.
     */
    val jsonData: String

    /**
     * Returns additional data that the assistant can use to provide contextually relevant information.
     */
    val clipData: ClipData?
}