package app.logdate.client.e2e

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** Only observed emulator system-app dialogs are recoverable; a LogDate failure stays visible. */
internal fun dismissNativeEmulatorPlatformDialog(device: UiDevice): Boolean {
    val systemUiTitle = By.pkg("android").res("android:id/alertTitle").text("System UI isn't responding")
    val systemUiBlocked = device.hasObject(systemUiTitle)
    val title =
        if (systemUiBlocked) {
            systemUiTitle
        } else {
            listOf("Bluetooth keeps stopping", "Digital Wellbeing isn't responding")
                .map { By.pkg("android").res("android:id/alertTitle").text(it) }
                .firstOrNull { device.hasObject(it) } ?: return false
        }
    val action =
        if (systemUiBlocked) {
            By.pkg("android").res("android:id/aerr_wait").text("Wait")
        } else {
            By.pkg("android").res("android:id/aerr_close")
        }
    checkNotNull(device.findObject(action)).click()
    check(device.wait(Until.gone(title), 5_000)) { "The emulator platform dialog remained in front of the app" }
    return true
}
