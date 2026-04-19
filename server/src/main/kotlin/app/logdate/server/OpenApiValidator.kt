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

    validateSpecContent(json, "JSON")
    validateSpecContent(yaml, "YAML/JSON")
}

private fun validateSpecContent(
    content: String,
    label: String,
) {
    require("openapi" in content) { "OpenAPI $label is missing the openapi field." }
    require("/api/v1/auth/signup/google" in content) { "OpenAPI $label missing /api/v1/auth/signup/google." }
    require("/api/v1/auth/signin/google" in content) { "OpenAPI $label missing /api/v1/auth/signin/google." }
    require("/api/v1/ops/sync/status" in content) { "OpenAPI $label missing /api/v1/ops/sync/status." }
    require("/api/v1/contents" in content) { "OpenAPI $label missing /api/v1/contents." }
    require("/api/v1/contents/{contentId}" in content) { "OpenAPI $label missing /api/v1/contents/{contentId}." }
    require("/api/v1/journals" in content) { "OpenAPI $label missing /api/v1/journals." }
    require("/api/v1/journals/{journalId}" in content) { "OpenAPI $label missing /api/v1/journals/{journalId}." }
    require("/api/v1/associations" in content) { "OpenAPI $label missing /api/v1/associations." }
    require("/api/v1/media" in content) { "OpenAPI $label missing /api/v1/media." }
    require("/api/v1/media/{mediaId}/binary" in content) { "OpenAPI $label missing /api/v1/media/{mediaId}/binary." }
    require("/api/v1/backups" in content) { "OpenAPI $label missing /api/v1/backups." }
    require("/api/v1/backups/{backupId}/binary" in content) {
        "OpenAPI $label missing /api/v1/backups/{backupId}/binary."
    }
    require("bearerAuth" in content) { "OpenAPI $label missing bearerAuth security scheme." }
}
