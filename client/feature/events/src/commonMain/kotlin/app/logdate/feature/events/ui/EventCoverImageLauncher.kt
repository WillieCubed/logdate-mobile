@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.runtime.Composable

/**
 * Returns a launcher lambda that opens the platform photo picker for selecting a cover image.
 *
 * The actual implementation lives per platform:
 * - Android: uses `ActivityResultContracts.PickVisualMedia` and reports the resulting `content://`
 *   URI as a string.
 * - iOS / desktop: use platform image pickers and report the selected asset/file identifier.
 *
 * The lambda is stable across recompositions, so callers can assign it to a button's `onClick`
 * without worrying about identity changes.
 *
 * @param onPicked invoked exactly once per successful picker run with the chosen URI, or with
 *   `null` when the user cancels.
 */
@Composable
expect fun rememberCoverImageLauncher(onPicked: (String?) -> Unit): () -> Unit
