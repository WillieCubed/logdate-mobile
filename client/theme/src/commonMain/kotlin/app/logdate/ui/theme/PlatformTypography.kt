package app.logdate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * Returns the active [Typography] for the host platform.
 *
 * Default actual returns the shared [Typography] constant defined in `Type.kt`. The iOS actual
 * (N2.2) replaces it with Apple's text-style ladder — LargeTitle / Title1 / Title2 / Title3 /
 * Headline / Body / Callout / Subheadline / Footnote / Caption — with letter-spacing 0, mapped
 * onto the same Compose roles.
 */
@Composable
expect fun platformTypography(): Typography
