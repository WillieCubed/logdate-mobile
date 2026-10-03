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
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.R
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import app.logdate.wear.capture.MomentNoteKind
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private const val RESOURCES_VERSION = "1"
private const val MAX_PREVIEW_LENGTH = 30

/**
 * Today's Summary tile showing entry count, current mood, and latest entry preview.
 *
 * Tapping opens the app to the timeline view.
 */
@OptIn(ExperimentalHorologistApi::class)
class TodaySummaryTileService : SuspendingTileService() {
    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources =
        ResourceBuilders.Resources
            .Builder()
            .setVersion(RESOURCES_VERSION)
            .build()

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val summary = fetchTodaySummary()
        return todaySummaryTile(requestParams, this, summary)
    }

    private suspend fun fetchTodaySummary(): TodaySummary =
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
            summarizeToday(repository.observeNotesForDay(today).first())
        } catch (e: Exception) {
            Napier.e("Failed to read today's notes for the Wear summary tile", e)
            TodaySummary(entryCount = 0, feeling = null, latestEntry = null)
        }
}

/**
 * What the tile shows: how many notes today has, the feeling of today's newest mood check-in, and
 * the newest note that is not a check-in.
 */
internal data class TodaySummary(
    val entryCount: Int,
    val feeling: Feeling?,
    val latestEntry: JournalNote?,
)

internal fun summarizeToday(notes: List<JournalNote>): TodaySummary =
    TodaySummary(
        entryCount = notes.size,
        feeling = MomentNoteCodec.latestFeeling(notes),
        latestEntry =
            notes
                .filter { MomentNoteCodec.classify(it) != MomentNoteKind.FEELING }
                .maxByOrNull(JournalNote::creationTimestamp),
    )

internal fun todaySummaryTile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    summary: TodaySummary,
): TileBuilders.Tile {
    val timeline =
        TimelineBuilders.Timeline
            .Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry
                    .Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout
                            .Builder()
                            .setRoot(todaySummaryLayout(requestParams, context, summary))
                            .build(),
                    ).build(),
            ).build()

    return TileBuilders.Tile
        .Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timeline)
        .setFreshnessIntervalMillis(15 * 60 * 1000L)
        .build()
}

private fun todaySummaryLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    summary: TodaySummary,
): LayoutElementBuilders.LayoutElement {
    val entryLabel =
        context.resources.getQuantityString(
            R.plurals.wear_tile_entry_count,
            summary.entryCount,
            summary.entryCount,
        )

    val moodLine =
        summary.feeling?.let { feeling ->
            context.getString(R.string.wear_tile_summary_mood, feeling.emoji, context.getString(feeling.labelRes))
        }
    val latestPreview = summary.latestEntry?.let { note -> latestEntryPreview(context, note) }

    return PrimaryLayout
        .Builder(requestParams.deviceConfiguration)
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(
            Text
                .Builder(context, context.getString(R.string.wear_tile_summary_title))
                .setColor(argb(Colors.DEFAULT.primary))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .build(),
        ).setContent(
            summaryContent(context, entryLabel, moodLine, latestPreview),
        ).setSecondaryLabelTextContent(
            Text
                .Builder(context, context.getString(R.string.wear_tile_summary_open))
                .setColor(argb(Colors.DEFAULT.onSurface))
                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                .setModifiers(
                    ModifiersBuilders.Modifiers
                        .Builder()
                        .setClickable(
                            ModifiersBuilders.Clickable
                                .Builder()
                                .setOnClick(createTimelineLaunchAction(context))
                                .setId("open_timeline")
                                .build(),
                        ).build(),
                ).build(),
        ).build()
}

private fun latestEntryPreview(
    context: Context,
    note: JournalNote,
): String =
    when (note) {
        is JournalNote.Text -> note.content.take(MAX_PREVIEW_LENGTH)
        is JournalNote.Audio -> context.getString(R.string.wear_tile_summary_voice_note)
        is JournalNote.Image -> context.getString(R.string.wear_tile_summary_photo)
        is JournalNote.Video -> context.getString(R.string.wear_tile_summary_video)
    }

private fun summaryContent(
    context: Context,
    entryLabel: String,
    moodLine: String?,
    latestPreview: String?,
): LayoutElementBuilders.LayoutElement {
    val column =
        LayoutElementBuilders.Column
            .Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .addContent(
                Text
                    .Builder(context, entryLabel)
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .build(),
            )

    if (moodLine != null) {
        column
            .addContent(spacer(4f))
            .addContent(
                Text
                    .Builder(context, moodLine)
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .build(),
            )
    }

    if (latestPreview != null) {
        column
            .addContent(spacer(4f))
            .addContent(
                Text
                    .Builder(
                        context,
                        context.getString(R.string.wear_tile_summary_latest, latestPreview),
                    ).setColor(argb(Colors.DEFAULT.onSurface))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setMaxLines(2)
                    .build(),
            )
    }

    return column.build()
}

private fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Spacer
        .Builder()
        .setHeight(dp(heightDp))
        .build()

private fun createTimelineLaunchAction(context: Context): ActionBuilders.LaunchAction =
    ActionBuilders.LaunchAction
        .Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity
                .Builder()
                .setPackageName(context.packageName)
                .setClassName(
                    ComponentName(context, "app.logdate.wear.presentation.MainActivity")
                        .className,
                ).addKeyToExtraMapping(
                    "tile_route",
                    ActionBuilders.AndroidStringExtra
                        .Builder()
                        .setValue("timeline")
                        .build(),
                ).build(),
        ).build()

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun todaySummaryTilePreview(context: Context) =
    TilePreviewData(
        onTileResourceRequest = {
            ResourceBuilders.Resources
                .Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        },
    ) {
        todaySummaryTile(
            it,
            context,
            TodaySummary(
                entryCount = 5,
                feeling = Feeling.GOOD,
                latestEntry =
                    JournalNote.Text(
                        creationTimestamp = Instant.fromEpochMilliseconds(0),
                        lastUpdated = Instant.fromEpochMilliseconds(0),
                        content = "Had an amazing lunch...",
                    ),
            ),
        )
    }
