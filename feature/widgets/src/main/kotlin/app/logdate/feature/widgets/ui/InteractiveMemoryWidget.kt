package app.logdate.feature.widgets.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import app.logdate.core.datastore.model.UserData
import app.logdate.feature.widgets.di.getJournalNotesRepository
import app.logdate.feature.widgets.di.getMediaManager
import app.logdate.feature.widgets.di.getPhotoDetailActivity
import app.logdate.feature.widgets.di.getUserStateRepository
import app.logdate.feature.widgets.glance.LogdateGlanceTheme

/**
 * A GlanceAppWidget that displays a photo from a user's journal.
 */
class InteractiveMemoryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val noteRepository = getJournalNotesRepository(context)
        val mediaManager = getMediaManager(context)
        val userStateRepository = getUserStateRepository(context)
        // Use Glance ID to get the photo URI

        provideContent {
            val userState by userStateRepository.userData.collectAsState(UserData.DEFAULT)
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    LogdateGlanceTheme.colors
                }
            ) {
                InteractiveMemoryWidgetContent(
                    Uri.EMPTY,
                    isOnboarded = userState.isOnboarded,
                )
            }
        }
    }
}

@Composable
internal fun InteractiveMemoryWidgetContent(
    photoUri: Uri,
    isOnboarded: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val photoDetailActivity = getPhotoDetailActivity(context)
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.background),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                text = "Home",
                onClick = actionStartActivity(
                    photoDetailActivity,
                    parameters = actionParametersOf(
                        // TODO: Use intent extras to receive the photo ID in the detail activity
                        ActionParameters.Key<String>("photoId") to "home"
                    )
                )
            )
        }
    }
}

/**
 * An action that manually refreshes a PhotoAppWidget's content.
 */
class RefreshMemoryWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        PhotoAppWidget().update(context, glanceId)
        TODO("Sync the widget with the latest data.")
    }
}