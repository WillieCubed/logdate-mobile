package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.syntax.Tid
import kotlin.io.encoding.Base64

/**
 * Commit signer used by the standalone repo runtime.
 */
public interface RepoCommitSigner {
    /**
     * Signs [payload] for [commit] and returns the detached signature string.
     */
    public suspend fun sign(
        commit: RepoCommit,
        payload: ByteArray,
    ): String

    /**
     * Verifies [signature] for [commit] and [payload].
     */
    public suspend fun verify(
        commit: RepoCommit,
        payload: ByteArray,
        signature: String,
    ): Boolean
}

/**
 * Deterministic digest-based commit signer used by default.
 */
public object DigestRepoCommitSigner : RepoCommitSigner {
    override suspend fun sign(
        commit: RepoCommit,
        payload: ByteArray,
    ): String = Cid.sha256(DAG_CBOR_CODEC, payload).toString()

    override suspend fun verify(
        commit: RepoCommit,
        payload: ByteArray,
        signature: String,
    ): Boolean = sign(commit, payload) == signature
}

internal fun encodeCommitPayload(commit: RepoCommit): ByteArray =
    DagCborCodec.encode(
        buildJsonObject {
            put("version", REPO_COMMIT_VERSION)
            put("did", commit.repo.toString())
            put("data", DagCborCodec.link(commit.root))
            commit.prev?.let { put("prev", DagCborCodec.link(it)) }
            put("rev", Tid.fromLong(commit.revision).toString())
        },
    )

internal fun encodeSignedCommitPayload(
    commit: RepoCommit,
    signature: String,
): ByteArray =
    DagCborCodec.encode(
        buildJsonObject {
            put("version", REPO_COMMIT_VERSION)
            put("did", commit.repo.toString())
            put("data", DagCborCodec.link(commit.root))
            commit.prev?.let { put("prev", DagCborCodec.link(it)) }
            put("rev", Tid.fromLong(commit.revision).toString())
            put("sig", DagCborCodec.bytes(encodeCommitSignature(signature)))
        },
    )

internal const val REPO_COMMIT_VERSION: Int = 3

private fun encodeCommitSignature(signature: String): ByteArray =
    if (Cid.parse(signature).isSuccess) {
        signature.encodeToByteArray()
    } else {
        runCatching { Base64.UrlSafe.decode(signature.padBase64Url()) }.getOrElse { signature.encodeToByteArray() }
    }

internal fun decodeCommitSignature(element: JsonElement): String {
    val signatureBytes = DagCborCodec.bytesOrNull(element) ?: error("Commit signature must be bytes")
    val asciiSignature = signatureBytes.decodeToString()
    return if (Cid.parse(asciiSignature).isSuccess) {
        asciiSignature
    } else {
        Base64.UrlSafe.encode(signatureBytes).trimEnd('=')
    }
}

private fun String.padBase64Url(): String = this + "=".repeat((4 - length % 4) % 4)
