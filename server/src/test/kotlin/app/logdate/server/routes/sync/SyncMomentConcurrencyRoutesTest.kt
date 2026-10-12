package app.logdate.server.routes.sync

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureSyncTestApp
import app.logdate.server.logdate.LogDateMomentsRepository
import app.logdate.server.routes.support.authHeader
import app.logdate.server.routes.support.jsonBody
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real routes/repo; a barrier reproduces any route that checks a version outside the mutation boundary. */
class SyncMomentConcurrencyRoutesTest {
    @Test fun concurrentKnownVersionPatchesMustHaveOneConflict() =
        testApplication {
            val tokens = JwtTokenService("isolated-moment-race-probe")
            val arrived = AtomicInteger()
            val bothRead = CompletableDeferred<Unit>()
            var synchronize = false
            configureSyncTestApp(tokenService = tokens, momentDecorator = { actual ->
                object : LogDateMomentsRepository by actual {
                    override suspend fun getMoment(
                        userId: UUID,
                        id: String,
                    ): app.logdate.server.logdate.LogDateMoment? {
                        val captured = actual.getMoment(userId, id)
                        if (synchronize && captured != null) {
                            if (arrived.incrementAndGet() == 2) bothRead.complete(Unit)
                            withTimeout(10_000) { bothRead.await() }
                        }
                        return captured
                    }
                }
            })
            val auth = authHeader(tokens)
            val initial =
                client.put("/api/v1/moments/race-mark") {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"id":"race-mark","capturedAt":100,"lastUpdated":100,""" +
                            """"deviceId":"watch","payload":"LDSE2:initial","schema":1}""",
                    )
                }
            assertEquals(201, initial.status.value)
            val version =
                initial
                    .jsonBody()
                    .getValue("serverVersion")
                    .jsonPrimitive.long
            synchronize = true
            val statuses =
                coroutineScope {
                    listOf("phone-a", "phone-b")
                        .map { device ->
                            async {
                                client
                                    .patch("/api/v1/moments/race-mark") {
                                        header(HttpHeaders.Authorization, auth)
                                        contentType(ContentType.Application.Json)
                                        setBody(
                                            """{"lastUpdated":200,"deviceId":"$device","payload":"LDSE2:$device","schema":1,""" +
                                                """"versionConstraint":{"type":"known","serverVersion":$version}}""",
                                        )
                                    }.status.value
                            }
                        }.awaitAll()
                        .sorted()
                }
            assertEquals(listOf(200, 409), statuses, "Two requests read the same real stored version; only one may commit")
        }
}
