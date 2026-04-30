package app.logdate.client.media

/**
 * Deletes media files owned by the editor (audio recordings, captured photos/videos)
 * when their owning draft or block is discarded.
 *
 * The contract is best-effort: implementations log and continue rather than throwing
 * if a path is missing, the file is already gone, or the platform doesn't support
 * the URI scheme. The editor's draft-discard path calls this AFTER it has identified
 * paths that are unreferenced — never on paths still owned by a persisted entry.
 */
interface MediaCleaner {
    /**
     * Deletes the file referenced by [path], if it exists.
     *
     * @param path Either an absolute filesystem path or a `file://` URI. Other URI
     *   schemes (content://, http://, etc.) are ignored.
     */
    suspend fun delete(path: String)

    /**
     * Convenience: delete every path in [paths]. Errors on one path do not stop the rest.
     */
    suspend fun deleteAll(paths: Iterable<String>) {
        for (path in paths) delete(path)
    }
}

/** No-op cleaner. Used when no platform impl is wired (e.g., tests). */
object NoOpMediaCleaner : MediaCleaner {
    override suspend fun delete(path: String) = Unit
}
