@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.watch

import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.renderer.TileRenderer
import com.google.common.util.concurrent.ListenableFuture
import io.github.aakira.napier.Napier

@Composable
actual fun NativeTodayTilePreview(
    presentation: TodayTilePresentation?,
    modifier: Modifier,
) {
    if (presentation == null) return
    NativeWatchTilePreview(presentation, modifier) { inactive -> logDateTodayTileLayout(presentation, inactive, inactive) }
}

@Composable
actual fun NativeOnThisDayTilePreview(
    presentation: OnThisDayTilePresentation,
    modifier: Modifier,
) {
    NativeWatchTilePreview(presentation, modifier) { inactive -> logDateOnThisDayTileLayout(presentation, inactive) }
}

@Composable
private fun NativeWatchTilePreview(
    presentation: Any,
    modifier: Modifier,
    content: MaterialScope.(ModifiersBuilders.Clickable) -> LayoutElementBuilders.LayoutElement,
) {
    val context = LocalContext.current
    val previewContext =
        remember(context) {
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1f })
        }
    val layout =
        remember(previewContext, presentation) {
            val device =
                DeviceParametersBuilders.DeviceParameters
                    .Builder()
                    .setScreenWidthDp(192)
                    .setScreenHeightDp(192)
                    .setScreenDensity(previewContext.resources.displayMetrics.density)
                    .setFontScale(1f)
                    .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
                    .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
                    .setRendererSchemaVersion(
                        VersionBuilders.VersionInfo
                            .Builder()
                            .setMajor(1)
                            .setMinor(500)
                            .build(),
                    ).build()
            val inactive =
                ModifiersBuilders.Clickable
                    .Builder()
                    .setId("preview")
                    .setOnClick(ActionBuilders.LoadAction.Builder().build())
                    .build()
            LayoutElementBuilders.Layout
                .Builder()
                .setRoot(
                    materialScope(context = previewContext, deviceConfiguration = device, allowDynamicTheme = false) {
                        content(inactive)
                    },
                ).build()
        }
    key(previewContext, layout) {
        AndroidView(
            modifier = modifier.size(192.dp).clearAndSetSemantics {},
            factory = { NativeTilePreviewHost(previewContext).apply { render(layout) } },
            onReset = null,
            onRelease = NativeTilePreviewHost::release,
            update = {},
        )
    }
}

/** Each layout owns its host; an obsolete async render can never attach to a replacement host. */
private class NativeTilePreviewHost(
    context: android.content.Context,
) : FrameLayout(context) {
    private var pending: ListenableFuture<View>? = null

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = true

    fun render(layout: LayoutElementBuilders.Layout) {
        val executor = ContextCompat.getMainExecutor(context)
        val future =
            TileRenderer(context, executor) { }
                .inflateAsync(
                    layout,
                    ResourceBuilders.Resources
                        .Builder()
                        .setVersion("preview")
                        .build(),
                    this,
                )
        pending = future
        future.addListener({
            if (!future.isCancelled) {
                try {
                    future.get()
                } catch (failure: Exception) {
                    Napier.e("Could not render native watch tile preview", failure)
                }
            }
        }, executor)
    }

    fun release() {
        pending?.cancel(false)
        pending = null
        removeAllViews()
    }
}
