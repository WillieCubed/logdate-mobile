package app.logdate.ui.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


/**
 * A wrapper for the [imePadding] modifier to support compilation across platforms.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun Modifier.applyImeScroll(): Modifier = imeNestedScroll()