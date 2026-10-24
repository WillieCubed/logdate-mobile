package app.logdate.client.sync

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.sync.cloud.ContentChangesResponse
import app.logdate.client.sync.cloud.InMemorySecureStorage
import app.logdate.client.sync.cloud.JournalChangesResponse
import app.logdate.client.sync.cloud.TestCryptoManager
import app.logdate.client.sync.space.FakeDataHome
import app.logdate.client.sync.space.SpaceDataHomes
import app.logdate.client.sync.space.SpaceRoute
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.lexicon.app.logdate.crypto.SealedPayload
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.JournalChange
import app.logdate.space.SealedRecordCodec
import app.logdate.space.SealingKeys
import app.logdate.space.SpaceRef
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecoveryPhraseAccountProofTest {
    private val words = List(12) { "word-$it" }
    private val otherWords = List(12) { "other-$it" }
    private val crypto = TestCryptoManager()

    @Test
    fun `a signed-in account's encrypted journal proves its recovery phrase`() =
        runTest {
            val proof = proof(journalCiphertext(words))
            assertEquals("test-account-id", proof.verify(words))
        }

    @Test
    fun `a Space-only journal proves its phrase through the authenticated sealed feed`() =
        runTest {
            val did = "did:plc:recoveryproof"
            val space = SpaceRef.personal(did)
            val home = FakeDataHome()
            val keys = SealingKeys.personal(crypto.deriveMasterKey(words))
            val sealed =
                SealedRecordCodec.seal(
                    keys,
                    space,
                    did,
                    SealedPayload("app.logdate.journal.journal", buildJsonObject { put("title", "Journal") }, "journal-0"),
                )
            home.signedIn("test-access-token").putRecord(space, sealed.rkey, sealed.record)
            val verifier = spaceProof(home, did)
            assertEquals("test-account-id", verifier.verify(words))
            assertEquals(
                RecoveryPhraseVerificationReason.MISMATCH,
                assertFailsWith<RecoveryPhraseVerificationException> { verifier.verify(otherWords) }.reason,
            )
        }

    @Test
    fun `a Space record for a different key makes mixed proof fail closed`() =
        runTest {
            val did = "did:plc:recoveryproof"
            val space = SpaceRef.personal(did)
            val home = FakeDataHome()
            for ((phrase, rkey) in listOf(words to "journal-0", otherWords to "journal-1")) {
                val sealed =
                    SealedRecordCodec.seal(
                        SealingKeys.personal(crypto.deriveMasterKey(phrase)),
                        space,
                        did,
                        SealedPayload("app.logdate.journal.journal", buildJsonObject { put("title", "Journal") }, rkey),
                    )
                home.signedIn("test-access-token").putRecord(space, sealed.rkey, sealed.record)
            }
            assertEquals(
                RecoveryPhraseVerificationReason.MISMATCH,
                assertFailsWith<RecoveryPhraseVerificationException> { spaceProof(home, did).verify(words) }.reason,
            )
        }

    @Test
    fun `a legacy encrypted journal proves the phrase without installing it`() =
        runTest {
            val legacy = legacyJournalCiphertext(words)
            assertEquals("test-account-id", proof(legacy).verify(words))
            assertEquals(
                RecoveryPhraseVerificationReason.NO_PROOF,
                assertFailsWith<RecoveryPhraseVerificationException> { proof(legacy).verify(otherWords) }.reason,
            )
        }

    @Test
    fun `malformed encrypted journal blocks fallback to a registered recovery key`() =
        runTest {
            val candidate = proof("LDSE2:not-json")
            assertEquals(
                RecoveryPhraseVerificationReason.NO_PROOF,
                assertFailsWith<RecoveryPhraseVerificationException> { candidate.verify(words) }.reason,
            )
        }

    @Test
    fun `a fingerprint without a valid encrypted envelope is not account proof`() =
        runTest {
            val fingerprint = IdentityKeyFingerprint.of(crypto.deriveMasterKey(words), crypto)
            val candidate = proof("LDSE2:{\"fp\":\"$fingerprint\",\"env\":{}}")
            assertEquals(
                RecoveryPhraseVerificationReason.NO_PROOF,
                assertFailsWith<RecoveryPhraseVerificationException> { candidate.verify(words) }.reason,
            )
        }

    @Test
    fun `mixed encrypted journal identities do not prove either phrase`() =
        runTest {
            val proof = proof(journalCiphertext(otherWords), secondTitle = journalCiphertext(words))
            assertEquals(
                RecoveryPhraseVerificationReason.MISMATCH,
                assertFailsWith<RecoveryPhraseVerificationException> { proof.verify(words) }.reason,
            )
        }

    @Test
    fun `a matching journal cannot hide a different note identity`() =
        runTest {
            val matching = journalCiphertext(words)
            val different = journalCiphertext(otherWords)
            val api =
                fakeCloudApiClient {
                    getJournalChangesResponse =
                        Result.success(
                            JournalChangesResponse(
                                listOf(JournalChange("journal", matching, "", 1L, 1L, 1L)),
                                emptyList(),
                                1L,
                            ),
                        )
                    getContentChangesResponse =
                        Result.success(
                            ContentChangesResponse(
                                listOf(ContentChange("note", "TEXT", different, createdAt = 1L, lastUpdated = 1L, serverVersion = 1L)),
                                emptyList(),
                                1L,
                            ),
                        )
                }
            val proof = CloudRecoveryPhraseAccountProof(fakeSessionStorage(), crypto, api)
            assertEquals(
                RecoveryPhraseVerificationReason.MISMATCH,
                assertFailsWith<RecoveryPhraseVerificationException> { proof.verify(words) }.reason,
            )
        }

    @Test
    fun `a different valid phrase cannot pass an account's encrypted journal`() =
        runTest {
            val proof = proof(journalCiphertext(words))
            assertEquals(
                RecoveryPhraseVerificationReason.MISMATCH,
                assertFailsWith<RecoveryPhraseVerificationException> { proof.verify(otherWords) }.reason,
            )
        }

    @Test
    fun `a separately registered PLC recovery phrase cannot authorize an unproven journal root`() =
        runTest {
            assertEquals(
                RecoveryPhraseVerificationReason.NO_PROOF,
                assertFailsWith<RecoveryPhraseVerificationException> { proof(null).verify(words) }.reason,
            )
            assertEquals(
                RecoveryPhraseVerificationReason.NO_PROOF,
                assertFailsWith<RecoveryPhraseVerificationException> {
                    spaceProof(FakeDataHome(), "did:plc:recoveryproof").verify(words)
                }.reason,
            )
        }

    @Test
    fun `a cloud failure never turns lack of proof into success`() =
        runTest {
            val api = fakeCloudApiClient { getJournalChangesResponse = Result.failure(IllegalStateException("offline")) }
            val proof = CloudRecoveryPhraseAccountProof(fakeSessionStorage(), crypto, api)
            assertEquals(
                RecoveryPhraseVerificationReason.UNAVAILABLE,
                assertFailsWith<RecoveryPhraseVerificationException> { proof.verify(words) }.reason,
            )
        }

    @Test
    fun `an unavailable account storage route is a retryable verification failure`() =
        runTest {
            val verifier =
                CloudRecoveryPhraseAccountProof(
                    fakeSessionStorage(),
                    crypto,
                    fakeCloudApiClient(),
                    spaceRoute = { SpaceRoute.Blocked("storage unavailable") },
                )
            assertEquals(
                RecoveryPhraseVerificationReason.UNAVAILABLE,
                assertFailsWith<RecoveryPhraseVerificationException> { verifier.verify(words) }.reason,
            )
        }

    private suspend fun journalCiphertext(phrase: List<String>): String {
        val fingerprint = IdentityKeyFingerprint.of(crypto.deriveMasterKey(phrase), crypto)
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        identity.recoverIdentity(phrase)
        val service = ContentEncryptionService(identity, KeyDerivation(crypto), crypto)
        val envelope = service.encryptContent("sync:journal:journal-0:title", "journal")
        return "LDSE2:{\"fp\":\"$fingerprint\",\"env\":${Json.encodeToString(envelope)}}"
    }

    private suspend fun legacyJournalCiphertext(phrase: List<String>): String {
        val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
        identity.recoverIdentity(phrase)
        val service = ContentEncryptionService(identity, KeyDerivation(crypto), crypto)
        return "LDSE1:" + Json.encodeToString(service.encryptContent("sync:journal:journal-0:title", "legacy journal"))
    }

    private fun proof(
        journalTitle: String?,
        secondTitle: String? = null,
    ): CloudRecoveryPhraseAccountProof {
        val api =
            fakeCloudApiClient {
                getJournalChangesResponse =
                    Result.success(
                        JournalChangesResponse(
                            changes =
                                listOfNotNull(journalTitle, secondTitle).mapIndexed { index, title ->
                                    JournalChange("journal-$index", title, "", 1L, 1L, 1L)
                                },
                            deletions = emptyList(),
                            lastTimestamp = 1L,
                        ),
                    )
            }
        return CloudRecoveryPhraseAccountProof(fakeSessionStorage(), crypto, api)
    }

    private fun spaceProof(
        home: FakeDataHome,
        did: String,
    ) = CloudRecoveryPhraseAccountProof(
        fakeSessionStorage(),
        crypto,
        fakeCloudApiClient(),
        spaceRoute = { SpaceRoute.Space },
        spaceDataHomes = SpaceDataHomes { _, token -> home.signedIn(token) },
        accountDid = { accountId -> did.takeIf { accountId == "test-account-id" } },
    )
}
