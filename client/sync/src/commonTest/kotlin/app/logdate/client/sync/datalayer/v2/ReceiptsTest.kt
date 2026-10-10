package app.logdate.client.sync.datalayer.v2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests the receipts the phone gives the watch for each record.
 */
class ReceiptsTest {
    private val response =
        ReceiptsResponse(
            cloud = CloudPresence.SIGNED_IN,
            receipts = listOf(Receipt(RecordKind.NOTE, ID, contentVersion = 5, state = ReceiptState.BACKED_UP, serverVersion = 9)),
        )

    @Test
    fun `a receipts response has a stable wire form`() {
        assertEquals(GOLDEN, WearJson.encode(ReceiptsResponse.serializer(), response))
    }

    @Test
    fun `a receipts request reads back`() {
        val request = ReceiptsRequest(items = listOf(ReceiptQuery(RecordKind.MOMENT, ID, contentVersion = 7)))
        val bytes = WearJson.encodeToBytes(ReceiptsRequest.serializer(), request)

        assertEquals(Decoded.Supported(request), WearJson.decode(bytes, WearProtocol.RECEIPTS_SCHEMA, ReceiptsRequest.serializer()))
    }

    @Test
    fun `a receipt for an older version never covers a newer edit`() {
        val receipt = Receipt(RecordKind.NOTE, ID, contentVersion = 5, state = ReceiptState.ON_PHONE)

        assertTrue(receipt.covers(5))
        assertTrue(receipt.covers(4))
        assertFalse(receipt.covers(6))
    }

    @Test
    fun `an unknown receipt state in this schema is malformed`() {
        val unknown = GOLDEN.replace("BACKED_UP", "ARCHIVED")

        assertIs<Decoded.Malformed>(WearJson.decode(unknown, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer()))
    }

    @Test
    fun `a newer receipts schema is unsupported`() {
        val newer = GOLDEN.replace("\"schema\":1", "\"schema\":2")

        assertIs<Decoded.Unsupported>(WearJson.decode(newer, WearProtocol.RECEIPTS_SCHEMA, ReceiptsResponse.serializer()))
    }

    private companion object {
        const val ID = "550e8400-e29b-41d4-a716-446655440000"
        const val GOLDEN =
            """{"schema":1,"cloud":"SIGNED_IN","receipts":[{"kind":"NOTE","id":"550e8400-e29b-41d4-a716-446655440000",""" +
                """"contentVersion":5,"state":"BACKED_UP","serverVersion":9}]}"""
    }
}
