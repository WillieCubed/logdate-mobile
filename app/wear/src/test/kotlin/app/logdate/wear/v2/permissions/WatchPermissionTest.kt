package app.logdate.wear.v2.permissions

import android.Manifest
import org.junit.Test
import kotlin.test.assertEquals

class WatchPermissionTest {
    @Test
    fun `Wear OS 6 reads the heart rate under its health permission`() {
        assertEquals("android.permission.health.READ_HEART_RATE", heartRatePermission(sdkInt = 36))
    }

    @Test
    fun `earlier watches read the heart rate under body sensors`() {
        assertEquals(Manifest.permission.BODY_SENSORS, heartRatePermission(sdkInt = 34))
    }
}
