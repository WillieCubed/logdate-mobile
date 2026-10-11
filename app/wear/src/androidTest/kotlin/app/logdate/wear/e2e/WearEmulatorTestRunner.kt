package app.logdate.wear.e2e

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.runner.AndroidJUnitRunner
import io.github.aakira.napier.Napier

/** Keeps Wear's charging activity from taking focus between emulator UI tests. */
class WearEmulatorTestRunner : AndroidJUnitRunner() {
    private var batteryOverridden = false

    override fun onStart() {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
            "Wear instrumentation tests require an Android emulator"
        }
        batteryCommand("unplug")
        batteryOverridden = true
        super.onStart()
    }

    override fun finish(
        resultCode: Int,
        results: Bundle?,
    ) {
        try {
            if (batteryOverridden) batteryCommand("reset")
        } catch (e: Exception) {
            Napier.w("Could not restore the emulator battery after instrumentation", e)
        } finally {
            super.finish(resultCode, results)
        }
    }

    private fun batteryCommand(action: String) {
        uiAutomation.executeShellCommand("cmd battery $action").use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
