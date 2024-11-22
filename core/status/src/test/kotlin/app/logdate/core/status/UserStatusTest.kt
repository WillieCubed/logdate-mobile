package app.logdate.core.status

import app.logdate.core.activitypub.LogdateServerClient
import kotlinx.coroutines.test.runTest
import org.junit.Test


class UserStatusTest {

    @Test
    fun `ensure status can be set online`() = runTest {
        // Given
        val logdateServerClient = LogdateServerClient(
            domain = "logdate.app"
        )
        val presenceProvider = DefaultPresenceProvider(logdateServerClient)

        // When
        presenceProvider.setIsOnline(true)

        // Then
        val status = presenceProvider.isOnline
        assert(status)
    }

    @Test
    fun `ensure status can be reset after being set`() = runTest {
        val logdateServerClient = LogdateServerClient(
            domain = "logdate.app"
        )
        val presenceProvider = DefaultPresenceProvider(logdateServerClient)

        presenceProvider.setIsOnline(true)
        assert(presenceProvider.isOnline)
        presenceProvider.setIsOnline(false)
        assert(!presenceProvider.isOnline)
    }
}
