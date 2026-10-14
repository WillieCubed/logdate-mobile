package app.logdate.wear.tile

import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tile
import app.logdate.ui.watch.OnThisDayTilePresentation
import app.logdate.ui.watch.logDateOnThisDayTileLayout
import app.logdate.wear.R
import app.logdate.wear.v2.surfaces.SurfaceLaunchActions
import kotlin.time.Duration.Companion.seconds

/** Uses the shared tile surface while encrypted startup is pending; never invents an empty library. */
internal fun MaterialScope.startupTile(): TileBuilders.Tile =
    tile(
        timeline =
            TimelineBuilders.Timeline.fromLayoutElement(
                logDateOnThisDayTileLayout(
                    OnThisDayTilePresentation(
                        title = context.getString(R.string.wear_startup_opening),
                        yearsAgo = null,
                        summary = context.getString(R.string.wear_tile_summary_open),
                    ),
                    clickable(SurfaceLaunchActions.home(context), "open_home"),
                ),
            ),
        freshness = 1.seconds,
    )
