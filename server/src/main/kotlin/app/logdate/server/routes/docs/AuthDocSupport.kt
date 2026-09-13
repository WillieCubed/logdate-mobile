package app.logdate.server.routes.docs

import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.SIGNIN_RATE_LIMIT
import app.logdate.server.routes.SIGNUP_RATE_LIMIT
import app.logdate.server.routes.apiError
import app.logdate.server.routes.rateLimited
import app.logdate.shared.model.PasskeyAssertionAuthenticatorResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.ktor.http.HttpStatusCode

/** Examples and response helpers shared by the sign-up, sign-in, restore and token documentation. */
internal const val SESSION_TOKEN = "st_5f2c9e1a7b3d4c6e8f0a1b2c3d4e5f60"

internal val passkeyCredential =
    PasskeyCredentialResponse(
        id = DocExamples.CREDENTIAL_ID,
        rawId = DocExamples.CREDENTIAL_ID,
        response =
            PasskeyAuthenticatorResponse(
                clientDataJSON = DocExamples.CLIENT_DATA_JSON,
                attestationObject = DocExamples.ATTESTATION_OBJECT,
            ),
    )

internal val passkeyAssertion =
    PasskeyAssertionResponse(
        id = DocExamples.CREDENTIAL_ID,
        rawId = DocExamples.CREDENTIAL_ID,
        response =
            PasskeyAssertionAuthenticatorResponse(
                clientDataJSON = DocExamples.CLIENT_DATA_JSON,
                authenticatorData = DocExamples.AUTHENTICATOR_DATA,
                signature = DocExamples.SIGNATURE,
                userHandle = DocExamples.ACCOUNT_ID,
            ),
    )

internal val usernameRules =
    ErrorCase(
        "VALIDATION_ERROR",
        "The username or display name breaks a rule: usernames are 3–50 characters of letters, digits and " +
            "underscores and cannot be a reserved word such as `admin` or `api`; display names are 1–100 " +
            "characters. The `message` says which rule.",
        "Username can only contain letters, numbers, and underscores",
    )

internal val ownerIdRequired =
    ErrorCase(
        "CANONICAL_OWNER_ID_REQUIRED",
        "`requestedOwnerId` is missing. The app generates this UUID once per install and it becomes the account ID; " +
            "send one, and keep sending the same one.",
        "A canonical device identity is required to create a Cloud account",
    )
internal val ownerIdInvalid =
    ErrorCase("CANONICAL_OWNER_ID_INVALID", "`requestedOwnerId` is not a UUID.", "The canonical device identity must be a UUID")
internal val ownerIdTaken =
    ErrorCase(
        "CANONICAL_OWNER_ID_TAKEN",
        "An account with this `requestedOwnerId` already exists. That usually means this device already signed up: " +
            "sign in instead.",
        "This device identity is already associated with an account",
    )

internal fun ResponsesConfig.signupRateLimited() = rateLimited(SIGNUP_RATE_LIMIT, ErrorEnvelope.API)

internal fun ResponsesConfig.signinRateLimited() = rateLimited(SIGNIN_RATE_LIMIT, ErrorEnvelope.API)

/** The `500` every Authentication and Account endpoint can answer. */
internal fun ResponsesConfig.apiServerError() = apiError(HttpStatusCode.InternalServerError, DocExamples.apiServerError)
