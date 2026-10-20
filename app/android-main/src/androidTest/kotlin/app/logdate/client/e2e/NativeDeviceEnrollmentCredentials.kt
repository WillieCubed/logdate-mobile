package app.logdate.client.e2e

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.URI
import kotlin.uuid.Uuid

internal fun readNativeDeviceEnrollmentCredentials(context: Context): NativeDeviceEnrollmentCredentials =
    try {
        val file = File(context.noBackupFilesDir, "native-device-enrollment-fixture.json")
        require(file.isFile && file.length() in 1..32_768) { "Private enrollment fixture required" }
        val json = JSONObject(file.readText())
        val owner = json.getString("owner").also { Uuid.parse(it) }
        val origin = json.getString("origin").trimEnd('/')
        val uri = URI(origin)
        require(uri.scheme == "http" && uri.host == "10.0.2.2" && uri.port in 1..65535 && uri.userInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty().isEmpty())
        val access = json.getString("accessToken").also { require(it.isNotBlank()) }
        val refresh = json.getString("refreshToken").also { require(it.isNotBlank()) }
        val source = json.optString("sourceDevice").takeIf { it.isNotEmpty() }?.also { Uuid.parse(it) }
        val fingerprint = json.optString("sourceFingerprint").takeIf { it.isNotEmpty() }
        NativeDeviceEnrollmentCredentials(owner, origin, access, refresh, source, fingerprint, json.optJSONObject("sourceProof"))
    } catch (_: Exception) {
        error("The private enrollment fixture is missing or invalid")
    }

internal class NativeDeviceEnrollmentCredentials(
    val owner: String,
    val origin: String,
    val accessToken: String,
    val refreshToken: String,
    val sourceDevice: String?,
    val sourceFingerprint: String?,
    val sourceProof: JSONObject?,
)
