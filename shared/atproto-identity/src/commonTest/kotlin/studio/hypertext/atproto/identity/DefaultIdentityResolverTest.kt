package studio.hypertext.atproto.identity

import kotlinx.coroutines.test.runTest
import studio.hypertext.atproto.syntax.Handle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultIdentityResolverTest {
    @Test
    fun resolvesHandleThenDocument(): Unit =
        runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val document = DidDocument(id = did)
            val identityResolver =
                DefaultIdentityResolver(
                    didResolver =
                        object : DidResolver {
                            override suspend fun resolve(did: AtprotoDid): Result<DidDocument> = Result.success(document)
                        },
                    handleResolver =
                        object : HandleResolver {
                            override suspend fun resolve(handle: Handle): Result<AtprotoDid> = Result.success(did)
                        },
                )

            val result = identityResolver.resolveDocument(Handle.require("example.com"))

            assertTrue(result.isSuccess)
            assertEquals(document, result.getOrThrow())
        }

    @Test
    fun resolvesDidDirectly(): Unit =
        runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val document = DidDocument(id = did)
            val identityResolver =
                DefaultIdentityResolver(
                    didResolver =
                        object : DidResolver {
                            override suspend fun resolve(did: AtprotoDid): Result<DidDocument> = Result.success(document)
                        },
                    handleResolver =
                        object : HandleResolver {
                            override suspend fun resolve(handle: Handle): Result<AtprotoDid> = error("resolve(handle) should not be called")
                        },
                )

            val result = identityResolver.resolveDocument(did)

            assertTrue(result.isSuccess)
            assertEquals(document, result.getOrThrow())
        }

    @Test
    fun resolvesDidFromHandle(): Unit =
        runTest {
            val did = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val identityResolver =
                DefaultIdentityResolver(
                    didResolver =
                        object : DidResolver {
                            override suspend fun resolve(did: AtprotoDid): Result<DidDocument> = error("resolve(did) should not be called")
                        },
                    handleResolver =
                        object : HandleResolver {
                            override suspend fun resolve(handle: Handle): Result<AtprotoDid> = Result.success(did)
                        },
                )

            val result = identityResolver.resolveDid(Handle.require("example.com"))

            assertTrue(result.isSuccess)
            assertEquals(did, result.getOrThrow())
        }

    @Test
    fun propagatesHandleResolutionFailure(): Unit =
        runTest {
            val exception = HandleResolutionException("example.com", "HTTP 404")
            val identityResolver =
                DefaultIdentityResolver(
                    didResolver =
                        object : DidResolver {
                            override suspend fun resolve(did: AtprotoDid): Result<DidDocument> = error("resolve(did) should not be called")
                        },
                    handleResolver =
                        object : HandleResolver {
                            override suspend fun resolve(handle: Handle): Result<AtprotoDid> = Result.failure(exception)
                        },
                )

            val didResult = identityResolver.resolveDid(Handle.require("example.com"))
            val documentResult = identityResolver.resolveDocument(Handle.require("example.com"))

            assertTrue(didResult.isFailure)
            assertSame(exception, didResult.exceptionOrNull())
            assertTrue(documentResult.isFailure)
            assertSame(exception, documentResult.exceptionOrNull())
        }
}
