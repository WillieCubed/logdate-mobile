package app.logdate.client.watch.presence

/** Presence schedules catch-up, but never grants a peer access or overrides an account/link change. */
class WatchCatchUp(
    private val enabled: suspend () -> Boolean,
    private val currentLinks: suspend () -> Set<String>,
    private val refreshState: suspend () -> Unit,
    private val refreshWindow: suspend () -> Boolean,
) {
    suspend fun run(): Boolean {
        if (!enabled()) return true
        val links = currentLinks()
        if (links.isEmpty()) return true
        refreshState()
        if (!enabled() || currentLinks() != links) return true
        return refreshWindow()
    }
}
