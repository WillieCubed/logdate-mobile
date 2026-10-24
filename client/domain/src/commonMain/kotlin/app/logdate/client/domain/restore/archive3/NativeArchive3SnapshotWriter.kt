package app.logdate.client.domain.restore.archive3

import app.logdate.client.domain.export.archive.ArchivePathAllocator
import app.logdate.client.domain.export.archive.ArchiveSnapshot
import app.logdate.client.domain.export.archive.MediaFileNamer
import app.logdate.client.domain.export.archive.MediaReferences
import app.logdate.client.domain.export.archive.MediaResolver
import app.logdate.client.domain.export.archive.MediaSourceOpener
import app.logdate.client.domain.export.archive.StagingDirectoryArchiveContainer
import app.logdate.client.domain.export.archive.mediaRequests
import app.logdate.client.domain.export.archive.toArchiveDraft
import app.logdate.client.domain.export.archive.toArchiveNote
import app.logdate.client.domain.export.archive.toArchivePlace
import app.logdate.client.domain.export.archive.toArchiveProfile
import app.logdate.client.domain.export.archive.toArchiveSample
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Place
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import okio.Source
import kotlin.uuid.Uuid

/** A source original selected by the frozen Android journal snapshot. */
data class NativeArchive3Original(
    val reference: String,
    val bytes: Long,
    val sha256: String,
    val open: () -> Source,
)

/** Reads only immutable, already-censused source content; the callback owns the source fence. */
data class NativeArchive3SnapshotInput(
    val exportedAt: String,
    val journals: List<Journal>,
    val notes: List<JournalNote>,
    val journalIdsByNote: Map<Uuid, List<Uuid>>,
    val drafts: List<EditorDraft>,
    val profile: LogDateProfile,
    val places: List<Place.UserDefined>,
    val locationHistory: List<LocationHistoryItem>,
    val originals: List<NativeArchive3Original>,
    val unsupportedPopulatedCategories: Set<String>,
    val validateContext: () -> Unit,
)

/** Uses the established archive projections while requiring every original and category to survive. */
object NativeArchive3SnapshotWriter {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    suspend fun write(
        input: NativeArchive3SnapshotInput,
        container: StagingDirectoryArchiveContainer,
    ) {
        try {
            require(input.unsupportedPopulatedCategories.isEmpty()) { "Native transfer source has unsupported content" }
            input.validateContext()
            val sources = input.originals.associateBy { it.reference }
            require(sources.size == input.originals.size)
            val snapshot =
                ArchiveSnapshot(
                    input.journals,
                    input.notes,
                    input.journalIdsByNote,
                    input.drafts,
                    input.profile,
                    input.places,
                    input.locationHistory,
                    null,
                    "3.0",
                    emptySet(),
                )
            val resolution =
                MediaResolver(MediaSourceOpener { reference -> sources[reference]?.open?.invoke() }, MediaFileNamer(ArchivePathAllocator()))
                    .resolve(snapshot.mediaRequests(TimeZone.UTC))
            require(!resolution.hasOmittedFiles && resolution.files.size == sources.size) { "Native original is unavailable" }
            val media = MediaReferences(resolution)
            val journalIds = input.journals.map { it.id }.toSet()
            require(
                input.journalIdsByNote.values
                    .flatten()
                    .all { it in journalIds },
            )
            val notes =
                JsonArray(
                    input.notes.map { note ->
                        val archive = note.toArchiveNote(input.journalIdsByNote[note.uid].orEmpty(), media)
                        val payload = json.encodeToJsonElement(archive) as JsonObject
                        JsonObject(
                            payload +
                                ("nativePrimaryJournalId" to JsonPrimitive(input.journalIdsByNote[note.uid]?.firstOrNull()?.toString())),
                        )
                    },
                )
            val drafts = JsonArray(input.drafts.map { json.encodeToJsonElement(it.toArchiveDraft(media)) })
            val profile = input.profile.toArchiveProfile()?.let { json.encodeToJsonElement(it) as JsonObject }
            val places = JsonArray(input.places.map { json.encodeToJsonElement(it.toArchivePlace()) })
            val samples =
                input.locationHistory.map { item ->
                    val projected = json.encodeToJsonElement(item.toArchiveSample()) as JsonObject
                    JsonObject(
                        projected +
                            buildMap {
                                put("sampleId", JsonPrimitive(item.sampleId))
                                put("userId", JsonPrimitive(item.userId))
                                put("deviceId", JsonPrimitive(item.deviceId))
                                item.activityType?.let { put("activityType", JsonPrimitive(it)) }
                                item.timeZoneId?.let { put("timeZoneId", JsonPrimitive(it)) }
                            },
                    )
                }
            val source =
                NativeArchive3ExportSource(
                    input.exportedAt,
                    input.journals.map {
                        NativeArchive3JournalRecord(
                            it.id.toString(),
                            it.title,
                            it.description,
                            it.isFavorited,
                            it.created.toString(),
                            it.lastUpdated.toString(),
                        )
                    },
                    notes,
                    drafts,
                    resolution.files.map { file ->
                        val original = sources.getValue(file.reference)
                        NativeArchive3MediaSource(file.path, file.type.mimeType, original.bytes, original.sha256, original.open)
                    },
                    places,
                    profile,
                    samples,
                    input.unsupportedPopulatedCategories,
                    input.validateContext,
                )
            NativeArchive3Writer.write(source, container)
        } catch (failure: Throwable) {
            runCatching { container.discard() }
            throw failure
        }
    }
}
