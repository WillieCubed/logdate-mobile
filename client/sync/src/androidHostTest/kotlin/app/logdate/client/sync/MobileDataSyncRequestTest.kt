package app.logdate.client.sync

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.metadata.UploadScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MobileDataSyncRequestTest {
    private val scope = UploadScope("owner", "origin")
    private val session = OriginBoundSession("origin", UserSession("fixture", "fixture", "owner"))

    @Test
    fun `automatic checks do not require an expedited foreground service`() {
        val request = immediateSyncRequest(AndroidLogDateSyncWorker.SYNC_TYPE_FULL, null)
        assertEquals(false, request.workSpec.expedited)
    }

    @Test
    fun `an explicit backup retains expedited execution with ordinary work fallback`() {
        val request = immediateSyncRequest(AndroidLogDateSyncWorker.SYNC_TYPE_FULL, null, expedited = true)
        assertEquals(true, request.workSpec.expedited)
        assertEquals(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.workSpec.outOfQuotaPolicy)
    }

    @Test
    fun `work input preserves one request consent across reconstruction`() {
        val input = mobileDataSyncInput(scope)
        val restored = androidx.work.Data.fromByteArray(input.toByteArray())
        assertEquals(scope, mobileDataSyncScope(restored, session))
    }

    @Test
    fun `consent cannot move to a different account or server`() {
        val input = mobileDataSyncInput(scope)
        assertNull(mobileDataSyncScope(input, session.copy(origin = "other")))
        assertNull(mobileDataSyncScope(input, session.copy(session = session.session.copy(accountId = "other"))))
        assertNull(mobileDataSyncScope(input, null))
        assertNull(mobileDataSyncScope(mobileDataSyncInput(null), session))
    }
}
