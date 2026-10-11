package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.testing.LayoutElementAssertionsProvider
import androidx.wear.protolayout.testing.hasClickable
import androidx.wear.protolayout.testing.hasText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.tile.TodaySummary
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.surfaces.OnThisDayTileIds
import app.logdate.wear.v2.surfaces.SurfaceLaunchActions
import app.logdate.wear.v2.surfaces.TodayTileIds
import app.logdate.wear.v2.surfaces.onThisDayTileLayout
import app.logdate.wear.v2.surfaces.todayTileLayout
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Checks the new tiles' layouts with protolayout-testing; tiles are not Compose, so they have no screenshots. */
@RunWith(AndroidJUnit4::class)
class SurfaceLayoutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val note = JournalNote.Text(creationTimestamp = NOON, lastUpdated = NOON, content = "Breakfast by the window")
    private val device =
        DeviceParametersBuilders.DeviceParameters
            .Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2f)
            .setFontScale(1f)
            .setRendererSchemaVersion(
                VersionBuilders.VersionInfo
                    .Builder()
                    .setMajor(1)
                    .setMinor(500)
                    .build(),
            ).setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .build()

    @Test
    fun today_counts_the_moments_and_shows_the_newest() {
        val layout = layout { todayTileLayout(TodaySummary(entryCount = 3, feeling = null, latestEntry = note)) }

        layout.onElement(hasText("Today")).assertExists()
        layout.onElement(hasText("3 moments today")).assertExists()
        layout.onElement(hasText("Breakfast by the window")).assertExists()
    }

    @Test
    fun today_records_from_its_edge_button_even_when_empty() {
        val layout = layout { todayTileLayout(TodaySummary(entryCount = 0, feeling = null, latestEntry = null)) }

        layout.onElement(hasText("Nothing recorded yet today")).assertExists()
        layout.onElement(hasText("Record")).assertExists()
        layout.onElement(hasClickable(SurfaceLaunchActions.record(context), TodayTileIds.RECORD)).assertExists()
    }

    @Test
    fun the_tile_actions_open_the_recorder_a_moment_and_home() {
        assertEquals(WearLaunchRequest.Record, SurfaceLaunchActions.record(context).request())
        assertEquals(WearLaunchRequest.Moment(note.uid), SurfaceLaunchActions.moment(context, note.uid).request())
        assertNull(SurfaceLaunchActions.home(context).request())
    }

    @Test
    fun on_this_day_shows_how_long_ago_and_opens_its_moment() {
        val day = OnThisDay(date = LocalDate(2025, 10, 10), yearsAgo = 1, leadMoment = note, momentCount = 2, summary = "")

        val layout = layout { onThisDayTileLayout(day) }

        layout.onElement(hasText("On this day")).assertExists()
        layout.onElement(hasText("1 year ago")).assertExists()
        layout.onElement(hasText("Breakfast by the window")).assertExists()
        layout.onElement(hasClickable(SurfaceLaunchActions.moment(context, note.uid), OnThisDayTileIds.MOMENT)).assertExists()
    }

    @Test
    fun on_this_day_prefers_the_recalled_summary() {
        val day = OnThisDay(date = LocalDate(2023, 10, 10), yearsAgo = 3, leadMoment = note, momentCount = 4, summary = "Moving day")

        val layout = layout { onThisDayTileLayout(day) }

        layout.onElement(hasText("3 years ago")).assertExists()
        layout.onElement(hasText("Moving day")).assertExists()
        layout.onElement(hasText("Breakfast by the window")).assertDoesNotExist()
    }

    @Test
    fun without_a_day_on_this_day_says_so_and_opens_the_app() {
        val layout = layout { onThisDayTileLayout(null) }

        layout.onElement(hasText("Nothing from this day in past years")).assertExists()
        layout.onElement(hasClickable(SurfaceLaunchActions.home(context), OnThisDayTileIds.OPEN)).assertExists()
    }

    private fun layout(content: MaterialScope.() -> LayoutElementBuilders.LayoutElement): LayoutElementAssertionsProvider =
        LayoutElementAssertionsProvider(
            materialScope(context = context, deviceConfiguration = device, allowDynamicTheme = false, layout = content),
        )

    private fun ActionBuilders.LaunchAction.request(): WearLaunchRequest? {
        val extras = requireNotNull(androidActivity).keyToExtraMapping
        return WearLaunchRequest.parse(
            route = (extras[WearLaunchRequest.EXTRA_ROUTE] as? ActionBuilders.AndroidStringExtra)?.value,
            noteId = (extras[WearLaunchRequest.EXTRA_NOTE_ID] as? ActionBuilders.AndroidStringExtra)?.value,
        )
    }

    private companion object {
        val NOON: Instant = Instant.fromEpochMilliseconds(1_760_097_600_000)
    }
}
