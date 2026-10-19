package app.logdate.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Window-backed private surfaces must disappear with the locked journal, including windows
 * already open when the lock activates. The caller's composition and remembered state remain.
 * Startup repair UI outside the private content subtree retains the default visibility.
 */
val LocalPrivateModalVisible = staticCompositionLocalOf { true }
