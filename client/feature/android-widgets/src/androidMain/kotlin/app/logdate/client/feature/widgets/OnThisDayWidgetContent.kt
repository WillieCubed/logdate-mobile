@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle

@Composable
internal fun OnThisDayWidgetContent(state: OnThisDayWidgetState) {
    GlanceTheme {
        when (state) {
            is OnThisDayWidgetState.HasMemory -> MemoryContent(state)
            is OnThisDayWidgetState.Loading -> LoadingContent()
            is OnThisDayWidgetState.NewUser -> NewUserContent()
            is OnThisDayWidgetState.NoMemoryToday -> NoMemoryTodayContent()
        }
    }
}

private fun createWidgetLaunchIntent(dateIso: String? = null): Intent =
    Intent().apply {
        component =
            ComponentName(
                "app.logdate.client",
                "app.logdate.client.MainActivity",
            )
        if (dateIso != null) {
            putExtra(EXTRA_NAV_SOURCE, NAV_SOURCE_ON_THIS_DAY_WIDGET)
            putExtra(EXTRA_WIDGET_TARGET_DATE, dateIso)
        }
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/**
 * Shared widget shell with rounded background and adaptive padding.
 */
@Composable
private fun WidgetContainer(
    modifier: GlanceModifier = GlanceModifier,
    onClick: Intent? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val size = LocalSize.current
    val isCompact = size.height < 120.dp
    val verticalPadding = if (isCompact) 12.dp else 16.dp
    val horizontalPadding = if (isCompact) 16.dp else 20.dp

    val baseModifier =
        modifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)

    val finalModifier =
        if (onClick != null) {
            baseModifier.clickable(actionStartActivity(onClick))
        } else {
            baseModifier
        }

    Column(
        modifier = finalModifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        content()
    }
}

// region Memory state — left-aligned editorial layout

@Composable
private fun MemoryContent(state: OnThisDayWidgetState.HasMemory) {
    val context = LocalContext.current
    val launchIntent = createWidgetLaunchIntent(dateIso = state.dateIso)
    val size = LocalSize.current
    val isCompact = size.height < 120.dp
    // Decode the thumbnail from the internal file path. Glance composition runs on a
    // background coroutine (not the main thread), so file IO here is safe. We use
    // ImageProvider(Bitmap) instead of ImageProvider(Uri) because file:// URIs cannot
    // be shared with the launcher process via RemoteViews.setImageViewUri().
    val thumbnailBitmap = state.thumbnailUri?.let { loadScaledThumbnail(it) }
    val hasThumbnail = thumbnailBitmap != null

    WidgetContainer(onClick = launchIntent) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_on_this_day),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.widget_header),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = state.dateFormatted,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }

        Spacer(modifier = GlanceModifier.height(if (isCompact) 8.dp else 12.dp))

        if (hasThumbnail) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(thumbnailBitmap!!),
                    contentDescription = null,
                    contentScale = androidx.glance.layout.ContentScale.Crop,
                    modifier =
                        GlanceModifier
                            .size(if (isCompact) 56.dp else 64.dp)
                            .cornerRadius(12.dp),
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                Text(
                    text = state.summary,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = if (isCompact) 13.sp else 15.sp,
                        ),
                    maxLines = if (isCompact) 2 else 3,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        } else {
            Text(
                text = state.summary,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (isCompact) 14.sp else 16.sp,
                    ),
                maxLines = if (isCompact) 2 else 4,
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Action
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            FilledButton(
                text = context.getString(R.string.widget_view_memory),
                onClick = actionStartActivity(launchIntent),
            )
        }
    }
}

// endregion

// region Empty states — centered layout with icon anchor

@Composable
private fun LoadingContent() {
    val context = LocalContext.current

    WidgetContainer(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = GlanceModifier.defaultWeight())

        Image(
            provider = ImageProvider(R.drawable.ic_widget_on_this_day),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        Text(
            text = context.getString(R.string.widget_loading_message),
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                ),
        )

        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun NewUserContent() {
    val context = LocalContext.current
    val launchIntent = createWidgetLaunchIntent()

    WidgetContainer(
        onClick = launchIntent,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = GlanceModifier.defaultWeight())

        Image(
            provider = ImageProvider(R.drawable.ic_widget_on_this_day),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        EmptyStateBody(
            message = context.getString(R.string.widget_new_user_message),
            ctaText = context.getString(R.string.widget_new_user_cta),
            ctaIntent = launchIntent,
        )

        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun NoMemoryTodayContent() {
    val context = LocalContext.current
    val launchIntent = createWidgetLaunchIntent()

    WidgetContainer(
        onClick = launchIntent,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = GlanceModifier.defaultWeight())

        Image(
            provider = ImageProvider(R.drawable.ic_widget_on_this_day),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        EmptyStateBody(
            message = context.getString(R.string.widget_empty_message),
            ctaText = context.getString(R.string.widget_empty_cta),
            ctaIntent = launchIntent,
        )

        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun EmptyStateBody(
    message: String,
    ctaText: String,
    ctaIntent: Intent,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.width(260.dp),
    ) {
        Text(
            text = message,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                ),
        )

        Spacer(modifier = GlanceModifier.height(12.dp))

        FilledButton(
            text = ctaText,
            onClick = actionStartActivity(ctaIntent),
        )
    }
}

/**
 * Decodes the image at [uriString] into a downsampled [Bitmap] suitable for
 * embedding in a widget's [RemoteViews].
 *
 * Uses [inSampleSize] = 4 to keep the embedded bitmap small enough to avoid
 * [android.os.TransactionTooLargeException] when the widget is updated.
 *
 * Returns null if the file cannot be decoded (missing file, unsupported format, etc.).
 */
private fun loadScaledThumbnail(uriString: String): Bitmap? =
    try {
        val path =
            if (uriString.startsWith("file://")) {
                Uri.parse(uriString).path ?: uriString
            } else {
                uriString
            }
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
    } catch (_: Exception) {
        null
    }

// endregion
