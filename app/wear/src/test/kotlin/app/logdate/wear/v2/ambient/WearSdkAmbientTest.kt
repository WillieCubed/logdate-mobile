package app.logdate.wear.v2.ambient

import android.content.Context
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WearSdkAmbientTest {
    private val context = mockk<Context>(relaxed = true)
    private val wearSdkClasses: (String) -> Class<*>? = { name ->
        when (name) {
            "com.google.wear.services.ambient.AmbientManager" -> FakeAmbientManager::class.java
            "com.google.wear.Sdk" -> FakeWearSdk::class.java
            else -> null
        }
    }

    @Before
    fun resetTheFakeSdk() {
        FakeWearSdk.manager = null
        FakeWearSdk.failure = null
    }

    @Test
    fun `a watch before Wear OS 6 never uses the Wear SDK`() {
        FakeWearSdk.manager = FakeAmbientManager()

        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 35, loadClass = wearSdkClasses))
    }

    @Test
    fun `a watch without the Wear SDK classes has no ambient`() {
        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = { null }))
    }

    @Test
    fun `a Wear SDK without the ambient service has no ambient`() {
        FakeWearSdk.manager = null

        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = wearSdkClasses))
    }

    @Test
    fun `Wear OS 6 with the ambient service has ambient`() {
        FakeWearSdk.manager = FakeAmbientManager()

        assertTrue(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = wearSdkClasses))
    }

    @Test
    fun `a lookup that throws means no ambient`() {
        FakeWearSdk.failure = IllegalStateException("service crashed")

        assertFalse(WearSdkAmbient.isAvailable(context, sdkInt = 36, loadClass = wearSdkClasses))
    }
}

class FakeAmbientManager

/** Stands in for `com.google.wear.Sdk`, which only Wear OS 6 has. */
class FakeWearSdk {
    companion object {
        var manager: Any? = null
        var failure: RuntimeException? = null

        @JvmStatic
        fun getWearManager(
            context: Context,
            type: Class<*>,
        ): Any? {
            failure?.let { throw it }
            return manager
        }
    }
}
