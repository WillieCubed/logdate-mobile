package app.logdate.core.permission

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPermissionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionProvider {
    override fun hasPermission(permission: AppPermission): Boolean {
        return context.checkSelfPermission(permission.value) == PackageManager.PERMISSION_GRANTED
    }
}