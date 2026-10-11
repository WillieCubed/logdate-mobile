package app.logdate.wear.e2e

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.renderer.TileRenderer
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.tile.TodaySummary
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.surfaces.onThisDayTileLayout
import app.logdate.wear.v2.surfaces.todayTileLayout
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Native ProtoLayout renders complement semantic assertions with inspectable watch-size artifacts. */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class SurfaceTileRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val note =
        JournalNote.Text(
            creationTimestamp = Instant.fromEpochMilliseconds(1_760_097_600_000),
            lastUpdated = Instant.fromEpochMilliseconds(1_760_097_600_000),
            content = "Breakfast by the window",
        )
    private val day = OnThisDay(LocalDate(2024, 10, 10), 2, note, 3, "Moving day")

    @Test
    fun render_populated_and_empty_tiles_at_watch_sizes() {
        for ((name, size, scale) in listOf(Triple("small", 192, 1f), Triple("large", 227, 1f), Triple("large_font", 192, 1.24f))) {
            render("today_$name", size, scale) { todayTileLayout(TodaySummary(3, null, note)) }
            render("memory_$name", size, scale) { onThisDayTileLayout(day) }
            render("empty_$name", size, scale) { onThisDayTileLayout(null) }
            render("today_empty_$name", size, scale) { todayTileLayout(TodaySummary(0, null, null)) }
        }
        render("today_compat", 192, 1f, schemaMinor = 0) { todayTileLayout(TodaySummary(3, null, note)) }
        render("memory_compat", 192, 1f, schemaMinor = 0) { onThisDayTileLayout(day) }
        render("empty_compat", 192, 1f, schemaMinor = 0) { onThisDayTileLayout(null) }
    }

    private fun render(
        name: String,
        size: Int,
        scale: Float,
        schemaMinor: Int = 500,
        content: MaterialScope.() -> LayoutElementBuilders.LayoutElement,
    ) {
        val configuration = Configuration(context.resources.configuration).apply { fontScale = scale }
        val renderingContext = context.createConfigurationContext(configuration)
        val density = renderingContext.resources.displayMetrics.density
        val pixels = (size * density).roundToInt()
        val device =
            DeviceParametersBuilders.DeviceParameters
                .Builder()
                .setRendererSchemaVersion(
                    VersionBuilders.VersionInfo
                        .Builder()
                        .setMajor(1)
                        .setMinor(schemaMinor)
                        .build(),
                ).setScreenWidthDp(size)
                .setScreenHeightDp(size)
                .setScreenDensity(density)
                .setFontScale(scale)
                .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
                .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
                .build()
        val element = materialScope(renderingContext, device, allowDynamicTheme = true, layout = content)
        val layout =
            LayoutElementBuilders.Layout
                .Builder()
                .setRoot(element)
                .build()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var parent: FrameLayout
        lateinit var future: ListenableFuture<View>
        instrumentation.runOnMainSync {
            parent = FrameLayout(renderingContext).apply { setBackgroundColor(Color.BLACK) }
            val renderer = TileRenderer(renderingContext, ContextCompat.getMainExecutor(renderingContext)) { }
            future =
                renderer.inflateAsync(
                    layout,
                    ResourceBuilders.Resources
                        .Builder()
                        .setVersion("1")
                        .build(),
                    parent,
                )
        }
        future.get(10, TimeUnit.SECONDS)
        instrumentation.waitForIdleSync()
        val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync {
            val spec = View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY)
            parent.measure(spec, spec)
            parent.layout(0, 0, pixels, pixels)
            val canvas = Canvas(bitmap)
            canvas.clipPath(Path().apply { addOval(RectF(0f, 0f, pixels.toFloat(), pixels.toFloat()), Path.Direction.CW) })
            parent.draw(canvas)
        }
        val colors = IntArray(pixels * pixels)
        bitmap.getPixels(colors, 0, pixels, 0, 0, pixels, pixels)
        assertTrue(colors.toSet().size > 20, "The native tile render must contain visible content: $name")
        val output = requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir"))
        File(
            File(output).apply {
                mkdirs()
            },
            "tile_$name.png",
        ).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
