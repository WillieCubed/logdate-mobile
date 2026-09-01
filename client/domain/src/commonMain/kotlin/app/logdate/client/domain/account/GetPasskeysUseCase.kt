package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.PasskeyInfo

/**
 * Reads the account's passkeys with the detail needed to tell them apart.
 *
 * The account payload carries credential IDs and nothing else, which is not enough for someone
 * deciding which credential to revoke. This fetches the nickname, device type, and timestamps the
 * server records.
 */
class GetPasskeysUseCase(
    private val passkeyAccountRepository: PasskeyAccountRepository,
) {
    /**
     * Returns the account's passkeys, or an empty list if they cannot be read.
     *
     * A failure here is not worth interrupting the settings screen for: the list is supplementary
     * detail, and the caller still knows which credential IDs exist from the account itself.
     */
    suspend operator fun invoke(): List<PasskeyInfo> = passkeyAccountRepository.listPasskeys().getOrElse { emptyList() }
}
