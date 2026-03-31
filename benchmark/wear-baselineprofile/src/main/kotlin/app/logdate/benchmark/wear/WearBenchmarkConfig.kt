package app.logdate.benchmark.wear

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope

internal object WearBenchmarkConfig {
    const val PACKAGE_NAME = "app.logdate.wear"
    private const val MAIN_ACTIVITY = "app.logdate.wear.presentation.MainActivity"

    fun MacrobenchmarkScope.startFromLauncher() {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(PACKAGE_NAME, MAIN_ACTIVITY)
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }

    fun MacrobenchmarkScope.startQuickCapture() {
        startActivityAndWait(
            Intent(Intent.ACTION_VIEW, Uri.parse("wear://logdate/quick-capture")).apply {
                setClassName(PACKAGE_NAME, MAIN_ACTIVITY)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }
}
