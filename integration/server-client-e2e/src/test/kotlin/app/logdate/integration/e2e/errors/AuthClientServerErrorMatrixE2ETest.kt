package app.logdate.integration.e2e.errors

import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.integration.e2e.fixtures.assertCloudError
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.fixtures.syntheticPasskeyCredential
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.model.CompleteAccountCreationRequest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthClientServerErrorMatrixE2ETest {
    @Test
    fun `auth endpoints surface validation and authorization errors to client`() =
        kotlinx.coroutines.test.runTest {
            withServerClientHarness {
                val invalidUsername = apiClient.checkUsernameAvailability("ab")
                assertCloudError(invalidUsername, expectedCode = "VALIDATION_ERROR", expectedStatus = 400)

                val completeWithMissingSession =
                    apiClient.completeAccountCreation(
                        CompleteAccountCreationRequest(
                            sessionToken = "miss",
                            credential = syntheticPasskeyCredential("cred-missing-session"),
                        ),
                    )
                assertCloudError(completeWithMissingSession, expectedCode = "INVALID_SESSION_TOKEN", expectedStatus = 401)

                val invalidRefresh = apiClient.refreshAccessToken("")
                assertCloudError(invalidRefresh, expectedCode = "INVALID_REFRESH_TOKEN", expectedStatus = 401)

                val invalidAccessToken = apiClient.getAccountInfo("invalid-token")
                assertCloudError(invalidAccessToken, expectedCode = "INVALID_TOKEN", expectedStatus = 401)

                val complete = apiClient.createAccountWithSyntheticPasskey("auth_err_${Random.nextInt(1000, 9999)}")
                val profile = apiClient.getAccountInfo(complete.data.tokens.accessToken)
                assertTrue(profile.isSuccess)
                assertIs<CloudApiException?>(invalidAccessToken.exceptionOrNull())
            }
        }
}
