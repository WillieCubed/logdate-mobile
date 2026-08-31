package app.logdate.client.domain.user

import app.logdate.client.device.identity.CanonicalOwnerProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetUserIdUseCaseTest {
    @Test
    fun `uses canonical owner independently of cloud session`() =
        runTest {
            val useCase = GetUserIdUseCase(FakeCanonicalOwnerProvider("b930bf49-d1e9-4a65-b84b-8e74af45012b"))

            val result = useCase()

            assertIs<GetUserIdUseCase.UserIdResult.Success>(result)
            assertEquals("b930bf49-d1e9-4a65-b84b-8e74af45012b", result.userId)
        }

    private class FakeCanonicalOwnerProvider(
        private val ownerId: String,
    ) : CanonicalOwnerProvider {
        override suspend fun getCanonicalOwnerId(): String = ownerId

        override suspend fun hasBoundOwner(): Boolean = true
    }
}
