package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.EmailBindingRequest
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.GoogleAuthRequest
import app.logdate.server.routes.PasskeyAllowCredentialDto
import app.logdate.server.routes.SigninPasskeyBeginData
import app.logdate.server.routes.SigninPasskeyBeginRequest
import app.logdate.server.routes.SigninPasskeyBeginResponse
import app.logdate.server.routes.SigninPasskeyCompleteRequest
import app.logdate.server.routes.SignupPasskeyBeginData
import app.logdate.server.routes.SignupPasskeyBeginRequest
import app.logdate.server.routes.SignupPasskeyBeginResponse
import app.logdate.server.routes.SignupPasskeyCompleteRequest
import app.logdate.server.routes.apiError
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.publicOperation
import app.logdate.shared.model.UsernameAvailabilityData
import app.logdate.shared.model.UsernameAvailabilityResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/** Documentation for the username, passkey and Google sign-up and sign-in endpoints in `AuthV1Routes.kt`. */
internal object AuthDocs {
    val checkUsernameAvailability: RouteConfig.() -> Unit = {
        publicOperation(
            "checkUsernameAvailability",
            ApiTags.AUTHENTICATION,
            "Check a username",
            """
            Reports whether a username is available before sign-up starts, so a form can show availability as the
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
            apiServerError()
        }
    }

    val beginPasskeySignup: RouteConfig.() -> Unit = {
        publicOperation(
            "beginPasskeySignup",
            ApiTags.AUTHENTICATION,
            "Begin passkey sign-up",
            """
            Step one of two for creating an account with a passkey. You send the person's chosen username and
            display name; the server reserves nothing yet but returns a `sessionToken` and the WebAuthn
            `registrationOptions` (a challenge, the relying party, and the user record) that the device needs to
            create a key pair.

            Pass `registrationOptions` to the platform's credential API (`navigator.credentials.create()` in a
            browser, Credential Manager on Android, AuthenticationServices on iOS). It returns a credential; send
            that together with the `sessionToken` to **Complete passkey sign-up**. The session is short-lived, so
            complete both steps promptly.

            `requestedOwnerId` is a UUID the app generates once per install. It becomes the account's ID, which
            is what lets a device that already holds local data claim the same identity in the cloud.

            > [!NOTE]
            > Limited to {{auth.signup}} per IP address, counted separately for each sign-up call.
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
                "Registration can proceed. Keep `sessionToken` for the next step and pass `registrationOptions` to the authenticator.",
                SignupPasskeyBeginResponse(
                    success = true,
                    data = SignupPasskeyBeginData(sessionToken = SESSION_TOKEN, registrationOptions = DocExamples.registrationOptions),
                ),
            )
            apiError(HttpStatusCode.BadRequest, usernameRules, ownerIdRequired, ownerIdInvalid, DocExamples.apiInvalidRequest)
            apiError(
                HttpStatusCode.Conflict,
                ErrorCase(
                    "USERNAME_TAKEN",
                    "Another account already has that username. Choose a different one.",
                    "Username is already taken",
                ),
                ownerIdTaken,
            )
            signupRateLimited()
            apiServerError()
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
            behind and you can start again from step one.

            Optionally include `emailBinding` with a Google ID token to attach a verified email address to the
            new account in the same step. That is how the app gets an email onto a passkey-only account.

            > [!NOTE]
            > Limited to {{auth.signup}} per IP address, counted separately for each sign-up call.
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

            If an account already has this Google identity, or exactly one existing account has the same
            verified email, that account is returned (linking the Google identity to it in the second case),
            but only when its ID equals `requestedOwnerId`. Any other outcome is `409 ACCOUNT_LINK_CONFLICT`:
            the resolved account has a different ID, more than one account carries the verified email,
            `requestedOwnerId` already belongs to an unrelated account, or no unique username can be derived.

            `username` and `displayName` are optional; when omitted the server derives them from the Google
            profile. `requestedOwnerId` is the app's per-install UUID: a new account takes it as its ID, and an
            existing account must already have it.

            > [!NOTE]
            > Limited to {{auth.signup}} per IP address, counted separately for each sign-up call.
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
                    "The account this Google identity or verified email resolves to is not the one named by `requestedOwnerId`, " +
                        "more than one account carries the verified email, `requestedOwnerId` is already taken, or no unique username " +
                        "could be derived. If the account exists, call **Sign in with Google**; otherwise sign in with a passkey.",
                    "Google account could not be linked automatically",
                ),
            )
            signupRateLimited()
            apiServerError()
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
            party ID. Pass `data` to the platform's credential API (`navigator.credentials.get()` in a browser);
            it will select a passkey and sign the challenge.

            `username` is optional. Send it and `allowCredentials` lists that account's passkeys so the
            authenticator can select the right one directly. Omit it for a "discoverable" sign-in, where the
            person chooses a passkey and the server determines the account from it.

            Because `allowCredentials` is only filled in for an existing account, the response reveals whether a
            username exists. Do not expose this call as a public username checker.

            > [!NOTE]
            > Limited to {{auth.signin}} per IP address, counted separately for each sign-in call.
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
            apiServerError()
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
            > Limited to {{auth.signin}} per IP address, counted separately for each sign-in call.
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
            apiServerError()
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

            If no account matches, the response is `404 ACCOUNT_NOT_FOUND_SIGNUP_REQUIRED`; call **Sign up with Google**
            with the same token to create one. This call never creates accounts, so a sign-in button cannot
            accidentally register someone.

            > [!NOTE]
            > Limited to {{auth.signin}} per IP address, counted separately for each sign-in call.
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
                    "No account is linked to this Google identity, and either no account or more than one account has its verified " +
                        "email. If none does, call **Sign up with Google** with the same token; if several do, the server will not " +
                        "pick one, so sign in with a passkey.",
                    "No account found. Use Google signup first.",
                ),
            )
            signinRateLimited()
            apiServerError()
            apiError(HttpStatusCode.ServiceUnavailable, DocExamples.googleNotConfigured)
        }
    }
}
