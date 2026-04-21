package app.logdate.server.routes

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountIdentity
import app.logdate.server.auth.AuthMetricsSnapshot
import app.logdate.server.auth.AuthOperationMetricsSnapshot
import app.logdate.server.auth.IdentityProvider
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.PasskeyAssertionAuthenticatorResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests the serialization and integrity of Data Transfer Objects (DTOs) for the V1 Auth API.
 *
 * This suite ensures that all models used for passkey-based signup, sign-in, account views,
 * and authentication metrics correctly serialize to and from JSON. It also validates the
 * behavior of default constructors and property accessors for these models.
 */
@OptIn(ExperimentalUuidApi::class)
class AuthV1ModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `auth route DTOs construct and serialize`() {
        val emailBinding = EmailBindingRequest(source = "google_id_token", value = "token", nonce = "nonce")
        val signupBeginReq = SignupPasskeyBeginRequest(username = "user_1", displayName = "User One", bio = "bio")
        val signupBeginData =
            SignupPasskeyBeginData(
                sessionToken = "sess-1",
                registrationOptions =
                    app.logdate.shared.model.PasskeyRegistrationOptions(
                        challenge = "challenge",
                        rpId = "logdate.app",
                        rpName = "LogDate",
                        user =
                            app.logdate.shared.model
                                .PasskeyUser("1", "u", "U"),
                    ),
            )
        val signupBeginResp = SignupPasskeyBeginResponse(success = true, data = signupBeginData)

        val signupComplete =
            SignupPasskeyCompleteRequest(
                sessionToken = "sess-1",
                credential =
                    PasskeyCredentialResponse(
                        id = "id",
                        rawId = "raw",
                        response = PasskeyAuthenticatorResponse(clientDataJSON = "cd", attestationObject = "ao"),
                    ),
                emailBinding = emailBinding,
            )

        val signinBeginReq = SigninPasskeyBeginRequest(username = "user_1")
        val signinBeginResp =
            SigninPasskeyBeginResponse(
                success = true,
                data =
                    SigninPasskeyBeginData(
                        challenge = "challenge",
                        rpId = "logdate.app",
                        allowCredentials = listOf(PasskeyAllowCredentialDto(id = "cred")),
                        timeout = 300_000,
                        userVerification = "preferred",
                    ),
            )

        val signinComplete =
            SigninPasskeyCompleteRequest(
                credential =
                    PasskeyAssertionResponse(
                        id = "id",
                        rawId = "raw",
                        response =
                            PasskeyAssertionAuthenticatorResponse(
                                clientDataJSON = "cd",
                                authenticatorData = "ad",
                                signature = "sig",
                                userHandle = "uh",
                            ),
                    ),
                challenge = "challenge",
            )

        val google = GoogleAuthRequest(idToken = "id-tok", username = "user", displayName = "User", nonce = "nonce")
        val accountView =
            AuthAccountView(
                id = "id",
                username = "user",
                displayName = "User",
                did = "did:web:user.logdate.app",
                handle = "user.logdate.app",
                bio = "bio",
                email = "u@example.com",
                emailVerified = true,
                linkedProviders = listOf("google", "passkey"),
                passkeyCredentialIds = listOf("cred"),
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
            )
        val copiedAccountView = accountView.copy(displayName = "Updated")
        assertEquals("Updated", copiedAccountView.displayName)
        assertEquals("user", copiedAccountView.username)
        val authData = AuthResponseData(account = accountView, tokens = AccountTokens("a", "r"))
        val authResp = AuthResponse(success = true, data = authData)

        val refreshReq = RefreshTokenRequestV1(refreshToken = "refresh")
        val refreshResp = RefreshTokenResponseV1(success = true, data = RefreshTokenDataV1(accessToken = "access"))
        val identities =
            IdentityListResponse(
                success = true,
                data =
                    listOf(
                        IdentityView(
                            provider = "google",
                            providerSubject = "sub",
                            email = "u@example.com",
                            emailVerified = true,
                            createdAt = "2026-01-01T00:00:00Z",
                            lastSignInAt = null,
                        ),
                    ),
            )
        val copiedIdentity = identities.data.first().copy(lastSignInAt = "2026-01-02T00:00:00Z")
        assertEquals("2026-01-02T00:00:00Z", copiedIdentity.lastSignInAt)

        val metrics =
            AuthMetricsResponse(
                success = true,
                data =
                    AuthMetricsSnapshot(
                        generatedAt = 1,
                        errorsByCode = mapOf("VALIDATION_ERROR" to 1L),
                        rateLimitedByOperation = mapOf("auth.signup.passkey.begin" to 1L),
                        operations = listOf(AuthOperationMetricsSnapshot("auth.signup.passkey.begin", 1, 0, 10)),
                    ),
            )

        val payload =
            listOf(
                json.encodeToString(EmailBindingRequest.serializer(), emailBinding),
                json.encodeToString(SignupPasskeyBeginRequest.serializer(), signupBeginReq),
                json.encodeToString(SignupPasskeyBeginResponse.serializer(), signupBeginResp),
                json.encodeToString(SignupPasskeyCompleteRequest.serializer(), signupComplete),
                json.encodeToString(SigninPasskeyBeginRequest.serializer(), signinBeginReq),
                json.encodeToString(SigninPasskeyBeginResponse.serializer(), signinBeginResp),
                json.encodeToString(SigninPasskeyCompleteRequest.serializer(), signinComplete),
                json.encodeToString(GoogleAuthRequest.serializer(), google),
                json.encodeToString(AuthResponse.serializer(), authResp),
                json.encodeToString(RefreshTokenRequestV1.serializer(), refreshReq),
                json.encodeToString(RefreshTokenResponseV1.serializer(), refreshResp),
                json.encodeToString(IdentityListResponse.serializer(), identities),
                json.encodeToString(AuthMetricsResponse.serializer(), metrics),
            ).joinToString("\n")

        assertTrue(payload.contains("google_id_token"))
        assertTrue(payload.contains("auth.signup.passkey.begin"))
        assertEquals("user_1", signupBeginReq.username)
        assertEquals("refresh", refreshReq.refreshToken)
    }

    @Test
    fun `auth DTO getters default constructors and companion serializers are callable`() {
        val emailBinding = EmailBindingRequest(source = "google_id_token", value = "token")
        val signupBegin = SignupPasskeyBeginRequest(username = "user_a", displayName = "User A")
        val registrationOptions =
            app.logdate.shared.model.PasskeyRegistrationOptions(
                challenge = "challenge",
                rpId = "logdate.app",
                rpName = "LogDate",
                user =
                    app.logdate.shared.model
                        .PasskeyUser("u1", "user_a", "User A"),
            )
        val signupBeginData = SignupPasskeyBeginData("session", registrationOptions)
        val signupBeginResp = SignupPasskeyBeginResponse(true, signupBeginData)
        val credential =
            PasskeyCredentialResponse(
                id = "cred-1",
                rawId = "cred-1",
                response = PasskeyAuthenticatorResponse(clientDataJSON = "cd", attestationObject = "ao"),
            )
        val signupComplete = SignupPasskeyCompleteRequest(sessionToken = "session", credential = credential)
        val signinBeginReq = SigninPasskeyBeginRequest()
        val signinBeginData =
            SigninPasskeyBeginData(
                challenge = "c",
                rpId = "rp",
                allowCredentials = listOf(PasskeyAllowCredentialDto(id = "allow-1")),
                timeout = 10L,
                userVerification = "required",
            )
        val signinBeginResp = SigninPasskeyBeginResponse(true, signinBeginData)
        val refreshData = RefreshTokenDataV1("access-token")
        val refreshResp = RefreshTokenResponseV1(success = true, data = refreshData)
        val googleReqWithDefaults = GoogleAuthRequest(idToken = "id-tok")
        val authAccountWithDefaults =
            AuthAccountView(
                id = "acc-2",
                username = "user_b",
                displayName = "User B",
                emailVerified = false,
                linkedProviders = emptyList(),
                passkeyCredentialIds = emptyList(),
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z",
            )
        val authResponseData =
            AuthResponseData(
                account =
                    AuthAccountView(
                        id = "acc-1",
                        username = "user_a",
                        displayName = "User A",
                        did = "did:web:user-a.logdate.app",
                        handle = "user-a.logdate.app",
                        bio = "bio",
                        email = "user@example.com",
                        emailVerified = true,
                        linkedProviders = listOf("google"),
                        passkeyCredentialIds = listOf("cred-1"),
                        createdAt = "2026-01-01T00:00:00Z",
                        updatedAt = "2026-01-01T00:00:00Z",
                    ),
                tokens = AccountTokens("access", "refresh"),
            )
        val authResponse = AuthResponse(success = true, data = authResponseData)
        val identity =
            IdentityView(
                provider = "google",
                providerSubject = "subject",
                email = "user@example.com",
                emailVerified = true,
                createdAt = "2026-01-01T00:00:00Z",
                lastSignInAt = null,
            )
        val identityList = IdentityListResponse(success = true, data = listOf(identity))
        val metricsResponse =
            AuthMetricsResponse(
                success = true,
                data = AuthMetricsSnapshot(0L, emptyMap(), emptyMap(), emptyList()),
            )

        assertEquals("token", invokeGetter(emailBinding, "getValue"))
        assertEquals("user_a", invokeGetter(signupBegin, "getUsername"))
        assertEquals("session", invokeGetter(signupBeginData, "getSessionToken"))
        assertEquals("true", invokeGetter(signupBeginResp, "getSuccess").toString())
        assertEquals("session", invokeGetter(signupComplete, "getSessionToken"))
        assertEquals("null", invokeGetter(signinBeginReq, "getUsername").toString())
        assertEquals("c", invokeGetter(signinBeginData, "getChallenge"))
        assertEquals("true", invokeGetter(signinBeginResp, "getSuccess").toString())
        assertEquals("access-token", invokeGetter(refreshData, "getAccessToken"))
        assertEquals("true", invokeGetter(refreshResp, "getSuccess").toString())
        val refreshDataObj = invokeGetter(refreshResp, "getData")
        assertNotNull(refreshDataObj)
        assertEquals("access-token", refreshDataObj.javaClass.getMethod("getAccessToken").invoke(refreshDataObj))
        assertEquals("acc-1", invokeGetter(authResponseData.account, "getId"))
        assertEquals("did:web:user-a.logdate.app", invokeGetter(authResponseData.account, "getDid"))
        assertEquals("user-a.logdate.app", invokeGetter(authResponseData.account, "getHandle"))
        assertEquals("bio", invokeGetter(authResponseData.account, "getBio"))
        assertEquals("user@example.com", invokeGetter(authResponseData.account, "getEmail"))
        assertEquals("true", invokeGetter(authResponseData.account, "getEmailVerified").toString())
        assertEquals(1, (invokeGetter(authResponseData.account, "getLinkedProviders") as List<*>).size)
        assertEquals(1, (invokeGetter(authResponseData.account, "getPasskeyCredentialIds") as List<*>).size)
        assertEquals("2026-01-01T00:00:00Z", invokeGetter(authResponseData.account, "getCreatedAt"))
        assertEquals("2026-01-01T00:00:00Z", invokeGetter(authResponseData.account, "getUpdatedAt"))
        assertEquals("id-tok", invokeGetter(googleReqWithDefaults, "getIdToken"))
        assertEquals("null", invokeGetter(googleReqWithDefaults, "getUsername").toString())
        assertEquals("acc-2", invokeGetter(authAccountWithDefaults, "getId"))
        assertEquals("subject", invokeGetter(identity, "getProviderSubject"))
        assertEquals("google", invokeGetter(identity, "getProvider"))
        assertEquals("user@example.com", invokeGetter(identity, "getEmail"))
        assertEquals("true", invokeGetter(identity, "getEmailVerified").toString())
        assertEquals("2026-01-01T00:00:00Z", invokeGetter(identity, "getCreatedAt"))
        assertEquals("true", invokeGetter(identityList, "getSuccess").toString())
        assertEquals(1, (invokeGetter(identityList, "getData") as List<*>).size)
        assertEquals("true", invokeGetter(authResponse, "getSuccess").toString())
        assertNotNull(invokeGetter(authResponse, "getData"))
        assertNotNull(invokeGetter(authResponseData, "getTokens"))
        assertEquals("true", invokeGetter(metricsResponse, "getSuccess").toString())
        assertNotNull(invokeGetter(metricsResponse, "getData"))

        val allow = PasskeyAllowCredentialDto(id = "allow-2")
        assertEquals("public-key", invokeGetter(allow, "getType"))
        assertEquals("allow-2", invokeGetter(allow, "getId"))
        assertEquals(0, (invokeGetter(allow, "getTransports") as List<*>).size)

        assertEquals("rp", invokeGetter(signinBeginData, "getRpId"))
        assertEquals(1, (invokeGetter(signinBeginData, "getAllowCredentials") as List<*>).size)
        assertEquals(10L, invokeGetter(signinBeginData, "getTimeout"))
        assertEquals("required", invokeGetter(signinBeginData, "getUserVerification"))
        assertNotNull(invokeGetter(signinBeginResp, "getData"))
        assertNotNull(invokeGetter(signupBeginResp, "getData"))
        assertNotNull(invokeGetter(signupBeginData, "getRegistrationOptions"))

        assertNotNull(AuthResponseData.Companion.serializer())
        assertNotNull(AuthAccountView.Companion.serializer())
        assertNotNull(IdentityView.Companion.serializer())
        assertNotNull(SignupPasskeyBeginData.Companion.serializer())
        assertNotNull(SigninPasskeyBeginData.Companion.serializer())
        assertNotNull(PasskeyAllowCredentialDto.Companion.serializer())
        assertNotNull(RefreshTokenDataV1.Companion.serializer())
    }

    private fun invokeGetter(
        target: Any,
        getter: String,
    ): Any? = target::class.java.getMethod(getter).invoke(target)

    @Test
    fun `identity defaults and google resolution identity getter are reachable`() {
        val identityWithDefaults =
            IdentityView(
                provider = "google",
                providerSubject = "sub-default",
                emailVerified = true,
                createdAt = "2026-01-01T00:00:00Z",
            )
        assertEquals(null, identityWithDefaults.email)
        assertEquals(null, identityWithDefaults.lastSignInAt)

        val now = Clock.System.now()
        val account =
            Account(
                id = Uuid.random(),
                username = "u",
                displayName = "U",
                email = "u@example.com",
                emailVerified = true,
                createdAt = now,
            )
        val linkedIdentity =
            AccountIdentity(
                id = Uuid.random(),
                accountId = account.id,
                provider = IdentityProvider.GOOGLE,
                providerSubject = "google-sub",
                email = "u@example.com",
                emailVerified = true,
                createdAt = now,
            )

        val resolutionClass = Class.forName("app.logdate.server.routes.GoogleResolution")
        val ctor = resolutionClass.getDeclaredConstructor(Account::class.java, AccountIdentity::class.java)
        ctor.isAccessible = true
        val resolution = ctor.newInstance(account, linkedIdentity)
        val getter = resolutionClass.getDeclaredMethod("getIdentity")
        getter.isAccessible = true
        val resolvedIdentity = getter.invoke(resolution) as AccountIdentity
        assertEquals("google-sub", resolvedIdentity.providerSubject)
    }

    @Test
    fun `in-memory auth rate limiter evicts stale entries after window passes`() {
        val limiterClass = Class.forName("app.logdate.server.routes.InMemoryAuthRateLimiter")
        val limiterCtor = limiterClass.getDeclaredConstructor()
        limiterCtor.isAccessible = true
        val limiter = limiterCtor.newInstance()

        val policyClass = Class.forName("app.logdate.server.routes.AuthRateLimitPolicy")
        val policyCtor =
            policyClass.getDeclaredConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
        policyCtor.isAccessible = true
        val policy = policyCtor.newInstance(2, 60)

        val allow =
            limiterClass.getDeclaredMethod(
                "allow",
                String::class.java,
                policyClass,
                Long::class.javaPrimitiveType,
            )
        allow.isAccessible = true

        assertTrue(allow.invoke(limiter, "ip-1", policy, 1_000L) as Boolean)
        assertTrue(allow.invoke(limiter, "ip-1", policy, 2_000L) as Boolean)
        assertFalse(allow.invoke(limiter, "ip-1", policy, 3_000L) as Boolean)
        assertTrue(allow.invoke(limiter, "ip-1", policy, 70_000L) as Boolean)
    }
}
