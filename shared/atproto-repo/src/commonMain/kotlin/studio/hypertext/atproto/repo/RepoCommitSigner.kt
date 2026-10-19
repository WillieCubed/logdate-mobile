package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import studio.hypertext.atproto.syntax.Tid
import kotlin.io.encoding.Base64

/**
 * Commit signer used by the standalone repo runtime.
 *
 * The AT Protocol signs a commit by hashing the DAG-CBOR bytes of the unsigned commit with SHA-256
 * and signing that hash with the account's current signing key. When the key cannot be found,
 * [sign] must throw rather than return something that is not a signature.
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
 *
 * It stores a hash of the commit where the signature belongs. That is enough for a repository
 * that never leaves this process, such as one in a test, but it is not a signature: relays and
 * other AT Protocol services reject these commits. A repository that other services will read
 * must use a signer backed by the account's signing key.
 *
 * The value is the unpadded base64url form of the SHA-256 digest of the unsigned commit, so the
 * commit's `sig` field holds those 32 bytes. Versions before 2026-10 returned the digest as a CID
 * string instead; commits written that way do not verify with this signer, and an import only
 * checks the head commit, which every new write replaces.
 */
public object DigestRepoCommitSigner : RepoCommitSigner {
    override suspend fun sign(
        commit: RepoCommit,
        payload: ByteArray,
    ): String = signatureBase64.encode(payload.toByteString().sha256().toByteArray())

    override suspend fun verify(
        commit: RepoCommit,
        payload: ByteArray,
        signature: String,
    ): Boolean = sign(commit, payload) == signature
}

/**
 * The unsigned commit object as the repository specification defines it: exactly `version`,
 * `did`, `data`, `rev` and `prev`. `prev` has to be present even when it is null; the reference
 * implementation rejects a version 3 commit without it.
 */
private fun unsignedCommitObject(commit: RepoCommit): JsonObject =
    buildJsonObject {
        put("version", REPO_COMMIT_VERSION)
        put("did", commit.repo.toString())
        put("data", DagCborCodec.link(commit.root))
        put("rev", Tid.fromLong(commit.revision).toString())
        put("prev", commit.prev?.let(DagCborCodec::link) ?: JsonNull)
    }

/** The bytes a signer signs: the DAG-CBOR encoding of the commit without its signature. */
internal fun encodeCommitPayload(commit: RepoCommit): ByteArray = DagCborCodec.encode(unsignedCommitObject(commit))

internal fun encodeSignedCommitPayload(
    commit: RepoCommit,
    signature: String,
): ByteArray =
    DagCborCodec.encode(
        JsonObject(unsignedCommitObject(commit) + (COMMIT_SIGNATURE_FIELD to DagCborCodec.bytes(encodeCommitSignature(signature)))),
    )

internal const val REPO_COMMIT_VERSION: Int = 3

internal const val COMMIT_SIGNATURE_FIELD: String = "sig"

/**
 * Decodes a signer's output into the raw signature bytes stored in the commit's `sig` field.
 *
 * Signatures are always unpadded base64url text. No other reading is attempted: a base64url
 * signature can start with "b" just like a CID string, so guessing at the format would store some
 * real signatures as text that no verifier accepts.
 */
internal fun encodeCommitSignature(signature: String): ByteArray =
    runCatching { signatureBase64.decode(signature) }
        .getOrElse { cause -> throw IllegalArgumentException("Commit signatures must be base64url text, got \"$signature\"", cause) }

internal fun decodeCommitSignature(element: JsonElement): String {
    val signatureBytes = DagCborCodec.bytesOrNull(element) ?: error("Commit signature must be bytes")
    return signatureBase64.encode(signatureBytes)
}

private val signatureBase64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
