package app.logdate.benchmark.phone

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = PhoneBenchmarkConfig.PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            PhoneBenchmarkConfig.run {
                startFromLauncher()
                device.waitForIdle()
                startFromDeepLink()
            }
        }
    }
}
