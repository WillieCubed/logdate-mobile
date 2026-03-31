package app.logdate.benchmark.wear

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = WearBenchmarkConfig.PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            WearBenchmarkConfig.run {
                startFromLauncher()
                device.waitForIdle()
                startQuickCapture()
            }
        }
    }
}
