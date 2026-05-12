package app.logdate.feature.editor.ui.audio.expansion

import androidx.compose.runtime.Composable

/**
 * Currently a no-op on iOS.
 *
 * The Android counterpart flips the system status-bar icons to white via
 * `WindowInsetsControllerCompat`. The equivalent on iOS requires overriding
 * `preferredStatusBarStyle` (and possibly `prefersStatusBarHidden`) on the host
 * `UIViewController`. The Compose surface doesn't own that controller, so a proper
 * implementation needs a Swift-side bridge: a `LogDateImmersiveStatusBarStyle`
 * shared-state flag that the root `ComposeUIViewController` reads in its overridden
 * `preferredStatusBarStyle` getter, plus a `setNeedsStatusBarAppearanceUpdate()` ping when
 * the flag changes.
 *
 * That bridge will land alongside the N4.2 / L2 chrome work; in the meantime the immersive
 * audio screen runs with the default system bar treatment on iOS.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun ImmersiveSystemBarEffect() {
    // Intentionally no-op until the Swift-side bridge lands.
}
