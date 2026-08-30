@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.fixtures

import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import kotlin.uuid.Uuid

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
                requestedOwnerId = Uuid.random().toString(),
            ),
        ).getOrElse { throw AssertionError("beginAccountCreation failed: ${it.message}", it) }

    // Perform the real ceremony against the rpId and challenge the server just issued, so the
    // relying party verifies this the same way it verifies a platform authenticator. A
    // placeholder credential only survives the in-memory repositories.
    val options = begin.data.registrationOptions
    val authenticator = TestAuthenticator(rpId = options.rpId)
    val (clientDataJson, attestationObject) = authenticator.register(options.challenge)

    val complete =
        completeAccountCreation(
            CompleteAccountCreationRequest(
                sessionToken = begin.data.sessionToken,
                credential =
                    PasskeyCredentialResponse(
                        id = authenticator.credentialIdB64,
                        rawId = authenticator.credentialIdB64,
                        response =
                            PasskeyAuthenticatorResponse(
                                clientDataJSON = clientDataJson,
                                attestationObject = attestationObject,
                            ),
                    ),
            ),
        ).getOrElse { throw AssertionError("completeAccountCreation failed: ${it.message}", it) }

    return complete
}
