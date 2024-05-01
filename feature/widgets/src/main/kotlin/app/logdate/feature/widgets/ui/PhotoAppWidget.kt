package app.logdate.feature.widgets.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.core.datastore.model.UserData
import app.logdate.feature.widgets.R
import app.logdate.feature.widgets.di.getJournalNotesRepository
import app.logdate.feature.widgets.di.getMediaManager
import app.logdate.feature.widgets.di.getPhotoDetailActivity
import app.logdate.feature.widgets.di.getUserStateRepository
import app.logdate.feature.widgets.glance.LogdateGlanceTheme
import app.logdate.feature.widgets.glance.typography
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * A GlanceAppWidget that displays a photo from a user's journal.
 */
class PhotoAppWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val noteRepository = getJournalNotesRepository(context)
        val mediaManager = getMediaManager(context)
        val userStateRepository = getUserStateRepository(context)
        // Use Glance ID to get the photo URI

        coroutineScope {
            ensureRegularUpdates(context)
            provideContent {
                val userState by userStateRepository.userData.collectAsState(UserData.DEFAULT)
                GlanceTheme(
                    colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        GlanceTheme.colors
                    } else {
                        LogdateGlanceTheme.colors
                    }
                ) {
                    PhotoAppWidgetContent(
                        Uri.EMPTY,
                        isOnboarded = userState.isOnboarded,
                    )
                }
            }
        }
    }

    private fun ensureRegularUpdates(context: Context) {
        PhotoAppWidgetWorker.enqueue(context)
    }
}

/**
 * A worker that automatically updates the currently displayed photos in all [PhotoAppWidget]s.
 */
internal class PhotoAppWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORKER_TAG = "PhotoAppWidgetWorker"
        private const val UPDATE_INTERVAL = 15

        internal fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequest.Builder(
                    PhotoAppWidgetWorker::class.java,
                    UPDATE_INTERVAL.minutes.toJavaDuration()
                )
                    .setInitialDelay(UPDATE_INTERVAL.minutes.toJavaDuration())
                    .build()
            )
        }
    }

    override suspend fun doWork(): Result {
        PhotoAppWidget().apply {
            // TODO: Update state
//            applicationContext.weatherWidgetStore.loadWeather()
            // Call update/updateAll in case a Worker for the widget is not currently running.
            updateAll(applicationContext)
        }
        return Result.success()
    }
}


@Composable
internal fun PhotoAppWidgetContent(
    photoUri: Uri,
    isOnboarded: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    val photoDetailActivity = getPhotoDetailActivity(context)
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(
                actionStartActivity(
                    photoDetailActivity,
                    parameters = actionParametersOf(
                        // TODO: Use intent extras to receive the photo ID in the detail activity
                        ActionParameters.Key<String>("mediaId") to "home",
                    )
                )
            )
            .background(GlanceTheme.colors.background),
    ) {
        if (!isOnboarded) {
            SetupContent()
        } else {
            Image(
                provider = ImageProvider(photoUri),
                contentDescription = "A photo from your journal.",
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
    }
}

enum class SetupErrorState(
    @StringRes val title: Int,
    @StringRes val description: Int,
) {
    NO_ACCOUNT(
        R.string.widget_error_no_account_title,
        R.string.widget_error_no_account_description,
    ),
    NO_PHOTOS(
        R.string.widget_error_no_photos_title,
        R.string.widget_error_no_photos_description,
    ),
    UNKNOWN(
        R.string.widget_error_unknown_title,
        R.string.widget_error_unknown_description,
    ),
}

@Composable
private fun SetupContent(
    errorState: SetupErrorState = SetupErrorState.UNKNOWN,
) {
    val title = stringResource(errorState.title)
    val description = stringResource(errorState.description)
    Text(
        stringResource(R.string.widget_error_unknown_title),
        style = GlanceTheme.typography.titleSmall
    )
}

/**
 * An action that manually refreshes a PhotoAppWidget's content.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        PhotoAppWidget().update(context, glanceId)
        TODO("Sync the widget with the latest data.")
    }
}