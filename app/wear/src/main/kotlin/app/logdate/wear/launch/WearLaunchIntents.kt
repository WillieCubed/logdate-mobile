package app.logdate.wear.launch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.logdate.wear.presentation.MainActivity
import kotlin.uuid.Uuid

/** Pending intents that open the watch app on a route, for notifications and complications. */
object WearLaunchIntents {
    const val ROUTE_RECORD = "record"
    const val ROUTE_MOMENT = "moment"
    private const val REQUEST_HOME = 3001
    private const val REQUEST_RECORD = 3002
    private const val REQUEST_MOMENT = 3003

    fun home(context: Context): PendingIntent = activity(context, REQUEST_HOME, openApp(context))

    fun record(context: Context): PendingIntent =
        activity(context, REQUEST_RECORD, openApp(context).putExtra(WearLaunchRequest.EXTRA_ROUTE, ROUTE_RECORD))

    /** Opens the moment [noteId]; each moment gets its own pending intent. */
    fun moment(
        context: Context,
        noteId: Uuid,
    ): PendingIntent =
        activity(
            context,
            REQUEST_MOMENT,
            openApp(context)
                .setData(Uri.parse("logdate-watch://moment/$noteId"))
                .putExtra(WearLaunchRequest.EXTRA_ROUTE, ROUTE_MOMENT)
                .putExtra(WearLaunchRequest.EXTRA_NOTE_ID, noteId.toString()),
        )

    private fun openApp(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun activity(
        context: Context,
        requestCode: Int,
        intent: Intent,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
