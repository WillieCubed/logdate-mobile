package app.logdate.benchmark.wear

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = WearBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(),
        ) {
            WearBenchmarkConfig.run {
                startFromLauncher()
            }
        }
    }

    @Test
    fun warmStartup() {
        benchmarkRule.measureRepeated(
            packageName = WearBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 6,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                device.pressHome()
            },
        ) {
            WearBenchmarkConfig.run {
                startFromLauncher()
            }
        }
    }

    @Test
    fun homeInteractionSmoke() {
        benchmarkRule.measureRepeated(
            packageName = WearBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 6,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                device.pressHome()
            },
        ) {
            WearBenchmarkConfig.run {
                startFromLauncher()
            }
            device.waitForIdle()
            repeat(2) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.7f).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.3f).toInt(),
                    10,
                )
            }
        }
    }
}
