package app.logdate.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ButtonGroupDefaults
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tile
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.launch.isWearStartupReady
import app.logdate.wear.presentation.MainActivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val FRESHNESS_MINUTES = 15

private const val ROUTE_QUICK_RECORD = "quick_record"
private const val ROUTE_MEMORIES = "memories"
private const val ROUTE_MOOD = "mood"
private const val ROUTE_QUICK_TEXT = "quick_text"

/**
 * Quick Capture tile showing four capture buttons and today's entry count.
 *
 * Tapping a button launches the app directly to the corresponding capture screen.
 */
class QuickCaptureTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        if (!this@QuickCaptureTileService.isWearStartupReady()) return quickCaptureTile(entryCount = null)
        return quickCaptureTile(fetchTodayEntryCount())
    }

    private suspend fun fetchTodayEntryCount(): Int =
        try {
            val repository =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            repository.observeNotesForDay(today).first().size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to count today's notes for the Quick Capture tile", e)
            0
        }
}

internal fun MaterialScope.quickCaptureTile(entryCount: Int?): TileBuilders.Tile =
    tile(
        timeline = TimelineBuilders.Timeline.fromLayoutElement(quickCaptureLayout(entryCount)),
        freshness = FRESHNESS_MINUTES.minutes,
    )

internal fun MaterialScope.quickCaptureLayout(entryCount: Int?): LayoutElementBuilders.LayoutElement {
    val entryLabel =
        entryCount?.let { context.resources.getQuantityString(R.plurals.wear_tile_entry_count, it, it) }
            ?: context.getString(R.string.wear_startup_opening)

    return primaryLayout(
        titleSlot = { text(context.getString(R.string.app_name).layoutString) },
        mainSlot = {
            LayoutElementBuilders.Column
                .Builder()
                .setWidth(expand())
                .setHeight(expand())
                .addContent(
                    buttonGroup {
                        buttonGroupItem { captureButton(R.string.wear_tile_record, ROUTE_QUICK_RECORD) }
                        buttonGroupItem { captureButton(R.string.wear_tile_memories, ROUTE_MEMORIES) }
                    },
                ).addContent(ButtonGroupDefaults.DEFAULT_SPACER_BETWEEN_BUTTON_GROUPS)
                .addContent(
                    buttonGroup {
                        buttonGroupItem { captureButton(R.string.wear_tile_mood, ROUTE_MOOD) }
                        buttonGroupItem { captureButton(R.string.wear_tile_text, ROUTE_QUICK_TEXT) }
                    },
                ).build()
        },
        bottomSlot = {
            text(
                entryLabel.layoutString,
                typography = Typography.LABEL_SMALL,
                color = colorScheme.onSurfaceVariant,
            )
        },
    )
}

private fun MaterialScope.captureButton(
    labelRes: Int,
    route: String,
): LayoutElementBuilders.LayoutElement =
    textButton(
        onClick = clickable(quickCaptureAction(context, route), route),
        labelContent = { text(context.getString(labelRes).layoutString) },
        width = expand(),
        height = expand(),
    )

internal fun quickCaptureAction(
    context: Context,
    route: String,
): ActionBuilders.LaunchAction =
    ActionBuilders.launchAction(
        ComponentName(context, MainActivity::class.java),
        mapOf(WearLaunchRequest.EXTRA_ROUTE to ActionBuilders.stringExtra(route)),
    )

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun quickCaptureTilePreview(context: Context) =
    TilePreviewData(onTileResourceRequest = { previewResources() }) {
        previewTile(materialScope(context, it.deviceConfiguration) { quickCaptureLayout(entryCount = 3) })
    }
