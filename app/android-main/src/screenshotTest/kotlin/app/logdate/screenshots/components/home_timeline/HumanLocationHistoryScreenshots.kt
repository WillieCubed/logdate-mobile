@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:package-name")

package app.logdate.screenshots.components.home_timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.location.timeline.ui.history.HistoryItemKind
import app.logdate.feature.location.timeline.ui.history.HistoryItemUi
import app.logdate.feature.location.timeline.ui.history.HistoryMemoryKind
import app.logdate.feature.location.timeline.ui.history.HistoryMemoryUi
import app.logdate.feature.location.timeline.ui.history.HistoryPlaceUi
import app.logdate.feature.location.timeline.ui.history.HistoryTab
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryActions
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryContent
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryDetailContent
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryState
import app.logdate.feature.location.timeline.ui.history.HumanHistoryMap
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.shared.model.location.TravelMode
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val cafeMemory = HistoryMemoryUi("coffee-note", "A quiet table, a good book, and nowhere to rush.", HistoryMemoryKind.Text)
private val historyDay =
    HumanLocationHistoryState(
        dateLabel = "Tuesday, September 29",
        daySummary = "6 visits in your day",
        selectedItemId = "cafe-morning",
        items =
            listOf(
                HistoryItemUi("home-morning", HistoryItemKind.Visit, "Home", "Until 9:10 AM", ""),
                HistoryItemUi("walk-cafe", HistoryItemKind.Journey, "Walked", "9:10–9:28 AM", "18 min"),
                HistoryItemUi(
                    "cafe-morning",
                    HistoryItemKind.Visit,
                    "Mothership Coffee",
                    "9:28–10:45 AM",
                    "Arts District · 1 hr 17 min",
                    listOf(cafeMemory),
                ),
                HistoryItemUi(
                    "walk-library",
                    HistoryItemKind.Journey,
                    "Walked",
                    "10:45–11:12 AM",
                    "27 min",
                ),
                HistoryItemUi(
                    "library",
                    HistoryItemKind.Visit,
                    "Las Vegas Library",
                    "11:12 AM–1:20 PM",
                    "2 hr 8 min",
                    listOf(
                        HistoryMemoryUi("library-photo", "The book I came for", HistoryMemoryKind.Image),
                        HistoryMemoryUi(
                            "library-audio",
                            "A thought to come back to",
                            HistoryMemoryKind.Audio,
                            audio =
                                MomentAudioUiState(
                                    uri = "file:///screenshot-fixtures/library.m4a",
                                    durationMs = 42000,
                                    transcript = "I like how a familiar place can make room for a new idea.",
                                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000810"),
                                ),
                        ),
                    ),
                ),
                HistoryItemUi("travelling", HistoryItemKind.Journey, "Travelling", "1:20–1:45 PM", "Activity not confirmed"),
                HistoryItemUi(
                    "cafe-afternoon",
                    HistoryItemKind.Visit,
                    "Mothership Coffee",
                    "1:45–2:10 PM",
                    "Back for another coffee · 25 min",
                ),
                HistoryItemUi("bus", HistoryItemKind.Journey, "By bus", "2:10–2:42 PM", "Confirmed by you · 32 min"),
                HistoryItemUi("brief", HistoryItemKind.Visit, "Near Baker Park", "2:42 PM", "Briefly seen here; visit length unknown"),
                HistoryItemUi(
                    "gap",
                    HistoryItemKind.Gap,
                    "Part of your day is missing",
                    "2:43–3:20 PM",
                    "No location was recorded during this time.",
                ),
                HistoryItemUi(
                    "home-evening",
                    HistoryItemKind.Visit,
                    "Home",
                    "From 3:20 PM",
                    memories =
                        listOf(
                            HistoryMemoryUi("home-video", "The evening light", HistoryMemoryKind.Video, "0:18"),
                        ),
                ),
            ),
        places =
            listOf(
                HistoryPlaceUi("cafe", "Mothership Coffee", "2 visits today · Arts District", listOf(cafeMemory)),
                HistoryPlaceUi("library", "Las Vegas Library", "Visited today · 2 memories"),
                HistoryPlaceUi("home", "Home", "Last visited today"),
            ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun HumanLocationHistoryDayScreenshot() = HistoryScene(historyDay)

@PreviewTest
@Preview(name = "History Standalone", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryStandaloneScreenshot() = HistoryScene(historyDay, showTitle = true)

@PreviewTest
@Preview(name = "History Large Text", device = PHONE, fontScale = 1.8f, showBackground = true)
@Composable
fun HumanLocationHistoryLargeTextScreenshot() = HistoryScene(historyDay)

@PreviewTest
@Preview(name = "History Places", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryPlacesScreenshot() = HistoryScene(historyDay.copy(tab = HistoryTab.Places, placesFilterLabel = "30 days"))

@PreviewTest
@Preview(name = "History Recovery", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryRecoveryScreenshot() =
    HistoryScene(
        historyDay.copy(recoveryMessage = "Location is paused. Your saved days and memories are still here."),
    )

@PreviewTest
@Preview(name = "History Visit Detail", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryDetailScreenshot() = HistoryDetailScene("cafe-morning")

@PreviewTest
@Preview(name = "History Audio", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryAudioScreenshot() = HistoryDetailScene("library")

@Composable
private fun HistoryScene(state: HumanLocationHistoryState, showTitle: Boolean = false) {
    ScreenshotTheme {
        HumanLocationHistoryContent(
            state,
            HumanLocationHistoryActions(),
            mapContent = { modifier -> HistoryMapFixture(modifier) },
            toolbarActions = { IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More location options") } },
            showTitle = showTitle,
        )
    }
}

@Composable
private fun HistoryMapFixture(modifier: Modifier) {
    val time = Instant.parse("2026-09-29T10:00:00Z")
    val home = SemanticPlace("home", "Home", 36.1699, -115.1398)
    val cafe = SemanticPlace("cafe", "Mothership Coffee", 36.1662, -115.1431)
    val library = SemanticPlace("library", "Las Vegas Library", 36.1624, -115.1452)
    val park = SemanticPlace("park", "Near Baker Park", 36.1595, -115.1500)
    val visits = listOf(
        "home-morning" to home,
        "cafe-morning" to cafe,
        "library" to library,
        "cafe-afternoon" to cafe,
        "brief" to park,
        "home-evening" to home,
    ).map { (id, place) ->
        PlaceVisit(id, time, time, listOf(id), place.latitude, place.longitude, true, place)
    }
    fun leg(id: String, mode: TravelMode, from: SemanticPlace, to: SemanticPlace): JourneyLeg =
        JourneyLeg(
            id,
            time,
            time,
            listOf(from.id, to.id),
            mode,
            listOf(from, to).mapIndexed { index, place ->
                LocationObservation("$id-$index", "fixture", "fixture", time, place.latitude, place.longitude)
            },
        )
    HumanHistoryMap(
        visits +
            listOf(
                leg("walk-cafe", TravelMode.WALKING, home, cafe),
                leg("walk-library", TravelMode.WALKING, cafe, library),
                leg("bus", TravelMode.BUS, cafe, park),
            ),
        "cafe-morning",
        {},
        modifier,
        {},
    )
}

@PreviewTest
@Preview(name = "History Places Map", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryPlacesMapScreenshot() = HistoryScene(historyDay.copy(tab = HistoryTab.Places, placesMapVisible = true))

@PreviewTest
@Preview(name = "History Search Empty", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryNoSearchResultsScreenshot() = HistoryScene(historyDay.copy(tab = HistoryTab.Places, placesQuery = "park"))

@PreviewTest
@Preview(name = "History Places Empty", device = PHONE, showBackground = true)
@Composable
fun HumanLocationHistoryEmptyPlacesScreenshot() = HistoryScene(historyDay.copy(tab = HistoryTab.Places, places = emptyList()))

@Composable
private fun HistoryDetailScene(id: String) {
    ScreenshotTheme {
        Surface(Modifier.fillMaxSize()) {
            HumanLocationHistoryDetailContent(
                item = historyDay.items.first { it.id == id },
                actions = HumanLocationHistoryActions(),
                onRequestDelete = {},
            )
        }
    }
}
