package app.logdate.wear.sync.v2.removal

import android.content.Context
import app.logdate.client.device.storage.SecureStorage
import app.logdate.wear.recording.WearRecorder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchRemovalRecoveryTest {
    private var marker: String? = null
    private var pending = 0
    private val secure = mockk<SecureStorage>()
    private val recorder = mockk<WearRecorder>()
    private val wiper = mockk<WatchDataWiper>()
    private val subject =
        WatchRemovalRecovery(
            context = mockk<Context>(),
            lock = WatchRemovalLock(secure),
            recorder = recorder,
            wiper = wiper,
            pending = { pending },
        )

    init {
        coEvery { secure.getString(WatchRemovalLock.KEY) } answers { marker }
        every { recorder.isRecording } returns MutableStateFlow(false)
        coEvery { wiper.wipe() } returns Unit
    }

    @Test
    fun `legacy deferred removal and prepared removal never erase captures during recovery`() =
        runTest {
            marker = "pending:old-link"
            assertTrue(subject.finish())
            marker = "prepared:new-link"
            assertTrue(subject.finish())
            coVerify(exactly = 0) { wiper.wipe() }
        }

    @Test
    fun `interrupted wipe waits until the outbox is empty`() =
        runTest {
            marker = "wiping:new-link"
            pending = 1
            assertFalse(subject.finish())
            coVerify(exactly = 0) { wiper.wipe() }

            pending = 0
            assertTrue(subject.finish())
            coVerify(exactly = 1) { wiper.wipe() }
        }

    @Test
    fun `separate server revocation still erases the watch`() =
        runTest {
            marker = "wiping"
            pending = 1
            assertTrue(subject.finish())
            coVerify(exactly = 1) { wiper.wipe() }
        }
}
