package app.logdate.client.sync.peer.delivery

import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearJson
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A separate peer body; marks never enter the closed note serializer. */
object MomentEnvelopeBody {
    fun envelope(
        moment: MarkedMoment,
        origin: RecordOrigin,
    ): RecordEnvelope =
        RecordEnvelope(
            kind = RecordKind.MOMENT,
            id = moment.id.toString(),
            contentVersion = moment.lastUpdated.toEpochMilliseconds(),
            syncVersion = moment.syncVersion,
            origin = origin,
            body =
                WearJson.json.encodeToJsonElement(
                    Body.serializer(),
                    Body(
                        id = moment.id.toString(),
                        capturedAt = moment.capturedAt.toString(),
                        timeZoneId = moment.timeZoneId,
                        latitude = moment.location?.latitude,
                        longitude = moment.location?.longitude,
                        accuracy = moment.location?.accuracyMeters,
                        heartRateBpm = moment.heartRateBpm,
                        state = moment.state.name,
                        caption = moment.caption,
                        resolvedNoteIds = moment.resolvedNoteIds.map { it.toString() }.sorted(),
                        originDeviceId = moment.originDeviceId,
                        lastUpdated = moment.lastUpdated.toString(),
                    ),
                ),
        )

    fun decode(envelope: RecordEnvelope): MarkedMoment {
        require(envelope.schema == 1 && envelope.kind == RecordKind.MOMENT && !envelope.removed && envelope.enc == null)
        require(envelope.syncVersion >= 0 && envelope.bodyRef == null && envelope.media.isEmpty())
        val body = WearJson.json.decodeFromJsonElement(Body.serializer(), requireNotNull(envelope.body))
        require(body.schema == 1 && body.id == envelope.id)
        require((body.latitude == null) == (body.longitude == null))
        val moment =
            MarkedMoment(
                id = Uuid.parse(body.id),
                capturedAt = Instant.parse(body.capturedAt),
                timeZoneId = body.timeZoneId,
                location = body.latitude?.let { MarkLocation(it, requireNotNull(body.longitude), body.accuracy) },
                heartRateBpm = body.heartRateBpm,
                state = MarkState.valueOf(body.state),
                caption = body.caption,
                resolvedNoteIds = body.resolvedNoteIds.map(Uuid::parse).toSet(),
                originDeviceId = body.originDeviceId,
                lastUpdated = Instant.parse(body.lastUpdated),
                syncVersion = envelope.syncVersion,
            )
        require(moment.id.toString() == envelope.id && moment.lastUpdated.toEpochMilliseconds() == envelope.contentVersion)
        return moment
    }

    @Serializable
    private data class Body(
        val schema: Int = 1,
        val id: String,
        val capturedAt: String,
        val timeZoneId: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val accuracy: Float? = null,
        val heartRateBpm: Int? = null,
        val state: String,
        val caption: String,
        val resolvedNoteIds: List<String>,
        val originDeviceId: String,
        val lastUpdated: String,
    )
}
