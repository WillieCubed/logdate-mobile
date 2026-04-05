package app.logdate.feature.editor.ui.audio.expansion

import androidx.compose.runtime.Composable

/**
 * Sets system bar (status bar + navigation bar) icon appearance to light/white while
 * the immersive audio screen is visible, then restores the previous appearance on disposal.
 *
 * No-op on non-Android platforms.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
expect fun ImmersiveSystemBarEffect()
