package app.logdate.client

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import app.logdate.navigation.routes.core.JournalDetail
import app.logdate.navigation.routes.core.NoteViewerRoute
import app.logdate.navigation.routes.core.PostcardViewerRoute
import app.logdate.navigation.routes.core.RewindDetailRoute
import app.logdate.navigation.routes.core.TimelineDetail
import kotlin.uuid.Uuid

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
        "journal" -> segments.firstOrNull()?.parseUuidTo { JournalDetail(it) }
        "day" -> segments.firstOrNull()?.parseDateTo { TimelineDetail(it) }
        "note" -> segments.firstOrNull()?.parseUuidTo { NoteViewerRoute(it) }
        "postcard" -> segments.firstOrNull()?.parseUuidTo { PostcardViewerRoute(it) }
        "rewind" -> segments.firstOrNull()?.parseUuidTo { RewindDetailRoute(it) }
        "logdate.app" -> resolveWebPath(segments)
        else -> null
    }
}

private fun resolveWebPath(segments: List<String>): NavKey? {
    val type = segments.getOrNull(0) ?: return null
    val value = segments.getOrNull(1) ?: return null
    return when (type) {
        "journal" -> value.parseUuidTo { JournalDetail(it) }
        "day" -> value.parseDateTo { TimelineDetail(it) }
        "postcard" -> value.parseUuidTo { PostcardViewerRoute(it) }
        "rewind" -> value.parseUuidTo { RewindDetailRoute(it) }
        else -> null
    }
}

private inline fun <T : NavKey> String.parseUuidTo(create: (Uuid) -> T): T? = runCatching { create(Uuid.parse(this)) }.getOrNull()

private inline fun <T : NavKey> String.parseDateTo(create: (kotlinx.datetime.LocalDate) -> T): T? =
    runCatching { create(kotlinx.datetime.LocalDate.parse(this)) }.getOrNull()
