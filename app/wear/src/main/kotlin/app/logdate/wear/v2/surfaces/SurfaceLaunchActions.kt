package app.logdate.wear.v2.surfaces

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import app.logdate.wear.launch.WearLaunchIntents
import app.logdate.wear.launch.WearLaunchRequest
import app.logdate.wear.presentation.MainActivity
import kotlin.uuid.Uuid

/** Tile actions that open the app on a route, with the extras [WearLaunchIntents] uses for notifications and complications. */
object SurfaceLaunchActions {
    fun home(context: Context): ActionBuilders.LaunchAction = ActionBuilders.launchAction(mainActivity(context))

    fun record(context: Context): ActionBuilders.LaunchAction =
        ActionBuilders.launchAction(
            mainActivity(context),
            mapOf(WearLaunchRequest.EXTRA_ROUTE to ActionBuilders.stringExtra(WearLaunchIntents.ROUTE_RECORD)),
        )

    /** Opens the moment saved as the note [noteId]. */
    fun moment(
        context: Context,
        noteId: Uuid,
    ): ActionBuilders.LaunchAction =
        ActionBuilders.launchAction(
            mainActivity(context),
            mapOf(
                WearLaunchRequest.EXTRA_ROUTE to ActionBuilders.stringExtra(WearLaunchIntents.ROUTE_MOMENT),
                WearLaunchRequest.EXTRA_NOTE_ID to ActionBuilders.stringExtra(noteId.toString()),
            ),
        )

    private fun mainActivity(context: Context): ComponentName = ComponentName(context, MainActivity::class.java)
}
