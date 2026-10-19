package app.logdate.client.sync.space.mappers

import app.logdate.client.sync.space.SpaceRecordMapper
import app.logdate.client.sync.space.SpaceRecordMappingException
import app.logdate.client.sync.space.decodeRecord
import app.logdate.client.sync.space.encodeRecord
import app.logdate.client.sync.space.parseDatetime
import app.logdate.client.sync.space.toDatetime
import app.logdate.lexicon.app.logdate.place.Journey
import app.logdate.lexicon.app.logdate.place.Visit
import app.logdate.shared.model.location.HistoryJourney
import kotlinx.serialization.json.JsonObject

/**
 * Maps a [HistoryJourney], travel between two visits, to an `app.logdate.place.journey` record and
 * back. The record key is the journey's id.
 *
 * Nothing on this device writes journeys yet. The mapper exists so that one written by another
 * app, or by a later version of this one, syncs and isn't lost. A journey that ends before it
 * starts, or a negative distance, is refused.
 */
object PlaceJourneyRecordMapper : SpaceRecordMapper<HistoryJourney> {
    override val collection: String = Journey.TYPE

    override fun rkeyOf(value: HistoryJourney): String = keyFor("$collection/${value.id}", value.id)

    override fun toRecord(value: HistoryJourney): JsonObject {
        val owner = "$collection/${value.id}"
        keyFor(owner, value.id)
        checkShape(owner, value.start, value.end, value.distanceMeters)
        return encodeRecord(
            Journey.serializer(),
            Journey(
                from = value.fromVisitId?.let { refTo(owner, Visit.TYPE, it) },
                to = value.toVisitId?.let { refTo(owner, Visit.TYPE, it) },
                startsAt = value.start.toDatetime("$owner startsAt"),
                endsAt = value.end?.toDatetime("$owner endsAt"),
                mode = value.mode?.let { TravelModes.toLexicon(owner, it) },
                distanceMeters = value.distanceMeters,
                createdAt = value.createdAt.toDatetime("$owner createdAt"),
            ),
        )
    }

    override fun fromRecord(
        rkey: String,
        record: JsonObject,
    ): HistoryJourney {
        val owner = "$collection/$rkey"
        val decoded = decodeRecord(Journey.serializer(), collection, rkey, record)
        val journey =
            HistoryJourney(
                id = keyFor(owner, rkey),
                start = parseDatetime("$owner startsAt", decoded.startsAt),
                createdAt = parseDatetime("$owner createdAt", decoded.createdAt),
                fromVisitId = decoded.from?.let { keyOfRef(owner, Visit.TYPE, it) },
                toVisitId = decoded.to?.let { keyOfRef(owner, Visit.TYPE, it) },
                end = decoded.endsAt?.let { parseDatetime("$owner endsAt", it) },
                mode = decoded.mode?.let { TravelModes.fromLexicon(owner, it) },
                distanceMeters = decoded.distanceMeters,
            )
        checkShape(owner, journey.start, journey.end, journey.distanceMeters)
        return journey
    }

    private fun checkShape(
        owner: String,
        start: kotlin.time.Instant,
        end: kotlin.time.Instant?,
        distanceMeters: Long?,
    ) {
        if (end != null && end < start) throw SpaceRecordMappingException("$owner: it ends before it starts")
        if (distanceMeters != null && distanceMeters < 0) throw SpaceRecordMappingException("$owner: its distance is negative")
    }
}
