@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * Opacity applied to a disabled (but still visible) settings control, matching the Material
 * convention used across the settings surface. Centralized here so the value is defined once.
 */
const val DISABLED_CONTENT_ALPHA = 0.6f

/**
 * Grays a control out when [enabled] is false, using the shared [DISABLED_CONTENT_ALPHA].
 *
 * Prefer this over hand-writing `alpha(if (enabled) 1f else 0.6f)` so the disabled look stays
 * consistent everywhere.
 */
fun Modifier.disabledAlpha(enabled: Boolean): Modifier {
    if (enabled) return this
    return this.alpha(DISABLED_CONTENT_ALPHA)
}

/**
 * Whether settings controls in the current subtree are interactive.
 *
 * Settings row components ([ToggleSettingsItem], [SimpleSettingsItem], [LinkedToggleSettingsItem],
 * [SettingsNavigationItem]) read this as the default for their `enabled` parameter, so wrapping a
 * group of dependent settings in [SettingsFeatureGroup] disables all of them at once. Defaults to
 * `true` (enabled).
 */
val LocalSettingsEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

/**
 * Marks [content] as the dependent settings governed by a feature's master toggle
 * ([MasterFeatureToggle]).
 *
 * When [enabled] is false, every settings row inside grays out and stops responding to input —
 * the standard "feature off ⇒ disabled, not hidden" behavior — without each call site threading
 * `enabled` through manually. Nesting composes: an inner group can only further disable, never
 * re-enable, content disabled by an outer one.
 *
 * For non-row content inside the group (raw `Text`, segmented buttons, etc.), read
 * [LocalSettingsEnabled] and apply [disabledAlpha] / pass `enabled` yourself.
 */
@Composable
fun SettingsFeatureGroup(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSettingsEnabled provides (enabled && LocalSettingsEnabled.current),
        content = content,
    )
}
