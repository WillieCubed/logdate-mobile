package app.logdate.client.domain.activity

import app.logdate.client.sync.peer.state.ActivitySnapshot
import kotlin.time.Instant

/**
 * The activity both devices act on. The phone knows vehicles best, only the worn watch knows sleep,
 * and the watch reports exercise but not an ordinary walk, so movement either device sees counts.
 */
fun agreedActivity(
    phone: Activity?,
    watch: Activity?,
    watchWorn: Boolean,
): Activity? =
    phone?.takeIf { it.kind == ActivityKind.IN_VEHICLE }
        ?: watch?.takeIf { it.kind == ActivityKind.SLEEP && watchWorn }
        ?: listOfNotNull(phone, watch).firstOrNull { it.kind == ActivityKind.MOVING }

/** The most recently ended of [ended]. */
fun latestEnded(vararg ended: Activity?): Activity? = ended.filterNotNull().maxByOrNull { it.endedAt ?: it.startedAt }

/** This log for the peer's state document, or null when there is nothing to tell. */
fun ActivityLog.toSnapshot(): ActivitySnapshot? {
    if (open == null && lastEnded == null) return null
    return ActivitySnapshot(
        kind = open?.kind?.name,
        startedAt = open?.startedAt?.toEpochMilliseconds(),
        lastEndedKind = lastEnded?.kind?.name,
        lastStartedAt = lastEnded?.startedAt?.toEpochMilliseconds(),
        lastEndedAt = lastEnded?.endedAt?.toEpochMilliseconds(),
    )
}

/** The peer's activity under way. When it ends is the peer's to judge, so it never carries a stop time. */
fun ActivitySnapshot.current(): Activity? {
    val kind = kindNamed(kind) ?: return null
    return Activity(kind, Instant.fromEpochMilliseconds(startedAt ?: return null), endedAt = null)
}

/** The last activity the peer saw end. */
fun ActivitySnapshot.lastEnded(): Activity? {
    val kind = kindNamed(lastEndedKind) ?: return null
    val startedAt = Instant.fromEpochMilliseconds(lastStartedAt ?: return null)
    return Activity(kind, startedAt, Instant.fromEpochMilliseconds(lastEndedAt ?: return null))
}

private fun kindNamed(name: String?): ActivityKind? = ActivityKind.entries.firstOrNull { it.name == name }
