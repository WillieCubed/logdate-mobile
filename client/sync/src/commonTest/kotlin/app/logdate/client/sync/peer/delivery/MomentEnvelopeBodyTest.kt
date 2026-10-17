package app.logdate.client.sync.peer.delivery

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.moments.MarkLocation
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.datalayer.v2.RecordEnvelopeCodec
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearJson
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MomentEnvelopeBodyTest {
    private val at = Instant.fromEpochMilliseconds(1234)
    private val moment =
        MarkedMoment(
            Uuid.random(),
            at,
            "UTC",
            MarkLocation(1.0, 2.0, 3f),
            72,
            MarkState.FILLED,
            "Remember",
            setOf(Uuid.random()),
            "watch-device",
            at,
            8,
        )

    @Test fun separateMomentEnvelopeRoundTripsEveryField() {
        val envelope = MomentEnvelopeBody.envelope(moment, RecordOrigin.WATCH)
        assertEquals(RecordKind.MOMENT, envelope.kind)
        assertEquals(1234L, envelope.contentVersion)
        assertEquals(8L, envelope.syncVersion)
        assertEquals(moment, MomentEnvelopeBody.decode(envelope))
        RecordEnvelopeCodec.encode(envelope)
    }

    @Test fun theActivityRecordedWithAMarkTravelsAndIsLeftOutWhileNull() {
        val inVehicle = moment.copy(capturedDuring = CaptureActivity.IN_VEHICLE)
        assertEquals(inVehicle, MomentEnvelopeBody.decode(MomentEnvelopeBody.envelope(inVehicle, RecordOrigin.WATCH)))
        val body =
            WearJson.json.encodeToString(
                JsonElement.serializer(),
                requireNotNull(MomentEnvelopeBody.envelope(moment, RecordOrigin.WATCH).body),
            )
        assertFalse("capturedDuring" in body)
    }

    @Test fun preciseClocksKeepTheirBodyWhileVersionUsesMilliseconds() {
        val precise =
            moment.copy(
                capturedAt = Instant.parse("1970-01-01T00:00:01.234123456Z"),
                lastUpdated = Instant.parse("1970-01-01T00:00:01.234987654Z"),
            )
        assertEquals(precise, MomentEnvelopeBody.decode(MomentEnvelopeBody.envelope(precise, RecordOrigin.PHONE)))
    }

    @Test fun mismatchedIdentityVersionAndKindAreRefused() {
        val envelope = MomentEnvelopeBody.envelope(moment, RecordOrigin.WATCH)
        assertFailsWith<IllegalArgumentException> { MomentEnvelopeBody.decode(envelope.copy(id = Uuid.random().toString())) }
        assertFailsWith<IllegalArgumentException> { MomentEnvelopeBody.decode(envelope.copy(contentVersion = 1235)) }
        assertFailsWith<IllegalArgumentException> { MomentEnvelopeBody.decode(envelope.copy(kind = RecordKind.NOTE)) }
        assertFailsWith<IllegalArgumentException> { MomentEnvelopeBody.decode(envelope.copy(syncVersion = -1)) }
        assertFailsWith<IllegalArgumentException> { MomentEnvelopeBody.decode(envelope.copy(removed = true)) }
    }

    @Test fun unknownBodySchemaAndStateAreRefused() {
        val envelope = MomentEnvelopeBody.envelope(moment, RecordOrigin.WATCH)

        fun edited(
            from: String,
            to: String,
        ) = envelope.copy(
            body =
                WearJson.json.parseToJsonElement(
                    WearJson.encode(JsonElement.serializer(), envelope.body!!).replace(from, to),
                ),
        )
        assertFailsWith<IllegalArgumentException> { MomentEnvelopeBody.decode(edited("\"schema\":1", "\"schema\":2")) }
        assertFailsWith<IllegalArgumentException> { MomentEnvelopeBody.decode(edited("FILLED", "FUTURE_STATE")) }
    }
}
