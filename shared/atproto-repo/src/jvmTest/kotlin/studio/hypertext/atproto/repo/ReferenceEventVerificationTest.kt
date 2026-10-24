package studio.hypertext.atproto.repo

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import java.io.File
import java.nio.file.Files
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hands the CAR file of a stream commit to the AT Protocol reference implementation
 * (`@atproto/repo`, run through `tools/atproto-repo-verify/verify-event.mjs`) and checks what a
 * relay checks: the signature, that the blocks reach every change, that the changes are the event's
 * operations, and that undoing the operations gives back the previous tree.
 */
class ReferenceEventVerificationTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val profile = Nsid.require("app.logdate.actor.profile")
    private val documents = Nsid.require("site.standard.document")

    @Test
    fun `a first commit verifies with no previous tree`() =
        runBlocking<Unit> {
            val (signer, store, engine) = newRepo()
            engine.putRecord(record(profile, "self"), value(profile, "hello")).getOrThrow()

            val summary = verifyLastCommit(store, signer, previousCar = null).getOrFail()

            assertEquals(1, summary.getValue("ops").jsonPrimitive.int)
        }

    @Test
    fun `an update after earlier commits verifies against the previous tree`() =
        runBlocking<Unit> {
            val (signer, store, engine) = newRepo()
            repeat(40) { index -> engine.putRecord(record(documents, docKey(index)), value(documents, "story $index")).getOrThrow() }
            engine.putRecord(record(profile, "self"), value(profile, "one")).getOrThrow()
            val before = CarCodec.write(engine.export(repo).getOrThrow())

            engine.putRecord(record(profile, "self"), value(profile, "two")).getOrThrow()

            verifyLastCommit(store, signer, previousCar = before).getOrFail()
        }

    @Test
    fun `a deletion verifies because the whole tree travels with the commit`() =
        runBlocking<Unit> {
            val (signer, store, engine) = newRepo()
            repeat(60) { index -> engine.putRecord(record(documents, docKey(index)), value(documents, "story $index")).getOrThrow() }
            engine.deleteRecord(record(documents, docKey(1))).getOrThrow()
            val before = CarCodec.write(engine.export(repo).getOrThrow())

            engine.deleteRecord(record(documents, docKey(6))).getOrThrow()

            verifyLastCommit(store, signer, previousCar = before).getOrFail()
        }

    @Test
    fun `a batch of creates in a deep tree verifies`() =
        runBlocking<Unit> {
            val (signer, store, engine) = newRepo()
            repeat(200) { index -> engine.putRecord(record(documents, docKey(index)), value(documents, "story $index")).getOrThrow() }
            val before = CarCodec.write(engine.export(repo).getOrThrow())

            engine
                .putRecords(
                    (200..230).map { index -> BatchRecordWrite(record(documents, docKey(index)), value(documents, "story $index")) } +
                        BatchRecordWrite(record(profile, "self"), value(profile, "me")),
                ).getOrThrow()

            val summary = verifyLastCommit(store, signer, previousCar = before).getOrFail()
            assertEquals(32, summary.getValue("ops").jsonPrimitive.int)
        }

    @Test
    fun `a commit signed by a different key is rejected`() =
        runBlocking<Unit> {
            val (_, store, engine) = newRepo()
            engine.putRecord(record(profile, "self"), value(profile, "hello")).getOrThrow()
            val stranger = KeyPairCommitSigner(EcCurve.P256)

            val result = verifyLastCommit(store, stranger, previousCar = null)

            assertTrue(result.exitCode != 0, "the reference implementation accepted a signature from the wrong key: ${result.stdout}")
        }

    @Test
    fun `operations that disagree with the blocks are rejected`() =
        runBlocking<Unit> {
            val (signer, store, engine) = newRepo()
            engine.putRecord(record(profile, "self"), value(profile, "hello")).getOrThrow()
            val wrongCid = Cid.sha256(DAG_CBOR_CODEC, byteArrayOf(9, 9, 9))

            val result =
                verifyLastCommit(store, signer, previousCar = null) { operations ->
                    operations.map { it.copy(cid = wrongCid) }
                }

            assertTrue(result.exitCode != 0, "the reference implementation accepted an operation with the wrong record: ${result.stdout}")
        }

    @Test
    fun `a missing operation is rejected because undoing the commit no longer gives the previous tree`() =
        runBlocking<Unit> {
            val (signer, store, engine) = newRepo()
            engine.putRecord(record(documents, "3kaaaa"), value(documents, "a")).getOrThrow()
            val before = CarCodec.write(engine.export(repo).getOrThrow())
            engine
                .putRecords(
                    listOf(
                        BatchRecordWrite(record(documents, "3kaaab"), value(documents, "b")),
                        BatchRecordWrite(record(profile, "self"), value(profile, "me")),
                    ),
                ).getOrThrow()

            val result = verifyLastCommit(store, signer, previousCar = before) { operations -> operations.drop(1) }

            assertTrue(result.exitCode != 0, "the reference implementation accepted an event that hides a change: ${result.stdout}")
        }

    private fun docKey(index: Int) = "3k" + index.toString().padStart(4, 'a')

    private suspend fun newRepo(): Triple<KeyPairCommitSigner, InMemoryRepoBlockStore, DefaultRepoEngine> {
        val signer = KeyPairCommitSigner(EcCurve.P256)
        val store = InMemoryRepoBlockStore()
        return Triple(signer, store, DefaultRepoEngine(store, signer = signer))
    }

    private suspend fun verifyLastCommit(
        store: InMemoryRepoBlockStore,
        signer: KeyPairCommitSigner,
        previousCar: ByteArray?,
        reportedOperations: (List<RepoOperation>) -> List<RepoOperation> = { it },
    ): VerifierResult {
        val commit = store.listCommits(repo).getOrThrow().last()
        val change = RepoCommitChanges.read(store, commit).getOrThrow()
        val script =
            System
                .getProperty("atproto.referenceVerifier")
                ?.let { File(it).resolveSibling("verify-event.mjs") }
                ?: fail("atproto.referenceVerifier is not set; run this test through Gradle's jvmTest task")
        val workDir = Files.createTempDirectory("repo-event").toFile()
        try {
            val eventCar = File(workDir, "event.car").apply { writeBytes(change.toCommitCar()) }
            val operations = File(workDir, "ops.json").apply { writeText(operationsJson(reportedOperations(change.operations))) }
            val previous = previousCar?.let { File(workDir, "previous.car").apply { writeBytes(it) }.absolutePath } ?: "-"
            val prevData =
                change.previous
                    ?.commit
                    ?.root
                    ?.toString() ?: "-"
            val stdout = File(workDir, "stdout.txt")
            val stderr = File(workDir, "stderr.txt")
            val command =
                listOf(
                    "node",
                    script.absolutePath,
                    previous,
                    eventCar.absolutePath,
                    operations.absolutePath,
                    repo.toString(),
                    signer.didKey,
                    prevData,
                )
            val process =
                runCatching { ProcessBuilder(command).redirectOutput(stdout).redirectError(stderr).start() }
                    .getOrElse { error -> fail("Could not start Node.js to run the reference verifier: ${error.message}") }
            if (!process.waitFor(VERIFIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor()
                fail("Reference verifier did not finish within $VERIFIER_TIMEOUT_SECONDS seconds: ${stderr.readText().trim()}")
            }
            return VerifierResult(process.exitValue(), stdout.readText().trim(), stderr.readText().trim())
        } finally {
            workDir.listFiles()?.forEach(File::delete)
            workDir.delete()
        }
    }

    private fun operationsJson(operations: List<RepoOperation>): String =
        JsonArray(
            operations.map { operation ->
                JsonObject(
                    mapOf(
                        "action" to JsonPrimitive(operation.action.wireName),
                        "path" to JsonPrimitive(operation.path),
                        "cid" to (operation.cid?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
                        "prev" to (operation.prev?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
                    ),
                )
            },
        ).toString()

    private fun VerifierResult.getOrFail(): JsonObject {
        if (exitCode != 0) fail("Reference implementation rejected the event: $stderr")
        return Json.parseToJsonElement(stdout).jsonObject
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
