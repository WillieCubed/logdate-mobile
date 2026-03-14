package app.logdate.ui.restore

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the app is in a post-cloud-restore state where the database is empty
 * but the user was previously onboarded. Used to show contextual empty states
 * in the timeline instead of the default new-user experience.
 */
val LocalIsPostCloudRestore = staticCompositionLocalOf { false }

/**
 * Callback to acknowledge the post-cloud-restore state and write the device sentinel.
 * After this is called, [LocalIsPostCloudRestore] will transition to false.
 */
val LocalAcknowledgeCloudRestore = staticCompositionLocalOf<() -> Unit> { {} }
