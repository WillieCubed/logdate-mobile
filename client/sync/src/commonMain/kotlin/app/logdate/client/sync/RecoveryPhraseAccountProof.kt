package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.EncryptedEnvelope
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.space.SpaceDataHomes
import app.logdate.client.sync.space.SpaceRoute
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealedRecordException
import app.logdate.space.SealingKeys
import app.logdate.space.SpaceRef
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Verifies a candidate identity against encrypted content owned by the current account. */
fun interface RecoveryPhraseAccountProof {
    /** Returns the account that was verified, or fails without changing local identity material. */
    suspend fun verify(words: List<String>): String
}

enum class RecoveryPhraseVerificationReason { MISMATCH, UNAVAILABLE, NO_PROOF }

class RecoveryPhraseVerificationException(
    val reason: RecoveryPhraseVerificationReason = RecoveryPhraseVerificationReason.MISMATCH,
    cause: Throwable? = null,
) : Exception("Recovery phrase account verification failed: $reason", cause)

@OptIn(ExperimentalEncodingApi::class)
class CloudRecoveryPhraseAccountProof(
    private val sessionStorage: SessionStorage,
    private val cryptoManager: CryptoManager,
    private val cloudApiClient: CloudApiClient,
    private val spaceRoute: (suspend () -> SpaceRoute)? = null,
    private val spaceDataHomes: SpaceDataHomes? = null,
    private val accountDid: (suspend (String) -> String?)? = null,
) : RecoveryPhraseAccountProof {
    override suspend fun verify(words: List<String>): String {
        val binding = sessionStorage.getOriginBoundSession()
        val session =
            binding?.session ?: sessionStorage.getSession()
                ?: throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
        require(cryptoManager.validateRecoveryPhrase(words)) { "Invalid recovery phrase" }
        val key = cryptoManager.deriveMasterKey(words)
        val fingerprint = IdentityKeyFingerprint.of(key, cryptoManager)
        val cloud =
            try {
                when (spaceRoute?.invoke() ?: SpaceRoute.Legacy) {
                    SpaceRoute.Legacy -> cloudProof(session.accessToken, fingerprint, key)
                    SpaceRoute.Space -> spaceProof(binding?.origin, session.accessToken, session.accountId, key)
                    is SpaceRoute.Blocked -> throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE, error)
            }
        when (cloud) {
            CloudProof.MATCH -> Unit
            CloudProof.MISMATCH -> throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.MISMATCH)
            CloudProof.INCONCLUSIVE -> throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.NO_PROOF)
            CloudProof.NO_SAMPLE -> throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.NO_PROOF)
        }
        val currentBinding = sessionStorage.getOriginBoundSession()
        val current = currentBinding?.session ?: sessionStorage.getSession()
        if (current?.accountId != session.accountId ||
            (binding != null && currentBinding?.origin != binding.origin)
        ) {
            throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
        }
        return session.accountId
    }

    private suspend fun spaceProof(
        origin: String?,
        token: String,
        accountId: String,
        key: ByteArray,
    ): CloudProof {
        val did = accountDid?.invoke(accountId) ?: return CloudProof.INCONCLUSIVE
        val home = spaceDataHomes?.open(origin ?: return CloudProof.INCONCLUSIVE, token) ?: return CloudProof.INCONCLUSIVE
        val space = SpaceRef.personal(did)
        val keys = SealingKeys.personal(key)
        var cursor = 0L
        var matched = false
        var inconclusive = false
        repeat(MAX_PAGES) {
            val page = home.listChanges(space, cursor, PAGE_SIZE)
            page.changes.filterNot { it.deleted }.forEach { change ->
                val envelope = change.record
                if (envelope == null) {
                    inconclusive = true
                } else if (envelope.version != 1L || envelope.nonce.size != 12 || envelope.ciphertext.size < 16) {
                    inconclusive = true
                } else if (envelope.keyId != keys.keyId) {
                    return CloudProof.MISMATCH
                } else {
                    try {
                        SealedRecordCodec.open(keys, space, did, change.rkey, envelope)
                        matched = true
                    } catch (_: SealedRecordException) {
                        inconclusive = true
                    }
                }
            }
            if (!page.hasMore) {
                return when {
                    inconclusive -> CloudProof.INCONCLUSIVE
                    matched -> CloudProof.MATCH
                    else -> CloudProof.NO_SAMPLE
                }
            }
            if (page.cursor <= cursor) return CloudProof.INCONCLUSIVE
            cursor = page.cursor
        }
        return CloudProof.INCONCLUSIVE
    }

    private suspend fun cloudProof(
        token: String,
        fingerprint: String,
        key: ByteArray,
    ): CloudProof {
        val journals = pageProof(fingerprint, key) { since -> journalPage(token, since) }
        if (journals == CloudProof.MISMATCH) return journals
        val content = pageProof(fingerprint, key) { since -> contentPage(token, since) }
        if (content == CloudProof.MISMATCH) return content
        val drafts = pageProof(fingerprint, key) { since -> draftPage(token, since) }
        if (drafts == CloudProof.MISMATCH) return drafts
        return when {
            listOf(journals, content, drafts).contains(CloudProof.INCONCLUSIVE) -> CloudProof.INCONCLUSIVE
            listOf(journals, content, drafts).contains(CloudProof.MATCH) -> CloudProof.MATCH
            else -> CloudProof.NO_SAMPLE
        }
    }

    private suspend fun journalPage(
        token: String,
        since: Long,
    ): Page =
        cloudApiClient.getJournalChanges(token, since, PAGE_SIZE).getOrThrow().let { page ->
            Page(
                page.changes.flatMap {
                    listOf(
                        Sample("sync:journal:${it.id}:title", it.title),
                        Sample("sync:journal:${it.id}:description", it.description),
                    )
                },
                page.lastTimestamp,
                page.hasMore,
            )
        }

    private suspend fun contentPage(
        token: String,
        since: Long,
    ): Page =
        cloudApiClient.getContentChanges(token, since, PAGE_SIZE).getOrThrow().let { page ->
            Page(
                page.changes.flatMap {
                    listOfNotNull(
                        it.content?.let { value -> Sample("sync:note:${it.id}:text", value) },
                        it.caption?.let { value -> Sample("sync:note:${it.id}:caption", value) },
                        it.location?.let { value -> Sample("sync:note:${it.id}:location", value) },
                        it.transcript?.let { value -> Sample("sync:note:${it.id.lowercase()}:transcript", value) },
                        it.capturedOn?.let { value -> Sample("sync:note:${it.id}:capturedOn", value) },
                        it.capturedDuring?.let { value -> Sample("sync:note:${it.id}:capturedDuring", value) },
                    )
                },
                page.lastTimestamp,
                page.hasMore,
            )
        }

    private suspend fun draftPage(
        token: String,
        since: Long,
    ): Page =
        cloudApiClient.getDraftChanges(token, since, PAGE_SIZE).getOrThrow().let { page ->
            Page(
                page.drafts.flatMap {
                    listOfNotNull(
                        it.encryptedBlocks?.let { value ->
                            Sample("sync:draft:${it.id}:blocks", value)
                        },
                        Sample("sync:draft:${it.id}:content", it.content),
                    )
                },
                page.lastTimestamp,
                page.hasMore,
            )
        }

    private suspend fun pageProof(
        fingerprint: String,
        key: ByteArray,
        fetch: suspend (Long) -> Page,
    ): CloudProof {
        var cursor = 0L
        var foundSample = false
        var inconclusive = false
        repeat(MAX_PAGES) {
            val page = fetch(cursor)
            page.values.forEach { sample ->
                when (sampleProof(sample, fingerprint, key)) {
                    CloudProof.MISMATCH -> return CloudProof.MISMATCH
                    CloudProof.INCONCLUSIVE -> inconclusive = true
                    CloudProof.MATCH -> foundSample = true
                    CloudProof.NO_SAMPLE -> Unit
                }
            }
            if (!page.hasMore) {
                return when {
                    inconclusive -> CloudProof.INCONCLUSIVE
                    foundSample -> CloudProof.MATCH
                    else -> CloudProof.NO_SAMPLE
                }
            }
            if (page.lastTimestamp <= cursor) throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
            cursor = page.lastTimestamp
        }
        throw RecoveryPhraseVerificationException(RecoveryPhraseVerificationReason.UNAVAILABLE)
    }

    private fun sampleProof(
        sample: Sample,
        fingerprint: String,
        key: ByteArray,
    ): CloudProof {
        val value = sample.value
        if (value.startsWith("LDSE2:")) {
            val parsed =
                try {
                    val body = Json.parseToJsonElement(value.removePrefix("LDSE2:")).jsonObject
                    val recorded = body["fp"]?.jsonPrimitive?.content ?: return CloudProof.INCONCLUSIVE
                    val envelope = Json.decodeFromJsonElement<EncryptedEnvelope>(body["env"] ?: return CloudProof.INCONCLUSIVE)
                    recorded to envelope
                } catch (_: Exception) {
                    null
                }
            val (recorded, envelope) = parsed ?: return CloudProof.INCONCLUSIVE
            if (envelope.version != 1 || envelope.algorithm != "AES-GCM") return CloudProof.INCONCLUSIVE
            return when {
                recorded.isBlank() -> CloudProof.INCONCLUSIVE
                recorded != fingerprint -> CloudProof.MISMATCH
                trialDecrypt(sample.fieldId, envelope, key) -> CloudProof.MATCH
                else -> CloudProof.INCONCLUSIVE
            }
        }
        if (value.startsWith("LDSE1:")) {
            return try {
                val envelope = Json.decodeFromString<EncryptedEnvelope>(value.removePrefix("LDSE1:"))
                if (trialDecrypt(sample.fieldId, envelope, key)) CloudProof.MATCH else CloudProof.INCONCLUSIVE
            } catch (_: Exception) {
                CloudProof.INCONCLUSIVE
            }
        }
        return if (value.startsWith("LDSE") &&
            value.substringBefore(':').drop(4).all(Char::isDigit)
        ) {
            CloudProof.INCONCLUSIVE
        } else {
            CloudProof.NO_SAMPLE
        }
    }

    private fun trialDecrypt(
        fieldId: String,
        envelope: EncryptedEnvelope,
        key: ByteArray,
    ): Boolean {
        return try {
            if (envelope.version != 1 || envelope.algorithm != "AES-GCM") return false
            val aad = "type=CONTENT|v=1|id=$fieldId".encodeToByteArray()
            if (!Base64.decode(envelope.aad).contentEquals(aad)) return false
            val iv = Base64.decode(envelope.iv)
            val ciphertext = Base64.decode(envelope.ciphertext)
            if (iv.size != 12 || ciphertext.size < 16) return false
            val derived = KeyDerivation(cryptoManager).deriveKey(key, "journal_content", fieldId)
            cryptoManager.aesGcmDecrypt(derived, iv, aad, ciphertext)
            true
        } catch (_: Exception) {
            false
        }
    }

    private data class Sample(
        val fieldId: String,
        val value: String,
    )

    private data class Page(
        val values: List<Sample>,
        val lastTimestamp: Long,
        val hasMore: Boolean,
    )

    private enum class CloudProof { MATCH, MISMATCH, INCONCLUSIVE, NO_SAMPLE }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 50
    }
}
