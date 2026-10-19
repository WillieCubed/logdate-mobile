package studio.hypertext.atproto.repo

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import java.io.File
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Exports repositories from [DefaultRepoEngine] and checks them with the AT Protocol reference
 * implementation (`@atproto/repo`, run through `tools/atproto-repo-verify/verify.mjs`).
 *
 * The verifier checks every block against its CID, the strict version 3 commit shape, that the
 * revision is a TID, the signature against the account's `did:key`, the presence of every record,
 * and that the tree is the canonical one the reference implementation builds for the same entries.
 */
class ReferenceRepoVerificationTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val entries = Nsid.require("studio.hypertext.logdate.entry")
    private val journals = Nsid.require("studio.hypertext.logdate.journal")

    @Test
    fun `reference implementation accepts a P-256 signed repository with a deep tree`() =
        runBlocking {
            val signer = KeyPairCommitSigner(EcCurve.P256)
            val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)
            // Enough keys that the tree has several layers, plus updates and deletes so the
            // tree shape comes from a history of edits rather than a single build.
            repeat(300) { index ->
                engine.putRecord(record(entries, "entry-$index"), value(entries, "entry $index")).getOrThrow()
            }
            repeat(20) { index ->
                engine.putRecord(record(journals, Tid.fromLong(1_000_000L + index).value), value(journals, "journal $index")).getOrThrow()
            }
            (0 until 300 step 7).forEach { index -> engine.deleteRecord(record(entries, "entry-$index")).getOrThrow() }
            engine.putRecord(record(entries, "entry-1"), value(entries, "rewritten")).getOrThrow()

            val summary = verifyWithReference(engine, signer.didKey)

            assertEquals(300 - 43 + 20, summary.getValue("records").jsonPrimitive.int)
            assertEquals(repo.toString(), summary.getValue("did").jsonPrimitive.content)
        }

    @Test
    fun `reference implementation accepts a K-256 signed repository`() =
        runBlocking {
            val signer = KeyPairCommitSigner(EcCurve.K256)
            val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)
            engine.putRecord(record(entries, "only"), value(entries, "only")).getOrThrow()

            val summary = verifyWithReference(engine, signer.didKey)

            assertEquals(1, summary.getValue("records").jsonPrimitive.int)
        }

    @Test
    fun `reference implementation accepts an empty repository`() =
        runBlocking {
            val signer = KeyPairCommitSigner(EcCurve.P256)
            val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)
            engine.putRecord(record(entries, "gone"), value(entries, "gone")).getOrThrow()
            engine.deleteRecord(record(entries, "gone")).getOrThrow()

            val summary = verifyWithReference(engine, signer.didKey)

            assertEquals(0, summary.getValue("records").jsonPrimitive.int)
        }

    @Test
    fun `each new commit has a later revision that the reference implementation accepts`() =
        runBlocking {
            val signer = KeyPairCommitSigner(EcCurve.P256)
            val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)
            engine.putRecord(record(entries, "first"), value(entries, "first")).getOrThrow()
            val firstRev = verifyWithReference(engine, signer.didKey).getValue("rev").jsonPrimitive.content

            engine.putRecord(record(entries, "second"), value(entries, "second")).getOrThrow()
            val second = verifyWithReference(engine, signer.didKey, previousRev = firstRev)

            assertTrue(second.getValue("rev").jsonPrimitive.content > firstRev)
        }

    @Test
    fun `a repository written before this change verifies after its next commit`() =
        runBlocking {
            val blockStore = InMemoryRepoBlockStore()
            // Older commits used counter revisions, digest signatures and left out `prev`.
            val legacyRoot = MerkleSearchTree.empty().toBlockGraph()
            legacyRoot.blocks.forEach { blockStore.writeBlock(repo, it).getOrThrow() }
            val legacyBytes =
                DagCborCodec.encode(
                    buildJsonObject {
                        put("version", 3)
                        put("did", repo.toString())
                        put("data", DagCborCodec.link(legacyRoot.root))
                        put("rev", Tid.fromLong(4).value)
                        put("sig", DagCborCodec.bytes("bafyreidigestsignature".encodeToByteArray()))
                    },
                )
            val legacyCid = Cid.sha256(DAG_CBOR_CODEC, legacyBytes)
            blockStore.writeBlock(repo, RepoBlock(legacyCid, legacyBytes)).getOrThrow()
            blockStore
                .appendCommit(
                    repo,
                    SignedRepoCommit(
                        cid = legacyCid,
                        commit = RepoCommit(repo = repo, root = legacyRoot.root, revision = 4, createdAtEpochMillis = 0, recordCount = 0),
                        signature = "bafyreidigestsignature",
                    ),
                ).getOrThrow()
            blockStore.writeHead(RepoHead(repo = repo, root = legacyRoot.root, commitCid = legacyCid, revision = 4)).getOrThrow()
            val signer = KeyPairCommitSigner(EcCurve.P256)
            val engine = DefaultRepoEngine(blockStore, signer = signer)

            engine.putRecord(record(entries, "after-upgrade"), value(entries, "after")).getOrThrow()

            val summary = verifyWithReference(engine, signer.didKey, previousRev = Tid.fromLong(4).value)
            assertEquals(1, summary.getValue("records").jsonPrimitive.int)
        }

    @Test
    fun `reference implementation rejects a repository signed with a different key`() =
        runBlocking {
            val signer = KeyPairCommitSigner(EcCurve.P256)
            val otherKey = KeyPairCommitSigner(EcCurve.P256)
            val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)
            engine.putRecord(record(entries, "entry"), value(entries, "entry")).getOrThrow()

            val result = runVerifier(engine, otherKey.didKey, previousRev = null)

            assertTrue(result.exitCode != 0, "Verifier accepted a signature from the wrong key: ${result.stdout}")
            assertTrue(result.stderr.contains("Invalid signature"), result.stderr)
        }

    private suspend fun verifyWithReference(
        engine: DefaultRepoEngine,
        didKey: String,
        previousRev: String? = null,
    ) = runVerifier(engine, didKey, previousRev).let { result ->
        if (result.exitCode != 0) fail("Reference implementation rejected the export: ${result.stderr}")
        Json.parseToJsonElement(result.stdout).jsonObject
    }

    private suspend fun runVerifier(
        engine: DefaultRepoEngine,
        didKey: String,
        previousRev: String?,
    ): VerifierResult {
        val script =
            System.getProperty("atproto.referenceVerifier")
                ?: fail("atproto.referenceVerifier is not set; run this test through Gradle's jvmTest task")
        val car = File.createTempFile("repo-export", ".car")
        try {
            car.writeBytes(CarCodec.write(engine.export(repo).getOrThrow()))
            val command = listOfNotNull("node", script, car.absolutePath, repo.toString(), didKey, previousRev)
            val process =
                runCatching { ProcessBuilder(command).start() }
                    .getOrElse { error -> fail("Could not start Node.js to run the reference verifier: ${error.message}") }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            if (!process.waitFor(VERIFIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                fail("Reference verifier did not finish within $VERIFIER_TIMEOUT_SECONDS seconds")
            }
            return VerifierResult(process.exitValue(), stdout.trim(), stderr.trim())
        } finally {
            car.delete()
        }
    }

    private fun record(
        collection: Nsid,
        key: String,
    ) = RepoRecordId(repo = repo, collection = collection, recordKey = RecordKey.require(key))

    private fun value(
        collection: Nsid,
        text: String,
    ) = buildJsonObject {
        put("\$type", collection.toString())
        put("text", text)
    }

    private data class VerifierResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    /** Signs commits with a freshly generated key, the way a hosted account's key signs them. */
    private class KeyPairCommitSigner(
        private val curve: EcCurve,
    ) : RepoCommitSigner {
        private val keyPair = EcKeySupport.generateKeyPair(curve)

        val didKey: String = "did:key:" + EcKeySupport.encodePublicKeyMultibase(keyPair.public as ECPublicKey, curve)

        override suspend fun sign(
            commit: RepoCommit,
            payload: ByteArray,
        ): String = base64Url.encodeToString(EcKeySupport.signSha256(keyPair.private, curve, payload))

        override suspend fun verify(
            commit: RepoCommit,
            payload: ByteArray,
            signature: String,
        ): Boolean = EcKeySupport.verifySha256(keyPair.public, curve, payload, Base64.getUrlDecoder().decode(signature))

        private companion object {
            private val base64Url = Base64.getUrlEncoder().withoutPadding()
        }
    }

    private companion object {
        private const val VERIFIER_TIMEOUT_SECONDS = 60L
    }
}
