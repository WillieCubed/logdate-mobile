package app.logdate.client.media.storage

/**
 * Where this platform keeps each [MediaCollection], for this install of LogDate.
 *
 * This is the only platform-specific part of resolving media references: [MediaFileResolver]
 * does the rest in shared code. Every implementation must pass `MediaDirectoriesContract`; the
 * rules are in `docs/reference/media-references.md`.
 */
interface MediaDirectories {
    /**
     * The absolute directory holding [collection] in this install, written with `/` separators and
     * no trailing slash, and already in its [canonicalPath] spelling.
     */
    fun directory(collection: MediaCollection): String

    /**
     * [path] spelled the way [directory] spells it: symlinks and platform aliases resolved, `/`
     * separators. Used to recognise a file inside a collection however its path was written.
     */
    fun canonicalPath(path: String): String = path

    /**
     * Where a file that an earlier install of LogDate wrote at the absolute [path] is in this
     * install (another iOS app container, another Android user, another home directory), or `null`
     * when [path] is not from an install this platform recognises. The file may not exist there.
     */
    fun pathInCurrentInstall(path: String): String? = null
}
