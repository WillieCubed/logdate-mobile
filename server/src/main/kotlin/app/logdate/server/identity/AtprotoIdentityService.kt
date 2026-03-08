package app.logdate.server.identity

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.identity.Service
import studio.hypertext.atproto.identity.VerificationMethod
import studio.hypertext.atproto.syntax.Handle
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Provisions and resolves AT Protocol identity data for LogDate accounts.
 */
@OptIn(ExperimentalUuidApi::class)
class AtprotoIdentityService(
    private val accountRepository: AccountRepository,
    private val signingKeyService: SigningKeyService,
    val config: AtprotoIdentityConfig,
    private val plcIdentityService: PlcIdentityService = PlcIdentityService(signingKeyService = signingKeyService, config = config),
) {
    suspend fun ensureIdentity(account: Account): Account {
        val existingHandle = account.handle?.let(::canonicalizeHandle)
        val existingDid = account.did?.let(::canonicalizeDid)
        if (existingHandle != null && existingDid != null && account.signingKeyPublic != null) {
            if (existingHandle == account.handle && existingDid == account.did) {
                return account
            }
            return accountRepository.save(account.copy(handle = existingHandle, did = existingDid))
        }

        val handle = provisionedHandleFor(account.copy(handle = existingHandle, did = existingDid))
        val did =
            when (config.hostedAccountDidMethod) {
                HostedAccountDidMethod.PLC -> plcIdentityService.provisionHostedDid(account.id, handle).did
                HostedAccountDidMethod.WEB -> didForHandle(handle).toString()
            }
        val signingKey = account.signingKeyPublic ?: signingKeyService.ensureActiveKey(account.id).publicKeyMultibase

        return accountRepository.save(
            account.copy(
                did = did,
                handle = handle,
                signingKeyPublic = signingKey,
            ),
        )
    }

    suspend fun backfillMissingIdentities() {
        accountRepository.getAllAccounts().forEach { ensureIdentity(it) }
    }

    suspend fun findByHandle(handle: String): Account? {
        val normalizedHandle = canonicalizeHandle(handle) ?: return null
        val direct = accountRepository.findByHandle(normalizedHandle)
        if (direct != null) {
            return ensureIdentity(direct)
        }

        return accountRepository
            .getAllAccounts()
            .firstNotNullOfOrNull { account ->
                val resolvedHandle =
                    if (account.handle != null || account.did != null || account.signingKeyPublic != null) {
                        account.handle?.let(::canonicalizeHandle)
                    } else {
                        provisionedHandleFor(account)
                    }
                if (resolvedHandle == normalizedHandle) {
                    ensureIdentity(account)
                } else {
                    null
                }
            }
    }

    suspend fun findByDid(did: String): Account? {
        val normalizedDid = canonicalizeDid(did) ?: return null
        val direct = accountRepository.findByDid(normalizedDid)
        if (direct != null) {
            return ensureIdentity(direct)
        }

        val handle = handleFromDid(normalizedDid) ?: return null
        return findByHandle(handle)
    }

    fun didForHandle(handle: String): AtprotoDid = AtprotoDid.require("did:web:${requireNotNull(canonicalizeHandle(handle))}")

    fun documentFor(account: Account): DidDocument {
        val did = AtprotoDid.require(requireNotNull(account.did))
        val handle = requireNotNull(account.handle)
        val signingKeyPublic = requireNotNull(account.signingKeyPublic)

        return DidDocument(
            context = listOf("https://www.w3.org/ns/did/v1", "https://w3id.org/security/multikey/v1"),
            id = did,
            alsoKnownAs = listOf("at://$handle"),
            verificationMethod =
                listOf(
                    VerificationMethod(
                        id = "$did#atproto",
                        type = "Multikey",
                        controller = did,
                        publicKeyMultibase = signingKeyPublic,
                    ),
                ),
            service =
                listOf(
                    Service(
                        id = "#atproto_pds",
                        type = "AtprotoPersonalDataServer",
                        serviceEndpoint = config.pdsServiceEndpoint,
                    ),
                ),
        )
    }

    fun serverDocument(): DidDocument =
        DidDocument(
            id = AtprotoDid.require(config.serverDid),
            service =
                listOf(
                    Service(
                        id = "#atproto_pds",
                        type = "AtprotoPersonalDataServer",
                        serviceEndpoint = config.pdsServiceEndpoint,
                    ),
                ),
        )

    fun defaultHandleFor(username: String): String = "${managedHandleLabel(username)}.${config.normalizedHandleDomain}"

    private fun handleFromDid(did: String): String? {
        if (!did.startsWith("did:web:")) {
            return null
        }
        return canonicalizeHandle(did.substringAfter("did:web:"))
    }

    private suspend fun provisionedHandleFor(account: Account): String {
        val existingHandle = account.handle?.let(::canonicalizeHandle)
        if (existingHandle != null) {
            return ensureUniqueHandle(existingHandle, account.id)
        }

        val baseLabel = managedHandleLabel(account.username)
        val baseHandle = "$baseLabel.${config.normalizedHandleDomain}"
        if (isHandleAvailableForAccount(baseHandle, account.id)) {
            return baseHandle
        }

        val suffix =
            account.id
                .toString()
                .substring(0, HANDLE_COLLISION_SUFFIX_LENGTH)
                .lowercase()
        val suffixedBase = managedHandleLabel(account.username, maxLength = MAX_HANDLE_LABEL_LENGTH - suffix.length - 1)
        return "$suffixedBase-$suffix.${config.normalizedHandleDomain}"
    }

    private suspend fun ensureUniqueHandle(
        handle: String,
        accountId: Uuid,
    ): String {
        if (isHandleAvailableForAccount(handle, accountId)) {
            return handle
        }

        val label = handle.substringBefore('.')
        val domain = handle.substringAfter('.', missingDelimiterValue = config.normalizedHandleDomain)
        val suffix = accountId.toString().substring(0, HANDLE_COLLISION_SUFFIX_LENGTH).lowercase()
        val adjustedLabel =
            if (label.length >= MAX_HANDLE_LABEL_LENGTH - suffix.length) {
                label.take(MAX_HANDLE_LABEL_LENGTH - suffix.length - 1).trim('-')
            } else {
                label
            }.ifBlank { DEFAULT_HANDLE_LABEL }
        return "$adjustedLabel-$suffix.$domain"
    }

    private suspend fun isHandleAvailableForAccount(
        handle: String,
        accountId: Uuid,
    ): Boolean = accountRepository.findByHandle(handle)?.id?.let { it == accountId } ?: true

    private fun managedHandleLabel(
        username: String,
        maxLength: Int = MAX_HANDLE_LABEL_LENGTH,
    ): String {
        val sanitized =
            username
                .trim()
                .lowercase()
                .replace(invalidLabelCharacterRegex, "-")
                .trim('-')
                .ifBlank { DEFAULT_HANDLE_LABEL }
        return sanitized.take(maxLength.coerceAtLeast(1)).trim('-').ifBlank { DEFAULT_HANDLE_LABEL }
    }

    private fun canonicalizeHandle(handle: String): String? {
        val trimmed = handle.trim().trim('.').lowercase()
        if (trimmed.isBlank()) {
            return null
        }
        return Handle.parse(trimmed).getOrNull()?.toString()
    }

    private fun canonicalizeDid(did: String): String? {
        val trimmed = did.trim().lowercase()
        if (trimmed.isBlank()) {
            return null
        }
        return AtprotoDid.parse(trimmed).getOrNull()?.toString()
    }

    private companion object {
        private const val DEFAULT_HANDLE_LABEL = "user"
        private const val MAX_HANDLE_LABEL_LENGTH = 63
        private const val HANDLE_COLLISION_SUFFIX_LENGTH = 8
        private val invalidLabelCharacterRegex = Regex("[^a-z0-9-]+")
    }
}
