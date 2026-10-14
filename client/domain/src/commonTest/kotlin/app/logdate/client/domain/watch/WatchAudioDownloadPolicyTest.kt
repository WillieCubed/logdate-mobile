package app.logdate.client.domain.watch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchAudioDownloadPolicyTest {
    @Test fun chargerOrUnmeteredUsesEitherSafeRoute() {
        val policy = WatchAudioDownloadPolicy.WIFI_OR_CHARGING
        assertTrue(policy.permits(true, true, false))
        assertTrue(policy.permits(false, true, true))
        assertFalse(policy.permits(false, true, false))
        assertFalse(policy.permits(false, false, true))
    }

    @Test fun explicitChoicesKeepMeteredAndOfflineDownloadsOff() {
        assertFalse(WatchAudioDownloadPolicy.WIFI_ONLY.permits(false, true, true))
        assertFalse(WatchAudioDownloadPolicy.CHARGING_ONLY.permits(true, true, false))
        assertFalse(WatchAudioDownloadPolicy.ON_DEMAND.permits(true, true, true))
    }
}
