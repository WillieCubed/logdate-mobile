package app.logdate.client.data.restore

import app.logdate.client.media.storage.UnavailableMediaReferences

internal fun retainedUnavailableMediaReference(reference: String): String =
    UnavailableMediaReferences.nativeRestore(UnavailableMediaReferences.nativeSource(reference) ?: reference)

/** Only the verified source attachment may replace its unchanged original or quarantine marker. */
internal fun repairRetainedMediaReference(
    current: String?,
    source: String?,
    recovered: String?,
): String? {
    if (current == null || source == null || recovered == null || current == recovered) return current
    val original = UnavailableMediaReferences.nativeSource(source) ?: source
    if (UnavailableMediaReferences.isUnavailable(recovered)) {
        // A duplicate source row is not proof that its destination URI contains source bytes.
        return if ((current == source || current == original) &&
            !UnavailableMediaReferences.isUnavailable(current) &&
            UnavailableMediaReferences.nativeSource(recovered) == original
        ) {
            recovered
        } else {
            current
        }
    }
    return if (current == source ||
        current == original ||
        UnavailableMediaReferences.nativeSource(current) == original
    ) {
        recovered
    } else {
        current
    }
}

internal fun retainedMediaRepairId(
    generationId: String,
    targetId: String,
    projectionSha256: String,
): String {
    require(projectionSha256.matches(Regex("[0-9a-f]{64}")))
    return "retained:$generationId:media:$targetId:$projectionSha256"
}

internal suspend fun shouldReimportRetainedMedia(
    previous: RetainedJournalMergeCheckpoint.Entry?,
    sourceSha256: String,
    targetPresent: Boolean,
    references: List<String?>,
    isAvailable: suspend (String) -> Boolean,
): Boolean {
    if (previous?.applied != true || previous.sourceSha256 != sourceSha256) return true
    if (!targetPresent) return false
    return references.filterNotNull().any { UnavailableMediaReferences.isUnavailable(it) || !isAvailable(it) }
}
