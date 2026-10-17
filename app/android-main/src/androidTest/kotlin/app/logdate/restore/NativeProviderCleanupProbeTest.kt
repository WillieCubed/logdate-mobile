package app.logdate.restore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.permissions.AndroidRestoreCredentialManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in SDK smoke probe for a dedicated synthetic emulator, without a seeded Google key. */
@RunWith(AndroidJUnit4::class)
class NativeProviderCleanupProbeTest {
    @Test
    fun emptyProviderCleanupIsIdempotent() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("nativeProviderCleanup") == "true")
            val context = ApplicationProvider.getApplicationContext<Context>()
            val provider = AndroidRestoreCredentialManager(context)
            withTimeout(30_000) {
                provider.clearRestoreCredential().getOrThrow()
                provider.clearRestoreCredential().getOrThrow()
            }
        }
}
