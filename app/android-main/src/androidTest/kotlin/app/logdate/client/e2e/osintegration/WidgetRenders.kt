package app.logdate.client.e2e.osintegration

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.composeForPreview
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders Glance widgets the way a launcher draws them, for the widget references in the OS
 * integration screenshots.
 *
 * Glance turns a widget into RemoteViews, and a launcher inflates those into ordinary views. This
 * does the same on the device: it composes the widget's preview (the content the widget picker
 * shows), inflates the RemoteViews with a context configured for each [Look], and draws the views
 * into a bitmap, so dark mode, right-to-left layout, and large text come from Android's own
 * resource handling.
 */
object WidgetRenders {
    /** A system setting a person can change that a widget has to follow. */
    enum class Look(
        val label: String,
    ) {
        Light("light"),
        Dark("dark"),
        RightToLeft("rtl"),
        LargeText("large-text"),
    }

    /** Draws [widget]'s preview at [widthDp] by [heightDp] in [look], on a launcher-like background. */
    fun render(
        widget: GlanceAppWidget,
        widthDp: Int,
        heightDp: Int,
        look: Look,
    ): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.withLook(look)
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        val info =
            AppWidgetProviderInfo().apply {
                minWidth = width
                minHeight = height
            }
        val remoteViews = runBlocking { widget.composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, info) }
        // A launcher's widget host takes its direction from the window; a detached view defaults to
        // left-to-right, so the host is set to the configuration's direction.
        val host = FrameLayout(context).apply { layoutDirection = context.resources.configuration.layoutDirection }
        val view = remoteViews.apply(context, host)
        host.addView(view, FrameLayout.LayoutParams(width, height))
        val margin = (MARGIN_DP * density).roundToInt()
        val bitmap = Bitmap.createBitmap(width + margin * 2, height + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (look == Look.Dark) DARK_BACKGROUND else LIGHT_BACKGROUND)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        canvas.translate(margin.toFloat(), margin.toFloat())
        host.draw(canvas)
        return bitmap
    }

    private fun Context.withLook(look: Look): Context {
        val configuration = Configuration(resources.configuration)
        val night = if (look == Look.Dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        if (look == Look.RightToLeft) {
            val arabic = Locale.forLanguageTag("ar")
            configuration.setLocale(arabic)
            configuration.setLayoutDirection(arabic)
        }
        if (look == Look.LargeText) configuration.fontScale = 2f
        return createConfigurationContext(configuration)
    }

    private const val MARGIN_DP = 16
    private const val LIGHT_BACKGROUND = 0xFFE9E6EF.toInt()
    private const val DARK_BACKGROUND = 0xFF1B1A20.toInt()
}
