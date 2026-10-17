package app.logdate.wear.tile

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.TileBuilders

private const val PREVIEW_RESOURCES_VERSION = "1"

/** Wraps a preview layout the way [Material3TileService] wraps a tile's. */
internal fun previewTile(layout: LayoutElementBuilders.LayoutElement): TileBuilders.Tile =
    TileBuilders.Tile
        .Builder()
        .setResourcesVersion(PREVIEW_RESOURCES_VERSION)
        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
        .build()

/** The resources for [previewTile]'s version; the tiles use no images. */
internal fun previewResources(): ResourceBuilders.Resources =
    ResourceBuilders.Resources
        .Builder()
        .setVersion(PREVIEW_RESOURCES_VERSION)
        .build()
