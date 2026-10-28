@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.e2e

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.text.toUiText
import app.logdate.feature.rewind.ui.LocationMapRewindPanelUiState
import app.logdate.feature.rewind.ui.MapPanelPoint
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.detail.RewindStoryContent
import app.logdate.feature.rewind.ui.detail.RewindStoryView
import app.logdate.ui.maps.LocalGoogleMapsAvailabilityOverride
import app.logdate.ui.platform.LocalReduceMotionOverride
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic coordinates only. Run on an emulator or Gradle Managed Device. */
class RewindGeographicMapE2ETest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun configureSystemBars() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    @Test fun geographyLoadsBehindMarkersAndMapTapsStillAdvanceTheStory() {
        showMap(listOf(MapPanelPoint(36.1699, -115.1398), MapPanelPoint(36.1716, -115.1460), MapPanelPoint(36.1740, -115.1320)))
        awaitMap()
        assertEquals(
            "Rewind details must fill their playback area without a decorative frame",
            compose.onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("rewind-map-surface").fetchSemanticsNode().boundsInRoot,
        )
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(
            "Phone map fills its edge-to-edge scene",
            root,
            compose.onNodeWithTag("rewind-map-surface").fetchSemanticsNode().boundsInRoot,
        )
        capture("rewind-geographic-map")
        compose.onNodeWithTag("rewind-native-map").performTouchInput { click(Offset(width * 0.75f, height * 0.5f)) }
        compose.onNodeWithText("Next moment").assertIsDisplayed()
    }

    @Test fun boundedSceneFillsItsOwnBoundsWithoutAdoptingWindowInsets() {
        showMap(listOf(MapPanelPoint(36.1699, -115.1398)), bounded = true)
        awaitMap()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val scene = compose.onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInRoot
        assertTrue("Responsive parent can bound the scene", scene.height < root.height && scene.width < root.width)
        assertEquals(scene, compose.onNodeWithTag("rewind-native-map").fetchSemanticsNode().boundsInRoot)
        val heading = compose.onNodeWithText("Where your week was").fetchSemanticsNode().boundsInRoot
        assertEquals(
            "A contained scene must not inherit the window's top inset",
            with(compose.density) { 96.dp.toPx() },
            heading.top - scene.top,
            1f,
        )
        capture("rewind-bounded-scene-map")
    }

    @Test fun singleLocationRendersOneRealNeighborhood() {
        showMap(listOf(MapPanelPoint(36.1699, -115.1398)))
        awaitMap()
        capture("rewind-single-location-map")
    }

    @Test fun reducedMotionKeepsTheImmersiveMapAndStoryNavigation() {
        showMap(OUTLIER_POINTS, reduceMotion = true, fontScale = 2f)
        awaitMap()
        capture("rewind-immersive-map-reduced-motion-large-text")
        compose.onNodeWithTag("rewind-native-map").performTouchInput { click(Offset(width * 0.75f, height * 0.65f)) }
        compose.onNodeWithText("Next moment").assertIsDisplayed()
    }

    @Test fun distantLocationsKeepTheOutlierNoticeAndAttributionVisible() {
        showMap(OUTLIER_POINTS)
        awaitMap()
        compose.onNodeWithTag("rewind-map-notice").assertIsDisplayed()
        capture("rewind-immersive-map-outlier")
    }

    @Test fun mapSwipesNavigateTheStoryInsteadOfPanning() {
        showMap(listOf(MapPanelPoint(36.1699, -115.1398)))
        awaitMap()
        compose.onNodeWithTag("rewind-native-map").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Next moment").assertIsDisplayed()
    }

    @Test fun missingConfigurationShowsAnHonestUnavailableState() {
        showMap(listOf(MapPanelPoint(36.1699, -115.1398)), mapsEnabled = false)
        compose.onNodeWithText("Map unavailable on this device.").assertIsDisplayed()
        capture("rewind-map-unavailable")
    }

    @Test fun emptyLocationsDoNotDrawFakeGeography() {
        showMap(emptyList())
        compose.onNodeWithText("No locations to show on this map.").assertIsDisplayed()
    }

    private companion object {
        val OUTLIER_POINTS =
            listOf(
                MapPanelPoint(36.1699, -115.1398),
                MapPanelPoint(36.1716, -115.1460),
                MapPanelPoint(36.1740, -115.1320),
                MapPanelPoint(-33.86, 151.21),
            )
    }

    private fun showMap(
        points: List<MapPanelPoint>,
        mapsEnabled: Boolean? = null,
        reduceMotion: Boolean? = null,
        fontScale: Float? = null,
        bounded: Boolean = false,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalGoogleMapsAvailabilityOverride provides mapsEnabled,
                LocalReduceMotionOverride provides reduceMotion,
                LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
            ) {
                LogDateTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RewindStoryView(
                            panels =
                                listOf(
                                    LocationMapRewindPanelUiState(
                                        points,
                                        "Where your week was".toUiText(),
                                        "Your recorded locations".toUiText(),
                                    ),
                                    SubtitledRewindPanelUiState("Next moment", "Tuesday".toUiText(), null),
                                ),
                            onExit = {},
                            externalPause = true,
                            modifier = if (bounded) Modifier.size(320.dp, 500.dp) else Modifier.fillMaxSize(),
                        ) { RewindStoryContent(it) }
                    }
                }
            }
        }
    }

    private fun awaitMap() {
        compose.waitUntil(60_000) {
            compose
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Map loaded"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("rewind-native-map").assertIsDisplayed()
        assertEquals(
            "Geography must fill the Rewind surface behind its overlay text",
            compose.onNodeWithTag("rewind-map-surface").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("rewind-native-map").fetchSemanticsNode().boundsInRoot,
        )
    }

    private fun capture(name: String) {
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: error("Map screenshots require an explicit emulator output directory")
        val file = File(directory, "$name.png")
        file.parentFile?.mkdirs()
        compose.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        val map =
            compose
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Map loaded"))
                .fetchSemanticsNodes()
                .firstOrNull()
        compose.waitUntil(10_000) {
            if (!device.takeScreenshot(file)) return@waitUntil false
            if (map == null) return@waitUntil true
            val bounds = map.boundsInWindow
            val image = BitmapFactory.decodeFile(file.absolutePath) ?: return@waitUntil false
            // Reject captures taken before the fade has delivered geography.
            val samples = listOf(0.15f to 0.55f, 0.85f to 0.55f, 0.15f to 0.75f, 0.85f to 0.75f)
            val visible =
                samples.count { (x, y) ->
                    val pixel = image.getPixel((bounds.left + bounds.width * x).toInt(), (bounds.top + bounds.height * y).toInt())
                    android.graphics.Color.red(pixel) > 25 &&
                        android.graphics.Color.green(pixel) > 30 &&
                        android.graphics.Color.blue(pixel) > 35
                }
            image.recycle()
            visible >= 2
        }
    }
}
