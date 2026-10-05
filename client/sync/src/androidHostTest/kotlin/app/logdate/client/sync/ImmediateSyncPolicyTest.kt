package app.logdate.client.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class ImmediateSyncPolicyTest {
    @Test
    fun `reconnection replaces a queued request waiting in scheduler backoff`() {
        assertEquals(ExistingWorkPolicy.REPLACE, immediateSyncPolicy(listOf(WorkInfo.State.ENQUEUED)))
    }

    @Test
    fun `reconnection preserves a running sync`() {
        assertEquals(ExistingWorkPolicy.KEEP, immediateSyncPolicy(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.RUNNING)))
    }

    @Test
    fun `mobile data consent follows a running worker instead of being discarded`() {
        assertEquals(
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            immediateSyncPolicy(listOf(WorkInfo.State.RUNNING), hasMobileDataConsent = true),
        )
    }

    @Test
    fun `automatic scheduling preserves consent already stored in a queued request`() {
        assertEquals(
            ExistingWorkPolicy.KEEP,
            immediateSyncPolicy(listOf(WorkInfo.State.ENQUEUED), preserveConsentedRequest = true),
        )
        assertEquals(
            ExistingWorkPolicy.REPLACE,
            immediateSyncPolicy(listOf(WorkInfo.State.ENQUEUED), hasMobileDataConsent = true, preserveConsentedRequest = true),
        )
    }

    @Test
    fun `a failed scheduler request does not prevent resumption`() {
        assertEquals(ExistingWorkPolicy.REPLACE, immediateSyncPolicy(listOf(WorkInfo.State.FAILED)))
    }
}
