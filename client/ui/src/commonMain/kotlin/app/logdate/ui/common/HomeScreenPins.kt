package app.logdate.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.uuid.Uuid

/**
 * Pins LogDate content to the launcher's home screen, where the platform supports it. The launcher
 * asks the person to confirm and place the pin, so a request is never a silent change.
 */
interface HomeScreenPins {
    /** Whether the current launcher accepts pinned shortcuts. */
    val canPin: Boolean

    /** Asks the launcher to pin a shortcut that opens the journal [journalId], titled [title]. */
    fun pinJournal(
        journalId: Uuid,
        title: String,
    )
}

/** The platform's [HomeScreenPins], or null where nothing can be pinned. */
val LocalHomeScreenPins = staticCompositionLocalOf<HomeScreenPins?> { null }
