package app.logdate.client.device.identity

import kotlin.uuid.Uuid

/** Account admission only: never use this to rewrite stored IDs or encryption bindings. */
fun sameOwnerId(
    first: String?,
    second: String?,
): Boolean {
    if (first == second) return true
    if (first == null || second == null) return false
    return runCatching { Uuid.parse(first) == Uuid.parse(second) }.getOrDefault(false)
}
