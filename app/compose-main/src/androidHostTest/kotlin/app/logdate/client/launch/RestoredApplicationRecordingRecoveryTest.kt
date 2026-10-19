package app.logdate.client.launch

import android.app.Application
import app.logdate.client.ApplicationStartupCoordinator
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.media.audio.CrashSafeRecording
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class RestoredApplicationRecordingRecoveryTest {
    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun `restore retained Application salvages completed recording before database access and retry is idempotent`() =
        runBlocking {
            val folder = Files.createTempDirectory("restored-application-audio").toFile()
            val application =
                object : Application() {
                    override fun getFilesDir(): File = folder

                    override fun getApplicationContext(): android.content.Context = this
                }
            val final = File(folder, "audio_notes/synthetic-recording.m4a")
            val staged = CrashSafeRecording.backupExcludedFile(final)
            val audio = byteArrayOf(1, 2, 3, 4)
            staged.writeBytes(audio)
            assertTrue(CrashSafeRecording.markCompletedForRetry(staged))
            startKoin {
                modules(module { single<LogDateDatabase> { error("Synthetic database boundary reached") } })
            }
            try {
                val startup = ApplicationStartupCoordinator.get(application)
                assertFalse(startup.awaitReady())
                assertTrue(final.isFile)
                assertContentEquals(audio, final.readBytes())
                assertFalse(staged.exists())
                val retry = ApplicationStartupCoordinator.get(application)
                assertSame(startup, retry)
                assertFalse(retry.awaitReady())
                assertContentEquals(audio, final.readBytes())
            } finally {
                stopKoin()
                setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
                folder.deleteRecursively()
            }
        }
}
