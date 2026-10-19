package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.centimetersToMeters
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.parseDecimal
import app.logdate.client.sync.space.parseUuidKey
import app.logdate.client.sync.space.recordRef
import app.logdate.client.sync.space.toCentimeters
import app.logdate.client.sync.space.toDatetime
import app.logdate.client.sync.space.toDecimalString
import app.logdate.lexicon.app.logdate.DefsLocation
import app.logdate.lexicon.app.logdate.journal.Entry
import app.logdate.lexicon.community.lexicon.location.Geo
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid
import app.logdate.lexicon.app.logdate.journal.MarkedMoment as MarkedMomentRecord

/**
 * Maps a [MarkedMoment] to an `app.logdate.journal.markedMoment` record and back.
 *
 * The record key is the mark's id, and the notes that filled it become references to the entries
 * those notes became, which share the notes' ids. A mark that is open and has no caption writes no
 * caption, and a record without one reads as an empty caption.
 *
 * [MarkedMoment.syncVersion] isn't part of the record: the kind gives a mark read from the space
 * the position of its newest record in the space's change log. A location's accuracy is kept in
 * whole centimeters, so a mark reads back with its accuracy rounded to the nearest centimeter.
 *
 * A mark this device can't hold without losing something is refused: a state or activity it doesn't
 * know, or a location that names a place, an altitude or a label, which a mark has nowhere to keep.
 */
object MarkedMomentRecordMapper : SpaceRecordMapper<MarkedMoment> {
    override val collection: String = MarkedMomentRecord.TYPE

    private const val MAX_RESOLVED_ENTRIES = 200
    private const val MAX_HEART_RATE_BPM = 400

    private val states =
        NameTable(
            "its state",
            mapOf(MarkState.OPEN.name to "open", MarkState.DISMISSED.name to "dismissed", MarkState.FILLED.name to "filled"),
        )

    private val activities =
        NameTable(
            "what the person was doing",
            mapOf(CaptureActivity.MOVING.name to "moving", CaptureActivity.IN_VEHICLE.name to "inVehicle"),
        )

    override fun rkeyOf(value: MarkedMoment): String = value.id.toString()

    override fun toRecord(value: MarkedMoment): JsonObject {
        val owner = "$collection/${value.id}"
        value.heartRateBpm?.let {
            if (it !in
                0..MAX_HEART_RATE_BPM
            ) {
                throw SpaceRecordMappingException("$owner: the heart rate is outside 0 to $MAX_HEART_RATE_BPM")
            }
        }
        val resolved =
            value.resolvedNoteIds
                .map { it.toString() }
                .sorted()
                .map { recordRef(Entry.TYPE, Uuid.parse(it)) }
        return encodeRecord(
            MarkedMomentRecord.serializer(),
            MarkedMomentRecord(
                state = states.lexiconName(owner, value.state.name),
                caption = value.caption.ifEmpty { null },
                resolvedEntries = boundedList(owner, "the notes that filled it", MAX_RESOLVED_ENTRIES, resolved).ifEmpty { null },
                location = value.location?.let { toLocation(owner, it) },
                timeZone = value.timeZoneId,
                heartRateBpm = value.heartRateBpm?.toLong(),
                capturedDuring = value.capturedDuring?.let { activities.lexiconName(owner, it.name) },
                originDevice = value.originDeviceId,
                createdAt = value.capturedAt.toDatetime("$owner createdAt"),
                updatedAt = value.lastUpdated.toDatetime("$owner updatedAt"),
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): MarkedMoment {
        val owner = "$collection/$rkey"
        val decoded = decodeRecord(MarkedMomentRecord.serializer(), collection, rkey, record)
        val capturedAt = parseDatetime("$owner createdAt", decoded.createdAt)
        return MarkedMoment(
            id = parseUuidKey(collection, rkey),
            capturedAt = capturedAt,
            timeZoneId = decoded.timeZone,
            location = decoded.location?.let { fromLocation(owner, it) },
            heartRateBpm =
                decoded.heartRateBpm?.let {
                    if (it !in
                        0..MAX_HEART_RATE_BPM
                    ) {
                        throw SpaceRecordMappingException("$owner: the heart rate is outside 0 to $MAX_HEART_RATE_BPM")
                    }
                    it.toInt()
                },
            state = MarkState.valueOf(states.localName(owner, decoded.state)),
            caption = decoded.caption.orEmpty(),
            resolvedNoteIds =
                decoded.resolvedEntries
                    .orEmpty()
                    .map { parseNoteId(owner, it.collection, it.rkey) }
                    .toSet(),
            originDeviceId = decoded.originDevice,
            lastUpdated = decoded.updatedAt?.let { parseDatetime("$owner updatedAt", it) } ?: capturedAt,
            syncVersion = 0,
            capturedDuring = decoded.capturedDuring?.let { CaptureActivity.valueOf(activities.localName(owner, it)) },
        )
    }

    private fun toLocation(
        owner: String,
        location: MarkLocation,
    ) = DefsLocation(
        geo =
            Geo(
                latitude = location.latitude.toDecimalString("$owner latitude"),
                longitude = location.longitude.toDecimalString("$owner longitude"),
            ),
        accuracyCentimeters = location.accuracyMeters?.toCentimeters("$owner accuracy"),
    )

    private fun fromLocation(
        owner: String,
        location: DefsLocation,
    ): MarkLocation {
        val geo = location.geo ?: throw SpaceRecordMappingException("$owner: its location has no coordinates, which a mark needs")
        if (geo.altitude != null || geo.name != null || location.place != null || location.placeDetails != null) {
            throw SpaceRecordMappingException("$owner: its location has an altitude, name or place, which a mark has nowhere to keep")
        }
        return MarkLocation(
            latitude = parseDecimal("$owner latitude", geo.latitude),
            longitude = parseDecimal("$owner longitude", geo.longitude),
            accuracyMeters = location.accuracyCentimeters?.centimetersToMeters(),
        )
    }

    private fun parseNoteId(
        owner: String,
        collection: String,
        rkey: String,
    ): Uuid {
        if (collection != Entry.TYPE) throw SpaceRecordMappingException("$owner: a note that filled it is a $collection, not an entry")
        return parseUuidKey(Entry.TYPE, rkey)
    }
}
