package app.logdate.client.data.account

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnavailablePasskeyAccountRepositoryTest {
    @Test
    fun `restore key sign in fails without a desktop authenticator`() =
        runTest {
            val repository = UnavailablePasskeyAccountRepository()

            assertTrue(repository.createRestoreKey().isFailure)
            assertTrue(repository.signOut().isSuccess)
            assertFalse(repository.isAuthenticated.value)
            assertNull(repository.getCurrentAccount())

            val restored = repository.signInWithRestoreKey()

            assertTrue(restored.isFailure)
            assertFalse(repository.isAuthenticated.value)
        }

    @Test
    fun `delete restore key fails clearly`() =
        runTest {
            val repository = UnavailablePasskeyAccountRepository()

            assertTrue(repository.deleteRestoreKey().isFailure)
        }
}
