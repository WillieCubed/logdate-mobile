package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.v2.window.WatchWindowPublisher
import app.logdate.client.sync.v2.window.WindowResyncResponder
import app.logdate.client.sync.v2.window.WindowRollWorker
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WatchWindowRuntimeTest {
    @Test
    fun the_installed_phone_resolves_its_window_publisher_and_responder() {
        val koin = KoinJavaComponent.getKoin()
        assertNotNull(koin.get<WatchWindowPublisher>())
        assertNotNull(koin.get<WindowResyncResponder>())
    }

    @Test
    fun the_installed_manifest_receives_window_resync_requests() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val request =
            Intent("com.google.android.gms.wearable.REQUEST_RECEIVED")
                .setData("wear://watch/logdate/v2/window/resync".toUri())
                .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(request, PackageManager.GET_RESOLVED_FILTER)
        assertTrue(matches.any { it.serviceInfo.name.endsWith("PhoneDataLayerListenerService") })
    }

    @Test
    fun the_installed_roll_worker_resolves_dependencies_and_respects_the_disabled_flag() =
        runBlocking {
            val flags = KoinJavaComponent.getKoin().get<FeatureFlagStore>()
            val wasEnabled = flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)
            try {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, false)
                val context = ApplicationProvider.getApplicationContext<Context>()
                val worker = TestListenableWorkerBuilder<WindowRollWorker>(context).build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
            } finally {
                flags.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, wasEnabled)
            }
        }
}
