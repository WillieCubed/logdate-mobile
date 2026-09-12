package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.EmailBindingRequest
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.GoogleAuthRequest
import app.logdate.server.routes.LogoutRequestV1
import app.logdate.server.routes.OkResponse
import app.logdate.server.routes.PasskeyAllowCredentialDto
import app.logdate.server.routes.RefreshTokenDataV1
import app.logdate.server.routes.RefreshTokenRequestV1
import app.logdate.server.routes.RefreshTokenResponseV1
import app.logdate.server.routes.RestoreRegisterBeginResponse
import app.logdate.server.routes.RestoreRegisterCompleteRequest
import app.logdate.server.routes.SIGNIN_RATE_LIMIT
import app.logdate.server.routes.SIGNUP_RATE_LIMIT
import app.logdate.server.routes.SigninPasskeyBeginData
import app.logdate.server.routes.SigninPasskeyBeginRequest
import app.logdate.server.routes.SigninPasskeyBeginResponse
import app.logdate.server.routes.SigninPasskeyCompleteRequest
import app.logdate.server.routes.SignupPasskeyBeginData
import app.logdate.server.routes.SignupPasskeyBeginRequest
import app.logdate.server.routes.SignupPasskeyBeginResponse
import app.logdate.server.routes.SignupPasskeyCompleteRequest
import app.logdate.server.routes.SuccessResponse
import app.logdate.server.routes.apiError
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.publicOperation
import app.logdate.server.routes.rateLimited
import app.logdate.shared.model.PasskeyAssertionAuthenticatorResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import app.logdate.shared.model.UsernameAvailabilityData
import app.logdate.shared.model.UsernameAvailabilityResponse
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/** Documentation for the sign-up, sign-in, restore and token endpoints in `AuthV1Routes.kt`. */
internal object AuthDocs {
    private const val SESSION_TOKEN = "st_5f2c9e1a7b3d4c6e8f0a1b2c3d4e5f60"

    private val passkeyCredential =
        PasskeyCredentialResponse(
            id = DocExamples.CREDENTIAL_ID,
            rawId = DocExamples.CREDENTIAL_ID,
            response =
                PasskeyAuthenticatorResponse(
                    clientDataJSON = DocExamples.CLIENT_DATA_JSON,
                    attestationObject = DocExamples.ATTESTATION_OBJECT,
                ),
        )

    private val passkeyAssertion =
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

    private val usernameRules =
        ErrorCase(
            "VALIDATION_ERROR",
            "The username or display name breaks a rule: usernames are 3–50 characters of letters, digits and " +
                "underscores and cannot be a reserved word such as `admin` or `api`; display names are 1–100 " +
                "characters. The `message` says which rule.",
            "Username can only contain letters, numbers, and underscores",
        )

    private val ownerIdRequired =
        ErrorCase(
            "CANONICAL_OWNER_ID_REQUIRED",
            "`requestedOwnerId` is missing. The app generates this UUID once per install and it becomes the account ID; " +
                "send one, and keep sending the same one.",
            "A canonical device identity is required to create a Cloud account",
        )
    private val ownerIdInvalid =
        ErrorCase("CANONICAL_OWNER_ID_INVALID", "`requestedOwnerId` is not a UUID.", "The canonical device identity must be a UUID")
    private val ownerIdTaken =
        ErrorCase(
            "CANONICAL_OWNER_ID_TAKEN",
            "An account with this `requestedOwnerId` already exists. That usually means this device already signed up: " +
                "sign in instead.",
            "This device identity is already associated with an account",
        )

    private fun ResponsesConfig.signupRateLimited() =
        rateLimited(SIGNUP_RATE_LIMIT, ErrorEnvelope.API, "IP address", retryAfterHeader = false)

    private fun ResponsesConfig.signinRateLimited() =
        rateLimited(SIGNIN_RATE_LIMIT, ErrorEnvelope.API, "IP address", retryAfterHeader = false)

    private fun ResponsesConfig.serverError() = apiError(HttpStatusCode.InternalServerError, DocExamples.apiServerError)

    val checkUsernameAvailability: RouteConfig.() -> Unit = {
        publicOperation(
            "checkUsernameAvailability",
            ApiTags.AUTHENTICATION,
            "Check a username",
            """
            Tells you whether a username is free before you start sign-up, so a form can show a green tick as the
            person types. It also validates the username against the same rules sign-up applies.

            Availability is not a reservation: another person can take the name between this call and
            **Begin passkey sign-up**, which then answers `409 USERNAME_TAKEN`.
            """,
        )
        request {
            pathParameter<String>("username") {
                description = "The username to check. 3–50 characters: letters, digits and underscores. Case-sensitive."
                example("Example") { value = DocExamples.USERNAME }
            }
        }
        response {
            ok(
                "The username is valid; `available` says whether nobody has it yet.",
                UsernameAvailabilityResponse(
                    success = true,
                    data = UsernameAvailabilityData(username = DocExamples.USERNAME, available = true),
                ),
            )
            apiError(HttpStatusCode.BadRequest, usernameRules)
            serverError()
        }
    }

    val beginPasskeySignup: RouteConfig.() -> Unit = {
        publicOperation(
            "beginPasskeySignup",
            ApiTags.AUTHENTICATION,
            "Begin passkey sign-up",
            """
            Step one of two for creating an account with a passkey. You send the person's chosen username and
            display name; the server reserves nothing yet but hands back a `sessionToken` and the WebAuthn
            `registrationOptions` (a challenge, the relying party, and the user record) that the device needs to
            create a key pair.

            Pass `registrationOptions` to the platform's credential API (`navigator.credentials.create()` in a
            browser, Credential Manager on Android, AuthenticationServices on iOS). It returns a credential; send
            that together with the `sessionToken` to **Complete passkey sign-up**. The session is short-lived, so
            do both steps in one sitting.

            `requestedOwnerId` is a UUID the app generates once per install. It becomes the account's ID, which
            is what lets a device that already holds local data claim the same identity in the cloud.

            > [!NOTE]
            > Limited to {{auth.signup}} per IP address, shared with the other sign-up calls.
            """,
        )
        request {
            jsonBody(
                SignupPasskeyBeginRequest(
                    username = DocExamples.USERNAME,
                    displayName = DocExamples.DISPLAY_NAME,
                    bio = "Morning walker, occasional photographer.",
                    requestedOwnerId = DocExamples.ACCOUNT_ID,
                ),
                "Who is signing up. `bio` is optional.",
            )
        }
        response {
            ok(
                "Registration can proceed. Keep `sessionToken` for the next step and hand `registrationOptions` to the authenticator.",
                SignupPasskeyBeginResponse(
                    success = true,
                    data = SignupPasskeyBeginData(sessionToken = SESSION_TOKEN, registrationOptions = DocExamples.registrationOptions),
                ),
            )
            apiError(HttpStatusCode.BadRequest, usernameRules, ownerIdRequired, ownerIdInvalid, DocExamples.apiInvalidRequest)
            apiError(
                HttpStatusCode.Conflict,
                ErrorCase("USERNAME_TAKEN", "Somebody already has that username. Ask for another one.", "Username is already taken"),
                ownerIdTaken,
            )
            signupRateLimited()
            serverError()
        }
    }

    val completePasskeySignup: RouteConfig.() -> Unit = {
        publicOperation(
            "completePasskeySignup",
            ApiTags.AUTHENTICATION,
            "Complete passkey sign-up",
            """
            Step two: send back the credential the device created plus the `sessionToken` from **Begin passkey
            sign-up**. The server verifies the attestation against the challenge it issued, creates the account,
            stores the passkey, and returns the account together with its first tokens. The person is now signed
            in; there is no separate sign-in call to make.

            Verification happens before anything is written, so a failed attempt leaves no half-created account
            behind and you can simply start again from step one.

            Optionally include `emailBinding` with a Google ID token to attach a verified email address to the
            new account in the same step. That is how the app gets an email onto a passkey-only account.

            > [!NOTE]
            > Limited to {{auth.signup}} per IP address, shared with the other sign-up calls.
            """,
        )
        request {
            jsonBody(
                SignupPasskeyCompleteRequest(
                    sessionToken = SESSION_TOKEN,
                    credential = passkeyCredential,
                    emailBinding = EmailBindingRequest(source = "google_id_token", value = DocExamples.GOOGLE_ID_TOKEN),
                ),
                "The session token from step one and the credential the authenticator produced. `emailBinding` is optional.",
            )
        }
        response {
            code(HttpStatusCode.Created) {
                description = "The account exists and the person is signed in. Store both tokens."
                body<app.logdate.server.routes.AuthResponse> { example("Example") { value = DocExamples.authResponse } }
            }
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "PASSKEY_VERIFICATION_FAILED",
                    "The credential did not verify against the challenge: wrong origin, expired challenge, or a " +
                        "malformed response. Start again from **Begin passkey sign-up**.",
                    "Challenge mismatch",
                ),
                ErrorCase(
                    "EMAIL_BINDING_INVALID",
                    "The Google ID token in `emailBinding` could not be verified. Sign up without it, or fetch a fresh token.",
                    "Email binding could not be verified",
                ),
                DocExamples.apiInvalidRequest,
            )
            apiError(
                HttpStatusCode.Unauthorized,
                ErrorCase(
                    "INVALID_SESSION_TOKEN",
                    "The `sessionToken` is unknown, already used, or expired. Start again from **Begin passkey sign-up**.",
                    "Session token is invalid or expired",
                ),
            )
            apiError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "ACCOUNT_LINK_CONFLICT",
                    "The Google identity in `emailBinding` is already attached to another account. Sign in to that account instead.",
                    "Google identity is already linked to another account",
                ),
                ownerIdTaken,
            )
            signupRateLimited()
            apiError(
                HttpStatusCode.InternalServerError,
                ErrorCase(
                    "PASSKEY_STORE_FAILED",
                    "The account was created but the passkey could not be saved, so the server rolled the account back. Try again.",
                    "Failed to store passkey",
                ),
                DocExamples.apiServerError,
            )
            apiError(HttpStatusCode.ServiceUnavailable, DocExamples.googleNotConfigured)
        }
    }

    val signupWithGoogle: RouteConfig.() -> Unit = {
        publicOperation(
            "signupWithGoogle",
            ApiTags.AUTHENTICATION,
            "Sign up with Google",
            """
            Creates an account from a Google ID token in a single call and signs the person in. The token must
            come from one of the client IDs this deployment trusts and its email must be verified by Google.

            If an account already has this Google identity, you simply get that account back. If exactly one
            existing account has the same verified email, the Google identity is linked to it and you get that
            account. If more than one account matches, nothing is linked and you get `409 ACCOUNT_LINK_CONFLICT`.

            `username` and `displayName` are optional; when omitted the server derives them from the Google
            profile. `requestedOwnerId` is the app's per-install UUID and becomes the account ID.

            > [!NOTE]
            > Limited to {{auth.signup}} per IP address, shared with the other sign-up calls.
            """,
        )
        request {
            jsonBody(
                GoogleAuthRequest(
                    idToken = DocExamples.GOOGLE_ID_TOKEN,
                    username = DocExamples.USERNAME,
                    displayName = DocExamples.DISPLAY_NAME,
                    requestedOwnerId = DocExamples.ACCOUNT_ID,
                ),
                "A Google ID token plus the optional profile fields. `nonce` must match the nonce you put in the token request, if you used one.",
            )
        }
        response {
            ok("The account exists (created or found) and the person is signed in.", DocExamples.authResponse)
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "GOOGLE_EMAIL_UNVERIFIED",
                    "Google reports the email as unverified. Ask the person to verify it with Google, or use a passkey.",
                    "Google account email must be verified",
                ),
                ownerIdRequired,
                ownerIdInvalid,
                DocExamples.apiInvalidRequest,
            )
            apiError(
                HttpStatusCode.Unauthorized,
                ErrorCase(
                    "GOOGLE_TOKEN_INVALID",
                    "The ID token is expired, malformed, signed for another client ID, or its nonce does not match. Get a fresh token.",
                    "Google token is invalid",
                ),
            )
            apiError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "ACCOUNT_LINK_CONFLICT",
                    "More than one account carries this verified email, so the server cannot pick one to link. Sign in with a passkey.",
                    "Google account could not be linked automatically",
                ),
            )
            signupRateLimited()
            serverError()
            apiError(HttpStatusCode.ServiceUnavailable, DocExamples.googleNotConfigured)
        }
    }

    val beginPasskeySignin: RouteConfig.() -> Unit = {
        publicOperation(
            "beginPasskeySignin",
            ApiTags.AUTHENTICATION,
            "Begin passkey sign-in",
            """
            Step one of two for signing in with a passkey. The server returns a fresh `challenge` and the relying
            party ID. Hand `data` to the platform's credential API (`navigator.credentials.get()` in a browser);
            it will pick a passkey and sign the challenge.

            `username` is optional. Send it and `allowCredentials` lists that account's passkeys so the
            authenticator can go straight to the right one. Leave it out for a "discoverable" sign-in where the
            person chooses a passkey and the server works out the account from it.

            The response looks the same whether or not the username exists, so this call cannot be used to
            discover usernames.

            > [!NOTE]
            > Limited to {{auth.signin}} per IP address, shared with the other sign-in calls.
            """,
        )
        request {
            jsonBody(SigninPasskeyBeginRequest(username = DocExamples.USERNAME), "Optionally, the username of the account signing in.")
        }
        response {
            ok(
                "A challenge to sign. Keep `challenge`; you send it back with the signed credential.",
                SigninPasskeyBeginResponse(
                    success = true,
                    data =
                        SigninPasskeyBeginData(
                            challenge = DocExamples.CHALLENGE,
                            rpId = "logdate.app",
                            allowCredentials =
                                listOf(
                                    PasskeyAllowCredentialDto(id = DocExamples.CREDENTIAL_ID, transports = listOf("internal")),
                                ),
                            timeout = 300_000,
                            userVerification = "required",
                        ),
                ),
            )
            apiError(HttpStatusCode.BadRequest, DocExamples.apiInvalidRequest)
            signinRateLimited()
            serverError()
        }
    }

    val completePasskeySignin: RouteConfig.() -> Unit = {
        publicOperation(
            "completePasskeySignin",
            ApiTags.AUTHENTICATION,
            "Complete passkey sign-in",
            """
            Step two: send the signed assertion together with the `challenge` from **Begin passkey sign-in**.
            The server checks the signature against the stored public key and returns the account and a new
            pair of tokens.

            > [!NOTE]
            > Limited to {{auth.signin}} per IP address, shared with the other sign-in calls.
            """,
        )
        request {
            jsonBody(
                SigninPasskeyCompleteRequest(credential = passkeyAssertion, challenge = DocExamples.CHALLENGE),
                "The assertion the authenticator produced and the challenge it signed.",
            )
        }
        response {
            ok("Signed in. Store both tokens.", DocExamples.authResponse)
            apiError(HttpStatusCode.BadRequest, DocExamples.apiInvalidRequest)
            apiError(
                HttpStatusCode.Unauthorized,
                ErrorCase(
                    "AUTHENTICATION_FAILED",
                    "The signature did not verify, the challenge expired, or the passkey is not registered here. Start again from step one.",
                    "Failed to verify passkey",
                ),
            )
            apiError(
                HttpStatusCode.NotFound,
                ErrorCase(
                    "ACCOUNT_NOT_FOUND",
                    "The passkey verified but its account no longer exists (it was deleted). Offer sign-up.",
                    "Account not found",
                ),
            )
            signinRateLimited()
            serverError()
        }
    }

    val signinWithGoogle: RouteConfig.() -> Unit = {
        publicOperation(
            "signinWithGoogle",
            ApiTags.AUTHENTICATION,
            "Sign in with Google",
            """
            Signs an existing person in with a Google ID token. The account is found by its linked Google
            identity or, failing that, by exactly one account with the same verified email, which then gets the
            Google identity linked to it.

            If no account matches you get `404 ACCOUNT_NOT_FOUND_SIGNUP_REQUIRED`; call **Sign up with Google**
            with the same token to create one. This call never creates accounts, so a sign-in button cannot
            accidentally register someone.

            > [!NOTE]
            > Limited to {{auth.signin}} per IP address, shared with the other sign-in calls.
            """,
        )
        request {
            jsonBody(
                GoogleAuthRequest(idToken = DocExamples.GOOGLE_ID_TOKEN),
                "A Google ID token. `nonce` must match the one in the token request, if used.",
            )
        }
        response {
            ok("Signed in. Store both tokens.", DocExamples.authResponse)
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "GOOGLE_EMAIL_UNVERIFIED",
                    "Google reports the email as unverified. Ask the person to verify it with Google, or use a passkey.",
                    "Google account email must be verified",
                ),
                DocExamples.apiInvalidRequest,
            )
            apiError(
                HttpStatusCode.Unauthorized,
                ErrorCase(
                    "GOOGLE_TOKEN_INVALID",
                    "The ID token is expired, malformed, signed for another client ID, or its nonce does not match. Get a fresh token.",
                    "Google token is invalid",
                ),
            )
            apiError(
                HttpStatusCode.NotFound,
                ErrorCase(
                    "ACCOUNT_NOT_FOUND_SIGNUP_REQUIRED",
                    "No account is linked to this Google identity or its verified email. Call **Sign up with Google**.",
                    "No account found. Use Google signup first.",
                ),
            )
            signinRateLimited()
            serverError()
            apiError(HttpStatusCode.ServiceUnavailable, DocExamples.googleNotConfigured)
        }
    }

    val beginRestoreCredentialRegistration: RouteConfig.() -> Unit = {
        bearerOperation(
            "beginRestoreCredentialRegistration",
            ApiTags.AUTHENTICATION,
            "Begin restore credential setup",
            """
            A restore credential is a special passkey the app registers while a person is signed in, kept by the
            platform's credential manager, and used later to get back into the account when every other device
            is gone. This call returns the registration options for creating one; it works exactly like
            **Begin passkey sign-up** except that the account already exists and you are authenticated.

            Hand `data` to the credential API, then send the result to **Complete restore credential setup**.
            """,
        )
        response {
            ok(
                "Registration options for the restore credential.",
                RestoreRegisterBeginResponse(success = true, data = DocExamples.registrationOptions),
            )
            bearerUnauthorized(ErrorEnvelope.API)
            apiError(HttpStatusCode.NotFound, DocExamples.apiAccountNotFound)
            serverError()
        }
    }

    val completeRestoreCredentialRegistration: RouteConfig.() -> Unit = {
        bearerOperation(
            "completeRestoreCredentialRegistration",
            ApiTags.AUTHENTICATION,
            "Complete restore credential setup",
            """
            Verifies and stores the restore credential created from **Begin restore credential setup**.
            `credentialJson` is the credential object exactly as the platform returned it, serialized to a JSON
            string (the same shape as the `credential` field of **Complete passkey sign-up**).
            """,
        )
        request {
            jsonBody(
                RestoreRegisterCompleteRequest(
                    credentialJson =
                        """{"id":"${DocExamples.CREDENTIAL_ID}","rawId":"${DocExamples.CREDENTIAL_ID}","type":"public-key",""" +
                            """"response":{"clientDataJSON":"${DocExamples.CLIENT_DATA_JSON}","attestationObject":"${DocExamples.ATTESTATION_OBJECT}"}}""",
                    challenge = DocExamples.CHALLENGE,
                ),
                "The credential as a JSON string, and the challenge it was created for.",
            )
        }
        response {
            ok("The restore credential is stored.", SuccessResponse(success = true))
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "RESTORE_KEY_REGISTRATION_FAILED",
                    "The credential did not verify against the challenge. Start again from **Begin restore credential setup**.",
                    "Challenge mismatch",
                ),
                DocExamples.apiInvalidRequest,
            )
            bearerUnauthorized(ErrorEnvelope.API)
            apiError(HttpStatusCode.NotFound, DocExamples.apiAccountNotFound)
            serverError()
        }
    }

    val beginRestoreSignin: RouteConfig.() -> Unit = {
        publicOperation(
            "beginRestoreSignin",
            ApiTags.AUTHENTICATION,
            "Begin restore sign-in",
            """
            Starts signing in with a restore credential on a device that has nothing else: no tokens, no
            username. The server returns a challenge with an empty `allowCredentials` list, so the credential
            manager offers whatever restore credentials it holds for this relying party. `userVerification` is
            `discouraged` because the whole point is to work when the person cannot do anything else.

            Send the signed result to **Complete restore sign-in**.
            """,
        )
        response {
            ok(
                "A challenge to sign with the restore credential.",
                SigninPasskeyBeginResponse(
                    success = true,
                    data =
                        SigninPasskeyBeginData(
                            challenge = DocExamples.CHALLENGE,
                            rpId = "logdate.app",
                            allowCredentials = emptyList(),
                            timeout = 300_000,
                            userVerification = "discouraged",
                        ),
                ),
            )
            serverError()
        }
    }

    val completeRestoreSignin: RouteConfig.() -> Unit = {
        publicOperation(
            "completeRestoreSignin",
            ApiTags.AUTHENTICATION,
            "Complete restore sign-in",
            """
            Verifies the restore credential's signature over the challenge from **Begin restore sign-in** and,
            if it matches an account, signs the person in with fresh tokens. From here the app can register a
            normal passkey on the new device with **Begin adding a passkey**.
            """,
        )
        request {
            jsonBody(
                SigninPasskeyCompleteRequest(credential = passkeyAssertion, challenge = DocExamples.CHALLENGE),
                "The assertion produced by the restore credential and the challenge it signed.",
            )
        }
        response {
            ok("Signed in. Store both tokens.", DocExamples.authResponse)
            apiError(HttpStatusCode.BadRequest, DocExamples.apiInvalidRequest)
            apiError(
                HttpStatusCode.Unauthorized,
                ErrorCase(
                    "RESTORE_FAILED",
                    "The signature did not verify or the credential is not a registered restore credential. Start again from step one.",
                    "Restore sign-in failed",
                ),
            )
            apiError(
                HttpStatusCode.NotFound,
                ErrorCase("ACCOUNT_NOT_FOUND", "The credential verified but its account no longer exists.", "Account not found"),
            )
            serverError()
        }
    }

    val refreshAccessToken: RouteConfig.() -> Unit = {
        publicOperation(
            "refreshAccessToken",
            ApiTags.AUTHENTICATION,
            "Refresh the access token",
            """
            Trades a refresh token for a new access token. This is the one authentication call a client makes
            routinely: whenever a request answers `401`, refresh once and retry it. The refresh token itself is
            not rotated; keep using it until it expires or the person logs out.

            No `Authorization` header is needed; the refresh token in the body is the credential.
            """,
        )
        request { jsonBody(RefreshTokenRequestV1(refreshToken = DocExamples.REFRESH_TOKEN), "The refresh token from sign-in.") }
        response {
            ok(
                "A new access token. The refresh token stays the same.",
                RefreshTokenResponseV1(success = true, data = RefreshTokenDataV1(accessToken = DocExamples.ACCESS_TOKEN)),
            )
            apiError(HttpStatusCode.BadRequest, DocExamples.apiInvalidRequest)
            apiError(
                HttpStatusCode.Unauthorized,
                ErrorCase(
                    "INVALID_REFRESH_TOKEN",
                    "The refresh token is blank, malformed or expired. Sign the person in again.",
                    "Invalid or expired refresh token",
                ),
                ErrorCase(
                    "REFRESH_TOKEN_REVOKED",
                    "This refresh token was invalidated by **Log out**. Sign the person in again; do not retry.",
                    "Refresh token has been revoked",
                ),
            )
            serverError()
        }
    }

    val logout: RouteConfig.() -> Unit = {
        publicOperation(
            "logout",
            ApiTags.AUTHENTICATION,
            "Log out",
            """
            Revokes a refresh token so it can never mint another access token. Call it when the person signs
            out, then discard both tokens locally. The current access token is not revoked; it simply expires
            on its own within minutes, which is why logout takes the refresh token and not the access token.

            Logging out an already revoked token succeeds again; the call is safe to repeat.
            """,
        )
        request { jsonBody(LogoutRequestV1(refreshToken = DocExamples.REFRESH_TOKEN), "The refresh token to revoke.") }
        response {
            ok("The refresh token is revoked.", OkResponse(ok = true))
            apiError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "INVALID_REFRESH_TOKEN",
                    "`refreshToken` is blank. Send the token you want revoked.",
                    "Refresh token is required",
                ),
                DocExamples.apiInvalidRequest,
            )
            serverError()
        }
    }
}
