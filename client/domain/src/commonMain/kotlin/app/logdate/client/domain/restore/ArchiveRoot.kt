package app.logdate.client.domain.restore

/**
 * Finds where an export archive's files start inside the zip.
 *
 * Exports keep their files at the top level, but an archive that was unzipped and zipped again
 * with Finder or Explorer usually gets one wrapper folder around them. Readers look the
 * archive's marker file up through [find] and prepend the returned prefix to every entry name.
 */
object ArchiveRoot {
    private const val MACOS_RESOURCE_FORKS = "__MACOSX/"
    private const val FINDER_METADATA = ".DS_Store"

    /**
     * Returns the prefix in front of [marker] in [entryNames]: `""` when the marker is at the top
     * level, `"Folder/"` when the whole archive sits inside that single folder, or `null` when
     * the marker is missing or the layout is ambiguous.
     *
     * Only one wrapper folder is recognised, and only when no other file sits at the top level.
     * macOS resource-fork entries (`__MACOSX/`) and `.DS_Store` files are ignored.
     */
    fun find(
        entryNames: Iterable<String>,
        marker: String,
    ): String? {
        val entries =
            entryNames
                .map { it.trimStart('/') }
                .filterNot { it.isEmpty() || it.startsWith(MACOS_RESOURCE_FORKS) || it.substringAfterLast('/') == FINDER_METADATA }
        if (marker in entries) return ""

        val hasTopLevelFile = entries.any { '/' !in it }
        if (hasTopLevelFile) return null

        val topLevelFolders = entries.map { it.substringBefore('/') }.distinct()
        val wrapper = topLevelFolders.singleOrNull() ?: return null
        return "$wrapper/".takeIf { "$it$marker" in entries }
    }
}
