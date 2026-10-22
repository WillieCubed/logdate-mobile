package app.logdate.client.data.restore

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeAccountRecoveryJobsTest {
    @Test
    fun `scheduled recovery survives reboot and waits for network without requiring charging`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val request =
            NativeAccountRecoveryRequest("2f18404e-e9f5-434d-8b03-2f04f09ac523", "19b6ef01-19ec-4b65-8c82-c49b1ea25862", "a".repeat(64))
        assertTrue(scheduleNativeAccountRecovery(context, request))
        assertTrue(scheduleNativeAccountRecovery(context, request))
        val job = assertNotNull(context.getSystemService(JobScheduler::class.java).allPendingJobs.singleOrNull())
        assertTrue(job.isPersisted)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
        assertEquals(false, job.isRequireCharging)
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, job.backoffPolicy)
        assertEquals(Json.encodeToString(request), job.extras.getString("request"))
    }

    @Test
    fun `partial owner publication cannot replace the original persisted job payload`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val original =
            NativeAccountRecoveryRequest("2f18404e-e9f5-434d-8b03-2f04f09ac523", "19b6ef01-19ec-4b65-8c82-c49b1ea25862", "a".repeat(64))
        assertTrue(scheduleNativeAccountRecovery(context, original))
        val derived = original.copy(owner = "347c6231-19f4-49fa-bd1d-58ed34b9be03", binding = "true")
        kotlin.test.assertFalse(scheduleNativeAccountRecovery(context, derived))
        val job = context.getSystemService(JobScheduler::class.java).allPendingJobs.single()
        assertEquals(Json.encodeToString(original), job.extras.getString("request"))
    }
}
