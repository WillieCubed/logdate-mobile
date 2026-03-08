package app.logdate.server.identity

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AtprotoIdentityServiceTest {
    @Test
    fun `ensureIdentity provisions did handle and signing key`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "alice",
                        displayName = "Alice",
                        createdAt = Clock.System.now(),
                    ),
                )

            val ensured = service.ensureIdentity(account)

            assertEquals("alice.logdate.app", ensured.handle)
            assertTrue(ensured.did?.startsWith("did:plc:") == true)
            assertTrue(ensured.signingKeyPublic?.startsWith("z") == true)
            assertEquals(ensured.did, accountRepository.findByDid(ensured.did!!)?.did)
            assertEquals(ensured.handle, accountRepository.findByHandle(ensured.handle!!)?.handle)
        }

    @Test
    fun `findByHandle resolves provisioned handle`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "bob",
                        displayName = "Bob",
                        createdAt = Clock.System.now(),
                    ),
                )
            service.ensureIdentity(account)

            val resolved = service.findByHandle("bob.logdate.app")

            assertTrue(resolved?.did?.startsWith("did:plc:") == true)
        }

    @Test
    fun `findByDid resolves from did web handle`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "carol",
                        displayName = "Carol",
                        createdAt = Clock.System.now(),
                    ),
                )
            service.ensureIdentity(account)

            val resolved =
                service.findByDid(
                    requireNotNull(accountRepository.findByUsername("carol")?.let { service.ensureIdentity(it).did }),
                )

            assertEquals("carol.logdate.app", resolved?.handle)
        }

    @Test
    fun `web mode provisions did web identities and resolves normalized did lookups`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://logdate.app",
                            hostedAccountDidMethod = HostedAccountDidMethod.WEB,
                        ),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "web-user",
                        displayName = "Web User",
                        createdAt = Clock.System.now(),
                    ),
                )

            val ensured = service.ensureIdentity(account)
            val resolved = service.findByDid("  DID:WEB:WEB-USER.LOGDATE.APP  ")

            assertEquals("did:web:web-user.logdate.app", ensured.did)
            assertEquals("web-user.logdate.app", resolved?.handle)
        }

    @Test
    fun `documentFor builds atproto pds did document`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://pds.logdate.app"),
                )
            val ensured =
                service.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "dana",
                            displayName = "Dana",
                            createdAt = Clock.System.now(),
                        ),
                    ),
                )

            val document = service.documentFor(ensured)

            assertTrue(document.id.toString().startsWith("did:plc:"))
            assertEquals(listOf("at://dana.logdate.app"), document.alsoKnownAs)
            assertEquals("https://pds.logdate.app", document.service.single().serviceEndpoint)
            assertEquals("${document.id}#atproto", document.verificationMethod.single().id)
        }

    @Test
    fun `ensureIdentity normalizes usernames into valid handles`() =
        kotlinx.coroutines.test.runTest {
            val service =
                AtprotoIdentityService(
                    accountRepository = InMemoryAccountRepository(),
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "LogDate.App.", pdsServiceEndpoint = "https://logdate.app"),
                )
            val ensured =
                service.ensureIdentity(
                    Account(
                        id = Uuid.random(),
                        username = "eve_user",
                        displayName = "Eve",
                        createdAt = Clock.System.now(),
                    ),
                )

            assertEquals("eve-user.logdate.app", ensured.handle)
            assertEquals("did:web:eve.logdate.app", service.didForHandle("EVE.LogDate.App").toString())
            assertNotNull(ensured.signingKeyPublic)
        }

    @Test
    fun `ensureIdentity disambiguates managed handle collisions`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val first =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "same_user",
                        displayName = "Same One",
                        createdAt = Clock.System.now(),
                    ),
                )
            val second =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "same-user",
                        displayName = "Same Two",
                        createdAt = Clock.System.now(),
                    ),
                )

            val firstEnsured = service.ensureIdentity(first)
            val secondEnsured = service.ensureIdentity(second)
            val secondHandle = assertNotNull(secondEnsured.handle)

            assertEquals("same-user.logdate.app", firstEnsured.handle)
            assertTrue(secondHandle.startsWith("same-user-"))
            assertTrue(secondHandle.endsWith(".logdate.app"))
            assertTrue(secondEnsured.did?.startsWith("did:plc:") == true)
        }

    @Test
    fun `ensureIdentity reuses canonical identity and normalizes stored casing`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = signingKeyService,
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val canonical =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "ida",
                        displayName = "Ida",
                        createdAt = Clock.System.now(),
                        handle = "ida.logdate.app",
                        did = "did:web:ida.logdate.app",
                        signingKeyPublic = "zCanonicalKey",
                    ),
                )

            assertSame(canonical, service.ensureIdentity(canonical))

            val mixedCase =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "jules",
                        displayName = "Jules",
                        createdAt = Clock.System.now(),
                        handle = "Jules.LogDate.App.",
                        did = "did:web:Jules.LogDate.App",
                        signingKeyPublic = "zMixedCase",
                    ),
                )

            val normalized = service.ensureIdentity(mixedCase)

            assertEquals("jules.logdate.app", normalized.handle)
            assertEquals("did:web:jules.logdate.app", normalized.did)
            assertEquals("zMixedCase", normalized.signingKeyPublic)
        }

    @Test
    fun `ensureIdentity saves normalized canonical values and trims explicit handle collisions`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val mixedCase =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "mina",
                        displayName = "Mina",
                        createdAt = Clock.System.now(),
                        handle = "Mina.LogDate.App.",
                        did = "did:web:mina.logdate.app",
                        signingKeyPublic = "zMixed",
                    ),
                )

            val normalized = service.ensureIdentity(mixedCase)

            assertEquals("mina.logdate.app", normalized.handle)
            assertEquals("did:web:mina.logdate.app", normalized.did)

            val longHandle = "${"a".repeat(60)}.logdate.app"
            val colliding =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "collision",
                        displayName = "Collision",
                        createdAt = Clock.System.now(),
                        handle = longHandle,
                        did = null,
                        signingKeyPublic = null,
                    ),
                )
            accountRepository.save(
                Account(
                    id = Uuid.random(),
                    username = "taken",
                    displayName = "Taken",
                    createdAt = Clock.System.now(),
                    handle = longHandle,
                    did = "did:web:${longHandle.lowercase()}",
                    signingKeyPublic = "zTaken",
                ),
            )

            val resolved = service.ensureIdentity(colliding)

            val resolvedHandle = assertNotNull(resolved.handle)
            assertTrue(resolvedHandle != longHandle)
            assertTrue(resolvedHandle.contains("-${colliding.id.toString().substring(0, 8).lowercase()}"))
            assertTrue(resolvedHandle.endsWith(".logdate.app"))
        }

    @Test
    fun `identity service backfills accounts and rejects invalid handle inputs`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val saved =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "kara",
                        displayName = "Kara",
                        createdAt = Clock.System.now(),
                    ),
                )

            service.backfillMissingIdentities()

            val backfilled = accountRepository.findById(saved.id)
            assertEquals("kara.logdate.app", backfilled?.handle)
            assertTrue(backfilled?.did?.startsWith("did:plc:") == true)
            val preservedHandle =
                service.ensureIdentity(
                    accountRepository.save(
                        Account(
                            id = Uuid.random(),
                            username = "lena",
                            displayName = "Lena",
                            createdAt = Clock.System.now(),
                            handle = "lena.logdate.app",
                        ),
                    ),
                )
            assertEquals("lena.logdate.app", preservedHandle.handle)
            assertNull(service.findByHandle(" "))
            assertNull(service.findByDid(" "))
            assertNull(service.findByDid("did:plc:not-supported"))
            assertFailsWith<IllegalArgumentException> {
                service.didForHandle("invalid handle")
            }
        }

    @Test
    fun `document helpers require complete identity and expose server did document`() =
        kotlinx.coroutines.test.runTest {
            val service =
                AtprotoIdentityService(
                    accountRepository = InMemoryAccountRepository(),
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://pds.logdate.app",
                        ),
                )

            assertEquals("profile-user.logdate.app", service.defaultHandleFor("profile_user"))
            assertEquals("user.logdate.app", service.defaultHandleFor("!!!"))

            val serverDocument = service.serverDocument()
            assertEquals("did:web:logdate.app", serverDocument.id.toString())
            assertTrue(serverDocument.verificationMethod.isEmpty())
            assertEquals("https://pds.logdate.app", serverDocument.service.single().serviceEndpoint)

            assertFailsWith<IllegalArgumentException> {
                service.documentFor(
                    Account(
                        id = Uuid.random(),
                        username = "missing-did",
                        displayName = "Missing DID",
                        createdAt = Clock.System.now(),
                        handle = "missing.logdate.app",
                        signingKeyPublic = "zKey",
                    ),
                )
            }
            assertFailsWith<IllegalArgumentException> {
                service.documentFor(
                    Account(
                        id = Uuid.random(),
                        username = "missing-handle",
                        displayName = "Missing Handle",
                        createdAt = Clock.System.now(),
                        did = "did:web:missing.logdate.app",
                        signingKeyPublic = "zKey",
                    ),
                )
            }
            assertFailsWith<IllegalArgumentException> {
                service.documentFor(
                    Account(
                        id = Uuid.random(),
                        username = "missing-key",
                        displayName = "Missing Key",
                        createdAt = Clock.System.now(),
                        handle = "missing.logdate.app",
                        did = "did:web:missing.logdate.app",
                    ),
                )
            }
        }

    @Test
    fun `managed handle label reflection covers constrained lengths`() {
        val service =
            AtprotoIdentityService(
                accountRepository = InMemoryAccountRepository(),
                signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
            )
        val method =
            service::class.java
                .getDeclaredMethod("managedHandleLabel", String::class.java, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }

        assertEquals("a", method.invoke(service, "alpha", 0))
        assertEquals("user", method.invoke(service, "!!!", 5))
    }

    @Test
    fun `identity helper reflection covers did parsing fallback`() {
        val service =
            AtprotoIdentityService(
                accountRepository = InMemoryAccountRepository(),
                signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
            )
        val handleFromDid =
            service::class.java
                .getDeclaredMethod("handleFromDid", String::class.java)
                .apply { isAccessible = true }

        assertEquals("alice.logdate.app", handleFromDid.invoke(service, "did:web:alice.logdate.app"))
        assertNull(handleFromDid.invoke(service, "did:plc:alice123"))
    }
}
