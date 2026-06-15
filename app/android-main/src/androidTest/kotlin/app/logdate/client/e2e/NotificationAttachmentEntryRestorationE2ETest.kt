package app.logdate.client.e2e

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.EditorActivity
import app.logdate.client.MainActivity
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_DRAFT
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_EVENT_DETAIL
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_MEMORY_RECALL
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_NEW_ENTRY
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_DRAFT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_EVENT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_RECALL_DATE
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.location.tracking.EXTRA_NAV_SOURCE as EXTRA_LOCATION_NAV_SOURCE
import app.logdate.client.location.tracking.NAV_SOURCE_LOCATION_HISTORY
import app.logdate.client.media.audio.EXTRA_NAV_SOURCE as EXTRA_AUDIO_NAV_SOURCE
import app.logdate.client.media.audio.EXTRA_NOTE_ID
import app.logdate.client.media.audio.NAV_SOURCE_AUDIO_PLAYBACK
import app.logdate.client.resolveMainActivityNavKey
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_ID
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_TARGET
import app.logdate.client.rewind.REWIND_NOTIFICATION_TARGET_DETAIL
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import app.logdate.client.testing.onboarding.putOnboardingTestFixture
import app.logdate.feature.core.notifications.EXTRA_NAV_SOURCE as EXTRA_DATA_TRANSFER_NAV_SOURCE
import app.logdate.feature.core.notifications.NAV_SOURCE_DATA_TRANSFER
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import app.logdate.shared.model.Event
import app.logdate.shared.model.Rewind
import kotlin.uuid.Uuid

/**
 * Instrumented E2E coverage that every notification / deep-link / ambient launch destination
 * survives a foldable posture publish and an activity recreation.
 *
 * For each entry route handled by `MainActivity.resolveMainActivityNavKey` (and the incoming
 * share path), this suite:
 *  1. launches the real onboarded [MainActivity] with the route's intent,
 *  2. publishes a separating book hinge so the hinge-aware layout is active,
 *  3. asserts the activity reaches RESUMED without crashing (the destination rendered),
 *  4. recreates the activity, and
 *  5. asserts the route plus its arguments still resolve identically from the preserved intent.
 *
 * The route+argument survival is asserted through `resolveMainActivityNavKey(activity.intent)`
 * rather than private nav-stack inspection, so the contract stays checkable from the test source
 * set. The watch-sync notification is covered via its data-transfer source (the only sync entry
 * the resolver maps) — there is no distinct watch-sync nav key (see reported risks).
 */
@RunWith(AndroidJUnit4::class)
class NotificationAttachmentEntryRestorationE2ETest : KoinComponent {
    private val postureSupport = FoldablePostureTestSupport()
    private val koinRule = OnboardingKoinModuleOverrideRule(module {})
    private val eventRepository: EventRepository by inject()
    private val rewindRepository: RewindRepository by inject()

    @get:Rule
    val ruleChain: RuleChain =
        RuleChain
            .outerRule(koinRule)
            .around(postureSupport.publisherRule)

    @Test
    fun audioPlaybackNoteRoute_survivesPostureAndRecreation() {
        val noteId = Uuid.random().toString()
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_AUDIO_NAV_SOURCE, NAV_SOURCE_AUDIO_PLAYBACK)
                putExtra(EXTRA_NOTE_ID, noteId)
            },
        )
    }

    @Test
    fun dayTimelineDeepLinkRoute_survivesPostureAndRecreation() {
        assertRouteSurvivesRestoration(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://logdate.app/day/2026-06-15")),
        )
    }

    @Test
    fun locationTimelineRoute_survivesPostureAndRecreation() {
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_LOCATION_NAV_SOURCE, NAV_SOURCE_LOCATION_HISTORY)
            },
        )
    }

    @Test
    fun newEntryDraftRoute_survivesPostureAndRecreation() {
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_NEW_ENTRY)
            },
        )

        val draftId = Uuid.random().toString()
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_DRAFT)
                putExtra(EXTRA_AMBIENT_PROMPT_DRAFT_ID, draftId)
            },
        )
    }

    @Test
    fun memoryRecallDayRoute_survivesPostureAndRecreation() {
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_MEMORY_RECALL)
                putExtra(EXTRA_AMBIENT_PROMPT_RECALL_DATE, "2026-06-15")
            },
        )
    }

    @Test
    fun eventDetailRoute_survivesPostureAndRecreation() {
        val eventId = seedEvent()
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_EVENT_DETAIL)
                putExtra(EXTRA_AMBIENT_PROMPT_EVENT_ID, eventId.toString())
            },
        )
    }

    @Test
    fun rewindDetailRoute_survivesPostureAndRecreation() {
        val rewindId = seedRewind()
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_REWIND_NOTIFICATION_TARGET, REWIND_NOTIFICATION_TARGET_DETAIL)
                putExtra(EXTRA_REWIND_NOTIFICATION_ID, rewindId.toString())
            },
        )
    }

    @Test
    fun exportRestoreDataTransferRoute_survivesPostureAndRecreation() {
        assertRouteSurvivesRestoration(
            Intent().apply {
                putExtra(EXTRA_DATA_TRANSFER_NAV_SOURCE, NAV_SOURCE_DATA_TRANSFER)
            },
        )
    }

    @Test
    fun incomingShareTextLaunch_survivesPostureAndRecreation() {
        val editorIntent =
            launchIncomingShareAndCaptureEditorIntent(
                Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Shared note for restoration")
                },
            )
        assertNotNull(editorIntent)

        // The incoming-share path intentionally finishes MainActivity after opening the editor.
        // The foldable contract to preserve is the launched editor window and its intent.
        ActivityScenario.launch<EditorActivity>(editorIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            publishBookPosture(scenario)
            assertResumed(scenario)

            scenario.recreate()
            assertResumed(scenario)
        }
    }

    private fun launchIncomingShareAndCaptureEditorIntent(
        shareIntent: Intent,
        timeoutMillis: Long = 10_000,
    ): Intent? {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val launchLatch = CountDownLatch(1)
        var launchedEditorIntent: Intent? = null
        val lifecycleCallbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    if (activity is EditorActivity) {
                        launchedEditorIntent = Intent(activity.intent)
                        launchLatch.countDown()
                        activity.finish()
                    }
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)

        return try {
            ActivityScenario.launch<MainActivity>(shareIntent).use {
                launchLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
                launchedEditorIntent
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }

    private fun seedEvent(): Uuid {
        val eventId = Uuid.random()
        val now = Clock.System.now()
        val event =
            Event(
                id = eventId,
                title = "Restoration event",
                description = "Seeded for foldable restoration validation",
                startTime = now,
                endTime = now,
            )
        runBlocking {
            check(eventRepository.createEvent(event).isSuccess) { "Failed to seed event ${event.id}" }
        }
        return eventId
    }

    private fun seedRewind(): Uuid {
        val rewindId = Uuid.random()
        val now = Clock.System.now()
        runBlocking {
            rewindRepository.saveRewind(
                Rewind(
                    uid = rewindId,
                    startDate = now,
                    endDate = now,
                    generationDate = now,
                    label = "2026#24",
                    title = "Restoration rewind",
                    content = emptyList(),
                ),
            )
        }
        return rewindId
    }

    /**
     * Launches the route, drives it through a book-posture publish and an activity recreation,
     * and asserts the resolved [androidx.navigation3.runtime.NavKey] is identical before launch
     * and after the preserved intent is re-resolved post-recreation.
     */
    private fun assertRouteSurvivesRestoration(routeIntent: Intent) {
        val expectedKey = resolveMainActivityNavKey(routeIntent)
        assertNotNull(expectedKey, "Route intent must resolve to a navigation destination")

        launchOnboarded(routeIntent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            publishBookPosture(scenario)
            assertResumed(scenario)

            scenario.recreate()
            assertResumed(scenario)

            scenario.onActivity { activity ->
                assertEquals(
                    expectedKey,
                    resolveMainActivityNavKey(activity.intent),
                    "Route and arguments must survive activity recreation",
                )
            }
        }
    }

    private fun launchOnboarded(routeIntent: Intent): ActivityScenario<MainActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent =
            Intent(routeIntent).apply {
                setClass(context, MainActivity::class.java)
                putOnboardingTestFixture(OnboardingTestFixture.ONBOARDED_HOME)
            }
        return ActivityScenario.launch(launchIntent)
    }

    private fun publishBookPosture(scenario: ActivityScenario<out Activity>) {
        scenario.onActivity { activity ->
            postureSupport.publishBookPosture(activity)
        }
    }

    private fun assertResumed(scenario: ActivityScenario<out Activity>) {
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }
}
