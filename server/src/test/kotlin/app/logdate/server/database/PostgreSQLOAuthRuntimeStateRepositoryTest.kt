package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.oauth.StoredAuthorizationCode
import app.logdate.server.oauth.StoredAuthorizationRequest
import app.logdate.server.oauth.StoredRefreshToken
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class PostgreSQLOAuthRuntimeStateRepositoryTest {
    @Test
    fun `oauth runtime repository stores and consumes state`() =
        runTest {
            withH2Database(
                OAuthAuthorizationRequestsTable,
                OAuthAuthorizationCodesTable,
                OAuthRefreshTokensTable,
            ) {
                val repository = PostgreSQLOAuthRuntimeStateRepository()
                val now = Clock.System.now()

                val request =
                    StoredAuthorizationRequest(
                        requestUri = "urn:ietf:params:oauth:request_uri:test",
                        clientId = "https://client.example/metadata.json",
                        clientName = "Client",
                        redirectUri = "https://client.example/callback",
                        scope = "atproto",
                        state = "state-1",
                        loginHint = "alice.example.com",
                        codeChallenge = "challenge-1",
                        dpopKeyThumbprint = "thumbprint-1",
                        clientAuthKeyId = "kid-1",
                        clientAuthKeyThumbprint = "auth-thumbprint-1",
                        expiresAt = now + 5.minutes,
                    )
                val code =
                    StoredAuthorizationCode(
                        code = "code-1",
                        clientId = request.clientId,
                        redirectUri = request.redirectUri,
                        subjectDid = "did:plc:alice123",
                        subjectHandle = "alice.example.com",
                        scope = "atproto",
                        codeChallenge = request.codeChallenge,
                        dpopKeyThumbprint = request.dpopKeyThumbprint,
                        clientAuthKeyId = request.clientAuthKeyId,
                        clientAuthKeyThumbprint = request.clientAuthKeyThumbprint,
                        expiresAt = now + 1.minutes,
                    )
                val refresh =
                    StoredRefreshToken(
                        token = "ref-1",
                        clientId = request.clientId,
                        subjectDid = "did:plc:alice123",
                        scope = "atproto",
                        dpopKeyThumbprint = request.dpopKeyThumbprint,
                        clientAuthKeyId = request.clientAuthKeyId,
                        clientAuthKeyThumbprint = request.clientAuthKeyThumbprint,
                        expiresAt = now + 5.minutes,
                    )

                kotlinx.coroutines.runBlocking {
                    repository.saveAuthorizationRequest(request)
                    repository.saveAuthorizationCode(code)
                    repository.saveRefreshToken(refresh)

                    val storedRequest = repository.findAuthorizationRequest(request.requestUri)
                    assertNotNull(storedRequest)
                    assertEquals(request.requestUri, storedRequest.requestUri)
                    assertEquals(request.clientId, storedRequest.clientId)
                    assertEquals(request.clientName, storedRequest.clientName)

                    val storedCode = repository.takeAuthorizationCode(code.code)
                    assertNotNull(storedCode)
                    assertEquals(code.code, storedCode.code)
                    assertEquals(code.subjectDid, storedCode.subjectDid)
                    assertNull(repository.takeAuthorizationCode(code.code))

                    val storedRefresh = repository.findRefreshToken(refresh.token)
                    assertNotNull(storedRefresh)
                    assertEquals(refresh.subjectDid, storedRefresh.subjectDid)

                    repository.revokeRefreshToken(refresh.token, now)

                    assertEquals(
                        now.toEpochMilliseconds(),
                        repository.findRefreshToken(refresh.token)?.revokedAt?.toEpochMilliseconds(),
                    )

                    repository.deleteAuthorizationRequest(request.requestUri)
                    repository.deleteRefreshToken(refresh.token)

                    assertNull(repository.findAuthorizationRequest(request.requestUri))
                    assertNull(repository.findRefreshToken(refresh.token))
                }
            }
        }
}
