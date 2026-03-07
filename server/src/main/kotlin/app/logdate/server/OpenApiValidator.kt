package app.logdate.server

import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val outputDirectory = Path.of(System.getProperty("logdate.openapi.outputDir") ?: "build/openapi").toAbsolutePath()
    val jsonFile = outputDirectory.resolve("openapi.json")
    val yamlFile = outputDirectory.resolve("openapi.yaml")

    require(Files.exists(jsonFile)) { "OpenAPI JSON file is missing: $jsonFile" }
    require(Files.exists(yamlFile)) { "OpenAPI YAML file is missing: $yamlFile" }

    val json = Files.readString(jsonFile)
    val yaml = Files.readString(yamlFile)

    require("\"openapi\"" in json) { "OpenAPI JSON is missing the openapi field." }
    require("\"/api/v1/auth/signup/google\"" in json) { "OpenAPI JSON missing /api/v1/auth/signup/google." }
    require("\"/api/v1/auth/signin/google\"" in json) { "OpenAPI JSON missing /api/v1/auth/signin/google." }
    require("\"/api/v1/ops/sync/status\"" in json) { "OpenAPI JSON missing /api/v1/ops/sync/status." }
    require("\"/api/v1/contents\"" in json) { "OpenAPI JSON missing /api/v1/contents." }
    require("\"/api/v1/contents/{contentId}\"" in json) { "OpenAPI JSON missing /api/v1/contents/{contentId}." }
    require("\"/api/v1/journals\"" in json) { "OpenAPI JSON missing /api/v1/journals." }
    require("\"/api/v1/journals/{journalId}\"" in json) { "OpenAPI JSON missing /api/v1/journals/{journalId}." }
    require("\"/api/v1/associations\"" in json) { "OpenAPI JSON missing /api/v1/associations." }
    require("\"/api/v1/media\"" in json) { "OpenAPI JSON missing /api/v1/media." }
    require("\"/api/v1/media/{mediaId}/binary\"" in json) { "OpenAPI JSON missing /api/v1/media/{mediaId}/binary." }
    require("\"/api/v1/backups\"" in json) { "OpenAPI JSON missing /api/v1/backups." }
    require("\"/api/v1/backups/{backupId}/binary\"" in json) {
        "OpenAPI JSON missing /api/v1/backups/{backupId}/binary."
    }
    require("\"bearerAuth\"" in json) { "OpenAPI JSON missing bearerAuth security scheme." }

    require("openapi:" in yaml) { "OpenAPI YAML is missing the openapi key." }
    require("/api/v1/ops/sync/status:" in yaml) { "OpenAPI YAML missing /api/v1/ops/sync/status path." }
    require("/api/v1/contents:" in yaml) { "OpenAPI YAML missing /api/v1/contents path." }
    require("/api/v1/contents/{contentId}:" in yaml) { "OpenAPI YAML missing /api/v1/contents/{contentId} path." }
    require("/api/v1/journals:" in yaml) { "OpenAPI YAML missing /api/v1/journals path." }
    require("/api/v1/journals/{journalId}:" in yaml) { "OpenAPI YAML missing /api/v1/journals/{journalId} path." }
    require("/api/v1/associations:" in yaml) { "OpenAPI YAML missing /api/v1/associations path." }
    require("/api/v1/media:" in yaml) { "OpenAPI YAML missing /api/v1/media path." }
    require("/api/v1/media/{mediaId}/binary:" in yaml) { "OpenAPI YAML missing /api/v1/media/{mediaId}/binary path." }
    require("/api/v1/backups/{backupId}/binary:" in yaml) {
        "OpenAPI YAML missing /api/v1/backups/{backupId}/binary path."
    }
}
