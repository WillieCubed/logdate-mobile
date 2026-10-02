package app.logdate.feature.core.settings.ui.devices

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceApprovalEnvelopeCacheTest {
    @Test
    fun repeatedApprovalUsesTheSameSealedEnvelope() =
        runTest {
            val cache = DeviceApprovalEnvelopeCache()
            var seals = 0

            assertEquals("envelope-1", cache.envelopeFor("request-a") { "envelope-${++seals}" })
            assertEquals("envelope-1", cache.envelopeFor("request-a") { "envelope-${++seals}" })
            assertEquals(1, seals)
        }

    @Test
    fun anotherRequestGetsANewEnvelope() =
        runTest {
            val cache = DeviceApprovalEnvelopeCache()

            assertEquals("envelope-a", cache.envelopeFor("request-a") { "envelope-a" })
            assertEquals("envelope-b", cache.envelopeFor("request-b") { "envelope-b" })
        }

    @Test
    fun clearDropsThePreviousEnvelope() =
        runTest {
            val cache = DeviceApprovalEnvelopeCache()

            cache.envelopeFor("request-a") { "original" }
            cache.clear()

            assertEquals("replacement", cache.envelopeFor("request-a") { "replacement" })
        }
}
