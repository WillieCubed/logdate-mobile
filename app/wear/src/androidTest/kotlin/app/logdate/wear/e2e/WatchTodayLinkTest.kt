package app.logdate.wear.e2e

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.wear.presentation.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone opens LogDate on the watch by sending this link through RemoteActivityHelper, which
 * only delivers browsable VIEW intents. Without a matching filter here, the request reaches the
 * watch and nothing opens.
 */
@RunWith(AndroidJUnit4::class)
class WatchTodayLinkTest {
    @Test
    @Suppress("DEPRECATION")
    fun the_today_link_from_the_phone_opens_main_activity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val link =
            Intent(Intent.ACTION_VIEW, Uri.parse("studio.hypertext.logdate://watch/today"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(context.packageName)

        val resolved = context.packageManager.resolveActivity(link, PackageManager.MATCH_DEFAULT_ONLY)

        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
    }
}
