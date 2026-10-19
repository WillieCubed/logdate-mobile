package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.parseDecimal
import app.logdate.client.sync.space.toCentimeters
import app.logdate.client.sync.space.toDatetime
import app.logdate.client.sync.space.toDecimalString
import app.logdate.lexicon.app.logdate.place.Place
import app.logdate.lexicon.community.lexicon.location.Geo
import kotlinx.serialization.json.JsonObject

/**
 * Maps a [MergedPlace] to an `app.logdate.place.place` record and back. The record key is the
 * place's id. Deleting the record deletes both the saved place and the history place.
 *
 * A place no one confirmed writes no `userConfirmed`, and a place with a `createdAt` is always
 * confirmed, because a saved place is. A record that gives its center an altitude or a label is
 * refused: this device has nowhere to keep it.
 */
object PlaceRecordMapper : SpaceRecordMapper<MergedPlace> {
    override val collection: String = Place.TYPE

    override fun rkeyOf(value: MergedPlace): String = keyFor("$collection/${value.id}", value.id)

    override fun toRecord(value: MergedPlace): JsonObject {
        val owner = "$collection/${value.id}"
        keyFor(owner, value.id)
        return encodeRecord(
            Place.serializer(),
            Place(
                name = value.name,
                description = value.description,
                location =
                    Geo(
                        latitude = value.latitude.toDecimalString("$owner latitude"),
                        longitude = value.longitude.toDecimalString("$owner longitude"),
                    ),
                radiusCentimeters = value.radiusMeters?.toCentimeters("$owner radius"),
                locality = value.locality,
                externalId = value.externalId,
                userConfirmed = value.userConfirmed.takeIf { it },
                createdAt = value.savedAt?.toDatetime("$owner createdAt"),
                updatedAt = value.updatedAt?.toDatetime("$owner updatedAt"),
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): MergedPlace {
        val owner = "$collection/$rkey"
        val decoded = decodeRecord(Place.serializer(), collection, rkey, record)
        if (decoded.location.altitude != null || decoded.location.name != null) {
            throw SpaceRecordMappingException("$owner: its center has an altitude or a label, which this device can't keep")
        }
        val savedAt = decoded.createdAt?.let { parseDatetime("$owner createdAt", it) }
        return MergedPlace(
            id = keyFor(owner, rkey),
            name = decoded.name,
            latitude = parseDecimal("$owner latitude", decoded.location.latitude),
            longitude = parseDecimal("$owner longitude", decoded.location.longitude),
            radiusMeters = decoded.radiusCentimeters?.let { it / MergedPlace.CENTIMETERS_PER_METER },
            description = decoded.description,
            locality = decoded.locality,
            externalId = decoded.externalId,
            userConfirmed = decoded.userConfirmed == true || savedAt != null,
            savedAt = savedAt,
            updatedAt = decoded.updatedAt?.let { parseDatetime("$owner updatedAt", it) },
        )
    }
}
