package app.logdate.client.domain.export.archive

import app.logdate.shared.model.profile.LogDateProfile
import kotlin.time.Instant

/** What the app stores that the 2.0 archive does not export yet. It shrinks as each kind of data is added. */
internal object ArchiveCoverage {
    val notYetSupported: List<ArchiveCategory> =
        listOf(
            ArchiveCategory.EDITOR_DRAFTS,
            ArchiveCategory.TRANSCRIPTS,
            ArchiveCategory.AUDIO_TAGS,
            ArchiveCategory.PEOPLE,
            ArchiveCategory.EVENTS,
            ArchiveCategory.REWINDS,
            ArchiveCategory.POSTCARDS,
            ArchiveCategory.STICKERS,
            ArchiveCategory.HEALTH_SNAPSHOTS,
            ArchiveCategory.FAVORITES,
            ArchiveCategory.JOURNAL_COVERS,
            ArchiveCategory.PROFILE_PHOTO,
            ArchiveCategory.SETTINGS,
        )
}

internal class ArchiveManifestBuilder(
    private val options: ArchiveExportOptions,
    private val snapshot: ArchiveSnapshot,
    private val resolution: MediaResolution?,
) {
    fun counts(): ArchiveCounts =
        ArchiveCounts(
            journals = snapshot.journals.size,
            notes = snapshot.notes.size,
            drafts = snapshot.drafts.size,
            media = resolution?.files?.size ?: 0,
            places = snapshot.places.size,
            locationSamples = snapshot.locationHistory.size,
            hasProfile = snapshot.profile != LogDateProfile(),
        )

    fun scope(): ArchiveScope {
        val omitted =
            buildList {
                if (!options.includeJournals) add(ArchiveOmission(ArchiveCategory.JOURNALS, ArchiveOmissionReason.NOT_REQUESTED))
                if (!options.includeNotes) add(ArchiveOmission(ArchiveCategory.NOTES, ArchiveOmissionReason.NOT_REQUESTED))
                if (!options.includeDrafts) add(ArchiveOmission(ArchiveCategory.DRAFTS, ArchiveOmissionReason.NOT_REQUESTED))
                if (!options.includeMedia) add(ArchiveOmission(ArchiveCategory.MEDIA, ArchiveOmissionReason.NOT_REQUESTED))
                snapshot.unreadable.forEach { add(ArchiveOmission(it, ArchiveOmissionReason.UNREADABLE)) }
                if (resolution?.hasOmittedFiles == true) add(ArchiveOmission(ArchiveCategory.MEDIA, ArchiveOmissionReason.UNREADABLE))
                ArchiveCoverage.notYetSupported.forEach { add(ArchiveOmission(it, ArchiveOmissionReason.NOT_YET_SUPPORTED)) }
            }
        return ArchiveScope(
            complete = omitted.isEmpty() && !options.isFiltered,
            dateRange = ArchiveDateRange(options.from, options.to).takeIf { options.isFiltered },
            omitted = omitted,
        )
    }

    fun manifest(
        exportedAt: Instant,
        exportTimeZone: String,
        contents: List<ArchiveContent>,
    ) = ArchiveManifest(
        exportedAt = exportedAt,
        exportTimeZone = exportTimeZone,
        generator = ArchiveGenerator(version = snapshot.appVersion),
        owner = ArchiveOwner(displayName = snapshot.ownerName),
        scope = scope(),
        counts = counts(),
        contents = contents,
    )
}
