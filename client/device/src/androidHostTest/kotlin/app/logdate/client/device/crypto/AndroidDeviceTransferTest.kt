package app.logdate.client.device.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AndroidDeviceTransferTest {
    @Test
    fun `sealed approval matches Swift transfer fixture`() {
        val sender = AndroidDeviceTransfer(privateKey = ByteArray(32) { 0x22 })
        val envelope =
            sender.seal(
                recipientPublicKey = "e06Qm75__kTEZaIgA31gjuNYl9Me-XLwf3SJLLD3PxM",
                identityKey = ByteArray(32) { 0x31 },
                legacyMediaKey = ByteArray(32) { 0x44 },
                accountId = "61ad68f2-6d4b-42dd-b263-f838195714ad",
                requestId = Uuid.parse("4d90d8dc-49b2-4ba1-8c20-529b80c77542"),
                confirmationCode = "413827",
                nonce = ByteArray(12) { 0x15 },
            )
        val fields = Json.parseToJsonElement(envelope).jsonObject
        assertEquals("D6poTtKIZ7l_Smot7l34zpdOdrcBjj8iocTPJnhXDyA", fields.getValue("pk").jsonPrimitive.content)
        assertEquals("FRUVFRUVFRUVFRUV", fields.getValue("iv").jsonPrimitive.content)
        assertEquals(
            "o2eUQFsBL6GesSpQuypKWoCB59grVlAF4PamiEuzHgchOKQw4rLmd9weueWLiNwXmH6De2tiDYMHUrqtCwapY7s5cQVNmGHmCHpnAlgAl5LMYQYFrWSLN_FayGkUIKSz8lwmLgPVO-GPxbw7nb7-qBUEiiXCzZ6z3WtR6NI0MhzkvEsUpCC__3INf5w",
            fields.getValue("ct").jsonPrimitive.content,
        )
    }

    @Test
    fun `phone first transfer includes the account session for Swift`() {
        val sender = AndroidDeviceTransfer(privateKey = ByteArray(32) { 0x22 })
        val envelope =
            sender.seal(
                recipientPublicKey = "e06Qm75__kTEZaIgA31gjuNYl9Me-XLwf3SJLLD3PxM",
                identityKey = ByteArray(32) { 0x31 },
                legacyMediaKey = ByteArray(32) { 0x44 },
                accountId = "61ad68f2-6d4b-42dd-b263-f838195714ad",
                requestId = Uuid.parse("4d90d8dc-49b2-4ba1-8c20-529b80c77542"),
                confirmationCode = "413827",
                session = DeviceTransferSession("Willie", "access", "refresh"),
                nonce = ByteArray(12) { 0x15 },
            )
        val fields = Json.parseToJsonElement(envelope).jsonObject
        assertEquals(
            "o2eUQFsBL6GesSpQuypKWoCB59grVlAF4PamiEuzHgchOKQw4rLmd9weueWLiNwXmH6De2tiDYMHUrqtCwapY7s5cQVNmGHmCHpnAlgAl5LMYQYFrWSLN_FayGkUIKSz8lwmLgPVO-GPxbw7nb7-qBUEiiXCzZ6z3WtRuc1oxN240zwxyCjBkPQ0fPULguXJQKhe253MlipCPmYu6-wpY54ZEO4I2EVNorsOFERj3J1WTGm--erOr6My7mvph4IsMFWA55NRqvx6ETQc1XmW_W02SX7-TtKlJh-ZYqomHkoEB8E-bIE",
            fields.getValue("ct").jsonPrimitive.content,
        )
    }
}
