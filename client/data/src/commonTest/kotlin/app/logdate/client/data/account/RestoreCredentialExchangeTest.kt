package app.logdate.client.data.account

import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.BeginAuthenticationData
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAllowCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RestoreCredentialExchangeTest {
    private val beginData =
        BeginAuthenticationData("challenge", "example.com", listOf(PasskeyAllowCredential(id = "key")), 1000, "discouraged")
    private val account =
        LogDateAccount(
            Uuid.random(),
            "synthetic",
            "Synthetic",
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
    private val response = CompleteAuthenticationData(account, AccountTokens("access", "refresh"))
    private val assertion =
        """
        {"id":"key","rawId":"key","response":{
          "clientDataJSON":"client","authenticatorData":"auth","signature":"sig","userHandle":"owner"
        },"type":"public-key"}
        """.trimIndent()

    @Test
    fun `exchanges the issued challenge and credential without a session graph`() =
        runTest {
            val exchange =
                RestoreCredentialExchange(
                    begin = { Result.success(beginData) },
                    getCredential = {
                        assertEquals("example.com", it.rpId)
                        assertEquals(listOf("key"), it.allowCredentials)
                        Result.success(assertion)
                    },
                    complete = {
                        assertEquals("challenge", it.challenge)
                        assertEquals("sig", it.credential.response.signature)
                        Result.success(response)
                    },
                )
            assertEquals(response, exchange().getOrThrow())
        }

    @Test
    fun `does not complete a rejected credential`() =
        runTest {
            var completed = false
            val exchange =
                RestoreCredentialExchange(
                    { Result.success(beginData) },
                    { Result.failure(IllegalStateException("unavailable")) },
                    {
                        completed = true
                        Result.success(response)
                    },
                )
            assertEquals("unavailable", exchange().exceptionOrNull()?.message)
            assertFalse(completed)
        }

    @Test
    fun `cancellation returned by an API is propagated`() =
        runTest {
            val exchange =
                RestoreCredentialExchange(
                    { Result.failure(CancellationException("callback expired")) },
                    { Result.success(assertion) },
                    { Result.success(response) },
                )
            assertFailsWith<CancellationException> { exchange() }
        }
}
