package app.logdate.server.auth

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AccountModelsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `account and session models serialize and deserialize`() {
        val now = Clock.System.now()
        val account =
            Account(
                id = Uuid.random(),
                username = "u1",
                displayName = "User One",
                did = "did:web:u1.logdate.app",
                handle = "u1.logdate.app",
                signingKeyPublic = "zExampleKey",
                email = "u1@example.com",
                emailVerified = true,
                bio = "bio",
                createdAt = now,
                lastSignInAt = now,
                timezone = "UTC",
                locale = "en-US",
                preferences = "{}",
                isActive = true,
            )
        val encodedAccount = json.encodeToString(Account.serializer(), account)
        val decodedAccount = json.decodeFromString(Account.serializer(), encodedAccount)
        assertEquals(account.id, decodedAccount.id)
        assertEquals("u1", decodedAccount.username)
        assertEquals("did:web:u1.logdate.app", decodedAccount.did)
        assertEquals("u1.logdate.app", decodedAccount.handle)
        assertEquals("zExampleKey", decodedAccount.signingKeyPublic)
        assertEquals("UTC", decodedAccount.timezone)
        assertEquals("en-US", decodedAccount.locale)

        val device =
            DeviceInfo(
                platform = "android",
                deviceName = "pixel",
                osVersion = "15",
                appVersion = "1.2.3",
                capabilities = listOf("uv"),
            )
        val encodedDevice = json.encodeToString(DeviceInfo.serializer(), device)
        val decodedDevice = json.decodeFromString(DeviceInfo.serializer(), encodedDevice)
        assertEquals("android", decodedDevice.platform)
        assertEquals("pixel", decodedDevice.deviceName)
        assertEquals("15", decodedDevice.osVersion)
        assertEquals("1.2.3", decodedDevice.appVersion)
        assertEquals(listOf("uv"), decodedDevice.capabilities)

        val session =
            TemporarySession(
                id = "s-1",
                temporaryUserId = Uuid.random(),
                challenge = "challenge",
                username = "u1",
                displayName = "User One",
                bio = null,
                deviceInfo = device,
                sessionType = SessionType.ACCOUNT_CREATION,
                createdAt = now,
                expiresAt = now,
                isUsed = false,
            )
        val encodedSession = json.encodeToString(TemporarySession.serializer(), session)
        val decodedSession = json.decodeFromString(TemporarySession.serializer(), encodedSession)
        assertEquals(session.id, decodedSession.id)
        assertEquals(SessionType.ACCOUNT_CREATION, decodedSession.sessionType)
        assertTrue(SessionType.entries.contains(SessionType.AUTHENTICATION))

        val identity =
            AccountIdentity(
                id = Uuid.random(),
                accountId = account.id,
                provider = IdentityProvider.PASSKEY,
                providerSubject = "credential-1",
                email = account.email,
                emailVerified = true,
                createdAt = now,
                lastSignInAt = now,
            )
        val encodedIdentity = json.encodeToString(AccountIdentity.serializer(), identity)
        val decodedIdentity = json.decodeFromString(AccountIdentity.serializer(), encodedIdentity)
        assertEquals(identity.provider, decodedIdentity.provider)

        val linkEvent =
            AccountLinkEvent(
                id = Uuid.random(),
                accountId = account.id,
                provider = IdentityProvider.GOOGLE,
                providerSubject = "google-sub-1",
                reason = "implicit_verified_email",
                createdAt = now,
            )
        val encodedEvent = json.encodeToString(AccountLinkEvent.serializer(), linkEvent)
        val decodedEvent = json.decodeFromString(AccountLinkEvent.serializer(), encodedEvent)
        assertEquals(linkEvent.reason, decodedEvent.reason)
        assertTrue(IdentityProvider.entries.contains(IdentityProvider.GOOGLE))
    }
}
