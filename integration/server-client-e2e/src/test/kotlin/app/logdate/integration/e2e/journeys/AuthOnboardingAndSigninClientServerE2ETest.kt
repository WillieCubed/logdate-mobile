package app.logdate.integration.e2e.journeys

import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the foundational authentication and onboarding flow against a
 * live server environment.
 *
 * These tests verify the security-critical paths of identity creation using synthetic
 * passkeys, username availability negotiation, and the subsequent lifecycle of
 * OAuth-style access and refresh tokens, ensuring a seamless and secure entry point
 * for new and returning users.
 */
class AuthOnboardingAndSigninClientServerE2ETest {
    @Test
    fun `client signup and token lifecycle succeed against real server`() =
        kotlinx.coroutines.test.runTest {
            withServerClientHarness {
                val username = "journey_auth_${Random.nextInt(1000, 9999)}"

                val availability = apiClient.checkUsernameAvailability(username)
                assertTrue(availability.isSuccess)
                assertTrue(availability.getOrThrow().available)

                val complete = apiClient.createAccountWithSyntheticPasskey(username)
                assertEquals(username, complete.data.account.username)

                val accessToken = complete.data.tokens.accessToken
                val refreshToken = complete.data.tokens.refreshToken

                val accountInfo = apiClient.getAccountInfo(accessToken)
                assertTrue(accountInfo.isSuccess)
                assertEquals(username, accountInfo.getOrThrow().username)

                val refreshed = apiClient.refreshAccessToken(refreshToken)
                assertTrue(refreshed.isSuccess)
                assertTrue(refreshed.getOrThrow().isNotBlank())
            }
        }
}
