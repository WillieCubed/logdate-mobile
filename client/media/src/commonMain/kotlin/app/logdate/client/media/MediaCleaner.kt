package app.logdate.client.media

/**
 * Cleans disposable editor-owned files when their owning draft or block is discarded.
 *
 * The contract is best-effort: implementations log and continue rather than throwing
 * if a path is missing, the file is already gone, or the platform cannot prove the
 * file is disposable. Android retains persisted originals and recordings until a
 * serialized, reference-aware collector can prove that no writer still owns them.
 */
interface MediaCleaner {
    /**
     * Deletes the file referenced by [path] only when the platform can prove it is disposable.
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
