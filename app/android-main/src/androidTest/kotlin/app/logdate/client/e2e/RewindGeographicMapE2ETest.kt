@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.e2e

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.text.toUiText
import app.logdate.feature.rewind.ui.LocationMapRewindPanelUiState
import app.logdate.feature.rewind.ui.MapPanelPoint
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.detail.RewindStoryContent
import app.logdate.feature.rewind.ui.detail.RewindStoryView
import app.logdate.ui.maps.LocalGoogleMapsAvailabilityOverride
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic coordinates only. Run on an emulator or Gradle Managed Device. */
class RewindGeographicMapE2ETest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun geographyLoadsBehindMarkersAndMapTapsStillAdvanceTheStory() {
        showMap(listOf(MapPanelPoint(36.1699, -115.1398), MapPanelPoint(36.1716, -115.1460), MapPanelPoint(36.1740, -115.1320)))
        awaitMap()
        capture("rewind-geographic-map")
        compose.onNodeWithTag("rewind-native-map").performTouchInput { click(Offset(width * 0.75f, height * 0.5f)) }
        compose.onNodeWithText("Next moment").assertIsDisplayed()
    }

    @Test fun singleLocationRendersOneRealNeighborhood() {
        showMap(listOf(MapPanelPoint(36.1699, -115.1398)))
        awaitMap()
        capture("rewind-single-location-map")
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

    private fun showMap(
        points: List<MapPanelPoint>,
        mapsEnabled: Boolean? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalGoogleMapsAvailabilityOverride provides mapsEnabled) {
                LogDateTheme {
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
                        modifier = Modifier.fillMaxSize(),
                    ) { RewindStoryContent(it) }
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
        compose.waitUntil(10_000) { device.takeScreenshot(file) }
        val map =
            compose
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Map loaded"))
                .fetchSemanticsNodes()
                .firstOrNull()
        if (map != null) {
            val bounds = map.boundsInWindow
            val image = BitmapFactory.decodeFile(file.absolutePath)
            val gapY = (bounds.top - 8).toInt()
            assertEquals(
                "Native map must not paint over the story heading",
                image.getPixel((bounds.left - 8).toInt(), gapY),
                image.getPixel(bounds.center.x.toInt(), gapY),
            )
            image.recycle()
        }
    }
}
