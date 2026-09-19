package app.logdate.client.domain.export.archive

/**
 * `SHA256SUMS`: one line per file, `<sha256>  <path>`, sorted by path.
 *
 * The format is the one `shasum -a 256 -c` and `sha256sum -c` read, with two spaces between the hash
 * and the path. The file lists every file in the archive except itself.
 */
object ChecksumFile {
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun render(entries: List<HashLedger.Entry>): String =
        entries
            .sortedBy { it.path.value }
            .joinToString(separator = "") { "${it.sha256}  ${it.path.value}\n" }

    /** The hash recorded for each path, or null if any line is malformed or names an unsafe path. */
    fun parse(text: String): Map<String, String>? {
        val checksums = linkedMapOf<String, String>()
        for (line in text.lineSequence().filter { it.isNotEmpty() }) {
            val hash = line.substringBefore("  ", missingDelimiterValue = "")
            val path = line.substringAfter("  ", missingDelimiterValue = "")
            if (!SHA256.matches(hash) || ArchivePath.parse(path) == null) return null
            checksums[path] = hash
        }
        return checksums
    }
}
