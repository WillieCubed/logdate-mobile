package app.logdate.client.data.restore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.account.RenewalStorage
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.device.storage.SecureStorage
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NativeRecoveryOwnerCasingTest {
    private val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
    private val ownerKey = stringPreferencesKey("identity.canonical_owner_id")
    private val response =
        CompleteAuthenticationData(
            LogDateAccount(Uuid.parse(raw), "person", "Person"),
            AccountTokens("synthetic-access", "synthetic-refresh"),
        )

    @Test
    fun `background credential recovery preserves bound raw owner through interrupted secure publication`() =
        runTest {
            for (interrupt in listOf(false, true)) {
                val directory = Files.createTempDirectory("native-owner-case").toFile()
                try {
                    val preferences = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(directory, "main.preferences_pb") }
                    preferences.edit {
                        it[ownerKey] = raw
                        it[stringPreferencesKey("identity.canonical_owner_bound")] = "true"
                    }
                    val initial = preferences.data.first()
                    val request = assertNotNull(nativeAccountRecoveryRequest(Uuid.random().toString(), Uuid.random().toString(), initial))
                    val config = assertNotNull(nativeRestoreConfiguration(initial))
                    val inbox = NativeAccountRecoveryInbox(directory)
                    assertTrue(inbox.retain(request))
                    val backing = RenewalStorage()
                    var failWrite = interrupt
                    val storage =
                        object : SecureStorage by backing {
                            override suspend fun putString(
                                key: String,
                                value: String,
                            ) {
                                if (failWrite && key.startsWith("session_record_v2")) {
                                    failWrite = false
                                    error("Interrupted secure publication")
                                }
                                backing.putString(key, value)
                            }
                        }

                    fun vault() =
                        SecureSessionStorage(
                            storage,
                            config,
                            backgroundScope,
                            nativeRestorePolicy = NativeRestorePolicy(DataStoreKeyValueStorage(preferences)),
                        )
                    var vault = vault()
                    var acknowledgments = 0

                    fun attempt() =
                        NativeAccountRecoveryAttempt(
                            request,
                            preferences,
                            NativeAccountRecoveryInbox(directory),
                            vault,
                            { true },
                            { false },
                            { Result.success(response) },
                            {},
                            { acknowledgments++ },
                        )
                    if (interrupt) {
                        assertFalse(attempt().run())
                        assertEquals(raw, preferences.data.first()[ownerKey])
                        assertEquals(raw, inbox.acceptedTarget(request))
                        assertEquals(
                            nativeAccountRecoveryCommitStamp(request, raw),
                            preferences.data.first()[stringPreferencesKey("identity.native_restore_commit")],
                        )
                        assertEquals(0, acknowledgments)
                        vault = vault()
                    }
                    assertTrue(attempt().run())
                    assertEquals(raw, preferences.data.first()[ownerKey])
                    assertEquals(raw.lowercase(), vault.getSession()?.accountId)
                    assertEquals(1, acknowledgments)
                    assertNull(inbox.pending())
                } finally {
                    directory.deleteRecursively()
                }
            }
        }
}
