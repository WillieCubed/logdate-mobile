package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.applyImeScroll(): Modifier = this // No-op on iOS