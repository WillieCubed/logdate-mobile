package app.logdate.client.data.restore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.PinnedLogDateConfigRepository
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.ServerProtocolFeature
import kotlinx.serialization.json.Json
import java.net.URI

/** Rejects incomplete or cross-origin restored configuration before contacting any server. */
internal fun nativeRestoreConfiguration(preferences: Preferences): PinnedLogDateConfigRepository? =
    runCatching {
        val origin = preferences[stringPreferencesKey("backend_url")] ?: DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
        val parsed = URI(origin)
        require(parsed.scheme == "https" && !parsed.host.isNullOrBlank())
        require(parsed.rawUserInfo == null && parsed.rawQuery == null && parsed.rawFragment == null)
        require(parsed.path.isNullOrEmpty() || parsed.path == "/")
        val descriptor =
            preferences[stringPreferencesKey("server_descriptor")]?.let {
                Json { ignoreUnknownKeys = true }.decodeFromString<ServerDescriptor>(it)
            }
        val canonical = origin.trimEnd('/')
        if (descriptor != null) {
            require(descriptor.serverOrigin.trimEnd('/') == canonical)
            val api = URI(descriptor.apiBaseUrl)
            require(api.scheme == parsed.scheme && api.host == parsed.host && api.port == parsed.port)
            require(api.rawUserInfo == null && api.rawQuery == null && api.rawFragment == null)
            require(descriptor.hasCapability(ServerCapability.AUTH_PASSKEY))
            require(descriptor.hasProtocolFeature(ServerProtocolFeature.CANONICAL_OWNER_BINDING_V1))
        } else {
            require(canonical == DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL)
        }
        val version = preferences[stringPreferencesKey("api_version")] ?: DefaultLogDateConfigRepository.DEFAULT_API_VERSION
        require(version.matches(Regex("[A-Za-z0-9_-]+")))
        PinnedLogDateConfigRepository(canonical, descriptor, version)
    }.getOrNull()
