package app.logdate.server.openapi

import app.logdate.server.AUTODOC_EXTENSION
import app.logdate.server.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The gates every published operation has to pass. While the reference is being rewritten,
 * operations still carrying machine-generated text are listed in [PENDING_DOCUMENTATION]; the
 * list can only shrink, and the enricher that produces that text goes away when it is empty.
 */
class OpenApiContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    private class Operation(
        val method: String,
        val path: String,
        val body: JsonObject,
    ) {
        val label get() = "$method $path"
        val operationId get() = body["operationId"]?.jsonPrimitive?.content
        val isAutoDocumented get() = body[AUTODOC_EXTENSION]?.jsonPrimitive?.content == "true"
        val responses get() = body["responses"]?.jsonObject.orEmpty()
        val tags get() = body["tags"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val hasSecurity get() = body["security"]?.jsonArray?.isNotEmpty() == true
    }

    private fun withSpec(block: (JsonObject, List<Operation>) -> Unit) =
        testApplication {
            application { module() }
            val document = json.parseToJsonElement(client.get("/openapi.json").bodyAsText()).jsonObject
            val operations =
                assertNotNull(document["paths"]?.jsonObject).flatMap { (path, item) ->
                    item.jsonObject.filterKeys { it in HTTP_METHODS }.map { (method, op) ->
                        Operation(method.uppercase(), path, op.jsonObject)
                    }
                }
            block(document, operations)
        }

    private fun List<Operation>.documented() = filterNot { it.isAutoDocumented }

    @Test
    fun `machine documented operations are exactly the pending list`() =
        withSpec { _, operations ->
            val autoDocumented = operations.filter { it.isAutoDocumented }.map { it.label }.toSet()
            assertEquals(PENDING_DOCUMENTATION, autoDocumented, "PENDING_DOCUMENTATION must match the operations the enricher touched")
        }

    @Test
    fun `every documented operation is hand written`() =
        withSpec { _, operations ->
            val ids = mutableSetOf<String>()
            operations.documented().forEach { op ->
                val summary =
                    op.body["summary"]
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                val description =
                    op.body["description"]
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                assertTrue(summary.isNotBlank() && summary.length <= 60, "${op.label}: summary must be a short title, got '$summary'")
                assertTrue(description.length >= 40, "${op.label}: description too short to help anyone: '$description'")
                assertTrue(!MACHINE_TEXT.matches(summary), "${op.label}: summary looks machine generated: '$summary'")
                assertTrue(!description.contains("this endpoint", ignoreCase = true), "${op.label}: say what it does, not 'this endpoint'")
                assertEquals(1, op.tags.size, "${op.label}: exactly one tag, got ${op.tags}")
                val id = assertNotNull(op.operationId, "${op.label}: operationId")
                assertTrue(OPERATION_ID.matches(id), "${op.label}: operationId must be camelCase, got '$id'")
                assertTrue(ids.add(id), "duplicate operationId $id")
            }
        }

    @Test
    fun `every tag is declared with a description and grouped exactly once`() =
        withSpec { document, operations ->
            val declared =
                assertNotNull(document["tags"]?.jsonArray).associate { tag ->
                    val name =
                        tag.jsonObject["name"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                    name to
                        tag.jsonObject["description"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                }
            declared.forEach { (name, description) -> assertTrue(description.length >= 80, "tag '$name' needs a real description") }
            val groups = assertNotNull(document["x-tagGroups"]?.jsonArray, "x-tagGroups missing")
            val grouped = groups.flatMap { it.jsonObject["tags"]!!.jsonArray.map { t -> t.jsonPrimitive.content } }
            assertEquals(grouped.size, grouped.toSet().size, "a tag appears in more than one group: $grouped")
            assertEquals(declared.keys, grouped.toSet(), "declared tags and grouped tags differ")
            operations.documented().forEach { op ->
                op.tags.forEach { assertTrue(it in declared, "${op.label}: tag '$it' is not declared in ApiTags") }
            }
            if (PENDING_DOCUMENTATION.isEmpty()) {
                val used = operations.flatMap { it.tags }.toSet()
                assertEquals(declared.keys, used, "every declared tag must be used by at least one operation")
            }
        }

    @Test
    fun `path templates and path parameters agree`() =
        withSpec { _, operations ->
            operations.documented().forEach { op ->
                val templated = PATH_PARAM.findAll(op.path).map { it.groupValues[1] }.toSet()
                val declared =
                    op.body["parameters"]?.jsonArray.orEmpty().map { it.jsonObject }.filter {
                        it["in"]?.jsonPrimitive?.content == "path"
                    }
                assertEquals(templated, declared.map { it["name"]!!.jsonPrimitive.content }.toSet(), "${op.label}: path parameters")
                declared.forEach { parameter ->
                    assertTrue(parameter["required"]?.jsonPrimitive?.content == "true", "${op.label}: path parameter must be required")
                    assertTrue(
                        parameter["description"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                            .length >= 20,
                        "${op.label}: describe ${parameter["name"]}",
                    )
                }
            }
        }

    @Test
    fun `every documented operation declares a success response with content`() =
        withSpec { _, operations ->
            operations.documented().forEach { op ->
                val successes = op.responses.filterKeys { it.startsWith("2") }
                assertTrue(successes.isNotEmpty(), "${op.label}: no 2xx response")
                successes.forEach { (status, response) ->
                    val body = response.jsonObject
                    assertTrue(
                        body["description"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                            .isNotBlank(),
                        "${op.label}: $status needs a description",
                    )
                    if (status != "204") assertTrue(body.containsKey("content"), "${op.label}: $status must declare a body")
                    if (status ==
                        "201"
                    ) {
                        assertTrue(
                            body["headers"]?.jsonObject?.containsKey("Location") == true,
                            "${op.label}: 201 must document Location",
                        )
                    }
                }
            }
        }

    @Test
    fun `every json body carries an example`() =
        withSpec { _, operations ->
            operations.documented().forEach { op ->
                op.body["requestBody"]?.jsonObject?.get("content")?.jsonObject?.forEach { (mediaType, media) ->
                    if (mediaType ==
                        "application/json"
                    ) {
                        assertTrue(media.jsonObject.hasExample(), "${op.label}: request body needs an example")
                    }
                }
                op.responses.filterKeys { it.startsWith("2") }.forEach { (status, response) ->
                    response.jsonObject["content"]?.jsonObject?.get("application/json")?.jsonObject?.let { media ->
                        assertTrue(media.hasExample(), "${op.label}: $status response needs an example")
                    }
                }
            }
        }

    @Test
    fun `protected operations document 401 in their family's envelope`() =
        withSpec { _, operations ->
            operations.documented().forEach { op ->
                if (op.hasSecurity) {
                    val unauthorized = assertNotNull(op.responses["401"], "${op.label}: protected but no 401 documented").jsonObject
                    val ref =
                        unauthorized["content"]
                            ?.jsonObject
                            ?.get("application/json")
                            ?.jsonObject
                            ?.get("schema")
                            ?.jsonObject
                            ?.get("\$ref")
                            ?.jsonPrimitive
                            ?.content
                    assertEquals("#/components/schemas/${envelopeFor(op.path)}", ref, "${op.label}: 401 envelope")
                } else {
                    assertTrue(op.operationId in PUBLIC_OPERATIONS, "${op.label}: no security declared but not listed as public")
                }
            }
        }

    @Test
    fun `rate limited operations document 429 and quota enforced operations document 402`() =
        withSpec { _, operations ->
            operations.documented().forEach { op ->
                if (op.operationId in RATE_LIMITED_WITH_RETRY_AFTER + RATE_LIMITED_WITHOUT_RETRY_AFTER) {
                    val limited = assertNotNull(op.responses["429"], "${op.label}: rate limited but no 429").jsonObject
                    val hasRetryAfter = limited["headers"]?.jsonObject?.containsKey("Retry-After") == true
                    assertEquals(op.operationId in RATE_LIMITED_WITH_RETRY_AFTER, hasRetryAfter, "${op.label}: Retry-After header")
                }
                if (op.operationId in QUOTA_ENFORCED) assertNotNull(op.responses["402"], "${op.label}: quota enforced but no 402")
                if (op.path.startsWith("/xrpc/")) assertNotNull(op.responses["501"], "${op.label}: XRPC methods answer 501 when disabled")
            }
        }

    @Test
    fun `info servers and external docs are complete`() =
        withSpec { document, _ ->
            val info = assertNotNull(document["info"]?.jsonObject)
            assertEquals(API_TITLE, info["title"]?.jsonPrimitive?.content)
            assertEquals(OpenApiOverview.markdown, info["description"]?.jsonPrimitive?.content)
            assertEquals(
                PRODUCT_URL,
                info["contact"]
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.content,
            )
            val servers = assertNotNull(document["servers"]?.jsonArray).map { it.jsonObject["url"]!!.jsonPrimitive.content }
            assertEquals(listOf("/", HOSTED_SERVER_URL), servers)
            assertNotNull(document["externalDocs"]?.jsonObject?.get("url"))
        }

    @Test
    fun `operational routes are not published`() =
        withSpec { document, _ ->
            val paths = assertNotNull(document["paths"]?.jsonObject)
            HIDDEN_PATHS.forEach { assertTrue(!paths.containsKey(it), "$it must not be published") }
        }

    @Test
    fun `route files carry no inline documentation`() {
        val routesDir =
            listOf(File("src/main/kotlin/app/logdate/server/routes"), File("server/src/main/kotlin/app/logdate/server/routes"))
                .first { it.isDirectory }
        routesDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val relative = file.relativeTo(routesDir).path
            if (relative.startsWith("docs/") ||
                relative == "OpenApiDocumentation.kt" ||
                relative in PENDING_INLINE_DOCS_FILES
            ) {
                return@forEach
            }
            val offending = file.readLines().withIndex().filter { (_, line) -> INLINE_DOC_LINE.containsMatchIn(line) }
            assertTrue(offending.isEmpty(), "$relative documents routes inline; move it to routes/docs: ${offending.map { it.index + 1 }}")
        }
    }

    private fun JsonObject.hasExample() = containsKey("example") || (this["examples"]?.jsonObject?.isNotEmpty() == true)

    private fun envelopeFor(path: String): String =
        when {
            path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/identity") -> "ApiErrorResponse"
            path.startsWith("/api/v1/quota") || path.startsWith("/api/v1/transcription") -> "MessageErrorResponse"
            path.startsWith("/xrpc/") -> "PdsErrorResponse"
            path.startsWith("/oauth/") -> "OAuthErrorResponse"
            else -> "SimpleErrorResponse"
        }

    companion object {
        private val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete")
        private val MACHINE_TEXT = Regex("^(Get|Post|Put|Patch|Delete) [A-Za-z.]+\\.?$")
        private val OPERATION_ID = Regex("^[a-z][A-Za-z0-9]*$")
        private val PATH_PARAM = Regex("\\{([^}]+)}")
        private val INLINE_DOC_LINE = Regex("^\\s*(summary|description) = \"|^\\s*tags = listOf\\(")
        private val HIDDEN_PATHS =
            setOf(
                "/health",
                "/docs",
                "/favicon.svg",
                "/api/v1/auth/metrics",
                "/api/v1/auth/metrics/prometheus",
                "/api/v1/ops/sync/metrics",
                "/api/v1/ops/sync/metrics/prometheus",
                "/api/v1/ops/sync/tombstones:purge",
                "/api/v1/ops/backups:purge",
            )

        /** Operations that are public by design; every other documented operation must declare security. */
        private val PUBLIC_OPERATIONS = setOf<String>()

        private val RATE_LIMITED_WITHOUT_RETRY_AFTER =
            setOf(
                "beginPasskeySignup",
                "completePasskeySignup",
                "signupWithGoogle",
                "beginPasskeySignin",
                "completePasskeySignin",
                "signinWithGoogle",
            )
        private val RATE_LIMITED_WITH_RETRY_AFTER = setOf("uploadMedia", "uploadBackup", "createTranscriptionSession")
        private val QUOTA_ENFORCED = setOf("uploadMedia", "uploadBackup")

        /** Route files that still document inline; shrinks as each family moves to routes/docs. */
        private val PENDING_INLINE_DOCS_FILES =
            setOf(
                "AuthV1Routes.kt",
                "OAuthRoutes.kt",
                "QuotaRoutes.kt",
                "ResourceRoutes.kt",
                "ServerInfoRoutes.kt",
                "TranscriptionRoutes.kt",
                "sync/SyncBackupRoutes.kt",
                "sync/SyncCollectionRoutes.kt",
                "sync/SyncMediaRoutes.kt",
                "sync/SyncStatusRoutes.kt",
            )

        /** Operations whose text is still machine generated. Entries are removed as they are written; never added. */
        private val PENDING_DOCUMENTATION =
            setOf(
                "GET /.well-known/atproto-did",
                "GET /.well-known/did.json",
                "GET /.well-known/assetlinks.json",
                "GET /.well-known/oauth-authorization-server",
                "GET /.well-known/oauth-protected-resource",
                "GET /oauth/jwks",
                "POST /oauth/par",
                "GET /oauth/authorize",
                "POST /oauth/authorize",
                "POST /oauth/token",
                "POST /oauth/revoke",
                "GET /xrpc/com.atproto.identity.resolveHandle",
                "POST /xrpc/com.atproto.server.createAccount",
                "POST /xrpc/com.atproto.server.createSession",
                "GET /xrpc/com.atproto.server.getSession",
                "POST /xrpc/com.atproto.server.refreshSession",
                "POST /xrpc/com.atproto.server.deleteSession",
                "GET /xrpc/com.atproto.server.describeServer",
                "GET /xrpc/com.atproto.repo.describeRepo",
                "GET /xrpc/com.atproto.sync.getRepo",
                "GET /xrpc/com.atproto.sync.getLatestCommit",
                "GET /xrpc/com.atproto.sync.getRepoStatus",
                "GET /xrpc/com.atproto.repo.getRecord",
                "GET /xrpc/com.atproto.repo.listRecords",
                "POST /xrpc/com.atproto.repo.createRecord",
                "POST /xrpc/com.atproto.repo.putRecord",
                "POST /xrpc/com.atproto.repo.deleteRecord",
                "POST /xrpc/com.atproto.repo.uploadBlob",
                "GET /xrpc/com.atproto.sync.getBlob",
                "GET /api/v1/server/info",
                "GET /api/v1/plans",
                "GET /api/v1/auth/signup/username/{username}/available",
                "POST /api/v1/auth/signup/passkey/begin",
                "POST /api/v1/auth/signup/passkey/complete",
                "POST /api/v1/auth/signup/google",
                "POST /api/v1/auth/signin/passkey/begin",
                "POST /api/v1/auth/signin/passkey/complete",
                "POST /api/v1/auth/signin/google",
                "POST /api/v1/auth/restore/register/begin",
                "POST /api/v1/auth/restore/register/complete",
                "POST /api/v1/auth/restore/begin",
                "POST /api/v1/auth/restore/complete",
                "POST /api/v1/auth/token/refresh",
                "POST /api/v1/auth/logout",
                "GET /api/v1/auth/me",
                "PUT /api/v1/auth/me",
                "DELETE /api/v1/auth/me",
                "GET /api/v1/auth/me/passkeys",
                "POST /api/v1/auth/me/passkeys/begin",
                "POST /api/v1/auth/me/passkeys/complete",
                "DELETE /api/v1/auth/me/passkeys/{credentialId}",
                "POST /api/v1/auth/me/email/verify/begin",
                "POST /api/v1/auth/me/email/verify/complete",
                "GET /api/v1/auth/me/entitlement",
                "GET /api/v1/auth/me/identities",
                "GET /api/v1/identity",
                "POST /api/v1/identity/signing-key/export",
                "POST /api/v1/identity/signing-key/rotate",
                "POST /api/v1/identity/signing-key/import",
                "POST /api/v1/identity/signing-key/import/recovery/prepare",
                "POST /api/v1/identity/signing-key/import/recovery/complete",
                "POST /api/v1/identity/plc/recovery-key",
                "GET /api/v1/identity/plc/operations",
                "GET /api/v1/ops/sync/status",
                "GET /api/v1/contents/{contentId}",
                "PUT /api/v1/contents/{contentId}",
                "PATCH /api/v1/contents/{contentId}",
                "DELETE /api/v1/contents/{contentId}",
                "GET /api/v1/contents",
                "GET /api/v1/journals/{journalId}",
                "PUT /api/v1/journals/{journalId}",
                "PATCH /api/v1/journals/{journalId}",
                "DELETE /api/v1/journals/{journalId}",
                "GET /api/v1/journals",
                "GET /api/v1/associations",
                "POST /api/v1/associations",
                "DELETE /api/v1/associations",
                "PUT /api/v1/associations/{journalId}/{contentId}",
                "DELETE /api/v1/associations/{journalId}/{contentId}",
                "PUT /api/v1/drafts/{draftId}",
                "DELETE /api/v1/drafts/{draftId}",
                "GET /api/v1/drafts/changes",
                "POST /api/v1/media",
                "GET /api/v1/media/{mediaId}",
                "DELETE /api/v1/media/{mediaId}",
                "GET /api/v1/media/{mediaId}/binary",
                "GET /api/v1/backups",
                "POST /api/v1/backups",
                "GET /api/v1/backups/{backupId}",
                "DELETE /api/v1/backups/{backupId}",
                "GET /api/v1/backups/{backupId}/binary",
                "GET /api/v1/quota",
                "GET /api/v1/resources/{resourceId}",
                "POST /api/v1/transcription/sessions",
            )
    }
}
