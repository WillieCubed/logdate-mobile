package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Modifier that applies padding to the IME (soft keyboard) height when it is visible.
 *
 * This modifier is only supported on Android.
 */
@Composable
expect fun Modifier.applyImeScroll(): Modifier