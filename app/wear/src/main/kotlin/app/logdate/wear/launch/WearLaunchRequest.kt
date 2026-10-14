package app.logdate.wear.launch

import android.content.Intent
import kotlin.uuid.Uuid

/**
 * What a tile, a complication or another app asked the watch app to open.
 *
 * Tiles and complications that older builds placed on a watch face keep sending the route names
 * those builds used, so every one of them still parses.
 */
sealed interface WearLaunchRequest {
    data object Home : WearLaunchRequest

    /** Open the recorder. Today's tiles and complications ask for it as `quick_record` or `voice_note`. */
    data object Record : WearLaunchRequest

    data object Memories : WearLaunchRequest

    data object MoodCheckIn : WearLaunchRequest

    data object QuickText : WearLaunchRequest

    data object Timeline : WearLaunchRequest

    data object RemoteCamera : WearLaunchRequest

    data object Settings : WearLaunchRequest

    /** Mark the current moment. */
    data object Mark : WearLaunchRequest

    data object OnThisDay : WearLaunchRequest

    /** Open the moment saved as the note [noteId]. */
    data class Moment(
        val noteId: Uuid,
    ) : WearLaunchRequest

    companion object {
        /** The intent extra that names the route. Its name comes from the first tiles. */
        const val EXTRA_ROUTE = "tile_route"

        /** The intent extra that names the note a `moment` route opens. */
        const val EXTRA_NOTE_ID = "note_id"

        /** The link the phone's "Open LogDate on watch" sends. */
        private const val TODAY_LINK = "studio.hypertext.logdate://watch/today"

        /** Parses a link the phone sent to this watch, or null for a link this app doesn't open. */
        fun parseLink(link: String?): WearLaunchRequest? {
            if (link == TODAY_LINK) return Home
            val prefix = "studio.hypertext.logdate://watch/moment/"
            if (link?.startsWith(prefix) != true) return null
            return Uuid.parseOrNull(link.removePrefix(prefix))?.let(::Moment)
        }

        /**
         * Parses a route name and, for `moment`, its note ID. Returns null for a missing or unknown
         * route, and for a `moment` without a valid note ID.
         */
        fun parse(
            route: String?,
            noteId: String? = null,
        ): WearLaunchRequest? =
            when (route) {
                "quick_record", "voice_note", "record" -> Record
                "memories" -> Memories
                "mood" -> MoodCheckIn
                "quick_text" -> QuickText
                "timeline" -> Timeline
                "remote_camera" -> RemoteCamera
                "settings" -> Settings
                "rewind", "health" -> Home
                "mark" -> Mark
                "on_this_day" -> OnThisDay
                "moment" -> noteId?.let { Uuid.parseOrNull(it) }?.let { Moment(it) }
                else -> null
            }
    }
}

/** The request this intent carries, or null when it carries none. */
fun Intent.toWearLaunchRequest(): WearLaunchRequest? =
    WearLaunchRequest.parse(
        route = getStringExtra(WearLaunchRequest.EXTRA_ROUTE),
        noteId = getStringExtra(WearLaunchRequest.EXTRA_NOTE_ID),
    ) ?: WearLaunchRequest.parseLink(dataString)
