package studio.hypertext.atproto.sample

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DefaultIdentityResolver
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.identity.DidWebResolver
import studio.hypertext.atproto.identity.Service
import studio.hypertext.atproto.identity.VerificationMethod
import studio.hypertext.atproto.identity.WellKnownHandleResolver
import studio.hypertext.atproto.pds.ResolveHandleResponse
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.Handle
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import studio.hypertext.atproto.xrpc.query

private val json = Json { ignoreUnknownKeys = true }

fun main() {
    runBlocking {
        val did = AtprotoDid.require("did:web:example.com")
        val handle = Handle.require("alice.example.com")
        val collection = Nsid.require("studio.hypertext.logdate.entry")
        val recordKey = RecordKey.require("entry-1")
        val repoEngine = DefaultRepoEngine(InMemoryRepoBlockStore())
        val httpClient =
            HttpClient(
                MockEngine { request ->
                    when {
                        request.url.host == "example.com" && request.url.encodedPath == "/.well-known/did.json" ->
                            respond(
                                content =
                                    json.encodeToString(
                                        DidDocument(
                                            id = did,
                                            alsoKnownAs = listOf("at://$handle"),
                                            verificationMethod =
                                                listOf(
                                                    VerificationMethod(
                                                        id = "$did#atproto",
                                                        type = "Multikey",
                                                        controller = did,
                                                        publicKeyMultibase = "zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme",
                                                    ),
                                                ),
                                            service =
                                                listOf(
                                                    Service(
                                                        id = "$did#atproto_pds",
                                                        type = "AtprotoPersonalDataServer",
                                                        serviceEndpoint = "https://example.com",
                                                    ),
                                                ),
                                        ),
                                    ),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )

                        request.url.host == "alice.example.com" && request.url.encodedPath == "/.well-known/atproto-did" ->
                            respond(
                                content = did.toString(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                            )

                        request.url.host == "example.com" &&
                            request.url.encodedPath == "/xrpc/com.atproto.identity.resolveHandle" ->
                            respond(
                                content = json.encodeToString(ResolveHandleResponse(did)),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )

                        else -> error("Unexpected request URL: ${request.url}")
                    }
                },
            ) {
                install(ContentNegotiation) {
                    json(json)
                }
            }

        val identityResolver = DefaultIdentityResolver(DidWebResolver(httpClient, json), WellKnownHandleResolver(httpClient))
        val didDocument = identityResolver.resolveDocument(handle).getOrThrow()

        val xrpcClient = KtorXrpcClient(httpClient = httpClient, baseUrl = "https://example.com", json = json)
        val resolvedHandle =
            xrpcClient.query<ResolveHandleResponse>(Nsid.require("com.atproto.identity.resolveHandle")) {
                queryParameter("handle", handle.normalized)
            }

        repoEngine
            .createRecord(
                repo = did,
                collection = collection,
                value =
                    buildJsonObject {
                        put("\$type", collection.toString())
                        put("text", "Hello from a standalone ATProto consumer")
                    },
                recordKey = recordKey,
            ).getOrThrow()

        val storedRecord =
            checkNotNull(
                repoEngine
                    .getRecord(
                        RepoRecordId(
                            repo = did,
                            collection = collection,
                            recordKey = recordKey,
                        ),
                    ).getOrThrow(),
            )

        check(resolvedHandle.did == did)
        check(didDocument.id == did)
        check(
            storedRecord
                .value
                .get("text")
                ?.jsonPrimitive
                ?.content == "Hello from a standalone ATProto consumer",
        )

        println("Resolved ${handle.normalized} to ${resolvedHandle.did} and loaded ${storedRecord.uri}")
    }
}
