package app.logdate.client.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncForegroundNotificationTitleTest {
    @Test
    fun `a long reconciliation with no uploads is labelled checking not backing up`() {
        assertEquals(SyncNotificationPhase.CHECKING, syncNotificationPhase(null))
        assertEquals(SyncNotificationPhase.CHECKING, syncNotificationPhase(0))
    }

    @Test
    fun `real pending backup work keeps the memories title`() {
        assertEquals(SyncNotificationPhase.BACKING_UP, syncNotificationPhase(1))
    }
}
