package app.logdate.client.domain.restore.archive3

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.floor

internal fun validateRecords(
    journals: List<NativeArchive3JournalRecord>,
    notes: JsonArray,
    drafts: JsonArray,
    media: JsonArray,
    resources: Map<String, NativeArchive3Bundle.Resource>,
) {
    val journalIds = journals.map { it.id }.toSet()
    val noteIds = recordIds(notes)
    val draftIds = recordIds(drafts)
    require(noteIds.intersect(draftIds).isEmpty()) { "Ambiguous native archive identity" }
    val referencedMedia = linkedMapOf<String, MutableSet<String>>()
    notes.forEach { validateNote(it.jsonObject, journalIds, referencedMedia) }
    drafts.forEach { validateDraft(it.jsonObject, journalIds, referencedMedia) }
    validateInventory(media, resources, referencedMedia)
}

private fun recordIds(records: JsonArray): Set<String> {
    val ids =
        records.map {
            it.jsonObject
                .getValue("id")
                .string()
                .also(::requireCanonicalId)
        }
    require(ids.toSet().size == ids.size) { "Repeated native archive identity" }
    return ids.toSet()
}

private fun mediaReference(
    raw: JsonElement,
    requiredOriginal: Boolean,
    referencedMedia: MutableMap<String, MutableSet<String>>,
) {
    val ref = raw.jsonObject
    if (ref["status"] == JsonPrimitive("included")) {
        val path = ref.getValue("path").string()
        require(path.startsWith("media/"))
        val type = ref.getValue("mediaType").string()
        referencedMedia.getOrPut(path) { mutableSetOf() }.add(type)
    } else {
        require(!requiredOriginal) { "Native archive omits an original" }
    }
}

private fun membership(
    record: JsonObject,
    journalIds: Set<String>,
): Set<String> {
    val ids = record.getValue("journalIds").jsonArray.map { it.string().also(::requireCanonicalId) }
    require(ids.toSet().size == ids.size && journalIds.containsAll(ids)) { "Invalid native journal relationship" }
    return ids.toSet()
}

private fun validateNote(
    note: JsonObject,
    journalIds: Set<String>,
    referencedMedia: MutableMap<String, MutableSet<String>>,
) {
    val memberships = membership(note, journalIds)
    note.getValue("nativePrimaryJournalId").nullableString()?.let { require(it in memberships) }
    val nativeTime = note["nativeDateSeconds"]
    if (nativeTime != null) {
        val seconds = requireNotNull(nativeTime.jsonPrimitive.doubleOrNull)
        val millis = seconds * 1000
        require(millis.isFinite() && millis >= Long.MIN_VALUE.toDouble() && millis < Long.MAX_VALUE.toDouble())
    }
    require((nativeTime != null) == ("nativeUpdatedAt" in note))
    val kind = note.getValue("type").string()
    if (kind == "text") {
        require("media" !in note && "nativeOriginalFileName" !in note && "nativeMediaReference" !in note)
        require(
            note["text"] is JsonPrimitive && note["text"]!!.jsonPrimitive.isString && note["textFormat"] == JsonPrimitive("markdown"),
        )
    } else {
        mediaReference(requireNotNull(note["media"]), true, referencedMedia)
    }
    if (note["nativeSourceMetadataPresent"] == JsonPrimitive(false)) validateDerivedMetadata(note, kind, nativeTime)
    note["transcript"]?.jsonObject?.get("segments")?.jsonArray?.forEach { segment ->
        val item = segment.jsonObject
        require(item.getValue("endMs").jsonPrimitive.longOrNull!! >= item.getValue("startMs").jsonPrimitive.longOrNull!!)
    }
}

private fun validateDerivedMetadata(
    note: JsonObject,
    kind: String,
    nativeTime: JsonElement?,
) {
    require(nativeTime != null && "sourceMedia" !in note && note["nativeText"] is JsonPrimitive)
    val derived =
        setOf(
            "id",
            "type",
            "createdAt",
            "updatedAt",
            "media",
            "durationMs",
            "transcript",
            "journalIds",
            "nativeDateSeconds",
            "nativeUpdatedAt",
            "nativeText",
            "nativeMediaRevision",
            "nativeMediaReference",
            "nativePrimaryJournalId",
            "nativeOriginalFileName",
            "nativeSourceMetadataPresent",
        ) +
            if (kind == "text") setOf("text", "textFormat") else setOf("caption")
    require(note.keys.all { it in derived })
    require(note[if (kind == "text") "text" else "caption"] == note["nativeText"])
    require(note["updatedAt"] == note["nativeUpdatedAt"])
    val rawCreated = requireNotNull(note["createdAt"]?.nullableString())
    require(rawCreated.matches(Regex(".*\\.[0-9]{9}Z")))
    // Foundation Date stores seconds relative to 2001; observe the same binary64 value.
    val observed = (nativeTime.jsonPrimitive.doubleOrNull!! - 978307200.0) + 978307200.0
    var whole = floor(observed).toLong()
    var nanos = floor((observed - floor(observed)) * 1_000_000_000 + 0.5).toInt()
    if (nanos == 1_000_000_000) {
        whole++
        nanos = 0
    }
    require(sourceInstantParts(rawCreated) == whole to nanos) { "Native source timestamp disagrees with its observation" }
}

private fun validateDraft(
    draft: JsonObject,
    journalIds: Set<String>,
    referencedMedia: MutableMap<String, MutableSet<String>>,
) {
    membership(draft, journalIds)
    val blocks = draft.getValue("blocks").jsonArray
    val blockIds = recordIds(blocks)
    for (value in blocks) {
        val block = value.jsonObject
        for (field in listOf("media", "thumbnail")) block[field]?.let { mediaReference(it, true, referencedMedia) }
    }
    val edits = draft["nativeEditedText"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("blockId").string() }
    require(edits.distinct().size == edits.size && blockIds.containsAll(edits)) { "Invalid native draft edit" }
}

private fun validateInventory(
    media: JsonArray,
    resources: Map<String, NativeArchive3Bundle.Resource>,
    referencedMedia: Map<String, Set<String>>,
) {
    val inventory = linkedSetOf<String>()
    for (raw in media) {
        val item = raw.jsonObject
        val path = item.getValue("path").string()
        require(inventory.add(path)) { "Repeated native media path" }
        val resource = requireNotNull(resources[path])
        require(resource.bytes > 0 && item.getValue("bytes").jsonPrimitive.longOrNull == resource.bytes)
        require(item.getValue("sha256").string() == resource.sha256)
        require(referencedMedia[path] == setOf(item.getValue("mediaType").string())) { "Native media type mismatch" }
    }
    require(inventory == referencedMedia.keys) { "Native media inventory mismatch" }
}
