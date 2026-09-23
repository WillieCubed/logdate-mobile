package app.logdate.client.sync.cloud

import app.logdate.client.sync.crypto.UnreadablePayloadException
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Maps each change on a downloaded page separately, setting aside the ones this device cannot
 * read instead of failing the page. A page used to be all-or-nothing, so one record encrypted with
 * a replaced key failed every sync from then on and nothing else on the page, or after it, arrived.
 */
internal suspend fun <C, T> List<C>.readEach(
    idOf: (C) -> String,
    read: suspend (C) -> T,
): Pair<List<T>, List<Uuid>> {
    val readable = mutableListOf<T>()
    val unreadable = mutableListOf<Uuid>()
    for (change in this) {
        try {
            readable += read(change)
        } catch (e: UnreadablePayloadException) {
            Napier.w("Setting aside synced record ${idOf(change)}: this device cannot read it", e)
            unreadable += Uuid.parse(idOf(change))
        }
    }
    return readable to unreadable
}
