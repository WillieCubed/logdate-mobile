package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.parseDecimal
import app.logdate.client.sync.space.toDatetime
import app.logdate.client.sync.space.toDecimalString
import app.logdate.lexicon.app.logdate.place.Place
import app.logdate.lexicon.app.logdate.place.Visit
import app.logdate.lexicon.community.lexicon.location.Geo
import app.logdate.shared.model.location.ManualVisit
import kotlinx.serialization.json.JsonObject

/**
 * Maps a visit the person added to their history, a [ManualVisit], to an `app.logdate.place.visit`
 * record and back. The record key is the visit's id, the same key corrections and entry links use
 * to name it.
 *
 * A visit LogDate works out from samples is never a record. A visit's own location, when it
 * differs from its place's center, is the record's `location`. A record whose location has an
 * altitude or a label is refused, and so is a visit that ends before it starts.
 */
object PlaceVisitRecordMapper : SpaceRecordMapper<ManualVisit> {
    override val collection: String = Visit.TYPE

    override fun rkeyOf(value: ManualVisit): String = keyFor("$collection/${value.id}", value.id)

    override fun toRecord(value: ManualVisit): JsonObject {
        val owner = "$collection/${value.id}"
        keyFor(owner, value.id)
        if (value.end < value.start) throw SpaceRecordMappingException("$owner: it ends before it starts")
        val latitude = value.latitude
        val longitude = value.longitude
        return encodeRecord(
            Visit.serializer(),
            Visit(
                place = refTo(owner, Place.TYPE, value.placeId),
                location =
                    if (latitude != null && longitude != null) {
                        Geo(
                            latitude = latitude.toDecimalString("$owner latitude"),
                            longitude = longitude.toDecimalString("$owner longitude"),
                        )
                    } else {
                        null
                    },
                startsAt = value.start.toDatetime("$owner startsAt"),
                endsAt = value.end.toDatetime("$owner endsAt"),
                createdAt = value.createdAt?.toDatetime("$owner createdAt"),
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): ManualVisit {
        val owner = "$collection/$rkey"
        val decoded = decodeRecord(Visit.serializer(), collection, rkey, record)
        val start = parseDatetime("$owner startsAt", decoded.startsAt)
        val end = parseDatetime("$owner endsAt", decoded.endsAt)
        if (end < start) throw SpaceRecordMappingException("$owner: it ends before it starts")
        val location = decoded.location
        if (location != null && (location.altitude != null || location.name != null)) {
            throw SpaceRecordMappingException("$owner: its location has an altitude or a label, which this device can't keep")
        }
        return ManualVisit(
            id = keyFor(owner, rkey),
            start = start,
            end = end,
            placeId = keyOfRef(owner, Place.TYPE, decoded.place),
            latitude = location?.let { parseDecimal("$owner latitude", it.latitude) },
            longitude = location?.let { parseDecimal("$owner longitude", it.longitude) },
            createdAt = decoded.createdAt?.let { parseDatetime("$owner createdAt", it) },
        )
    }
}
