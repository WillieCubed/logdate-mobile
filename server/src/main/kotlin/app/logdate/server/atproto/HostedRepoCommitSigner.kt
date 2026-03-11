package app.logdate.server.atproto

import app.logdate.server.auth.AccountRepository
import app.logdate.server.identity.SigningKeyService
import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import studio.hypertext.atproto.repo.DigestRepoCommitSigner
import studio.hypertext.atproto.repo.RepoCommit
import studio.hypertext.atproto.repo.RepoCommitSigner
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi

/**
 * Repo signer backed by the hosted account signing keys already provisioned for DID documents.
 */
@OptIn(ExperimentalUuidApi::class)
class HostedRepoCommitSigner(
    private val accountRepository: AccountRepository,
    private val signingKeyService: SigningKeyService,
) : RepoCommitSigner {
    override suspend fun sign(
        commit: RepoCommit,
        payload: ByteArray,
    ): String {
        val account =
            accountRepository.findByDid(commit.repo.toString())
                ?: return DigestRepoCommitSigner.sign(commit, payload)
        val activeKey = signingKeyService.ensureActiveKey(account.id)
        val privateKey = signingKeyService.decryptPrivateKey(activeKey)
        val curve =
            EcCurve.fromSigningKeyAlgorithm(activeKey.algorithm)
                ?: return DigestRepoCommitSigner.sign(commit, payload)
        val signature = EcKeySupport.signSha256(privateKey, curve, payload)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }

    override suspend fun verify(
        commit: RepoCommit,
        payload: ByteArray,
        signature: String,
    ): Boolean {
        val account =
            accountRepository.findByDid(commit.repo.toString())
                ?: return DigestRepoCommitSigner.verify(commit, payload, signature)
        val publicKeyMultibase =
            account.signingKeyPublic
                ?: return DigestRepoCommitSigner.verify(commit, payload, signature)
        val decoded = runCatching { EcKeySupport.decodePublicKey(publicKeyMultibase) }.getOrNull()
        if (decoded == null) {
            return DigestRepoCommitSigner.verify(commit, payload, signature)
        }
        return EcKeySupport.verifySha256(
            publicKey = decoded.publicKey,
            curve = decoded.curve,
            payload = payload,
            signature = Base64.getUrlDecoder().decode(signature.addBase64Padding()),
        )
    }
}

private fun String.addBase64Padding(): String = this + "=".repeat((4 - length % 4) % 4)
