package app.logdate.feature.core.settings.ui

import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_account_waiting
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudArchivePresentationTest {
    @Test
    fun `automatic backup failures remain waiting without technical explanations`() {
        for (phase in listOf(
            CloudArchivePhase.NEEDS_RECOVERY,
            CloudArchivePhase.FAILED,
            CloudArchivePhase.UNAVAILABLE,
            CloudArchivePhase.RETRYING,
        )) {
            assertEquals(Res.string.sync_account_waiting, CloudArchiveStatus(phase).messageResource())
        }
        assertEquals(Res.string.sync_account_waiting, CloudArchiveStatus(CloudArchivePhase.COMPLETE).messageResource())
        assertEquals(Res.string.sync_feedback_up_to_date, CloudArchiveStatus(CloudArchivePhase.COMPLETE, 123L).messageResource())
    }
}
