@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptEngineMetadata
import app.logdate.client.sync.cloud.ContentUpdateRequest
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AudioTranscriptSyncE2ETest {
    @Test
    fun `real server stores opaque structured transcript and preserves it for older updates`() =
        runTest {
            withServerClientHarness {
                val token =
                    apiClient
                        .createAccountWithSyntheticPasskey("transcript_${Random.nextInt(100000, 999999)}")
                        .data.tokens.accessToken
                val crypto = DesktopCryptoManager()
                val identity = IdentityKeyManager(TranscriptSecrets(), crypto)
                identity.setupNewIdentity()
                val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
                val source = DefaultCloudContentDataSource(apiClient, cipher)
                val document =
                    TranscriptDocument.fromPlainText("Private spoken memories").copy(
                        revision = 6,
                        engine = TranscriptEngineMetadata("sherpa-onnx", "local-model"),
                    )
                val now = Clock.System.now()
                val note =
                    JournalNote.Audio(
                        "audio.m4a",
                        creationTimestamp = now,
                        lastUpdated = now,
                        caption = "Recording caption",
                        transcript = document,
                    )
                source.uploadNote(token, note).getOrThrow()
                val opaque =
                    apiClient
                        .getContentChanges(token, 0)
                        .getOrThrow()
                        .changes
                        .single()
                assertTrue(opaque.transcript.orEmpty().startsWith("LDSE2:"))
                assertFalse(opaque.transcript.orEmpty().contains(document.plainText))
                apiClient
                    .updateContent(
                        token,
                        note.uid.toString(),
                        ContentUpdateRequest(lastUpdated = now.toEpochMilliseconds() + 1),
                    ).getOrThrow()
                val downloaded =
                    source
                        .getContentChanges(token, Instant.fromEpochMilliseconds(0))
                        .getOrThrow()
                        .changes
                        .single() as JournalNote.Audio
                assertEquals(document, downloaded.transcript)
                assertEquals("Recording caption", downloaded.caption)
                apiClient
                    .updateContent(
                        token,
                        note.uid.toString(),
                        ContentUpdateRequest(lastUpdated = now.toEpochMilliseconds() + 2, mediaUri = "replacement.m4a"),
                    ).getOrThrow()
                val replacement =
                    source
                        .getContentChanges(token, Instant.fromEpochMilliseconds(0))
                        .getOrThrow()
                        .changes
                        .single()
                assertEquals("replacement.m4a", (replacement as JournalNote.Audio).mediaRef)
                assertNull(replacement.transcript)
            }
        }
}

private class TranscriptSecrets : SecureStorage {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = state.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        state.value = state.value + (key to value)
    }

    override suspend fun remove(key: String) {
        state.value = state.value - key
    }

    override suspend fun clear() {
        state.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = state.map { it[key] }

    override fun observeAll(): Flow<Map<String, String>> = state

    override suspend fun encrypt(data: ByteArray): ByteArray = error("Not used")

    override suspend fun decrypt(data: ByteArray): ByteArray? = error("Not used")
}
