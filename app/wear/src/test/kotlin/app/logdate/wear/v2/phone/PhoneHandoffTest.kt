package app.logdate.wear.v2.phone

import android.content.Intent
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneHandoffTest {
    private val remoteActivityHelper = mockk<RemoteActivityHelper>()
    private val intent = mockk<Intent>()
    private val sentLinks = mutableListOf<String>()
    private val handoff =
        PhoneHandoff(
            remoteActivityHelper = remoteActivityHelper,
            packageName = "studio.hypertext.logdate",
            ioDispatcher = UnconfinedTestDispatcher(),
            viewIntent = { link ->
                sentLinks += link
                intent
            },
        )

    @Test
    fun `the Play listing opens on the phone that lacks LogDate`() =
        runTest {
            val accepted = mockk<ListenableFuture<Void>> { every { get(any(), any()) } returns null }
            every { remoteActivityHelper.startRemoteActivity(intent, "phone-1") } returns accepted

            assertEquals(HandoffResult.SENT, handoff.openAppListing("phone-1"))
            assertEquals(listOf("market://details?id=studio.hypertext.logdate"), sentLinks)
        }

    @Test
    fun `a phone that refuses the link is reported`() =
        runTest {
            val refused =
                mockk<ListenableFuture<Void>> {
                    every { get(any(), any()) } throws ExecutionException(IllegalStateException("No handler"))
                }
            every { remoteActivityHelper.startRemoteActivity(intent, "phone-1") } returns refused

            assertEquals(HandoffResult.FAILED, handoff.open("studio.hypertext.logdate://settings/watch", "phone-1"))
        }

    @Test
    fun `a phone that never answers is reported`() =
        runTest {
            val silent = mockk<ListenableFuture<Void>> { every { get(any(), any()) } throws TimeoutException() }
            every { remoteActivityHelper.startRemoteActivity(intent, "phone-1") } returns silent

            assertEquals(HandoffResult.FAILED, handoff.openAppListing("phone-1"))
        }
}
