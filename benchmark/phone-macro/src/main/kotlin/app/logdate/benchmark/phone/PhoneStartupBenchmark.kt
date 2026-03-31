package app.logdate.benchmark.phone

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = PhoneBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(),
        ) {
            PhoneBenchmarkConfig.run {
                startFromLauncher()
            }
        }
    }

    @Test
    fun warmStartupFromDeepLink() {
        benchmarkRule.measureRepeated(
            packageName = PhoneBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 6,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
        ) {
            PhoneBenchmarkConfig.run {
                startFromDeepLink()
            }
        }
    }

    @Test
    fun homeInteractionSmoke() {
        benchmarkRule.measureRepeated(
            packageName = PhoneBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 6,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
        ) {
            PhoneBenchmarkConfig.run {
                startFromLauncher()
            }
            device.waitForIdle()
            repeat(3) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.8f).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.2f).toInt(),
                    12,
                )
            }
        }
    }
}
