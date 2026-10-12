package app.logdate.client.sync.datalayer.v2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowBodyKindTest {
    private val legacy =
        """
        {
          "context": {
            "linkId": "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE",
            "phoneNode": "phone", "watchNode": "watch", "mode": "PAIR"
          },
          "noteId": "00000000-0000-0000-0000-000000000001",
          "contentVersion": 1, "revision": 2,
          "body": {"sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "sizeBytes": 42}
        }
        """.trimIndent()

    @Test fun legacyBodyRequestsStillMeanNotes() {
        assertEquals(RecordKind.NOTE, WearJson.json.decodeFromString(WindowBodyRequest.serializer(), legacy).kind)
    }

    @Test fun momentKindSurvivesRequestAndTicketRoundTrips() {
        val request = WearJson.json.decodeFromString(WindowBodyRequest.serializer(), legacy).copy(kind = RecordKind.MOMENT)
        val ticket = WindowBodyTicket(request = request, channelPath = WindowBodyPaths.CHANNEL_PREFIX + "ticket")
        assertEquals(
            request,
            WearJson.json.decodeFromString(WindowBodyRequest.serializer(), WearJson.encode(WindowBodyRequest.serializer(), request)),
        )
        assertEquals(
            ticket,
            WearJson.json.decodeFromString(WindowBodyTicket.serializer(), WearJson.encode(WindowBodyTicket.serializer(), ticket)),
        )
    }

    @Test fun unknownRecordKindsAreNeverReadAsNotes() {
        assertFailsWith<IllegalArgumentException> {
            WearJson.json.decodeFromString(
                WindowBodyRequest.serializer(),
                legacy.dropLast(1) + ",\"kind\":\"FUTURE\"}",
            )
        }
    }
}
