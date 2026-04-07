@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.runtime.Composable

/**
 * iOS stub for [rememberCoverImageLauncher]. The cover image picker is not yet implemented on
 * iOS — invoking the returned lambda is a no-op so the editor degrades gracefully.
 */
@Composable
actual fun rememberCoverImageLauncher(onPicked: (String?) -> Unit): () -> Unit = {}
