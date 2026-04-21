package app.logdate.client.e2e

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
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

/**
 * Instrumented E2E tests for the [ShareReceiver] component.
 *
 * This suite validates the application's ability to handle custom broadcast intents
 * for sharing operations, such as copying deep links to the system clipboard and
 * launching the system share sheet with generated QR code assets.
 */
@RunWith(AndroidJUnit4::class)
class ShareReceiverE2ETest {
    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var clipboardContext: Context
    private lateinit var startedActivityIntent: Intent
    private lateinit var shareActionContext: Context
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
        shareActionContext =
            object : ContextWrapper(context) {
                override fun startActivity(intent: Intent) {
                    startedActivityIntent = intent
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

    @Test
    fun shareQrCodeAction_launchesChooserWithQrImagePayload() {
        val sharedLink = "https://logdate.app/j/example"
        val qrCodeUri = Uri.parse("content://app.logdate.test/qr/example.png")

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ShareReceiver().onReceive(
                shareActionContext,
                Intent(shareActionContext, ShareReceiver::class.java).apply {
                    action = CustomIntents.ACTION_SHARE_QR_CODE
                    putExtra(CustomIntents.EXTRA_SHARE_TEXT, sharedLink)
                    putExtra(CustomIntents.EXTRA_SHARE_TITLE, "Example journal")
                    putExtra(CustomIntents.EXTRA_SHARE_URI, qrCodeUri)
                },
            )
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(Intent.ACTION_CHOOSER, startedActivityIntent.action)
        val shareIntent =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                startedActivityIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                startedActivityIntent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent.action)
        assertEquals(qrCodeUri, shareIntent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertEquals(sharedLink, shareIntent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
