@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.networking.DefaultServerDiscoveryClient
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.sync.cloud.Association
import app.logdate.client.sync.cloud.AssociationDeleteItem
import app.logdate.client.sync.cloud.AssociationUploadRequest
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.ConditionalCreateResult
import app.logdate.client.sync.cloud.ConditionalCreateUnsupportedException
import app.logdate.client.sync.cloud.ContentState
import app.logdate.client.sync.cloud.ContentUploadRequest
import app.logdate.client.sync.cloud.JournalUploadRequest
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.ServerClientE2EHarness
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.ServerProtocolFeature
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The real cloud client against the real server for the strict creates a linked watch uses: a
 * repeated create changes nothing, and a record deleted elsewhere is reported as deleted, not
 * recreated.
 */
class ConditionalCreateClientServerE2ETest {
    @Test
    fun `a watch session creates each record once and never brings back a deleted one`() =
        runTest {
            withServerClientHarness {
                val origin = baseUrl.removeSuffix("/api/v1")
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                val client = LogDateCloudApiClient(config, httpClient)
                val phone = apiClient.createAccountWithSyntheticPasskey("watch_create_${Random.nextInt(100000, 999999)}").data
                val phoneToken = phone.tokens.accessToken
                val code = DeviceLinkApiClient(httpClient, config).mintLinkCode(phoneToken).getOrThrow()
                val watchToken =
                    DeviceLinkApiClient(httpClient, config)
                        .redeem(code.code, "Pixel Watch 3")
                        .getOrThrow()
                        .tokens.accessToken
                val contentId = Uuid.random().toString()

                assertIs<ConditionalCreateUnsupportedException>(
                    client.createContentIfAbsent(watchToken, content(contentId, "first")).exceptionOrNull(),
                )

                val descriptor = DefaultServerDiscoveryClient(httpClient).discoverServer(origin).getOrThrow()
                assertTrue(descriptor.hasProtocolFeature(ServerProtocolFeature.CONDITIONAL_CREATE_V1))
                config.updateServerDescriptor(descriptor.copy(serverOrigin = origin))

                val created =
                    assertIs<ConditionalCreateResult.Created>(
                        client.createContentIfAbsent(watchToken, content(contentId, "first")).getOrThrow(),
                    )
                val duplicate = client.createContentIfAbsent(watchToken, content(contentId, "replayed")).getOrThrow()
                assertEquals(ConditionalCreateResult.AlreadyExists(created.serverVersion), duplicate)
                assertEquals(ContentState.Present(created.serverVersion), client.contentState(watchToken, contentId).getOrThrow())
                assertEquals("LDSE2:first", phoneReads(phoneToken, contentId))
                assertEquals(ContentState.Absent, client.contentState(watchToken, Uuid.random().toString()).getOrThrow())

                assertEquals(HttpStatusCode.NoContent, httpClient.delete("$baseUrl/contents/$contentId") { bearer(phoneToken) }.status)

                val deleted =
                    assertIs<ConditionalCreateResult.Deleted>(
                        client.createContentIfAbsent(watchToken, content(contentId, "again")).getOrThrow(),
                    )
                assertTrue(deleted.deletedAt > Instant.fromEpochMilliseconds(0))
                assertEquals(ContentState.Deleted(deleted.deletedAt), client.contentState(watchToken, contentId).getOrThrow())
                assertEquals(HttpStatusCode.NotFound, httpClient.get("$baseUrl/contents/$contentId") { bearer(phoneToken) }.status)
            }
        }

    @Test
    fun `journals and links are created if absent too`() =
        runTest {
            withServerClientHarness {
                val origin = baseUrl.removeSuffix("/api/v1")
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                val client = LogDateCloudApiClient(config, httpClient)
                val phone = apiClient.createAccountWithSyntheticPasskey("watch_journal_${Random.nextInt(100000, 999999)}").data
                val phoneToken = phone.tokens.accessToken
                val links = DeviceLinkApiClient(httpClient, config)
                val watchToken =
                    links
                        .redeem(
                            links.mintLinkCode(phoneToken).getOrThrow().code,
                            "Pixel Watch 3",
                        ).getOrThrow()
                        .tokens.accessToken
                config.updateServerDescriptor(
                    DefaultServerDiscoveryClient(httpClient).discoverServer(origin).getOrThrow().copy(serverOrigin = origin),
                )
                val journalId = Uuid.random().toString()
                val alreadyLinked = Uuid.random().toString()
                val removed = Uuid.random().toString()
                val fresh = Uuid.random().toString()

                val created =
                    assertIs<ConditionalCreateResult.Created>(
                        client.createJournalIfAbsent(watchToken, journal(journalId, "Walks")).getOrThrow(),
                    )
                assertEquals(
                    ConditionalCreateResult.AlreadyExists(created.serverVersion),
                    client.createJournalIfAbsent(watchToken, journal(journalId, "Other")).getOrThrow(),
                )
                assertEquals(HttpStatusCode.NoContent, httpClient.delete("$baseUrl/journals/$journalId") { bearer(phoneToken) }.status)
                assertIs<ConditionalCreateResult.Deleted>(client.createJournalIfAbsent(watchToken, journal(journalId, "Back")).getOrThrow())

                val first = client.uploadAssociationsIfAbsent(watchToken, links(journalId, alreadyLinked, removed)).getOrThrow()
                assertEquals(2, first.uploadedCount)
                assertEquals(
                    HttpStatusCode.NoContent,
                    httpClient.delete("$baseUrl/associations/$journalId/$removed") { bearer(phoneToken) }.status,
                )

                val second = client.uploadAssociationsIfAbsent(watchToken, links(journalId, alreadyLinked, removed, fresh)).getOrThrow()

                assertEquals(1, second.uploadedCount)
                assertEquals(listOf(AssociationDeleteItem(journalId, alreadyLinked)), second.existing)
                assertEquals(listOf(removed), second.deleted.map { it.contentId })
            }
        }

    @Test
    fun `a rejected token keeps its status and code`() =
        runTest {
            withServerClientHarness {
                val origin = baseUrl.removeSuffix("/api/v1")
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                val client = LogDateCloudApiClient(config, httpClient)
                config.updateServerDescriptor(
                    DefaultServerDiscoveryClient(httpClient).discoverServer(origin).getOrThrow().copy(serverOrigin = origin),
                )

                val rejected = client.createContentIfAbsent("not-a-token", content(Uuid.random().toString(), "x")).exceptionOrNull()

                assertEquals(401, assertIs<CloudApiException>(rejected).statusCode)
                assertEquals("UNAUTHORIZED", rejected.errorCode)
            }
        }

    private suspend fun ServerClientE2EHarness.phoneReads(
        phoneToken: String,
        contentId: String,
    ): String {
        val response = httpClient.get("$baseUrl/contents/$contentId") { bearer(phoneToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject
            .getValue("content")
            .jsonPrimitive.content
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) = header(HttpHeaders.Authorization, "Bearer $token")

    private fun content(
        id: String,
        text: String,
    ) = ContentUploadRequest(id = id, type = "TEXT", content = "LDSE2:$text", mediaUri = null, createdAt = 1, lastUpdated = 1)

    private fun journal(
        id: String,
        title: String,
    ) = JournalUploadRequest(id = id, title = title, description = "", createdAt = 1, lastUpdated = 1)

    private fun links(
        journalId: String,
        vararg contentIds: String,
    ) = AssociationUploadRequest(contentIds.map { Association(journalId = journalId, contentId = it, createdAt = 1) })
}
