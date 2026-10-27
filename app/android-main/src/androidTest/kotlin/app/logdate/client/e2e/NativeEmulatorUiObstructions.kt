package app.logdate.client.e2e

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** Only observed emulator system-app dialogs are recoverable; a LogDate failure stays visible. */
internal fun dismissNativeEmulatorPlatformDialog(device: UiDevice): Boolean =
    dismissNativeEmulatorPlatformDialog(
        isTitlePresent = { title -> device.hasObject(By.pkg("android").res("android:id/alertTitle").text(title)) },
        clickAction = { resource ->
            val action = By.pkg("android").res(resource)
            checkNotNull(device.findObject(if (resource == "android:id/aerr_wait") action.text("Wait") else action)).click()
        },
        waitUntilGone = { title ->
            device.wait(Until.gone(By.pkg("android").res("android:id/alertTitle").text(title)), 5_000)
        },
    )

internal fun dismissNativeEmulatorPlatformDialog(
    isTitlePresent: (String) -> Boolean,
    clickAction: (String) -> Unit,
    waitUntilGone: (String) -> Boolean,
): Boolean {
    val waitTitle = listOf("System UI isn't responding", "Process system isn't responding").firstOrNull(isTitlePresent)
    val title =
        waitTitle
            ?: listOf("Bluetooth keeps stopping", "Digital Wellbeing isn't responding").firstOrNull(isTitlePresent)
            ?: return false
    clickAction(if (waitTitle != null) "android:id/aerr_wait" else "android:id/aerr_close")
    check(waitUntilGone(title)) { "The emulator platform dialog remained in front of the app" }
    return true
}
