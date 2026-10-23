package app.logdate.client.sync.space

import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import studio.hypertext.atproto.syntax.Nsid

/** One record an item is stored as: its type, its key, its content and the blobs it refers to. */
internal class SpaceOutgoing(
    val collection: String,
    val rkey: String,
    val record: JsonObject,
    val blobs: List<String>,
)

/**
 * Every record [item] is stored as, each checked against its schema, its main record first.
 *
 * @throws SpaceRecordMappingException when the item can't be written as records.
 * @throws SpaceRecordInvalidException when a record this device built fails its schema.
 */
internal fun <T : Any> SpaceKind<T>.outgoingRecords(
    item: T,
    validator: LexiconRecordValidator,
): List<SpaceOutgoing> = parts.flatMap { part -> part.outgoing(isMain = part === main, item, validator) }

private fun <T : Any, P> SpacePart<T, P>.outgoing(
    isMain: Boolean,
    item: T,
    validator: LexiconRecordValidator,
): List<SpaceOutgoing> {
    val values = recordsOf(item)
    if (isMain && values.size != 1) {
        throw SpaceRecordMappingException("$collection: an item must have exactly one main record, not ${values.size}")
    }
    return values.map { value ->
        val rkey = canonicalRecordKey(mapper.rkeyOf(value))
        val record = mapper.toRecord(value)
        val problems = validator.validateRecord(Nsid.require(collection), record).issues
        if (problems.isNotEmpty()) {
            // Paths only: an issue's message can quote the record's contents.
            val paths = problems.joinToString { it.path.ifEmpty { "(record)" } }
            throw SpaceRecordInvalidException("The $collection record this device built fails its schema at $paths")
        }
        SpaceOutgoing(collection, rkey, record, mapper.blobCidsOf(value))
    }
}
