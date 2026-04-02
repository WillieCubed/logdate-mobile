package app.logdate.client.testing.onboarding

import android.content.Intent
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.testing.launch.ActivityLaunchTestOverrides
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@Serializable
data class OnboardingTestFixture(
    val isOnboarded: Boolean = false,
    val entryMode: OnboardingEntryMode = OnboardingEntryMode.FRESH,
    val hasPersonalIntro: Boolean = false,
    val hasBirthday: Boolean = false,
    val hasCloudAccount: Boolean = false,
    val notificationsHandledOnThisDevice: Boolean = false,
    val recommendationsEnabled: Boolean = true,
    val locationTrackingEnabled: Boolean = false,
    val sleepBasedDayBoundariesEnabled: Boolean = false,
) {
    companion object {
        val FRESH_ONBOARDING = OnboardingTestFixture()
        val ONBOARDED_HOME = OnboardingTestFixture(isOnboarded = true)
    }
}

fun Intent.putOnboardingTestFixture(fixture: OnboardingTestFixture): Intent =
    apply {
        putExtra(ONBOARDING_TEST_FIXTURE_EXTRA, ONBOARDING_TEST_FIXTURE_JSON.encodeToString(OnboardingTestFixture.serializer(), fixture))
    }

fun Intent.readOnboardingTestFixture(): OnboardingTestFixture? {
    val serialized =
        getStringExtra(ONBOARDING_TEST_FIXTURE_EXTRA)
            ?: ActivityLaunchTestOverrides.onboardingFixture?.let {
                ONBOARDING_TEST_FIXTURE_JSON.encodeToString(OnboardingTestFixture.serializer(), it)
            }
            ?: return null
    return runCatching {
        ONBOARDING_TEST_FIXTURE_JSON.decodeFromString(OnboardingTestFixture.serializer(), serialized)
    }.onFailure { error ->
        Napier.w("Ignoring invalid onboarding test fixture payload", error)
    }.getOrNull()
}

class OnboardingTestFixtureApplier(
    private val profileRepository: ProfileRepository,
    private val userStateRepository: UserStateRepository,
    private val sessionStorage: SessionStorage,
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository,
    private val dayBoundarySettingsRepository: DayBoundarySettingsRepository,
    private val onboardingDeviceStateRepository: OnboardingDeviceStateRepository,
) {
    suspend fun apply(fixture: OnboardingTestFixture) {
        profileRepository.clearProfile().getOrThrow()
        sessionStorage.clearSession()
        onboardingDeviceStateRepository.clear()
        memoriesSettingsRepository.setContextualRecommendationsEnabled(true)
        locationTrackingSettingsRepository.setBackgroundTrackingEnabled(false)
        dayBoundarySettingsRepository.setSleepBasedBoundariesEnabled(false)
        userStateRepository.setIsOnboardingComplete(false)
        onboardingDeviceStateRepository.setActiveEntryMode(fixture.entryMode)

        if (fixture.hasPersonalIntro) {
            profileRepository.updateDisplayName(DEFAULT_DISPLAY_NAME).getOrThrow()
            profileRepository.updateBio(DEFAULT_BIO, DEFAULT_BIO).getOrThrow()
        }
        if (fixture.hasBirthday) {
            userStateRepository.setBirthday(DEFAULT_BIRTHDAY)
        }
        if (fixture.hasCloudAccount) {
            sessionStorage.saveSession(
                UserSession(
                    accessToken = fixtureSessionValue("access"),
                    refreshToken = fixtureSessionValue("refresh"),
                    accountId = fixtureSessionValue("account"),
                ),
            )
        }
        if (fixture.notificationsHandledOnThisDevice) {
            onboardingDeviceStateRepository.markNotificationsHandled()
        }

        memoriesSettingsRepository.setContextualRecommendationsEnabled(fixture.recommendationsEnabled)
        locationTrackingSettingsRepository.setBackgroundTrackingEnabled(fixture.locationTrackingEnabled)
        dayBoundarySettingsRepository.setSleepBasedBoundariesEnabled(fixture.sleepBasedDayBoundariesEnabled)

        if (fixture.isOnboarded) {
            userStateRepository.setIsOnboardingComplete(true)
        }
    }

    private companion object {
        const val DEFAULT_DISPLAY_NAME = "Test User"
        const val DEFAULT_BIO = "I keep a careful log of my days."
        val DEFAULT_BIRTHDAY = Instant.parse("1991-05-17T00:00:00Z")

        fun fixtureSessionValue(kind: String): String = "fixture-session-$kind"
    }
}

const val ONBOARDING_TEST_FIXTURE_EXTRA = "app.logdate.client.testing.onboarding.FIXTURE"

private val ONBOARDING_TEST_FIXTURE_JSON =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
