package app.logdate.tools.passkeyprovider

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A stored test passkey. [privateKeyPkcs8] is base64url PKCS#8 - fine for an emulator-only
 * authenticator, and deliberately not something to imitate in a real provider.
 */
data class StoredCredential(
    val credentialId: String,
    val rpId: String,
    val userName: String,
    val userHandle: String,
    val privateKeyPkcs8: String,
    val signCount: Int,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("credentialId", credentialId)
            .put("rpId", rpId)
            .put("userName", userName)
            .put("userHandle", userHandle)
            .put("privateKey", privateKeyPkcs8)
            .put("signCount", signCount)

    companion object {
        fun fromJson(json: JSONObject) =
            StoredCredential(
                credentialId = json.getString("credentialId"),
                rpId = json.getString("rpId"),
                userName = json.getString("userName"),
                userHandle = json.getString("userHandle"),
                privateKeyPkcs8 = json.getString("privateKey"),
                signCount = json.optInt("signCount", 0),
            )
    }
}

class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("test-passkeys", Context.MODE_PRIVATE)

    fun all(): List<StoredCredential> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { StoredCredential.fromJson(array.getJSONObject(it)) }
    }

    fun forRpId(rpId: String): List<StoredCredential> = all().filter { it.rpId == rpId }

    fun find(credentialId: String): StoredCredential? = all().firstOrNull { it.credentialId == credentialId }

    fun save(credential: StoredCredential) {
        val remaining = all().filterNot { it.credentialId == credential.credentialId }
        val array = JSONArray()
        (remaining + credential).forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "credentials"
    }
}
