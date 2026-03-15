package app.logdate.client.data.account

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StubPasskeyAccountRepositoryTest {
    @Test
    fun `restore key sign in reauthenticates the stub account`() =
        runTest {
            val repository = StubPasskeyAccountRepository()

            assertTrue(repository.createRestoreKey().isSuccess)
            assertTrue(repository.signOut().isSuccess)
            assertTrue(!repository.isAuthenticated.value)

            val restored = repository.signInWithRestoreKey()

            assertTrue(restored.isSuccess)
            assertEquals("desktop_user", restored.getOrThrow().username)
            assertTrue(repository.isAuthenticated.value)
        }

    @Test
    fun `delete restore key is a non fatal no op`() =
        runTest {
            val repository = StubPasskeyAccountRepository()

            assertTrue(repository.deleteRestoreKey().isSuccess)
        }
}
