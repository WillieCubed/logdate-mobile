package app.logdate.benchmark.phone

import android.os.Environment
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
                startFromLauncher(fixture = onboardedHomeFixtureJson())
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
                startFromDeepLink(fixture = onboardedHomeFixtureJson())
            }
        }
    }

    @Test
    fun homeInteractionSmoke() {
        benchmarkRule.measureRepeated(
            packageName = PhoneBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = 6,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
        ) {
            PhoneBenchmarkConfig.run {
                startFromLauncher(fixture = onboardedHomeFixtureJson())
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

    @Test
    fun openSearchAndTypeFirstQuery() {
        benchmarkRule.measureRepeated(
            packageName = PhoneBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = 6,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
        ) {
            PhoneBenchmarkConfig.run {
                startFromLauncher(fixture = onboardedHomeFixtureJson())
                openSearchFromHome()
                typeSearchQuery(query = "sun")
            }
        }
    }

    @Test
    fun coldStartupToFreshOnboarding() {
        benchmarkRule.measureRepeated(
            packageName = PhoneBenchmarkConfig.PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 3,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(),
        ) {
            PhoneBenchmarkConfig.run {
                startFromLauncher(fixture = freshOnboardingFixtureJson())
            }
            check(device.wait(Until.hasObject(By.textContains("Get started")), ONBOARDING_TIMEOUT_MS)) {
                "Fresh onboarding did not reach the landing screen"
            }
            captureBenchmarkScreenshot(device, "fresh-onboarding-start")
            device.findObject(By.textContains("Get started"))?.click()
            check(device.wait(Until.hasObject(By.textContains("Who are you?")), ONBOARDING_TIMEOUT_MS)) {
                "Fresh onboarding did not reach the personal intro screen"
            }
            captureBenchmarkScreenshot(device, "fresh-onboarding-personal-intro")
        }
    }

    private fun captureBenchmarkScreenshot(
        device: UiDevice,
        name: String,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir =
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "benchmark-artifacts/onboarding",
            ).apply { mkdirs() }
        device.takeScreenshot(File(outputDir, "$name.png"))
    }

    private companion object {
        const val ONBOARDING_TIMEOUT_MS = 5_000L
    }
}
