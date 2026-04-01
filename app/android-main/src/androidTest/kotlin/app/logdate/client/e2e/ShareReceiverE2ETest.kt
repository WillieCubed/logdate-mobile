package app.logdate.client.e2e

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.sharing.CustomIntents
import app.logdate.client.sharing.ShareReceiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class ShareReceiverE2ETest {
    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var clipboardContext: Context
    private lateinit var capturedClip: ClipData

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val clipSlot = slot<ClipData>()
        clipboardManager = mockk(relaxed = true)
        every { clipboardManager.setPrimaryClip(capture(clipSlot)) } answers {
            capturedClip = clipSlot.captured
        }
        clipboardContext =
            object : ContextWrapper(context) {
                override fun getSystemService(name: String): Any? =
                    when (name) {
                        Context.CLIPBOARD_SERVICE -> clipboardManager
                        else -> super.getSystemService(name)
                    }
            }
    }

    @Test
    fun copyLinkAction_writesSharedLinkToClipboard() {
        val sharedLink = "https://logdate.app/j/example"

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ShareReceiver().onReceive(
                clipboardContext,
                Intent(clipboardContext, ShareReceiver::class.java).apply {
                    action = CustomIntents.ACTION_COPY_LINK
                    putExtra(CustomIntents.EXTRA_SHARE_TEXT, sharedLink)
                },
            )
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        verify(exactly = 1) { clipboardManager.setPrimaryClip(any()) }
        assertNotNull(capturedClip)
        assertEquals(sharedLink, capturedClip.getItemAt(0).coerceToText(context).toString())
    }
}
