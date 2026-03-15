package app.logdate.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val RESOURCES_VERSION = "1"

private const val EXTRA_TILE_ROUTE = "tile_route"
private const val ROUTE_QUICK_RECORD = "quick_record"
private const val ROUTE_VOICE_NOTE = "voice_note"
private const val ROUTE_MOOD = "mood"
private const val ROUTE_QUICK_TEXT = "quick_text"

/**
 * Quick Capture tile showing four capture buttons and today's entry count.
 *
 * Tapping a button launches the app directly to the corresponding capture screen.
 */
@OptIn(ExperimentalHorologistApi::class)
class QuickCaptureTileService : SuspendingTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val entryCount = fetchTodayEntryCount()
        return quickCaptureTile(requestParams, this, entryCount)
    }

    private suspend fun fetchTodayEntryCount(): Int {
        return try {
            val repository = org.koin.java.KoinJavaComponent
                .getKoin()
                .get<JournalNotesRepository>()
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            repository.observeNotesForDay(today).first().size
        } catch (e: Exception) {
            0
        }
    }
}

internal fun quickCaptureTile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    entryCount: Int,
): TileBuilders.Tile {
    val timeline = TimelineBuilders.Timeline.Builder()
        .addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    LayoutElementBuilders.Layout.Builder()
                        .setRoot(quickCaptureLayout(requestParams, context, entryCount))
                        .build(),
                )
                .build(),
        )
        .build()

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timeline)
        .setFreshnessIntervalMillis(15 * 60 * 1000L) // Refresh every 15 minutes
        .build()
}

private fun quickCaptureLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    entryCount: Int,
): LayoutElementBuilders.LayoutElement {
    val entryLabel = context.resources.getQuantityString(
        R.plurals.wear_tile_entry_count,
        entryCount,
        entryCount,
    )

    return PrimaryLayout.Builder(requestParams.deviceConfiguration)
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(
            Text.Builder(context, context.getString(R.string.app_name))
                .setColor(argb(Colors.DEFAULT.primary))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .build(),
        )
        .setContent(
            captureButtonRow(context),
        )
        .setSecondaryLabelTextContent(
            Text.Builder(context, entryLabel)
                .setColor(argb(Colors.DEFAULT.onSurface))
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .build(),
        )
        .build()
}

private fun captureButtonRow(
    context: Context,
): LayoutElementBuilders.LayoutElement {
    return LayoutElementBuilders.Row.Builder()
        .setWidth(wrap())
        .setHeight(wrap())
        .addContent(
            captureButton(
                context = context,
                label = context.getString(R.string.wear_tile_record),
                route = ROUTE_QUICK_RECORD,
            ),
        )
        .addContent(spacer(8f))
        .addContent(
            captureButton(
                context = context,
                label = context.getString(R.string.wear_tile_voice),
                route = ROUTE_VOICE_NOTE,
            ),
        )
        .addContent(spacer(8f))
        .addContent(
            captureButton(
                context = context,
                label = context.getString(R.string.wear_tile_mood),
                route = ROUTE_MOOD,
            ),
        )
        .addContent(spacer(8f))
        .addContent(
            captureButton(
                context = context,
                label = context.getString(R.string.wear_tile_text),
                route = ROUTE_QUICK_TEXT,
            ),
        )
        .build()
}

private fun captureButton(
    context: Context,
    label: String,
    route: String,
): LayoutElementBuilders.LayoutElement {
    val launchAction = ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(context.packageName)
                .setClassName(
                    ComponentName(context, "app.logdate.wear.presentation.MainActivity")
                        .className,
                )
                .addKeyToExtraMapping(
                    EXTRA_TILE_ROUTE,
                    ActionBuilders.AndroidStringExtra.Builder()
                        .setValue(route)
                        .build(),
                )
                .build(),
        )
        .build()

    val clickable = ModifiersBuilders.Clickable.Builder()
        .setOnClick(launchAction)
        .setId(route)
        .build()

    return LayoutElementBuilders.Box.Builder()
        .setWidth(dp(48f))
        .setHeight(dp(48f))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setClickable(clickable)
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(Colors.DEFAULT.surface))
                        .setCorner(
                            ModifiersBuilders.Corner.Builder()
                                .setRadius(dp(24f))
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )
        .addContent(
            Text.Builder(context, label)
                .setColor(argb(Colors.DEFAULT.onSurface))
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .build(),
        )
        .build()
}

private fun spacer(widthDp: Float): LayoutElementBuilders.LayoutElement {
    return LayoutElementBuilders.Spacer.Builder()
        .setWidth(dp(widthDp))
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun quickCaptureTilePreview(context: Context) = TilePreviewData(
    onTileResourceRequest = {
        ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
    },
) {
    quickCaptureTile(it, context, entryCount = 3)
}
