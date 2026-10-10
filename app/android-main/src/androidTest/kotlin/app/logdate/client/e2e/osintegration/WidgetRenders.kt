package app.logdate.client.e2e.osintegration

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
        // Glance's layouts take their direction from the default locale (layoutDirection="locale"),
        // which a configuration context leaves alone, so right-to-left also sets the default locale
        // while it renders, as an Arabic system would have.
        val defaultLocale = Locale.getDefault()
        if (look == Look.RightToLeft) Locale.setDefault(RIGHT_TO_LEFT_LOCALE)
        try {
            return draw(widget, widthDp, heightDp, look)
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    private fun draw(
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
        // A widget with several preview sizes gets one layout per size, and the host picks the one
        // for its own size, as a launcher's does. RemoteViews.apply on a plain view always picks the
        // smallest. A host takes its direction from its window; a detached one defaults to
        // left-to-right, so it is set to the configuration's direction.
        val host = AppWidgetHostView(context).apply { layoutDirection = context.resources.configuration.layoutDirection }
        host.layoutAt(width, height)
        host.updateAppWidget(remoteViews)
        val margin = (MARGIN_DP * density).roundToInt()
        val bitmap = Bitmap.createBitmap(width + margin * 2, height + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (look == Look.Dark) DARK_BACKGROUND else LIGHT_BACKGROUND)
        host.layoutAt(width, height)
        canvas.translate(margin.toFloat(), margin.toFloat())
        host.draw(canvas)
        return bitmap
    }

    private fun View.layoutAt(
        width: Int,
        height: Int,
    ) {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, width, height)
    }

    private fun Context.withLook(look: Look): Context {
        val configuration = Configuration(resources.configuration)
        val night = if (look == Look.Dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        if (look == Look.RightToLeft) {
            configuration.setLocale(RIGHT_TO_LEFT_LOCALE)
            configuration.setLayoutDirection(RIGHT_TO_LEFT_LOCALE)
        }
        if (look == Look.LargeText) configuration.fontScale = 2f
        return createConfigurationContext(configuration)
    }

    private val RIGHT_TO_LEFT_LOCALE = Locale.forLanguageTag("ar")
    private const val MARGIN_DP = 16
    private const val LIGHT_BACKGROUND = 0xFFE9E6EF.toInt()
    private const val DARK_BACKGROUND = 0xFF1B1A20.toInt()
}
