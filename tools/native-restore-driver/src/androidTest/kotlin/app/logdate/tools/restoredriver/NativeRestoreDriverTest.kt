package app.logdate.tools.restoredriver

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deliberately never acquires UiAutomation: inner LogDate probes own that connection. */
@RunWith(AndroidJUnit4::class)
class NativeRestoreDriverTest {
    @Test
    fun retainsManagedLifecycle1() = hold("source")

    @Test
    fun retainsManagedLifecycle2() = hold("destination")

    private fun hold(role: String) {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("nativeManagedTransport") == "true")
        check(arguments.getString("numShards") == "2") { "Both managed instances must be acquired concurrently" }
        val shard = checkNotNull(arguments.getString("shardIndex")?.toIntOrNull())
        check(shard in 0..1)
        val partitions =
            listOf("retainsManagedLifecycle1", "retainsManagedLifecycle2").map { method ->
                kotlin.math.abs(Description.createTestDescription(NativeRestoreDriverTest::class.java, method).hashCode()) % 2
            }
        assertEquals(setOf(0, 1), partitions.toSet(), "Each role must occupy a different instrumentation shard")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val nonce = checkNotNull(arguments.getString("nativeManagedNonce"))
        check(nonce.matches(Regex("[a-f0-9-]{36}")))
        val lifetime = checkNotNull(arguments.getString("nativeManagedLifetimeMillis")?.toLongOrNull())
        check(lifetime in 120_000L..(90 * 60_000L))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        requireAttestedApks()
        val directory = context.noBackupFilesDir
        val ready = File(directory, "managed-restore-$nonce.json")
        val completion = File(directory, "managed-restore-$nonce-complete.json")
        check(!ready.exists() && !completion.exists()) { "Use a new managed test generation" }
        val startTicks = File("/proc/self/stat").readText().substringAfterLast(") ").split(' ')[19]
        val receipt =
            JSONObject()
                .put("nonce", nonce)
                .put("pid", Process.myPid())
                .put("startTicks", startTicks)
                .put("role", role)
                .put("shardIndex", shard)
                .put("phase", "ready")

        fun publish(phase: String) {
            receipt.put("phase", phase)
            val temporary = File(directory, ready.name + ".part")
            temporary.outputStream().use { output ->
                output.write(receipt.toString().toByteArray())
                output.fd.sync()
            }
            check(temporary.renameTo(ready))
        }
        publish("ready")
        try {
            val deadline = SystemClock.elapsedRealtime() + lifetime
            while (!completion.exists()) {
                check(SystemClock.elapsedRealtime() < deadline) { "The host did not complete native restore acceptance" }
                Thread.sleep(200)
            }
            val result = JSONObject(completion.readText())
            assertEquals(nonce, result.getString("nonce"))
            assertEquals(startTicks, result.getString("startTicks"))
            assertTrue(result.getBoolean("accepted"), "A native restore oracle failed")
            publish("accepted")
        } catch (failure: Throwable) {
            publish("failed")
            throw failure
        }
    }
}

internal fun requireAttestedApks() {
    val arguments = InstrumentationRegistry.getArguments()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val app = "studio.hypertext.logdate.debug"
    val packages = context.packageManager
    listOf(app to "nativeAppSha256", "$app.test" to "nativeProbeSha256").forEach { (name, argument) ->
        val expected = checkNotNull(arguments.getString(argument))
        check(expected.matches(Regex("[a-f0-9]{64}")))
        val info = packages.getApplicationInfo(name, 0)
        val actual =
            File(info.sourceDir).inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            }
        assertEquals(expected, actual, "GMD must install the exact APK selected by the host")
    }
    val appSignatures = packages.getPackageInfo(app, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
    val probeSignatures = packages.getPackageInfo("$app.test", PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
    assertTrue(checkNotNull(appSignatures).apkContentsSigners.contentEquals(checkNotNull(probeSignatures).apkContentsSigners))
    assertTrue(packages.queryInstrumentation(app, 0).any { it.packageName == "$app.test" && it.targetPackage == app })
}
