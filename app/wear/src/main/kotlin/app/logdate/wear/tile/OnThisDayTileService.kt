package app.logdate.wear.tile

import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tile
import app.logdate.wear.v2.surfaces.findOnThisDay
import app.logdate.wear.v2.surfaces.onThisDayTileLayout
import kotlin.time.Duration.Companion.hours

/** The On this day tile. Its manifest entry is disabled; `SurfaceComponents` offers it while the new watch app is on. */
class OnThisDayTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile =
        tile(
            timeline = TimelineBuilders.Timeline.fromLayoutElement(onThisDayTileLayout(findOnThisDay())),
            freshness = 1.hours,
        )
}
