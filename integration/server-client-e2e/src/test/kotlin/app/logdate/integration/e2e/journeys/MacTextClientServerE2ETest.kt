@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.device.storage.putBytes
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val ENTRY_ID = "61ad68f2-6d4b-42dd-b263-f838195714ad"
private val SWIFT_TEXT =
    """
    LDSE2:{"env":{"alg":"AES-GCM","v":1,"iv":"ICEiIyQlJicoKSor",
    "ct":"rqahAjNt7f0wholsZjWI8\/perFE7tL8MtBAazIEYJRyq",
    "aad":"dHlwZT1DT05URU5UfHY9MXxpZD1zeW5jOm5vdGU6NjFhZDY4ZjItNmQ0Yi00MmRkLWIyNjMtZjgzODE5NTcxNGFkOnRleHQ="},
    "fp":"gaEKxl1FgoE="}
    """.trimIndent().replace("\n", "")

class MacTextClientServerE2ETest {
    @Test
    fun `clearing a photo caption is encrypted and reaches another device`() = runTest { verifyCaptionRemoval(video = false) }

    @Test
    fun `clearing a video caption is encrypted and reaches another device`() = runTest { verifyCaptionRemoval(video = true) }

    private suspend fun verifyCaptionRemoval(video: Boolean) {
        withServerClientHarness {
            val token =
                apiClient
                    .createAccountWithSyntheticPasskey("caption_${Random.nextInt(100000, 999999)}")
                    .data.tokens.accessToken
            val crypto = DesktopCryptoManager()

            suspend fun device(): DefaultCloudContentDataSource {
                val secrets = MemorySecrets()
                secrets.putBytes("identity_key_v1", ByteArray(32) { it.toByte() })
                val identity = IdentityKeyManager(secrets, crypto)
                return DefaultCloudContentDataSource(
                    apiClient,
                    SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto),
                )
            }
            val writer = device()
            val reader = device()
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val id = Uuid.random()
            val original =
                if (video) {
                    JournalNote.Video(id, now, now, mediaRef = "video.mp4", caption = "Private caption")
                } else {
                    JournalNote.Image(id, now, now, mediaRef = "photo.jpg", caption = "Private caption")
                }
            writer.uploadNote(token, original).getOrThrow()
            val start = Instant.fromEpochMilliseconds(0)
            var received =
                reader
                    .getContentChanges(token, start)
                    .getOrThrow()
                    .changes
                    .single()
            for (caption in listOf("", " \n")) {
                val edited =
                    when (val note = received) {
                        is JournalNote.Image -> note.copy(caption = caption)
                        is JournalNote.Video -> note.copy(caption = caption)
                        else -> error("Expected a photo or video")
                    }
                val upload = writer.updateNote(token, edited).getOrThrow()
                val wire =
                    apiClient
                        .getContentChanges(token, 0)
                        .getOrThrow()
                        .changes
                        .single()
                assertTrue(wire.caption.orEmpty().startsWith("LDSE2:"))
                received =
                    reader
                        .getContentChanges(token, start)
                        .getOrThrow()
                        .changes
                        .single()
                val actualCaption =
                    when (val note = received) {
                        is JournalNote.Image -> note.caption
                        is JournalNote.Video -> note.caption
                        else -> error("Expected a photo or video")
                    }
                assertEquals(caption, actualCaption)
                assertEquals(id, received.uid)
                assertEquals(now, received.creationTimestamp)
                assertEquals(upload.serverVersion, received.syncVersion)
                assertTrue(writer.updateNote(token, edited).isFailure)
            }
        }
    }

    @Test
    fun `legacy text can be repaired under the current identity without changing its ID or overwriting a newer version`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("legacy_text_${Random.nextInt(100000, 999999)}").data
                val token = account.tokens.accessToken
                val id = Uuid.random()
                val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                val crypto = DesktopCryptoManager()

                suspend fun cipher(seed: Int): SyncPayloadCipher {
                    val secrets = MemorySecrets()
                    secrets.putBytes("identity_key_v1", ByteArray(32) { (it + seed).toByte() })
                    val identity = IdentityKeyManager(secrets, crypto)
                    return SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
                }

                val oldSecrets = MemorySecrets()
                oldSecrets.putBytes("identity_key_v1", ByteArray(32) { it.toByte() })
                val oldIdentity = IdentityKeyManager(oldSecrets, crypto)
                val oldEncryption = ContentEncryptionService(oldIdentity, KeyDerivation(crypto), crypto)
                val legacy = "LDSE1:" + Json.encodeToString(oldEncryption.encryptContent("sync:note:$id:text", "Surviving local entry"))
                val created =
                    httpClient.put("$baseUrl/contents/$id") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(HttpHeaders.IfNoneMatch, "*")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"id":"$id","type":"TEXT","content":${Json.encodeToString(legacy)},"mediaUri":null,
                            "createdAt":${now.toEpochMilliseconds()},"lastUpdated":${now.toEpochMilliseconds()},"deviceId":"android"}""",
                        )
                    }
                assertEquals(HttpStatusCode.Created, created.status)

                val cloud = DefaultCloudContentDataSource(apiClient, cipher(64))
                val start = Instant.fromEpochMilliseconds(0)
                val unreadable = cloud.getContentChanges(token, start).getOrThrow()
                assertEquals(listOf(id), unreadable.unreadable)
                assertTrue(unreadable.failures.isEmpty())
                val local =
                    JournalNote.Text(
                        uid = id,
                        creationTimestamp = now,
                        lastUpdated = now,
                        content = "Surviving local entry",
                        syncVersion = unreadable.unreadableVersions.getValue(id),
                    )
                val repaired = cloud.updateNote(token, local).getOrThrow()
                val otherDevice = DefaultCloudContentDataSource(apiClient, cipher(64))
                val downloaded =
                    otherDevice
                        .getContentChanges(token, start)
                        .getOrThrow()
                        .changes
                        .single() as JournalNote.Text
                assertEquals(id, downloaded.uid)
                assertEquals(local.content, downloaded.content)
                assertEquals(repaired.serverVersion, downloaded.syncVersion)

                cloud.updateNote(token, downloaded.copy(content = "Newer edit")).getOrThrow()
                assertTrue(cloud.updateNote(token, local).isFailure)
                val latest =
                    otherDevice
                        .getContentChanges(token, start)
                        .getOrThrow()
                        .changes
                        .single() as JournalNote.Text
                assertEquals("Newer edit", latest.content)
            }
        }

    @Test
    fun `Mac timeline text without a journal link becomes readable by Android sync`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("mac_timeline_${Random.nextInt(100000, 999999)}").data
                val token = account.tokens.accessToken
                val now = System.currentTimeMillis()
                val response =
                    httpClient.put("$baseUrl/contents/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(HttpHeaders.IfNoneMatch, "*")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {"id":"$ENTRY_ID","type":"TEXT","content":${Json.encodeToString(SWIFT_TEXT)},
                            "mediaUri":null,"createdAt":$now,"lastUpdated":$now,"deviceId":"mac"}
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.Created, response.status)

                val start = Instant.fromEpochMilliseconds(0)
                val secrets = MemorySecrets()
                secrets.putBytes("identity_key_v1", ByteArray(32) { it.toByte() })
                val crypto = DesktopCryptoManager()
                val identity = IdentityKeyManager(secrets, crypto)
                val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
                val changes = DefaultCloudContentDataSource(apiClient, cipher).getContentChanges(token, start).getOrThrow()
                val macEntry = changes.changes.single { it.uid.toString() == ENTRY_ID } as JournalNote.Text
                assertEquals("Written on my Mac", macEntry.content)
                val links = DefaultCloudAssociationDataSource(apiClient).getAssociationChanges(token, start).getOrThrow()
                assertTrue(links.additions.none { it.contentId.toString() == ENTRY_ID })
            }
        }

    @Test
    fun `Mac encrypted text and journal link become readable by Android sync`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("mac_text_${Random.nextInt(100000, 999999)}").data
                val token = account.tokens.accessToken
                val journalID = Uuid.random().toString()
                val now = System.currentTimeMillis()
                val secrets = MemorySecrets()
                secrets.putBytes("identity_key_v1", ByteArray(32) { it.toByte() })
                val crypto = DesktopCryptoManager()
                val identity = IdentityKeyManager(secrets, crypto)
                val androidCipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
                val title = androidCipher.encryptString("sync:journal:$journalID:title", "Everyday")

                val journalResponse =
                    httpClient.put("$baseUrl/journals/$journalID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(HttpHeaders.IfNoneMatch, "*")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {"id":"$journalID","title":${Json.encodeToString(title)},
                            "description":"","createdAt":$now,"lastUpdated":$now,"deviceId":"mac"}
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.Created, journalResponse.status)

                val contentResponse =
                    httpClient.put("$baseUrl/contents/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(HttpHeaders.IfNoneMatch, "*")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {"id":"$ENTRY_ID","type":"TEXT","content":${Json.encodeToString(SWIFT_TEXT)},
                            "mediaUri":null,"createdAt":$now,"lastUpdated":$now,"deviceId":"mac"}
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.Created, contentResponse.status)
                val associationResponse =
                    httpClient.post("$baseUrl/associations") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"associations":[{"journalId":"$journalID","contentId":"$ENTRY_ID","createdAt":$now,"deviceId":"mac"}]}""",
                        )
                    }
                assertEquals(HttpStatusCode.OK, associationResponse.status)

                val start = Instant.fromEpochMilliseconds(0)

                val unlinkResponse =
                    httpClient.delete("$baseUrl/associations/$journalID/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                assertEquals(HttpStatusCode.NoContent, unlinkResponse.status)
                val unlinked =
                    DefaultCloudAssociationDataSource(apiClient)
                        .getAssociationChanges(token, start)
                        .getOrThrow()
                assertTrue(unlinked.deletions.any { it.journalId.toString() == journalID && it.contentId.toString() == ENTRY_ID })

                val restoredAssociation =
                    httpClient.post("$baseUrl/associations") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"associations":[{"journalId":"$journalID","contentId":"$ENTRY_ID","createdAt":${now + 1},"deviceId":"mac"}]}""",
                        )
                    }
                assertEquals(HttpStatusCode.OK, restoredAssociation.status)

                val journals = DefaultCloudJournalDataSource(apiClient, androidCipher).getJournalChanges(token, start).getOrThrow()
                assertEquals("Everyday", journals.changes.single { it.id.toString() == journalID }.title)

                val changes = DefaultCloudContentDataSource(apiClient, androidCipher).getContentChanges(token, start).getOrThrow()
                val macEntry = changes.changes.single { it.uid.toString() == ENTRY_ID } as JournalNote.Text
                assertEquals("Written on my Mac", macEntry.content)
                assertTrue(macEntry.syncVersion > 0)

                val editedText = androidCipher.encryptString("sync:note:$ENTRY_ID:text", "Edited on my Mac")
                val editBody =
                    """
                    {"content":${Json.encodeToString(editedText)},"lastUpdated":${now + 1},"deviceId":"mac",
                    "versionConstraint":{"type":"known","serverVersion":${macEntry.syncVersion}}}
                    """.trimIndent()
                val editResponse =
                    httpClient.patch("$baseUrl/contents/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(editBody)
                    }
                assertEquals(HttpStatusCode.OK, editResponse.status)

                val updatedChanges =
                    DefaultCloudContentDataSource(apiClient, androidCipher)
                        .getContentChanges(token, start)
                        .getOrThrow()
                val editedEntry = updatedChanges.changes.single { it.uid.toString() == ENTRY_ID } as JournalNote.Text
                assertEquals("Edited on my Mac", editedEntry.content)
                assertTrue(editedEntry.syncVersion > macEntry.syncVersion)

                val staleEdit =
                    httpClient.patch("$baseUrl/contents/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(editBody)
                    }
                assertEquals(HttpStatusCode.Conflict, staleEdit.status)

                val clearedText = androidCipher.encryptString("sync:note:$ENTRY_ID:text", "")
                val clearResponse =
                    httpClient.patch("$baseUrl/contents/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {"content":${Json.encodeToString(clearedText)},"lastUpdated":${now + 2},"deviceId":"mac",
                            "versionConstraint":{"type":"known","serverVersion":${editedEntry.syncVersion}}}
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.OK, clearResponse.status)
                val clearedChanges =
                    DefaultCloudContentDataSource(apiClient, androidCipher)
                        .getContentChanges(token, start)
                        .getOrThrow()
                val clearedEntry = clearedChanges.changes.single { it.uid.toString() == ENTRY_ID } as JournalNote.Text
                assertEquals("", clearedEntry.content)

                val links = DefaultCloudAssociationDataSource(apiClient).getAssociationChanges(token, start).getOrThrow()
                assertTrue(links.additions.any { it.journalId.toString() == journalID && it.contentId.toString() == ENTRY_ID })

                val deletion =
                    httpClient.delete("$baseUrl/contents/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                assertEquals(HttpStatusCode.NoContent, deletion.status)

                // Choosing "Keep My Edit" after a deletion resets the Mac's local version to zero.
                // It must be able to recreate the same content ID and restore its journal link.
                val restoredText = androidCipher.encryptString("sync:note:$ENTRY_ID:text", "Kept on my Mac")
                val restoration =
                    httpClient.put("$baseUrl/contents/$ENTRY_ID") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(HttpHeaders.IfNoneMatch, "*")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {"id":"$ENTRY_ID","type":"TEXT","content":${Json.encodeToString(restoredText)},
                            "mediaUri":null,"createdAt":$now,"lastUpdated":${now + 3},"deviceId":"mac"}
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.Created, restoration.status)
                val restoredLink =
                    httpClient.post("$baseUrl/associations") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"associations":[{"journalId":"$journalID","contentId":"$ENTRY_ID","createdAt":${now + 3},"deviceId":"mac"}]}""",
                        )
                    }
                assertEquals(HttpStatusCode.OK, restoredLink.status)

                val restoredChanges =
                    DefaultCloudContentDataSource(apiClient, androidCipher).getContentChanges(token, start).getOrThrow()
                val restoredEntry = restoredChanges.changes.single { it.uid.toString() == ENTRY_ID } as JournalNote.Text
                assertEquals("Kept on my Mac", restoredEntry.content)
                val restoredLinks = DefaultCloudAssociationDataSource(apiClient).getAssociationChanges(token, start).getOrThrow()
                assertTrue(restoredLinks.additions.any { it.journalId.toString() == journalID && it.contentId.toString() == ENTRY_ID })
            }
        }
}

private class MemorySecrets : SecureStorage {
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

    override suspend fun encrypt(data: ByteArray): ByteArray = error("Not used by this fixture")

    override suspend fun decrypt(data: ByteArray): ByteArray? = error("Not used by this fixture")
}
