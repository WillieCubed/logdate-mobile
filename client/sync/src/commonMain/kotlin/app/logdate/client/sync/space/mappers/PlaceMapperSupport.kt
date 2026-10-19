package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.canonicalRecordKey
import app.logdate.lexicon.app.logdate.DefsRecordRef
import app.logdate.shared.model.location.TravelMode
import studio.hypertext.atproto.syntax.RecordKey

/*
 * What the marked-moment and location-history mappers share. Every failure names the record and
 * the field, never the value: coordinates, names and times are private.
 */

/** A record key for [id] in the form every client writes, or a refusal when no record can be keyed by it. */
internal fun keyFor(
    owner: String,
    id: String,
): String {
    if (id != canonicalRecordKey(id) || !RecordKey.isValid(id)) {
        throw SpaceRecordMappingException(
            "$owner: its id can't be a record key; ids are lowercase and use only letters, digits and . _ : ~ -",
        )
    }
    return id
}

/** A reference to the [collection] record keyed by [id]. */
internal fun refTo(
    owner: String,
    collection: String,
    id: String,
): DefsRecordRef = DefsRecordRef(collection = collection, rkey = keyFor(owner, id))

/** The key [ref] names, which must be a record of [collection]. */
internal fun keyOfRef(
    owner: String,
    collection: String,
    ref: DefsRecordRef,
): String {
    if (ref.collection !=
        collection
    ) {
        throw SpaceRecordMappingException("$owner: it refers to a ${ref.collection} where a $collection belongs")
    }
    return keyFor(owner, ref.rkey)
}

/** [value] when it has at most [max] items; a longer list can't fit its record, and cutting it short would lose some. */
internal fun <T> boundedList(
    owner: String,
    field: String,
    max: Int,
    value: List<T>,
): List<T> {
    if (value.size > max) throw SpaceRecordMappingException("$owner: $field holds ${value.size} items, more than the $max a record allows")
    return value
}

/**
 * Converts between a device's own name for a value and the lexicon's lowerCamelCase name. Names the
 * table doesn't know are refused: the lexicon's lists of values are open, but a value this device
 * can't hold would be lost the next time the device writes the record.
 */
internal class NameTable(
    private val field: String,
    table: Map<String, String>,
) {
    private val toLexicon = table
    private val fromLexicon = table.entries.associate { (local, lexicon) -> lexicon to local }

    init {
        require(fromLexicon.size == table.size) { "Two values of $field share a lexicon name" }
    }

    fun lexiconName(
        owner: String,
        local: String,
    ): String = toLexicon[local] ?: throw SpaceRecordMappingException("$owner: $field has a value this device can't write to a record")

    fun localName(
        owner: String,
        lexicon: String,
    ): String = fromLexicon[lexicon] ?: throw SpaceRecordMappingException("$owner: $field has a value this device doesn't know")
}

/** How the person moved, between [TravelMode] and the lexicon's names. */
internal object TravelModes {
    private val names =
        NameTable(
            "its travel mode",
            mapOf(
                TravelMode.STILL.name to "still",
                TravelMode.WALKING.name to "walking",
                TravelMode.RUNNING.name to "running",
                TravelMode.CYCLING.name to "cycling",
                TravelMode.VEHICLE.name to "vehicle",
                TravelMode.DRIVING.name to "driving",
                TravelMode.BUS.name to "bus",
                TravelMode.TRAIN.name to "train",
                TravelMode.UNKNOWN.name to "unknown",
            ),
        )

    fun toLexicon(
        owner: String,
        mode: TravelMode,
    ): String = names.lexiconName(owner, mode.name)

    fun fromLexicon(
        owner: String,
        name: String,
    ): TravelMode = TravelMode.valueOf(names.localName(owner, name))
}
