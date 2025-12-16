package app.logdate.client.permissions

import android.content.Context
import org.koin.java.KoinJavaComponent.get

/**
 * Creates an Android-specific permission manager
 */
actual fun createPermissionManager(): PermissionManager {
    // Get Android context from Koin
    val context: Context = get(Context::class.java)
    return AndroidPermissionManager(context)
}