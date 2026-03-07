package app.logdate.atproto.identity

import app.logdate.atproto.syntax.Handle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Resolves hostname-level `did:web` identities.
 *
 * @property httpClient HTTP client used for document fetches.
 * @property json JSON instance used for DID document decoding.
 */
public class DidWebResolver(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DidResolver {
    /**
     * Returns the DID document URL for [did].
     */
    public fun urlFor(did: AtprotoDid): String = did.didWebDocumentUrl()

    override suspend fun resolve(did: AtprotoDid): Result<DidDocument> =
        runCatching {
            val url: String = urlFor(did)
            val response = httpClient.get(url)
            if (response.status != HttpStatusCode.OK) {
                throw DidResolutionException(did.toString(), "HTTP ${response.status.value}")
            }

            val document: DidDocument =
                try {
                    json.decodeFromString(response.bodyAsText())
                } catch (exception: SerializationException) {
                    throw DidResolutionException(did.toString(), "Invalid DID document", exception)
                }

            if (document.id != did) {
                throw DidDocumentMismatchException(did.toString(), document.id.toString())
            }

            document
        }
}

/**
 * Resolves `did:plc` identities via `plc.directory`.
 *
 * @property httpClient HTTP client used for document fetches.
 * @property json JSON instance used for DID document decoding.
 */
public class DidPlcResolver(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DidResolver {
    /**
     * Returns the PLC directory URL for [did].
     */
    public fun urlFor(did: AtprotoDid): String = did.didPlcDocumentUrl()

    override suspend fun resolve(did: AtprotoDid): Result<DidDocument> =
        runCatching {
            val url: String = urlFor(did)
            val response = httpClient.get(url)
            if (response.status != HttpStatusCode.OK) {
                throw DidResolutionException(did.toString(), "HTTP ${response.status.value}")
            }

            val document: DidDocument =
                try {
                    json.decodeFromString(response.bodyAsText())
                } catch (exception: SerializationException) {
                    throw DidResolutionException(did.toString(), "Invalid DID document", exception)
                }

            if (document.id != did) {
                throw DidDocumentMismatchException(did.toString(), document.id.toString())
            }

            document
        }
}

/**
 * Resolves handles using the `/.well-known/atproto-did` endpoint.
 *
 * @property httpClient HTTP client used for handle fetches.
 */
public class WellKnownHandleResolver(
    private val httpClient: HttpClient,
) : HandleResolver {
    override suspend fun resolve(handle: Handle): Result<AtprotoDid> =
        runCatching {
            val response = httpClient.get("https://${handle.normalized}/.well-known/atproto-did")
            if (response.status != HttpStatusCode.OK) {
                throw HandleResolutionException(handle.normalized, "HTTP ${response.status.value}")
            }

            val body: String = response.bodyAsText().trim()
            AtprotoDid.require(body)
        }
}

/**
 * Default [IdentityResolver] implementation that composes a [DidResolver] and [HandleResolver].
 *
 * @property didResolver DID document resolver.
 * @property handleResolver Handle-to-DID resolver.
 */
public class DefaultIdentityResolver(
    private val didResolver: DidResolver,
    private val handleResolver: HandleResolver,
) : IdentityResolver {
    override suspend fun resolveDid(handle: Handle): Result<AtprotoDid> = handleResolver.resolve(handle)

    override suspend fun resolveDocument(did: AtprotoDid): Result<DidDocument> = didResolver.resolve(did)

    override suspend fun resolveDocument(handle: Handle): Result<DidDocument> =
        handleResolver.resolve(handle).fold(
            onSuccess = { did -> didResolver.resolve(did) },
            onFailure = { Result.failure(it) },
        )
}
