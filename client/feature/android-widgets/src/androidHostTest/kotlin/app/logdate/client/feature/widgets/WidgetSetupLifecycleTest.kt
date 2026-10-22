package app.logdate.client.feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class WidgetSetupLifecycleTest {
    @Before
    fun configureMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun restoreMain() = Dispatchers.resetMain()

    @Test
    fun `restored pending configuration is retained without writing before authorization`() {
        val request = WidgetSetupRequest.photoPrompt(7, PhotoWidgetShape.CIRCLE)
        val intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7)
        val controller = Robolectric.buildActivity(WidgetSetupLifecycleFixture::class.java, intent)
        try {
            val activity =
                controller
                    .create(retained(request))
                    .start()
                    .resume()
                    .get()
            assertEquals(WidgetSetupSaveState.Saving, activity.widgetSetupSaveState.value)
            val manager = AppWidgetManager.getInstance(activity)
            shadowOf(manager).addBoundWidget(
                7,
                AppWidgetProviderInfo().apply {
                    provider = ComponentName(activity, NewEntryWidgetReceiver::class.java)
                },
            )
            assertEquals(NewEntryWidgetReceiver::class.java.name, manager.getAppWidgetInfo(7).provider.className)
            assertTrue(activity.persistenceAttempts.isEmpty())
            assertFalse(activity.isFinishing)
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val saved = Bundle()
            controller.saveInstanceState(saved)
            val restored = WidgetSetupRequest.decodeFor(saved.getString("widgetSetupPending"), 7, request.target)
            assertEquals(request, assertNotNull(restored))
            controller.start().resume()
            val repository = SecurityReads()
            activity.authorize(repository)
            assertTrue(activity.persistenceAttempts.isEmpty())
            repository.next.complete(UserData())
            assertEquals(listOf(request), activity.persistenceAttempts)
            assertTrue(activity.isFinishing)
            assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).resultCode)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `returning to retained setup keeps content hidden until fresh authorization`() {
        val controller = Robolectric.buildActivity(WidgetSetupLifecycleFixture::class.java)
        try {
            val activity =
                controller
                    .create(retained())
                    .start()
                    .resume()
                    .get()
            val repository = SecurityReads()
            activity.authorize(repository)
            repository.next.complete(UserData())
            assertEquals(View.VISIBLE, activity.content.visibility)
            controller.pause().stop()
            assertFalse(activity.isFinishing)
            assertEquals(View.INVISIBLE, activity.content.visibility)
            repository.next = CompletableDeferred()
            controller.start().resume()
            assertEquals(2, repository.reads)
            assertEquals(View.INVISIBLE, activity.content.visibility)
            repository.next.complete(UserData())
            assertEquals(View.VISIBLE, activity.content.visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `late authorization from a stopped generation cannot reveal content`() {
        val controller = Robolectric.buildActivity(WidgetSetupLifecycleFixture::class.java)
        try {
            val activity =
                controller
                    .create(retained())
                    .start()
                    .resume()
                    .get()
            val repository = SecurityReads()
            activity.authorize(repository)
            controller.pause().stop()
            repository.next.complete(UserData())
            assertEquals(View.INVISIBLE, activity.content.visibility)
            repository.next = CompletableDeferred()
            controller.start().resume()
            assertEquals(2, repository.reads)
            assertEquals(View.INVISIBLE, activity.content.visibility)
            repository.next.complete(UserData(securityLevel = AppSecurityLevel.PASSWORD))
            assertTrue(activity.isFinishing)
            assertEquals(View.INVISIBLE, activity.content.visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun retained(request: WidgetSetupRequest? = null) =
        Bundle().apply {
            putBoolean("widgetSetupRetained", true)
            request?.let { putString("widgetSetupPending", it.encode()) }
        }
}

class WidgetSetupLifecycleFixture : SecureWidgetConfigActivity() {
    internal override val widgetSetupTarget = WidgetSetupTarget.PHOTO_PROMPT
    val content: View get() = findViewById(android.R.id.content)
    internal val persistenceAttempts = mutableListOf<WidgetSetupRequest>()

    internal override suspend fun persistRequest(
        request: WidgetSetupRequest,
        canCommit: () -> Boolean,
    ): WidgetSetupSaveResult {
        check(canCommit())
        persistenceAttempts += request
        return WidgetSetupSaveResult.Saved
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content.visibility = View.INVISIBLE
    }

    fun authorize(repository: UserStateRepository) =
        lifecycleScope.launch {
            if (unlockSetup(repository)) setContentView(TextView(this@WidgetSetupLifecycleFixture).apply { text = "Private fixture" })
        }
}

private class SecurityReads : UserStateRepository {
    var next = CompletableDeferred<UserData>()
    var reads = 0
    override val userData =
        flow {
            reads++
            emit(next.await())
        }

    override suspend fun setBirthday(birthday: Instant) = error("Read-only fixture")

    override suspend fun setIsOnboardingComplete(isComplete: Boolean) = error("Read-only fixture")

    override suspend fun setBiometricEnabled(isEnabled: Boolean) = error("Read-only fixture")

    override suspend fun addFavoriteNote(vararg noteId: String) = error("Read-only fixture")
}
