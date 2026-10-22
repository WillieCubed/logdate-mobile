package studio.hypertext.atproto.repo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.identity.AtprotoDid

internal fun validateImportedCommit(
    block: RepoBlock,
    metadata: SignedRepoCommit,
    repo: AtprotoDid,
    headCid: Cid,
): JsonObject {
    require(block.cid.isCidFor(block.bytes)) { "Commit ${metadata.cid} does not hash to its CID" }
    val signed = DagCborCodec.decode(block.bytes).jsonObject
    require(signed["version"]?.jsonPrimitive?.intOrNull == REPO_COMMIT_VERSION) { "Unsupported repo commit version" }
    val decoded = requireNotNull(CarCodec.decodeSignedCommitBlock(block)) { "Invalid commit ${metadata.cid}" }
    require(
        decoded.commit.repo == repo &&
            decoded.commit.repo == metadata.commit.repo &&
            decoded.commit.root == metadata.commit.root &&
            decoded.commit.prev == metadata.commit.prev &&
            decoded.commit.revision == metadata.commit.revision,
    ) { "Commit ${metadata.cid} metadata does not match its signed block" }
    // Historical digest signers stored CID text in both the row and the block. Only old
    // non-head rows need this compatibility; the head must verify with the current signer.
    val legacySignature =
        metadata.cid != headCid &&
            Cid.parse(metadata.signature).isSuccess &&
            DagCborCodec
                .bytesOrNull(signed[COMMIT_SIGNATURE_FIELD])
                ?.contentEquals(metadata.signature.encodeToByteArray()) == true
    require(decoded.signature == metadata.signature || legacySignature) {
        "Commit ${metadata.cid} lists a signature its block does not carry"
    }
    return signed
}
