package app.logdate.ui.common

import androidx.compose.foundation.text.handwriting.handwritingDetector
import androidx.compose.foundation.text.handwriting.handwritingHandler
import androidx.compose.ui.Modifier

actual fun Modifier.handwritingDelegator(onStart: () -> Unit): Modifier = handwritingDetector(onStart)

actual fun Modifier.handwritingDelegate(): Modifier = handwritingHandler()
