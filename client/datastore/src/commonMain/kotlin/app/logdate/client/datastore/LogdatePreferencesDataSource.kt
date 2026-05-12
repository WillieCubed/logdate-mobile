package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.shared.model.user.UserData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlin.time.Clock
import kotlin.time.Instant

private const val DEFAULT_PEOPLE_CONTACTS_ACCESS_MODE = "NONE"

/**
 * A local data source for user preferences.
 */
class LogdatePreferencesDataSource(
    private val userPreferences: DataStore<Preferences>,
) {
    // TODO: Migrate to datastore-proto
    companion object {
        val BIRTHDAY = longPreferencesKey("birthday")
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        val ONBOARDED_TIMESTAMP = longPreferencesKey("onboarded_timestamp")
        val SECURITY_LEVEL = stringPreferencesKey("security_level")
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val DAY_END_HOUR = intPreferencesKey("day_end_hour")
        val BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val SYSTEM_SEARCH_VISIBILITY_ENABLED = booleanPreferencesKey("system_search_visibility_enabled")
        val FAVORITE_NOTE_IDS = stringSetPreferencesKey("favorite_note_ids")

        // Account IDs that have already had their local data backfilled into the sync queue
        // after first sign-in. Idempotency guard so a re-sign-in into the same account doesn't
        // re-enqueue everything; signing into a different account does trigger a fresh pass.
        val BACKFILLED_ACCOUNT_IDS = stringSetPreferencesKey("backfilled_account_ids")

        // Feature flags
        val LIBRARY_ENABLED = booleanPreferencesKey("library_enabled")
        val EVENTS_ENABLED = booleanPreferencesKey("events_enabled")
        val PEOPLE_ENABLED = booleanPreferencesKey("people_enabled")
        val PEOPLE_CONTACTS_ACCESS_MODE = stringPreferencesKey("people_contacts_access_mode")

        // Journal UI keys
        val JOURNAL_LAYOUT_MODE = stringPreferencesKey("journal_layout_mode")

        // Profile keys
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PROFILE_PHOTO_URI = stringPreferencesKey("profile_photo_uri")
        val BIO = stringPreferencesKey("bio")
        val ORIGINAL_BIO = stringPreferencesKey("original_bio")
        val PROFILE_CREATED_AT = longPreferencesKey("profile_created_at")
        val PROFILE_LAST_UPDATED_AT = longPreferencesKey("profile_last_updated_at")

        // Calendar preferences
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")

        // Rewind preferences
        val REWIND_AUTO_GENERATION_ENABLED = booleanPreferencesKey("rewind_auto_generation_enabled")
        val REWIND_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rewind_notifications_enabled")
        val REWIND_REFLECTION_REPLIES_ENABLED = booleanPreferencesKey("rewind_reflection_replies_enabled")
        val HAS_SEEN_REWIND_ONBOARDING = booleanPreferencesKey("has_seen_rewind_onboarding")

        // Event inference preferences
        val EVENT_INFERENCE_SENSITIVITY = stringPreferencesKey("event_inference_sensitivity")
        val EVENT_INFERENCE_AI_NAMING_ENABLED = booleanPreferencesKey("event_inference_ai_naming_enabled")
        val EVENT_INFERENCE_LAST_RUN_AT = longPreferencesKey("event_inference_last_run_at")
        val EVENT_INFERENCE_LAST_CREATED_COUNT = intPreferencesKey("event_inference_last_created_count")
        val EVENT_INFERENCE_RECENT_CREATED_COUNT = intPreferencesKey("event_inference_recent_created_count")
        val EVENT_INFERENCE_LAST_ERROR_KIND = stringPreferencesKey("event_inference_last_error_kind")

        // Device calendar sync preferences
        val DEVICE_CALENDAR_SYNC_ENABLED = booleanPreferencesKey("device_calendar_sync_enabled")
        val DEVICE_CALENDAR_ENABLED_IDS = stringSetPreferencesKey("device_calendar_enabled_ids")
        val DEVICE_CALENDAR_SYNC_LAST_RUN_AT = longPreferencesKey("device_calendar_sync_last_run_at")
        val DEVICE_CALENDAR_SYNC_LAST_CREATED_COUNT = intPreferencesKey("device_calendar_sync_last_created_count")
        val DEVICE_CALENDAR_SYNC_LAST_UPDATED_COUNT = intPreferencesKey("device_calendar_sync_last_updated_count")
        val DEVICE_CALENDAR_SYNC_LAST_ERROR_KIND = stringPreferencesKey("device_calendar_sync_last_error_kind")

        // Android AppSearch metadata
        val ANDROID_PLATFORM_SEARCH_INDEX_GENERATION = longPreferencesKey("android_platform_search_index_generation")
        val ANDROID_PLATFORM_SEARCH_SCHEMA_VERSION = intPreferencesKey("android_platform_search_schema_version")
    }

    val userData: Flow<UserData> =
        userPreferences.data.map { prefs ->
            val birthdayMillis = prefs[BIRTHDAY] ?: 0
            Napier.d("Read birthday from preferences: $birthdayMillis")

            UserData(
                birthday = millisToInstantOrDistantPast(birthdayMillis),
                isOnboarded = prefs[IS_ONBOARDED] == true,
                onboardedDate = millisToInstantOrDistantPast(prefs[ONBOARDED_TIMESTAMP]),
                securityLevel =
                    AppSecurityLevel.valueOf(
                        prefs[SECURITY_LEVEL] ?: AppSecurityLevel.NONE.name,
                    ),
                favoriteNotes = prefs[FAVORITE_NOTE_IDS]?.toList().orEmpty(),
                // Profile data
                displayName = prefs[DISPLAY_NAME] ?: "",
                profilePhotoUri = prefs[PROFILE_PHOTO_URI],
                bio = prefs[BIO],
                originalBio = prefs[ORIGINAL_BIO],
                profileCreatedAt = millisToInstantOrDistantPast(prefs[PROFILE_CREATED_AT]),
                profileLastUpdatedAt = millisToInstantOrDistantPast(prefs[PROFILE_LAST_UPDATED_AT]),
            )
        }

    /**
     * Observes whether the Library feature is enabled.
     */
    fun observeLibraryEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[LIBRARY_ENABLED] ?: false
        }

    fun observeSystemSearchVisibilityEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[SYSTEM_SEARCH_VISIBILITY_ENABLED] ?: true
        }

    fun observeAndroidPlatformSearchIndexedGeneration(): Flow<Long?> =
        userPreferences.data.map { prefs ->
            prefs[ANDROID_PLATFORM_SEARCH_INDEX_GENERATION]
        }

    /**
     * Sets whether the Library feature is enabled.
     */
    suspend fun setLibraryEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[LIBRARY_ENABLED] = enabled
            }
        }
    }

    /**
     * Observes whether the Events feature is enabled.
     */
    fun observeEventsEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            // Default to on. Auto-events is the headline behavior of the feature; users
            // shouldn't have to opt in to discover that LogDate is noticing things for
            // them. They can still turn it off from the auto-events settings screen.
            prefs[EVENTS_ENABLED] ?: true
        }

    suspend fun isEventsEnabled(): Boolean = observeEventsEnabled().first()

    /**
     * Sets whether the Events feature is enabled.
     */
    suspend fun setEventsEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[EVENTS_ENABLED] = enabled
            }
        }
    }

    /**
     * Observes whether the People slice is enabled for this installation.
     *
     * This now defaults to on because the Android-first People experience is intended to ship as
     * a headline capability rather than a hidden lab feature.
     */
    fun observePeopleEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[PEOPLE_ENABLED] ?: true
        }

    suspend fun setPeopleEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[PEOPLE_ENABLED] = enabled
            }
        }
    }

    fun observePeopleContactsAccessMode(): Flow<String> =
        userPreferences.data.map { prefs ->
            prefs[PEOPLE_CONTACTS_ACCESS_MODE] ?: DEFAULT_PEOPLE_CONTACTS_ACCESS_MODE
        }

    suspend fun setPeopleContactsAccessMode(modeName: String) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[PEOPLE_CONTACTS_ACCESS_MODE] = modeName
            }
        }
    }

    suspend fun setSystemSearchVisibilityEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[SYSTEM_SEARCH_VISIBILITY_ENABLED] = enabled
            }
        }
    }

    suspend fun getSystemSearchVisibilityEnabled(): Boolean = observeSystemSearchVisibilityEnabled().first()

    suspend fun getAndroidPlatformSearchIndexedGeneration(): Long? =
        userPreferences.data
            .map { prefs -> prefs[ANDROID_PLATFORM_SEARCH_INDEX_GENERATION] }
            .first()

    suspend fun getAndroidPlatformSearchSchemaVersion(): Int? =
        userPreferences.data
            .map { prefs -> prefs[ANDROID_PLATFORM_SEARCH_SCHEMA_VERSION] }
            .first()

    suspend fun setAndroidPlatformSearchIndexState(
        generation: Long,
        schemaVersion: Int,
    ) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[ANDROID_PLATFORM_SEARCH_INDEX_GENERATION] = generation
                this[ANDROID_PLATFORM_SEARCH_SCHEMA_VERSION] = schemaVersion
            }
        }
    }

    /**
     * Observes the user's preferred day start hour.
     * Returns null when the user hasn't set a preference.
     */
    fun observeDayStartHour(): Flow<Int?> =
        userPreferences.data.map { prefs ->
            prefs[DAY_START_HOUR]
        }

    /**
     * Observes the persisted journal layout mode preference.
     */
    fun observeJournalLayoutMode(): Flow<String> =
        userPreferences.data.map { prefs ->
            prefs[JOURNAL_LAYOUT_MODE] ?: "CAROUSEL"
        }

    /**
     * Persists the journal layout mode preference.
     */
    suspend fun setJournalLayoutMode(modeName: String) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[JOURNAL_LAYOUT_MODE] = modeName
            }
        }
    }

    val backgroundSyncEnabled: Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[BACKGROUND_SYNC_ENABLED] ?: true
        }

    suspend fun setShouldHideOnboarding(value: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                putAll(
                    IS_ONBOARDED to value,
                    ONBOARDED_TIMESTAMP to Clock.System.now().toEpochMilliseconds(),
                )
            }
        }
    }

    suspend fun setShouldShowBiometric(value: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                putAll(
                    SECURITY_LEVEL to if (value) AppSecurityLevel.BIOMETRIC.name else AppSecurityLevel.NONE.name,
                )
            }
        }
    }

    suspend fun addFavoriteNotes(noteIds: Set<String>) {
        if (noteIds.isEmpty()) return

        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                val current = this[FAVORITE_NOTE_IDS].orEmpty()
                this[FAVORITE_NOTE_IDS] = current + noteIds
            }
        }
    }

    suspend fun setBirthdate(birthday: Instant) {
        Napier.d("Setting birthday to: $birthday")
        userPreferences.updateData { preferences ->
            val millisValue =
                if (birthday == Instant.DISTANT_PAST) {
                    0L
                } else {
                    birthday.toEpochMilliseconds()
                }
            Napier.d("Birthday in milliseconds: $millisValue")

            preferences.toMutablePreferences().apply {
                putAll(
                    BIRTHDAY to millisValue,
                )
                Napier.d("Birthday set in preferences: ${this[BIRTHDAY]}")
            }
        }
        Napier.d("Birthday update complete")
    }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[BACKGROUND_SYNC_ENABLED] = enabled
            }
        }
    }

    /**
     * Returns the set of account IDs that have already received a one-time local-data backfill
     * into the sync queue. See [BACKFILLED_ACCOUNT_IDS] for the why.
     */
    suspend fun getBackfilledAccountIds(): Set<String> = userPreferences.data.first()[BACKFILLED_ACCOUNT_IDS] ?: emptySet()

    suspend fun markAccountBackfilled(accountId: String) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                val current = this[BACKFILLED_ACCOUNT_IDS] ?: emptySet()
                this[BACKFILLED_ACCOUNT_IDS] = current + accountId
            }
        }
    }

    /**
     * Gets the user preferences
     */
    suspend fun getPreferences(): UserPreferences {
        // Using first() to get the first emission from the flow
        // This avoids collecting indefinitely
        return userPreferences.data
            .map { prefs ->
                UserPreferences(
                    dayStartHour = prefs[DAY_START_HOUR],
                    dayEndHour = prefs[DAY_END_HOUR],
                )
            }.first()
    }

    /**
     * Sets the day start and end hours
     */
    suspend fun setDayBounds(
        startHour: Int,
        endHour: Int,
    ) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                putAll(
                    DAY_START_HOUR to startHour,
                    DAY_END_HOUR to endHour,
                )
            }
        }
    }

    /**
     * Updates the user's display name
     */
    suspend fun updateDisplayName(displayName: String): Result<UserData> =
        try {
            val updatedPrefs =
                userPreferences.updateData { preferences ->
                    preferences.toMutablePreferences().apply {
                        this[DISPLAY_NAME] = displayName
                        // Set profile created timestamp if not already set
                        if (this[PROFILE_CREATED_AT] == null) {
                            this[PROFILE_CREATED_AT] = Clock.System.now().toEpochMilliseconds()
                        }
                    }
                }

            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update display name", e)
            Result.failure(e)
        }

    /**
     * Updates the user's birthday
     */
    suspend fun updateBirthday(birthday: Instant): Result<UserData> =
        try {
            setBirthdate(birthday)
            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update birthday", e)
            Result.failure(e)
        }

    /**
     * Updates the user's profile photo URI
     */
    suspend fun updateProfilePhotoUri(profilePhotoUri: String?): Result<UserData> =
        try {
            userPreferences.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    if (profilePhotoUri != null) {
                        this[PROFILE_PHOTO_URI] = profilePhotoUri
                    } else {
                        this.remove(PROFILE_PHOTO_URI)
                    }
                }
            }

            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update profile photo URI", e)
            Result.failure(e)
        }

    /**
     * Updates the user's bio and original bio
     */
    suspend fun updateBio(
        bio: String?,
        originalBio: String?,
    ): Result<UserData> =
        try {
            userPreferences.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    if (bio != null) {
                        this[BIO] = bio
                    } else {
                        this.remove(BIO)
                    }

                    if (originalBio != null) {
                        this[ORIGINAL_BIO] = originalBio
                    } else {
                        this.remove(ORIGINAL_BIO)
                    }
                }
            }

            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update bio", e)
            Result.failure(e)
        }

    /**
     * Updates the profile last updated timestamp
     */
    suspend fun updateProfileLastUpdated(timestamp: Instant) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[PROFILE_LAST_UPDATED_AT] = timestamp.toEpochMilliseconds()
            }
        }
    }

    /**
     * Gets the current user data synchronously
     */
    suspend fun getCurrentUserData(): UserData = userData.first()

    /**
     * Clears all user data
     */
    suspend fun clearUserData(): Result<Unit> =
        try {
            userPreferences.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    clear()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Failed to clear user data", e)
            Result.failure(e)
        }

    /**
     * Observes the user's preferred first day of the week.
     *
     * Returns null when no preference has been saved, allowing callers to apply
     * their own fallback (typically the device locale default).
     */
    fun observeFirstDayOfWeek(): Flow<DayOfWeek?> =
        userPreferences.data.map { prefs ->
            val stored = prefs[FIRST_DAY_OF_WEEK] ?: return@map null
            runCatching { DayOfWeek.valueOf(stored) }.getOrNull()
        }

    /**
     * Gets the user's preferred first day of the week, or null if not set.
     */
    suspend fun getFirstDayOfWeek(): DayOfWeek? = observeFirstDayOfWeek().first()

    /**
     * Sets the user's preferred first day of the week.
     */
    suspend fun setFirstDayOfWeek(dayOfWeek: DayOfWeek) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[FIRST_DAY_OF_WEEK] = dayOfWeek.name
            }
        }
    }

    /**
     * Observes whether weekly rewinds should be generated automatically in the background.
     *
     * Defaults to true so existing users keep getting weekly rewinds without opt-in.
     */
    fun observeRewindAutoGenerationEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[REWIND_AUTO_GENERATION_ENABLED] ?: true
        }

    suspend fun isRewindAutoGenerationEnabled(): Boolean = observeRewindAutoGenerationEnabled().first()

    suspend fun setRewindAutoGenerationEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[REWIND_AUTO_GENERATION_ENABLED] = enabled
            }
        }
    }

    /**
     * Observes whether the user wants a notification when a new weekly rewind is ready.
     *
     * Defaults to true. Independent of the system-level notification channel: this gates
     * whether the app even tries to post the notification, so users who turn rewinds off
     * here also stop seeing them in the notification settings flow.
     */
    fun observeRewindNotificationsEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[REWIND_NOTIFICATIONS_ENABLED] ?: true
        }

    suspend fun isRewindNotificationsEnabled(): Boolean = observeRewindNotificationsEnabled().first()

    suspend fun setRewindNotificationsEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[REWIND_NOTIFICATIONS_ENABLED] = enabled
            }
        }
    }

    /**
     * Observes whether the user wants to be able to type replies to a rewind's noticing prompts.
     *
     * Defaults to true. When false, the rewind detail screen still shows the prompts but hides
     * the reply chrome — useful for people who experience the prompts as decoration rather than
     * an invitation.
     */
    fun observeRewindReflectionRepliesEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[REWIND_REFLECTION_REPLIES_ENABLED] ?: true
        }

    suspend fun setRewindReflectionRepliesEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[REWIND_REFLECTION_REPLIES_ENABLED] = enabled
            }
        }
    }

    /**
     * Whether the user has seen the first-Rewind onboarding bottom sheet that explains
     * how the cadence, share, and curation strictness work. Defaults to false so brand-new
     * users still see it; gets set true the first time the sheet is dismissed.
     */
    fun observeHasSeenRewindOnboarding(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[HAS_SEEN_REWIND_ONBOARDING] ?: false
        }

    suspend fun hasSeenRewindOnboarding(): Boolean = observeHasSeenRewindOnboarding().first()

    suspend fun setHasSeenRewindOnboarding(value: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[HAS_SEEN_REWIND_ONBOARDING] = value
            }
        }
    }

    /**
     * Observes the user's auto-event inference sensitivity, defaulting to medium when unset.
     *
     * Persisted as the enum name (`LOW`, `MEDIUM`, `HIGH`) so we can extend the set later
     * without renumbering existing values.
     */
    fun observeEventInferenceSensitivity(): Flow<String> =
        userPreferences.data.map { prefs ->
            prefs[EVENT_INFERENCE_SENSITIVITY] ?: "MEDIUM"
        }

    suspend fun getEventInferenceSensitivity(): String = observeEventInferenceSensitivity().first()

    suspend fun setEventInferenceSensitivity(sensitivity: String) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[EVENT_INFERENCE_SENSITIVITY] = sensitivity
            }
        }
    }

    /**
     * Whether the inference worker is allowed to call out to the on-device extractor for
     * naming. Off → every event uses the heuristic fallback name, no network call.
     */
    fun observeEventInferenceAiNamingEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[EVENT_INFERENCE_AI_NAMING_ENABLED] ?: true
        }

    suspend fun isEventInferenceAiNamingEnabled(): Boolean = observeEventInferenceAiNamingEnabled().first()

    suspend fun setEventInferenceAiNamingEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[EVENT_INFERENCE_AI_NAMING_ENABLED] = enabled
            }
        }
    }

    /**
     * Observes the most recent event inference worker run summary so the settings screen can
     * show "Last run / events created / last error" without standing up a new repository.
     */
    fun observeEventInferenceStats(): Flow<EventInferenceStats> =
        userPreferences.data.map { prefs ->
            EventInferenceStats(
                lastRunAt =
                    prefs[EVENT_INFERENCE_LAST_RUN_AT]?.let { millis ->
                        if (millis == 0L) null else Instant.fromEpochMilliseconds(millis)
                    },
                lastCreatedCount = prefs[EVENT_INFERENCE_LAST_CREATED_COUNT] ?: 0,
                recentCreatedCount = prefs[EVENT_INFERENCE_RECENT_CREATED_COUNT] ?: 0,
                lastErrorKind = prefs[EVENT_INFERENCE_LAST_ERROR_KIND],
            )
        }

    /**
     * Records the result of one event inference worker run. Atomic — the recent rolling
     * total is incremented inside the same `updateData` block as the per-run stats so two
     * concurrent worker runs (immediate + periodic) can't lose increments to a stale read.
     *
     * [errorKind] is the `EventInferenceFailure` enum name (or `null` on success); the
     * settings screen translates the kind to a localized string instead of showing raw
     * exception messages.
     */
    suspend fun recordEventInferenceRun(
        runAt: Instant,
        createdThisRun: Int,
        errorKind: String?,
    ) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[EVENT_INFERENCE_LAST_RUN_AT] = runAt.toEpochMilliseconds()
                this[EVENT_INFERENCE_LAST_CREATED_COUNT] = createdThisRun
                val previousRecent = this[EVENT_INFERENCE_RECENT_CREATED_COUNT] ?: 0
                this[EVENT_INFERENCE_RECENT_CREATED_COUNT] = previousRecent + createdThisRun
                if (errorKind != null) {
                    this[EVENT_INFERENCE_LAST_ERROR_KIND] = errorKind
                } else {
                    this.remove(EVENT_INFERENCE_LAST_ERROR_KIND)
                }
            }
        }
    }

    /**
     * Whether the user has turned on device calendar sync. When `false` the import worker
     * still gets scheduled, but its first action is to short-circuit out — that keeps the
     * worker registered so flipping the toggle on takes effect on the next periodic tick
     * without re-bootstrapping WorkManager.
     */
    fun observeDeviceCalendarSyncEnabled(): Flow<Boolean> =
        userPreferences.data.map { prefs ->
            prefs[DEVICE_CALENDAR_SYNC_ENABLED] ?: false
        }

    suspend fun isDeviceCalendarSyncEnabled(): Boolean = observeDeviceCalendarSyncEnabled().first()

    suspend fun setDeviceCalendarSyncEnabled(enabled: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[DEVICE_CALENDAR_SYNC_ENABLED] = enabled
            }
        }
    }

    /**
     * The set of device calendar ids the user has opted into. Empty by default; the
     * settings overview surfaces an explicit "choose calendars" affordance when nothing's
     * been picked yet.
     */
    fun observeDeviceCalendarEnabledIds(): Flow<Set<String>> =
        userPreferences.data.map { prefs ->
            prefs[DEVICE_CALENDAR_ENABLED_IDS] ?: emptySet()
        }

    suspend fun getDeviceCalendarEnabledIds(): Set<String> = observeDeviceCalendarEnabledIds().first()

    suspend fun setDeviceCalendarEnabledIds(ids: Set<String>) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[DEVICE_CALENDAR_ENABLED_IDS] = ids
            }
        }
    }

    /**
     * Most recent device calendar import worker run. Surfaced verbatim by the calendar
     * sync settings status card; the worker writes via [recordDeviceCalendarSyncRun].
     */
    fun observeDeviceCalendarSyncStats(): Flow<DeviceCalendarSyncStats> =
        userPreferences.data.map { prefs ->
            DeviceCalendarSyncStats(
                lastRunAt =
                    prefs[DEVICE_CALENDAR_SYNC_LAST_RUN_AT]?.let { millis ->
                        if (millis == 0L) null else Instant.fromEpochMilliseconds(millis)
                    },
                lastCreatedCount = prefs[DEVICE_CALENDAR_SYNC_LAST_CREATED_COUNT] ?: 0,
                lastUpdatedCount = prefs[DEVICE_CALENDAR_SYNC_LAST_UPDATED_COUNT] ?: 0,
                lastErrorKind = prefs[DEVICE_CALENDAR_SYNC_LAST_ERROR_KIND],
            )
        }

    /**
     * Records the result of one calendar import worker run. [errorKind] is the
     * `CalendarImportFailure` enum name (or `null` on success); the settings screen
     * translates the kind to a localized string instead of showing raw exception
     * messages.
     */
    suspend fun recordDeviceCalendarSyncRun(
        runAt: Instant,
        created: Int,
        updated: Int,
        errorKind: String?,
    ) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[DEVICE_CALENDAR_SYNC_LAST_RUN_AT] = runAt.toEpochMilliseconds()
                this[DEVICE_CALENDAR_SYNC_LAST_CREATED_COUNT] = created
                this[DEVICE_CALENDAR_SYNC_LAST_UPDATED_COUNT] = updated
                if (errorKind != null) {
                    this[DEVICE_CALENDAR_SYNC_LAST_ERROR_KIND] = errorKind
                } else {
                    this.remove(DEVICE_CALENDAR_SYNC_LAST_ERROR_KIND)
                }
            }
        }
    }

    private fun millisToInstantOrDistantPast(millis: Long?): Instant {
        val value = millis ?: 0L
        return if (value == 0L) {
            Instant.DISTANT_PAST
        } else {
            Instant.fromEpochMilliseconds(value)
        }
    }
}
