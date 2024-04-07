package app.logdate.buildlogic

import org.gradle.api.Project
import java.util.Properties

object PropertiesLoader {
    fun loadProperties(project: Project): Properties {
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }
        return properties
    }
}