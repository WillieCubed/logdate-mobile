package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@Composable
actual fun rememberSystemReduceMotion(): State<Boolean> {
    val state = remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val token =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIAccessibilityReduceMotionStatusDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _: NSNotification? ->
                state.value = UIAccessibilityIsReduceMotionEnabled()
            }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(token)
        }
    }
    return state
}
