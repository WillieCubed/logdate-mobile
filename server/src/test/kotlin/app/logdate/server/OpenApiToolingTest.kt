package app.logdate.server

import com.sun.net.httpserver.HttpServer
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenApiToolingTest {
    @Test
    fun `openapi exporter writes json and yaml and validator accepts output`() {
        val outputDir = Files.createTempDirectory("logdate-openapi-test").toAbsolutePath()
        val previous = System.getProperty("logdate.openapi.outputDir")
        System.setProperty("logdate.openapi.outputDir", outputDir.toString())

        try {
            invokeMain("app.logdate.server.OpenApiExporterKt")

            val jsonPath = outputDir.resolve("openapi.json")
            val yamlPath = outputDir.resolve("openapi.yaml")
            assertTrue(Files.exists(jsonPath))
            assertTrue(Files.exists(yamlPath))

            val json = jsonPath.readText()
            val yaml = yamlPath.readText()
            assertTrue(json.contains("\"openapi\""))
            assertTrue(json.contains("\"/api/v1/backups\""))
            assertTrue(yaml.contains("/api/v1/media:"))

            invokeMain("app.logdate.server.OpenApiValidatorKt")
        } finally {
            if (previous == null) {
                System.clearProperty("logdate.openapi.outputDir")
            } else {
                System.setProperty("logdate.openapi.outputDir", previous)
            }
        }
    }

    @Test
    fun `openapi validator fails when generated files are missing`() {
        val outputDir = Files.createTempDirectory("logdate-openapi-missing").toAbsolutePath()
        val previous = System.getProperty("logdate.openapi.outputDir")
        System.setProperty("logdate.openapi.outputDir", outputDir.toString())

        try {
            assertFailsWith<IllegalArgumentException> {
                invokeMain("app.logdate.server.OpenApiValidatorKt")
            }
        } finally {
            if (previous == null) {
                System.clearProperty("logdate.openapi.outputDir")
            } else {
                System.setProperty("logdate.openapi.outputDir", previous)
            }
        }
    }

    @Test
    fun `openapi validator fails when required yaml backup path is missing`() {
        val outputDir = Files.createTempDirectory("logdate-openapi-invalid").toAbsolutePath()
        val previous = System.getProperty("logdate.openapi.outputDir")
        System.setProperty("logdate.openapi.outputDir", outputDir.toString())

        try {
            outputDir.resolve("openapi.json").writeText(
                """
                {
                  "openapi": "3.1.0",
                  "paths": {
                    "/api/v1/auth/signup/google": {},
                    "/api/v1/auth/signin/google": {},
                    "/api/v1/ops/sync/status": {},
                    "/api/v1/contents": {},
                    "/api/v1/contents/{contentId}": {},
                    "/api/v1/journals": {},
                    "/api/v1/journals/{journalId}": {},
                    "/api/v1/associations": {},
                    "/api/v1/media": {},
                    "/api/v1/media/{mediaId}/binary": {},
                    "/api/v1/backups": {}
                  },
                  "components": {
                    "securitySchemes": {
                      "bearerAuth": {}
                    }
                  }
                }
                """.trimIndent(),
            )
            outputDir.resolve("openapi.yaml").writeText(
                """
                openapi: 3.1.0
                /api/v1/ops/sync/status:
                /api/v1/contents:
                /api/v1/contents/{contentId}:
                /api/v1/journals:
                /api/v1/journals/{journalId}:
                /api/v1/associations:
                /api/v1/media:
                /api/v1/media/{mediaId}/binary:
                """.trimIndent(),
            )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    invokeMain("app.logdate.server.OpenApiValidatorKt")
                }
            assertTrue(error.message.orEmpty().contains("/api/v1/backups/{backupId}/binary"))
        } finally {
            if (previous == null) {
                System.clearProperty("logdate.openapi.outputDir")
            } else {
                System.setProperty("logdate.openapi.outputDir", previous)
            }
        }
    }

    @Test
    fun `openapi validator reaches yaml backup-path validation branch`() {
        val outputDir = Files.createTempDirectory("logdate-openapi-yaml-missing").toAbsolutePath()
        val previous = System.getProperty("logdate.openapi.outputDir")
        System.setProperty("logdate.openapi.outputDir", outputDir.toString())

        try {
            outputDir.resolve("openapi.json").writeText(
                """
                {
                  "openapi": "3.1.0",
                  "paths": {
                    "/api/v1/auth/signup/google": {},
                    "/api/v1/auth/signin/google": {},
                    "/api/v1/ops/sync/status": {},
                    "/api/v1/contents": {},
                    "/api/v1/contents/{contentId}": {},
                    "/api/v1/journals": {},
                    "/api/v1/journals/{journalId}": {},
                    "/api/v1/associations": {},
                    "/api/v1/media": {},
                    "/api/v1/media/{mediaId}/binary": {},
                    "/api/v1/backups": {},
                    "/api/v1/backups/{backupId}/binary": {}
                  },
                  "components": {
                    "securitySchemes": {
                      "bearerAuth": {}
                    }
                  }
                }
                """.trimIndent(),
            )
            outputDir.resolve("openapi.yaml").writeText(
                """
                openapi: 3.1.0
                /api/v1/ops/sync/status:
                /api/v1/contents:
                /api/v1/contents/{contentId}:
                /api/v1/journals:
                /api/v1/journals/{journalId}:
                /api/v1/associations:
                /api/v1/media:
                /api/v1/media/{mediaId}/binary:
                """.trimIndent(),
            )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    invokeMain("app.logdate.server.OpenApiValidatorKt")
                }
            assertTrue(error.message.orEmpty().contains("/api/v1/backups/{backupId}/binary"))
        } finally {
            if (previous == null) {
                System.clearProperty("logdate.openapi.outputDir")
            } else {
                System.setProperty("logdate.openapi.outputDir", previous)
            }
        }
    }

    @Test
    fun `openapi exporter fetch helper fails on non-200 responses`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/openapi.json") { exchange ->
            exchange.sendResponseHeaders(500, 0)
            exchange.responseBody.use { it.write("error".toByteArray()) }
        }
        server.start()

        val method =
            Class
                .forName("app.logdate.server.OpenApiExporterKt")
                .getDeclaredMethod(
                    "fetchOpenApi",
                    HttpClient::class.java,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
        method.isAccessible = true

        try {
            assertFailsWith<InvocationTargetException> {
                method.invoke(null, HttpClient.newHttpClient(), server.address.port, "/openapi.json")
            }
        } finally {
            server.stop(0)
        }
    }

    private fun invokeMain(className: String) {
        val method = Class.forName(className).getDeclaredMethod("main")
        try {
            method.invoke(null)
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            when (cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw RuntimeException(cause)
            }
        }
    }
}
