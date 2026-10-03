package app.logdate.client.networking

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DeviceApprovalCodeTest {
    @Test
    fun `native Swift QR fixture verifies on Android without trusting relay fields`() {
        val code =
            "logdate-device-enrollment:v2:eyJhY2NvdW50SWQiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTEiLCJ" +
                "jb25maXJtYXRpb25Db2RlIjoiMTIzNDU2IiwiZGV2aWNlTmFtZSI6Ik15IE1hYyIsImlkIjoiMjIyMjIyMjItMjIyMi0yMjIyLTI" +
                "yMjItMjIyMjIyMjIyMjIyIiwicHVibGljS2V5IjoiQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3YyJ" +
                "9"
        DeviceApprovalCode.parse(code).verify(request, account)
    }

    private val account = "11111111-1111-1111-1111-111111111111"
    private val id = "22222222-2222-2222-2222-222222222222"
    private val publicKey = Base64.UrlSafe.encode(ByteArray(32) { 7 }).trimEnd('=')
    private val request = DeviceEnrollmentRequest(id, "My Mac", publicKey, "123456", Long.MAX_VALUE, "pending")

    private fun qr(): String {
        val body =
            buildJsonObject {
                put("accountId", account)
                put("id", id)
                put("deviceName", "My Mac")
                put("publicKey", publicKey)
                put("confirmationCode", "123456")
            }
        return "logdate-device-enrollment:v2:" + Base64.UrlSafe.encode(body.toString().encodeToByteArray()).trimEnd('=')
    }

    @Test
    fun `scanned recipient key account and confirmation cannot be substituted by the relay`() {
        val code = DeviceApprovalCode.parse(qr())
        code.verify(request, account)
        assertFailsWith<IllegalArgumentException> {
            code.verify(request.copy(publicKey = Base64.UrlSafe.encode(ByteArray(32) { 8 }).trimEnd('=')), account)
        }
        assertFailsWith<IllegalArgumentException> { code.verify(request.copy(confirmationCode = "654321"), account) }
        assertFailsWith<IllegalArgumentException> { code.verify(request.copy(deviceName = "Someone else's Mac"), account) }
        assertFailsWith<IllegalArgumentException> { code.verify(request.copy(id = account), account) }
        assertFailsWith<IllegalArgumentException> { code.verify(request, id) }
    }

    @Test
    fun `request id alone cannot authorize encrypted key transfer`() {
        assertFailsWith<IllegalArgumentException> { DeviceApprovalCode.parse("logdate-device-enrollment:$id") }
    }
}
