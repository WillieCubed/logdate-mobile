package app.logdate.client.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeveloperModePersistenceTest {
    @Test
    fun developerModeChoiceSurvivesSeparateProcesses() {
        val directory = Files.createTempDirectory("logdate-developer-process").toFile()
        val path = directory.resolve("settings.preferences_pb").absolutePath
        val classpath =
            (
                System.getProperty("java.class.path").split(File.pathSeparator).asSequence() +
                    generateSequence(javaClass.classLoader) { it.parent }
                        .filterIsInstance<URLClassLoader>()
                        .flatMap { it.urLs.asSequence() }
                        .map { File(it.toURI()).absolutePath }
            ).distinct()
                .joinToString(File.pathSeparator)

        fun launch(action: String) {
            val process =
                ProcessBuilder(
                    File(System.getProperty("java.home"), "bin/java").absolutePath,
                    "-Djava.awt.headless=true",
                    "-cp",
                    classpath,
                    DeveloperModeProcessProbe::class.java.name,
                    path,
                    action,
                ).redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Preference process timed out: $action")
                assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
        try {
            launch("hidden")
            launch("enable")
            launch("enabled")
            launch("hide")
            launch("hidden")
            launch("enable")
            launch("enabled")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun developerModeChoiceSurvivesClosingAndReopeningTheStore() =
        runBlocking {
            val directory = Files.createTempDirectory("logdate-developer-mode").toFile()
            val path = directory.resolve("settings.preferences_pb").absolutePath.toPath()
            var storeJob = Job()

            fun openPreferences() =
                LogdatePreferencesDataSource(
                    PreferenceDataStoreFactory.createWithPath(
                        scope = CoroutineScope(Dispatchers.IO + storeJob),
                        produceFile = { path },
                    ),
                )
            try {
                val firstLaunch = openPreferences()
                assertFalse(firstLaunch.observeDeveloperModeEnabled().first())
                firstLaunch.setDeveloperModeEnabled(true)
                assertTrue(firstLaunch.observeDeveloperModeEnabled().first())
                storeJob.cancelAndJoin()

                storeJob = Job()
                val secondLaunch = openPreferences()
                assertTrue(secondLaunch.observeDeveloperModeEnabled().first())
                secondLaunch.setDeveloperModeEnabled(false)
                assertFalse(secondLaunch.observeDeveloperModeEnabled().first())
                storeJob.cancelAndJoin()

                storeJob = Job()
                val thirdLaunch = openPreferences()
                assertFalse(thirdLaunch.observeDeveloperModeEnabled().first())
                thirdLaunch.setDeveloperModeEnabled(true)
                assertTrue(thirdLaunch.observeDeveloperModeEnabled().first())
            } finally {
                storeJob.cancelAndJoin()
                directory.deleteRecursively()
            }
        }
}

object DeveloperModeProcessProbe {
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            val job = Job()
            val preferences =
                LogdatePreferencesDataSource(
                    PreferenceDataStoreFactory.createWithPath(
                        scope = CoroutineScope(Dispatchers.IO + job),
                        produceFile = { args[0].toPath() },
                    ),
                )
            try {
                when (args[1]) {
                    "enable" -> preferences.setDeveloperModeEnabled(true)
                    "hide" -> preferences.setDeveloperModeEnabled(false)
                    "enabled" -> assertTrue(preferences.observeDeveloperModeEnabled().first())
                    "hidden" -> assertFalse(preferences.observeDeveloperModeEnabled().first())
                    else -> error("Unknown preference action")
                }
            } finally {
                job.cancelAndJoin()
            }
        }
}
