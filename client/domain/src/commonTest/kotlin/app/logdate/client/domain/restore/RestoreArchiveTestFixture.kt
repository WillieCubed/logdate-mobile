package app.logdate.client.domain.restore

import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportJournalNoteRelation
import app.logdate.client.domain.export.ExportLocationHistoryItem
import app.logdate.client.domain.export.ExportMediaManifest
import app.logdate.client.domain.export.ExportNote
import app.logdate.client.domain.export.ExportPlace
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.export.LocationHistoryPayload
import app.logdate.client.domain.export.PlacesPayload
import app.logdate.client.domain.export.ProfilePayload
import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeJournalContentRepository
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeJournalNotesRepository
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeJournalRepository
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeLocationHistoryRepository
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeProfileRepository
import app.logdate.client.domain.restore.RestoreArchiveTestFixtures.FakeUserPlacesRepository
import app.logdate.shared.model.Journal
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal abstract class RestoreArchiveTestFixture {
    protected lateinit var journalRepo: FakeJournalRepository
    protected lateinit var notesRepo: FakeJournalNotesRepository
    protected lateinit var contentRepo: FakeJournalContentRepository
    protected lateinit var profileRepo: FakeProfileRepository
    protected lateinit var placesRepo: FakeUserPlacesRepository
    protected lateinit var locationHistoryRepo: FakeLocationHistoryRepository
    protected lateinit var useCase: RestoreUserDataUseCase

    protected val json = Json { ignoreUnknownKeys = true }
    protected val now = Clock.System.now()

    @BeforeTest
    fun setUp() {
        journalRepo = FakeJournalRepository()
        notesRepo = FakeJournalNotesRepository()
        contentRepo = FakeJournalContentRepository()
        profileRepo = FakeProfileRepository()
        placesRepo = FakeUserPlacesRepository()
        locationHistoryRepo = FakeLocationHistoryRepository()
        useCase =
            RestoreUserDataUseCase(
                journalRepo,
                notesRepo,
                contentRepo,
                profileRepo,
                placesRepo,
                locationHistoryRepo,
                RestoreTestOwnerProvider(),
                RestoreTestDeviceIdProvider(),
            )
    }

    // region Happy path

    protected fun buildBundle(
        journals: List<Journal> = emptyList(),
        notes: List<ExportNote> = emptyList(),
        relations: List<ExportJournalNoteRelation> = emptyList(),
        drafts: List<app.logdate.client.domain.export.ExportDraft> = emptyList(),
        profile: LogDateProfile? = null,
        places: List<ExportPlace> = emptyList(),
        locationHistory: List<ExportLocationHistoryItem> = emptyList(),
        mediaManifest: ExportMediaManifest? = null,
    ): RestoreBundle {
        val metadata =
            app.logdate.client.domain.export.ExportMetadata(
                version = ExportSchemaVersion.V1_2,
                exportDate = now,
                userId = "test-user",
                deviceId = "test-device",
                appVersion = "1.0.0",
                stats =
                    ExportStats(
                        journalCount = journals.size,
                        noteCount = notes.size,
                        draftCount = drafts.size,
                        mediaCount = 0,
                        placeCount = places.size,
                        locationHistoryCount = locationHistory.size,
                        hasProfile = profile != null,
                    ),
            )
        return RestoreBundle(
            metadataJson = json.encodeToString(metadata),
            journalsJson = json.encodeToString(mapOf("journals" to journals)),
            notesJson = json.encodeToString(mapOf("notes" to notes)),
            journalNotesJson = json.encodeToString(mapOf("journal_notes" to relations)),
            draftsJson = json.encodeToString(mapOf("drafts" to drafts)),
            profileJson = profile?.let { json.encodeToString(ProfilePayload(it)) },
            placesJson = places.takeIf { it.isNotEmpty() }?.let { json.encodeToString(PlacesPayload(it)) },
            locationHistoryJson =
                locationHistory.takeIf { it.isNotEmpty() }?.let {
                    json.encodeToString(LocationHistoryPayload(it))
                },
            mediaManifestJson = mediaManifest?.let { json.encodeToString(it) },
        )
    }

    protected fun buildBundleWithCustomStats(
        journals: List<Journal> = emptyList(),
        notes: List<ExportNote> = emptyList(),
        relations: List<ExportJournalNoteRelation> = emptyList(),
        drafts: List<app.logdate.client.domain.export.ExportDraft> = emptyList(),
        mediaManifest: ExportMediaManifest? = null,
        stats: ExportStats,
    ): RestoreBundle {
        val metadata =
            app.logdate.client.domain.export.ExportMetadata(
                version = ExportSchemaVersion.V1_2,
                exportDate = now,
                userId = "test-user",
                deviceId = "test-device",
                appVersion = "1.0.0",
                stats = stats,
            )
        return RestoreBundle(
            metadataJson = json.encodeToString(metadata),
            journalsJson = json.encodeToString(mapOf("journals" to journals)),
            notesJson = json.encodeToString(mapOf("notes" to notes)),
            journalNotesJson = json.encodeToString(mapOf("journal_notes" to relations)),
            draftsJson = json.encodeToString(mapOf("drafts" to drafts)),
            mediaManifestJson = mediaManifest?.let { json.encodeToString(it) },
        )
    }

    protected fun buildBundleWithVersion(version: ExportSchemaVersion): RestoreBundle {
        val metadata =
            app.logdate.client.domain.export.ExportMetadata(
                version = version,
                exportDate = now,
                userId = "test-user",
                deviceId = "test-device",
                appVersion = "1.0.0",
                stats = ExportStats(journalCount = 0, noteCount = 0, draftCount = 0, mediaCount = 0),
            )
        return RestoreBundle(
            metadataJson = json.encodeToString(metadata),
            journalsJson = json.encodeToString(mapOf("journals" to emptyList<Journal>())),
            notesJson = json.encodeToString(mapOf("notes" to emptyList<ExportNote>())),
            journalNotesJson = json.encodeToString(mapOf("journal_notes" to emptyList<ExportJournalNoteRelation>())),
            draftsJson = json.encodeToString(mapOf("drafts" to emptyList<ExportDraft>())),
        )
    }

    // endregion

    // region Fakes    // region Test data builders

    protected fun testJournal(
        id: Uuid = Uuid.random(),
        title: String = "Test Journal",
        lastUpdated: Instant = now,
    ) = Journal(
        id = id,
        title = title,
        description = "Description",
        created = now - 30.days,
        lastUpdated = lastUpdated,
    )

    protected fun testTextNote(
        id: Uuid = Uuid.random(),
        content: String = "Test note",
        createdAt: Instant = now,
        updatedAt: Instant = now,
    ) = ExportNote(
        id = id.toString(),
        type = "text",
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    protected fun testDraft(
        id: Uuid = Uuid.random(),
        content: String = "Draft content",
        journalId: String? = null,
        mediaReferences: List<String> = emptyList(),
    ) = app.logdate.client.domain.export.ExportDraft(
        id = id.toString(),
        journalId = journalId,
        content = content,
        createdAt = now,
        updatedAt = now,
        mediaReferences = mediaReferences,
    )
}
