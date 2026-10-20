@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.harness

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.data.AccountDeviceApi
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.uuid.Uuid

/** Local PostgreSQL-backed registry for the host's two-emulator native transfer fixture. */
object NativeDeviceEnrollmentHarness {
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            require(args.size == 2) { "Supply a private control directory and owned loopback server port" }
            val port = args[1].toInt()
            require(port in 1..65535) { "A valid owned server port is required" }
            val databaseUrl = System.getenv("DATABASE_URL").orEmpty()
            val database = runCatching { URI(databaseUrl.removePrefix("jdbc:")) }.getOrNull()
            require(
                databaseUrl.startsWith("jdbc:postgresql://") &&
                    database?.host == "127.0.0.1" &&
                    database.port in 1..65535 &&
                    database.userInfo == null &&
                    database.rawQuery == null &&
                    database.rawFragment == null,
            ) { "Only a local PostgreSQL acceptance fixture is allowed" }
            val control = Path.of(args[0])
            Files.createDirectories(control)
            Files.setPosixFilePermissions(control, PosixFilePermissions.fromString("rwx------"))
            require(!Files.exists(control.resolve("complete"))) { "A fresh enrollment control directory is required" }
            withServerClientHarness(serverPort = port) {
                val account = apiClient.createAccountWithSyntheticPasskey("transfer_${Uuid.random().toString().take(8)}").data
                val session =
                    OriginBoundSession(
                        baseUrl.removeSuffix("/api/v1"),
                        UserSession(account.tokens.accessToken, account.tokens.refreshToken, account.account.id.toString()),
                    )
                privateFile(
                    control.resolve("fixture.json"),
                    buildJsonObject {
                        put("owner", account.account.id.toString())
                        put("origin", session.origin.replace("127.0.0.1", "10.0.2.2"))
                        put("accessToken", account.tokens.accessToken)
                        put("refreshToken", account.tokens.refreshToken)
                    }.toString(),
                )
                privateFile(control.resolve("ready"), "ready")
                try {
                    awaitRegistryChecks(control, AccountDeviceApi(httpClient), apiClient, session)
                } finally {
                    Files.deleteIfExists(control.resolve("ready"))
                }
            }
        }

    private suspend fun awaitRegistryChecks(
        control: Path,
        devices: AccountDeviceApi,
        api: CloudApiClient,
        session: OriginBoundSession,
    ) {
        val stages = listOf("source", "destination", "replay")
        val deadline = System.nanoTime() + 15L * 60 * 1_000_000_000
        while (!Files.exists(control.resolve("complete"))) {
            check(System.nanoTime() < deadline) { "Native device enrollment acceptance timed out" }
            for (stage in stages) verifyRegistryStage(control, stage, devices, api, session)
            delay(100)
        }
        check(stages.all { Files.isRegularFile(control.resolve("verified-$it.json")) }) {
            "Native enrollment cannot complete without every verified registry stage"
        }
    }

    private suspend fun verifyRegistryStage(
        control: Path,
        stage: String,
        devices: AccountDeviceApi,
        api: CloudApiClient,
        session: OriginBoundSession,
    ) {
        val request = control.resolve("expected-$stage.json")
        val response = control.resolve("verified-$stage.json")
        if (!Files.exists(request) || Files.exists(response)) return
        val expectedDocument = Json.parseToJsonElement(Files.readString(request)).jsonObject
        val expected =
            expectedDocument
                .getValue("devices")
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toSet()
        check(expected.size == if (stage == "source") 1 else 2) { "Unexpected fixture device count" }
        val listed = devices.list(session)
        check(listed.size == expected.size && listed.map { it.id }.toSet() == expected) {
            "Native device registry did not retain exactly the expected installations"
        }
        check(listed.all { it.platform == "ANDROID" }) { "Native registry contains a different platform" }
        val transferProof =
            if (stage ==
                "source"
            ) {
                null
            } else {
                verifyTransferProof(api, session, expectedDocument.getValue("proof").jsonObject)
            }
        val responseJson =
            buildJsonObject {
                put("owner", session.session.accountId)
                put(
                    "devices",
                    buildJsonArray {
                        for (device in listed.sortedBy { it.id }) {
                            add(kotlinx.serialization.json.JsonPrimitive(device.id))
                        }
                    },
                )
                put("duplicates", false)
                transferProof?.let { put("transferProof", it) }
            }
        privateFile(response, responseJson.toString())
    }

    private suspend fun verifyTransferProof(
        api: CloudApiClient,
        session: OriginBoundSession,
        expected: JsonObject,
    ): JsonObject {
        val token = session.session.accessToken
        val notes = api.getContentChanges(token, 0).getOrThrow().changes
        val historical = notes.single { it.id == fixtureId(3) }
        val offline = notes.single { it.id == fixtureId(8) }
        val draft =
            api
                .getDraftChanges(token, 0)
                .getOrThrow()
                .drafts
                .single { it.id == fixtureId(10) }
        check(
            draft.deviceId.value == expected.getValue("device").jsonPrimitive.content,
        ) { "The restored draft was attributed to the wrong device" }
        check(draft.createdAt == expected.getValue("draftCreatedAt").jsonPrimitive.long) { "The restored draft creation time changed" }
        check(
            historical.createdAt == expected.getValue("historicalCreatedAt").jsonPrimitive.long,
        ) { "The retained note creation time changed" }
        check(
            offline.createdAt == expected.getValue("offlineCreatedAt").jsonPrimitive.long,
        ) { "The replacement note creation time changed" }
        check(historical.capturedOn?.startsWith("LDSE2:") == true && historical.capturedDuring?.startsWith("LDSE2:") == true) {
            "The retained capture metadata must reach the server encrypted"
        }
        check(draft.content.startsWith("LDSE2:")) { "The retained draft must reach the server encrypted" }
        return buildJsonObject {
            put("device", draft.deviceId.value)
            put("draftCreatedAt", draft.createdAt)
            put("historicalCreatedAt", historical.createdAt)
            put("offlineCreatedAt", offline.createdAt)
            put("encryptedCaptureRetained", true)
            put("encryptedDraft", true)
        }
    }

    private fun fixtureId(number: Int): String = "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"

    private fun privateFile(
        path: Path,
        content: String,
    ) {
        require(!Files.exists(path)) { "A fresh control document is required" }
        val staged = Files.createTempFile(path.parent, ".pending-", ".json")
        try {
            Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rw-------"))
            Files.writeString(staged, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(staged)
        }
    }
}
