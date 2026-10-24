package app.logdate.client.data.restore

import app.logdate.client.media.storage.UnavailableMediaReferences
import java.io.File

/** Preserves verified canonical originals and hydrates existing references without admitting a database source. */
internal class NativeLateMediaRepair(
    candidates: List<Map<String, File>>,
    private val requireCurrent: suspend () -> Unit,
    private val available: suspend (String) -> Boolean,
    private val importOriginal: suspend (RetainedManagedMediaSource.Asset) -> Unit,
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
    )

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
        val verified = mutableSetOf<String>()
        val blocked = mutableSetOf<String>()
        requireCurrent()
        for (target in targets) {
            val replacements = mutableMapOf<String, String>()
            val recovered = mutableSetOf<String>()
            for (reference in target.references) {
                val original = recover(reference)
                if (original == null) {
                    if (retainedCanonicalMediaDigest(canonical(reference)) != null) pending++
                } else {
                    recovered += original
                    if (UnavailableMediaReferences.isUnavailable(reference)) replacements[reference] = original
                }
            }
            if (replacements.isNotEmpty()) {
                requireCurrent()
                if (target.commit(replacements)) {
                    repaired++
                } else {
                    blocked += recovered
                    pending++
                }
                requireCurrent()
            }
            verified += recovered
        }
        val portableIncoming = mutableSetOf<String>()
        for (reference in incomingReferences) {
            val asset = source.resolve(reference) ?: continue
            if (authorizeUnreferenced(reference, asset)) recover(reference)?.let(portableIncoming::add)
        }
        return Result(repaired, pending, (verified + portableIncoming) - blocked, retry = blocked.isNotEmpty())
    }

    private fun canonical(reference: String): String = canonicalReference(UnavailableMediaReferences.nativeSource(reference) ?: reference)

    private suspend fun recover(reference: String): String? {
        val original = canonical(reference)
        if (retainedCanonicalMediaDigest(original) == null) return null
        requireCurrent()
        if (!available(original)) {
            val asset = source.resolve(original) ?: return null
            requireCurrent()
            importOriginal(asset)
            requireCurrent()
            check(available(original)) { "Imported native original does not match its canonical digest" }
        }
        requireCurrent()
        return original
    }
}
