package app.logdate.client.launch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.core.content.ContextCompat

internal suspend fun Context.awaitAndroidUserUnlock() {
    val application = applicationContext
    awaitUserUnlock(
        isUnlocked = { application.getSystemService(UserManager::class.java).isUserUnlocked },
        register = { onChanged ->
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (intent.action == Intent.ACTION_USER_UNLOCKED) onChanged()
                    }
                }
            ContextCompat.registerReceiver(
                application,
                receiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            AutoCloseable { application.unregisterReceiver(receiver) }
        },
    )
}
