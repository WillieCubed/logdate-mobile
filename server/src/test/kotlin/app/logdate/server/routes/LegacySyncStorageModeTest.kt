package app.logdate.server.routes

import app.logdate.server.auth.AccountStorageModes
import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureSyncTestApp
import app.logdate.server.routes.sync.locationHistoryRoutes
import app.logdate.server.sync.InMemoryLocationHistoryRepository
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.StorageModeErrorCodes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The per-kind sync routes serve only accounts in legacy storage. An account in its personal space,
 * or in a mode this server doesn't recognize, gets `403 SYNC_STORAGE_MODE_MISMATCH` on every one of
 * them, reads included, so a device with a stale idea of the account's mode can't split its data
 * between two stores.
 */
class LegacySyncStorageModeTest {
    private val tokenService = JwtTokenService("legacy-sync-storage-mode-secret")
    private val legacy = UUID.randomUUID()
    private val space = UUID.randomUUID()
    private val unknown = UUID.randomUUID()
    private val modes =
        AccountStorageModes { id ->
            mapOf(
                legacy to AccountStorageMode.LEGACY,
                space to AccountStorageMode.PERSONAL_SPACE_V1,
                unknown to AccountStorageMode.UNRECOGNIZED,
            )[id]
        }

    @Test
    fun `accounts outside legacy storage are refused on every per-kind sync route`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService, storageModes = modes)

            listOf(space, unknown).forEach { account ->
                everyLegacyRoute(account).forEach { (route, response) ->
                    assertEquals(HttpStatusCode.Forbidden, response.status, "$route for $account")
                    assertEquals(StorageModeErrorCodes.SYNC_STORAGE_MODE_MISMATCH, response.code(), "$route for $account")
                }
            }
        }

    @Test
    fun `a legacy account syncs as before`() =
        testApplication {
            configureSyncTestApp(tokenService = tokenService, storageModes = modes)

            everyLegacyRoute(legacy).forEach { (route, response) ->
                assertNotEquals(StorageModeErrorCodes.SYNC_STORAGE_MODE_MISMATCH, response.code(), route)
                assertNotEquals(HttpStatusCode.Forbidden, response.status, route)
            }
        }

    @Test
    fun `location history serves only legacy accounts`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { route("/api/v1") { locationHistoryRoutes(tokenService, InMemoryLocationHistoryRepository(), modes) } }
            }

            val refused = client.get("/api/v1/location-history") { bearer(space) }
            assertEquals(HttpStatusCode.Forbidden, refused.status)
            assertEquals(StorageModeErrorCodes.SYNC_STORAGE_MODE_MISMATCH, refused.code())
            assertEquals(HttpStatusCode.OK, client.get("/api/v1/location-history") { bearer(legacy) }.status)
        }

    private suspend fun ApplicationTestBuilder.everyLegacyRoute(account: UUID): Map<String, HttpResponse> =
        linkedMapOf(
            "GET /contents" to client.get("/api/v1/contents") { bearer(account) },
            "PUT /contents/{id}" to client.put("/api/v1/contents/c1") { bearer(account).json("{}") },
            "PATCH /contents/{id}" to client.patch("/api/v1/contents/c1") { bearer(account).json("{}") },
            "DELETE /contents/{id}" to client.delete("/api/v1/contents/c1") { bearer(account) },
            "GET /journals" to client.get("/api/v1/journals") { bearer(account) },
            "PUT /journals/{id}" to client.put("/api/v1/journals/j1") { bearer(account).json("{}") },
            "POST /journals/{id}/merge" to client.post("/api/v1/journals/j1/merge") { bearer(account).json("{}") },
            "GET /associations" to client.get("/api/v1/associations") { bearer(account) },
            "POST /associations" to client.post("/api/v1/associations") { bearer(account).json("{}") },
            "GET /drafts/changes" to client.get("/api/v1/drafts/changes") { bearer(account) },
            "PUT /drafts/{id}" to client.put("/api/v1/drafts/d1") { bearer(account).json("{}") },
            "GET /moments" to client.get("/api/v1/moments") { bearer(account) },
            "PUT /moments/{id}" to client.put("/api/v1/moments/m1") { bearer(account).json("{}") },
            "POST /media" to client.post("/api/v1/media") { bearer(account) },
            "GET /media/{id}" to client.get("/api/v1/media/m1") { bearer(account) },
            "DELETE /media/{id}" to client.delete("/api/v1/media/m1") { bearer(account) },
        )

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(account: UUID): io.ktor.client.request.HttpRequestBuilder {
        header(HttpHeaders.Authorization, "Bearer ${tokenService.generateAccessToken(account.toString())}")
        return this
    }

    private fun io.ktor.client.request.HttpRequestBuilder.json(body: String) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.code(): String? =
        runCatching {
            Json
                .parseToJsonElement(bodyAsText())
                .jsonObject["code"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()
}
