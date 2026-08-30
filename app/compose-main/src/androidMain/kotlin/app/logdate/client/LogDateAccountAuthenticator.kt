package app.logdate.client

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Registers LogDate as the owner of its account type.
 *
 * [android.accounts.AccountManager.addAccountExplicitly] only accepts accounts whose type is
 * declared by an authenticator belonging to the calling package. Without this declaration every
 * call threw `SecurityException: cannot explicitly add accounts of type: app.logdate.account`,
 * so the account was never recorded with the platform.
 *
 * LogDate authenticates with passkeys inside the app, not through the system account dialogs, so
 * this authenticator deliberately owns the type without offering a credential flow: it exists to
 * make the account visible to the platform and to keep its auth tokens in the system store. Any
 * entry point that would otherwise prompt returns the user to the app instead.
 */
class LogDateAccountAuthenticator(
    private val context: Context,
) : AbstractAccountAuthenticator(context) {
    /**
     * Sends the user into the app rather than a system-drawn form. Account creation requires the
     * passkey ceremony, which only the app can run.
     */
    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle =
        Bundle().apply {
            putParcelable(AccountManager.KEY_INTENT, launchAppIntent())
        }

    /** Same reasoning as [addAccount]: re-authentication happens in the app. */
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle =
        Bundle().apply {
            putParcelable(AccountManager.KEY_INTENT, launchAppIntent())
        }

    override fun updateCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle =
        Bundle().apply {
            putParcelable(AccountManager.KEY_INTENT, launchAppIntent())
        }

    override fun editProperties(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
    ): Bundle = Bundle()

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        options: Bundle?,
    ): Bundle = Bundle()

    override fun getAuthTokenLabel(authTokenType: String?): String = authTokenType.orEmpty()

    /** No feature gating: the single LogDate account type either exists or it does not. */
    override fun hasFeatures(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        features: Array<out String>?,
    ): Bundle =
        Bundle().apply {
            putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)
        }

    private fun launchAppIntent(): Intent? =
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}

/**
 * Binds [LogDateAccountAuthenticator] for the platform to discover via its
 * `android.accounts.AccountAuthenticator` intent filter.
 */
class LogDateAccountAuthenticatorService : Service() {
    private val authenticator by lazy { LogDateAccountAuthenticator(this) }

    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder
}
