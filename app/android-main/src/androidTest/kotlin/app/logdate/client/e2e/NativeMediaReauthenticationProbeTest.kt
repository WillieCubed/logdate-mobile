package app.logdate.client.e2e

import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.MainActivity
import app.logdate.client.data.account.AccountKeyUnlockCoordinator
import app.logdate.client.data.account.DefaultPasskeyAccountRepository
import app.logdate.client.data.account.RestoreCredentialLifecycle
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.PasskeyAuthenticationResult
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.PasskeyAuthenticationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Real server refusal and repository sign-in; the only authentication seam signs the ceremony. */
@RunWith(AndroidJUnit4::class)
class NativeMediaReauthenticationProbeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin get() = GlobalContext.get()
    private val sessions get() = koin.get<SessionStorage>()
    private val originals by lazy { NativeOriginalMediaProbe(context, koin) }
    private val checkpoint get() = File(context.noBackupFilesDir, "native-media-reauth-checkpoint.json")
    private val noteId = Uuid.parse("00000000-0000-0000-0000-000000000004")

    @Test
    fun revokedSessionRetainsOriginalUntilRealReauthentication() =
        runBlocking {
            val stage = InstrumentationRegistry.getArguments().getString("nativeMediaReauthenticationStage")
            assumeTrue(stage != null)
            require(stage in setOf("revoke", "resume"))
            require(Build.HARDWARE in setOf("ranchu", "goldfish") && context.packageName.endsWith(".debug"))
            val fixture = readNativeDeviceEnrollmentCredentials(context)
            require(fixture.sourceDevice != null)
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            ActivityScenario.launch(MainActivity::class.java).use {
                koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
                assertEquals(fixture.origin, koin.get<LogDateConfigRepository>().getCurrentBackendUrl())
                val scope = UploadScope(fixture.owner, fixture.origin)
                if (stage == "revoke") revoke(scope) else resume(scope)
            }
        }

    private suspend fun revoke(scope: UploadScope) {
        withTimeout(30_000) { koin.get<DataUsagePolicy>().policy.first { it == DataUsageMode.Conservative } }
        val initial = requireNotNull(sessions.captureAuthenticationSnapshot())
        val bound = requireNotNull(initial.session)
        assertTrue(bound.origin == scope.serverOrigin && bound.session.accountId == scope.ownerId)
        originals.prepareMissingOriginal(checkpoint, scope)
        originals.writeCheckpoint(checkpoint, JSONObject(checkpoint.readText()).put("identity", identity().toString()))
        koin.get<SyncManager>().requestBackup()
        originals.retainDeferredOperation(checkpoint, scope)
        val request = JSONObject(checkpoint.readText()).put("refreshToken", bound.session.refreshToken)
        writePrivate("media-reauth-revoke-request.json", request)
        report("MEDIA_REAUTH_REVOKE")
        val expired = JSONObject(awaitPrivate("media-reauth-expired-session.json"))
        assertEquals(scope.ownerId, expired.getString("owner"))
        assertEquals(scope.serverOrigin, expired.getString("origin"))
        assertEquals(request.getString("operationId"), expired.getString("operationId"))
        assertTrue(expired.getBoolean("refreshRevokedByServer"))
        assertTrue(
            sessions.replaceSessionIfAuthorized(
                bound,
                bound.session.copy(accessToken = expired.getString("expiredAccessToken")),
                initial.guard,
            ),
            "The fixture must not replace a newer authentication decision",
        )
        report("MEDIA_REAUTH_EXPIRED")
        withTimeout(90_000) { sessions.getSessionFlow().first { it == null } }
        assertRetainedSignedOut(scope)
        delay(1_000)
        assertRetainedSignedOut(scope)
        report("MEDIA_REAUTH_SIGNED_OUT")
    }

    private suspend fun resume(scope: UploadScope) {
        assertRetainedSignedOut(scope)
        report("MEDIA_REAUTH_COLD_SIGNED_OUT")
        val lifetime = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager =
            object : PasskeyManager by koin.get<PasskeyManager>() {
                override suspend fun authenticateWithPasskey(options: PasskeyAuthenticationOptions): Result<String> {
                    val request =
                        JSONObject(checkpoint.readText())
                            .put("rpId", options.rpId)
                            .put("challenge", options.challenge)
                    writePrivate("media-reauth-assertion-request.json", request)
                    report("MEDIA_REAUTH_ASSERTION")
                    return Result.success(awaitPrivate("media-reauth-assertion-response.json"))
                }

                override suspend fun authenticateWithUnlock(options: PasskeyAuthenticationOptions): Result<PasskeyAuthenticationResult> =
                    authenticateWithPasskey(options).map { PasskeyAuthenticationResult(it, null) }
            }
        val realApi = koin.get<PasskeyApiClientContract>()
        val observedApi =
            object : PasskeyApiClientContract by realApi {
                override suspend fun completeAuthentication(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData> {
                    val result = realApi.completeAuthentication(request)
                    result.getOrThrow()
                    require(!File(context.noBackupFilesDir, "media-reauth-authentication-boundary.txt").exists())
                    report("MEDIA_REAUTH_SERVER_AUTHENTICATED")
                    check(awaitPrivate("media-reauth-authentication-boundary.txt") == "verified")
                    return result
                }
            }
        try {
            val repository =
                DefaultPasskeyAccountRepository(
                    apiClient = observedApi,
                    passkeyManager = manager,
                    restoreCredentialManager = koin.get<RestoreCredentialLifecycle>(),
                    sessionStorage = sessions,
                    platformAccountManager = koin.get<PlatformAccountManager>(),
                    configRepository = koin.get<LogDateConfigRepository>(),
                    canonicalOwnerProvider = koin.get<CanonicalOwnerProvider>(),
                    hasLocalData = { true },
                    repositoryScope = lifetime,
                    accountKeyUnlockCoordinator = koin.get<AccountKeyUnlockCoordinator>(),
                    // This fixture covers session/media recovery; Google restore-key enrollment is separate.
                    createsRestoreKey = false,
                )
            val account = repository.authenticateWithPasskey(username = null, adoptLocalData = false).getOrThrow()
            assertEquals(scope.ownerId, account.id.toString())
            val current = requireNotNull(sessions.captureAuthenticationSnapshot()).session
            assertTrue(current?.origin == scope.serverOrigin && current.session.accountId == scope.ownerId)
            originals.verifyAutomaticOriginal(checkpoint, scope)
            assertEquals(JSONObject(checkpoint.readText()).getString("identity"), identity().toString())
            val fixtureFile = File(context.noBackupFilesDir, "native-device-enrollment-fixture.json")
            val fixture =
                JSONObject(fixtureFile.readText())
                    .put("accessToken", requireNotNull(current).session.accessToken)
                    .put("refreshToken", current.session.refreshToken)
            originals.writeCheckpoint(fixtureFile, fixture)
            report("MEDIA_REAUTH_AUTOMATIC_ORIGINAL")
        } finally {
            lifetime.cancel()
        }
    }

    private suspend fun assertRetainedSignedOut(scope: UploadScope) {
        assertTrue(requireNotNull(sessions.captureAuthenticationSnapshot()).session == null, "Revoked authentication must remain absent")
        val expected = JSONObject(checkpoint.readText())
        assertEquals(scope.ownerId, expected.getString("owner"))
        assertEquals(scope.serverOrigin, expected.getString("origin"))
        val row =
            requireNotNull(
                koin.get<LogDateDatabase>().downloadInboxDao().get(
                    scope.ownerId,
                    scope.serverOrigin,
                    "MEDIA_NATIVE_NOTE",
                    noteId.toString(),
                ),
            )
        assertEquals(expected.getString("operationId"), row.operationId)
        assertNotEquals("APPLIED", row.state)
        val note = koin.get<LogDateDatabase>().imageNoteDao().getNoteOneOff(noteId)
        assertEquals(expected.getString("missing"), note.contentUri)
        assertEquals(expected.getLong("created"), note.created.toEpochMilliseconds())
        assertEquals(expected.getLong("updated"), note.lastUpdated.toEpochMilliseconds())
        assertEquals(expected.getString("identity"), identity().toString())
    }

    private suspend fun identity(): JSONObject =
        JSONObject()
            .put("owner", koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId())
            .put(
                "device",
                koin
                    .get<DeviceIdProvider>()
                    .getDeviceId()
                    .value
                    .toString(),
            ).put(
                "fingerprint",
                IdentityKeyFingerprint
                    .of(koin.get<IdentityKeyManager>().getIdentityKey(), koin.get<CryptoManager>()),
            )

    private fun writePrivate(
        name: String,
        value: JSONObject,
    ) {
        val file = File(context.noBackupFilesDir, name)
        require(!file.exists()) { "A fresh private authentication request is required" }
        originals.writeCheckpoint(file, value)
    }

    private suspend fun awaitPrivate(name: String): String =
        withTimeout(30_000) {
            val file = File(context.noBackupFilesDir, name)
            while (!file.isFile) delay(25)
            require(file.length() in 1..32_768)
            file.readText()
        }

    private fun report(value: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("nativeMediaReauthentication", value) })
    }
}
