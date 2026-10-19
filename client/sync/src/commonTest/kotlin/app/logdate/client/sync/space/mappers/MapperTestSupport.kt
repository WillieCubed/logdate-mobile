package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceMediaReference
import app.logdate.lexicon.LogDateLexicons
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.syntax.Nsid
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** The problems the lexicon finds in [record], as `path: message` lines. */
internal fun lexiconIssues(
    collection: String,
    record: JsonObject,
): List<String> =
    LogDateLexicons.validator
        .validateRecord(Nsid.require(collection), record)
        .issues
        .map { "${it.path}: ${it.message}" }

internal fun assertValidRecord(
    collection: String,
    record: JsonObject,
) {
    assertEquals(emptyList(), lexiconIssues(collection, record), "$record")
}

/** The content identifier of an uploaded file in tests; [suffix] tells files apart. */
internal fun cidOf(suffix: String) = "bafkreibme22gw2h7y2h7tg2fhqotaqjucnbc24deqo72b6mkl2egez$suffix"

/** The reference a note keeps for its file once the transport has uploaded it to the personal space. */
internal fun uploadedRef(
    noteId: Uuid,
    suffix: String,
    mimeType: String,
    size: Long? = 61_204,
    alt: String? = null,
): String = SpaceMediaReference(cidOf(suffix), noteId, mimeType, storedSize = 65_536, size = size, alt = alt).format()
