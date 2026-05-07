package app.logdate.client

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import app.logdate.client.ui.navigation.LocationTimelineRoute
import app.logdate.client.ui.navigation.LogdateHostClass
import app.logdate.client.ui.navigation.SearchRoute
import app.logdate.client.ui.navigation.classifyLogdateHost
import app.logdate.client.ui.navigation.searchRouteFromParams
import app.logdate.feature.events.navigation.EventDetailRoute
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import app.logdate.feature.journals.navigation.NoteDetailRoute
import app.logdate.feature.postcards.navigation.PostcardViewerRoute
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.navigation.TimelineDetailRoute
import kotlin.uuid.Uuid

private const val PATH_JOURNAL = "journal"
private const val PATH_JOURNAL_SHORT = "j"
private const val PATH_DAY = "day"
private const val PATH_NOTE = "note"
private const val PATH_POSTCARD = "postcard"
private const val PATH_REWIND = "rewind"
private const val PATH_LOCATION = "location"
private const val PATH_EVENT = "event"
private const val PATH_SEARCH = "search"
private const val SEARCH_QUERY_PARAM = "q"
private const val SEARCH_TYPE_PARAM = "type"
private const val SEARCH_DATE_PARAM = "date"

internal val LOGDATE_API_BASE_URL: Uri = Uri.parse(BuildConfig.LOGDATE_API_BASE_URL)

/**
 * Resolves a deep link URI to the corresponding navigation route.
 *
 * Supports:
 *   - app-scheme URIs (`logdate://journal/{id}`,
 *     `studio.hypertext.logdate://journal/{id}`),
 *   - apex web URIs (`https://logdate.app/{type}/{id}`),
 *   - tenant subdomain web URIs (`https://{handle}.logdate.app/{type}/{id}`).
 *
 * Returns null if the URI doesn't match any known pattern, including any
 * traffic on a reserved subdomain (`app.logdate.app`, `api.logdate.app`,
 * etc.) and the apex marketing root.
 */
fun resolveDeepLinkUri(uri: Uri): NavKey? {
    val segments = uri.pathSegments ?: return null
    val host = uri.host ?: return null

    return when (classifyLogdateHost(host, BuildConfig.LOGDATE_ORIGIN)) {
        is LogdateHostClass.Apex,
        is LogdateHostClass.TenantClaimed,
        -> resolveWebPath(segments)

        LogdateHostClass.Other -> resolveCustomSchemeHost(host, segments, uri)
    }
}

private fun resolveCustomSchemeHost(
    host: String,
    segments: List<String>,
    uri: Uri,
): NavKey? =
    when (host) {
        PATH_JOURNAL -> segments.firstOrNull()?.parseUuidTo { JournalDetailsRoute(it) }
        PATH_DAY -> segments.firstOrNull()?.parseDateString { TimelineDetailRoute(it) }
        PATH_NOTE -> segments.firstOrNull()?.parseUuidTo { NoteDetailRoute(it) }
        PATH_POSTCARD -> segments.firstOrNull()?.parseUuidTo { PostcardViewerRoute(it) }
        PATH_REWIND -> segments.firstOrNull()?.parseUuidTo { RewindDetailRoute(it) }
        PATH_LOCATION -> LocationTimelineRoute
        PATH_EVENT -> segments.firstOrNull()?.let { EventDetailRoute(it) }
        PATH_SEARCH -> uri.toSearchRoute()
        else -> null
    }

private fun Uri.toSearchRoute(): SearchRoute =
    searchRouteFromParams(
        rawQuery = getQueryParameter(SEARCH_QUERY_PARAM),
        rawTypes = getQueryParameter(SEARCH_TYPE_PARAM),
        rawDate = getQueryParameter(SEARCH_DATE_PARAM),
    )

/**
 * Converts a navigation destination to its canonical logdate.app web URL.
 *
 * Inverse of [resolveDeepLinkUri] for web-scheme URIs. Returns null for destinations
 * with no stable addressable URL (list screens, settings, etc.).
 *
 * Currently emits the apex shape (`https://logdate.app/<type>/<id>`); the
 * web proxy 308s those to the canonical tenant subdomain when cloud knows
 * the share's owner. Once the share-sheet has the user's handle threaded
 * through (see logdate-web/docs/deep-linking.md item N-4), this should
 * emit the canonical shape directly.
 */
fun NavKey.toWebUrl(): String? =
    when (this) {
        is JournalDetailsRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_JOURNAL)
                .appendPath(journalId)
                .build()
                .toString()
        is TimelineDetailRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_DAY)
                .appendPath(dateIso)
                .build()
                .toString()
        is NoteDetailRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_NOTE)
                .appendPath(noteId)
                .build()
                .toString()
        is PostcardViewerRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_POSTCARD)
                .appendPath(postcardId)
                .build()
                .toString()
        is RewindDetailRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_REWIND)
                .appendPath(id)
                .build()
                .toString()
        is EventDetailRoute ->
            LOGDATE_API_BASE_URL
                .buildUpon()
                .appendPath(PATH_EVENT)
                .appendPath(eventId)
                .build()
                .toString()
        else -> null
    }

private fun resolveWebPath(segments: List<String>): NavKey? {
    val type = segments.getOrNull(0) ?: return null
    val value = segments.getOrNull(1)
    return when (type) {
        PATH_JOURNAL, PATH_JOURNAL_SHORT -> value?.parseUuidTo { JournalDetailsRoute(it) }
        PATH_NOTE -> value?.parseUuidTo { NoteDetailRoute(it) }
        PATH_DAY -> value?.parseDateString { TimelineDetailRoute(it) }
        PATH_POSTCARD -> value?.parseUuidTo { PostcardViewerRoute(it) }
        PATH_REWIND -> value?.parseUuidTo { RewindDetailRoute(it) }
        PATH_LOCATION -> LocationTimelineRoute
        PATH_EVENT -> value?.let { EventDetailRoute(it) }
        else -> null
    }
}

private inline fun <T : NavKey> String.parseUuidTo(create: (Uuid) -> T): T? = runCatching { create(Uuid.parse(this)) }.getOrNull()

private inline fun <T : NavKey> String.parseDateString(create: (String) -> T): T? =
    runCatching {
        kotlinx.datetime.LocalDate.parse(this)
        create(this)
    }.getOrNull()
