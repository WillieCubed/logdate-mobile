package app.logdate.client

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import app.logdate.navigation.routes.core.JournalDetail
import app.logdate.navigation.routes.core.NoteViewerRoute
import app.logdate.navigation.routes.core.PostcardViewerRoute
import app.logdate.navigation.routes.core.RewindDetailRoute
import app.logdate.navigation.routes.core.TimelineDetail
import kotlin.uuid.Uuid

private const val PATH_JOURNAL = "journal"
private const val PATH_DAY = "day"
private const val PATH_NOTE = "note"
private const val PATH_POSTCARD = "postcard"
private const val PATH_REWIND = "rewind"

internal val LOGDATE_API_BASE_URL: Uri = Uri.parse(BuildConfig.LOGDATE_API_BASE_URL)

/**
 * Resolves a deep link URI to the corresponding navigation route.
 *
 * Supports both app-scheme URIs (`logdate://journal/{id}`) and
 * web URIs (`https://logdate.app/journal/{id}`).
 *
 * Returns null if the URI doesn't match any known pattern.
 */
fun resolveDeepLinkUri(uri: Uri): NavKey? {
    val segments = uri.pathSegments ?: return null
    val host = uri.host ?: return null

    return when (host) {
        PATH_JOURNAL -> segments.firstOrNull()?.parseUuidTo { JournalDetail(it) }
        PATH_DAY -> segments.firstOrNull()?.parseDateTo { TimelineDetail(it) }
        PATH_NOTE -> segments.firstOrNull()?.parseUuidTo { NoteViewerRoute(it) }
        PATH_POSTCARD -> segments.firstOrNull()?.parseUuidTo { PostcardViewerRoute(it) }
        PATH_REWIND -> segments.firstOrNull()?.parseUuidTo { RewindDetailRoute(it) }
        BuildConfig.LOGDATE_ORIGIN -> resolveWebPath(segments)
        else -> null
    }
}

/**
 * Converts a navigation destination to its canonical logdate.app web URL.
 *
 * This is the inverse of [resolveDeepLinkUri] for web-scheme URIs.
 * Returns null for destinations with no stable addressable URL (list screens, settings, etc.).
 */
fun NavKey.toWebUrl(): String? =
    when (this) {
        is JournalDetail ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_JOURNAL)
                .appendPath(id.toString())
                .build()
                .toString()
        is TimelineDetail ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_DAY)
                .appendPath(day.toString())
                .build()
                .toString()
        is NoteViewerRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_NOTE)
                .appendPath(id.toString())
                .build()
                .toString()
        is PostcardViewerRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_POSTCARD)
                .appendPath(postcardId.toString())
                .build()
                .toString()
        is RewindDetailRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_REWIND)
                .appendPath(id.toString())
                .build()
                .toString()
        else -> null
    }

private fun resolveWebPath(segments: List<String>): NavKey? {
    val type = segments.getOrNull(0) ?: return null
    val value = segments.getOrNull(1) ?: return null
    return when (type) {
        PATH_JOURNAL -> value.parseUuidTo { JournalDetail(it) }
        PATH_DAY -> value.parseDateTo { TimelineDetail(it) }
        PATH_POSTCARD -> value.parseUuidTo { PostcardViewerRoute(it) }
        PATH_REWIND -> value.parseUuidTo { RewindDetailRoute(it) }
        else -> null
    }
}

private inline fun <T : NavKey> String.parseUuidTo(create: (Uuid) -> T): T? = runCatching { create(Uuid.parse(this)) }.getOrNull()

private inline fun <T : NavKey> String.parseDateTo(create: (kotlinx.datetime.LocalDate) -> T): T? =
    runCatching { create(kotlinx.datetime.LocalDate.parse(this)) }.getOrNull()
