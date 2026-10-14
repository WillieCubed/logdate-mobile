package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AudioShareRoutingTest {
    @Test
    fun sharedAudioFilesResolveToTheEntryEditorHost() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            for (mimeType in listOf("audio/wav", "audio/mpeg", "audio/mp4", "audio/ogg")) {
                val intent =
                    Intent(action).apply {
                        type = mimeType
                        setPackage(context.packageName)
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                val target = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                assertEquals("app.logdate.client.MainActivity", target?.activityInfo?.name, "$action $mimeType")
            }
        }
    }
}
