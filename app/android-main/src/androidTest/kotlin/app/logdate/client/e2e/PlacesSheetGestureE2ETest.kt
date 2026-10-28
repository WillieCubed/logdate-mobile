package app.logdate.client.e2e

import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.text.UiText
import app.logdate.feature.core.main.HomeRouteDestination
import app.logdate.feature.location.timeline.ui.history.HistoryMemoryKind
import app.logdate.feature.location.timeline.ui.history.HistoryMemoryUi
import app.logdate.feature.location.timeline.ui.history.HistoryPlaceUi
import app.logdate.feature.location.timeline.ui.history.HistoryTab
import app.logdate.feature.location.timeline.ui.history.HumanHistoryMap
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryActions
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryContent
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryState
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.workspace.LocalWorkspaceEnabled
import app.logdate.ui.workspace.SupportingExtent
import app.logdate.ui.workspace.WorkspaceDestination
import app.logdate.ui.workspace.WorkspaceScaffold
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Production Places components, synthetic memories, real Android touch input and a bounded video.
 * Resized windows prove layout; they do not simulate a physical fold or certify native map tiles.
 */
@SdkSuppress(minSdkVersion = 34)
class PlacesSheetGestureE2ETest {
    @Test(timeout = 180_000)
    fun fingerTrackingReversalBackAndThreeWindowSizes() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val oldSize = overrideValue(device.executeShellCommand("wm size"))
        val oldDensity = overrideValue(device.executeShellCommand("wm density"))
        val oldImePolicy = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "places-sheet")
        check(output.mkdirs() || output.isDirectory)
        try {
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            for ((label, size) in listOf(
                "normal-mobile" to (411 to 891),
                "foldable-sized-mobile" to (720 to 960),
                "landscape-tablet" to (1280 to 800),
            )) {
                device.executeShellCommand("wm density 160")
                device.executeShellCommand("wm size ${size.first}x${size.second}")
                ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.enableEdgeToEdge(
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        )
                        activity.window.isNavigationBarContrastEnforced = false
                        activity.setContent { PlacesSheetFixture() }
                    }
                    device.waitForIdle(2_000)
                    scenario.onActivity { check(it.window.decorView.width == size.first && it.window.decorView.height == size.second) }
                    checkNotNull(device.wait(Until.findObject(By.textContains("Riverside Café")), 15_000))
                    if (label == "normal-mobile") {
                        PlacesSheetRecording(instrumentation, device, output).record {
                            fun headerTop() = checkNotNull(device.findObject(By.desc("Choose section"))).visibleBounds.top
                            val start = headerTop()
                            val header = checkNotNull(device.findObject(By.desc("Choose section"))).visibleBounds
                            PlacesSheetPointer(instrumentation, Offset(header.exactCenterX(), header.exactCenterY())).use { pointer ->
                                pointer.moveBy(Offset(0f, -90f), 400)
                                device.waitForIdle(1_000)
                                val raised = headerTop()
                                assertTrue(raised < start - 50, "Native sheet must track a finger before release")
                                pointer.moveBy(Offset(0f, 45f), 300)
                                device.waitForIdle(1_000)
                                assertEquals(45f, (headerTop() - raised).toFloat(), 3f)
                                val beforeRapidReversal = headerTop()
                                pointer.moveBy(Offset(0f, -100f), 160)
                                pointer.moveBy(Offset(0f, 90f), 140)
                                device.waitForIdle(1_000)
                                assertEquals(
                                    (beforeRapidReversal - 10).toFloat(),
                                    headerTop().toFloat(),
                                    3f,
                                    "Rapid reversal must keep following the same finger",
                                )
                            }
                            val memory =
                                checkNotNull(
                                    device.wait(Until.findObject(By.textContains("Coffee before the train")), 5_000),
                                ).visibleBounds
                            val beforeScroll = headerTop()
                            PlacesSheetPointer(instrumentation, Offset(memory.exactCenterX(), memory.exactCenterY())).use { pointer ->
                                pointer.moveBy(Offset(0f, -100f), 500)
                                device.waitForIdle(1_000)
                                val afterScroll = headerTop()
                                assertTrue(afterScroll < beforeScroll - 60, "Native list drag must expand before scrolling rows")
                                pointer.moveBy(Offset(0f, 70f), 400)
                                device.waitForIdle(1_000)
                                assertTrue(headerTop() > afterScroll + 60, "At the list top, downward drag must return to the sheet")
                            }
                            device.waitForIdle(1_000)
                            val beforeIme = headerTop()
                            check(device.click(device.displayWidth / 2, 68))
                            awaitIme(scenario, true)
                            checkNotNull(device.wait(Until.findObject(By.desc("Show map")), 5_000))
                            device.pressBack()
                            awaitIme(scenario, false)
                            device.waitForIdle(1_000)
                            assertEquals(
                                beforeIme.toFloat(),
                                headerTop().toFloat(),
                                3f,
                                "Closing the IME must restore browsing instead of leaving full view",
                            )
                            device.pressBack()
                            checkNotNull(device.wait(Until.findObject(By.desc("Browse")), 5_000))
                            val peek = headerTop()
                            val collapsedHeader = checkNotNull(device.findObject(By.desc("Choose section"))).visibleBounds
                            PlacesSheetPointer(
                                instrumentation,
                                Offset(collapsedHeader.exactCenterX(), collapsedHeader.exactCenterY()),
                            ).use {
                                it.moveBy(Offset(0f, -230f), 700)
                            }
                            device.waitForIdle(1_000)
                            assertTrue(headerTop() < peek - 100)
                            checkNotNull(device.wait(Until.findObject(By.textContains("Riverside Café")), 5_000))
                        }
                    }
                    check(device.takeScreenshot(File(output, "$label-places-${size.first}x${size.second}.png")))
                    File(output, "$label-window.txt").writeText(
                        "Native API34 resized window; synthetic places/memories; no physical hinge or map-tile proof.\n" +
                            device.executeShellCommand("wm size") + device.executeShellCommand("wm density") +
                            "font_scale=" + device.executeShellCommand("settings get system font_scale"),
                    )
                }
            }
            File(
                output,
                "provenance.txt",
            ).writeText(
                "API34 Gradle-managed Pixel 9 Pro; density160 resized windows; production Places/workspace components with synthetic places/memories. Test ComponentActivity has transparent bars; this does not certify MainActivity window setup, native map tiles, or hinge hardware.\n",
            )
        } finally {
            if (oldImePolicy == "null") {
                device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            } else {
                device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $oldImePolicy")
            }
            device.executeShellCommand("wm size $oldSize")
            device.executeShellCommand("wm density $oldDensity")
        }
    }

    private fun awaitIme(
        scenario: ActivityScenario<ComponentActivity>,
        visible: Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var shown = false
            scenario.onActivity {
                shown = it.window.decorView.rootWindowInsets
                    ?.isVisible(WindowInsets.Type.ime()) == true
            }
            if (shown == visible) return
            SystemClock.sleep(50)
        }
        error("IME did not reach visible=$visible")
    }

    private fun overrideValue(value: String): String =
        value
            .lineSequence()
            .firstOrNull { it.startsWith("Override") }
            ?.substringAfter(":")
            ?.trim() ?: "reset"
}

@androidx.compose.runtime.Composable
private fun PlacesSheetFixture() {
    val instant = Instant.parse("2026-06-18T10:00:00Z")
    val place = SemanticPlace("cafe", "Riverside Café", 51.507, -0.12)
    val visits = listOf(PlaceVisit("visit", instant, instant, listOf("fix"), place.latitude, place.longitude, true, place))
    val places =
        listOf(
            HistoryPlaceUi(
                "cafe",
                "Riverside Café",
                listOf(UiText.Raw("London · 3 visits")),
                listOf(
                    HistoryMemoryUi(
                        "coffee",
                        UiText.Raw("Coffee before the train, and time to finish our conversation."),
                        HistoryMemoryKind.Text,
                    ),
                ),
            ),
            HistoryPlaceUi(
                "river",
                "River walk",
                listOf(UiText.Raw("London · 2 visits")),
                listOf(HistoryMemoryUi("walk", UiText.Raw("The long walk home along the river."), HistoryMemoryKind.Text)),
            ),
        ) + (1..12).map { HistoryPlaceUi("place-$it", "Synthetic place $it", listOf(UiText.Raw("Example visit"))) }
    LogDateTheme(dynamicColor = false, darkTheme = false) {
        CompositionLocalProvider(LocalWorkspaceEnabled provides true) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    WorkspaceScaffold(
                        HomeRouteDestination.entries.map {
                            WorkspaceDestination(
                                it.name,
                                if (it ==
                                    HomeRouteDestination.LocationHistory
                                ) {
                                    "Places"
                                } else {
                                    it.name
                                },
                                it.selectedIcon,
                                it.unselectedIcon,
                            )
                        },
                        HomeRouteDestination.LocationHistory.name,
                        {},
                        onSearch = {},
                    ) {
                        HumanLocationHistoryContent(
                            HumanLocationHistoryState(
                                UiText.Raw("18 Jun 2026"),
                                tab = HistoryTab.Places,
                                places = places,
                                placesFilterLabel = UiText.Raw("30 days"),
                            ),
                            HumanLocationHistoryActions(),
                            showTitle = false,
                            initialSupportingExtent = SupportingExtent.Browsing,
                            mapContent = { HumanHistoryMap(visits, null, {}, it, {}, "synthetic-fixture") },
                        )
                    }
                }
            }
        }
    }
}
