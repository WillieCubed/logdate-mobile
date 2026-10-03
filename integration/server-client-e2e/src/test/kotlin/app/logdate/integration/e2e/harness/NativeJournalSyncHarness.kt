@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.harness

import app.logdate.client.data.account.AccountKeyUnlockCoordinator
import app.logdate.client.data.account.AccountKeyUnlockStatus
import app.logdate.client.device.crypto.AccountKeyEnvelopeCipher
import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.DesktopCryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.DefaultAccountKeyEnvelopeApi
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.MediaUploadRequest
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.crypto.MediaPayloadKeyProvider
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.fixtures.uploadMedia
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

/** Disposable loopback journal for verifying the Swift store without opening an app window. */
object NativeJournalSyncHarness {
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            require(args.size == 1) { "Supply a private temporary control directory" }
            val control = Path.of(args.single())
            Files.createDirectories(control)
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("turnkey_${Random.nextInt(100000, 999999)}").data
                val token = account.tokens.accessToken
                val identityKey = ByteArray(32) { it.toByte() }
                val mediaKey = ByteArray(32) { (it + 32).toByte() }
                val crypto = DesktopCryptoManager()
                val credential =
                    apiClient
                        .getAccountInfo(token)
                        .getOrThrow()
                        .passkeyCredentialIds
                        .single()
                val secret = ByteArray(32) { (it + 128).toByte() }
                Files.write(control.resolve("fixture-unlock-secret"), secret)
                val storage = HarnessSecrets()
                val identity = IdentityKeyManager(storage, crypto)
                identity.installAccountKey(account.account.id.toString(), identityKey)
                val mediaKeys = MediaPayloadKeyProvider(storage, crypto, identity, KeyDerivation(crypto))
                mediaKeys.installAccountKey(mediaKey)
                val envelopeApi = DefaultAccountKeyEnvelopeApi(httpClient)
                val status =
                    AccountKeyUnlockCoordinator(envelopeApi, AccountKeyEnvelopeCipher(crypto), identity, mediaKeys)
                        .unlockOrProvision(baseUrl, account.account.id.toString(), token, credential, secret, true)
                check(status == AccountKeyUnlockStatus.UNLOCKED) { "Encrypted-envelope provisioning is unavailable" }
                val envelope = envelopeApi.fetch(baseUrl, token, credential).getOrThrow()!!
                val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
                val content = DefaultCloudContentDataSource(apiClient, cipher)
                val now = Clock.System.now()
                val initial = JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Existing Android journal entry")
                content.uploadNote(token, initial).getOrThrow()
                val bitmap = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
                val pixels = Random(42)
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) bitmap.setRGB(x, y, pixels.nextInt(0x1000000))
                }
                val photo =
                    ByteArrayOutputStream().use { output ->
                        check(ImageIO.write(bitmap, "png", output))
                        output.toByteArray()
                    }
                check(photo.size > 64 * 1024) { "Fixture must cross an encryption chunk boundary" }
                val encryptedPhoto =
                    AesGcmMediaPayloadCrypto(mediaKey)
                        .streamEncryptor()
                        .encrypt(Buffer().apply { write(photo) })
                        .buffered()
                        .use { it.readByteArray() }
                val photoEntry = JournalNote.Image(creationTimestamp = now, lastUpdated = now, mediaRef = "", caption = "Kotlin photo")
                val upload =
                    apiClient
                        .uploadMedia(
                            token,
                            MediaUploadRequest(
                                contentId = photoEntry.uid.toString(),
                                fileName = "fixture.png",
                                mimeType = "image/png",
                                sizeBytes = encryptedPhoto.size.toLong(),
                                data = encryptedPhoto,
                            ),
                        ).getOrThrow()
                content.uploadNote(token, photoEntry.copy(mediaRef = upload.mediaId)).getOrThrow()
                val fixture =
                    buildJsonObject {
                        put("owner", account.account.id.toString())
                        put("token", token)
                        put("refreshToken", account.tokens.refreshToken)
                        put("api", baseUrl)
                        put("entry", initial.uid.toString())
                        put("credential", credential)
                        put("envelope", envelope)
                        put("photo", photoEntry.uid.toString())
                        put("photoHash", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(photo)))
                    }.toString().toByteArray()
                val fixtureServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                fixtureServer.createContext("/fixture") { exchange ->
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.responseHeaders.add("Cache-Control", "no-store")
                    exchange.sendResponseHeaders(200, fixture.size.toLong())
                    exchange.responseBody.use { it.write(fixture) }
                }
                fixtureServer.start()
                Files.writeString(control.resolve("ready"), "http://127.0.0.1:${fixtureServer.address.port}/fixture")
                try {
                    val deadline = System.currentTimeMillis() + 10 * 60 * 1000
                    while (!Files.exists(control.resolve("complete")) && System.currentTimeMillis() < deadline) {
                        for (round in 1..2) {
                            val requested = Files.exists(control.resolve("phone-edit-$round"))
                            val confirmed = Files.exists(control.resolve("phone-edited-$round"))
                            if (requested && !confirmed) {
                                content
                                    .updateNote(
                                        token,
                                        initial.copy(content = "Concurrent Android edit $round", lastUpdated = Clock.System.now()),
                                    ).getOrThrow()
                                Files.writeString(control.resolve("phone-edited-$round"), "done")
                            }
                        }
                        delay(50)
                    }
                    check(Files.exists(control.resolve("complete"))) { "Mac verification did not finish" }
                    val macEnvelope = Files.readAllBytes(control.resolve("mac-envelope"))
                    val restored = AccountKeyEnvelopeCipher(crypto).open(account.account.id.toString(), credential, secret, macEnvelope)
                    check(restored.identity.contentEquals(identityKey) && restored.media.contentEquals(mediaKey))
                    val downloaded = content.getContentChanges(token, Instant.fromEpochMilliseconds(0)).getOrThrow()
                    check(downloaded.unreadable.isEmpty()) { "Kotlin could not decrypt a Mac edit" }
                    val notes = downloaded.changes.filterIsInstance<JournalNote.Text>()
                    check(notes.any { it.uid == initial.uid && it.content == "Kept Mac draft" })
                    check(notes.any { it.content == "New offline Mac entry" })
                    Files.writeString(control.resolve("verified"), "Kotlin decrypted the resolved Mac conflict and new offline entry.\n")
                } finally {
                    fixtureServer.stop(0)
                    Files.deleteIfExists(control.resolve("ready"))
                }
            }
            Unit
        }
}

private class HarnessSecrets : SecureStorage {
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

    override suspend fun encrypt(data: ByteArray): ByteArray = error("Not used by this disposable fixture")

    override suspend fun decrypt(data: ByteArray): ByteArray? = error("Not used by this disposable fixture")
}
