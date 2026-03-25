@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
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

@Composable
private fun MemoryContent(state: OnThisDayWidgetState.HasMemory) {
    val context = LocalContext.current
    val launchIntent = createWidgetLaunchIntent(dateIso = state.dateIso)

    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_widget_on_this_day),
                title = context.getString(R.string.widget_header),
            )
        },
        modifier =
            GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(launchIntent)),
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = state.dateFormatted,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = state.summary,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = 3,
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = context.getString(R.string.widget_view_memory),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier =
                        GlanceModifier
                            .padding(vertical = 8.dp)
                            .clickable(actionStartActivity(launchIntent)),
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    val context = LocalContext.current

    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_widget_on_this_day),
                title = context.getString(R.string.widget_header),
            )
        },
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_loading_message),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                    ),
            )
        }
    }
}

@Composable
private fun NewUserContent() {
    val context = LocalContext.current
    val launchIntent = createWidgetLaunchIntent()

    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_widget_on_this_day),
                title = context.getString(R.string.widget_header),
            )
        },
        modifier =
            GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(launchIntent)),
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_new_user_message),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                    ),
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            Text(
                text = context.getString(R.string.widget_open_app),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                modifier =
                    GlanceModifier
                        .clickable(actionStartActivity(launchIntent)),
            )
        }
    }
}

@Composable
private fun NoMemoryTodayContent() {
    val context = LocalContext.current
    val launchIntent = createWidgetLaunchIntent()

    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_widget_on_this_day),
                title = context.getString(R.string.widget_header),
            )
        },
        modifier =
            GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(launchIntent)),
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_empty_message),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                    ),
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            Text(
                text = context.getString(R.string.widget_open_app),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                modifier =
                    GlanceModifier
                        .clickable(actionStartActivity(launchIntent)),
            )
        }
    }
}
