@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:filename")

package app.logdate.feature.journals.ui.creation

import androidx.compose.runtime.Composable

/**
 * Composable that provides a media picker launcher.
 *
 * Call [launchPicker] to open the platform's photo/video picker.
 * Selected media URIs are returned via the [onMediaSelected] callback.
 */
@Composable
expect fun rememberMediaPickerLauncher(onMediaSelected: (List<String>) -> Unit): MediaPickerState

/**
 * State holder for the media picker launcher.
 */
expect class MediaPickerState {
    fun launchPicker()
}
