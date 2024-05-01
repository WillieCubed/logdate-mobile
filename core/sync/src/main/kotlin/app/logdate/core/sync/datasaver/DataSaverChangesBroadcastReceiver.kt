package app.logdate.core.sync.datasaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DataSaverChangesBroadcastReceiver(
    private val dataUsageState: DataUsageState,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != null && intent.action != ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED) {
            return
        }
        if (dataUsageState.shouldUseBackgroundServices) {
            //
        } else {
        }
    }
}