package app.logdate.client.data.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.account.RenewalStorage
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.device.restore.admitNativeContent
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.device.storage.SecureStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeAccountRecoveryAttemptTest {
    private val owner = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
    private val ownerKey = stringPreferencesKey("identity.canonical_owner_id")
    private val boundKey = stringPreferencesKey("identity.canonical_owner_bound")
    private val response =
        CompleteAuthenticationData(
            LogDateAccount(
                Uuid.parse(owner),
                "synthetic",
                "Synthetic",
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            ),
            AccountTokens("synthetic-access", "synthetic-refresh"),
        )

    @Test
    fun `offline attempt resumes after recreation without a foreground activity`() =
        runTest {
            fixture { state ->
                val first = state.attempt { Result.failure(IllegalStateException("offline")) }
                assertFalse(first.run())
                assertEquals(state.request, NativeAccountRecoveryInbox(state.directory).pending())
                assertFalse(state.acknowledged)
                assertTrue(state.attempt { Result.success(response) }.run())
                assertEquals(owner, state.vault.getSession()?.accountId)
                assertTrue(state.acknowledged)
                assertNull(NativeAccountRecoveryInbox(state.directory).pending())
            }
        }

    @Test
    fun `unrelated preference edits during authentication preserve recovery`() =
        runTest {
            fixture { state ->
                assertTrue(
                    state
                        .attempt {
                            state.preferences.edit { it[stringPreferencesKey("theme")] = "dark" }
                            Result.success(response)
                        }.run(),
                )
                assertEquals(owner, state.vault.getSession()?.accountId)
                assertEquals("dark", state.preferences.data.first()[stringPreferencesKey("theme")])
            }
        }

    @Test
    fun `first local media during an unbound exchange prevents claiming the journal`() =
        runTest {
            fixture(bound = false) { state ->
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val work =
                    async {
                        state
                            .attempt {
                                entered.complete(Unit)
                                release.await()
                                Result.success(response)
                            }.run()
                    }
                runCurrent()
                assertTrue(entered.isCompleted)
                admitNativeContent(File(state.directory, "media"))
                release.complete(Unit)
                assertFalse(work.await())
                assertNull(state.preferences.data.first()[ownerKey])
                assertNull(state.vault.getSession())
                assertFalse(state.acknowledged)
            }
        }

    @Test
    fun `sign out during exchange cannot bind or publish the restored account`() =
        runTest {
            fixture(bound = false) { state ->
                assertFalse(
                    state
                        .attempt {
                            state.vault.clearSession()
                            Result.success(response)
                        }.run(),
                )
                assertNull(state.preferences.data.first()[ownerKey])
                assertNull(state.vault.getSession())
                assertFalse(state.acknowledged)
            }
        }

    @Test
    fun `a durable session completes its matching request without consuming another credential`() =
        runTest {
            fixture { state ->
                val guard = assertNotNull(state.vault.captureInstallationGuard())
                assertTrue(
                    state.vault.installSessionIfCurrent(
                        guard,
                        app.logdate.client.datastore
                            .UserSession("durable", "refresh", owner),
                    ),
                )
                assertTrue(state.attempt { error("credential consumed twice") }.run())
                assertEquals("durable", state.vault.getSession()?.accessToken)
                assertTrue(state.acknowledged)
            }
        }

    @Test
    fun `switching away and back during exchange rejects the original attempt`() =
        runTest {
            fixture { state ->
                state.epoch.initialize()
                val original = assertNotNull(nativeRestoreConfiguration(state.preferences.data.first())).getCurrentBackendUrl()
                assertFalse(
                    state
                        .attempt {
                            state.selected.updateBackendUrl("https://other.example")
                            state.selected.updateBackendUrl(original)
                            Result.success(response)
                        }.run(),
                )
                assertNull(state.vault.getSession())
                assertFalse(state.acknowledged)
            }
        }

    @Test
    fun `configuration change while renewal is retained prevents secure publication`() =
        runTest {
            fixture { state ->
                state.renewal = { state.preferences.edit { it[stringPreferencesKey("api_version")] = "changed" } }
                assertFalse(state.attempt { Result.success(response) }.run())
                assertNull(state.vault.getSession())
                assertFalse(state.acknowledged)
            }
        }

    @Test
    fun `fresh attempts reject a selected server whose persistence is still delayed`() =
        runTest {
            fixture { state ->
                state.selected.updateBackendUrl("https://other.example")
                assertFalse(state.attempt { error("credential consumed for stale persisted configuration") }.run())
                assertNull(state.vault.getSession())
                assertFalse(state.acknowledged)
            }
        }

    @Test
    fun `an interrupted owner commit resumes only its retained authenticated target`() =
        runTest {
            fixture(bound = false, failFirstSessionWrite = true) { state ->
                assertFalse(state.attempt { Result.success(response) }.run())
                assertEquals(owner, state.preferences.data.first()[ownerKey])
                assertEquals("true", state.preferences.data.first()[boundKey])
                assertEquals(state.request, NativeAccountRecoveryInbox(state.directory).pending())
                assertFalse(state.acknowledged)
                state.vault = state.newVault()
                val wrongOwner = response.copy(account = response.account.copy(id = Uuid.random()))
                assertFalse(state.attempt { Result.success(wrongOwner) }.run())
                assertEquals(owner, state.preferences.data.first()[ownerKey])
                assertNull(state.vault.getSession())
                state.vault = state.newVault()
                assertTrue(state.attempt { Result.success(response) }.run())
                assertEquals(owner, state.vault.getSession()?.accountId)
                assertTrue(state.acknowledged)
            }
        }

    @Test
    fun `a changed attempt epoch retains a valid job for a fresh retry`() =
        runTest {
            fixture { state ->
                state.epoch.initialize()
                val interrupted =
                    state.attempt {
                        state.epoch.transition(true) { Unit }
                        Result.success(response)
                    }
                assertFalse(interrupted.run())
                assertTrue(interrupted.canRetry())
                assertTrue(state.attempt { Result.success(response) }.run())
                assertTrue(state.acknowledged)
            }
        }

    @Test
    fun `persisted system custody repairs corrupted metadata without another device transfer`() =
        runTest {
            fixture { state ->
                File(state.directory, "native-account-recovery.json").writeText("truncated")
                assertTrue(
                    recoverNativeAccountRecoveryCustody(
                        state.request,
                        state.preferences.data.first(),
                        NativeAccountRecoveryInbox(state.directory),
                        false,
                    ),
                )
                assertTrue(
                    state.directory.listFiles()!!.any {
                        it.name.contains("unreadable") && it.isFile && it.readText() == "truncated"
                    },
                )
                assertTrue(state.attempt { Result.success(response) }.run())
                assertEquals(owner, state.vault.getSession()?.accountId)
            }
        }

    @Test
    fun `changed configuration cannot rewrite corrupted custody from stale system extras`() =
        runTest {
            fixture { state ->
                val file = File(state.directory, "native-account-recovery.json")
                file.writeText("truncated")
                state.preferences.edit { it[stringPreferencesKey("api_version")] = "different" }
                assertFalse(
                    recoverNativeAccountRecoveryCustody(
                        state.request,
                        state.preferences.data.first(),
                        NativeAccountRecoveryInbox(state.directory),
                        false,
                    ),
                )
                assertEquals("truncated", file.readText())
                assertNull(state.vault.getSession())
            }
        }

    @Test
    fun `corrupted custody after an owner commit recovers only the stamped account`() =
        runTest {
            fixture(bound = false, failFirstSessionWrite = true) { state ->
                assertFalse(state.attempt { Result.success(response) }.run())
                File(state.directory, "native-account-recovery.json").writeText("truncated")
                assertTrue(
                    recoverNativeAccountRecoveryCustody(
                        state.request,
                        state.preferences.data.first(),
                        NativeAccountRecoveryInbox(state.directory),
                        false,
                    ),
                )
                assertEquals(owner, NativeAccountRecoveryInbox(state.directory).acceptedTarget(state.request))
                state.vault = state.newVault()
                val wrong = response.copy(account = response.account.copy(id = Uuid.random()))
                assertFalse(state.attempt { Result.success(wrong) }.run())
                assertEquals(owner, state.preferences.data.first()[ownerKey])
                assertTrue(state.attempt { Result.success(response) }.run())
            }
        }

    @Test
    fun `new local content prevents rebuilding an unbound request from corrupted metadata`() =
        runTest {
            fixture(bound = false) { state ->
                val file = File(state.directory, "native-account-recovery.json")
                file.writeText("truncated")
                admitNativeContent(File(state.directory, "media"))
                assertFalse(
                    recoverNativeAccountRecoveryCustody(
                        state.request,
                        state.preferences.data.first(),
                        NativeAccountRecoveryInbox(state.directory),
                        true,
                    ),
                )
                assertEquals("truncated", file.readText())
                assertNull(state.preferences.data.first()[ownerKey])
            }
        }

    @Test
    fun `stale system extras cannot replace a valid newer delivery request`() =
        runTest {
            fixture { state ->
                val inbox = NativeAccountRecoveryInbox(state.directory)
                val next =
                    state.request.copy(
                        delivery = "47a986a7-e027-4c9b-9b49-745ae7d845cb",
                        generation = "528c7e1d-4e2c-4fb5-af7c-0cd3891770db",
                    )
                assertTrue(inbox.retain(next))
                assertFalse(recoverNativeAccountRecoveryCustody(state.request, state.preferences.data.first(), inbox, false))
                assertEquals(next, inbox.pending())
            }
        }

    @Test
    fun `accepted target survives corrupted custody before the owner preference edit`() =
        runTest {
            fixture(bound = false) { state ->
                val inbox = NativeAccountRecoveryInbox(state.directory)
                assertTrue(inbox.acceptTarget(state.request, owner))
                File(state.directory, "native-account-recovery.json").writeText("truncated")
                assertTrue(
                    recoverNativeAccountRecoveryCustody(
                        state.request,
                        state.preferences.data.first(),
                        NativeAccountRecoveryInbox(state.directory),
                        false,
                    ),
                )
                assertEquals(owner, NativeAccountRecoveryInbox(state.directory).acceptedTarget(state.request))
                val wrong = response.copy(account = response.account.copy(id = Uuid.random()))
                assertFalse(state.attempt { Result.success(wrong) }.run())
                assertNull(state.preferences.data.first()[ownerKey])
                assertTrue(state.attempt { Result.success(response) }.run())
            }
        }

    @Test
    fun `unbound custody with no independent receipt cannot forget a previously accepted account`() =
        runTest {
            fixture(bound = false) { state ->
                val inbox = NativeAccountRecoveryInbox(state.directory)
                assertTrue(inbox.acceptTarget(state.request, owner))
                state.directory
                    .listFiles()!!
                    .filter { it.name.startsWith("native-account-target-") }
                    .forEach { assertTrue(it.delete()) }
                File(state.directory, "native-account-recovery.json").writeText("truncated")
                assertFalse(
                    recoverNativeAccountRecoveryCustody(
                        state.request,
                        state.preferences.data.first(),
                        NativeAccountRecoveryInbox(state.directory),
                        false,
                    ),
                )
                assertNull(state.preferences.data.first()[ownerKey])
                assertNull(state.vault.getSession())
            }
        }

    private suspend fun TestScope.fixture(
        bound: Boolean = true,
        failFirstSessionWrite: Boolean = false,
        block: suspend (Fixture) -> Unit,
    ) {
        val directory = Files.createTempDirectory("native-account-attempt").toFile()
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(directory, "main.preferences_pb") }
            if (bound) {
                preferences.edit {
                    it[ownerKey] = owner
                    it[boundKey] = "true"
                }
            }
            val snapshot = preferences.data.first()
            val config = assertNotNull(nativeRestoreConfiguration(snapshot))
            val request =
                assertNotNull(
                    nativeAccountRecoveryRequest(
                        "2f18404e-e9f5-434d-8b03-2f04f09ac523",
                        "19b6ef01-19ec-4b65-8c82-c49b1ea25862",
                        snapshot,
                    ),
                )
            val inbox = NativeAccountRecoveryInbox(directory)
            assertTrue(inbox.retain(request))
            val backing = RenewalStorage()
            var failWrite = failFirstSessionWrite
            val storage =
                object : SecureStorage by backing {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (failWrite && key.startsWith("session_record_v2")) {
                            failWrite = false
                            error("simulated interrupted secure write")
                        }
                        backing.putString(key, value)
                    }
                }
            val epoch =
                app.logdate.shared.config.PrivacyScopeEpoch(
                    object : app.logdate.shared.config.PrivacyEpochStorage {
                        override suspend fun read() = storage.getString("diagnostic_privacy_epoch")

                        override suspend fun write(epoch: String) = storage.putString("diagnostic_privacy_epoch", epoch)
                    },
                )

            fun newVault() =
                SecureSessionStorage(
                    storage,
                    config,
                    backgroundScope,
                    nativeRestorePolicy = NativeRestorePolicy(DataStoreKeyValueStorage(preferences)),
                )
            val selected =
                DefaultLogDateConfigRepository(
                    initialBackendUrl = config.getCurrentBackendUrl(),
                    initialApiVersion = config.apiVersion.value,
                    privacyEpoch = epoch,
                )
            block(Fixture(directory, preferences, request, newVault(), epoch, config, selected, ::newVault))
        } finally {
            directory.deleteRecursively()
        }
    }

    private class Fixture(
        val directory: File,
        val preferences: DataStore<Preferences>,
        val request: NativeAccountRecoveryRequest,
        var vault: SecureSessionStorage,
        val epoch: app.logdate.shared.config.PrivacyScopeEpoch,
        val config: LogDateConfigRepository,
        val selected: DefaultLogDateConfigRepository,
        val newVault: () -> SecureSessionStorage,
    ) {
        var acknowledged = false
        var renewal: suspend () -> Unit = {}

        fun attempt(exchange: suspend () -> Result<CompleteAuthenticationData>) =
            NativeAccountRecoveryAttempt(
                request,
                preferences,
                NativeAccountRecoveryInbox(directory),
                vault,
                deliveryCurrent = { true },
                hasLocalContent = { File(directory, "media").exists() },
                exchange = {
                    exchange()
                },
                markRenewal = {
                    renewal()
                },
                acknowledge = {
                    acknowledged = true
                },
                privacyEpoch = epoch,
                selectedConfigurationCurrent = { nativeRestoreSelectedConfigurationMatches(config, selected) },
            )
    }
}
