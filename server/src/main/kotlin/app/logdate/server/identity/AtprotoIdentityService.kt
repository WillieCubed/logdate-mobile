package app.logdate.server.identity

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import studio.hypertext.atproto.crypto.Multikey
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.identity.Service
import studio.hypertext.atproto.identity.VerificationMethod
import studio.hypertext.atproto.pds.DescribeRepoResponse
import studio.hypertext.atproto.pds.PdsIdentityService
import studio.hypertext.atproto.pds.ResolveHandleResponse
import studio.hypertext.atproto.plc.PlcOperation
import studio.hypertext.atproto.syntax.Handle
import studio.hypertext.atproto.syntax.Nsid
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
) : PdsIdentityService {
    private var repoCollectionsResolver: suspend (String) -> List<Nsid> = { emptyList() }

    fun setRepoCollectionsResolver(resolver: suspend (String) -> List<Nsid>) {
        repoCollectionsResolver = resolver
    }

    suspend fun ensureIdentity(account: Account): Account {
        val existingHandle = account.handle?.let(::canonicalizeHandle)
        val existingDid = account.did?.let(::canonicalizeDid)
        val existingRecoveryDidKey = account.plcRecoveryDidKey?.let(::canonicalizeDidKey)
        if (existingHandle != null && existingDid != null && account.signingKeyPublic != null) {
            if (
                existingHandle == account.handle &&
                existingDid == account.did &&
                existingRecoveryDidKey == account.plcRecoveryDidKey
            ) {
                return account
            }
            return accountRepository.save(
                account.copy(
                    handle = existingHandle,
                    did = existingDid,
                    plcRecoveryDidKey = existingRecoveryDidKey,
                ),
            )
        }

        val normalizedAccount =
            account.copy(
                handle = existingHandle,
                did = existingDid,
                plcRecoveryDidKey = existingRecoveryDidKey,
            )
        val handle = provisionedHandleFor(normalizedAccount)
        val did =
            when (config.hostedAccountDidMethod) {
                HostedAccountDidMethod.PLC -> {
                    val provisionedIdentity =
                        plcIdentityService.provisionHostedDid(
                            accountId = account.id,
                            handle = handle,
                            recoveryDidKey = existingRecoveryDidKey,
                        )
                    provisionedIdentity.did
                }
                HostedAccountDidMethod.WEB -> didForHandle(handle).toString()
            }
        val signingKey = account.signingKeyPublic ?: signingKeyService.ensureActiveKey(account.id).publicKeyMultibase

        return accountRepository.save(
            normalizedAccount.copy(
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

    suspend fun rotateSigningKey(account: Account): RotatedIdentitySigningKey {
        val ensured = ensureIdentity(account)
        val did = requireNotNull(ensured.did)
        val previousPublicKey = requireNotNull(ensured.signingKeyPublic)

        return when {
            did.startsWith("did:web:") -> {
                val activeKey = signingKeyService.rotateKey(ensured.id)
                val updatedAccount = accountRepository.save(ensured.copy(signingKeyPublic = activeKey.publicKeyMultibase))
                RotatedIdentitySigningKey(
                    account = updatedAccount,
                    previousPublicKeyMultibase = previousPublicKey,
                    activeKey = activeKey,
                )
            }

            did.startsWith("did:plc:") -> {
                val rotated =
                    plcIdentityService.rotateHostedDid(
                        accountId = ensured.id,
                        did = AtprotoDid.require(did),
                        handle = requireNotNull(ensured.handle),
                        recoveryDidKey = ensured.plcRecoveryDidKey,
                    )
                val activeKey = signingKeyService.ensureActiveKey(ensured.id)
                val updatedAccount = accountRepository.save(ensured.copy(signingKeyPublic = activeKey.publicKeyMultibase))
                RotatedIdentitySigningKey(
                    account = updatedAccount,
                    previousPublicKeyMultibase = rotated.previousPublicKeyMultibase,
                    activeKey = activeKey,
                    plcOperation = rotated.operation,
                )
            }

            else -> throw IdentityLifecycleConflictException("Signing key rotation is not supported for DID $did")
        }
    }

    suspend fun importSigningKey(
        account: Account,
        exportedKey: SigningKeyService.ExportedSigningKey,
        passphrase: String,
    ): ImportedIdentitySigningKey {
        val ensured = ensureIdentity(account)
        val currentDid =
            ensured.did
                ?: throw IdentityLifecycleConflictException("Account identity must be provisioned before importing a signing key")
        val currentHandle =
            ensured.handle
                ?: throw IdentityLifecycleConflictException("Account identity must be provisioned before importing a signing key")
        val currentPublicKey =
            ensured.signingKeyPublic
                ?: throw IdentityLifecycleConflictException("Account identity must be provisioned before importing a signing key")

        val activeKey =
            when {
                exportedKey.publicKeyMultibase == currentPublicKey -> {
                    signingKeyService.importActiveKey(ensured.id, exportedKey, passphrase)
                }

                currentDid.startsWith("did:web:") -> {
                    signingKeyService.importActiveKey(ensured.id, exportedKey, passphrase)
                }

                currentDid.startsWith("did:plc:") -> {
                    if (!config.publishHostedPlcOperations) {
                        throw IdentityLifecycleConflictException("Hosted PLC recovery requires published PLC operations")
                    }
                    val preparedKey = signingKeyService.prepareImportedKey(ensured.id, exportedKey, passphrase)
                    plcIdentityService
                        .rotateHostedDid(
                            accountId = ensured.id,
                            did = AtprotoDid.require(currentDid),
                            handle = currentHandle,
                            recoveryDidKey = ensured.plcRecoveryDidKey,
                            preparedKey = preparedKey,
                        ).let { signingKeyService.ensureActiveKey(ensured.id) }
                }

                else -> {
                    throw IdentityLifecycleConflictException("Signing key import is not supported for DID $currentDid")
                }
            }
        val updatedAccount =
            accountRepository.save(
                ensured.copy(
                    did = currentDid,
                    handle = currentHandle,
                    signingKeyPublic = activeKey.publicKeyMultibase,
                ),
            )
        return ImportedIdentitySigningKey(
            account = updatedAccount,
            activeKey = activeKey,
        )
    }

    suspend fun registerPlcRecoveryKey(
        account: Account,
        recoveryDidKey: String,
    ): RegisteredPlcRecoveryKey {
        val normalizedRecoveryDidKey =
            canonicalizeDidKey(recoveryDidKey)
                ?: throw IdentityLifecycleValidationException("Recovery key must be a valid did:key multikey value")
        val ensured = ensureIdentity(account)
        val did =
            ensured.did
                ?: throw IdentityLifecycleConflictException("Account identity must be provisioned before registering a PLC recovery key")
        if (!did.startsWith("did:plc:")) {
            throw IdentityLifecycleConflictException("PLC recovery keys are only supported for hosted did:plc identities")
        }
        if (!config.publishHostedPlcOperations) {
            throw IdentityLifecycleConflictException("Hosted PLC recovery keys require published PLC operations")
        }

        val plcOperation =
            if (ensured.plcRecoveryDidKey == normalizedRecoveryDidKey) {
                null
            } else {
                val updatedRecoveryKey =
                    plcIdentityService.updateHostedRecoveryKey(
                        accountId = ensured.id,
                        did = AtprotoDid.require(did),
                        handle = requireNotNull(ensured.handle),
                        recoveryDidKey = normalizedRecoveryDidKey,
                    )
                updatedRecoveryKey.operation
            }
        val updatedAccount = accountRepository.save(ensured.copy(plcRecoveryDidKey = normalizedRecoveryDidKey))
        return RegisteredPlcRecoveryKey(
            account = updatedAccount,
            recoveryDidKey = normalizedRecoveryDidKey,
            plcOperation = plcOperation,
        )
    }

    override suspend fun resolveHandle(handle: String): Result<ResolveHandleResponse?> =
        runCatching {
            findByHandle(handle)
                ?.did
                ?.let(AtprotoDid::require)
                ?.let(::ResolveHandleResponse)
        }

    override suspend fun describeRepo(repo: String): Result<DescribeRepoResponse?> =
        runCatching {
            val account =
                when {
                    repo.startsWith("did:") -> findByDid(repo)
                    else -> findByHandle(repo)
                } ?: return@runCatching null
            DescribeRepoResponse(
                handle = requireNotNull(account.handle),
                did = AtprotoDid.require(requireNotNull(account.did)),
                didDoc = documentFor(account),
                collections = repoCollectionsResolver(requireNotNull(account.did)),
                handleIsCorrect = true,
            )
        }

    override suspend fun didDocument(did: AtprotoDid): Result<DidDocument?> =
        runCatching {
            findByDid(did.toString())?.let(::documentFor)
        }

    suspend fun identityStatus(account: Account): IdentityStatus {
        val ensured = ensureIdentity(account)
        val did = requireNotNull(ensured.did)
        val handle = requireNotNull(ensured.handle)
        val signingKeyPublic = requireNotNull(ensured.signingKeyPublic)
        val operations =
            if (did.startsWith("did:plc:")) {
                plcIdentityService.listStoredOperations(AtprotoDid.require(did))
            } else {
                emptyList()
            }

        return IdentityStatus(
            did = did,
            handle = handle,
            signingKeyPublicMultibase = signingKeyPublic,
            signingKeyDidKey = didKeyFor(signingKeyPublic),
            plcRecoveryDidKey = ensured.plcRecoveryDidKey,
            plcOperationCount = operations.size,
        )
    }

    suspend fun hostedPlcOperations(account: Account): List<StoredHostedPlcOperation> {
        val ensured = ensureIdentity(account)
        val did =
            ensured.did
                ?: throw IdentityLifecycleConflictException("Account identity must be provisioned before listing PLC operations")
        if (!did.startsWith("did:plc:")) {
            return emptyList()
        }
        return plcIdentityService.listStoredOperations(AtprotoDid.require(did))
    }

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

    private fun canonicalizeDidKey(didKey: String): String? {
        val trimmed = didKey.trim()
        if (trimmed.isBlank()) {
            return null
        }
        if (!trimmed.regionMatches(0, DID_KEY_PREFIX, 0, DID_KEY_PREFIX.length, ignoreCase = true)) {
            return null
        }
        val suffix = trimmed.substring(DID_KEY_PREFIX.length)
        if (suffix.isBlank()) {
            return null
        }
        return runCatching {
            Multikey.decode(suffix)
            "$DID_KEY_PREFIX$suffix"
        }.getOrNull()
    }

    private companion object {
        private const val DEFAULT_HANDLE_LABEL = "user"
        private const val DID_KEY_PREFIX = "did:key:"
        private const val MAX_HANDLE_LABEL_LENGTH = 63
        private const val HANDLE_COLLISION_SUFFIX_LENGTH = 8
        private val invalidLabelCharacterRegex = Regex("[^a-z0-9-]+")
    }
}

data class RotatedIdentitySigningKey(
    val account: Account,
    val previousPublicKeyMultibase: String,
    val activeKey: StoredSigningKey,
    val plcOperation: PlcOperation? = null,
)

data class ImportedIdentitySigningKey(
    val account: Account,
    val activeKey: StoredSigningKey,
)

data class RegisteredPlcRecoveryKey(
    val account: Account,
    val recoveryDidKey: String,
    val plcOperation: PlcOperation? = null,
)

data class IdentityStatus(
    val did: String,
    val handle: String,
    val signingKeyPublicMultibase: String,
    val signingKeyDidKey: String,
    val plcRecoveryDidKey: String?,
    val plcOperationCount: Int,
)
