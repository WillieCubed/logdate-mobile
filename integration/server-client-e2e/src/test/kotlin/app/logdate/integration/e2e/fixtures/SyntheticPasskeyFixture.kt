package app.logdate.integration.e2e.fixtures

import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import kotlin.random.Random

fun syntheticPasskeyCredential(credentialId: String): PasskeyCredentialResponse =
    PasskeyCredentialResponse(
        id = credentialId,
        rawId = credentialId,
        response =
            PasskeyAuthenticatorResponse(
                clientDataJSON = "client-data-$credentialId",
                attestationObject = "attestation-$credentialId",
            ),
    )

suspend fun LogDateCloudApiClient.createAccountWithSyntheticPasskey(
    username: String,
    displayName: String = username,
): CompleteAccountCreationResponse {
    val begin =
        beginAccountCreation(
            BeginAccountCreationRequest(
                username = username,
                displayName = displayName,
            ),
        ).getOrElse { throw AssertionError("beginAccountCreation failed: ${it.message}", it) }

    val credentialId = "cred-$username-${Random.nextInt(1000, 9999)}"
    val complete =
        completeAccountCreation(
            CompleteAccountCreationRequest(
                sessionToken = begin.data.sessionToken,
                credential = syntheticPasskeyCredential(credentialId),
            ),
        ).getOrElse { throw AssertionError("completeAccountCreation failed: ${it.message}", it) }

    return complete
}
