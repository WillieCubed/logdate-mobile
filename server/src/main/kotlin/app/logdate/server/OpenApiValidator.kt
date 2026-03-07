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
    require("\"/api/v1/sync/media\"" in json) { "OpenAPI JSON missing /api/v1/sync/media." }
    require("\"/api/v1/sync/backups\"" in json) { "OpenAPI JSON missing /api/v1/sync/backups." }
    require("\"bearerAuth\"" in json) { "OpenAPI JSON missing bearerAuth security scheme." }

    require("openapi:" in yaml) { "OpenAPI YAML is missing the openapi key." }
    require("/api/v1/sync/media:" in yaml) { "OpenAPI YAML missing /api/v1/sync/media path." }
}
