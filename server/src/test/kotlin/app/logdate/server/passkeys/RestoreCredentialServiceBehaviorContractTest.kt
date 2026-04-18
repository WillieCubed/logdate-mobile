package app.logdate.server.passkeys

import app.logdate.shared.model.AuthenticatorAssertionResponse
import app.logdate.shared.model.AuthenticatorAttestationResponse
import app.logdate.shared.model.PasskeyAuthenticationResponse
import app.logdate.shared.model.PasskeyChallenge
import app.logdate.shared.model.PasskeyRegistrationResponse
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.RegistrationData
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.AttestationObject
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RestoreCredentialServiceBehaviorContractTest {
    // -----------------------------------------------------------------------
    // Simplified mode — registration
    // -----------------------------------------------------------------------

    @Test
    fun `simplified registration succeeds and stores credential`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.getCredentialIdsForUser(any()) } returns emptyList()
        coEvery { repository.deactivateAllForUser(any()) } returns true
        coEvery { repository.store(any(), any(), any(), any()) } returns true

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)
        val userId = Uuid.random()
        val options = service.generateRegistrationOptions(userId, "alice", "Alice")

        val result =
            service.verifyRegistration(
                userId,
                options.challenge,
                PasskeyRegistrationResponse(
                    id = "cred-abc",
                    rawId = "cred-abc",
                    response = AuthenticatorAttestationResponse(clientDataJSON = "client", attestationObject = "att"),
                ),
            )

        assertTrue(result.success)
        assertNotNull(result.credentialId)
        assertNull(result.error)
    }

    @Test
    fun `simplified registration deactivates previous credentials before storing new one`() {
        val repository = mockk<RestoreCredentialRepository>()
        val userId = Uuid.random()
        coEvery { repository.getCredentialIdsForUser(userId) } returns listOf("old-cred")
        coEvery { repository.deactivateAllForUser(userId) } returns true
        coEvery { repository.store(any(), any(), any(), any()) } returns true

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)
        val options = service.generateRegistrationOptions(userId, "alice", "Alice")

        val result =
            service.verifyRegistration(
                userId,
                options.challenge,
                PasskeyRegistrationResponse(
                    id = "new-cred",
                    rawId = "new-cred",
                    response = AuthenticatorAttestationResponse(clientDataJSON = "cd", attestationObject = "ao"),
                ),
            )

        assertTrue(result.success)
        coVerify(exactly = 1) { repository.deactivateAllForUser(userId) }
        coVerify(exactly = 1) { repository.store(userId, any(), any(), any()) }
    }

    @Test
    fun `simplified registration rejects when storage fails`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.getCredentialIdsForUser(any()) } returns emptyList()
        coEvery { repository.deactivateAllForUser(any()) } returns true
        coEvery { repository.store(any(), any(), any(), any()) } throws IllegalStateException("store failed")

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)
        val userId = Uuid.random()
        val options = service.generateRegistrationOptions(userId, "alice", "Alice")

        val result =
            service.verifyRegistration(
                userId,
                options.challenge,
                PasskeyRegistrationResponse(
                    id = "cred",
                    rawId = "cred",
                    response = AuthenticatorAttestationResponse(clientDataJSON = "cd", attestationObject = "ao"),
                ),
            )

        assertFalse(result.success)
        assertContains(result.error.orEmpty(), "store failed")
    }

    // -----------------------------------------------------------------------
    // Simplified mode — authentication
    // -----------------------------------------------------------------------

    @Test
    fun `simplified authentication returns userId and deactivates credential`() {
        val userId = Uuid.random()
        val repository = mockk<RestoreCredentialRepository>()
        val stored =
            StoredRestoreCredentialData(
                credentialId = "cred-xyz",
                publicKey = byteArrayOf(1, 2, 3),
                signCount = 0L,
                userId = userId,
            )
        coEvery { repository.findByCredentialId("cred-xyz") } returns stored
        coEvery { repository.deactivate("cred-xyz") } returns true

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)
        val options = service.generateAuthOptions()

        val result =
            service.verifyAuthentication(
                options.challenge,
                PasskeyAuthenticationResponse(
                    id = "cred-xyz",
                    rawId = "cred-xyz",
                    response =
                        AuthenticatorAssertionResponse(
                            clientDataJSON = "cd",
                            authenticatorData = "ad",
                            signature = "sig",
                            userHandle = null,
                        ),
                ),
            )

        assertTrue(result.success)
        assertEquals(userId, result.userId)
        assertEquals("cred-xyz", result.credentialId)
        coVerify(exactly = 1) { repository.deactivate("cred-xyz") }
    }

    @Test
    fun `simplified authentication fails when credential not found`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.findByCredentialId(any()) } returns null

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)
        val options = service.generateAuthOptions()

        val result =
            service.verifyAuthentication(
                options.challenge,
                PasskeyAuthenticationResponse(
                    id = "unknown-cred",
                    rawId = "unknown-cred",
                    response = AuthenticatorAssertionResponse("cd", "ad", "sig", null),
                ),
            )

        assertFalse(result.success)
        assertContains(result.error.orEmpty(), "Restore credential not found")
    }

    @Test
    fun `simplified authentication prevents replay — deactivated credential is not found again`() {
        val userId = Uuid.random()
        val repository = InMemoryRestoreCredentialRepository()
        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)

        val regOptions = service.generateRegistrationOptions(userId, "alice", "Alice")
        service.verifyRegistration(
            userId,
            regOptions.challenge,
            PasskeyRegistrationResponse(
                id = encodeBase64Url(byteArrayOf(1, 2, 3)),
                rawId = encodeBase64Url(byteArrayOf(1, 2, 3)),
                response = AuthenticatorAttestationResponse("cd", "ao"),
            ),
        )

        val credentialId = encodeBase64Url(byteArrayOf(1, 2, 3))
        val authOptions1 = service.generateAuthOptions()
        val firstAttempt =
            service.verifyAuthentication(
                authOptions1.challenge,
                PasskeyAuthenticationResponse(
                    id = credentialId,
                    rawId = credentialId,
                    response = AuthenticatorAssertionResponse("cd", "ad", "sig", null),
                ),
            )
        assertTrue(firstAttempt.success)

        val authOptions2 = service.generateAuthOptions()
        val replayAttempt =
            service.verifyAuthentication(
                authOptions2.challenge,
                PasskeyAuthenticationResponse(
                    id = credentialId,
                    rawId = credentialId,
                    response = AuthenticatorAssertionResponse("cd", "ad", "sig", null),
                ),
            )
        assertFalse(replayAttempt.success)
        assertContains(replayAttempt.error.orEmpty(), "Restore credential not found")
    }

    // -----------------------------------------------------------------------
    // Challenge validation
    // -----------------------------------------------------------------------

    @Test
    fun `challenge validation rejects used wrong-type wrong-user and expired challenges`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.getCredentialIdsForUser(any()) } returns emptyList()
        coEvery { repository.deactivateAllForUser(any()) } returns true
        coEvery { repository.store(any(), any(), any(), any()) } returns true

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)
        val userId = Uuid.random()
        val registration =
            PasskeyRegistrationResponse(
                id = "cred",
                rawId = "cred",
                response = AuthenticatorAttestationResponse("cd", "ao"),
            )

        // Used challenge
        putChallenge(service, "used-reg", challenge(userId, "restore-registration", isUsed = true))
        assertFalse(service.verifyRegistration(userId, "used-reg", registration).success)

        // Wrong type for registration (authentication type supplied)
        putChallenge(service, "wrong-type-reg", challenge(userId, "restore-authentication"))
        assertFalse(service.verifyRegistration(userId, "wrong-type-reg", registration).success)

        // Wrong user
        putChallenge(service, "wrong-user-reg", challenge(Uuid.random(), "restore-registration"))
        assertFalse(service.verifyRegistration(userId, "wrong-user-reg", registration).success)

        // Expired
        putChallenge(
            service,
            "expired-reg",
            PasskeyChallenge(
                challenge = "expired-reg",
                userId = userId,
                type = "restore-registration",
                expiresAt = (Clock.System.now() - 1.minutes).toString(),
                isUsed = false,
            ),
        )
        assertFalse(service.verifyRegistration(userId, "expired-reg", registration).success)

        // Missing entirely
        assertFalse(service.verifyRegistration(userId, "no-such-challenge", registration).success)
    }

    @Test
    fun `authentication challenge validation rejects used wrong-type and expired challenges`() {
        val repository = mockk<RestoreCredentialRepository>()
        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = false)
        val authResponse =
            PasskeyAuthenticationResponse(
                id = "cred",
                rawId = "cred",
                response = AuthenticatorAssertionResponse("cd", "ad", "sig", null),
            )

        // Used challenge
        putChallenge(service, "used-auth", challenge(Uuid.random(), "restore-authentication", isUsed = true))
        assertFalse(service.verifyAuthentication("used-auth", authResponse).success)

        // Wrong type (registration type for authentication)
        putChallenge(service, "wrong-type-auth", challenge(Uuid.random(), "restore-registration"))
        assertFalse(service.verifyAuthentication("wrong-type-auth", authResponse).success)

        // Expired
        putChallenge(
            service,
            "expired-auth",
            PasskeyChallenge(
                challenge = "expired-auth",
                userId = Uuid.random(),
                type = "restore-authentication",
                expiresAt = (Clock.System.now() - 1.minutes).toString(),
                isUsed = false,
            ),
        )
        assertFalse(service.verifyAuthentication("expired-auth", authResponse).success)

        // Missing entirely
        assertFalse(service.verifyAuthentication("no-such-challenge", authResponse).success)
    }

    // -----------------------------------------------------------------------
    // Strict mode — registration
    // -----------------------------------------------------------------------

    @Test
    fun `strict registration validates base64url encoding of fields`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.getCredentialIdsForUser(any()) } returns emptyList()
        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = true)
        val userId = Uuid.random()

        // Challenge missing entirely
        val missingChallenge =
            service.verifyRegistration(
                userId,
                "missing-challenge",
                PasskeyRegistrationResponse("id", "id", AuthenticatorAttestationResponse("x", "y")),
            )
        assertFalse(missingChallenge.success)

        // Challenge present but not valid base64url
        putChallenge(service, "not*base64", challenge(userId, "restore-registration"))
        val invalidChallenge =
            service.verifyRegistration(
                userId,
                "not*base64",
                PasskeyRegistrationResponse("id", "id", AuthenticatorAttestationResponse("x", "y")),
            )
        assertFalse(invalidChallenge.success)
        assertContains(invalidChallenge.error.orEmpty(), "Challenge is not valid base64url")

        // Valid challenge, invalid attestation object
        val opts = service.generateRegistrationOptions(userId, "u", "U")
        val invalidAtt =
            service.verifyRegistration(
                userId,
                opts.challenge,
                PasskeyRegistrationResponse(
                    id = "id",
                    rawId = "id",
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON = encodeBase64Url("cd".encodeToByteArray()),
                            attestationObject = "*invalid*",
                        ),
                ),
            )
        assertFalse(invalidAtt.success)
        assertContains(invalidAtt.error.orEmpty(), "Attestation object is not valid base64url")

        // Valid challenge, invalid clientDataJSON
        val opts2 = service.generateRegistrationOptions(userId, "u2", "U2")
        val invalidCd =
            service.verifyRegistration(
                userId,
                opts2.challenge,
                PasskeyRegistrationResponse(
                    id = "id",
                    rawId = "id",
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON = "*invalid*",
                            attestationObject = encodeBase64Url("ao".encodeToByteArray()),
                        ),
                ),
            )
        assertFalse(invalidCd.success)
        assertContains(invalidCd.error.orEmpty(), "Client data is not valid base64url")
    }

    @Test
    fun `strict registration handles missing attested credential data and conversion errors`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.getCredentialIdsForUser(any()) } returns emptyList()
        coEvery { repository.deactivateAllForUser(any()) } returns true
        coEvery { repository.store(any(), any(), any(), any()) } returns true

        val manager = mockk<WebAuthnManager>()
        val converter = mockk<AttestedCredentialDataConverter>()
        val registrationData = mockk<RegistrationData>()
        val attestationObject = mockk<AttestationObject>()
        val authenticatorData = mockk<AuthenticatorData<RegistrationExtensionAuthenticatorOutput>>()
        val attestedCredentialData = mockk<AttestedCredentialData>()

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = true)
        setPrivateField(service, "webAuthnManager", manager)
        setPrivateField(service, "attestedCredentialDataConverter", converter)

        val userId = Uuid.random()
        val response =
            PasskeyRegistrationResponse(
                id = encodeBase64Url(byteArrayOf(1, 2, 3)),
                rawId = encodeBase64Url(byteArrayOf(1, 2, 3)),
                response =
                    AuthenticatorAttestationResponse(
                        clientDataJSON = encodeBase64Url("cd".encodeToByteArray()),
                        attestationObject = encodeBase64Url("ao".encodeToByteArray()),
                    ),
            )

        // Missing attestation object
        every { manager.verify(any<RegistrationRequest>(), any<RegistrationParameters>()) } returns registrationData
        every { registrationData.attestationObject } returns null
        val opts1 = service.generateRegistrationOptions(userId, "u", "U")
        val noAttestation = service.verifyRegistration(userId, opts1.challenge, response)
        assertFalse(noAttestation.success)
        assertContains(noAttestation.error.orEmpty(), "Attested credential data is missing")

        // Missing attested credential data
        every { registrationData.attestationObject } returns attestationObject
        every { attestationObject.authenticatorData } returns authenticatorData
        every { authenticatorData.attestedCredentialData } returns null
        val opts2 = service.generateRegistrationOptions(userId, "u2", "U2")
        val noAttestedData = service.verifyRegistration(userId, opts2.challenge, response)
        assertFalse(noAttestedData.success)
        assertContains(noAttestedData.error.orEmpty(), "Attested credential data is missing")

        // Data conversion exception
        val opts3 = service.generateRegistrationOptions(userId, "u3", "U3")
        every { manager.verify(any<RegistrationRequest>(), any<RegistrationParameters>()) } throws DataConversionException("dc")
        val conversionError = service.verifyRegistration(userId, opts3.challenge, response)
        assertFalse(conversionError.success)
        assertContains(conversionError.error.orEmpty(), "Registration data conversion failed")
    }

    @Test
    fun `strict registration stores credential and deactivates previous on success`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.getCredentialIdsForUser(any()) } returns listOf("old-cred")
        coEvery { repository.deactivateAllForUser(any()) } returns true
        coEvery { repository.store(any(), any(), any(), any()) } returns true

        val manager = mockk<WebAuthnManager>()
        val converter = mockk<AttestedCredentialDataConverter>()
        val registrationData = mockk<RegistrationData>()
        val attestationObject = mockk<AttestationObject>()
        val authenticatorData = mockk<AuthenticatorData<RegistrationExtensionAuthenticatorOutput>>()
        val attestedCredentialData = mockk<AttestedCredentialData>()
        val credentialBytes = byteArrayOf(9, 8, 7, 6)
        val canonicalId = encodeBase64Url(credentialBytes)

        every { manager.verify(any<RegistrationRequest>(), any<RegistrationParameters>()) } returns registrationData
        every { registrationData.attestationObject } returns attestationObject
        every { attestationObject.authenticatorData } returns authenticatorData
        every { authenticatorData.attestedCredentialData } returns attestedCredentialData
        every { authenticatorData.signCount } returns 0L
        every { attestedCredentialData.credentialId } returns credentialBytes
        every { converter.convert(attestedCredentialData) } returns byteArrayOf(0xFF.toByte())

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = true)
        setPrivateField(service, "webAuthnManager", manager)
        setPrivateField(service, "attestedCredentialDataConverter", converter)

        val userId = Uuid.random()
        val opts = service.generateRegistrationOptions(userId, "alice", "Alice")
        val result =
            service.verifyRegistration(
                userId,
                opts.challenge,
                PasskeyRegistrationResponse(
                    id = canonicalId,
                    rawId = canonicalId,
                    response =
                        AuthenticatorAttestationResponse(
                            clientDataJSON = encodeBase64Url("cd".encodeToByteArray()),
                            attestationObject = encodeBase64Url("ao".encodeToByteArray()),
                        ),
                ),
            )

        assertTrue(result.success)
        assertEquals(canonicalId, result.credentialId)
        coVerify(exactly = 1) { repository.deactivateAllForUser(userId) }
        coVerify(exactly = 1) { repository.store(userId, canonicalId, byteArrayOf(0xFF.toByte()), 0L) }
    }

    // -----------------------------------------------------------------------
    // Strict mode — authentication
    // -----------------------------------------------------------------------

    @Test
    fun `strict authentication validates base64url encoding of payload fields`() {
        val repository = mockk<RestoreCredentialRepository>()
        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = true)
        val validId = encodeBase64Url(byteArrayOf(1, 2))
        val stored =
            StoredRestoreCredentialData(
                credentialId = validId,
                publicKey = byteArrayOf(1, 2, 3),
                signCount = 0L,
                userId = Uuid.random(),
            )
        coEvery { repository.findByCredentialId(validId) } returns stored
        coEvery { repository.findByCredentialId(any()) } returns null

        val baseResponse =
            PasskeyAuthenticationResponse(
                id = validId,
                rawId = validId,
                response =
                    AuthenticatorAssertionResponse(
                        clientDataJSON = encodeBase64Url("cd".encodeToByteArray()),
                        authenticatorData = encodeBase64Url("ad".encodeToByteArray()),
                        signature = encodeBase64Url("sig".encodeToByteArray()),
                        userHandle = null,
                    ),
            )

        // Invalid credential ID
        val invalidId =
            service.verifyAuthentication(
                service.generateAuthOptions().challenge,
                baseResponse.copy(id = "*invalid*"),
            )
        assertFalse(invalidId.success)
        assertContains(invalidId.error.orEmpty(), "Credential ID is not valid base64url")

        // Credential not found
        coEvery { repository.findByCredentialId(validId) } returns null
        val notFound =
            service.verifyAuthentication(
                service.generateAuthOptions().challenge,
                baseResponse,
            )
        assertFalse(notFound.success)
        assertContains(notFound.error.orEmpty(), "Restore credential not found")
    }

    @Test
    fun `strict authentication handles field decode errors`() {
        val repository = mockk<RestoreCredentialRepository>()
        val userId = Uuid.random()
        val credentialBytes = byteArrayOf(5, 6, 7)
        val credentialId = encodeBase64Url(credentialBytes)
        val stored =
            StoredRestoreCredentialData(
                credentialId = credentialId,
                publicKey = byteArrayOf(1, 2, 3),
                signCount = 1L,
                userId = userId,
            )
        val converter = mockk<AttestedCredentialDataConverter>()
        val manager = mockk<WebAuthnManager>(relaxed = true)
        val attestedCredentialData = mockk<AttestedCredentialData>()
        coEvery { repository.findByCredentialId(credentialId) } returns stored
        every { converter.convert(stored.publicKey) } returns attestedCredentialData

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = true)
        setPrivateField(service, "webAuthnManager", manager)
        setPrivateField(service, "attestedCredentialDataConverter", converter)

        // Invalid authenticator data
        val invalidAuthData =
            service.verifyAuthentication(
                service.generateAuthOptions().challenge,
                PasskeyAuthenticationResponse(
                    id = credentialId,
                    rawId = credentialId,
                    response = AuthenticatorAssertionResponse("cd", "*invalid*", "sig", null),
                ),
            )
        assertFalse(invalidAuthData.success)
        assertContains(invalidAuthData.error.orEmpty(), "Authenticator data is not valid base64url")

        // Invalid client data
        val invalidCd =
            service.verifyAuthentication(
                service.generateAuthOptions().challenge,
                PasskeyAuthenticationResponse(
                    id = credentialId,
                    rawId = credentialId,
                    response = AuthenticatorAssertionResponse("*invalid*", encodeBase64Url("ad".encodeToByteArray()), "sig", null),
                ),
            )
        assertFalse(invalidCd.success)
        assertContains(invalidCd.error.orEmpty(), "Client data is not valid base64url")

        // Invalid signature
        val invalidSig =
            service.verifyAuthentication(
                service.generateAuthOptions().challenge,
                PasskeyAuthenticationResponse(
                    id = credentialId,
                    rawId = credentialId,
                    response =
                        AuthenticatorAssertionResponse(
                            encodeBase64Url("cd".encodeToByteArray()),
                            encodeBase64Url("ad".encodeToByteArray()),
                            "*invalid*",
                            null,
                        ),
                ),
            )
        assertFalse(invalidSig.success)
        assertContains(invalidSig.error.orEmpty(), "Signature is not valid base64url")
    }

    @Test
    fun `strict authentication surfaces data conversion failure`() {
        val repository = mockk<RestoreCredentialRepository>()
        val userId = Uuid.random()
        val credentialBytes = byteArrayOf(3, 3, 3)
        val credentialId = encodeBase64Url(credentialBytes)
        val stored =
            StoredRestoreCredentialData(
                credentialId = credentialId,
                publicKey = byteArrayOf(9, 9),
                signCount = 0L,
                userId = userId,
            )
        val converter = mockk<AttestedCredentialDataConverter>()
        val manager = mockk<WebAuthnManager>(relaxed = true)
        coEvery { repository.findByCredentialId(credentialId) } returns stored
        every { converter.convert(stored.publicKey) } throws DataConversionException("bad key")

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = true)
        setPrivateField(service, "webAuthnManager", manager)
        setPrivateField(service, "attestedCredentialDataConverter", converter)

        val result =
            service.verifyAuthentication(
                service.generateAuthOptions().challenge,
                PasskeyAuthenticationResponse(
                    id = credentialId,
                    rawId = credentialId,
                    response =
                        AuthenticatorAssertionResponse(
                            encodeBase64Url("cd".encodeToByteArray()),
                            encodeBase64Url("ad".encodeToByteArray()),
                            encodeBase64Url("sig".encodeToByteArray()),
                            null,
                        ),
                ),
            )
        assertFalse(result.success)
        assertContains(result.error.orEmpty(), "Authentication data conversion failed")
    }

    @Test
    fun `strict authentication returns userId and deactivates credential on success`() {
        val repository = mockk<RestoreCredentialRepository>()
        val userId = Uuid.random()
        val credentialBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val credentialId = encodeBase64Url(credentialBytes)
        val stored =
            StoredRestoreCredentialData(
                credentialId = credentialId,
                publicKey = byteArrayOf(1, 2, 3),
                signCount = 2L,
                userId = userId,
            )
        val converter = mockk<AttestedCredentialDataConverter>()
        val manager = mockk<WebAuthnManager>()
        val authenticationData = mockk<AuthenticationData>()
        val authDataObj = mockk<AuthenticatorData<AuthenticationExtensionAuthenticatorOutput>>()
        val attestedCredentialData = mockk<AttestedCredentialData>()

        coEvery { repository.findByCredentialId(credentialId) } returns stored
        coEvery { repository.updateSignCount(credentialId, 5L) } returns true
        coEvery { repository.deactivate(credentialId) } returns true
        every { converter.convert(stored.publicKey) } returns attestedCredentialData
        every { manager.verify(any<AuthenticationRequest>(), any<AuthenticationParameters>()) } returns authenticationData
        every { authenticationData.authenticatorData } returns authDataObj
        every { authDataObj.signCount } returns 5L

        val service = RestoreCredentialService(restoreCredentialRepository = repository, strictVerificationEnabled = true)
        setPrivateField(service, "webAuthnManager", manager)
        setPrivateField(service, "attestedCredentialDataConverter", converter)

        val challenge = service.generateAuthOptions().challenge
        val result =
            service.verifyAuthentication(
                challenge,
                PasskeyAuthenticationResponse(
                    id = credentialId,
                    rawId = credentialId,
                    response =
                        AuthenticatorAssertionResponse(
                            clientDataJSON = encodeBase64Url("cd".encodeToByteArray()),
                            authenticatorData = encodeBase64Url("ad".encodeToByteArray()),
                            signature = encodeBase64Url("sig".encodeToByteArray()),
                            userHandle = null,
                        ),
                ),
            )

        assertTrue(result.success)
        assertEquals(userId, result.userId)
        assertEquals(credentialId, result.credentialId)
        coVerify(exactly = 1) { repository.updateSignCount(credentialId, 5L) }
        coVerify(exactly = 1) { repository.deactivate(credentialId) }
    }

    // -----------------------------------------------------------------------
    // Registration options — generate options shape
    // -----------------------------------------------------------------------

    @Test
    fun `generateRegistrationOptions returns expected shape`() {
        val repository = mockk<RestoreCredentialRepository>()
        coEvery { repository.getCredentialIdsForUser(any()) } returns emptyList()

        val service =
            RestoreCredentialService(
                restoreCredentialRepository = repository,
                relyingPartyId = "example.com",
                relyingPartyName = "Example",
            )
        val userId = Uuid.random()
        val opts = service.generateRegistrationOptions(userId, "bob", "Bob")

        assertEquals("example.com", opts.rpId)
        assertEquals("Example", opts.rpName)
        assertTrue(opts.challenge.isNotEmpty())
        assertTrue(opts.excludeCredentials.isEmpty())
    }

    @Test
    fun `generateAuthOptions returns empty allowCredentials`() {
        val repository = mockk<RestoreCredentialRepository>()
        val service = RestoreCredentialService(restoreCredentialRepository = repository)
        val opts = service.generateAuthOptions()

        assertTrue(opts.challenge.isNotEmpty())
        assertTrue(opts.allowCredentials.isEmpty())
        assertEquals("logdate.app", opts.rpId)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun encodeBase64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun challenge(
        userId: Uuid,
        type: String,
        isUsed: Boolean = false,
    ): PasskeyChallenge =
        PasskeyChallenge(
            challenge = "c",
            userId = userId,
            type = type,
            expiresAt = (Clock.System.now() + 5.minutes).toString(),
            isUsed = isUsed,
        )

    @Suppress("UNCHECKED_CAST")
    private fun putChallenge(
        service: RestoreCredentialService,
        challenge: String,
        challengeData: PasskeyChallenge,
    ) {
        val field = service::class.java.getDeclaredField("challenges")
        field.isAccessible = true
        val map = field.get(service) as MutableMap<String, PasskeyChallenge>
        map[challenge] = challengeData.copy(challenge = challenge)
    }

    private fun setPrivateField(
        target: Any,
        fieldName: String,
        value: Any,
    ) {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
