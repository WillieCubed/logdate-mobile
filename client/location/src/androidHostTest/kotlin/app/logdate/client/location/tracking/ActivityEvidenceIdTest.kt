package app.logdate.client.location.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ActivityEvidenceIdTest {
    @Test
    fun activityMetadataIsOpaqueWhileRepeatedDeliveryIsStable() {
        val id = activityEvidenceId("private-device", 123456789L, 7, 1)
        assertTrue(id.matches(Regex("[0-9a-f]{64}")))
        assertTrue("private-device" !in id && "123456789" !in id)
        assertEquals(id, activityEvidenceId("private-device", 123456789L, 7, 1))
        assertNotEquals(id, activityEvidenceId("private-device", 123456789L, 7, 0))
    }
}
