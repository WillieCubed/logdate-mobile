package app.logdate.benchmark.phone

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope

internal object PhoneBenchmarkConfig {
    const val PACKAGE_NAME = "co.reasonabletech.logdate"
    private const val MAIN_ACTIVITY = "app.logdate.client.MainActivity"

    fun MacrobenchmarkScope.startFromLauncher() {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(PACKAGE_NAME, MAIN_ACTIVITY)
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }

    fun MacrobenchmarkScope.startFromDeepLink() {
        startActivityAndWait(
            Intent(Intent.ACTION_VIEW, Uri.parse("logdate://rewind")).apply {
                setPackage(PACKAGE_NAME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }
}
