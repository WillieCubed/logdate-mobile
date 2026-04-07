@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.runtime.Composable

/**
 * Desktop stub for [rememberCoverImageLauncher]. Picking a cover image is a no-op on desktop;
 * the editor still renders any URI the user already has on the event.
 */
@Composable
actual fun rememberCoverImageLauncher(onPicked: (String?) -> Unit): () -> Unit = {}
