package app.logdate.ui.common

import androidx.compose.ui.Modifier

actual fun Modifier.handwritingDelegator(onStart: () -> Unit): Modifier = this

actual fun Modifier.handwritingDelegate(): Modifier = this
