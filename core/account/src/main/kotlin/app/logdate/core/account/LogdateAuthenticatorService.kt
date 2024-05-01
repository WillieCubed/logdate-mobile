package app.logdate.core.account

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A service that allows the Android system to access the authenticator.
 */
@AndroidEntryPoint
class LogdateAuthenticatorService @Inject constructor(
    private val authenticator: LogdateAuthenticator
) : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return authenticator.iBinder
    }
}