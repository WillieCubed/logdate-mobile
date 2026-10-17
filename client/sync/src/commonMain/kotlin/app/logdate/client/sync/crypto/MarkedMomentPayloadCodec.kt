package app.logdate.client.sync.crypto

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Cloud marks have no plaintext legacy representation. Identity is also bound inside the payload. */
class MarkedMomentPayloadCodec(
    private val cipher: SyncPayloadCipher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun encode(moment: MarkedMoment): String =
        cipher.encryptString(
            fieldId(moment.id),
            json.encodeToString(
                Payload(
                    id = moment.id.toString(),
                    capturedAt = moment.capturedAt.toString(),
                    timeZoneId = moment.timeZoneId,
                    location = moment.location?.let { Location(it.latitude, it.longitude, it.accuracyMeters) },
                    heartRateBpm = moment.heartRateBpm,
                    state = moment.state.name,
                    caption = moment.caption,
                    resolvedNoteIds = moment.resolvedNoteIds.map { it.toString() }.sorted(),
                    originDeviceId = moment.originDeviceId,
                    lastUpdated = moment.lastUpdated.toString(),
                    capturedDuring = moment.capturedDuring?.wireName,
                ),
            ),
        )

    suspend fun decode(
        id: Uuid,
        schema: Int,
        serverVersion: Long,
        payload: String,
    ): MarkedMoment {
        require(schema == 1) { "Unsupported marked-moment schema" }
        require(serverVersion >= 0) { "Invalid marked-moment server version" }
        require(payload.startsWith("LDSE2:")) { "Marked moments require encrypted payloads" }
        val decoded = json.decodeFromString<Payload>(cipher.decryptString(fieldId(id), payload))
        require(decoded.id == id.toString()) { "Marked-moment payload identity mismatch" }
        return MarkedMoment(
            id = id,
            capturedAt = Instant.parse(decoded.capturedAt),
            timeZoneId = decoded.timeZoneId,
            location = decoded.location?.let { MarkLocation(it.latitude, it.longitude, it.accuracyMeters) },
            heartRateBpm = decoded.heartRateBpm,
            state = MarkState.valueOf(decoded.state),
            caption = decoded.caption,
            resolvedNoteIds = decoded.resolvedNoteIds.map(Uuid::parse).toSet(),
            originDeviceId = decoded.originDeviceId,
            lastUpdated = Instant.parse(decoded.lastUpdated),
            syncVersion = serverVersion,
            capturedDuring = CaptureActivity.fromWireName(decoded.capturedDuring),
        )
    }

    private fun fieldId(id: Uuid): String = "sync:moment:$id:payload"

    @Serializable
    private data class Payload(
        val id: String,
        val capturedAt: String,
        val timeZoneId: String,
        val location: Location?,
        val heartRateBpm: Int?,
        val state: String,
        val caption: String,
        val resolvedNoteIds: List<String>,
        val originDeviceId: String,
        val lastUpdated: String,
        /** Left out while null, so older payloads and older readers are unchanged. */
        val capturedDuring: String? = null,
    )

    @Serializable
    private data class Location(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
    )
}
