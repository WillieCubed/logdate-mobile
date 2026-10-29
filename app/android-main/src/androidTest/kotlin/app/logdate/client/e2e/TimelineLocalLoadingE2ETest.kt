package app.logdate.client.e2e

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.domain.moments.ObserveTimelineMarksUseCase
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.GetTimelineDayUseCase
import app.logdate.client.domain.timeline.GroupNotesByDayBoundsUseCase
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import app.logdate.client.testing.onboarding.putOnboardingTestFixture
import app.logdate.shared.model.Event
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.io.File
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Real local database, production routes, bounded synthetic corpus and one managed-device runtime. */
class TimelineLocalLoadingE2ETest {
    @get:Rule val koin: TestRule = OnboardingKoinModuleOverrideRule(module {})

    @Test(timeout = 180_000)
    fun retainedEntriesRenderOfflineBeforeSlowOptionalMetadata() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(Build.HARDWARE in setOf("ranchu", "goldfish") && context.packageName.endsWith(".debug"))
        val device = UiDevice.getInstance(instrumentation)
        val output =
            File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "timeline-local-loading")
        check(output.mkdirs() || output.isDirectory)
        val container = GlobalContext.get()
        val notes = container.get<JournalNotesRepository>()
        val originalEvents = container.get<EventRepository>()
        val slowEvents =
            object : EventRepository by originalEvents {
                override fun observeAllEvents(): Flow<List<Event>> =
                    flow {
                        delay(5_000)
                        emitAll(originalEvents.observeAllEvents())
                    }
            }
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val corpus =
            List(827) { index ->
                val at = now - (826 - index).minutes
                JournalNote.Text(content = "Synthetic retained memory $index", creationTimestamp = at, lastUpdated = at)
            }
        val corpusIds = corpus.map { it.uid }.toSet()
        val oldSize = overrideValue(device.executeShellCommand("wm size"))
        val oldDensity = overrideValue(device.executeShellCommand("wm density"))
        val wifi = device.executeShellCommand("settings get global wifi_on").trim() == "1"
        val mobile = device.executeShellCommand("settings get global mobile_data").trim() == "1"
        val timings = mutableListOf<String>()
        try {
            device.executeShellCommand("svc wifi disable")
            device.executeShellCommand("svc data disable")
            assertTrue(runBlocking { notes.observeRecentNotes(1).first().isEmpty() }, "Expected an empty isolated managed test database")
            val emptyIntent =
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                    putOnboardingTestFixture(OnboardingTestFixture.ONBOARDED_HOME)
                }
            val emptyStart = SystemClock.elapsedRealtimeNanos()
            ActivityScenario.launch<MainActivity>(emptyIntent).use {
                assertTrue(device.wait(Until.hasObject(By.text("Start your timeline")), 15_000), "Empty local data stayed in loading")
                timings += "empty-freshActivityMs=${(SystemClock.elapsedRealtimeNanos() - emptyStart) / 1_000_000.0}"
                check(device.takeScreenshot(File(output, "phone-empty.png")))
            }
            runBlocking { corpus.forEach { notes.create(it) } }
            repeat(4) { sample ->
                val timeline =
                    GetStreamingTimelineUseCase(
                        notes,
                        container.get<GetTimelineDayUseCase>(),
                        container.get<GroupNotesByDayBoundsUseCase>(),
                        slowEvents,
                        container.get<ObserveTimelineMarksUseCase>()(),
                    )
                val start = SystemClock.elapsedRealtimeNanos()
                val first =
                    runBlocking {
                        timeline().first { snapshot -> snapshot.days.any { day -> day.entries.any { entry -> entry.uid in corpusIds } } }
                    }
                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                timings += "${if (sample == 0) "fresh-subscription" else "warm-subscription-$sample"}Ms=$elapsed"
                assertTrue(first.days.isNotEmpty())
                assertTrue(elapsed < 1_000, "Local data waited for optional metadata: $elapsed ms")
            }
            for ((name, size) in listOf("phone" to (411 to 891), "foldable-sized" to (720 to 960), "landscape-tablet" to (1280 to 800))) {
                device.executeShellCommand("wm density 160")
                device.executeShellCommand("wm size ${size.first}x${size.second}")
                val intent =
                    Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                        putOnboardingTestFixture(OnboardingTestFixture.ONBOARDED_HOME)
                    }
                val start = SystemClock.elapsedRealtimeNanos()
                ActivityScenario.launch<MainActivity>(intent).use {
                    val entry = checkNotNull(device.wait(Until.findObject(By.textStartsWith("Synthetic retained memory")), 15_000))
                    assertTrue(!entry.visibleBounds.isEmpty)
                    timings += "$name-freshActivityFirstEntryMs=${(SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0}"
                    device.waitForIdle(1_000)
                    check(device.takeScreenshot(File(output, "$name.png")))
                    val warmStart = SystemClock.elapsedRealtimeNanos()
                    it.recreate()
                    val warmEntry = checkNotNull(device.wait(Until.findObject(By.textStartsWith("Synthetic retained memory")), 15_000))
                    assertTrue(!warmEntry.visibleBounds.isEmpty)
                    timings += "$name-recreatedActivityFirstEntryMs=${(SystemClock.elapsedRealtimeNanos() - warmStart) / 1_000_000.0}"
                }
            }
        } finally {
            runBlocking { corpus.forEach { notes.removeById(it.uid) } }
            device.executeShellCommand("wm size $oldSize")
            device.executeShellCommand("wm density $oldDensity")
            device.executeShellCommand("svc wifi ${if (wifi) "enable" else "disable"}")
            device.executeShellCommand("svc data ${if (mobile) "enable" else "disable"}")
            File(output, "timings.txt").writeText(
                "827 synthetic local entries; offline.\n" +
                    "Subscription samples delay event metadata 5000ms; Activities use the normal event repository.\n" +
                    "Activities run inside an initialized instrumentation process; this is not cold process startup.\n" +
                    "Activity samples include launch, fixture work and accessibility waits; they do not measure the first frame.\n" +
                    timings.joinToString("\n"),
            )
        }
    }

    private fun overrideValue(value: String): String =
        value
            .lineSequence()
            .firstOrNull { it.startsWith("Override") }
            ?.substringAfter(": ")
            ?.trim() ?: "reset"
}
