package app.logdate.wear.v2.surfaces

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import app.logdate.wear.complication.OnThisDayComplicationService
import app.logdate.wear.complication.StreakComplicationService
import app.logdate.wear.tile.OnThisDayTileService
import app.logdate.wear.tile.TodaySummaryTileService

/**
 * The watch's tiles and complications. Their class names never change once shipped, because watch
 * faces and the tile carousel refer to them by name.
 */
object SurfaceCatalog {
    /** Tiles to redraw when moments change. */
    val TILES: List<Class<out TileService>> =
        listOf(
            TodaySummaryTileService::class.java,
            OnThisDayTileService::class.java,
        )

    /** Complications to redraw when moments change. */
    val COMPLICATIONS: List<Class<out ComplicationDataSourceService>> =
        listOf(
            StreakComplicationService::class.java,
            OnThisDayComplicationService::class.java,
        )

    /** Surfaces only the new watch app offers; off while it is off. */
    val NEW_APP_ONLY: List<String> =
        listOf(
            "app.logdate.wear.tile.OnThisDayTileService",
            "app.logdate.wear.complication.OnThisDayComplicationService",
        )

    /** Surfaces the new watch app retires; off while it is on. */
    val CURRENT_APP_ONLY: List<String> =
        listOf(
            "app.logdate.wear.complication.EntryCountComplicationService",
            "app.logdate.wear.complication.MoodComplicationService",
            "app.logdate.wear.tile.QuickCaptureTileService",
        )
}

/** Turns one of the app's own components on or off. */
fun interface ComponentSwitch {
    fun setEnabled(
        className: String,
        enabled: Boolean,
    )
}

/** [ComponentSwitch] through the package manager, without restarting the app. */
class PackageManagerComponentSwitch(
    private val context: Context,
) : ComponentSwitch {
    override fun setEnabled(
        className: String,
        enabled: Boolean,
    ) {
        val component = ComponentName(context.packageName, className)
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(component) == state) return
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}

/** Offers the new watch app's surfaces and retires the ones it replaces while it is on, and the reverse while it is off. */
class SurfaceComponents(
    private val switch: ComponentSwitch,
    private val newAppOnly: List<String> = SurfaceCatalog.NEW_APP_ONLY,
    private val currentAppOnly: List<String> = SurfaceCatalog.CURRENT_APP_ONLY,
) {
    fun apply(newAppOn: Boolean) {
        newAppOnly.forEach { switch.setEnabled(it, newAppOn) }
        currentAppOnly.forEach { switch.setEnabled(it, !newAppOn) }
    }
}
