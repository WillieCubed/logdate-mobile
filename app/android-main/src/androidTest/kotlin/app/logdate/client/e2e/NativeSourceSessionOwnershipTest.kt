package app.logdate.client.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class NativeSourceSessionOwnershipTest {
    @Test
    fun rejectedSetupPreservesAnUnrelatedSession() =
        runBlocking {
            val sessions = mockk<SessionStorage>()
            val other = OriginBoundSession("https://example.test", UserSession("other-access", "other-refresh", "other-owner"))
            every { sessions.getOriginBoundSession() } returns other
            assertFailsWith<IllegalStateException> {
                clearNativeSourceSession(sessions, nativeSourceSession(Uuid.random().toString(), "fixture-owner"))
            }
            coVerify(exactly = 0) { sessions.clearSessionIfCurrent(any(), any()) }
            coVerify(exactly = 0) { sessions.clearSession() }
        }

    @Test
    fun failureBeforeSessionInstallationHasNothingToClear() =
        runBlocking {
            val sessions = mockk<SessionStorage>()
            every { sessions.getOriginBoundSession() } returns null
            every { sessions.getSession() } returns null
            assertFalse(clearNativeSourceSession(sessions, nativeSourceSession(Uuid.random().toString(), "fixture-owner")))
            coVerify(exactly = 0) { sessions.clearSessionIfCurrent(any(), any()) }
        }

    @Test
    fun cleanupUsesTheCapturedSessionAndCannotClearALaterSignIn() =
        runBlocking {
            val sessions = mockk<SessionStorage>()
            val expected = nativeSourceSession(Uuid.random().toString(), "fixture-owner")
            val bound = OriginBoundSession("https://example.test", expected)
            every { sessions.getOriginBoundSession() } returns bound
            coEvery { sessions.clearSessionIfCurrent(bound, any()) } returns false
            assertFailsWith<IllegalStateException> { clearNativeSourceSession(sessions, expected) }
            coEvery { sessions.clearSessionIfCurrent(bound, any()) } returns true
            assertTrue(clearNativeSourceSession(sessions, expected))
            coVerify(exactly = 2) { sessions.clearSessionIfCurrent(bound, any()) }
            coVerify(exactly = 0) { sessions.clearSession() }
        }
}
