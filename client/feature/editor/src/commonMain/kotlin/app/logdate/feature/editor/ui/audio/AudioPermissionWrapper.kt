package app.logdate.feature.editor.ui.audio

import androidx.compose.runtime.Composable

/**
 * Platform-specific wrapper component that handles audio recording permissions.
 * 
 * This is used to handle permission requests and display appropriate UI based on permission state.
 */
@Composable
expect fun AudioPermissionWrapper(
    content: @Composable () -> Unit
)