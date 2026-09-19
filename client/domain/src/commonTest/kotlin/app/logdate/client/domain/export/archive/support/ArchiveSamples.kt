package app.logdate.client.domain.export.archive.support

import app.logdate.client.domain.export.archive.ArchiveBlockType
import app.logdate.client.domain.export.archive.ArchiveCategory
import app.logdate.client.domain.export.archive.ArchiveContent
import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveDateRange
import app.logdate.client.domain.export.archive.ArchiveDraft
import app.logdate.client.domain.export.archive.ArchiveDraftBlock
import app.logdate.client.domain.export.archive.ArchiveDraftFile
import app.logdate.client.domain.export.archive.ArchiveGenerator
import app.logdate.client.domain.export.archive.ArchiveJournal
import app.logdate.client.domain.export.archive.ArchiveJournalFile
import app.logdate.client.domain.export.archive.ArchiveLayout
import app.logdate.client.domain.export.archive.ArchiveLocation
import app.logdate.client.domain.export.archive.ArchiveLocationSample
import app.logdate.client.domain.export.archive.ArchiveManifest
import app.logdate.client.domain.export.archive.ArchiveMediaFile
import app.logdate.client.domain.export.archive.ArchiveMediaInventoryEntry
import app.logdate.client.domain.export.archive.ArchiveMediaRef
import app.logdate.client.domain.export.archive.ArchiveMediaStatus
import app.logdate.client.domain.export.archive.ArchiveNote
import app.logdate.client.domain.export.archive.ArchiveNoteFile
import app.logdate.client.domain.export.archive.ArchiveNoteType
import app.logdate.client.domain.export.archive.ArchiveOmission
import app.logdate.client.domain.export.archive.ArchiveOmissionReason
import app.logdate.client.domain.export.archive.ArchiveOwner
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchivePlace
import app.logdate.client.domain.export.archive.ArchivePlaceFile
import app.logdate.client.domain.export.archive.ArchiveProfile
import app.logdate.client.domain.export.archive.ArchiveProfileFile
import app.logdate.client.domain.export.archive.ArchiveRole
import app.logdate.client.domain.export.archive.ArchiveScope
import app.logdate.client.domain.export.archive.ArchiveTextFormat
import kotlin.time.Instant

/** One instance of every archive record with every optional field filled in. */
object ArchiveSamples {
    private val at: Instant = Instant.parse("2026-09-18T03:30:05.123Z")
    private const val JOURNAL_ID = "3f2b8c1e-9d4a-4e6b-8a7c-1d2e3f4a5b6c"
    private const val NOTE_ID = "0b6c1d2e-3f4a-4b5c-8d6e-7f8091a2b3c4"

    val location =
        ArchiveLocation(latitude = 39.7392, longitude = -104.9903, altitudeMeters = 1609.3, accuracyMeters = 5.0, placeName = "Denver")

    val includedMedia =
        ArchiveMediaRef(
            status = ArchiveMediaStatus.INCLUDED,
            path = ArchivePath.of("media/photos/2026/2026-09-17_21-30-05.jpg"),
            mediaType = "image/jpeg",
        )

    val omittedMedia = ArchiveMediaRef(status = ArchiveMediaStatus.OMITTED, omittedReason = ArchiveOmissionReason.UNREADABLE)

    val note =
        ArchiveNote(
            id = NOTE_ID,
            type = ArchiveNoteType.IMAGE,
            createdAt = at,
            updatedAt = at,
            timeZone = "America/Denver",
            createdAtLocal = "2026-09-17T21:30:05.123-06:00",
            text = "Walk by the river",
            textFormat = ArchiveTextFormat.MARKDOWN,
            caption = "Sunset",
            media = includedMedia,
            durationMs = 1_000,
            location = location,
            journalIds = listOf(JOURNAL_ID),
        )

    val noteFile = ArchiveNoteFile(listOf(note, note.copy(media = omittedMedia)))

    val journalFile =
        ArchiveJournalFile(
            listOf(ArchiveJournal(id = JOURNAL_ID, title = "Daily", description = "Every day", createdAt = at, updatedAt = at)),
        )

    val draftFile =
        ArchiveDraftFile(
            listOf(
                ArchiveDraft(
                    id = "9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d",
                    journalIds = listOf(JOURNAL_ID),
                    createdAt = at,
                    updatedAt = at,
                    blocks =
                        listOf(
                            ArchiveDraftBlock(
                                id = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                                type = ArchiveBlockType.AUDIO,
                                timestamp = at,
                                text = "Voice note",
                                media = includedMedia,
                                caption = "Morning",
                                durationMs = 12_000,
                                transcription = "Good morning",
                                location = location,
                            ),
                        ),
                ),
            ),
        )

    val placeFile =
        ArchivePlaceFile(
            listOf(
                ArchivePlace(
                    id = "5c4d3e2f-1a0b-4c9d-8e7f-6a5b4c3d2e1f",
                    name = "Home",
                    latitude = 39.7,
                    longitude = -104.9,
                    radiusMeters = 100.0,
                    description = "Where I live",
                ),
            ),
        )

    val profileFile =
        ArchiveProfileFile(
            ArchiveProfile(displayName = "Sam", birthday = at, bio = "Hello", originalBio = "Hi", createdAt = at, updatedAt = at),
        )

    val locationSample =
        ArchiveLocationSample(
            timestamp = at,
            loggedAt = at,
            latitude = 39.7392,
            longitude = -104.9903,
            altitudeMeters = 1609.3,
            accuracyMeters = 5.0,
            speedMetersPerSecond = 1.4,
            bearingDegrees = 90.0,
            confidence = 0.9,
            isGenuine = true,
            isMock = false,
            capturePipeline = "FUSED",
            captureSource = "BACKGROUND_PERIODIC",
        )

    val mediaFile =
        ArchiveMediaFile(
            listOf(
                ArchiveMediaInventoryEntry(
                    path = ArchivePath.of("media/photos/2026/2026-09-17_21-30-05.jpg"),
                    mediaType = "image/jpeg",
                    bytes = 123_456,
                    sha256 = "a".repeat(64),
                ),
            ),
        )

    val manifest =
        ArchiveManifest(
            exportedAt = at,
            exportTimeZone = "America/Denver",
            generator = ArchiveGenerator(version = "1.2.3"),
            owner = ArchiveOwner(displayName = "Sam"),
            scope =
                ArchiveScope(
                    complete = false,
                    dateRange = ArchiveDateRange(from = at, to = at),
                    omitted = listOf(ArchiveOmission(ArchiveCategory.PEOPLE, ArchiveOmissionReason.NOT_YET_SUPPORTED)),
                ),
            counts = ArchiveCounts(journals = 1, notes = 2, drafts = 1, media = 1, places = 1, locationSamples = 1, hasProfile = true),
            contents =
                listOf(
                    ArchiveContent(ArchiveRole.README, ArchiveLayout.README, ArchiveLayout.TEXT_MEDIA_TYPE),
                    ArchiveContent(
                        ArchiveRole.JOURNALS,
                        ArchiveLayout.JOURNALS,
                        ArchiveLayout.JSON_MEDIA_TYPE,
                        ArchiveLayout.SCHEMA_JOURNALS,
                    ),
                    ArchiveContent(ArchiveRole.NOTES, ArchiveLayout.NOTES, ArchiveLayout.JSON_MEDIA_TYPE, ArchiveLayout.SCHEMA_NOTES),
                    ArchiveContent(
                        ArchiveRole.MEDIA_INVENTORY,
                        ArchiveLayout.MEDIA_INVENTORY,
                        ArchiveLayout.JSON_MEDIA_TYPE,
                        ArchiveLayout.SCHEMA_MEDIA,
                    ),
                    ArchiveContent(ArchiveRole.CHECKSUMS, ArchiveLayout.CHECKSUMS, ArchiveLayout.TEXT_MEDIA_TYPE),
                ),
        )
}
