package app.logdate.feature.onboarding.ui

import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.domain.dayboundary.DayBoundarySettings
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.health.HealthDataAvailability
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal class FakeMemoriesSettingsRepository : MemoriesSettingsRepository {
    var contextualRecommendationsEnabled: Boolean = true
        private set
    private val _settings = MutableStateFlow(MemoriesSettings())

    override suspend fun getSettings(): MemoriesSettings = _settings.value

    override fun observeSettings(): Flow<MemoriesSettings> = _settings

    override suspend fun updateSettings(settings: MemoriesSettings) {
        _settings.value = settings
    }

    override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
        contextualRecommendationsEnabled = enabled
        _settings.value = _settings.value.copy(contextualRecommendationsEnabled = enabled)
    }

    override suspend fun setAiRecallEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(aiRecallEnabled = enabled)
    }

    override suspend fun setRecallMode(mode: RecallMode) {
        _settings.value = _settings.value.copy(recallMode = mode)
    }

    override suspend fun setWidgetContentTypes(types: Set<WidgetContentType>) {
        _settings.value = _settings.value.copy(widgetContentTypes = types)
    }
}

internal class FakeLocationTrackingSettingsRepository : LocationTrackingSettingsRepository {
    var backgroundTrackingEnabled: Boolean = false
        private set
    private val _settings = MutableStateFlow(LocationTrackingSettings())

    override suspend fun getSettings(): LocationTrackingSettings = _settings.value

    override fun observeSettings(): Flow<LocationTrackingSettings> = _settings

    override suspend fun updateSettings(settings: LocationTrackingSettings) {
        _settings.value = settings
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        backgroundTrackingEnabled = enabled
        _settings.value = _settings.value.copy(backgroundTrackingEnabled = enabled)
    }
}

internal class FakeDayBoundarySettingsRepository : DayBoundarySettingsRepository {
    var sleepBasedBoundariesEnabled: Boolean = false
        private set
    private val _settings = MutableStateFlow(DayBoundarySettings())

    override suspend fun getSettings(): DayBoundarySettings = _settings.value

    override fun observeSettings(): Flow<DayBoundarySettings> = _settings

    override suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean) {
        sleepBasedBoundariesEnabled = enabled
        _settings.value = _settings.value.copy(sleepBasedBoundariesEnabled = enabled)
    }
}

internal class FakeLocalFirstHealthRepository : LocalFirstHealthRepository {
    var isAvailable: Boolean = true
    var hasPermissions: Boolean = true

    override suspend fun getHealthDataAvailability(): HealthDataAvailability =
        if (isAvailable) {
            HealthDataAvailability.AVAILABLE
        } else {
            HealthDataAvailability.NOT_AVAILABLE
        }

    override suspend fun isHealthDataAvailable(): Boolean = isAvailable

    override suspend fun getAvailableDataTypes(): List<String> = emptyList()

    override suspend fun hasSleepPermissions(): Boolean = hasPermissions

    override suspend fun requestSleepPermissions(): Boolean = hasPermissions

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> = emptyList()

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = null

    override suspend fun getDayBoundsForDate(
        date: LocalDate,
        timeZone: TimeZone,
        sleepBasedBoundariesEnabled: Boolean,
    ): DayBounds = error("Not used in onboarding tests")
}

internal class InMemorySecureStorage : SecureStorage {
    private val storage = MutableStateFlow<Map<String, String>>(emptyMap())

    override suspend fun getString(key: String): String? = storage.value[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        storage.value = storage.value + (key to value)
    }

    override suspend fun remove(key: String) {
        storage.value = storage.value - key
    }

    override suspend fun clear() {
        storage.value = emptyMap()
    }

    override fun observeString(key: String): Flow<String?> = storage.map { values -> values[key] }

    override fun observeAll(): Flow<Map<String, String>> = storage

    override suspend fun encrypt(data: ByteArray): ByteArray = data

    override suspend fun decrypt(data: ByteArray): ByteArray? = data
}

internal class FakeCryptoManager : CryptoManager {
    override suspend fun generateRecoveryPhrase(): List<String> = (1..12).map { "word$it" }

    override suspend fun deriveMasterKey(phrase: List<String>): ByteArray {
        val phraseBytes = phrase.joinToString(" ").encodeToByteArray()
        return ByteArray(32) { index -> phraseBytes[index % phraseBytes.size] }
    }

    override fun validateRecoveryPhrase(phrase: List<String>): Boolean = phrase.size == 12 && phrase.all { it.isNotBlank() }

    override fun encryptSink(
        sink: okio.Sink,
        key: ByteArray,
        iv: ByteArray,
    ): okio.Sink = sink

    override fun decryptSource(
        source: okio.Source,
        key: ByteArray,
        iv: ByteArray,
    ): okio.Source = source

    override fun generateRandomBytes(size: Int): ByteArray = ByteArray(size) { it.toByte() }

    override fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray = ByteArray(32)

    override fun aesGcmEncrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = plaintext

    override fun aesGcmDecrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = ciphertext
}

internal class FakeStreakSettingsRepository : StreakSettingsRepository {
    private val streakEnabled = MutableStateFlow(true)
    private val cachedStreak = MutableStateFlow(0)

    override fun observeStreakEnabled(): Flow<Boolean> = streakEnabled

    override suspend fun isStreakEnabled(): Boolean = streakEnabled.value

    override suspend fun setStreakEnabled(enabled: Boolean) {
        streakEnabled.value = enabled
    }

    override fun observeCachedStreak(): Flow<Int> = cachedStreak

    override suspend fun getCachedStreak(): Int = cachedStreak.value

    override suspend fun setCachedStreak(value: Int) {
        cachedStreak.value = value
    }
}

// endregion
