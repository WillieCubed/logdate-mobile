package app.logdate.client.e2e

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.data.restore.NativeAccountRecoveryJobService
import app.logdate.client.data.restore.NativeAccountRecoveryRestartReceiver
import app.logdate.client.ensureLogDateApplicationStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NativeAccountJobStateProbeTest {
    @Test
    fun persistedRequestRetainsItsOriginalIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("nativeAccountJobAcceptance") == "true")
        val context = instrumentation.targetContext
        require(Build.HARDWARE in setOf("ranchu", "goldfish") && context.packageName.endsWith(".debug"))
        ensureLogDateApplicationStarted(context.applicationContext as Application)
        val expected = JSONObject(assertNotNull(arguments.getString("nativeAccountExpectedRequest")))
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job =
            runBlocking {
                withTimeout(30_000) {
                    var pending = scheduler.getPendingJob(0x4e415252)
                    while (pending == null) {
                        delay(50)
                        pending = scheduler.getPendingJob(0x4e415252)
                    }
                    assertNotNull(pending)
                }
            }
        assertTrue(job.isPersisted)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, job.backoffPolicy)
        assertEquals(ComponentName(context, NativeAccountRecoveryJobService::class.java), job.service)
        val actual = JSONObject(assertNotNull(job.extras.getString("request")))
        assertEquals(expected.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
        expected.keys().forEach { assertEquals(expected.get(it), actual.get(it), "Recovery request field $it changed") }
        val service = context.packageManager.getServiceInfo(job.service, 0)
        assertTrue(service.enabled)
        assertFalse(service.exported)
        assertEquals("android.permission.BIND_JOB_SERVICE", service.permission)
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, NativeAccountRecoveryRestartReceiver::class.java), 0)
        assertTrue(receiver.enabled)
        assertFalse(receiver.exported)
    }
}
