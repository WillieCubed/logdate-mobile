package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberSystemReduceMotion(): State<Boolean> = remember { mutableStateOf(false) }
