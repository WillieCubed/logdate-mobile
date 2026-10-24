package app.logdate.client.data.restore

/** Migrates only authenticated, still-referenced legacy originals into immutable canonical storage. */
internal class NativeLateLegacyMediaRepair(
    private val candidates: List<NativeLegacyLateMediaCandidate>,
    private val requireCurrent: suspend () -> Unit,
    private val legacyReadable: suspend (String) -> Boolean,
    private val available: suspend (String) -> Boolean,
    private val importOriginal: suspend (NativeLegacyLateMediaCandidate) -> String?,
    private val remembered: (NativeLegacyLateMediaCandidate) -> String?,
    private val remember: (NativeLegacyLateMediaCandidate, String) -> Unit,
    private val canonicalReference: (String) -> String,
) {
    data class Result(
        val verifiedFiles: Set<String>,
        val retry: Boolean,
    )

    suspend fun repair(targets: List<NativeLateMediaRepair.Target>): Result {
        val verified = mutableSetOf<String>()
        val blocked = mutableSetOf<String>()
        for (target in targets) {
            requireCurrent()
            val replacements = mutableMapOf<String, String>()
            val recovered = mutableSetOf<String>()
            for (candidate in candidates) {
                val references = candidate.references.intersect(target.references)
                if (references.isEmpty()) continue
                // A readable same-path original may contain a newer revision. Keep incoming
                // custody until the user has a recovery route that exposes both originals.
                if (references.any { legacyReadable(it) }) continue
                var canonical = remembered(candidate)
                if (canonical == null || !available(canonical)) {
                    // Only immutable canonical storage receives incoming bytes; live legacy paths remain untouched.
                    requireCurrent()
                    canonical = importOriginal(candidate) ?: continue
                    check(retainedCanonicalMediaDigest(canonical) == candidate.sha256 && available(canonical))
                    requireCurrent()
                    remember(candidate, canonical)
                }
                requireCurrent()
                check(retainedCanonicalMediaDigest(canonical) == candidate.sha256 && available(canonical))
                recovered += candidate.file.canonicalPath
                references.filter { canonicalReference(it) != canonical }.forEach { replacements[it] = canonical }
            }
            if (replacements.isNotEmpty()) {
                requireCurrent()
                if (!target.commit(replacements)) blocked += recovered
                requireCurrent()
            }
            verified += recovered
        }
        for (candidate in candidates.filter { it.references.isEmpty() }) {
            requireCurrent()
            val canonical = remembered(candidate) ?: continue
            if (retainedCanonicalMediaDigest(canonical) == candidate.sha256 && available(canonical)) {
                requireCurrent()
                verified += candidate.file.canonicalPath
            }
        }
        return Result(verified - blocked, blocked.isNotEmpty())
    }
}
