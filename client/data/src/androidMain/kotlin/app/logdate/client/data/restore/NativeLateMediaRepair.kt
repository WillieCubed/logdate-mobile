package app.logdate.client.data.restore

import app.logdate.client.media.storage.UnavailableMediaReferences
import java.io.File

/** Preserves verified canonical originals and hydrates existing references without admitting a database source. */
internal class NativeLateMediaRepair(
    candidates: List<Map<String, File>>,
    private val requireCurrent: suspend () -> Unit,
    private val available: suspend (String) -> Boolean,
    private val importOriginal: suspend (RetainedManagedMediaSource.Asset) -> String,
    private val canonicalReference: (String) -> String = { it },
    private val authorizeUnreferenced: (String, RetainedManagedMediaSource.Asset) -> Boolean = { _, _ -> false },
) {
    data class Target(
        val references: Set<String>,
        val commit: suspend (Map<String, String>) -> Boolean,
    )

    data class Result(
        val repaired: Int,
        val pending: Int,
        val recoveredReferences: Set<String>,
        val retry: Boolean,
        val recoveredSources: Set<RecoveredSource> = emptySet(),
    )

    data class RecoveredSource(
        val path: String,
        val file: String,
        val reference: String,
    )

    private data class Recovered(
        val reference: String,
        val asset: RetainedManagedMediaSource.Asset?,
    ) {
        val source: RecoveredSource?
            get() = asset?.let { RecoveredSource(it.sourcePath, it.file.canonicalPath, reference) }
    }

    private val source = RetainedManagedMediaSource(emptyMap(), candidates)
    private val incomingReferences =
        candidates
            .flatMap { it.keys }
            .mapNotNull { path ->
                if (!path.startsWith("files/media/")) return@mapNotNull null
                val reference = "logdate-media://library/" + path.removePrefix("files/media/")
                reference.takeIf { retainedCanonicalMediaDigest(it) != null }
            }.distinct()

    suspend fun repair(targets: List<Target>): Result {
        var repaired = 0
        var pending = 0
        val admitted = mutableListOf<Recovered>()
        val blockedSources = mutableSetOf<Pair<String, String>>()
        var retry = false
        requireCurrent()
        for (target in targets) {
            val replacements = mutableMapOf<String, String>()
            val recovered = mutableListOf<Recovered>()
            for (reference in target.references) {
                val original = recover(reference)
                if (original == null) {
                    if (retainedCanonicalMediaDigest(canonical(reference)) != null) pending++
                } else {
                    if (original.reference != canonical(reference) && !UnavailableMediaReferences.isUnavailable(reference)) {
                        // This readable working reference is not authority to replace its bytes
                        // or attach the incoming revision to the same row without user choice.
                        original.source?.let { blockedSources += it.path to it.file }
                        pending++
                        continue
                    }
                    recovered += original
                    if (UnavailableMediaReferences.isUnavailable(reference)) replacements[reference] = original.reference
                }
            }
            if (replacements.isNotEmpty()) {
                requireCurrent()
                if (target.commit(replacements)) {
                    repaired++
                    admitted += recovered
                } else {
                    recovered.mapNotNull { it.source }.forEach { blockedSources += it.path to it.file }
                    pending++
                    retry = true
                }
                requireCurrent()
            } else {
                admitted += recovered
            }
        }
        for (reference in incomingReferences) {
            val asset = source.resolve(reference) ?: continue
            if (authorizeUnreferenced(reference, asset)) recover(reference)?.let(admitted::add)
        }
        val accepted = admitted.filter { it.source?.let { source -> (source.path to source.file) !in blockedSources } ?: true }
        return Result(
            repaired,
            pending,
            accepted.mapTo(mutableSetOf()) { it.reference },
            retry,
            accepted.mapNotNullTo(mutableSetOf()) { it.source },
        )
    }

    private fun canonical(reference: String): String = canonicalReference(UnavailableMediaReferences.nativeSource(reference) ?: reference)

    private suspend fun recover(reference: String): Recovered? {
        val original = canonical(reference)
        if (retainedCanonicalMediaDigest(original) == null) return null
        requireCurrent()
        if (!available(original)) {
            val asset = source.resolve(original) ?: return null
            requireCurrent()
            val imported = importOriginal(asset)
            requireCurrent()
            check(retainedCanonicalMediaDigest(imported) == retainedCanonicalMediaDigest(original) && available(imported)) {
                "Imported native original does not match its canonical digest"
            }
            return Recovered(imported, asset)
        }
        requireCurrent()
        return Recovered(original, source.resolve(original))
    }
}
