package app.logdate.client.watch

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RemoteActivityHelper only sends ACTION_VIEW intents with a data URI and CATEGORY_BROWSABLE. The
 * host tests cannot read a real Intent, so the shape every watch request uses is checked here.
 */
@RunWith(AndroidJUnit4::class)
class WatchLinkIntentTest {
    @Test
    fun `a watch link is a browsable view of the link`() {
        val intent = watchLinkIntent(WATCH_TODAY_LINK)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("studio.hypertext.logdate://watch/today", intent.dataString)
    }
}
